package com.approval.system.mapper;

import com.approval.system.entity.MahjongRound;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 麻将单局记录Mapper
 */
@Mapper
public interface MahjongRoundMapper extends BaseMapper<MahjongRound> {

    /**
     * 获取游戏的当前进行中的局
     */
    @Select("SELECT * FROM mahjong_rounds " +
            "WHERE game_id = #{gameId} AND round_status = 1 " +
            "ORDER BY round_number DESC LIMIT 1")
    MahjongRound selectCurrentRound(@Param("gameId") Long gameId);

    /**
     * 获取游戏的最后一局
     */
    @Select("SELECT * FROM mahjong_rounds " +
            "WHERE game_id = #{gameId} " +
            "ORDER BY round_number DESC LIMIT 1")
    MahjongRound selectLastRound(@Param("gameId") Long gameId);

    /**
     * 获取游戏的所有局
     */
    @Select("SELECT * FROM mahjong_rounds " +
            "WHERE game_id = #{gameId} " +
            "ORDER BY round_number ASC")
    List<MahjongRound> selectByGameId(@Param("gameId") Long gameId);

    /**
     * 获取指定局
     */
    @Select("SELECT * FROM mahjong_rounds " +
            "WHERE game_id = #{gameId} AND round_number = #{roundNumber}")
    MahjongRound selectByGameIdAndRoundNumber(@Param("gameId") Long gameId, @Param("roundNumber") Integer roundNumber);
}
