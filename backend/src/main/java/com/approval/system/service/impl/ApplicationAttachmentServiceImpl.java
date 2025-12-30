package com.approval.system.service.impl;

import com.approval.system.common.utils.OssUtils;
import com.approval.system.entity.ApplicationAttachment;
import com.approval.system.mapper.ApplicationAttachmentMapper;
import com.approval.system.service.IApplicationAttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ApplicationAttachmentServiceImpl extends ServiceImpl<ApplicationAttachmentMapper, ApplicationAttachment> implements IApplicationAttachmentService {

    @Autowired
    private OssUtils ossUtils;

    @Override
    public ApplicationAttachment uploadApplicationAttachment(Long applicationId, MultipartFile file) {
        try {
            // 上传文件到OSS
            String fileUrl = ossUtils.uploadFile(file);

            // 保存附件记录
            ApplicationAttachment attachment = ApplicationAttachment.builder()
                    .applicationId(applicationId)
                    .fileName(file.getOriginalFilename())
                    .fileUrl(fileUrl)
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .createdAt(LocalDateTime.now())
                    .build();

            this.save(attachment);
            return attachment;
        } catch (Exception e) {
            log.error("上传申请附件失败，applicationId: {}", applicationId, e);
            throw new RuntimeException("上传附件失败: " + e.getMessage());
        }
    }

    @Override
    public List<ApplicationAttachment> getApplicationAttachments(Long applicationId) {
        QueryWrapper<ApplicationAttachment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("application_id", applicationId);
        queryWrapper.orderByDesc("created_at");
        return this.list(queryWrapper);
    }

    @Override
    public void deleteAttachment(Long attachmentId) {
        ApplicationAttachment attachment = this.getById(attachmentId);
        if (attachment != null) {
            // 从OSS删除文件
            // ossUtils.deleteFile(attachment.getOssKey());
            this.removeById(attachmentId);
        }
    }
}
