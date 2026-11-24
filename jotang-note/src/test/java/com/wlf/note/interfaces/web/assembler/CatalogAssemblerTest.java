package com.wlf.note.interfaces.web.assembler;

import com.wlf.note.application.result.CourseResult;
import com.wlf.note.application.result.TagResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogAssemblerTest {

    private final CatalogAssembler assembler = new CatalogAssembler();

    @Test
    void mapsCourseAndTag() {
        assertThat(assembler.toResponse(new CourseResult(1L, "课程")).name()).isEqualTo("课程");
        assertThat(assembler.toResponse(new TagResult(2L, "标签")).name()).isEqualTo("标签");
    }
}
