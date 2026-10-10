package com.wlf.ai;

import com.wlf.ai.dto.AiSummaryRequest;
import com.wlf.ai.dto.AiSummaryResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.note.NoteFileOwnershipRow;
import com.wlf.note.NoteService;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MimeTypeUtils;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiSummaryService} 的单元测试：模型、存储与 note 模块全部 mock，不起 Spring 上下文。
 *
 * <p>重点在<b>编排</b>而不是模型本身：什么情况下不该去调模型（省一次计费）、
 * 图片以什么 MIME 交给模型、上游失败翻成哪个错误码、以及回程截断。
 * 判定规则的细节在 {@link AiSummaryImagePolicyTest}，这里不重复。
 */
class AiSummaryServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};
    private static final Long USER_ID = 7L;
    private static final Long NOTE_ID = 42L;

    private ChatClient chatClient;
    private StorageService storageService;
    private NoteService noteService;
    private AiSummaryService service;

    /** 最近一次 stub 出来的请求规格，用来断言真正发出去的内容 */
    private ChatClient.ChatClientRequestSpec requestSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        storageService = mock(StorageService.class);
        noteService = mock(NoteService.class);
        // 策略与限流器用真实现：它们本身就是这一层的规则，mock 掉等于把被测对象掏空
        service = new AiSummaryService(chatClient, new AiSummaryImagePolicy(),
                new AiSummaryRateLimiter(), noteService, storageService);
    }

    // ==================== 上传场景 ====================

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
        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(PDF, "讲义.pdf")))
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

    // ==================== 回程截断 ====================

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

    // ==================== 上游失败 ====================

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

    // ==================== 编辑场景 ====================

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
        when(noteService.requireOwnFile(NOTE_ID, USER_ID)).thenReturn(fileOf("application/pdf", 1024L));

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
    @SuppressWarnings("unchecked")
    private void stubModel(String reply) {
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(reply);
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
