package com.wlf.note.interfaces.web.assembler;

import com.wlf.note.application.query.AdminNoteListQuery;
import com.wlf.note.application.query.NoteListQuery;
import com.wlf.note.application.result.AdminNoteItemResult;
import com.wlf.note.application.result.CourseResult;
import com.wlf.note.application.result.NoteDetailResult;
import com.wlf.note.application.result.NoteListItemResult;
import com.wlf.note.application.result.TagResult;
import com.wlf.note.application.result.UploaderResult;
import com.wlf.note.domain.note.NoteSort;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.PreviewMode;
import com.wlf.note.interfaces.web.dto.AdminNoteItemResponse;
import com.wlf.note.interfaces.web.dto.AdminNoteListRequest;
import com.wlf.note.interfaces.web.dto.NoteDetailResponse;
import com.wlf.note.interfaces.web.dto.NoteListItemResponse;
import com.wlf.note.interfaces.web.dto.NoteListRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoteAssemblerTest {

    private final NoteAssembler assembler = new NoteAssembler();

    @Test
    void mapsListRequestToQuery() {
        NoteListRequest request = new NoteListRequest();
        request.setPage(2);
        request.setSize(10);
        request.setCourseId(3L);
        request.setTagId(4L);
        request.setKeyword("kw");
        request.setSort(NoteListRequest.Sort.HOT_VIEW);

        NoteListQuery query = assembler.toQuery(request);

        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(10);
        assertThat(query.courseId()).isEqualTo(3L);
        assertThat(query.tagId()).isEqualTo(4L);
        assertThat(query.keyword()).isEqualTo("kw");
        assertThat(query.sort()).isEqualTo(NoteSort.HOT_VIEW);
    }

    @Test
    void mapsAdminListRequestToQuery() {
        AdminNoteListRequest request = new AdminNoteListRequest();
        request.setPage(3);
        request.setSize(15);
        request.setKeyword("kw");

        AdminNoteListQuery query = assembler.toQuery(request);

        assertThat(query.page()).isEqualTo(3);
        assertThat(query.size()).isEqualTo(15);
        assertThat(query.keyword()).isEqualTo("kw");
    }

    @Test
    void mapsAdminNoteItemResultToResponse() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 20, 9, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 11, 24, 12, 0);
        AdminNoteItemResult result = new AdminNoteItemResult(
                1L, "标题", new CourseResult(2L, "课程"),
                new UploaderResult(3L, "昵称", "url", "学院"),
                List.of(new TagResult(4L, "标签")),
                10, 2, 1, createdAt, updatedAt);

        AdminNoteItemResponse response = assembler.toResponse(result);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.course().name()).isEqualTo("课程");
        assertThat(response.uploader().nickname()).isEqualTo("昵称");
        assertThat(response.tags()).singleElement().extracting("name").isEqualTo("标签");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void mapsDetailResultToResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        NoteDetailResult result = new NoteDetailResult(
                1L, "标题", "简介", "老师", NoteStatus.ONLINE,
                new CourseResult(2L, "课程"),
                new UploaderResult(3L, "昵称", "url", "学院"),
                List.of(new TagResult(4L, "标签")),
                10, 2, 1, now, now,
                new NoteDetailResult.FileInfo("笔记.md", 100, "text/markdown", PreviewMode.TEXT_PROXY),
                true);

        NoteDetailResponse response = assembler.toResponse(result);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.course().name()).isEqualTo("课程");
        assertThat(response.uploader().nickname()).isEqualTo("昵称");
        assertThat(response.tags()).singleElement().extracting("name").isEqualTo("标签");
        assertThat(response.file().previewMode()).isEqualTo(PreviewMode.TEXT_PROXY);
        assertThat(response.isFavorited()).isTrue();
    }

    @Test
    void mapsListItemResultToResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        NoteListItemResult result = new NoteListItemResult(
                1L, "标题", new CourseResult(2L, "课程"),
                new UploaderResult(3L, "昵称", "url", "学院"),
                List.of(new TagResult(4L, "标签")),
                10, 2, 1, now, false);

        NoteListItemResponse response = assembler.toResponse(result);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.course().name()).isEqualTo("课程");
        assertThat(response.uploader().collegeName()).isEqualTo("学院");
        assertThat(response.tags()).singleElement().extracting("name").isEqualTo("标签");
        assertThat(response.isFavorited()).isFalse();
    }
}
