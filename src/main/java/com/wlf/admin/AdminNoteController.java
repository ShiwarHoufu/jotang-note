package com.wlf.admin;

import com.wlf.common.ApiResponse;
import com.wlf.note.NoteService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员笔记下架 / 恢复接口，仅 role=ADMIN 可访问。
 * 只改 status，不动 OSS 对象。见《概要设计》§5.6、§6.1。
 *
 * <p><b>本类不做管理员校验，也不取 {@code @AuthenticationPrincipal}。</b>
 * 「是不是管理员」由 {@code SecurityConfig} 的
 * {@code requestMatchers("/api/admin/**").hasRole("ADMIN")} 在过滤器链上判掉：
 * 未登录 → 40100，已登录但非管理员 → 40300，两条都在进 Controller 之前就产出了。
 *
 * <p>两个接口都用 POST 而不是 PUT：它们是命名动作（下架 / 恢复），不是对资源的幂等覆写。
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
     * <p>已是 {@code OFFLINE} 时返回 200 且什么都不做（幂等）；笔记已删除返回 40301，
     * 笔记不存在返回 40400。见 {@code NoteService#adminOffline}。
     */
    @PostMapping("/api/admin/notes/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable Long id) {
        noteService.adminOffline(id);
        return ApiResponse.ok();
    }

    /**
     * 恢复：{@code OFFLINE → ONLINE}
     *
     * <p>注意它不能复活已删除的笔记：{@code DELETED} 是终态，
     * 已删除的笔记报 40301。
     */
    @PostMapping("/api/admin/notes/{id}/online")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        noteService.adminRestore(id);
        return ApiResponse.ok();
    }
}
