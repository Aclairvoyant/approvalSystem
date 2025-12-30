package com.approval.system.service;

import com.approval.system.entity.ApprovalAttachment;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IApprovalAttachmentService extends IService<ApprovalAttachment> {

    /**
     * 上传审批附件
     */
    ApprovalAttachment uploadApprovalAttachment(Long applicationId, Long operatorId, MultipartFile file);

    /**
     * 获取申请的审批附件列表
     */
    List<ApprovalAttachment> getApprovalAttachments(Long applicationId);

    /**
     * 删除附件
     */
    void deleteAttachment(Long attachmentId);
}
