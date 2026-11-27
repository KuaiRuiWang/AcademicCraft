package com.wkr.aicode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wkr.aicode.controller.GraduationDocumentController;
import com.wkr.aicode.core.thesis.GraduationDocumentKind;
import com.wkr.aicode.core.thesis.GraduationForm;
import com.wkr.aicode.core.thesis.ThesisWriter;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.model.entity.App;
import com.wkr.aicode.model.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.wkr.aicode.core.thesis.GraduationDocumentKind.assignment;
import static com.wkr.aicode.core.thesis.GraduationDocumentKind.proposal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class GraduationDocumentServiceTest {
    final User owner = User.builder().id(2L).build();
    final ThesisService thesis = mock(ThesisService.class);
    final GraduationForm forms = mock(GraduationForm.class);
    final ThesisWriter writer = mock(ThesisWriter.class);
    final ObjectMapper json = new ObjectMapper();
    GraduationDocumentService service;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(source);
        db.execute("CREATE TABLE app (id BIGINT PRIMARY KEY, userId BIGINT, isDelete INT DEFAULT 0)");
        db.update("INSERT INTO app (id,userId) VALUES (1, 2)");
        db.execute(Files.readString(Path.of("sql/upgrade_graduation_documents.sql")).replace("COLLATE = utf8mb4_unicode_ci", ""));
        var app = App.builder().id(1L).userId(2L).thesisTitle("图书借阅系统").initPrompt("预约及借阅").build();
        when(thesis.authorizedApp(1L, owner)).thenReturn(app);
        service = new GraduationDocumentService(thesis, db, new TransactionTemplate(new DataSourceTransactionManager(source)),
                json, forms, writer);
    }

    @AfterEach void shutdown() { service.close(); }

    @Test void importedTemplateIsScopedAndFailedRetryPreservesCompletedWord() throws Exception {
        byte[] template = {42};
        when(forms.inspect(any())).thenReturn(List.of("课题名称", "研究内容"));
        UserService users = mock(UserService.class);
        when(users.getLoginUser(any())).thenReturn(owner);
        var mvc = MockMvcBuilders.standaloneSetup(new GraduationDocumentController(service, users)).build();
        mvc.perform(post("/app/1/graduation-documents/proposal/template").param("filename", "学校.docx")
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document").content(template))
                .andExpect(jsonPath("$.code").value(0));
        var imported = service.get(1L, proposal, owner);
        assertEquals("学校.docx", imported.templateName());
        assertNull(service.get(1L, assignment, owner).templateId());
        assertThrows(BusinessException.class, () -> service.generate(1L, assignment, owner, imported.templateId()));
        assertEquals("waiting", service.get(1L, assignment, owner).status());

        byte[] word = {1, 2, 3};
        when(forms.render(eq(proposal), eq("图书借阅系统"), anyMap(), aryEq(template))).thenReturn(word);
        var blocked = new CountDownLatch(1);
        when(writer.writeGraduationDocument(eq(proposal), anyString(), eq("预约及借阅"), eq(List.of("研究内容"))))
                .thenAnswer(call -> { assertTrue(blocked.await(3, TimeUnit.SECONDS)); return "{\"研究内容\":\"拟设计预约流程\"}"; })
                .thenReturn("{}");
        service.generate(1L, proposal, owner, imported.templateId());
        try {
            assertThrows(BusinessException.class, () -> service.generate(1L, proposal, owner, "default"));
        } finally {
            blocked.countDown();
        }
        await(proposal, "ready");
        assertEquals("拟设计预约流程", service.get(1L, proposal, owner).sections().get("研究内容"));
        assertArrayEquals(word, service.download(1L, proposal, owner));
        service.generate(1L, proposal, owner, imported.templateId());
        await(proposal, "failed");
        assertArrayEquals(word, service.download(1L, proposal, owner));
        service.deleteForApp(1L);
        assertThrows(BusinessException.class, () -> service.download(1L, proposal, owner));
    }

    @Test void defaultAssignmentWorksBeforeAnyThesisOrSourceExists() throws Exception {
        when(writer.writeGraduationDocument(eq(assignment), anyString(), anyString(), eq(assignment.fields)))
                .thenReturn("{\"主要任务\":\"设计预约\",\"技术要求\":\"持久化\",\"进度安排\":\"第1周调研\",\"成果要求\":\"应用源码\"}");
        when(forms.render(eq(assignment), anyString(), anyMap(), isNull())).thenReturn(new byte[]{3});
        service.generate(1L, assignment, owner, "default");
        await(assignment, "ready");
        assertEquals(assignment.fields, List.copyOf(service.get(1L, assignment, owner).sections().keySet()));
        assertEquals("waiting", service.get(1L, proposal, owner).status());
    }

    @Test void invalidReplacementLeavesPreviousSchoolTemplateUsable() throws Exception {
        when(forms.inspect(any())).thenReturn(List.of("研究内容"));
        service.importTemplate(1L, proposal, owner, "学校.docx", new ByteArrayInputStream(new byte[]{1}));
        var saved = service.get(1L, proposal, owner);
        assertThrows(BusinessException.class, () -> service.importTemplate(1L, proposal, owner, "空.docx",
                new ByteArrayInputStream(new byte[0])));
        assertEquals(saved.templateId(), service.get(1L, proposal, owner).templateId());
    }

    private void await(GraduationDocumentKind kind, String status) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (status.equals(service.get(1L, kind, owner).status())) return;
            Thread.sleep(20);
        }
        assertEquals(status, service.get(1L, kind, owner).status());
    }
}
