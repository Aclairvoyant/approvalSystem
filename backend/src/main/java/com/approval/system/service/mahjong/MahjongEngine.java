package com.approval.system.service.mahjong;

import com.approval.system.common.enums.MahjongModeEnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.TreeSet;

/**
 * 一局麻将的规则流程：发牌、摸牌补花、出牌、抢牌（胡 > 杠/碰 > 吃，一炮多响）、杠、胡、荒庄。
 * <p>
 * 纯 Java，不依赖 Spring；所有方法直接修改传入的 {@link MahjongRound}，非法操作抛
 * {@link MahjongRuleException}，调用方应丢弃这次修改（服务层每次都从数据库重新读状态）。
 * <p>
 * 座位按 0..seatCount-1 编号，出牌顺序是 seat → seat+1 → …；「上家」是 seat-1。
 */
public final class MahjongEngine {

    public static final int HAND_SIZE = 13;

    private MahjongEngine() {
    }

    // ------------------------------------------------------------------ 开局

    public static MahjongRound deal(MahjongModeEnum mode, int seatCount, int dealer, int roundNo, int huangCount,
                                    int capPoints, boolean[] bots, Random random) {
        List<Integer> tiles = MahjongTiles.fullSet();
        Collections.shuffle(tiles, random);
        return dealFrom(mode, seatCount, dealer, roundNo, huangCount, capPoints, bots, tiles);
    }

    /**
     * 按给定顺序发牌：tiles 的开头依次发给庄家、下家……（每人 13 张，庄家再多摸 1 张），
     * 末尾用作百搭引导牌和补花、补杠的牌。
     */
    public static MahjongRound dealFrom(MahjongModeEnum mode, int seatCount, int dealer, int roundNo, int huangCount,
                                        int capPoints, boolean[] bots, List<Integer> tiles) {
        if (seatCount < 2 || seatCount > 4) {
            throw new MahjongRuleException("座位数只能是 2 到 4");
        }
        if (dealer < 0 || dealer >= seatCount || bots == null || bots.length != seatCount) {
            throw new MahjongRuleException("座位信息不正确");
        }
        MahjongRound round = new MahjongRound();
        round.setMode(mode);
        round.setSeatCount(seatCount);
        round.setDealer(dealer);
        round.setRoundNo(roundNo);
        round.setHuangCount(huangCount);
        round.setCapPoints(capPoints);
        round.setWall(new ArrayList<>(tiles));
        for (int seat = 0; seat < seatCount; seat++) {
            round.getPlayers().add(new MahjongPlayer(seat, bots[seat]));
        }

        MahjongRules rules = MahjongRules.of(round);
        if (rules.isWildEnabled()) {
            int indicator = round.getWall().remove(round.getWall().size() - 1);
            round.setIndicator(indicator);
            round.setWild(MahjongTiles.wildAfter(indicator));
        }

        for (int k = 0; k < seatCount; k++) {
            MahjongPlayer player = round.player((dealer + k) % seatCount);
            for (int i = 0; i < HAND_SIZE; i++) {
                player.getHand().add(round.getWall().remove(0));
            }
        }
        round.player(dealer).getHand().add(round.getWall().remove(0));

        // 起手花从庄家开始依次补
        for (int k = 0; k < seatCount; k++) {
            replaceInitialFlowers(round, (dealer + k) % seatCount);
        }

        MahjongPlayer dealerPlayer = round.player(dealer);
        round.setPhase(MahjongPhase.ACTION);
        round.setTurn(dealer);
        round.setDrawnTile(dealerPlayer.getHand().get(dealerPlayer.getHand().size() - 1));
        round.setReplacementDraw(false);
        checkFourWild(round, dealer);
        return round;
    }

    private static void replaceInitialFlowers(MahjongRound round, int seat) {
        MahjongPlayer player = round.player(seat);
        while (true) {
            Integer flower = player.getHand().stream().filter(MahjongTiles::isFlower).findFirst().orElse(null);
            if (flower == null) {
                return;
            }
            player.getHand().remove(flower);
            player.getFlowers().add(flower);
            round.emit(MahjongEvent.open(MahjongEvent.FLOWER, seat, flower));
            // 144 张里只有 20 张花，开局补花不会把牌墙摸空
            player.getHand().add(round.getWall().remove(round.getWall().size() - 1));
        }
    }

    // ------------------------------------------------------------------ 查询

    /** 座位当前能做的操作 */
    public static MahjongSeatOptions options(MahjongRound round, int seat) {
        MahjongSeatOptions options = new MahjongSeatOptions();
        if (round.isFinished() || seat < 0 || seat >= round.getSeatCount()) {
            return options;
        }
        MahjongPlayer player = round.player(seat);
        if (round.getPhase() == MahjongPhase.ACTION && round.getTurn() == seat) {
            options.setMyTurn(true);
            options.setDiscardable(discardable(round, player));
            options.setCanHu(canSelfDrawHu(round, seat));
            options.setKongTiles(kongTiles(round, player));
            if (MahjongRules.of(round).isQiaoRequired() && !player.isQiao()) {
                for (int tile : options.getDiscardable()) {
                    List<Integer> waits = waitsAfterDiscard(round, player, tile);
                    if (!waits.isEmpty()) {
                        options.getQiaoOptions().add(new MahjongSeatOptions.QiaoOption(tile, waits));
                    }
                }
            }
        } else if (round.getPhase() == MahjongPhase.CLAIM) {
            MahjongClaim claim = findClaim(round, seat);
            if (claim != null && claim.isPending()) {
                options.setClaimPending(true);
                options.setClaimTile(round.getLastDiscard());
                options.setCanHu(claim.isHu());
                options.setCanPong(claim.isPong());
                options.setCanKong(claim.isKong());
                options.setChiStarts(new ArrayList<>(claim.getChiStarts()));
            }
        }
        return options;
    }

    /** 手里 3n+1 张时听哪些牌（不考虑花数和是否已敲），用于提示 */
    public static List<Integer> waits(MahjongRound round, int seat) {
        MahjongPlayer player = round.player(seat);
        if (player.getHand().size() % 3 != 1) {
            return List.of();
        }
        return waitsOf(round, player, player.getHand());
    }

    /** 电脑、托管或已敲的座位由系统自动行动 */
    public static boolean isAutomatic(MahjongRound round, int seat) {
        MahjongPlayer player = round.player(seat);
        return player.isBot() || player.isAutoPlay() || player.isQiao();
    }

    /** 现在是否该由系统替 turn 座位出牌 */
    public static boolean isAutomaticTurn(MahjongRound round) {
        return round.getPhase() == MahjongPhase.ACTION && isAutomatic(round, round.getTurn());
    }

    /** 正在等真人：自己回合，或别人出牌后还有真人没表态 */
    public static boolean isWaitingForHuman(MahjongRound round) {
        if (round.getPhase() == MahjongPhase.ACTION) {
            return !isAutomatic(round, round.getTurn());
        }
        if (round.getPhase() == MahjongPhase.CLAIM) {
            return round.getClaims().stream().anyMatch(MahjongClaim::isPending);
        }
        return false;
    }

    // ------------------------------------------------------------------ 操作

    /** 真人发来的操作；真人自己动手就取消托管 */
    public static void apply(MahjongRound round, int seat, MahjongAction action) {
        if (round.isFinished()) {
            throw new MahjongRuleException("本局已经结束");
        }
        if (seat < 0 || seat >= round.getSeatCount()) {
            throw new MahjongRuleException("座位不存在");
        }
        if (action == null || action.type() == null) {
            throw new MahjongRuleException("操作无效");
        }
        MahjongPlayer player = round.player(seat);
        if (player.isAutoPlay()) {
            player.setAutoPlay(false);
            round.emit(MahjongEvent.open(MahjongEvent.AUTO_OFF, seat, null));
        }
        if (action.type() == MahjongActionType.CANCEL_AUTO) {
            return;
        }
        perform(round, seat, action);
    }

    /** 电脑 / 托管 / 已敲的座位自动行动一步 */
    public static void playAutomaticTurn(MahjongRound round) {
        if (!isAutomaticTurn(round)) {
            throw new MahjongRuleException("现在不该系统出牌");
        }
        int seat = round.getTurn();
        try {
            perform(round, seat, MahjongBot.decideTurn(round, seat));
        } catch (MahjongRuleException e) {
            // 策略算出了不合法的操作也不能卡住牌局：退回到打出一张能打的牌
            perform(round, seat, MahjongAction.discard(fallbackDiscard(round, seat)));
        }
    }

    private static int fallbackDiscard(MahjongRound round, int seat) {
        List<Integer> discardable = discardable(round, round.player(seat));
        if (round.getDrawnTile() != null && discardable.contains(round.getDrawnTile())) {
            return round.getDrawnTile();
        }
        return discardable.get(0);
    }

    /** 真人超时：自己回合由系统代打并进入托管；响应阶段没表态的都算「过」 */
    public static void timeout(MahjongRound round) {
        if (round.getPhase() == MahjongPhase.ACTION) {
            int seat = round.getTurn();
            MahjongPlayer player = round.player(seat);
            if (!player.isAutoPlay()) {
                player.setAutoPlay(true);
                round.emit(MahjongEvent.open(MahjongEvent.AUTO_ON, seat, null));
            }
            perform(round, seat, MahjongBot.decideTurn(round, seat));
        } else if (round.getPhase() == MahjongPhase.CLAIM) {
            for (MahjongClaim claim : round.getClaims()) {
                if (claim.isPending()) {
                    claim.setResponse(MahjongActionType.PASS);
                }
            }
            resolveClaims(round);
        }
    }

    private static void perform(MahjongRound round, int seat, MahjongAction action) {
        switch (round.getPhase()) {
            case ACTION -> performTurnAction(round, seat, action);
            case CLAIM -> respond(round, seat, action);
            default -> throw new MahjongRuleException("本局已经结束");
        }
    }

    private static void performTurnAction(MahjongRound round, int seat, MahjongAction action) {
        if (seat != round.getTurn()) {
            throw new MahjongRuleException("还没轮到你");
        }
        switch (action.type()) {
            case DISCARD -> discard(round, seat, requireTile(action), false);
            case QIAO -> discard(round, seat, requireTile(action), true);
            case HU -> {
                if (!canSelfDrawHu(round, seat)) {
                    throw new MahjongRuleException("现在不能胡");
                }
                winBySelfDraw(round, seat);
            }
            case KONG -> selfKong(round, seat, requireTile(action));
            default -> throw new MahjongRuleException("现在轮到你出牌");
        }
    }

    private static void respond(MahjongRound round, int seat, MahjongAction action) {
        MahjongClaim claim = findClaim(round, seat);
        if (claim == null || !claim.isPending()) {
            throw new MahjongRuleException(seat == round.getTurn() ? "等其他人选择" : "现在没有需要你选择的牌");
        }
        switch (action.type()) {
            case PASS -> claim.setResponse(MahjongActionType.PASS);
            case HU -> {
                if (!claim.isHu()) {
                    throw new MahjongRuleException("这张牌不能胡");
                }
                claim.setResponse(MahjongActionType.HU);
            }
            case PONG -> {
                if (!claim.isPong()) {
                    throw new MahjongRuleException("不能碰这张牌");
                }
                claim.setResponse(MahjongActionType.PONG);
            }
            case KONG -> {
                if (!claim.isKong()) {
                    throw new MahjongRuleException("不能杠这张牌");
                }
                claim.setResponse(MahjongActionType.KONG);
            }
            case CHI -> {
                Integer start = action.chiStart();
                if (start == null || !claim.getChiStarts().contains(start)) {
                    throw new MahjongRuleException("不能这样吃");
                }
                claim.setResponse(MahjongActionType.CHI);
                claim.setResponseChiStart(start);
            }
            default -> throw new MahjongRuleException("现在只能选择胡、杠、碰、吃或过");
        }
        resolveClaims(round);
    }

    private static int requireTile(MahjongAction action) {
        if (action.tile() == null || !MahjongTiles.isValid(action.tile())) {
            throw new MahjongRuleException("请选择一张牌");
        }
        return action.tile();
    }

    // ------------------------------------------------------------------ 出牌与抢牌

    private static void discard(MahjongRound round, int seat, int tile, boolean qiao) {
        MahjongRules rules = MahjongRules.of(round);
        MahjongPlayer player = round.player(seat);
        if (!player.getHand().contains(tile)) {
            throw new MahjongRuleException("手里没有这张牌");
        }
        if (player.isQiao() && !Objects.equals(tile, round.getDrawnTile())) {
            throw new MahjongRuleException("敲了之后不能换牌，只能打出摸到的牌");
        }
        if (round.isJustClaimed() && Objects.equals(tile, round.getRestrictedTile())
                && hasOtherTile(player.getHand(), tile)) {
            throw new MahjongRuleException("吃碰后不能马上打出同一种牌");
        }
        if (qiao) {
            if (!rules.isQiaoRequired()) {
                throw new MahjongRuleException("这个玩法不用敲");
            }
            if (player.isQiao()) {
                throw new MahjongRuleException("已经敲过了");
            }
            if (waitsAfterDiscard(round, player, tile).isEmpty()) {
                throw new MahjongRuleException("打出这张后还没听牌，不能敲");
            }
        }

        player.getHand().remove(Integer.valueOf(tile));
        player.getDiscards().add(tile);
        if (qiao) {
            player.setQiao(true);
        }
        round.setDrawnTile(null);
        round.setJustClaimed(false);
        round.setRestrictedTile(null);
        round.setReplacementDraw(false);
        round.setLastDiscard(tile);
        round.setLastDiscardSeat(seat);
        round.emit(MahjongEvent.open(qiao ? MahjongEvent.QIAO : MahjongEvent.DISCARD, seat, tile));
        openClaimWindow(round, seat, tile);
    }

    private static void openClaimWindow(MahjongRound round, int discarder, int tile) {
        round.getClaims().clear();
        int seatCount = round.getSeatCount();
        for (int k = 1; k < seatCount; k++) {
            int seat = (discarder + k) % seatCount;
            MahjongClaim claim = claimOptions(round, seat, tile, k == 1);
            if (claim.hasOption()) {
                round.getClaims().add(claim);
            }
        }
        if (round.getClaims().isEmpty()) {
            drawNext(round, round.nextSeat(discarder));
            return;
        }
        round.setPhase(MahjongPhase.CLAIM);
        round.setTurn(discarder);
        for (MahjongClaim claim : round.getClaims()) {
            if (isAutomatic(round, claim.getSeat())) {
                autoRespond(round, claim);
            }
        }
        resolveClaims(round);
    }

    private static MahjongClaim claimOptions(MahjongRound round, int seat, int tile, boolean nextSeat) {
        MahjongPlayer player = round.player(seat);
        MahjongClaim claim = new MahjongClaim(seat);
        MahjongWin win = MahjongScoring.evaluate(round, seat, tile, false);
        claim.setHu(MahjongScoring.meetsWinRequirement(round, seat, win));
        if (player.isQiao()) {
            return claim;
        }
        int same = MahjongTiles.count(player.getHand(), tile);
        claim.setPong(same >= 2);
        claim.setKong(same >= 3 && !round.getWall().isEmpty());
        if (nextSeat && MahjongTiles.isSuited(tile)) {
            for (int start = tile - 2; start <= tile; start++) {
                if (!MahjongTiles.isSuited(start) || MahjongTiles.suit(start) != MahjongTiles.suit(tile)
                        || MahjongTiles.rank(start) > 7) {
                    continue;
                }
                boolean complete = true;
                for (int t = start; t < start + 3; t++) {
                    if (t != tile && !player.getHand().contains(t)) {
                        complete = false;
                        break;
                    }
                }
                if (complete) {
                    claim.getChiStarts().add(start);
                }
            }
        }
        return claim;
    }

    private static void autoRespond(MahjongRound round, MahjongClaim claim) {
        if (claim.isHu()) {
            // 能胡就胡；敲麻敲后有胡必胡
            claim.setResponse(MahjongActionType.HU);
            return;
        }
        if (round.player(claim.getSeat()).isQiao()) {
            claim.setResponse(MahjongActionType.PASS);
            return;
        }
        MahjongAction decision = MahjongBot.decideClaim(round, claim);
        claim.setResponse(decision.type());
        claim.setResponseChiStart(decision.chiStart());
    }

    /**
     * 按优先级结算抢牌：能胡的人都表态后，所有选胡的一起胡（一炮多响）；
     * 否则看碰 / 杠，再看吃；更高优先级的人还没表态就继续等。
     */
    static void resolveClaims(MahjongRound round) {
        List<MahjongClaim> claims = round.getClaims();
        List<MahjongClaim> huCapable = claims.stream().filter(MahjongClaim::isHu).toList();
        if (huCapable.stream().anyMatch(MahjongClaim::isPending)) {
            return;
        }
        List<Integer> winners = huCapable.stream()
                .filter(claim -> claim.getResponse() == MahjongActionType.HU)
                .map(MahjongClaim::getSeat)
                .toList();
        if (!winners.isEmpty()) {
            if (!MahjongRules.of(round).isMultipleWinners()) {
                winners = winners.subList(0, 1);
            }
            winByDiscard(round, winners);
            return;
        }
        for (MahjongClaim claim : claims) {
            if (claim.isPong() || claim.isKong()) {
                if (claim.isPending()) {
                    return;
                }
                if (claim.getResponse() == MahjongActionType.KONG) {
                    claimKong(round, claim.getSeat());
                    return;
                }
                if (claim.getResponse() == MahjongActionType.PONG) {
                    claimPong(round, claim.getSeat());
                    return;
                }
            }
        }
        for (MahjongClaim claim : claims) {
            if (!claim.getChiStarts().isEmpty()) {
                if (claim.isPending()) {
                    return;
                }
                if (claim.getResponse() == MahjongActionType.CHI) {
                    claimChi(round, claim.getSeat(), claim.getResponseChiStart());
                    return;
                }
            }
        }
        int discarder = round.getLastDiscardSeat();
        round.getClaims().clear();
        drawNext(round, round.nextSeat(discarder));
    }

    private static void claimPong(MahjongRound round, int seat) {
        int tile = round.getLastDiscard();
        int from = round.getLastDiscardSeat();
        MahjongPlayer player = round.player(seat);
        removeTiles(player.getHand(), tile, 2);
        player.getMelds().add(new MahjongMeld(MahjongMeld.Type.PONG, new ArrayList<>(List.of(tile, tile, tile)),
                from, tile));
        takeLastDiscard(round);
        enterTurnAfterClaim(round, seat, tile);
        round.emit(MahjongEvent.open(MahjongEvent.PONG, seat, tile));
    }

    private static void claimChi(MahjongRound round, int seat, int start) {
        int tile = round.getLastDiscard();
        int from = round.getLastDiscardSeat();
        MahjongPlayer player = round.player(seat);
        for (int t = start; t < start + 3; t++) {
            if (t != tile) {
                removeTiles(player.getHand(), t, 1);
            }
        }
        player.getMelds().add(new MahjongMeld(MahjongMeld.Type.CHI,
                new ArrayList<>(List.of(start, start + 1, start + 2)), from, tile));
        takeLastDiscard(round);
        enterTurnAfterClaim(round, seat, tile);
        round.emit(MahjongEvent.open(MahjongEvent.CHI, seat, tile));
    }

    private static void claimKong(MahjongRound round, int seat) {
        int tile = round.getLastDiscard();
        int from = round.getLastDiscardSeat();
        MahjongPlayer player = round.player(seat);
        removeTiles(player.getHand(), tile, 3);
        player.getMelds().add(new MahjongMeld(MahjongMeld.Type.MING_GANG,
                new ArrayList<>(List.of(tile, tile, tile, tile)), from, tile));
        takeLastDiscard(round);
        round.getClaims().clear();
        round.emit(MahjongEvent.open(MahjongEvent.MING_GANG, seat, tile));
        replacementDraw(round, seat);
    }

    private static void enterTurnAfterClaim(MahjongRound round, int seat, int restrictedTile) {
        round.getClaims().clear();
        round.setPhase(MahjongPhase.ACTION);
        round.setTurn(seat);
        round.setDrawnTile(null);
        round.setReplacementDraw(false);
        round.setJustClaimed(true);
        round.setRestrictedTile(restrictedTile);
    }

    private static void takeLastDiscard(MahjongRound round) {
        List<Integer> discards = round.player(round.getLastDiscardSeat()).getDiscards();
        if (!discards.isEmpty()) {
            discards.remove(discards.size() - 1);
        }
    }

    // ------------------------------------------------------------------ 摸牌、杠

    private static void drawNext(MahjongRound round, int seat) {
        round.getClaims().clear();
        if (round.getWall().isEmpty()) {
            drawGame(round);
            return;
        }
        int tile = round.getWall().remove(0);
        startTurnWithDraw(round, seat, tile, false);
    }

    private static void replacementDraw(MahjongRound round, int seat) {
        if (round.getWall().isEmpty()) {
            drawGame(round);
            return;
        }
        int tile = round.getWall().remove(round.getWall().size() - 1);
        startTurnWithDraw(round, seat, tile, true);
    }

    private static void startTurnWithDraw(MahjongRound round, int seat, int tile, boolean replacement) {
        MahjongPlayer player = round.player(seat);
        player.getHand().add(tile);
        round.setPhase(MahjongPhase.ACTION);
        round.setTurn(seat);
        round.setDrawnTile(tile);
        round.setReplacementDraw(replacement);
        round.setJustClaimed(false);
        round.setRestrictedTile(null);
        round.emit(MahjongEvent.secret(MahjongEvent.DRAW, seat, tile));

        // 摸到花亮出来，从牌尾补
        while (round.getDrawnTile() != null && MahjongTiles.isFlower(round.getDrawnTile())) {
            int flower = round.getDrawnTile();
            player.getHand().remove(Integer.valueOf(flower));
            player.getFlowers().add(flower);
            round.emit(MahjongEvent.open(MahjongEvent.FLOWER, seat, flower));
            if (round.getWall().isEmpty()) {
                drawGame(round);
                return;
            }
            int next = round.getWall().remove(round.getWall().size() - 1);
            player.getHand().add(next);
            round.setDrawnTile(next);
            round.setReplacementDraw(true);
            round.emit(MahjongEvent.secret(MahjongEvent.DRAW, seat, next));
        }
        checkFourWild(round, seat);
    }

    /** 百搭：手里集齐四张百搭立即胡 */
    private static void checkFourWild(MahjongRound round, int seat) {
        if (round.isFinished() || round.getWild() == null || !MahjongRules.of(round).isWildEnabled()) {
            return;
        }
        if (MahjongTiles.count(round.player(seat).getHand(), round.getWild()) == 4) {
            winBySelfDraw(round, seat);
        }
    }

    private static void selfKong(MahjongRound round, int seat, int tile) {
        MahjongPlayer player = round.player(seat);
        if (round.isJustClaimed()) {
            throw new MahjongRuleException("吃碰后要先出一张牌");
        }
        if (round.getWall().isEmpty()) {
            throw new MahjongRuleException("牌墙已经摸完，不能再杠");
        }
        MahjongMeld pong = player.getMelds().stream()
                .filter(meld -> meld.getType() == MahjongMeld.Type.PONG && meld.firstTile() == tile)
                .findFirst()
                .orElse(null);
        if (pong != null && player.getHand().contains(tile)) {
            if (player.isQiao() && !Objects.equals(tile, round.getDrawnTile())) {
                throw new MahjongRuleException("敲了之后只能用摸到的牌加杠");
            }
            player.getHand().remove(Integer.valueOf(tile));
            pong.setType(MahjongMeld.Type.JIA_GANG);
            pong.getTiles().add(tile);
            round.emit(MahjongEvent.open(MahjongEvent.JIA_GANG, seat, tile));
            replacementDraw(round, seat);
            return;
        }
        if (player.isQiao()) {
            throw new MahjongRuleException("敲了之后不能暗杠");
        }
        if (MahjongTiles.count(player.getHand(), tile) < 4) {
            throw new MahjongRuleException("不能杠这张牌");
        }
        removeTiles(player.getHand(), tile, 4);
        player.getMelds().add(new MahjongMeld(MahjongMeld.Type.AN_GANG,
                new ArrayList<>(List.of(tile, tile, tile, tile)), null, null));
        // 暗杠的牌面别人看不到
        round.emit(MahjongEvent.secret(MahjongEvent.AN_GANG, seat, tile));
        replacementDraw(round, seat);
    }

    // ------------------------------------------------------------------ 胡与结算

    static boolean canSelfDrawHu(MahjongRound round, int seat) {
        if (round.getPhase() != MahjongPhase.ACTION || round.getTurn() != seat || round.isJustClaimed()
                || round.getDrawnTile() == null) {
            return false;
        }
        MahjongWin win = MahjongScoring.evaluate(round, seat, round.getDrawnTile(), true);
        return MahjongScoring.meetsWinRequirement(round, seat, win);
    }

    private static void winBySelfDraw(MahjongRound round, int seat) {
        MahjongRules rules = MahjongRules.of(round);
        int tile = round.getDrawnTile() != null ? round.getDrawnTile() : lastOf(round.player(seat).getHand());
        MahjongWin win = MahjongScoring.evaluate(round, seat, tile, true);
        if (win == null) {
            throw new MahjongRuleException("现在不能胡");
        }
        int[] deltas = new int[round.getSeatCount()];
        int pay = rules.capped(win.getMultiple());
        win.setPayEach(pay);
        for (int other = 0; other < round.getSeatCount(); other++) {
            if (other != seat) {
                win.getPayers().add(other);
                deltas[other] -= pay;
                deltas[seat] += pay;
            }
        }
        MahjongRoundResult result = new MahjongRoundResult();
        result.setSelfDraw(true);
        result.setWinningTile(tile);
        result.getWins().add(win);
        round.emit(MahjongEvent.open(MahjongEvent.HU, seat, tile));
        finish(round, result, deltas);
    }

    private static void winByDiscard(MahjongRound round, List<Integer> winners) {
        MahjongRules rules = MahjongRules.of(round);
        int tile = round.getLastDiscard();
        int from = round.getLastDiscardSeat();
        int[] deltas = new int[round.getSeatCount()];
        MahjongRoundResult result = new MahjongRoundResult();
        result.setSelfDraw(false);
        result.setDiscarder(from);
        result.setWinningTile(tile);
        for (int seat : winners) {
            MahjongWin win = MahjongScoring.evaluate(round, seat, tile, false);
            int pay = rules.capped(win.getMultiple());
            win.setPayEach(pay);
            win.getPayers().add(from);
            deltas[from] -= pay;
            deltas[seat] += pay;
            result.getWins().add(win);
            round.emit(MahjongEvent.open(MahjongEvent.HU, seat, tile));
        }
        takeLastDiscard(round);
        round.getClaims().clear();
        finish(round, result, deltas);
    }

    private static void drawGame(MahjongRound round) {
        MahjongRoundResult result = new MahjongRoundResult();
        result.setDrawGame(true);
        round.emit(MahjongEvent.open(MahjongEvent.DRAW_GAME, round.getTurn(), null));
        finish(round, result, new int[round.getSeatCount()]);
    }

    private static void finish(MahjongRound round, MahjongRoundResult result, int[] deltas) {
        for (int seat = 0; seat < round.getSeatCount(); seat++) {
            result.getDeltas().add(deltas[seat]);
            result.getSeatFlowers().add(MahjongScoring.exposedFlowers(round, round.player(seat)));
        }
        round.getClaims().clear();
        round.setResult(result);
        round.setPhase(MahjongPhase.FINISHED);
    }

    // ------------------------------------------------------------------ 工具

    static List<Integer> discardable(MahjongRound round, MahjongPlayer player) {
        if (player.isQiao()) {
            return round.getDrawnTile() != null ? List.of(round.getDrawnTile()) : List.of();
        }
        TreeSet<Integer> tiles = new TreeSet<>(player.getHand());
        Integer restricted = round.isJustClaimed() ? round.getRestrictedTile() : null;
        if (restricted != null && tiles.size() > 1) {
            tiles.remove(restricted);
        }
        return new ArrayList<>(tiles);
    }

    static List<Integer> kongTiles(MahjongRound round, MahjongPlayer player) {
        if (round.isJustClaimed() || round.getWall().isEmpty()) {
            return List.of();
        }
        TreeSet<Integer> tiles = new TreeSet<>();
        for (MahjongMeld meld : player.getMelds()) {
            int tile = meld.firstTile();
            if (meld.getType() == MahjongMeld.Type.PONG && player.getHand().contains(tile)
                    && (!player.isQiao() || Objects.equals(tile, round.getDrawnTile()))) {
                tiles.add(tile);
            }
        }
        if (!player.isQiao()) {
            for (int tile : new TreeSet<>(player.getHand())) {
                if (MahjongTiles.count(player.getHand(), tile) == 4) {
                    tiles.add(tile);
                }
            }
        }
        return new ArrayList<>(tiles);
    }

    static List<Integer> waitsAfterDiscard(MahjongRound round, MahjongPlayer player, int tile) {
        List<Integer> rest = new ArrayList<>(player.getHand());
        rest.remove(Integer.valueOf(tile));
        return waitsOf(round, player, rest);
    }

    static List<Integer> waitsOf(MahjongRound round, MahjongPlayer player, List<Integer> hand) {
        Integer wild = MahjongRules.of(round).isWildEnabled() ? round.getWild() : null;
        int[] counts = new int[MahjongTiles.COUNT_SIZE];
        int jokers = 0;
        for (int tile : hand) {
            if (wild != null && tile == wild) {
                jokers++;
            } else {
                counts[tile]++;
            }
        }
        return MahjongHandAnalyzer.waits(counts, jokers, 4 - player.getMelds().size());
    }

    static MahjongClaim findClaim(MahjongRound round, int seat) {
        for (MahjongClaim claim : round.getClaims()) {
            if (claim.getSeat() == seat) {
                return claim;
            }
        }
        return null;
    }

    private static boolean hasOtherTile(List<Integer> hand, int tile) {
        for (int item : hand) {
            if (item != tile) {
                return true;
            }
        }
        return false;
    }

    private static void removeTiles(List<Integer> hand, int tile, int copies) {
        for (int i = 0; i < copies; i++) {
            if (!hand.remove(Integer.valueOf(tile))) {
                throw new MahjongRuleException("手里的牌不够");
            }
        }
    }

    private static int lastOf(List<Integer> tiles) {
        return tiles.get(tiles.size() - 1);
    }
}
