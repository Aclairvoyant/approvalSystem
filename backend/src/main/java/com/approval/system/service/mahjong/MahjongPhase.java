package com.approval.system.service.mahjong;

/**
 * 一局里的阶段。
 */
public enum MahjongPhase {
    /** 轮到 turn 座位：手里 3n+2 张，要出牌（或自摸、杠、敲） */
    ACTION,
    /** turn 座位刚打出一张牌，等其他人决定胡 / 杠 / 碰 / 吃 / 过 */
    CLAIM,
    /** 本局结束（有人胡或荒庄） */
    FINISHED
}
