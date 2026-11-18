package com.wlf.ai;

import com.wlf.ai.application.assistant.AiRateLimiter;
import com.wlf.ai.application.assistant.AiSummaryService;
import com.wlf.ai.application.assistant.UntrustedContent;
import com.wlf.ai.application.assistant.dto.AiSummaryRequest;
import com.wlf.ai.application.assistant.dto.AiSummaryResponse;
import com.wlf.ai.application.port.out.ChatModelPort;
import com.wlf.ai.application.port.out.NoteContentReader;
import com.wlf.ai.application.port.out.NoteFileRef;
import com.wlf.ai.domain.assistant.AiSummaryFilePolicy;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AiSummaryService} 的单元测试：模型端口、存储与 note 模块全部 mock，不起 Spring 上下文。
 *
 * <p>重点是<b>编排</b>而不是模型本身：什么情况下不该去调模型（省一次计费）、
 * 以什么 MIME 交给模型、上游失败翻成哪个错误码、回程截断、文本与图片的分流。
 * 判定规则的细节在 {@link AiSummaryFilePolicyTest}，这里不重复。
 */
class AiSummaryServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    /** 走纯文本调用的一档：正文原样进 user 段，不进 media */
    private static final byte[] MD = "# 第一章 绪论".getBytes();
    /** 白名单之外：拿它验「本地就拦下，不白花一次计费」 */
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};
    private static final Long USER_ID = 7L;
    private static final Long NOTE_ID = 42L;

    private ChatModelPort chatModelPort;
    private StorageService storageService;
    private NoteContentReader noteContentReader;
    private AiSummaryService service;

    @BeforeEach
    void setUp() {
        chatModelPort = mock(ChatModelPort.class);
        storageService = mock(StorageService.class);
        noteContentReader = mock(NoteContentReader.class);
        // 策略与限流器用真实现：它们本身就是这一层的规则，mock 掉等于把被测对象掏空
        service = new AiSummaryService(chatModelPort, new AiSummaryFilePolicy(),
                new AiRateLimiter(10, Duration.ofHours(1), "生成"), noteContentReader, storageService);
    }

    // ==================== 上传场景 ====================

    @Test
    void uploadReturnsModelText() {
        stubImageModel("这是一份高数期中复习提纲，覆盖极限与导数。");

        AiSummaryResponse response = service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png"));

        assertThat(response.summary()).isEqualTo("这是一份高数期中复习提纲，覆盖极限与导数。");
    }

    /**
     * 图片必须以<b>服务端判定的 MIME</b> 交给模型（而不是 multipart 自带的 Content-Type，
     * 那个由客户端决定）。这条同时验证了「图片字节真的进了请求」——本服务最核心的一步。
     */
    @Test
    void uploadSendsImageWithServerJudgedMimeAndBytes() {
        stubImageModel("摘要");

        service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png"));

        SentImage sent = sentImage();
        assertThat(sent.contentType()).isEqualTo("image/png");
        assertThat(sent.image()).containsExactly(PNG);
    }

    /** 类型不合法是本地就能判的，不该白花一次计费去撞上游 */
    @Test
    void uploadRejectsUnsupportedTypeWithoutCallingModel() {
        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(PDF, "讲义.pdf")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verifyNoInteractions(chatModelPort);
    }

    @Test
    void uploadRejectsOversizedImageWithoutCallingModel() {
        byte[] tooBig = Arrays.copyOf(PNG, (int) AiSummaryFilePolicy.MAX_SIZE_BYTES + 1);

        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(tooBig, "巨图.png")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verifyNoInteractions(chatModelPort);
    }

    // ==================== 文本路径 ====================

    /**
     * 正文进 user 段、<b>不走图文调用</b>——这是文本路径与图片路径唯一的分野，
     * 写错的后果是把一段 md 当成二进制图塞进多模态输入。同时断言正文真的进了请求体。
     */
    @Test
    void uploadSendsTextBodyWithoutImage() {
        stubTextModel("摘要");

        service.summarizeUpload(USER_ID, uploadRequest(MD, "笔记.md"));

        assertThat(sentText())
                .contains("# 第一章 绪论")
                // 正文要包在信封锁标里，且标记只出现一次——多了就等于自己伪造了闭合
                .containsOnlyOnce(UntrustedContent.BEGIN)
                .containsOnlyOnce(UntrustedContent.END);
        verify(chatModelPort, never()).chatWithImage(anyString(), anyString(), anyString(), any(byte[].class));
    }

    /** 正文超长是本地就能判的，且判在取配额之前，所以这一趟既不花计费也不花额度 */
    @Test
    void overlongTextIsRejectedWithoutCallingModel() {
        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(longText(), "长笔记.md")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verifyNoInteractions(chatModelPort);
    }

    /** 校验不过的请求不该消耗配额：顺序写反的话，第二次调用会直接撞限频 */
    @Test
    void overlongTextDoesNotConsumeQuota() {
        AiSummaryService oneShotSummary = new AiSummaryService(chatModelPort, new AiSummaryFilePolicy(),
                new AiRateLimiter(1, Duration.ofHours(1), "生成"), noteContentReader, storageService);
        stubTextModel("摘要");

        assertThatThrownBy(() -> oneShotSummary.summarizeUpload(USER_ID, uploadRequest(longText(), "长笔记.md")))
                .isInstanceOf(BusinessException.class);

        assertThat(oneShotSummary.summarizeUpload(USER_ID, uploadRequest(MD, "笔记.md")).summary())
                .isEqualTo("摘要");
    }

    /**
     * 文本路径的上游失败仍归 50300。
     *
     * <p>这条和上一条一起钉住错误码的分界：<b>输入不合法是 42200、上游不可用才是 50300</b>。
     * 若把解码与长度判定包进 {@link AiSummaryService} 那个 catch，超长会被报成「稍后重试可能有救」，
     * 而用户重试永远不会成功（§5.7 对 50300 的定义）。
     */
    @Test
    void textPathUpstreamFailureIsStill503Not422() {
        when(chatModelPort.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(MD, "笔记.md")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    // ==================== 回程截断 ====================

    /**
     * 摘要是建议，但不能超过 {@code note.summary} 的列宽——否则用户点保存就撞上
     * {@code @Size(max = 512)}，收到一个「简介不超过 512 个字符」的 40001，
     * 而那段字根本不是他自己打的（§6.8）。
     */
    @Test
    void overlongSummaryIsTruncatedToTheColumnWidth() {
        stubImageModel("字".repeat(600));

        AiSummaryResponse response = service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png"));

        assertThat(response.summary()).hasSize(512);
    }

    /** 按 code point 截：emoji 是代理对，按 UTF-16 单元截会留下孤立的半个字符 */
    @Test
    void truncationCountsCodePointsNotUtf16Units() {
        stubImageModel("📚".repeat(600));

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
        when(chatModelPort.chatWithImage(anyString(), anyString(), anyString(), any(byte[].class)))
                .thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> service.summarizeUpload(USER_ID, uploadRequest(PNG, "板书.png")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
    }

    // ==================== 编辑场景 ====================

    @Test
    void editScenarioReadsObjectFromNoteBucketAndSendsStoredMime() {
        when(noteContentReader.requireOwnFile(NOTE_ID, USER_ID)).thenReturn(fileOf("image/jpeg", 1024L));
        when(storageService.open(Bucket.NOTE, "notes/2026/10/x.jpg"))
                .thenReturn(new ByteArrayInputStream(PNG));
        stubImageModel("摘要");

        AiSummaryResponse response = service.summarizeExistingNote(USER_ID, NOTE_ID, "标题", "张老师");

        assertThat(response.summary()).isEqualTo("摘要");
        assertThat(sentImage().contentType()).isEqualTo("image/jpeg");
    }

    /** 归属/状态的判门归 note 模块，本层只负责不吞掉它——40300 必须原样传上去 */
    @Test
    void editScenarioPropagatesOwnershipFailure() {
        when(noteContentReader.requireOwnFile(NOTE_ID, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.summarizeExistingNote(USER_ID, NOTE_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(chatModelPort);
        verify(storageService, never()).open(any(), anyString());
    }

    /** 附件类型不支持：不去读 OSS，更不去调模型 */
    @Test
    void editScenarioRejectsUnsupportedAttachmentWithoutTouchingOss() {
        when(noteContentReader.requireOwnFile(NOTE_ID, USER_ID)).thenReturn(fileOf("application/pdf", 1024L));

        assertThatThrownBy(() -> service.summarizeExistingNote(USER_ID, NOTE_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(storageService, never()).open(any(), anyString());
        verifyNoInteractions(chatModelPort);
    }

    /** 落库的 content_type 认不出来（含 null）：42200，不读 OSS、不调模型，也不是 NPE */
    @Test
    void editScenarioRejectsUnknownStoredContentTypeWithoutTouchingOss() {
        when(noteContentReader.requireOwnFile(NOTE_ID, USER_ID)).thenReturn(fileOf(null, 1024L));

        assertThatThrownBy(() -> service.summarizeExistingNote(USER_ID, NOTE_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(storageService, never()).open(any(), anyString());
        verifyNoInteractions(chatModelPort);
    }

    /** 编辑场景的同一个分野：从 note 桶读回来的文本也走纯文本调用，不带图片 */
    @Test
    void editScenarioReadsTextObjectAndSendsStoredMimeWithoutImage() {
        when(noteContentReader.requireOwnFile(NOTE_ID, USER_ID)).thenReturn(fileOf("text/markdown", 1024L));
        when(storageService.open(Bucket.NOTE, "notes/2026/10/x.jpg"))
                .thenReturn(new ByteArrayInputStream(MD));
        stubTextModel("摘要");

        AiSummaryResponse response = service.summarizeExistingNote(USER_ID, NOTE_ID, "标题", "张老师");

        assertThat(response.summary()).isEqualTo("摘要");
        assertThat(sentText()).contains("# 第一章 绪论");
        verify(chatModelPort, never()).chatWithImage(anyString(), anyString(), anyString(), any(byte[].class));
    }

    /** 文本的「先拦后读」：库里那列 size 超了就不打开对象 */
    @Test
    void editScenarioRejectsOverlongTextWithoutOpeningIt() {
        when(noteContentReader.requireOwnFile(NOTE_ID, USER_ID))
                .thenReturn(fileOf("text/markdown", AiSummaryFilePolicy.MAX_TEXT_BYTES + 1));

        assertThatThrownBy(() -> service.summarizeExistingNote(USER_ID, NOTE_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(storageService, never()).open(any(), anyString());
        verifyNoInteractions(chatModelPort);
    }

    /**
     * 防御性用例：{@code size} 列本身是 {@code NOT NULL}，造一个 null 行是为了覆盖「投影一旦
     * 被改坏（漏选 / 列名写错）」的那条路——不该 NPE，也不该因此整份拉进堆。
     */
    @Test
    void editScenarioHandlesMissingSizeDefensively() {
        when(noteContentReader.requireOwnFile(NOTE_ID, USER_ID)).thenReturn(fileOf("text/plain", null));
        when(storageService.open(Bucket.NOTE, "notes/2026/10/x.jpg"))
                .thenReturn(new ByteArrayInputStream(MD));
        stubTextModel("摘要");

        assertThat(service.summarizeExistingNote(USER_ID, NOTE_ID, null, null).summary()).isEqualTo("摘要");
    }

    /**
     * 编辑场景没有 multipart 的 size 可判，只能先用库里那一列拦一道。
     * <b>不先拦的话会把一个上百 MB 的对象整个读进堆里</b>再拒掉。
     */
    @Test
    void editScenarioRejectsOversizedObjectWithoutOpeningIt() {
        when(noteContentReader.requireOwnFile(NOTE_ID, USER_ID))
                .thenReturn(fileOf("image/png", AiSummaryFilePolicy.MAX_SIZE_BYTES + 1));

        assertThatThrownBy(() -> service.summarizeExistingNote(USER_ID, NOTE_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(storageService, never()).open(any(), anyString());
        verifyNoInteractions(chatModelPort);
    }

    // ==================== 辅助 ====================

    private AiSummaryRequest uploadRequest(byte[] content, String filename) {
        AiSummaryRequest request = new AiSummaryRequest();
        request.setFile(new MockMultipartFile("file", filename, "application/octet-stream", content));
        return request;
    }

    /** 一段刚好超过字符上限的正文：字节数远小于字节预筛，只有 code point 计数拦得住它 */
    private static byte[] longText() {
        return "a".repeat(AiSummaryFilePolicy.MAX_TEXT_CHARS + 1).getBytes();
    }

    private static NoteFileRef fileOf(String contentType, Long size) {
        return new NoteFileRef("notes/2026/10/x.jpg", size, contentType);
    }

    private void stubImageModel(String reply) {
        when(chatModelPort.chatWithImage(anyString(), anyString(), anyString(), any(byte[].class)))
                .thenReturn(reply);
    }

    private void stubTextModel(String reply) {
        when(chatModelPort.chat(anyString(), anyString())).thenReturn(reply);
    }

    /** 取出文本路径真正发给模型的 user 段 */
    private String sentText() {
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(chatModelPort).chat(system.capture(), user.capture());
        assertThat(system.getValue()).contains("摘要助手");
        return user.getValue();
    }

    /** 取出图文路径真正发给模型的一组参数 */
    private SentImage sentImage() {
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> image = ArgumentCaptor.forClass(byte[].class);
        verify(chatModelPort).chatWithImage(system.capture(), user.capture(), contentType.capture(), image.capture());
        assertThat(system.getValue()).contains("摘要助手");
        return new SentImage(system.getValue(), user.getValue(), contentType.getValue(), image.getValue());
    }

    private record SentImage(String system, String userMessage, String contentType, byte[] image) {
    }
}
