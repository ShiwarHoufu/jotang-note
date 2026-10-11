package com.wlf.ai;

import com.wlf.ai.dto.AiChatResponse;
import com.wlf.ai.dto.AiSummaryRequest;
import com.wlf.ai.dto.AiSummaryResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.AiMessage;
import com.wlf.note.NoteFileOwnershipRow;
import com.wlf.note.NoteService;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.MimeTypeUtils;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiService} 的单元测试：模型、存储与 note 模块全部 mock，不起 Spring 上下文。
 *
 * <p>重点是<b>编排</b>而不是模型本身：什么情况下不该去调模型（省一次计费）、
 * 以什么 MIME 交给模型、上游失败翻成哪个错误码、回程截断、system / user 分段。
 * 判定规则的细节在 {@link AiSummaryImagePolicyTest}，这里不重复。
 */
class AiServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    /** note 收、但模型读不了的类型（§6.8）：拿它验「本地就拦下，不白花一次计费」 */
    private static final byte[] MD = "# 第一章 绪论".getBytes();
    private static final Long USER_ID = 7L;
    private static final Long NOTE_ID = 42L;
    private static final String MESSAGE = "帮我解释一下拉格朗日中值定理";
    private static final String CONVERSATION_ID = "3f1a0b7c-9d2e-4a51-8c60-7b4f2e1d0a33";

    private ChatClient chatClient;
    private StorageService storageService;
    private NoteService noteService;
    private AiMessageMapper aiMessageMapper;
    private PlatformTransactionManager transactionManager;
    /** 只有摘要这个留成字段：对话那条的额度用例要的是另一个上限，不能用这个实例 */
    private AiRateLimiter summaryRateLimiter;
    private AiService service;

    /** 最近一次 stub 出来的请求规格，用来断言真正发出去的内容 */
    private ChatClient.ChatClientRequestSpec requestSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        storageService = mock(StorageService.class);
        noteService = mock(NoteService.class);
        aiMessageMapper = mock(AiMessageMapper.class);
        // 事务边界由服务自己持有，这里给一个能"开"出事务的桩即可——
        // 本层验的是编排，事务的原子性由 TransactionTemplate 负责，不在这里重验
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        // 策略与限流器用真实现：它们本身就是这一层的规则，mock 掉等于把被测对象掏空。
        // 额度与 AiConfig 里那两个 Bean 对齐，但仍要自己 new 两个——这正是「摘要与对话
        // 各持一个实例」的用法，共用一个实例的写法在这里会立刻暴露出额度被互相挤占
        summaryRateLimiter = new AiRateLimiter(10, Duration.ofHours(1), "生成");
        AiRateLimiter chatRateLimiter = new AiRateLimiter(30, Duration.ofHours(1), "对话");
        service = new AiService(chatClient, new AiSummaryImagePolicy(), summaryRateLimiter, chatRateLimiter,
                noteService, storageService, aiMessageMapper, transactionManager);
    }

    // ==================== 摘要：上传场景 ====================

    @Test
    void uploadReturnsModelText() {
        stubModel("这是一份高数期中复习提纲，覆盖极限与导数。");

        AiSummaryResponse response = service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png"));

        assertThat(response.summary()).isEqualTo("这是一份高数期中复习提纲，覆盖极限与导数。");
    }

    /**
     * 图片必须以<b>服务端判定的 MIME</b> 交给模型（而不是 multipart 自带的 Content-Type，
     * 那个由客户端决定）。这条同时验证了「图片真的进了请求体」——本服务最核心的一步。
     */
    @Test
    void uploadSendsImageWithServerJudgedMime() {
        stubModel("摘要");

        service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png"));

        ArgumentCaptor<Consumer<ChatClient.PromptUserSpec>> captor = userSpecCaptor();
        ChatClient.PromptUserSpec userSpec = stubUserSpec();
        captor.getValue().accept(userSpec);

        verify(userSpec).media(eq(MimeTypeUtils.parseMimeType("image/png")), any(Resource.class));
    }

    /** 类型不合法是本地就能判的，不该白花一次计费去撞上游 */
    @Test
    void uploadRejectsNonImageWithoutCallingModel() {
        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(MD, "笔记.md")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(chatClient, never()).prompt();
    }

    @Test
    void uploadRejectsOversizedImageWithoutCallingModel() {
        byte[] tooBig = Arrays.copyOf(PNG, (int) AiSummaryImagePolicy.MAX_SIZE_BYTES + 1);

        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(tooBig, "巨图.png")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(chatClient, never()).prompt();
    }

    // ==================== 摘要：回程截断 ====================

    /**
     * 摘要是建议，但不能超过 {@code note.summary} 的列宽——否则用户点保存就撞上
     * {@code @Size(max = 512)}，收到一个「简介不超过 512 个字符」的 40001，
     * 而那段字根本不是他自己打的（§6.8）。
     */
    @Test
    void overlongSummaryIsTruncatedToTheColumnWidth() {
        stubModel("字".repeat(600));

        AiSummaryResponse response = service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png"));

        assertThat(response.summary()).hasSize(512);
    }

    /** 按 code point 截：emoji 是代理对，按 UTF-16 单元截会留下孤立的半个字符 */
    @Test
    void truncationCountsCodePointsNotUtf16Units() {
        stubModel("📚".repeat(600));

        AiSummaryResponse response = service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png"));

        assertThat(response.summary().codePointCount(0, response.summary().length())).isEqualTo(512);
    }

    // ==================== 摘要：上游失败 ====================

    /**
     * 上游出问题必须是 <b>50300</b> 而不是 50000：「上游不可用，重试可能有救」与
     * 「我们的代码错了」对前端是两种处置（§5.7、§6.8）。
     */
    @Test
    void upstreamFailureBecomesAiUnavailableNotServerError() {
        when(chatClient.prompt()).thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    // ==================== 摘要：编辑场景 ====================

    @Test
    void editScenarioReadsObjectFromNoteBucketAndSendsStoredMime() {
        when(noteService.requireOwnFile(NOTE_ID, USER_ID)).thenReturn(fileOf("image/jpeg", 1024L));
        when(storageService.open(Bucket.NOTE, "notes/2026/10/x.jpg"))
                .thenReturn(new ByteArrayInputStream(PNG));
        stubModel("摘要");

        AiSummaryResponse response = service.summarizeExistingNote(USER_ID, NOTE_ID, "标题", "张老师");

        assertThat(response.summary()).isEqualTo("摘要");
        ArgumentCaptor<Consumer<ChatClient.PromptUserSpec>> captor = userSpecCaptor();
        ChatClient.PromptUserSpec userSpec = stubUserSpec();
        captor.getValue().accept(userSpec);
        verify(userSpec).media(eq(MimeTypeUtils.parseMimeType("image/jpeg")), any(Resource.class));
    }

    /** 归属/状态的判门归 note 模块，本层只负责不吞掉它——40300 必须原样传上去 */
    @Test
    void editScenarioPropagatesOwnershipFailure() {
        when(noteService.requireOwnFile(NOTE_ID, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.summarizeExistingNote(USER_ID, NOTE_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(storageService, never()).open(any(), anyString());
    }

    /** 附件不是图片：不去读 OSS，更不去调模型 */
    @Test
    void editScenarioRejectsNonImageAttachmentWithoutTouchingOss() {
        when(noteService.requireOwnFile(NOTE_ID, USER_ID)).thenReturn(fileOf("text/markdown", 1024L));

        assertThatThrownBy(() -> service.summarizeExistingNote(USER_ID, NOTE_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(storageService, never()).open(any(), anyString());
        verify(chatClient, never()).prompt();
    }

    /**
     * 编辑场景没有 multipart 的 size 可判，只能先用库里那一列拦一道。
     * <b>不先拦的话会把一个上百 MB 的对象整个读进堆里</b>再拒掉。
     */
    @Test
    void editScenarioRejectsOversizedObjectWithoutOpeningIt() {
        when(noteService.requireOwnFile(NOTE_ID, USER_ID))
                .thenReturn(fileOf("image/png", AiSummaryImagePolicy.MAX_SIZE_BYTES + 1));

        assertThatThrownBy(() -> service.summarizeExistingNote(USER_ID, NOTE_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(storageService, never()).open(any(), anyString());
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
        AiService oneShotChat = new AiService(chatClient, new AiSummaryImagePolicy(),
                summaryRateLimiter, new AiRateLimiter(1, Duration.ofHours(1), "对话"),
                noteService, storageService, aiMessageMapper, transactionManager);
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
        AiService oneShotChat = new AiService(chatClient, new AiSummaryImagePolicy(),
                summaryRateLimiter, new AiRateLimiter(1, Duration.ofHours(1), "对话"),
                noteService, storageService, aiMessageMapper, transactionManager);
        stubModel("回答");
        oneShotChat.chat(USER_ID, MESSAGE, null);
        verify(chatClient, times(1)).prompt();

        assertThatThrownBy(() -> oneShotChat.chat(USER_ID, MESSAGE, null))
                .isInstanceOf(BusinessException.class);

        verify(chatClient, times(1)).prompt();
        // 被拒的那轮也不该落库
        verify(aiMessageMapper, times(2)).insert(any(AiMessage.class));
    }

    /**
     * 续聊时历史必须按「旧 user、旧 assistant、新 user」逐条原样排在前面。
     * 这条钉的是前缀缓存的前提：顺序或内容一变，缓存就从那里断掉，整段历史按全价重算。
     */
    @Test
    void chatReplaysHistoryInOrderThenAppendsTheNewMessage() {
        when(aiMessageMapper.selectList(any())).thenReturn(List.of(
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
        when(aiMessageMapper.selectList(any()))
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
        verify(aiMessageMapper, times(2)).insert(saved.capture());
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
        when(aiMessageMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.chat(USER_ID, MESSAGE, CONVERSATION_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(chatClient, never()).prompt();
        verify(aiMessageMapper, never()).insert(any(AiMessage.class));
    }

    /** 模型失败时一条消息都不落：历史里不会留下没有回复的孤行，用户重试即干净重放 */
    @Test
    void chatWritesNothingWhenTheModelFails() {
        stubFailure();

        assertThatThrownBy(() -> service.chat(USER_ID, MESSAGE, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));

        verify(aiMessageMapper, never()).insert(any(AiMessage.class));
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

    // ==================== 辅助 ====================

    private AiSummaryRequest uploadRequest(byte[] content, String filename) {
        AiSummaryRequest request = new AiSummaryRequest();
        request.setFile(new MockMultipartFile("file", filename, "application/octet-stream", content));
        return request;
    }

    private static NoteFileOwnershipRow fileOf(String contentType, Long size) {
        NoteFileOwnershipRow row = new NoteFileOwnershipRow();
        row.setUploaderId(USER_ID);
        row.setStatus("ONLINE");
        row.setStorageKey("notes/2026/10/x.jpg");
        row.setContentType(contentType);
        row.setSize(size);
        return row;
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
     * 摘要走 {@code user(Consumer)}、对话走 {@code messages(List)} 再挂 {@code tools(...)}，
     * 每条链都要 stub——漏掉的那条会返回 mock 默认的 null，链式调用直接 NPE，
     * 报错看着像生产代码的 bug。
     */
    @SuppressWarnings("unchecked")
    private void stubRequestSpec() {
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
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

    /** 取出传给 {@code user(...)} 的那个 Consumer，用来检查它到底往请求里放了什么 */
    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Consumer<ChatClient.PromptUserSpec>> userSpecCaptor() {
        ArgumentCaptor<Consumer<ChatClient.PromptUserSpec>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestSpec).user(captor.capture());
        return captor;
    }

    /**
     * 一个能安全执行 service 里那个 lambda 的 {@code PromptUserSpec}。
     *
     * <p>必须把 {@code text(...)} stub 成返回自身：真实实现是返回 {@code this} 的流式接口，
     * 而 mock 默认返回 null——不 stub 的话 {@code spec.text(...).media(...)} 会直接 NPE，
     * 报错看上去像生产代码的 bug，其实是 mock 没搭好。
     */
    private ChatClient.PromptUserSpec stubUserSpec() {
        ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
        when(userSpec.text(anyString())).thenReturn(userSpec);
        return userSpec;
    }
}
