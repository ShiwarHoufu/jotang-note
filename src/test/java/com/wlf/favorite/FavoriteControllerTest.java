package com.wlf.favorite;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.JwtTokenProvider;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.User;
import com.wlf.note.NoteMapper;
import com.wlf.note.NoteStatus;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收藏接口的 HTTP 层验证：路由、鉴权与响应形状。
 *
 * <p>与 {@link FavoriteServiceTest} 分工：那边验业务规则（状态守卫、计数增减、幂等），
 * 这边只验「请求进来之后变成了什么响应」——错误码有没有落到正确的 HTTP 状态、
 * {@code data.favoriteCount} 有没有出来。业务规则不在这里重复断言。
 *
 * <p>这里把三条错误码都从 HTTP 层走了一遍（40301 / 40400 / 40902），
 * 而不是只验服务层：这几个码分别映射到 403 / 404 / 409，映射写错在服务层是看不出来的。
 *
 * <p>用例随事务回滚；用户自造，不依赖开发库里已有什么。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class FavoriteControllerTest {

    private static final String MARKER = "ZZTEST-FAV-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private UserMapper userMapper;

    private Long userId;
    private Long noteId;

    @BeforeEach
    void setUp() {
        userId = insertUser();
        noteId = insertNote(NoteStatus.ONLINE);
    }

    /** 未登录不能收藏，也不能取消——两个接口都靠 SecurityConfig 的 anyRequest().authenticated() */
    @Test
    void favoritingRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/favorite", noteId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void unfavoritingRequiresLogin() throws Exception {
        mockMvc.perform(delete("/api/notes/{id}/favorite", noteId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 收藏成功回最新计数。前端直接拿这个数刷新卡片，不用自己 ±1——
     * 一旦服务端不回，前端本地加就必然在并发与连点下漂移。
     */
    @Test
    void favoritingReturnsTheNewCount() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/favorite", noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.favoriteCount").value(1));
    }

    @Test
    void favoritingTwiceReportsAlreadyFavorited() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/favorite", noteId).header("Authorization", token()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/notes/{id}/favorite", noteId).header("Authorization", token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    /** §6.5：只有 ONLINE 能收藏，40301 映射到 403 */
    @Test
    void favoritingAnOfflineNoteIsForbidden() throws Exception {
        setNoteStatus(NoteStatus.OFFLINE);

        mockMvc.perform(post("/api/notes/{id}/favorite", noteId).header("Authorization", token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void favoritingAnUnknownNoteIsNotFound() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/favorite", 999_999_999L).header("Authorization", token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void unfavoritingDecrementsTheCount() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/favorite", noteId).header("Authorization", token()));

        mockMvc.perform(delete("/api/notes/{id}/favorite", noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.favoriteCount").value(0));
    }

    /**
     * 幂等：没收藏过也回 200 与当前计数。连点两次取消不该有一次报错，
     * 前端也就不必「先查再删」。
     */
    @Test
    void unfavoritingWithoutHavingFavoritedIsIdempotent() throws Exception {
        mockMvc.perform(delete("/api/notes/{id}/favorite", noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.favoriteCount").value(0));
    }

    /**
     * 与 40301 不对称：已下架的笔记允许取消收藏。
     * 若这里也拦成 403，收藏列表里的「已下架」占位项就永远清不掉。
     */
    @Test
    void unfavoritingAnOfflineNoteIsAllowed() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/favorite", noteId).header("Authorization", token()));
        setNoteStatus(NoteStatus.OFFLINE);

        mockMvc.perform(delete("/api/notes/{id}/favorite", noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteCount").value(0));
    }

    /** 幂等只覆盖「没收藏过」这一种；笔记本身不存在仍是 40400，两个分支要分清 */
    @Test
    void unfavoritingAnUnknownNoteIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/notes/{id}/favorite", 999_999_999L).header("Authorization", token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /**
     * 只改状态的原子 UPDATE，不整行回写：{@code updateById} 会连着把 {@code updated_at}
     * 等字段一起写回去，而那个字段的语义是「用户最后编辑笔记的时间」，不该被本用例碰到。
     */
    private void setNoteStatus(NoteStatus status) {
        noteMapper.update(null, Wrappers.<Note>lambdaUpdate()
                .eq(Note::getId, noteId)
                .set(Note::getStatus, status.name()));
    }

    private String token() {
        return "Bearer " + jwtTokenProvider.issue(userId, MARKER + "user", "USER");
    }

    private Long insertNote(NoteStatus status) {
        Note note = new Note();
        note.setUploaderId(userId);
        note.setCourseId(courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId());
        note.setTitle(MARKER + "笔记");
        note.setStatus(status.name());
        noteMapper.insert(note);
        return note.getId();
    }

    /**
     * 造用户。{@code user.college_id} 非空，所以先造学院；
     * 用户名带随机后缀，避免同一用例里造多个时撞 {@code uk_username}。
     */
    private Long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + "学院-" + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + "收藏者");
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user.getId();
    }
}
