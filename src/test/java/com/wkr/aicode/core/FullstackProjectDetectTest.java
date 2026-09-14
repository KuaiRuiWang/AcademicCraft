package com.wkr.aicode.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已生成全栈项目检测测试：chatToGenCode 据此区分「首次生成」与「修改已有项目」
 *
 * @author <a>百夜</a>
 */
class FullstackProjectDetectTest {

    @TempDir
    Path projectDir;

    @Test
    void detectsProjectWhenAllArtifactsExist() throws Exception {
        Files.createDirectory(projectDir.resolve("frontend"));
        Files.createDirectory(projectDir.resolve("backend"));
        Files.createFile(projectDir.resolve("api-contract.json"));
        assertTrue(AiCodeGeneratorFacade.isGeneratedFullstackProject(projectDir.toString()));
    }

    @Test
    void rejectsProjectWithoutContractFile() throws Exception {
        Files.createDirectory(projectDir.resolve("frontend"));
        Files.createDirectory(projectDir.resolve("backend"));
        assertFalse(AiCodeGeneratorFacade.isGeneratedFullstackProject(projectDir.toString()));
    }

    @Test
    void rejectsProjectWithoutBackendDir() throws Exception {
        Files.createDirectory(projectDir.resolve("frontend"));
        Files.createFile(projectDir.resolve("api-contract.json"));
        assertFalse(AiCodeGeneratorFacade.isGeneratedFullstackProject(projectDir.toString()));
    }

    @Test
    void rejectsEmptyProjectDir() {
        assertFalse(AiCodeGeneratorFacade.isGeneratedFullstackProject(projectDir.toString()));
    }

    @Test
    void rejectsProjectWithFileNamedFrontend() throws Exception {
        Files.createFile(projectDir.resolve("frontend"));
        Files.createDirectory(projectDir.resolve("backend"));
        Files.createFile(projectDir.resolve("api-contract.json"));
        assertFalse(AiCodeGeneratorFacade.isGeneratedFullstackProject(projectDir.toString()));
    }
}
