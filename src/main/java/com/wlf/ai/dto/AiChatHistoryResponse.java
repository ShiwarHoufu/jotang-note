package com.wlf.ai.dto;

import java.util.List;

/**
 * 恢复最近一次会话的出参。
 *
 * @param conversationId 该用户最近一次对话所在的会话 id；<b>没有任何会话时为 {@code null}</b>
 * @param messages       该会话的历史，按发生顺序正序。只带 {@code (role, content)}，
 *                       与对话链路「历史不带时间戳保前缀缓存」同理，前端渲染也不需要更多字段
 */
public record AiChatHistoryResponse(String conversationId, List<Message> messages) {

    /** 一条历史消息。 */
    public record Message(String role, String content) {
    }

    /**
     * 「无可恢复的会话」。
     *
     * <p>用户从没用过对话、以及会话在查询与回显之间被删掉，都走这个形态。
     * 前端只需判 {@code conversationId} 是否为空，不必区分这两种情形。
     */
    public static AiChatHistoryResponse empty() {
        return new AiChatHistoryResponse(null, List.of());
    }
}
