package com.approval.system.service.notification;

/**
 * 申请被批准或驳回（包括管理员代为审批）。事务提交后由 {@link ApplicationNotifier} 邮件通知申请人。
 *
 * @param operatorId 实际操作人：指定审批人，或代为审批的管理员
 * @param detail     审批意见 / 驳回原因
 */
public record ApplicationDecidedEvent(
        Long applicationId,
        Long applicantId,
        Long operatorId,
        String title,
        boolean approved,
        String detail) {
}
