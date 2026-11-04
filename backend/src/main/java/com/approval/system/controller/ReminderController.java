package com.approval.system.controller;

import com.approval.system.common.response.ApiResponse;
import com.approval.system.dto.ReminderSummaryResponse;
import com.approval.system.entity.Application;
import com.approval.system.entity.CoupleEvent;
import com.approval.system.entity.DailyItem;
import com.approval.system.service.IApplicationService;
import com.approval.system.service.ICoupleEventService;
import com.approval.system.service.IDailyItemService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    @Autowired
    private IApplicationService applicationService;

    @Autowired
    private IDailyItemService dailyItemService;

    @Autowired
    private ICoupleEventService coupleEventService;

    @GetMapping("/today")
    public ApiResponse<ReminderSummaryResponse> getTodaySummary(@AuthenticationPrincipal Long userId) {
        Page<Application> pendingPage = applicationService.getApproverPendingApplications(userId, 1, 5);
        List<DailyItem> todayItems = dailyItemService.listTodayItems(userId);
        List<DailyItem> overdueItems = dailyItemService.listOverdueItems(userId);
        List<CoupleEvent> allEvents = coupleEventService.listVisibleEvents(userId);
        List<CoupleEvent> upcomingEvents = coupleEventService.listUpcomingEvents(userId, 30);

        ReminderSummaryResponse response = ReminderSummaryResponse.builder()
                .pendingApprovalCount(pendingPage.getTotal())
                // 列表最多 5 条，计数要单独查，否则首页数字永远不超过 5
                .todayDailyCount(dailyItemService.countTodayItems(userId))
                .overdueDailyCount(dailyItemService.countOverdueItems(userId))
                .totalEventCount((long) allEvents.size())
                .upcomingEventCount(coupleEventService.countUpcomingEvents(userId, 30))
                .pendingApprovals(pendingPage.getRecords())
                .todayDailyItems(todayItems)
                .overdueDailyItems(overdueItems)
                .upcomingEvents(upcomingEvents)
                .build();
        return ApiResponse.success(response);
    }
}
