package com.wlf.ai.rag.chat;

import com.wlf.ai.chat.persistence.ConversationStore;
import com.wlf.ai.infrastructure.llm.LlmClient;
import com.wlf.ai.infrastructure.llm.LlmMessage;
import com.wlf.ai.infrastructure.quota.AiQuota;
import com.wlf.ai.infrastructure.quota.FixedWindowRateLimiter;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * RAG 问答：知识库检索 + 多轮对话。
 *
 * <p>会话复用 {@code ai_message}，与普通对话共用一个额度。
 */
@Slf4j
@Service
public class RagChatService {

    /** 送模型的上下文窗口：只保留最近这么多条消息 */
    private static final int CONTEXT_MESSAGES = 40;

    private final LlmClient llmClient;
    private final FixedWindowRateLimiter rateLimiter;
    private final ConversationStore conversationStore;
    private final RetrievalAugmentationAdvisor ragAdvisor;

    public RagChatService(LlmClient llmClient,
                          FixedWindowRateLimiter rateLimiter,
                          ConversationStore conversationStore,
                          RetrievalAugmentationAdvisor ragAdvisor) {
        this.llmClient = llmClient;
        this.rateLimiter = rateLimiter;
        this.conversationStore = conversationStore;
        this.ragAdvisor = ragAdvisor;
    }

    /** 一轮问答。留空 conversationId 即开新会话；带了 id 却查不到一律 40400。 */
    public Reply chat(Long userId, String message, String conversationId) {
        boolean existing = conversationId != null && !conversationId.isBlank();
        String id = existing ? conversationId : UUID.randomUUID().toString();
        List<LlmMessage> history = existing ? requireHistory(userId, id) : List.of();

        rateLimiter.acquire(userId, AiQuota.CHAT);

        String reply = ask(history, message);
        conversationStore.appendTurn(userId, id, message, reply);
        return new Reply(reply, id);
    }

    private List<LlmMessage> requireHistory(Long userId, String conversationId) {
        List<LlmMessage> history = conversationStore.findMessages(userId, conversationId);
        if (history.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return history;
    }

    private String ask(List<LlmMessage> history, String message) {
        try {
            return llmClient.chatWithAdvisor(RagChatPrompt.SYSTEM, contextWindow(history), message,
                    ragAdvisor).strip();
        } catch (RuntimeException e) {
            log.error("调用 RAG 问答模型失败，异常类型={}", e.getClass().getName(), e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "问答服务暂不可用，请稍后重试");
        }
    }

    /** 超出窗口的旧消息只留在库里和界面上，不再参与模型计算 */
    private static List<LlmMessage> contextWindow(List<LlmMessage> history) {
        if (history.size() <= CONTEXT_MESSAGES) {
            return history;
        }
        return List.copyOf(history.subList(history.size() - CONTEXT_MESSAGES, history.size()));
    }

    /** 一轮问答的输出。 */
    public record Reply(String reply, String conversationId) {
    }
}
