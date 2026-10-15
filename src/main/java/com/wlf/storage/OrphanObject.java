package com.wlf.storage;

/**
 * 一条待清理的孤儿对象，也就是消息的载荷。见《功能说明：笔记上传》§2.5。
 *
 * <p>只带桶与对象键，<b>不带 noteId</b>：这条链路要清的正是「入库失败、库里没有行」的对象，
 * 带上它只会诱导消费者去查一张查不到的库表。桶留在载荷里，是为了让将来旧头像对象的清理
 * 能复用同一条队列而不必改消息格式。
 */
public record OrphanObject(Bucket bucket, String storageKey) {
}
