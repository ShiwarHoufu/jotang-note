package com.wlf.note;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PreviewMode#of(String)} 的单元测试。
 *
 * <p>这张映射是读取侧从 {@code note_file.content_type} 反推渲染意图的<b>唯一</b>依据（§6.2）。
 * 用例做两件事：<b>逐项锁住 13 个可能入库的 {@code content_type} 各自的去向</b>
 * （与 §6.1 那张表的「预览方式」列一一对照），以及<b>锁住兜底方向</b>——
 * 认不出来时必须落到 {@link PreviewMode#DOWNLOAD_ONLY}，那是唯一失败得安全的取值。
 *
 * <p>另有一半闭环在 {@link NoteFilePolicyTest}：那边验「写入侧白名单真的判出这些
 * {@code content_type}」，这边验「这些值都认得」。两边同时绿，才说明两张表同步。
 */
class PreviewModeTest {

    // ==================== 与 §6.1 类型表逐项对照 ====================

    /** 四种光栅图走签名 URL + {@code <img>} 直出，不受默认域名强制下载影响（§6.2） */
    @Test
    void rasterImagesAreInline() {
        assertThat(PreviewMode.of("image/jpeg")).isEqualTo(PreviewMode.IMAGE_INLINE);
        assertThat(PreviewMode.of("image/png")).isEqualTo(PreviewMode.IMAGE_INLINE);
        assertThat(PreviewMode.of("image/gif")).isEqualTo(PreviewMode.IMAGE_INLINE);
        assertThat(PreviewMode.of("image/webp")).isEqualTo(PreviewMode.IMAGE_INLINE);
    }

    @Test
    void pdfIsInline() {
        assertThat(PreviewMode.of("application/pdf")).isEqualTo(PreviewMode.PDF_INLINE);
    }

    @Test
    void textTypesGoThroughTheBackendProxy() {
        assertThat(PreviewMode.of("text/markdown")).isEqualTo(PreviewMode.TEXT_PROXY);
        assertThat(PreviewMode.of("text/plain")).isEqualTo(PreviewMode.TEXT_PROXY);
    }

    /** Office 三种与压缩包三种都只下载。它们的 {@code content_type} 互不相同，映射也各走各的 */
    @Test
    void officeAndArchiveTypesAreDownloadOnly() {
        assertThat(PreviewMode.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("application/zip")).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("application/vnd.rar")).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("application/x-7z-compressed")).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
    }

    // ==================== 兜底方向 ====================

    /**
     * 认不出来的一律落地成 DOWNLOAD_ONLY。这不是随手取的默认值，而是刻意选的失败方向：
     * 前端最多少一个内联预览，绝不会把不该内联的类型塞进 {@code <img>} / {@code <iframe>}。
     *
     * <p>顺带覆盖两处边界——{@code null}（列是 NOT NULL，出现即为数据不一致）与空串；
     * 以及「写入侧加了新类型、这里忘了同步」的后果：落到 DOWNLOAD_ONLY，功能降级而非开口子。
     */
    @Test
    void unknownContentTypeFallsBackToDownloadOnly() {
        assertThat(PreviewMode.of(null)).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("")).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("application/octet-stream")).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("text/html")).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
        assertThat(PreviewMode.of("image/avif")).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
    }

    /**
     * svg 是 §6.1 / §8.3 点名要拒的可注入类型，它进不了库（写入侧闸门是唯一入口）。
     * 映射表刻意用逐项列举而不是 {@code image/} 前缀匹配，就是为了在这一层也堵住它——
     * 万一上游哪天漏了一个，这里也不会顺手把它判成内联。
     */
    @Test
    void svgIsNotTreatedAsAnInlineImage() {
        assertThat(PreviewMode.of("image/svg+xml")).isEqualTo(PreviewMode.DOWNLOAD_ONLY);
    }
}
