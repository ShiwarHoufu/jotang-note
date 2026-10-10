package com.wlf.note;

import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.College;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NoteService#requireOwnFile} 的用例。见《概要设计》§5.8（ai 模块的编辑场景）。
 *
 * <p><b>为什么值得为它单独起真库</b>：{@code AiSummaryService} 的单元测试把 note 层整个
 * mock 掉了，于是 {@code selectFileOwnership} 这条新 SQL 一次都不会被执行——列名映射错了
 * 没有任何测试会红。这类错误不会报异常，只会安静地给出 {@code null}：
 *
 * <ul>
 *   <li>{@code storage_key} 映射不上 → 读 OSS 时拿到 null 的 key，报一个不知所云的错</li>
 *   <li>{@code size} 映射不上 → <b>「先按库里的大小拦住」那道守卫静默失效</b>，
 *       于是一个上百 MB 的对象会先被整个读进堆里再被拒。这正是
 *       {@code NoteFileOwnershipRow#size} 存在的唯一理由，所以它必须被断言</li>
 * </ul>
 *
 * <p>判门口径本身与 {@code NoteService#update} 完全一致，故这里只逐条对齐那四条边界，
 * 不重复 {@code NoteUpdateServiceTest} 已经覆盖的其它规则。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NoteFileAccessServiceTest {

    private static final String MARKER = "ZZTEST-FIL-";

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

    private Long courseId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(null).get(0).getId();
    }

    // ==================== 列映射（本测试类存在的主要理由）====================

    /**
     * 四列都要如实带回来。{@code size} 尤其重要——它是「读 OSS 之前先拦一道」的唯一依据。
     */
    @Test
    void returnsStorageKeySizeAndContentTypeOfTheOwnNote() {
        Fixture fixture = createNote(NoteStatus.ONLINE, "image/png", 2048L);

        NoteFileOwnershipRow row = noteService.requireOwnFile(fixture.noteId(), fixture.uploaderId());

        assertThat(row.getStorageKey()).isEqualTo(fixture.storageKey());
        assertThat(row.getContentType()).isEqualTo("image/png");
        assertThat(row.getSize()).isEqualTo(2048L);
        assertThat(row.getStatus()).isEqualTo(NoteStatus.ONLINE.name());
        assertThat(row.getUploaderId()).isEqualTo(fixture.uploaderId());
    }

    // ==================== 状态与归属 ====================

    /** 与 §5.4 的编辑同口径：下架了还能改元数据，自然也能拿它生成摘要 */
    @Test
    void offlineNoteIsStillReadable() {
        Fixture fixture = createNote(NoteStatus.OFFLINE, "image/jpeg", 1024L);

        assertThat(noteService.requireOwnFile(fixture.noteId(), fixture.uploaderId()).getStorageKey())
                .isEqualTo(fixture.storageKey());
    }

    /** DELETED 是终态，且对象已按 §3.3 清理——没有任何理由再为它读一次 OSS */
    @Test
    void deletedNoteIsRejected() {
        Fixture fixture = createNote(NoteStatus.DELETED, "image/png", 1024L);

        assertThatThrownBy(() -> noteService.requireOwnFile(fixture.noteId(), fixture.uploaderId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNAVAILABLE));
    }

    @Test
    void strangerIsForbidden() {
        Fixture fixture = createNote(NoteStatus.ONLINE, "image/png", 1024L);
        Long strangerId = insertUser("stranger");

        assertThatThrownBy(() -> noteService.requireOwnFile(fixture.noteId(), strangerId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void missingNoteIsNotFound() {
        assertThatThrownBy(() -> noteService.requireOwnFile(-1L, insertUser("anyone")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    /** 先判存在、再判归属：拿一个不存在的 id 配一个不相干的用户，得到的必须是 40400 而不是 40300 */
    @Test
    void missingNoteIsReportedAsNotFoundEvenForAStranger() {
        assertThatThrownBy(() -> noteService.requireOwnFile(-1L, insertUser("nobody")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ==================== 夹具 ====================

    private record Fixture(Long noteId, Long uploaderId, String storageKey) {
    }

    private Fixture createNote(NoteStatus status, String contentType, Long size) {
        Long uploaderId = insertUser("uploader");

        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus(status.name());
        noteMapper.insert(note);

        String storageKey = "notes/2026/10/" + UUID.randomUUID() + ".png";
        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey(storageKey);
        file.setOriginalName("板书.png");
        file.setSize(size);
        file.setContentType(contentType);
        noteFileMapper.insert(file);

        return new Fixture(note.getId(), uploaderId, storageKey);
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
