package com.wlf.auth;

import com.wlf.catalog.CollegeMapper;
import com.wlf.entity.College;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册接口的 HTTP 层验证（决策 D8）：字段级校验、错误码与响应体形状。
 *
 * <p>走真实请求而非直接调 Service，是因为 {@code @NotNull} 这类 Bean Validation
 * 只在 DispatcherServlet 里生效，Service 层单测覆盖不到；
 * data 的 JSON 形状（{@code [{field, message}]}）也只有序列化一遍才看得见。
 *
 * <p>用例随事务回滚，不往库里留注册出来的用户。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CollegeMapper collegeMapper;

    @Test
    void missingCollegeIdIsRejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"d8_http_missing","email":"d8_http_missing@example.com",
                                 "password":"Passw0rd!23"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("collegeId"))
                .andExpect(jsonPath("$.data[0].message").value("学院不能为空"));
    }

    @Test
    void unknownCollegeIdIsRejectedWithSameShape() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"d8_http_bad","email":"d8_http_bad@example.com",
                                 "password":"Passw0rd!23","collegeId":999999999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                // 与上面 @NotNull 失败时的形状一致，前端一套代码解析
                .andExpect(jsonPath("$.data[0].field").value("collegeId"))
                .andExpect(jsonPath("$.data[0].message").value("学院不存在"));
    }

    @Test
    void registerSucceedsAndEchoesCollegeId() throws Exception {
        Long collegeId = insertCollege("计算机学院");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"d8_http_ok","email":"d8_http_ok@example.com",
                                 "password":"Passw0rd!23","collegeId":%d}
                                """.formatted(collegeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("d8_http_ok"))
                .andExpect(jsonPath("$.data.collegeId").value(collegeId))
                // 只给 id 不给学院名（D8）；passwordHash 绝不能出现在响应里
                .andExpect(jsonPath("$.data.collegeName").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    /**
     * 字段名写成 snake_case 时，Jackson 认不出就**静默丢弃**，
     * 于是 collegeId 为 null，报出的是看似无关的「学院不能为空」。
     * 这条把该行为钉住：接口契约是 camelCase（与 avatarUrl 一致），不是数据库列名。
     */
    @Test
    void snakeCaseFieldIsSilentlyDropped() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"d8_http_snake","email":"d8_http_snake@example.com",
                                 "password":"Passw0rd!23","college_id":"1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("collegeId"))
                .andExpect(jsonPath("$.data[0].message").value("学院不能为空"));
    }

    /** collegeId 写成 JSON 字符串能不能收：确认 Jackson 的宽松转换行为 */
    @Test
    void collegeIdAsJsonString() throws Exception {
        Long collegeId = insertCollege("计算机学院");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"d8_http_str","email":"d8_http_str@example.com",
                                 "password":"Passw0rd!23","collegeId":"%d"}
                                """.formatted(collegeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collegeId").value(collegeId));
    }

    private Long insertCollege(String name) {
        College college = new College();
        college.setName(name);
        collegeMapper.insert(college);
        return college.getId();
    }
}
