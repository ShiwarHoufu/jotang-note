package com.wlf.note.infrastructure.client.user;

import com.wlf.note.domain.uploader.UploaderProfile;
import com.wlf.note.domain.uploader.UploaderProfileCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachingUploaderProfileReaderTest {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final UploaderProfile ALICE =
            new UploaderProfile(1L, "alice", "avatars/1/a.jpg", "计算机学院");
    private static final UploaderProfile BOB =
            new UploaderProfile(2L, "bob", "avatars/2/b.jpg", "数学学院");

    @Mock
    private RemoteUserProfileReader remote;
    @Mock
    private UploaderProfileCache cache;

    private CachingUploaderProfileReader reader;

    @BeforeEach
    void setUp() {
        reader = new CachingUploaderProfileReader(remote, cache, TTL);
    }

    @Test
    void returnsCachedProfilesWithoutRemoteCall() {
        when(cache.getAll(List.of(1L))).thenReturn(Map.of(1L, ALICE));

        assertThat(reader.batchGet(List.of(1L))).containsExactlyEntriesOf(Map.of(1L, ALICE));
        verifyNoInteractions(remote);
    }

    @Test
    void fetchesOnlyMissingIdsAndBackfills() {
        when(cache.getAll(List.of(1L, 2L))).thenReturn(Map.of(1L, ALICE));
        when(remote.fetch(List.of(2L))).thenReturn(Map.of(2L, BOB));

        assertThat(reader.batchGet(List.of(1L, 2L)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(1L, ALICE, 2L, BOB));
        verify(cache).putAll(Map.of(2L, BOB), TTL);
    }

    @Test
    void returnsCachedPartWhenRemoteFails() {
        when(cache.getAll(List.of(1L, 2L))).thenReturn(Map.of(1L, ALICE));
        when(remote.fetch(List.of(2L))).thenThrow(new RuntimeException("user-service down"));

        assertThat(reader.batchGet(List.of(1L, 2L))).containsExactlyEntriesOf(Map.of(1L, ALICE));
        verify(cache, never()).putAll(any(), any());
    }

    @Test
    void fallsBackToRemoteWhenCacheReadFails() {
        when(cache.getAll(List.of(1L))).thenThrow(new RuntimeException("redis down"));
        when(remote.fetch(List.of(1L))).thenReturn(Map.of(1L, ALICE));

        assertThat(reader.batchGet(List.of(1L))).containsExactlyEntriesOf(Map.of(1L, ALICE));
    }

    @Test
    void ignoresCacheWriteFailure() {
        when(cache.getAll(List.of(1L))).thenReturn(Map.of());
        when(remote.fetch(List.of(1L))).thenReturn(Map.of(1L, ALICE));
        doThrow(new RuntimeException("redis down")).when(cache).putAll(any(), any());

        assertThat(reader.batchGet(List.of(1L))).containsExactlyEntriesOf(Map.of(1L, ALICE));
    }

    @Test
    void doesNotCacheIdsMissingFromRemote() {
        when(cache.getAll(List.of(1L))).thenReturn(Map.of());
        when(remote.fetch(List.of(1L))).thenReturn(Map.of());

        assertThat(reader.batchGet(List.of(1L))).isEmpty();
        verify(cache, never()).putAll(any(), any());
    }

    @Test
    void deduplicatesIds() {
        when(cache.getAll(List.of(1L))).thenReturn(Map.of(1L, ALICE));

        assertThat(reader.batchGet(List.of(1L, 1L))).containsExactlyEntriesOf(Map.of(1L, ALICE));
        verifyNoInteractions(remote);
    }

    @Test
    void skipsEverythingForEmptyInput() {
        assertThat(reader.batchGet(List.of())).isEmpty();
        verifyNoInteractions(remote, cache);
    }
}
