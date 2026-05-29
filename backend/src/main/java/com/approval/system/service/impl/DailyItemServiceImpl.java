package com.approval.system.service.impl;

import com.approval.system.entity.DailyItem;
import com.approval.system.mapper.DailyItemMapper;
import com.approval.system.service.IDailyItemService;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DailyItemServiceImpl extends ServiceImpl<DailyItemMapper, DailyItem> implements IDailyItemService {

    @Autowired
    private IUserRelationService userRelationService;

    @Override
    @Transactional
    public DailyItem createItem(Long userId, DailyItem item) {
        validateTitle(item.getTitle());
        Long partnerId = resolvePartnerId(userId, item.getPartnerId());
        LocalDateTime now = LocalDateTime.now();

        DailyItem dailyItem = DailyItem.builder()
                .creatorId(userId)
                .partnerId(partnerId)
                .itemType(defaultNumber(item.getItemType(), 1))
                .title(item.getTitle().trim())
                .content(item.getContent())
                .status(1)
                .priority(defaultNumber(item.getPriority(), 1))
                .targetDate(item.getTargetDate())
                .createdAt(now)
                .updatedAt(now)
                .build();

        this.save(dailyItem);
        return dailyItem;
    }

    @Override
    @Transactional
    public DailyItem updateItem(Long userId, Long itemId, DailyItem item) {
        DailyItem existing = getVisibleItem(userId, itemId);
        validateTitle(item.getTitle());

        existing.setTitle(item.getTitle().trim());
        existing.setContent(item.getContent());
        existing.setItemType(defaultNumber(item.getItemType(), existing.getItemType()));
        existing.setPriority(defaultNumber(item.getPriority(), existing.getPriority()));
        existing.setTargetDate(item.getTargetDate());
        existing.setPartnerId(resolvePartnerId(userId, item.getPartnerId()));
        existing.setUpdatedAt(LocalDateTime.now());
        this.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public DailyItem completeItem(Long userId, Long itemId) {
        DailyItem item = getVisibleItem(userId, itemId);
        item.setStatus(2);
        item.setCompletedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        this.updateById(item);
        return item;
    }

    @Override
    @Transactional
    public DailyItem archiveItem(Long userId, Long itemId) {
        DailyItem item = getVisibleItem(userId, itemId);
        item.setStatus(3);
        item.setUpdatedAt(LocalDateTime.now());
        this.updateById(item);
        return item;
    }

    @Override
    public List<DailyItem> listVisibleItems(Long userId, Integer status, Integer itemType, Boolean todayOnly) {
        QueryWrapper<DailyItem> queryWrapper = visibleQuery(userId);
        if (status != null) {
            queryWrapper.eq("status", status);
        } else {
            queryWrapper.ne("status", 3);
        }
        if (itemType != null) {
            queryWrapper.eq("item_type", itemType);
        }
        if (Boolean.TRUE.equals(todayOnly)) {
            queryWrapper.eq("target_date", LocalDate.now());
        }
        queryWrapper.orderByAsc("target_date");
        queryWrapper.orderByDesc("priority");
        queryWrapper.orderByDesc("created_at");
        return this.list(queryWrapper);
    }

    @Override
    public List<DailyItem> listTodayItems(Long userId) {
        QueryWrapper<DailyItem> queryWrapper = visibleQuery(userId);
        queryWrapper.eq("status", 1);
        queryWrapper.eq("target_date", LocalDate.now());
        queryWrapper.orderByDesc("priority");
        queryWrapper.orderByDesc("created_at");
        queryWrapper.last("limit 5");
        return this.list(queryWrapper);
    }

    @Override
    public List<DailyItem> listOverdueItems(Long userId) {
        QueryWrapper<DailyItem> queryWrapper = visibleQuery(userId);
        queryWrapper.eq("status", 1);
        queryWrapper.lt("target_date", LocalDate.now());
        queryWrapper.orderByAsc("target_date");
        queryWrapper.last("limit 5");
        return this.list(queryWrapper);
    }

    @Override
    @Transactional
    public void deleteItem(Long userId, Long itemId) {
        DailyItem item = getVisibleItem(userId, itemId);
        this.removeById(item.getId());
    }

    private QueryWrapper<DailyItem> visibleQuery(Long userId) {
        QueryWrapper<DailyItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("creator_id", userId).or().eq("partner_id", userId));
        return queryWrapper;
    }

    private DailyItem getVisibleItem(Long userId, Long itemId) {
        QueryWrapper<DailyItem> queryWrapper = visibleQuery(userId);
        queryWrapper.eq("id", itemId);
        DailyItem item = this.getOne(queryWrapper);
        if (item == null) {
            throw new RuntimeException("No permission or item not found");
        }
        return item;
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

    private Integer defaultNumber(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new RuntimeException("Title is required");
        }
    }
}
