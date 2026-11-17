package com.wlf.app.client.user;

import com.wlf.note.application.note.dto.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RemoteUserProfileReaderTest {

    private final UserClient userClient = mock(UserClient.class);
    private final RemoteUserProfileReader reader = new RemoteUserProfileReader(userClient);

    @Test
    void mapsRemoteProfilesById() {
        when(userClient.batch(List.of(1L, 2L))).thenReturn(List.of(
                new InternalUserProfile(1L, "甲", "avatars/1.png", "计算机学院"),
                new InternalUserProfile(2L, "乙", null, "软件学院")));

        Map<Long, UserProfile> profiles = reader.batchGet(List.of(1L, 2L));

        assertThat(profiles).containsOnlyKeys(1L, 2L);
        assertThat(profiles.get(1L).nickname()).isEqualTo("甲");
        assertThat(profiles.get(1L).avatarKey()).isEqualTo("avatars/1.png");
        assertThat(profiles.get(2L).collegeName()).isEqualTo("软件学院");
    }

    @Test
    void emptyInputSkipsRemoteCall() {
        assertThat(reader.batchGet(List.of())).isEmpty();
        verifyNoInteractions(userClient);
    }

    @Test
    void remoteFailureDegradesToEmptyMap() {
        when(userClient.batch(List.of(1L))).thenThrow(new RuntimeException("user-service down"));

        assertThat(reader.batchGet(List.of(1L))).isEmpty();
        verify(userClient).batch(List.of(1L));
    }
}
