package com.approval.system.service.impl;

import com.approval.system.config.MiMoConfig;
import com.approval.system.dto.GobangAiMoveInsight;
import com.approval.system.dto.GobangAiPoint;
import com.approval.system.dto.GobangAiReviewResponse;
import com.approval.system.entity.GobangAiReview;
import com.approval.system.entity.GobangGame;
import com.approval.system.entity.GobangMove;
import com.approval.system.mapper.GobangAiReviewMapper;
import com.approval.system.mapper.GobangGameMapper;
import com.approval.system.mapper.GobangMoveMapper;
import com.approval.system.service.IGobangEngine;
import com.approval.system.service.IGobangReviewService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class GobangReviewServiceImpl implements IGobangReviewService {

    private static final String MOVE_PLACE_STONE = "PLACE_STONE";
    private static final String SOURCE_HEURISTIC = "heuristic";
    private static final String SOURCE_MIMO = "mimo";
    private static final int STRONG_BETTER_THRESHOLD = 180;

    @Autowired
    private GobangGameMapper gobangGameMapper;

    @Autowired
    private GobangMoveMapper gobangMoveMapper;

    @Autowired
    private GobangAiReviewMapper gobangAiReviewMapper;

    @Autowired
    private IGobangEngine gobangEngine;

    @Autowired
    private MiMoConfig miMoConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public GobangAiReviewResponse reviewGame(Long gameId, Long userId) {
        ensureAuthorizedGame(gameId, userId);

        GobangAiReviewResponse savedReview = readSavedReview(gameId);
        boolean canUseMiMo = canUseMiMo();
        if (savedReview != null && (isMiMoReview(savedReview) || !canUseMiMo)) {
            return savedReview;
        }

        List<GobangMove> moves = getOrderedMoves(gameId);
        GobangAiReviewResponse fallback = buildHeuristicReview(gameId, moves);
        if (!canUseMiMo) {
            saveReview(gameId, fallback);
            return fallback;
        }

        try {
            GobangAiReviewResponse response = requestMiMoReview(gameId, moves, fallback);
            saveReview(gameId, response);
            return response;
        } catch (Exception e) {
            log.warn("MiMo 五子棋复盘失败，降级使用本地启发式复盘: gameId={}", gameId, e);
            saveReview(gameId, fallback);
            return fallback;
        }
    }

    @Override
    public GobangAiReviewResponse getSavedReview(Long gameId, Long userId) {
        ensureAuthorizedGame(gameId, userId);
        return readSavedReview(gameId);
    }

    private GobangAiReviewResponse buildHeuristicReview(Long gameId, List<GobangMove> moves) {
        List<List<Integer>> board = gobangEngine.createEmptyBoard();
        List<GobangAiMoveInsight> insights = new ArrayList<>();

        for (GobangMove move : moves) {
            if (!MOVE_PLACE_STONE.equals(move.getMoveType())) {
                continue;
            }
            if (!isValidPlacement(move)) {
                continue;
            }

            MoveAnalysis analysis = analyzeMove(board, move);
            insights.add(GobangAiMoveInsight.builder()
                    .moveNumber(move.getMoveNumber())
                    .playerColor(move.getColor())
                    .rowIndex(move.getRowIndex())
                    .colIndex(move.getColIndex())
                    .winRate(analysis.getWinRate())
                    .bestMoveWinRate(analysis.getBestMoveWinRate())
                    .severity(analysis.getSeverity())
                    .summary(analysis.getSummary())
                    .suggestedPoints(analysis.getSuggestedPoints())
                    .build());

            try {
                gobangEngine.placeStone(board, move.getRowIndex(), move.getColIndex(), move.getColor());
            } catch (IllegalArgumentException e) {
                log.debug("忽略非法五子棋历史落子: moveNumber={}", move.getMoveNumber(), e);
            }
        }

        return GobangAiReviewResponse.builder()
                .gameId(gameId)
                .source(SOURCE_HEURISTIC)
                .overview(insights.isEmpty() ? "本局暂无可复盘的落子。" : "已基于五子棋棋型生成本地复盘建议。")
                .insights(insights)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private MoveAnalysis analyzeMove(List<List<Integer>> board, GobangMove move) {
        int color = move.getColor();
        int opponent = color == IGobangEngine.BLACK ? IGobangEngine.WHITE : IGobangEngine.BLACK;
        int moveScore = scorePoint(board, move.getRowIndex(), move.getColIndex(), color);
        List<GobangAiPoint> suggestions = new ArrayList<>();
        ScoredPoint best = findBestPoint(board, color, opponent);
        int winRate = estimateWinRateAfterMove(board, move.getRowIndex(), move.getColIndex(), color);
        int bestMoveWinRate = best == null ? winRate : estimateWinRateAfterMove(board, best.getRow(), best.getCol(), color);

        BoardPoint winningPoint = bestPoint(findImmediateWinningPoints(board, color));
        if (winningPoint != null && !winningPoint.matches(move)) {
            suggestions.add(toSuggestion(winningPoint, "WINNING_MOVE", "必杀点", 98));
            return MoveAnalysis.of(
                    "critical",
                    "这一手错过了直接成五的点位，优先下在标记处可以立即建立胜势。",
                    suggestions,
                    winRate,
                    bestMoveWinRate);
        }

        BoardPoint blockPoint = bestPoint(findImmediateWinningPoints(board, opponent));
        if (blockPoint != null && !blockPoint.matches(move)) {
            suggestions.add(toSuggestion(blockPoint, "BLOCK", "必须防守", 92));
            return MoveAnalysis.of(
                    "warning",
                    "对手已有一手成五威胁，这里更需要先补住防守点。",
                    suggestions,
                    winRate,
                    bestMoveWinRate);
        }

        if (best != null && !best.matches(move) && best.getScore() - moveScore >= STRONG_BETTER_THRESHOLD) {
            suggestions.add(toSuggestion(best, "BETTER_MOVE", "更优点", Math.min(90, 58 + (best.getScore() - moveScore) / 80)));
            return MoveAnalysis.of(
                    "info",
                    "这一手可下，但标记点能同时兼顾进攻潜力和防守价值。",
                    suggestions,
                    winRate,
                    bestMoveWinRate);
        }

        return MoveAnalysis.of(
                "good",
                "这一手与当前棋型基本匹配，没有明显错过的强制点。",
                suggestions,
                winRate,
                bestMoveWinRate);
    }

    private List<BoardPoint> findImmediateWinningPoints(List<List<Integer>> board, int color) {
        List<BoardPoint> points = new ArrayList<>();
        for (BoardPoint point : candidatePoints(board)) {
            board.get(point.getRow()).set(point.getCol(), color);
            boolean wins = gobangEngine.hasFive(board, point.getRow(), point.getCol(), color);
            board.get(point.getRow()).set(point.getCol(), IGobangEngine.EMPTY);
            if (wins) {
                points.add(point);
            }
        }
        return points;
    }

    private ScoredPoint findBestPoint(List<List<Integer>> board, int color, int opponent) {
        ScoredPoint best = null;
        for (BoardPoint point : candidatePoints(board)) {
            int attackScore = scorePoint(board, point.getRow(), point.getCol(), color);
            int defenseScore = scorePoint(board, point.getRow(), point.getCol(), opponent);
            int totalScore = attackScore + (int) (defenseScore * 0.92);
            if (best == null || totalScore > best.getScore()) {
                best = new ScoredPoint(point.getRow(), point.getCol(), totalScore);
            }
        }
        return best;
    }

    private int estimateWinRateAfterMove(List<List<Integer>> board, int row, int col, int color) {
        if (!isInRange(row, col) || board.get(row).get(col) != IGobangEngine.EMPTY) {
            return 50;
        }

        int opponent = color == IGobangEngine.BLACK ? IGobangEngine.WHITE : IGobangEngine.BLACK;
        board.get(row).set(col, color);
        try {
            if (gobangEngine.hasFive(board, row, col, color)) {
                return 100;
            }

            int opponentWinningPoints = findImmediateWinningPoints(board, opponent).size();
            if (opponentWinningPoints > 0) {
                return opponentWinningPoints >= 2 ? 4 : 10;
            }

            int ownWinningPoints = findImmediateWinningPoints(board, color).size();
            if (ownWinningPoints >= 2) {
                return 92;
            }
            if (ownWinningPoints == 1) {
                return 74;
            }

            ScoredPoint ownBest = findBestPoint(board, color, opponent);
            ScoredPoint opponentBest = findBestPoint(board, opponent, color);
            int ownPressure = ownBest == null ? 0 : pressureScore(ownBest.getScore());
            int opponentPressure = opponentBest == null ? 0 : pressureScore(opponentBest.getScore());
            return clampWinRate(50 + ownPressure - opponentPressure);
        } finally {
            board.get(row).set(col, IGobangEngine.EMPTY);
        }
    }

    private int pressureScore(int score) {
        if (score >= 100000) return 44;
        if (score >= 10000) return 34;
        if (score >= 5200) return 28;
        if (score >= 1500) return 20;
        if (score >= 420) return 12;
        if (score >= 220) return 7;
        if (score >= 80) return 4;
        return Math.max(0, Math.min(3, score / 50));
    }

    private int clampWinRate(int value) {
        return Math.max(5, Math.min(95, value));
    }

    private int scorePoint(List<List<Integer>> board, int row, int col, int color) {
        if (!isInRange(row, col) || board.get(row).get(col) != IGobangEngine.EMPTY) {
            return -1;
        }

        board.get(row).set(col, color);
        if (gobangEngine.hasFive(board, row, col, color)) {
            board.get(row).set(col, IGobangEngine.EMPTY);
            return 100000;
        }

        int score = 0;
        int[][] directions = {
                {1, 0},
                {0, 1},
                {1, 1},
                {1, -1}
        };
        for (int[] direction : directions) {
            LineShape shape = lineShape(board, row, col, direction[0], direction[1], color);
            score += scoreShape(shape);
        }
        board.get(row).set(col, IGobangEngine.EMPTY);
        return score;
    }

    private LineShape lineShape(List<List<Integer>> board, int row, int col, int rowStep, int colStep, int color) {
        int forward = countDirection(board, row, col, rowStep, colStep, color);
        int backward = countDirection(board, row, col, -rowStep, -colStep, color);
        int openEnds = 0;
        if (isOpenEnd(board, row + (forward + 1) * rowStep, col + (forward + 1) * colStep)) {
            openEnds++;
        }
        if (isOpenEnd(board, row - (backward + 1) * rowStep, col - (backward + 1) * colStep)) {
            openEnds++;
        }
        return new LineShape(1 + forward + backward, openEnds);
    }

    private int scoreShape(LineShape shape) {
        int stones = shape.getStones();
        int openEnds = shape.getOpenEnds();
        if (stones >= 5) return 100000;
        if (stones == 4 && openEnds == 2) return 10000;
        if (stones == 4 && openEnds == 1) return 5200;
        if (stones == 3 && openEnds == 2) return 1500;
        if (stones == 3 && openEnds == 1) return 420;
        if (stones == 2 && openEnds == 2) return 220;
        if (stones == 2 && openEnds == 1) return 80;
        return stones * 12 + openEnds * 10;
    }

    private int countDirection(List<List<Integer>> board, int row, int col, int rowStep, int colStep, int color) {
        int count = 0;
        int currentRow = row + rowStep;
        int currentCol = col + colStep;
        while (isInRange(currentRow, currentCol) && board.get(currentRow).get(currentCol) == color) {
            count++;
            currentRow += rowStep;
            currentCol += colStep;
        }
        return count;
    }

    private boolean isOpenEnd(List<List<Integer>> board, int row, int col) {
        return isInRange(row, col) && board.get(row).get(col) == IGobangEngine.EMPTY;
    }

    private List<BoardPoint> candidatePoints(List<List<Integer>> board) {
        List<BoardPoint> points = new ArrayList<>();
        boolean hasStone = false;
        for (int row = 0; row < IGobangEngine.BOARD_SIZE; row++) {
            for (int col = 0; col < IGobangEngine.BOARD_SIZE; col++) {
                if (board.get(row).get(col) != IGobangEngine.EMPTY) {
                    hasStone = true;
                    continue;
                }
                if (hasNeighbor(board, row, col)) {
                    points.add(new BoardPoint(row, col));
                }
            }
        }

        if (!hasStone) {
            points.add(new BoardPoint(7, 7));
        }

        points.sort(Comparator
                .comparingInt((BoardPoint point) -> centerDistance(point.getRow(), point.getCol()))
                .thenComparingInt(BoardPoint::getRow)
                .thenComparingInt(BoardPoint::getCol));
        return points;
    }

    private boolean hasNeighbor(List<List<Integer>> board, int row, int col) {
        for (int rowOffset = -2; rowOffset <= 2; rowOffset++) {
            for (int colOffset = -2; colOffset <= 2; colOffset++) {
                if (rowOffset == 0 && colOffset == 0) {
                    continue;
                }
                int nextRow = row + rowOffset;
                int nextCol = col + colOffset;
                if (isInRange(nextRow, nextCol) && board.get(nextRow).get(nextCol) != IGobangEngine.EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }

    private int centerDistance(int row, int col) {
        return Math.abs(row - 7) + Math.abs(col - 7);
    }

    private BoardPoint bestPoint(List<BoardPoint> points) {
        return points.isEmpty() ? null : points.get(0);
    }

    private GobangAiPoint toSuggestion(BoardPoint point, String type, String label, int score) {
        return GobangAiPoint.builder()
                .row(point.getRow())
                .col(point.getCol())
                .type(type)
                .label(label)
                .score(score)
                .build();
    }

    private boolean canUseMiMo() {
        return Boolean.TRUE.equals(miMoConfig.getEnabled())
                && StringUtils.hasText(miMoConfig.getBaseUrl())
                && StringUtils.hasText(miMoConfig.getApiKey())
                && !"your-mimo-api-key".equals(miMoConfig.getApiKey());
    }

    private boolean isMiMoReview(GobangAiReviewResponse response) {
        return response != null && SOURCE_MIMO.equalsIgnoreCase(response.getSource());
    }

    private GobangAiReviewResponse requestMiMoReview(Long gameId, List<GobangMove> moves, GobangAiReviewResponse fallback) throws JsonProcessingException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", miMoConfig.getChatModel());
        body.put("temperature", 0.15);
        body.put("messages", List.of(
                Map.of("role", "system", "content", buildSystemPrompt()),
                Map.of("role", "user", "content", buildUserPrompt(gameId, moves, fallback))
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", miMoConfig.getApiKey());

        String url = miMoConfig.getBaseUrl() + "/chat/completions";
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        String content = extractMessageContent(response.getBody());
        GobangAiReviewResponse parsed = objectMapper.readValue(stripCodeFence(content), GobangAiReviewResponse.class);
        return normalizeMiMoReview(gameId, parsed, fallback);
    }

    private String buildSystemPrompt() {
        return "你是严厉、精准的五子棋高段复盘教练。只返回合法 JSON，不要 markdown，不要解释 JSON 外内容。"
                + "坐标 row/col/rowIndex/colIndex 使用 0 到 14 的数组下标。insights 必须覆盖落子历史中的每一步，不得编造步数。"
                + "返回格式：{\"overview\":\"一句犀利总评\",\"insights\":[{\"moveNumber\":1,\"playerColor\":1,"
                + "\"rowIndex\":7,\"colIndex\":7,\"winRate\":56,\"bestMoveWinRate\":78,"
                + "\"severity\":\"good|info|warning|critical\",\"summary\":\"20到45字的直接棋评\","
                + "\"suggestedPoints\":[{\"row\":7,\"col\":8,\"label\":\"必杀点\",\"type\":\"WINNING_MOVE|BLOCK|BETTER_MOVE|COMMENTARY\",\"score\":88}]}]}。"
                + "winRate 是该步落子后执棋方胜率预测，bestMoveWinRate 是该步行棋前最佳点可达到的胜率，均为 0 到 100 的整数。"
                + "severity 规则：错过直接成五或必杀为 critical；必须挡没挡为 warning；胜率明显少于最佳点为 info；无明显问题为 good。"
                + "summary 要像棋评，不要泛泛鼓励；优先指出错过必杀、必须挡、缓手、俗手、失先手、保留主动权、造双威胁等具体战术。"
                + "suggestedPoints 只放真有价值的点，最多 2 个，必杀、必须防守和胜率显著更高的位置优先。";
    }

    private String buildUserPrompt(Long gameId, List<GobangMove> moves, GobangAiReviewResponse fallback) throws JsonProcessingException {
        List<Map<String, Object>> moveRows = moves.stream()
                .filter(move -> MOVE_PLACE_STONE.equals(move.getMoveType()))
                .map(move -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("moveNumber", move.getMoveNumber());
                    item.put("color", move.getColor());
                    item.put("row", move.getRowIndex());
                    item.put("col", move.getColIndex());
                    return item;
                })
                .toList();
        return "对局 ID：" + gameId
                + "\n落子历史：" + objectMapper.writeValueAsString(moveRows)
                + "\n本地棋型候选：" + objectMapper.writeValueAsString(fallback.getInsights())
                + "\n请以本地棋型候选为底稿：保留每一步的 moveNumber、playerColor、rowIndex、colIndex，并输出 winRate 与 bestMoveWinRate。"
                + "你可以按棋理小幅修正胜率，但不要让明显必杀、必须防守、活四、冲四活三等关键点被轻描淡写。"
                + "请给出适合回放逐步展示的完整 JSON，文字要短、狠、准。";
    }

    private String extractMessageContent(String responseBody) throws JsonProcessingException {
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("MiMo 复盘响应为空");
        }
        Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> choice)) {
            throw new IllegalStateException("MiMo 复盘响应缺少 choices");
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new IllegalStateException("MiMo 复盘响应缺少 message");
        }
        Object contentValue = message.get("content");
        if (!(contentValue instanceof String content) || !StringUtils.hasText(content)) {
            throw new IllegalStateException("MiMo 复盘响应缺少 content");
        }
        return content;
    }

    private GobangAiReviewResponse normalizeMiMoReview(Long gameId, GobangAiReviewResponse parsed, GobangAiReviewResponse fallback) {
        if (parsed == null || parsed.getInsights() == null || parsed.getInsights().isEmpty()) {
            return fallback;
        }
        parsed.setGameId(gameId);
        parsed.setSource(SOURCE_MIMO);
        parsed.setGeneratedAt(LocalDateTime.now());
        if (!StringUtils.hasText(parsed.getOverview())) {
            parsed.setOverview(fallback.getOverview());
        }
        parsed.setInsights(mergeInsights(fallback.getInsights(), parsed.getInsights()));
        return parsed.getInsights().isEmpty() ? fallback : parsed;
    }

    private GobangAiReviewResponse enrichReviewWithLocalPredictions(Long gameId, GobangAiReviewResponse response) {
        GobangAiReviewResponse fallback = buildHeuristicReview(gameId, getOrderedMoves(gameId));
        response.setInsights(mergeInsights(fallback.getInsights(), response.getInsights()));
        if (!StringUtils.hasText(response.getOverview())) {
            response.setOverview(fallback.getOverview());
        }
        return response;
    }

    private boolean needsPredictionEnrichment(GobangAiReviewResponse response) {
        if (response == null || response.getInsights() == null || response.getInsights().isEmpty()) {
            return false;
        }
        return response.getInsights().stream()
                .anyMatch(insight -> insight != null
                        && (insight.getWinRate() == null || insight.getBestMoveWinRate() == null));
    }

    private List<GobangAiMoveInsight> mergeInsights(List<GobangAiMoveInsight> fallbackInsights, List<GobangAiMoveInsight> parsedInsights) {
        List<GobangAiMoveInsight> sanitizedParsed = Optional.ofNullable(parsedInsights)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(this::sanitizeInsight)
                .filter(Objects::nonNull)
                .toList();

        List<GobangAiMoveInsight> sanitizedFallback = Optional.ofNullable(fallbackInsights)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(this::sanitizeInsight)
                .filter(Objects::nonNull)
                .toList();

        if (sanitizedFallback.isEmpty()) {
            return sanitizedParsed;
        }

        Map<Integer, GobangAiMoveInsight> parsedByMove = new HashMap<>();
        for (GobangAiMoveInsight insight : sanitizedParsed) {
            parsedByMove.put(insight.getMoveNumber(), insight);
        }

        List<GobangAiMoveInsight> merged = new ArrayList<>();
        for (GobangAiMoveInsight fallbackInsight : sanitizedFallback) {
            merged.add(mergeInsight(fallbackInsight, parsedByMove.get(fallbackInsight.getMoveNumber())));
        }
        return merged;
    }

    private GobangAiMoveInsight mergeInsight(GobangAiMoveInsight fallback, GobangAiMoveInsight parsed) {
        if (parsed == null) {
            return fallback;
        }
        if (fallback == null) {
            return parsed;
        }

        List<GobangAiPoint> suggestedPoints = parsed.getSuggestedPoints() == null || parsed.getSuggestedPoints().isEmpty()
                ? fallback.getSuggestedPoints()
                : parsed.getSuggestedPoints();

        return GobangAiMoveInsight.builder()
                .moveNumber(fallback.getMoveNumber())
                .playerColor(parsed.getPlayerColor() != null ? parsed.getPlayerColor() : fallback.getPlayerColor())
                .rowIndex(parsed.getRowIndex() != null ? parsed.getRowIndex() : fallback.getRowIndex())
                .colIndex(parsed.getColIndex() != null ? parsed.getColIndex() : fallback.getColIndex())
                .winRate(parsed.getWinRate() != null ? parsed.getWinRate() : fallback.getWinRate())
                .bestMoveWinRate(parsed.getBestMoveWinRate() != null ? parsed.getBestMoveWinRate() : fallback.getBestMoveWinRate())
                .severity(StringUtils.hasText(parsed.getSeverity()) ? parsed.getSeverity() : fallback.getSeverity())
                .summary(StringUtils.hasText(parsed.getSummary()) ? parsed.getSummary() : fallback.getSummary())
                .suggestedPoints(suggestedPoints)
                .build();
    }

    private GobangAiMoveInsight sanitizeInsight(GobangAiMoveInsight insight) {
        if (insight == null || insight.getMoveNumber() == null) {
            return null;
        }
        List<GobangAiPoint> points = Optional.ofNullable(insight.getSuggestedPoints())
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(this::isValidPoint)
                .toList();
        insight.setSuggestedPoints(points);
        insight.setWinRate(clampNullableWinRate(insight.getWinRate()));
        insight.setBestMoveWinRate(clampNullableWinRate(insight.getBestMoveWinRate()));
        if (!StringUtils.hasText(insight.getSeverity())) {
            insight.setSeverity(points.isEmpty() ? "good" : "info");
        }
        if (!StringUtils.hasText(insight.getSummary())) {
            insight.setSummary(points.isEmpty() ? "这一手没有明显问题。" : "标记点有更高价值。");
        }
        return insight;
    }

    private Integer clampNullableWinRate(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.max(0, Math.min(100, value));
    }

    private boolean isValidPoint(GobangAiPoint point) {
        return point != null
                && point.getRow() != null
                && point.getCol() != null
                && isInRange(point.getRow(), point.getCol());
    }

    private String stripCodeFence(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            if (firstNewline > 0) {
                value = value.substring(firstNewline + 1);
            }
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3);
            }
        }
        return value.trim();
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

    private GobangGame ensureAuthorizedGame(Long gameId, Long userId) {
        GobangGame game = gobangGameMapper.selectById(gameId);
        if (game == null) {
            throw new RuntimeException("五子棋对局不存在");
        }
        if (!isGameParticipant(game, userId)) {
            throw new RuntimeException("你不是这场五子棋的参与者");
        }
        return game;
    }

    private GobangAiReviewResponse readSavedReview(Long gameId) {
        GobangAiReview savedReview = findSavedReview(gameId);
        if (savedReview == null || !StringUtils.hasText(savedReview.getReviewData())) {
            return null;
        }

        try {
            GobangAiReviewResponse response = objectMapper.readValue(savedReview.getReviewData(), GobangAiReviewResponse.class);
            response.setGameId(gameId);
            response.setSource(StringUtils.hasText(response.getSource()) ? response.getSource() : savedReview.getSource());
            if (response.getGeneratedAt() == null) {
                response.setGeneratedAt(savedReview.getCreatedAt());
            }
            if (needsPredictionEnrichment(response)) {
                response = enrichReviewWithLocalPredictions(gameId, response);
                saveReview(gameId, response);
            }
            return response;
        } catch (Exception e) {
            log.warn("读取已保存五子棋复盘失败，忽略缓存: gameId={}", gameId, e);
            return null;
        }
    }

    private GobangAiReview findSavedReview(Long gameId) {
        return gobangAiReviewMapper.selectOne(new QueryWrapper<GobangAiReview>()
                .eq("game_id", gameId));
    }

    private void saveReview(Long gameId, GobangAiReviewResponse response) {
        if (response == null) {
            return;
        }
        response.setGameId(gameId);
        if (response.getGeneratedAt() == null) {
            response.setGeneratedAt(LocalDateTime.now());
        }

        try {
            String reviewData = objectMapper.writeValueAsString(response);
            GobangAiReview savedReview = findSavedReview(gameId);
            if (savedReview == null) {
                gobangAiReviewMapper.insert(GobangAiReview.builder()
                        .gameId(gameId)
                        .source(StringUtils.hasText(response.getSource()) ? response.getSource() : SOURCE_HEURISTIC)
                        .reviewData(reviewData)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
                return;
            }

            savedReview.setSource(StringUtils.hasText(response.getSource()) ? response.getSource() : savedReview.getSource());
            savedReview.setReviewData(reviewData);
            savedReview.setUpdatedAt(LocalDateTime.now());
            gobangAiReviewMapper.updateById(savedReview);
        } catch (Exception e) {
            log.warn("保存五子棋 AI 复盘失败: gameId={}", gameId, e);
        }
    }

    private boolean isGameParticipant(GobangGame game, Long userId) {
        return Objects.equals(game.getBlackPlayerId(), userId)
                || Objects.equals(game.getWhitePlayerId(), userId)
                || Objects.equals(game.getInvitedPlayerId(), userId);
    }

    private boolean isValidPlacement(GobangMove move) {
        return move.getRowIndex() != null
                && move.getColIndex() != null
                && move.getColor() != null
                && isInRange(move.getRowIndex(), move.getColIndex())
                && (move.getColor() == IGobangEngine.BLACK || move.getColor() == IGobangEngine.WHITE);
    }

    private boolean isInRange(int row, int col) {
        return row >= 0 && row < IGobangEngine.BOARD_SIZE && col >= 0 && col < IGobangEngine.BOARD_SIZE;
    }

    public void setGobangGameMapper(GobangGameMapper gobangGameMapper) {
        this.gobangGameMapper = gobangGameMapper;
    }

    public void setGobangMoveMapper(GobangMoveMapper gobangMoveMapper) {
        this.gobangMoveMapper = gobangMoveMapper;
    }

    public void setGobangAiReviewMapper(GobangAiReviewMapper gobangAiReviewMapper) {
        this.gobangAiReviewMapper = gobangAiReviewMapper;
    }

    public void setGobangEngine(IGobangEngine gobangEngine) {
        this.gobangEngine = gobangEngine;
    }

    public void setMiMoConfig(MiMoConfig miMoConfig) {
        this.miMoConfig = miMoConfig;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class BoardPoint {
        private int row;
        private int col;

        boolean matches(GobangMove move) {
            return Objects.equals(row, move.getRowIndex()) && Objects.equals(col, move.getColIndex());
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ScoredPoint extends BoardPoint {
        private int score;

        ScoredPoint(int row, int col, int score) {
            super(row, col);
            this.score = score;
        }
    }

    @Data
    @AllArgsConstructor(staticName = "of")
    private static class MoveAnalysis {
        private String severity;
        private String summary;
        private List<GobangAiPoint> suggestedPoints;
        private int winRate;
        private int bestMoveWinRate;
    }

    @Data
    @AllArgsConstructor
    private static class LineShape {
        private int stones;
        private int openEnds;
    }
}
