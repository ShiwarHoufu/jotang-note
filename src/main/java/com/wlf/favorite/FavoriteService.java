package com.wlf.favorite;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.PageResponse;
import com.wlf.common.Paging;
import com.wlf.entity.Favorite;
import com.wlf.favorite.dto.FavoriteItemResponse;
import com.wlf.favorite.dto.FavoriteListQuery;
import com.wlf.note.NoteMapper;
import com.wlf.note.NoteStatus;
import com.wlf.note.NoteTagRef;
import com.wlf.note.dto.UploaderResponse;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收藏业务：仅 ONLINE 可收藏；列表按 {@code note.status} 渲染「已下架 / 已删除」占位。
 * 见《概要设计》§5.5、§6.5。
 *
 * <p>三个入口：{@link #favorite} / {@link #unfavorite} 是写入侧，
 * {@link #listFavorites} 是「我的收藏」；另有两个给别的模块用的只读方法
 * （{@link #isFavorited} / {@link #favoritedNoteIds}），供详情页与笔记列表填 {@code isFavorited}。
 *
 * <p><b>本类直接注入 {@link NoteMapper}</b>，这是 §1.1 明确允许的一类写入：
 * 跨模块写对方模块拥有的单表列，且对方的 Service 承接不了这个写
 * （{@code NoteService} 已经注入本类，反过来注入它即是构造器循环依赖）。
 * 既存先例是 {@code AuthService} 直接注入 {@code UserMapper}。详见
 * {@code NoteMapper#incrementFavoriteCount} 与 §1.1 的「跨模块写对方单表列的条件」。
 *
 * <p>注意这与 {@link #isFavorited} 的性质不同：那边是纯读，走 {@code favorite} 表自己的谓词，
 * 不碰 {@code note} 表；而 {@link #listFavorites} 的 {@code favorite ⋈ note} 是 §1.1 里
 * 「检索侧的多表 JOIN 不受此限」的又一例，与 {@code NoteMapper.xml} 连 {@code course} 同理。
 */
@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    /**
     * 只为维护 {@code note.favorite_count} 与取那行锁而存在。
     *
     * <p><b>不注入 {@code NoteService}</b>：{@code NoteService} 已经注入本类（详情页要填
     * {@code isFavorited}），反向注入即是构造器循环依赖，应用连启动都起不来。
     * {@code NoteService} 的类注释里也留了同样的警告。
     */
    private final NoteMapper noteMapper;

    /**
     * 收藏列表要展示上传者，头像得拼成公网直连 URL（§6.7）。
     * 与 {@code NoteService} 注入的是同一个接口，头像是公共读 Bucket，不需要签名。
     */
    private final StorageService storageService;

    public FavoriteService(FavoriteMapper favoriteMapper,
                           NoteMapper noteMapper,
                           StorageService storageService) {
        this.favoriteMapper = favoriteMapper;
        this.noteMapper = noteMapper;
        this.storageService = storageService;
    }

    /**
     * 收藏一篇笔记，返回操作后的最新收藏量。见 §5.5、§6.5。
     *
     * <p><b>只允许收藏 {@code ONLINE} 的笔记</b>（§6.5）：已下架 / 已删除的笔记拒绝收藏，
     * 40301。这与 {@link #isFavorited} 刻意相反——那边如实回答「关系在不在」，
     * 这边回答「现在能不能收藏」。两条规则面向的问题不同，不要合并。
     *
     * <p><b>四步的顺序都是有意的，尤其是第一步：</b>
     * <ol>
     *   <li>{@link NoteMapper#selectStatusForUpdate} 一次拿到「存在性 + 状态 + X 锁」。
     *       先取锁是<b>死锁防线</b>：InnoDB 检查 {@code fk_fav_note} 时会对 note 行取隐式共享锁，
     *       若先插 favorite 再更新 note，两个用户同时收藏同一篇笔记就会各持 S(note) 互等 X(note)。
     *       取消收藏走同一条也先取锁，全站锁序统一为 note → favorite。</li>
     *   <li>{@code INSERT favorite}。这步还顺带挡住「给不存在的 note_id 插收藏」——
     *       那会撞 {@code fk_fav_note} 抛 {@code DataIntegrityViolationException}（<b>不是</b>
     *       {@code DuplicateKeyException}），一路掉进兜底变成 50000，把「笔记不存在」伪装成
     *       「服务器炸了」。第 1 步已经把这种 id 判成 40400 了。</li>
     *   <li>自增计数。因为第 1 步已持 X 锁，状态在提交前不会变，这里不需要再带 status 守卫。</li>
     *   <li>回读最新值返回。持有该行的锁，读到的必然是自己这次操作之后的值。</li>
     * </ol>
     *
     * @param userId 收藏者，取自 JWT——不接受请求参数传入，否则可以替别人收藏
     * @throws BusinessException 40400 笔记不存在；40301 笔记非 ONLINE；40902 已收藏过
     */
    @Transactional
    public long favorite(Long userId, Long noteId) {
        String status = noteMapper.selectStatusForUpdate(noteId);
        if (status == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!NoteStatus.ONLINE.name().equals(status)) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setNoteId(noteId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 去重由 uk_user_note 兜底，不做「先查再插」——那套在并发下会漏，
            // 两个请求可以同时查到「没收藏过」（§3.3）。异常继续外抛而不是吞掉，
            // 事务照常回滚；此时第 3 步还没执行，没有任何东西要撤销。
            throw new BusinessException(ErrorCode.ALREADY_FAVORITED);
        }

        noteMapper.incrementFavoriteCount(noteId);
        // 笔记行被上面的 FOR UPDATE 锁着且确实存在，这条读不可能为 null
        return noteMapper.selectFavoriteCount(noteId);
    }

    /**
     * 取消收藏，返回操作后的最新收藏量。见 §5.5、§6.5。
     *
     * <p><b>幂等</b>：本来就没收藏过也返回 200，而不是 40400。DELETE 本就是这个语义，
     * 前端不必「先查再删」；连点两次不该有一次报错。
     *
     * <p><b>笔记非 ONLINE 也放行</b>（与 {@link #favorite} 不对称，这是有意的）：
     * §5.7 的 40301 管的是「收藏」这个动作，而占位项如果连取消都不允许，
     * 用户就永远清不掉自己收藏列表里的「已下架 / 已删除」，只能越积越多。
     * 这与 §6.5「不解除收藏关系」也不矛盾：那条说的是<b>系统不自动断链</b>，
     * 这里说的是<b>用户主动清理</b>可以。
     *
     * <p>第一步取锁而不取状态：本方法不关心 {@code ONLINE}，但要与
     * {@link #favorite} 走同一把锁的同一获取顺序（note → favorite），否则收藏与取消收藏
     * 并发作用于同一 {@code (user, note)} 时锁序相反，一样会死锁。
     *
     * @param userId 取消者，取自 JWT
     * @throws BusinessException 40400 笔记不存在。注意「存在但没收藏过」是 200，两者要分清
     */
    @Transactional
    public long unfavorite(Long userId, Long noteId) {
        // 返回值是状态，这里用不上——要的是这把排他锁与「行存在吗」这一个事实
        if (noteMapper.selectStatusForUpdate(noteId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        int deleted = favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getNoteId, noteId));

        if (deleted > 0) {
            // 只有真删掉了关系才减计数。无条件减会让「连点两次取消」把计数多减一次
            noteMapper.decrementFavoriteCount(noteId);
        }

        return noteMapper.selectFavoriteCount(noteId);
    }

    /**
     * 我的收藏，按收藏时间倒序分页。见 §5.5、§6.5、§4.1。
     *
     * <p><b>只查自己的收藏</b>：{@code userId} 取自 JWT，绝不从请求参数传入——
     * 这个接口没有「看别人收藏」的语义，把 id 交出去就等于开放了它。
     * 也正因为过滤条件落在 {@code favorite.user_id} 上，结果集天然只有「我收藏的那些」，
     * 不需要像笔记列表那样另造一个课程把开发库里的既有数据圈出去。
     *
     * <p><b>三种状态都渲染</b>（§6.5）：{@code ONLINE} 正常项，
     * {@code OFFLINE} / {@code DELETED} 是占位项——保留标题与课程，其余置空。
     * 见 {@link #toFavoriteItem}。
     *
     * <p><b>本方法刻意不开事务。</b>纯读，没有需要原子化的写；两条查询之间即便插进一次
     * 「笔记刚被下架」，最坏结果也只是标题用得上而计数没了，而这两者本来就允许短暂不一致
     * （§3.3 的计数是派生值）。与 {@code NoteService.detail} 同一个态度：
     * 为一次只读的装配开事务，只会白白多占一个连接。
     *
     * <p><b>固定三次数据库交互</b>，与页大小无关：主干分页 + 插件改写的 count + 一次标签
     * {@code IN} 查询。标签不逐条查——那会立刻退化成 N+1（一页 20 条就是 20 次往返）。
     *
     * @param userId 当前登录者，取自 JWT
     * @throws BusinessException 40001 分页过深（{@code page × size > 1000}，见 {@link Paging#MAX_PAGE_DEPTH}）
     */
    public PageResponse<FavoriteItemResponse> listFavorites(Long userId, FavoriteListQuery query) {
        Paging.requireShallow(query.getPage(), query.getSize());

        // Page 只携带「第几页、每页几条」，count 语句由分页插件改写生成，不在这里手写
        IPage<FavoriteItemRow> result = favoriteMapper.selectFavoritePage(
                new Page<>(query.getPage(), query.getSize()), userId);

        // total 取插件回填的真实总数；page/size 回显入参而不是 result.getCurrent()，
        // 免得前端收到一个与它请求时不一致的页码（插件内部对越界页有它自己的处理）
        return new PageResponse<>(
                assembleFavorites(result.getRecords()),
                result.getTotal(),
                query.getPage(),
                query.getSize());
    }

    /**
     * 把一页的行装配成出参：一次批量查询取回整页的标签，再在内存里按 {@code noteId} 归位。
     *
     * <p>空页要在这里短路——{@code IN ()} 是语法错误，那条批量查询吃不下空集合。
     *
     * <p>标签查询复用 {@link NoteMapper#selectTagRefsByNoteIds} 而不是在 {@code FavoriteMapper.xml}
     * 里另抄一条：那条 SQL（{@code note_tag ⋈ tag}）与列表页用的是同一条，抄第二份只会让两处
     * 将来各自漂移。本类已经因为 {@code favorite_count} 直接依赖 {@code note} 模块了
     * （见类注释），再多引一个它的只读投影不是新的妥协类别。
     *
     * <p><b>对「本页全部 noteId」一次取，不按 ONLINE 过滤后再取</b>：
     * 后者会引入一个窗口——笔记正好在主查询与标签查询之间被下架，主行说 ONLINE 而标签是空的。
     * 取回来的标签只装配到正常项上，占位项那部分丢掉，代价只是一次
     * {@code IN} 多带几个 id，与页大小无关。
     */
    private List<FavoriteItemResponse> assembleFavorites(List<FavoriteItemRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = rows.stream().map(FavoriteItemRow::getNoteId).toList();

        // groupingBy 的下游是 toList（ArrayList），组内保持查询的 encounter order，
        // 于是每篇笔记的标签仍是 tag.id 升序——与详情页、列表页那条查询的口径一致
        Map<Long, List<TagResponse>> tagsByNote = noteMapper.selectTagRefsByNoteIds(noteIds).stream()
                .collect(Collectors.groupingBy(NoteTagRef::getNoteId,
                        Collectors.mapping(ref -> new TagResponse(ref.getTagId(), ref.getTagName()),
                                Collectors.toList())));

        return rows.stream()
                .map(row -> toFavoriteItem(row, tagsByNote))
                .toList();
    }

    /**
     * 一行 → 一项。正常项与占位项的分叉只有这一处，
     * 所以「占位项到底空掉了哪些字段」是一个能一眼读完的事实，不会散在装配代码里。
     *
     * <p>占位项保留标题与课程、隐去其余，理由见 {@link FavoriteItemResponse}：
     * 用户要认得出自己收藏的是什么，但已下架笔记的上传者、标签与计数不该再从这条路径露出去。
     *
     * <p>{@code OFFLINE → ONLINE} 恢复之后不需要任何额外处理就会重新走正常项那一支——
     * 这正是 §6.5 说的「恢复后自动正常展示，计数不变」，也说明占位不是一种被写进去的状态，
     * 而是每次读取时对当前 {@code note.status} 的即时判断。
     */
    private FavoriteItemResponse toFavoriteItem(FavoriteItemRow row, Map<Long, List<TagResponse>> tagsByNote) {
        NoteStatus status = NoteStatus.valueOf(row.getStatus());
        CourseResponse course = new CourseResponse(row.getCourseId(), row.getCourseName());

        if (status != NoteStatus.ONLINE) {
            return new FavoriteItemResponse(row.getNoteId(), status, row.getFavoritedAt(),
                    row.getTitle(), course,
                    null, List.of(), null, null, null, null);
        }

        return new FavoriteItemResponse(row.getNoteId(), status, row.getFavoritedAt(),
                row.getTitle(), course,
                new UploaderResponse(row.getUploaderId(), row.getNickname(),
                        avatarUrl(row.getAvatar()), row.getCollegeName()),
                // 没有标签时给空列表：getOrDefault 的默认值必须是不可变的 List.of()，
                // 而不是 null——前端可以无条件遍历
                tagsByNote.getOrDefault(row.getNoteId(), List.of()),
                row.getViewCount(), row.getDownloadCount(), row.getFavoriteCount(),
                row.getUpdatedAt());
    }

    /**
     * 头像对象键 → 公网直连 URL（§6.7）。
     *
     * <p>空值必须在这里短路：{@code StorageService#publicUrl} 是裸字符串拼接，
     * 喂个 null 会拼出一个以 {@code /null} 结尾、看着像 URL 的东西——
     * 前端会安心地拿它去 {@code <img src>}，然后拿到一个 404。
     *
     * <p>与 {@code NoteService} 里那三行是同一段逻辑。这里先复制而不是抽公共助手：
     * 抽取要动 note 模块（已在用的那处），而本切片的目标是把收藏做完；
     * 「个人主页」落地时会出现第三份，那时才是抽取的时机——三处会逼出一个正确的形状，
     * 两处则容易抽出只够这两处用的东西。
     */
    private String avatarUrl(String avatarKey) {
        return avatarKey == null ? null : storageService.publicUrl(Bucket.AVATAR, avatarKey);
    }

    /**
     * 某个用户是否收藏了某篇笔记。详情页用它填 {@code isFavorited}（§5.4）。
     *
     * <p><b>只看 favorite 表，不看笔记状态。</b>§6.5 明确「占位项不可预览/下载，
     * 但不解除收藏关系」，所以已下架 / 已删除的笔记只要关系还在就返回 true。
     * 这个字段回答的是「这条关系存在吗」，而不是「现在还能收藏吗」——
     * 后者由 {@code note.status} 单独回答，前端据此隐藏按钮（§4.1）。
     * 若在这里顺手改成「非 ONLINE 一律 false」，就等于让数据去配合渲染，
     * 而关系实际还在，等笔记恢复上架时这个字段又会自己变回 true。
     *
     * <p>实现上委托给 {@link #favoritedNoteIds}，而不是另写一条 {@code exists}：
     * 两者回答的是同一个问题，只是批量与单条两种用法。写两遍谓词就意味着将来两处可能不一致，
     * 而调用方一个看列表一个看详情，不一致时表现为「同一篇笔记收藏态对不上」，很难查。
     *
     * @param userId 当前登录者，取自 JWT
     * @param noteId 任意笔记 id，<b>允许是不存在的 id</b>——「笔记不存在」与
     *               「笔记存在但没收藏」对调用方的含义相同，都是 false。
     *               笔记存在与否由详情接口自己判定（它要为此报 40400），
     *               本方法只回答收藏关系这一个问题，不越界去判另一件事
     * @return null 参数视为未收藏：调用方是「JWT 里的 userId」与「路径上的 noteId」，
     *         两者都不该为 null，真出现了也不值得让请求失败，返回 false 即可
     */
    public boolean isFavorited(Long userId, Long noteId) {
        return noteId != null && favoritedNoteIds(userId, List.of(noteId)).contains(noteId);
    }

    /**
     * 一批笔记里，该用户收藏了哪些。列表页用它填 {@code isFavorited}（§5.4）。
     *
     * <p><b>批量而不是逐条</b>：一页 20 条就会变成 20 次往返（N+1）。这里一次
     * {@code IN} 查询取回整页的收藏关系，条数与页大小无关。查询走 {@code uk_user_note}
     * 唯一索引，命中行数至多等于入参个数。
     *
     * <p>返回 {@code Set} 而不是 {@code List}：调用方要的是「在不在集合里」，
     * 不是顺序，也不是条数。用 Set 把这个意图写进返回值，顺带避免调用方写出
     * {@code list.contains(...)} 这种 O(n) 的误用。
     *
     * @param userId  当前登录者，取自 JWT
     * @param noteIds 待判定的笔记 id。空集合直接短路——{@code IN ()} 是语法错误，
     *                不能让它流到 SQL
     * @return 入参中已收藏的 id；未收藏的不出现。无收藏或无有效入参时返回空集合
     */
    public Set<Long> favoritedNoteIds(Long userId, Collection<Long> noteIds) {
        if (userId == null || noteIds.isEmpty()) {
            return Set.of();
        }
        return favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                        .select(Favorite::getNoteId)
                        .eq(Favorite::getUserId, userId)
                        .in(Favorite::getNoteId, noteIds))
                .stream()
                .map(Favorite::getNoteId)
                .collect(Collectors.toSet());
    }
}
