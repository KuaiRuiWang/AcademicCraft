package com.wkr.aicode.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.wkr.aicode.core.thesis.ProjectSnapshotService;
import com.wkr.aicode.core.thesis.ThesisCitations;
import com.wkr.aicode.core.thesis.ThesisLiteratureSearch;
import com.wkr.aicode.core.thesis.ThesisReference;
import com.wkr.aicode.core.thesis.ThesisWriter;
import com.wkr.aicode.core.thesis.ThesisDiagramService;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.mapper.AppMapper;
import com.wkr.aicode.mapper.AppThesisMapper;
import com.wkr.aicode.mapper.AppThesisChapterMapper;
import com.wkr.aicode.model.dto.app.ThesisChapterUpdateRequest;
import com.wkr.aicode.model.dto.app.ThesisSelectionRewriteRequest;
import com.wkr.aicode.model.entity.App;
import com.wkr.aicode.model.entity.AppThesis;
import com.wkr.aicode.model.entity.AppThesisChapter;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.app.ThesisVO;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** 论文任务独立于 SSE 连接运行，任务与章节进度均落库。 */
@Service
@Slf4j
public class ThesisService {
    private static final Pattern NON_PROSE_BLOCK = Pattern.compile("(?m)^[ \\t]{0,3}(?:`{3,}|~{3,})|<!-- 保留配图 \\d+ -->");
    @Resource private AppMapper appMapper;
    @Resource private AppThesisMapper thesisMapper;
    @Resource private AppThesisChapterMapper chapterMapper;
    @Resource private ProjectSnapshotService snapshotService;
    @Resource private ThesisWriter thesisWriter;
    @Resource private ThesisDiagramService diagramService;
    @Resource private ThesisLiteratureSearch literatureSearch;
    @Resource private ObjectMapper objectMapper;
    @Resource private TransactionTemplate transactionTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, Thread.ofVirtual().name("thesis-", 0).factory());

    public ThesisVO getThesis(Long appId, User user) {
        App app = authorizedApp(appId, user);
        return transactionTemplate.execute(status -> readThesis(appId, app));
    }

    private ThesisVO readThesis(Long appId, App app) {
        AppThesis thesis = thesisMapper.selectForUpdate(appId);
        ThesisVO result = new ThesisVO();
        if (thesis == null) {
            result.setAppId(appId);
            result.setTitle(defaultTitle(app));
            result.setStatus("waiting");
            result.setStage("应用生成完成后可生成论文初稿");
            result.setChapters(List.of());
        } else {
            BeanUtil.copyProperties(thesis, result);
            List<AppThesisChapter> chapters = chapters(appId);
            result.setChapters(chapters);
            ThesisCitations.Snapshot snapshot = readLiterature(thesis);
            if (snapshot != null) {
                result.setLiteratureStatus(snapshot.status());
                result.setReferenceCandidates(snapshot.references().stream().map(reference ->
                        new ThesisVO.ReferenceCandidate(reference.id(), reference.title(), reference.year(), reference.url())).toList());
                result.setReferences(ThesisCitations.bibliography(
                        chapters.stream().map(AppThesisChapter::getContent).toList(), snapshot.references()));
            }
        }
        return result;
    }

    public void deleteForApp(Long appId) {
        transactionTemplate.executeWithoutResult(status -> {
            thesisMapper.selectForUpdate(appId);
            chapterMapper.deleteByQuery(QueryWrapper.create().eq("appId", appId));
            thesisMapper.deleteById(appId);
        });
    }

    public void generate(Long appId, User user) {
        App app = authorizedApp(appId, user);
        start(app, true);
    }

    /** 生成代码之前撤销旧任务，防止旧模型响应覆盖新版本。 */
    public void sourceChanging(App app) {
        transactionTemplate.executeWithoutResult(status -> {
            AppThesis thesis = thesisMapper.selectForUpdate(app.getId());
            if (thesis == null) return;
            thesis.setRevision(UUID.randomUUID().toString());
            thesis.setStatus("stale");
            thesis.setStage("应用代码已变更，论文需要更新");
            thesis.setErrorMessage("");
            thesisMapper.update(thesis);
        });
    }

    /** 仅由成功的代码生成出口调用，论文失败不改变应用生成结果。 */
    public void generationSucceeded(Long appId) {
        try {
            App app = appMapper.selectOneById(appId);
            if (app == null || !Integer.valueOf(1).equals(app.getGenerateThesis())) return;
            start(app, false);
        } catch (Exception e) {
            log.error("创建论文任务失败，appId={}", appId, e);
        }
    }

    void start(App app, boolean requestedByUser) {
        // 1. 在生成任务前冻结源码资料，模型始终依据同一份材料写作。
        ProjectSnapshotService.Snapshot snapshot = snapshotService.capture(app);
        String revision = transactionTemplate.execute(status -> {
            AppThesis thesis = thesisMapper.selectForUpdate(app.getId());
            if (thesis == null) {
                thesis = new AppThesis();
                thesis.setAppId(app.getId());
                thesis.setTitle(defaultTitle(app));
                thesis.setStatus("pending");
                thesis.setStage("准备项目资料");
                thesis.setRevision(UUID.randomUUID().toString());
                thesisMapper.insert(thesis, true);
            }
            ThrowUtils.throwIf("generating".equals(thesis.getStatus()), ErrorCode.OPERATION_ERROR, "论文正在生成，请查看当前进度");
            if (!requestedByUser && "ready".equals(thesis.getStatus()) && Objects.equals(thesis.getSourceVersion(), snapshot.version())) return null;
            boolean regenerate = requestedByUser && "ready".equals(thesis.getStatus());
            thesis.setRevision(UUID.randomUUID().toString());
            thesis.setSourceVersion(snapshot.version());
            thesis.setSourceContext(snapshot.context());
            thesis.setStatus("generating");
            thesis.setStage("项目资料已冻结，准备检索文献");
            thesis.setErrorMessage("");
            thesisMapper.update(thesis);
            if (chapters(app.getId()).isEmpty()) {
                for (int index = 0; index < ThesisWriter.CHAPTERS.size(); index++) {
                    AppThesisChapter chapter = new AppThesisChapter();
                    chapter.setAppId(app.getId());
                    chapter.setChapterNo(index + 1);
                    chapter.setTitle(ThesisWriter.CHAPTERS.get(index));
                    chapter.setStatus("pending");
                    chapter.setUserEdited(0);
                    chapterMapper.insert(chapter, true);
                }
            } else if (regenerate) {
                for (AppThesisChapter chapter : chapters(app.getId())) {
                    if (!Integer.valueOf(1).equals(chapter.getUserEdited())) {
                        chapter.setStatus("pending");
                        chapterMapper.update(chapter);
                    }
                }
            }
            return thesis.getRevision();
        });
        if (revision != null) executor.submit(() -> writeChapters(app.getId(), revision));
    }

    void writeChapters(Long appId, String revision) {
        try {
            AppThesis thesis = thesisMapper.selectOneById(appId);
            if (!isCurrent(thesis, revision)) return;
            List<AppThesisChapter> chapters = chapters(appId);
            // 1. 写作前准备本次源码版本的候选文献，版本失效时停止使用检索结果。
            ThesisCitations.Snapshot literature = prepareLiterature(thesis, chapters, revision);
            if (literature == null) return;
            StringBuilder previous = new StringBuilder();
            for (AppThesisChapter chapter : chapters) {
                // 2. 重试保留已完成章节；更新源码时只重写未被用户编辑的章节。
                if (Integer.valueOf(1).equals(chapter.getUserEdited()) || ("ready".equals(chapter.getStatus())
                        && Objects.equals(chapter.getSourceVersion(), thesis.getSourceVersion()))) {
                    appendSummary(previous, chapter);
                    continue;
                }
                String chapterTitle = "参考文献与待补材料".equals(chapter.getTitle())
                        ? ThesisWriter.MATERIALS_CHAPTER : chapter.getTitle();
                if (!withCurrent(appId, revision, current -> {
                    current.setStage("正在生成：" + chapterTitle);
                    chapter.setStatus("generating");
                    chapter.setErrorMessage("");
                    chapterMapper.update(chapter);
                })) return;
                try {
                    List<ThesisReference> references = chapterTitle.startsWith("第一章 ") || chapterTitle.startsWith("第二章 ")
                            ? literature.references() : List.of();
                    String content = thesisWriter.writeChapter(thesis.getTitle(), chapterTitle,
                            thesis.getSourceContext(), previous.toString(), references);
                    Set<String> allowed = referenceIds(references);
                    String verified = ThesisCitations.replace(content, id -> allowed.contains(id) ? "[@" + id + "]" : "");
                    String illustrated = diagramService.renderChapter(appId, revision, chapter.getChapterNo(), verified);
                    if (!withCurrent(appId, revision, current -> {
                        chapter.setTitle(chapterTitle);
                        chapter.setContent(illustrated);
                        chapter.setSourceVersion(current.getSourceVersion());
                        chapter.setStatus("ready");
                        chapterMapper.update(chapter);
                    })) return;
                    appendSummary(previous, chapter);
                } catch (Exception error) {
                    withCurrent(appId, revision, current -> {
                        chapter.setStatus("failed");
                        chapter.setErrorMessage("本章生成失败，可重试未完成章节");
                        chapterMapper.update(chapter);
                    });
                    throw error;
                }
            }
            withCurrent(appId, revision, current -> {
                current.setStatus("ready");
                current.setStage("初稿已生成，可编辑章节或导出 Word");
                current.setErrorMessage("");
            });
        } catch (Exception error) {
            log.error("论文生成失败，appId={}，revision={}", appId, revision, error);
            withCurrent(appId, revision, thesis -> {
                thesis.setStatus("failed");
                thesis.setStage("部分章节未完成，已保留生成结果");
                thesis.setErrorMessage("论文生成中断，请重试未完成章节");
            });
        }
    }

    private ThesisCitations.Snapshot prepareLiterature(AppThesis thesis, List<AppThesisChapter> chapters, String revision)
            throws JsonProcessingException {
        ThesisCitations.Snapshot previous = readLiterature(thesis);
        if (previous != null && "ready".equals(previous.status())
                && Objects.equals(previous.sourceVersion(), thesis.getSourceVersion())) {
            return previous;
        }
        Long appId = thesis.getAppId();
        if (!withCurrent(appId, revision, current -> current.setStage("正在检索并核验文献"))) return null;

        List<String> queries;
        try {
            queries = thesisWriter.planLiteratureSearch(thesis.getTitle(), thesis.getSourceContext());
        } catch (RuntimeException error) {
            log.info("论文检索词生成未成功，将使用论文题目检索，appId={}", appId);
            queries = List.of();
        }
        if (queries.isEmpty()) queries = List.of(thesis.getTitle());
        ThesisLiteratureSearch.Result search = literatureSearch.search(queries);
        Map<String, ThesisReference> references = new LinkedHashMap<>();
        for (ThesisReference reference : search.references()) references.put(reference.id(), reference);

        // 人工编辑及已完成章节可能仍引用上次检索的来源。
        if (previous != null) {
            Set<String> cited = new HashSet<>();
            for (AppThesisChapter chapter : chapters) cited.addAll(ThesisCitations.ids(chapter.getContent()));
            for (ThesisReference reference : previous.references()) {
                if (cited.contains(reference.id())) references.putIfAbsent(reference.id(), reference);
            }
        }
        ThesisCitations.Snapshot snapshot = new ThesisCitations.Snapshot(
                thesis.getSourceVersion(), search.status(), List.copyOf(references.values()));
        String serialized = objectMapper.writeValueAsString(snapshot);
        return withCurrent(appId, revision, current -> current.setReferenceSnapshot(serialized)) ? snapshot : null;
    }

    private ThesisCitations.Snapshot readLiterature(AppThesis thesis) {
        if (StrUtil.isBlank(thesis.getReferenceSnapshot())) return null;
        try {
            return objectMapper.readValue(thesis.getReferenceSnapshot(), ThesisCitations.Snapshot.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("读取论文文献快照失败", error);
        }
    }

    private Set<String> referenceIds(List<ThesisReference> references) {
        Set<String> ids = new HashSet<>();
        for (ThesisReference reference : references) ids.add(reference.id());
        return ids;
    }

    private void appendSummary(StringBuilder previous, AppThesisChapter chapter) {
        String content = Objects.toString(chapter.getContent(), "");
        previous.append(chapter.getTitle()).append("：").append(content, 0, Math.min(400, content.length())).append('\n');
    }

    boolean withCurrent(Long appId, String revision, Consumer<AppThesis> action) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            AppThesis thesis = thesisMapper.selectForUpdate(appId);
            if (!isCurrent(thesis, revision)) return false;
            action.accept(thesis);
            thesisMapper.update(thesis);
            return true;
        }));
    }

    static boolean isCurrent(AppThesis thesis, String revision) {
        return thesis != null && Objects.equals(thesis.getRevision(), revision) && "generating".equals(thesis.getStatus());
    }

    public void saveChapter(Long appId, Integer chapterNo, ThesisChapterUpdateRequest request, User user) {
        authorizedApp(appId, user);
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getContent()) || request.getContent().length() > 100_000,
                ErrorCode.PARAMS_ERROR, "正文不能为空，且不能超过 10 万字");
        diagramService.validateChapter(appId, request.getContent());
        transactionTemplate.executeWithoutResult(status -> {
            AppThesis thesis = thesisMapper.selectForUpdate(appId);
            ThrowUtils.throwIf(thesis == null, ErrorCode.NOT_FOUND_ERROR, "论文不存在");
            ThrowUtils.throwIf("generating".equals(thesis.getStatus()) || !Objects.equals(thesis.getRevision(), request.getRevision()),
                    ErrorCode.OPERATION_ERROR, "论文正在生成或版本已变化，请刷新后编辑");
            AppThesisChapter chapter = chapterMapper.selectOneByQuery(QueryWrapper.create().eq("appId", appId).eq("chapterNo", chapterNo));
            ThrowUtils.throwIf(chapter == null, ErrorCode.NOT_FOUND_ERROR, "章节不存在");
            ThrowUtils.throwIf(!Objects.equals(chapter.getContent(), request.getOriginalContent()),
                    ErrorCode.OPERATION_ERROR, "本章已在其他窗口修改，请刷新后核对，当前输入尚未保存");
            List<String> citations = ThesisCitations.ids(request.getContent());
            if (!citations.isEmpty()) {
                ThesisCitations.Snapshot literature = readLiterature(thesis);
                ThrowUtils.throwIf(literature == null || !referenceIds(literature.references()).containsAll(citations),
                        ErrorCode.PARAMS_ERROR, "正文含有未核验的文献，请从参考文献中重新选择");
            }
            chapter.setContent(request.getContent());
            chapter.setSourceVersion(thesis.getSourceVersion());
            chapter.setUserEdited(1);
            chapter.setStatus("ready");
            chapter.setErrorMessage("");
            chapterMapper.update(chapter);
        });
    }

    /** 根据当前编辑稿的选区生成建议，确认采用后仍由现有章节保存接口写入。 */
    public String rewriteSelection(Long appId, Integer chapterNo, ThesisSelectionRewriteRequest request, User user) {
        // 1. 校验应用归属、选区和修改要求，限制提交给模型的上下文。
        authorizedApp(appId, user);
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getSelectedText())
                        || request.getSelectedText().length() > 4000,
                ErrorCode.PARAMS_ERROR, "请选择 4000 字以内的正文");
        ThrowUtils.throwIf(StrUtil.isBlank(request.getInstruction()) || request.getInstruction().length() > 500,
                ErrorCode.PARAMS_ERROR, "请输入 500 字以内的修改要求");
        ThrowUtils.throwIf(NON_PROSE_BLOCK.matcher(request.getSelectedText()).find(),
                ErrorCode.PARAMS_ERROR, "请选择正文，避开配图和代码块");
        String beforeText = Objects.toString(request.getBeforeText(), "");
        String afterText = Objects.toString(request.getAfterText(), "");
        ThrowUtils.throwIf(beforeText.length() > 500 || afterText.length() > 500,
                ErrorCode.PARAMS_ERROR, "选区的上下文过长");

        // 2. 检查编辑版本，只为已有正文的章节生成建议。
        AppThesis thesis = thesisMapper.selectOneById(appId);
        ThrowUtils.throwIf(thesis == null, ErrorCode.NOT_FOUND_ERROR, "论文不存在");
        ThrowUtils.throwIf("generating".equals(thesis.getStatus()) || !Objects.equals(thesis.getRevision(), request.getRevision()),
                ErrorCode.OPERATION_ERROR, "论文正在生成或版本已变化，请刷新后编辑");
        AppThesisChapter chapter = chapterMapper.selectOneByQuery(QueryWrapper.create().eq("appId", appId).eq("chapterNo", chapterNo));
        ThrowUtils.throwIf(chapter == null || StrUtil.isBlank(chapter.getContent()), ErrorCode.NOT_FOUND_ERROR, "章节正文不存在");

        // 3. 返回选区的替换文字，禁止建议插入配图占位行或代码块。
        String suggestion = thesisWriter.rewriteSelection(thesis.getTitle(), chapter.getTitle(),
                request.getSelectedText(), request.getInstruction().strip(), beforeText, afterText);
        ThrowUtils.throwIf(StrUtil.isBlank(suggestion) || suggestion.length() > 8000 || NON_PROSE_BLOCK.matcher(suggestion).find(),
                ErrorCode.OPERATION_ERROR, "AI 返回的修改建议无法使用，请调整要求后重试");
        ThrowUtils.throwIf(!ThesisCitations.ids(request.getSelectedText()).equals(ThesisCitations.ids(suggestion)),
                ErrorCode.OPERATION_ERROR, "AI 修改建议改变了文献引用，请调整选区后重试");
        return suggestion;
    }

    public Path previewMermaid(Long appId, Integer chapterNo, int figureNo, User user) {
        authorizedApp(appId, user);
        AppThesisChapter chapter = chapterMapper.selectOneByQuery(QueryWrapper.create().eq("appId", appId).eq("chapterNo", chapterNo));
        ThrowUtils.throwIf(chapter == null || StrUtil.isBlank(chapter.getContent()), ErrorCode.NOT_FOUND_ERROR, "章节正文不存在");
        return diagramService.previewMermaid(appId, chapter.getContent(), figureNo);
    }

    public App authorizedApp(Long appId, User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!Objects.equals(app.getUserId(), user.getId()), ErrorCode.NO_AUTH_ERROR, "仅应用创建者可以访问论文");
        return app;
    }

    private List<AppThesisChapter> chapters(Long appId) {
        return chapterMapper.selectListByQuery(QueryWrapper.create().eq("appId", appId).orderBy("chapterNo", true));
    }

    private String defaultTitle(App app) {
        if (StrUtil.isNotBlank(app.getThesisTitle())) return app.getThesisTitle().strip();
        String name = Objects.toString(app.getAppName(), "Web 应用").replaceFirst("^(帮我|请)?(创建|设计|构建|制作|做)(一个|一套)?", "");
        return name + "的设计与实现";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedJobs() {
        // shortcut: 当前按单后端实例部署恢复任务；多实例部署前需增加任务租约。
        for (AppThesis thesis : thesisMapper.selectListByQuery(QueryWrapper.create().eq("status", "generating"))) {
            executor.submit(() -> writeChapters(thesis.getAppId(), thesis.getRevision()));
        }
    }

    @PreDestroy
    public void close() { executor.shutdown(); }
}
