package com.approval.system.service.mahjong;

/**
 * 不符合规则的操作。消息是给玩家看的中文提示，由服务层转换成 BusinessException。
 */
public class MahjongRuleException extends RuntimeException {

    public MahjongRuleException(String message) {
        super(message);
    }
}
