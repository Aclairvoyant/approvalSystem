package com.approval.system.service.impl;

import com.approval.system.common.enums.GameStatusEnum;
import com.approval.system.dto.GameResponse;
import com.approval.system.entity.Game;
import com.approval.system.entity.GameMove;
import com.approval.system.mapper.GameMapper;
import com.approval.system.mapper.GameMoveMapper;
import com.approval.system.mapper.UserMapper;
import com.approval.system.service.IGameTaskService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceDiceTest {

    private static final long GAME_ID = 1L;
    private static final long PLAYER1 = 10L;
    private static final long PLAYER2 = 20L;

    @Mock
    private GameMapper gameMapper;

    @Mock
    private GameMoveMapper gameMoveMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private IGameTaskService gameTaskService;

    private final FlightChessEngineImpl chessEngine = new FlightChessEngineImpl();

    private GameServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GameServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", gameMapper);
        ReflectionTestUtils.setField(service, "gameMoveMapper", gameMoveMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "gameTaskService", gameTaskService);
        ReflectionTestUtils.setField(service, "chessEngine", chessEngine);
    }

    @Test
    void rollDiceDoesNotRerollWhileThePlayerHasNotMoved() {
        givenGame(playingGame(2));

        Integer result = service.rollDice(GAME_ID, PLAYER1);

        assertThat(result).isEqualTo(2);
        verify(gameMapper, never()).update(any(), any());
        verify(gameMoveMapper, never()).insert(any(GameMove.class));
    }

    @Test
    void rollDiceOnlyWritesWhenNoDiceIsPending() {
        givenGame(playingGame(null));
        when(gameMapper.update(isNull(), any())).thenReturn(1);

        Integer result = service.rollDice(GAME_ID, PLAYER1);

        assertThat(result).isBetween(1, 6);
        UpdateWrapper<Game> update = capturedUpdate();
        assertThat(update.getSqlSegment()).contains("last_dice_result IS NULL");
        assertThat(update.getParamNameValuePairs()).containsValue(result);
        verify(gameMoveMapper).insert(any(GameMove.class));
    }

    @Test
    void rollDiceRejectsConcurrentSecondRoll() {
        givenGame(playingGame(null));
        when(gameMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rollDice(GAME_ID, PLAYER1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("已经掷过骰子了，请先移动棋子");
        verify(gameMoveMapper, never()).insert(any(GameMove.class));
    }

    @Test
    void movePieceUsesServerSideDiceAndConsumesIt() {
        Game game = playingGame(3);
        game.setPlayer1Pieces(List.of(10, 0));
        givenGame(game);
        when(gameMapper.update(isNull(), any())).thenReturn(1);

        GameResponse response = service.movePiece(GAME_ID, PLAYER1, 0);

        assertThat(response.getPlayer1Pieces().get(0)).isEqualTo(chessEngine.calculateNewPosition(10, 3, 1));
        assertThat(response.getLastDiceResult()).isNull();
        UpdateWrapper<Game> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("last_dice_result=");
        assertThat(update.getSqlSegment()).contains("last_dice_result =");
        assertThat(update.getParamNameValuePairs()).containsValue(3);

        ArgumentCaptor<GameMove> move = ArgumentCaptor.forClass(GameMove.class);
        verify(gameMoveMapper).insert(move.capture());
        assertThat(move.getValue().getDiceResult()).isEqualTo(3);
    }

    @Test
    void movePieceRequiresARollFirst() {
        givenGame(playingGame(null));

        assertThatThrownBy(() -> service.movePiece(GAME_ID, PLAYER1, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("请先掷骰子");
        verify(gameMapper, never()).update(any(), any());
    }

    @Test
    void passTurnClearsDiceAndSwitchesTurn() {
        givenGame(playingGame(4));
        when(gameMapper.update(isNull(), any())).thenReturn(1);

        Game passed = service.passTurn(GAME_ID, PLAYER1);

        assertThat(passed.getCurrentTurn()).isEqualTo(2);
        assertThat(passed.getLastDiceResult()).isNull();
        UpdateWrapper<Game> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("current_turn=").contains("last_dice_result=");
    }

    @Test
    void passTurnIsANoOpOnceTheDiceIsCleared() {
        givenGame(playingGame(null));

        assertThat(service.passTurn(GAME_ID, PLAYER1)).isNull();
        verify(gameMapper, never()).update(any(), any());
    }

    private Game playingGame(Integer lastDiceResult) {
        return Game.builder()
                .id(GAME_ID)
                .player1Id(PLAYER1)
                .player2Id(PLAYER2)
                .currentTurn(1)
                .gameStatus(GameStatusEnum.PLAYING.getCode())
                .player1Pieces(List.of(0, 0))
                .player2Pieces(List.of(0, 0))
                .lastDiceResult(lastDiceResult)
                .build();
    }

    private void givenGame(Game game) {
        when(gameMapper.selectById(GAME_ID)).thenReturn(game);
    }

    @SuppressWarnings("unchecked")
    private UpdateWrapper<Game> capturedUpdate() {
        ArgumentCaptor<Wrapper<Game>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(gameMapper).update(isNull(), captor.capture());
        return (UpdateWrapper<Game>) captor.getValue();
    }
}
