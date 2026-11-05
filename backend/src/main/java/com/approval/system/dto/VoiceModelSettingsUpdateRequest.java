package com.approval.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VoiceModelSettingsUpdateRequest {

    private Boolean enabled;

    @Size(max = 50, message = "服务商不能超过50个字符")
    private String provider;

    @Size(max = 500, message = "语音识别服务地址不能超过500个字符")
    private String asrBaseUrl;

    @Size(max = 50, message = "语音识别鉴权方式不能超过50个字符")
    private String asrAuthScheme;

    @Size(max = 100, message = "语音识别 Key 请求头不能超过100个字符")
    private String asrApiKeyHeader;

    @Size(max = 1000, message = "语音识别 API Key不能超过1000个字符")
    private String asrApiKey;

    @Size(max = 100, message = "语音识别模型不能超过100个字符")
    private String asrModel;

    @Size(max = 500, message = "语音大模型服务地址不能超过500个字符")
    private String chatBaseUrl;

    @Size(max = 50, message = "语音大模型鉴权方式不能超过50个字符")
    private String chatAuthScheme;

    @Size(max = 100, message = "语音大模型 Key 请求头不能超过100个字符")
    private String chatApiKeyHeader;

    @Size(max = 1000, message = "语音大模型 API Key不能超过1000个字符")
    private String chatApiKey;

    @Size(max = 100, message = "语音大模型名称不能超过100个字符")
    private String chatModel;

    /**
     * Backward-compatible common fields for older callers.
     */
    @Size(max = 500, message = "服务地址不能超过500个字符")
    private String baseUrl;

    @Size(max = 50, message = "鉴权方式不能超过50个字符")
    private String authScheme;

    @Size(max = 100, message = "Key 请求头不能超过100个字符")
    private String apiKeyHeader;

    @Size(max = 1000, message = "API Key不能超过1000个字符")
    private String apiKey;
}
