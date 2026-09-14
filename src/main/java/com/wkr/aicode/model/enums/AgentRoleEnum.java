package com.wkr.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 智能体角色枚举（全栈代码生成工作流）
 */
@Getter
public enum AgentRoleEnum {

    ARCHITECT("架构师", "architect"),
    FRONTEND("前端专家", "frontend"),
    BACKEND("后端专家", "backend");


    private final String text;
    private final String value;

    AgentRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static AgentRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (AgentRoleEnum anEnum : AgentRoleEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
