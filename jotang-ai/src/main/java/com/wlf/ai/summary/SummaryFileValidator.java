package com.wlf.ai.summary;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FileMagic;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** AI 摘要的输入准入判定。 */
public final class SummaryFileValidator {

    /** 单份附件内联进请求体的上限。 */
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** 摘要正文的字符上限，按 code point 计。同为建议值，待实测 */
    public static final int MAX_TEXT_CHARS = 20000;

    /** 读之前的字节预筛。 */
    public static final long MAX_TEXT_BYTES = (long) MAX_TEXT_CHARS * 4;

    /** 读文件头的字节数：必须 ≥ 最长的魔数（WEBP 需要 12 字节），取 16 留些余量 */
    public static final int HEAD_BYTES = 16;

    private record Format(String contentType, Predicate<byte[]> magic) {
    }

    /** 允许让 AI 进行摘要的文件类型白名单 */
    private static final Map<String, Format> FORMATS = buildFormats();

    private static Map<String, Format> buildFormats() {
        Map<String, Format> formats = new LinkedHashMap<>();

        // 无魔数的文本文件
        formats.put("md", new Format("text/markdown", null));
        formats.put("txt", new Format("text/plain", null));

        // 光栅图
        formats.put("jpg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("jpeg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("png", new Format("image/png", FileMagic::png));
        formats.put("gif", new Format("image/gif", FileMagic::gif));
        formats.put("webp", new Format("image/webp", FileMagic::webp));

        return Map.copyOf(formats);
    }

    /**
     * 判定待摘要的上传文件是否准入，并给出交给模型的 MIME。
     *
     * @param originalName 客户端送来的原始文件名，可含路径（会被清洗掉）
     * @param size         字节数，取自 multipart
     * @param head         文件头，最多 {@link #HEAD_BYTES} 字节；文本类型不看
     * @return 服务端判定的 MIME，如 {@code image/jpeg} 或 {@code text/markdown}
     * @throws BusinessException 42200 类型 / 大小不合法
     */
    public static String inspect(String originalName, long size, byte[] head) {
        if (size <= 0) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容为空");
        }

        String extension = extensionOf(sanitizeName(originalName));
        Format format = FORMATS.get(extension);
        if (format == null) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "AI 摘要目前只支持 jpg / png / gif / webp 图片与 md / txt 文本");
        }

        requireWithinLimit(format.contentType(), size);

        if (format.magic() == null) {
            return format.contentType();
        }
        if (!format.magic().test(head)) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "文件内容与扩展名 ." + extension + " 不符");
        }
        return format.contentType();
    }

    /**
     * 编辑场景的判据：附件早已在 OSS，手里只有落库的 {@code content_type}，没有字节可验。
     *
     * @throws BusinessException 42200 该类型不支持生成摘要（落库值为 {@code null} 也走这里）
     */
    public static void requireSummarizable(String contentType) {
        boolean known = contentType != null && FORMATS.values().stream()
                .anyMatch(format -> format.contentType().equals(contentType));
        if (!known) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "这篇笔记的附件类型不支持生成摘要");
        }
    }

    public static boolean isText(String contentType) {
        return FORMATS.values().stream()
                .anyMatch(format -> format.magic() == null && format.contentType().equals(contentType));
    }

    /** 大小上限：图片按 {@link #MAX_SIZE_BYTES}，文本另有更小的 {@link #MAX_TEXT_BYTES}。 */
    public static void requireWithinLimit(String contentType, Long size) {
        if (size == null) {
            return;
        }
        if (isText(contentType)) {
            if (size > MAX_TEXT_BYTES) {
                throw textTooLong();
            }
        } else if (size > MAX_SIZE_BYTES) {
            throw imageTooLarge();
        }
    }

    public static void requireTextWithinLimit(String text) {
        if (text.codePointCount(0, text.length()) > MAX_TEXT_CHARS) {
            throw textTooLong();
        }
    }

    private static BusinessException textTooLong() {
        return new BusinessException(ErrorCode.FILE_INVALID,
                "笔记正文超过 " + MAX_TEXT_CHARS + " 字，无法生成摘要");
    }

    private static BusinessException imageTooLarge() {
        return new BusinessException(ErrorCode.FILE_INVALID,
                "图片超过 " + (MAX_SIZE_BYTES >> 20) + "MB，无法生成摘要");
    }

    private static String sanitizeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "缺少文件名");
        }
        String name = originalName.trim();
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        if (name.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件名不合法");
        }
        return name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        // dot == 0 是 .bashrc 这类隐藏文件，它没有「扩展名」这个语义
        if (dot <= 0 || dot == name.length() - 1) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件缺少扩展名：" + name);
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private SummaryFileValidator() {
    }
}
