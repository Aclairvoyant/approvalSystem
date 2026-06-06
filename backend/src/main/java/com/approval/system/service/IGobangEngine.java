package com.approval.system.service;

import java.util.List;

public interface IGobangEngine {

    int BOARD_SIZE = 15;
    int EMPTY = 0;
    int BLACK = 1;
    int WHITE = 2;

    List<List<Integer>> createEmptyBoard();

    void placeStone(List<List<Integer>> board, int row, int col, int color);

    boolean hasFive(List<List<Integer>> board, int row, int col, int color);
}
