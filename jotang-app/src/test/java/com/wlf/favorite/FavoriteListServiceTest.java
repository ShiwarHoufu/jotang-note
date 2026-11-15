package com.wlf.favorite;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.catalog.TagMapper;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.common.PageResponse;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Favorite;
import com.wlf.entity.Note;
import com.wlf.entity.Tag;
import com.wlf.entity.User;
import com.wlf.favorite.dto.FavoriteItemResponse;
import com.wlf.favorite.dto.FavoriteListQuery;
import com.wlf.note.NoteMapper;
import com.wlf.note.NoteStatus;
import com.wlf.note.NoteTagMapper;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link FavoriteService#listFavorites} 的用例。见《概要设计》§5.5、§6.5、§4.1。
 *
 * <p><b>结果集天然是可数的</b>，不需要像 {@code NoteListServiceTest} 那样另造一个课程把
 * 开发库里既有的数据圈出去：这条查询的过滤条件是 {@code favorite.user_id = ?}，
 * 而每个用例都用一个全新的读者，他的收藏夹里只会有本用例插进去的那几行。
 *
 * <p>重点在三条<b>写错了不会立刻暴露</b>的规则：
 * ① 占位项该保留什么、该空掉什么（§6.5）；② 恢复上架后自动回到正常项，不需要任何额外处理；
 * ③ 排序键是收藏时间、并列时靠 {@code favorite.id} 兜底。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class FavoriteListServiceTest {

    // 保持短：user.username 只有 VARCHAR(32)，MARKER + role + '-' + 8 位随机后缀必须放得下
    private static final String MARKER = "ZZTFL-";
    private static final String AVATAR_KEY = "avatars/7/abc.png";
    private static final String AVATAR_URL = "https://jotang-avatar.oss-cn-chengdu.aliyuncs.com/" + AVATAR_KEY;

    /** 收藏时间的基准。{@code favorite.created_at} 是秒精度的 DATETIME，用例全部显式指定 */
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteTagMapper noteTagMapper;

    @Autowired
    private TagMapper tagMapper;

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
    private Long readerId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();
        Long collegeId = insertCollege();
        uploaderId = insertUser("uploader", collegeId, AVATAR_KEY);
        readerId = insertUser("reader", collegeId, null);

        when(storageService.publicUrl(Bucket.AVATAR, AVATAR_KEY)).thenReturn(AVATAR_URL);
    }

    // ==================== 正常项 ====================

    /** 正常项要把卡片上要显示的东西一次给全，且 avatarUrl 是拼好的公网地址而不是对象键 */
    @Test
    void anOnlineItemCarriesTheWholeCard() {
        Note note = insertNote("期中复习提纲", NoteStatus.ONLINE);
        note.setViewCount(12L);
        note.setDownloadCount(3L);
        note.setFavoriteCount(5L);
        note.setUpdatedAt(LocalDateTime.of(2026, 4, 1, 8, 0));
        noteMapper.updateById(note);
        favor(note.getId(), BASE_TIME);
        tag(note.getId(), "高数");

        FavoriteItemResponse item = items().get(0);

        assertThat(item.noteId()).isEqualTo(note.getId());
        assertThat(item.status()).isEqualTo(NoteStatus.ONLINE);
        assertThat(item.favoritedAt()).isEqualTo(BASE_TIME);
        assertThat(item.title()).isEqualTo("期中复习提纲");
        assertThat(item.course().id()).isEqualTo(courseId);
        assertThat(item.uploader().id()).isEqualTo(uploaderId);
        assertThat(item.uploader().avatarUrl()).isEqualTo(AVATAR_URL);
        assertThat(item.tags()).extracting(TagResponse::name).containsExactly(MARKER + "高数");
        assertThat(item.viewCount()).isEqualTo(12L);
        assertThat(item.downloadCount()).isEqualTo(3L);
        assertThat(item.favoriteCount()).isEqualTo(5L);
        assertThat(item.updatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 1, 8, 0));
    }

    /**
     * 没设过头像时 {@code avatarUrl} 是 null，而不是以 {@code /null} 结尾的假 URL。
     * 这条挡的是拼接处那个漏掉的空值短路——前端会安心地拿它去 {@code <img src>}。
     */
    @Test
    void anUploaderWithoutAvatarYieldsNullRatherThanAMalformedUrl() {
        Note note = insertNote("作者没头像", NoteStatus.ONLINE);
        note.setUploaderId(insertUser("avatarless", insertCollege(), null));
        noteMapper.updateById(note);
        favor(note.getId(), BASE_TIME);

        assertThat(items().get(0).uploader().avatarUrl()).isNull();
    }

    /**
     * 标签必须归到自己那篇笔记上，不能串到一起。
     * 装配用的是 {@code groupingBy(noteId)}，写错成「整页的标签都挂到第一行」也不会报错。
     */
    @Test
    void tagsAreAttachedToTheirOwnNote() {
        Note withTag = insertNote("有标签", NoteStatus.ONLINE);
        favor(withTag.getId(), BASE_TIME);
        tag(withTag.getId(), "高数");

        Note withoutTag = insertNote("没标签", NoteStatus.ONLINE);
        favor(withoutTag.getId(), BASE_TIME.plusDays(1));

        Map<Long, List<String>> tagsByNote = items().stream()
                .collect(Collectors.toMap(FavoriteItemResponse::noteId,
                        item -> item.tags().stream().map(TagResponse::name).toList()));

        assertThat(tagsByNote.get(withTag.getId())).containsExactly(MARKER + "高数");
        // 没有标签时是空列表而不是 null——前端可以无条件遍历
        assertThat(tagsByNote.get(withoutTag.getId())).isEmpty();
    }

    // ==================== 占位项（§6.5）====================

    /**
     * 占位项该保留什么、该空掉什么，一条用例全钉住。
     *
     * <p>保留标题与课程：用户要认得出自己当初收藏的是什么。
     * 空掉上传者、标签、三个计数与 {@code updatedAt}：已下架笔记的这些内容不该再从这条路径露出去。
     * <p>注意 {@code favoritedAt} <b>照常返回</b>——它是「我什么时候收藏的」，属于用户自己的关系，
     * 不是笔记的内容，不随笔记下架而消失。
     */
    @Test
    void anOfflineItemKeepsItsIdentityButDropsItsContent() {
        Note note = insertNote("已下架的笔记", NoteStatus.OFFLINE);
        note.setViewCount(12L);
        note.setDownloadCount(3L);
        note.setFavoriteCount(5L);
        note.setUpdatedAt(LocalDateTime.of(2026, 4, 1, 8, 0));
        noteMapper.updateById(note);
        favor(note.getId(), BASE_TIME);
        tag(note.getId(), "高数");

        FavoriteItemResponse item = items().get(0);

        assertThat(item.status()).isEqualTo(NoteStatus.OFFLINE);
        assertThat(item.favoritedAt()).isEqualTo(BASE_TIME);
        assertThat(item.title()).isEqualTo("已下架的笔记");
        assertThat(item.course().id()).isEqualTo(courseId);

        assertThat(item.uploader()).isNull();
        assertThat(item.tags()).isEmpty();
        assertThat(item.viewCount()).isNull();
        assertThat(item.downloadCount()).isNull();
        assertThat(item.favoriteCount()).isNull();
        assertThat(item.updatedAt()).isNull();
    }

    /** 软删除同理：行还在、关系还在，渲染成「已删除」占位（D4） */
    @Test
    void aDeletedItemIsAlsoAPlaceholder() {
        Note note = insertNote("已删除的笔记", NoteStatus.DELETED);
        favor(note.getId(), BASE_TIME);

        FavoriteItemResponse item = items().get(0);

        assertThat(item.status()).isEqualTo(NoteStatus.DELETED);
        assertThat(item.title()).isEqualTo("已删除的笔记");
        assertThat(item.uploader()).isNull();
    }

    /**
     * §6.5：「{@code OFFLINE→ONLINE} 恢复后自动正常展示，计数不变」。
     *
     * <p>这条说明占位<b>不是一种被写进去的状态</b>，而是每次读取时对当前 {@code note.status}
     * 的即时判断。若实现里给占位另存了什么标记（或缓存了判定结果），恢复上架后就不会自己回来。
     */
    @Test
    void aRestoredNoteIsRenderedNormallyAgain() {
        Note note = insertNote("先下架再恢复", NoteStatus.ONLINE);
        favor(note.getId(), BASE_TIME);

        assertThat(items().get(0).uploader()).isNotNull();

        setStatus(note.getId(), NoteStatus.OFFLINE);
        assertThat(items().get(0).uploader()).isNull();

        setStatus(note.getId(), NoteStatus.ONLINE);
        assertThat(items().get(0).uploader()).isNotNull();
        assertThat(items().get(0).status()).isEqualTo(NoteStatus.ONLINE);
    }

    // ==================== 范围与排序 ====================

    /** 只看自己的：过滤条件在 {@code favorite.user_id} 上，别人的收藏一行都不该出现 */
    @Test
    void onlyTheReadersOwnFavoritesAreListed() {
        Long mine = insertNote("我收藏的", NoteStatus.ONLINE).getId();
        Long theirs = insertNote("别人收藏的", NoteStatus.ONLINE).getId();
        favor(mine, BASE_TIME);

        Favorite favorite = new Favorite();
        favorite.setUserId(insertUser("someone-else", insertCollege(), null));
        favorite.setNoteId(theirs);
        favoriteMapper.insert(favorite);

        assertThat(ids()).containsExactly(mine);
    }

    @Test
    void itemsAreOrderedByFavoritedAtDescending() {
        Long oldest = insertNote("最先收藏", NoteStatus.ONLINE).getId();
        Long middle = insertNote("中间收藏", NoteStatus.ONLINE).getId();
        Long newest = insertNote("最后收藏", NoteStatus.ONLINE).getId();
        favor(oldest, BASE_TIME);
        favor(middle, BASE_TIME.plusDays(1));
        favor(newest, BASE_TIME.plusDays(2));

        assertThat(ids()).containsExactly(newest, middle, oldest);
    }

    /**
     * 同一秒内收藏的几篇要靠次级键定序。{@code created_at} 是秒精度的 DATETIME，
     * 连点几下收藏就会全部落在同一秒——没有确定的次级键时，翻页会看到重复或漏掉的项。
     */
    @Test
    void favoritesInTheSameSecondFallBackToTheNewestRowFirst() {
        Long first = insertNote("同秒第一", NoteStatus.ONLINE).getId();
        Long second = insertNote("同秒第二", NoteStatus.ONLINE).getId();
        favor(first, BASE_TIME);
        favor(second, BASE_TIME);

        assertThat(ids()).containsExactly(second, first);
    }

    @Test
    void anEmptyCollectionYieldsAnEmptyPageRatherThanNull() {
        PageResponse<FavoriteItemResponse> page = favoriteService.listFavorites(readerId, query());

        // 标签批量查询吃不下空集合（IN () 是语法错误），装配那一步必须短路
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page()).isEqualTo(1);
    }

    @Test
    void aPageCarriesTheRealTotalAndSplitsItemsAcrossPages() {
        for (int i = 0; i < 3; i++) {
            favor(insertNote("第 " + i + " 篇", NoteStatus.ONLINE).getId(), BASE_TIME.plusDays(i));
        }

        FavoriteListQuery first = query();
        first.setSize(2);
        PageResponse<FavoriteItemResponse> firstPage = favoriteService.listFavorites(readerId, first);

        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.total()).isEqualTo(3);

        FavoriteListQuery second = query();
        second.setSize(2);
        second.setPage(2);
        PageResponse<FavoriteItemResponse> secondPage = favoriteService.listFavorites(readerId, second);

        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.total()).isEqualTo(3);
    }

    // ==================== 分页守卫 ====================

    /**
     * 分页深度上限对「我的收藏」同样生效（§3.3：「对所有列表 / 搜索统一生效」）。
     * 报错而不是截断：返回一页空白但 {@code total} 还写着很大的数，用户分不清是翻得太深还是这页没数据。
     */
    @Test
    void deepPaginationIsRejectedWithAFieldDetail() {
        FavoriteListQuery query = query();
        query.setPage(51);
        query.setSize(20);

        assertThatThrownBy(() -> favoriteService.listFavorites(readerId, query))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(e.getData())
                            .isEqualTo(List.of(new FieldViolation("page", "分页过深，最多前 1000 条")));
                });
    }

    /** 极限页码不能把守卫绕过去：{@code int} 相乘溢出成负数会让「1020 > 1000」变成假 */
    @Test
    void aHugePageNumberCannotOverflowPastTheGuard() {
        FavoriteListQuery query = query();
        query.setPage(Integer.MAX_VALUE);

        assertThatThrownBy(() -> favoriteService.listFavorites(readerId, query))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== 夹具 ====================

    private List<FavoriteItemResponse> items() {
        return favoriteService.listFavorites(readerId, query()).items();
    }

    private List<Long> ids() {
        return items().stream().map(FavoriteItemResponse::noteId).toList();
    }

    /** 每页 50 条（上界），免得用例里插的几篇被默认的 20 条截掉 */
    private FavoriteListQuery query() {
        FavoriteListQuery query = new FavoriteListQuery();
        query.setSize(50);
        return query;
    }

    private Note insertNote(String title, NoteStatus status) {
        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(title);
        note.setStatus(status.name());
        noteMapper.insert(note);
        return note;
    }

    /**
     * 把某篇笔记收进读者的收藏夹，并<b>显式钉死收藏时间</b>。
     *
     * <p>不靠建表语句的 {@code DEFAULT CURRENT_TIMESTAMP}：那一列是秒精度，
     * 同一用例里连着收藏几篇会全部落在同一秒，排序断言就成了碰运气
     * （真并列时才轮到次级键 {@code favorite.id} 生效，那是另一条用例要单独验的事）。
     */
    private void favor(Long noteId, LocalDateTime favoritedAt) {
        Favorite favorite = new Favorite();
        favorite.setUserId(readerId);
        favorite.setNoteId(noteId);
        favorite.setCreatedAt(favoritedAt);
        favoriteMapper.insert(favorite);
    }

    /** 直接改库而不是走状态机：本用例要的是「表里已经是那个状态」，与迁移是否合法无关 */
    private void setStatus(Long noteId, NoteStatus status) {
        noteMapper.update(null, Wrappers.<Note>lambdaUpdate()
                .eq(Note::getId, noteId)
                .set(Note::getStatus, status.name()));
    }

    /**
     * 给笔记打一个标签。名字加 {@code MARKER} 是为了不与环境里既有的标签撞
     * {@code tag.uk_name}；同一用例内不会再建同名标签，用例之间又各自回滚，所以不需要再加随机后缀
     * ——加了反而让断言只能写前缀匹配。
     */
    private void tag(Long noteId, String name) {
        Tag tag = new Tag();
        tag.setName(MARKER + name);
        tagMapper.insert(tag);
        noteTagMapper.insertBatch(noteId, List.of(tag.getId()));
    }

    private Long insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college.getId();
    }

    /**
     * 造用户。{@code username} / {@code email} 上有唯一键，同一个测试里可能造多个，
     * 故拼一段随机后缀；{@code user.college_id} 非空，所以调用方要先备好学院。
     */
    private Long insertUser(String role, Long collegeId, String avatarKey) {
        // 后缀取 8 位而不是整串 UUID：user.username 列宽只有 32，超长在严格模式下直接报错
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
