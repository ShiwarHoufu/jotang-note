package com.wlf.ai.dto;

/**
 * 对话响应体。服务端不做会话记忆以外的任何事：消息落库只为下一轮带上下文。
 *
 * @param reply          模型的回答正文。空串是可能的返回（模型返回了空内容），不是错误
 * @param conversationId 本轮所属的会话 id。
 */
public record AiChatResponse(String reply, String conversationId) {
}
