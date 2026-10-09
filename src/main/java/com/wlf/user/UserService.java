package com.wlf.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.auth.dto.UserInfo;
import com.wlf.catalog.CollegeService;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.User;
import com.wlf.user.dto.UpdateProfileRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资料维护、头像上传（写公共读 Bucket）、个人主页统计。
 * 其他模块取用户信息也走此 Service，不直接引用 UserMapper。
 * 见《概要设计》§5.2、§6.7。
 *
 * <p>当前只实现了改资料；头像上传与个人主页随后续切片补上。
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    /**
     * 学院存在性校验走对方模块的 Service，不直接引用 {@code CollegeMapper}——§1.1 的跨模块规矩。
     * 与 {@code AuthService} 注册时的做法同源。
     */
    private final CollegeService collegeService;

    public UserService(UserMapper userMapper, CollegeService collegeService) {
        this.userMapper = userMapper;
        this.collegeService = collegeService;
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
        return UserInfo.from(user);
    }
}
