package com.wlf.common;

import java.util.List;

/**
 * 列表 / 搜索的公共分页守卫。
 */
public final class Paging {

    /**
     * 分页深度上限：只能翻到前 1000 条。按默认 {@code size=20} 算就是最多 50 页。
     */
    public static final int MAX_PAGE_DEPTH = 1000;

    private Paging() {
    }

    /**
     * 分页深度守卫。超出时<b>报错而不是截断</b>：返回一页空白但 {@code total} 还写着很大的数，
     * 用户分不清是「翻得太深」还是「这一页恰好没数据」，前端也无从把「下一页」置灰。
     *
     * @throws BusinessException 40001，{@code data} 带 {@code field: page} 的字段明细
     */
    public static void requireShallow(int page, int size) {
        if ((long) page * size > MAX_PAGE_DEPTH) {
            String message = "分页过深，最多前 " + MAX_PAGE_DEPTH + " 条";
            throw new BusinessException(ErrorCode.PARAM_INVALID, message,
                    List.of(new FieldViolation("page", message)));
        }
    }
}
