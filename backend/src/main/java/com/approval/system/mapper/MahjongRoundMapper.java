package com.approval.system.mapper;

import com.approval.system.entity.MahjongRound;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 麻将单局记录Mapper
 * 注意：JSON字段需要使用BaseMapper的方法（如selectOne/selectList）
 * 才能正确应用JacksonTypeHandler，不要使用@Select注解
 */
@Mapper
public interface MahjongRoundMapper extends BaseMapper<MahjongRound> {

    /**
     * 获取游戏的当前进行中的局
     * 注意：此方法需要在Service层使用selectOne + LambdaQueryWrapper实现
     */
    default MahjongRound selectCurrentRound(Long gameId) {
        // 需要在Service层实现
        return null;
    }

    /**
     * 获取游戏的最后一局
     * 注意：此方法需要在Service层使用selectOne + LambdaQueryWrapper实现
     */
    default MahjongRound selectLastRound(Long gameId) {
        // 需要在Service层实现
        return null;
    }
}
