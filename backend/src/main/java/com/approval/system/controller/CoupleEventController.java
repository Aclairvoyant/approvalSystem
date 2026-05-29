package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.CoupleEventRequest;
import com.approval.system.entity.CoupleEvent;
import com.approval.system.service.ICoupleEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/couple-events")
public class CoupleEventController {

    @Autowired
    private ICoupleEventService coupleEventService;

    @GetMapping
    public ApiResponse<List<CoupleEvent>> listEvents() {
        try {
            return ApiResponse.success(coupleEventService.listVisibleEvents(currentUserId()));
        } catch (Exception e) {
            log.error("List couple events failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @GetMapping("/upcoming")
    public ApiResponse<List<CoupleEvent>> listUpcomingEvents(@RequestParam(defaultValue = "30") Integer days) {
        try {
            return ApiResponse.success(coupleEventService.listUpcomingEvents(currentUserId(), days));
        } catch (Exception e) {
            log.error("List upcoming couple events failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<CoupleEvent> createEvent(@RequestBody CoupleEventRequest request) {
        try {
            return ApiResponse.success(coupleEventService.createEvent(currentUserId(), toEntity(request)));
        } catch (Exception e) {
            log.error("Create couple event failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<CoupleEvent> updateEvent(@PathVariable Long id, @RequestBody CoupleEventRequest request) {
        try {
            return ApiResponse.success(coupleEventService.updateEvent(currentUserId(), id, toEntity(request)));
        } catch (Exception e) {
            log.error("Update couple event failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteEvent(@PathVariable Long id) {
        try {
            coupleEventService.deleteEvent(currentUserId(), id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Delete couple event failed", e);
            return ApiResponse.fail(400, e.getMessage());
        }
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

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
