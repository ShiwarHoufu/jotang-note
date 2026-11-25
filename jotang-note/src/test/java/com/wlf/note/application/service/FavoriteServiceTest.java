package com.wlf.note.application.service;

import com.wlf.note.application.command.FavoriteNoteCommand;
import com.wlf.note.domain.favorite.Favorite;
import com.wlf.note.domain.favorite.FavoriteQueryRepository;
import com.wlf.note.domain.favorite.FavoriteRepository;
import com.wlf.note.domain.note.NoteDetailCache;
import com.wlf.note.domain.note.NoteQueryRepository;
import com.wlf.note.domain.note.NoteRepository;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.uploader.UploaderProfileReader;
import com.wlf.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long NOTE_ID = 10L;

    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private FavoriteQueryRepository favoriteQueryRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private NoteQueryRepository noteQueryRepository;
    @Mock
    private UploaderProfileReader uploaderProfileReader;
    @Mock
    private StorageService storageService;
    @Mock
    private NoteDetailCache noteDetailCache;

    private FavoriteService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(favoriteRepository, favoriteQueryRepository, noteRepository,
                noteQueryRepository, uploaderProfileReader, storageService, noteDetailCache);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void favoriteEvictsDetailCacheOnlyAfterCommit() {
        when(noteRepository.lockStatus(NOTE_ID)).thenReturn(Optional.of(NoteStatus.ONLINE));
        when(noteRepository.favoriteCount(NOTE_ID)).thenReturn(1L);

        service.favorite(new FavoriteNoteCommand(USER_ID, NOTE_ID));

        verify(favoriteRepository).insert(any(Favorite.class));
        verify(noteDetailCache, never()).evict(any());
        afterCommit();
        verify(noteDetailCache).evict(NOTE_ID);
    }

    @Test
    void unfavoriteWithoutRelationDoesNotEvictCache() {
        when(noteRepository.lockStatus(NOTE_ID)).thenReturn(Optional.of(NoteStatus.ONLINE));
        when(favoriteRepository.deleteByUserAndNote(USER_ID, NOTE_ID)).thenReturn(0);
        when(noteRepository.favoriteCount(NOTE_ID)).thenReturn(0L);

        service.unfavorite(new FavoriteNoteCommand(USER_ID, NOTE_ID));

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(noteDetailCache, never()).evict(any());
    }

    @Test
    void unfavoriteEvictsDetailCacheAfterCommit() {
        when(noteRepository.lockStatus(NOTE_ID)).thenReturn(Optional.of(NoteStatus.ONLINE));
        when(favoriteRepository.deleteByUserAndNote(USER_ID, NOTE_ID)).thenReturn(1);
        when(noteRepository.favoriteCount(NOTE_ID)).thenReturn(0L);

        service.unfavorite(new FavoriteNoteCommand(USER_ID, NOTE_ID));

        verify(noteDetailCache, never()).evict(any());
        afterCommit();
        verify(noteDetailCache).evict(NOTE_ID);
    }

    @Test
    void cacheEvictionFailureDoesNotBreakTheCall() {
        when(noteRepository.lockStatus(NOTE_ID)).thenReturn(Optional.of(NoteStatus.ONLINE));
        when(noteRepository.favoriteCount(NOTE_ID)).thenReturn(1L);
        doThrow(new RuntimeException("redis down")).when(noteDetailCache).evict(NOTE_ID);

        service.favorite(new FavoriteNoteCommand(USER_ID, NOTE_ID));

        assertThatCode(this::afterCommit).doesNotThrowAnyException();
    }

    private void afterCommit() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }
}
