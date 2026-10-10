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
 * AI 摘要的图片准入判定。见《概要设计》§5.8、§6.8。
 *
 * <p>这是本项目第三条「扩展名 + 魔数」的准入链路（另两条：{@code NoteFilePolicy} 管笔记附件、
 * {@code AvatarFilePolicy} 管头像）。按 §6.7 的分工，<b>白名单与大小上限是调用方的策略</b>，
 * 三条各持一份；而「字节是什么类型」是同一个事实，共用 {@link FileMagic}，不复制。
 *
 * <p><b>白名单是 {@code NoteFilePolicy} 的真子集</b>：只有四种光栅图。模型的多模态输入
 * 不收任何其它格式（pdf / docx / xlsx / pptx 都不行，§6.8），所以在这里多放一种进来
 * 不是「宽容」，而是把一个注定失败的请求推给上游、白花一次计费。
 *
 * <p><b>与另两条链路不同，本策略不产出「决策记录」</b>：那两条的记录里带 {@code extension}，
 * 是为了拼 OSS 对象键（§6.1 的 {@code notes/yyyy/MM/...}、§6.7 的 {@code avatars/{userId}/...}）；
 * AI 这一趟<b>读完即弃、不生成任何对象键</b>，唯一要带出去的是「该用哪个 MIME 交给模型」。
 * 所以 {@link #inspect} 直接返回该 MIME 字符串，不为对齐形状而多造一个单字段的 record。
 */
@Slf4j
@Component
public class AiSummaryImagePolicy {

    /**
     * 摘要用的图片大小上限（§7.1）。
     *
     * <p><b>显著小于 §6.1 的 100MB</b>，两个理由叠在一起：图片要以 base64 内联进请求体，
     * 体积膨胀约 4/3；而模型侧的请求体本身有上限。上限值目前是建议值，待真实调用的费用与
     * 时延实测后再钉死。
     */
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** 读文件头的字节数：必须 ≥ 最长的魔数（WEBP 需要 12 字节），取 16 留些余量 */
    private static final int HEAD_BYTES = 16;

    /** 扩展名 → 判定格式，键是小写扩展名（不含点）。用 LinkedHashMap 只为让表在源码里保持录入顺序 */
    private static final Map<String, Format> FORMATS = buildFormats();

    private record Format(String contentType, Predicate<byte[]> magic) {
    }

    private static Map<String, Format> buildFormats() {
        Map<String, Format> formats = new LinkedHashMap<>();
        // jpg 与 jpeg 是同一类型的两个扩展名，都映射到 image/jpeg
        formats.put("jpg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("jpeg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("png", new Format("image/png", FileMagic::png));
        formats.put("gif", new Format("image/gif", FileMagic::gif));
        formats.put("webp", new Format("image/webp", FileMagic::webp));
        return Map.copyOf(formats);
    }

    /**
     * 判定一份待摘要的图片是否准入，并给出交给模型的 MIME。
     *
     * <p><b>本方法会读取 {@code content} 的文件头</b>；调用方随后要重新打开一个流去读全部字节——
     * {@code MultipartFile#getInputStream} 可重复调用，与 {@code NoteFilePolicy#inspect} 同一约定。
     *
     * <p>只看扩展名与文件头，<b>不看 multipart 自带的 {@code Content-Type}</b>：那个值由客户端决定。
     * 这里比另两条链路还多一层理由——除了防伪装，放行了什么类型就等于把它交给上游模型去解析。
     *
     * @param originalName 客户端送来的原始文件名，可含路径（会被清洗掉）
     * @param size         字节数，取自 multipart
     * @return 服务端判定的 MIME，如 {@code image/jpeg}
     * @throws BusinessException 42200 类型 / 大小不合法；50000 读取流本身失败
     */
    public String inspect(String originalName, long size, InputStream content) {
        if (size <= 0) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "图片内容为空");
        }
        if (size > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "图片超过 " + (MAX_SIZE_BYTES >> 20) + "MB，无法生成摘要");
        }

        String extension = extensionOf(sanitizeName(originalName));
        Format format = FORMATS.get(extension);
        if (format == null) {
            // pdf / docx / zip 以及一切没列进来的类型都从这里出去。这里必须挡住而不是放行：
            // 模型收不了这些格式，放过去只会白花一次计费再拿回一个上游错误（§6.8）
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "AI 摘要目前只支持 jpg / png / gif / webp 图片");
        }

        if (!format.magic().test(readHead(content))) {
            // 魔数与扩展名不符：既可能是改了后缀的伪装文件，也可能是真的损坏了，对外不区分
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "文件内容与扩展名 ." + extension + " 不符");
        }

        return format.contentType();
    }

    /**
     * 该 MIME 是不是本策略认得的图片类型。
     *
     * <p><b>给编辑场景用</b>：那时文件早已在 OSS，手里只有落库的 {@code content_type}，
     * 没有字节可验，所以只能按类型判。它同时决定了按钮能不能出现，以及请求该不该被接受——
     * 前端控可见性、后端控可行性（与 §5.4 的编辑按钮同一分工）。
     */
    public boolean supports(String contentType) {
        return contentType != null && FORMATS.values().stream()
                .anyMatch(format -> format.contentType().equals(contentType));
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
