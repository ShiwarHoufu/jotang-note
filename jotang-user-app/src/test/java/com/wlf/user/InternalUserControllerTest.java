package com.wlf.user;

import com.wlf.common.IdentityHeaders;
import com.wlf.user.domain.college.College;
import com.wlf.user.domain.user.User;
import com.wlf.user.infrastructure.persistence.CollegeMapper;
import com.wlf.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /internal/users/batch} 的 HTTP 层验证：服务令牌、批量返回与缺失 id 的处理。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class InternalUserControllerTest {

    private static final String MARKER = "ZZTEST-INTERNAL-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Value("${jotang.internal.token:dev-internal-token}")
    private String internalToken;

    @Test
    void batchReturnsExistingUsersAndSkipsMissingOnes() throws Exception {
        College college = insertCollege();
        Long userId = insertUser(college.getId(), "内部用户甲");

        mockMvc.perform(get("/internal/users/batch")
                        .param("ids", userId + ",999999999")
                        .header(IdentityHeaders.INTERNAL_TOKEN, internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(userId))
                .andExpect(jsonPath("$[0].nickname").value("内部用户甲"))
                .andExpect(jsonPath("$[0].collegeName").value(college.getName()));
    }

    @Test
    void missingIdsReturnEmptyList() throws Exception {
        mockMvc.perform(get("/internal/users/batch")
                        .param("ids", "999999999")
                        .header(IdentityHeaders.INTERNAL_TOKEN, internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void missingInternalTokenIsRejected() throws Exception {
        mockMvc.perform(get("/internal/users/batch").param("ids", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void wrongInternalTokenIsRejected() throws Exception {
        mockMvc.perform(get("/internal/users/batch")
                        .param("ids", "1")
                        .header(IdentityHeaders.INTERNAL_TOKEN, "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ---- helpers ----------------------------------------------------------

    private College insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college;
    }

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
