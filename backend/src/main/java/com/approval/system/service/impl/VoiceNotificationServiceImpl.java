package com.approval.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.approval.system.config.IhuYiVoiceConfig;
import com.approval.system.service.IVoiceNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Service
public class VoiceNotificationServiceImpl implements IVoiceNotificationService {

    @Autowired
    private IhuYiVoiceConfig voiceConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public boolean sendVoiceNotification(String mobile, String content) {
        if (!voiceConfig.getEnabled()) {
            log.info("语音通知功能已关闭，跳过发送");
            return false;
        }

        try {
            // 获取当前时间戳（10位）
            long timestamp = Instant.now().getEpochSecond();

            // 生成动态密码
            String dynamicPassword = generateDynamicPassword(mobile, content, timestamp);

            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("account", voiceConfig.getAccount());
            params.add("password", dynamicPassword);
            params.add("mobile", mobile);
            params.add("content", content);
            params.add("templateid", voiceConfig.getTemplateId());
            params.add("time", String.valueOf(timestamp));

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(
                    voiceConfig.getApiUrl(),
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.info("语音通知API响应: {}", responseBody);

                // 解析响应
                JSONObject jsonResponse = JSON.parseObject(responseBody);
                Integer code = jsonResponse.getInteger("code");
                String msg = jsonResponse.getString("msg");

                if (code != null && code == 2) {
                    log.info("语音通知发送成功，手机号: {}, 流水号: {}", mobile, jsonResponse.getString("voiceid"));
                    return true;
                } else {
                    log.error("语音通知发送失败，手机号: {}, 错误码: {}, 错误信息: {}", mobile, code, msg);
                    return false;
                }
            } else {
                log.error("语音通知API请求失败，状态码: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("发送语音通知异常，手机号: {}", mobile, e);
            return false;
        }
    }

    @Override
    public boolean sendVoiceNotificationWithFullContent(String mobile, String content) {
        if (!voiceConfig.getEnabled()) {
            log.info("语音通知功能已关闭，跳过发送");
            return false;
        }

        try {
            // 构建请求参数（不使用模板，直接发送完整内容）
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("account", voiceConfig.getAccount());
            params.add("password", voiceConfig.getPassword());  // 使用固定密码
            params.add("mobile", mobile);
            params.add("content", content);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(
                    voiceConfig.getApiUrl(),
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.info("语音通知API响应: {}", responseBody);

                JSONObject jsonResponse = JSON.parseObject(responseBody);
                Integer code = jsonResponse.getInteger("code");
                String msg = jsonResponse.getString("msg");

                if (code != null && code == 2) {
                    log.info("语音通知发送成功，手机号: {}", mobile);
                    return true;
                } else {
                    log.error("语音通知发送失败，手机号: {}, 错误码: {}, 错误信息: {}", mobile, code, msg);
                    return false;
                }
            } else {
                log.error("语音通知API请求失败，状态码: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("发送语音通知异常，手机号: {}", mobile, e);
            return false;
        }
    }

    @Override
    public boolean notifyApproverNewApplication(String mobile, String applicantName, String title) {
        if (mobile == null || mobile.isEmpty()) {
            log.warn("审批人手机号为空，无法发送语音通知");
            return false;
        }

        // 使用默认模板：您的订单号是：【变量】。已由【变量】发出，请注意查收。
        // 这里将其改编为：您有新的审批申请：【变量】。由【变量】提交，请及时处理。
        // 模板变量内容格式：变量1|变量2
        String content = title + "|" + applicantName;

        return sendVoiceNotification(mobile, content);
    }

    /**
     * 生成动态密码
     * 密码=MD5(account + password + mobile + content + time)
     *
     * @param mobile 手机号
     * @param content 内容
     * @param timestamp 时间戳
     * @return 动态密码
     */
    private String generateDynamicPassword(String mobile, String content, long timestamp) {
        String rawString = voiceConfig.getAccount() +
                voiceConfig.getPassword() +
                mobile +
                content +
                timestamp;

        return DigestUtils.md5DigestAsHex(rawString.getBytes(StandardCharsets.UTF_8));
    }
}
