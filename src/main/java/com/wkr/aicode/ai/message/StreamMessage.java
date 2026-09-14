package com.wkr.aicode.ai.message;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式消息响应基类
 */
@Data
@NoArgsConstructor
public class StreamMessage {
    private String type;

    /**
     * 产出该消息的智能体角色（全栈模式下为 architect/frontend/backend，其余模式为空）
     */
    private String agentRole;

    public StreamMessage(String type) {
        this.type = type;
    }
}
