package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.command.UpdateProfileCommand;
import com.wlf.user.application.command.UploadAvatarCommand;
import com.wlf.user.application.result.UserResult;
import com.wlf.user.interfaces.web.dto.AvatarUploadRequest;
import com.wlf.user.interfaces.web.dto.UpdateProfileRequest;
import com.wlf.user.interfaces.web.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UserAssemblerTest {

    private final UserAssembler assembler = new UserAssembler();

    @Test
    void mapsUpdateProfileRequestToCommand() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("小明");
        request.setCollegeId(7L);

        UpdateProfileCommand command = assembler.toCommand(42L, request);

        assertThat(command.userId()).isEqualTo(42L);
        assertThat(command.nickname()).isEqualTo("小明");
        assertThat(command.collegeId()).isEqualTo(7L);
    }

    @Test
    void mapsAvatarRequestToCommand() throws IOException {
        byte[] bytes = "avatar-bytes".getBytes(StandardCharsets.UTF_8);
        AvatarUploadRequest request = new AvatarUploadRequest();
        request.setFile(new MockMultipartFile("file", "avatar.png", "image/png", bytes));

        UploadAvatarCommand command = assembler.toCommand(42L, request);

        assertThat(command.userId()).isEqualTo(42L);
        assertThat(command.file().originalFilename()).isEqualTo("avatar.png");
        assertThat(command.file().size()).isEqualTo(bytes.length);
        try (InputStream in = command.file().open()) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void mapsUserResultToResponse() {
        UserResult result = new UserResult(1L, "alice", "Alice", 7L,
                "https://oss.example/avatar.png", "USER");

        UserResponse response = assembler.toResponse(result);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.nickname()).isEqualTo("Alice");
        assertThat(response.collegeId()).isEqualTo(7L);
        assertThat(response.avatarUrl()).isEqualTo("https://oss.example/avatar.png");
        assertThat(response.role()).isEqualTo("USER");
    }
}
