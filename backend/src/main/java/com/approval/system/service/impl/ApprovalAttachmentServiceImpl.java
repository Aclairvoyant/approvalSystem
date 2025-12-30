package com.approval.system.service.impl;

import com.approval.system.common.utils.OssUtils;
import com.approval.system.entity.ApprovalAttachment;
import com.approval.system.mapper.ApprovalAttachmentMapper;
import com.approval.system.service.IApprovalAttachmentService;
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
public class ApprovalAttachmentServiceImpl extends ServiceImpl<ApprovalAttachmentMapper, ApprovalAttachment> implements IApprovalAttachmentService {

    @Autowired
    private OssUtils ossUtils;

    @Override
    public ApprovalAttachment uploadApprovalAttachment(Long applicationId, Long operatorId, MultipartFile file) {
        try {
            // 上传文件到OSS
            String fileUrl = ossUtils.uploadFile(file);

            // 保存附件记录
            ApprovalAttachment attachment = ApprovalAttachment.builder()
                    .applicationId(applicationId)
                    .operatorId(operatorId)
                    .fileName(file.getOriginalFilename())
                    .fileUrl(fileUrl)
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .createdAt(LocalDateTime.now())
                    .build();

            this.save(attachment);
            return attachment;
        } catch (Exception e) {
            log.error("上传审批附件失败，applicationId: {}, operatorId: {}", applicationId, operatorId, e);
            throw new RuntimeException("上传附件失败: " + e.getMessage());
        }
    }

    @Override
    public List<ApprovalAttachment> getApprovalAttachments(Long applicationId) {
        QueryWrapper<ApprovalAttachment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("application_id", applicationId);
        queryWrapper.orderByDesc("created_at");
        return this.list(queryWrapper);
    }

    @Override
    public void deleteAttachment(Long attachmentId) {
        ApprovalAttachment attachment = this.getById(attachmentId);
        if (attachment != null) {
            // 从OSS删除文件
            // ossUtils.deleteFile(attachment.getOssKey());
            this.removeById(attachmentId);
        }
    }
}
