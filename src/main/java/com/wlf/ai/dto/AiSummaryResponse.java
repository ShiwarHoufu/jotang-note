package com.wlf.ai.dto;

/**
 * 「自动生成摘要」的响应体。见《概要设计》§5.8。
 *
 * <p>只有一个字段，且<b>不落库</b>：它是给前端填进简介输入框的<b>建议</b>，
 * 存不存由用户随后那次上传（{@code POST /api/notes}）或编辑（{@code PUT /api/notes/{id}}）决定。
 * 因此本接口没有任何服务端状态，也就不存在「生成过但没保存」这类需要过期与清理的中间态——
 * 这正是 §5.8 选择「读过即弃、不做暂存」的直接后果。
 *
 * @param summary 摘要正文，已按 {@code note.summary} 的列宽截断。空串是可能的返回
 *                （模型明确表示图里没有可摘要的内容），调用方不应把它当成错误
 */
public record AiSummaryResponse(String summary) {
}
