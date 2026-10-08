package com.wlf.favorite.dto;

/**
 * 收藏 / 取消收藏的出参。见《概要设计》§5.5。
 *
 * <p><b>为什么要回一个计数，而不是空 {@code data}</b>：这个数在详情页与列表页的卡片上都要显示，
 * 而并发（或用户连点）之下「本地 ±1」很容易和真实值漂移，且漂移之后没有任何一次请求会把它纠正回来——
 * 详情页不会再刷。由服务端在同一个事务里回读一次最新值，前端拿到什么就显示什么。
 *
 * <p>代价是收藏链路多一次 {@code SELECT favorite_count}。这次读和写自增在同一个事务内、
 * 且调用方已持有该行的 X 锁，所以读到的必然是自己这次操作之后的值，不会出现「回了旧值」。
 *
 * <p>只有一个字段也要包一层 record，而不是直接返回裸 {@code Long}：裸值在 JSON 里是
 * {@code {"data":6}}，前端读的是 {@code data.favoriteCount} 还是 {@code data} 全靠猜，
 * 将来要补第二个字段（比如 {@code isFavorited}）就成了破坏性变更。多一个字段名是非破坏性的。
 *
 * @param favoriteCount 操作后的最新收藏量。取消收藏时若该笔记已不存在，请求早在读状态那步
 *                      就以 40400 结束，不会走到这里，故本字段恒有值
 */
public record FavoriteCountResponse(long favoriteCount) {
}
