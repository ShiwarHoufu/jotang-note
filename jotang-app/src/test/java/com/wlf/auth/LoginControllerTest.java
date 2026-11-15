package com.wlf.auth;

import com.jayway.jsonpath.JsonPath;
import com.wlf.catalog.CollegeMapper;
import com.wlf.entity.College;
import com.wlf.entity.User;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录接口的 HTTP 层验证：Bean Validation、防枚举、限频锁定、账号状态与响应形状。
 *
 * <p>走真实请求：{@code @NotBlank} 只在 DispatcherServlet 生效，42300 的 {@code data}
 * 形状也只有序列化一遍才看得见。
 *
 * <p>{@link LoginAttemptLimiter} 是单例 Bean，状态活在整个上下文里，不受事务回滚影响，
 * 所以每个用例都用 {@link #uniqueName} 造自己的用户名，避免互相踩到对方的锁定计数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class LoginControllerTest {

    private static final String PASSWORD = "Passw0rd!23";
    private static final String MARKER = "zztest-login-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void blankCredentialsAreRejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[*].field").value(containsInAnyOrder("username", "password")));
    }

    /**
     * 防账号枚举（§2.2）：「用户不存在」与「密码错误」必须是同一响应——
     * 同一个 40101、同一句文案，连报文都逐字节相同，否则攻击者能据此判断用户名是否存在。
     */
    @Test
    void unknownUserAndWrongPasswordAreIndistinguishable() throws Exception {
        User user = insertUser(1);

        String unknownUser = login(uniqueName(), PASSWORD).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        String wrongPassword = login(user.getUsername(), "WrongPassw0rd!").andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(unknownUser).isEqualTo(wrongPassword);
        assertThat(JsonPath.<Integer>read(unknownUser, "$.code")).isEqualTo(40101);
    }

    @Test
    void successfulLoginReturnsTokenAndUserInfo() throws Exception {
        User user = insertUser(1);

        login(user.getUsername(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").value(user.getId()))
                .andExpect(jsonPath("$.data.user.username").value(user.getUsername()))
                .andExpect(jsonPath("$.data.user.nickname").value(user.getNickname()))
                .andExpect(jsonPath("$.data.user.collegeId").value(user.getCollegeId()))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.avatarUrl").value(nullValue()))
                // 密码哈希绝不能随出参外传
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    /** 登录发出去的 token 是一把真能用的凭证：拿它访问受保护接口应当通过（§6.4） */
    @Test
    void tokenFromLoginAuthenticatesAProtectedRequest() throws Exception {
        User user = insertUser(1);

        String body = login(user.getUsername(), PASSWORD).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = JsonPath.read(body, "$.data.token");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(user.getUsername()));
    }

    /**
     * 连续失败 5 次触发锁定，第 6 次直接被拒（§6.3）。
     *
     * <p>用<b>不存在的用户名</b>：限频发生在查库之前，拿谁的名字刷都会被锁——
     * 否则攻击者可以只挑不存在的名字试探而不留痕迹。
     */
    @Test
    void fiveConsecutiveFailuresLockTheAccountWith42300() throws Exception {
        String username = uniqueName();

        for (int i = 0; i < 5; i++) {
            login(username, "WrongPassw0rd!")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        login(username, "WrongPassw0rd!")
                .andExpect(status().isLocked())                 // 423
                .andExpect(jsonPath("$.code").value(42300))
                // data 带剩余秒数，供前端倒计时；锁定 10 分钟，落在 (0, 600]
                .andExpect(jsonPath("$.data").value(allOf(greaterThan(0), lessThanOrEqualTo(600))));
    }

    /**
     * 计数口径必须与数据库对齐（§2.3）：库的排序规则大小写不敏感，
     * 换着大小写、前后加空格试必须落在同一个计数槽，否则一绕就锁不住。
     */
    @Test
    void lockoutCountsCaseAndWhitespaceVariantsTogether() throws Exception {
        String username = uniqueName();

        for (int i = 0; i < 5; i++) {
            login(username.toUpperCase(Locale.ROOT), "WrongPassw0rd!")
                    .andExpect(status().isUnauthorized());
        }

        login("  " + username + "  ", "WrongPassw0rd!")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value(42300));
    }

    /** 成功登录清零失败计数：否则攒够阈值的账号在有正确密码的情况下也会被锁死 */
    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        User user = insertUser(1);

        for (int i = 0; i < 4; i++) {
            login(user.getUsername(), "WrongPassw0rd!").andExpect(status().isUnauthorized());
        }
        login(user.getUsername(), PASSWORD).andExpect(status().isOk());

        // 若成功未清零，这 4 次失败加上前面的 4 次会在第 1 次就凑满 5 触发锁定，
        // 后续几次应当变成 42300；全部停在 40101 才说明计数确实从头再来。
        for (int i = 0; i < 4; i++) {
            login(user.getUsername(), "WrongPassw0rd!")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40101));
        }
    }

    /** 禁用账号在密码通过之后被挡下 → 40300（§2.1 把状态检查排在密码之后） */
    @Test
    void disabledAccountIsRejectedWith40300() throws Exception {
        User user = insertUser(0);

        login(user.getUsername(), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.message").value("账号已被禁用"));
    }

    // ---- helpers ----------------------------------------------------------

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password)));
    }

    private static String uniqueName() {
        return MARKER + UUID.randomUUID().toString().substring(0, 8);
    }

    private User insertUser(int status) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + "college-" + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(uniqueName());
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setNickname(MARKER + "nick-" + suffix);
        user.setCollegeId(college.getId());
        user.setRole("USER");
        user.setStatus(status);
        userMapper.insert(user);
        return user;
    }
}
