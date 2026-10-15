package com.wlf.ai;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AiSummaryFilePolicy} 的单元测试：不起 Spring 上下文，字节数组直接喂。
 *
 * <p>重点有四条：<b>白名单之外一律拒绝</b>（放过去不是「宽容」，是把一个注定失败的请求
 * 推给上游白花钱）、<b>扩展名与魔数必须一致</b>、<b>图片的大小上界</b>，以及<b>文本的两道闸</b>
 * ——字节预筛防堆、code point 计数才是真闸门，缺一不可。
 *
 * <p>另外还要覆住 {@code requireSummarizable}——它是编辑场景唯一的判断依据，那边没有字节可验，
 * 只有库里那个 {@code content_type}。
 */
class AiSummaryFilePolicyTest {

    // ---- 合法文件头。尾部多余字节对判定无影响：魔数只校验前缀 ----
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    /** note 的合法类型，也是本策略的合法类型——这是它与头像白名单的差别（头像不收 gif） */
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    /** note 里不收、模型也读不了的类型：走不到上游，本地就该拒 */
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
    /** 文本这档没有魔数可验，内容是什么都不影响准入——只按扩展名与大小判 */
    private static final byte[] TXT = "第一章 绪论".getBytes();

    private final AiSummaryFilePolicy policy = new AiSummaryFilePolicy();

    // ==================== 白名单放行 ====================

    /** 返回的必须是交给模型的 MIME，而不是扩展名——图片支直接拿它去构造 Media */
    @Test
    void rasterImagesAreAcceptedAndMimeIsNormalized() {
        assertThat(inspect("板书.jpg", JPEG)).isEqualTo("image/jpeg");
        assertThat(inspect("板书.jpeg", JPEG)).isEqualTo("image/jpeg");
        assertThat(inspect("截图.png", PNG)).isEqualTo("image/png");
        assertThat(inspect("拍的.gif", GIF)).isEqualTo("image/gif");
        assertThat(inspect("拍的.webp", WEBP)).isEqualTo("image/webp");
    }

    @Test
    void markdownAndPlainTextAreAcceptedAsTextMime() {
        assertThat(inspect("笔记.md", TXT)).isEqualTo("text/markdown");
        assertThat(inspect("说明.txt", TXT)).isEqualTo("text/plain");
    }

    @Test
    void extensionIsCaseInsensitive() {
        assertThat(inspect("照片.PNG", PNG)).isEqualTo("image/png");
        assertThat(inspect("照片.JpEg", JPEG)).isEqualTo("image/jpeg");
        assertThat(inspect("笔记.MD", TXT)).isEqualTo("text/markdown");
    }

    @Test
    void pathInFileNameIsStrippedBeforeExtensionIsRead() {
        assertThat(inspect("../../etc/evil.png", PNG)).isEqualTo("image/png");
        assertThat(inspect("C:\\Users\\x\\板书.png", PNG)).isEqualTo("image/png");
        assertThat(inspect("../../etc/evil.md", TXT)).isEqualTo("text/markdown");
    }

    // ==================== 白名单之外一律拒绝 ====================

    /** note 收 pdf / zip 与否不由这里管，但 AI 这边一定不收：放行只会白花一次计费再换回上游错误 */
    @Test
    void typesOutsideTheWhitelistAreRejected() {
        assertThatThrownBy(() -> inspect("讲义.pdf", PDF)).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
        assertThatThrownBy(() -> inspect("打包.zip", ZIP)).isInstanceOf(BusinessException.class);
    }

    /** 白名单是「扩展名」而不是 MIME 串：note 认 md，不认 markdown，这里同理 */
    @Test
    void markdownExtensionIsNotInTheWhitelist() {
        assertThatThrownBy(() -> inspect("笔记.markdown", TXT)).isInstanceOf(BusinessException.class);
    }

    @Test
    void svgAndHtmlAreRejected() {
        assertThatThrownBy(() -> inspect("evil.svg", "<svg onload=alert(1)>".getBytes()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inspect("evil.html", "<html></html>".getBytes()))
                .isInstanceOf(BusinessException.class);
    }

    /** 改后缀的伪装文件：扩展名合法但文件头对不上 */
    @Test
    void extensionMustMatchMagicBytes() {
        assertThatThrownBy(() -> inspect("伪装.png", JPEG)).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
        // 把 PDF 报成图片
        assertThatThrownBy(() -> inspect("伪装.jpg", PDF)).isInstanceOf(BusinessException.class);
        // RIFF 头但偏移 8 处不是 WEBP（比如其实是 wav）
        byte[] riffButNotWebp = {0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45};
        assertThatThrownBy(() -> inspect("伪装.webp", riffButNotWebp)).isInstanceOf(BusinessException.class);
    }

    @Test
    void truncatedHeadIsRejected() {
        assertThatThrownBy(() -> inspect("半张.png", new byte[]{(byte) 0x89, 0x50}))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void missingOrHiddenExtensionIsRejected() {
        assertThatThrownBy(() -> inspect("没有扩展名", PNG)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inspect(".png", PNG)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inspect("结尾是点.", PNG)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inspect(null, PNG)).isInstanceOf(BusinessException.class);
    }

    // ==================== 大小上界 ====================

    @Test
    void emptyFileIsRejected() {
        assertThatThrownBy(() -> inspect("空.png", new byte[0])).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
        assertThatThrownBy(() -> inspect("空.md", new byte[0])).isInstanceOf(BusinessException.class);
    }

    /** 10MB 是上界（含），再多一个字节就拒——与 AvatarFilePolicyTest 的 2MB 那条同形 */
    @Test
    void sizeLimitIsTenMegabytesInclusive() {
        assertThat(inspect("刚好.png", pngOfSize(AiSummaryFilePolicy.MAX_SIZE_BYTES))).isEqualTo("image/png");

        assertThatThrownBy(() -> inspect("超一点.png", pngOfSize(AiSummaryFilePolicy.MAX_SIZE_BYTES + 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
    }

    /**
     * 文本在<b>读之前</b>的那道闸：用 {@code MAX_TEXT_BYTES}，比图片那个 10MB 小得多。
     * 它防的是「把大对象读进堆」，报的是字数口径而不是 MB。
     */
    @Test
    void textByteLimitRejectsBeforeDecoding() {
        assertThat(inspect("刚好.md", textOfSize(AiSummaryFilePolicy.MAX_TEXT_BYTES))).isEqualTo("text/markdown");

        assertThatThrownBy(() -> inspect("超一点.md", textOfSize(AiSummaryFilePolicy.MAX_TEXT_BYTES + 1)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID);
                    assertThat(ex.getMessage()).contains(AiSummaryFilePolicy.MAX_TEXT_CHARS + " 字");
                });
    }

    /**
     * 这条是<b>「字节预筛不够」的唯一证据</b>：八万个 ASCII 字符的字节数远小于
     * {@code MAX_TEXT_BYTES}（那是按「一个字符最多四字节」算出来的上界），却超了字符上限。
     * 字节那道拦住的是大对象，字符这道才是真正的闸门。
     */
    @Test
    void textLongerThanTheCharLimitIsRejectedEvenUnderTheByteLimit() {
        String body = "a".repeat(AiSummaryFilePolicy.MAX_TEXT_CHARS + 1);
        assertThat(body.length()).isLessThan((int) AiSummaryFilePolicy.MAX_TEXT_BYTES);

        assertThatThrownBy(() -> AiSummaryFilePolicy.requireTextWithinLimit(body))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
    }

    @Test
    void textAtExactlyTheCharLimitIsAccepted() {
        assertThatCode(() -> AiSummaryFilePolicy.requireTextWithinLimit(
                "字".repeat(AiSummaryFilePolicy.MAX_TEXT_CHARS))).doesNotThrowAnyException();
    }

    /** 按 code point 而不是 UTF-16 单元计：一个 emoji 是代理对，算成两个字就会误拒一半的正文 */
    @Test
    void textLimitCountsCodePointsNotUtf16Units() {
        String body = "📚".repeat(AiSummaryFilePolicy.MAX_TEXT_CHARS);
        assertThat(body.length()).isGreaterThan(AiSummaryFilePolicy.MAX_TEXT_CHARS);

        assertThatCode(() -> AiSummaryFilePolicy.requireTextWithinLimit(body)).doesNotThrowAnyException();
    }

    // ==================== requireSummarizable（编辑场景的判据）====================

    /** 编辑场景没有字节可验，只能按库里那列 content_type 判——它是按钮可否出现的唯一依据 */
    @Test
    void requireSummarizableAcceptsImagesAndText() {
        assertThatCode(() -> policy.requireSummarizable("image/jpeg")).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireSummarizable("image/png")).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireSummarizable("image/gif")).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireSummarizable("image/webp")).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireSummarizable("text/markdown")).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireSummarizable("text/plain")).doesNotThrowAnyException();
    }

    @Test
    void requireSummarizableRejectsEverythingElseIncludingNull() {
        // note 支持的类型不等于 AI 支持的类型——它与 inspect 的白名单必须一致
        assertThatThrownBy(() -> policy.requireSummarizable("application/pdf"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
        assertThatThrownBy(() -> policy.requireSummarizable("image/svg+xml")).isInstanceOf(BusinessException.class);
        // 落库值为 null 不该抛 NPE：它是「这行数据不完整」，不是「服务器错了」
        assertThatThrownBy(() -> policy.requireSummarizable(null)).isInstanceOf(BusinessException.class);
    }

    // ==================== 分档判据与大小上限 ====================

    /** 走纯文本调用的只有 md / txt 两档；未知值与 null 都当二进制，不能让它们抛 NPE */
    @Test
    void isTextIsDerivedFromTheSameTable() {
        assertThat(AiSummaryFilePolicy.isText("text/markdown")).isTrue();
        assertThat(AiSummaryFilePolicy.isText("text/plain")).isTrue();
        assertThat(AiSummaryFilePolicy.isText("image/png")).isFalse();
        assertThat(AiSummaryFilePolicy.isText("application/pdf")).isFalse();
        assertThat(AiSummaryFilePolicy.isText(null)).isFalse();
    }

    /** 编辑场景的「先拦后读」：这一层只用库里的 size，一个字节都不碰 */
    @Test
    void requireWithinLimitGivesEachKindItsOwnCeiling() {
        assertThatCode(() -> policy.requireWithinLimit("image/png", AiSummaryFilePolicy.MAX_SIZE_BYTES))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireWithinLimit("image/png", AiSummaryFilePolicy.MAX_SIZE_BYTES + 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        // 文本的上限小得多：图片那边轻松放行的尺寸，文本要拒
        assertThatCode(() -> policy.requireWithinLimit("text/plain", AiSummaryFilePolicy.MAX_TEXT_BYTES))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireWithinLimit("text/plain", AiSummaryFilePolicy.MAX_TEXT_BYTES + 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        // size 为 null（投影被改坏那类）：这里放过去，交给调用方的有界读兜住
        assertThatCode(() -> policy.requireWithinLimit("text/plain", null)).doesNotThrowAnyException();
    }

    // ==================== 辅助 ====================

    private String inspect(String name, byte[] content) {
        return policy.inspect(name, content.length, new ByteArrayInputStream(content));
    }

    /** 生成一个以 PNG 魔数开头、总长恰好为 {@code size} 的字节数组 */
    private static byte[] pngOfSize(long size) {
        return Arrays.copyOf(PNG, (int) size);
    }

    /** 生成一段总长恰好为 {@code size} 的文本字节（补出来的零字节不影响准入判定） */
    private static byte[] textOfSize(long size) {
        return Arrays.copyOf(TXT, (int) size);
    }
}
