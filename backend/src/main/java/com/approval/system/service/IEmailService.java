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
     * 发送批准通知邮件给申请人
     *
     * @param email 申请人邮箱
     * @param applicantName 申请人姓名
     * @param approverName 审批人姓名
     * @param title 申请标题
     * @param approvalDetail 审批意见
     * @param applicationId 申请ID
     * @return 是否发送成功
     */
    boolean sendApprovalNotification(String email, String applicantName, String approverName,
                                     String title, String approvalDetail, Long applicationId);

    /**
     * 发送驳回通知邮件给申请人
     *
     * @param email 申请人邮箱
     * @param applicantName 申请人姓名
     * @param approverName 审批人姓名
     * @param title 申请标题
     * @param rejectReason 驳回原因
     * @param applicationId 申请ID
     * @return 是否发送成功
     */
    boolean sendRejectionNotification(String email, String applicantName, String approverName,
                                      String title, String rejectReason, Long applicationId);

    /**
     * 检查邮件发送频率限制
     *
     * @param email 邮箱地址
     * @return 是否允许发送(true表示可以发送,false表示频率过高)
     */
    boolean checkRateLimit(String email);

    /**
     * 发送通用邮件
     *
     * @param email 收件人邮箱
     * @param title 邮件标题
     * @param content 邮件内容
     * @return 是否发送成功
     */
    boolean sendEmail(String email, String title, String content);
}
