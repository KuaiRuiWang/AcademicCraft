package com.wkr.aicode.service.impl;

import com.sun.net.httpserver.HttpServer;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.model.entity.App;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 容器部署器单元测试：模板渲染、前置校验快速失败、健康检查轮询
 * （compose 启动与回滚依赖真实 Docker，另行服务器验证）
 *
 * @author <a>百夜</a>
 */
class ContainerDeployServiceImplTest {

    private final ContainerDeployServiceImpl service = new ContainerDeployServiceImpl();

    @Test
    void renderComposeReplacesTokensAndKeepsEnvRefs() {
        String compose = service.renderCompose("fullstack_abc123", 3123);
        assertTrue(compose.contains("container_name: fullstack_abc123"));
        assertTrue(compose.contains("container_name: fullstack_abc123_mysql"));
        assertTrue(compose.contains("\"3123:3000\""), "宿主端口应注入到端口映射");
        assertTrue(compose.contains("${DB_PASSWORD}"), "compose 应保留 .env 变量引用");
        assertFalse(compose.contains("__"), "占位符应全部被替换");
    }

    @Test
    void deployFailsFastWhenCodeMissing() {
        App app = App.builder().id(987654321L).build();
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deploy(app, "abc123"));
        assertTrue(exception.getMessage().contains("应用代码不存在"));
    }

    @Test
    void lifecycleOpsRejectUndeployedApp() {
        App app = App.builder().id(1L).build();
        assertTrue(assertThrows(BusinessException.class, () -> service.stop(app))
                .getMessage().contains("尚未部署"));
        assertTrue(assertThrows(BusinessException.class, () -> service.restart(app))
                .getMessage().contains("尚未部署"));
        assertTrue(assertThrows(BusinessException.class, () -> service.remove(app))
                .getMessage().contains("尚未部署"));
    }

    @Test
    void waitForHealthyReturnsTrueWhenEndpointOk() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/health", exchange -> {
            byte[] body = "{\"code\":0,\"data\":\"ok\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            assertTrue(service.waitForHealthy(server.getAddress().getPort(), 5));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void waitForHealthyTimesOutWhenNothingListens() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        long start = System.currentTimeMillis();
        assertFalse(service.waitForHealthy(freePort, 2));
        assertTrue(System.currentTimeMillis() - start >= 1900, "应轮询至超时而非提前返回");
    }
}
