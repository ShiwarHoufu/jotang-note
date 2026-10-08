package com.wlf.note.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 「我的上传」的查询参数。见《概要设计》§5.4。
 *
 * <p><b>为什么不复用 {@link NoteListQuery}</b>：那边还带着 {@code sort} / {@code courseId} /
 * {@code tagId}，这三个在本列表里没有意义（排序固定为上传时间倒序，见下）。复用的后果不是
 * 「多几个用不上的字段」，而是接口悄悄接受了它们——前端传 {@code sort=hot_view} 会拿到 200
 * 和一个排序完全没变的列表，排查起来要一路读到 SQL 才明白它被忽略了。窄的入参让「不支持」
 * 这件事在参数绑定阶段就显形。这与 {@code FavoriteListQuery} 不复用 {@code NoteListQuery} 是同一条理由。
 *
 * <p><b>排序不开放成参数</b>：本列表只有一种合理顺序，即上传时间倒序（最近传的在最前）。
 * 加一个 {@code sort} 参数就得配一份 {@code ORDER BY} 白名单，而这里没有第二种排序需求
 * （与 {@code FavoriteListQuery} 同理）。
 *
 * <p><b>也没有状态筛选</b>：{@code DELETED} 无条件排除（那是本列表的定义），
 * 剩下的 {@code ONLINE} 与 {@code OFFLINE} 一起返回，由前端按 {@code status} 分组展示。
 *
 * <p><b>为什么是 {@code @ModelAttribute} 对象而不是两个 {@code @RequestParam}</b>：
 * {@code GlobalExceptionHandler} 只处理了 {@code MethodArgumentNotValidException} 与
 * {@code ConstraintViolationException}。散装参数上挂 {@code @Min} 会让校验失败走 Spring 6.1 的
 * {@code HandlerMethodValidationException}——那个没人接，会掉进兜底变成 50000，
 * 把「页码填错了」伪装成「服务器炸了」。走 DTO + {@code @Valid} 则复用上传接口那条已验证过的链路，
 * 拿到的是 40001 加字段明细。类型转换失败（{@code page=abc}）同样落成字段级绑定错误。
 *
 * <p>默认值靠字段初始化而不是 {@code @RequestParam(defaultValue=...)}：
 * 参数缺席时 Spring 不去碰这个字段，初始化值就留住了。
 *
 * <p>上界只管到单页大小与页码下界，<b>页码上界不在这里</b>——那是 {@code page × size} 的
 * 联合约束，只能在服务层用 {@code Paging.requireShallow} 判（插件的 {@code maxLimit} 管不住页码）。
 */
@Getter
@Setter
public class MyNoteListQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private int page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 50, message = "每页最多 50 条")
    private int size = 20;
}
