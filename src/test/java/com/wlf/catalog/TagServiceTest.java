package com.wlf.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link TagService#resolveIds} 的按名解析与按名创建，以及
 * {@link TagService#list} 的候选列表口径。
 *
 * <p>用例自造的标签名一律带 {@link #MARKER}：{@code tag.name} 上有 {@code uk_name}，
 * 而 data.sql 已种入真实标签，撞名会直接报 DuplicateKeyException。
 *
 * <p>用例随事务回滚。
 *
 * <p><b>候选列表那几条有同一条前提：本地库的标签总数没到上限。</b>几个「没人用」的标签
 * 排在候选列表最末尾，库里的标签一旦超过上限就会先被截掉。本机数据量远没到这个数，
 * 真撞上了说明该回头审视上限本身，让它红比让它悄悄跳过有用。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class TagServiceTest {

    private static final String MARKER = "ZZTEST-";

    @Autowired
    private TagService tagService;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void resolvesExistingTagToItsId() {
        Long existingId = insertTag(MARKER + "已有");

        assertThat(tagService.resolveIds(List.of(MARKER + "已有"))).containsExactly(existingId);
    }

    @Test
    void createsUnknownTagOnTheFly() {
        List<Long> ids = tagService.resolveIds(List.of(MARKER + "新标签"));

        assertThat(ids).hasSize(1);
        assertThat(countByName(MARKER + "新标签")).isEqualTo(1);
        assertThat(ids.get(0)).isEqualTo(idOf(MARKER + "新标签"));
    }

    /** 同名复用：第二次解析不能再建一行，必须拿回同一个 id */
    @Test
    void reusesTheSameRowOnSecondResolve() {
        List<Long> first = tagService.resolveIds(List.of(MARKER + "复用"));
        List<Long> second = tagService.resolveIds(List.of(MARKER + "复用"));

        assertThat(second).isEqualTo(first);
        assertThat(countByName(MARKER + "复用")).isEqualTo(1);
    }

    @Test
    void mixedExistingAndNewAreBothResolved() {
        Long existingId = insertTag(MARKER + "旧");

        List<Long> ids = tagService.resolveIds(List.of(MARKER + "旧", MARKER + "新"));

        assertThat(ids).containsExactly(existingId, idOf(MARKER + "新"));
    }

    /** 去重按「去空白之后」判定，否则「 标签 A」与「标签 A」会各建一行 */
    @Test
    void duplicateNamesCollapseIntoOne() {
        List<Long> ids = tagService.resolveIds(List.of(MARKER + "重复", " " + MARKER + "重复 "));

        assertThat(ids).hasSize(1);
        assertThat(countByName(MARKER + "重复")).isEqualTo(1);
    }

    @Test
    void blankAndNullNamesAreDropped() {
        assertThat(tagService.resolveIds(Arrays.asList(null, "   ", ""))).isEmpty();
        assertThat(tagService.resolveIds(null)).isEmpty();
    }

    /** 返回的 id 顺序必须与入参去重后的顺序一致：前端按打标顺序渲染标签 */
    @Test
    void returnedIdsFollowInputOrder() {
        Long first = insertTag(MARKER + "甲");
        Long second = insertTag(MARKER + "乙");

        assertThat(tagService.resolveIds(List.of(MARKER + "乙", MARKER + "甲")))
                .containsExactly(second, first);
    }

    @Test
    void overlongNameIsRejected() {
        String tooLong = MARKER + "长".repeat(40);

        assertThatThrownBy(() -> tagService.resolveIds(List.of(tooLong)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    // data 携带字段级明细，前端据此把标签输入框标红（§5.7）
                    assertThat(e.getData()).isEqualTo(List.of(new FieldViolation("tags", e.getMessage())));
                });
        assertThat(countByName(tooLong)).isZero();
    }

    @Test
    void tooManyTagsAreRejected() {
        List<String> eleven = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> MARKER + i)
                .toList();

        assertThatThrownBy(() -> tagService.resolveIds(eleven))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));

        // 校验在写库之前完成，不能出现「拒了请求却留下一半标签」
        assertThat(countByName(MARKER + "0")).isZero();
    }

    // ==================== 候选列表 ====================

    /**
     * 上限真的生效：造出超过上限的标签，返回的仍只有上限个。
     *
     * <p>不写成「造 5 个、拿到 5 个」——那在没有上限时也照样通过。先把总数顶过上限，
     * 断言才有意义；{@code 20} 是 {@code TagService.CANDIDATE_LIMIT} 的取值，
     * 它是私有的，这里只能照抄一份。
     */
    @Test
    void listIsCappedAtTheCandidateLimit() {
        insertTags(40);
        assertThat(tagMapper.selectCount(null)).isGreaterThan(20);

        assertThat(tagService.list()).hasSize(20);
    }

    /**
     * 用过的排在没用过的之前——这条盯「按使用量倒序」。
     *
     * <p>只断言自己造的那三个标签之间的<b>相对</b>顺序，不碰绝对位置：本地库里可能有
     * 使用量更高的标签插在中间（与 {@code CatalogControllerTest} 用 {@code hasItem} 同规矩）。
     */
    @Test
    void mostUsedTagsComeFirst() {
        Long hot = insertTag(MARKER + "热门");
        Long warm = insertTag(MARKER + "一般");
        Long cold = insertTag(MARKER + "没人用");
        attachNotes(hot, 3);
        attachNotes(warm, 1);

        List<String> names = namesOfList();

        assertThat(names).contains(MARKER + "热门", MARKER + "一般", MARKER + "没人用");
        assertThat(names.indexOf(MARKER + "热门")).isLessThan(names.indexOf(MARKER + "一般"));
        assertThat(names.indexOf(MARKER + "一般")).isLessThan(names.indexOf(MARKER + "没人用"));
    }

    /**
     * 一个 {@code note_tag} 行都没有的标签，<b>仍然在候选里</b>，只是排在用过之后。
     *
     * <p>这条是 {@code LEFT JOIN} 那个选择的守卫，也是本次改动的核心风险点：换成
     * {@code INNER JOIN} 的话，data.sql 种下的这四个常用标签会因为没人用过而整个消失，
     * 「预置一批候选标签」就成了空话——而这件事在有真实数据之前完全看不出来。
     * 所以这里把种子里那四个名字写死。
     */
    @Test
    void unusedSeededTagsAreStillCandidates() {
        assertThat(namesOfList()).contains("期末复习", "课堂笔记", "习题解答", "实验报告");
    }

    // ==================== 辅助 ====================

    private List<String> namesOfList() {
        return tagService.list().stream().map(TagResponse::name).toList();
    }

    private void insertTags(int count) {
        for (int i = 0; i < count; i++) {
            insertTag(MARKER + "批量" + i);
        }
    }

    /**
     * 给标签挂上若干篇笔记，撑出使用量。
     *
     * <p>走原生 SQL 而不走 Mapper：这里要的只是「note 与 note_tag 各有一行」，
     * 走实体类还得先备好学院 / 课程 / 上传者的 id，反而是更长的路。
     *
     * <p>每次调用建一个上传者——{@code uk_email} 挡着，同一个测试里不能共用。
     * 用 {@code tagId} 当后缀，天然不会重复。
     */
    private void attachNotes(Long tagId, int count) {
        Long uploaderId = insertUploader(tagId);
        Long courseId = jdbcTemplate.queryForObject("SELECT id FROM course LIMIT 1", Long.class);

        for (int i = 0; i < count; i++) {
            String title = MARKER + "笔记-" + tagId + "-" + i;
            jdbcTemplate.update("INSERT INTO note (uploader_id, course_id, title) VALUES (?, ?, ?)",
                    uploaderId, courseId, title);
            // 用刚写的标题查回来，不依赖 LAST_INSERT_ID()：那是连接级的，
            // 连接池下要它成立得先证明两次调用走的是同一条连接
            Long noteId = jdbcTemplate.queryForObject(
                    "SELECT id FROM note WHERE title = ?", Long.class, title);
            jdbcTemplate.update("INSERT INTO note_tag (note_id, tag_id) VALUES (?, ?)", noteId, tagId);
        }
    }

    /** data.sql 只种了学院与课程，没有用户，上传者得自己造一个 */
    private Long insertUploader(Long tagId) {
        Long collegeId = jdbcTemplate.queryForObject("SELECT id FROM college LIMIT 1", Long.class);
        String username = MARKER + "u" + tagId;
        jdbcTemplate.update("""
                        INSERT INTO `user` (username, email, password_hash, nickname, college_id)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                username, username + "@example.com", "not-a-real-hash", MARKER + "u", collegeId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, username);
    }

    private Long insertTag(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tagMapper.insert(tag);
        return tag.getId();
    }

    private Long idOf(String name) {
        return tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name)).getId();
    }

    private long countByName(String name) {
        return tagMapper.selectCount(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name));
    }
}
