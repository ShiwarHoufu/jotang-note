package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.result.CollegeResult;
import com.wlf.user.interfaces.web.dto.CollegeResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollegeAssemblerTest {

    private final CollegeAssembler assembler = new CollegeAssembler();

    @Test
    void mapsResultToResponse() {
        CollegeResponse response = assembler.toResponse(new CollegeResult(1L, "计算机科学与工程学院"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("计算机科学与工程学院");
    }
}
