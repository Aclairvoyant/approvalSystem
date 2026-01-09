package com.approval.system.mapper;

import com.approval.system.entity.MahjongUserStats;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 麻将用户统计Mapper
 */
@Mapper
public interface MahjongUserStatsMapper extends BaseMapper<MahjongUserStats> {

    /**
     * 根据用户ID查询统计
     */
    @Select("SELECT * FROM mahjong_user_stats WHERE user_id = #{userId}")
    MahjongUserStats selectByUserId(@Param("userId") Long userId);
}
