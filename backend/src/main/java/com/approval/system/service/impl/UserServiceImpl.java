package com.approval.system.service.impl;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.common.enums.UserStatusEnum;
import com.approval.system.dto.UserUpdateRequest;
import com.approval.system.entity.User;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User register(String username, String phone, String password, String email, String realName) {
        // 检查用户名是否已存在
        User existingUser = findByUsername(username);
        if (existingUser != null) {
            throw BusinessException.conflict("用户名已存在");
        }

        // 检查手机号是否已存在
        User existingPhone = findByPhone(phone);
        if (existingPhone != null) {
            throw BusinessException.conflict("手机号已被注册");
        }

        User user = User.builder()
                .username(username)
                .phone(phone)
                .email(email)
                .password(passwordEncoder.encode(password))
                .realName(realName)
                .status(UserStatusEnum.ENABLED.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        this.save(user);
        return user;
    }

    @Override
    public User login(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw BusinessException.badRequest("密码错误");
        }

        // 与 JWT 过滤器保持一致：只有启用状态（1）的账号可以登录
        if (!UserStatusEnum.ENABLED.is(user.getStatus())) {
            throw BusinessException.badRequest("用户已被禁用");
        }

        return user;
    }

    @Override
    public User findByUsername(String username) {
        return this.baseMapper.selectByUsername(username);
    }

    @Override
    public User getUserById(Long id) {
        return this.getById(id);
    }

    @Override
    public User findByPhone(String phone) {
        return this.baseMapper.selectByPhone(phone);
    }

    @Override
    public User getUserByPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone);
        return this.getOne(wrapper);
    }

    @Override
    public User getUserByEmail(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        return this.getOne(wrapper);
    }

    @Override
    public boolean updateUserInfo(Long id, String realName, String email, String avatar) {
        User user = this.getById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        user.setRealName(realName);
        user.setEmail(email);
        user.setAvatar(avatar);
        user.setUpdatedAt(LocalDateTime.now());

        return this.updateById(user);
    }

    @Override
    public boolean updateUserProfile(Long id, UserUpdateRequest request) {
        User user = this.getById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        // 检查手机号是否被其他用户使用
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            User existingPhone = findByPhone(request.getPhone());
            if (existingPhone != null && !existingPhone.getId().equals(id)) {
                throw BusinessException.conflict("手机号已被其他用户使用");
            }
            user.setPhone(request.getPhone());
        }

        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        user.setUpdatedAt(LocalDateTime.now());
        return this.updateById(user);
    }

    @Override
    public boolean changePassword(Long id, String oldPassword, String newPassword) {
        User user = this.getById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw BusinessException.badRequest("原密码错误");
        }

        if (newPassword.length() < 6) {
            throw BusinessException.badRequest("新密码长度至少6位");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        return this.updateById(user);
    }

    @Override
    public boolean updateAvatar(Long id, String avatarUrl) {
        User user = this.getById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        user.setAvatar(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        return this.updateById(user);
    }

    @Override
    public Page<User> getAllUsers(Integer pageNum, Integer pageSize) {
        Page<User> page = new Page<>(pageNum, pageSize);
        return this.page(page);
    }

    @Override
    public boolean verifyPassword(Long id, String password) {
        User user = this.getById(id);
        if (user == null) {
            return false;
        }

        return passwordEncoder.matches(password, user.getPassword());
    }

    @Override
    public boolean updateEmail(Long id, String email) {
        User user = this.getById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        // 检查邮箱是否已被使用
        User existingUser = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .ne(User::getId, id));

        if (existingUser != null) {
            throw BusinessException.conflict("该邮箱已被使用");
        }

        user.setEmail(email);
        user.setUpdatedAt(LocalDateTime.now());
        return this.updateById(user);
    }
}
