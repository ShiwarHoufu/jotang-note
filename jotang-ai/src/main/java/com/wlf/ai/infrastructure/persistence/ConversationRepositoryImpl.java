package com.wlf.ai.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.ai.domain.chat.ChatMessage;
import com.wlf.ai.domain.chat.Conversation;
import com.wlf.ai.domain.chat.ConversationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 对话聚合的 MyBatis-Plus 实现。 */
@Repository
public class ConversationRepositoryImpl implements ConversationRepository {

    private final AiMessageMapper aiMessageMapper;
    private final AiMessageConverter converter;

    public ConversationRepositoryImpl(AiMessageMapper aiMessageMapper, AiMessageConverter converter) {
        this.aiMessageMapper = aiMessageMapper;
        this.converter = converter;
    }

    @Override
    public Optional<Conversation> findLatest(Long userId) {
        String conversationId = aiMessageMapper.selectLatestConversationId(userId);
        if (conversationId == null) {
            return Optional.empty();
        }
        return find(userId, conversationId);
    }

    @Override
    public Optional<Conversation> find(Long userId, String conversationId) {
        List<AiMessagePO> rows = aiMessageMapper.selectList(Wrappers.<AiMessagePO>lambdaQuery()
                .eq(AiMessagePO::getUserId, userId)
                .eq(AiMessagePO::getConversationId, conversationId)
                .orderByAsc(AiMessagePO::getId));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Conversation.restore(userId, conversationId,
                rows.stream().map(converter::toMessage).toList()));
    }

    @Override
    public void append(Conversation conversation) {
        for (ChatMessage message : conversation.pendingMessages()) {
            aiMessageMapper.insert(converter.toPO(conversation, message));
        }
    }
}
