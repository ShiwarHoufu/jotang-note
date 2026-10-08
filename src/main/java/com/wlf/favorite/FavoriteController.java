package com.wlf.favorite;

import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.common.PageResponse;
import com.wlf.favorite.dto.FavoriteCountResponse;
import com.wlf.favorite.dto.FavoriteItemResponse;
import com.wlf.favorite.dto.FavoriteListQuery;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口：收藏 / 取消收藏 / 我的收藏（含占位）。
 * 见《概要设计》§5.5。
 */
@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * 收藏。
     *
     * <p>返回操作后的最新收藏量，前端直接拿去刷新计数.
     *
     * <p>没写成「PUT 幂等」而是 POST + 40902：重复收藏是要让用户知道的
     * （按钮该已经是「已收藏」态了），静默成功反而掩盖了前端状态没同步的问题。
     */
    @PostMapping("/api/notes/{id}/favorite")
    public ApiResponse<FavoriteCountResponse> favorite(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(new FavoriteCountResponse(favoriteService.favorite(user.userId(), id)));
    }

    /**
     * 取消收藏。
     *
     * <p><b>幂等</b>：本来就没收藏过也返回 200 与当前计数，不是 40400。
     * 只有笔记本身不存在才是 40400。
     *
     * <p>笔记已下架 / 已删除时<b>照常允许取消</b>：不然收藏列表里的占位项永远清不掉。
     * 这与收藏接口的 40301 不对称，理由见 {@code FavoriteService#unfavorite}。
     */
    @DeleteMapping("/api/notes/{id}/favorite")
    public ApiResponse<FavoriteCountResponse> unfavorite(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @PathVariable Long id) {
        return ApiResponse.ok(new FavoriteCountResponse(favoriteService.unfavorite(user.userId(), id)));
    }

    /**
     * 我的收藏，按收藏时间倒序分页。
     *
     * <p>参数收进 {@link FavoriteListQuery} 而不是散装 {@code @RequestParam}，
     * 否则校验失败会掉进兜底变成 50000
     */
    @GetMapping("/api/users/me/favorites")
    public ApiResponse<PageResponse<FavoriteItemResponse>> listFavorites(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ModelAttribute FavoriteListQuery query) {
        return ApiResponse.ok(favoriteService.listFavorites(user.userId(), query));
    }
}
