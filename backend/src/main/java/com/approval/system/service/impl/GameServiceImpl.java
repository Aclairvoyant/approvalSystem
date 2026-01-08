package com.approval.system.service.impl;

import com.approval.system.common.enums.GameMoveTypeEnum;
import com.approval.system.common.enums.GameStatusEnum;
import com.approval.system.dto.GameResponse;
import com.approval.system.dto.GameTaskResponse;
import com.approval.system.entity.Game;
import com.approval.system.entity.GameMove;
import com.approval.system.entity.User;
import com.approval.system.mapper.GameMapper;
import com.approval.system.mapper.GameMoveMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IFlightChessEngine;
import com.approval.system.service.IGameService;
import com.approval.system.service.IGameTaskService;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GameServiceImpl extends ServiceImpl<GameMapper, Game> implements IGameService {

    @Autowired
    private IUserRelationService userRelationService;

    @Autowired
    private IFlightChessEngine chessEngine;

    @Autowired
    private GameMoveMapper gameMoveMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IGameTaskService gameTaskService;

    private final Random random = new Random();

    /**
     * 默认任务位置
     */
    private static final List<Integer> DEFAULT_TASK_POSITIONS = Arrays.asList(5, 10, 15, 20, 25, 30, 35, 40, 45, 50);

    @Override
    @Transactional
    public GameResponse createGame(Long userId, Long opponentUserId) {
        return createGame(userId, opponentUserId, null);
    }

    @Override
    @Transactional
    public GameResponse createGame(Long userId, Long opponentUserId, List<Integer> taskPositions) {
        log.info("创建游戏: userId={}, opponentUserId={}, taskPositions={}", userId, opponentUserId, taskPositions);

        // 验证是否互为对象
        if (!userRelationService.isRelated(userId, opponentUserId)) {
            throw new RuntimeException("只有互为对象的用户才能一起玩游戏");
        }

        // 检查是否有未完成的游戏
        QueryWrapper<Game> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("player1_id", userId).or().eq("player2_id", userId));
        queryWrapper.in("game_status", GameStatusEnum.WAITING.getCode(), GameStatusEnum.PLAYING.getCode());
        Game existingGame = this.getOne(queryWrapper);
        if (existingGame != null) {
            throw new RuntimeException("您有未完成的游戏，请先结束当前游戏");
        }

        // 生成6位随机房间号
        String gameCode = generateGameCode();

        // 初始化棋子位置
        List<Integer> initialPieces = Arrays.asList(0, 0, 0, 0);

        // 使用自定义任务位置或默认位置
        List<Integer> finalTaskPositions = (taskPositions != null && !taskPositions.isEmpty())
                ? taskPositions
                : DEFAULT_TASK_POSITIONS;

        // 创建游戏
        Game game = Game.builder()
                .gameCode(gameCode)
                .player1Id(userId)
                .player2Id(null)  // 等待对方加入
                .currentTurn(1)
                .gameStatus(GameStatusEnum.WAITING.getCode())
                .player1Pieces(initialPieces)
                .player2Pieces(initialPieces)
                .taskPositions(finalTaskPositions)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        this.save(game);
        log.info("游戏创建成功: gameId={}, gameCode={}, taskPositions={}", game.getId(), gameCode, finalTaskPositions);

        return convertToResponse(game);
    }

    @Override
    @Transactional
    public GameResponse updateTaskPositions(Long gameId, Long userId, List<Integer> taskPositions) {
        Game game = getGameById(gameId);

        // 只有房主可以修改任务位置
        if (!game.getPlayer1Id().equals(userId)) {
            throw new RuntimeException("只有房主可以修改任务位置");
        }

        // 只有等待中的游戏可以修改
        if (!game.getGameStatus().equals(GameStatusEnum.WAITING.getCode())) {
            throw new RuntimeException("只能在等待状态修改任务位置");
        }

        // 验证任务位置有效性（1-52之间）
        if (taskPositions != null && !taskPositions.isEmpty()) {
            for (Integer pos : taskPositions) {
                if (pos < 1 || pos > 52) {
                    throw new RuntimeException("任务位置必须在1-52之间");
                }
            }
        }

        game.setTaskPositions(taskPositions != null && !taskPositions.isEmpty() ? taskPositions : DEFAULT_TASK_POSITIONS);
        game.setUpdatedAt(LocalDateTime.now());
        this.updateById(game);

        log.info("更新任务位置: gameId={}, taskPositions={}", gameId, taskPositions);

        return convertToResponse(game);
    }

    @Override
    @Transactional
    public GameResponse joinGame(Long userId, String gameCode) {
        log.info("加入游戏: userId={}, gameCode={}", userId, gameCode);

        // 查找游戏
        QueryWrapper<Game> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("game_code", gameCode);
        queryWrapper.eq("game_status", GameStatusEnum.WAITING.getCode());
        Game game = this.getOne(queryWrapper);

        if (game == null) {
            throw new RuntimeException("游戏房间不存在或已开始");
        }

        // 不能加入自己创建的游戏
        if (game.getPlayer1Id().equals(userId)) {
            throw new RuntimeException("不能加入自己创建的游戏");
        }

        // 验证是否互为对象
        if (!userRelationService.isRelated(userId, game.getPlayer1Id())) {
            throw new RuntimeException("只有互为对象的用户才能一起玩游戏");
        }

        // 获取任务位置
        List<Integer> taskPositions = game.getTaskPositions();
        if (taskPositions == null || taskPositions.isEmpty()) {
            taskPositions = DEFAULT_TASK_POSITIONS;
        }

        // 为每个任务位置分配随机任务
        List<GameTaskResponse> randomTasks = gameTaskService.getRandomTasks(taskPositions.size());
        Map<String, Object> taskAssignments = new HashMap<>();
        for (int i = 0; i < taskPositions.size() && i < randomTasks.size(); i++) {
            taskAssignments.put(String.valueOf(taskPositions.get(i)), randomTasks.get(i).getId());
        }

        // 更新游戏状态
        game.setPlayer2Id(userId);
        game.setGameStatus(GameStatusEnum.PLAYING.getCode());
        game.setStartedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());
        game.setTaskPositions(taskPositions);
        game.setTaskAssignments(taskAssignments);
        // 随机决定谁先手
        game.setCurrentTurn(random.nextBoolean() ? 1 : 2);

        this.updateById(game);
        log.info("玩家加入游戏: gameId={}, player2Id={}, taskAssignments={}", game.getId(), userId, taskAssignments);

        return convertToResponse(game);
    }

    @Override
    public Integer rollDice(Long gameId, Long userId) {
        Game game = getGameById(gameId);

        // 验证是否轮到该玩家
        if (!isPlayerTurn(gameId, userId)) {
            throw new RuntimeException("还没轮到你");
        }

        // 掷骰子
        int diceResult = random.nextInt(6) + 1;

        // 记录操作
        GameMove move = GameMove.builder()
                .gameId(gameId)
                .playerId(userId)
                .moveType(GameMoveTypeEnum.ROLL_DICE.getCode())
                .diceResult(diceResult)
                .createdAt(LocalDateTime.now())
                .build();
        gameMoveMapper.insert(move);

        // 更新游戏状态
        game.setLastDiceResult(diceResult);
        game.setLastMoveTime(LocalDateTime.now());
        this.updateById(game);

        log.info("掷骰子: gameId={}, userId={}, result={}", gameId, userId, diceResult);
        return diceResult;
    }

    @Override
    @Transactional
    public GameResponse movePiece(Long gameId, Long userId, Integer pieceIndex, Integer diceResult) {
        Game game = getGameById(gameId);

        // 验证游戏状态
        if (!game.getGameStatus().equals(GameStatusEnum.PLAYING.getCode())) {
            throw new RuntimeException("游戏未开始或已结束");
        }

        // 验证是否轮到该玩家
        if (!isPlayerTurn(gameId, userId)) {
            throw new RuntimeException("还没轮到你");
        }

        // 确定玩家编号
        int playerNumber = game.getPlayer1Id().equals(userId) ? 1 : 2;
        List<Integer> playerPieces = playerNumber == 1 ?
                new ArrayList<>(game.getPlayer1Pieces()) :
                new ArrayList<>(game.getPlayer2Pieces());

        // 验证棋子索引
        if (pieceIndex < 0 || pieceIndex >= 4) {
            throw new RuntimeException("无效的棋子索引");
        }

        int currentPosition = playerPieces.get(pieceIndex);
        int newPosition;

        // 如果在基地，检查是否可以出基地
        if (currentPosition == 0) {
            if (!chessEngine.canLeaveBattlebase(diceResult)) {
                throw new RuntimeException("必须掷到6才能出基地");
            }
            newPosition = 1;  // 出基地到位置1
        } else {
            // 计算新位置
            newPosition = chessEngine.calculateNewPosition(currentPosition, diceResult, playerNumber);
            if (newPosition == -1) {
                throw new RuntimeException("无法移动该棋子");
            }
        }

        // 记录移动前位置
        int fromPosition = currentPosition;

        // 更新棋子位置
        playerPieces.set(pieceIndex, newPosition);

        // 检查是否吃子
        GameResponse gameResponse = convertToResponse(game);
        if (playerNumber == 1) {
            gameResponse.setPlayer1Pieces(playerPieces);
        } else {
            gameResponse.setPlayer2Pieces(playerPieces);
        }

        Integer capturedPieceIndex = null;
        if (chessEngine.checkCapture(gameResponse, newPosition, playerNumber)) {
            int opponentNumber = playerNumber == 1 ? 2 : 1;
            List<Integer> opponentPieces = opponentNumber == 1 ?
                    new ArrayList<>(game.getPlayer1Pieces()) :
                    new ArrayList<>(game.getPlayer2Pieces());

            // 找到被吃的棋子
            for (int i = 0; i < opponentPieces.size(); i++) {
                if (opponentPieces.get(i).equals(newPosition)) {
                    capturedPieceIndex = i;
                    opponentPieces.set(i, 0);  // 回基地
                    log.info("吃子: 玩家{}的棋子{}被吃", opponentNumber, i);
                    break;
                }
            }

            // 更新对方棋子
            if (opponentNumber == 1) {
                game.setPlayer1Pieces(opponentPieces);
            } else {
                game.setPlayer2Pieces(opponentPieces);
            }
        }

        // 更新当前玩家棋子
        if (playerNumber == 1) {
            game.setPlayer1Pieces(playerPieces);
        } else {
            game.setPlayer2Pieces(playerPieces);
        }

        // 检查是否触发任务
        Long triggeredTaskId = null;
        if (chessEngine.isTaskTriggerPosition(newPosition)) {
            // 随机选择一个任务
            var task = gameTaskService.getRandomTask(null);
            if (task != null) {
                triggeredTaskId = task.getId();
                log.info("触发任务: position={}, taskId={}", newPosition, triggeredTaskId);
            }
        }

        // 记录移动操作
        GameMove move = GameMove.builder()
                .gameId(gameId)
                .playerId(userId)
                .moveType(GameMoveTypeEnum.MOVE_PIECE.getCode())
                .diceResult(diceResult)
                .pieceIndex(pieceIndex)
                .fromPosition(fromPosition)
                .toPosition(newPosition)
                .capturedPieceIndex(capturedPieceIndex)
                .taskId(triggeredTaskId)
                .createdAt(LocalDateTime.now())
                .build();
        gameMoveMapper.insert(move);

        // 切换回合（如果掷到6，不切换）
        if (diceResult != 6) {
            game.setCurrentTurn(game.getCurrentTurn() == 1 ? 2 : 1);
        }

        game.setLastMoveTime(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());
        this.updateById(game);

        log.info("移动棋子: gameId={}, userId={}, piece={}, from={}, to={}",
                gameId, userId, pieceIndex, fromPosition, newPosition);

        return convertToResponse(game);
    }

    @Override
    public GameResponse getGameDetail(Long gameId, Long userId) {
        Game game = getGameById(gameId);

        // 验证用户是否是游戏参与者
        if (!validateGamePlayer(gameId, userId)) {
            throw new RuntimeException("您不是这场游戏的参与者");
        }

        return convertToResponse(game);
    }

    @Override
    public Page<GameResponse> getUserGames(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        QueryWrapper<Game> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("player1_id", userId).or().eq("player2_id", userId));
        if (status != null) {
            queryWrapper.eq("game_status", status);
        }
        queryWrapper.orderByDesc("created_at");

        Page<Game> page = this.page(new Page<>(pageNum, pageSize), queryWrapper);

        Page<GameResponse> responsePage = new Page<>(pageNum, pageSize, page.getTotal());
        responsePage.setRecords(page.getRecords().stream().map(this::convertToResponse).toList());

        return responsePage;
    }

    @Override
    @Transactional
    public void endGame(Long gameId, Long winnerId) {
        Game game = getGameById(gameId);

        game.setGameStatus(GameStatusEnum.FINISHED.getCode());
        game.setWinnerId(winnerId);
        game.setEndedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());

        this.updateById(game);
        log.info("游戏结束: gameId={}, winnerId={}", gameId, winnerId);
    }

    @Override
    public boolean isPlayerTurn(Long gameId, Long userId) {
        Game game = getGameById(gameId);

        if (!game.getGameStatus().equals(GameStatusEnum.PLAYING.getCode())) {
            return false;
        }

        int currentTurn = game.getCurrentTurn();
        if (currentTurn == 1 && game.getPlayer1Id().equals(userId)) {
            return true;
        }
        return currentTurn == 2 && game.getPlayer2Id().equals(userId);
    }

    @Override
    public boolean validateGamePlayer(Long gameId, Long userId) {
        Game game = getGameById(gameId);
        return game.getPlayer1Id().equals(userId) ||
                (game.getPlayer2Id() != null && game.getPlayer2Id().equals(userId));
    }

    @Override
    public void updateLastMoveTime(Long gameId) {
        Game game = getGameById(gameId);
        game.setLastMoveTime(LocalDateTime.now());
        this.updateById(game);
    }

    @Override
    @Transactional
    public void cancelGame(Long gameId, Long userId) {
        Game game = getGameById(gameId);

        // 只有房主可以取消等待中的游戏
        if (!game.getPlayer1Id().equals(userId)) {
            throw new RuntimeException("只有房主可以取消游戏");
        }

        if (!game.getGameStatus().equals(GameStatusEnum.WAITING.getCode())) {
            throw new RuntimeException("只能取消等待中的游戏");
        }

        game.setGameStatus(GameStatusEnum.CANCELLED.getCode());
        game.setUpdatedAt(LocalDateTime.now());
        this.updateById(game);

        log.info("游戏取消: gameId={}", gameId);
    }

    @Override
    @Transactional
    public void forceEndGame(Long gameId, Long userId) {
        Game game = getGameById(gameId);

        // 只有房主可以强制结束游戏
        if (!game.getPlayer1Id().equals(userId)) {
            throw new RuntimeException("只有房主可以结束游戏");
        }

        // 只能结束进行中的游戏
        if (!game.getGameStatus().equals(GameStatusEnum.PLAYING.getCode())) {
            throw new RuntimeException("只能结束进行中的游戏");
        }

        // 强制结束游戏，房主主动结束则对方获胜（或平局处理）
        // 这里设置为无胜者，表示平局/中止
        game.setGameStatus(GameStatusEnum.FINISHED.getCode());
        game.setWinnerId(null);  // 无胜者，表示游戏被中止
        game.setEndedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());
        this.updateById(game);

        log.info("游戏强制结束: gameId={}, by userId={}", gameId, userId);
    }

    @Override
    public Game getGameById(Long gameId) {
        Game game = this.getById(gameId);
        if (game == null) {
            throw new RuntimeException("游戏不存在");
        }
        return game;
    }

    /**
     * 生成6位随机房间号
     */
    private String generateGameCode() {
        String code;
        int attempts = 0;
        do {
            code = String.format("%06d", random.nextInt(1000000));
            // 检查是否已存在
            QueryWrapper<Game> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("game_code", code);
            queryWrapper.eq("game_status", GameStatusEnum.WAITING.getCode());
            if (this.count(queryWrapper) == 0) {
                break;
            }
            attempts++;
        } while (attempts < 100);

        return code;
    }

    /**
     * 转换为响应对象
     */
    private GameResponse convertToResponse(Game game) {
        User player1 = userMapper.selectById(game.getPlayer1Id());
        User player2 = game.getPlayer2Id() != null ? userMapper.selectById(game.getPlayer2Id()) : null;

        // 获取任务位置
        List<Integer> taskPositions = game.getTaskPositions();
        if (taskPositions == null || taskPositions.isEmpty()) {
            taskPositions = DEFAULT_TASK_POSITIONS;
        }

        // 构建任务位置信息
        List<GameResponse.TaskPositionInfo> taskInfos = new ArrayList<>();
        Map<String, Object> taskAssignments = game.getTaskAssignments();
        if (taskAssignments != null && !taskAssignments.isEmpty()) {
            for (Integer pos : taskPositions) {
                // 处理JSON反序列化可能返回Integer的情况
                Object taskIdObj = taskAssignments.get(String.valueOf(pos));
                Long taskId = null;
                if (taskIdObj != null) {
                    if (taskIdObj instanceof Long) {
                        taskId = (Long) taskIdObj;
                    } else if (taskIdObj instanceof Integer) {
                        taskId = ((Integer) taskIdObj).longValue();
                    } else if (taskIdObj instanceof Number) {
                        taskId = ((Number) taskIdObj).longValue();
                    }
                }

                String title = "任务";
                if (taskId != null) {
                    try {
                        GameTaskResponse task = gameTaskService.getTaskById(taskId);
                        title = task.getTitle();
                    } catch (Exception e) {
                        log.warn("获取任务信息失败: taskId={}", taskId);
                    }
                }
                taskInfos.add(GameResponse.TaskPositionInfo.builder()
                        .position(pos)
                        .taskId(taskId)
                        .title(title)
                        .build());
            }
        } else {
            // 游戏还没开始，只返回位置信息，不返回具体任务
            for (Integer pos : taskPositions) {
                taskInfos.add(GameResponse.TaskPositionInfo.builder()
                        .position(pos)
                        .taskId(null)
                        .title("任务")
                        .build());
            }
        }

        return GameResponse.builder()
                .id(game.getId())
                .gameCode(game.getGameCode())
                .player1Id(game.getPlayer1Id())
                .player1Name(player1 != null ? player1.getRealName() : null)
                .player1Avatar(player1 != null ? player1.getAvatar() : null)
                .player2Id(game.getPlayer2Id())
                .player2Name(player2 != null ? player2.getRealName() : null)
                .player2Avatar(player2 != null ? player2.getAvatar() : null)
                .currentTurn(game.getCurrentTurn())
                .gameStatus(game.getGameStatus())
                .winnerId(game.getWinnerId())
                .player1Pieces(game.getPlayer1Pieces())
                .player2Pieces(game.getPlayer2Pieces())
                .lastDiceResult(game.getLastDiceResult())
                .taskPositions(taskPositions)
                .taskInfos(taskInfos)
                .createdAt(game.getCreatedAt())
                .startedAt(game.getStartedAt())
                .endedAt(game.getEndedAt())
                .build();
    }
}
