package com.approval.system.common.exception;

import com.approval.system.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("发生系统异常", e);
        return ApiResponse.fail(500, "系统异常: " + e.getMessage());
    }

    /**
     * 处理资源不存在异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ApiResponse<Void> handleNotFoundException(NoHandlerFoundException e) {
        return ApiResponse.fail(404, "资源不存在");
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.error("非法参数", e);
        return ApiResponse.fail(400, "参数错误: " + e.getMessage());
    }
}
