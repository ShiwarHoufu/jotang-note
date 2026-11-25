package com.wlf.note.interfaces.web;

import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.common.PageResponse;
import com.wlf.note.application.command.DeleteNoteCommand;
import com.wlf.note.application.query.MyNoteListQuery;
import com.wlf.note.application.query.NoteListQuery;
import com.wlf.note.application.result.MyNoteItemResult;
import com.wlf.note.application.result.NoteListItemResult;
import com.wlf.note.application.result.PageResult;
import com.wlf.note.application.result.RawContentResult;
import com.wlf.note.application.service.NoteCommandService;
import com.wlf.note.application.service.NoteQueryService;
import com.wlf.note.interfaces.web.assembler.NoteAssembler;
import com.wlf.note.interfaces.web.dto.MyNoteItemResponse;
import com.wlf.note.interfaces.web.dto.MyNoteListRequest;
import com.wlf.note.interfaces.web.dto.NoteCreatedResponse;
import com.wlf.note.interfaces.web.dto.NoteDetailResponse;
import com.wlf.note.interfaces.web.dto.NoteListItemResponse;
import com.wlf.note.interfaces.web.dto.NoteListRequest;
import com.wlf.note.interfaces.web.dto.NotePreviewUrlResponse;
import com.wlf.note.interfaces.web.dto.NoteUpdateRequest;
import com.wlf.note.interfaces.web.dto.NoteUploadRequest;
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

/** 笔记接口。 */
@RestController
public class NoteController {

    private final NoteCommandService noteCommandService;
    private final NoteQueryService noteQueryService;
    private final NoteAssembler noteAssembler;

    public NoteController(NoteCommandService noteCommandService,
                          NoteQueryService noteQueryService,
                          NoteAssembler noteAssembler) {
        this.noteCommandService = noteCommandService;
        this.noteQueryService = noteQueryService;
        this.noteAssembler = noteAssembler;
    }

    @PostMapping("/api/notes")
    public ApiResponse<NoteCreatedResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @ModelAttribute NoteUploadRequest request) {
        return ApiResponse.ok(noteAssembler.toResponse(
                noteCommandService.upload(noteAssembler.toCommand(user.userId(), request))));
    }

    @GetMapping("/api/notes/{id}")
    public ApiResponse<NoteDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(noteAssembler.toResponse(
                noteQueryService.detail(id, user.userId(), user.isAdmin())));
    }

    @GetMapping("/api/notes/{id}/preview-url")
    public ApiResponse<NotePreviewUrlResponse> previewUrl(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(noteAssembler.toResponse(
                noteQueryService.previewUrl(id, user.userId(), user.isAdmin())));
    }

    @GetMapping("/api/notes/{id}/raw")
    public ResponseEntity<InputStreamResource> raw(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id) {
        RawContentResult content = noteQueryService.openRaw(id, user.userId(), user.isAdmin());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(content.size())
                .body(new InputStreamResource(content.content()));
    }

    @GetMapping("/api/notes")
    public ApiResponse<PageResponse<NoteListItemResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ModelAttribute NoteListRequest request) {
        NoteListQuery query = noteAssembler.toQuery(request);
        PageResult<NoteListItemResult> result = noteQueryService.list(user.userId(), query);
        return ApiResponse.ok(new PageResponse<>(
                result.items().stream().map(noteAssembler::toResponse).toList(),
                result.total(), result.page(), result.size()));
    }

    @GetMapping("/api/notes/mine")
    public ApiResponse<PageResponse<MyNoteItemResponse>> listMyNotes(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ModelAttribute MyNoteListRequest request) {
        MyNoteListQuery query = noteAssembler.toQuery(request);
        PageResult<MyNoteItemResult> result = noteQueryService.listMine(user.userId(), query);
        return ApiResponse.ok(new PageResponse<>(
                result.items().stream().map(noteAssembler::toResponse).toList(),
                result.total(), result.page(), result.size()));
    }

    @PutMapping("/api/notes/{id}")
    public ApiResponse<Void> update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id,
                                    @Valid @RequestBody NoteUpdateRequest request) {
        noteCommandService.update(noteAssembler.toCommand(id, user.userId(), request));
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/notes/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id) {
        noteCommandService.delete(new DeleteNoteCommand(id, user.userId()));
        return ApiResponse.ok();
    }
}
