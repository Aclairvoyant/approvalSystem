package com.approval.system.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO 约束与数据库列长度、前端表单规则保持一致。
 */
class RequestDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<String> messages(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private static RegisterRequest validRegistration() {
        return RegisterRequest.builder()
                .username("zhouyu")
                .phone("13800000001")
                .password("secret1")
                .email("zhouyu@example.com")
                .realName("周屿")
                .emailVerificationCode("123456")
                .build();
    }

    @Test
    void validRegistrationPasses() {
        assertThat(messages(validRegistration())).isEmpty();
    }

    @Test
    void registrationEnforcesUsersTableLimitsAndFormats() {
        RegisterRequest request = validRegistration();
        request.setUsername("u".repeat(51));
        request.setRealName("名".repeat(51));
        request.setPhone("12345");
        request.setPassword("12345");
        request.setEmail("not-an-email");

        assertThat(messages(request)).containsExactlyInAnyOrder(
                "用户名不能超过50个字符",
                "姓名不能超过50个字符",
                "请输入正确的手机号",
                "密码长度需为6-64位",
                "邮箱格式不正确");
    }

    @Test
    void registrationRequiresUsernamePhoneAndPassword() {
        assertThat(messages(new RegisterRequest())).containsExactlyInAnyOrder("请输入用户名", "请输入手机号", "请输入密码");
    }

    @Test
    void applicationCreateRequiresApproverAndTitleWithinColumnLimits() {
        ApplicationCreateRequest request = ApplicationCreateRequest.builder()
                .title(" ")
                .remark("备".repeat(501))
                .build();

        assertThat(messages(request)).containsExactlyInAnyOrder("请选择审批人", "请输入标题", "备注不能超过500个字符");

        request.setTitle("标".repeat(256));
        assertThat(messages(request)).contains("标题不能超过255个字符");
    }

    @Test
    void applicationUpdateDoesNotRequireApprover() {
        ApplicationUpdateRequest request = ApplicationUpdateRequest.builder().title("周末加班").build();

        assertThat(messages(request)).isEmpty();
    }

    @Test
    void approvalDetailIsLimitedToOperationLogColumn() {
        assertThat(messages(new ApplicationApprovalRequest("意".repeat(500)))).isEmpty();
        assertThat(messages(new ApplicationApprovalRequest("意".repeat(501)))).containsExactly("审批意见不能超过500个字符");
    }

    @Test
    void taskCompletionTakesRecordIdFromPathSoBodyMayOmitIt() {
        // 前端只提交 { completionNote }；recordId 在 URL 里
        assertThat(messages(TaskCompleteRequest.builder().completionNote("完成啦").build())).isEmpty();
    }

    @Test
    void commentContentMustNotBeBlank() {
        assertThat(messages(new CommentCreateRequest(99L, "  ", null))).containsExactly("评论内容不能为空");
        assertThat(messages(new CommentCreateRequest(null, "hi", null))).containsExactly("申请ID不能为空");
    }

    @Test
    void coupleEventRejectsUnknownTypeAndMissingDate() {
        CoupleEventRequest request = new CoupleEventRequest();
        request.setTitle("在一起纪念日");
        request.setEventType(9);
        request.setRepeatType(1);

        assertThat(messages(request)).containsExactlyInAnyOrder("纪念日类型不正确", "请选择日期");

        request.setEventType(1);
        request.setEventDate(LocalDate.of(2024, 5, 20));
        assertThat(messages(request)).isEmpty();
    }

    @Test
    void dailyItemRejectsUnknownPriority() {
        DailyItemRequest request = new DailyItemRequest();
        request.setTitle("周末买菜");
        request.setPriority(3);

        assertThat(messages(request)).containsExactly("优先级不正确");
    }

    @Test
    void profileUpdateIsPartialButValidatesProvidedFields() {
        assertThat(messages(new UserUpdateRequest())).isEmpty();
        assertThat(messages(UserUpdateRequest.builder().phone("1380000").build())).containsExactly("请输入正确的手机号");
    }

    @Test
    void adminUserUpdateAllowsClearingEmail() {
        assertThat(messages(AdminUserUpdateRequest.builder().phone("13800000001").email("").build())).isEmpty();
        assertThat(messages(AdminUserUpdateRequest.builder().email("bad@").build())).containsExactly("邮箱格式不正确");
    }

    @Test
    void emailSettingsPortMustBeAValidTcpPort() {
        EmailSettingsUpdateRequest request = new EmailSettingsUpdateRequest();
        request.setPort(70000);

        assertThat(messages(request)).containsExactly("SMTP 端口必须在 1-65535 之间");
    }

    @Test
    void sendEmailCodeKeepsTheOriginalEmailPattern() {
        assertThat(messages(new SendEmailCodeRequest("zhou.yu@example.com"))).isEmpty();
        assertThat(messages(new SendEmailCodeRequest("zhouyu@localhost"))).containsExactly("邮箱格式不正确");
        assertThat(messages(new SendEmailCodeRequest(""))).contains("请提供邮箱地址");
    }
}
