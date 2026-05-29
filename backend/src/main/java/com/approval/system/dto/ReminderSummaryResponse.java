package com.approval.system.dto;

import com.approval.system.entity.Application;
import com.approval.system.entity.CoupleEvent;
import com.approval.system.entity.DailyItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReminderSummaryResponse {
    private Long pendingApprovalCount;
    private Long todayDailyCount;
    private Long overdueDailyCount;
    private Long totalEventCount;
    private Long upcomingEventCount;
    private List<Application> pendingApprovals;
    private List<DailyItem> todayDailyItems;
    private List<DailyItem> overdueDailyItems;
    private List<CoupleEvent> upcomingEvents;
}
