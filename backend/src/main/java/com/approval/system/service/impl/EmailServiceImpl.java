package com.approval.system.service.impl;

import cn.hutool.extra.mail.MailAccount;
import cn.hutool.extra.mail.MailUtil;
import com.approval.system.service.IEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.util.Random;

@Slf4j
@Service
public class EmailServiceImpl implements IEmailService {

    @Autowired
    private CacheManager cacheManager;

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.port}")
    private Integer mailPort;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.mail.password}")
    private String mailPassword;

    private static final int CODE_LENGTH = 6;
    private static final int RATE_LIMIT_SECONDS = 60; // 60秒内只能发送一次

    /**
     * 创建邮件账号配置
     */
    private MailAccount createMailAccount() {
        MailAccount account = new MailAccount();
        account.setHost(mailHost);
        account.setPort(mailPort);
        account.setAuth(true);
        account.setUser(fromEmail);
        account.setPass(mailPassword);
        account.setFrom(fromEmail);
        account.setSslEnable(true);
        account.setCharset(Charset.defaultCharset());
        return account;
    }

    @Override
    public boolean sendVerificationCode(String email) {
        try {
            // 检查频率限制
            if (!checkRateLimit(email)) {
                log.warn("邮件发送频率过高，邮箱: {}", email);
                return false;
            }

            // 生成6位数验证码
            String code = generateVerificationCode();

            // 构建邮件内容
            String content = buildVerificationCodeEmail(code);

            // 发送邮件
            MailAccount account = createMailAccount();
            MailUtil.send(account, email, "【审批管理系统】邮箱验证码", content, true);

            // 将验证码存入缓存（5分钟有效）
            Cache verificationCache = cacheManager.getCache("emailVerificationCodes");
            if (verificationCache != null) {
                verificationCache.put(email, code);
            }

            // 记录发送时间（用于限流）
            Cache sendRecordCache = cacheManager.getCache("emailSendRecords");
            if (sendRecordCache != null) {
                sendRecordCache.put(email, System.currentTimeMillis());
            }

            log.info("邮箱验证码发送成功，邮箱: {}", email);
            return true;
        } catch (Exception e) {
            log.error("邮箱验证码发送失败，邮箱: {}", email, e);
            return false;
        }
    }

    @Override
    public boolean verifyCode(String email, String code) {
        if (email == null || code == null) {
            return false;
        }

        Cache verificationCache = cacheManager.getCache("emailVerificationCodes");
        if (verificationCache == null) {
            return false;
        }

        String cachedCode = verificationCache.get(email, String.class);
        if (cachedCode == null) {
            log.warn("验证码不存在或已过期，邮箱: {}", email);
            return false;
        }

        boolean isValid = code.equals(cachedCode);
        if (isValid) {
            // 验证成功后清除验证码
            verificationCache.evict(email);
            log.info("邮箱验证码验证成功，邮箱: {}", email);
        } else {
            log.warn("邮箱验证码验证失败，邮箱: {}", email);
        }

        return isValid;
    }

    @Override
    public boolean sendApplicationNotification(String email, String applicantName, String title, Long applicationId) {
        try {
            String content = buildApplicationNotificationEmail(applicantName, title, applicationId);

            // 发送邮件
            MailAccount account = createMailAccount();
            MailUtil.send(account, email, "【审批管理系统】您有新的待审批申请", content, true);

            log.info("申请通知邮件发送成功，邮箱: {}, 申请ID: {}", email, applicationId);
            return true;
        } catch (Exception e) {
            log.error("申请通知邮件发送失败，邮箱: {}, 申请ID: {}", email, applicationId, e);
            return false;
        }
    }

    @Override
    public boolean checkRateLimit(String email) {
        Cache sendRecordCache = cacheManager.getCache("emailSendRecords");
        if (sendRecordCache == null) {
            return true;
        }

        Long lastSendTime = sendRecordCache.get(email, Long.class);
        if (lastSendTime == null) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - lastSendTime) / 1000;

        return elapsedSeconds >= RATE_LIMIT_SECONDS;
    }

    /**
     * 生成6位数字验证码
     */
    private String generateVerificationCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 构建验证码邮件内容
     */
    private String buildVerificationCodeEmail(String code) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }" +
                ".content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }" +
                ".code-box { background: white; border: 2px dashed #667eea; border-radius: 8px; padding: 20px; text-align: center; margin: 20px 0; }" +
                ".code { font-size: 32px; font-weight: bold; color: #667eea; letter-spacing: 8px; }" +
                ".notice { color: #999; font-size: 14px; margin-top: 20px; }" +
                ".footer { text-align: center; padding: 20px; color: #999; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\">" +
                "<div class=\"header\">" +
                "<h1>邮箱验证</h1>" +
                "</div>" +
                "<div class=\"content\">" +
                "<p>您好，</p>" +
                "<p>您正在进行邮箱验证，验证码为：</p>" +
                "<div class=\"code-box\">" +
                "<div class=\"code\">" + code + "</div>" +
                "</div>" +
                "<p class=\"notice\">验证码有效期为5分钟，请及时使用。如非本人操作，请忽略此邮件。</p>" +
                "</div>" +
                "<div class=\"footer\">" +
                "<p>此邮件由系统自动发送，请勿回复。</p>" +
                "<p>&copy; 2025 审批管理系统</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    /**
     * 构建申请通知邮件内容
     */
    private String buildApplicationNotificationEmail(String applicantName, String title, Long applicationId) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }" +
                ".content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }" +
                ".info-box { background: white; border-left: 4px solid #667eea; padding: 15px; margin: 15px 0; border-radius: 4px; }" +
                ".info-label { color: #999; font-size: 14px; margin-bottom: 5px; }" +
                ".info-value { color: #333; font-size: 16px; font-weight: 500; }" +
                ".button { display: inline-block; background: #667eea; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; margin-top: 20px; }" +
                ".footer { text-align: center; padding: 20px; color: #999; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\">" +
                "<div class=\"header\">" +
                "<h1>新的待审批申请</h1>" +
                "</div>" +
                "<div class=\"content\">" +
                "<p>您好，</p>" +
                "<p>您收到了一个新的待审批申请，详情如下：</p>" +
                "<div class=\"info-box\">" +
                "<div class=\"info-label\">申请人</div>" +
                "<div class=\"info-value\">" + applicantName + "</div>" +
                "</div>" +
                "<div class=\"info-box\">" +
                "<div class=\"info-label\">申请事项</div>" +
                "<div class=\"info-value\">" + title + "</div>" +
                "</div>" +
                "<div class=\"info-box\">" +
                "<div class=\"info-label\">申请编号</div>" +
                "<div class=\"info-value\">#" + applicationId + "</div>" +
                "</div>" +
                "<p style=\"margin-top: 20px;\">请登录系统查看详情并及时处理。</p>" +
                "</div>" +
                "<div class=\"footer\">" +
                "<p>此邮件由系统自动发送，请勿回复。</p>" +
                "<p>&copy; 2025 审批管理系统</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
