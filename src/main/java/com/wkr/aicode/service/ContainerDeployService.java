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

    /**
     * 停止应用容器（docker compose down，保留数据卷；同步移除反代配置）
     *
     * @param app 应用（需已校验归属，且已部署）
     */
    void stop(App app);

    /**
     * 重启应用容器（docker compose up -d，复用镜像与数据卷，健康检查通过后恢复反代配置）
     *
     * @param app 应用（需已校验归属，且已部署）
     */
    void restart(App app);

    /**
     * 删除应用容器：容器 + 数据卷 + 应用镜像 + 反代配置 + 宿主端口全部清理（deployKey 保留，重新部署复用同一访问地址）
     *
     * @param app 应用（需已校验归属，且已部署）
     */
    void remove(App app);
}
