package com.wlf.ai;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AiSummaryImagePolicy} 的单元测试：不起 Spring 上下文，字节数组直接喂。
 *
 * <p>重点有三条：<b>白名单之外一律拒绝</b>（放过去不是「宽容」，是把一个注定失败的请求
 * 推给上游白花钱）、<b>扩展名与魔数必须一致</b>、以及<b>大小上界</b>。
 *
 * <p>另外还要覆住 {@code supports}——它是编辑场景唯一的判断依据，那边没有字节可验，
 * 只有库里那个 {@code content_type}。
 */
class AiSummaryImagePolicyTest {

    // ---- 合法文件头。尾部多余字节对判定无影响：魔数只校验前缀 ----
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    /** note 的合法类型，也是本策略的合法类型——这是它与头像白名单的差别（头像不收 gif） */
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    /** note 里合法、但模型读不了的类型（§6.8） */
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
    private static final byte[] TXT = "第一章 绪论".getBytes();

    private final AiSummaryImagePolicy policy = new AiSummaryImagePolicy();

    // ==================== 白名单放行 ====================

    /** 返回的必须是交给模型的 MIME，而不是扩展名——service 直接拿它去构造 Media */
    @Test
    void rasterImagesAreAcceptedAndMimeIsNormalized() {
        assertThat(inspect("板书.jpg", JPEG)).isEqualTo("image/jpeg");
        assertThat(inspect("板书.jpeg", JPEG)).isEqualTo("image/jpeg");
        assertThat(inspect("截图.png", PNG)).isEqualTo("image/png");
        assertThat(inspect("拍的.gif", GIF)).isEqualTo("image/gif");
        assertThat(inspect("拍的.webp", WEBP)).isEqualTo("image/webp");
    }

    @Test
    void extensionIsCaseInsensitive() {
        assertThat(inspect("照片.PNG", PNG)).isEqualTo("image/png");
        assertThat(inspect("照片.JpEg", JPEG)).isEqualTo("image/jpeg");
    }

    @Test
    void pathInFileNameIsStrippedBeforeExtensionIsRead() {
        assertThat(inspect("../../etc/evil.png", PNG)).isEqualTo("image/png");
        assertThat(inspect("C:\\Users\\x\\板书.png", PNG)).isEqualTo("image/png");
    }

    // ==================== 白名单之外一律拒绝 ====================

    /**
     * 这条是本策略与另两条准入链路的<b>关键差别</b>：note 收 PDF、收 docx，
     * 而 AI 只能读光栅图。放行它们不会带来安全风险，但会白花一次计费再换回一个上游错误。
     */
    @Test
    void nonImageTypesValidForNotesAreRejectedHere() {
        assertThatThrownBy(() -> inspect("讲义.pdf", PDF)).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
        assertThatThrownBy(() -> inspect("讲义.docx", ZIP)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inspect("笔记.md", TXT)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inspect("打包.zip", ZIP)).isInstanceOf(BusinessException.class);
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
    }

    /** 10MB 是上界（含），再多一个字节就拒——与 AvatarFilePolicyTest 的 2MB 那条同形 */
    @Test
    void sizeLimitIsTenMegabytesInclusive() {
        assertThat(inspect("刚好.png", pngOfSize(AiSummaryImagePolicy.MAX_SIZE_BYTES))).isEqualTo("image/png");

        assertThatThrownBy(() -> inspect("超一点.png", pngOfSize(AiSummaryImagePolicy.MAX_SIZE_BYTES + 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
    }

    // ==================== supports（编辑场景的判据）====================

    /** 编辑场景没有字节可验，只能按库里那列 content_type 判——它是按钮可否出现的唯一依据 */
    @Test
    void supportsAcceptsExactlyTheFourRasterTypes() {
        assertThat(policy.supports("image/jpeg")).isTrue();
        assertThat(policy.supports("image/png")).isTrue();
        assertThat(policy.supports("image/gif")).isTrue();
        assertThat(policy.supports("image/webp")).isTrue();
    }

    @Test
    void supportsRejectsEverythingElseIncludingNull() {
        // PDF 在 note 里合法，在 AI 这里不合法——supports 与 inspect 的白名单必须一致
        assertThat(policy.supports("application/pdf")).isFalse();
        assertThat(policy.supports("text/markdown")).isFalse();
        assertThat(policy.supports("image/svg+xml")).isFalse();
        // 落库值为 null 不该抛 NPE：它是「这行数据不完整」，不是「服务器错了」
        assertThat(policy.supports(null)).isFalse();
    }

    // ==================== 辅助 ====================

    private String inspect(String name, byte[] content) {
        return policy.inspect(name, content.length, new ByteArrayInputStream(content));
    }

    /** 生成一个以 PNG 魔数开头、总长恰好为 {@code size} 的字节数组 */
    private static byte[] pngOfSize(long size) {
        return Arrays.copyOf(PNG, (int) size);
    }
}
