package com.approval.system.service.mahjong;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从完整状态生成对外视角。这是隐藏信息的唯一出口：
 * <ul>
 *   <li>公共视角：只有手牌张数、亮出的牌、花、牌河、剩余张数；暗杠只显示是暗杠；
 *       不含牌墙、任何人的暗牌、刚摸到的牌和谁能吃碰胡。本局结束后只亮胡家的手牌和暗杠，
 *       没胡的人（包括荒庄时所有人）的牌一直不公开。</li>
 *   <li>私有视角：只含这一个座位自己的手牌、听牌和可选操作。</li>
 * </ul>
 */
public final class MahjongViews {

    private MahjongViews() {
    }

    @Data
    @NoArgsConstructor
    public static class PublicRound {
        private String mode;
        private int roundNo;
        private int dealer;
        private int seatCount;
        private String phase;
        private int turn;
        private int wallCount;
        private Integer indicator;
        private Integer wild;
        private int huangCount;
        private int capPoints;
        private Integer lastDiscard;
        private Integer lastDiscardSeat;
        /** 别人出牌后还在等人表态（不说是谁） */
        private boolean waitingClaims;
        private int step;
        private long deadline;
        private List<PublicSeat> seats = new ArrayList<>();
        private MahjongRoundResult result;
        private List<PublicEvent> events = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class PublicSeat {
        private int seat;
        private int handCount;
        /** 正在出牌且手里有刚摸的牌（多出来的那一张） */
        private boolean holdingDrawn;
        private List<PublicMeld> melds = new ArrayList<>();
        private List<Integer> flowers = new ArrayList<>();
        private List<Integer> discards = new ArrayList<>();
        private boolean qiao;
        private boolean autoPlay;
        /** 只在本局结束后、并且这个座位胡了才给出 */
        private List<Integer> hand;
    }

    @Data
    @NoArgsConstructor
    public static class PublicMeld {
        private String type;
        /** 暗杠在本局结束前为 null */
        private List<Integer> tiles;
        private Integer fromSeat;
        private Integer claimedTile;
        private boolean concealed;
    }

    @Data
    @NoArgsConstructor
    public static class PublicEvent {
        private String type;
        private int seat;
        /** 摸牌、暗杠这类别人看不到的事件不带牌面 */
        private Integer tile;
    }

    @Data
    @NoArgsConstructor
    public static class PrivateHand {
        private int seat;
        private int step;
        /** 手牌（已排序，不含刚摸到的那张） */
        private List<Integer> hand = new ArrayList<>();
        private Integer drawnTile;
        /** 自己暗杠的牌（公共视角里暗杠不显示牌面，自己要知道杠的是什么），按副露顺序 */
        private List<Integer> concealedKongs = new ArrayList<>();
        private List<Integer> waits = new ArrayList<>();
        private boolean qiao;
        private boolean autoPlay;
        private long deadline;
        private MahjongSeatOptions options;
        /** 自己摸牌的事件（带牌面），只发给自己 */
        private List<PublicEvent> events = new ArrayList<>();
    }

    public static PublicRound publicView(MahjongRound round, List<MahjongEvent> events) {
        boolean finished = round.isFinished();
        Set<Integer> winners = new HashSet<>();
        if (finished && round.getResult() != null) {
            round.getResult().getWins().forEach(win -> winners.add(win.getSeat()));
        }
        PublicRound view = new PublicRound();
        view.setMode(round.getMode() != null ? round.getMode().name() : null);
        view.setRoundNo(round.getRoundNo());
        view.setDealer(round.getDealer());
        view.setSeatCount(round.getSeatCount());
        view.setPhase(round.getPhase() != null ? round.getPhase().name() : null);
        view.setTurn(round.getTurn());
        view.setWallCount(round.getWall().size());
        view.setIndicator(round.getIndicator());
        view.setWild(round.getWild());
        view.setHuangCount(round.getHuangCount());
        view.setCapPoints(round.getCapPoints());
        view.setLastDiscard(round.getLastDiscard());
        view.setLastDiscardSeat(round.getLastDiscardSeat());
        view.setWaitingClaims(round.getPhase() == MahjongPhase.CLAIM);
        view.setStep(round.getStep());
        view.setDeadline(round.getDeadline());
        for (MahjongPlayer player : round.getPlayers()) {
            boolean revealed = winners.contains(player.getSeat());
            PublicSeat seat = new PublicSeat();
            seat.setSeat(player.getSeat());
            seat.setHandCount(player.getHand().size());
            seat.setHoldingDrawn(round.getPhase() == MahjongPhase.ACTION && round.getTurn() == player.getSeat()
                    && round.getDrawnTile() != null);
            for (MahjongMeld meld : player.getMelds()) {
                PublicMeld publicMeld = new PublicMeld();
                publicMeld.setType(meld.getType().name());
                publicMeld.setFromSeat(meld.getFromSeat());
                publicMeld.setClaimedTile(meld.getClaimedTile());
                boolean concealed = meld.getType() == MahjongMeld.Type.AN_GANG;
                publicMeld.setConcealed(concealed);
                publicMeld.setTiles(concealed && !revealed ? null : new ArrayList<>(meld.getTiles()));
                seat.getMelds().add(publicMeld);
            }
            seat.setFlowers(new ArrayList<>(player.getFlowers()));
            seat.setDiscards(new ArrayList<>(player.getDiscards()));
            seat.setQiao(player.isQiao());
            seat.setAutoPlay(player.isAutoPlay());
            if (revealed) {
                seat.setHand(sorted(player.getHand()));
            }
            view.getSeats().add(seat);
        }
        if (finished) {
            view.setResult(round.getResult());
        }
        if (events != null) {
            for (MahjongEvent event : events) {
                view.getEvents().add(toPublic(event, false));
            }
        }
        return view;
    }

    public static PrivateHand privateView(MahjongRound round, int seat, List<MahjongEvent> events) {
        MahjongPlayer player = round.player(seat);
        PrivateHand view = new PrivateHand();
        view.setSeat(seat);
        view.setStep(round.getStep());
        view.setDeadline(round.getDeadline());
        view.setQiao(player.isQiao());
        view.setAutoPlay(player.isAutoPlay());

        List<Integer> hand = new ArrayList<>(player.getHand());
        Integer drawn = round.getPhase() == MahjongPhase.ACTION && round.getTurn() == seat
                ? round.getDrawnTile() : null;
        if (drawn != null && hand.remove(drawn)) {
            view.setDrawnTile(drawn);
        }
        view.setHand(sorted(hand));
        for (MahjongMeld meld : player.getMelds()) {
            if (meld.getType() == MahjongMeld.Type.AN_GANG) {
                view.getConcealedKongs().add(meld.firstTile());
            }
        }
        view.setWaits(MahjongEngine.waits(round, seat));
        view.setOptions(MahjongEngine.options(round, seat));
        if (events != null) {
            for (MahjongEvent event : events) {
                if (event.getSeat() == seat) {
                    view.getEvents().add(toPublic(event, true));
                }
            }
        }
        return view;
    }

    private static PublicEvent toPublic(MahjongEvent event, boolean owner) {
        PublicEvent view = new PublicEvent();
        view.setType(event.getType());
        view.setSeat(event.getSeat());
        view.setTile(event.isHidden() && !owner ? null : event.getTile());
        return view;
    }

    private static List<Integer> sorted(List<Integer> tiles) {
        List<Integer> copy = new ArrayList<>(tiles);
        copy.sort(Integer::compareTo);
        return copy;
    }
}
