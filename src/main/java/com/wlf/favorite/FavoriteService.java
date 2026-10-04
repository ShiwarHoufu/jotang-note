package com.wlf.favorite;

import org.springframework.stereotype.Service;

/**
 * 收藏业务：仅 ONLINE 可收藏；列表按 note.status 渲染「已下架/已删除」占位。
 * 见《概要设计》§6.5。
 */
@Service
public class FavoriteService {
}
