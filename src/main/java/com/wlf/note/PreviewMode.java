package com.wlf.note;

import java.util.Set;

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
    DOWNLOAD_ONLY;

    /**
     * 可能落进 {@code note_file.content_type} 的光栅图类型。
     *
     * <p>逐项列举而<b>不是</b>用 {@code image/} 前缀匹配：前缀会顺手把 {@code image/svg+xml}
     * 也判成内联，而 svg 正是 §6.1 / §8.3 点名要拒的可注入类型。它当然进不了库
     * （写入侧闸门是唯一入口），但这里逐项列举等于再上一道保险，且与写入侧同为白名单语义。
     */
    private static final Set<String> INLINE_IMAGES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    /**
     * 由 {@code note_file.content_type} 推导渲染意图。
     *
     * <p><b>为什么输入是 content_type 而不是扩展名</b>：扩展名没有落库——{@code note_file}
     * 只存 {@code storage_key} 与 {@code original_name}，要从前者尾段抠回来既绕又脆；
     * 而 {@code content_type} 是上传时由 {@link NoteFilePolicy} 判定并写入对象元数据的服务端值
     * （§6.2：读取时不再覆写，故它即是最终值），读取侧手上正好有它。
     *
     * <p><b>为什么不把它当第 4 个字段落库</b>：预览方式是 {@code content_type} 的纯函数，
     * 而 {@code content_type} 已在入口钉死；落库只是把函数值再抄一份，换不到什么，
     * 反倒多一处可能与 {@code content_type} 不一致的地方。
     *
     * <p><b>因此本方法与 {@link NoteFilePolicy} 的类型白名单是一对必须同步的规则</b>，
     * 故刻意做成对 {@code content_type} 的<b>全函数</b>：不抛异常、不返回 null，
     * 认不出来的一律落到 {@link #DOWNLOAD_ONLY}——失败方向是「少一个内联预览」，
     * 而不是「放行了不该内联的类型」。新增受支持类型时若漏改这里，后果同样只是降级为仅下载，
     * 不构成安全洞。
     *
     * <p><b>不要改成「抠出扩展名再查 NoteFilePolicy 的 FORMATS」</b>：那张表是写入侧的准入闸门，
     * 拿它做读取侧推导，会在将来从白名单里摘掉某个类型时让历史行读不出来（{@code FORMATS.get}
     * 返 null）；读取侧本就不该执行写入侧的准入策略。
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
        if ("application/pdf".equals(contentType)) {
            return PDF_INLINE;
        }
        if ("text/markdown".equals(contentType) || "text/plain".equals(contentType)) {
            return TEXT_PROXY;
        }
        return DOWNLOAD_ONLY;
    }
}
