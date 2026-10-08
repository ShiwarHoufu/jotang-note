package com.wlf.admin;

import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.JwtTokenProvider;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.note.NoteMapper;
import com.wlf.note.NoteFileMapper;
import com.wlf.note.NoteStatus;
import com.wlf.storage.StorageService;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员下架 / 恢复的 HTTP 层验证。见《概要设计》§5.6、§4.1。
 *
 * <p>本类承担一个此前的接口都不必操心的问题：<b>基于角色的鉴权</b>。
 * 它是第一处 ADMIN 接口，所以三条路径都要在 HTTP 层验一遍——
 * 未登录 40100、已登录但非管理员 40300、管理员放行。
 * 前两条由 {@code SecurityConfig} 的 {@code /api/admin/**} 规则产出，
 * 不经过 {@code @RestControllerAdvice}，因此必须在这里（而不是 Service 单测里）验证它们真的成立。
 *
 * <p>用例随事务回滚；用户与笔记自造。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AdminNoteControllerTest {

    private static final String MARKER = "ZZTEST-ADMIN-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteFileMapper noteFileMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private UserMapper userMapper;

    @MockitoBean
    private StorageService storageService;

    private Long courseId;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(null).get(0).getId();
        ownerId = insertUser("owner");
    }

    // ==================== 鉴权 ====================

    @Test
    void offlineRequiresLogin() throws Exception {
        Long noteId = insertNote(NoteStatus.ONLINE);

        mockMvc.perform(post("/api/admin/notes/{id}/offline", noteId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 普通登录用户（role=USER）打管理接口 → 40300。
     *
     * <p>这条盯的是 {@code SecurityConfig} 里那条 {@code hasRole("ADMIN")}：
     * 它靠 {@code JwtAuthFilter} 已经发出的 {@code ROLE_} 前缀权限生效。
     * 谁要是把那个前缀去掉，或者把规则写在 {@code anyRequest()} 之后，
     * 这里会立刻暴露——而且暴露的是「普通用户能下架别人的笔记」这种级别的洞。
     */
    @Test
    void offlineRejectsAnOrdinaryUserWithForbidden() throws Exception {
        Long noteId = insertNote(NoteStatus.ONLINE);

        mockMvc.perform(post("/api/admin/notes/{id}/offline", noteId)
                        .header("Authorization", tokenOf(ownerId, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        // 被拒之后状态不该有任何变化
        mockMvc.perform(get("/api/notes/{id}", noteId).header("Authorization", tokenOf(ownerId, "USER")))
                .andExpect(jsonPath("$.data.status").value("ONLINE"));
    }

    /** 恢复接口走同一条规则。两个端点各验一次，免得只有一个被放行 */
    @Test
    void restoreRejectsAnOrdinaryUserWithForbidden() throws Exception {
        Long noteId = insertNote(NoteStatus.OFFLINE);

        mockMvc.perform(post("/api/admin/notes/{id}/online", noteId)
                        .header("Authorization", tokenOf(ownerId, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    // ==================== 正常迁移 ====================

    /** 下架成功：出参是「无数据的成功」，详情页随即显示 {@code OFFLINE} */
    @Test
    void anAdminCanOfflineANote() throws Exception {
        Long noteId = insertNote(NoteStatus.ONLINE);

        mockMvc.perform(post("/api/admin/notes/{id}/offline", noteId).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/notes/{id}", noteId).header("Authorization", adminToken()))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                // 非 ONLINE 时文件字段整体置空（§4.1），下架后文件名不该再露出去
                .andExpect(jsonPath("$.data.file").value(nullValue()));
    }

    @Test
    void anAdminCanRestoreANote() throws Exception {
        Long noteId = insertNote(NoteStatus.OFFLINE);

        mockMvc.perform(post("/api/admin/notes/{id}/online", noteId).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/notes/{id}", noteId).header("Authorization", adminToken()))
                .andExpect(jsonPath("$.data.status").value("ONLINE"))
                .andExpect(jsonPath("$.data.file.originalName").value("课件.pdf"));
    }

    /** 幂等：连点两次不该有一次报错（与删除、取消收藏同口径） */
    @Test
    void offliningTwiceIsStillOk() throws Exception {
        Long noteId = insertNote(NoteStatus.ONLINE);

        mockMvc.perform(post("/api/admin/notes/{id}/offline", noteId).header("Authorization", adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/notes/{id}/offline", noteId).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 边界 ====================

    /**
     * 已删除的笔记报 40301，<b>不是 50000</b>。
     *
     * <p>这条是 HTTP 层的验收：{@code DELETED} 是终态，若让状态机去判，它抛的
     * {@code IllegalStateException} 会被兜底翻成「服务器内部错误」，
     * 管理员看到的就成了「后端炸了」。两个方向都验。
     */
    @Test
    void aDeletedNoteIsNoteUnavailableInBothDirections() throws Exception {
        Long noteId = insertNote(NoteStatus.DELETED);

        mockMvc.perform(post("/api/admin/notes/{id}/offline", noteId).header("Authorization", adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/admin/notes/{id}/online", noteId).header("Authorization", adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void anUnknownNoteIsNotFound() throws Exception {
        mockMvc.perform(post("/api/admin/notes/{id}/offline", 999_999_999L)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    // ==================== 夹具 ====================

    private String adminToken() {
        return tokenOf(insertUser("admin"), "ADMIN");
    }

    /** 签指定用户、指定角色的 token。role 直接进 JWT 的 claim，由 JwtAuthFilter 转成权限 */
    private String tokenOf(Long userId, String role) {
        return "Bearer " + jwtTokenProvider.issue(userId, MARKER + role, role);
    }

    /** 直接插库造一篇笔记（含文件行）。详情接口要 JOIN note_file / course / user / college，四张都得齐 */
    private Long insertNote(NoteStatus status) {
        Note note = new Note();
        note.setUploaderId(ownerId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus(status.name());
        noteMapper.insert(note);

        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey("notes/2026/10/" + UUID.randomUUID() + ".pdf");
        file.setOriginalName("课件.pdf");
        file.setSize(1024L);
        file.setContentType("application/pdf");
        noteFileMapper.insert(file);

        return note.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser(String role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + "学院-" + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + role + "-" + suffix);
        user.setEmail(MARKER + role + "-" + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + role);
        user.setCollegeId(college.getId());
        // user.role 列本身也写成 ADMIN，免得「token 说管理员、库里说普通用户」造成困惑。
        // 鉴权实际只看 token 里的 claim（JwtAuthFilter 不查库），但夹具与语义对齐更好读
        user.setRole(role);
        userMapper.insert(user);
        return user.getId();
    }
}
