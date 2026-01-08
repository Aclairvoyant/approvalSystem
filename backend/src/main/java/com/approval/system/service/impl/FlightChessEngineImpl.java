package com.approval.system.service.impl;

import com.approval.system.dto.GameResponse;
import com.approval.system.service.IFlightChessEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 飞行棋游戏引擎实现类
 * 实现飞行棋的核心游戏规则
 */
@Slf4j
@Service
public class FlightChessEngineImpl implements IFlightChessEngine {

    // 飞行棋常量
    private static final int BOARD_SIZE = 52;  // 棋盘格子数
    private static final int PIECES_PER_PLAYER = 2;  // 每个玩家的棋子数
    private static final int BASE_POSITION = 0;  // 基地位置
    private static final int DESTINATION = 100;  // 终点位置
    private static final int DICE_TO_LEAVE_BASE_1 = 6;  // 出基地需要的骰子点数1
    private static final int DICE_TO_LEAVE_BASE_2 = 5;  // 出基地需要的骰子点数2

    // 默认任务触发位置（10个特殊格子）
    private static final int[] DEFAULT_TASK_POSITIONS = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};

    // 当前任务位置（可自定义）
    private int[] taskPositions = DEFAULT_TASK_POSITIONS;

    // 飞行点（简化版，某些位置可以飞跃）- 可选功能
    // private static final Map<Integer, Integer> FLIGHT_POINTS = Map.of(17, 34);

    @Override
    public GameResponse initializeBoard(Long player1Id, Long player2Id) {
        log.info("初始化棋盘: player1={}, player2={}", player1Id, player2Id);

        // 所有棋子初始在基地（位置0）- 每个玩家2个棋子
        List<Integer> player1Pieces = Arrays.asList(0, 0);
        List<Integer> player2Pieces = Arrays.asList(0, 0);

        return GameResponse.builder()
                .player1Id(player1Id)
                .player2Id(player2Id)
                .player1Pieces(player1Pieces)
                .player2Pieces(player2Pieces)
                .currentTurn(1)  // 玩家1先手
                .build();
    }

    @Override
    public int calculateNewPosition(int currentPosition, int steps, int playerNumber) {
        // 如果在基地（position=0），不能通过普通移动出基地
        if (currentPosition == BASE_POSITION) {
            return -1;
        }

        // 如果已经到达终点，不能再移动
        if (currentPosition == DESTINATION) {
            return -1;
        }

        // 计算新位置
        int newPosition = currentPosition + steps;

        // 检查是否超出棋盘
        if (newPosition > BOARD_SIZE) {
            // 超出棋盘，尝试进入终点区域
            int overSteps = newPosition - BOARD_SIZE;
            // 简化处理：超出棋盘后直接到达终点
            if (overSteps <= 6) {
                return DESTINATION;
            } else {
                // 超出太多，不能移动
                return -1;
            }
        }

        return newPosition;
    }

    @Override
    public boolean canLeaveBattlebase(int diceResult) {
        // 投到5或6都可以出基地
        return diceResult == DICE_TO_LEAVE_BASE_1 || diceResult == DICE_TO_LEAVE_BASE_2;
    }

    @Override
    public boolean canMovePiece(List<Integer> pieces, int pieceIndex, int diceResult) {
        if (pieceIndex < 0 || pieceIndex >= PIECES_PER_PLAYER) {
            return false;
        }

        int currentPosition = pieces.get(pieceIndex);

        // 如果在基地，必须掷到6才能出
        if (currentPosition == BASE_POSITION) {
            return canLeaveBattlebase(diceResult);
        }

        // 如果已经到达终点，不能再移动
        if (currentPosition == DESTINATION) {
            return false;
        }

        // 检查移动后是否超出棋盘
        int newPosition = currentPosition + diceResult;
        if (newPosition > BOARD_SIZE + 6) {
            // 超出太多，不能移动
            return false;
        }

        return true;
    }

    @Override
    public boolean checkCapture(GameResponse game, int position, int playerNumber) {
        if (position <= BASE_POSITION || position >= DESTINATION) {
            // 基地和终点不能吃子
            return false;
        }

        // 获取对方的棋子
        List<Integer> opponentPieces = playerNumber == 1 ? game.getPlayer2Pieces() : game.getPlayer1Pieces();

        // 检查对方是否有棋子在这个位置
        for (Integer opponentPos : opponentPieces) {
            if (opponentPos.equals(position)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void executeCaptureAndSendBack(GameResponse game, int position, int opponentPlayerNumber) {
        List<Integer> opponentPieces = opponentPlayerNumber == 1 ?
            new ArrayList<>(game.getPlayer1Pieces()) :
            new ArrayList<>(game.getPlayer2Pieces());

        // 将对方在该位置的所有棋子送回基地
        for (int i = 0; i < opponentPieces.size(); i++) {
            if (opponentPieces.get(i).equals(position)) {
                opponentPieces.set(i, BASE_POSITION);
                log.info("玩家{}的棋子{}被吃，回到基地", opponentPlayerNumber, i);
            }
        }

        // 更新游戏状态
        if (opponentPlayerNumber == 1) {
            game.setPlayer1Pieces(opponentPieces);
        } else {
            game.setPlayer2Pieces(opponentPieces);
        }
    }

    @Override
    public boolean isAtDestination(int position) {
        return position == DESTINATION;
    }

    @Override
    public Integer checkFlightPoint(int position, int playerNumber) {
        // 简化版暂不实现飞行点功能
        // 如需实现，可以定义特定位置的飞行规则
        // 例如：if (position == 17) return 34;
        return null;
    }

    @Override
    public boolean isGameOver(GameResponse game) {
        // 检查玩家1是否所有棋子到达终点
        boolean player1Wins = game.getPlayer1Pieces().stream()
                .allMatch(pos -> pos == DESTINATION);

        // 检查玩家2是否所有棋子到达终点
        boolean player2Wins = game.getPlayer2Pieces().stream()
                .allMatch(pos -> pos == DESTINATION);

        return player1Wins || player2Wins;
    }

    @Override
    public Long determineWinner(GameResponse game) {
        // 检查玩家1是否胜利
        boolean player1Wins = game.getPlayer1Pieces().stream()
                .allMatch(pos -> pos == DESTINATION);

        if (player1Wins) {
            return game.getPlayer1Id();
        }

        // 检查玩家2是否胜利
        boolean player2Wins = game.getPlayer2Pieces().stream()
                .allMatch(pos -> pos == DESTINATION);

        if (player2Wins) {
            return game.getPlayer2Id();
        }

        return null;  // 游戏未结束
    }

    @Override
    public boolean isTaskTriggerPosition(int position) {
        return Arrays.stream(taskPositions).anyMatch(pos -> pos == position);
    }

    @Override
    public int[] getTaskTriggerPositions() {
        return taskPositions.clone();
    }

    @Override
    public void setTaskTriggerPositions(int[] positions) {
        if (positions != null && positions.length > 0) {
            this.taskPositions = positions.clone();
            log.info("设置自定义任务位置: {}", Arrays.toString(this.taskPositions));
        }
    }

    @Override
    public void resetTaskTriggerPositions() {
        this.taskPositions = DEFAULT_TASK_POSITIONS;
        log.info("重置任务位置为默认值");
    }

    /**
     * 辅助方法：从基地出发（掷到6后）
     * @return 出基地后的初始位置
     */
    public int leaveBasePosition() {
        return 1;  // 出基地后到达位置1
    }

    /**
     * 辅助方法：获取玩家所有可移动的棋子索引
     * @param pieces 玩家的棋子位置
     * @param diceResult 骰子结果
     * @return 可移动的棋子索引列表
     */
    public List<Integer> getMovablePieces(List<Integer> pieces, int diceResult) {
        List<Integer> movablePieces = new ArrayList<>();

        for (int i = 0; i < pieces.size(); i++) {
            if (canMovePiece(pieces, i, diceResult)) {
                movablePieces.add(i);
            }
        }

        return movablePieces;
    }

    /**
     * 辅助方法：检查是否有棋子可以移动
     * @param pieces 玩家的棋子位置
     * @param diceResult 骰子结果
     * @return true表示有棋子可以移动
     */
    @Override
    public boolean hasMovablePiece(List<Integer> pieces, int diceResult) {
        return !getMovablePieces(pieces, diceResult).isEmpty();
    }
}
