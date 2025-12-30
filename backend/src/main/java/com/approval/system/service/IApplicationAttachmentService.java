package com.approval.system.service;

import com.approval.system.entity.ApplicationAttachment;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IApplicationAttachmentService extends IService<ApplicationAttachment> {

    /**
     * 上传申请附件
     */
    ApplicationAttachment uploadApplicationAttachment(Long applicationId, MultipartFile file);

    /**
     * 获取申请的附件列表
     */
    List<ApplicationAttachment> getApplicationAttachments(Long applicationId);

    /**
     * 删除附件
     */
    void deleteAttachment(Long attachmentId);
}
