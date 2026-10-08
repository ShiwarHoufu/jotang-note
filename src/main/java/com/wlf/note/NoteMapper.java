package com.wlf.note;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.entity.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * note 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供。涉及多表 JOIN 的读路径——<b>目前是详情，
 * 随后的列表 / 热门 / 搜索同理</b>——走 {@code NoteMapper.xml}。
 * 依据是《概要设计》§1.1：跨模块不引用对方实体，但**检索侧的多表 JOIN 不受此限**，
 * 判定看的是「读路径」而不是接口名里有没有 search。
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 详情主干：note ⋈ note_file ⋈ course ⋈ user ⋈ college，一次连接出一行。
     *
     * <p>不筛 {@code status}：已下架的笔记也要查得出来（§4.1 的详情页要渲染「已下架」提示）。
     * 查不到只意味着 id 不存在，报 40400 由调用方决定。
     *
     * <p>{@code note_file} 那几个字段无论笔记什么状态都会带回来，是否对外暴露是
     * {@link NoteService#detail} 按 {@code status} 做的策略，不在这里裁剪——
     * mapper 只陈述事实。
     *
     * @return 该笔记的一行投影；id 不存在时为 {@code null}
     */
    NoteDetailRow selectDetail(@Param("id") Long id);

    /**
     * 某篇笔记的标签 id 与名称，按 {@code tag.id} 升序。
     *
     * <p>单独一条而不是并进主干：标签与笔记是 to-many，并进去会让 note 的列跟着标签重复。
     *
     * <p>这是 §1.1 意义上的一次跨模块 JOIN（{@code tag} 属于 catalog），
     * 与主干里连 {@code course} / {@code user} 是同一回事。
     */
    List<TagResponse> selectTagRefs(@Param("noteId") Long noteId);

    /**
     * 列表 / 搜索的一页。见 §5.4。
     *
     * <p>第一个参数必须是 {@code IPage}：{@link com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor}
     * 靠它识别出这是分页查询，并自动改写出一条 count 语句（用 {@code optimizeCountSql} 顺手去掉
     * {@code ORDER BY}）。条数不由调用方在这里传，而由 {@code page} 对象携带。
     *
     * <p><b>只返回 {@code ONLINE} 的笔记</b>——§4.1 的表里「列表/搜索」这一列对
     * OFFLINE / DELETED 是「不可见」。状态由调用方传入而不是在 SQL 里写死字面量，
     * 理由同 {@link #incrementViewCount}。
     *
     * <p>{@code sort} 传的是 {@code NoteListQuery.Sort} 的 {@code name()}，不是枚举本身：
     * XML 里要拿它跟字符串比较来选一段写死的 {@code ORDER BY}。<b>这样做是为了全程不出现
     * {@code ${}}</b>——{@code ORDER BY} 没法参数化，只能拼接，所以排序键必须来自白名单
     * 而不是用户输入的字面量。
     *
     * @param tagId 非 null 时按标签筛选。对应的 JOIN 带着 {@code tag_id = ?} 的等值条件，
     *              而 {@code note_tag} 的主键是 {@code (note_id, tag_id)}，所以每篇笔记至多匹配一行，
     *              <b>不会让分页的行数翻倍</b>——这一点是分页查询里最容易被忽略的坑
     * @return 一页数据；{@code total} 等由 MP 回填到返回的 {@code IPage} 上
     */
    IPage<NoteListRow> selectListPage(IPage<NoteListRow> page,
                                      @Param("status") String status,
                                      @Param("sort") String sort,
                                      @Param("courseId") Long courseId,
                                      @Param("tagId") Long tagId);

    /**
     * 一批笔记的标签，用于列表装配——<b>一次查一页，避免 N+1</b>。
     *
     * <p>入参必须非空：{@code IN ()} 是语法错误，调用方在空页时直接短路，不该走到这里。
     *
     * <p>排序按 {@code (note_id, tag_id)}：同一篇笔记的标签按 {@code tag.id} 升序
     * （理由同 {@link #selectTagRefs}），同时让结果按笔记聚在一起，便于分组时顺序稳定。
     */
    List<NoteTagRef> selectTagRefsByNoteIds(@Param("noteIds") Collection<Long> noteIds);

    /**
     * 浏览量原子自增，<b>仅当笔记处于传入的状态</b>。
     *
     * <p>写成「自增 + 守卫」合一的语句，而不是「先判断再自增」：§3.3 要求计数一律用
     * {@code SET x = x + 1} 的原子 UPDATE，读出来加一再写回会丢并发；把状态条件塞进同一个
     * {@code WHERE}，就省掉了一次独立的判断，也不存在两次语句之间的竞态窗口。
     *
     * <p>状态由调用方传入而不是在 SQL 里写死 {@code 'ONLINE'}：那是 {@link NoteStatus}
     * 的取值，写死在 SQL 字符串里就成了同一规则的又一份副本。
     *
     * <p>用注解而不是 XML：本语句不参与多表 JOIN，也没有动态分支，为它开一段 XML 不划算——
     * 与 {@link NoteTagMapper} 的分工一致。
     *
     * @param status 只有处于该状态的笔记才计数；详情页传 {@link NoteStatus#ONLINE}，
     *               于是「已下架 / 已删除不算浏览」这条规则由 SQL 自己保证
     * @return 受影响行数。0 表示笔记不存在、或状态不符、或另一个事务刚改过状态——
     *         三种情形对调用方是同一个结果「这次没计数」，故无需区分
     */
    @Update("UPDATE note SET view_count = view_count + 1 WHERE id = #{id} AND status = #{status}")
    int incrementViewCount(@Param("id") Long id, @Param("status") String status);

    /**
     * 收藏量原子自增。见 §3.3、§6.5。
     *
     * <p><b>为什么不放在 {@code FavoriteMapper}</b>：{@code favorite_count} 是 {@code note} 的列，
     * 与上面那条浏览量自增是同一类东西（note 的原子计数器），放在一起才是同质的位置。
     * 代价是 {@code FavoriteService} 要注入本 Mapper——这是 §1.1「不引用对方 Mapper」的一处违反，
     * 既存先例是 {@code AuthService} 直接注入 {@code UserMapper}（注册即往 user 表插行），
     * 形状相同：<b>写的是对方模块拥有的单表列，而对方的 Service 承接不了这个写</b>
     * （{@code NoteService} 已经注入 {@code FavoriteService}，再加一个反向入口就是构造器循环依赖）。
     * §1.1 的措辞已按此校正，见该节的「跨模块访问」。
     *
     * <p><b>这里刻意不带 {@code status} 守卫</b>，与 {@link #incrementViewCount} 不同：
     * 调用方 {@code FavoriteService#favorite} 已经在同一事务里用
     * {@link #selectStatusForUpdate} 拿到该行的 X 锁，状态在提交前不可能变，
     * 再写一遍 {@code AND status = ?} 只是把同一条规则说两遍。
     * 浏览量那条没有这个前提（详情页刻意不开事务），所以它必须自带守卫。
     */
    @Update("UPDATE note SET favorite_count = favorite_count + 1 WHERE id = #{id}")
    int incrementFavoriteCount(@Param("id") Long id);

    /**
     * 收藏量原子自减。
     *
     * <p>{@code favorite_count} 是派生值（§3.3 提供了一条离线校准 SQL 用于对账），
     * 因此它可能与 {@code favorite} 表的实际行数漂移。{@code AND favorite_count > 0} 是漂移时的
     * 兜底：计数值一旦为负，前端会渲染出「-1 人收藏」，而那是修不回来的观感问题，
     * 比少减一次严重得多——校准 SQL 迟早会把真实值算回来。
     *
     * @return 受影响行数。0 表示笔记不存在、或计数已经是 0——两种情形对调用方都是
     *         「这次没减」，无需区分
     */
    @Update("UPDATE note SET favorite_count = favorite_count - 1 WHERE id = #{id} AND favorite_count > 0")
    int decrementFavoriteCount(@Param("id") Long id);

    /**
     * 读某篇笔记的收藏量。收藏 / 取消收藏接口靠它把最新值回给前端。
     *
     * <p>为什么不直接用 {@code BaseMapper#selectById}：那会把标题、简介、教师等十几列一并读回来，
     * 只为拿一个计数。这里要的是「最新的收藏量」这一个事实，语句就该只陈述它。
     *
     * @return 收藏量；<b>笔记不存在时为 {@code null}</b>——调用方据此报 40400
     */
    @Select("SELECT favorite_count FROM note WHERE id = #{id}")
    Long selectFavoriteCount(@Param("id") Long id);

    /**
     * 锁住笔记行并读出状态，供收藏 / 取消收藏把「存在性 + 状态 + 排他锁」一次拿到。
     *
     * <p><b>这里的 {@code FOR UPDATE} 不是顺手加的，取锁顺序是收藏接口唯一的死锁防线。</b>
     * InnoDB 在检查 {@code fk_fav_note} 时会对父行 {@code note} 取隐式<b>共享</b>锁，
     * 所以「先 INSERT favorite 再 UPDATE note」的直觉写法会这样死锁：两个用户同时收藏同一篇笔记，
     * T1、T2 各持 S(note)，随后各自要 UPDATE note 把它升级成 X(note)，互等。
     * 热门笔记被多人收藏正是最现实的争用场景。
     *
     * <p>因此收藏与取消收藏<b>都先走这一条</b>拿到 X 锁，全站锁序统一为 note → favorite。
     * 取消收藏那里只用它的「行存在吗」，不用返回值——但要的正是同一把锁、同一个先后。
     *
     * @return 状态字符串；<b>笔记不存在时为 {@code null}</b>——调用方据此报 40400
     */
    @Select("SELECT status FROM note WHERE id = #{id} FOR UPDATE")
    String selectStatusForUpdate(@Param("id") Long id);
}
