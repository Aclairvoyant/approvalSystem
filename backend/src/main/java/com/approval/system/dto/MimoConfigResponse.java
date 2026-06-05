package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MimoConfigResponse {

    private Boolean enabled;

    private String baseUrl;

    private String asrModel;

    private String chatModel;

    private Boolean apiKeyConfigured;

    private String apiKeyMasked;
}
