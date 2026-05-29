package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.DailyItemRequest;
import com.approval.system.entity.DailyItem;
import com.approval.system.service.IDailyItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/daily-items")
public class DailyItemController {

    @Autowired
    private IDailyItemService dailyItemService;

    @GetMapping
    public ApiResponse<List<DailyItem>> listItems(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer itemType,
            @RequestParam(required = false) Boolean todayOnly) {
        try {
            Long userId = currentUserId();
            return ApiResponse.success(dailyItemService.listVisibleItems(userId, status, itemType, todayOnly));
        } catch (Exception e) {
            log.error("List daily items failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<DailyItem> createItem(@RequestBody DailyItemRequest request) {
        try {
            Long userId = currentUserId();
            return ApiResponse.success(dailyItemService.createItem(userId, toEntity(request)));
        } catch (Exception e) {
            log.error("Create daily item failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<DailyItem> updateItem(@PathVariable Long id, @RequestBody DailyItemRequest request) {
        try {
            Long userId = currentUserId();
            return ApiResponse.success(dailyItemService.updateItem(userId, id, toEntity(request)));
        } catch (Exception e) {
            log.error("Update daily item failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<DailyItem> completeItem(@PathVariable Long id) {
        try {
            Long userId = currentUserId();
            return ApiResponse.success(dailyItemService.completeItem(userId, id));
        } catch (Exception e) {
            log.error("Complete daily item failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<DailyItem> archiveItem(@PathVariable Long id) {
        try {
            Long userId = currentUserId();
            return ApiResponse.success(dailyItemService.archiveItem(userId, id));
        } catch (Exception e) {
            log.error("Archive daily item failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        try {
            Long userId = currentUserId();
            dailyItemService.deleteItem(userId, id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Delete daily item failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    private DailyItem toEntity(DailyItemRequest request) {
        return DailyItem.builder()
                .partnerId(request.getPartnerId())
                .itemType(request.getItemType())
                .title(request.getTitle())
                .content(request.getContent())
                .priority(request.getPriority())
                .targetDate(request.getTargetDate())
                .build();
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
