package com.wlf.common;

import java.util.List;

/**
 * 列表 / 搜索的公共分页守卫。见《概要设计》§3.3、§5.4。
 *
 * <p><b>为什么是共用的工具而不是各 Service 里各写一遍</b>：§3.3 说的是「分页深度上限对<b>所有</b>
 * 列表 / 搜索统一生效」，那就意味着这条规则只有一处定义。它此前是 {@code NoteService} 的私有方法，
 * 「我的收藏」落地时成了第二处调用方——规则一旦有第二份副本，
 * 「哪条适用于哪里」就开始靠记性，而这里的代价是用户看到的行为不一致（一个列表截断、另一个报错）。
 *
 * <p>放在 {@code common} 而不是某个模块里：它不是 note 的规则，也不是 favorite 的规则，
 * 是全部列表接口的共同约束，出错时产出的也是 {@code common} 的 40001 + {@link FieldViolation}。
 */
public final class Paging {

    /**
     * 分页深度上限：只能翻到前 1000 条（§3.3）。按默认 {@code size=20} 算就是最多 50 页。
     *
     * <p>管的是「页码」，不是「单页大小」——后者的守卫在各查询 DTO 的 {@code @Max(50)} 上。
     * 分页插件的 {@code maxLimit} 只能截断单页，管不住页码，所以这一条必须落在服务层。
     */
    public static final int MAX_PAGE_DEPTH = 1000;

    private Paging() {
    }

    /**
     * 分页深度守卫。超出时<b>报错而不是截断</b>：返回一页空白但 {@code total} 还写着很大的数，
     * 用户分不清是「翻得太深」还是「这一页恰好没数据」，前端也无从把「下一页」置灰。
     *
     * <p>乘法前把 {@code page} 提升为 {@code long}：{@code page} 只有下界没有上界，
     * {@code int × int} 在页码接近 {@code Integer.MAX_VALUE} 时会溢出成负数，
     * 于是「1020 &gt; 1000」变成「-20 &gt; 1000」为假——守卫形同虚设。
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
