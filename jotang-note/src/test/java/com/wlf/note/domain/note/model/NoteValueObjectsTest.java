package com.wlf.note.domain.note.model;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteValueObjectsTest {

    private static final String VALID_STORAGE_KEY =
            "notes/2026/11/123e4567-e89b-12d3-a456-426614174000.md";

    @SuppressWarnings("unchecked")
    private static void assertInvalidField(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
            assertThat((List<FieldViolation>) ex.getData())
                    .singleElement()
                    .extracting(FieldViolation::field)
                    .isEqualTo(field);
        });
    }

    private static void assertServerError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
    }

    @Nested
    class NoteTitleTest {

        @Test
        void trimsAndAcceptsBoundaries() {
            assertThat(new NoteTitle("  标题  ").value()).isEqualTo("标题");
            assertThat(new NoteTitle("a").value()).hasSize(1);
            assertThat(new NoteTitle("a".repeat(128)).value()).hasSize(128);
        }

        @Test
        void rejectsBlankOrTooLong() {
            assertInvalidField(() -> new NoteTitle(null), "title");
            assertInvalidField(() -> new NoteTitle("   "), "title");
            assertInvalidField(() -> new NoteTitle("a".repeat(129)), "title");
        }
    }

    @Nested
    class NoteSummaryTest {

        @Test
        void trimsAndNormalizesBlankToNull() {
            assertThat(new NoteSummary("  简介  ").value()).isEqualTo("简介");
            assertThat(new NoteSummary("   ").value()).isNull();
            assertThat(new NoteSummary(null).value()).isNull();
            assertThat(new NoteSummary("a".repeat(512)).value()).hasSize(512);
        }

        @Test
        void rejectsTooLong() {
            assertInvalidField(() -> new NoteSummary("a".repeat(513)), "summary");
        }
    }

    @Nested
    class TeacherNameTest {

        @Test
        void trimsAndNormalizesBlankToNull() {
            assertThat(new TeacherName("  王老师  ").value()).isEqualTo("王老师");
            assertThat(new TeacherName("   ").value()).isNull();
            assertThat(new TeacherName("a".repeat(64)).value()).hasSize(64);
        }

        @Test
        void rejectsTooLong() {
            assertInvalidField(() -> new TeacherName("a".repeat(65)), "teacher");
        }
    }

    @Nested
    class CourseIdTest {

        @Test
        void acceptsPositiveId() {
            assertThat(new CourseId(1L).value()).isEqualTo(1L);
        }

        @Test
        void rejectsMissingOrNonPositive() {
            assertInvalidField(() -> new CourseId(null), "courseId");
            assertInvalidField(() -> new CourseId(0L), "courseId");
            assertInvalidField(() -> new CourseId(-1L), "courseId");
        }
    }

    @Nested
    class StorageKeyTest {

        @Test
        void acceptsGeneratedKey() {
            assertThat(new StorageKey(VALID_STORAGE_KEY).value()).isEqualTo(VALID_STORAGE_KEY);
        }

        @Test
        void rejectsIllegalKey() {
            assertServerError(() -> new StorageKey(null));
            assertServerError(() -> new StorageKey("avatars/42/x.png"));
            assertServerError(() -> new StorageKey("notes/2026/11/not-a-uuid.md"));
            assertServerError(() -> new StorageKey("notes/2026/11/123e4567-e89b-12d3-a456-426614174000.exe"));
        }
    }

    @Nested
    class OriginalFileNameTest {

        @Test
        void trimsAndStripsPath() {
            assertThat(new OriginalFileName("  ../../etc/passwd  ").value()).isEqualTo("passwd");
            assertThat(new OriginalFileName("C:\\Users\\x\\笔记.md").value()).isEqualTo("笔记.md");
        }

        @Test
        void rejectsBlankOrTooLong() {
            assertInvalidField(() -> new OriginalFileName(null), "file");
            assertInvalidField(() -> new OriginalFileName("   "), "file");
            assertInvalidField(() -> new OriginalFileName("a".repeat(256)), "file");
        }
    }

    @Nested
    class FileSizeTest {

        @Test
        void acceptsPositiveWithinLimit() {
            assertThat(new FileSize(1).value()).isEqualTo(1);
            assertThat(new FileSize(FileSize.MAX_BYTES).value()).isEqualTo(FileSize.MAX_BYTES);
        }

        @Test
        void rejectsOutOfRange() {
            assertInvalidField(() -> new FileSize(0), "file");
            assertInvalidField(() -> new FileSize(-1), "file");
            assertInvalidField(() -> new FileSize(FileSize.MAX_BYTES + 1), "file");
        }
    }

    @Nested
    class ContentTypeTest {

        @Test
        void acceptsSupportedTypes() {
            assertThat(new ContentType("text/markdown").value()).isEqualTo("text/markdown");
            assertThat(new ContentType("image/png").value()).isEqualTo("image/png");
        }

        @Test
        void rejectsUnknownType() {
            assertServerError(() -> new ContentType(null));
            assertServerError(() -> new ContentType("application/pdf"));
        }
    }
}
