package com.wlf.auth;

import com.wlf.catalog.CollegeMapper;
import com.wlf.entity.College;
import com.wlf.entity.User;
import com.wlf.mail.MailService;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 密码找回两个接口的 HTTP 层验证。见《概要设计》§6.6。
 *
 * <p>走真实请求而非直接调 Service，理由同 {@code AuthControllerTest}：
 * {@code @Email} / {@code @Size} 这类 Bean Validation 只在 DispatcherServlet 里生效，
 * Service 层单测覆盖不到；响应体形状与 HTTP 状态码也只有序列化一遍才看得见。
 *
 * <p>发信换成 mock：本类验的是「线上收到的报文长什么样」，不是 SMTP。
 *
 * <p>每个用例用自己的邮箱——{@link PasswordResetRateLimiter} 是单例 Bean，
 * 状态活在整个上下文里，共用邮箱的第二个用例会先撞 42300。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class PasswordResetControllerTest {

    private static final String MARKER = "ZZTEST-PRC-";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MailService mailService;

    /**
     * 成功响应的形状：{@code code=0}、{@code message="ok"}、{@code data=null}。
     *
     * <p><b>「若邮箱存在，重置邮件已发送」不在这个报文里</b>——它是前端写死的提示文案。
     * 后端一旦按「邮箱存不存在」返回不同的话术，这个接口就成了一个免费的用户枚举器（§6.6）。
     * 本条刻意用一个<b>没注册过</b>的邮箱来验这件事。
     */
    @Test
    void forgotLooksIdenticalForAnUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                // data 为 null 时字段仍保留（ApiResponse 的约定），不是整个字段消失
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void forgotWithAMalformedEmailIsRejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("email"));
    }

    /**
     * 同一邮箱 5 分钟内第二次 → 42300（§6.3）。
     *
     * <p>用的是<b>未注册</b>邮箱，而这正是要点：限频发生在查库之前，
     * 所以拿别人邮箱刷也照样被限住——否则这个接口可以当成一个不用登录的邮件轰炸器。
     */
    @Test
    void repeatedForgotWithinTheWindowIsRejectedWith42300() throws Exception {
        String body = """
                {"email":"%s"}
                """.formatted(uniqueEmail());

        mockMvc.perform(post("/api/auth/password/forgot").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password/forgot").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isLocked())          // 423
                .andExpect(jsonPath("$.code").value(42300))
                .andExpect(jsonPath("$.data").isNumber()); // 剩余秒数，供前端倒计时
    }

    @Test
    void resetWithABlankTokenIsRejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"token":"","newPassword":"Passw0rd!23"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("token"));
    }

    /**
     * 新密码的长度下限必须与注册一致（8）：注册得进来的密码，找回时也得能设，
     * 否则用户会卡在「我的密码符合规则，但这里说太短」而又没有任何办法自己走出来。
     */
    @Test
    void resetWithATooShortPasswordIsRejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"token":"whatever","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("newPassword"));
    }

    @Test
    void resetWithAnUnknownTokenIsRejectedWith40001() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"token":"no-such-token-anywhere","newPassword":"Passw0rd!23"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    /** 走完整条链路：申请 → 从（mock 收到的）邮件里取令牌 → 重置 → 新密码能验上 */
    @Test
    void forgotThenResetThroughHttpChangesTheStoredPassword() throws Exception {
        User user = insertUser();

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"Passw0rd!23"}
                                """.formatted(captureEmailedToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String stored = userMapper.selectById(user.getId()).getPasswordHash();
        assertThat(passwordEncoder.matches("Passw0rd!23", stored)).isTrue();
    }

    // ---- helpers ----------------------------------------------------------

    private static String uniqueEmail() {
        return MARKER + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private User insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(uniqueEmail());
        user.setPasswordHash(passwordEncoder.encode("OldPassw0rd!"));
        user.setNickname(MARKER + suffix);
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user;
    }

    private String captureEmailedToken() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(anyString(), anyString(), body.capture());

        Matcher matcher = TOKEN_IN_LINK.matcher(body.getValue());
        assertThat(matcher.find()).as("邮件正文里应当有一条 ?token=… 的重置链接").isTrue();
        return matcher.group(1);
    }
}
