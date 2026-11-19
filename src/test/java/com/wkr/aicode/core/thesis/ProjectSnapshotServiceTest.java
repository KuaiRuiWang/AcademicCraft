package com.wkr.aicode.core.thesis;

import com.wkr.aicode.model.entity.App;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProjectSnapshotServiceTest {
    @TempDir Path root;

    @Test
    void capturesVueContractAndModelsButExcludesCredentialsAndDependencies() throws Exception {
        Files.createDirectories(root.resolve("frontend/src"));
        Files.createDirectories(root.resolve("backend/node_modules/express"));
        Files.writeString(root.resolve("frontend/src/App.vue"), "<template>图书列表</template>");
        Files.writeString(root.resolve("api-contract.json"), "{\"models\":[\"books\"]}");
        Files.writeString(root.resolve(".env"), "DB_PASSWORD=never-include-this");
        Files.writeString(root.resolve("backend/node_modules/express/index.js"), "dependency-implementation");
        App app = App.builder().id(900001L).initPrompt("图书管理").codeGenType("fullstack_vue_nodejs").build();
        ProjectSnapshotService service = new ProjectSnapshotService();
        var first = service.capture(app, root);
        assertTrue(first.context().contains("App.vue"));
        assertTrue(first.context().contains("api-contract.json"));
        assertFalse(first.context().contains("never-include-this"));
        assertFalse(first.context().contains("dependency-implementation"));
        assertEquals(first.version(), service.capture(app, root).version());
        Files.writeString(root.resolve("frontend/src/App.vue"), "<template>图书详情</template>");
        assertNotEquals(first.version(), service.capture(app, root).version());
        assertTrue(first.context().contains("图书列表"), "冻结资料不随后续代码修改而变化");
    }
}
