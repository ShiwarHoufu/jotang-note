package com.wlf.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link TagService#resolveIds} 的按名解析与按名创建。
 *
 * <p>用例自造的标签名一律带 {@link #MARKER}：{@code tag.name} 上有 {@code uk_name}，
 * 而 data.sql 已种入真实标签，撞名会直接报 DuplicateKeyException。
 *
 * <p>用例随事务回滚。
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
