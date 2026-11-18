package com.wlf.ai.domain.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 一段对话。
 *
 * <p>消息按轮成对写入；对外只暴露最近窗口给模型。
 */
public final class Conversation {

    private final String id;
    private final Long userId;
    private final List<ChatMessage> messages;
    private final List<ChatMessage> pending = new ArrayList<>();

    private Conversation(String id, Long userId, List<ChatMessage> messages) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.messages = new ArrayList<>(messages);
    }

    public static Conversation start(Long userId) {
        return new Conversation(UUID.randomUUID().toString(), userId, List.of());
    }

    public static Conversation restore(Long userId, String id, List<ChatMessage> messages) {
        return new Conversation(id, userId, messages);
    }

    public String id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    public List<ChatMessage> contextWindow(ContextWindow window) {
        return window.apply(messages);
    }

    /** 一轮问答落两条消息，避免模型失败时历史里只留半轮。 */
    public void appendTurn(String userText, String assistantText) {
        ChatMessage user = new ChatMessage(ChatMessageRole.USER, userText);
        ChatMessage assistant = new ChatMessage(ChatMessageRole.ASSISTANT, assistantText);
        messages.add(user);
        messages.add(assistant);
        pending.add(user);
        pending.add(assistant);
    }

    /** 本次待持久化的消息。 */
    public List<ChatMessage> pendingMessages() {
        return List.copyOf(pending);
    }
}
