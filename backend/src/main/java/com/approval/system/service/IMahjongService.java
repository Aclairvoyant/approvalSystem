package com.approval.system.service;

import com.approval.system.dto.MahjongActionRequest;
import com.approval.system.dto.MahjongCreateRequest;
import com.approval.system.dto.MahjongGameResponse;
import com.approval.system.dto.MahjongJoinRequest;

import java.util.List;

/**
 * 麻将游戏服务接口
 */
public interface IMahjongService {

    /**
     * 创建游戏房间
     * @param request 创建请求
     * @param userId 创建者ID
     * @return 游戏响应
     */
    MahjongGameResponse createGame(MahjongCreateRequest request, Long userId);

    /**
     * 加入游戏
     * @param request 加入请求
     * @param userId 玩家ID
     * @return 游戏响应
     */
    MahjongGameResponse joinGame(MahjongJoinRequest request, Long userId);

    /**
     * 离开游戏
     * @param gameId 游戏ID
     * @param userId 玩家ID
     */
    void leaveGame(Long gameId, Long userId);

    /**
     * 开始游戏
     * @param gameId 游戏ID
     * @param userId 请求者ID（必须是房主）
     * @return 游戏响应
     */
    MahjongGameResponse startGame(Long gameId, Long userId);

    /**
     * 获取游戏状态
     * @param gameId 游戏ID
     * @param userId 请求者ID
     * @return 游戏响应
     */
    MahjongGameResponse getGameState(Long gameId, Long userId);

    /**
     * 通过房间号获取游戏
     * @param gameCode 房间号
     * @param userId 请求者ID
     * @return 游戏响应
     */
    MahjongGameResponse getGameByCode(String gameCode, Long userId);

    /**
     * 执行玩家操作
     * @param gameId 游戏ID
     * @param userId 玩家ID
     * @param request 操作请求
     * @return 游戏响应
     */
    MahjongGameResponse executeAction(Long gameId, Long userId, MahjongActionRequest request);

    /**
     * 获取用户的游戏列表
     * @param userId 用户ID
     * @return 游戏列表
     */
    List<MahjongGameResponse> getUserGames(Long userId);

    /**
     * 获取用户正在进行的游戏
     * @param userId 用户ID
     * @return 游戏响应，如果没有则返回null
     */
    MahjongGameResponse getActiveGame(Long userId);

    /**
     * 获取玩家在游戏中的座位号
     * @param gameId 游戏ID
     * @param userId 用户ID
     * @return 座位号(1-4)，如果不在游戏中返回0
     */
    int getPlayerSeat(Long gameId, Long userId);

    /**
     * 准备下一局
     * @param gameId 游戏ID
     * @param userId 请求者ID
     * @return 游戏响应
     */
    MahjongGameResponse nextRound(Long gameId, Long userId);
}
