package com.wlf.ai.application.assistant.result;

import java.util.List;

/** 恢复最近一次会话的用例输出。 */
public record ChatHistoryResult(String conversationId, List<Message> messages) {

    public record Message(String role, String content) {
    }

    public static ChatHistoryResult empty() {
        return new ChatHistoryResult(null, List.of());
    }
}
