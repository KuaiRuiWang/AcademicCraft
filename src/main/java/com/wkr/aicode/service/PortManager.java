package com.wkr.aicode.service;

/**
 * 全栈应用宿主端口分配器：段内唯一分配 + 落库持久化（app.nodePort），重启后复用
 *
 * @author <a>百夜</a>
 */
public interface PortManager {

    /**
     * 为应用分配唯一宿主端口：已有分配则复用（保证重复部署幂等），否则段内探测空闲端口
     */
    int allocatePort(Long appId);

    /**
     * 释放端口（删除应用时调用，释放后可被再次分配）
     */
    void releasePort(Long appId);
}
