package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.ApplicationTemplateRequest;
import com.approval.system.entity.ApplicationTemplate;
import com.approval.system.service.IApplicationTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/application-templates")
public class ApplicationTemplateController {

    @Autowired
    private IApplicationTemplateService applicationTemplateService;

    @GetMapping
    public ApiResponse<List<ApplicationTemplate>> listTemplates() {
        try {
            return ApiResponse.success(applicationTemplateService.listVisibleTemplates(currentUserId()));
        } catch (Exception e) {
            log.error("List application templates failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<ApplicationTemplate> createTemplate(@RequestBody ApplicationTemplateRequest request) {
        try {
            return ApiResponse.success(applicationTemplateService.createTemplate(currentUserId(), toEntity(request)));
        } catch (Exception e) {
            log.error("Create application template failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ApplicationTemplate> updateTemplate(@PathVariable Long id, @RequestBody ApplicationTemplateRequest request) {
        try {
            return ApiResponse.success(applicationTemplateService.updateTemplate(currentUserId(), id, toEntity(request)));
        } catch (Exception e) {
            log.error("Update application template failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/use")
    public ApiResponse<ApplicationTemplate> useTemplate(@PathVariable Long id) {
        try {
            return ApiResponse.success(applicationTemplateService.useTemplate(currentUserId(), id));
        } catch (Exception e) {
            log.error("Use application template failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        try {
            applicationTemplateService.deleteTemplate(currentUserId(), id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Delete application template failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    private ApplicationTemplate toEntity(ApplicationTemplateRequest request) {
        return ApplicationTemplate.builder()
                .partnerId(request.getPartnerId())
                .title(request.getTitle())
                .description(request.getDescription())
                .remark(request.getRemark())
                .shared(request.getShared())
                .build();
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
