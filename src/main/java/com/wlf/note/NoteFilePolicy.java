package com.wlf.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
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
 * 上传文件的准入判定与对象键生成。见《概要设计》§6.1。
 *
 * <p><b>这是预览型 XSS 的唯一防线。</b>§8.3 说得很直白：图片 / PDF 预览的
 * {@code Content-Type} 取自上传时写入对象元数据的服务端判定值，读取时无从覆写
 * （OSS 自 2025-01-20 起禁止签名 URL 覆盖 {@code response-content-type}），
 * 因此类型的正确性完全押在这一层上——一旦放进来一个 svg 或 html，
 * 它就会被当作图片或 PDF 内联渲染在 OSS 域名下。故采用<b>白名单</b>而非黑名单：
 * 只有明确认识的扩展名才可能通过，其余一律 42200。
 *
 * <p>判定顺序（先便宜的、后昂贵的）：文件名与大小 → 扩展名白名单 → 魔数 →
 * 仅对无魔数的 md/txt 降级做全量 UTF-8 解码。
 *
 * <p><b>关于 {@code PK\x03\x04} 这个坑</b>：docx / xlsx / pptx / zip 的魔数完全一样，
 * 光看文件头区分不了。所以 {@code content_type} 一律由<b>扩展名</b>映射，魔数只负责确认
 * 「确实属于这一族」。反过来写成「按魔数推断类型」的话，docx 会被判成 zip，
 * 而且测试还很难发现——因为两者都能通过校验。这正是本节把类型表按扩展名组织的原因。
 *
 * <p><b>关于 md / txt</b>：这类文件没有魔数可验，§6.1 要求降级为
 * 「扩展名 + UTF-8 可解码 + 无 NUL 字节」。这一步是 O(n) 全量扫描，代价只在文本类型上发生；
 * 它的目的不是防 XSS（{@code /raw} 固定返回 {@code text/plain} 且带 {@code nosniff}，
 * 本就不给浏览器解析的机会），而是把「二进制文件改个 .txt 后缀」挡在门外，
 * 免得代理接口去流式转发一堆乱码。
 */
@Slf4j
@Component
public class NoteFilePolicy {

    /** 单文件上限 100MB（§6.1），与 Nginx 的 {@code client_max_body_size}、后端 multipart 配置同源 */
    public static final long MAX_SIZE_BYTES = 100L * 1024 * 1024;

    /** 与 {@code note_file.original_name VARCHAR(255)} 对齐。utf8mb4 下 VARCHAR 单位是字符不是字节 */
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    /** 读文件头的字节数：必须 ≥ 最长的魔数（WEBP 需要 12 字节），取 16 留些余量 */
    private static final int HEAD_BYTES = 16;

    private static final int TEXT_CHUNK_BYTES = 8 * 1024;

    private static final DateTimeFormatter KEY_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    // ---- 魔数表 ----
    // 用十六进制字节数组而非字符串字面量：rar / png 的魔数里含 0x1A、0x89 这类控制字符与
    // 非 ASCII 字节，写成字符串会是一串转义符，既难读也容易抄错。

    /** {@code %PDF-} */
    private static final byte[] MAGIC_PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};
    /** JPEG 的 SOI 标记 {@code FF D8 FF} */
    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /** {@code 89 50 4E 47 0D 0A 1A 0A} */
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    /** {@code GIF8}（87a / 89a 两个版本共用这四字节） */
    private static final byte[] MAGIC_GIF = {0x47, 0x49, 0x46, 0x38};
    /** {@code RIFF}，WebP 的容器头 */
    private static final byte[] MAGIC_RIFF = {0x52, 0x49, 0x46, 0x46};
    /** {@code WEBP}，位于 RIFF 容器偏移 8 处 */
    private static final byte[] MAGIC_WEBP = {0x57, 0x45, 0x42, 0x50};
    /** {@code PK\x03\x04}，zip 与新式 Office（docx/xlsx/pptx）同族 */
    private static final byte[] MAGIC_ZIP = {0x50, 0x4B, 0x03, 0x04};
    /** {@code Rar!\x1a\x07}，rar4 与 rar5 共用前 6 字节（第 7 字节起才是版本差异） */
    private static final byte[] MAGIC_RAR = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07};
    /** {@code 37 7A BC AF 27 1C} */
    private static final byte[] MAGIC_7Z = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};

    /**
     * 扩展名 → 判定格式。键是小写扩展名（不含点），值为该类型的元信息。
     *
     * <p>用 LinkedHashMap 只为让表在源码里保持人类录入的顺序，运行时不依赖顺序。
     *
     * <p>表里只放<b>写入侧闸门</b>需要的两样东西——「类型是什么」与「凭什么认它」。
     * 预览方式不在这里：它是 {@code contentType} 的纯函数，归读取侧，
     * 由 {@link PreviewMode#of(String)} 推导（§6.1 那张表的「预览方式」列即该映射的出处）。
     * 两处都写会变成同一规则的两份副本。注意这两处<b>必须同步</b>：新增受支持类型时
     * 除了往本表加一行，还要看 {@code PreviewMode.of} 是否认得这个新的 {@code contentType}。
     */
    private static final Map<String, Format> FORMATS = buildFormats();

    /**
     * 一种受支持的文件类型，只保留写入侧闸门需要的两样东西。
     *
     * @param contentType 服务端判定值
     * @param magic       文件头判定；<b>null 表示该类型没有魔数</b>（只有 md / txt），
     *                    此时降级为 UTF-8 文本校验。空安全靠调用方判 null，而不是塞个恒真谓词——
     *                    「没有魔数」和「任何字节都算通过」是两件事，后者会让校验形同虚设
     */
    private record Format(String contentType, Predicate<byte[]> magic) {
    }

    private static Map<String, Format> buildFormats() {
        Map<String, Format> formats = new LinkedHashMap<>();

        // 无魔数，靠降级校验（见类注释）
        formats.put("md", new Format("text/markdown", null));
        formats.put("txt", new Format("text/plain", null));

        // 浏览器可内联渲染的单一格式
        formats.put("pdf", new Format("application/pdf", head -> startsWith(head, MAGIC_PDF)));
        formats.put("jpg", new Format("image/jpeg", head -> startsWith(head, MAGIC_JPEG)));
        formats.put("jpeg", new Format("image/jpeg", head -> startsWith(head, MAGIC_JPEG)));
        formats.put("png", new Format("image/png", head -> startsWith(head, MAGIC_PNG)));
        formats.put("gif", new Format("image/gif", head -> startsWith(head, MAGIC_GIF)));
        formats.put("webp", new Format("image/webp", NoteFilePolicy::isWebp));

        // 仅下载。注意四者的魔数完全相同，类型只能由扩展名区分
        formats.put("docx", new Format(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                head -> startsWith(head, MAGIC_ZIP)));
        formats.put("xlsx", new Format(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                head -> startsWith(head, MAGIC_ZIP)));
        formats.put("pptx", new Format(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                head -> startsWith(head, MAGIC_ZIP)));
        formats.put("zip", new Format("application/zip", head -> startsWith(head, MAGIC_ZIP)));
        formats.put("rar", new Format("application/vnd.rar", head -> startsWith(head, MAGIC_RAR)));
        formats.put("7z", new Format("application/x-7z-compressed", head -> startsWith(head, MAGIC_7Z)));

        return Map.copyOf(formats);
    }

    /**
     * 判定一份上传文件是否准入，并给出落库与渲染所需的元信息。
     *
     * <p>不合法一律抛 42200（{@link ErrorCode#FILE_INVALID}），具体原因写在 message 里
     * （§5.7 对该码的约定就是「扩展名 / 魔数 / 大小由 message 说明」）。
     *
     * <p><b>本方法会读取 {@code content}</b>：二进制类型只读文件头，
     * md / txt 会读到末尾（UTF-8 全量校验）。
     * 但调用方在上传前需要重新打开一个流——{@code MultipartFile#getInputStream} 可重复调用。
     *
     * @param originalName 客户端送来的原始文件名，可含路径（会被清洗掉）
     * @param size         字节数，取自 multipart；本方法不做与实际内容的交叉核对
     * @throws BusinessException 42200 文件类型 / 大小不合法；50000 读取流本身失败
     */
    public FileDecision inspect(String originalName, long size, InputStream content) {
        String safeName = sanitizeName(originalName);
        if (size <= 0) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容为空");
        }
        if (size > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件超过 " + (MAX_SIZE_BYTES >> 20) + "MB");
        }

        String extension = extensionOf(safeName);
        Format format = FORMATS.get(extension);
        if (format == null) {
            // 白名单之外：svg / html / js / exe 以及一切没列进来的类型都从这里出去
            throw new BusinessException(ErrorCode.FILE_INVALID, "不支持的文件类型：." + extension);
        }

        byte[] head = readHead(content);
        if (format.magic() == null) {
            assertPlainText(head, content, extension);
        } else if (!format.magic().test(head)) {
            // 魔数与扩展名不符：既可能是改了后缀的伪装文件，也可能是真的损坏了，对外不区分
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容与扩展名 ." + extension + " 不符");
        }

        return new FileDecision(safeName, extension, format.contentType());
    }

    /**
     * 生成 OSS 对象键 {@code notes/yyyy/MM/{uuid}.{ext}}（§6.1）。
     *
     * <p>不用原始文件名：原始名可能重名、可能含 OSS 不接受的字符，也可能带上路径。
     * 它在库里另存为 {@code note_file.original_name}，下载时再还原。
     *
     * @param extension {@link FileDecision#extension()}，已归一化为小写
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
     * {@code C:\Users\x\笔记.pdf} 都可能被送上来。这里只留最后一段，
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

    private static boolean isWebp(byte[] head) {
        // RIFF 头偏移 4 起的 4 字节是整个文件的长度，每次都不同，进不了魔数表，故单独比对偏移 8 处的 WEBP
        return startsWith(head, MAGIC_RIFF) && startsWithAt(head, 8, MAGIC_WEBP);
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
