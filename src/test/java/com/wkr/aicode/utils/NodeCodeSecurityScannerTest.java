package com.wkr.aicode.utils;

import cn.hutool.core.io.FileUtil;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.utils.NodeCodeSecurityScanner.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码静态扫描器单元测试：高危模式拦截、合法生成代码不误报
 *
 * @author <a>百夜</a>
 */
class NodeCodeSecurityScannerTest {

    @TempDir
    Path tempDir;

    private void write(String relativePath, String content) {
        FileUtil.writeUtf8String(content, tempDir.resolve(relativePath).toFile());
    }

    private File srcDir() {
        return tempDir.resolve("src").toFile();
    }

    @Test
    void rejectsProjectUsingChildProcess() {
        write("src/service/runner.js", "const { exec } = require('child_process');\n");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> NodeCodeSecurityScanner.assertSafe(srcDir()));
        assertTrue(exception.getMessage().contains("child_process"));
    }

    @Test
    void flagsDynamicCodeExecutionAndFileOps() {
        write("src/a.js", "eval('1+1');\n"
                + "new Function('return 1');\n"
                + "fs.unlinkSync('/tmp/x');\n"
                + "const p = '/etc/passwd';\n");
        List<Violation> violations = NodeCodeSecurityScanner.scan(srcDir());
        assertEquals(4, violations.size());
    }

    @Test
    void flagsWholeEnvDumpButNotPropertyReads() {
        write("src/leak.js", "console.log(process.env);\n");
        assertEquals(1, NodeCodeSecurityScanner.scan(srcDir()).size());

        FileUtil.clean(srcDir());
        write("src/database.js", "const { DB_HOST } = process.env;\n"
                + "const name = process.env.DB_NAME || 'app_db';\n"
                + "const pwd = process.env['DB_PASSWORD'];\n");
        assertTrue(NodeCodeSecurityScanner.scan(srcDir()).isEmpty());
    }

    @Test
    void allowsNormalGeneratedProject() {
        write("src/config/database.js", "const sequelize = new Sequelize(\n"
                + "  process.env.DB_NAME || 'app_db',\n"
                + "  process.env.DB_USER || 'appuser',\n"
                + "  process.env.DB_PASSWORD || ''\n"
                + ");\n");
        write("src/app.js", "const express = require('express');\n"
                + "app.get('/api/health', (req, res) => res.json({ code: 0, data: 'ok' }));\n");
        write("src/db/init.js", "async function initDatabase() {\n"
                + "  await sequelize.sync();\n"
                + "}\n");
        assertDoesNotThrow(() -> NodeCodeSecurityScanner.assertSafe(srcDir()));
    }

    @Test
    void ignoresNodeModulesAndNonJsFiles() {
        write("src/node_modules/pkg/index.js", "require('child_process');\n");
        write("src/readme.md", "child_process\n");
        assertTrue(NodeCodeSecurityScanner.scan(srcDir()).isEmpty());
    }

    @Test
    void treatsMissingDirAsClean() {
        File missing = new File(tempDir.toFile(), "not-exists");
        assertTrue(NodeCodeSecurityScanner.scan(missing).isEmpty());
        assertDoesNotThrow(() -> NodeCodeSecurityScanner.assertSafe(missing));
    }
}
