package com.wkr.aicode.core;

import com.wkr.aicode.ai.ApiContractDesignService;
import com.wkr.aicode.ai.model.ApiContract;
import com.wkr.aicode.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API 契约设计链路测试（不依赖真实模型）
 *
 * @author <a>百夜</a>
 */
class ApiContractDesignFacadeTest {

    /**
     * 合法契约：2 张表、含健康检查接口
     */
    private static final String VALID_CONTRACT = """
            {
              "models": [
                { "table": "posts", "fields": [
                  { "name": "id", "type": "int", "pk": true },
                  { "name": "title", "type": "varchar(200)", "pk": false },
                  { "name": "createdAt", "type": "datetime", "pk": false }
                ]},
                { "table": "comments", "fields": [
                  { "name": "id", "type": "int", "pk": true },
                  { "name": "postId", "type": "int", "pk": false },
                  { "name": "content", "type": "text", "pk": false }
                ]}
              ],
              "apis": [
                { "method": "GET", "path": "/api/health", "request": "", "response": "boolean", "description": "健康检查" },
                { "method": "GET", "path": "/api/posts", "request": "分页参数：page、pageSize", "response": "Post[]", "description": "分页查询文章列表" },
                { "method": "POST", "path": "/api/posts", "request": "PostInput", "response": "Post", "description": "创建文章" }
              ],
              "responseFormat": { "code": 0, "data": "any", "message": "string" }
            }
            """;

    /**
     * 非法契约：缺少 GET /api/health
     */
    private static final String CONTRACT_WITHOUT_HEALTH = """
            {
              "models": [
                { "table": "posts", "fields": [
                  { "name": "id", "type": "int", "pk": true },
                  { "name": "title", "type": "varchar(200)", "pk": false }
                ]}
              ],
              "apis": [
                { "method": "GET", "path": "/api/posts", "request": "", "response": "Post[]", "description": "文章列表" }
              ],
              "responseFormat": { "code": 0, "data": "any", "message": "string" }
            }
            """;

    private ApiContractDesignFacade facade;

    private StubContractService stubService;

    @BeforeEach
    void setUp() {
        facade = new ApiContractDesignFacade();
        stubService = new StubContractService();
        ReflectionTestUtils.setField(facade, "apiContractDesignService", stubService);
    }

    @Test
    void designContract_validJson_parsedAndValidated() {
        stubService.enqueue(VALID_CONTRACT);

        ApiContract contract = facade.designContract("做一个博客系统");

        assertEquals(2, contract.getModels().size());
        assertEquals("posts", contract.getModels().get(0).getTable());
        assertEquals(3, contract.getApis().size());
        assertEquals("/api/health", contract.getApis().get(0).getPath());
        assertEquals(0, contract.getResponseFormat().getCode());
        assertEquals(1, stubService.getPrompts().size());
    }

    @Test
    void designContract_markdownWrapped_stillParsed() {
        stubService.enqueue("好的，以下是对应的契约：\n```json\n" + VALID_CONTRACT + "\n```\n希望有帮助。");

        ApiContract contract = facade.designContract("做一个博客系统");

        assertEquals(2, contract.getModels().size());
    }

    @Test
    void designContract_invalidOutput_retriedWithFeedback() {
        stubService.enqueue(CONTRACT_WITHOUT_HEALTH);
        stubService.enqueue(VALID_CONTRACT);

        ApiContract contract = facade.designContract("做一个博客系统");

        assertNotNull(contract);
        assertEquals(2, stubService.getPrompts().size());
        // 重试时把上次的校验问题回传给模型
        assertTrue(stubService.getPrompts().get(1).contains("必须包含健康检查接口"));
    }

    @Test
    void designContract_modelCallFailed_retried() {
        stubService.enqueueError("连接超时");
        stubService.enqueue(VALID_CONTRACT);

        ApiContract contract = facade.designContract("做一个博客系统");

        assertNotNull(contract);
        assertEquals(2, stubService.getPrompts().size());
    }

    @Test
    void designContract_alwaysInvalid_throwsBusinessException() {
        stubService.enqueue(CONTRACT_WITHOUT_HEALTH);
        stubService.enqueue("这不是 JSON");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> facade.designContract("做一个博客系统"));

        assertTrue(exception.getMessage().contains("API 契约生成失败"));
        assertEquals(2, stubService.getPrompts().size());
    }

    @Test
    void designContract_blankRequirement_throwsBusinessException() {
        assertThrows(BusinessException.class, () -> facade.designContract("  "));
        assertTrue(stubService.getPrompts().isEmpty());
    }

    @Test
    void saveContract_writesFileToProjectDir(@TempDir Path tempDir) throws Exception {
        stubService.enqueue(VALID_CONTRACT);
        ApiContract contract = facade.designContract("做一个博客系统");

        File contractFile = facade.saveContract(contract, tempDir.toString());

        assertTrue(contractFile.exists());
        assertEquals("api-contract.json", contractFile.getName());
        String content = Files.readString(contractFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"posts\""));
        assertTrue(content.contains("/api/health"));
        assertFalse(content.isBlank());
    }

    @Test
    void designAndSaveContract_writesFile(@TempDir Path tempDir) {
        stubService.enqueue(VALID_CONTRACT);

        ApiContract contract = facade.designAndSaveContract("做一个博客系统", tempDir.toString());

        assertNotNull(contract);
        assertTrue(new File(tempDir.toFile(), "api-contract.json").exists());
    }

    /**
     * 按预设队列返回模型输出的桩服务
     */
    private static class StubContractService implements ApiContractDesignService {

        private final Deque<Object> responses = new ArrayDeque<>();

        private final List<String> prompts = new ArrayList<>();

        void enqueue(String response) {
            responses.add(response);
        }

        void enqueueError(String message) {
            responses.add(new RuntimeException(message));
        }

        List<String> getPrompts() {
            return prompts;
        }

        @Override
        public String designApiContract(String requirement) {
            prompts.add(requirement);
            Object next = responses.poll();
            if (next instanceof RuntimeException exception) {
                throw exception;
            }
            return (String) next;
        }
    }
}
