package com.approval.system.dto;

import com.approval.system.common.enums.MahjongModeEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongCreateRequest {

    @NotNull(message = "请选择玩法")
    private MahjongModeEnum mode;

    @NotNull(message = "请选择座位数")
    @Min(value = 2, message = "座位数只能是 2 到 4")
    @Max(value = 4, message = "座位数只能是 2 到 4")
    private Integer seatCount;

    /** 每个付分人每局最多付多少分；不传用默认 128，0 表示不封顶 */
    @Min(value = 0, message = "封顶分数不能小于 0")
    @Max(value = 100000, message = "封顶分数不能超过 100000")
    private Integer capPoints;

    /** 可选：直接邀请自己的对象（必须互为对象） */
    private Long inviteUserId;
}
