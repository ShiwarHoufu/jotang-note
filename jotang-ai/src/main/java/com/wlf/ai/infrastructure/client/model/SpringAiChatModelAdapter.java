package com.wlf.ai.infrastructure.client.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wlf.ai.application.port.out.ChatModelPort;
import com.wlf.ai.application.port.out.ChatTool;
import com.wlf.ai.application.port.out.ChatToolParameter;
import com.wlf.ai.domain.chat.ChatMessage;
import com.wlf.ai.domain.chat.ChatMessageRole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 把 Spring AI 的 {@link ChatClient} 适配成 {@link ChatModelPort}。
 */
@Component
public class SpringAiChatModelAdapter implements ChatModelPort {

    /** 只用来拼工具的 inputSchema，不参与请求体的编解码 */
    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;

    public SpringAiChatModelAdapter(ChatClient aiChatClient) {
        this.chatClient = aiChatClient;
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> history, String userMessage, List<ChatTool> tools) {
        List<Message> messages = new ArrayList<>(history.size() + 1);
        history.forEach(row -> messages.add(toMessage(row)));
        messages.add(new UserMessage(userMessage));

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages);
        if (!tools.isEmpty()) {
            spec = spec.tools(toCallbacks(tools).toArray(ToolCallback[]::new));
        }
        return textOf(spec.call().content());
    }

    @Override
    public String chatWithImage(String systemPrompt, String userMessage, String contentType, byte[] image) {
        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(spec -> spec.text(userMessage)
                        .media(MimeTypeUtils.parseMimeType(contentType), new ByteArrayResource(image)))
                .call()
                .content();
        return textOf(raw);
    }

    /** 模型返回 null 不算出错 */
    private static String textOf(String raw) {
        return raw == null ? "" : raw;
    }

    private static Message toMessage(ChatMessage message) {
        return message.role() == ChatMessageRole.ASSISTANT
                ? new AssistantMessage(message.content())
                : new UserMessage(message.content());
    }

    private List<ToolCallback> toCallbacks(List<ChatTool> tools) {
        return tools.stream().map(this::toCallback).toList();
    }

    private ToolCallback toCallback(ChatTool tool) {
        Function<Map<String, Object>, Object> handler = tool::invoke;
        return FunctionToolCallback.builder(tool.name(), handler)
                .description(tool.description())
                .inputType(Map.class)
                .inputSchema(inputSchema(tool.parameters()))
                .build();
    }

    private String inputSchema(List<ChatToolParameter> parameters) {
        ObjectNode schema = SCHEMA_MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        for (ChatToolParameter parameter : parameters) {
            properties.putObject(parameter.name())
                    .put("type", jsonType(parameter.type()))
                    .put("description", parameter.description());
            required.add(parameter.name());
        }
        schema.put("additionalProperties", false);
        return schema.toString();
    }

    private static String jsonType(ChatToolParameter.Type type) {
        return switch (type) {
            case STRING -> "string";
            case INTEGER -> "integer";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
        };
    }
}
