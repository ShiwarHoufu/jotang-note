package com.wlf.note.interfaces.web.assembler;

import com.wlf.note.application.query.FavoriteListQuery;
import com.wlf.note.application.result.FavoriteCountResult;
import com.wlf.note.application.result.FavoriteItemResult;
import com.wlf.note.interfaces.web.dto.FavoriteCountResponse;
import com.wlf.note.interfaces.web.dto.FavoriteItemResponse;
import com.wlf.note.interfaces.web.dto.FavoriteListRequest;
import com.wlf.note.interfaces.web.dto.UploaderResponse;
import org.springframework.stereotype.Component;

/** favorite 接口的 Request/Response 与 Query/Result 映射。 */
@Component
public class FavoriteAssembler {

    private final NoteAssembler noteAssembler;

    public FavoriteAssembler(NoteAssembler noteAssembler) {
        this.noteAssembler = noteAssembler;
    }

    public FavoriteListQuery toQuery(FavoriteListRequest request) {
        return new FavoriteListQuery(request.getPage(), request.getSize());
    }

    public FavoriteCountResponse toResponse(FavoriteCountResult result) {
        return new FavoriteCountResponse(result.favoriteCount());
    }

    public FavoriteItemResponse toResponse(FavoriteItemResult result) {
        UploaderResponse uploader = result.uploader() == null
                ? null
                : noteAssembler.toResponse(result.uploader());
        return new FavoriteItemResponse(
                result.noteId(), result.status(), result.favoritedAt(), result.title(),
                noteAssembler.toResponse(result.course()), uploader,
                noteAssembler.toTagResponses(result.tags()),
                result.viewCount(), result.downloadCount(), result.favoriteCount(), result.updatedAt());
    }
}
