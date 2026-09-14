package com.wkr.aicode.service.impl;

import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动态 Nginx 配置服务单元测试：模板渲染、文件写入/删除、非法入参、
 * reload 失败清理（docker exec 依赖真实服务器，另行验证）
 *
 * @author <a>百夜</a>
 */
class NginxConfigServiceImplTest {

    @TempDir
    Path tempDir;

    private NginxConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NginxConfigServiceImpl() {
            @Override
            boolean reloadNginx() {
                return true;
            }
        };
        ReflectionTestUtils.setField(service, "confDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "containerName", "test-nginx");
        ReflectionTestUtils.setField(service, "upstreamHost", "127.0.0.1");
    }

    @Test
    void renderLocationContainsProxyPassAndWebSocketHeaders() {
        String location = service.renderLocation("Abc123", 3210);
        assertTrue(location.startsWith("location ^~ /apps/Abc123/ {"));
        assertTrue(location.contains("proxy_pass http://127.0.0.1:3210/;"));
        assertTrue(location.contains("proxy_set_header Upgrade $http_upgrade;"));
        assertTrue(location.contains("proxy_set_header Connection $http_connection;"));
        assertTrue(location.contains("proxy_http_version 1.1;"));
    }

    @Test
    void applyConfigWritesConfFile() {
        service.applyConfig("Abc123", 3210);
        File confFile = new File(tempDir.toFile(), "apps-Abc123.conf");
        assertTrue(confFile.isFile());
        String content = cn.hutool.core.io.FileUtil.readUtf8String(confFile);
        assertTrue(content.contains("location ^~ /apps/Abc123/ {"));
        assertTrue(content.contains("127.0.0.1:3210"));
    }

    @Test
    void applyConfigRejectsIllegalDeployKey() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyConfig("../etc/passwd", 3210));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void applyConfigFailsWhenConfDirMissing() {
        ReflectionTestUtils.setField(service, "confDir", tempDir.resolve("not-exists").toString());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyConfig("Abc123", 3210));
        assertTrue(exception.getMessage().contains("未配置 Nginx 动态目录"));
    }

    @Test
    void applyConfigCleansUpWhenReloadFails() {
        NginxConfigServiceImpl failing = new NginxConfigServiceImpl() {
            @Override
            boolean reloadNginx() {
                return false;
            }
        };
        ReflectionTestUtils.setField(failing, "confDir", tempDir.toString());
        ReflectionTestUtils.setField(failing, "containerName", "test-nginx");
        ReflectionTestUtils.setField(failing, "upstreamHost", "127.0.0.1");
        assertThrows(BusinessException.class, () -> failing.applyConfig("Abc123", 3210));
        assertFalse(new File(tempDir.toFile(), "apps-Abc123.conf").exists(),
                "reload 失败后应清理本次写入的配置");
    }

    @Test
    void removeConfigDeletesFileAndIsIdempotent() {
        service.applyConfig("Abc123", 3210);
        File confFile = new File(tempDir.toFile(), "apps-Abc123.conf");
        assertTrue(confFile.isFile());
        service.removeConfig("Abc123");
        assertFalse(confFile.exists());
        // 再次删除不应抛错
        service.removeConfig("Abc123");
    }

    @Test
    void isAvailableReflectsConfDirExistence() {
        assertTrue(service.isAvailable());
        ReflectionTestUtils.setField(service, "confDir", tempDir.resolve("not-exists").toString());
        assertFalse(service.isAvailable());
    }
}
