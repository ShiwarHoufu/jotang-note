package com.wlf.ai.application.assistant.command;

/** 一轮问答的用例输入。 */
public record ChatCommand(Long userId, String message, String conversationId) {
}
