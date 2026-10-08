package com.wlf.note;

import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.common.PageResponse;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.User;
import com.wlf.note.dto.MyNoteItemResponse;
import com.wlf.note.dto.MyNoteListQuery;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NoteService#listMyNotes} 的用例。见《概要设计》§5.4、§4.1。
 *
 * <p>重点在两件事：
 *
 * <ul>
 *   <li><b>筛的是「我的」还是「不是删除的」</b>——两个条件都必须落在 SQL 里，
 *       漏掉任一个都会静默多出别人的笔记、或多出用户以为已经删掉的笔记</li>
 *   <li><b>{@code total} 也必须是筛过的数</b>。这条比它看起来重要：过滤若改在 Service 里做，
 *       列表看着对，但前端会按 {@code total} 算出一堆点进去是空的页</li>
 * </ul>
 *
 * <p>数据自造成、随事务回滚。本列表按 {@code uploader_id} 圈定范围，
 * 所以每个用例用自己的用户即可完全隔离，不需要自造课程来划边界。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class MyNoteListServiceTest {

    private static final String MARKER = "ZZTEST-MY-";

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long courseId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(null).get(0).getId();
    }

    @Test
    void onlyMyOwnNotesAreReturned() {
        Long me = insertUser("me");
        Long other = insertUser("other");
        Long mine = insertNote(me, NoteStatus.ONLINE, MARKER + "我的", LocalDateTime.now());
        insertNote(other, NoteStatus.ONLINE, MARKER + "别人的", LocalDateTime.now());

        List<MyNoteItemResponse> items = list(me).items();

        assertThat(items).extracting(MyNoteItemResponse::id).containsExactly(mine);
    }

    /**
     * 本用例是这条需求的核心：删掉的笔记不再出现在「我的上传」里。
     *
     * <p>注意它与 {@code OFFLINE} 的区别——下一条用例证明已下架的是<b>照常返回</b>的。
     * §4.1 表格里「我的上传」这一列的两档正是这两条用例。
     */
    @Test
    void deletedNotesAreNotReturned() {
        Long me = insertUser("me");
        insertNote(me, NoteStatus.ONLINE, MARKER + "在线的", LocalDateTime.now());
        insertNote(me, NoteStatus.DELETED, MARKER + "已删除的", LocalDateTime.now());

        List<MyNoteItemResponse> items = list(me).items();

        assertThat(items).extracting(MyNoteItemResponse::title)
                .containsExactly(MARKER + "在线的");
    }

    /** 已下架照常返回，并带上 {@code OFFLINE} 让前端标注——它不是「删掉了」，只是被下架 */
    @Test
    void offlineNotesAreReturnedWithTheirStatus() {
        Long me = insertUser("me");
        Long offline = insertNote(me, NoteStatus.OFFLINE, MARKER + "已下架", LocalDateTime.now());

        MyNoteItemResponse item = list(me).items().get(0);

        assertThat(item.id()).isEqualTo(offline);
        assertThat(item.status()).isEqualTo(NoteStatus.OFFLINE);
    }

    /**
     * {@code total} 是<b>筛过之后</b>的数：不含别人的、也不含自己已删除的。
     *
     * <p>这条盯着「过滤别挪到 Service 里做」——那样列表看着是对的，
     * 但分页插件回填的 {@code total} 会把被排除的也数进去，前端据此算出多余的页。
     */
    @Test
    void totalExcludesOtherPeoplesAndDeletedNotes() {
        Long me = insertUser("me");
        Long other = insertUser("other");
        insertNote(me, NoteStatus.ONLINE, MARKER + "留下", LocalDateTime.now());
        insertNote(me, NoteStatus.DELETED, MARKER + "删掉", LocalDateTime.now());
        insertNote(other, NoteStatus.ONLINE, MARKER + "别人的", LocalDateTime.now());

        PageResponse<MyNoteItemResponse> page = list(me);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
    }

    /** 按上传时间倒序：最近传的在最前 */
    @Test
    void notesAreOrderedByCreatedAtDescending() {
        Long me = insertUser("me");
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        insertNote(me, NoteStatus.ONLINE, MARKER + "最早", base);
        insertNote(me, NoteStatus.ONLINE, MARKER + "最晚", base.plusDays(2));
        insertNote(me, NoteStatus.ONLINE, MARKER + "居中", base.plusDays(1));

        assertThat(list(me).items()).extracting(MyNoteItemResponse::title)
                .containsExactly(MARKER + "最晚", MARKER + "居中", MARKER + "最早");
    }

    /**
     * 标签一次批量取回并按 {@code noteId} 归位；没有标签的那篇给<b>空数组而不是 null</b>。
     *
     * <p>挑两篇（一篇有标签、一篇没有）而不是只挑一篇：只验「有的那个」的话，
     * {@code getOrDefault} 的默认值写成 {@code null} 也能过，而前端会在遍历时炸。
     */
    @Test
    void tagsAreAssembledPerNoteAndMissingOnesBecomeEmptyArrays() {
        Long me = insertUser("me");
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        Long tagged = insertNote(me, NoteStatus.ONLINE, MARKER + "有标签", base.plusDays(1));
        Long bare = insertNote(me, NoteStatus.ONLINE, MARKER + "没标签", base);
        Long tagA = insertTag(MARKER + "A");
        Long tagB = insertTag(MARKER + "B");
        linkTags(tagged, tagA, tagB);

        List<MyNoteItemResponse> items = list(me).items();

        assertThat(items.get(0).id()).isEqualTo(tagged);
        assertThat(items.get(0).tags()).extracting(tag -> tag.id()).containsExactly(tagA, tagB);
        assertThat(items.get(1).id()).isEqualTo(bare);
        assertThat(items.get(1).tags()).isEmpty();
    }

    /** 空页要看得出「没数据」，且不能因为 {@code IN ()} 语法错误炸掉 */
    @Test
    void anUploaderWithNoNotesGetsAnEmptyPage() {
        Long me = insertUser("me");
        insertNote(insertUser("other"), NoteStatus.ONLINE, MARKER + "别人的", LocalDateTime.now());

        PageResponse<MyNoteItemResponse> page = list(me);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    /** 只有别人传过笔记的用户，也应当是空页——删到只剩别人时最容易暴露条件写错 */
    @Test
    void anUploaderWhoseNotesAreAllDeletedSeesNothing() {
        Long me = insertUser("me");
        insertNote(me, NoteStatus.DELETED, MARKER + "删掉的", LocalDateTime.now());
        insertNote(me, NoteStatus.DELETED, MARKER + "也删掉的", LocalDateTime.now());

        PageResponse<MyNoteItemResponse> page = list(me);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    /** 分页深度上限对所有列表统一生效（§3.3），本列表也不例外 */
    @Test
    void deepPaginationIsRejected() {
        Long me = insertUser("me");
        MyNoteListQuery query = new MyNoteListQuery();
        query.setPage(51);

        assertThatThrownBy(() -> noteService.listMyNotes(me, query))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat((List<?>) e.getData()).singleElement().isInstanceOf(FieldViolation.class);
                });
    }

    // ==================== 夹具 ====================

    private PageResponse<MyNoteItemResponse> list(Long uploaderId) {
        return noteService.listMyNotes(uploaderId, new MyNoteListQuery());
    }

    /**
     * 直接插库造数据。{@code created_at} 显式写入而不是靠 DDL 的
     * {@code DEFAULT CURRENT_TIMESTAMP}：排序用例需要确定的时间差，
     * 而同秒内插入的几行在秒精度列上并列，断言不出顺序。
     */
    private Long insertNote(Long uploaderId, NoteStatus status, String title, LocalDateTime createdAt) {
        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(title);
        note.setStatus(status.name());
        noteMapper.insert(note);

        jdbcTemplate.update("UPDATE note SET created_at = ? WHERE id = ?", createdAt, note.getId());
        return note.getId();
    }

    private Long insertTag(String name) {
        jdbcTemplate.update("INSERT INTO tag (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM tag WHERE name = ?", Long.class, name);
    }

    private void linkTags(Long noteId, Long... tagIds) {
        for (Long tagId : tagIds) {
            jdbcTemplate.update("INSERT INTO note_tag (note_id, tag_id) VALUES (?, ?)", noteId, tagId);
        }
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
