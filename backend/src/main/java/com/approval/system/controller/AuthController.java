package com.approval.system.controller;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.common.response.ApiResponse;
import com.approval.system.common.utils.JwtUtils;
import com.approval.system.common.utils.OssUtils;
import com.approval.system.dto.ChangeEmailRequest;
import com.approval.system.dto.ChangePasswordRequest;
import com.approval.system.dto.LoginRequest;
import com.approval.system.dto.LoginResponse;
import com.approval.system.dto.RegisterRequest;
import com.approval.system.dto.SendEmailCodeRequest;
import com.approval.system.dto.UserUpdateRequest;
import com.approval.system.entity.User;
import com.approval.system.service.IEmailService;
import com.approval.system.service.IUserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private IUserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private OssUtils ossUtils;

    @Autowired
    private IEmailService emailService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<User> register(@Valid @RequestBody RegisterRequest request) {
        // 填了邮箱就必须带上有效的邮箱验证码
        if (StringUtils.hasLength(request.getEmail())) {
            if (!StringUtils.hasLength(request.getEmailVerificationCode())) {
                throw BusinessException.badRequest("请提供邮箱验证码");
            }
            if (!emailService.verifyCode(request.getEmail(), request.getEmailVerificationCode())) {
                throw BusinessException.badRequest("邮箱验证码错误或已过期");
            }
        }

        User user = userService.register(request.getUsername(), request.getPhone(),
                request.getPassword(), request.getEmail(), request.getRealName());
        // 不返回密码
        user.setPassword(null);
        return ApiResponse.success("注册成功", user);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request.getUsername(), request.getPassword());

        // 生成JWT Token
        String token = jwtUtils.generateToken(user.getId(), user.getUsername());

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .role(user.getRole() != null ? user.getRole() : 0)
                .voiceNotificationEnabled(user.getVoiceNotificationEnabled() != null && user.getVoiceNotificationEnabled())
                .build();

        return ApiResponse.success("登录成功", response);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public ApiResponse<User> getUserInfo(@AuthenticationPrincipal Long userId) {
        User user = userId == null ? null : userService.getUserById(userId);
        if (user == null) {
            // 保持原约定：前端收到 401 会清掉本地登录态
            return ApiResponse.fail(401, "用户未登录");
        }
        user.setPassword(null);
        return ApiResponse.success(user);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/update-profile")
    public ApiResponse<User> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        if (!userService.updateUserProfile(userId, request)) {
            throw BusinessException.badRequest("更新失败");
        }
        User user = requireUser(userId);
        user.setPassword(null);
        return ApiResponse.success("更新成功", user);
    }

    /**
     * 修改密码
     */
    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (!userService.changePassword(userId, request.getOldPassword(), request.getNewPassword())) {
            throw BusinessException.badRequest("密码修改失败");
        }
        return ApiResponse.success("密码修改成功", null);
    }

    /**
     * 上传头像
     */
    @PostMapping("/upload-avatar")
    public ApiResponse<String> uploadAvatar(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw BusinessException.badRequest("请选择文件");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw BusinessException.badRequest("只能上传图片文件");
        }

        // 上传到OSS，返回公网URL
        String avatarUrl = ossUtils.uploadFile(file);
        userService.updateAvatar(userId, avatarUrl);
        return ApiResponse.success("头像上传成功", avatarUrl);
    }

    /**
     * 发送邮箱验证码（无需登录）
     */
    @PostMapping("/send-email-code")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        String email = request.getEmail();

        // 检查频率限制
        if (!emailService.checkRateLimit(email)) {
            throw new BusinessException(429, "发送过于频繁，请60秒后再试");
        }

        if (!emailService.sendVerificationCode(email)) {
            throw BusinessException.badRequest("验证码发送失败");
        }
        return ApiResponse.success("验证码已发送，请查收邮件");
    }

    /**
     * 修改邮箱（需要验证密码和邮箱验证码）
     */
    @PutMapping("/change-email")
    public ApiResponse<User> changeEmail(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangeEmailRequest request) {
        String newEmail = request.getNewEmail();

        // 验证邮箱验证码
        if (!emailService.verifyCode(newEmail, request.getVerificationCode())) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }

        // 验证密码
        requireUser(userId);
        if (!userService.verifyPassword(userId, request.getPassword())) {
            throw BusinessException.badRequest("密码错误");
        }

        userService.updateEmail(userId, newEmail);

        User updatedUser = requireUser(userId);
        updatedUser.setPassword(null);
        return ApiResponse.success("邮箱修改成功", updatedUser);
    }

    private User requireUser(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }
}
