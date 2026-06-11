package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveEmailSettings {

    private String host;

    private Integer port;

    private String username;

    private String fromEmail;

    private String password;

    private Boolean sslEnabled;
}
