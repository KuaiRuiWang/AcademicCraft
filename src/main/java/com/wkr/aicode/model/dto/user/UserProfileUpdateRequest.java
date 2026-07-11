package com.wkr.aicode.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户个人信息更新请求
 *
 * @author <a>百夜</a>
 */
@Data
public class UserProfileUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像URL
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;
}
