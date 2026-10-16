package com.wlf.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FileMagic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 上传文件的准入判定与对象键生成。
 */
@Slf4j
@Component
public class NoteFilePolicy {

    /** 单文件上限 100MB，与 Nginx 的 {@code client_max_body_size}、后端 multipart 配置同源 */
    public static final long MAX_SIZE_BYTES = 100L * 1024 * 1024;

    /** 与 {@code note_file.original_name VARCHAR(255)} 对齐。utf8mb4 下 VARCHAR 单位是字符不是字节 */
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    /** 读文件头的字节数：必须 ≥ 最长的魔数（WEBP 需要 12 字节），取 16 留些余量 */
    private static final int HEAD_BYTES = 16;

    private static final int TEXT_CHUNK_BYTES = 8 * 1024;

    private static final DateTimeFormatter KEY_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    /**
     * 一种受支持的文件类型。
     *
     * @param contentType 服务端判定值
     * @param magic       文件头判定。null 表示该类型没有魔数（只有 md / txt）
     */
    private record Format(String contentType, Predicate<byte[]> magic) {
    }

    /**
     * 允许上传的文件类型白名单。
     * key是小写扩展名，value为该类型的元信息。
     *
     * <p>预览方式不在这里。由 {@link PreviewMode#of(String)} 推导。
     * 新增受支持类型时，除了往本表加一行，还要看 {@code PreviewMode.of} 是否认得这个新的 {@code contentType}。
     */
    private static final Map<String, Format> FORMATS = buildFormats();

    private static Map<String, Format> buildFormats() {
        Map<String, Format> formats = new LinkedHashMap<>();

        // 无魔数，靠降级校验
        formats.put("md", new Format("text/markdown", null));
        formats.put("txt", new Format("text/plain", null));

        // 浏览器可内联渲染的光栅图
        formats.put("jpg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("jpeg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("png", new Format("image/png", FileMagic::png));
        formats.put("gif", new Format("image/gif", FileMagic::gif));
        formats.put("webp", new Format("image/webp", FileMagic::webp));

        return Map.copyOf(formats);
    }

    /**
     * 判定待上传文件是否准入，
     * 并给出落库与渲染所需的元信息。
     *
     * @param originalName 客户端送来的原始文件名
     * @param size         字节数，取自 multipart
     * @throws BusinessException 42200 文件类型 / 大小不合法；50000 读取流本身失败
     */
    public FileDecision inspect(String originalName, long size, InputStream content) {
        // 清洗文件名
        String safeName = sanitizeName(originalName);
        // 文件大小校验
        if (size <= 0) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容为空");
        }
        if (size > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件超过 " + (MAX_SIZE_BYTES >> 20) + "MB");
        }

        // 根据扩展名对文件进行准入判断
        String extension = extensionOf(safeName);
        Format format = FORMATS.get(extension);
        if (format == null) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "不支持的文件类型：." + extension);
        }

        // 魔数校验。防止扩展名撒谎
        byte[] head = readHead(content);
        if (format.magic() == null) {
            assertPlainText(head, content, extension);
        } else if (!format.magic().test(head)) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容与扩展名 ." + extension + " 不符");
        }

        return new FileDecision(safeName, extension, format.contentType());
    }

    /**
     * 生成 OSS 对象键 {@code notes/yyyy/MM/{uuid}.{ext}}。
     */
    public String objectKey(String extension) {
        return "notes/" + LocalDate.now().format(KEY_MONTH) + "/" + UUID.randomUUID() + "." + extension;
    }

    /** 取扩展名并归一化为小写。用 {@code Locale.ROOT}：默认区域可能是土耳其语，那里 {@code I} 会被小写成 {@code ı} */
    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        // dot == 0 是 .bashrc 这类隐藏文件，它没有「扩展名」这个语义，不能把 bashrc 当类型
        if (dot <= 0 || dot == name.length() - 1) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件缺少扩展名：" + name);
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 清洗文件名：去掉路径、校验长度。
     *
     * <p>浏览器一般只送文件名，但客户端是可控的——{@code ../../etc/passwd} 或
     * {@code C:\Users\x\笔记.png} 都可能被送上来。这里只留最后一段，
     * 落库的 {@code original_name} 与下载时还原的文件名都以本结果为准。
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
        if (name.length() > MAX_ORIGINAL_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件名超过 " + MAX_ORIGINAL_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    private static byte[] readHead(InputStream content) {
        try {
            return content.readNBytes(HEAD_BYTES);
        } catch (IOException e) {
            log.error("读取上传文件头失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    /**
     * md / txt 的降级校验：整份内容必须是可解码的 UTF-8，且不含 NUL 字节。
     *
     * <p>按块流式解码而不是 {@code readAllBytes()}：100MB 的 .txt 也在允许范围内，
     * 一次性读进堆里不值当。
     *
     * <p>块边界会把一个多字节字符切开（一个 UTF-8 字符最多 4 字节），若对每块单独
     * {@code decoder.decode(bb)}（endOfInput=true）就会误判。所以这里让 decoder 保持状态、
     * 以 {@code endOfInput=false} 解码，并把它<b>没有消费</b>的尾部字节留到下一块开头拼接
     * （{@code input.position()} 之后的即未消费部分）——decoder 遇到不完整的多字节序列时
     * 正好会把位置停在序列首字节，这就是「留到下一块」能成立的原因。
     */
    private static void assertPlainText(byte[] head, InputStream content, String extension) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        // UTF-8 里每个字符至少占 1 字节，故解码出的字符数恒不超过本批字节数，不会 OVERFLOW
        CharBuffer chars = CharBuffer.allocate(TEXT_CHUNK_BYTES + HEAD_BYTES);
        byte[] chunk = new byte[TEXT_CHUNK_BYTES];
        byte[] carry = head;

        while (true) {
            int read = read(content, chunk);
            boolean eof = read < 0;

            byte[] batch = new byte[carry.length + (eof ? 0 : read)];
            System.arraycopy(carry, 0, batch, 0, carry.length);
            if (!eof) {
                System.arraycopy(chunk, 0, batch, carry.length, read);
            }

            ByteBuffer input = ByteBuffer.wrap(batch);
            chars.clear();
            CoderResult result = decoder.decode(input, chars, eof);
            if (result.isError()) {
                throw new BusinessException(ErrorCode.FILE_INVALID, "." + extension + " 必须是 UTF-8 纯文本，该文件不是");
            }
            carry = Arrays.copyOfRange(batch, input.position(), batch.length);

            chars.flip();
            while (chars.hasRemaining()) {
                // NUL 本身是合法 UTF-8（U+0000），decoder 不会拦，得单独查
                if (chars.get() == '\u0000') {
                    throw new BusinessException(ErrorCode.FILE_INVALID, "." + extension + " 内含 NUL 字节，不是纯文本");
                }
            }

            if (eof) {
                return;
            }
        }
    }

    private static int read(InputStream content, byte[] chunk) {
        try {
            return content.read(chunk);
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }
}
