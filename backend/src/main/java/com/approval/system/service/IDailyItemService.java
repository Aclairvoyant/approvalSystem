package com.approval.system.service;

import com.approval.system.entity.DailyItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IDailyItemService extends IService<DailyItem> {
    DailyItem createItem(Long userId, DailyItem item);

    DailyItem updateItem(Long userId, Long itemId, DailyItem item);

    DailyItem completeItem(Long userId, Long itemId);

    DailyItem archiveItem(Long userId, Long itemId);

    List<DailyItem> listVisibleItems(Long userId, Integer status, Integer itemType, Boolean todayOnly);

    List<DailyItem> listTodayItems(Long userId);

    List<DailyItem> listOverdueItems(Long userId);

    void deleteItem(Long userId, Long itemId);
}
