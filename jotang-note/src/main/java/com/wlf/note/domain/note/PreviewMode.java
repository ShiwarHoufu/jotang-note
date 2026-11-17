package com.wlf.note.domain.note;

import java.util.Set;

/**
 * 笔记的预览方式，由文件类型决定。
 *
 * <p>取值按<b>前端的渲染动作</b>划分，而不是按后端的交付手段划分——前端拿到的就是这个枚举，
 * 它需要知道的是「用 img、拿文本、还是只给下载按钮」。
 */
public enum PreviewMode {

    /** 光栅图：后端签发签名 URL，前端 {@code <img src>} 直出。<b>不受</b>默认域名强制下载影响 */
    IMAGE_INLINE,

    /** MD / 文本：后端 {@code /raw} 代理返回 {@code text/plain}，前端再走 marked + DOMPurify */
    TEXT_PROXY,

    /** 不进浏览器渲染，只签发下载 URL（附件形式并还原文件名）。未知类型的兜底也是它 */
    DOWNLOAD_ONLY;

    /**
     * 可能落进 {@code note_file.content_type} 的光栅图类型。
     */
    private static final Set<String> INLINE_IMAGES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /**
     * 由 {@code note_file.content_type} 推导渲染意图。
     *
     * @param contentType 服务端判定值；{@code null} 与任何未知值同等对待
     */
    public static PreviewMode of(String contentType) {
        if (contentType == null) {
            return DOWNLOAD_ONLY;
        }
        if (INLINE_IMAGES.contains(contentType)) {
            return IMAGE_INLINE;
        }
        if ("text/markdown".equals(contentType) || "text/plain".equals(contentType)) {
            return TEXT_PROXY;
        }
        return DOWNLOAD_ONLY;
    }
}
