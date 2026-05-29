package com.approval.system.service;

import com.approval.system.entity.CoupleEvent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ICoupleEventService extends IService<CoupleEvent> {
    CoupleEvent createEvent(Long userId, CoupleEvent event);

    CoupleEvent updateEvent(Long userId, Long eventId, CoupleEvent event);

    List<CoupleEvent> listVisibleEvents(Long userId);

    List<CoupleEvent> listUpcomingEvents(Long userId, int days);

    void deleteEvent(Long userId, Long eventId);
}
