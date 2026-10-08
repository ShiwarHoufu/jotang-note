package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Favorite;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.favorite.FavoriteMapper;
import com.wlf.note.dto.NoteDetailResponse;
import com.wlf.storage.Bucket;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link NoteService#detail} 的用例。见《概要设计》§5.4、§4.1、§6.2、§6.5。
 *
 * <p>重点不在「字段有没有搬对地方」，而在几条<b>容易实现成另一个样子</b>的规则：
 *
 * <ul>
 *   <li><b>非 ONLINE 照常返回</b>，且只丢文件字段、元数据一条不少（§4.1）</li>
 *   <li><b>浏览量只在 ONLINE 时涨</b>，且返回值含本次（§5.4）</li>
 *   <li><b>收藏关系不因下架而消失</b>（§6.5）</li>
 *   <li><b>没设头像时 avatarUrl 是 null</b>，不是拼出来的半截 URL</li>
 * </ul>
 *
 * <p>上传者与浏览者刻意分属<b>两个不同学院</b>：D8 说笔记的学院是「上传者的学院」，
 * 只断言 collegeName 等于某个值无法区分它是从上传者推的还是从浏览者推的。
 * 两边一开始就不同，断言才有判别力。用例随事务回滚。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NoteDetailServiceTest {

    private static final String MARKER = "ZZTEST-";
    private static final String AVATAR_KEY = "avatars/7/abc.png";
    private static final String AVATAR_URL = "https://jotang-avatar.oss-cn-chengdu.aliyuncs.com/" + AVATAR_KEY;

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteFileMapper noteFileMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

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
    private Long uploaderCollegeId;
    private String uploaderCollegeName;
    private Long viewerId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();
        uploaderCollegeName = MARKER + "上传者学院";
        uploaderCollegeId = insertCollege(uploaderCollegeName);
        // 浏览者放在另一个学院：详情里的 collegeName 必须来自上传者，不能是浏览者的
        viewerId = insertUser("viewer", null, insertCollege(MARKER + "浏览者学院"));

        when(storageService.publicUrl(Bucket.AVATAR, AVATAR_KEY)).thenReturn(AVATAR_URL);
    }

    @Test
    void onlineNoteIsFullyAssembled() {
        Fixture fixture = createNote(NoteStatus.ONLINE, AVATAR_KEY);
        Long reviewTag = attachTag(fixture.noteId(), MARKER + "标签甲");
        Long finalTag = attachTag(fixture.noteId(), MARKER + "标签乙");

        NoteDetailResponse detail = noteService.detail(fixture.noteId(), viewerId);

        assertThat(detail.id()).isEqualTo(fixture.noteId());
        assertThat(detail.title()).isEqualTo(MARKER + "标题");
        assertThat(detail.summary()).isEqualTo("简介");
        assertThat(detail.teacher()).isEqualTo("张老师");
        assertThat(detail.status()).isEqualTo(NoteStatus.ONLINE);

        assertThat(detail.course().id()).isEqualTo(courseId);
        assertThat(detail.course().name()).isNotBlank();

        assertThat(detail.uploader().id()).isEqualTo(fixture.uploaderId());
        assertThat(detail.uploader().nickname()).isEqualTo(MARKER + "uploader");
        assertThat(detail.uploader().avatarUrl()).isEqualTo(AVATAR_URL);
        // D8：学院从上传者推导，与浏览者无关
        assertThat(detail.uploader().collegeName()).isEqualTo(uploaderCollegeName);

        assertThat(detail.tags()).extracting(TagResponse::id).containsExactly(reviewTag, finalTag);
        assertThat(detail.tags()).extracting(TagResponse::name)
                .containsExactly(MARKER + "标签甲", MARKER + "标签乙");

        assertThat(detail.file()).isNotNull();
        assertThat(detail.file().originalName()).isEqualTo("课件.pdf");
        assertThat(detail.file().size()).isEqualTo(1048576L);
        assertThat(detail.file().contentType()).isEqualTo("application/pdf");
        // previewMode 不在库里，是由 contentType 现推的（§6.2）
        assertThat(detail.file().previewMode()).isEqualTo(PreviewMode.PDF_INLINE);

        assertThat(detail.isFavorited()).isFalse();
    }

    /**
     * §4.1：详情页对已下架 / 已删除的笔记是「提示」，不是「禁止」——
     * 所以照常 200、元数据照给，只把文件字段整体丢掉。
     *
     * <p>两种状态写在同一个用例里：它们在这件事上必须是同一个行为，
     * 分开写反而会掩盖「其中一个被特判了」这种情况。
     */
    @Test
    void nonOnlineNoteKeepsMetadataButDropsTheFile() {
        for (NoteStatus status : List.of(NoteStatus.OFFLINE, NoteStatus.DELETED)) {
            Fixture fixture = createNote(status, AVATAR_KEY);

            NoteDetailResponse detail = noteService.detail(fixture.noteId(), viewerId);

            assertThat(detail.status()).isEqualTo(status);
            assertThat(detail.file()).isNull();

            // 元数据一条不少：已下架的笔记仍是可展示、可分享的页面
            assertThat(detail.title()).isEqualTo(MARKER + "标题");
            assertThat(detail.uploader().collegeName()).isEqualTo(uploaderCollegeName);
            // 没有标签时给空列表而不是 null，前端可以无条件遍历
            assertThat(detail.tags()).isEmpty();
        }
    }

    /** §5.4：仅 ONLINE 计数，且返回的值含本次浏览 */
    @Test
    void viewCountGrowsOnlyForOnlineNotesAndIncludesThisVisit() {
        Fixture online = createNote(NoteStatus.ONLINE, null);
        Fixture offline = createNote(NoteStatus.OFFLINE, null);

        assertThat(noteService.detail(online.noteId(), viewerId).viewCount()).isEqualTo(1L);
        assertThat(noteService.detail(online.noteId(), viewerId).viewCount()).isEqualTo(2L);

        assertThat(noteService.detail(offline.noteId(), viewerId).viewCount()).isZero();
        assertThat(noteService.detail(offline.noteId(), viewerId).viewCount()).isZero();

        // 回库核对：返回的值不是凭空加的，确实写进去了
        assertThat(noteMapper.selectById(online.noteId()).getViewCount()).isEqualTo(2L);
        assertThat(noteMapper.selectById(offline.noteId()).getViewCount()).isZero();
    }

    /** 收藏是「与登录者相关」的状态：同一篇笔记，收藏者与非收藏者看到的不一样 */
    @Test
    void favoritedIsReportedPerViewer() {
        Fixture fixture = createNote(NoteStatus.ONLINE, null);
        favorite(viewerId, fixture.noteId());

        assertThat(noteService.detail(fixture.noteId(), viewerId).isFavorited()).isTrue();
        assertThat(noteService.detail(fixture.noteId(),
                insertUser("other", null, uploaderCollegeId)).isFavorited()).isFalse();
    }

    /** §6.5：下架不解除收藏关系。若实现里顺手加了「非 ONLINE 一律 false」，这条会红 */
    @Test
    void favoritedRelationshipSurvivesOffline() {
        Fixture fixture = createNote(NoteStatus.OFFLINE, null);
        favorite(viewerId, fixture.noteId());

        NoteDetailResponse detail = noteService.detail(fixture.noteId(), viewerId);

        assertThat(detail.status()).isEqualTo(NoteStatus.OFFLINE);
        assertThat(detail.isFavorited()).isTrue();
    }

    /**
     * 没设过头像时必须是 null。{@code StorageService#publicUrl} 是裸字符串拼接，
     * 传 null 会拼出一个以 {@code /null} 结尾的 URL——那东西看着合法，前端会照单全收地
     * 塞进 {@code <img src>}，然后拿到 404。
     */
    @Test
    void missingAvatarYieldsNullRatherThanABrokenUrl() {
        Fixture fixture = createNote(NoteStatus.ONLINE, null);

        assertThat(noteService.detail(fixture.noteId(), viewerId).uploader().avatarUrl()).isNull();
    }

    @Test
    void unknownNoteIsNotFound() {
        assertThatThrownBy(() -> noteService.detail(999_999_999L, viewerId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    /** 不存在的笔记不该被那次「先自增」的 UPDATE 影响——它的 WHERE 根本匹配不到行 */
    @Test
    void unknownNoteIsNotCountedAsAView() {
        Fixture fixture = createNote(NoteStatus.ONLINE, null);

        assertThatThrownBy(() -> noteService.detail(999_999_999L, viewerId))
                .isInstanceOf(BusinessException.class);

        assertThat(noteMapper.selectById(fixture.noteId()).getViewCount()).isZero();
    }

    // ==================== 夹具 ====================

    private record Fixture(Long noteId, Long uploaderId) {
    }

    private Fixture createNote(NoteStatus status, String avatarKey) {
        Long uploaderId = insertUser("uploader", avatarKey, uploaderCollegeId);

        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setSummary("简介");
        note.setTeacher("张老师");
        note.setStatus(status.name());
        noteMapper.insert(note);

        // 直接插 note_file 而不走上传流程：详情要验的是「读」，
        // 用上传流程造数据会把 NoteFilePolicy 与 OSS 一起拖进来
        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey("notes/2026/10/" + UUID.randomUUID() + ".pdf");
        file.setOriginalName("课件.pdf");
        file.setSize(1048576L);
        file.setContentType("application/pdf");
        noteFileMapper.insert(file);

        return new Fixture(note.getId(), uploaderId);
    }

    /**
     * 建标签并挂到笔记上，返回它的 id。按 id 升序返回正是详情接口的排序口径，
     * 所以断言能用 {@code containsExactly} 而不是 {@code containsExactlyInAnyOrder}。
     */
    private Long attachTag(Long noteId, String name) {
        jdbcTemplate.update("INSERT INTO tag (name) VALUES (?)", name);
        Long tagId = jdbcTemplate.queryForObject("SELECT id FROM tag WHERE name = ?", Long.class, name);
        jdbcTemplate.update("INSERT INTO note_tag (note_id, tag_id) VALUES (?, ?)", noteId, tagId);
        return tagId;
    }

    private void favorite(Long userId, Long noteId) {
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setNoteId(noteId);
        favoriteMapper.insert(favorite);
    }

    private Long insertCollege(String name) {
        College college = new College();
        college.setName(name);
        collegeMapper.insert(college);
        return college.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser(String role, String avatarKey, Long collegeId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(MARKER + role + "-" + suffix);
        user.setEmail(MARKER + role + "-" + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + role);
        user.setCollegeId(collegeId);
        user.setAvatar(avatarKey);
        userMapper.insert(user);
        return user.getId();
    }
}
