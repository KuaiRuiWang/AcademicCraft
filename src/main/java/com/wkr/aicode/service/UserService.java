package com.wkr.aicode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wkr.aicode.model.dto.user.UserQueryRequest;
import com.wkr.aicode.model.entity.User;
import com.wkr.aicode.model.vo.user.LoginUserVO;
import com.wkr.aicode.model.vo.user.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author <a>百夜</a>
 */
public interface UserService extends IService<User> {
    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户登录
     *
     * @param userAccount   账号
     * @param userPassword  密码
     * @param request
     * @return 登录成功返回登录信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户注册
     *
     * @param userAccount   账号
     * @param userPassword  密码
     * @param checkPassword 密码校验
     * @return
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    UserVO getUserVO(User user);

    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取加密密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

}
