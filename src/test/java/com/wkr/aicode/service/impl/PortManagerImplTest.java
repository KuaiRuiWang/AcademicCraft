package com.wkr.aicode.service.impl;

import com.mybatisflex.core.update.UpdateWrapper;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.mapper.AppMapper;
import com.wkr.aicode.model.entity.App;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端口分配器单元测试：端口复用、段内分配、DB 占用排除与释放
 * 使用 JDK 动态代理桩而非 Mockito：UpdateChain 需要能解析出实体类的 mapper（会剥离 Proxy，Mockito 子类桩无法解析）
 *
 * @author <a>百夜</a>
 */
class PortManagerImplTest {

    private StubMapper stub;
    private PortManagerImpl portManager;

    @BeforeEach
    void setUp() {
        stub = new StubMapper();
        AppMapper appMapper = (AppMapper) Proxy.newProxyInstance(
                AppMapper.class.getClassLoader(),
                new Class<?>[]{AppMapper.class},
                stub);
        portManager = new PortManagerImpl();
        ReflectionTestUtils.setField(portManager, "appMapper", appMapper);
    }

    @Test
    void reusesAssignedPortForIdempotentRedeploy() {
        stub.app = App.builder().id(1L).nodePort(3200).build();
        assertEquals(3200, portManager.allocatePort(1L));
        assertEquals(0, stub.updateCount, "复用已有端口不应再次写库");
    }

    @Test
    void allocatesFreePortAndPersistsIt() {
        stub.app = App.builder().id(2L).build();
        int port = portManager.allocatePort(2L);
        assertTrue(port >= PortManagerImpl.PORT_RANGE_START && port <= PortManagerImpl.PORT_RANGE_END);
        assertEquals(1, stub.updateCount);
    }

    @Test
    void skipsPortsAlreadyTakenByOtherApps() {
        stub.app = App.builder().id(3L).build();
        stub.appsWithPort = List.of(App.builder().id(4L).nodePort(PortManagerImpl.PORT_RANGE_START).build());
        int port = portManager.allocatePort(3L);
        assertTrue(port > PortManagerImpl.PORT_RANGE_START, "应跳过已被其他应用占用的段首端口");
    }

    @Test
    void failsWhenAppMissing() {
        assertThrows(BusinessException.class, () -> portManager.allocatePort(9L));
    }

    @Test
    void releaseClearsPortColumnViaUpdateWrapper() {
        portManager.releasePort(5L);
        assertNotNull(stub.updates, "应通过 updateByQuery 执行释放");
        assertTrue(stub.updates.containsKey("nodePort"), "SET 子句应包含 nodePort");
        assertNull(stub.updates.get("nodePort"), "nodePort 应被置空");
    }

    /**
     * 手写桩：只实现 PortManager 用到的 mapper 方法
     */
    private static class StubMapper implements InvocationHandler {

        App app;
        List<App> appsWithPort = Collections.emptyList();
        int updateCount;
        Map<?, ?> updates;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "selectOneById":
                    return app;
                case "selectListByQuery":
                    return appsWithPort;
                case "update":
                    updateCount++;
                    return 1;
                case "updateByQuery":
                    updates = ((UpdateWrapper<?>) args[0]).getUpdates();
                    return 1;
                case "toString":
                    return "StubAppMapper";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return null;
            }
        }
    }
}
