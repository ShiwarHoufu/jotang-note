package com.wlf.favorite.dto;

import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.note.NoteStatus;
import com.wlf.note.dto.UploaderResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收藏项出参：正常项，或「已下架 / 已删除」占位。见《概要设计》§5.5、§6.5、§4.1。
 *
 * <p><b>占位项保留 {@code title} 与 {@code course}，其余为空</b>：
 * 用户的收藏在笔记被下架 / 删除之后仍然存在（§6.5 不解除收藏关系），
 * 而一个只剩「笔记已删除」五个字的条目，用户认不出自己当初收藏的是什么。
 * 所以保留的是<b>身份</b>（这篇笔记是什么课、叫什么名字），隐去的是<b>内容</b>
 * （谁传的、有什么标签、多少人看过、什么时候改的）。
 *
 * <p>这与详情页「非 ONLINE 时 {@code file} 整体置空」是同一个手法，只是这一层还留着身份。
 * <b>不含 {@code file} 字段</b>，所以 §4.1 那条「已下架笔记的文件名不该再从任何路径露出去」
 * 不受影响——本接口根本不给文件信息。
 *
 * <p><b>为什么是扁平结构而不是嵌一个可空的 {@code note} 对象</b>：占位项需要保留标题与课程，
 * 嵌套的话「内容块」就不是整体可空的了，前端反而要逐字段判空。扁平 + 一组明确的空值
 * 让「是不是占位项」只需要看 {@code status} 一个字段。
 *
 * <p><b>不含 {@code isFavorited}</b>：本列表里的每一项按定义都已收藏，恒等于 true 的字段不给
 * （与 {@code NoteListItemResponse} 省略 {@code status} 同一理由）。
 * 它也不含 {@code favorite.id}：关系行的 id 是存储细节，对外只谈「哪篇笔记」。
 *
 * @param status      {@link NoteStatus} 枚举而非裸字符串。这是唯一会把 {@code OFFLINE} /
 *                    {@code DELETED} 当正常业务值返回的两个接口之一（另一个是详情页，§4.1），
 *                    前端据此渲染「已下架」/「已删除」占位并隐藏预览下载入口
 * @param favoritedAt 收藏时刻，也是本列表的排序键。与 {@code updatedAt}（笔记最后被编辑的时间）
 *                    是两回事——把一个旧笔记刚收藏进来，这两个值会差很远，别读混
 * @param uploader    <b>占位项为 {@code null}</b>。已下架笔记的上传者不该再从这条路径露出去；
 *                    正常项里的 {@code avatarUrl} 是拼好的完整公网地址（§6.7）
 * @param tags        <b>占位项为空数组而不是 null</b>，前端可以无条件遍历。
 *                    单篇上限 10 个（{@code TagService} 的护栏），全量返回由前端决定展示几个
 * @param viewCount   以下四个计数与 {@code updatedAt} 在<b>占位项上都是 {@code null}</b>。
 *                    故用包装类型 {@code Long} 而不是详情 / 列表里那种 {@code long}：
 *                    {@code 0} 是一个会被前端当真值的合法计数，表达不了「不可用」
 * @param updatedAt   笔记最后被编辑的时间（§3.3），不是这一行最后被写的时间
 */
public record FavoriteItemResponse(
        Long noteId,
        NoteStatus status,
        LocalDateTime favoritedAt,
        String title,
        CourseResponse course,
        UploaderResponse uploader,
        List<TagResponse> tags,
        Long viewCount,
        Long downloadCount,
        Long favoriteCount,
        LocalDateTime updatedAt
) {
}
