package com.approval.system.common.exception;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.support.MockMvcSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一异常出口：所有错误保持 HTTP 200 + ApiResponse{code, message}，
 * 业务/校验消息原样返回，未预期异常只返回通用提示。
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcSupport.standalone(new ThrowingController());
    }

    @Test
    void businessExceptionKeepsItsCodeAndMessage() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("申请单不存在"));
    }

    @Test
    void unexpectedExceptionReturnsGenericMessageWithoutInternals() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE))
                .andExpect(content().string(not(containsString("jdbc:mysql"))));
    }

    @Test
    void illegalArgumentFromLibrariesIsNotEchoed() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.BAD_REQUEST_MESSAGE));
    }

    @Test
    void invalidRequestBodyReturnsFirstFieldMessageAndAllFieldErrors() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请输入标题"))
                .andExpect(jsonPath("$.data.title").value("请输入标题"));
    }

    @Test
    void constrainedRequestParamIsValidated() throws Exception {
        mockMvc.perform(get("/test/param").param("detail", "x".repeat(11)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能超过10个字符"));
    }

    @Test
    void missingRequestParamIsReportedByName() throws Exception {
        mockMvc.perform(get("/test/required-param"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必要参数: status"));
    }

    @Test
    void typeMismatchIsReportedByName() throws Exception {
        mockMvc.perform(get("/test/required-param").param("status", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数格式不正确: status"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体格式不正确"));
    }

    @Test
    void accessDeniedBecomes403InsteadOfGeneric500() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.FORBIDDEN_MESSAGE));
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        @GetMapping("/business")
        ApiResponse<Void> business() {
            throw BusinessException.notFound("申请单不存在");
        }

        @GetMapping("/unexpected")
        ApiResponse<Void> unexpected() {
            throw new IllegalStateException("Communications link failure jdbc:mysql://10.0.0.1/approval");
        }

        @GetMapping("/illegal-argument")
        ApiResponse<Long> illegalArgument() {
            // NumberFormatException 是 IllegalArgumentException 的子类，消息来自 JDK
            return ApiResponse.success(Long.valueOf("not-a-number"));
        }

        @PostMapping("/body")
        ApiResponse<Void> body(@Valid @RequestBody Payload payload) {
            return ApiResponse.success();
        }

        @GetMapping("/param")
        ApiResponse<Void> param(@RequestParam @Size(max = 10, message = "不能超过10个字符") String detail) {
            return ApiResponse.success();
        }

        @GetMapping("/required-param")
        ApiResponse<Void> requiredParam(@RequestParam Integer status) {
            return ApiResponse.success();
        }

        @GetMapping("/denied")
        ApiResponse<Void> denied() {
            throw new AccessDeniedException("Access Denied");
        }
    }

    static class Payload {
        @NotBlank(message = "请输入标题")
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
