package com.wlf.ai.chat;

import com.wlf.ai.chat.persistence.ConversationStore;
import com.wlf.ai.chat.tool.KbSearchTool;
import com.wlf.ai.chat.tool.NoteReadTool;
import com.wlf.ai.chat.tool.NoteSearchTool;
import com.wlf.ai.infrastructure.llm.LlmClient;
import com.wlf.ai.infrastructure.llm.LlmMessage;
import com.wlf.ai.infrastructure.note.NoteGateway;
import com.wlf.ai.infrastructure.quota.AiQuota;
import com.wlf.ai.infrastructure.quota.FixedWindowRateLimiter;
import com.wlf.ai.infrastructure.vectorstore.KbVectorStore;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * AI 对话：一轮问答与恢复最近一次会话。
 *
 * <p>对话会把消息写进 {@code ai_message}；模型可经工具检索笔记与知识库。
 */
@Slf4j
@Service
public class ChatService {

    /** 送模型的上下文窗口：只保留最近这么多条消息 */
    private static final int CONTEXT_MESSAGES = 40;

    private final LlmClient llmClient;
    /** 对话与摘要各算各的额度：两者代价差一个数量级，共用一个桶会互相挤占 */
    private final FixedWindowRateLimiter rateLimiter;
    private final NoteGateway noteGateway;
    private final ConversationStore conversationStore;
    private final KbVectorStore kbVectorStore;

    public ChatService(LlmClient llmClient,
                       FixedWindowRateLimiter rateLimiter,
                       NoteGateway noteGateway,
                       ConversationStore conversationStore,
                       KbVectorStore kbVectorStore) {
        this.llmClient = llmClient;
        this.rateLimiter = rateLimiter;
        this.noteGateway = noteGateway;
        this.conversationStore = conversationStore;
        this.kbVectorStore = kbVectorStore;
    }

    /** 一轮对话。留空 conversationId 即开新会话；带了 id 却查不到一律 40400。 */
    public Reply chat(Long userId, String message, String conversationId) {
        boolean existing = conversationId != null && !conversationId.isBlank();
        String id = existing ? conversationId : UUID.randomUUID().toString();
        List<LlmMessage> history = existing ? requireHistory(userId, id) : List.of();

        rateLimiter.acquire(userId, AiQuota.CHAT);

        String reply = ask(userId, history, message);
        conversationStore.appendTurn(userId, id, message, reply);
        return new Reply(reply, id);
    }

    /** 该用户最近一次会话及其历史；刷新页面后前端靠它接回上一段对话。 */
    public History history(Long userId) {
        return conversationStore.findLatestConversationId(userId)
                .map(id -> new History(id, conversationStore.findMessages(userId, id).stream()
                        .map(row -> new History.Message(row.role().name(), row.content()))
                        .toList()))
                .orElseGet(History::empty);
    }



    private List<LlmMessage> requireHistory(Long userId, String conversationId) {
        List<LlmMessage> history = conversationStore.findMessages(userId, conversationId);
        if (history.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return history;
    }

    private String ask(Long userId, List<LlmMessage> history, String message) {
        try {
            return llmClient.chat(ChatPrompt.SYSTEM, contextWindow(history), message,
                    new KbSearchTool(userId, kbVectorStore),
                    new NoteSearchTool(userId, noteGateway),
                    new NoteReadTool(userId, noteGateway, llmClient)
            ).strip();
        } catch (RuntimeException e) {
            log.error("调用对话模型失败，异常类型={}", e.getClass().getName(), e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "对话服务暂不可用，请稍后重试");
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

    /** 最近一次会话及其历史。 */
    public record History(String conversationId, List<Message> messages) {

        public record Message(String role, String content) {
        }

        static History empty() {
            return new History(null, List.of());
        }
    }
}
