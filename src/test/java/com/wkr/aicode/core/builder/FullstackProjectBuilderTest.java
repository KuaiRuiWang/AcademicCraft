package com.wkr.aicode.core.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全栈项目构建器单元测试：产物复制与前置校验
 * （npm 构建、镜像构建依赖真实环境，另行冒烟/服务器验证）
 *
 * @author <a>百夜</a>
 */
class FullstackProjectBuilderTest {

    private FullstackProjectBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new FullstackProjectBuilder();
        ReflectionTestUtils.setField(builder, "vueProjectBuilder", new VueProjectBuilder());
    }

    @Test
    void copiesDistIntoBackendPublicAndClearsStaleFiles(@TempDir Path projectDir) throws Exception {
        Path dist = Files.createDirectories(projectDir.resolve("frontend/dist"));
        Files.writeString(dist.resolve("index.html"), "<html>ok</html>");
        Path assets = Files.createDirectories(dist.resolve("assets"));
        Files.writeString(assets.resolve("app.js"), "console.log(1)");
        Path publicDir = Files.createDirectories(projectDir.resolve("backend/public"));
        Files.writeString(publicDir.resolve("stale.html"), "old");

        assertTrue(builder.copyDistToPublic(projectDir.toFile()));

        assertTrue(Files.isRegularFile(publicDir.resolve("index.html")));
        assertTrue(Files.isRegularFile(publicDir.resolve("assets/app.js")));
        assertFalse(Files.exists(publicDir.resolve("stale.html")), "过期文件应被清理");
    }

    @Test
    void failsWhenDistMissing(@TempDir Path projectDir) {
        assertFalse(builder.copyDistToPublic(projectDir.toFile()));
    }

    @Test
    void failsWhenFrontendDirOrPackageJsonMissing(@TempDir Path projectDir) {
        assertFalse(builder.buildFrontend(projectDir.toFile()), "frontend 目录不存在时应失败");
        try {
            Files.createDirectories(projectDir.resolve("frontend"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertFalse(builder.buildFrontend(projectDir.toFile()), "frontend 缺少 package.json 时应失败");
    }

    @Test
    void failsWhenDockerfileMissing(@TempDir Path projectDir) {
        assertFalse(builder.buildImage(projectDir.toFile(), "fullstack-test"));
    }
}
