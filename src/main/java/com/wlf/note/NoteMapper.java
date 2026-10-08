package com.wlf.note;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.entity.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
