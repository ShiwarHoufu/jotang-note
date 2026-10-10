package com.wlf.note;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wlf.catalog.CourseService;
import com.wlf.catalog.TagService;
import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.common.PageResponse;
import com.wlf.common.Paging;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.favorite.FavoriteService;
import com.wlf.note.dto.MyNoteItemResponse;
import com.wlf.note.dto.MyNoteListQuery;
import com.wlf.note.dto.NoteCreatedResponse;
import com.wlf.note.dto.NoteDetailResponse;
import com.wlf.note.dto.NoteListItemResponse;
import com.wlf.note.dto.NoteListQuery;
import com.wlf.note.dto.NoteUpdateRequest;
import com.wlf.note.dto.NoteUploadRequest;
import com.wlf.note.dto.UploaderResponse;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 笔记核心业务。见《概要设计》§6.1（上传）、§6.2（预览与下载）、§5.4（详情 / 编辑 / 删除）。
 *
 * <p>当前已落地上传、详情、列表、我的上传、编辑、删除；搜索 / 预览 / 下载待后续切片。
 */
@Slf4j
@Service
public class NoteService {

    /** 详情缓存的 key 前缀。带 {@code note:} 归属前缀，便于在 redis-cli 里辨认和将来成片清理 */
    private static final String DETAIL_CACHE_PREFIX = "note:meta:";

    /**
     * 详情缓存的存活时间。
     *
     * <p>这个数字同时是「写路径不失效缓存」这一取舍的<b>代价上限</b>：按约定，本版不在
     * 编辑 / 删除 / 下架 / 恢复里删缓存，所以那些写操作之后，详情页最多脏这么久。
     * 它是用户可见的（管理员下架后前端仍在显示「在线」），不是理论上的边界情况——
     * 将来若把这个窗口收到不可感知的范围，要么改小 TTL，要么给写路径补失效。
     */
    private static final Duration DETAIL_CACHE_TTL = Duration.ofMinutes(10);

    private final NoteMapper noteMapper;
    private final NoteFileMapper noteFileMapper;
    private final NoteTagMapper noteTagMapper;
    private final CourseService courseService;
    private final TagService tagService;
    /**
     * 详情页要填「是否已收藏」，这是 note 模块第一次反向依赖 favorite 模块。
     *
     * <p><b>这个方向不能反过来。</b>{@code FavoriteService} 判笔记状态时走它自己的 SQL
     * （§6.5 的收藏列表本来就是 {@code favorite JOIN note}），不调本类；哪天有人图省事
     * 让它注入 {@code NoteService} 来「复用一下状态判断」，两边就成了构造器循环依赖，
     * 应用连启动都起不来。
     */
    private final FavoriteService favoriteService;
    private final NoteFilePolicy filePolicy;
    private final StorageService storageService;
    /** 状态迁移的合法性判据。见 {@code NoteStateMachine}（删除与管理员的下架/恢复共用） */
    private final NoteStateMachine stateMachine;
    private final TransactionTemplate transactionTemplate;

    /**
     * 详情页的读缓存（《技术选型》§5 的 V2 缓存条目）。<b>目前只有 {@link #detail} 用它。</b>
     *
     * <p>键 {@code note:meta:{noteId}}，值是 {@link NoteMeta} 的 JSON——序列化规则见
     * {@code RedisConfig}。读写与降级都在 {@link #loadMeta} 里。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    public NoteService(NoteMapper noteMapper,
                       NoteFileMapper noteFileMapper,
                       NoteTagMapper noteTagMapper,
                       CourseService courseService,
                       TagService tagService,
                       FavoriteService favoriteService,
                       NoteFilePolicy filePolicy,
                       StorageService storageService,
                       NoteStateMachine stateMachine,
                       RedisTemplate<String, Object> redisTemplate,
                       PlatformTransactionManager transactionManager) {
        this.noteMapper = noteMapper;
        this.noteFileMapper = noteFileMapper;
        this.noteTagMapper = noteTagMapper;
        this.courseService = courseService;
        this.tagService = tagService;
        this.favoriteService = favoriteService;
        this.filePolicy = filePolicy;
        this.storageService = storageService;
        this.stateMachine = stateMachine;
        this.redisTemplate = redisTemplate;
        // 自己 new 而不依赖 Spring Boot 的自动配置：本类要的是「一段明确可控的事务边界」，
        // 而不是一个可被替换的托管 Bean，显式持有反而少一层「bean 从哪来」的疑问
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 上传笔记：校验 → 解析标签 → 流式传 OSS → 事务落库。见 §6.1。
     *
     * <p><b>三步的先后顺序都是有意的</b>：
     * <ol>
     *   <li>课程与文件校验放在最前，不合格就不碰 OSS——对象存下来再发现参数错，
     *       只能靠补偿删除收场</li>
     *   <li>标签解析放在 OSS 之前：它会往 {@code tag} 表写新标签，但如果标签集合本身超标，
     *       异常会在这里抛出，此时还没有对象产生</li>
     *   <li>OSS 上传放在事务<b>之外</b>：单文件可达 100MB，把一次上百 MB 的网络传输
     *       圈进数据库事务，会让一个连接被占住整个上传时长。代价是「对象已存、入库失败」
     *       这个窗口真实存在，由下面的补偿删除兜住；补偿失败也只是留下孤儿对象，
     *       由每日维护脚本按 {@code is_purged} 对账，不影响用户请求的成败</li>
     * </ol>
     *
     * @param uploaderId 上传者，取自 JWT，不接受请求体传入——否则可以冒名顶替他人上传
     */
    public NoteCreatedResponse upload(Long uploaderId, NoteUploadRequest request) {
        MultipartFile file = request.getFile();
        requireCourse(request.getCourseId());
        List<Long> tagIds = tagService.resolveIds(request.getTags());

        FileDecision decision = inspect(file, uploaderId);
        StoredObject stored = store(file, decision, uploaderId);

        try {
            return transactionTemplate.execute(
                    status -> insertNote(uploaderId, request, decision, stored, tagIds)
            );
        } catch (RuntimeException e) {
            purgeQuietly(stored.key());
            throw e;
        }
    }

    /**
     * 详情：读取元数据与标签、判断是否已收藏，并在 ONLINE 时把浏览量 +1。
     *
     * <p><b>本方法刻意不开事务。</b>里面的写语句只有浏览量自增一条，而把它圈进事务的后果是：
     * {@code note} 那一行的排他锁会一直持有到方法返回——这中间还夹着标签查询、收藏查询与响应体装配。
     * 详情页是最热的读接口，为一个统计计数把行锁按住整个请求时长，代价远大于收益；
     * 不圈事务则 UPDATE 立即提交、锁立即释放。代价是「自增」与「读取」之间可能插进别人的自增，
     * 于是返回的 {@code viewCount} 可能略大于自己那一次 +1——计数本身就没有去重
     * （D2 对下载量也是这个口径），多算一两次不改变它的性质。
     *
     * <p><b>主干与标签走读缓存</b>（见 {@link #loadMeta}）
     *
     * @param viewerId 当前登录者，只用于判断「他收藏过没有」；取自 JWT
     * @throws BusinessException 40400 笔记不存在
     */
    public NoteDetailResponse detail(Long noteId, Long viewerId) {
        // id 不存在时这条 UPDATE 影响 0 行，无害；真正的 404 判定交给下面的 loadMeta。
        // 自增不参与缓存——它每次请求都要落库。两者于是出现可见差值，直到 TTL 到期 ViewCount 才跳到最新。
        noteMapper.incrementViewCount(noteId, NoteStatus.ONLINE.name());

        NoteMeta meta = loadMeta(noteId);
        if (meta == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        NoteDetailRow row = meta.row();

        NoteStatus status = NoteStatus.valueOf(row.getStatus());
        boolean online = status == NoteStatus.ONLINE;

        //装配响应
        return new NoteDetailResponse(
                row.getId(),
                row.getTitle(),
                row.getSummary(),
                row.getTeacher(),
                status,
                new CourseResponse(row.getCourseId(), row.getCourseName()),
                new UploaderResponse(
                        row.getUploaderId(), row.getNickname(),
                        avatarUrl(row.getAvatar()), row.getCollegeName()),
                meta.tags(),
                row.getViewCount(),
                row.getDownloadCount(),
                row.getFavoriteCount(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                // 非 ONLINE 时整个 file 置空，而不是把三个字段各自置 null：
                // 「这条笔记此刻没有可展示的文件」是一个整体事实，前端一个 truthy 判断就能收工
                online ? fileInfoOf(row) : null,
                // 非 ONLINE 也照查：§6.5 说收藏关系不因下架而解除（详见 FavoriteService#isFavorited）
                favoriteService.isFavorited(viewerId, noteId));
    }

    /**
     * 取详情的主干与标签：<b>读穿 + 降级</b>。命中缓存就直接返回，未命中才查那两条 SQL 并回填。
     *
     * <p>两件事刻意不做（对应「暂不处理穿透 / 击穿」）：<b>不缓存 null</b>——笔记不存在时直接返回
     * {@code null} 交给上层抛 40400，不让空值占住 key；<b>不防击穿</b>——热点 key 失效的瞬间，
     * 并发请求会一起查库。
     *
     * @return 笔记不存在时为 {@code null}，由调用方转 40400
     */
    private NoteMeta loadMeta(Long noteId) {
        String key = DETAIL_CACHE_PREFIX + noteId;

        try {
            // 用 instanceof 而不是「先取出来再强转」：缓存里若混进别的类型（换过实现、被手工塞过
            // 脏数据），强转会抛 ClassCastException 把请求打死；而它本该被当成「没命中」——
            // 下面照样查库救得回来，下次回填也就覆盖掉了。
            if (redisTemplate.opsForValue().get(key) instanceof NoteMeta cached) {
                return cached;
            }
        } catch (RuntimeException e) {
            log.warn("读笔记详情缓存失败，降级直查库 noteId={}", noteId, e);
        }

        NoteDetailRow row = noteMapper.selectDetail(noteId);
        if (row == null) {
            return null;
        }
        NoteMeta loaded = new NoteMeta(row, noteMapper.selectTagRefs(noteId));

        try {
            redisTemplate.opsForValue().set(key, loaded, DETAIL_CACHE_TTL);
        } catch (RuntimeException e) {
            log.warn("回填笔记详情缓存失败，不影响本次请求 noteId={}", noteId, e);
        }
        return loaded;
    }

    /**
     * 文件字段的装配。{@code previewMode} 不在库里，由 {@code contentType} 现推（§6.2）——
     * 它是类型的纯函数，落库等于把函数值再抄一份。
     */
    private static NoteDetailResponse.FileInfo fileInfoOf(NoteDetailRow row) {
        return new NoteDetailResponse.FileInfo(
                row.getOriginalName(),
                row.getSize(),
                row.getContentType(),
                PreviewMode.of(row.getContentType()));
    }

    /**
     * 头像对象键 → 公网直连 URL（§6.7）。
     *
     * <p>空值必须在这里短路：{@code StorageService#publicUrl} 是裸字符串拼接，
     * 喂个 null 会拼出一个以 {@code /null} 结尾、看着像 URL 的东西——
     * 前端会安心地拿它去 {@code <img src>}，然后拿到一个 404。
     */
    private String avatarUrl(String avatarKey) {
        return avatarKey == null ? null : storageService.publicUrl(Bucket.AVATAR, avatarKey);
    }

    /**
     * 列表：按最近更新 / 热门浏览 / 热门下载取一页笔记。见 §5.4。
     *
     * <p><b>只出 {@code ONLINE} 的笔记</b>——§4.1 的表里「列表 / 搜索」这一列对
     * OFFLINE、DELETED 是不可见。这与详情页正相反：详情要把状态带出去让前端渲染提示，
     * 列表则根本不该让它们出现。
     *
     * <p><b>一页固定四次数据库交互</b>，与页大小无关：主干分页查询 + MP 自动改写的 count
     * + 标签批量查询 + 收藏批量查询。后两条都是对「这一页的 id 列表」做一次 {@code IN}，
     * 而不是逐条查——那会立刻退化成 N+1（一页 20 条就是 40 次往返）。
     *
     * <p>标签与收藏都在 Java 里按 {@code noteId} 归到各自的行上，而不是让 SQL 去关联。
     *
     * @param viewerId 当前登录者，只用于填 {@code isFavorited}；取自 JWT
     * @throws BusinessException 40001 分页过深（{@code page × size > 1000}，见 {@link Paging#MAX_PAGE_DEPTH}）
     */
    public PageResponse<NoteListItemResponse> list(Long viewerId, NoteListQuery query) {
        Paging.requireShallow(query.getPage(), query.getSize());

        // Page 只携带「第几页、每页几条」，count 语句由分页插件改写生成，不在这里手写
        IPage<NoteListRow> result = noteMapper.selectListPage(
                new Page<>(query.getPage(), query.getSize()),
                NoteStatus.ONLINE.name(),
                query.getSort().name(),
                query.getCourseId(),
                query.getTagId());

        // total 取插件回填的真实总数，不封顶；page/size 回显入参而不是 result.getCurrent()，
        // 免得前端收到一个与它请求时不一致的页码（插件内部对越界页有它自己的处理）
        return new PageResponse<>(
                assemble(result.getRecords(), viewerId),
                result.getTotal(),
                query.getPage(),
                query.getSize());
    }

    /**
     * 把一页的行装配成出参：两次批量查询，再在内存里按 {@code noteId} 归位。
     *
     * <p>空页要在这里短路——{@code IN ()} 是语法错误，两条批量查询都吃不下空集合。
     */
    private List<NoteListItemResponse> assemble(List<NoteListRow> rows, Long viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = rows.stream().map(NoteListRow::getId).toList();

        Map<Long, List<TagResponse>> tagsByNote = tagsGroupedByNote(noteIds);

        Set<Long> favoritedNoteIds = favoriteService.favoritedNoteIds(viewerId, noteIds);

        return rows.stream()
                .map(row -> new NoteListItemResponse(
                        row.getId(),
                        row.getTitle(),
                        new CourseResponse(row.getCourseId(), row.getCourseName()),
                        new UploaderResponse(row.getUploaderId(), row.getNickname(),
                                avatarUrl(row.getAvatar()), row.getCollegeName()),
                        // 没有标签时给空列表：getOrDefault 的默认值必须是不可变的 List.of()，
                        // 而不是 null——前端可以无条件遍历
                        tagsByNote.getOrDefault(row.getId(), List.of()),
                        row.getViewCount(),
                        row.getDownloadCount(),
                        row.getFavoriteCount(),
                        row.getUpdatedAt(),
                        favoritedNoteIds.contains(row.getId())))
                .toList();
    }

    /**
     * 一页笔记的标签，按 {@code noteId} 归位。公开列表与「我的上传」共用。
     *
     * <p>入参必须非空：{@code IN ()} 是语法错误，调用方要在空页时先短路。
     *
     * <p>抽成方法而不是各写一遍：两条列表的标签口径必须完全一致（排序、别名、归位键），
     * 而这段代码里没有任何一处是某一条列表独有的——复制一份只会让「两处排序口径悄悄分叉」
     * 成为可能，而那种分叉在页面上表现为「同几篇笔记在两个列表里标签顺序不同」，
     * 极难被发现。
     */
    private Map<Long, List<TagResponse>> tagsGroupedByNote(List<Long> noteIds) {
        // groupingBy 的下游是 toList（ArrayList），组内保持查询的 encounter order，
        // 于是每篇笔记的标签仍是 tag.id 升序——与详情页那条查询的口径一致
        return noteMapper.selectTagRefsByNoteIds(noteIds).stream()
                .collect(Collectors.groupingBy(NoteTagRef::getNoteId,
                        Collectors.mapping(ref -> new TagResponse(ref.getTagId(), ref.getTagName()),
                                Collectors.toList())));
    }

    /**
     * 我的上传：本人传过的笔记，按上传时间倒序分页。见 §5.4、§4.1。
     *
     * <p><b>已删除的不出</b>——{@code DELETED} 由 SQL 无条件排除。
     * <p><b>已下架的照出</b>，靠 {@code status} 让前端加「已下架」角标。
     *
     * <p>与 {@link #list} 一样，一页只有三次数据库交互：主干分页 + 插件改写出的 count
     * + 标签批量查询。<b>没有第四次</b>——本列表不带 {@code isFavorited}，
     * 所以不需要 {@code FavoriteService} 那一次批量查询。
     *
     * @param uploaderId 当前登录者，取自 JWT。本列表只有本人能看，绝不接受请求参数传入
     * @throws BusinessException 40001 分页过深（{@code page × size > 1000}，见 {@link Paging#MAX_PAGE_DEPTH}）
     */
    public PageResponse<MyNoteItemResponse> listMyNotes(Long uploaderId, MyNoteListQuery query) {
        Paging.requireShallow(query.getPage(), query.getSize());

        IPage<MyNoteRow> result = noteMapper.selectMyNotePage(
                new Page<>(query.getPage(), query.getSize()),
                uploaderId,
                // 「要排除的那个」由调用方传入，不写死在 SQL 里
                NoteStatus.DELETED.name());

        return new PageResponse<>(
                assembleMyNotes(result.getRecords()),
                result.getTotal(),
                query.getPage(),
                query.getSize());
    }

    /**
     * 把一页的行装配成出参：一次批量查询取回整页的标签，再在内存里按 {@code noteId} 归位。
     *
     * <p>空页要在这里短路——{@code IN ()} 是语法错误，标签查询吃不下空集合
     * （与 {@link #assemble} 同一处守卫，两处都不能省）。
     */
    private List<MyNoteItemResponse> assembleMyNotes(List<MyNoteRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = rows.stream().map(MyNoteRow::getId).toList();
        Map<Long, List<TagResponse>> tagsByNote = tagsGroupedByNote(noteIds);

        return rows.stream()
                .map(row -> new MyNoteItemResponse(
                        row.getId(),
                        NoteStatus.valueOf(row.getStatus()),
                        row.getTitle(),
                        new CourseResponse(row.getCourseId(), row.getCourseName()),
                        // 没有标签时给空列表：默认值必须是不可变的 List.of()，
                        // 而不是 null——前端可以无条件遍历
                        tagsByNote.getOrDefault(row.getId(), List.of()),
                        row.getViewCount(),
                        row.getDownloadCount(),
                        row.getFavoriteCount(),
                        row.getCreatedAt()))
                .toList();
    }

    /**
     * 编辑元数据：上传者本人改写标题 / 简介 / 教师 / 课程 / 标签。见 §5.4、§4.1。
     *
     * <p><b>整条链路不碰 OSS，也不碰 {@code note_file}。</b>附件只在首次上传时确定，
     * 编辑时既不能新增也不能删除（§4.1）。这不是「本轮没做」，而是契约本身的一部分——
     * 一旦允许在编辑里换文件，就要同时处理「新对象传了、事务回滚了」（上传的补偿删除）
     * 与「旧对象删了、事务回滚了」（删除的对象清理）这两个窗口，它们各自都需要
     * 「事务外做网络调用 + 提交后清理」的编排。而本方法只需要一个普通的短事务。
     *
     * <p><b>标签是全量替换</b>：{@code request.getTags()} 是「编辑之后应有的全部标签」，
     * 传 null 或空数组即清空，不是「不动」。见 {@link NoteUpdateRequest}。
     *
     * @param uploaderId 当前登录者，取自 JWT；非本人一律 40300
     * @throws BusinessException 40400 笔记不存在；40300 非上传者本人；
     *                           40301 笔记已删除；40001 课程不存在
     */
    public void update(Long noteId, Long uploaderId, NoteUpdateRequest request) {
        transactionTemplate.executeWithoutResult(
                status -> applyUpdate(noteId, uploaderId, request));
    }

    /**
     * 事务内的那一段：校验 → 改写两份数据。
     *
     * <p><b>三步的顺序是有意的：</b>
     * <ol>
     *   <li>先锁行读归属与状态——这是唯一一次能确认「这行确实是我的、且此刻还能改」的机会，
     *       后面所有写入都建立在它之上。用 {@code selectOwnershipForUpdate} 而非普通读，
     *       是因为紧接着就要写，且判断本身（状态是不是 DELETED）依赖读到的值不变</li>
     *   <li>再校验课程、解析标签。放在锁之后，是为了让一个注定被拒的请求（非本人 / 已删除）
     *       不去 {@code tag} 表建新标签</li>
     *   <li>最后写 note 与 note_tag。{@code updated_at} 由 {@code updateMetadata} 写，
     *       这是全系统唯一写它的地方（§3.3）</li>
     * </ol>
     *
     * <p><b>{@code resolveIds} 这次在事务内</b>，与上传刻意放在事务外正相反。上传是因为它后面
     * 还跟着一次上百 MB 的 OSS 传输，不能让事务被网络等待占住；这里没有 OSS，
     * 放进来反而让「本次编辑顺手新建的标签」跟着编辑一起回滚——请求没成功，
     * 却往 {@code tag} 表留了几个没人引用的词，没有道理。
     *
     * <p><b>状态门只挡 {@code DELETED}，放行 {@code OFFLINE}。</b>编辑不迁移状态，
     * 一篇被管理员下架的笔记改完仍是下架的（{@code updateMetadata} 的 {@code SET} 里
     * 根本没有 {@code status} 这一列），所以「允许编辑下架笔记」不会让它重新可见。
     * 反过来说，禁止编辑的后果是上传者只能删了重传——{@code note.id} 变了，
     * 浏览与收藏数清零，别人收藏列表里的关系全断。删除那条链路本来就允许 {@code OFFLINE}
     * （§4.1 有 {@code OFFLINE→DELETED} 这条边），编辑没理由比删除更严。
     *
     * <p><b>这里用普通比较判 {@code DELETED}，不走 {@link NoteStateMachine}。</b>
     * 状态机回答的是「能不能从 A 迁到 B」，而编辑根本不发生迁移——拿它判门是误用，
     * 它不知道自己的答案会被用来决定「能不能写元数据」。
     */
    private void applyUpdate(Long noteId, Long uploaderId, NoteUpdateRequest request) {
        NoteOwnershipRow row = noteMapper.selectOwnershipForUpdate(noteId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        // 先判存在、再判归属：与删除同序，理由见 softDelete
        if (!Objects.equals(row.getUploaderId(), uploaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (NoteStatus.valueOf(row.getStatus()) == NoteStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }

        requireCourse(request.getCourseId());
        List<Long> tagIds = tagService.resolveIds(request.getTags());

        noteMapper.updateMetadata(noteId, request.getTitle().trim(),
                trimToNull(request.getSummary()), trimToNull(request.getTeacher()),
                request.getCourseId());

        // 标签全量替换：先删净再插回。空集合时跳过插入——insertBatch 拼不出合法语句
        noteTagMapper.deleteByNoteId(noteId);
        if (!tagIds.isEmpty()) {
            noteTagMapper.insertBatch(noteId, tagIds);
        }
    }

    /**
     * 供 ai 模块读取本人笔记的附件（§5.8 的编辑场景）。<b>只读</b>：不开事务、不加锁、不改任何列。
     *
     * <p><b>判门口径与 {@link #update} 完全一致</b>——不存在 40400、非本人 40300、
     * {@code DELETED} 报 40301、{@code OFFLINE} 放行。两者本来就是同一件事的两面：
     * <b>能改这篇笔记的元数据，就该能拿它的附件生成一段摘要</b>；反过来说，
     * 已经删掉的笔记没有任何理由再为它读一次 OSS。
     *
     * <p><b>这是 ai 模块唯一的读入口</b>（§1.1「跨模块访问」的第三个实例）：ai 要的是
     * 「附件在 OSS 的哪个键、当前用户能不能读」，属纯读路径，故走本类而不是让 ai 直接注入
     * {@link NoteMapper}。方向是单向的 ai → note——ai 的入口在它自己的 Controller 上，
     * note 不反向依赖 ai，因此<b>不构成构造器循环依赖</b>。这正是它与
     * {@code FavoriteService → NoteMapper} 那个例外的分界：那边正是因为反向注入会成环，
     * 才被允许直接写对方的单表。
     *
     * <p>也因此它<b>不加锁</b>：{@code selectOwnershipForUpdate} 的 {@code FOR UPDATE}
     * 是为「读状态 → 判 → 写」之间的窗口加的，而这里读完就结束了。后面跟着的是一次 OSS 读
     * 和一次几秒到几十秒的模型调用，期间没有任何本行的写入，锁只会白白挡住浏览量自增。
     *
     * @throws BusinessException 40400 笔记不存在；40300 非上传者本人；40301 笔记已删除
     */
    public NoteFileOwnershipRow requireOwnFile(Long noteId, Long uploaderId) {
        NoteFileOwnershipRow row = noteMapper.selectFileOwnership(noteId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        // 先判存在、再判归属：与 applyUpdate / softDelete 同序——反过来会用 40300
        // 泄露「这个 id 是存在的」
        if (!Objects.equals(row.getUploaderId(), uploaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (NoteStatus.valueOf(row.getStatus()) == NoteStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }
        return row;
    }

    /**
     * 管理员下架：{@code ONLINE → OFFLINE}。见 §5.6、§4.1。
     *
     * <p><b>刻意不做归属校验</b>——管理员可以下架任何人的笔记，这与本类其余写方法
     * 「一律本人」的口径正相反。方法名带 {@code admin} 前缀就是为了让这件事在<b>调用点</b>
     * 就看得见：谁想在用户路径上复用它，应当立刻意识到这里没有 40300。
     * 「谁能调到这里」由 {@code SecurityConfig} 的 {@code /api/admin/**} 规则保证，
     * 40300 在那条链上产出，不在本类里判。
     *
     * <p><b>下架是可逆的，所以不动 OSS 对象</b>：对象必须留着，否则恢复之后笔记就打不开了
     * （这与删除那个不可逆的操作正相反）。
     *
     * @throws BusinessException 40400 笔记不存在；40301 笔记已删除
     */
    public void adminOffline(Long noteId) {
        transition(noteId, NoteStatus.OFFLINE);
    }

    /** 管理员恢复：{@code OFFLINE → ONLINE}。见 {@link #adminOffline}，除目标状态外完全同构。 */
    public void adminRestore(Long noteId) {
        transition(noteId, NoteStatus.ONLINE);
    }

    /**
     * 事务内的那一段：锁行读状态 → 把三种情形各自收口 → 迁移。
     *
     * <p><b>三种情形一步都不能省，而且都不能丢给状态机：</b>
     * <ol>
     *   <li><b>不存在</b> → 40400</li>
     *   <li><b>已是 {@code DELETED}</b> → 40301。{@code DELETED} 是终态，在
     *       {@link NoteStateMachine} 的表里是空集，它抛的是 {@code IllegalStateException}，
     *       最终会翻成 50000——把「这篇笔记已经删了」伪装成「服务器内部错误」。
     *       状态机自己的注释里就预留了这个警告：它的异常不是被承诺的契约，
     *       调用方要先读状态、把自己认识的情形处理掉</li>
     *   <li><b>已经在目标状态</b> → 幂等返回，不写库。与删除、取消收藏同口径：
     *       前端连点两次不该有一次报错，两个人同时操作也不该让后者撞上异常。
     *       同理，{@code from == to} 在状态机那里也是非法迁移</li>
     * </ol>
     *
     * <p>收掉这三支之后，走到状态机的必然是一次真实的 {@code ONLINE ↔ OFFLINE} 迁移，
     * 于是 {@code requireTransition} 退化成一道纯粹的安全网——它再抛异常就一定是代码缺陷，
     * 而不是某个用户的动作。这正是 {@code NoteStateMachine} 想要的用法。
     *
     * <p><b>不碰 {@code updated_at}</b>：写语句里根本没有这一列（见
     * {@link NoteMapper#updateStatus}），§3.3 要求管理员的「下架 / 恢复」与删除一样不刷新它。
     *
     * <p>用 {@code transactionTemplate} 而不是 {@code @Transactional}，与
     * {@link #update} 保持一致：本类所有写方法都显式持有事务边界，读起来不必去猜
     * 注解在哪一层生效。这里没有提交后要做的事，所以用 {@code executeWithoutResult}。
     */
    private void transition(Long noteId, NoteStatus target) {
        transactionTemplate.executeWithoutResult(status -> {
            String current = noteMapper.selectStatusForUpdate(noteId);
            if (current == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }

            NoteStatus from = NoteStatus.valueOf(current);
            if (from == NoteStatus.DELETED) {
                throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
            }
            if (from == target) {
                return;
            }

            // 走到这里必然是一次真实迁移；上面三支已经把「不存在 / 终态 / 原地不动」都收掉了
            stateMachine.requireTransition(from, target);
            noteMapper.updateStatus(noteId, target.name());
        });
    }

    /**
     * 软删除：上传者本人把自己的笔记置为 {@code DELETED}。见 §5.4、§4.1、§3.3。
     *
     * <p><b>本方法必须是最外层事务边界，不要从别的 {@code @Transactional} 方法里调用。</b>
     * 清理对象那一步刻意放在 {@code transactionTemplate.execute(...)} <b>之后</b>，依赖
     * {@code execute} 确实开了新事务、并在返回前提交。若被一个事务方法调用，{@code REQUIRED}
     * 会加入外层事务，清理就跑到外层提交之前去了——一旦外层回滚，对象已经删掉而笔记还在，
     * 那篇笔记就永远打不开了。
     *
     * <p><b>为什么不用 {@code @Transactional}</b>：同一个取舍的两面。业务上，删除要做的第二件事
     * 是一次 OSS 网络调用，它不该被圈进数据库事务（§6.1 给上传定了同样的规矩）。工程上，
     * 用 {@code @Transactional} 加 {@code afterCommit} 回调也能达到目的，但本项目所有服务测试
     * 都跑在被 Spring 托管的测试事务里，挂在被加入事务上的 {@code afterCommit} 在测试中
     * <b>永远不会触发</b>，清理逻辑将完全无法断言；{@code execute} 之后的普通代码没有这个问题。
     *
     * <p><b>删除已删除的笔记是幂等的（200）</b>，与取消收藏同口径：DELETE 本就该如此，
     * 前端连点两次不该有一次报错。只有笔记不存在才是 40400。
     *
     * @param uploaderId 当前登录者，取自 JWT。本接口没有「管理员代删」的语义
     *                   （§5.6 给管理员的是下架 / 恢复），所以这里不看 role，非本人一律 40300
     * @throws BusinessException 40400 笔记不存在；40300 非上传者本人
     */
    public void delete(Long noteId, Long uploaderId) {
        // 返回非 null 表示「还有对象没清掉」，交给下面在事务外处理；null 表示这次无事可做
        String storageKey = transactionTemplate.execute(status -> softDelete(noteId, uploaderId));
        if (storageKey != null) {
            purgeObjectThenMark(noteId, storageKey);
        }
    }

    /**
     * 事务内的那一段：校验归属 → 走状态机 → 落库。返回「还没被物理清理的对象键」，没有则 null。
     *
     * <p><b>「已经删除」这一支也要返回 storageKey</b>，不能直接 return null——幂等 200 不等于
     * 什么都不做。若上一次的清理失败了（{@code is_purged} 仍是 0），那个对象就再也没人管：
     * {@link #purgeQuietly} 的注释一直说「残留由每日维护脚本对账」，而那只在 §7.3 补上之后才算数。
     * 让这一支照常返回待清键，重复点删除就顺带成了清理的重试路径，兜底不必依赖一个外部脚本。
     *
     * <p>{@code OFFLINE} 的笔记<b>允许</b>删除（§4.1 的图里有 {@code OFFLINE→DELETED} 这条边）：
     * 管理员下架过的笔记，上传者仍然有权把它删掉。这也是本流程绝不返回 40301 的原因——
     * 那个码管的是预览 / 下载 / 收藏。
     */
    private String softDelete(Long noteId, Long uploaderId) {
        NoteOwnershipRow row = noteMapper.selectOwnershipForUpdate(noteId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        // 先判存在、再判归属：id 不存在就报不存在。这不泄露新信息——详情接口对任何存在的笔记
        // 都返回 200，存在性本来就是登录用户可探测的
        if (!Objects.equals(row.getUploaderId(), uploaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 先取待清键再动状态：两支（正常删除、已经删过）都要用它
        String pendingKey = pendingStorageKey(noteId);

        NoteStatus from = NoteStatus.valueOf(row.getStatus());
        if (from == NoteStatus.DELETED) {
            return pendingKey;
        }

        // 状态在提交前不会再变：上面那条 selectOwnershipForUpdate 已经持有该行的 X 锁
        stateMachine.requireTransition(from, NoteStatus.DELETED);
        noteMapper.markDeleted(noteId, NoteStatus.DELETED.name());
        return pendingKey;
    }

    /**
     * 还没被物理清理的对象键；没有则 null。三种「没有」要分开对待：
     * 已经清理过（正常，静默返回 null）、{@code note_file} 行缺失、{@code storage_key} 为空
     * （后两种是数据不一致，记 warn 后照样放行）。
     *
     * <p>后两种只记日志不抛异常：用户要的是「这篇笔记别在站内出现了」，而状态那一步已经落库。
     * 因为一条脏的 {@code note_file} 行把一次成功的删除变成失败，是拿次要的事顶掉主要的事。
     * 而且 {@code StorageService#delete} 是裸字符串拼接，喂 null 会删到一个不存在的键，
     * 所以这里必须在调用之前把空值挡掉。
     */
    private String pendingStorageKey(Long noteId) {
        // uk_note_id 保证一篇笔记至多一个文件行，selectOne 不会拿到多行
        NoteFile file = noteFileMapper.selectOne(Wrappers.<NoteFile>lambdaQuery()
                .eq(NoteFile::getNoteId, noteId));
        if (file == null) {
            log.warn("笔记没有文件行，跳过对象清理 noteId={}", noteId);
            return null;
        }
        if (file.getIsPurged() != null && file.getIsPurged() != 0) {
            return null;
        }
        if (file.getStorageKey() == null || file.getStorageKey().isBlank()) {
            log.warn("文件行没有 storage_key，跳过对象清理 noteId={}", noteId);
            return null;
        }
        return file.getStorageKey();
    }

    /**
     * 事务提交后清理 OSS 对象并记账。见 §3.3、§7.3。
     *
     * <p><b>顺序是先删对象、后置标记，不能反。</b>若先置 {@code is_purged = 1} 而随后的删除失败，
     * 这一行就谎称「对象已清理」——而对象还在，且任何对账都救不回来（对账的判据正是这一列）。
     * 先删后标，最坏只是「对象删了、标记没写上」，重跑一次即可；而
     * {@code StorageService#delete} 对不存在的对象是 no-op，所以重跑安全。
     *
     * <p>标记带 {@code is_purged = 0} 的条件，让它在重复清理时也幂等。
     *
     * <p>失败只记日志、不往外抛：这一步只影响存储回收，不影响用户可见的结果——
     * {@code status} 已是 {@code DELETED}，§6.2 之后不再为它签发任何 URL。
     */
    private void purgeObjectThenMark(Long noteId, String storageKey) {
        try {
            storageService.delete(Bucket.NOTE, storageKey);
        } catch (RuntimeException e) {
            // 残留对象由 §7.3 的每日维护脚本按 is_purged = 0 对账清理
            log.error("清理笔记对象失败，is_purged 保持 0 待对账 noteId={} key={}", noteId, storageKey, e);
            return;
        }
        noteFileMapper.update(null, Wrappers.<NoteFile>lambdaUpdate()
                .eq(NoteFile::getNoteId, noteId)
                .eq(NoteFile::getIsPurged, 0)
                .set(NoteFile::getIsPurged, 1));
    }

    /**
     * 落库三张表。
     *
     * <p>计数列（浏览 / 下载 / 收藏）与 {@code is_purged} 都<b>不显式赋值</b>：
     * MyBatis-Plus 默认跳过 null 字段，于是这些列交给建表语句的 {@code DEFAULT 0}。
     */
    private NoteCreatedResponse insertNote(Long uploaderId, NoteUploadRequest request,
                                           FileDecision decision, StoredObject stored, List<Long> tagIds) {
        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(request.getCourseId());
        note.setTitle(request.getTitle().trim());
        note.setSummary(trimToNull(request.getSummary()));
        note.setTeacher(trimToNull(request.getTeacher()));
        // 提交后立即上架（§4.1）；此处不是「迁移」，故不经 NoteStateMachine
        note.setStatus(NoteStatus.ONLINE.name());
        noteMapper.insert(note);

        NoteFile noteFile = new NoteFile();
        noteFile.setNoteId(note.getId());
        // 用 storage 回传的 key 而非本地那个变量：将来 storage 若对 key 做归一化，
        // 落库的必须是真正存进去的那个
        noteFile.setStorageKey(stored.key());
        noteFile.setOriginalName(decision.originalName());
        noteFile.setSize(stored.size());
        noteFile.setContentType(stored.contentType());
        noteFileMapper.insert(noteFile);

        if (!tagIds.isEmpty()) {
            noteTagMapper.insertBatch(note.getId(), tagIds);
        }

        return new NoteCreatedResponse(note.getId());
    }

    private FileDecision inspect(MultipartFile file, Long uploaderId) {
        // MultipartFile#getInputStream 可重复调用：策略读一遍（二进制只读文件头，
        // md/txt 读到末尾），上传时再开一个新的，两者互不影响
        try (InputStream in = file.getInputStream()) {
            return filePolicy.inspect(file.getOriginalFilename(), file.getSize(), in);
        } catch (IOException e) {
            log.error("读取上传文件失败 uploaderId={} name={}", uploaderId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    private StoredObject store(MultipartFile file, FileDecision decision, Long uploaderId) {
        String key = filePolicy.objectKey(decision.extension());
        try (InputStream in = file.getInputStream()) {
            // 笔记对象不设 Cache-Control：它靠短 TTL 的签名 URL 读取，缓存由 §6.2 那套控制，
            // 与头像桶的「版本化键 + 长强缓存」是两套策略
            return storageService.upload(Bucket.NOTE, key, in, file.getSize(),
                    decision.contentType(), null);
        } catch (IOException e) {
            log.error("读取上传文件失败 uploaderId={} name={}", uploaderId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    /**
     * 校验课程合法性。走 {@link CourseService} 而不是直接查 {@code course} 表：§1.1 约定
     * 跨模块的校验一律经对方 Service。
     */
    private void requireCourse(Long courseId) {
        if (!courseService.exists(courseId)) {
            String message = "课程不存在";
            throw new BusinessException(ErrorCode.PARAM_INVALID, message,
                    List.of(new FieldViolation("courseId", message)));
        }
    }

    /**
     * 补偿删除：入库失败时刚上传的对象已无人引用。
     */
    private void purgeQuietly(String key) {
        try {
            storageService.delete(Bucket.NOTE, key);
        } catch (RuntimeException e) {
            log.error("补偿删除对象失败，可能残留孤儿对象 key={}", key, e);
        }
    }

    /** 空白串归一为 null：简介与教师是可选字段，存空串与存 null 在查询与展示上要分两路处理 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
