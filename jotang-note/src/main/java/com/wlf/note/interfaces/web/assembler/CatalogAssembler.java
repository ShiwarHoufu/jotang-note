package com.wlf.note.interfaces.web.assembler;

import com.wlf.note.application.result.CourseResult;
import com.wlf.note.application.result.TagResult;
import com.wlf.note.interfaces.web.dto.CourseResponse;
import com.wlf.note.interfaces.web.dto.TagResponse;
import org.springframework.stereotype.Component;

/** catalog 接口的 Result → Response 映射。 */
@Component
public class CatalogAssembler {

    public CourseResponse toResponse(CourseResult result) {
        return new CourseResponse(result.id(), result.name());
    }

    public TagResponse toResponse(TagResult result) {
        return new TagResponse(result.id(), result.name());
    }
}
