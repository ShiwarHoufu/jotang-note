package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 孤儿对象对账的用例。见《概要设计》§3.3、§7.3。
 *
 * <p>重点全在<b>「它不该碰什么」</b>：{@code is_purged = 0} 是 {@code note_file} 的建表默认值，
 * <b>每一篇活着的笔记都是 0</b>，少了 {@code status = DELETED} 候选集就会变成「全部在线笔记」，
 * 对账会安静地把站上所有文件删光。这是本文件最重要的一条。
 *
 * <p>与 {@code NoteDeleteServiceTest} 同样起真库并 mock 掉存储层：这里验的是「哪些行被选中、
 * {@code is_purged} 变成什么」，而候选集的选取恰恰是这条链路上最容易写错的地方。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class OrphanObjectSweeperTest {

    private static final String MARKER = "ZZTEST-SWEEP-";

    /** 用例里显式传的批量上限，与被测类里那个配置值无关 */
    private static final int LIMIT = 100;

    @Autowired
    private NoteService noteService;

    @Autowired
    private OrphanObjectSweeper sweeper;

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
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();

        // 候选集查的是整张表，开发库里若残留着别处删过却没清掉的行，计数断言就会飘。
        // 先把它们全标成已清理——用例结束随事务回滚，不会真的动到那些行。
        jdbcTemplate.update("""
                UPDATE note_file nf JOIN note n ON n.id = nf.note_id
                SET nf.is_purged = 1
                WHERE n.status = ? AND nf.is_purged = 0
                """, NoteStatus.DELETED.name());
    }

    // ==================== 兜底路径本身 ====================

    /**
     * 走一遍真实链路：删除只改状态、一次都不碰 OSS，{@code is_purged} 留 0 →
     * 对账扫到这一行 → 对象清掉、标记补上。
     *
     * <p><b>对象回收全靠这里</b>——删除路径已经不负责清理了，这个接缝断了只会表现为
     * 「存储慢慢涨」，不会有任何报错。
     */
    @Test
    void sweepsWhatTheDeletePathLeavesBehind() {
        Fixture fixture = createNote(NoteStatus.ONLINE);

        noteService.delete(fixture.noteId(), fixture.uploaderId());

        assertThat(noteMapper.selectById(fixture.noteId()).getStatus())
                .isEqualTo(NoteStatus.DELETED.name());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged())
                .isEqualTo(0);
        verify(storageService, never()).delete(any(), anyString());

        assertThat(noteService.sweepOrphanObjects(LIMIT)).isEqualTo(1);

        verify(storageService).delete(Bucket.NOTE, fixture.storageKey());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged())
                .isEqualTo(1);
    }

    /** 定时入口只是转发，业务规则全在 Service 里；顺带验一次配置项真的绑上了 */
    @Test
    void theScheduledEntryPointDelegatesToTheSameSweep() {
        Fixture fixture = createDeletedNote();

        sweeper.sweep();

        verify(storageService).delete(Bucket.NOTE, fixture.storageKey());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged())
                .isEqualTo(1);
    }

    // ==================== 不该碰什么 ====================

    /** {@code is_purged = 0} 不是「待清理」的意思，它是建表默认值——活着的笔记都是 0 */
    @Test
    void onlineNotesAreNeverSwept() {
        Fixture fixture = createNote(NoteStatus.ONLINE);

        assertThat(noteService.sweepOrphanObjects(LIMIT)).isZero();

        verify(storageService, never()).delete(Bucket.NOTE, fixture.storageKey());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged())
                .isEqualTo(0);
    }

    /** 已下架同理：下架只改 {@code status}，对象必须留着，否则恢复之后笔记打不开 */
    @Test
    void offlineNotesAreNeverSwept() {
        Fixture fixture = createNote(NoteStatus.OFFLINE);

        assertThat(noteService.sweepOrphanObjects(LIMIT)).isZero();

        verify(storageService, never()).delete(Bucket.NOTE, fixture.storageKey());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged())
                .isEqualTo(0);
    }

    /** 已经清过的行不该重复出现在候选集里 */
    @Test
    void skipsNotesThatWereAlreadyPurged() {
        Fixture fixture = createDeletedNote();
        markPurged(fixture);

        assertThat(noteService.sweepOrphanObjects(LIMIT)).isZero();

        verify(storageService, never()).delete(Bucket.NOTE, fixture.storageKey());
    }

    /** 空 {@code storage_key}（只可能来自手工改库）既不删也不标记，否则它每轮都会占一个名额 */
    @Test
    void aRowWithABlankStorageKeyIsNotSwept() {
        Fixture fixture = createDeletedNote();
        jdbcTemplate.update("UPDATE note_file SET storage_key = '' WHERE id = ?", fixture.fileId());

        assertThat(noteService.sweepOrphanObjects(LIMIT)).isZero();

        verify(storageService, never()).delete(any(), anyString());
    }

    // ==================== 批量与重试 ====================

    /** 一轮只做 {@code limit} 条，剩下的下一轮自然接上——不需要游标或分页状态 */
    @Test
    void oneRoundStopsAtTheBatchLimitAndTheNextRoundPicksUpTheRest() {
        Fixture older = createDeletedNote();
        Fixture newer = createDeletedNote();

        assertThat(noteService.sweepOrphanObjects(1)).isEqualTo(1);

        // 顺序按 note.id 升序，先造的那条先做
        assertThat(noteFileMapper.selectById(older.fileId()).getIsPurged()).isEqualTo(1);
        assertThat(noteFileMapper.selectById(newer.fileId()).getIsPurged()).isEqualTo(0);

        assertThat(noteService.sweepOrphanObjects(1)).isEqualTo(1);

        assertThat(noteFileMapper.selectById(newer.fileId()).getIsPurged()).isEqualTo(1);
    }

    /** 清理失败只影响这一条：{@code is_purged} 停在 0（不能谎报成 1，对账的判据正是这一列），且不往外抛 */
    @Test
    void aFailedOssDeleteLeavesTheMarkUnsetForTheNextRound() {
        Fixture fixture = createDeletedNote();
        whenDeleteThrows();

        assertThat(noteService.sweepOrphanObjects(LIMIT)).isZero();

        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged()).isEqualTo(0);

        whenDeleteSucceeds();
        assertThat(noteService.sweepOrphanObjects(LIMIT)).isEqualTo(1);
    }

    // ==================== 夹具 ====================

    private record Fixture(Long noteId, Long fileId, Long uploaderId, String storageKey) {
    }

    private Fixture createNote(NoteStatus status) {
        Long uploaderId = insertUser();

        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus(status.name());
        noteMapper.insert(note);

        // 直接插 note_file 而不走上传流程：本用例验的是对账，把 NoteFilePolicy 与 OSS 拖进来
        // 只会让夹具变长，而对象键只要是个确定值就够了
        String storageKey = "notes/2026/10/" + UUID.randomUUID() + ".pdf";
        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey(storageKey);
        file.setOriginalName("课件.pdf");
        file.setSize(1048576L);
        file.setContentType("application/pdf");
        noteFileMapper.insert(file);

        return new Fixture(note.getId(), file.getId(), uploaderId, storageKey);
    }

    /** 已删除、对象没清的一行——对账要处理的正是这个状态，不必每次都走一遍删除流程 */
    private Fixture createDeletedNote() {
        return createNote(NoteStatus.DELETED);
    }

    private void markPurged(Fixture fixture) {
        jdbcTemplate.update("UPDATE note_file SET is_purged = 1 WHERE id = ?", fixture.fileId());
    }

    private void whenDeleteThrows() {
        doThrow(new IllegalStateException("模拟 OSS 故障")).when(storageService).delete(any(), anyString());
    }

    private void whenDeleteSucceeds() {
        doNothing().when(storageService).delete(any(), anyString());
    }

    private Long insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER);
        user.setCollegeId(insertCollege());
        userMapper.insert(user);
        return user.getId();
    }
}
