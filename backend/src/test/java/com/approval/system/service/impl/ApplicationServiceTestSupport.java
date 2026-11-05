package com.approval.system.service.impl;

import com.approval.system.mapper.ApplicationMapper;
import com.approval.system.mapper.OperationLogMapper;
import com.approval.system.mapper.UserMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 用 mock mapper 手工组装 ApplicationServiceImpl（不启动 Spring）。
 * 操作日志走真实的 OperationLogServiceImpl，方便断言写入的 OperationLog。
 */
final class ApplicationServiceTestSupport {

    private ApplicationServiceTestSupport() {
    }

    static OperationLogServiceImpl operationLogService(OperationLogMapper operationLogMapper) {
        OperationLogServiceImpl operationLogService = new OperationLogServiceImpl();
        ReflectionTestUtils.setField(operationLogService, "baseMapper", operationLogMapper);
        return operationLogService;
    }

    static ApplicationServiceImpl applicationService(ApplicationMapper applicationMapper,
                                                     UserMapper userMapper,
                                                     OperationLogMapper operationLogMapper,
                                                     VoiceApplicationProcessor voiceApplicationProcessor,
                                                     ApplicationEventPublisher eventPublisher) {
        ApplicationServiceImpl service = new ApplicationServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", applicationMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "operationLogService", operationLogService(operationLogMapper));
        ReflectionTestUtils.setField(service, "voiceApplicationProcessor", voiceApplicationProcessor);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        return service;
    }
}
