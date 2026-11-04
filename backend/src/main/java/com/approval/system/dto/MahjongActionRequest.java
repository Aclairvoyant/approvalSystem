package com.approval.system.dto;

import com.approval.system.service.mahjong.MahjongActionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 牌局操作：出牌 / 敲 / 胡 / 杠 / 碰 / 吃 / 过 / 取消托管。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongActionRequest {

    @NotNull(message = "请选择操作")
    private MahjongActionType type;

    /** 出牌、敲、自己回合杠的那张牌（编码见 MahjongTiles） */
    @Min(value = 11, message = "牌不存在")
    @Max(value = 58, message = "牌不存在")
    private Integer tile;

    /** 吃：顺子里最小的那张 */
    @Min(value = 11, message = "牌不存在")
    @Max(value = 39, message = "牌不存在")
    private Integer chiStart;
}
