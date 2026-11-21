package com.wlf.ai.rag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RAG 问答入参。
 *
 * @param message        用户这一轮说的话
 * @param conversationId 上一轮返回的会话 id；留空 = 开一段新会话
 */
public record RagChatRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 2000, message = "消息不能超过 2000 个字符")
        String message,

        @Size(max = 36, message = "会话标识不合法")
        String conversationId) {
}
