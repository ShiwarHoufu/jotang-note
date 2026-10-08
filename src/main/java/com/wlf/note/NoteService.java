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
import com.wlf.note.dto.NoteCreatedResponse;
import com.wlf.note.dto.NoteDetailResponse;
import com.wlf.note.dto.NoteListItemResponse;
import com.wlf.note.dto.NoteListQuery;
import com.wlf.note.dto.NoteUploadRequest;
import com.wlf.note.dto.UploaderResponse;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 笔记核心业务。见《概要设计》§6.1（上传）、§6.2（预览与下载）、§5.4（详情）。
 *
 * <p>当前只落地了上传与详情两条链路，列表 / 搜索 / 编辑 / 删除 / 预览 / 下载待后续切片。
 */
@Slf4j
@Service
public class NoteService {

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

    public NoteService(NoteMapper noteMapper,
                       NoteFileMapper noteFileMapper,
                       NoteTagMapper noteTagMapper,
                       CourseService courseService,
                       TagService tagService,
                       FavoriteService favoriteService,
                       NoteFilePolicy filePolicy,
                       StorageService storageService,
                       NoteStateMachine stateMachine,
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
     * <p>三条查询的顺序是有意的：<b>先自增、后读取</b>，这样 SELECT 拿到的就是已含本次浏览的值。
     *
     * <p><b>本方法刻意不开事务。</b>里面的写语句只有浏览量自增一条，而把它圈进事务的后果是：
     * {@code note} 那一行的排他锁会一直持有到方法返回——这中间还夹着标签查询、收藏查询与响应体装配。
     * 详情页是最热的读接口，为一个统计计数把行锁按住整个请求时长，代价远大于收益；
     * 不圈事务则 UPDATE 立即提交、锁立即释放。代价是「自增」与「读取」之间可能插进别人的自增，
     * 于是返回的 {@code viewCount} 可能略大于自己那一次 +1——计数本身就没有去重
     * （D2 对下载量也是这个口径），多算一两次不改变它的性质。
     *
     * @param viewerId 当前登录者，只用于判断「他收藏过没有」；取自 JWT
     * @throws BusinessException 40400 笔记不存在
     */
    public NoteDetailResponse detail(Long noteId, Long viewerId) {
        // 状态由调用方传入：'ONLINE' 这个字面量属于 NoteStatus，不该在 SQL 里再写一份。
        // id 不存在时这条 UPDATE 影响 0 行，无害；真正的 404 判定交给下面的 selectDetail。
        noteMapper.incrementViewCount(noteId, NoteStatus.ONLINE.name());

        NoteDetailRow row = noteMapper.selectDetail(noteId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        NoteStatus status = NoteStatus.valueOf(row.getStatus());
        boolean online = status == NoteStatus.ONLINE;

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
                noteMapper.selectTagRefs(noteId),
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

        // groupingBy 的下游是 toList（ArrayList），组内保持查询的 encounter order，
        // 于是每篇笔记的标签仍是 tag.id 升序——与详情页那条查询的口径一致
        Map<Long, List<TagResponse>> tagsByNote = noteMapper.selectTagRefsByNoteIds(noteIds).stream()
                .collect(Collectors.groupingBy(NoteTagRef::getNoteId,
                        Collectors.mapping(ref -> new TagResponse(ref.getTagId(), ref.getTagName()),
                                Collectors.toList())));

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
        NoteDeleteRow row = noteMapper.selectForDelete(noteId);
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

        // 状态在提交前不会再变：上面那条 selectForDelete 已经持有该行的 X 锁
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
     * 在 Java 侧再写一遍 0 只会多一处可能与 DDL 不一致的地方。
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
            return storageService.upload(Bucket.NOTE, key, in, file.getSize(), decision.contentType());
        } catch (IOException e) {
            log.error("读取上传文件失败 uploaderId={} name={}", uploaderId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    /**
     * 校验课程合法性。走 {@link CourseService} 而不是直接查 {@code course} 表：§1.1 约定
     * 跨模块的校验一律经对方 Service。
     *
     * <p>报 40001 带字段明细而不是 40400：课程由前端下拉框提供，id 对不上属于请求参数错，
     * 前端据此把课程选择框标红比弹一个「资源不存在」有用。
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
     *
     * <p>删除失败只记日志、不再外抛——原始异常才是用户与排查者要看的那个，
     * 用「OSS 删不掉」把它顶掉只会把排查方向带偏。残留对象由 §7.3 的每日维护脚本对账清理
     * （那一步按 {@code note_file.is_purged = 0} 扫，仓外的脚本负责，见 §7.3）。
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
