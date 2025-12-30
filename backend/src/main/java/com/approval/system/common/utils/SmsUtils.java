package com.approval.system.common.utils;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.approval.system.common.config.AliyunSmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsUtils {

    @Autowired
    private AliyunSmsProperties smsProperties;

    /**
     * 发送短信
     */
    public boolean sendSms(String phone, String title, String content) {
        try {
            // 设置超时时间
            System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
            System.setProperty("sun.net.client.defaultReadTimeout", "10000");

            // 初始化acsClient
            DefaultProfile profile = DefaultProfile.getProfile(
                    smsProperties.getRegionId(),
                    smsProperties.getAccessKeyId(),
                    smsProperties.getAccessKeySecret()
            );
            IAcsClient acsClient = new DefaultAcsClient(profile);

            // 组装请求对象
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumbers(phone);
            request.setSignName(smsProperties.getSignName());
            request.setTemplateCode(smsProperties.getTemplateCode());
            // 可选：设置模板参数，JSON格式
            request.setTemplateParam("{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}");

            // 发送请求
            SendSmsResponse sendSmsResponse = acsClient.getAcsResponse(request);

            if ("OK".equals(sendSmsResponse.getCode())) {
                log.info("短信发送成功，手机号: {}, 消息ID: {}", phone, sendSmsResponse.getBizId());
                return true;
            } else {
                log.error("短信发送失败，手机号: {}, 错误码: {}, 错误信息: {}", phone,
                        sendSmsResponse.getCode(), sendSmsResponse.getMessage());
                return false;
            }
        } catch (ClientException e) {
            log.error("短信发送异常，手机号: {}, 错误: {}", phone, e.getMessage(), e);
            return false;
        }
    }
}
