package com.wkr.aicode.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import com.wkr.aicode.exception.ThrowUtils;
import com.wkr.aicode.mapper.AppMapper;
import com.wkr.aicode.model.entity.App;
import com.wkr.aicode.service.PortManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全栈应用宿主端口分配器实现
 *
 * @author <a>百夜</a>
 */
@Slf4j
@Service
public class PortManagerImpl implements PortManager {

    /**
     * 端口段（避开常用服务与既有服务端口）
     */
    public static final int PORT_RANGE_START = 3100;
    public static final int PORT_RANGE_END = 3999;

    @Resource
    private AppMapper appMapper;

    @Override
    public synchronized int allocatePort(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (app.getNodePort() != null) {
            // 已有分配（可能是正在运行的旧容器占用）→ 直接复用，避免重复部署时换端口
            return app.getNodePort();
        }
        Set<Integer> allocatedPorts = queryAllocatedPorts(appId);
        for (int port = PORT_RANGE_START; port <= PORT_RANGE_END; port++) {
            if (allocatedPorts.contains(port) || !isPortFree(port)) {
                continue;
            }
            persistPort(appId, port);
            log.info("为应用 {} 分配宿主端口 {}", appId, port);
            return port;
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "端口段 " + PORT_RANGE_START + "-" + PORT_RANGE_END + " 已耗尽，无可用端口");
    }

    @Override
    public void releasePort(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // UpdateChain 才能显式写入 null（实体 update 默认忽略 null 字段）
        boolean updated = UpdateChain.of(appMapper)
                .set("nodePort", null)
                .where("id = ?", appId)
                .update();
        log.info("已释放应用 {} 的宿主端口（更新成功={}）", appId, updated);
    }

    /**
     * 查询已被其他应用占用的端口（数据库侧校验）
     */
    private Set<Integer> queryAllocatedPorts(Long excludeAppId) {
        List<App> apps = appMapper.selectListByQuery(
                QueryWrapper.create().where("nodePort IS NOT NULL"));
        return apps.stream()
                .filter(app -> app.getNodePort() != null && !app.getId().equals(excludeAppId))
                .map(App::getNodePort)
                .collect(Collectors.toSet());
    }

    /**
     * 端口是否空闲（socket 侧校验）
     */
    private boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void persistPort(Long appId, Integer port) {
        App update = new App();
        update.setId(appId);
        update.setNodePort(port);
        appMapper.update(update);
    }
}
