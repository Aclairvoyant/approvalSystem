package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.CoupleEventRequest;
import com.approval.system.entity.CoupleEvent;
import com.approval.system.service.ICoupleEventService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/couple-events")
public class CoupleEventController {

    @Autowired
    private ICoupleEventService coupleEventService;

    @GetMapping
    public ApiResponse<List<CoupleEvent>> listEvents(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(coupleEventService.listVisibleEvents(userId));
    }

    @GetMapping("/upcoming")
    public ApiResponse<List<CoupleEvent>> listUpcomingEvents(@AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "30") Integer days) {
        return ApiResponse.success(coupleEventService.listUpcomingEvents(userId, days));
    }

    @PostMapping
    public ApiResponse<CoupleEvent> createEvent(@AuthenticationPrincipal Long userId, @Valid @RequestBody CoupleEventRequest request) {
        return ApiResponse.success(coupleEventService.createEvent(userId, toEntity(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<CoupleEvent> updateEvent(@AuthenticationPrincipal Long userId, @PathVariable Long id, @Valid @RequestBody CoupleEventRequest request) {
        return ApiResponse.success(coupleEventService.updateEvent(userId, id, toEntity(request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteEvent(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        coupleEventService.deleteEvent(userId, id);
        return ApiResponse.success();
    }

    private CoupleEvent toEntity(CoupleEventRequest request) {
        return CoupleEvent.builder()
                .partnerId(request.getPartnerId())
                .eventType(request.getEventType())
                .title(request.getTitle())
                .eventDate(request.getEventDate())
                .repeatType(request.getRepeatType())
                .remindDaysBefore(request.getRemindDaysBefore())
                .note(request.getNote())
                .build();
    }
}
