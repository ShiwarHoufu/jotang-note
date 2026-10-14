package com.wlf.note;

import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.common.PageResponse;
import com.wlf.note.dto.MyNoteItemResponse;
import com.wlf.note.dto.MyNoteListQuery;
import com.wlf.note.dto.NoteCreatedResponse;
import com.wlf.note.dto.NoteDetailResponse;
import com.wlf.note.dto.NoteListItemResponse;
import com.wlf.note.dto.NoteListQuery;
import com.wlf.note.dto.NotePreviewUrlResponse;
import com.wlf.note.dto.NoteUpdateRequest;
import com.wlf.note.dto.NoteUploadRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 笔记接口。
 *
 * <p>当前有上传、详情、列表、我的上传、编辑、删除与预览；搜索 / 下载随后续切片补上。
 * 全部接口都要求登录
 */
@RestController
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * 上传笔记。
     *
     * <p>返回体只有新笔记的 id，前端据此跳详情页。
     */
    // multipart/form-data（文件上传表单）不能用 @RequestBody，要用 @ModelAttribute 接收
    @PostMapping("/api/notes")
    public ApiResponse<NoteCreatedResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @ModelAttribute NoteUploadRequest request) {
        return ApiResponse.ok(noteService.upload(user.userId(), request));
    }

    /**
     * 笔记详情。
     *
     * <p>已下架的笔记只对上传者本人与管理员开放，其余人 40301；
     * 已删除的对所有人 40301。
     * id 不存 40400。
     */
    @GetMapping("/api/notes/{id}")
    public ApiResponse<NoteDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(noteService.detail(id, user.userId(), user.isAdmin()));
    }

    /**
     * 图片预览：
     * 返回 OSS 签名 URL，前端 {@code <img>} 直出。
     */
    @GetMapping("/api/notes/{id}/preview-url")
    public ApiResponse<NotePreviewUrlResponse> previewUrl(@PathVariable Long id) {
        return ApiResponse.ok(noteService.previewUrl(id));
    }

    /**
     * 文本预览：
     * 后端代理返回原文
     *
     * <p>不套 {@code ApiResponse} ：成功时响应体是文件原文本身；
     * 失败仍由 {@code GlobalExceptionHandler} 写回 JSON 信封。
     */
    @GetMapping("/api/notes/{id}/raw")
    public ResponseEntity<InputStreamResource> raw(@PathVariable Long id) {
        NoteRawContent content = noteService.openRaw(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(content.size())
                .body(new InputStreamResource(content.content()));
    }

    /**
     * 笔记列表。
     *
     * <p>参数收进 {@link NoteListQuery} 而不是一串 {@code @RequestParam}：校验与类型转换失败
     * 才能落成 40001 加字段明细，而不是掉进兜底变成 50000（理由见该类的注释）。
     *
     * <p>登录者同样取自 JWT，只用于显示收藏态。
     */
    // 多个查询参数收进一个对象用 @ModelAttribute；这条与 /{id} 的区别只是路径是否带变量
    @GetMapping("/api/notes")
    public ApiResponse<PageResponse<NoteListItemResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ModelAttribute NoteListQuery query) {
        return ApiResponse.ok(noteService.list(user.userId(), query));
    }

    /**
     * 我的上传。按上传时间倒序分页。
     *
     * <p><b>已删除的不展示</b>（§4.1 的表里这一列对 {@code DELETED} 是不可见）；
     * 已下架的照常返回，由 {@code status} 让前端标注。
     *
     * <p>路径在 {@code /api/users/me} 下但实现在本类，与「我的收藏」在
     * {@code FavoriteController} 同例：前缀归 users，资源归各自的模块。
     *
     * <p>登录者取自 JWT：这是「我的」列表，绝不接受请求参数传入，否则可以翻别人的上传。
     */
    @GetMapping("/api/users/me/notes")
    public ApiResponse<PageResponse<MyNoteItemResponse>> listMyNotes(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ModelAttribute MyNoteListQuery query) {
        return ApiResponse.ok(noteService.listMyNotes(user.userId(), query));
    }

    /**
     * 编辑自己笔记元数据。
     *
     * <p><b>PUT 的语义是全量替换</b>：没传的字段等于清空。前端编辑表单本就该回填全部字段再整体提交。
     * <p>已删除的笔记报 40301；已下架的可以编辑（改完仍是下架状态）。
     */
    @PutMapping("/api/notes/{id}")
    public ApiResponse<Void> update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id,
                                    @Valid @RequestBody NoteUpdateRequest request) {
        noteService.update(id, user.userId(), request);
        return ApiResponse.ok();
    }

    /**
     * 软删除自己的笔记。
     */
    @DeleteMapping("/api/notes/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id) {
        noteService.delete(id, user.userId());
        return ApiResponse.ok();
    }
}
