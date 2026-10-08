package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Favorite;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.favorite.FavoriteMapper;
import com.wlf.favorite.FavoriteService;
import com.wlf.favorite.dto.FavoriteItemResponse;
import com.wlf.favorite.dto.FavoriteListQuery;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link NoteService#delete} 的用例。见《概要设计》§5.4、§4.1、§3.3、§6.5。
 *
 * <p>重点在几条<b>写错了不会立刻暴露</b>的规则：
 *
 * <ul>
 *   <li><b>只改该改的列</b>——{@code updated_at}、{@code favorite_count}、{@code note_tag} 都不能被碰到。
 *       它们各自都有一个看似「顺手」的理由去改，而每个都错</li>
 *   <li><b>幂等 200 不等于什么都不做</b>——上一次清理失败了，这一次要接着清</li>
 *   <li><b>清理失败不能让删除失败</b>，但也不能把 {@code is_purged} 谎报成 1</li>
 *   <li><b>归属校验</b>——这是全项目第一处 40300</li>
 * </ul>
 *
 * <p>与 {@code NoteDetailServiceTest} 同样起真库并 mock 掉存储层：这里验的是「哪几列被写了、
 * 写成了什么」，换成 mock 的 Mapper 就只剩「调没调这个方法」。用例随事务回滚，数据自造。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NoteDeleteServiceTest {

    private static final String MARKER = "ZZTEST-DEL-";

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteFileMapper noteFileMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    /** 只为验「删完之后收藏列表渲染成占位」——那条规则横跨两个模块，值得在这里接一次 */
    @Autowired
    private FavoriteService favoriteService;

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
    }

    // ==================== 正常路径 ====================

    /** 状态置为 DELETED、写下删除时刻、对象被清理并记账——四件事一起对才算删干净 */
    @Test
    void deletingMarksTheNoteDeletedAndPurgesItsObject() {
        Fixture fixture = createNote(NoteStatus.ONLINE);

        noteService.delete(fixture.noteId(), fixture.uploaderId());

        Note note = noteMapper.selectById(fixture.noteId());
        assertThat(note.getStatus()).isEqualTo(NoteStatus.DELETED.name());
        assertThat(note.getDeletedAt()).isNotNull();

        verify(storageService).delete(Bucket.NOTE, fixture.storageKey());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged()).isEqualTo(1);
    }

    /** §4.1 的图里有 {@code OFFLINE→DELETED}：管理员下架过的笔记，上传者仍然有权删掉 */
    @Test
    void anOfflineNoteCanStillBeDeletedByItsUploader() {
        Fixture fixture = createNote(NoteStatus.OFFLINE);

        noteService.delete(fixture.noteId(), fixture.uploaderId());

        assertThat(noteMapper.selectById(fixture.noteId()).getStatus())
                .isEqualTo(NoteStatus.DELETED.name());
    }

    /**
     * 幂等：第二次不报错、状态不变，而且<b>不会重复清理</b>——第一次已经把 {@code is_purged}
     * 置成 1，第二次读到的就是「没什么可清」。
     */
    @Test
    void deletingTwiceIsIdempotentAndPurgesOnlyOnce() {
        Fixture fixture = createNote(NoteStatus.ONLINE);

        noteService.delete(fixture.noteId(), fixture.uploaderId());
        noteService.delete(fixture.noteId(), fixture.uploaderId());

        assertThat(noteMapper.selectById(fixture.noteId()).getStatus())
                .isEqualTo(NoteStatus.DELETED.name());
        verify(storageService, times(1)).delete(Bucket.NOTE, fixture.storageKey());
    }

    // ==================== 清理失败与重试 ====================

    /**
     * 清理失败不能让删除失败：状态已经落库，用户要的「这篇别在站内出现了」已经达成，
     * 而对象清理只影响存储回收（§6.2 之后不再为它签发任何 URL）。
     * 同时 {@code is_purged} 必须<b>留在 0</b>——谎报成 1 就再也没人对账得出来。
     */
    @Test
    void aFailedPurgeStillDeletesTheNoteButLeavesTheMarkUnset() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        whenDeleteThrows();

        noteService.delete(fixture.noteId(), fixture.uploaderId());

        assertThat(noteMapper.selectById(fixture.noteId()).getStatus())
                .isEqualTo(NoteStatus.DELETED.name());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged()).isZero();
    }

    /**
     * <b>幂等 200 不等于什么都不做</b>：上一次清理失败留下了 {@code is_purged = 0}，
     * 这一次必须接着清，否则那个对象永远成为孤儿。
     *
     * <p>这条兜底是有必要的：{@code NoteService#purgeQuietly} 的注释说「残留由每日维护脚本对账」，
     * 而那个脚本是本仓库之外的 shell，此前只清 {@code password_reset_token}。
     * 让重复删除顺带成为重试路径，兜底就不必依赖它。
     */
    @Test
    void repeatingTheDeleteRetriesAPurgeThatPreviouslyFailed() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        whenDeleteThrows();
        noteService.delete(fixture.noteId(), fixture.uploaderId());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged()).isZero();

        whenDeleteSucceeds();
        noteService.delete(fixture.noteId(), fixture.uploaderId());

        verify(storageService, times(2)).delete(Bucket.NOTE, fixture.storageKey());
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged()).isEqualTo(1);
    }

    /**
     * {@code note_file} 行缺失时照样删得掉，只是没什么可清。
     * 状态那一步已经落库，为一条脏数据把整次删除判失败是本末倒置。
     */
    @Test
    void deletingSucceedsWhenTheFileRowIsMissing() {
        Long uploaderId = insertUser("uploader");
        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "没有文件行");
        note.setStatus(NoteStatus.ONLINE.name());
        noteMapper.insert(note);

        noteService.delete(note.getId(), uploaderId);

        assertThat(noteMapper.selectById(note.getId()).getStatus())
                .isEqualTo(NoteStatus.DELETED.name());
        verify(storageService, never()).delete(any(), anyString());
    }

    // ==================== 不该被碰的列 ====================

    /**
     * 删除不能碰 {@code updated_at}。它的语义是「用户最后编辑笔记的时间」（§3.3），
     * 删除不是编辑。
     *
     * <p>写法是有意的：{@code updated_at} 是秒精度的 {@code DATETIME}，
     * 「操作一次再比较前后」在同秒内根本发现不了问题。所以先把它设成一个过去的时间再操作——
     * 若哪天有人给这一列加回 {@code ON UPDATE CURRENT_TIMESTAMP}，或在这个 UPDATE 里顺手写了个
     * {@code NOW()}，断言随即失败。这样无需 sleep 就能当哨兵用
     * （与 {@code NoteDetailServiceTest#viewingDoesNotTouchUpdatedAt} 同一手法）。
     */
    @Test
    void deletingDoesNotTouchUpdatedAt() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        LocalDateTime editedAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE note SET updated_at = ? WHERE id = ?", editedAt, fixture.noteId());

        noteService.delete(fixture.noteId(), fixture.uploaderId());
        assertThat(noteMapper.selectById(fixture.noteId()).getUpdatedAt()).isEqualTo(editedAt);

        // 幂等的那一次同样不能碰它
        noteService.delete(fixture.noteId(), fixture.uploaderId());
        assertThat(noteMapper.selectById(fixture.noteId()).getUpdatedAt()).isEqualTo(editedAt);
    }

    /**
     * §6.5：删笔记<b>不解除收藏关系</b>，所以 {@code favorite_count} 也不该动。
     * 顺手减一的写法看起来挺合理（笔记没了，收藏自然没了），但那会让收藏数比
     * {@code favorite} 表的实际行数少，而那张表的行还在（收藏列表要拿它渲染占位）。
     */
    @Test
    void deletingKeepsTheFavoriteRelationshipAndItsCount() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        Long readerId = insertUser("reader");
        Favorite favorite = new Favorite();
        favorite.setUserId(readerId);
        favorite.setNoteId(fixture.noteId());
        favoriteMapper.insert(favorite);
        jdbcTemplate.update("UPDATE note SET favorite_count = 1 WHERE id = ?", fixture.noteId());

        noteService.delete(fixture.noteId(), fixture.uploaderId());

        assertThat(noteMapper.selectById(fixture.noteId()).getFavoriteCount()).isEqualTo(1L);
        assertThat(favoriteMapper.selectCount(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getNoteId, fixture.noteId()))).isEqualTo(1);
    }

    /**
     * 删完之后，收藏列表里那一项要渲染成占位——这条把「删除」与「我的收藏」第一次真正接上：
     * 此前 {@code FavoriteListServiceTest} 只能靠直接改库制造 {@code DELETED}。
     */
    @Test
    void aDeletedNoteShowsUpAsAPlaceholderInTheFavoritesList() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        Long readerId = insertUser("reader");
        Favorite favorite = new Favorite();
        favorite.setUserId(readerId);
        favorite.setNoteId(fixture.noteId());
        favoriteMapper.insert(favorite);

        noteService.delete(fixture.noteId(), fixture.uploaderId());

        FavoriteListQuery query = new FavoriteListQuery();
        FavoriteItemResponse item = favoriteService.listFavorites(readerId, query).items().get(0);
        assertThat(item.status()).isEqualTo(NoteStatus.DELETED);
        // 身份保留（认得出收藏的是什么），内容隐去
        assertThat(item.title()).isEqualTo(MARKER + "标题");
        assertThat(item.uploader()).isNull();
    }

    /** 软删除只动 note 与 note_file 两处；标签关联是「用户当时打的标」，不是可回收的东西 */
    @Test
    void deletingKeepsTheTagRows() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        jdbcTemplate.update("INSERT INTO tag (name) VALUES (?)", MARKER + "标签");
        Long tagId = jdbcTemplate.queryForObject("SELECT id FROM tag WHERE name = ?",
                Long.class, MARKER + "标签");
        jdbcTemplate.update("INSERT INTO note_tag (note_id, tag_id) VALUES (?, ?)",
                fixture.noteId(), tagId);

        noteService.delete(fixture.noteId(), fixture.uploaderId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_tag WHERE note_id = ?", Integer.class, fixture.noteId()))
                .isEqualTo(1);
    }

    // ==================== 归属与存在性 ====================

    /** 全项目第一处归属校验：非本人 → 40300，且存储层一次都不该被调用 */
    @Test
    void deletingSomebodyElsesNoteIsForbidden() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        Long intruderId = insertUser("intruder");

        assertThatThrownBy(() -> noteService.delete(fixture.noteId(), intruderId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(noteMapper.selectById(fixture.noteId()).getStatus())
                .isEqualTo(NoteStatus.ONLINE.name());
        verify(storageService, never()).delete(any(), anyString());
    }

    /**
     * 管理员也不能删：§5.4 写的是「上传者本人」，§5.6 给管理员的是下架 / 恢复。
     * 本接口不看 {@code role}，所以管理员与陌生人走的是同一条 40300。
     */
    @Test
    void deletingIsNotAllowedForAnAdminEither() {
        Fixture fixture = createNote(NoteStatus.ONLINE);
        Long adminId = insertUser("admin");

        assertThatThrownBy(() -> noteService.delete(fixture.noteId(), adminId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    /** 存在性先于归属：id 不存在就报不存在 */
    @Test
    void deletingAnUnknownNoteIsNotFound() {
        assertThatThrownBy(() -> noteService.delete(999_999_999L, insertUser("anyone")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ==================== 夹具 ====================

    private record Fixture(Long noteId, Long fileId, Long uploaderId, String storageKey) {
    }

    private Fixture createNote(NoteStatus status) {
        Long uploaderId = insertUser("uploader");

        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus(status.name());
        noteMapper.insert(note);

        // 直接插 note_file 而不走上传流程：本用例验的是删除，把 NoteFilePolicy 与 OSS 拖进来
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
