package com.wlf.storage;

/**
 * 孤儿对象的上报口：把「这个 key 已是垃圾、得有人来删」记下来。见《功能说明：笔记上传》§2.5。
 *
 * <p>它会成为接口，是为了「投 MQ」这件事能整体关掉（{@code jotang.purge.mode}）：内联那条
 * {@link StorageService#delete} 由调用方自己走，本接口只是第二道兜底。补的是定时对账够不着的那类
 * 孤儿——{@code note_file} 里没有行，对账的候选集扫不到。
 *
 * <p>实现<b>不吞异常</b>：投递失败要不要影响这次操作，是调用方的决定。与 {@code QueuedMailService}
 * 同一条规矩。
 */
public interface OrphanObjectPublisher {

    void publish(Bucket bucket, String storageKey);
}
