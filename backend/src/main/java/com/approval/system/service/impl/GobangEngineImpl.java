package com.approval.system.service.impl;

import com.approval.system.service.IGobangEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GobangEngineImpl implements IGobangEngine {

    private static final int[][] DIRECTIONS = {
            {1, 0},
            {0, 1},
            {1, 1},
            {1, -1}
    };

    @Override
    public List<List<Integer>> createEmptyBoard() {
        List<List<Integer>> board = new ArrayList<>(BOARD_SIZE);
        for (int row = 0; row < BOARD_SIZE; row++) {
            board.add(new ArrayList<>(Collections.nCopies(BOARD_SIZE, EMPTY)));
        }
        return board;
    }

    @Override
    public void placeStone(List<List<Integer>> board, int row, int col, int color) {
        validateBoard(board);
        validateCoordinate(row, col);
        validateColor(color);
        if (board.get(row).get(col) != EMPTY) {
            throw new IllegalArgumentException("该位置已有棋子");
        }
        board.get(row).set(col, color);
    }

    @Override
    public boolean hasFive(List<List<Integer>> board, int row, int col, int color) {
        validateBoard(board);
        validateCoordinate(row, col);
        validateColor(color);

        for (int[] direction : DIRECTIONS) {
            int count = 1
                    + countDirection(board, row, col, direction[0], direction[1], color)
                    + countDirection(board, row, col, -direction[0], -direction[1], color);
            if (count >= 5) {
                return true;
            }
        }
        return false;
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

    private void validateBoard(List<List<Integer>> board) {
        if (board == null || board.size() != BOARD_SIZE) {
            throw new IllegalArgumentException("棋盘数据无效");
        }
        for (List<Integer> row : board) {
            if (row == null || row.size() != BOARD_SIZE) {
                throw new IllegalArgumentException("棋盘数据无效");
            }
        }
    }

    private void validateCoordinate(int row, int col) {
        if (!isInRange(row, col)) {
            throw new IllegalArgumentException("棋盘坐标无效");
        }
    }

    private void validateColor(int color) {
        if (color != BLACK && color != WHITE) {
            throw new IllegalArgumentException("棋子颜色无效");
        }
    }

    private boolean isInRange(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }
}
