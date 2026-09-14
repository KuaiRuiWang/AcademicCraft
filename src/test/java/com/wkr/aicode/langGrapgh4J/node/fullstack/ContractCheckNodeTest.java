package com.wkr.aicode.langGrapgh4J.node.fullstack;

import com.wkr.aicode.ai.model.ApiContract;
import com.wkr.aicode.langGrapgh4J.model.QualityResult;
import com.wkr.aicode.langGrapgh4J.state.WorkflowContext;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约校验节点测试：三方比对结果、修复提示写入与回环路由上限（不依赖 Spring 与真实模型）
 *
 * @author <a>百夜</a>
 */
class ContractCheckNodeTest {

    private static final ApiContract CONTRACT = ApiContract.builder()
            .apis(List.of(
                    ApiContract.ApiEndpoint.builder().method("GET").path("/api/health").build(),
                    ApiContract.ApiEndpoint.builder().method("GET").path("/api/posts").build(),
                    ApiContract.ApiEndpoint.builder().method("GET").path("/api/posts/:id").build(),
                    ApiContract.ApiEndpoint.builder().method("POST").path("/api/posts").build()
            ))
            .build();

    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * 后端实现全部契约接口、前端只调用契约接口 → 校验通过
     */
    @Test
    void passesWhenBothSidesMatchContract(@TempDir Path projectDir) throws IOException {
        Path backendDir = projectDir.resolve("backend");
        writeFile(backendDir.resolve("src/app.js"), """
                const express = require('express');
                const routes = require('./routes');
                const app = express();
                app.get('/api/health', (req, res) => res.json({ code: 0, data: 'ok' }));
                app.use('/api', routes);
                app.listen(3000, '0.0.0.0');
                """);
        writeFile(backendDir.resolve("src/routes/index.js"), """
                const router = require('express').Router();
                const postRoutes = require('./posts');
                router.use('/posts', postRoutes);
                module.exports = router;
                """);
        writeFile(backendDir.resolve("src/routes/posts.js"), """
                const router = require('express').Router();
                router.get('/', (req, res) => res.json({ code: 0, data: [], message: 'ok' }));
                router.get('/:id', (req, res) => res.json({ code: 0, data: null, message: 'ok' }));
                router.post('/', (req, res) => res.json({ code: 0, data: null, message: 'ok' }));
                module.exports = router;
                """);
        Path frontendDir = projectDir.resolve("frontend");
        writeFile(frontendDir.resolve("src/pages/PostList.vue"), """
                <script setup>
                import request from '@/utils/request'
                const load = async () => {
                  await request('/posts')
                  await request(`/posts/${1}`)
                }
                </script>
                """);

        WorkflowContext context = newContext(projectDir);
        List<String> emitted = runCheckNode(context);

        QualityResult result = context.getContractCheckResult();
        assertTrue(result.getIsValid(), "应校验通过：" + result.getErrors());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(context.getBackendFixHints().isEmpty());
        assertTrue(context.getFrontendFixHints().isEmpty());
        assertEquals(1, context.getContractCheckRound());
        assertTrue(emitted.stream().anyMatch(message -> message.contains("契约校验通过")), emitted.toString());
    }

    /**
     * 后端缺少契约接口、前端调用契约外接口 → 分别给出正确差异，并写入对应修复提示
     */
    @Test
    void reportsBackendMissingRouteAndFrontendIllegalCall(@TempDir Path projectDir) throws IOException {
        Path backendDir = projectDir.resolve("backend");
        writeFile(backendDir.resolve("src/app.js"), """
                const express = require('express');
                const app = express();
                app.get('/api/health', (req, res) => res.json({ code: 0, data: 'ok' }));
                app.get('/api/posts', (req, res) => res.json({ code: 0, data: [], message: 'ok' }));
                app.listen(3000, '0.0.0.0');
                """);
        Path frontendDir = projectDir.resolve("frontend");
        writeFile(frontendDir.resolve("src/pages/PostEdit.vue"), """
                <script setup>
                import request from '@/utils/request'
                const load = async () => {
                  await request('/posts')
                  await request('/tags')
                }
                </script>
                """);

        WorkflowContext context = newContext(projectDir);
        runCheckNode(context);

        QualityResult result = context.getContractCheckResult();
        assertFalse(result.getIsValid());
        List<String> backendHints = context.getBackendFixHints();
        List<String> frontendHints = context.getFrontendFixHints();
        assertEquals(2, backendHints.size(), "缺失 GET /api/posts/:id 与 POST /api/posts：" + backendHints);
        assertTrue(backendHints.stream().anyMatch(hint -> hint.contains("GET /api/posts/*")), backendHints.toString());
        assertTrue(backendHints.stream().anyMatch(hint -> hint.contains("POST /api/posts")), backendHints.toString());
        assertEquals(1, frontendHints.size(), frontendHints.toString());
        assertTrue(frontendHints.get(0).contains("/api/tags"), frontendHints.toString());
    }

    private WorkflowContext newContext(Path projectDir) {
        return WorkflowContext.builder()
                .appId(1L)
                .originalPrompt("博客系统")
                .apiContract(CONTRACT)
                .fullstackProjectDir(projectDir.toString())
                .frontendDir(projectDir.resolve("frontend").toString())
                .backendDir(projectDir.resolve("backend").toString())
                .build();
    }

    private MessagesState<String> stateOf(WorkflowContext context) {
        return new MessagesState<>(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, context));
    }

    /**
     * 直接驱动节点（sink 在订阅时同步执行，subscribe 返回即处理完成）
     */
    private List<String> runCheckNode(WorkflowContext context) {
        List<String> emitted = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        Flux.<String>create(sink -> {
            AgentStreamPipe pipe = new AgentStreamPipe(sink);
            ContractCheckNode.create(pipe).apply(stateOf(context)).join();
            sink.complete();
        }).subscribe(emitted::add, errors::add);
        assertTrue(errors.isEmpty(), "节点执行不应抛异常：" + errors);
        return emitted;
    }
}
