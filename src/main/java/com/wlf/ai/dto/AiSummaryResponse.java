package com.wlf.ai.dto;

/**
 * 「自动生成摘要」的响应体。见《概要设计》§5.8。
 *
 * <p>只有一个字段，且不落库：它是给前端填进简介输入框的建议。
 *
 * @param summary 摘要正文，已按 {@code note.summary} 的列宽截断。
 */
public record AiSummaryResponse(String summary) {
}
