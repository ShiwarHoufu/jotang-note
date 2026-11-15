package com.wlf.user;

/**
 * 头像准入判定的结果，由 {@link AvatarFilePolicy#inspect} 产出。
 *
 * <p>比 note 的 {@code FileDecision} 少一个 {@code originalName}：头像不还原原始文件名，
 * 落库的只有对象键（{@code user.avatar}），故没有这个字段可存，也不该为了对齐形状而留一个
 * 无人消费的字段。
 *
 * @param extension   已归一化为小写的扩展名，不含点，用于拼对象键
 * @param contentType 服务端判定值，写入 OSS 对象元数据；浏览器渲染头像时就按它认类型，
 *                    读取侧不覆写（§6.7）
 */
public record AvatarDecision(String extension, String contentType) {
}
