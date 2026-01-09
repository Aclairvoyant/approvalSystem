package com.approval.system.mapper;

import com.approval.system.entity.MahjongAction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 麻将操作记录Mapper
 */
@Mapper
public interface MahjongActionMapper extends BaseMapper<MahjongAction> {

    /**
     * 获取单局的所有操作记录
     */
    @Select("SELECT * FROM mahjong_actions " +
            "WHERE round_id = #{roundId} " +
            "ORDER BY created_at ASC")
    List<MahjongAction> selectByRoundId(@Param("roundId") Long roundId);

    /**
     * 获取单局的最后一条操作
     */
    @Select("SELECT * FROM mahjong_actions " +
            "WHERE round_id = #{roundId} " +
            "ORDER BY created_at DESC LIMIT 1")
    MahjongAction selectLastAction(@Param("roundId") Long roundId);
}
