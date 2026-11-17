package com.wkr.aicode.ai.tool;

import com.wkr.aicode.ai.message.ToolResult;
import com.wkr.aicode.model.enums.AgentRoleEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @TempDir
    Path root;

    @Test
    void reportsWriteReadAndFailedReplacementTruthfully() throws Exception {
        AgentWorkspace workspace = new AgentWorkspace(root.toString(), AgentRoleEnum.FRONTEND);
        FileWriteTool writer = new FileWriteTool();
        FileReadTool reader = new FileReadTool();
        FileModifyTool modifier = new FileModifyTool();
        writer.setWorkspace(workspace);
        reader.setWorkspace(workspace);
        modifier.setWorkspace(workspace);
        String path = "frontend/task.ts";
        assertEquals("success", ToolResult.parse(writer.writeFile(path, "const task = 1", 1L)).status());
        ToolResult read = ToolResult.parse(reader.readFile(path, 1L));
        assertEquals("success", read.status());
        assertEquals("const task = 1", read.content());
        assertEquals("error", ToolResult.parse(modifier.modifyFile(path, "missing", "new", 1L)).status());
        assertEquals("const task = 1", Files.readString(root.resolve(path)));
        assertEquals("unchanged", ToolResult.parse(modifier.modifyFile(path, "task", "task", 1L)).status());
        assertEquals("error", ToolResult.parse(reader.readFile("frontend/missing.ts", 1L)).status());
        assertEquals("error", ToolResult.parse(writer.writeFile("backend/app.js", "hack", 1L)).status());
    }

    @Test
    void returnsActualDirectoryContentsAndNoChangeForMissingDeletion() {
        AgentWorkspace workspace = new AgentWorkspace(root.toString(), AgentRoleEnum.FRONTEND);
        FileWriteTool writer = new FileWriteTool();
        FileDirReadTool directories = new FileDirReadTool();
        FileDeleteTool deletion = new FileDeleteTool();
        writer.setWorkspace(workspace);
        directories.setWorkspace(workspace);
        deletion.setWorkspace(workspace);
        writer.writeFile("frontend/a.txt", "a", 1L);
        writer.writeFile("frontend/b.txt", "b", 1L);
        ToolResult read = ToolResult.parse(directories.readDir("frontend", 1L));
        assertEquals("success", read.status());
        assertTrue(read.content().contains("a.txt\n"));
        assertTrue(read.content().contains("b.txt\n"));
        assertEquals("success", ToolResult.parse(deletion.deleteFile("frontend/a.txt", 1L)).status());
        assertEquals("unchanged", ToolResult.parse(deletion.deleteFile("frontend/a.txt", 1L)).status());
    }
}
