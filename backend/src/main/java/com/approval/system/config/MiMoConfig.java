package com.approval.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 小米 MiMo 大模型配置
 * <p>
 * 接口为 OpenAI 兼容格式，但鉴权头为 {@code api-key}（非 Authorization: Bearer）。
 * ASR 与文本对话共用同一个 chat/completions 端点，仅 model 不同。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mimo")
public class MiMoConfig {

    /**
     * 接口基础地址，如 https://api.xiaomimimo.com/v1
     */
    private String baseUrl;

    /**
     * API Key（通过 api-key 请求头传递）
     */
    private String apiKey;

    /**
     * 语音识别（ASR）模型名称
     */
    private String asrModel = "mimo-v2.5-asr";

    /**
     * 文本抽取使用的模型名称
     */
    private String chatModel = "mimo-v2.5";

    /**
     * 是否启用 MiMo 相关功能
     */
    private Boolean enabled = true;
}
