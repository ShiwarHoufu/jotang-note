package com.wlf.note.dto;

import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.note.NoteStatus;
import com.wlf.note.PreviewMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 详情出参。见《概要设计》§5.4、§4.1、§6.2。
 *
 * <p>一个接口吃七张表：{@code note} / {@code note_file} / {@code course} / {@code user} /
 * {@code college} / {@code note_tag}⋈{@code tag} / {@code favorite}。
 *
 * <p><b>非 ONLINE 时本对象仍然返回，不是 40301。</b>§4.1 的表把「详情页」与
 * 「预览/下载/收藏」分成了两列：已下架的笔记在详情页是「提示已下架」，在预览/下载/收藏才是禁止。
 * 所以详情是唯一会把 {@code OFFLINE} 暴露给前端的接口，{@link #status()} 原样返回，
 * 由前端渲染提示条。
 *
 * @param status    {@link NoteStatus} 枚举而非裸字符串
 * @param course    复用了 §5.3 已发布的 {@link CourseResponse}，而不是在 note 侧另定一个
 *                  {@code {id, name}}。课程 / 标签的形状是 catalog 模块对外承诺的契约，
 *                  详情页把同一个东西再声明一遍，就成了「同一规则的两份副本」
 * @param uploader  上传者。学院挂在这一层而不是顶层，因为它<b>不是笔记的属性</b>——
 *                  {@code note} 表不存学院，学院读的是上传者的 {@code user.college_id}（决策 D8，§3.3）。
 *                  放在这里，等于把「改学院会回溯改变该用户全部历史笔记的归属」这个事实写进了契约
 * @param tags      复用了 {@link TagResponse}，理由同 {@code course}
 * @param viewCount 已经含本次浏览。递增走 §3.3 的原子
 *                  {@code UPDATE note SET view_count = view_count + 1 WHERE id = ? AND status = 'ONLINE'}，
 *                  WHERE 子句自己承担了「仅 ONLINE 计数」的守卫，随后再查主干自然拿到新值
 * @param file      <b>非 ONLINE 时为 {@code null}</b>。ONLINE 笔记必有且只有一个文件
 * @param isFavorited 如实反映 {@code favorite} 表里的关系，<b>非 ONLINE 时也照查</b>：
 */
public record NoteDetailResponse(
        Long id,
        String title,
        String summary,
        String teacher,
        NoteStatus status,
        CourseResponse course,
        Uploader uploader,
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
     * 上传者。{@code avatarUrl} 是<b>拼好的完整公网 URL</b>，不是 OSS 对象键——
     * §5.4 的统一约定：只要接口展示上传者信息就返回可直接 {@code <img src>} 的地址，
     * 前端无需二次请求签名或走代理。对象键留在 {@code user.avatar} 里不外露。
     *
     * @param collegeName 上传者的学院，前端拿它渲染「计算机学院 · 离散数学」
     */
    public record Uploader(Long id, String nickname, String avatarUrl, String collegeName) {
    }

    /**
     * 文件信息。非 ONLINE 时整个 {@link NoteDetailResponse#file()} 为 null，本记录不出现。
     *
     * <p>不返回 {@code storageKey}：那是 OSS 对象键，属于「东西存在哪」而非「东西是什么」，
     * 露出去只会诱使前端自己拼 URL 绕过 §6.2 的鉴权。
     *
     * @param contentType 上传时由服务端按魔数判定并钉死的值，写入 OSS 对象元数据后不可覆写（§6.2）
     * @param previewMode <b>读取侧由 {@code contentType} 现推</b>，不是从库里读的
     *                    （{@code note_file} 没有这一列）：它是 {@code contentType} 的纯函数，
     *                    由 {@link PreviewMode#of(String)} 算出。前端据此决定用
     *                    {@code <img>} / {@code <iframe>} / 代理接口 / 只给下载按钮
     */
    public record FileInfo(String originalName, long size, String contentType, PreviewMode previewMode) {
    }
}
