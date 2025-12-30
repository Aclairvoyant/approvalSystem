package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileUploadResponse {
    private Long attachmentId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
}
