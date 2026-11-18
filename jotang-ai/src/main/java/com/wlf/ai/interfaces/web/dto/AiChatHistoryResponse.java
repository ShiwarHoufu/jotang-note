package com.wlf.ai.interfaces.web.dto;

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
}
