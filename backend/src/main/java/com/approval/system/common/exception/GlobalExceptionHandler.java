package com.approval.system.common.exception;

import com.approval.system.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 统一异常出口。所有错误都以 HTTP 200 + {@code ApiResponse{code, message}} 返回（与改造前的约定一致），
 * 前端 http.ts 按 body 里的 code 处理：401 跳登录、403 提示无权限、其余直接展示 message。
 * <p>
 * 只有 {@link BusinessException} 和参数校验的消息会透传给前端；
 * 其它未预期的异常一律记录完整堆栈，并返回通用提示，避免把 SQL、内部地址等细节暴露出去。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String INTERNAL_ERROR_MESSAGE = "系统繁忙，请稍后重试";
    static final String BAD_REQUEST_MESSAGE = "请求参数错误";
    static final String FORBIDDEN_MESSAGE = "没有权限访问该资源";
    static final String UNAUTHORIZED_MESSAGE = "登录状态已失效，请重新登录";

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} {} -> code={}, message={}",
                request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /**
     * {@code @Valid @RequestBody} 校验失败（MethodArgumentNotValidException 是 BindException 的子类），
     * 以及表单/查询参数绑定到对象时的校验失败。message 取第一条错误，data 带上全部字段错误。
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Map<String, String>> handleBind(BindException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), messageOf(fieldError));
        }
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::messageOf)
                .findFirst()
                .orElseGet(() -> e.getBindingResult().getGlobalErrors().stream()
                        .map(this::messageOf)
                        .findFirst()
                        .orElse(BAD_REQUEST_MESSAGE));
        log.info("参数校验失败: {}", errors);
        return ApiResponse.<Map<String, String>>builder()
                .code(BusinessException.BAD_REQUEST)
                .message(message)
                .data(errors.isEmpty() ? null : errors)
                .build();
    }

    /**
     * 方法参数上的约束（如 {@code @RequestParam @Size(max = 500) String detail}）校验失败。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ApiResponse<Void> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(this::messageOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(BAD_REQUEST_MESSAGE);
        log.info("参数校验失败: {}", message);
        return ApiResponse.fail(BusinessException.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(BAD_REQUEST_MESSAGE);
        log.info("参数校验失败: {}", message);
        return ApiResponse.fail(BusinessException.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        return ApiResponse.fail(BusinessException.BAD_REQUEST, "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ApiResponse<Void> handleMissingPart(MissingServletRequestPartException e) {
        return ApiResponse.fail(BusinessException.BAD_REQUEST, "缺少必要参数: " + e.getRequestPartName());
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ApiResponse<Void> handleMissingPathVariable(MissingPathVariableException e) {
        return ApiResponse.fail(BusinessException.BAD_REQUEST, "缺少必要参数: " + e.getVariableName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResponse.fail(BusinessException.BAD_REQUEST, "参数格式不正确: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.info("请求体无法解析: {}", e.getMostSpecificCause().getMessage());
        return ApiResponse.fail(BusinessException.BAD_REQUEST, "请求体格式不正确");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ApiResponse.fail(413, "上传文件过大");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResponse.fail(405, "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ApiResponse<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ApiResponse.fail(415, "不支持的请求类型");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ApiResponse<Void> handleNotFound(Exception e) {
        return ApiResponse.fail(BusinessException.NOT_FOUND, "资源不存在");
    }

    /**
     * 方法级安全（@PreAuthorize）拒绝访问。URL 级别的拒绝由 SecurityConfig 的 accessDeniedHandler 处理。
     * 必须显式声明，否则会被下面的 Exception 兜底吞掉，变成 500。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("拒绝访问: {} {}", request.getMethod(), request.getRequestURI());
        return ApiResponse.fail(BusinessException.FORBIDDEN, FORBIDDEN_MESSAGE);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ApiResponse<Void> handleAuthentication(AuthenticationException e) {
        return ApiResponse.fail(401, UNAUTHORIZED_MESSAGE);
    }

    /**
     * IllegalArgumentException 既可能是我们自己的参数检查，也可能来自第三方库（如数字解析），
     * 消息不可控，统一返回通用提示。需要给用户看的提示请抛 BusinessException。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("非法参数: {} {}", request.getMethod(), request.getRequestURI(), e);
        return ApiResponse.fail(BusinessException.BAD_REQUEST, BAD_REQUEST_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} {}", request.getMethod(), request.getRequestURI(), e);
        return ApiResponse.fail(500, INTERNAL_ERROR_MESSAGE);
    }

    private String messageOf(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? BAD_REQUEST_MESSAGE : message;
    }
}
