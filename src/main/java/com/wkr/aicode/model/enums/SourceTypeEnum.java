package com.wkr.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 源码交付方式枚举
 *
 * @author <a>百夜</a>
 */
@Getter
public enum SourceTypeEnum {

    ZIP_DOWNLOAD("ZIP下载", 1),
    CLOUD_LINK("网盘链接", 2),
    BOTH("两者都有", 3);

    private final String text;
    private final int value;

    SourceTypeEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static SourceTypeEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (SourceTypeEnum anEnum : SourceTypeEnum.values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }
}
