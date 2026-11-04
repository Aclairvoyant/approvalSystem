package com.approval.system.service.impl;

import com.approval.system.dto.GameResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlightChessEngineGameOverTest {

    private final FlightChessEngineImpl engine = new FlightChessEngineImpl();

    private GameResponse game(List<Integer> player1Pieces, List<Integer> player2Pieces) {
        return GameResponse.builder()
                .player1Id(1L)
                .player2Id(2L)
                .player1Pieces(player1Pieces)
                .player2Pieces(player2Pieces)
                .build();
    }

    @Test
    void gameEndsWhenBothPiecesReachDestination() {
        GameResponse game = game(List.of(100, 100), List.of(30, 0));

        assertThat(engine.isGameOver(game)).isTrue();
        assertThat(engine.determineWinner(game)).isEqualTo(1L);
    }

    @Test
    void gameContinuesWhileAnyPieceIsOnTheBoard() {
        GameResponse game = game(List.of(100, 48), List.of(100, 0));

        assertThat(engine.isGameOver(game)).isFalse();
        assertThat(engine.determineWinner(game)).isNull();
    }

    @Test
    void legacyGamesWithFourStoredPiecesCanStillEnd() {
        // 早期对局存了 4 枚棋子，后两枚永远停在基地；规则只有 2 枚棋子，应当判定结束
        GameResponse game = game(List.of(0, 12, 0, 0), List.of(100, 100, 0, 0));

        assertThat(engine.isGameOver(game)).isTrue();
        assertThat(engine.determineWinner(game)).isEqualTo(2L);
    }
}
