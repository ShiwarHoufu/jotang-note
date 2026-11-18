package com.wlf.ai.domain.chat;

import java.util.List;

/** 送模型的上下文窗口：只保留最近 {@code maxMessages} 条。 */
public record ContextWindow(int maxMessages) {

    public static final ContextWindow DEFAULT = new ContextWindow(40);

    public ContextWindow {
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be positive");
        }
    }

    public List<ChatMessage> apply(List<ChatMessage> messages) {
        if (messages.size() <= maxMessages) {
            return List.copyOf(messages);
        }
        return List.copyOf(messages.subList(messages.size() - maxMessages, messages.size()));
    }
}
