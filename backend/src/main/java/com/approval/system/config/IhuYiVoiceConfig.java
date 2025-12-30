package com.approval.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ihuyi.voice")
public class IhuYiVoiceConfig {
    /**
     * API接口地址
     */
    private String apiUrl;

    /**
     * 互亿无线账号(APIID)
     */
    private String account;

    /**
     * 互亿无线密码(APIKEY)
     */
    private String password;

    /**
     * 语音模板ID
     */
    private String templateId;

    /**
     * 是否启用语音通知
     */
    private Boolean enabled = true;
}
