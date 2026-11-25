package com.wlf.note.interfaces.web.dto;

import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.PreviewMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 详情出参。
 *
 * <p>本服务的表：{@code note} / {@code note_file} / {@code course} / {@code note_tag}⋈{@code tag} / {@code favorite}；
 * 上传者及其学院来自 user-service。
 *
 * @param status    {@link NoteStatus} 枚举而非裸字符串
 * @param course    复用了已发布的 {@link CourseResponse}
 * @param uploader  上传者。学院挂在这一层而不是顶层
 * @param tags      复用了 {@link TagResponse}
 * @param viewCount 已经含本次浏览。递
 * @param file      <b>详情可见时返回</b>：ONLINE 所有人，OFFLINE 仅本人/管理员；DELETED 不可达
 * @param isFavorited 如实反映 {@code favorite} 表里的关系，<b>非 ONLINE 时也照查</b>：
 */
public record NoteDetailResponse(
        Long id,
        String title,
        String summary,
        String teacher,
        NoteStatus status,
        CourseResponse course,
        UploaderResponse uploader,
        List<TagResponse> tags,
        long viewCount,
        long downloadCount,
        long favoriteCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        FileInfo file,
        boolean isFavorited
) {

    /**
     * 文件信息。
     * 与详情可见性一致：OFFLINE 时仅本人/管理员能拿到，DELETED 不可达。
     *
     * <p>不返回 {@code storageKey}：那是 OSS 对象键，属于「东西存在哪」而非「东西是什么」，
     * 露出去只会诱使前端自己拼 URL 绕过 §6.2 的鉴权。
     *
     * @param contentType 上传时由服务端按魔数判定并钉死的值，写入 OSS 对象元数据后不可覆写
     * @param previewMode <b>读取侧由 {@code contentType} 现推</b>。前端据此决定用
     *                    {@code <img>} / {@code <iframe>} / 代理接口 / 只给下载按钮
     */
    public record FileInfo(String originalName, long size, String contentType, PreviewMode previewMode) {
    }
}
