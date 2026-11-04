package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.ApplicationTemplateRequest;
import com.approval.system.entity.ApplicationTemplate;
import com.approval.system.service.IApplicationTemplateService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/application-templates")
public class ApplicationTemplateController {

    @Autowired
    private IApplicationTemplateService applicationTemplateService;

    @GetMapping
    public ApiResponse<List<ApplicationTemplate>> listTemplates(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(applicationTemplateService.listVisibleTemplates(userId));
    }

    @PostMapping
    public ApiResponse<ApplicationTemplate> createTemplate(@AuthenticationPrincipal Long userId, @Valid @RequestBody ApplicationTemplateRequest request) {
        return ApiResponse.success(applicationTemplateService.createTemplate(userId, toEntity(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApplicationTemplate> updateTemplate(@AuthenticationPrincipal Long userId, @PathVariable Long id, @Valid @RequestBody ApplicationTemplateRequest request) {
        return ApiResponse.success(applicationTemplateService.updateTemplate(userId, id, toEntity(request)));
    }

    @PostMapping("/{id}/use")
    public ApiResponse<ApplicationTemplate> useTemplate(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.success(applicationTemplateService.useTemplate(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemplate(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        applicationTemplateService.deleteTemplate(userId, id);
        return ApiResponse.success();
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
}
