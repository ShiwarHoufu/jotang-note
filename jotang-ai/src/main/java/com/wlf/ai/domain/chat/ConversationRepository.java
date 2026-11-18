package com.wlf.ai.domain.chat;

import java.util.Optional;

/** 对话聚合的持久化端口。 */
public interface ConversationRepository {

    Optional<Conversation> findLatest(Long userId);

    Optional<Conversation> find(Long userId, String conversationId);

    /** 只写入聚合里尚未持久化的消息。 */
    void append(Conversation conversation);
}
