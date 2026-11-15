package com.wlf.common;

import java.util.List;

/**
 * 分页响应的统一外壳。
 *
 * <p><b>为什么不直接把 MyBatis-Plus 的 {@code IPage} 序列化出去</b>：那会把
 * {@code optimizeCountSql}、{@code searchCount}、{@code orders}、{@code maxLimit} 这些
 * 纯粹描述「插件怎么干活」的字段一并吐给前端，等于让接口契约被分页实现绑架——
 * 哪天换掉 MP 的分页插件，契约就跟着变了。这里只留四个前端真正要用的东西。
 *
 * @param items 当前页的数据；空页时是空列表而不是 null，前端可以无条件遍历
 * @param total 符合条件的总条数，与分页深度上限无关
 * @param page  当前页码，从 1 开始（与前端分页组件一致，不是 MP {@code IPage} 内部的 current 语义）
 * @param size  每页条数
 */
public record PageResponse<T>(List<T> items, long total, long page, long size) {
}
