package com.wkr.aicode.service;

import com.wkr.aicode.model.entity.App;

/**
 * 全栈应用容器部署器：前端构建 → 运行时配置生成 → docker compose 启动 → 健康检查（失败回滚）
 * 每个应用独立 compose 工程（app 容器 + 独立 MySQL 容器），端口复用 app.nodePort
 *
 * @author <a>百夜</a>
 */
public interface ContainerDeployService {

    /**
     * 部署全栈应用（幂等：重复部署 = 重建镜像 + 复用端口与数据卷）
     *
     * @param app       应用（需已校验归属）
     * @param deployKey 部署标识
     * @return 应用访问地址 /apps/{deployKey}/
     */
    String deploy(App app, String deployKey);
}
