package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.note.domain.favorite.Favorite;
import com.wlf.note.infrastructure.persistence.po.FavoritePO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteConverterTest {

    @Test
    void convertsPOToDomain() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        FavoritePO po = new FavoritePO();
        po.setId(1L);
        po.setUserId(42L);
        po.setNoteId(7L);
        po.setCreatedAt(now);

        Favorite favorite = FavoriteConverter.toDomain(po);

        assertThat(favorite.id()).isEqualTo(1L);
        assertThat(favorite.userId()).isEqualTo(42L);
        assertThat(favorite.noteId()).isEqualTo(7L);
        assertThat(favorite.createdAt()).isEqualTo(now);
    }

    @Test
    void convertsDomainToPO() {
        Favorite favorite = Favorite.create(42L, 7L);
        favorite.assignId(1L);

        FavoritePO po = FavoriteConverter.toPO(favorite);

        assertThat(po.getId()).isEqualTo(1L);
        assertThat(po.getUserId()).isEqualTo(42L);
        assertThat(po.getNoteId()).isEqualTo(7L);
    }
}
