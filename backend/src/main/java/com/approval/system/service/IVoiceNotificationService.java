package com.approval.system.service;

/**
 * 语音通知服务接口
 */
public interface IVoiceNotificationService {

    /**
     * 发送语音通知（使用模板变量方式）
     *
     * @param mobile 手机号码
     * @param content 变量内容（多个变量以 | 隔开）
     * @return 是否发送成功
     */
    boolean sendVoiceNotification(String mobile, String content);

    /**
     * 发送语音通知（完整内容方式）
     *
     * @param mobile 手机号码
     * @param content 完整语音内容
     * @return 是否发送成功
     */
    boolean sendVoiceNotificationWithFullContent(String mobile, String content);

    /**
     * 通知审批人有新的申请待审批
     *
     * @param mobile 审批人手机号
     * @param applicantName 申请人姓名
     * @param title 申请标题
     * @return 是否发送成功
     */
    boolean notifyApproverNewApplication(String mobile, String applicantName, String title);
}
