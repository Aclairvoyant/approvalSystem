package com.approval.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音解析结果：包含 ASR 转写原文，以及 MiMo 抽取出的申请字段。
 * 仅用于回填前端表单，不直接创建申请。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VoiceParseResult {

    /**
     * 语音识别转写出的原始文字（供用户核对）
     */
    private String transcript;

    /**
     * 抽取出的事项标题
     */
    private String title;

    /**
     * 抽取出的事项描述
     */
    private String description;

    /**
     * 抽取出的备注（可能为空）
     */
    private String remark;
}
