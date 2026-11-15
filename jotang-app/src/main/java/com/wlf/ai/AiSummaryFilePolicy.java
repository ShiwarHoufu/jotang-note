package com.wlf.ai;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FileMagic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * AI 摘要的输入准入判定。
 */
@Slf4j
@Component
public class AiSummaryFilePolicy {

    /** 单份附件内联进请求体的上限。*/
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** 摘要正文的字符上限，按 code point 计。同为建议值，待实测 */
    public static final int MAX_TEXT_CHARS = 20000;

    /** 读之前的字节预筛。*/
    public static final long MAX_TEXT_BYTES = (long) MAX_TEXT_CHARS * 4;

    /** 读文件头的字节数：必须 ≥ 最长的魔数（WEBP 需要 12 字节），取 16 留些余量 */
    private static final int HEAD_BYTES = 16;

    /** 一种受支持的类型 */
    private record Format(String contentType, Predicate<byte[]> magic) {
    }

    /** 允许让 AI 进行摘要的文件类型白名单*/
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
     * 判定 待摘要 的上传文件是否准入
     * 并给出交给模型的 MIME。
     *
     * @param originalName 客户端送来的原始文件名，可含路径（会被清洗掉）
     * @param size         字节数，取自 multipart
     * @return 服务端判定的 MIME，如 {@code image/jpeg} 或 {@code text/markdown}
     * @throws BusinessException 42200 类型 / 大小不合法；50000 读取流本身失败
     */
    public String inspect(String originalName, long size, InputStream content) {
        if (size <= 0) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容为空");
        }

        // 文件准入类型校验
        String extension = extensionOf(sanitizeName(originalName));
        Format format = FORMATS.get(extension);
        if (format == null) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "AI 摘要目前只支持 jpg / png / gif / webp 图片与 md / txt 文本");
        }

        // 检查大小是否超过上限（不同文件类型限制不一样）
        requireWithinLimit(format.contentType(), size);

        if (format.magic() == null) {
            return format.contentType();
        }
        // 魔数与扩展名不符
        if (!format.magic().test(readHead(content))) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "文件内容与扩展名 ." + extension + " 不符");
        }
        return format.contentType();
    }

    /**
     * 编辑场景的判据：
     * 那时文件早已在 OSS，手里只有落库的 {@code content_type}，没有字节可验，所以只能按类型判。
     *
     * @throws BusinessException 42200 该类型不支持生成摘要（落库值为 {@code null} 也走这里，不是 NPE）
     */
    public void requireSummarizable(String contentType) {
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

    /**
     * 大小上限校验：图片按 {@link #MAX_SIZE_BYTES}，文本另有更小的 {@link #MAX_TEXT_BYTES}。
     */
    public void requireWithinLimit(String contentType, Long size) {
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


    static BusinessException textTooLong() {
        return new BusinessException(ErrorCode.FILE_INVALID,
                "笔记正文超过 " + MAX_TEXT_CHARS + " 字，无法生成摘要");
    }

    static BusinessException imageTooLarge() {
        return new BusinessException(ErrorCode.FILE_INVALID,
                "图片超过 " + (MAX_SIZE_BYTES >> 20) + "MB，无法生成摘要");
    }


    public static void requireTextWithinLimit(String text) {
        if (text.codePointCount(0, text.length()) > MAX_TEXT_CHARS) {
            throw textTooLong();
        }
    }

    /**
     * 清洗文件名并取扩展名。
     */
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

    /** 取扩展名并归一化为小写 */
    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        // dot == 0 是 .bashrc 这类隐藏文件，它没有「扩展名」这个语义
        if (dot <= 0 || dot == name.length() - 1) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件缺少扩展名：" + name);
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readHead(InputStream content) {
        try {
            return content.readNBytes(HEAD_BYTES);
        } catch (IOException e) {
            log.error("读取待摘要图片的文件头失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取图片失败");
        }
    }
}
