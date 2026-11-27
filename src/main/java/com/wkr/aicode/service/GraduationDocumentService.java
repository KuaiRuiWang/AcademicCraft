package com.wkr.aicode.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wkr.aicode.core.thesis.GraduationDocumentKind;
import com.wkr.aicode.core.thesis.GraduationForm;
import com.wkr.aicode.core.thesis.ThesisWriter;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.model.entity.App;
import com.wkr.aicode.model.entity.User;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class GraduationDocumentService {
    private static final int MAX_TEMPLATE_BYTES = 5 * 1024 * 1024;
    private final ThesisService thesis;
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final GraduationForm forms;
    private final ThesisWriter writer;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(12), Thread.ofVirtual().name("graduation-document-", 0).factory());

    public record View(String status, String error, String templateId, String templateName, List<String> templateFields,
                       List<String> defaultFields, String resultTemplateName, Map<String, String> sections) {}
    private record Template(String name, List<String> fields, byte[] file) {}

    public View get(Long appId, GraduationDocumentKind kind, User user) {
        thesis.authorizedApp(appId, user);
        return db.query("""
                SELECT status, errorMessage, templateId, templateName, templateFields, resultTemplateName, sections
                FROM app_graduation_document WHERE appId=? AND kind=?
                """, (rs, row) -> new View(rs.getString("status"), rs.getString("errorMessage"), rs.getString("templateId"),
                rs.getString("templateName"), fields(rs.getString("templateFields")), kind.fields, rs.getString("resultTemplateName"),
                sections(rs.getString("sections"))), appId, kind.name()).stream().findFirst()
                .orElseGet(() -> new View("waiting", null, null, null, List.of(), kind.fields, null, Map.of()));
    }

    public void importTemplate(Long appId, GraduationDocumentKind kind, User user, String originalName, InputStream input)
            throws IOException {
        thesis.authorizedApp(appId, user);
        String name = Objects.toString(originalName, "").replace('\\', '/');
        String filename = name.substring(name.lastIndexOf('/') + 1);
        ThrowUtils.throwIf(filename.length() > 255 || !filename.toLowerCase(Locale.ROOT).endsWith(".docx"),
                ErrorCode.PARAMS_ERROR, "请选择 .docx 模板");

        // 先识别栏目，校验通过后才替换已保存的模板。
        byte[] bytes = input.readNBytes(MAX_TEMPLATE_BYTES + 1);
        ThrowUtils.throwIf(bytes.length == 0 || bytes.length > MAX_TEMPLATE_BYTES,
                ErrorCode.PARAMS_ERROR, "请选择 5 MB 以内的非空模板");
        String fields = json.writeValueAsString(forms.inspect(bytes));
        transactions.executeWithoutResult(tx -> {
            ensureDocument(appId, kind, user);
            db.update("""
                    UPDATE app_graduation_document SET templateId=?, templateName=?, templateFields=?, templateFile=?
                    WHERE appId=? AND kind=?
                    """, UUID.randomUUID().toString(), filename, fields, bytes, appId, kind.name());
        });
    }

    public void generate(Long appId, GraduationDocumentKind kind, User user, String templateId) {
        App app = thesis.authorizedApp(appId, user);
        String revision = UUID.randomUUID().toString();
        // 1. 冻结本次所选模板，并占用当前应用的该类任务。
        Template template = transactions.execute(tx -> {
            ensureDocument(appId, kind, user);
            int claimed = db.update("""
                    UPDATE app_graduation_document SET revision=?, status='generating', errorMessage=NULL
                    WHERE appId=? AND kind=? AND status<>'generating'
                    """, revision, appId, kind.name());
            ThrowUtils.throwIf(claimed != 1, ErrorCode.OPERATION_ERROR, "这份材料正在生成");
            if ("default".equals(templateId)) return new Template("内置模板", kind.fields, null);
            return db.query("""
                    SELECT templateName, templateFields, templateFile FROM app_graduation_document
                    WHERE appId=? AND kind=? AND templateId=?
                    """, (rs, row) -> new Template(rs.getString(1), fields(rs.getString(2)), rs.getBytes(3)),
                    appId, kind.name(), templateId).stream().findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "模板已变更，请重新选择"));
        });
        // 2. 后台写作完成后一次性发布正文与 Word，失败时仍能下载上一份结果。
        try {
            workers.execute(() -> write(app, kind, revision, template));
        } catch (RejectedExecutionException busy) {
            fail(appId, kind, revision, "生成队列已满，请稍后重试");
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "生成队列已满，请稍后重试");
        }
    }

    private void ensureDocument(Long appId, GraduationDocumentKind kind, User user) {
        // 与应用删除串行，避免长时间解析模板后为已删除的应用创建记录。
        var owners = db.queryForList("SELECT userId FROM app WHERE id=? AND isDelete=0 FOR UPDATE", Long.class, appId);
        ThrowUtils.throwIf(!owners.contains(user.getId()), ErrorCode.NO_AUTH_ERROR, "应用不存在或无权访问");
        db.update("""
                INSERT INTO app_graduation_document (appId, kind) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE appId=appId
                """, appId, kind.name());
    }

    private void write(App app, GraduationDocumentKind kind, String revision, Template template) {
        try {
            String title = ThesisService.defaultTitle(app);
            var requested = template.fields.stream().filter(name -> !"课题名称".equals(name)).toList();
            String raw = writer.writeGraduationDocument(kind, title, Objects.toString(app.getInitPrompt(), ""), requested);
            var response = json.readTree(raw.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
            Map<String, String> values = new LinkedHashMap<>();
            for (String field : template.fields) {
                if ("课题名称".equals(field)) {
                    values.put(field, title);
                } else {
                    var value = response.path(field);
                    if (!value.isTextual() || value.asText().isBlank()) throw new IOException("缺少材料栏目");
                    values.put(field, value.asText().strip());
                }
            }
            byte[] word = forms.render(kind, title, values, template.file);
            db.update("""
                    UPDATE app_graduation_document SET status='ready', sections=?, documentFile=?, resultTemplateName=?
                    WHERE appId=? AND kind=? AND revision=? AND status='generating'
                    """, json.writeValueAsString(values), word, template.name, app.getId(), kind.name(), revision);
        } catch (Exception error) {
            log.warn("毕设材料生成失败，appId={}，kind={}", app.getId(), kind, error);
            fail(app.getId(), kind, revision, "材料生成失败，请重试");
        }
    }

    public byte[] download(Long appId, GraduationDocumentKind kind, User user) {
        thesis.authorizedApp(appId, user);
        return db.query("""
                SELECT documentFile FROM app_graduation_document WHERE appId=? AND kind=? AND documentFile IS NOT NULL
                """, (rs, row) -> rs.getBytes(1), appId, kind.name()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先生成材料"));
    }

    private void fail(Long appId, GraduationDocumentKind kind, String revision, String message) {
        db.update("""
                UPDATE app_graduation_document SET status='failed', errorMessage=?
                WHERE appId=? AND kind=? AND revision=? AND status='generating'
                """, message, appId, kind.name(), revision);
    }

    private List<String> fields(String stored) {
        try {
            return stored == null ? List.of() : List.of(json.readValue(stored, String[].class));
        } catch (IOException error) {
            throw new IllegalStateException("模板栏目读取失败", error);
        }
    }

    private Map<String, String> sections(String stored) {
        try {
            return stored == null ? Map.of() : json.readValue(stored, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (IOException error) {
            throw new IllegalStateException("材料正文读取失败", error);
        }
    }

    public void deleteForApp(Long appId) {
        db.update("DELETE FROM app_graduation_document WHERE appId=?", appId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        // 当前使用单后端实例，与论文任务共享相同的部署方式。
        db.update("UPDATE app_graduation_document SET status='failed', errorMessage='生成被中断，请重试' WHERE status='generating'");
    }

    @PreDestroy
    public void close() { workers.shutdownNow(); }
}
