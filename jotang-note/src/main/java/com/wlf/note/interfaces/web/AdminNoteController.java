package com.wlf.note.interfaces.web;

import com.wlf.common.ApiResponse;
import com.wlf.common.PageResponse;
import com.wlf.note.application.command.ChangeNoteStatusCommand;
import com.wlf.note.application.result.AdminNoteItemResult;
import com.wlf.note.application.result.PageResult;
import com.wlf.note.application.service.NoteCommandService;
import com.wlf.note.application.service.NoteQueryService;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.interfaces.web.assembler.NoteAssembler;
import com.wlf.note.interfaces.web.dto.AdminNoteItemResponse;
import com.wlf.note.interfaces.web.dto.AdminNoteListRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员笔记下架 / 恢复 / 已下架列表接口，仅 role=ADMIN 可访问。 */
@RestController
public class AdminNoteController {

    private final NoteCommandService noteCommandService;
    private final NoteQueryService noteQueryService;
    private final NoteAssembler noteAssembler;

    public AdminNoteController(NoteCommandService noteCommandService,
                               NoteQueryService noteQueryService,
                               NoteAssembler noteAssembler) {
        this.noteCommandService = noteCommandService;
        this.noteQueryService = noteQueryService;
        this.noteAssembler = noteAssembler;
    }

    @GetMapping("/api/admin/notes/offline")
    public ApiResponse<PageResponse<AdminNoteItemResponse>> listOffline(
            @Valid @ModelAttribute AdminNoteListRequest request) {
        PageResult<AdminNoteItemResult> result =
                noteQueryService.listOffline(noteAssembler.toQuery(request));
        return ApiResponse.ok(new PageResponse<>(
                result.items().stream().map(noteAssembler::toResponse).toList(),
                result.total(), result.page(), result.size()));
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
