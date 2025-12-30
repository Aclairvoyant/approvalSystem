package com.approval.system.common.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.approval.system.common.config.AliyunOssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class OssUtils {

    @Autowired
    private AliyunOssProperties ossProperties;

    /**
     * 上传文件到OSS
     */
    public String uploadFile(MultipartFile file) {
        try {
            // 验证文件大小
            if (file.getSize() > ossProperties.getMaxFileSize()) {
                throw new RuntimeException("文件大小超过限制");
            }

            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            String allowedTypesStr = ossProperties.getAllowedFileTypes();
            if (allowedTypesStr != null && !allowedTypesStr.isEmpty()) {
                String[] allowedTypes = allowedTypesStr.split(",");
                boolean isAllowed = false;
                for (String type : allowedTypes) {
                    String trimmedType = type.trim();
                    // 支持通配符匹配，如 image/* 匹配所有图片类型
                    if (trimmedType.endsWith("/*")) {
                        String prefix = trimmedType.substring(0, trimmedType.length() - 1);
                        if (contentType.startsWith(prefix)) {
                            isAllowed = true;
                            break;
                        }
                    } else if (contentType.equals(trimmedType) || contentType.contains(trimmedType)) {
                        isAllowed = true;
                        break;
                    }
                }
                if (!isAllowed) {
                    log.warn("不支持的文件类型: {}, 允许的类型: {}", contentType, allowedTypesStr);
                    throw new RuntimeException("不支持的文件类型: " + contentType);
                }
            }

            // 生成OSS对象键
            String ossKey = generateOssKey(Objects.requireNonNull(file.getOriginalFilename()));

            // 初始化OSS客户端
            OSS ossClient = new OSSClientBuilder().build(
                    ossProperties.getEndpoint(),
                    ossProperties.getAccessKeyId(),
                    ossProperties.getAccessKeySecret()
            );

            try {
                // 上传文件
                InputStream inputStream = file.getInputStream();
                PutObjectRequest putObjectRequest = new PutObjectRequest(
                        ossProperties.getBucketName(),
                        ossKey,
                        inputStream
                );

                ossClient.putObject(putObjectRequest);

                // 生成访问URL
                String fileUrl = ossProperties.getBucketUrl() + "/" + ossKey;

                log.info("文件上传成功，OSS键: {}, URL: {}", ossKey, fileUrl);
                return fileUrl;
            } finally {
                ossClient.shutdown();
            }
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除OSS中的文件
     */
    public void deleteFile(String ossKey) {
        try {
            OSS ossClient = new OSSClientBuilder().build(
                    ossProperties.getEndpoint(),
                    ossProperties.getAccessKeyId(),
                    ossProperties.getAccessKeySecret()
            );

            try {
                ossClient.deleteObject(ossProperties.getBucketName(), ossKey);
                log.info("文件删除成功，OSS键: {}", ossKey);
            } finally {
                ossClient.shutdown();
            }
        } catch (Exception e) {
            log.error("文件删除失败，OSS键: {}", ossKey, e);
        }
    }

    /**
     * 生成OSS对象键
     */
    private String generateOssKey(String originalFilename) {
        String ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "approvals/" + timestamp + "/" + uuid + ext;
    }
}
