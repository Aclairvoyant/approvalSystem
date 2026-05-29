package com.approval.system.service;

import com.approval.system.entity.ApplicationTemplate;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IApplicationTemplateService extends IService<ApplicationTemplate> {
    ApplicationTemplate createTemplate(Long userId, ApplicationTemplate template);

    ApplicationTemplate updateTemplate(Long userId, Long templateId, ApplicationTemplate template);

    ApplicationTemplate useTemplate(Long userId, Long templateId);

    List<ApplicationTemplate> listVisibleTemplates(Long userId);

    void deleteTemplate(Long userId, Long templateId);
}
