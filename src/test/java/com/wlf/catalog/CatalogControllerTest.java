package com.wlf.catalog;

import com.wlf.common.JwtTokenProvider;
import com.wlf.entity.Course;
import com.wlf.entity.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * catalog 三个接口的 HTTP 层验证：路由、参数绑定、鉴权差异与响应体形状。
 *
 * <p>token 用真实的 {@link JwtTokenProvider} 签，而不是打桩——
 * 这样顺带把 JwtAuthFilter 的解析链路也走到了；这些接口不读 principal，
 * 所以 userId 随便给一个不存在的值也不影响。
 *
 * <p>用例自造的课程名/标签名一律带 {@link #MARKER}：
 * data.sql 里已有真实数据，且 course.name 与 tag.name 上都有唯一约束，
 * 撞名会直接报 DuplicateKeyException。
 *
 * <p>用例随事务回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CatalogControllerTest {

    private static final String MARKER = "ZZTEST-D8";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void coursesRequireLogin() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void tagsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /** 与上面两条相反：学院列表必须免登录，否则注册页取不到下拉数据 */
    @Test
    void collegesArePublic() throws Exception {
        mockMvc.perform(get("/api/colleges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void coursesFilterByKeywordAndExposeOnlyIdAndName() throws Exception {
        insertCourse(MARKER + "-编译原理");

        mockMvc.perform(get("/api/courses")
                        .param("keyword", MARKER)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value(MARKER + "-编译原理"))
                // 两个不该外传的字段：isOther 是「系统怎么处理这条记录」，
                // 「其他」已由后端排序解决，前端不需要自己判断
                .andExpect(jsonPath("$.data[0].isOther").doesNotExist())
                .andExpect(jsonPath("$.data[0].college").doesNotExist());
    }

    @Test
    void tagsExposeIdAndName() throws Exception {
        insertTag(MARKER + "-标签");

        mockMvc.perform(get("/api/tags").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 种子里已有若干标签，用 hasItem 断言包含而不锁定位置
                .andExpect(jsonPath("$.data[*].name", hasItem(MARKER + "-标签")))
                .andExpect(jsonPath("$.data[*].id").exists());
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.issue(1L, "catalog_tester", "USER");
    }

    private void insertCourse(String name) {
        Course course = new Course();
        course.setName(name);
        course.setIsOther(0);
        courseMapper.insert(course);
    }

    private void insertTag(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tagMapper.insert(tag);
    }
}
