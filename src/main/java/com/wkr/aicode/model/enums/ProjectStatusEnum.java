package com.wkr.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 项目状态枚举
 *
 * @author <a>百夜</a>
 */
@Getter
public enum ProjectStatusEnum {

    DRAFT("草稿", 0),
    PUBLISHED("已发布", 1),
    TAKEN_DOWN("已下架", 2);

    private final String text;
    private final int value;

    ProjectStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static ProjectStatusEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ProjectStatusEnum anEnum : ProjectStatusEnum.values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }
}
