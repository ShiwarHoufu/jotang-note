package com.wlf.note.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 列表 / 搜索的查询参数。见《概要设计》§5.4。
 *
 * <p><b>为什么是一个 {@code @ModelAttribute} 对象，而不是一串 {@code @RequestParam}</b>：
 * {@code GlobalExceptionHandler} 只处理了 {@code MethodArgumentNotValidException} 与
 * {@code ConstraintViolationException}。散装参数上挂 {@code @Min} 会让校验失败走
 * Spring 6.1 的 {@code HandlerMethodValidationException}——那个没人接，会掉进兜底变成 50000，
 * 把「页码填错了」伪装成「服务器炸了」。走 DTO + {@code @Valid} 则复用上传接口那条已验证过的
 * 链路，拿到的是 40001 加字段明细。类型转换失败（比如 {@code sort=bogus}、
 * {@code page=abc}）同样落成字段级绑定错误，不会变成 500。
 *
 * <p>默认值靠字段初始化而不是 {@code @RequestParam(defaultValue=...)}：参数缺席时
 * Spring 不去碰这个字段，初始化值就留住了。
 */
@Getter
@Setter
public class NoteListQuery {

    /** 课程筛选。null / 不传表示不筛 */
    private Long courseId;

    /** 标签筛选。null / 不传表示不筛。与 {@code courseId} 同时给就是「与」关系 */
    private Long tagId;

    /** 排序方式。不传时按最近更新。取值非法会在绑定阶段就被拦下，不会流到 SQL */
    private Sort sort = Sort.LATEST;

    @Min(value = 1, message = "页码从 1 开始")
    private int page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 50, message = "每页最多 50 条")
    private int size = 20;

    /**
     * 排序方式。<b>同时是 SQL 里排序字段的白名单</b>——见 {@code NoteMapper.xml} 的
     * {@code <choose>}：每个取值对应一段写死的 {@code ORDER BY}，全程不出现 {@code ${}}。
     * 这是必须的：{@code ORDER BY} 没法参数化，只能拼接，所以排序键绝不能来自用户输入的字面量。
     */
    public enum Sort {

        /** 最近更新：按 {@code updated_at} 倒序，即「用户最后编辑笔记的时间」（§3.3） */
        LATEST,

        /** 热门·浏览 */
        HOT_VIEW,

        /** 热门·下载 */
        HOT_DOWNLOAD
    }
}
