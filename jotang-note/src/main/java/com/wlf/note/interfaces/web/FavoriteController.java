package com.wlf.note.interfaces.web;

import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.common.PageResponse;
import com.wlf.note.application.command.FavoriteNoteCommand;
import com.wlf.note.application.service.FavoriteService;
import com.wlf.note.application.query.FavoriteListQuery;
import com.wlf.note.application.result.FavoriteItemResult;
import com.wlf.note.application.result.PageResult;
import com.wlf.note.interfaces.web.assembler.FavoriteAssembler;
import com.wlf.note.interfaces.web.dto.FavoriteCountResponse;
import com.wlf.note.interfaces.web.dto.FavoriteItemResponse;
import com.wlf.note.interfaces.web.dto.FavoriteListRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 收藏接口：收藏 / 取消收藏 / 我的收藏。 */
@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final FavoriteAssembler favoriteAssembler;

    public FavoriteController(FavoriteService favoriteService,
                              FavoriteAssembler favoriteAssembler) {
        this.favoriteService = favoriteService;
        this.favoriteAssembler = favoriteAssembler;
    }

    @PostMapping("/api/notes/{id}/favorite")
    public ApiResponse<FavoriteCountResponse> favorite(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(favoriteAssembler.toResponse(
                favoriteService.favorite(new FavoriteNoteCommand(user.userId(), id))));
    }

    @DeleteMapping("/api/notes/{id}/favorite")
    public ApiResponse<FavoriteCountResponse> unfavorite(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(favoriteAssembler.toResponse(
                favoriteService.unfavorite(new FavoriteNoteCommand(user.userId(), id))));
    }

    @GetMapping("/api/favorites/mine")
    public ApiResponse<PageResponse<FavoriteItemResponse>> listFavorites(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ModelAttribute FavoriteListRequest request) {
        FavoriteListQuery query = favoriteAssembler.toQuery(request);
        PageResult<FavoriteItemResult> result = favoriteService.listFavorites(user.userId(), query);
        return ApiResponse.ok(new PageResponse<>(
                result.items().stream().map(favoriteAssembler::toResponse).toList(),
                result.total(), result.page(), result.size()));
    }
}
