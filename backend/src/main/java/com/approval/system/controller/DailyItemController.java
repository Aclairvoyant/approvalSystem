package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.DailyItemRequest;
import com.approval.system.entity.DailyItem;
import com.approval.system.service.IDailyItemService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer itemType,
            @RequestParam(required = false) Boolean todayOnly) {
        return ApiResponse.success(dailyItemService.listVisibleItems(userId, status, itemType, todayOnly));
    }

    @PostMapping
    public ApiResponse<DailyItem> createItem(@AuthenticationPrincipal Long userId, @Valid @RequestBody DailyItemRequest request) {
        return ApiResponse.success(dailyItemService.createItem(userId, toEntity(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<DailyItem> updateItem(@AuthenticationPrincipal Long userId, @PathVariable Long id, @Valid @RequestBody DailyItemRequest request) {
        return ApiResponse.success(dailyItemService.updateItem(userId, id, toEntity(request)));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<DailyItem> completeItem(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.success(dailyItemService.completeItem(userId, id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<DailyItem> archiveItem(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.success(dailyItemService.archiveItem(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteItem(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        dailyItemService.deleteItem(userId, id);
        return ApiResponse.success();
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
}
