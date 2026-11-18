package com.wlf.ai.domain.chat;

import java.util.Objects;

/** 一条对话消息。 */
public record ChatMessage(ChatMessageRole role, String content) {

    public ChatMessage {
        Objects.requireNonNull(role);
        Objects.requireNonNull(content);
    }
}
