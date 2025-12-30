package com.approval.system.service;

/**
 * 邮件服务接口
 */
public interface IEmailService {

    /**
     * 发送邮箱验证码
     *
     * @param email 邮箱地址
     * @return 是否发送成功
     */
    boolean sendVerificationCode(String email);

    /**
     * 验证邮箱验证码
     *
     * @param email 邮箱地址
     * @param code 验证码
     * @return 是否验证成功
     */
    boolean verifyCode(String email, String code);

    /**
     * 发送申请通知邮件给审批人
     *
     * @param email 审批人邮箱
     * @param applicantName 申请人姓名
     * @param title 申请标题
     * @param applicationId 申请ID
     * @return 是否发送成功
     */
    boolean sendApplicationNotification(String email, String applicantName, String title, Long applicationId);

    /**
     * 检查邮件发送频率限制
     *
     * @param email 邮箱地址
     * @return 是否允许发送（true表示可以发送，false表示频率过高）
     */
    boolean checkRateLimit(String email);
}
