package com.wkr.aicode.service;

import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import com.wkr.aicode.core.thesis.ProjectSnapshotService;
import com.wkr.aicode.core.thesis.ThesisWriter;
import com.wkr.aicode.core.thesis.ThesisDiagramService;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.mapper.AppMapper;
import com.wkr.aicode.mapper.AppThesisChapterMapper;
import com.wkr.aicode.mapper.AppThesisMapper;
import com.wkr.aicode.model.dto.app.ThesisChapterUpdateRequest;
import com.wkr.aicode.model.dto.app.ThesisSelectionRewriteRequest;
import com.wkr.aicode.model.entity.App;
import com.wkr.aicode.model.entity.User;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 MyBatis-Flex 映射和事务验证任务持久化、重试及旧响应隔离。 */
@Timeout(20)
class ThesisServiceTest {
    ThesisService service;
    AppMapper apps;
    ProjectSnapshotService snapshots;
    ThesisWriter writer;
    ThesisDiagramService diagrams;
    final User owner = User.builder().id(2L).build();
    final App app = App.builder().id(1L).userId(2L).appName("图书管理").initPrompt("管理图书").codeGenType("vue_project").generateThesis(1).build();

    @BeforeEach
    void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        String initial = Files.readString(Path.of("sql/init.sql")).replace("\r\n", "\n");
        String appSchema = initial.substring(initial.indexOf("create table app\n"), initial.indexOf("-- 应用论文任务"));
        String thesisSchema = Files.readString(Path.of("sql/upgrade_thesis.sql"));
        String schema = (appSchema + thesisSchema.substring(thesisSchema.indexOf("CREATE TABLE")))
                .replaceAll("(?i)collate\\s*=\\s*utf8mb4_unicode_ci", "")
                .replaceAll("(?i)on\\s+update\\s+CURRENT_TIMESTAMP", "")
                .replaceAll("(?i)comment\\s+'[^']*'", "");
        new ResourceDatabasePopulator(new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8))).execute(dataSource);
        var factory = new FlexSqlSessionFactoryBean();
        var configuration = new FlexConfiguration();
        factory.setConfiguration(configuration);
        factory.setDataSource(dataSource);
        var sessionFactory = factory.getObject();
        configuration.addMapper(AppMapper.class);
        configuration.addMapper(AppThesisMapper.class);
        configuration.addMapper(AppThesisChapterMapper.class);
        var session = new SqlSessionTemplate(sessionFactory);
        apps = session.getMapper(AppMapper.class);
        apps.insert(app, true);
        service = new ThesisService();
        snapshots = mock(ProjectSnapshotService.class);
        writer = mock(ThesisWriter.class);
        diagrams = mock(ThesisDiagramService.class);
        when(diagrams.renderChapter(anyLong(), anyString(), anyInt(), anyString())).thenAnswer(call -> call.getArgument(3));
        when(snapshots.capture(any())).thenReturn(new ProjectSnapshotService.Snapshot("version-1", "真实源码版本一"));
        when(writer.writeChapter(anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> call.getArgument(1) + "的正文");
        ReflectionTestUtils.setField(service, "appMapper", apps);
        ReflectionTestUtils.setField(service, "thesisMapper", session.getMapper(AppThesisMapper.class));
        ReflectionTestUtils.setField(service, "chapterMapper", session.getMapper(AppThesisChapterMapper.class));
        ReflectionTestUtils.setField(service, "snapshotService", snapshots);
        ReflectionTestUtils.setField(service, "thesisWriter", writer);
        ReflectionTestUtils.setField(service, "diagramService", diagrams);
        ReflectionTestUtils.setField(service, "transactionTemplate", new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @AfterEach void close() { if (service != null) service.close(); }

    @Test void rejectsOtherOwnersBeforeModelOrSnapshotAccess() {
        User stranger = User.builder().id(3L).build();
        assertThrows(BusinessException.class, () -> service.getThesis(1L, stranger));
        assertThrows(BusinessException.class, () -> service.generate(1L, stranger));
        assertThrows(BusinessException.class, () -> service.saveChapter(1L, 1, null, stranger));
        assertThrows(BusinessException.class, () -> service.rewriteSelection(1L, 1, null, stranger));
        verifyNoInteractions(writer, snapshots);
    }

    @Test void persistsChaptersAndPreservesManualEditsAcrossSourceChanges() throws Exception {
        service.generate(1L, owner);
        await("ready");
        var first = service.getThesis(1L, owner);
        assertEquals(10, first.getChapters().size());
        var input = new ThesisChapterUpdateRequest();
        input.setRevision(first.getRevision());
        input.setOriginalContent(first.getChapters().get(0).getContent());
        input.setContent("作者手动修订的摘要");
        service.saveChapter(1L, 1, input, owner);
        service.sourceChanging(app);
        assertEquals("stale", service.getThesis(1L, owner).getStatus());
        when(snapshots.capture(any())).thenReturn(new ProjectSnapshotService.Snapshot("version-2", "真实源码版本二"));
        service.generate(1L, owner);
        await("ready");
        var updated = service.getThesis(1L, owner);
        assertEquals("version-2", updated.getSourceVersion());
        assertEquals("作者手动修订的摘要", updated.getChapters().get(0).getContent());
        assertEquals(1, updated.getChapters().get(0).getUserEdited());
        assertEquals("version-2", updated.getChapters().get(1).getSourceVersion());
        assertThrows(BusinessException.class, () -> service.saveChapter(1L, 1, input, owner));
    }

    @Test void retryKeepsCompletedChaptersAndContinuesAfterModelFailure() throws Exception {
        AtomicBoolean failed = new AtomicBoolean();
        when(writer.writeChapter(anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            if ("第三章 需求分析".equals(call.getArgument(1)) && !failed.getAndSet(true)) throw new IllegalStateException("temporary model failure");
            return call.getArgument(1) + "的正文";
        });
        service.generate(1L, owner);
        await("failed");
        var first = service.getThesis(1L, owner);
        assertEquals("ready", first.getChapters().get(0).getStatus());
        assertEquals("failed", first.getChapters().get(4).getStatus());
        service.generate(1L, owner);
        await("ready");
        verify(writer, times(1)).writeChapter(anyString(), eq("摘要"), anyString(), anyString());
        verify(writer, times(2)).writeChapter(anyString(), eq("第三章 需求分析"), anyString(), anyString());
    }

    @Test void aLateOldModelResponseCannotOverwriteTheNewSourceVersion() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(writer.writeChapter(anyString(), anyString(), eq("真实源码版本一"), anyString())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return "旧模型响应";
        });
        service.generate(1L, owner);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertThrows(BusinessException.class, () -> service.generate(1L, owner));
        service.sourceChanging(app);
        when(snapshots.capture(any())).thenReturn(new ProjectSnapshotService.Snapshot("version-2", "真实源码版本二"));
        service.generate(1L, owner);
        await("ready");
        release.countDown();
        Thread.sleep(50);
        assertTrue(service.getThesis(1L, owner).getChapters().stream().noneMatch(c -> "旧模型响应".equals(c.getContent())));
    }

    @Test void concurrentManualEditsAreRejectedWithoutReplacingSavedText() throws Exception {
        service.generate(1L, owner);
        await("ready");
        var thesis = service.getThesis(1L, owner);
        var input = new ThesisChapterUpdateRequest();
        input.setRevision(thesis.getRevision());
        input.setOriginalContent("过时的正文");
        input.setContent("不应覆盖");
        assertThrows(BusinessException.class, () -> service.saveChapter(1L, 1, input, owner));
        assertEquals("摘要的正文", service.getThesis(1L, owner).getChapters().get(0).getContent());
    }

    @Test void rewritingAnUnsavedSelectionReturnsASuggestionWithoutUpdatingTheThesis() throws Exception {
        service.generate(1L, owner);
        await("ready");
        var thesis = service.getThesis(1L, owner);
        var input = new ThesisSelectionRewriteRequest();
        input.setSelectedText("编辑稿中的句子");
        input.setInstruction("精简");
        input.setBeforeText("前文");
        input.setAfterText("后文");
        input.setRevision("old-revision");
        assertThrows(BusinessException.class, () -> service.rewriteSelection(1L, 1, input, owner));
        verify(writer, never()).rewriteSelection(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        input.setRevision(thesis.getRevision());
        when(writer.rewriteSelection(thesis.getTitle(), "摘要", "编辑稿中的句子", "精简", "前文", "后文"))
                .thenReturn("精简后的句子", "<!-- 保留配图 1 -->");
        assertEquals("精简后的句子", service.rewriteSelection(1L, 1, input, owner));
        assertThrows(BusinessException.class, () -> service.rewriteSelection(1L, 1, input, owner));
        var result = service.getThesis(1L, owner);
        assertEquals("ready", result.getStatus());
        assertEquals(thesis.getRevision(), result.getRevision());
        assertEquals(thesis.getChapters(), result.getChapters());
        verify(writer, times(10)).writeChapter(anyString(), anyString(), anyString(), anyString());
    }

    @Test void explicitRegenerationRewritesAiChaptersWhileAutomaticDuplicateDoesNot() throws Exception {
        service.generate(1L, owner);
        await("ready");
        service.generationSucceeded(1L);
        verify(writer, times(1)).writeChapter(anyString(), eq("摘要"), anyString(), anyString());
        service.generate(1L, owner);
        await("ready");
        verify(writer, times(2)).writeChapter(anyString(), eq("摘要"), anyString(), anyString());
    }

    @Test void resumesPersistedInterruptedJobsWithoutRewritingFinishedChapters() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        when(writer.writeChapter(anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            if ("第三章 需求分析".equals(call.getArgument(1)) && !interrupted.getAndSet(true)) throw new IllegalStateException("interrupted");
            return call.getArgument(1) + "的正文";
        });
        service.generate(1L, owner);
        await("failed");
        AppThesisMapper mapper = (AppThesisMapper) ReflectionTestUtils.getField(service, "thesisMapper");
        var persisted = mapper.selectOneById(1L);
        persisted.setStatus("generating");
        mapper.update(persisted);
        service.resumeInterruptedJobs();
        await("ready");
        verify(writer, times(1)).writeChapter(anyString(), eq("摘要"), anyString(), anyString());
        assertEquals("version-1", service.getThesis(1L, owner).getSourceVersion());
    }

    @Test void manualGenerationDoesNotSilentlyEnableAutomaticModelCalls() throws Exception {
        app.setGenerateThesis(0);
        apps.update(app);
        service.generate(1L, owner);
        await("ready");
        assertEquals(0, apps.selectOneById(1L).getGenerateThesis());
        service.sourceChanging(app);
        service.generationSucceeded(1L);
        assertEquals("stale", service.getThesis(1L, owner).getStatus());
        verify(writer, times(1)).writeChapter(anyString(), eq("摘要"), anyString(), anyString());
    }

    @Test void diagramFailureKeepsFinishedChaptersAndRetryContinuesAtTheFailedChapter() throws Exception {
        AtomicBoolean failed = new AtomicBoolean();
        when(diagrams.renderChapter(anyLong(), anyString(), anyInt(), anyString())).thenAnswer(call -> {
            if (Integer.valueOf(6).equals(call.getArgument(2)) && !failed.getAndSet(true))
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "invalid diagram layout");
            return call.getArgument(3);
        });
        service.generate(1L, owner);
        await("failed");
        var failedThesis = service.getThesis(1L, owner);
        assertEquals("ready", failedThesis.getChapters().get(4).getStatus());
        assertEquals("failed", failedThesis.getChapters().get(5).getStatus());
        service.generate(1L, owner);
        await("ready");
        verify(writer, times(1)).writeChapter(anyString(), eq("摘要"), anyString(), anyString());
        verify(writer, times(2)).writeChapter(anyString(), eq("第四章 系统设计"), anyString(), anyString());
    }

    @Test void invalidEditedFigureDoesNotReplaceTheSavedChapter() throws Exception {
        service.generate(1L, owner);
        await("ready");
        var thesis = service.getThesis(1L, owner);
        var input = new ThesisChapterUpdateRequest();
        input.setRevision(thesis.getRevision());
        input.setOriginalContent(thesis.getChapters().get(0).getContent());
        input.setContent("```diagram-design\ninvalid\n```");
        doThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "invalid SVG")).when(diagrams).validateChapter(1L, input.getContent());
        assertThrows(BusinessException.class, () -> service.saveChapter(1L, 1, input, owner));
        assertEquals(input.getOriginalContent(), service.getThesis(1L, owner).getChapters().get(0).getContent());
    }

    private void await(String status) throws Exception {
        for (int i = 0; i < 300; i++) {
            if (status.equals(service.getThesis(1L, owner).getStatus())) return;
            Thread.sleep(10);
        }
        fail("任务未达到预期状态：" + status + "，实际：" + service.getThesis(1L, owner).getStatus());
    }
}
