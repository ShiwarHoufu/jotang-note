package com.wlf.note;

/**
 * 笔记的预览方式，由扩展名决定（§6.1 的「预览方式」列）。见《概要设计》§6.2。
 *
 * <p>取值按<b>前端的渲染动作</b>划分，而不是按后端的交付手段划分——前端拿到的就是这个枚举，
 * 它需要知道的是「用 img、用 iframe、拿文本、还是只给下载按钮」。
 *
 * <p>刻意不叫 {@code PDF_SIGNED_URL} 这类名字：PDF 走签名 URL 只是<b>当前</b>的交付手段，
 * 《概要设计》§10 还挂着一个未决问题（OSS 默认域名对该类型强制下载，默认域名的
 * {@code Content-Disposition: attachment} 压过签名 URL 里写的 {@code inline}）。
 * 若最终改判为自定义域名或 pdf.js，变的只是交付手段，前端「用 iframe 内联展示」这条
 * 渲染意图不变，因此本枚举无需改动。
 */
public enum PreviewMode {

    /** 光栅图：后端签发签名 URL，前端 {@code <img src>} 直出。<b>不受</b>默认域名强制下载影响 */
    IMAGE_INLINE,

    /** PDF：后端签发签名 URL，前端 {@code <iframe>} 内联展示（交付手段见 §10 待定） */
    PDF_INLINE,

    /** MD / 文本：后端 {@code /raw} 代理返回 {@code text/plain}，前端再走 marked + DOMPurify */
    TEXT_PROXY,

    /** Office / 压缩包：不进浏览器渲染，只签发下载 URL（附件形式并还原文件名） */
    DOWNLOAD_ONLY
}
