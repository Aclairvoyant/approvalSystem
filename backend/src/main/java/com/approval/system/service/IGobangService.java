package com.approval.system.service;

import com.approval.system.dto.GobangMoveResponse;
import com.approval.system.dto.GobangResponse;
import com.approval.system.entity.GobangGame;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface IGobangService {

    GobangResponse createGame(Long userId, Long opponentUserId);

    GobangResponse joinGame(Long userId, String gameCode);

    GobangResponse getGameDetail(Long gameId, Long userId);

    Page<GobangResponse> getUserGames(Long userId, Integer status, Integer pageNum, Integer pageSize);

    GobangResponse placeStone(Long gameId, Long userId, Integer row, Integer col);

    GobangResponse requestUndo(Long gameId, Long userId);

    GobangResponse respondUndo(Long gameId, Long userId, Boolean accepted);

    GobangResponse surrender(Long gameId, Long userId);

    List<GobangMoveResponse> getMoves(Long gameId, Long userId);

    void cancelGame(Long gameId, Long userId);

    boolean validateGamePlayer(Long gameId, Long userId);

    void updateLastMoveTime(Long gameId);

    GobangGame getGameById(Long gameId);
}
