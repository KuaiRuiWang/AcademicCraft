package com.wkr.aicode.admin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminPermission {
    SUPER_ADMIN("管理员与角色"),
    USER_READ("查看用户"),
    USER_WRITE("管理用户"),
    APP_READ("查看应用"),
    APP_WRITE("管理应用"),
    CHAT_READ("查看对话"),
    TEMPLATE_READ("查看模板"),
    TEMPLATE_WRITE("管理模板"),
    CATEGORY_READ("查看分类"),
    CATEGORY_WRITE("管理分类"),
    MODEL_READ("查看模型"),
    MODEL_WRITE("管理模型"),
    BINDING_READ("查看步骤配置"),
    BINDING_WRITE("配置步骤模型");

    private final String label;
}
