package com.approval.system.service;

import com.approval.system.dto.VoiceParseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音填写申请服务：把口述音频转写为文字，再抽取成申请字段。
 */
public interface IVoiceApplicationService {

    /**
     * 解析语音音频，返回转写文字与抽取出的申请字段（标题/描述/备注）。
     *
     * @param audio    音频文件（wav 或 mp3）
     * @param language 语种，可选值 auto/zh/en，为空时按 zh 处理
     * @return 解析结果
     */
    VoiceParseResult parseVoice(MultipartFile audio, String language);
}
