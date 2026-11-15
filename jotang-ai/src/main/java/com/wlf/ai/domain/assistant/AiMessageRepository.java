package com.wlf.ai.domain.assistant;

import java.util.List;

/**
 * AI 对话消息的持久化端口。
 */
public interface AiMessageRepository {

    /** 该用户最近一次会话的 id；没有消息时为 null。 */
    String findLatestConversationId(Long userId);

    /** 按归属与会话 id 取历史，按 id 升序。 */
    List<AiMessage> findHistory(Long userId, String conversationId);

    void insert(AiMessage message);
}
