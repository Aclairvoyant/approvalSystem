package com.approval.system.service.impl;

import com.approval.system.entity.ApplicationTemplate;
import com.approval.system.mapper.ApplicationTemplateMapper;
import com.approval.system.service.IApplicationTemplateService;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApplicationTemplateServiceImpl extends ServiceImpl<ApplicationTemplateMapper, ApplicationTemplate> implements IApplicationTemplateService {

    @Autowired
    private IUserRelationService userRelationService;

    @Override
    @Transactional
    public ApplicationTemplate createTemplate(Long userId, ApplicationTemplate template) {
        validateTemplate(template);
        Boolean shared = Boolean.TRUE.equals(template.getShared());
        LocalDateTime now = LocalDateTime.now();
        ApplicationTemplate applicationTemplate = ApplicationTemplate.builder()
                .creatorId(userId)
                .partnerId(shared ? resolvePartnerId(userId, template.getPartnerId()) : null)
                .title(template.getTitle().trim())
                .description(template.getDescription())
                .remark(template.getRemark())
                .shared(shared)
                .usageCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        this.save(applicationTemplate);
        return applicationTemplate;
    }

    @Override
    @Transactional
    public ApplicationTemplate updateTemplate(Long userId, Long templateId, ApplicationTemplate template) {
        ApplicationTemplate existing = getOwnedTemplate(userId, templateId);
        validateTemplate(template);
        Boolean shared = Boolean.TRUE.equals(template.getShared());
        existing.setTitle(template.getTitle().trim());
        existing.setDescription(template.getDescription());
        existing.setRemark(template.getRemark());
        existing.setShared(shared);
        existing.setPartnerId(shared ? resolvePartnerId(userId, template.getPartnerId()) : null);
        existing.setUpdatedAt(LocalDateTime.now());
        this.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public ApplicationTemplate useTemplate(Long userId, Long templateId) {
        ApplicationTemplate template = getVisibleTemplate(userId, templateId);
        template.setUsageCount(template.getUsageCount() == null ? 1 : template.getUsageCount() + 1);
        template.setUpdatedAt(LocalDateTime.now());
        this.updateById(template);
        return template;
    }

    @Override
    public List<ApplicationTemplate> listVisibleTemplates(Long userId) {
        QueryWrapper<ApplicationTemplate> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("creator_id", userId)
                .or(shared -> shared.eq("shared", true).eq("partner_id", userId)));
        queryWrapper.orderByDesc("usage_count");
        queryWrapper.orderByDesc("updated_at");
        return this.list(queryWrapper);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long userId, Long templateId) {
        ApplicationTemplate template = getOwnedTemplate(userId, templateId);
        this.removeById(template.getId());
    }

    private ApplicationTemplate getVisibleTemplate(Long userId, Long templateId) {
        QueryWrapper<ApplicationTemplate> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", templateId);
        queryWrapper.and(w -> w.eq("creator_id", userId)
                .or(shared -> shared.eq("shared", true).eq("partner_id", userId)));
        ApplicationTemplate template = this.getOne(queryWrapper);
        if (template == null) {
            throw new RuntimeException("No permission or template not found");
        }
        return template;
    }

    private ApplicationTemplate getOwnedTemplate(Long userId, Long templateId) {
        QueryWrapper<ApplicationTemplate> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", templateId);
        queryWrapper.eq("creator_id", userId);
        ApplicationTemplate template = this.getOne(queryWrapper);
        if (template == null) {
            throw new RuntimeException("Only the creator can edit this template");
        }
        return template;
    }

    private Long resolvePartnerId(Long userId, Long requestedPartnerId) {
        Long partnerId = requestedPartnerId != null ? requestedPartnerId : userRelationService.getActivePartnerId(userId);
        if (partnerId == null) {
            return null;
        }
        if (!userRelationService.isRelated(userId, partnerId)) {
            throw new RuntimeException("Partner relationship is required");
        }
        return partnerId;
    }

    private void validateTemplate(ApplicationTemplate template) {
        if (template.getTitle() == null || template.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Title is required");
        }
        if (template.getDescription() == null || template.getDescription().trim().isEmpty()) {
            throw new RuntimeException("Description is required");
        }
    }
}
