package com.wlf.ai.application.assistant;

import com.wlf.ai.application.assistant.command.ChatCommand;
import com.wlf.ai.application.assistant.prompt.ChatPrompt;
import com.wlf.ai.application.assistant.result.ChatHistoryResult;
import com.wlf.ai.application.assistant.result.ChatResult;
import com.wlf.ai.application.port.out.ChatMessage;
import com.wlf.ai.application.port.out.ChatModelPort;
import com.wlf.ai.application.port.out.NoteContentReader;
import com.wlf.ai.domain.assistant.AiMessage;
import com.wlf.ai.domain.assistant.AiMessageRepository;
import com.wlf.ai.domain.assistant.AiMessageRole;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 对话的应用服务：一轮问答与恢复最近一次会话。
 *
 * <p>对话会把消息写进 {@code ai_message}；模型可经 {@link NoteTools} 检索笔记。
 */
@Slf4j
@Service
public class AiChatService {

    /** 送模型的上下文上限 */
    private static final int MAX_CONTEXT_MESSAGES = 40;

    private final ChatModelPort chatModelPort;
    /** 摘要与对话各持一个实例、各算各的额度：两者代价差一个数量级，共用一个桶会互相挤占 */
    private final AiRateLimiter chatRateLimiter;
    private final NoteContentReader noteContentReader;
    private final StorageService storageService;
    private final AiMessageRepository aiMessageRepository;
    private final TransactionTemplate transactionTemplate;

    public AiChatService(ChatModelPort chatModelPort,
                         @Qualifier("aiChatRateLimiter") AiRateLimiter chatRateLimiter,
                         NoteContentReader noteContentReader,
                         StorageService storageService,
                         AiMessageRepository aiMessageRepository,
                         PlatformTransactionManager transactionManager) {
        this.chatModelPort = chatModelPort;
        this.chatRateLimiter = chatRateLimiter;
        this.noteContentReader = noteContentReader;
        this.storageService = storageService;
        this.aiMessageRepository = aiMessageRepository;
        // 与 NoteContentReader 同：自己 new，显式持有事务边界，读代码时不必去猜注解在哪一层生效
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 一轮问答。
     */
    public ChatResult chat(ChatCommand command) {
        Long userId = command.userId();
        String message = command.message();
        String conversationId = command.conversationId();
        // 查询conversationId，留空即开新会话
        boolean isNew = conversationId == null || conversationId.isBlank();
        String conversation = isNew ? UUID.randomUUID().toString() : conversationId;

        List<AiMessage> history = loadHistory(userId, conversation);
        // 带了conversationId 但查不到 history
        if (!isNew && history.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 取配额
        chatRateLimiter.requireSlot(userId);

        // 组装请求，发起call
        String reply = ask(userId, history, message);
        // 消息落库
        appendTurn(userId, conversation, message, reply);

        return new ChatResult(reply, conversation);
    }

    /**
     * 该用户最近一次会话及其历史。
     * 刷新页面后前端靠它接回上一段对话。
     *
     * <p>不限频。
     */
    public ChatHistoryResult history(Long userId) {
        String conversationId = aiMessageRepository.findLatestConversationId(userId);
        if (conversationId == null) {
            return ChatHistoryResult.empty();
        }
        List<AiMessage> rows = loadHistory(userId, conversationId);
        // 并发删会话的兜底：宁可让前端停在空状态，也不回一个「发出去就 40400」的 id
        if (rows.isEmpty()) {
            return ChatHistoryResult.empty();
        }
        return new ChatHistoryResult(conversationId, rows.stream()
                .map(row -> new ChatHistoryResult.Message(row.getRole(), row.getContent()))
                .toList());
    }

    /** 归属校验与取历史是同一次查询：条件里带 {@code user_id}，别人的会话自然查不出来 */
    private List<AiMessage> loadHistory(Long userId, String conversationId) {
        return aiMessageRepository.findHistory(userId, conversationId);
    }

    /**
     * 发起 call
     */
    private String ask(Long userId, List<AiMessage> history, String message) {

        // 组装历史会话记录，超出窗口的旧消息只留在库里和界面上，不再参与模型计算
        List<AiMessage> window = history.size() > MAX_CONTEXT_MESSAGES
                ? history.subList(history.size() - MAX_CONTEXT_MESSAGES, history.size())
                : history;
        List<ChatMessage> promptHistory = new ArrayList<>(window.size());
        window.forEach(row -> promptHistory.add(toPromptMessage(row)));

        try {
            String raw = chatModelPort.chat(ChatPrompt.SYSTEM, promptHistory, message,
                    new NoteTools(userId, noteContentReader, storageService, chatModelPort).tools());
            // 模型返回 null 不算出错，给空串而不是让 NPE 变成 50000
            return raw == null ? "" : raw.strip();
        } catch (RuntimeException e) {
            // 这里也会吞掉工具链路的异常（例如模型报回一个不存在的工具名，框架抛
            // IllegalStateException 且无人接），连类名一起记，否则日志里只剩一句笼统的失败
            log.error("调用对话模型失败，异常类型={}", e.getClass().getName(), e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "对话服务暂不可用，请稍后重试");
        }
    }

    private static ChatMessage toPromptMessage(AiMessage row) {
        AiMessageRole role = AiMessageRole.ASSISTANT.name().equals(row.getRole())
                ? AiMessageRole.ASSISTANT
                : AiMessageRole.USER;
        return new ChatMessage(role, row.getContent());
    }

    /**
     * 落库一轮对话消息。
     */
    private void appendTurn(Long userId, String conversationId, String message, String reply) {
        transactionTemplate.executeWithoutResult(status -> {
            aiMessageRepository.insert(rowOf(userId, conversationId, AiMessageRole.USER, message));
            aiMessageRepository.insert(rowOf(userId, conversationId, AiMessageRole.ASSISTANT, reply));
        });
    }

    private static AiMessage rowOf(Long userId, String conversationId, AiMessageRole role, String content) {
        AiMessage row = new AiMessage();
        row.setUserId(userId);
        row.setConversationId(conversationId);
        row.setRole(role.name());
        row.setContent(content);
        return row;
    }
}
