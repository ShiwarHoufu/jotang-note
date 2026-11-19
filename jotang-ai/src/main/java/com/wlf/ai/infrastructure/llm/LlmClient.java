package com.wlf.ai.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型调用的唯一入口：屏蔽 Spring AI 的协议细节，统一空响应。
 *
 * <p>供应商切换、重试与降级都收在这里，业务侧只关心「问一句话、拿一段文本」。
 */
@Component
public class LlmClient {

    private final ChatClient chatClient;

    public LlmClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 多轮对话，可选挂载工具。 */
    public String chat(String systemPrompt, List<LlmMessage> history, String userMessage, Object... tools) {
        List<Message> messages = new ArrayList<>(history.size() + 1);
        history.forEach(row -> messages.add(toMessage(row)));
        messages.add(new UserMessage(userMessage));

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages);
        if (tools.length > 0) {
            spec = spec.tools(tools);
        }
        return text(spec.call().content());
    }

    /** 单轮文本。 */
    public String chat(String systemPrompt, String userMessage) {
        return text(chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content());
    }

    /** 单轮图文。 */
    public String chatWithImage(String systemPrompt, String userMessage, String contentType, byte[] image) {
        return text(chatClient.prompt()
                .system(systemPrompt)
                .user(spec -> spec.text(userMessage)
                        .media(MimeTypeUtils.parseMimeType(contentType), new ByteArrayResource(image)))
                .call()
                .content());
    }

    private static Message toMessage(LlmMessage message) {
        return message.role() == LlmRole.ASSISTANT
                ? new AssistantMessage(message.content())
                : new UserMessage(message.content());
    }

    private static String text(String raw) {
        return raw == null ? "" : raw;
    }
}
