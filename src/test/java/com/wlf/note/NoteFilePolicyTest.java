package com.wlf.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NoteFilePolicy} 的单元测试：不起 Spring 上下文，字节数组直接喂。
 *
 * <p>这是预览型 XSS 的唯一防线（§8.3），用例按「白名单放行 / 白名单外拒绝 / 魔数不符 /
 * 文本降级 / 边界」分组，重点是那些<b>写错了也不会立刻暴露</b>的地方——
 * docx 与 zip 魔数同族、跨块边界被切开的多字节字符、藏在合法 UTF-8 里的 NUL。
 *
 * <p>几处「可内联预览 / 仅下载」的断言写成 {@code PreviewMode.of(decision.contentType())}：
 * 预览方式已经不在上传判定里（理由见 {@link PreviewMode#of(String)}），而是读取侧由
 * {@code content_type} 现推。这样写顺带锁住一件事——本表判出的 {@code content_type}
 * 必须仍被那张映射认识，否则「传上去了却只能下载」会在详情页上静默发生。
 * 映射本身的全量用例见 {@link PreviewModeTest}。
 */
class NoteFilePolicyTest {

    // ---- 各类文件的合法文件头。尾部多余字节对二进制类型无影响：魔数只校验前缀 ----
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};          // %PDF-1.7
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};                      // GIF89a
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
    private static final byte[] RAR = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00};
    private static final byte[] SEVEN_Z = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    /** Windows 可执行文件的 MZ 头，用来冒充别的类型 */
    private static final byte[] EXE = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00};

    private final NoteFilePolicy policy = new NoteFilePolicy();

    // ==================== 白名单放行 ====================

    @Test
    void pdfIsAcceptedForInlinePreview() {
        FileDecision decision = inspect("课件.pdf", PDF);

        assertThat(decision.contentType()).isEqualTo("application/pdf");
        assertThat(PreviewMode.of(decision.contentType())).isEqualTo(PreviewMode.PDF_INLINE);
        assertThat(decision.extension()).isEqualTo("pdf");
        assertThat(decision.originalName()).isEqualTo("课件.pdf");
    }

    @Test
    void rasterImagesAreAcceptedForInlinePreview() {
        assertThat(inspect("a.png", PNG).contentType()).isEqualTo("image/png");
        assertThat(inspect("a.jpg", JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(inspect("a.gif", GIF).contentType()).isEqualTo("image/gif");
        assertThat(inspect("a.webp", WEBP).contentType()).isEqualTo("image/webp");

        assertThat(PreviewMode.of(inspect("a.png", PNG).contentType())).isEqualTo(PreviewMode.IMAGE_INLINE);
    }

    /** jpg 与 jpeg 是两个常见写法，都要认；归一化后仍是各自的原始写法 */
    @Test
    void jpegAliasIsAccepted() {
        assertThat(inspect("a.jpeg", JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(inspect("a.jpeg", JPEG).extension()).isEqualTo("jpeg");
    }

    /**
     * docx / xlsx / pptx / zip 的魔数完全一样，类型只能由扩展名区分。
     * 这组用例锁住的就是「别按魔数推断类型」——否则 docx 会被判成 zip。
     */
    @Test
    void officeAndArchiveFilesAreDownloadOnlyAndKeepDistinctTypes() {
        assertThat(inspect("论文.docx", ZIP).contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(inspect("成绩.xlsx", ZIP).contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(inspect("汇报.pptx", ZIP).contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        assertThat(inspect("资料.zip", ZIP).contentType()).isEqualTo("application/zip");
        assertThat(inspect("资料.rar", RAR).contentType()).isEqualTo("application/vnd.rar");
        assertThat(inspect("资料.7z", SEVEN_Z).contentType()).isEqualTo("application/x-7z-compressed");

        assertThat(PreviewMode.of(inspect("论文.docx", ZIP).contentType())).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
    }

    @Test
    void markdownAndTextGoThroughBackendProxy() {
        assertThat(PreviewMode.of(inspect("笔记.md", text("# 标题\n正文")).contentType()))
                .isEqualTo(PreviewMode.TEXT_PROXY);
        assertThat(inspect("笔记.md", text("# 标题")).contentType()).isEqualTo("text/markdown");
        assertThat(inspect("说明.txt", text("正文")).contentType()).isEqualTo("text/plain");
    }

    @Test
    void extensionIsLowerCasedButOriginalNameKeepsItsCase() {
        FileDecision decision = inspect("PHOTO.PNG", PNG);

        assertThat(decision.extension()).isEqualTo("png");
        assertThat(decision.originalName()).isEqualTo("PHOTO.PNG");
    }

    /** 客户端可能送来带路径的文件名（Windows 浏览器历史上会送全路径） */
    @Test
    void pathInFileNameIsStripped() {
        assertThat(inspect("../../etc/passwd.pdf", PDF).originalName()).isEqualTo("passwd.pdf");
        assertThat(inspect("C:\\Users\\张三\\笔记.PDF", PDF).originalName()).isEqualTo("笔记.PDF");
    }

    @Test
    void sizeExactlyAtLimitIsAccepted() {
        FileDecision decision = policy.inspect("big.pdf", NoteFilePolicy.MAX_SIZE_BYTES, new ByteArrayInputStream(PDF));

        assertThat(decision.extension()).isEqualTo("pdf");
    }

    // ==================== 大小与文件名 ====================

    @Test
    void oversizedFileIsRejected() {
        assertThatThrownBy(() ->
                policy.inspect("big.pdf", NoteFilePolicy.MAX_SIZE_BYTES + 1, new ByteArrayInputStream(PDF)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID);
                    assertThat(e.getMessage()).contains("100MB");
                });
    }

    @Test
    void emptyFileIsRejected() {
        assertRejected("空.txt", text(""));
    }

    @Test
    void overlongFileNameIsRejected() {
        assertRejectedWithMessage("a".repeat(256) + ".pdf", PDF, "文件名超过");
    }

    // ==================== 白名单之外 ====================

    /** svg / html / js 能在 OSS 域名下形成存储型 XSS，是刻意不收的（§6.1、§6.7）；.doc 等旧版 Office 也不在白名单内 */
    @Test
    void unlistedExtensionsAreRejected() {
        assertRejectedWithMessage("evil.svg", text("<svg onload=alert(1)>"), "不支持的文件类型");
        assertRejected("evil.html", text("<script>alert(1)</script>"));
        assertRejected("evil.js", text("alert(1)"));
        assertRejected("evil.exe", EXE);
        assertRejected("旧格式.doc", ZIP);
    }

    @Test
    void missingExtensionIsRejected() {
        assertRejectedWithMessage("README", text("hello"), "文件缺少扩展名");
    }

    /** .bashrc 这类隐藏文件没有「扩展名」语义，不能把它后面的部分当类型 */
    @Test
    void dotFileIsRejected() {
        assertRejectedWithMessage(".bashrc", text("export A=1"), "文件缺少扩展名");
    }

    @Test
    void trailingDotIsRejected() {
        assertRejected("weird.", text("x"));
    }

    // ==================== 魔数与扩展名不符 ====================

    @Test
    void executableRenamedToPdfIsRejected() {
        assertRejectedWithMessage("fake.pdf", EXE, "文件内容与扩展名 .pdf 不符");
    }

    @Test
    void pngRenamedToJpgIsRejected() {
        assertRejected("fake.jpg", PNG);
    }

    /** PK 头冒充 PDF：魔数族不对，不能因为「都是文件头」就放行 */
    @Test
    void zipRenamedToPdfIsRejected() {
        assertRejected("fake.pdf", ZIP);
    }

    /** 文件头被截断：前缀对得上但长度不够，属于残缺文件，一律拒 */
    @Test
    void truncatedMagicIsRejected() {
        assertRejected("broken.pdf", new byte[]{0x25, 0x50, 0x44, 0x46});
        assertRejected("broken.zip", new byte[]{0x50, 0x4B, 0x03});
    }

    // ==================== md / txt 的降级校验 ====================

    @Test
    void utf8TextIsAccepted() {
        assertThat(inspect("笔记.md", text("# 中文标题\nEnglish 123\n")).extension()).isEqualTo("md");
    }

    /** NUL 本身是合法 UTF-8（U+0000），decoder 不会拦，必须单独查 */
    @Test
    void textContainingNulByteIsRejected() {
        assertRejectedWithMessage("fake.txt", bytes('a', 'b', 'c', 0x00, 'd'), "NUL");
    }

    @Test
    void binaryMasqueradingAsTextIsRejected() {
        assertRejected("fake.txt", bytes(0x01, 0x02, 0xFF, 0xFE));
    }

    /** 结尾是残缺的多字节序列，EOF 时必须以 REPORT 判为非法，而不是当作流恰好结束 */
    @Test
    void truncatedMultibyteCharAtEofIsRejected() {
        assertRejected("fake.txt", bytes('a', 'b', 'c', 0xE4, 0xB8));
    }

    /**
     * 类注释里那个「块边界切开多字节字符」的场景：8KB 块在解码前拼接头部、块之间保留
     * 未消费的尾字节，才不会把一个「中」字切成两半后误判为非法 UTF-8。
     * 若哪天把实现改回「每块单独 decode(bb)」，本用例会红。
     */
    @Test
    void multibyteCharSplitAcrossChunkBoundaryIsAccepted() {
        // 16 字节头部之后，首个 8KB 块止于第 8207 字节；让「中」占 8206~8208，正好跨块
        String content = "a".repeat(8206) + "中";

        assertThat(inspect("边界.md", text(content)).extension()).isEqualTo("md");
    }

    // ==================== 对象键 ====================

    @Test
    void objectKeyFollowsDocumentedLayout() {
        assertThat(policy.objectKey("pdf")).matches("notes/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf");
    }

    @Test
    void objectKeyIsUniquePerCall() {
        assertThat(policy.objectKey("pdf")).isNotEqualTo(policy.objectKey("pdf"));
    }

    // ==================== 辅助 ====================

    private FileDecision inspect(String name, byte[] content) {
        return policy.inspect(name, content.length, new ByteArrayInputStream(content));
    }

    private void assertRejected(String name, byte[] content) {
        assertThatThrownBy(() -> inspect(name, content))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
    }

    private void assertRejectedWithMessage(String name, byte[] content, String expectedMessagePart) {
        assertThatThrownBy(() -> inspect(name, content))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID);
                    assertThat(e.getMessage()).contains(expectedMessagePart);
                });
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
