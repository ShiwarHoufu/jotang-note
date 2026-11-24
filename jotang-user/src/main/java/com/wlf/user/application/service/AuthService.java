package com.wlf.user.application.service;

import com.wlf.user.application.policy.LoginAttemptLimiter;
import com.wlf.user.application.command.LoginCommand;
import com.wlf.user.application.command.RegisterCommand;
import com.wlf.user.application.query.CurrentUserQuery;
import com.wlf.user.application.result.LoginResult;
import com.wlf.user.application.result.UserResult;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.common.JwtTokenProvider;
import com.wlf.user.domain.user.PasswordHasher;
import com.wlf.user.domain.user.User;
import com.wlf.user.domain.user.UserRepository;
import com.wlf.user.domain.user.model.CollegeId;
import com.wlf.user.domain.user.model.Email;
import com.wlf.user.domain.user.model.PasswordHash;
import com.wlf.user.domain.user.model.RawPassword;
import com.wlf.user.domain.user.model.Username;
import com.wlf.storage.StorageService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 注册、登录、退出。登录校验 BCrypt 密码后由 JwtTokenProvider 签发令牌。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final CollegeService collegeService;

    /**
     * 仅用于把 {@code UserResult.avatarUrl} 拼出来。
     */
    private final StorageService storageService;

    public AuthService(UserRepository userRepository,
                       PasswordHasher passwordHasher,
                       JwtTokenProvider jwtTokenProvider,
                       LoginAttemptLimiter loginAttemptLimiter,
                       CollegeService collegeService,
                       StorageService storageService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.collegeService = collegeService;
        this.storageService = storageService;
    }

    /**
     * 注册。
     */
    public UserResult register(RegisterCommand command) {
        Long collegeId = command.collegeId();

        if (!collegeService.exists(collegeId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                    List.of(new FieldViolation("collegeId", "学院不存在")));
        }

        Username username = new Username(command.username());
        Email email = new Email(command.email());

        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN);
        }

        PasswordHash passwordHash = passwordHasher.hash(new RawPassword(command.password()));
        User user = User.register(username, email, passwordHash, new CollegeId(collegeId));

        try {
            userRepository.insert(user);
        }
        catch (DuplicateKeyException ex) {
            // 竞态：两个请求同时通过了上面的预检查。重新判定到底撞了哪个唯一索引。
            throw new BusinessException(userRepository.existsByUsername(username)
                    ? ErrorCode.USERNAME_TAKEN
                    : ErrorCode.EMAIL_TAKEN);
        }

        return UserResult.from(user, storageService);
    }

    /**
     * 登录。失败累计达阈值即锁定。
     *
     * <p>用户不存在与密码错误返回同一个错误码与文案，避免账号枚举。
     */
    public LoginResult login(LoginCommand command) {
        String rawUsername = command.username().trim();
        loginAttemptLimiter.ensureNotLocked(rawUsername);

        Username username = null;
        try {
            username = new Username(rawUsername);
        }
        catch (BusinessException ignored) {
            // 用户名格式不合法不可能匹配任何账号，按凭证错误处理
        }

        RawPassword rawPassword = null;
        try {
            rawPassword = new RawPassword(command.password());
        }
        catch (BusinessException ignored) {
            // 密码长度不合法同样按凭证错误处理
        }

        User user = username == null ? null : userRepository.findByUsername(username).orElse(null);
        boolean passwordMatches = user != null && rawPassword != null
                && user.verifyPassword(rawPassword, passwordHasher);

        if (!passwordMatches) {
            loginAttemptLimiter.recordFailure(rawUsername);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }

        user.ensureCanLogin();
        loginAttemptLimiter.reset(rawUsername);

        String token = jwtTokenProvider.issue(user.id(), user.username().value(), user.role().name());
        return new LoginResult(token, UserResult.from(user, storageService));
    }

    /** 当前登录用户 */
    public UserResult currentUser(CurrentUserQuery query) {
        User user = userRepository.findById(query.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        return UserResult.from(user, storageService);
    }
}
