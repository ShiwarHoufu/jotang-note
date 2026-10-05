package com.wlf.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.dto.CourseResponse;
import com.wlf.entity.Course;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link CourseService#list} 的关键词过滤与排序。
 *
 * <p>拼接条件出错不会抛异常，只会静默返回错误结果，所以这里逐条钉住行为。<br>
 * 断言刻意写成「包含某个刚插入的课程」而非「等于某个固定列表」，
 * 这样不受开发库里已有数据的影响。
 *
 * <p>数据随用例回滚，不留痕。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CourseServiceTest {

    /**
     * 用例自造的名字必须带上这个标记。
     *
     * <p>{@code course.name} 上有 {@code uk_name}，而 data.sql 已种入「微积分」「编译原理」
     * 等真实课程名——用例若直接插同名课程会撞唯一索引。加个种子数据不可能出现的前缀，
     * 以后往 data.sql 加课程也不必回头核对测试。
     */
    private static final String M = "ZZTEST-";

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseMapper courseMapper;

    @Test
    void keywordMatchesCourseName() {
        insert(M + "数据结构");
        insert(M + "编译原理");

        assertThat(namesOf(courseService.list(M + "编译")))
                .contains(M + "编译原理")
                .doesNotContain(M + "数据结构");
    }

    @Test
    void builtInOtherCourseSortsLast() {
        insert(M + "离散数学");

        Course other = courseMapper.selectOne(
                Wrappers.<Course>lambdaQuery().eq(Course::getIsOther, 1));
        assertThat(other).as("data.sql 应当已预置内置「其他」课程").isNotNull();

        List<String> names = namesOf(courseService.list(null));

        assertThat(names).contains(M + "离散数学");
        assertThat(names.get(names.size() - 1)).isEqualTo(other.getName());
    }

    @Test
    void blankKeywordIsTreatedAsNoFilter() {
        insert(M + "离散数学");

        assertThat(namesOf(courseService.list("   ")))
                .isEqualTo(namesOf(courseService.list(null)));
    }

    private void insert(String name) {
        Course course = new Course();
        course.setName(name);
        course.setIsOther(0);
        courseMapper.insert(course);
    }

    private static List<String> namesOf(List<CourseResponse> courses) {
        return courses.stream().map(CourseResponse::name).toList();
    }
}
