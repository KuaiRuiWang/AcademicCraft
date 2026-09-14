package com.wkr.aicode.ai.tool;

import com.wkr.aicode.model.enums.AgentRoleEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 工作区沙箱测试
 *
 * @author <a>百夜</a>
 */
class AgentWorkspaceTest {

    @TempDir
    Path projectRoot;

    @Test
    void frontend_canWriteItsOwnFiles() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.FRONTEND);

        Path resolved = workspace.resolveForWrite("frontend/src/App.vue");

        assertNotNull(resolved);
        assertTrue(resolved.startsWith(projectRoot));
    }

    @Test
    void frontend_cannotWriteBackendFiles() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.FRONTEND);

        assertNull(workspace.resolveForWrite("backend/src/app.js"));
        assertNull(workspace.resolveForWrite("api-contract.json"));
    }

    @Test
    void frontend_cannotEscapeByTraversal() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.FRONTEND);

        assertNull(workspace.resolveForWrite("../backend/x.js"));
        assertNull(workspace.resolveForWrite("frontend/../backend/x.js"));
        assertNull(workspace.resolveForWrite("./frontend/../../x.js"));
    }

    @Test
    void backend_canWriteItsOwnFilesAndRootDeployFiles() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.BACKEND);

        assertNotNull(workspace.resolveForWrite("backend/src/app.js"));
        assertNotNull(workspace.resolveForWrite("backend/package.json"));
        assertNotNull(workspace.resolveForWrite("Dockerfile"));
        assertNotNull(workspace.resolveForWrite("docker-compose.yml"));
        assertNotNull(workspace.resolveForWrite(".env.example"));
    }

    @Test
    void backend_cannotWriteFrontendFiles() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.BACKEND);

        assertNull(workspace.resolveForWrite("frontend/src/App.vue"));
        assertNull(workspace.resolveForWrite("backend/../frontend/a.vue"));
        assertNull(workspace.resolveForWrite("api-contract.json"));
    }

    @Test
    void architect_canOnlyWriteContractFile() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.ARCHITECT);

        assertNotNull(workspace.resolveForWrite("api-contract.json"));
        assertNull(workspace.resolveForWrite("frontend/a.vue"));
        assertNull(workspace.resolveForWrite("backend/x.js"));
    }

    @Test
    void absolutePathsAreRejected() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.BACKEND);

        assertNull(workspace.resolveForWrite("/etc/passwd"));
        assertNull(workspace.resolveForWrite("C:/Windows/system32/x.js"));
        assertNull(workspace.resolveForWrite("C:\\Windows\\system32\\x.js"));
    }

    @Test
    void readIsAllowedAcrossAgentsButNotOutsideRoot() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.FRONTEND);

        assertNotNull(workspace.resolveForRead("backend/src/app.js"));
        assertNotNull(workspace.resolveForRead("api-contract.json"));
        assertNull(workspace.resolveForRead("../../etc/passwd"));
    }

    @Test
    void readBlankPathReturnsProjectRoot() {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.FRONTEND);

        assertEquals(projectRoot.toAbsolutePath().normalize(), workspace.resolveForRead(null));
        assertEquals(projectRoot.toAbsolutePath().normalize(), workspace.resolveForRead(""));
    }

    @Test
    void legacyVueProjectWorkspaceHasNoRoleRestriction() {
        AgentWorkspace workspace = AgentWorkspace.vueProject(1L);

        assertNotNull(workspace.resolveForWrite("src/main.js"));
        assertNotNull(workspace.resolveForWrite("package.json"));
        assertTrue(workspace.getProjectRoot().toString().endsWith("vue_project_1"));
    }

    @Test
    void writeDeniedMessageDiffersByRole() {
        AgentWorkspace frontend = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.FRONTEND);
        AgentWorkspace backend = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.BACKEND);

        assertTrue(frontend.getWriteDeniedMessage("backend/x.js").contains("frontend/"));
        assertTrue(backend.getWriteDeniedMessage("frontend/a.vue").contains("backend/"));
    }

    @Test
    void writeToolRespectsWorkspace() throws Exception {
        AgentWorkspace workspace = new AgentWorkspace(projectRoot.toString(), AgentRoleEnum.BACKEND);
        FileWriteTool writeTool = new FileWriteTool();
        writeTool.setWorkspace(workspace);

        String denied = writeTool.writeFile("frontend/src/App.vue", "hack", 1L);
        assertTrue(denied.contains("无权限"));
        assertFalse(Files.exists(projectRoot.resolve("frontend/src/App.vue")));

        String success = writeTool.writeFile("backend/src/app.js", "const app = 1", 1L);
        assertTrue(success.contains("文件写入成功"));
        assertTrue(Files.exists(projectRoot.resolve("backend/src/app.js")));
    }

    @Test
    void legacyWriteToolWithoutWorkspaceKeepsOldBehaviour() {
        FileWriteTool writeTool = new FileWriteTool();
        writeTool.setWorkspace(null);

        AgentWorkspace workspace = writeTool.resolveWorkspace(0L);

        assertTrue(workspace.getProjectRoot().toString().endsWith("vue_project_0"));
        assertNull(workspace.getRole());
    }
}
