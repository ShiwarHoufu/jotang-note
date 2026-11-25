package com.wlf.note.application.service;

import com.wlf.note.domain.note.NoteQueryRepository;
import com.wlf.note.domain.note.NoteRepository;
import com.wlf.note.domain.note.ViewCountBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewCountServiceTest {

    @Mock
    private ViewCountBuffer viewCountBuffer;
    @Mock
    private NoteQueryRepository noteQueryRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private ViewCountService service;

    @BeforeEach
    void setUp() {
        service = new ViewCountService(viewCountBuffer, noteQueryRepository, noteRepository, transactionManager);
    }

    @Test
    void recordViewReturnsTotalWhenCounterExists() {
        when(viewCountBuffer.increment(1L)).thenReturn(5L);

        assertThat(service.recordView(1L)).isEqualTo(5L);
        verifyNoInteractions(noteQueryRepository);
    }

    @Test
    void recordViewInitializesMissingCounterFromDb() {
        when(viewCountBuffer.increment(1L)).thenReturn(null, 42L);
        when(noteQueryRepository.findViewCount(1L)).thenReturn(Optional.of(41L));

        assertThat(service.recordView(1L)).isEqualTo(42L);
        verify(viewCountBuffer).initializeIfAbsent(1L, 41L);
    }

    @Test
    void recordViewSwallowsRedisFailure() {
        when(viewCountBuffer.increment(1L)).thenThrow(new RuntimeException("redis down"));

        assertThat(service.recordView(1L)).isNull();
    }

    @Test
    void currentReturnsNullWhenCounterMissing() {
        when(viewCountBuffer.totals(List.of(1L))).thenReturn(Map.of());

        assertThat(service.current(1L)).isNull();
    }

    @Test
    void currentSwallowsRedisFailure() {
        when(viewCountBuffer.totals(List.of(1L))).thenThrow(new RuntimeException("redis down"));

        assertThat(service.current(1L)).isNull();
    }

    @Test
    void flushWritesTotalsThenClearsDirtyMarks() {
        stubTransaction();
        when(viewCountBuffer.dirtyNoteIds(200)).thenReturn(List.of(1L, 2L));
        when(viewCountBuffer.totals(List.of(1L, 2L))).thenReturn(Map.of(1L, 100L, 2L, 200L));
        when(viewCountBuffer.clearIfUnchanged(1L, 100L)).thenReturn(true);
        when(viewCountBuffer.clearIfUnchanged(2L, 200L)).thenReturn(true);

        assertThat(service.flush(200)).isEqualTo(2);
        verify(noteRepository).saveViewCounts(Map.of(1L, 100L, 2L, 200L));
        verify(viewCountBuffer).clearIfUnchanged(1L, 100L);
        verify(viewCountBuffer).clearIfUnchanged(2L, 200L);
    }

    @Test
    void flushKeepsDirtyMarksWhenDbFails() {
        stubTransaction();
        when(viewCountBuffer.dirtyNoteIds(200)).thenReturn(List.of(1L));
        when(viewCountBuffer.totals(List.of(1L))).thenReturn(Map.of(1L, 100L));
        doThrow(new RuntimeException("db down")).when(noteRepository).saveViewCounts(any());

        assertThat(service.flush(200)).isZero();
        verify(viewCountBuffer, never()).clearIfUnchanged(anyLong(), anyLong());
    }

    @Test
    void flushDiscardsDirtyIdsWhoseCounterIsGone() {
        stubTransaction();
        when(viewCountBuffer.dirtyNoteIds(200)).thenReturn(List.of(1L, 2L));
        when(viewCountBuffer.totals(List.of(1L, 2L))).thenReturn(Map.of(1L, 100L));
        when(viewCountBuffer.clearIfUnchanged(1L, 100L)).thenReturn(true);

        assertThat(service.flush(200)).isEqualTo(1);
        verify(viewCountBuffer).discard(List.of(2L));
    }

    @Test
    void flushSkipsWhenNothingIsDirty() {
        when(viewCountBuffer.dirtyNoteIds(200)).thenReturn(List.of());

        assertThat(service.flush(200)).isZero();
        verify(viewCountBuffer, never()).totals(any());
    }

    @Test
    void flushSkipsWhenDirtyReadFails() {
        when(viewCountBuffer.dirtyNoteIds(200)).thenThrow(new RuntimeException("redis down"));

        assertThat(service.flush(200)).isZero();
        verifyNoInteractions(noteRepository);
    }

    private void stubTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    }
}
