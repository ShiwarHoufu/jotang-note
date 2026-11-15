package com.wlf.user;

import com.wlf.catalog.CollegeMapper;
import com.wlf.common.JwtTokenProvider;
import com.wlf.entity.College;
import com.wlf.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /api/users/me} 的 HTTP 层验证：路由、参数绑定、鉴权与响应形状。
 * 见《概要设计》§5.2。
 *
 * <p>走真实请求而非直接调 Service，是因为 {@code @NotBlank} / {@code @NotNull} / {@code @Size}
 * 这类 Bean Validation 只在 DispatcherServlet 里生效，Service 层单测覆盖不到；
 * data 的 JSON 形状（{@code [{field, message}]}）也只有序列化一遍才看得见。
 * 业务与落库细节归 {@link UserProfileServiceTest}，这里不重复断言。
 *
 * <p>用例随事务回滚，不往库里留造出来的用户。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserControllerTest {

    private static final String MARKER = "ZZTEST-PROF-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    // ==================== 鉴权 ====================

    /** {@code /api/users/me} 不在 SecurityConfig 的放行名单里，匿名一律 40100 */
    @Test
    void updatingProfileRequiresLogin() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(insertCollege(), "新昵称")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ==================== 正常路径 ====================

    /**
     * 改完回显更新后的资料，形状与 {@code GET /api/auth/me} 完全一致。
     *
     * <p>{@code avatarUrl} 目前恒为 null（头像接口未做，见 {@link com.wlf.auth.dto.UserInfo}），
     * 这条顺带把它钉住——哪天头像切片落地，会在这里红，提醒改的人一并更新契约。
     */
    @Test
    void profileUpdateEchoesNewValues() throws Exception {
        Long collegeId = insertCollege();
        Long userId = insertUser(collegeId, "旧昵称");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(collegeId, "新昵称"))
                        .header("Authorization", bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.collegeId").value(collegeId))
                // UserInfo 只给 collegeId 不给学院名（D8），前端按既有 id→name 映射渲染
                .andExpect(jsonPath("$.data.collegeName").doesNotExist())
                .andExpect(jsonPath("$.data.avatarUrl").value(nullValue()))
                // 密码哈希与邮箱绝不能出现在响应里——UserInfo 刻意不含这两项
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    // ==================== 昵称校验 ====================

    @Test
    void missingNicknameIsRejected() throws Exception {
        assertNicknameViolation("{\"collegeId\":%d}".formatted(insertCollege()));
    }

    /** 全空格过不了 {@code @NotBlank} */
    @Test
    void blankNicknameIsRejected() throws Exception {
        assertNicknameViolation(body(insertCollege(), "   "));
    }

    /** 列宽 32，超一个字符就该被拦下而不是撞库 */
    @Test
    void tooLongNicknameIsRejected() throws Exception {
        assertNicknameViolation(body(insertCollege(), "字".repeat(33)));
    }

    // ==================== 学院校验 ====================

    @Test
    void missingCollegeIdIsRejected() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\"}")
                        .header("Authorization", bearer(insertUser(insertCollege(), "旧昵称"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("collegeId"))
                .andExpect(jsonPath("$.data[0].message").value("学院不能为空"));
    }

    /**
     * 学院不存在走 Service 的查库判断，与 {@code @NotNull} 失败**同形状**（都是 40001 + 字段明细），
     * 前端一套代码解析。文案与 {@code AuthService} 注册时一致。
     */
    @Test
    void unknownCollegeIdIsRejectedWithSameShape() throws Exception {
        assertCollegeViolation("999999999");
    }

    /**
     * {@code 0} 与负数被 {@code CollegeService#exists} 判否，落到「学院不存在」而不是别的文案。
     *
     * <p>这条钉住「刻意没加 {@code @Positive}」：加了的话这里会变成
     * {@code @Positive} 的默认文案，同一个字段就出现了第二种形状。
     */
    @Test
    void nonPositiveCollegeIdFallsIntoCollegeNotFound() throws Exception {
        assertCollegeViolation("0");
        assertCollegeViolation("-1");
    }

    // ==================== 边界 ====================

    /** token 有效但指向一个库里没有的用户：40400，与服务层 {@code currentUser} 同口径 */
    @Test
    void tokenForMissingUserIsNotFound() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(insertCollege(), "新昵称"))
                        .header("Authorization", bearer(999999999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /**
     * 请求体里塞别人的 {@code id} / {@code userId} 改不动别人。
     *
     * <p>{@code UpdateProfileRequest} 没有这两个字段，Jackson 默认对未知字段静默丢弃，
     * 所以它们连绑定都进不去；主体只来自 JWT。这条把「改的是谁由 principal 决定」
     * 用一个真请求钉住，而不是只相信字段清单。
     */
    @Test
    void extraUserIdInBodyDoesNotRetarget() throws Exception {
        Long collegeId = insertCollege();
        Long victimId = insertUser(collegeId, "受害者");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"攻击者改的名","collegeId":%d,"id":%d,"userId":%d}
                                """.formatted(collegeId, victimId, victimId))
                        .header("Authorization", bearer(insertUser(collegeId, "攻击者"))))
                .andExpect(status().isOk());

        assertThat(userMapper.selectById(victimId).getNickname()).isEqualTo("受害者");
    }

    // ==================== 辅助 ====================

    private void assertNicknameViolation(String json) throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .header("Authorization", bearer(insertUser(insertCollege(), "旧昵称"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("nickname"));
    }

    private void assertCollegeViolation(String collegeId) throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(collegeId, "新昵称"))
                        .header("Authorization", bearer(insertUser(insertCollege(), "旧昵称"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("collegeId"))
                .andExpect(jsonPath("$.data[0].message").value("学院不存在"));
    }

    private static String body(Long collegeId, String nickname) {
        return body(String.valueOf(collegeId), nickname);
    }

    /** collegeId 用字符串拼接，好让 0 / 负数这类非法值也能走同一个入口 */
    private static String body(String collegeId, String nickname) {
        return "{\"nickname\":\"%s\",\"collegeId\":%s}".formatted(nickname, collegeId);
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.issue(userId, MARKER + "user", "USER");
    }

    private Long insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser(Long collegeId, String nickname) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(nickname);
        user.setCollegeId(collegeId);
        userMapper.insert(user);
        return user.getId();
    }
}
