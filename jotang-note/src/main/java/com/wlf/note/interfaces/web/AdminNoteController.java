package com.wlf.note.interfaces.web;

import com.wlf.common.ApiResponse;
import com.wlf.note.application.command.ChangeNoteStatusCommand;
import com.wlf.note.application.service.NoteCommandService;
import com.wlf.note.domain.note.NoteStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员笔记下架 / 恢复接口，仅 role=ADMIN 可访问。 */
@RestController
public class AdminNoteController {

    private final NoteCommandService noteCommandService;

    public AdminNoteController(NoteCommandService noteCommandService) {
        this.noteCommandService = noteCommandService;
    }

    @PostMapping("/api/admin/notes/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable Long id) {
        noteCommandService.changeStatus(new ChangeNoteStatusCommand(id, NoteStatus.OFFLINE));
        return ApiResponse.ok();
    }

    @PostMapping("/api/admin/notes/{id}/online")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        noteCommandService.changeStatus(new ChangeNoteStatusCommand(id, NoteStatus.ONLINE));
        return ApiResponse.ok();
    }
}
