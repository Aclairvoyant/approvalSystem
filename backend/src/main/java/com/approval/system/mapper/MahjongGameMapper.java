package com.approval.system.mapper;

import com.approval.system.entity.MahjongGame;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 麻将游戏Mapper
 */
@Mapper
public interface MahjongGameMapper extends BaseMapper<MahjongGame> {

    /**
     * 根据房间号查询游戏
     */
    @Select("SELECT * FROM mahjong_games WHERE game_code = #{gameCode}")
    MahjongGame selectByGameCode(@Param("gameCode") String gameCode);

    /**
     * 查询用户参与的游戏列表
     */
    @Select("SELECT * FROM mahjong_games " +
            "WHERE player1_id = #{userId} OR player2_id = #{userId} " +
            "OR player3_id = #{userId} OR player4_id = #{userId} " +
            "ORDER BY created_at DESC")
    List<MahjongGame> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户参与的指定状态的游戏
     */
    @Select("SELECT * FROM mahjong_games " +
            "WHERE (player1_id = #{userId} OR player2_id = #{userId} " +
            "OR player3_id = #{userId} OR player4_id = #{userId}) " +
            "AND game_status = #{status} " +
            "ORDER BY created_at DESC")
    List<MahjongGame> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 检查房间号是否存在
     */
    @Select("SELECT COUNT(*) FROM mahjong_games WHERE game_code = #{gameCode}")
    int countByGameCode(@Param("gameCode") String gameCode);
}
