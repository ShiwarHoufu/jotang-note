package com.wlf.user.interfaces.internal.assembler;

import com.wlf.user.application.query.BatchUserDisplayQuery;
import com.wlf.user.application.result.UserDisplayResult;
import com.wlf.user.interfaces.internal.dto.InternalUserProfileResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InternalUserAssemblerTest {

    private final InternalUserAssembler assembler = new InternalUserAssembler();

    @Test
    void mapsIdsToQuery() {
        BatchUserDisplayQuery query = assembler.toQuery(List.of(1L, 2L));

        assertThat(query.ids()).containsExactly(1L, 2L);
    }

    @Test
    void mapsResultToResponse() {
        UserDisplayResult result = new UserDisplayResult(1L, "Alice",
                "avatars/1/123e4567-e89b-12d3-a456-426614174000.png", "计算机学院");

        InternalUserProfileResponse response = assembler.toResponse(result);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("Alice");
        assertThat(response.avatarKey()).isEqualTo("avatars/1/123e4567-e89b-12d3-a456-426614174000.png");
        assertThat(response.collegeName()).isEqualTo("计算机学院");
    }
}
