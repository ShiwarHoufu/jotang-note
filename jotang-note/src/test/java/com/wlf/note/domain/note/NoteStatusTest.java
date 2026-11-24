package com.wlf.note.domain.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteStatusTest {

    @Test
    void mapsKnownNames() {
        assertThat(NoteStatus.fromName("ONLINE")).isEqualTo(NoteStatus.ONLINE);
        assertThat(NoteStatus.fromName("OFFLINE")).isEqualTo(NoteStatus.OFFLINE);
        assertThat(NoteStatus.fromName("DELETED")).isEqualTo(NoteStatus.DELETED);
    }

    @Test
    void rejectsUnknownName() {
        assertThatThrownBy(() -> NoteStatus.fromName("UNKNOWN"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
        assertThatThrownBy(() -> NoteStatus.fromName(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
    }
}
