package com.wlf.note;

import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.note.dto.NoteCreatedResponse;
import com.wlf.note.dto.NoteUploadRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记接口。见《概要设计》§5.4。
 *
 * <p>当前只有上传；列表 / 搜索 / 详情 / 编辑 / 删除 / 预览 / 下载 / 我的上传随后续切片补上。
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
}
