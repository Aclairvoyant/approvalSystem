package com.approval.system.service.notification;

/**
 * 一份申请进入"待审批"状态：普通申请创建成功，或语音申请的音频处理完成。
 * 事务提交后由 {@link ApplicationNotifier} 通知审批人。
 *
 * @param sendVoiceNotification 是否额外给审批人打语音电话（仅普通申请创建时由申请人勾选）
 */
public record ApplicationSubmittedEvent(
        Long applicationId,
        Long applicantId,
        Long approverId,
        String title,
        boolean sendVoiceNotification) {
}
