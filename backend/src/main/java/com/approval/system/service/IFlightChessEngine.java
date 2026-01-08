package com.approval.system.service;

import com.approval.system.dto.GameResponse;

import java.util.List;

/**
 * 飞行棋游戏引擎接口
 * 负责处理飞行棋的核心游戏规则
 */
public interface IFlightChessEngine {

    /**
     * 初始化棋盘（所有棋子在基地 position=0）
     * @param player1Id 玩家1的ID
     * @param player2Id 玩家2的ID
     * @return 初始化后的游戏响应对象
     */
    GameResponse initializeBoard(Long player1Id, Long player2Id);

    /**
     * 计算棋子移动后的位置
     * @param currentPosition 当前位置
     * @param steps 骰子点数
     * @param playerNumber 玩家编号 1 or 2
     * @return 新位置，如果无法移动返回-1
     */
    int calculateNewPosition(int currentPosition, int steps, int playerNumber);

    /**
     * 检查是否可以出基地（必须掷到6）
     * @param diceResult 骰子结果
     * @return true表示可以出基地
     */
    boolean canLeaveBattlebase(int diceResult);

    /**
     * 检查指定棋子是否可以移动
     * @param pieces 玩家的所有棋子位置
     * @param pieceIndex 棋子索引（0-3）
     * @param diceResult 骰子结果
     * @return true表示可以移动
     */
    boolean canMovePiece(List<Integer> pieces, int pieceIndex, int diceResult);

    /**
     * 检查是否吃子（两个不同玩家的棋子在同一位置）
     * @param game 游戏状态
     * @param position 位置
     * @param playerNumber 当前玩家编号
     * @return true表示发生吃子
     */
    boolean checkCapture(GameResponse game, int position, int playerNumber);

    /**
     * 执行吃子操作（被吃的棋子回基地）
     * @param game 游戏状态
     * @param position 位置
     * @param opponentPlayerNumber 对方玩家编号
     */
    void executeCaptureAndSendBack(GameResponse game, int position, int opponentPlayerNumber);

    /**
     * 检查是否到达终点
     * @param position 位置
     * @return true表示到达终点
     */
    boolean isAtDestination(int position);

    /**
     * 检查是否触发飞行点（某些位置可以飞到另一个位置）
     * @param position 位置
     * @param playerNumber 玩家编号
     * @return 飞行后的位置，如果不是飞行点返回null
     */
    Integer checkFlightPoint(int position, int playerNumber);

    /**
     * 检查游戏是否结束（一方所有棋子到达终点）
     * @param game 游戏状态
     * @return true表示游戏结束
     */
    boolean isGameOver(GameResponse game);

    /**
     * 判断获胜者
     * @param game 游戏状态
     * @return 获胜者ID
     */
    Long determineWinner(GameResponse game);

    /**
     * 检查是否触发任务格子（特定位置触发任务）
     * @param position 位置
     * @return true表示是任务格子
     */
    boolean isTaskTriggerPosition(int position);

    /**
     * 获取所有任务触发位置
     * @return 任务格子位置数组
     */
    int[] getTaskTriggerPositions();

    /**
     * 设置自定义任务触发位置
     * @param positions 任务格子位置数组
     */
    void setTaskTriggerPositions(int[] positions);

    /**
     * 重置任务触发位置为默认值
     */
    void resetTaskTriggerPositions();

    /**
     * 检查是否有棋子可以移动
     * @param pieces 玩家的棋子位置
     * @param diceResult 骰子结果
     * @return true表示有棋子可以移动
     */
    boolean hasMovablePiece(List<Integer> pieces, int diceResult);
}
