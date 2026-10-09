package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.JwtTokenProvider;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 笔记接口的 HTTP 层验证：路由、参数绑定、鉴权与响应形状。
 *
 * <p>与 {@link NoteServiceTest} / {@link NoteDetailServiceTest} 分工：那边验业务结果与补偿逻辑，
 * 这边只验「请求进来之后变成了什么响应」——落库与装配细节不在这里重复断言。
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
    private Long uploaderId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();
        uploaderId = insertUploader();
        when(storageService.upload(eq(Bucket.NOTE), anyString(), any(), anyLong(), anyString(), isNull()))
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

    // ==================== GET /api/notes/{id} ====================

    @Test
    void detailRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/notes/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void detailReturnsUnknownNoteAsNotFound() throws Exception {
        mockMvc.perform(get("/api/notes/999999999").header("Authorization", token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /**
     * §4.1 那条边界在 HTTP 层的样子：已下架的笔记是 <b>200 + status=OFFLINE</b>，
     * 不是 40301——40301 只发给预览 / 下载 / 收藏。同时文件字段整体为 null。
     *
     * <p>{@code file} 断言用 {@code nullValue()} 而不是 {@code doesNotExist()}：
     * 这个键是<b>存在且为 null</b>，不是从响应里消失，两者对前端不是一回事。
     */
    @Test
    void detailServesOfflineNoteWithNullFile() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "已下架", NoteStatus.OFFLINE);

        mockMvc.perform(get("/api/notes/" + noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"))
                .andExpect(jsonPath("$.data.title").value(MARKER + "已下架"))
                .andExpect(jsonPath("$.data.file").value(nullValue()))
                .andExpect(jsonPath("$.data.isFavorited").value(false));
    }

    // ==================== GET /api/notes ====================

    @Test
    void listRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 非法的 {@code sort} 必须落成 40001 而不是 50000。
     *
     * <p>这条盯的是参数绑定这一层：{@code GlobalExceptionHandler} 只处理了
     * {@code MethodArgumentNotValidException} 与 {@code ConstraintViolationException}。
     * 如果把参数写成散装的 {@code @RequestParam}，枚举转换失败会走 Spring 6.1 的
     * {@code HandlerMethodValidationException}——没人接，掉进兜底变成「服务器内部错误」，
     * 前端看到的是后端炸了而不是自己传错了。
     */
    @Test
    void listRejectsUnknownSortWithFieldDetail() throws Exception {
        mockMvc.perform(get("/api/notes").param("sort", "bogus").header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("sort"));
    }

    @Test
    void listRejectsPageBelowOne() throws Exception {
        mockMvc.perform(get("/api/notes").param("page", "0").header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("page"));
    }

    @Test
    void listRejectsSizeAboveFifty() throws Exception {
        mockMvc.perform(get("/api/notes").param("size", "51").header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("size"));
    }

    @Test
    void listRejectsDeepPagination() throws Exception {
        mockMvc.perform(get("/api/notes").param("page", "51").header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("page"));
    }

    /** 分页外壳的四个字段都要在，且默认值生效 */
    @Test
    void listReturnsPageEnvelope() throws Exception {
        Long isolatedCourse = insertCourse();
        insertNote(isolatedCourse, MARKER + "列表项", NoteStatus.ONLINE);
        // 已下架的不能出现（§4.1）
        insertNote(isolatedCourse, MARKER + "已下架", NoteStatus.OFFLINE);

        mockMvc.perform(get("/api/notes")
                        .param("courseId", String.valueOf(isolatedCourse))
                        .header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value(MARKER + "列表项"))
                .andExpect(jsonPath("$.data.items[0].isFavorited").value(false))
                .andExpect(jsonPath("$.data.items[0].tags").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    // ==================== 删除 ====================

    @Test
    void deleteRequiresLogin() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "未登录删", NoteStatus.ONLINE);

        mockMvc.perform(delete("/api/notes/{id}", noteId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 删除成功是「无数据的成功」（{@code data} 为 null），与退出登录同款；
     * 而删完之后<b>详情页照常返回 200</b>——§4.1 说详情是唯一要把状态带出去的读接口，
     * 前端据此渲染「已删除」提示，文件字段整体置空。
     */
    @Test
    void deleteReturnsNoDataAndTheNoteStaysReadableAsDeleted() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "待删除", NoteStatus.ONLINE);

        mockMvc.perform(delete("/api/notes/{id}", noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/notes/{id}", noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"))
                .andExpect(jsonPath("$.data.title").value(MARKER + "待删除"))
                .andExpect(jsonPath("$.data.file").value(nullValue()));
    }

    /** 幂等：连点两次不该有一次报错 */
    @Test
    void deletingTwiceIsStillOk() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "删两次", NoteStatus.ONLINE);

        mockMvc.perform(delete("/api/notes/{id}", noteId).header("Authorization", token()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/notes/{id}", noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 非本人 40300——全项目第一处归属校验，走 HTTP 验一遍码与状态的映射 */
    @Test
    void deleteRejectsSomebodyElseWithForbidden() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "别人的笔记", NoteStatus.ONLINE);
        Long intruderId = insertStranger();

        mockMvc.perform(delete("/api/notes/{id}", noteId)
                        .header("Authorization", tokenOf(intruderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void deletingAnUnknownNoteIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/notes/{id}", 999_999_999L).header("Authorization", token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    // ==================== 编辑 ====================

    @Test
    void updateRequiresLogin() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "未登录改", NoteStatus.ONLINE);

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("标题", courseId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 编辑成功是「无数据的成功」（{@code data} 为 null），与删除同款；
     * 而改完之后详情页照常打开、拿到的就是新值——这就是出参不给数据的原因。
     */
    @Test
    void updateReturnsNoDataAndTheDetailReflectsTheChange() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "改前", NoteStatus.ONLINE);

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(MARKER + "改后", courseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/notes/{id}", noteId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(MARKER + "改后"))
                .andExpect(jsonPath("$.data.status").value("ONLINE"));
    }

    @Test
    void updateRejectsBlankTitle() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "空标题", NoteStatus.ONLINE);

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("   ", courseId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("title"));
    }

    /**
     * <b>已下架的笔记可以编辑，且改完仍是 OFFLINE。</b>这是本接口与预览 / 下载 / 收藏
     * 在 §4.1 那张表上的分歧点：那三个动作对 OFFLINE 一律 40301，编辑只挡 DELETED。
     * 理由是编辑不迁移状态——改完不会让它重新可见，而禁止编辑只会逼上传者删了重传。
     */
    @Test
    void updateSucceedsOnAnOfflineNoteAndLeavesItOffline() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "下架待改", NoteStatus.OFFLINE);

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(MARKER + "下架改后", courseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/notes/{id}", noteId).header("Authorization", token()))
                .andExpect(jsonPath("$.data.title").value(MARKER + "下架改后"))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));
    }

    /** 已删除是终态：40301，与预览 / 下载 / 收藏同一口径 */
    @Test
    void updateRejectsADeletedNoteWithNoteUnavailable() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "已删除", NoteStatus.DELETED);

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(MARKER + "改不动", courseId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void updateRejectsSomebodyElseWithForbidden() throws Exception {
        Long noteId = insertNote(courseId, MARKER + "别人的笔记", NoteStatus.ONLINE);
        Long intruderId = insertStranger();

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .header("Authorization", tokenOf(intruderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(MARKER + "越权改", courseId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void updatingAnUnknownNoteIsNotFound() throws Exception {
        mockMvc.perform(put("/api/notes/{id}", 999_999_999L)
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(MARKER + "不存在", courseId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    // ==================== GET /api/users/me/notes ====================

    @Test
    void myNotesRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/users/me/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 只出自己<b>未删除</b>的笔记，已下架的照常返回并带着 {@code OFFLINE}。
     * 同时验证响应体里<b>没有 {@code uploader} 与 {@code isFavorited}</b>——
     * 上传者恒等于自己、收藏自己的笔记没有意义，恒等于一个值的字段不给。
     */
    @Test
    void myNotesReturnsOwnUndeltedNotesAndOmitsConstantFields() throws Exception {
        insertNote(courseId, MARKER + "在线的", NoteStatus.ONLINE);
        insertNote(courseId, MARKER + "已下架的", NoteStatus.OFFLINE);
        insertNote(courseId, MARKER + "已删除的", NoteStatus.DELETED);

        mockMvc.perform(get("/api/users/me/notes").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                // 按标题集合断言而不是按下标：两条笔记的 created_at 落在同一秒，
                // 先后由次级键 id 决定，拿下标断言会把顺序写死进用例
                .andExpect(jsonPath("$.data.items[*].title",
                        containsInAnyOrder(MARKER + "在线的", MARKER + "已下架的")))
                .andExpect(jsonPath("$.data.items[*].status",
                        containsInAnyOrder("ONLINE", "OFFLINE")))
                .andExpect(jsonPath("$.data.items[0].course.id").value(courseId))
                .andExpect(jsonPath("$.data.items[0].tags").isArray())
                .andExpect(jsonPath("$.data.items[0].uploader").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].isFavorited").doesNotExist());
    }

    /** 分页外壳与参数守卫，与其余列表同一套（§3.3 的上限对所有列表统一生效） */
    @Test
    void myNotesRejectsDeepPagination() throws Exception {
        mockMvc.perform(get("/api/users/me/notes").param("page", "51").header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("page"));
    }

    @Test
    void myNotesRejectsPageBelowOne() throws Exception {
        mockMvc.perform(get("/api/users/me/notes").param("page", "0").header("Authorization", token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("page"));
    }

    /** 编辑请求体：只含元数据，没有任何文件相关字段（附件在编辑时不可增删） */
    private String json(String title, Long courseId) {
        return """
                {"title":"%s","summary":"简介","teacher":"张老师","courseId":%d,"tags":["%s标签"]}
                """.formatted(title, courseId, MARKER);
    }

    /** 直接插库造一篇笔记（含文件行），避开上传流程——这里验的是读路径的路由与响应形状 */
    private Long insertNote(Long noteCourseId, String title, NoteStatus status) {
        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(noteCourseId);
        note.setTitle(title);
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

    /**
     * 造一个只属于本用例的课程。列表接口没有能把开发库里已有笔记排除掉的天然边界，
     * 想用 {@code total == 1} 这类断言就必须先用一个自造课程把结果圈住。
     */
    private Long insertCourse() {
        Course course = new Course();
        course.setName(MARKER + "课程-" + UUID.randomUUID().toString().substring(0, 8));
        course.setIsOther(0);
        courseMapper.insert(course);
        return course.getId();
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "笔记.pdf", "application/pdf", PDF);
    }

    private String token() {
        return "Bearer " + jwtTokenProvider.issue(uploaderId, MARKER + "uploader", "USER");
    }

    /** 签指定用户的 token。用于「非本人」这类要区分身份的用例 */
    private String tokenOf(Long userId) {
        return "Bearer " + jwtTokenProvider.issue(userId, MARKER + "stranger", "USER");
    }

    /**
     * 造一个与上传者无关的用户。与 {@code insertUploader()} 分开而不是加个参数：
     * 那个方法用的是固定的 {@code MARKER + "uploader"} 用户名，同一个用例里再调一次会撞
     * {@code uk_username}，所以这里必须自带随机后缀。
     */
    private Long insertStranger() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + "他人学院-" + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + "stranger-" + suffix);
        user.setEmail(MARKER + "stranger-" + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + "他人");
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user.getId();
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
