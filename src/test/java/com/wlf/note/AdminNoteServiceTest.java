package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link NoteService#adminOffline} / {@link NoteService#adminRestore} 的用例。
 * 见《概要设计》§5.6、§4.1、§3.3。
 *
 * <p>重点在几条<b>写错了不会当场暴露</b>的规则：
 *
 * <ul>
 *   <li><b>只改 {@code status} 一列</b>——尤其不碰 {@code updated_at}。下架过的笔记
 *       不该因为被下架就排到「最近更新」的最前面</li>
 *   <li><b>不动 OSS 对象</b>——下架是可逆的，对象删了恢复之后就打不开。这与删除正相反</li>
 *   <li><b>三种边界各自收口</b>：不存在 40400、已删除 40301、已在目标状态幂等 200。
 *       后两种若丢给 {@code NoteStateMachine}，都会变成 50000</li>
 *   <li><b>不做归属校验</b>——管理员改的是别人的笔记，这是本类唯一一处如此</li>
 * </ul>
 *
 * <p>与 {@code NoteDeleteServiceTest} 同样起真库并 mock 掉存储层；这里 mock 它的意义是
 * <b>断言一次都没被调用</b>——「下架不动对象」最直接的验收方式就是存储层全程静默。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AdminNoteServiceTest {

    private static final String MARKER = "ZZTEST-ADM-";

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

    // ==================== 正常迁移 ====================

    /** 下架：ONLINE → OFFLINE，且存储层全程静默（对象留着，恢复才可能） */
    @Test
    void offliningMovesAnOnlineNoteToOfflineWithoutTouchingStorage() {
        Long noteId = createNote(NoteStatus.ONLINE);

        noteService.adminOffline(noteId);

        assertThat(noteMapper.selectById(noteId).getStatus()).isEqualTo(NoteStatus.OFFLINE.name());
        verifyNoInteractions(storageService);
    }

    /** 恢复：OFFLINE → ONLINE。这两条边是状态机里早就写好、此前一直没有生产调用方的那两条 */
    @Test
    void restoringMovesAnOfflineNoteBackOnline() {
        Long noteId = createNote(NoteStatus.OFFLINE);

        noteService.adminRestore(noteId);

        assertThat(noteMapper.selectById(noteId).getStatus()).isEqualTo(NoteStatus.ONLINE.name());
        verifyNoInteractions(storageService);
    }

    /** 下架再恢复要能回到原点——这是「可逆」这条承诺最直接的验收 */
    @Test
    void offliningThenRestoringReturnsToTheOriginalState() {
        Long noteId = createNote(NoteStatus.ONLINE);

        noteService.adminOffline(noteId);
        noteService.adminRestore(noteId);

        assertThat(noteMapper.selectById(noteId).getStatus()).isEqualTo(NoteStatus.ONLINE.name());
    }

    /**
     * <b>管理员改的是别人的笔记</b>——本类唯一一个不做归属校验的写方法。
     *
     * <p>这条同时兼作「admin 前缀不能被误当成普通写方法」的守卫：若哪天有人给它加上了
     * 「非本人 40300」，本用例会立刻失败。
     */
    @Test
    void anAdminCanOfflineSomebodyElsesNote() {
        Long ownerId = insertUser("owner");
        Long noteId = createNote(ownerId, NoteStatus.ONLINE);

        assertThatCode(() -> noteService.adminOffline(noteId)).doesNotThrowAnyException();

        assertThat(noteMapper.selectById(noteId).getStatus()).isEqualTo(NoteStatus.OFFLINE.name());
    }

    // ==================== 只该改的那一列 ====================

    /**
     * 下架不能碰 {@code updated_at}。它的语义是「用户最后编辑笔记的时间」（§3.3），
     * 管理员下架不是用户编辑。
     *
     * <p>写法与 {@code NoteDeleteServiceTest#deletingDoesNotTouchUpdatedAt} 同一手法：
     * 先把它设成一个过去的时间再操作。若哪天有人给这一列加回 {@code ON UPDATE CURRENT_TIMESTAMP}，
     * 或在写语句里顺手写了个 {@code NOW()}，断言随即失败——无需 sleep 即可当哨兵。
     */
    @Test
    void adminTransitionsDoNotTouchUpdatedAt() {
        Long noteId = createNote(NoteStatus.ONLINE);
        LocalDateTime editedAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE note SET updated_at = ? WHERE id = ?", editedAt, noteId);

        noteService.adminOffline(noteId);
        assertThat(noteMapper.selectById(noteId).getUpdatedAt()).isEqualTo(editedAt);

        noteService.adminRestore(noteId);
        assertThat(noteMapper.selectById(noteId).getUpdatedAt()).isEqualTo(editedAt);
    }

    /** 除 {@code status} 之外一个字节都不该动：计数、创建时间、删除时刻、标签、文件行 */
    @Test
    void adminTransitionsOnlyTouchTheStatusColumn() {
        Long noteId = createNote(NoteStatus.ONLINE);
        LocalDateTime createdAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("""
                UPDATE note SET created_at = ?, view_count = 7, download_count = 5, favorite_count = 3
                WHERE id = ?""", createdAt, noteId);
        Long tagId = insertTag(MARKER + "标签");
        jdbcTemplate.update("INSERT INTO note_tag (note_id, tag_id) VALUES (?, ?)", noteId, tagId);
        NoteFile before = noteFileMapper.selectOne(
                Wrappers.<NoteFile>lambdaQuery().eq(NoteFile::getNoteId, noteId));

        noteService.adminOffline(noteId);

        Note note = noteMapper.selectById(noteId);
        assertThat(note.getCreatedAt()).isEqualTo(createdAt);
        assertThat(note.getViewCount()).isEqualTo(7);
        assertThat(note.getDownloadCount()).isEqualTo(5);
        assertThat(note.getFavoriteCount()).isEqualTo(3);
        assertThat(note.getDeletedAt()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_tag WHERE note_id = ?", Integer.class, noteId)).isEqualTo(1);

        NoteFile after = noteFileMapper.selectById(before.getId());
        assertThat(after.getStorageKey()).isEqualTo(before.getStorageKey());
        assertThat(after.getIsPurged()).isZero();
    }

    // ==================== 三种边界情形 ====================

    /**
     * 重复下架<b>幂等</b>：第二次不写库、不报错。与删除、取消收藏同口径。
     *
     * <p>这一支不能丢给状态机——{@code from == to} 在它那里是非法迁移，
     * 会抛 {@code IllegalStateException} 并最终翻成 50000。
     */
    @Test
    void offliningTwiceIsIdempotent() {
        Long noteId = createNote(NoteStatus.ONLINE);

        noteService.adminOffline(noteId);
        assertThatCode(() -> noteService.adminOffline(noteId)).doesNotThrowAnyException();

        assertThat(noteMapper.selectById(noteId).getStatus()).isEqualTo(NoteStatus.OFFLINE.name());
    }

    /** 对本来就在线的笔记点「恢复」同理，不该报错 */
    @Test
    void restoringANoteThatIsAlreadyOnlineIsIdempotent() {
        Long noteId = createNote(NoteStatus.ONLINE);

        assertThatCode(() -> noteService.adminRestore(noteId)).doesNotThrowAnyException();

        assertThat(noteMapper.selectById(noteId).getStatus()).isEqualTo(NoteStatus.ONLINE.name());
    }

    /**
     * <b>已删除的笔记报 40301，不是 50000。</b>
     *
     * <p>这一支必须显式判：{@code DELETED} 在状态机的表里是空集，丢给它就是
     * {@code IllegalStateException} → 50000，把「这篇已经删了」伪装成「服务器内部错误」。
     * 两个方向都要试——下架与恢复在状态机那里都走不通。
     */
    @Test
    void aDeletedNoteRejectsBothOfflineAndRestoreWithNoteUnavailable() {
        Long noteId = createNote(NoteStatus.DELETED);

        assertThatThrownBy(() -> noteService.adminOffline(noteId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNAVAILABLE));

        assertThatThrownBy(() -> noteService.adminRestore(noteId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNAVAILABLE));

        assertThat(noteMapper.selectById(noteId).getStatus()).isEqualTo(NoteStatus.DELETED.name());
    }

    @Test
    void anUnknownNoteIsNotFound() {
        assertThatThrownBy(() -> noteService.adminOffline(999_999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> noteService.adminRestore(999_999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ==================== 夹具 ====================

    private Long createNote(NoteStatus status) {
        return createNote(insertUser("owner"), status);
    }

    private Long createNote(Long uploaderId, NoteStatus status) {
        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus(status.name());
        noteMapper.insert(note);

        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey("notes/2026/10/" + UUID.randomUUID() + ".pdf");
        file.setOriginalName("课件.pdf");
        file.setSize(1048576L);
        file.setContentType("application/pdf");
        noteFileMapper.insert(file);

        return note.getId();
    }

    private Long insertTag(String name) {
        jdbcTemplate.update("INSERT INTO tag (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM tag WHERE name = ?", Long.class, name);
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
