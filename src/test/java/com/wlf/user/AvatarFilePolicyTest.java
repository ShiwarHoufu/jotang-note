package com.wlf.user;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AvatarFilePolicy} 的单元测试：不起 Spring 上下文，字节数组直接喂。
 *
 * <p>头像是本模块里唯一直接面向公网的写入点，而它落在<b>公共读</b>桶中（§6.7），
 * 一旦放进来 svg / html 就是 OSS 域名下的存储型 XSS。故本类的重点全在「白名单之外一律拒绝」
 * 与「扩展名与魔数必须一致」这两条上。
 *
 * <p>注意头像白名单是 note 那张表的<b>子集</b>：gif / pdf 在笔记里合法，在头像里不合法。
 * 这条差异正是两个策略类各持一份白名单的理由（见 {@link AvatarFilePolicy} 类注释）。
 */
class AvatarFilePolicyTest {

    // ---- 合法文件头。尾部多余字节对判定无影响：魔数只校验前缀 ----
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    /** note 的合法类型，但不是头像的 */
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};
    /** Windows 可执行文件的 MZ 头，用来冒充图片 */
    private static final byte[] EXE = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00};

    private final AvatarFilePolicy policy = new AvatarFilePolicy();

    // ==================== 白名单放行 ====================

    @Test
    void rasterImagesAreAccepted() {
        assertThat(inspect("头像.jpg", JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(inspect("头像.jpeg", JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(inspect("头像.png", PNG).contentType()).isEqualTo("image/png");
        assertThat(inspect("头像.webp", WEBP).contentType()).isEqualTo("image/webp");
        assertThat(inspect("头像.jpg", JPEG).extension()).isEqualTo("jpg");
    }

    /** 扩展名大小写不敏感；主体是客户端可控的，不能靠它已经规范化过 */
    @Test
    void extensionIsCaseInsensitive() {
        assertThat(inspect("照片.PNG", PNG).extension()).isEqualTo("png");
        assertThat(inspect("照片.JpEg", JPEG).contentType()).isEqualTo("image/jpeg");
    }

    /** 客户端可能送带路径的文件名（浏览器一般不会，但客户端是可控的），先剥掉再取扩展名 */
    @Test
    void pathInFileNameIsStrippedBeforeExtensionIsRead() {
        assertThat(inspect("../../etc/evil.png", PNG).extension()).isEqualTo("png");
        assertThat(inspect("C:\\Users\\x\\头像.png", PNG).extension()).isEqualTo("png");
    }

    // ==================== 白名单之外一律拒绝 ====================

    /** svg 能在 OSS 域名下形成存储型 XSS，是本节最主要的防守对象（§6.7、§8.3） */
    @Test
    void svgIsRejected() {
        byte[] svg = "<svg onload=alert(1)>".getBytes();
        assertThatThrownBy(() -> inspect("evil.svg", svg))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
    }

    @Test
    void htmlAndScriptAreRejected() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertThatThrownBy(() -> inspect("evil.html", html)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inspect("evil.js", html)).isInstanceOf(BusinessException.class);
    }

    /** gif / pdf 是 note 的合法类型，头像不收——白名单是子集，不是同一张表 */
    @Test
    void typesValidForNotesAreRejectedForAvatars() {
        assertThatThrownBy(() -> inspect("动图.gif", GIF)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inspect("文档.pdf", PDF)).isInstanceOf(BusinessException.class);
    }

    /** 改后缀的伪装文件：扩展名合法但文件头对不上 */
    @Test
    void extensionMustMatchMagicBytes() {
        assertThatThrownBy(() -> inspect("伪装.png", JPEG))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
        assertThatThrownBy(() -> inspect("伪装.jpg", EXE)).isInstanceOf(BusinessException.class);
        // RIFF 头但偏移 8 处不是 WEBP（比如其实是 wav）
        byte[] riffButNotWebp = {0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45};
        assertThatThrownBy(() -> inspect("伪装.webp", riffButNotWebp)).isInstanceOf(BusinessException.class);
    }

    /** 太短的文件头不能因为「前缀恰好对上」就放行 */
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

    // ==================== 边界 ====================

    @Test
    void emptyFileIsRejected() {
        assertThatThrownBy(() -> inspect("空.png", new byte[0]))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
    }

    /** 2MB 是上界（含），再多一个字节就拒 */
    @Test
    void sizeLimitIsTwoMegabytesInclusive() {
        assertThat(inspect("刚好.png", pngOfSize(AvatarFilePolicy.MAX_SIZE_BYTES)).extension())
                .isEqualTo("png");

        assertThatThrownBy(() -> inspect("超一点.png", pngOfSize(AvatarFilePolicy.MAX_SIZE_BYTES + 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));
    }

    // ==================== 对象键 ====================

    /** 键按 {@code avatars/{userId}/{uuid}.{ext}} 生成，且每次都不一样（版本化，§6.7） */
    @Test
    void objectKeyIsVersionedUnderUserDirectory() {
        String first = policy.objectKey(42L, "png");
        String second = policy.objectKey(42L, "png");

        assertThat(first).matches("avatars/42/[0-9a-f-]{36}\\.png");
        // 版本化的键不该相同：相同就意味着覆盖旧对象，长强缓存会开始串图
        assertThat(first).isNotEqualTo(second);
    }

    // ==================== 辅助 ====================

    private AvatarDecision inspect(String name, byte[] content) {
        return policy.inspect(name, content.length, new ByteArrayInputStream(content));
    }

    /** 生成一个以 PNG 魔数开头、总长恰好为 {@code size} 的字节数组 */
    private static byte[] pngOfSize(long size) {
        byte[] bytes = Arrays.copyOf(PNG, (int) size);
        // 魔数之外的部分填 0 即可：判定只看文件头
        return bytes;
    }
}
