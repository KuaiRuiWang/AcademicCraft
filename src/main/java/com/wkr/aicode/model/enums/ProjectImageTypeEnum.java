package com.wkr.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 项目图片类型枚举
 *
 * @author <a>百夜</a>
 */
@Getter
public enum ProjectImageTypeEnum {

    SCREENSHOT("截图预览", 0),
    DETAIL_IMAGE("详情介绍图", 1);

    private final String text;
    private final int value;

    ProjectImageTypeEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static ProjectImageTypeEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ProjectImageTypeEnum anEnum : ProjectImageTypeEnum.values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }
}
