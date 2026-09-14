package com.wkr.aicode.service;

/**
 * 动态 Nginx 配置：为全栈应用写入 /apps/{deployKey}/ 反代 location 并 reload。
 * 内网机 openresty 容器（host 网络）通过 include 拾取 confDir/*.conf；
 * 公网入口机全量透传 /apps/，无需逐应用配置。
 *
 * @author <a>百夜</a>
 */
public interface NginxConfigService {

    /**
     * 当前环境是否具备动态配置能力（confDir 存在）
     */
    boolean isAvailable();

    /**
     * 写入 /apps/{deployKey}/ 反代配置并 reload（幂等：已存在则覆盖）
     */
    void applyConfig(String deployKey, int port);

    /**
     * 删除反代配置并 reload（供应用删除/部署回滚调用，幂等）
     */
    void removeConfig(String deployKey);
}
