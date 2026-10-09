package com.wlf.user;

import com.wlf.catalog.CollegeMapper;
import com.wlf.common.JwtTokenProvider;
import com.wlf.entity.College;
import com.wlf.entity.User;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/users/me/avatar} 的 HTTP 层验证：路由、参数绑定、鉴权与响应形状。
 * 见《概要设计》§5.2、§6.7。
 *
 * <p>走真实请求而非直接调 Service，是因为「缺文件 → 40001 而不是 50000」这类行为只在
 * DispatcherServlet 的参数绑定与校验里体现（见 {@code AvatarUploadRequest} 的类注释），
 * 业务与落库细节归 {@link UserAvatarServiceTest}，这里不重复断言。
 *
 * <p>用例随事务回滚，不往库里留造出来的用户。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserAvatarControllerTest {

    private static final String MARKER = "ZZTEST-AVATAR-HTTP-";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @MockitoBean
    private StorageService storageService;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = insertUser();

        when(storageService.upload(any(), anyString(), any(), anyLong(), anyString(), anyString()))
                .thenAnswer(invocation -> new StoredObject(
                        invocation.getArgument(1, String.class),
                        invocation.getArgument(4, String.class),
                        invocation.getArgument(3, Long.class)));
        when(storageService.publicUrl(eq(Bucket.AVATAR), anyString()))
                .thenAnswer(invocation -> "https://avatar.example/" + invocation.getArgument(1, String.class));
    }

    // ==================== 鉴权 ====================

    /** {@code /api/users/me/avatar} 不在 SecurityConfig 的放行名单里，匿名一律 40100 */
    @Test
    void uploadingAvatarRequiresLogin() throws Exception {
        mockMvc.perform(multipart("/api/users/me/avatar").file(png()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ==================== 参数绑定 ====================

    /** 忘了选文件是客户端错误，必须是 40001 带字段明细，不能掉进兜底的 50000 */
    @Test
    void missingFileIsRejectedWithFieldDetail() throws Exception {
        mockMvc.perform(multipart("/api/users/me/avatar").header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("file"));
    }

    /** svg 会在公共读桶的 OSS 域名下形成存储型 XSS，必须在入口拒掉（§6.7、§8.3） */
    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(new MockMultipartFile("file", "evil.svg", "image/svg+xml",
                                "<svg onload=alert(1)>".getBytes()))
                        .header("Authorization", token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42200));
    }

    /** 头像上限 2MB，远超要给出 42200 而不是 50000 */
    @Test
    void oversizeFileIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(new MockMultipartFile("file", "大图.png", "image/png", pngOfSize(2 * 1024 * 1024 + 1)))
                        .header("Authorization", token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42200));
    }

    // ==================== 正常路径 ====================

    /** 出参是完整的 {@code UserInfo}，其中 {@code avatarUrl} 由新落库的对象键拼出 */
    @Test
    void uploadingAvatarReturnsUpdatedUserInfo() throws Exception {
        mockMvc.perform(multipart("/api/users/me/avatar").file(png()).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.avatarUrl")
                        .value(containsString("avatars/" + userId + "/")))
                // 其余字段照常回显，前端可整体覆盖 Pinia
                .andExpect(jsonPath("$.data.nickname").value(MARKER + "昵称"))
                .andExpect(jsonPath("$.data.username").exists());

        assertThat(userMapper.selectById(userId).getAvatar())
                .matches("avatars/" + userId + "/[0-9a-f-]{36}\\.png");
    }

    // ==================== 拒绝路径 ====================

    /** token 指向的用户已不在库中：40400，与改资料同口径 */
    @Test
    void tokenForMissingUserIsNotFound() throws Exception {
        String token = "Bearer " + jwtTokenProvider.issue(999999999L, MARKER + "ghost", "USER");

        mockMvc.perform(multipart("/api/users/me/avatar").file(png()).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    // ==================== 辅助 ====================

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "头像.png", "image/png", PNG);
    }

    private static byte[] pngOfSize(int size) {
        return Arrays.copyOf(PNG, size);
    }

    private String token() {
        return "Bearer " + jwtTokenProvider.issue(userId, MARKER + "self", "USER");
    }

    private Long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + "学院-" + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + "昵称");
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user.getId();
    }
}
