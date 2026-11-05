package com.approval.system.service.mahjong;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结算明细里的一行，例如「花牌 ×3 = 3」「清一色 = 2」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MahjongScoreItem {
    private String name;
    private int value;
}
