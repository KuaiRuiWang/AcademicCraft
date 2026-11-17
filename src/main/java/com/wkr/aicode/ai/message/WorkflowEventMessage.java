package com.wkr.aicode.ai.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流节点的真实生命周期，供流式出口转换为 AG-UI 事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkflowEventMessage extends StreamMessage {

    private String event;
    private String name;
    private String label;
    private String status;

    public WorkflowEventMessage(String event, String name, String label, String status, String agentRole) {
        super(StreamMessageTypeEnum.WORKFLOW_EVENT.getValue());
        this.event = event;
        this.name = name;
        this.label = label;
        this.status = status;
        setAgentRole(agentRole);
    }
}
