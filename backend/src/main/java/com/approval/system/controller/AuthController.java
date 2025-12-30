package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.common.utils.JwtUtils;
import com.approval.system.common.utils.OssUtils;
import com.approval.system.dto.LoginRequest;
import com.approval.system.dto.LoginResponse;
import com.approval.system.dto.RegisterRequest;
import com.approval.system.dto.UserUpdateRequest;
import com.approval.system.entity.User;
import com.approval.system.service.IEmailService;
import com.approval.system.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ApiResponse<User> register(@RequestBody RegisterRequest request) {
        try {
            // 验证邮箱验证码
            if (request.getEmail() != null && !request.getEmail().isEmpty()) {
                if (request.getEmailVerificationCode() == null || request.getEmailVerificationCode().isEmpty()) {
                    return ApiResponse.fail(400, "请提供邮箱验证码");
                }

                if (!emailService.verifyCode(request.getEmail(), request.getEmailVerificationCode())) {
                    return ApiResponse.fail(400, "邮箱验证码错误或已过期");
                }
            }

            User user = userService.register(request.getUsername(), request.getPhone(),
                    request.getPassword(), request.getEmail(), request.getRealName());
            // 不返回密码
            user.setPassword(null);
            return ApiResponse.success("注册成功", user);
        } catch (Exception e) {
            log.error("注册失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
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
                    .build();

            return ApiResponse.success("登录成功", response);
        } catch (Exception e) {
            log.error("登录失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public ApiResponse<User> getUserInfo() {
        try {
            // 从SecurityContext中获取用户ID
            Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();

            if (principal instanceof Long) {
                Long userId = (Long) principal;
                User user = userService.getUserById(userId);
                if (user != null) {
                    user.setPassword(null);
                    return ApiResponse.success(user);
                }
            }
            return ApiResponse.fail(401, "用户未登录");
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/update-profile")
    public ApiResponse<User> updateProfile(@RequestBody UserUpdateRequest request) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            boolean success = userService.updateUserProfile(userId, request);
            if (success) {
                User user = userService.getUserById(userId);
                user.setPassword(null);
                return ApiResponse.success("更新成功", user);
            }
            return ApiResponse.fail(400, "更新失败");
        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 修改密码
     */
    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody java.util.Map<String, String> request) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");

            if (oldPassword == null || newPassword == null) {
                return ApiResponse.fail(400, "请提供原密码和新密码");
            }

            boolean success = userService.changePassword(userId, oldPassword, newPassword);
            if (success) {
                return ApiResponse.success("密码修改成功", null);
            }
            return ApiResponse.fail(400, "密码修改失败");
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 上传头像
     */
    @PostMapping("/upload-avatar")
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            if (file.isEmpty()) {
                return ApiResponse.fail(400, "请选择文件");
            }

            // 检查文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ApiResponse.fail(400, "只能上传图片文件");
            }

            // 上传到OSS，返回公网URL
            String avatarUrl = ossUtils.uploadFile(file);

            // 更新用户头像
            userService.updateAvatar(userId, avatarUrl);

            return ApiResponse.success("头像上传成功", avatarUrl);
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/send-email-code")
    public ApiResponse<Void> sendEmailCode(@RequestBody java.util.Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.isEmpty()) {
                return ApiResponse.fail(400, "请提供邮箱地址");
            }

            // 验证邮箱格式
            if (!email.matches("^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)+$")) {
                return ApiResponse.fail(400, "邮箱格式不正确");
            }

            // 检查频率限制
            if (!emailService.checkRateLimit(email)) {
                return ApiResponse.fail(429, "发送过于频繁，请60秒后再试");
            }

            boolean success = emailService.sendVerificationCode(email);
            if (success) {
                return ApiResponse.success("验证码已发送，请查收邮件");
            }
            return ApiResponse.fail(400, "验证码发送失败");
        } catch (Exception e) {
            log.error("发送邮箱验证码失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    /**
     * 修改邮箱（需要验证密码和邮箱验证码）
     */
    @PutMapping("/change-email")
    public ApiResponse<User> changeEmail(@RequestBody java.util.Map<String, String> request) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail(401, "用户未登录");
            }

            String password = request.get("password");
            String newEmail = request.get("newEmail");
            String verificationCode = request.get("verificationCode");

            if (password == null || newEmail == null || verificationCode == null) {
                return ApiResponse.fail(400, "请提供密码、新邮箱和验证码");
            }

            // 验证邮箱格式
            if (!newEmail.matches("^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)+$")) {
                return ApiResponse.fail(400, "邮箱格式不正确");
            }

            // 验证邮箱验证码
            if (!emailService.verifyCode(newEmail, verificationCode)) {
                return ApiResponse.fail(400, "验证码错误或已过期");
            }

            // 验证密码
            User user = userService.getUserById(userId);
            if (user == null) {
                return ApiResponse.fail(404, "用户不存在");
            }

            if (!userService.verifyPassword(userId, password)) {
                return ApiResponse.fail(400, "密码错误");
            }

            // 更新邮箱
            userService.updateEmail(userId, newEmail);

            User updatedUser = userService.getUserById(userId);
            updatedUser.setPassword(null);

            return ApiResponse.success("邮箱修改成功", updatedUser);
        } catch (Exception e) {
            log.error("修改邮箱失败", e);
            return ApiResponse.fail(500, e.getMessage());
        }
    }
}
