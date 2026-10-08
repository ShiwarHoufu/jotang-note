package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.JwtTokenProvider;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.User;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import com.wlf.user.UserMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/notes} 的 HTTP 层验证：路由、multipart 绑定、鉴权与校验的响应形状。
 *
 * <p>与 {@link NoteServiceTest} 分工：那边验业务结果与补偿逻辑，这边只验「请求进来之后
 * 变成了什么响应」——落库细节不在这里重复断言。
 *
 * <p>用例随事务回滚；上传者自造，不依赖开发库里的用户。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class NoteControllerTest {

    private static final String MARKER = "ZZTEST-";
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};

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

    @MockitoBean
    private StorageService storageService;

    private Long courseId;
    private Long uploaderId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();
        uploaderId = insertUploader();
        when(storageService.upload(eq(Bucket.NOTE), anyString(), any(), anyLong(), anyString()))
                .thenAnswer(invocation -> new StoredObject(
                        invocation.getArgument(1, String.class),
                        invocation.getArgument(4, String.class),
                        invocation.getArgument(3, Long.class)));
    }

    @Test
    void uploadRequiresLogin() throws Exception {
        mockMvc.perform(multipart("/api/notes")
                        .file(pdf())
                        .param("title", "标题")
                        .param("courseId", String.valueOf(courseId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void uploadRejectsBlankTitle() throws Exception {
        mockMvc.perform(multipart("/api/notes")
                        .file(pdf())
                        .param("title", "   ")
                        .param("courseId", String.valueOf(courseId))
                        .header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                // 字段级明细让前端能直接定位到标题输入框
                .andExpect(jsonPath("$.data[0].field").value("title"));
    }

    @Test
    void uploadRejectsUnknownCourse() throws Exception {
        mockMvc.perform(multipart("/api/notes")
                        .file(pdf())
                        .param("title", "标题")
                        .param("courseId", "999999999")
                        .header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("courseId"));
    }

    /** svg 能在 OSS 域名下形成存储型 XSS，必须在入口拒掉（§6.1、§8.3） */
    @Test
    void uploadRejectsUnsupportedFileType() throws Exception {
        mockMvc.perform(multipart("/api/notes")
                        .file(new MockMultipartFile("file", "evil.svg", "image/svg+xml",
                                "<svg onload=alert(1)>".getBytes()))
                        .param("title", "标题")
                        .param("courseId", String.valueOf(courseId))
                        .header("Authorization", token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42200));
    }

    @Test
    void uploadCreatesNoteAndReturnsItsId() throws Exception {
        String body = mockMvc.perform(multipart("/api/notes")
                        .file(pdf())
                        .param("title", "  期中复习提纲  ")
                        .param("courseId", String.valueOf(courseId))
                        .param("tags", MARKER + "期末")
                        .param("tags", MARKER + "重点")
                        .header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn().getResponse().getContentAsString();

        Long noteId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
        Note note = noteMapper.selectById(noteId);
        assertThat(note.getUploaderId()).isEqualTo(uploaderId);
        assertThat(note.getTitle()).isEqualTo("期中复习提纲");
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "笔记.pdf", "application/pdf", PDF);
    }

    private String token() {
        return "Bearer " + jwtTokenProvider.issue(uploaderId, MARKER + "uploader", "USER");
    }

    private Long insertUploader() {
        College college = new College();
        college.setName(MARKER + "学院");
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + "uploader");
        user.setEmail(MARKER + "uploader-note@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + "上传者");
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user.getId();
    }
}
