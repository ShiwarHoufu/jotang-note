package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.note.domain.favorite.Favorite;
import com.wlf.note.infrastructure.persistence.po.FavoritePO;

/** Favorite ↔ favorite 行。 */
public final class FavoriteConverter {

    private FavoriteConverter() {
    }

    public static Favorite toDomain(FavoritePO po) {
        return Favorite.reconstitute(po.getId(), po.getUserId(), po.getNoteId(), po.getCreatedAt());
    }

    public static FavoritePO toPO(Favorite favorite) {
        FavoritePO po = new FavoritePO();
        po.setId(favorite.id());
        po.setUserId(favorite.userId());
        po.setNoteId(favorite.noteId());
        po.setCreatedAt(favorite.createdAt());
        return po;
    }
}
