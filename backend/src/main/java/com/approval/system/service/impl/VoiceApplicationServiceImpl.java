package com.approval.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.approval.system.config.MiMoConfig;
import com.approval.system.dto.VoiceParseResult;
import com.approval.system.service.IVoiceApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * 语音填写申请服务实现。
 * <p>
 * 调用小米 MiMo（OpenAI 兼容接口，鉴权头为 {@code api-key}）完成两段处理：
 * <ol>
 *     <li>{@code mimo-v2.5-asr} 把音频转写为文字</li>
 *     <li>{@code mimo-v2.5} 把转写文字抽取为申请字段 JSON</li>
 * </ol>
 */
@Slf4j
@Service
public class VoiceApplicationServiceImpl implements IVoiceApplicationService {

    @Autowired
    private MiMoConfig miMoConfig;

    @Autowired
    private RestTemplate restTemplate;

    /** 抽取字段用的系统提示词 */
    private static final String EXTRACT_SYSTEM_PROMPT =
            "你是一个审批申请信息抽取助手。用户会用口语描述一件想申请的事情及理由，" +
            "你需要从中抽取出结构化的申请信息，并严格以 JSON 返回，不要输出任何额外文字或解释，不要使用 markdown 代码块。" +
            "返回格式：{\"title\":\"事项标题\",\"description\":\"事项描述\",\"remark\":\"备注\"}。" +
            "要求：title 为简短概括（不超过20字）；description 完整描述申请内容及理由；" +
            "remark 为补充说明，没有则留空字符串。所有字段都必须是字符串。";

    @Override
    public VoiceParseResult parseVoice(MultipartFile audio, String language) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("音频内容为空");
        }

        // 1) 语音转写
        String transcript;
        try {
            transcript = transcribe(audio.getBytes(), audio.getContentType(), audio.getOriginalFilename(), language);
        } catch (IOException e) {
            log.error("读取音频内容失败", e);
            throw new IllegalStateException("读取音频内容失败");
        }
        if (!StringUtils.hasText(transcript)) {
            throw new IllegalStateException("未能识别出语音内容，请重试或说得更清楚一些");
        }

        // 2) 抽取申请字段
        VoiceParseResult result = extractFields(transcript);
        result.setTranscript(transcript);
        return result;
    }

    /**
     * 调用 mimo-v2.5-asr 把音频转写为文字。
     */
    @Override
    public String transcribe(byte[] audioBytes, String contentType, String filename, String language) {
        validateMimoConfig();
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("音频内容为空");
        }

        String mime = resolveAudioMime(contentType, filename);
        String base64 = Base64.getEncoder().encodeToString(audioBytes);
        String dataUrl = "data:" + mime + ";base64," + base64;

        JSONObject inputAudio = new JSONObject();
        inputAudio.put("data", dataUrl);
        JSONObject audioPart = new JSONObject();
        audioPart.put("type", "input_audio");
        audioPart.put("input_audio", inputAudio);

        JSONArray content = new JSONArray();
        content.add(audioPart);
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", content);

        JSONObject body = new JSONObject();
        body.put("model", miMoConfig.getAsrModel());
        body.put("messages", new JSONArray().fluentAdd(message));
        JSONObject asrOptions = new JSONObject();
        asrOptions.put("language", StringUtils.hasText(language) ? language : "zh");
        body.put("asr_options", asrOptions);

        String responseText = postChatCompletion(body, "语音识别");
        return extractMessageContent(responseText);
    }

    private void validateMimoConfig() {
        if (miMoConfig.getEnabled() == null || !miMoConfig.getEnabled()) {
            throw new IllegalStateException("语音转文字功能未启用");
        }
        if (!StringUtils.hasText(miMoConfig.getApiKey()) || "your-mimo-api-key".equals(miMoConfig.getApiKey())) {
            throw new IllegalStateException("MiMo API Key 未配置，请联系管理员");
        }
    }

    /**
     * 调用 mimo-v2.5 从转写文字中抽取申请字段。
     */
    private VoiceParseResult extractFields(String transcript) {
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", EXTRACT_SYSTEM_PROMPT);
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", transcript);

        JSONObject body = new JSONObject();
        body.put("model", miMoConfig.getChatModel());
        body.put("messages", new JSONArray().fluentAdd(systemMsg).fluentAdd(userMsg));
        body.put("temperature", 0.2);

        String responseText = postChatCompletion(body, "字段抽取");
        String content = extractMessageContent(responseText);
        return parseExtractedJson(content, transcript);
    }

    /**
     * 发送 chat/completions 请求，返回响应体字符串。
     */
    private String postChatCompletion(JSONObject body, String stage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // MiMo 鉴权头为 api-key，而非标准的 Authorization: Bearer
        headers.set("api-key", miMoConfig.getApiKey());

        HttpEntity<String> request = new HttpEntity<>(body.toJSONString(), headers);
        String url = miMoConfig.getBaseUrl() + "/chat/completions";

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("MiMo {} 请求失败，状态码: {}", stage, response.getStatusCode());
                throw new IllegalStateException("MiMo " + stage + "请求失败");
            }
            return response.getBody();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("MiMo {} 调用异常", stage, e);
            throw new IllegalStateException("MiMo " + stage + "调用异常：" + e.getMessage());
        }
    }

    /**
     * 从 OpenAI 兼容响应中取出 choices[0].message.content。
     */
    private String extractMessageContent(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(responseBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("MiMo 响应缺少 choices: {}", responseBody);
                return null;
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            if (message == null) {
                return null;
            }
            return message.getString("content");
        } catch (Exception e) {
            log.error("解析 MiMo 响应失败: {}", responseBody, e);
            return null;
        }
    }

    /**
     * 解析抽取模型返回的 JSON 字符串为申请字段。
     * 容错处理：模型可能包裹 markdown 代码块或返回非法 JSON。
     */
    private VoiceParseResult parseExtractedJson(String content, String transcript) {
        VoiceParseResult result = VoiceParseResult.builder().build();
        if (!StringUtils.hasText(content)) {
            // 抽取失败则降级：把转写原文塞进描述
            result.setDescription(transcript);
            return result;
        }

        String cleaned = stripCodeFence(content).trim();
        try {
            JSONObject json = JSON.parseObject(cleaned);
            result.setTitle(trimToNull(json.getString("title")));
            result.setDescription(trimToNull(json.getString("description")));
            result.setRemark(emptyToBlank(json.getString("remark")));
        } catch (Exception e) {
            log.warn("抽取结果非合法 JSON，降级使用原文。content={}", content);
            result.setDescription(transcript);
        }

        // 描述兜底，避免空表单
        if (!StringUtils.hasText(result.getDescription())) {
            result.setDescription(transcript);
        }
        return result;
    }

    /**
     * 去掉 markdown 代码块包裹（```json ... ```）。
     */
    private String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t;
    }

    /**
     * 根据上传文件的 content-type / 文件名推断 MiMo 所需的 MIME 类型。
     * MiMo 仅支持 wav 与 mp3。
     */
    private String resolveAudioMime(String contentType, String filename) {
        String lowerName = filename == null ? "" : filename.toLowerCase();

        if (lowerName.endsWith(".mp3")
                || "audio/mpeg".equals(contentType) || "audio/mp3".equals(contentType)) {
            return "audio/mpeg";
        }
        if (lowerName.endsWith(".wav") || "audio/wav".equals(contentType) || "audio/x-wav".equals(contentType)) {
            return "audio/wav";
        }
        // 默认按 wav 处理（前端固定上传 16k 单声道 wav）
        return "audio/wav";
    }

    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private String emptyToBlank(String s) {
        return s == null ? "" : s.trim();
    }
}
