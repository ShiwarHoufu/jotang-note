package com.wlf.ai.application.assistant;

import com.wlf.ai.application.assistant.command.ChatCommand;
import com.wlf.ai.application.assistant.prompt.ChatPrompt;
import com.wlf.ai.application.assistant.result.ChatHistoryResult;
import com.wlf.ai.application.assistant.result.ChatResult;
import com.wlf.ai.application.port.out.ChatModelPort;
import com.wlf.ai.application.port.out.NoteContentReader;
import com.wlf.ai.domain.chat.ChatMessage;
import com.wlf.ai.domain.chat.ContextWindow;
import com.wlf.ai.domain.chat.Conversation;
import com.wlf.ai.domain.chat.ConversationRepository;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * AI 对话的应用服务：一轮问答与恢复最近一次会话。
 *
 * <p>对话会把消息写进 {@code ai_message}；模型可经 {@link NoteTools} 检索笔记。
 */
@Slf4j
@Service
public class AiChatService {

    private final ChatModelPort chatModelPort;
    /** 摘要与对话各持一个实例、各算各的额度：两者代价差一个数量级，共用一个桶会互相挤占 */
    private final AiRateLimiter chatRateLimiter;
    private final NoteContentReader noteContentReader;
    private final StorageService storageService;
    private final ConversationRepository conversationRepository;
    private final TransactionTemplate transactionTemplate;

    public AiChatService(ChatModelPort chatModelPort,
                         @Qualifier("aiChatRateLimiter") AiRateLimiter chatRateLimiter,
                         NoteContentReader noteContentReader,
                         StorageService storageService,
                         ConversationRepository conversationRepository,
                         PlatformTransactionManager transactionManager) {
        this.chatModelPort = chatModelPort;
        this.chatRateLimiter = chatRateLimiter;
        this.noteContentReader = noteContentReader;
        this.storageService = storageService;
        this.conversationRepository = conversationRepository;
        // 与 NoteContentReader 同：自己 new，显式持有事务边界，读代码时不必去猜注解在哪一层生效
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 一轮问答。
     */
    public ChatResult chat(ChatCommand command) {
        Conversation conversation = loadOrStart(command);

        // 取配额
        chatRateLimiter.requireSlot(command.userId());

        String reply = ask(conversation, command.message());

        conversation.appendTurn(command.message(), reply);
        transactionTemplate.executeWithoutResult(status -> conversationRepository.append(conversation));

        return new ChatResult(reply, conversation.id());
    }

    /**
     * 该用户最近一次会话及其历史。
     * 刷新页面后前端靠它接回上一段对话。
     *
     * <p>不限频。
     */
    public ChatHistoryResult history(Long userId) {
        return conversationRepository.findLatest(userId)
                .map(conversation -> new ChatHistoryResult(conversation.id(),
                        conversation.messages().stream()
                                .map(message -> new ChatHistoryResult.Message(
                                        message.role().name(), message.content()))
                                .toList()))
                .orElseGet(ChatHistoryResult::empty);
    }

    /** 留空即开新会话；带了 id 却查不到（含别人的会话）一律 40400。 */
    private Conversation loadOrStart(ChatCommand command) {
        if (command.conversationId() == null || command.conversationId().isBlank()) {
            return Conversation.start(command.userId());
        }
        return conversationRepository.find(command.userId(), command.conversationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /**
     * 发起 call
     */
    private String ask(Conversation conversation, String message) {
        // 超出窗口的旧消息只留在库里和界面上，不再参与模型计算
        List<ChatMessage> window = conversation.contextWindow(ContextWindow.DEFAULT);

        try {
            String raw = chatModelPort.chat(ChatPrompt.SYSTEM, window, message,
                    new NoteTools(conversation.userId(), noteContentReader, storageService, chatModelPort).tools());
            return raw == null ? "" : raw.strip();
        } catch (RuntimeException e) {
            log.error("调用对话模型失败，异常类型={}", e.getClass().getName(), e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, "对话服务暂不可用，请稍后重试");
        }
    }
}
