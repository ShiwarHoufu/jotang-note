package com.wlf.ai.infrastructure.persistence;

import com.wlf.ai.domain.chat.ChatMessage;
import com.wlf.ai.domain.chat.ChatMessageRole;
import com.wlf.ai.domain.chat.Conversation;
import org.springframework.stereotype.Component;

/** ai_message 行与对话领域对象之间的转换。 */
@Component
public class AiMessageConverter {

    public ChatMessage toMessage(AiMessagePO po) {
        ChatMessageRole role = ChatMessageRole.ASSISTANT.name().equals(po.getRole())
                ? ChatMessageRole.ASSISTANT
                : ChatMessageRole.USER;
        return new ChatMessage(role, po.getContent());
    }

    public AiMessagePO toPO(Conversation conversation, ChatMessage message) {
        AiMessagePO po = new AiMessagePO();
        po.setUserId(conversation.userId());
        po.setConversationId(conversation.id());
        po.setRole(message.role().name());
        po.setContent(message.content());
        return po;
    }
}
