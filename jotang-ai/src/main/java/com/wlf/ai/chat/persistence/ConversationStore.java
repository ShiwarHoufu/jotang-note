package com.wlf.ai.chat.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.ai.infrastructure.llm.LlmMessage;
import com.wlf.ai.infrastructure.llm.LlmRole;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 对话消息的读写。 */
@Repository
public class ConversationStore {

    private final AiMessageMapper aiMessageMapper;

    public ConversationStore(AiMessageMapper aiMessageMapper) {
        this.aiMessageMapper = aiMessageMapper;
    }

    public Optional<String> findLatestConversationId(Long userId) {
        return Optional.ofNullable(aiMessageMapper.selectLatestConversationId(userId));
    }

    /** 该会话的全部历史，按发生顺序正序；会话不存在时返回空列表。 */
    public List<LlmMessage> findMessages(Long userId, String conversationId) {
        return aiMessageMapper.selectList(Wrappers.<AiMessagePO>lambdaQuery()
                        .eq(AiMessagePO::getUserId, userId)
                        .eq(AiMessagePO::getConversationId, conversationId)
                        .orderByAsc(AiMessagePO::getId))
                .stream()
                .map(ConversationStore::toMessage)
                .toList();
    }

    /** 一轮问答落两条消息，避免模型失败时历史里只留半轮。 */
    @Transactional
    public void appendTurn(Long userId, String conversationId, String userMessage, String assistantMessage) {
        aiMessageMapper.insert(toPO(userId, conversationId, LlmRole.USER, userMessage));
        aiMessageMapper.insert(toPO(userId, conversationId, LlmRole.ASSISTANT, assistantMessage));
    }

    private static LlmMessage toMessage(AiMessagePO po) {
        LlmRole role = LlmRole.ASSISTANT.name().equals(po.getRole())
                ? LlmRole.ASSISTANT
                : LlmRole.USER;
        return new LlmMessage(role, po.getContent());
    }

    private static AiMessagePO toPO(Long userId, String conversationId, LlmRole role, String content) {
        AiMessagePO po = new AiMessagePO();
        po.setUserId(userId);
        po.setConversationId(conversationId);
        po.setRole(role.name());
        po.setContent(content);
        return po;
    }
}
