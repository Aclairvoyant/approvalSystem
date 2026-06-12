package com.approval.system.dto;

import lombok.Data;

@Data
public class VoiceModelSettingsUpdateRequest {

    private Boolean enabled;

    private String provider;

    private String asrBaseUrl;

    private String asrAuthScheme;

    private String asrApiKeyHeader;

    private String asrApiKey;

    private String asrModel;

    private String chatBaseUrl;

    private String chatAuthScheme;

    private String chatApiKeyHeader;

    private String chatApiKey;

    private String chatModel;

    /**
     * Backward-compatible common fields for older callers.
     */
    private String baseUrl;

    private String authScheme;

    private String apiKeyHeader;

    private String apiKey;
}
