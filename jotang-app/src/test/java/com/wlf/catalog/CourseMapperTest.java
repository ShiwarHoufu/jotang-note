package com.wlf.catalog;

import com.wlf.entity.Course;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link Course} 与 course 表的列映射。
 *
 * <p>重点是 {@code isOther ↔ is_other} 这条隐含映射：它由 MyBatis-Plus 的
 * {@code tableUnderline} 与 MyBatis 的 {@code mapUnderscoreToCamelCase} 两个默认开关推导而来，
 * 实体上没有任何注解体现，编译器也查不出来——只能真连库跑一次 INSERT + SELECT 才算验证。
 *
 * <p>{@code @ActiveProfiles("local")} 取 application-local.yaml 的库连接（含 3307 端口）；
 * {@code @Transactional} 让用例结束自动回滚，不往库里留测试数据。
 *
 * <p><b>没有用 {@code @MybatisPlusTest} 切片</b>：mybatis-plus-spring-boot4-starter-test 3.5.17
 * 依赖的 {@code mybatis-plus-spring-boot-test-autoconfigure} 里，{@code @MybatisPlusTest}
 * 的元注解仍指向 Boot 3 的 {@code org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase}，
 * 该类在 Boot 4 已迁到 {@code org.springframework.boot.jdbc.test.autoconfigure} 包，
 * 原位置不存在。等 MyBatis-Plus 修掉这处再换回切片测试，可省去加载整个上下文。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CourseMapperTest {

    /**
     * 用例自造的名字必须带上这个标记。
     *
     * <p>{@code course.name} 上有 {@code uk_name}，而 data.sql 已经种了「微积分」「编译原理」
     * 等真实课程名——用例若直接插同名课程，会撞唯一索引报 DuplicateKeyException。
     * 加个种子数据不可能出现的前缀，就不必每次改种子都回头核对测试。
     */
    private static final String MARKER = "ZZTEST-MAPPER";

    @Autowired
    private CourseMapper courseMapper;

    @Test
    void allColumnsSurviveInsertThenSelect() {
        Course course = new Course();
        course.setName(MARKER + "-编译原理");
        course.setIsOther(0);

        courseMapper.insert(course);
        assertThat(course.getId()).isNotNull();

        Course loaded = courseMapper.selectById(course.getId());
        assertThat(loaded.getName()).isEqualTo(MARKER + "-编译原理");
        assertThat(loaded.getIsOther()).isZero();
    }
}
