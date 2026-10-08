package com.wlf.note.dto;

/**
 * 上传成功的出参。见《概要设计》§5.4。
 *
 * <p>只回一个 id：上传后前端要么跳到详情页，要么把新笔记插进「我的上传」列表，
 * 两条路都只需要知道新建笔记的 id，没必要在创建接口里回一份完整详情
 * （详情还牵涉「是否已收藏」这类与创建无关的查询）。
 *
 * <p>包成对象而不是直接返 {@code Long}：响应体的 {@code data} 保持恒为 JSON 对象，
 * 前端拦截器与 Apifox 示例不必为这一个接口破例处理裸标量。
 */
public record NoteCreatedResponse(Long id) {
}
