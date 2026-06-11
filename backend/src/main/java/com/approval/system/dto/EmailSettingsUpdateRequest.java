package com.approval.system.dto;

import lombok.Data;

@Data
public class EmailSettingsUpdateRequest {

    private String host;

    private Integer port;

    private String username;

    private String fromEmail;

    private String password;

    private Boolean sslEnabled;
}
