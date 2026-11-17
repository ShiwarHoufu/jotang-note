package com.wlf.ai.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.ai.domain.assistant.AiMessage;
import com.wlf.ai.domain.assistant.AiMessageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 消息仓储的 MyBatis-Plus 实现。
 */
@Repository
public class AiMessageRepositoryImpl implements AiMessageRepository {

    private final AiMessageMapper aiMessageMapper;

    public AiMessageRepositoryImpl(AiMessageMapper aiMessageMapper) {
        this.aiMessageMapper = aiMessageMapper;
    }

    @Override
    public String findLatestConversationId(Long userId) {
        return aiMessageMapper.selectLatestConversationId(userId);
    }

    @Override
    public List<AiMessage> findHistory(Long userId, String conversationId) {
        return aiMessageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getUserId, userId)
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId));
    }

    @Override
    public void insert(AiMessage message) {
        aiMessageMapper.insert(message);
    }
}
