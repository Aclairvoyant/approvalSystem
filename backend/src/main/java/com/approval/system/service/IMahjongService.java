package com.approval.system.service;

import com.approval.system.dto.MahjongActionRequest;
import com.approval.system.dto.MahjongCreateRequest;
import com.approval.system.dto.MahjongRoomResponse;
import com.approval.system.entity.MahjongGame;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 上海麻将房间。和飞行棋、五子棋不同：凭房间号任何登录用户都能加入，不要求互为对象；
 * 房主可以直接邀请自己的对象，也可以把空位设成电脑。
 */
public interface IMahjongService {

    MahjongRoomResponse createRoom(Long userId, MahjongCreateRequest request);

    /** 凭房间号加入；已经在房间里的人直接返回房间 */
    MahjongRoomResponse joinRoom(Long userId, String gameCode);

    /** 坐在桌上的人和被邀请的对象可以查看；返回的视角只含请求者自己的手牌 */
    MahjongRoomResponse getRoom(Long gameId, Long userId);

    Page<MahjongRoomResponse> listRooms(Long userId, Integer status, Integer pageNum, Integer pageSize);

    /** 房主把空位设成电脑（bot=true）或把电脑改回空位 */
    MahjongRoomResponse setSeat(Long gameId, Long userId, int seatIndex, boolean bot);

    /** 房主直接邀请自己的对象 */
    MahjongRoomResponse invite(Long gameId, Long userId, Long inviteeId);

    /** 开局前离开座位（房主不能离开，只能取消房间） */
    void leaveRoom(Long gameId, Long userId);

    MahjongRoomResponse startGame(Long gameId, Long userId);

    /** 本局结束后再来一局：庄家顺延，总分累计 */
    MahjongRoomResponse nextRound(Long gameId, Long userId);

    void cancelRoom(Long gameId, Long userId);

    MahjongRoomResponse act(Long gameId, Long userId, MahjongActionRequest request);

    /**
     * 调度器调用：电脑 / 托管 / 已敲的座位自动行动，或真人超时代打。
     * 状态版本已经变了（有人先操作了）或还没到时间就什么都不做。
     */
    void runScheduledStep(Long gameId, int expectedVersion);

    /** 已经到期但还没执行的房间（服务重启后补跑用），只带 id 和 stateVersion */
    List<MahjongGame> findOverdueGames();

    /** 是否坐在这桌（订阅房间消息前校验） */
    boolean isSeated(Long gameId, Long userId);
}
