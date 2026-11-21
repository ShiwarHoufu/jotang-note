package com.wlf.ai.rag.chat.dto;

/**
 * RAG 问答响应体。
 *
 * @param reply          模型的回答正文
 * @param conversationId 本轮所属的会话 id
 */
public record RagChatResponse(String reply, String conversationId) {
}
