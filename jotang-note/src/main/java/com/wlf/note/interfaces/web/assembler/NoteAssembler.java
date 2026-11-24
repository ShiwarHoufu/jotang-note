package com.wlf.note.interfaces.web.assembler;

import com.wlf.note.application.command.UpdateNoteCommand;
import com.wlf.note.application.command.UploadNoteCommand;
import com.wlf.note.application.query.MyNoteListQuery;
import com.wlf.note.application.query.NoteListQuery;
import com.wlf.note.application.result.CourseResult;
import com.wlf.note.application.result.MyNoteItemResult;
import com.wlf.note.application.result.NoteCreatedResult;
import com.wlf.note.application.result.NoteDetailResult;
import com.wlf.note.application.result.NoteListItemResult;
import com.wlf.note.application.result.PreviewUrlResult;
import com.wlf.note.application.result.TagResult;
import com.wlf.note.application.result.UploaderResult;
import com.wlf.note.domain.note.NoteSort;
import com.wlf.note.interfaces.web.dto.CourseResponse;
import com.wlf.note.interfaces.web.dto.MyNoteItemResponse;
import com.wlf.note.interfaces.web.dto.MyNoteListRequest;
import com.wlf.note.interfaces.web.dto.NoteCreatedResponse;
import com.wlf.note.interfaces.web.dto.NoteDetailResponse;
import com.wlf.note.interfaces.web.dto.NoteListItemResponse;
import com.wlf.note.interfaces.web.dto.NoteListRequest;
import com.wlf.note.interfaces.web.dto.NotePreviewUrlResponse;
import com.wlf.note.interfaces.web.dto.NoteUpdateRequest;
import com.wlf.note.interfaces.web.dto.NoteUploadRequest;
import com.wlf.note.interfaces.web.dto.TagResponse;
import com.wlf.note.interfaces.web.dto.UploaderResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** note 接口的 Request/Response 与 Command/Query/Result 映射。 */
@Component
public class NoteAssembler {

    public UploadNoteCommand toCommand(Long uploaderId, NoteUploadRequest request) {
        return new UploadNoteCommand(uploaderId, new MultipartUploadedFile(request.getFile()),
                request.getTitle(), request.getSummary(), request.getTeacher(),
                request.getCourseId(), request.getTags());
    }

    public UpdateNoteCommand toCommand(Long noteId, Long editorId, NoteUpdateRequest request) {
        return new UpdateNoteCommand(noteId, editorId, request.getTitle(), request.getSummary(),
                request.getTeacher(), request.getCourseId(), request.getTags());
    }

    public NoteListQuery toQuery(NoteListRequest request) {
        return new NoteListQuery(request.getPage(), request.getSize(), request.getCourseId(),
                request.getTagId(), request.getKeyword(), NoteSort.valueOf(request.getSort().name()));
    }

    public MyNoteListQuery toQuery(MyNoteListRequest request) {
        return new MyNoteListQuery(request.getPage(), request.getSize());
    }

    public NoteCreatedResponse toResponse(NoteCreatedResult result) {
        return new NoteCreatedResponse(result.id());
    }

    public NoteDetailResponse toResponse(NoteDetailResult result) {
        return new NoteDetailResponse(
                result.id(), result.title(), result.summary(), result.teacher(), result.status(),
                toResponse(result.course()), toResponse(result.uploader()), toTagResponses(result.tags()),
                result.viewCount(), result.downloadCount(), result.favoriteCount(),
                result.createdAt(), result.updatedAt(), toResponse(result.file()), result.isFavorited());
    }

    public NoteListItemResponse toResponse(NoteListItemResult result) {
        return new NoteListItemResponse(
                result.id(), result.title(), toResponse(result.course()), toResponse(result.uploader()),
                toTagResponses(result.tags()), result.viewCount(), result.downloadCount(),
                result.favoriteCount(), result.updatedAt(), result.isFavorited());
    }

    public MyNoteItemResponse toResponse(MyNoteItemResult result) {
        return new MyNoteItemResponse(
                result.id(), result.status(), result.title(), toResponse(result.course()),
                toTagResponses(result.tags()), result.viewCount(), result.downloadCount(),
                result.favoriteCount(), result.createdAt(), result.isFavorited());
    }

    public NotePreviewUrlResponse toResponse(PreviewUrlResult result) {
        return new NotePreviewUrlResponse(result.url());
    }

    public CourseResponse toResponse(CourseResult result) {
        return new CourseResponse(result.id(), result.name());
    }

    public TagResponse toResponse(TagResult result) {
        return new TagResponse(result.id(), result.name());
    }

    public UploaderResponse toResponse(UploaderResult result) {
        return new UploaderResponse(result.id(), result.nickname(),
                result.avatarUrl(), result.collegeName());
    }

    public List<TagResponse> toTagResponses(List<TagResult> tags) {
        return tags.stream().map(this::toResponse).toList();
    }

    private NoteDetailResponse.FileInfo toResponse(NoteDetailResult.FileInfo file) {
        return file == null ? null : new NoteDetailResponse.FileInfo(
                file.originalName(), file.size(), file.contentType(), file.previewMode());
    }
}
