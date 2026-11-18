package com.wlf.ai.application.port.out;

import com.wlf.ai.domain.chat.ChatMessage;

import java.util.List;

/**
 * 对话模型的出站端口。上游失败以 {@link RuntimeException} 抛出，由调用方翻译成业务错误。
 */
public interface ChatModelPort {

    /** 多轮对话；{@code tools} 为空表示不挂工具。 */
    String chat(String systemPrompt, List<ChatMessage> history, String userMessage, List<ChatTool> tools);

    /** 单轮文本，无历史、无工具。 */
    default String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, List.of(), userMessage, List.of());
    }

    /** 单轮图文：{@code userMessage} 与图片一起进 user 段。 */
    String chatWithImage(String systemPrompt, String userMessage, String contentType, byte[] image);
}
