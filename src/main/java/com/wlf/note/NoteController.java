package com.wlf.note;

import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.common.PageResponse;
import com.wlf.note.dto.NoteCreatedResponse;
import com.wlf.note.dto.NoteDetailResponse;
import com.wlf.note.dto.NoteListItemResponse;
import com.wlf.note.dto.NoteListQuery;
import com.wlf.note.dto.NoteUploadRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记接口。见《概要设计》§5.4。
 *
 * <p>当前有上传、详情与列表；搜索 / 编辑 / 删除 / 预览 / 下载 / 我的上传随后续切片补上。
 * 全部接口都要求登录
 * {@code SecurityConfig} 的 {@code anyRequest().authenticated()} 已经覆盖。
 */
@RestController
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * 上传笔记。multipart 表单，文件 + 元数据。见 §6.1。
     *
     * <p>上传者取自 JWT 而非请求体：把「谁传的」交给客户端决定。
     * 返回体只有新笔记的 id，前端据此跳详情页。
     */
    // multipart/form-data（文件上传表单）不能用 @RequestBody，要用 @ModelAttribute 接收
    @PostMapping("/api/notes")
    public ApiResponse<NoteCreatedResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @ModelAttribute NoteUploadRequest request) {
        return ApiResponse.ok(noteService.upload(user.userId(), request));
    }

    /**
     * 笔记详情。见 §5.4、§4.1。
     *
     * <p>登录者取自 JWT，只用于填 {@code isFavorited}——它不该由客户端传，
     * 否则任何人都能问出「别人收藏过这篇没有」。
     *
     * <p>已下架 / 已删除的笔记同样返回 200：提示「已下架」是详情页的职责，
     * 40301 留给预览 / 下载 / 收藏（§4.1）。只有 id 不存在才是 40400。
     */
    @GetMapping("/api/notes/{id}")
    public ApiResponse<NoteDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(noteService.detail(id, user.userId()));
    }

    /**
     * 笔记列表。见 §5.4。
     *
     * <p>参数收进 {@link NoteListQuery} 而不是一串 {@code @RequestParam}：校验与类型转换失败
     * 才能落成 40001 加字段明细，而不是掉进兜底变成 50000（理由见该类的注释）。
     *
     * <p>登录者同样取自 JWT，只用于填 {@code isFavorited}——列表卡片上要显示收藏态。
     */
    // 多个查询参数收进一个对象用 @ModelAttribute；这条与 /{id} 的区别只是路径是否带变量
    @GetMapping("/api/notes")
    public ApiResponse<PageResponse<NoteListItemResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ModelAttribute NoteListQuery query) {
        return ApiResponse.ok(noteService.list(user.userId(), query));
    }
}
