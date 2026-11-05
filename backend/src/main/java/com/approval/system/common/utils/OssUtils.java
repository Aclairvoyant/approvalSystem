package com.approval.system.common.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.approval.system.common.config.AliyunOssProperties;
import com.approval.system.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
public class OssUtils {

    @Autowired
    private AliyunOssProperties ossProperties;

    /**
     * 上传文件到OSS。文件大小/类型不合法时抛出 {@link BusinessException}（提示给用户）；
     * OSS 调用失败时记录原始异常，只向上抛出通用提示。
     */
    public String uploadFile(MultipartFile file) {
        Long maxFileSize = ossProperties.getMaxFileSize();
        if (maxFileSize != null && maxFileSize > 0 && file.getSize() > maxFileSize) {
            throw BusinessException.badRequest("文件大小超过限制");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        validateAllowedContentType(contentType);
        String ossKey = generateOssKey(file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            return putObject(ossKey, inputStream);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败，OSS键: {}", ossKey, e);
            throw new BusinessException(BusinessException.SERVICE_UNAVAILABLE, "文件上传失败，请稍后重试", e);
        }
    }

    public String uploadBytes(byte[] data, String originalFilename, String contentType) {
        if (data == null || data.length == 0) {
            throw BusinessException.badRequest("文件内容不能为空");
        }
        Long maxFileSize = ossProperties.getMaxFileSize();
        if (maxFileSize != null && maxFileSize > 0 && data.length > maxFileSize) {
            throw BusinessException.badRequest("文件大小超过限制");
        }
        String safeContentType = contentType == null ? "application/octet-stream" : contentType;
        validateAllowedContentType(safeContentType);
        String ossKey = generateOssKey(originalFilename);

        try {
            return putObject(ossKey, new ByteArrayInputStream(data));
        } catch (Exception e) {
            log.error("文件上传失败，OSS键: {}", ossKey, e);
            throw new BusinessException(BusinessException.SERVICE_UNAVAILABLE, "文件上传失败，请稍后重试", e);
        }
    }

    private String putObject(String ossKey, InputStream inputStream) {
        OSS ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
        try {
            ossClient.putObject(new PutObjectRequest(ossProperties.getBucketName(), ossKey, inputStream));
            String fileUrl = ossProperties.getBucketUrl() + "/" + ossKey;
            log.info("文件上传成功，OSS键: {}, URL: {}", ossKey, fileUrl);
            return fileUrl;
        } finally {
            ossClient.shutdown();
        }
    }

    private void validateAllowedContentType(String contentType) {
        String allowedTypesStr = ossProperties.getAllowedFileTypes();
        if (allowedTypesStr == null || allowedTypesStr.isEmpty()) {
            return;
        }
        String[] allowedTypes = allowedTypesStr.split(",");
        for (String type : allowedTypes) {
            String trimmedType = type.trim();
            if (trimmedType.endsWith("/*")) {
                String prefix = trimmedType.substring(0, trimmedType.length() - 1);
                if (contentType.startsWith(prefix)) {
                    return;
                }
            } else if (contentType.equals(trimmedType)) {
                return;
            }
        }
        log.warn("不支持的文件类型: {}, 允许的类型: {}", contentType, allowedTypesStr);
        throw BusinessException.badRequest("不支持的文件类型: " + contentType);
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
        if (originalFilename == null) {
            throw BusinessException.badRequest("文件名不能为空");
        }
        int dot = originalFilename.lastIndexOf('.');
        String ext = dot >= 0 ? originalFilename.substring(dot) : "";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "approvals/" + timestamp + "/" + uuid + ext;
    }
}
