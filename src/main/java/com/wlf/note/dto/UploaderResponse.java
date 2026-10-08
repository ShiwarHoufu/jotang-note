package com.wlf.note.dto;

/**
 * 笔记的上传者，详情与列表共用。见《概要设计》§5.4。
 *
 * <p><b>为什么是顶层类型而不是嵌在某个响应里</b>：详情页与列表页都要展示上传者，
 * 形状必须完全一致（前端一套类型、后端一套装配）。嵌在 {@link NoteDetailResponse}
 * 里会让列表项的类型写成 {@code NoteDetailResponse.Uploader}——方向反了，
 * 而且同一个东西在契约里声明了两处。与 {@code course} / {@code tags} 复用
 * {@code CourseResponse} / {@code TagResponse} 是同一条理由。
 *
 * @param avatarUrl   <b>拼好的完整公网 URL</b>，不是 OSS 对象键——§5.4 的统一约定：
 *                    只要接口展示上传者信息就返回可直接 {@code <img src>} 的地址，
 *                    前端无需二次请求签名或走代理。对象键留在 {@code user.avatar} 里不外露。
 *                    没设过头像时为 {@code null}
 * @param collegeName 上传者的学院（决策 D8）。放在这一层而不是顶层，因为它
 *                    <b>不是笔记的属性</b>——{@code note} 表不存学院，读的是上传者的
 *                    {@code user.college_id}。放在这里，等于把「改学院会回溯改变该用户
 *                    全部历史笔记的归属」这个事实写进了契约
 */
public record UploaderResponse(Long id, String nickname, String avatarUrl, String collegeName) {
}
