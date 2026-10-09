package com.wlf.common;

/**
 * 文件类型判定的底层原语：魔数（文件头特征字节）与定长比对。
 *
 * <p><b>为什么单独一层</b>：note 的 §6.1 上传白名单与 user 的头像白名单是两条不同的准入规则，
 * 但「jpg 的头是 {@code FF D8 FF}」是同一个事实。两边各抄一份就是同一规则的两份副本
 * （§5.4 的口径），改动漏一处会静默放行错误的类型——而按 §8.3，魔数校验是预览型 XSS 的
 * <b>唯一</b>防线（OSS 自 2025-01-20 起禁止签名 URL 覆盖 {@code response-content-type}），
 * 这里不能有副本。
 *
 * <p><b>本类只回答「这是什么字节」，不回答「准不准入」</b>：白名单、大小上限、
 * 无魔数类型（md / txt）的降级校验一律留在各调用方的策略类
 * （{@code NoteFilePolicy} / {@code AvatarFilePolicy}），因为「谁的扩展名能收」是各模块
 * 自己的业务策略（§1.1：共享层不含业务策略）。
 *
 * <p>魔数刻意用十六进制字节数组而非字符串字面量：rar / png 的魔数里含 {@code 0x1A}、
 * {@code 0x89} 这类控制字符与非 ASCII 字节，写成字符串会是一串转义符，既难读也容易抄错。
 */
public final class FileMagic {

    /** {@code %PDF-} */
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};
    /** JPEG 的 SOI 标记 {@code FF D8 FF} */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /** {@code 89 50 4E 47 0D 0A 1A 0A} */
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    /** {@code GIF8}（87a / 89a 两个版本共用这四字节） */
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38};
    /** {@code RIFF}，WebP 的容器头 */
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46};
    /** {@code WEBP}，位于 RIFF 容器偏移 8 处 */
    private static final byte[] WEBP = {0x57, 0x45, 0x42, 0x50};
    /** {@code PK\x03\x04}，zip 与新式 Office（docx / xlsx / pptx）同族 */
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    /** {@code Rar!\x1a\x07}，rar4 与 rar5 共用前 6 字节（第 7 字节起才是版本差异） */
    private static final byte[] RAR = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07};
    /** {@code 37 7A BC AF 27 1C} */
    private static final byte[] SEVEN_ZIP = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};

    private FileMagic() {
    }

    // 每个方法只认自己那一种类型。这里刻意<b>不</b>给 md / txt 提供恒真谓词——
    // 「没有魔数」与「任何字节都算通过」是两件事，后者会让校验形同虚设，
    // 那类文件的降级校验由调用方自己做（见 NoteFilePolicy#assertPlainText）。

    public static boolean pdf(byte[] head) {
        return startsWith(head, PDF);
    }

    public static boolean jpeg(byte[] head) {
        return startsWith(head, JPEG);
    }

    public static boolean png(byte[] head) {
        return startsWith(head, PNG);
    }

    public static boolean gif(byte[] head) {
        return startsWith(head, GIF);
    }

    /**
     * RIFF 头偏移 4 起的 4 字节是整个文件的长度，每次都不同，进不了魔数表，
     * 故单独比对偏移 8 处的 {@code WEBP}。
     */
    public static boolean webp(byte[] head) {
        return startsWith(head, RIFF) && startsWithAt(head, 8, WEBP);
    }

    public static boolean zip(byte[] head) {
        return startsWith(head, ZIP);
    }

    public static boolean rar(byte[] head) {
        return startsWith(head, RAR);
    }

    public static boolean sevenZip(byte[] head) {
        return startsWith(head, SEVEN_ZIP);
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        return startsWithAt(head, 0, magic);
    }

    /** 定长比对。长度不足一律判否——短的「魔数」是残缺文件，不能因为前缀对上了就放行 */
    private static boolean startsWithAt(byte[] head, int offset, byte[] magic) {
        if (head.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
