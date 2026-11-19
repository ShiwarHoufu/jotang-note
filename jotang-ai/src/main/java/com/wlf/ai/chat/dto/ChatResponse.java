package com.wlf.ai.chat.dto;

/**
 * 对话响应体。
 *
 * @param reply          模型的回答正文。空串是可能的返回（模型返回了空内容），不是错误
 * @param conversationId 本轮所属的会话 id
 */
public record ChatResponse(String reply, String conversationId) {
}
