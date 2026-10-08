package com.wlf.note;

import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.common.PageResponse;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Favorite;
import com.wlf.entity.Note;
import com.wlf.entity.User;
import com.wlf.favorite.FavoriteMapper;
import com.wlf.note.dto.NoteListItemResponse;
import com.wlf.note.dto.NoteListQuery;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link NoteService#list} 的用例。见《概要设计》§5.4、§4.1。
 *
 * <p><b>每个用例都用一个自造的课程把结果圈住</b>（{@link #query()} 固定带上这个
 * {@code courseId}）。这不是偷懒：列表接口没有「只属于本次测试」的天然边界，开发库里
 * 已有的笔记会混进结果，用 {@code containsExactly} 断言就成了碰运气。造一个课程、
 * 把笔记都放进去、按它筛选，结果集才恰好是可数的。
 *
 * <p>重点在几条<b>写错了不会立刻暴露</b>的规则：只出 ONLINE、三种排序各自的键、
 * 并列时的次级排序、标签按笔记分组（而不是串到一起）、以及分页深度上限。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NoteListServiceTest {

    private static final String MARKER = "ZZTEST-";
    private static final String AVATAR_KEY = "avatars/7/abc.png";
    private static final String AVATAR_URL = "https://jotang-avatar.oss-cn-chengdu.aliyuncs.com/" + AVATAR_KEY;

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 5, 1, 0, 0);

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
    private FavoriteMapper favoriteMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StorageService storageService;

    private Long courseId;
    private Long uploaderId;
    private Long viewerId;

    @BeforeEach
    void setUp() {
        courseId = insertCourse();
        Long collegeId = insertCollege();
        uploaderId = insertUser("uploader", collegeId, null);
        viewerId = insertUser("viewer", collegeId, null);

        when(storageService.publicUrl(Bucket.AVATAR, AVATAR_KEY)).thenReturn(AVATAR_URL);
    }

    // ==================== 可见性 ====================

    /** §4.1：「列表/搜索」这一列对 OFFLINE、DELETED 是不可见——与详情页正相反 */
    @Test
    void onlyOnlineNotesAreListed() {
        Long online = insertNote("在线", NoteStatus.ONLINE, 0, 0, BASE_TIME);
        insertNote("已下架", NoteStatus.OFFLINE, 0, 0, BASE_TIME);
        insertNote("已删除", NoteStatus.DELETED, 0, 0, BASE_TIME);

        PageResponse<NoteListItemResponse> page = noteService.list(viewerId, query());

        assertThat(ids(page)).containsExactly(online);
        // total 也必须是筛过的数，否则前端会算出一堆点进去是空的页
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void noMatchingNoteYieldsEmptyPageRatherThanNull() {
        NoteListQuery query = query();
        query.setCourseId(999_999_999L);

        PageResponse<NoteListItemResponse> page = noteService.list(viewerId, query);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    // ==================== 排序 ====================

    @Test
    void latestSortsByUpdatedAtDescending() {
        Long old = insertNote("旧", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 1, 1, 0, 0));
        Long middle = insertNote("中", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 2, 1, 0, 0));
        Long recent = insertNote("新", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 3, 1, 0, 0));

        PageResponse<NoteListItemResponse> page = noteService.list(viewerId, query());

        assertThat(ids(page)).containsExactly(recent, middle, old);
        // 排序键就是序列化出去的那个字段，不是另算的
        assertThat(page.items().get(0).updatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 0));
    }

    @Test
    void hotViewSortsByViewCountDescending() {
        Long few = insertNote("少", NoteStatus.ONLINE, 1, 0, BASE_TIME);
        Long many = insertNote("多", NoteStatus.ONLINE, 99, 0, BASE_TIME);
        Long some = insertNote("中", NoteStatus.ONLINE, 10, 0, BASE_TIME);

        assertThat(idsWithSort(NoteListQuery.Sort.HOT_VIEW)).containsExactly(many, some, few);
    }

    @Test
    void hotDownloadSortsByDownloadCountDescending() {
        Long few = insertNote("少", NoteStatus.ONLINE, 0, 1, BASE_TIME);
        Long many = insertNote("多", NoteStatus.ONLINE, 0, 99, BASE_TIME);
        Long some = insertNote("中", NoteStatus.ONLINE, 0, 10, BASE_TIME);

        assertThat(idsWithSort(NoteListQuery.Sort.HOT_DOWNLOAD)).containsExactly(many, some, few);
    }

    /**
     * 并列时的次级排序。三篇 {@code view_count} 相同的笔记，只按它排的话 MySQL 不保证
     * 同分行的相对顺序稳定，翻页会看到重复或漏掉的笔记。实现里带了 {@code n.id DESC}，
     * 所以后插入的在前——若哪天那个次级键被去掉，这条会红。
     */
    @Test
    void tiesAreBrokenByIdDescending() {
        Long first = insertNote("甲", NoteStatus.ONLINE, 5, 0, BASE_TIME);
        Long second = insertNote("乙", NoteStatus.ONLINE, 5, 0, BASE_TIME);
        Long third = insertNote("丙", NoteStatus.ONLINE, 5, 0, BASE_TIME);

        assertThat(idsWithSort(NoteListQuery.Sort.HOT_VIEW)).containsExactly(third, second, first);
    }

    // ==================== 筛选 ====================

    @Test
    void courseFilterNarrowsTheResult() {
        Long mine = insertNote("本课程", NoteStatus.ONLINE, 0, 0, BASE_TIME);
        insertNote(insertCourse(), "别的课程", NoteStatus.ONLINE, 0, 0, BASE_TIME);

        assertThat(ids(pageOf(query()))).containsExactly(mine);
    }

    @Test
    void tagFilterNarrowsTheResult() {
        Long tagged = insertNote("带标签", NoteStatus.ONLINE, 0, 0, BASE_TIME);
        insertNote("不带标签", NoteStatus.ONLINE, 0, 0, BASE_TIME);
        Long tagId = attachTag(tagged, MARKER + "标签");

        NoteListQuery query = query();
        query.setTagId(tagId);

        assertThat(ids(pageOf(query))).containsExactly(tagged);
    }

    /** 两个筛选同时给是「与」关系，不是「或」 */
    @Test
    void filtersCombineWithAnd() {
        Long both = insertNote("两边都占", NoteStatus.ONLINE, 0, 0, BASE_TIME);
        Long onlyTag = insertNote("只有标签", NoteStatus.ONLINE, 0, 0, BASE_TIME);
        Long tagId = attachTag(both, MARKER + "标签甲");
        attachTag(onlyTag, MARKER + "标签乙");
        insertNote(insertCourse(), "只有课程", NoteStatus.ONLINE, 0, 0, BASE_TIME);

        NoteListQuery query = query();
        query.setTagId(tagId);
        // query() 已经带了 courseId，两者叠加后只剩 both
        assertThat(ids(pageOf(query))).containsExactly(both);
    }

    // ==================== 装配 ====================

    /**
     * 标签要归到各自的笔记上，不能串成一串。
     *
     * <p>这条盯的是「批量取回后按 noteId 分组」这一步——若不分组而是把所有标签平铺到每一条上，
     * 或者按行顺序错位分配，两种写法都能通过「总数对不对」这类粗断言，只有分笔记核对才发现得了。
     */
    @Test
    void tagsAreAssembledPerNote() {
        Long first = insertNote("甲", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 3, 1, 0, 0));
        Long second = insertNote("乙", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 2, 1, 0, 0));
        Long third = insertNote("丙", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 1, 1, 0, 0));
        attachTag(first, MARKER + "甲1");
        attachTag(first, MARKER + "甲2");
        attachTag(second, MARKER + "乙1");
        // third 一个标签都没有——它必须是空列表而不是 null，也不能蹭到别人的

        List<NoteListItemResponse> items = noteService.list(viewerId, query()).items();

        assertThat(items).extracting(NoteListItemResponse::title)
                .containsExactly("甲", "乙", "丙");
        assertThat(items.get(0).tags()).extracting(TagResponse::name)
                .containsExactly(MARKER + "甲1", MARKER + "甲2");
        assertThat(items.get(1).tags()).extracting(TagResponse::name)
                .containsExactly(MARKER + "乙1");
        assertThat(items.get(2).tags()).isEmpty();
    }

    @Test
    void uploaderAndCourseAreAssembled() {
        Long noteId = insertNote("标题", NoteStatus.ONLINE, 0, 0, BASE_TIME);

        NoteListItemResponse item = noteService.list(viewerId, query()).items().get(0);

        assertThat(item.id()).isEqualTo(noteId);
        assertThat(item.course().id()).isEqualTo(courseId);
        assertThat(item.course().name()).startsWith(MARKER);
        assertThat(item.uploader().id()).isEqualTo(uploaderId);
        assertThat(item.uploader().nickname()).isEqualTo(MARKER + "uploader");
        // 没设头像时是 null，不是拼出来的半截 URL
        assertThat(item.uploader().avatarUrl()).isNull();
        assertThat(item.uploader().collegeName()).startsWith(MARKER);
    }

    @Test
    void avatarUrlIsBuiltFromTheObjectKey() {
        Long withAvatar = insertUser("avatar-owner", insertCollege(), AVATAR_KEY);
        insertNote(withAvatar, courseId, "带头像", NoteStatus.ONLINE, 0, 0, BASE_TIME);

        assertThat(noteService.list(viewerId, query()).items().get(0).uploader().avatarUrl())
                .isEqualTo(AVATAR_URL);
    }

    /** 收藏态按「谁在看」算，而且是批量查的（不是逐条） */
    @Test
    void favoritedIsReportedPerViewer() {
        Long favorited = insertNote("收藏了", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 3, 1, 0, 0));
        insertNote("没收藏", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 2, 1, 0, 0));
        favorite(viewerId, favorited);

        assertThat(noteService.list(viewerId, query()).items())
                .extracting(NoteListItemResponse::isFavorited)
                .containsExactly(true, false);

        // 换个人看，两篇都是 false——收藏是「与登录者相关」的状态
        Long other = insertUser("other", insertCollege(), null);
        assertThat(noteService.list(other, query()).items())
                .extracting(NoteListItemResponse::isFavorited)
                .containsExactly(false, false);
    }

    // ==================== 分页 ====================

    @Test
    void pagesSplitTheResultAndKeepTheRealTotal() {
        Long newest = insertNote("最新", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 3, 1, 0, 0));
        Long middle = insertNote("中间", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 2, 1, 0, 0));
        Long oldest = insertNote("最旧", NoteStatus.ONLINE, 0, 0, LocalDateTime.of(2026, 1, 1, 0, 0));

        NoteListQuery firstPage = query();
        firstPage.setSize(2);
        PageResponse<NoteListItemResponse> page1 = noteService.list(viewerId, firstPage);

        assertThat(ids(page1)).containsExactly(newest, middle);
        assertThat(page1.total()).isEqualTo(3);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.size()).isEqualTo(2);

        NoteListQuery secondPage = query();
        secondPage.setSize(2);
        secondPage.setPage(2);
        PageResponse<NoteListItemResponse> page2 = noteService.list(viewerId, secondPage);

        assertThat(ids(page2)).containsExactly(oldest);
        assertThat(page2.total()).isEqualTo(3);
    }

    // ==================== 分页深度 ====================

    @Test
    void exactlyAtTheDepthLimitIsAllowed() {
        NoteListQuery query = query();
        query.setPage(50);
        query.setSize(20);   // 50 × 20 = 1000，正好在上限上

        assertThat(noteService.list(viewerId, query).items()).isEmpty();
    }

    /**
     * 超出深度上限报 40001，而不是返回一页空白。返回空白的话，前端分不清
     * 「翻得太深」和「这一页恰好没数据」，也无从把「下一页」置灰。
     */
    @Test
    void paginationBeyondTheDepthLimitIsRejected() {
        NoteListQuery query = query();
        query.setPage(51);
        query.setSize(20);   // 51 × 20 = 1020 > 1000

        assertThatThrownBy(() -> noteService.list(viewerId, query))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(e.getData())
                            .isEqualTo(List.of(new FieldViolation("page", "分页过深，最多前 1000 条")));
                });
    }

    /**
     * 极限页码不能把守卫绕过去。{@code page} 只有下界没有上界，
     * 若校验写成 {@code page * size}（int 相乘），一个接近 {@code Integer.MAX_VALUE} 的页码
     * 会溢出成负数，于是「1020 > 1000」变成「-20 > 1000」为假——守卫形同虚设。
     */
    @Test
    void hugePageNumberCannotOverflowPastTheGuard() {
        NoteListQuery query = query();
        query.setPage(Integer.MAX_VALUE);
        query.setSize(50);

        assertThatThrownBy(() -> noteService.list(viewerId, query))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    // ==================== 辅助 ====================

    /** 每次都用它取参数：固定带上本用例的 {@code courseId}，把结果圈在自己的数据里 */
    private NoteListQuery query() {
        NoteListQuery query = new NoteListQuery();
        query.setCourseId(courseId);
        return query;
    }

    private List<Long> ids(PageResponse<NoteListItemResponse> page) {
        return page.items().stream().map(NoteListItemResponse::id).toList();
    }

    private List<Long> idsWithSort(NoteListQuery.Sort sort) {
        NoteListQuery query = query();
        query.setSort(sort);
        return ids(pageOf(query));
    }

    private PageResponse<NoteListItemResponse> pageOf(NoteListQuery query) {
        return noteService.list(viewerId, query);
    }

    /** 传 null 表示这篇笔记不挂标签，返回 {@code null} 便于调用方链式忽略 */
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

    private Long insertNote(String title, NoteStatus status, long viewCount, long downloadCount,
                            LocalDateTime updatedAt) {
        return insertNote(uploaderId, courseId, title, status, viewCount, downloadCount, updatedAt);
    }

    /** 换个课程、上传者仍用默认的那个——用于「筛选只挑出本课程的笔记」这类用例 */
    private Long insertNote(Long noteCourseId, String title, NoteStatus status, long viewCount,
                            long downloadCount, LocalDateTime updatedAt) {
        return insertNote(uploaderId, noteCourseId, title, status, viewCount, downloadCount, updatedAt);
    }

    private Long insertNote(Long noteUploaderId, Long noteCourseId, String title, NoteStatus status,
                            long viewCount, long downloadCount, LocalDateTime updatedAt) {
        Note note = new Note();
        note.setUploaderId(noteUploaderId);
        note.setCourseId(noteCourseId);
        note.setTitle(title);
        note.setStatus(status.name());
        note.setViewCount(viewCount);
        note.setDownloadCount(downloadCount);
        // 直接指定：note.updated_at 没有 ON UPDATE，插入时给了什么就是什么（§3.3），
        // 于是排序用例可以精确构造顺序，不必靠 sleep 拉开时间差
        note.setUpdatedAt(updatedAt);
        noteMapper.insert(note);
        return note.getId();
    }

    private Long insertCourse() {
        Course course = new Course();
        course.setName(MARKER + "课程-" + suffix());
        course.setIsOther(0);
        courseMapper.insert(course);
        return course.getId();
    }

    private Long insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + suffix());
        collegeMapper.insert(college);
        return college.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser(String role, Long collegeId, String avatarKey) {
        String suffix = suffix();

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

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
