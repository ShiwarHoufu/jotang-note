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
 * AI 摘要的输入准入判定。见《概要设计》§5.8。
 *
 * <p>这是本项目第三条「扩展名 + 魔数」的准入链路（另两条：{@code NoteFilePolicy} 管笔记附件、
 * {@code AvatarFilePolicy} 管头像）。按 §6.7 的分工，<b>白名单与大小上限是调用方的策略</b>，
 * 三条各持一份；而「字节是什么类型」是同一个事实，共用 {@link FileMagic}，不复制。
 *
 * <p>白名单分两档：<b>光栅图</b>走模型的多模态输入，<b>纯文本</b>（md / txt）走纯文本调用。
 * 图片那档仍是 {@code NoteFilePolicy} 的真子集——模型的多模态输入不收任何其它格式（§6.8），
 * 多放一种进来不是「宽容」，而是把一个注定失败的请求推给上游、白花一次计费。
 *
 * <p><b>本策略不产出「决策记录」</b>：它读完即弃、不生成任何对象键（对比之下 {@code NoteFilePolicy}
 * 的记录带 {@code extension} 是为了拼 OSS 对象键）。要带出去的是「该用哪个 MIME 交给模型」，
 * 而走多模态还是走纯文本由那个 MIME 自己决定（{@link #isText}），不必再存一份。
 */
@Slf4j
@Component
public class AiSummaryFilePolicy {

    /**
     * 单份附件内联进请求体的上限（§6.1）。
     *
     * <p><b>显著小于 §6.1 的 100MB</b>，两个理由叠在一起：图片要以 base64 内联进请求体，
     * 体积膨胀约 4/3；而模型侧的请求体本身有上限。上限值目前是建议值，待真实调用的费用与
     * 时延实测后再钉死。
     */
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** 摘要正文的字符上限（§6.1），按 code point 计。同为建议值，待实测 */
    public static final int MAX_TEXT_CHARS = 20000;

    /**
     * 读之前的字节预筛。
     *
     * <p>UTF-8 每个 code point 占 1–4 字节，故「字节数 ≤ 4 × 字符上限」是后者成立的<b>必要条件</b>：
     * 先拦它<b>永不误拒</b>，但它<b>永远不够</b>——八万字节的纯 ASCII 就是八万个字符。它唯一的用处是
     * 不把大对象读进堆，真正的闸门是解码之后的 code point 计数。两道都删不得。
     */
    public static final long MAX_TEXT_BYTES = (long) MAX_TEXT_CHARS * 4;

    /** 读文件头的字节数：必须 ≥ 最长的魔数（WEBP 需要 12 字节），取 16 留些余量 */
    private static final int HEAD_BYTES = 16;

    /** 扩展名 → 判定格式，键是小写扩展名（不含点）。用 LinkedHashMap 只为让表在源码里保持录入顺序 */
    private static final Map<String, Format> FORMATS = buildFormats();

    /**
     * 一种受支持的类型。
     *
     * @param magic 文件头判定；<b>null 表示该类型没有魔数</b>，即 md / txt 这两档纯文本。
     *              「没有魔数」与「任何字节都算通过」是两件事，后者会让校验形同虚设，
     *              所以用 null 而不是塞个恒真谓词，空安全由调用方判 null
     */
    private record Format(String contentType, Predicate<byte[]> magic) {
    }

    private static Map<String, Format> buildFormats() {
        Map<String, Format> formats = new LinkedHashMap<>();

        // 无魔数，也不在这里做 UTF-8 校验（理由见 AiService#decodeText）
        formats.put("md", new Format("text/markdown", null));
        formats.put("txt", new Format("text/plain", null));

        // jpg 与 jpeg 是同一类型的两个扩展名，都映射到 image/jpeg
        formats.put("jpg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("jpeg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("png", new Format("image/png", FileMagic::png));
        formats.put("gif", new Format("image/gif", FileMagic::gif));
        formats.put("webp", new Format("image/webp", FileMagic::webp));

        return Map.copyOf(formats);
    }

    /**
     * 判定一份待摘要的上传文件是否准入，并给出交给模型的 MIME。
     *
     * <p><b>只有图片会读 {@code content} 的文件头</b>（文本那档不看字节）；调用方随后要重新打开
     * 一个流去读全部字节——{@code MultipartFile#getInputStream} 可重复调用，与
     * {@code NoteFilePolicy#inspect} 同一约定。
     *
     * <p>只看扩展名与文件头，<b>不看 multipart 自带的 {@code Content-Type}</b>：那个值由客户端决定。
     * 这里比另两条链路还多一层理由——除了防伪装，放行了什么类型就等于把它交给上游模型去解析。
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

        // 先解析扩展名：要知道是哪一档，才挑得出该用哪个上限
        String extension = extensionOf(sanitizeName(originalName));
        Format format = FORMATS.get(extension);
        if (format == null) {
            // svg / html，以及 pdf / office 这些 note 不收、模型也读不了的类型都从这里出去
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "AI 摘要目前只支持 jpg / png / gif / webp 图片与 md / txt 文本");
        }

        requireWithinLimit(format.contentType(), size);

        if (format.magic() == null) {
            return format.contentType();
        }
        if (!format.magic().test(readHead(content))) {
            // 魔数与扩展名不符：既可能是改了后缀的伪装文件，也可能是真的损坏了，对外不区分
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "文件内容与扩展名 ." + extension + " 不符");
        }

        return format.contentType();
    }

    /**
     * 编辑场景的判据：那时文件早已在 OSS，手里只有落库的 {@code content_type}，没有字节可验，
     * 所以只能按类型判。它同时决定了按钮能不能出现，以及请求该不该被接受——前端控可见性、
     * 后端控可行性（与 §5.4 的编辑按钮同一分工）。
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

    /**
     * 该 MIME 是不是走纯文本调用的一档。
     *
     * <p>判据是 {@link #FORMATS} 里那一行有没有魔数——有魔数的一律按二进制交给模型（图片），
     * 无魔数的才是能直读的文本。表是唯一真源，不为「哪些算文本」另存一份清单：
     * 两份的话，加一种文本类型时漏改其中一份，这里就会把一个 md 当成图片去读文件头。
     */
    public static boolean isText(String contentType) {
        return FORMATS.values().stream()
                .anyMatch(format -> format.magic() == null && format.contentType().equals(contentType));
    }

    /**
     * 大小上限：图片按 {@link #MAX_SIZE_BYTES}，文本另有更小的 {@link #MAX_TEXT_BYTES}。
     *
     * <p>编辑场景在 {@link com.wlf.storage.StorageService#open} <b>之前</b>调它：
     * {@code StorageService} 只有「存 / 读 / 签 / 删」四件原语、没有 stat，不先拦就会把一个
     * 上百 MB 的对象先整个读进堆里再被拒掉。
     *
     * @param size 字节数；{@code null} 是防御（列本身是 {@code NOT NULL}，只可能是投影没映射上），
     *             那时放它过去、由调用方的有界读兜住，而不是在这里拒
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

    /** 文本超限。与 {@link #requireTextWithinLimit} 报同一句话：同一个规则的两道闸，不该有两种说法 */
    static BusinessException textTooLong() {
        return new BusinessException(ErrorCode.FILE_INVALID,
                "笔记正文超过 " + MAX_TEXT_CHARS + " 字，无法生成摘要");
    }

    static BusinessException imageTooLarge() {
        return new BusinessException(ErrorCode.FILE_INVALID,
                "图片超过 " + (MAX_SIZE_BYTES >> 20) + "MB，无法生成摘要");
    }

    /**
     * 解码后的那道闸：正文按 code point 数超了就拒。字节预筛过不了这一关的补漏（见
     * {@link #MAX_TEXT_BYTES}），按 <b>code point</b> 而不是 {@code String#length} 计，
     * 免得一个 emoji 被算成两个字。
     */
    public static void requireTextWithinLimit(String text) {
        if (text.codePointCount(0, text.length()) > MAX_TEXT_CHARS) {
            throw textTooLong();
        }
    }

    /**
     * 清洗文件名并取扩展名。AI 不落库、不拼对象键，要的只有扩展名——
     * 但仍须先剥掉路径，免得 {@code ../x.png} 之类的输入影响判定。
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

    /** 取扩展名并归一化为小写。用 {@code Locale.ROOT}：默认区域可能是土耳其语，那里 {@code I} 会被小写成 {@code ı} */
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
