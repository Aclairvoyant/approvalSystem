package com.approval.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建麻将游戏请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongCreateRequest {

    /** 规则类型: 1=敲麻, 2=百搭 */
    @NotNull(message = "规则类型不能为空")
    @Min(value = 1, message = "规则类型必须是1或2")
    @Max(value = 2, message = "规则类型必须是1或2")
    private Integer ruleType;

    /** 花牌模式: 8=8花, 20=20花, 36=36花 (仅百搭模式) */
    @Builder.Default
    private Integer flowerMode = 8;

    /** 玩家人数: 2/3/4 */
    @NotNull(message = "玩家人数不能为空")
    @Min(value = 2, message = "玩家人数至少2人")
    @Max(value = 4, message = "玩家人数最多4人")
    private Integer playerCount;

    /** 总局数: 4/8/16 */
    @Builder.Default
    private Integer totalRounds = 8;

    /** 底分: 1/2/5/10 */
    @Builder.Default
    private Integer baseScore = 1;

    /** 封顶分数: 20/50/100/200/null=无封顶 */
    private Integer maxScore;

    /** 飞苍蝇数量: 0-5 */
    @Builder.Default
    @Min(value = 0, message = "飞苍蝇数量不能为负")
    @Max(value = 5, message = "飞苍蝇数量最多5只")
    private Integer flyCount = 0;
}
