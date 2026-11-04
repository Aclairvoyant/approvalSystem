package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MahjongSeatResponse {

    private Integer seatIndex;
    /** EMPTY / HUMAN / BOT，见 MahjongSeatTypeEnum */
    private String type;
    private Long userId;
    private String name;
    private String avatar;
    private Integer totalScore;
    private Boolean owner;
}
