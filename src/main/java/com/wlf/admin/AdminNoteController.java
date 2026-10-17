package com.wlf.admin;

import com.wlf.common.ApiResponse;
import com.wlf.note.NoteService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员笔记下架 / 恢复接口，仅 role=ADMIN 可访问。
 * 只改 status，不动 OSS 对象。
 */
@RestController
public class AdminNoteController {

    private final NoteService noteService;

    public AdminNoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * 下架：{@code ONLINE → OFFLINE}
     *
     * <p>已是 OFFLINE 时返回 200 且什么都不做（幂等）；
     * 笔记已删除返回 40301，笔记不存在返回 40400。
     */
    @PostMapping("/api/admin/notes/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable Long id) {
        noteService.adminOffline(id);
        return ApiResponse.ok();
    }

    /**
     * 恢复：{@code OFFLINE → ONLINE}
     *
     * <p>不能复活已删除的笔记。
     * 已删除的笔记报 40301。
     */
    @PostMapping("/api/admin/notes/{id}/online")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        noteService.adminRestore(id);
        return ApiResponse.ok();
    }
}
