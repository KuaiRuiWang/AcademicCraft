package com.wkr.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 全栈项目修改意图范围枚举（T-17 修改对话路由）
 * 由轻量模型对用户的修改需求分类，决定交给哪些角色的 Agent 处理
 */
@Getter
public enum ModifyScopeEnum {

    FRONTEND("仅前端", "frontend"),
    BACKEND("仅后端", "backend"),
    BOTH("前后端", "both");

    private final String text;
    private final String value;

    ModifyScopeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static ModifyScopeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ModifyScopeEnum anEnum : ModifyScopeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
