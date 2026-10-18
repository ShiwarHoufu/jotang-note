package com.wlf.ai.dto;

import java.util.List;

/**
 * 恢复最近一次会话的出参。
 *
 * @param conversationId 该用户最近一次对话所在的会话 id；没有任何会话时为 {@code null}
 * @param messages       该会话的历史，按发生顺序正序。
 */
public record AiChatHistoryResponse(String conversationId, List<Message> messages) {

    /** 一条历史消息。 */
    public record Message(String role, String content) {
    }

    /**
     * 无可恢复的会话。
     *
     * <p>用户从没用过对话、以及会话在查询与回显之间被删掉，都走这个形态。
     */
    public static AiChatHistoryResponse empty() {
        return new AiChatHistoryResponse(null, List.of());
    }
}
