package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.result.CollegeResult;
import com.wlf.user.interfaces.web.dto.CollegeResponse;
import org.springframework.stereotype.Component;

/** 学院接口的 Result 与 Response 映射。 */
@Component
public class CollegeAssembler {

    public CollegeResponse toResponse(CollegeResult result) {
        return new CollegeResponse(result.id(), result.name());
    }
}
