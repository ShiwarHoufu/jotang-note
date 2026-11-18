package com.wlf.ai.interfaces.web;

import com.wlf.ai.application.assistant.command.ChatCommand;
import com.wlf.ai.application.assistant.result.ChatHistoryResult;
import com.wlf.ai.application.assistant.result.ChatResult;
import com.wlf.ai.interfaces.web.dto.AiChatHistoryResponse;
import com.wlf.ai.interfaces.web.dto.AiChatRequest;
import com.wlf.ai.interfaces.web.dto.AiChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** 对话 HTTP 契约与用例输入/输出之间的转换。 */
@Component
public class ChatAssembler {

    public ChatCommand toCommand(Long userId, AiChatRequest request) {
        return new ChatCommand(userId, request.getMessage(), request.getConversationId());
    }

    public AiChatResponse toResponse(ChatResult result) {
        return new AiChatResponse(result.reply(), result.conversationId());
    }

    public AiChatHistoryResponse toResponse(ChatHistoryResult result) {
        List<AiChatHistoryResponse.Message> messages = result.messages().stream()
                .map(message -> new AiChatHistoryResponse.Message(message.role(), message.content()))
                .toList();
        return new AiChatHistoryResponse(result.conversationId(), messages);
    }
}
