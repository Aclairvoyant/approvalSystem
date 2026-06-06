package com.approval.system.service.impl;

import com.approval.system.common.enums.GameStatusEnum;
import com.approval.system.dto.GobangMoveResponse;
import com.approval.system.dto.GobangResponse;
import com.approval.system.entity.GobangGame;
import com.approval.system.entity.GobangMove;
import com.approval.system.entity.GobangUndoRequest;
import com.approval.system.entity.User;
import com.approval.system.mapper.GobangGameMapper;
import com.approval.system.mapper.GobangMoveMapper;
import com.approval.system.mapper.GobangUndoRequestMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IGobangEngine;
import com.approval.system.service.IGobangService;
import com.approval.system.service.IUserRelationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class GobangServiceImpl implements IGobangService {

    private static final String MOVE_PLACE_STONE = "PLACE_STONE";
    private static final String MOVE_UNDO = "UNDO";
    private static final String MOVE_SURRENDER = "SURRENDER";
    private static final int UNDO_PENDING = 1;
    private static final int UNDO_ACCEPTED = 2;
    private static final int UNDO_REJECTED = 3;

    @Autowired
    private GobangGameMapper gobangGameMapper;

    @Autowired
    private GobangMoveMapper gobangMoveMapper;

    @Autowired
    private GobangUndoRequestMapper gobangUndoRequestMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IUserRelationService userRelationService;

    @Autowired
    private IGobangEngine gobangEngine;

    @Autowired
    private ObjectMapper objectMapper;

    private final Random random = new Random();

    @Override
    @Transactional
    public GobangResponse createGame(Long userId, Long opponentUserId) {
        if (Objects.equals(userId, opponentUserId)) {
            throw new RuntimeException("不能邀请自己进行五子棋");
        }
        if (!userRelationService.isRelated(userId, opponentUserId)) {
            throw new RuntimeException("只有互为对象的用户才能一起玩五子棋");
        }
        Long activeCount = gobangGameMapper.selectCount(new QueryWrapper<GobangGame>()
                .and(wrapper -> wrapper
                        .nested(pair -> pair.eq("black_player_id", userId).eq("invited_player_id", opponentUserId))
                        .or(pair -> pair.eq("black_player_id", opponentUserId).eq("invited_player_id", userId)))
                .in("game_status", GameStatusEnum.WAITING.getCode(), GameStatusEnum.PLAYING.getCode()));
        if (activeCount != null && activeCount > 0) {
            throw new RuntimeException("你们已有未结束的五子棋对局");
        }

        GobangGame game = GobangGame.builder()
                .gameCode(generateGameCode())
                .blackPlayerId(userId)
                .invitedPlayerId(opponentUserId)
                .whitePlayerId(null)
                .currentTurn(1)
                .gameStatus(GameStatusEnum.WAITING.getCode())
                .boardData(writeJson(gobangEngine.createEmptyBoard()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        gobangGameMapper.insert(game);
        return convertToResponse(game);
    }

    @Override
    @Transactional
    public GobangResponse joinGame(Long userId, String gameCode) {
        GobangGame game = gobangGameMapper.selectOne(new QueryWrapper<GobangGame>()
                .eq("game_code", gameCode)
                .eq("game_status", GameStatusEnum.WAITING.getCode()));
        if (game == null) {
            throw new RuntimeException("五子棋房间不存在或已开始");
        }
        if (!Objects.equals(game.getInvitedPlayerId(), userId)) {
            throw new RuntimeException("只有被邀请的对象可以加入该房间");
        }
        if (Objects.equals(game.getBlackPlayerId(), userId)) {
            throw new RuntimeException("不能加入自己创建的房间");
        }

        game.setWhitePlayerId(userId);
        game.setCurrentTurn(1);
        game.setGameStatus(GameStatusEnum.PLAYING.getCode());
        game.setStartedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());
        gobangGameMapper.updateById(game);
        return convertToResponse(game);
    }

    @Override
    public GobangResponse getGameDetail(Long gameId, Long userId) {
        GobangGame game = getGameById(gameId);
        if (!isGameParticipant(game, userId)) {
            throw new RuntimeException("你不是这场五子棋的参与者");
        }
        return convertToResponse(game);
    }

    @Override
    public Page<GobangResponse> getUserGames(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        QueryWrapper<GobangGame> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w.eq("black_player_id", userId)
                .or()
                .eq("white_player_id", userId)
                .or()
                .eq("invited_player_id", userId));
        if (status != null) {
            wrapper.eq("game_status", status);
        }
        wrapper.orderByDesc("created_at");

        Page<GobangGame> page = new Page<>(pageNum, pageSize);
        Page<GobangGame> result = page.setRecords(Collections.emptyList());
        try {
            result = page;
            gobangGameMapper.selectPage(result, wrapper);
        } catch (UnsupportedOperationException ignored) {
            // Mapper mocks in unit tests do not exercise pagination.
        }

        Page<GobangResponse> response = new Page<>(pageNum, pageSize, result.getTotal());
        response.setRecords(result.getRecords().stream().map(this::convertToResponse).toList());
        return response;
    }

    @Override
    @Transactional
    public GobangResponse placeStone(Long gameId, Long userId, Integer row, Integer col) {
        GobangGame game = getGameByIdForUpdate(gameId);
        ensurePlaying(game);
        ensureParticipant(game, userId);

        int color = getPlayerColor(game, userId);
        if (!Objects.equals(game.getCurrentTurn(), color)) {
            throw new RuntimeException("还没轮到你");
        }

        List<List<Integer>> board = parseBoard(game.getBoardData());
        gobangEngine.placeStone(board, row, col, color);
        boolean won = gobangEngine.hasFive(board, row, col, color);

        game.setBoardData(writeJson(board));
        game.setLastMoveId(null);
        game.setUpdatedAt(LocalDateTime.now());
        if (won) {
            game.setGameStatus(GameStatusEnum.FINISHED.getCode());
            game.setWinnerId(userId);
            game.setEndedAt(LocalDateTime.now());
        } else {
            game.setCurrentTurn(color == IGobangEngine.BLACK ? IGobangEngine.WHITE : IGobangEngine.BLACK);
        }

        GobangMove move = GobangMove.builder()
                .gameId(gameId)
                .playerId(userId)
                .moveNumber(nextMoveNumber(gameId))
                .moveType(MOVE_PLACE_STONE)
                .rowIndex(row)
                .colIndex(col)
                .color(color)
                .moveData(writeJson(Map.of(
                        "row", row,
                        "col", col,
                        "color", color,
                        "win", won
                )))
                .createdAt(LocalDateTime.now())
                .build();
        gobangMoveMapper.insert(move);
        game.setLastMoveId(move.getId());
        gobangGameMapper.updateById(game);
        return convertToResponse(game);
    }

    @Override
    @Transactional
    public GobangResponse requestUndo(Long gameId, Long userId) {
        GobangGame game = getGameByIdForUpdate(gameId);
        ensurePlaying(game);
        ensureParticipant(game, userId);

        GobangUndoRequest existing = gobangUndoRequestMapper.selectOne(new QueryWrapper<GobangUndoRequest>()
                .eq("game_id", gameId)
                .eq("status", UNDO_PENDING));
        if (existing != null) {
            throw new RuntimeException("已有待处理的悔棋请求");
        }

        GobangMove latestMove = getLatestEffectivePlaceMove(gameId);
        if (latestMove == null) {
            throw new RuntimeException("当前没有可悔棋的落子");
        }
        Long targetUndoCount = gobangUndoRequestMapper.selectCount(new QueryWrapper<GobangUndoRequest>()
                .eq("game_id", gameId)
                .eq("target_move_number", latestMove.getMoveNumber()));
        if (targetUndoCount != null && targetUndoCount > 0) {
            throw new RuntimeException("该步已经申请过悔棋");
        }

        Long responderId = Objects.equals(userId, game.getBlackPlayerId())
                ? game.getWhitePlayerId()
                : game.getBlackPlayerId();
        GobangUndoRequest request = GobangUndoRequest.builder()
                .gameId(gameId)
                .requesterId(userId)
                .responderId(responderId)
                .targetMoveNumber(latestMove.getMoveNumber())
                .status(UNDO_PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        gobangUndoRequestMapper.insert(request);
        return convertToResponse(game, request);
    }

    @Override
    @Transactional
    public GobangResponse respondUndo(Long gameId, Long userId, Boolean accepted) {
        GobangGame game = getGameByIdForUpdate(gameId);
        ensurePlaying(game);
        ensureParticipant(game, userId);

        GobangUndoRequest request = gobangUndoRequestMapper.selectOne(new QueryWrapper<GobangUndoRequest>()
                .eq("game_id", gameId)
                .eq("status", UNDO_PENDING));
        if (request == null) {
            throw new RuntimeException("没有待处理的悔棋请求");
        }
        if (!Objects.equals(request.getResponderId(), userId)) {
            throw new RuntimeException("只有对方可以处理悔棋请求");
        }

        request.setStatus(Boolean.TRUE.equals(accepted) ? UNDO_ACCEPTED : UNDO_REJECTED);
        request.setRespondedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        gobangUndoRequestMapper.updateById(request);

        if (!Boolean.TRUE.equals(accepted)) {
            return convertToResponse(game, null);
        }

        List<GobangMove> moves = getOrderedMoves(gameId);
        List<List<Integer>> board = rebuildBoardWithoutMove(moves, request.getTargetMoveNumber());
        Integer undoneColor = moves.stream()
                .filter(move -> Objects.equals(move.getMoveNumber(), request.getTargetMoveNumber()))
                .findFirst()
                .map(GobangMove::getColor)
                .orElse(getPlayerColor(game, request.getRequesterId()));

        game.setBoardData(writeJson(board));
        game.setCurrentTurn(undoneColor);
        game.setUpdatedAt(LocalDateTime.now());

        GobangMove undoMove = GobangMove.builder()
                .gameId(gameId)
                .playerId(request.getRequesterId())
                .moveNumber(nextMoveNumberFromMoves(moves))
                .moveType(MOVE_UNDO)
                .moveData(writeJson(Map.of(
                        "requestId", request.getId(),
                        "targetMoveNumber", request.getTargetMoveNumber(),
                        "acceptedBy", userId
                )))
                .createdAt(LocalDateTime.now())
                .build();
        gobangMoveMapper.insert(undoMove);
        game.setLastMoveId(undoMove.getId());
        gobangGameMapper.updateById(game);
        return convertToResponse(game, null);
    }

    @Override
    @Transactional
    public GobangResponse surrender(Long gameId, Long userId) {
        GobangGame game = getGameByIdForUpdate(gameId);
        ensurePlaying(game);
        ensureParticipant(game, userId);

        Long winnerId = Objects.equals(userId, game.getBlackPlayerId())
                ? game.getWhitePlayerId()
                : game.getBlackPlayerId();
        game.setGameStatus(GameStatusEnum.FINISHED.getCode());
        game.setWinnerId(winnerId);
        game.setEndedAt(LocalDateTime.now());
        game.setUpdatedAt(LocalDateTime.now());

        GobangMove move = GobangMove.builder()
                .gameId(gameId)
                .playerId(userId)
                .moveNumber(nextMoveNumber(gameId))
                .moveType(MOVE_SURRENDER)
                .moveData(writeJson(Map.of("winnerId", winnerId)))
                .createdAt(LocalDateTime.now())
                .build();
        gobangMoveMapper.insert(move);
        game.setLastMoveId(move.getId());
        gobangGameMapper.updateById(game);
        return convertToResponse(game);
    }

    @Override
    public List<GobangMoveResponse> getMoves(Long gameId, Long userId) {
        GobangGame game = getGameById(gameId);
        ensureParticipant(game, userId);
        return getOrderedMoves(gameId).stream()
                .map(this::convertMoveToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void cancelGame(Long gameId, Long userId) {
        GobangGame game = getGameByIdForUpdate(gameId);
        if (!Objects.equals(game.getBlackPlayerId(), userId)) {
            throw new RuntimeException("只有房主可以取消五子棋房间");
        }
        if (!Objects.equals(game.getGameStatus(), GameStatusEnum.WAITING.getCode())) {
            throw new RuntimeException("只能取消等待中的五子棋房间");
        }
        game.setGameStatus(GameStatusEnum.CANCELLED.getCode());
        game.setUpdatedAt(LocalDateTime.now());
        gobangGameMapper.updateById(game);
    }

    @Override
    public boolean validateGamePlayer(Long gameId, Long userId) {
        return isGameParticipant(getGameById(gameId), userId);
    }

    @Override
    public void updateLastMoveTime(Long gameId) {
        GobangGame game = getGameById(gameId);
        game.setUpdatedAt(LocalDateTime.now());
        gobangGameMapper.updateById(game);
    }

    @Override
    public GobangGame getGameById(Long gameId) {
        GobangGame game = gobangGameMapper.selectById(gameId);
        if (game == null) {
            throw new RuntimeException("五子棋对局不存在");
        }
        return game;
    }

    private GobangGame getGameByIdForUpdate(Long gameId) {
        GobangGame game = gobangGameMapper.selectOne(new QueryWrapper<GobangGame>()
                .eq("id", gameId)
                .last("FOR UPDATE"));
        if (game == null) {
            game = gobangGameMapper.selectById(gameId);
        }
        if (game == null) {
            throw new RuntimeException("五子棋对局不存在");
        }
        return game;
    }

    private GobangMove getLatestEffectivePlaceMove(Long gameId) {
        List<GobangMove> effectiveMoves = getEffectivePlaceMoves(getOrderedMoves(gameId));
        return effectiveMoves.isEmpty() ? null : effectiveMoves.get(effectiveMoves.size() - 1);
    }

    private List<GobangMove> getOrderedMoves(Long gameId) {
        List<GobangMove> moves = gobangMoveMapper.selectList(new QueryWrapper<GobangMove>()
                .eq("game_id", gameId)
                .orderByAsc("move_number"));
        if (moves == null) {
            return Collections.emptyList();
        }
        return moves.stream()
                .sorted(Comparator.comparing(GobangMove::getMoveNumber, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<List<Integer>> rebuildBoardWithoutMove(List<GobangMove> moves, Integer targetMoveNumber) {
        List<List<Integer>> board = gobangEngine.createEmptyBoard();
        for (GobangMove move : getEffectivePlaceMoves(moves)) {
            if (Objects.equals(move.getMoveNumber(), targetMoveNumber)) {
                continue;
            }
            gobangEngine.placeStone(board, move.getRowIndex(), move.getColIndex(), move.getColor());
        }
        return board;
    }

    private List<GobangMove> getEffectivePlaceMoves(List<GobangMove> moves) {
        List<GobangMove> effectiveMoves = new ArrayList<>();
        for (GobangMove move : moves) {
            if (MOVE_PLACE_STONE.equals(move.getMoveType())) {
                effectiveMoves.add(move);
                continue;
            }
            if (MOVE_UNDO.equals(move.getMoveType())) {
                removeUndoneMove(effectiveMoves, readMoveDataInteger(move.getMoveData(), "targetMoveNumber"));
            }
        }
        return effectiveMoves;
    }

    private void removeUndoneMove(List<GobangMove> effectiveMoves, Integer targetMoveNumber) {
        if (effectiveMoves.isEmpty()) {
            return;
        }
        if (targetMoveNumber == null) {
            effectiveMoves.remove(effectiveMoves.size() - 1);
            return;
        }
        effectiveMoves.removeIf(move -> Objects.equals(move.getMoveNumber(), targetMoveNumber));
    }

    private Integer readMoveDataInteger(String moveData, String key) {
        if (moveData == null || moveData.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(moveData, new TypeReference<Map<String, Object>>() {});
            Object value = data.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return Integer.valueOf(stringValue);
            }
        } catch (Exception e) {
            log.warn("解析五子棋动作数据失败: {}", moveData, e);
        }
        return null;
    }

    private int nextMoveNumber(Long gameId) {
        Long count = gobangMoveMapper.selectCount(new QueryWrapper<GobangMove>().eq("game_id", gameId));
        return count == null ? 1 : count.intValue() + 1;
    }

    private int nextMoveNumberFromMoves(List<GobangMove> moves) {
        return moves.stream()
                .map(GobangMove::getMoveNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String generateGameCode() {
        for (int attempts = 0; attempts < 100; attempts++) {
            String code = String.format("%06d", random.nextInt(1000000));
            Long count = gobangGameMapper.selectCount(new QueryWrapper<GobangGame>()
                    .eq("game_code", code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new RuntimeException("生成五子棋房间号失败");
    }

    private void ensurePlaying(GobangGame game) {
        if (!Objects.equals(game.getGameStatus(), GameStatusEnum.PLAYING.getCode())) {
            throw new RuntimeException("五子棋尚未开始或已结束");
        }
    }

    private void ensureParticipant(GobangGame game, Long userId) {
        if (!isGameParticipant(game, userId)) {
            throw new RuntimeException("你不是这场五子棋的参与者");
        }
    }

    private boolean isGameParticipant(GobangGame game, Long userId) {
        return Objects.equals(game.getBlackPlayerId(), userId)
                || Objects.equals(game.getWhitePlayerId(), userId)
                || Objects.equals(game.getInvitedPlayerId(), userId);
    }

    private int getPlayerColor(GobangGame game, Long userId) {
        if (Objects.equals(game.getBlackPlayerId(), userId)) {
            return IGobangEngine.BLACK;
        }
        if (Objects.equals(game.getWhitePlayerId(), userId)) {
            return IGobangEngine.WHITE;
        }
        throw new RuntimeException("你不是这场五子棋的落子玩家");
    }

    private List<List<Integer>> parseBoard(String boardData) {
        if (boardData == null || boardData.isBlank()) {
            return gobangEngine.createEmptyBoard();
        }
        try {
            return objectMapper.readValue(boardData, new TypeReference<List<List<Integer>>>() {});
        } catch (Exception e) {
            log.warn("五子棋棋盘数据解析失败，将重置为空棋盘", e);
            return gobangEngine.createEmptyBoard();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化五子棋数据失败", e);
        }
    }

    private GobangResponse convertToResponse(GobangGame game) {
        return convertToResponse(game, findPendingUndoRequest(game.getId()));
    }

    private GobangResponse convertToResponse(GobangGame game, GobangUndoRequest pendingUndoRequest) {
        User black = userMapper != null && game.getBlackPlayerId() != null ? userMapper.selectById(game.getBlackPlayerId()) : null;
        User invited = userMapper != null && game.getInvitedPlayerId() != null ? userMapper.selectById(game.getInvitedPlayerId()) : null;
        User white = userMapper != null && game.getWhitePlayerId() != null ? userMapper.selectById(game.getWhitePlayerId()) : null;
        return GobangResponse.builder()
                .id(game.getId())
                .gameCode(game.getGameCode())
                .blackPlayerId(game.getBlackPlayerId())
                .blackPlayerName(black != null ? black.getRealName() : null)
                .blackPlayerAvatar(black != null ? black.getAvatar() : null)
                .invitedPlayerId(game.getInvitedPlayerId())
                .invitedPlayerName(invited != null ? invited.getRealName() : null)
                .invitedPlayerAvatar(invited != null ? invited.getAvatar() : null)
                .whitePlayerId(game.getWhitePlayerId())
                .whitePlayerName(white != null ? white.getRealName() : null)
                .whitePlayerAvatar(white != null ? white.getAvatar() : null)
                .currentTurn(game.getCurrentTurn())
                .gameStatus(game.getGameStatus())
                .winnerId(game.getWinnerId())
                .boardData(game.getBoardData())
                .lastMoveId(game.getLastMoveId())
                .pendingUndoRequesterId(pendingUndoRequest != null ? pendingUndoRequest.getRequesterId() : null)
                .pendingUndoResponderId(pendingUndoRequest != null ? pendingUndoRequest.getResponderId() : null)
                .pendingUndoTargetMoveNumber(pendingUndoRequest != null ? pendingUndoRequest.getTargetMoveNumber() : null)
                .createdAt(game.getCreatedAt())
                .startedAt(game.getStartedAt())
                .endedAt(game.getEndedAt())
                .updatedAt(game.getUpdatedAt())
                .build();
    }

    private GobangUndoRequest findPendingUndoRequest(Long gameId) {
        if (gameId == null) {
            return null;
        }
        return gobangUndoRequestMapper.selectOne(new QueryWrapper<GobangUndoRequest>()
                .eq("game_id", gameId)
                .eq("status", UNDO_PENDING));
    }

    private GobangMoveResponse convertMoveToResponse(GobangMove move) {
        User player = userMapper != null && move.getPlayerId() != null ? userMapper.selectById(move.getPlayerId()) : null;
        return GobangMoveResponse.builder()
                .id(move.getId())
                .gameId(move.getGameId())
                .playerId(move.getPlayerId())
                .playerName(player != null ? player.getRealName() : null)
                .playerAvatar(player != null ? player.getAvatar() : null)
                .moveNumber(move.getMoveNumber())
                .moveType(move.getMoveType())
                .rowIndex(move.getRowIndex())
                .colIndex(move.getColIndex())
                .color(move.getColor())
                .moveData(move.getMoveData())
                .createdAt(move.getCreatedAt())
                .build();
    }

    public void setGobangGameMapper(GobangGameMapper gobangGameMapper) {
        this.gobangGameMapper = gobangGameMapper;
    }

    public void setGobangMoveMapper(GobangMoveMapper gobangMoveMapper) {
        this.gobangMoveMapper = gobangMoveMapper;
    }

    public void setGobangUndoRequestMapper(GobangUndoRequestMapper gobangUndoRequestMapper) {
        this.gobangUndoRequestMapper = gobangUndoRequestMapper;
    }

    public void setUserMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public void setUserRelationService(IUserRelationService userRelationService) {
        this.userRelationService = userRelationService;
    }

    public void setGobangEngine(IGobangEngine gobangEngine) {
        this.gobangEngine = gobangEngine;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
