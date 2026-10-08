package com.wlf.common;

import java.util.List;

/**
 * 分页响应的统一外壳。见《概要设计》§5.4。
 *
 * <p><b>为什么不直接把 MyBatis-Plus 的 {@code IPage} 序列化出去</b>：那会把
 * {@code optimizeCountSql}、{@code searchCount}、{@code orders}、{@code maxLimit} 这些
 * 纯粹描述「插件怎么干活」的字段一并吐给前端，等于让接口契约被分页实现绑架——
 * 哪天换掉 MP 的分页插件，契约就跟着变了。这里只留四个前端真正要用的东西。
 *
 * <p>{@code total} 是<b>真实总数</b>，不封顶：前端 Element Plus 的分页器要它算页数，
 * 首页「共 N 篇笔记」也直接拿它。虽然分页深度被限制在前 1000 条（§3.3），
 * 但这个数本身不截断——否则「共 1000 篇」与「共 3421 篇」看起来一样，用户无从判断。
 *
 * @param items 当前页的数据；空页时是空列表而不是 null，前端可以无条件遍历
 * @param total 符合条件的总条数，与分页深度上限无关
 * @param page  当前页码，从 1 开始（与前端分页组件一致，不是 MP {@code IPage} 内部的 current 语义）
 * @param size  每页条数
 */
public record PageResponse<T>(List<T> items, long total, long page, long size) {
}
