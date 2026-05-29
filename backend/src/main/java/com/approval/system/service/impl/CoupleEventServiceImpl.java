package com.approval.system.service.impl;

import com.approval.system.entity.CoupleEvent;
import com.approval.system.mapper.CoupleEventMapper;
import com.approval.system.service.ICoupleEventService;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CoupleEventServiceImpl extends ServiceImpl<CoupleEventMapper, CoupleEvent> implements ICoupleEventService {

    @Autowired
    private IUserRelationService userRelationService;

    @Override
    @Transactional
    public CoupleEvent createEvent(Long userId, CoupleEvent event) {
        validateEvent(event);
        LocalDateTime now = LocalDateTime.now();
        CoupleEvent coupleEvent = CoupleEvent.builder()
                .creatorId(userId)
                .partnerId(resolvePartnerId(userId, event.getPartnerId()))
                .eventType(defaultNumber(event.getEventType(), 1))
                .title(event.getTitle().trim())
                .eventDate(event.getEventDate())
                .repeatType(defaultNumber(event.getRepeatType(), 1))
                .remindDaysBefore(defaultNumber(event.getRemindDaysBefore(), 1))
                .note(event.getNote())
                .createdAt(now)
                .updatedAt(now)
                .build();
        this.save(coupleEvent);
        return coupleEvent;
    }

    @Override
    @Transactional
    public CoupleEvent updateEvent(Long userId, Long eventId, CoupleEvent event) {
        CoupleEvent existing = getVisibleEvent(userId, eventId);
        validateEvent(event);
        if (event.getPartnerId() != null) {
            existing.setPartnerId(resolvePartnerId(userId, event.getPartnerId()));
        } else if (isSelfPartnerEvent(existing)) {
            Long repairedPartnerId = resolveSelfPartnerEventPartnerId(userId, existing);
            if (repairedPartnerId != null) {
                existing.setPartnerId(repairedPartnerId);
            }
        }
        existing.setEventType(defaultNumber(event.getEventType(), existing.getEventType()));
        existing.setTitle(event.getTitle().trim());
        existing.setEventDate(event.getEventDate());
        existing.setRepeatType(defaultNumber(event.getRepeatType(), existing.getRepeatType()));
        existing.setRemindDaysBefore(defaultNumber(event.getRemindDaysBefore(), existing.getRemindDaysBefore()));
        existing.setNote(event.getNote());
        existing.setUpdatedAt(LocalDateTime.now());
        this.updateById(existing);
        return existing;
    }

    @Override
    public List<CoupleEvent> listVisibleEvents(Long userId) {
        List<CoupleEvent> events = this.list(visibleQuery(userId));
        return events.stream()
                .sorted(Comparator.comparing(this::nextOccurrence))
                .collect(Collectors.toList());
    }

    @Override
    public List<CoupleEvent> listUpcomingEvents(Long userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate until = LocalDate.now().plusDays(days);
        return listVisibleEvents(userId).stream()
                .filter(event -> !nextOccurrence(event).isBefore(today) && !nextOccurrence(event).isAfter(until))
                .limit(5)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        CoupleEvent event = getVisibleEvent(userId, eventId);
        this.removeById(event.getId());
    }

    private QueryWrapper<CoupleEvent> visibleQuery(Long userId) {
        QueryWrapper<CoupleEvent> queryWrapper = new QueryWrapper<>();
        Long activePartnerId = userRelationService.getActivePartnerId(userId);
        queryWrapper.and(w -> {
            w.eq("creator_id", userId).or().eq("partner_id", userId);
            if (activePartnerId != null) {
                w.or(broken -> broken.eq("creator_id", activePartnerId).eq("partner_id", activePartnerId));
            }
        });
        return queryWrapper;
    }

    private CoupleEvent getVisibleEvent(Long userId, Long eventId) {
        QueryWrapper<CoupleEvent> queryWrapper = visibleQuery(userId);
        queryWrapper.eq("id", eventId);
        CoupleEvent event = this.getOne(queryWrapper);
        if (event == null) {
            throw new RuntimeException("No permission or event not found");
        }
        return event;
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

    private LocalDate nextOccurrence(CoupleEvent event) {
        LocalDate date = event.getEventDate();
        if (event.getRepeatType() == null || event.getRepeatType() != 1) {
            return date;
        }
        LocalDate next = date.withYear(LocalDate.now().getYear());
        return next.isBefore(LocalDate.now()) ? next.plusYears(1) : next;
    }

    private Integer defaultNumber(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private boolean isSelfPartnerEvent(CoupleEvent event) {
        return event.getPartnerId() != null && event.getPartnerId().equals(event.getCreatorId());
    }

    private Long resolveSelfPartnerEventPartnerId(Long userId, CoupleEvent event) {
        if (!userId.equals(event.getCreatorId()) && userRelationService.isRelated(event.getCreatorId(), userId)) {
            return userId;
        }
        return userRelationService.getActivePartnerId(event.getCreatorId());
    }

    private void validateEvent(CoupleEvent event) {
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Title is required");
        }
        if (event.getEventDate() == null) {
            throw new RuntimeException("Event date is required");
        }
    }
}
