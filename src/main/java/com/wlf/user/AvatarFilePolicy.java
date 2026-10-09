package com.wlf.user;

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
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 头像文件的准入判定与对象键生成。见《概要设计》§6.7。
 *
 * <p><b>为什么不能复用 {@code NoteFilePolicy}</b>：两者看着都是「扩展名 + 魔数」，
 * 但白名单与上限不同（头像 3 种 / 2MB，笔记 15 种 / 100MB），对象键的拼法也不同
 * （{@code avatars/{userId}/{uuid}.{ext}} vs {@code notes/yyyy/MM/{uuid}.{ext}}）。§1.1 明说
 * 白名单与对象键属于<b>调用方</b>的策略，note 与 user 各持一份。共用的只有「字节是什么类型」
 * 这层事实，已抽到 {@link FileMagic}。
 *
 * <p><b>为什么白名单必须严格执行</b>：头像桶是<b>公共读</b>的，对象会在 OSS 域名下被内联渲染。
 * 一旦放进来 svg 或 html，就是一个存储型 XSS（§8.3、§6.7）。故只认 jpg / jpeg / png / webp
 * 四种扩展名，且要求文件头与扩展名一致——改后缀的伪装文件过不了魔数。
 */
@Slf4j
@Component
public class AvatarFilePolicy {

    /** 单文件上限 2MB（§5.2、§6.7）。头像经 ECS 中转上传，与大文件的 100MB 是两回事 */
    public static final long MAX_SIZE_BYTES = 2L * 1024 * 1024;

    /** 读文件头的字节数：必须 ≥ 最长的魔数（WEBP 需要 12 字节），取 16 留些余量 */
    private static final int HEAD_BYTES = 16;

    /**
     * 扩展名 → 判定格式。键是小写扩展名（不含点），值为该类型的元信息。
     *
     * <p>用 {@link LinkedHashMap} 只为让表在源码里保持人类录入的顺序，运行时不依赖顺序。
     */
    private static final Map<String, Format> FORMATS = buildFormats();

    private record Format(String contentType, Predicate<byte[]> magic) {
    }

    private static Map<String, Format> buildFormats() {
        Map<String, Format> formats = new LinkedHashMap<>();
        // jpg 与 jpeg 是同一个类型的两个扩展名，都映射到 image/jpeg
        formats.put("jpg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("jpeg", new Format("image/jpeg", FileMagic::jpeg));
        formats.put("png", new Format("image/png", FileMagic::png));
        formats.put("webp", new Format("image/webp", FileMagic::webp));
        return Map.copyOf(formats);
    }

    /**
     * 判定一份头像文件是否准入。
     *
     * <p><b>本方法会读取 {@code content} 的文件头</b>；调用方在上传前需重新打开一个流——
     * {@code MultipartFile#getInputStream} 可重复调用。
     *
     * <p>判定只看扩展名与文件头，<b>不看 multipart 自带的 {@code Content-Type}</b>：
     * 那个值由客户端决定，正是 §6.7 要求「以服务端判定为准」的原因。
     *
     * @param originalName 客户端送来的原始文件名，可含路径（会被清洗掉）
     * @param size         字节数，取自 multipart
     * @throws BusinessException 42200 类型 / 大小不合法；50000 读取流本身失败
     */
    public AvatarDecision inspect(String originalName, long size, InputStream content) {
        if (size <= 0) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "头像文件为空");
        }
        if (size > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "头像超过 " + (MAX_SIZE_BYTES >> 20) + "MB");
        }

        String extension = extensionOf(sanitizeName(originalName));
        Format format = FORMATS.get(extension);
        if (format == null) {
            // svg / html / gif / 一切没列进来的类型都从这里出去
            throw new BusinessException(ErrorCode.FILE_INVALID, "头像仅支持 jpg / png / webp");
        }

        if (!format.magic().test(readHead(content))) {
            // 魔数与扩展名不符：既可能是改了后缀的伪装文件，也可能是真的损坏了，对外不区分
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容与扩展名 ." + extension + " 不符");
        }

        return new AvatarDecision(extension, format.contentType());
    }

    /**
     * 生成 OSS 对象键 {@code avatars/{userId}/{uuid}.{ext}}（§6.7）。
     *
     * <p><b>键是版本化的</b>：每次上传都换一个新的 uuid，旧对象不覆盖。这样头像桶可以放心
     * 配 {@code immutable} 长缓存而不会串图——浏览器缓存的旧 URL 指向一个永不改变的对象。
     *
     * <p>用 {@code userId} 做一级目录而非平铺：便于按用户排查，也让「这个 key 属于谁」
     * 一眼可读（它同时出现在公网 URL 里，属于公开信息）。
     */
    public String objectKey(Long userId, String extension) {
        return "avatars/" + userId + "/" + UUID.randomUUID() + "." + extension;
    }

    /**
     * 清洗文件名并取扩展名。头像是「重命名后上传」，原始文件名不落库，
     * 这里要的只有扩展名——但仍须先剥掉路径，免得 {@code ../x.png} 之类的输入影响判定。
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
            log.error("读取上传头像失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }
}
