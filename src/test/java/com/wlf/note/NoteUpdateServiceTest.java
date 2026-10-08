package com.wlf.note;

import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.note.dto.NoteUpdateRequest;
import com.wlf.storage.StorageService;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link NoteService#update} 的用例。见《概要设计》§5.4、§4.1、§3.3。
 *
 * <p>重点在几条<b>写错了不会当场暴露</b>的规则：
 *
 * <ul>
 *   <li><b>{@code updated_at} 必须被写，而且只有它该被「额外」写</b>——它是全系统唯一
 *       一个由编辑接口推动的时间戳（§3.3），漏写的话 {@code sort=latest} 的列表顺序
 *       再也不动，而这一点从数据上完全看不出来</li>
 *   <li><b>状态、三个计数、{@code created_at}、{@code note_file} 一律不许碰</b>——
 *       每一列都有一个「顺手改一下」的看似合理理由</li>
 *   <li><b>标签是全量替换</b>——传 null 是清空，不是「不动」。这两种语义在同一个
 *       {@code null} 上意思相反，写反了不会有任何报错</li>
 *   <li><b>被拒的请求不能留下痕迹</b>——非本人 / 课程不存在时，一个字都不该落库</li>
 * </ul>
 *
 * <p>与 {@code NoteDeleteServiceTest} 同样起真库并 mock 掉存储层。这里 mock 存储层的意义
 * 与那边不同：那边是「别真去 OSS 删」，这边是<b>断言它一次都没被调用</b>——
 * 「编辑不碰附件」这条契约，最直接的验收方式就是存储层全程静默。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NoteUpdateServiceTest {

    private static final String MARKER = "ZZTEST-UPD-";

    @Autowired
    private NoteService noteService;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StorageService storageService;

    private Long courseId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(null).get(0).getId();
    }

    // ==================== 正常路径 ====================

    /**
     * 四个可编辑字段都改写，且 {@code updated_at} 推到当下。
     *
     * <p>写法是有意的：先把 {@code updated_at} 设成一个过去的时间，编辑之后再断言它前进了。
     * 直接比较「编辑前后的值」在秒精度下同秒内发现不了问题（与
     * {@code NoteDeleteServiceTest#deletingDoesNotTouchUpdatedAt} 是同一手法，只是方向相反）。
     */
    @Test
    void editingRewritesMetadataAndBumpsUpdatedAt() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        LocalDateTime stale = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE note SET updated_at = ? WHERE id = ?", stale, fixture.noteId());

        NoteUpdateRequest request = request("新标题", "新简介", "李老师", courseId);
        request.setTags(List.of(MARKER + "标签"));

        noteService.update(fixture.noteId(), fixture.uploaderId(), request);

        Note note = noteMapper.selectById(fixture.noteId());
        assertThat(note.getTitle()).isEqualTo("新标题");
        assertThat(note.getSummary()).isEqualTo("新简介");
        assertThat(note.getTeacher()).isEqualTo("李老师");
        assertThat(note.getCourseId()).isEqualTo(courseId);
        assertThat(note.getUpdatedAt()).isAfter(stale);

        // 「编辑不碰附件」最直接的验收：存储层全程静默
        verifyNoInteractions(storageService);
    }

    /**
     * 编辑只动 {@code note} 的元数据列与 {@code note_tag} 两张表；下面这些列一个都不能被写：
     *
     * <ul>
     *   <li>{@code status}——编辑不迁移状态。这条用例用 {@code OFFLINE} 起手，顺带证明了
     *       「允许编辑下架笔记」不会让它重新上架</li>
     *   <li>三个计数列——它们是别处的派生值，与元数据无关</li>
     *   <li>{@code created_at}——发布时刻，不是编辑时刻</li>
     *   <li>{@code note_file}——附件在编辑时不可增删，所以这一行必须原封不动</li>
     * </ul>
     */
    @Test
    void editingDoesNotTouchStatusCountersCreationTimeOrTheFileRow() {
        Fixture fixture = createNote(NoteStatus.OFFLINE);
        LocalDateTime createdAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("""
                UPDATE note SET created_at = ?, view_count = 7, download_count = 5, favorite_count = 3
                WHERE id = ?""", createdAt, fixture.noteId());
        NoteFile before = noteFileMapper.selectById(fixture.fileId());

        noteService.update(fixture.noteId(), fixture.uploaderId(),
                request("改过的标题", null, null, courseId));

        Note note = noteMapper.selectById(fixture.noteId());
        assertThat(note.getStatus()).isEqualTo(NoteStatus.OFFLINE.name());
        assertThat(note.getCreatedAt()).isEqualTo(createdAt);
        assertThat(note.getViewCount()).isEqualTo(7);
        assertThat(note.getDownloadCount()).isEqualTo(5);
        assertThat(note.getFavoriteCount()).isEqualTo(3);
        assertThat(note.getDeletedAt()).isNull();

        NoteFile after = noteFileMapper.selectById(fixture.fileId());
        assertThat(after.getStorageKey()).isEqualTo(before.getStorageKey());
        assertThat(after.getOriginalName()).isEqualTo(before.getOriginalName());
        assertThat(after.getSize()).isEqualTo(before.getSize());
        assertThat(after.getContentType()).isEqualTo(before.getContentType());
        assertThat(after.getIsPurged()).isZero();
        verifyNoInteractions(storageService);
    }

    /** 空白串归一为 null，与上传同一口径：简介与教师是可选字段，存空串与存 null 要分两路处理 */
    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        Fixture fixture = createNote(NoteStatus.ONLINE);

        noteService.update(fixture.noteId(), fixture.uploaderId(),
                request("标题", "   ", "", courseId));

        Note note = noteMapper.selectById(fixture.noteId());
        assertThat(note.getSummary()).isNull();
        assertThat(note.getTeacher()).isNull();
    }

    // ==================== 标签是全量替换 ====================

    /**
     * 提交的标签集合<b>就是编辑之后应有的全部标签</b>：旧的 A 消失、新的 C 出现、
     * 两边的 B 留下。不存在的标签当场创建。
     */
    @Test
    void editingReplacesTheWholeTagSet() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        Long tagA = insertTag(MARKER + "A");
        Long tagB = insertTag(MARKER + "B");
        linkTags(fixture.noteId(), tagA, tagB);

        NoteUpdateRequest request = request("标题", null, null, courseId);
        request.setTags(List.of(MARKER + "B", MARKER + "C"));
        noteService.update(fixture.noteId(), fixture.uploaderId(), request);

        Long tagC = findTagId(MARKER + "C");
        assertThat(tagC).as("提交了一个不存在的标签名，应当当场创建").isNotNull();
        assertThat(tagIdsOf(fixture.noteId())).containsExactlyInAnyOrder(tagB, tagC);
    }

    /**
     * {@code tags} 传 null 是<b>清空</b>，不是「不动」——这是 PUT 全量替换语义里最容易被
     * 前端误用的一处（当成 PATCH 的话，用户改个标题就会莫名其妙丢掉全部标签）。
     * 空数组同理。
     */
    @Test
    void editingWithoutTagsClearsThemAll() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        linkTags(fixture.noteId(), insertTag(MARKER + "A"));

        NoteUpdateRequest request = request("改了标题", null, null, courseId);
        request.setTags(null);
        noteService.update(fixture.noteId(), fixture.uploaderId(), request);

        assertThat(tagIdsOf(fixture.noteId())).isEmpty();
        // 标签被清空，但元数据照常改写——两件事互不影响
        assertThat(noteMapper.selectById(fixture.noteId()).getTitle()).isEqualTo("改了标题");
    }

    /** 取消打标删的是<b>关联</b>，不是标签本身：{@code tag} 表只增不删，别的笔记可能还在用它 */
    @Test
    void aTagDroppedFromTheNoteStaysInTheTagTable() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        Long tagA = insertTag(MARKER + "A");
        linkTags(fixture.noteId(), tagA);

        NoteUpdateRequest request = request("标题", null, null, courseId);
        request.setTags(List.of());
        noteService.update(fixture.noteId(), fixture.uploaderId(), request);

        assertThat(tagIdsOf(fixture.noteId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag WHERE id = ?",
                Integer.class, tagA)).isEqualTo(1);
    }

    // ==================== 归属、存在性与状态门 ====================

    /** 非本人 → 40300，且元数据与标签一个字都没变 */
    @Test
    void editingSomebodyElsesNoteIsForbidden() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        Long tagA = insertTag(MARKER + "A");
        linkTags(fixture.noteId(), tagA);
        Long intruderId = insertUser("intruder");

        NoteUpdateRequest request = request("改过的标题", null, null, courseId);
        request.setTags(null);

        assertThatThrownBy(() -> noteService.update(fixture.noteId(), intruderId, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(noteMapper.selectById(fixture.noteId()).getTitle()).isEqualTo(MARKER + "标题");
        assertThat(tagIdsOf(fixture.noteId())).containsExactly(tagA);
        verifyNoInteractions(storageService);
    }

    /** 存在性先于归属：id 不存在就报不存在 */
    @Test
    void editingAnUnknownNoteIsNotFound() {
        assertThatThrownBy(() -> noteService.update(999_999_999L, insertUser("anyone"),
                request("标题", null, null, courseId)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    /**
     * {@code DELETED} 是终态，编辑被挡在门外（§4.1）——写进去也没人看得见，
     * 而且那行已经不对应任何可展示的东西了。
     *
     * <p>注意与 {@code OFFLINE} 的区别：上面那条用例证明了 {@code OFFLINE} 是<b>可以</b>编辑的。
     * 两者的判据不同，不是「非 ONLINE 一律拒」。
     */
    @Test
    void aDeletedNoteCannotBeEdited() {
        Fixture fixture = createNote(NoteStatus.DELETED);

        assertThatThrownBy(() -> noteService.update(fixture.noteId(), fixture.uploaderId(),
                request("改过的标题", null, null, courseId)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNAVAILABLE));

        assertThat(noteMapper.selectById(fixture.noteId()).getTitle()).isEqualTo(MARKER + "标题");
    }

    /**
     * 课程不合法 → 40001 带字段明细，且<b>课程校验先于一切写入</b>：标题没变、标签没被删。
     *
     * <p>报 40001 而不是 40400，与上传同一口径：课程由前端下拉框提供，id 对不上属于请求参数错，
     * 前端据此把课程选择框标红比弹一句「资源不存在」有用。
     */
    @Test
    void editingRejectsAnUnknownCourseBeforeWritingAnything() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        Long tagA = insertTag(MARKER + "A");
        linkTags(fixture.noteId(), tagA);

        NoteUpdateRequest request = request("改过的标题", null, null, 999_999_999L);
        request.setTags(null);

        assertThatThrownBy(() -> noteService.update(fixture.noteId(), fixture.uploaderId(), request))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat((List<?>) e.getData())
                            .singleElement()
                            .isEqualTo(new FieldViolation("courseId", "课程不存在"));
                });

        assertThat(noteMapper.selectById(fixture.noteId()).getTitle()).isEqualTo(MARKER + "标题");
        assertThat(tagIdsOf(fixture.noteId())).containsExactly(tagA);
    }

    // ==================== 夹具 ====================

    private record Fixture(Long noteId, Long fileId, Long uploaderId) {
    }

    private static NoteUpdateRequest request(String title, String summary, String teacher, Long courseId) {
        NoteUpdateRequest request = new NoteUpdateRequest();
        request.setTitle(title);
        request.setSummary(summary);
        request.setTeacher(teacher);
        request.setCourseId(courseId);
        return request;
    }

    /**
     * 直接插库造数据，不走上传流程：本用例验的是编辑，把 {@code NoteFilePolicy} 与 OSS
     * 拖进来只会让夹具变长，而 {@code note_file} 只要有一行可供「不许被碰」的断言对照就够了。
     */
    private Fixture createNote(NoteStatus status) {
        Long uploaderId = insertUser("uploader");

        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setSummary(MARKER + "原简介");
        note.setTeacher(MARKER + "原教师");
        note.setStatus(status.name());
        noteMapper.insert(note);

        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey("notes/2026/10/" + UUID.randomUUID() + ".pdf");
        file.setOriginalName("课件.pdf");
        file.setSize(1048576L);
        file.setContentType("application/pdf");
        noteFileMapper.insert(file);

        return new Fixture(note.getId(), file.getId(), uploaderId);
    }

    private Long insertTag(String name) {
        jdbcTemplate.update("INSERT INTO tag (name) VALUES (?)", name);
        return findTagId(name);
    }

    private Long findTagId(String name) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM tag WHERE name = ?", Long.class, name);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void linkTags(Long noteId, Long... tagIds) {
        for (Long tagId : tagIds) {
            jdbcTemplate.update("INSERT INTO note_tag (note_id, tag_id) VALUES (?, ?)", noteId, tagId);
        }
    }

    private List<Long> tagIdsOf(Long noteId) {
        return jdbcTemplate.queryForList(
                "SELECT tag_id FROM note_tag WHERE note_id = ? ORDER BY tag_id", Long.class, noteId);
    }

    private Long insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser(String role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(MARKER + role + "-" + suffix);
        user.setEmail(MARKER + role + "-" + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + role);
        user.setCollegeId(insertCollege());
        userMapper.insert(user);
        return user.getId();
    }
}
