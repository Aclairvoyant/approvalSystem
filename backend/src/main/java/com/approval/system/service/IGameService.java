package com.approval.system.service;

import com.approval.system.dto.GameResponse;
import com.approval.system.entity.Game;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 游戏服务接口
 */
public interface IGameService extends IService<Game> {

    /**
     * 创建游戏房间（必须与对方互为对象）
     */
    GameResponse createGame(Long userId, Long opponentUserId);

    /**
     * 创建游戏房间（支持自定义任务位置）
     */
    GameResponse createGame(Long userId, Long opponentUserId, List<Integer> taskPositions);

    /**
     * 更新游戏的任务位置配置（仅房主在等待状态可用）
     */
    GameResponse updateTaskPositions(Long gameId, Long userId, List<Integer> taskPositions);

    /**
     * 通过房间号加入游戏
     */
    GameResponse joinGame(Long userId, String gameCode);

    /**
     * 掷骰子
     */
    Integer rollDice(Long gameId, Long userId);

    /**
     * 移动棋子
     */
    GameResponse movePiece(Long gameId, Long userId, Integer pieceIndex, Integer diceResult);

    /**
     * 获取游戏详情
     */
    GameResponse getGameDetail(Long gameId, Long userId);

    /**
     * 获取用户的游戏列表（进行中+历史）
     */
    Page<GameResponse> getUserGames(Long userId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 结束游戏
     */
    void endGame(Long gameId, Long winnerId);

    /**
     * 检查是否轮到该玩家
     */
    boolean isPlayerTurn(Long gameId, Long userId);

    /**
     * 验证玩家权限（是否是游戏中的玩家）
     */
    boolean validateGamePlayer(Long gameId, Long userId);

    /**
     * 游戏心跳（更新最后操作时间）
     */
    void updateLastMoveTime(Long gameId);

    /**
     * 取消游戏（房主可以取消等待中的游戏）
     */
    void cancelGame(Long gameId, Long userId);

    /**
     * 房主强制结束游戏（进行中的游戏）
     */
    void forceEndGame(Long gameId, Long userId);

    /**
     * 根据游戏ID获取游戏实体
     */
    Game getGameById(Long gameId);
}
