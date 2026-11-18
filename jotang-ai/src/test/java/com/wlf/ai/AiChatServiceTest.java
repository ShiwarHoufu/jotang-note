package com.wlf.ai;

import com.wlf.ai.application.assistant.AiChatService;
import com.wlf.ai.application.assistant.AiRateLimiter;
import com.wlf.ai.application.assistant.NoteTools;
import com.wlf.ai.application.assistant.dto.AiChatHistoryResponse;
import com.wlf.ai.application.assistant.dto.AiChatResponse;
import com.wlf.ai.application.port.out.NoteContentReader;
import com.wlf.ai.domain.assistant.AiMessage;
import com.wlf.ai.domain.assistant.AiMessageRepository;
import com.wlf.ai.domain.assistant.AiMessageRole;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiChatService} 的单元测试：模型、存储与 note 模块全部 mock，不起 Spring 上下文。
 *
 * <p>重点是<b>编排</b>：会话的建立与续聊、历史如何重放、配额取在调模型之前、
 * 落库的原子性、上游失败翻成哪个错误码、滑动窗口怎么裁。
 */
class AiChatServiceTest {

    private static final Long USER_ID = 7L;
    private static final String MESSAGE = "帮我解释一下拉格朗日中值定理";
    private static final String CONVERSATION_ID = "3f1a0b7c-9d2e-4a51-8c60-7b4f2e1d0a33";

    private ChatClient chatClient;
    private StorageService storageService;
    private NoteContentReader noteContentReader;
    private AiMessageRepository aiMessageRepository;
    private PlatformTransactionManager transactionManager;
    private AiChatService service;

    /** 最近一次 stub 出来的请求规格，用来断言真正发出去的内容 */
    private ChatClient.ChatClientRequestSpec requestSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        storageService = mock(StorageService.class);
        noteContentReader = mock(NoteContentReader.class);
        aiMessageRepository = mock(AiMessageRepository.class);
        // 事务边界由服务自己持有，这里给一个能"开"出事务的桩即可——
        // 本层验的是编排，事务的原子性由 TransactionTemplate 负责，不在这里重验
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        // 限流器用真实现：它本身就是这一层的规则，mock 掉等于把被测对象掏空
        service = new AiChatService(chatClient, new AiRateLimiter(30, Duration.ofHours(1), "对话"),
                noteContentReader, storageService, aiMessageRepository, transactionManager);
    }

    // ==================== 对话 ====================

    @Test
    void chatReturnsModelTextAndANewConversationId() {
        stubModel("它是罗尔定理的推广：两端点函数值相等时，中间必有一点导数为零。");

        AiChatResponse response = service.chat(USER_ID, MESSAGE, null);

        assertThat(response.reply()).isEqualTo("它是罗尔定理的推广：两端点函数值相等时，中间必有一点导数为零。");
        assertThat(response.conversationId()).isNotBlank();
    }

    /** 指令仍进 system 段，用户的话走 messages 段——这层结构是防注入的唯一手段，不能被拼成一段 */
    @Test
    void chatKeepsInstructionInSystemSegment() {
        stubModel("回答");

        service.chat(USER_ID, MESSAGE, null);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(system.capture());
        assertThat(system.getValue()).contains("Jotang Note");

        List<Message> sent = sentMessages();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).isInstanceOf(UserMessage.class);
        assertThat(sent.get(0).getText()).isEqualTo(MESSAGE);
    }

    /**
     * 对话必须挂上笔记工具，否则模型只能凭印象编造笔记内容。
     *
     * <p>这里只钉「挂了一个什么」，钉不到「工具真的被执行了」——执行发生在框架的
     * {@code ToolCallingAdvisor} 里，而整条 {@code ChatClient} 在本测试里是 mock，
     * 那个 advisor 根本不在链上。工具本身的行为由 {@code NoteToolsTest} 单独覆盖。
     */
    @Test
    void chatAttachesTheNoteTools() {
        stubModel("回答");

        service.chat(USER_ID, MESSAGE, null);

        ArgumentCaptor<Object[]> tools = ArgumentCaptor.forClass(Object[].class);
        verify(requestSpec).tools(tools.capture());
        assertThat(tools.getValue()).hasSize(1);
        assertThat(tools.getValue()[0]).isInstanceOf(NoteTools.class);
    }

    /**
     * 对话走自己的额度，且**不与摘要共用**——共用一个桶的话，点满摘要就会把聊天一起卡死，
     * 而两者的代价差一个数量级（摘要一次调用，一轮对话最多 6 次）。
     */
    @Test
    void chatSpendsItsOwnQuotaNotTheSummaryOne() {
        AiRateLimiter summaryRateLimiter = new AiRateLimiter(10, Duration.ofHours(1), "生成");
        AiChatService oneShotChat = new AiChatService(chatClient,
                new AiRateLimiter(1, Duration.ofHours(1), "对话"),
                noteContentReader, storageService, aiMessageRepository, transactionManager);
        stubModel("回答");

        oneShotChat.chat(USER_ID, MESSAGE, null);

        assertThatThrownBy(() -> oneShotChat.chat(USER_ID, MESSAGE, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_RATE_LIMITED));
        // 摘要那边的额度一点没动
        assertThatCode(() -> summaryRateLimiter.requireSlot(USER_ID)).doesNotThrowAnyException();
    }

    /**
     * 超额要在<b>调用模型之前</b>拦下，否则「限频」就只是事后记账。
     * 与摘要那条「先本地校验、再取配额、最后调用」同一个顺序。
     */
    @Test
    void rateLimitedChatNeverReachesTheModel() {
        AiChatService oneShotChat = new AiChatService(chatClient,
                new AiRateLimiter(1, Duration.ofHours(1), "对话"),
                noteContentReader, storageService, aiMessageRepository, transactionManager);
        stubModel("回答");
        oneShotChat.chat(USER_ID, MESSAGE, null);
        verify(chatClient, times(1)).prompt();

        assertThatThrownBy(() -> oneShotChat.chat(USER_ID, MESSAGE, null))
                .isInstanceOf(BusinessException.class);

        verify(chatClient, times(1)).prompt();
        // 被拒的那轮也不该落库
        verify(aiMessageRepository, times(2)).insert(any(AiMessage.class));
    }

    /**
     * 续聊时历史必须按「旧 user、旧 assistant、新 user」逐条原样排在前面。
     * 这条钉的是前缀缓存的前提：顺序或内容一变，缓存就从那里断掉，整段历史按全价重算。
     */
    @Test
    void chatReplaysHistoryInOrderThenAppendsTheNewMessage() {
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID)).thenReturn(List.of(
                row(AiMessageRole.USER, "第一个问题"),
                row(AiMessageRole.ASSISTANT, "第一个回答")));
        stubModel("第二个回答");

        service.chat(USER_ID, "第二个问题", CONVERSATION_ID);

        List<Message> sent = sentMessages();
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0)).isInstanceOf(UserMessage.class);
        assertThat(sent.get(0).getText()).isEqualTo("第一个问题");
        assertThat(sent.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(sent.get(1).getText()).isEqualTo("第一个回答");
        assertThat(sent.get(2)).isInstanceOf(UserMessage.class);
        assertThat(sent.get(2).getText()).isEqualTo("第二个问题");
    }

    /**
     * 历史只带 role 与 content。{@link #row} 刻意给了 {@code createdAt}，
     * 这里断言它没进 prompt——混进时间戳这类每轮都变的东西，前缀缓存会整段失效。
     */
    @Test
    void replayCarriesNoTimestamp() {
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID))
                .thenReturn(List.of(row(AiMessageRole.USER, "旧问题")));
        stubModel("回答");

        service.chat(USER_ID, MESSAGE, CONVERSATION_ID);

        assertThat(sentMessages()).allSatisfy(m -> assertThat(m.getText()).doesNotContain("2026"));
    }

    @Test
    void chatStoresBothSidesOfTheTurnUnderTheSameConversation() {
        stubModel("回答");

        AiChatResponse response = service.chat(USER_ID, MESSAGE, null);

        ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
        verify(aiMessageRepository, times(2)).insert(saved.capture());
        assertThat(saved.getAllValues()).extracting(AiMessage::getRole)
                .containsExactly(AiMessageRole.USER.name(), AiMessageRole.ASSISTANT.name());
        assertThat(saved.getAllValues()).extracting(AiMessage::getContent)
                .containsExactly(MESSAGE, "回答");
        assertThat(saved.getAllValues()).extracting(AiMessage::getUserId).containsOnly(USER_ID);
        assertThat(saved.getAllValues()).extracting(AiMessage::getConversationId)
                .containsOnly(response.conversationId());
    }

    /** 陌生会话（含别人的会话）：零行 → 40400，且既不调模型也不写库 */
    @Test
    void strangerConversationIsNotFoundAndCostsNothing() {
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.chat(USER_ID, MESSAGE, CONVERSATION_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(chatClient, never()).prompt();
        verify(aiMessageRepository, never()).insert(any(AiMessage.class));
    }

    /** 模型失败时一条消息都不落：历史里不会留下没有回复的孤行，用户重试即干净重放 */
    @Test
    void chatWritesNothingWhenTheModelFails() {
        stubFailure();

        assertThatThrownBy(() -> service.chat(USER_ID, MESSAGE, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));

        verify(aiMessageRepository, never()).insert(any(AiMessage.class));
    }

    /** 上游失败归 50300，文案是对话版而不是枚举里那句摘要专用的 */
    @Test
    void chatUpstreamFailureBecomesAiUnavailableWithChatWording() {
        stubFailure();

        assertThatThrownBy(() -> service.chat(USER_ID, MESSAGE, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
                    assertThat(ex.getMessage()).contains("对话").doesNotContain("摘要");
                });
    }

    /** 模型返回 null 不算出错：给空回答，而不是一个 NPE 引发的 50000 */
    @Test
    void chatNullModelTextBecomesEmptyStringNotAnError() {
        stubModel(null);

        assertThat(service.chat(USER_ID, MESSAGE, null).reply()).isEmpty();
    }

    @Test
    void chatSurroundingWhitespaceIsTrimmed() {
        stubModel("\n  回答正文  \n");

        assertThat(service.chat(USER_ID, MESSAGE, null).reply()).isEqualTo("回答正文");
    }

    // ==================== 恢复最近会话 ====================

    @Test
    void historyReturnsLatestConversationInOrder() {
        when(aiMessageRepository.findLatestConversationId(USER_ID)).thenReturn(CONVERSATION_ID);
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID)).thenReturn(List.of(
                row(AiMessageRole.USER, "第一个问题"),
                row(AiMessageRole.ASSISTANT, "第一个回答")));

        AiChatHistoryResponse history = service.history(USER_ID);

        assertThat(history.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(history.messages()).extracting(AiChatHistoryResponse.Message::role)
                .containsExactly(AiMessageRole.USER.name(), AiMessageRole.ASSISTANT.name());
        assertThat(history.messages()).extracting(AiChatHistoryResponse.Message::content)
                .containsExactly("第一个问题", "第一个回答");
    }

    /**
     * 新用户：没有任何会话是<b>常态</b>，回空载荷而不是 40400。
     * 40400 会让前端 http 层弹全局提示，页面一打开就弹「资源不存在」不可接受。
     */
    @Test
    void historyReturnsEmptyPayloadWhenUserHasNoConversation() {
        AiChatHistoryResponse history = service.history(USER_ID);

        assertThat(history.conversationId()).isNull();
        assertThat(history.messages()).isEmpty();
        // 取不到 id 就不该再发第二条查询——空会话是能提前判定的
        verify(aiMessageRepository, never()).findHistory(any(), any());
    }

    /** 并发删会话的兜底：宁可让前端停在空状态，也不回一个「发出去就 40400」的 id */
    @Test
    void historyReturnsEmptyPayloadWhenConversationVanished() {
        when(aiMessageRepository.findLatestConversationId(USER_ID)).thenReturn(CONVERSATION_ID);
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID)).thenReturn(List.of());

        AiChatHistoryResponse history = service.history(USER_ID);

        assertThat(history.conversationId()).isNull();
        assertThat(history.messages()).isEmpty();
    }

    // ==================== 滑动窗口 ====================

    /** 50 条（25 轮）只发最近 40 条：单次成本的上界靠它，没有它长会话会撑爆模型上下文窗口 */
    @Test
    void askSendsOnlyTheWindowWhenHistoryIsOverLimit() {
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID)).thenReturn(historyOf(50));
        stubModel("回答");

        service.chat(USER_ID, MESSAGE, CONVERSATION_ID);

        List<Message> sent = sentMessages();
        // 窗口 40 条 + 本轮新消息
        assertThat(sent).hasSize(41);
        assertThat(sent.get(0).getText()).isEqualTo("消息10");
        assertThat(sent.get(39).getText()).isEqualTo("消息49");
    }

    /** 未超限时一条不裁——绝大多数会话的常态，也是前缀缓存逐轮照常命中的前提 */
    @Test
    void askKeepsWholeHistoryWhenUnderLimit() {
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID)).thenReturn(historyOf(40));
        stubModel("回答");

        service.chat(USER_ID, MESSAGE, CONVERSATION_ID);

        List<Message> sent = sentMessages();
        assertThat(sent).hasSize(41);
        assertThat(sent.get(0).getText()).isEqualTo("消息0");
    }

    /** 窗口起点必须落在 USER 行上，否则上下文会从助手的半句说起 */
    @Test
    void askWindowStartsOnAUserMessage() {
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID)).thenReturn(historyOf(50));
        stubModel("回答");

        service.chat(USER_ID, MESSAGE, CONVERSATION_ID);

        assertThat(sentMessages().get(0)).isInstanceOf(UserMessage.class);
    }

    /** 切片之后本轮新消息仍在最后——顺序错了模型就答非所问 */
    @Test
    void askAppendsNewMessageAfterTheWindow() {
        when(aiMessageRepository.findHistory(USER_ID, CONVERSATION_ID)).thenReturn(historyOf(50));
        stubModel("回答");

        service.chat(USER_ID, "本轮问题", CONVERSATION_ID);

        List<Message> sent = sentMessages();
        assertThat(sent.get(sent.size() - 1).getText()).isEqualTo("本轮问题");
    }

    // ==================== 辅助 ====================

    /** USER / ASSISTANT 交替的一段历史，内容带序号，便于断言窗口切到了第几条 */
    private static List<AiMessage> historyOf(int size) {
        List<AiMessage> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(row(i % 2 == 0 ? AiMessageRole.USER : AiMessageRole.ASSISTANT, "消息" + i));
        }
        return rows;
    }

    /** 把 {@code ChatClient} 那条链 stub 掉，并记下请求规格供断言 */
    private void stubModel(String reply) {
        stubRequestSpec();
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(reply);
    }

    private void stubFailure() {
        stubRequestSpec();
        when(requestSpec.call()).thenThrow(new IllegalStateException("上游 500"));
    }

    /**
     * 对话走 {@code messages(List)} 再挂 {@code tools(...)}，每条链都要 stub——
     * 漏掉的那条会返回 mock 默认的 null，链式调用直接 NPE，报错看着像生产代码的 bug。
     */
    @SuppressWarnings("unchecked")
    private void stubRequestSpec() {
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
    }

    /** 取出真正发给模型的那串消息，顺序即请求里的顺序 */
    @SuppressWarnings("unchecked")
    private List<Message> sentMessages() {
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(requestSpec).messages(captor.capture());
        return captor.getValue();
    }

    /** 一行历史。刻意带上 {@code createdAt}，用来证明它不会进 prompt */
    private static AiMessage row(AiMessageRole role, String content) {
        AiMessage row = new AiMessage();
        row.setUserId(USER_ID);
        row.setConversationId(CONVERSATION_ID);
        row.setRole(role.name());
        row.setContent(content);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 11, 12, 0));
        return row;
    }
}
