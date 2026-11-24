package com.wlf.note.interfaces.web.assembler;

import com.wlf.note.application.result.CourseResult;
import com.wlf.note.application.result.FavoriteCountResult;
import com.wlf.note.application.result.FavoriteItemResult;
import com.wlf.note.application.result.TagResult;
import com.wlf.note.application.result.UploaderResult;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.interfaces.web.dto.FavoriteCountResponse;
import com.wlf.note.interfaces.web.dto.FavoriteItemResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteAssemblerTest {

    private final FavoriteAssembler assembler = new FavoriteAssembler(new NoteAssembler());

    @Test
    void mapsCountResult() {
        FavoriteCountResponse response = assembler.toResponse(new FavoriteCountResult(6L));

        assertThat(response.favoriteCount()).isEqualTo(6L);
    }

    @Test
    void mapsNormalItem() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        FavoriteItemResult result = new FavoriteItemResult(
                1L, NoteStatus.ONLINE, now, "标题",
                new CourseResult(2L, "课程"),
                new UploaderResult(3L, "昵称", "url", "学院"),
                List.of(new TagResult(4L, "标签")),
                10L, 2L, 1L, now);

        FavoriteItemResponse response = assembler.toResponse(result);

        assertThat(response.noteId()).isEqualTo(1L);
        assertThat(response.course().name()).isEqualTo("课程");
        assertThat(response.uploader().nickname()).isEqualTo("昵称");
        assertThat(response.tags()).singleElement().extracting("name").isEqualTo("标签");
    }

    @Test
    void mapsPlaceholderItemWithNullUploader() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        FavoriteItemResult result = new FavoriteItemResult(
                1L, NoteStatus.DELETED, now, "标题",
                new CourseResult(2L, "课程"), null, List.of(),
                null, null, null, null);

        FavoriteItemResponse response = assembler.toResponse(result);

        assertThat(response.status()).isEqualTo(NoteStatus.DELETED);
        assertThat(response.uploader()).isNull();
        assertThat(response.tags()).isEmpty();
    }
}
