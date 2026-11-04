package com.approval.system.common.exception;

import lombok.Getter;

/**
 * 可预期的业务异常：消息会原样返回给前端展示，所以只放给用户看的中文提示。
 * 由 {@link GlobalExceptionHandler} 统一转换为 {@code ApiResponse.fail(code, message)}（HTTP 200）。
 * <p>
 * 常用 code：400 参数/状态不合法，403 无权限，404 资源不存在，409 冲突，503 外部服务不可用。
 * 不要用 401：前端收到 401 会清空登录态并跳转登录页。
 */
@Getter
public class BusinessException extends RuntimeException {

    public static final int BAD_REQUEST = 400;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    /** 依赖的外部服务（OSS、大模型、短信等）暂时不可用 */
    public static final int SERVICE_UNAVAILABLE = 503;

    private final int code;

    public BusinessException(String message) {
        this(BAD_REQUEST, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(BAD_REQUEST, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(FORBIDDEN, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(CONFLICT, message);
    }
}
