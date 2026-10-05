package com.wlf.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.auth.dto.LoginRequest;
import com.wlf.auth.dto.LoginResponse;
import com.wlf.auth.dto.RegisterRequest;
import com.wlf.auth.dto.UserInfo;
import com.wlf.catalog.CollegeService;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.common.JwtTokenProvider;
import com.wlf.entity.User;
import com.wlf.user.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 注册、登录、退出。登录校验 BCrypt 密码后由 JwtTokenProvider 签发令牌。
 * 见《概要设计》§5.1、§6.3、§6.4。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final CollegeService collegeService;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       LoginAttemptLimiter loginAttemptLimiter,
                       CollegeService collegeService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.collegeService = collegeService;
    }

    /**
     * 注册。昵称取用户名占位，之后由「基本资料」修改。
     *
     * <p>先查后插之间存在竞态窗口，因此同时依赖 {@code uk_username} / {@code uk_email}
     * 唯一索引兜底，捕获冲突后再判断是哪个字段，避免并发下漏判。
     */
    public UserInfo register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim();
        Long collegeId = request.getCollegeId();

        /*学院先校验：user.college_id 上的外键虽然也拦得住，
        但抛出的DataIntegrityViolationException 会被兜底成 50000，而传了个不存在的学院
        明明是客户端的参数错误，应当是 40001，且和 @Valid 一样带字段级明细。*/
        if (!collegeService.exists(collegeId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                    List.of(new FieldViolation("collegeId", "学院不存在")));
        }
        if (existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }
        if (existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(username);
        user.setCollegeId(collegeId);
        user.setRole("USER");
        user.setStatus(1);

        try {
            userMapper.insert(user);
        }
        catch (DuplicateKeyException ex) {
            // 竞态：两个请求同时通过了上面的预检查。重新判定到底撞了哪个唯一索引。
            throw new BusinessException(existsByUsername(username)
                    ? ErrorCode.USERNAME_TAKEN
                    : ErrorCode.EMAIL_TAKEN);
        }

        return UserInfo.from(user);
    }

    /**
     * 登录。失败累计达阈值即锁定（§6.3）。
     *
     * <p>「用户不存在」与「密码错误」返回同一个错误码与文案，避免账号枚举。
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        loginAttemptLimiter.ensureNotLocked(username);

        User user = userMapper.selectOne(
                //创建一个查询条件构造器
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)
        );

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(username);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }

        // status 目前无人写入（schema 注释为「预留」），但一旦有人手工置 0，这里必须挡住
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        loginAttemptLimiter.reset(username);

        String token = jwtTokenProvider.issue(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, UserInfo.from(user));
    }

    /** 当前登录用户 */
    public UserInfo currentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return UserInfo.from(user);
    }

    private boolean existsByUsername(String username) {
        return userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    private boolean existsByEmail(String email) {
        return userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    }
}
