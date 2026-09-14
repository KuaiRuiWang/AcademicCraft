package com.wkr.aicode.langGrapgh4J.node;

import com.wkr.aicode.ai.model.ApiContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 修改路由节点测试：从已生成产物加载 API 契约（分类逻辑依赖真实模型，在烟测中验证）
 *
 * @author <a>百夜</a>
 */
class ModifyRouterNodeTest {

    @TempDir
    Path projectDir;

    @Test
    void loadContractParsesExistingApiContractJson() throws Exception {
        Files.writeString(projectDir.resolve("api-contract.json"), """
                {
                  "models": [{"table": "posts", "fields": [{"name": "id", "type": "int", "pk": true}]}],
                  "apis": [{"method": "GET", "path": "/api/health", "response": "object"}],
                  "responseFormat": {"code": 0, "data": "object", "message": "string"}
                }
                """, StandardCharsets.UTF_8);
        ApiContract contract = ModifyRouterNode.loadContract(projectDir.toString());
        assertNotNull(contract, "契约文件存在且合法时应成功解析");
        assertEquals(1, contract.getModels().size());
        assertEquals("posts", contract.getModels().get(0).getTable());
        assertEquals("/api/health", contract.getApis().get(0).getPath());
    }

    @Test
    void loadContractReturnsNullWhenFileMissing() {
        assertNull(ModifyRouterNode.loadContract(projectDir.toString()));
    }

    @Test
    void loadContractReturnsNullForBlankFile() throws Exception {
        Files.writeString(projectDir.resolve("api-contract.json"), "  \n", StandardCharsets.UTF_8);
        assertNull(ModifyRouterNode.loadContract(projectDir.toString()));
    }

    @Test
    void loadContractReturnsNullForMalformedJson() throws Exception {
        Files.writeString(projectDir.resolve("api-contract.json"), "not a json file",
                StandardCharsets.UTF_8);
        assertNull(ModifyRouterNode.loadContract(projectDir.toString()));
    }

    @Test
    void loadContractRejectsDirectoryNamedAsContract() throws Exception {
        Files.createDirectory(projectDir.resolve("api-contract.json"));
        assertNull(ModifyRouterNode.loadContract(projectDir.toString()));
    }
}
