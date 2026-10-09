package com.wlf.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.auth.dto.UserInfo;
import com.wlf.catalog.CollegeService;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.User;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import com.wlf.user.dto.UpdateProfileRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 资料维护、头像上传（写公共读 Bucket）、个人主页统计。
 * 其他模块取用户信息也走此 Service，不直接引用 UserMapper。
 * 见《概要设计》§5.2、§6.7。
 *
 * <p>当前已落地改资料与头像上传；个人主页随后续切片补上。
 */
@Slf4j
@Service
public class UserService {

    /**
     * 头像对象的 {@code Cache-Control}。§6.7 要求写入对象元数据。
     *
     * <p><b>值取自调用方的策略而非存储层</b>（§1.1）：{@code immutable} 之所以成立，是因为
     * {@link AvatarFilePolicy#objectKey} 每次换头像都换一个键——同一个 URL 指向的对象永不改变。
     * 若哪天改成原地覆盖，这个头必须一并去掉，否则用户会一直看到旧头像。
     */
    private static final String AVATAR_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final UserMapper userMapper;

    /**
     * 学院存在性校验走对方模块的 Service，不直接引用 {@code CollegeMapper}——§1.1 的跨模块规矩。
     * 与 {@code AuthService} 注册时的做法同源。
     */
    private final CollegeService collegeService;

    /** 头像的准入规则。与 note 的 {@code NoteFilePolicy} 各持一份，理由见 {@link AvatarFilePolicy} */
    private final AvatarFilePolicy avatarFilePolicy;

    private final StorageService storageService;

    public UserService(UserMapper userMapper,
                       CollegeService collegeService,
                       AvatarFilePolicy avatarFilePolicy,
                       StorageService storageService) {
        this.userMapper = userMapper;
        this.collegeService = collegeService;
        this.avatarFilePolicy = avatarFilePolicy;
        this.storageService = storageService;
    }

    /**
     * 改昵称 / 改学院。见《概要设计》§5.2、§3.3。
     *
     * <p><b>改学院不级联写任何东西</b>，这不是遗漏：D8 定的是 {@code note} 根本不冗余存学院，
     * 笔记的学院 = 上传者的学院，读取时 JOIN {@code user} 推导。所以这一条 UPDATE 之后，
     * 该用户全部历史笔记的归属**自然**就变了——§3.3 说的「改学院会回溯改变其全部历史笔记的归属」，
     * 正是这个推导关系的直接后果，而不是某段级联代码在起作用。
     *
     * <p><b>本方法不开事务，是有意的。</b>它是「一条单语句 UPDATE + 一次只服务于 404 判定与
     * 响应装配的读」——没有任何不变量依赖读到的值，所以不存在需要串行化的「读-判-写」窗口，
     * 与 {@code NoteService.detail} 里那次刻意不包事务的浏览量自增同形。对比之下，
     * {@code FavoriteService#favorite} 与 {@code NoteService} 的编辑 / 删除 / 下架都要包事务：
     * 它们要么持锁后再写，要么依赖读到的状态决定能不能写，那是另一回事。
     *
     * <p>两个并发请求是单行的 last-write-wins，各自响应由自己的入参装配，自洽。
     * {@code update} 的受影响行数可以查 0（行在 SELECT 与 UPDATE 之间消失），但本项目
     * **没有任何删除用户的路径**，这个窗口是理论性的，不额外处理。
     *
     * @param userId 改谁，取自 JWT，不接受请求体传入——否则可以改任何人的资料
     * @throws BusinessException 40400 用户不存在；40001 学院不存在（带 {@code collegeId} 字段明细）
     */
    public UserInfo updateProfile(Long userId, UpdateProfileRequest request) {
        // 先判用户、后判学院：这是 404 门，与「改一个不存在的行」的自然读法一致。
        // 代价是「坏 token 配坏学院」会得到 40400 而非 40001，这是有意的
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 学院先校验：user.college_id 上的外键虽然也拦得住，但抛出的
        // DataIntegrityViolationException 会被兜底成 50000，而传了个不存在的学院
        // 明明是客户端的参数错误，应当是 40001，且和 @Valid 一样带字段级明细
        if (!collegeService.exists(request.getCollegeId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                    List.of(new FieldViolation("collegeId", "学院不存在")));
        }

        String nickname = request.getNickname().trim();

        // 列清单必须显式写出来，且绝不把上面那个实体交给 updateById。
        // selectById 捞回来的 User 每一列都非空，updateById 会因 MyBatis-Plus 默认的
        // NOT_NULL 策略生成一份含 username / email / password_hash / avatar / role / status /
        // created_at / updated_at 的**全列** SET：既会覆盖并发改动（头像切片上线后尤其危险），
        // 又平白重写密码哈希，而且因为 updated_at 被显式赋值，MySQL 的 ON UPDATE 会被抑制，
        // 此后真实修改也不再推进它。lambdaUpdate 让「只写这两列」在调用点一目了然。
        //
        // 不另开 mapper 的 @Update：UserMapper 的注释是「单表 CRUD 由 BaseMapper 提供，不写 XML」，
        // 这样写保住了那个边界；NoteService 里 noteFileMapper.update(null, lambdaUpdate()...) 是现成先例。
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getNickname, nickname)
                .set(User::getCollegeId, request.getCollegeId())
                .eq(User::getId, userId));

        // updated_at 刻意不写：user.updated_at 带 ON UPDATE CURRENT_TIMESTAMP，而 §3.3 明说
        // 这对 user 表是对的（每一次 UPDATE 本来就是一次真实的资料修改），与 note.updated_at
        // 刻意摘掉 ON UPDATE 正相反。附带一个正好合意的性质——两个值都没变时 MySQL 不推进它，
        // 而「提交了和现状一样的资料」本来就不算一次真实修改。全站没有任何代码读这个列
        // （UserInfo 无该字段、无 user mapper XML、无按它排序的列表），不必额外照顾。
        // 后来者请勿「修好」它去手写 NOW()。

        // 响应装配用内存里这个已被赋新值的对象，不再查一次库。
        // 注意它与写库走的不是同一个东西：上面那条 UPDATE 里没有实体。
        user.setNickname(nickname);
        user.setCollegeId(request.getCollegeId());
        return UserInfo.from(user, storageService);
    }

    /**
     * 上传头像：校验 → 传 OSS → 单列更新 {@code user.avatar}。见《概要设计》§6.7。
     *
     * <p><b>三步的先后顺序都是有意的</b>：
     * <ol>
     *   <li>用户存在性放在最前。不在就根本不该碰 OSS——先传后判会平白多出一个需要补偿删除的对象</li>
     *   <li>文件校验在 OSS 之前，与 {@code NoteService#upload} 同一条规矩：不合格就不产生对象</li>
     *   <li>OSS 上传在写库之前，因为对象键要先于落库存在</li>
     * </ol>
     *
     * <p><b>不开事务。</b>写库只有一条单语句 UPDATE，与 {@link #updateProfile} 同形——
     * 不存在需要串行化的「读-判-写」窗口。代价是「对象已存、更新失败」这个窗口真实存在，
     * 由 {@link #purgeQuietly} 兜住。
     *
     * <p><b>旧头像对象刻意不删。</b>键是版本化的，旧对象留在桶里，其 URL 在对方缓存过期前仍可访问
     * （§8.4 第 8 条明说接受这个代价）。清理旧对象挂在 §7.3 的每日维护脚本，不在请求链路上做——
     * 在这里删会让「换头像」多一次可能失败的网络写，而失败既不影响本次结果、也无从补救。
     *
     * <p>两个并发请求是 {@code user.avatar} 单列的 last-write-wins，各自响应由自己的入参装配，自洽；
     * 输掉的那次会在桶里留下一个无人引用的对象，同样归维护脚本。
     *
     * @param userId 换谁的头像，取自 JWT，不接受请求体传入——否则可以改任何人的头像
     * @throws BusinessException 40400 用户不存在；42200 文件类型 / 大小不合法
     */
    public UserInfo uploadAvatar(Long userId, MultipartFile file) {
        // 404 门放最前，与 updateProfile 同口径。注意这里读到的 avatar 是旧键——
        // 它在写库成功后才用于响应装配，写的是同一行，不存在不一致
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        AvatarDecision decision = inspectAvatar(file, userId);
        String key = avatarFilePolicy.objectKey(userId, decision.extension());
        StoredObject stored = storeAvatar(file, key, decision, userId);

        try {
            // 只写 avatar 一列，绝不把 selectById 捞回来的实体交给 updateById：
            // 那会生成一份含 username / email / password_hash / role / status 的全列 SET，
            // 覆盖并发改动并平白重写密码哈希。理由与 updateProfile 里那段完全一致。
            // 用 stored.key() 而不是本地的 key：落库的以存储层实际记下的键为准，
            // 与 NoteService 写 note_file.storage_key 的做法一致
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .set(User::getAvatar, stored.key())
                    .eq(User::getId, userId));
        } catch (RuntimeException e) {
            // 更新失败则刚传的对象无人引用，删掉；删不掉也只是留下孤儿，不影响本次请求的成败
            purgeQuietly(stored.key());
            throw e;
        }

        // 与写库走的不是同一个东西：那条 UPDATE 里没有实体。响应装配用内存里这个对象，
        // 免得再查一次库；赋的也是写进库的那个 stored.key()，两者不会不一致。
        // updated_at 不手写——user 表带 ON UPDATE，换头像是一次真实修改
        user.setAvatar(stored.key());
        return UserInfo.from(user, storageService);
    }

    private AvatarDecision inspectAvatar(MultipartFile file, Long userId) {
        // MultipartFile#getInputStream 可重复调用：策略读一遍文件头，上传时再开一个新的
        try (InputStream in = file.getInputStream()) {
            return avatarFilePolicy.inspect(file.getOriginalFilename(), file.getSize(), in);
        } catch (IOException e) {
            log.error("读取上传头像失败 userId={} name={}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    private StoredObject storeAvatar(MultipartFile file, String key, AvatarDecision decision, Long userId) {
        try (InputStream in = file.getInputStream()) {
            return storageService.upload(Bucket.AVATAR, key, in, file.getSize(),
                    decision.contentType(), AVATAR_CACHE_CONTROL);
        } catch (IOException e) {
            log.error("读取上传头像失败 userId={} name={}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    /** 补偿删除：更新失败时刚上传的对象已无人引用。删不掉也只记日志，不掩盖原始异常 */
    private void purgeQuietly(String key) {
        try {
            storageService.delete(Bucket.AVATAR, key);
        } catch (RuntimeException e) {
            log.error("补偿删除头像对象失败 key={}", key, e);
        }
    }
}
