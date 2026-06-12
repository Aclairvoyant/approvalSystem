package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceModelSettingsResponse {

    private Boolean enabled;

    private String provider;

    private String asrBaseUrl;

    private String asrAuthScheme;

    private String asrApiKeyHeader;

    private String asrModel;

    private Boolean asrApiKeyConfigured;

    private String asrApiKeyMasked;

    private String chatBaseUrl;

    private String chatAuthScheme;

    private String chatApiKeyHeader;

    private String chatModel;

    private Boolean chatApiKeyConfigured;

    private String chatApiKeyMasked;

    /**
     * Backward-compatible common values for older callers.
     */
    private String baseUrl;

    private String authScheme;

    private String apiKeyHeader;

    private Boolean apiKeyConfigured;

    private String apiKeyMasked;
}
