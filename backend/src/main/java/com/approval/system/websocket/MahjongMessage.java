package com.approval.system.websocket;

import com.approval.system.dto.MahjongRoomResponse;
import com.approval.system.service.mahjong.MahjongViews;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 麻将的 STOMP 消息。
 * <ul>
 *   <li>/topic/mahjong/{id}：MAHJONG_ROOM，房间公共视角（不含任何人的暗牌和牌墙）；</li>
 *   <li>/user/queue/mahjong/{id}：MAHJONG_HAND 自己的手牌和可选操作，MAHJONG_SYNC 完整的个人视角，
 *       MAHJONG_ERROR 只给操作者本人的错误提示。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MahjongMessage {

    public static final String TOPIC_PREFIX = "/topic/mahjong/";
    /** convertAndSendToUser 用的目的地；客户端订阅 /user/queue/mahjong/{id} */
    public static final String USER_QUEUE_PREFIX = "/queue/mahjong/";

    public enum MessageType {
        MAHJONG_ROOM,
        MAHJONG_HAND,
        MAHJONG_SYNC,
        MAHJONG_ERROR
    }

    private MessageType type;
    private Long gameId;
    private Integer version;
    private Long serverTime;
    private Map<String, Object> data;
    private LocalDateTime timestamp;

    public static String topic(Long gameId) {
        return TOPIC_PREFIX + gameId;
    }

    public static String userQueue(Long gameId) {
        return USER_QUEUE_PREFIX + gameId;
    }

    public static MahjongMessage room(Long gameId, int version, MahjongRoomResponse room) {
        return build(MessageType.MAHJONG_ROOM, gameId, version, "room", room);
    }

    public static MahjongMessage hand(Long gameId, int version, MahjongViews.PrivateHand hand) {
        return build(MessageType.MAHJONG_HAND, gameId, version, "hand", hand);
    }

    public static MahjongMessage sync(Long gameId, MahjongRoomResponse room) {
        return build(MessageType.MAHJONG_SYNC, gameId, room.getVersion(), "room", room);
    }

    public static MahjongMessage error(Long gameId, String message) {
        return build(MessageType.MAHJONG_ERROR, gameId, null, "error", message);
    }

    private static MahjongMessage build(MessageType type, Long gameId, Integer version, String key, Object value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(key, value);
        return MahjongMessage.builder()
                .type(type)
                .gameId(gameId)
                .version(version)
                .serverTime(System.currentTimeMillis())
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
