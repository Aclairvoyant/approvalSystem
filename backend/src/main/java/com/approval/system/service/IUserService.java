package com.approval.system.service;

import com.approval.system.dto.UserUpdateRequest;
import com.approval.system.entity.User;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IUserService extends IService<User> {

    /**
     * 用户注册
     */
    User register(String username, String phone, String password, String email, String realName);

    /**
     * 用户登录
     */
    User login(String username, String password);

    /**
     * 根据用户名查找用户
     */
    User findByUsername(String username);

    /**
     * 根据ID获取用户信息
     */
    User getUserById(Long id);

    /**
     * 根据手机号查找用户
     */
    User findByPhone(String phone);

    /**
     * 更新用户信息
     */
    boolean updateUserInfo(Long id, String realName, String email, String avatar);

    /**
     * 更新用户资料
     */
    boolean updateUserProfile(Long id, UserUpdateRequest request);

    /**
     * 修改密码
     */
    boolean changePassword(Long id, String oldPassword, String newPassword);

    /**
     * 更新头像
     */
    boolean updateAvatar(Long id, String avatarUrl);

    /**
     * 验证密码
     */
    boolean verifyPassword(Long id, String password);

    /**
     * 更新邮箱
     */
    boolean updateEmail(Long id, String email);

    /**
     * 获取所有用户（管理员用）
     */
    Page<User> getAllUsers(Integer pageNum, Integer pageSize);
}
