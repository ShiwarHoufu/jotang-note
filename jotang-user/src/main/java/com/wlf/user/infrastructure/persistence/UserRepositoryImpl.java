package com.wlf.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.user.domain.user.User;
import com.wlf.user.domain.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储的 MyBatis-Plus 实现。
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getEmail, email)));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    }

    @Override
    public List<User> findByIds(Collection<Long> ids) {
        return userMapper.selectList(Wrappers.<User>lambdaQuery().in(User::getId, ids));
    }

    @Override
    public void insert(User user) {
        userMapper.insert(user);
    }

    @Override
    public void updateProfile(Long userId, String nickname, Long collegeId) {
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getNickname, nickname)
                .set(User::getCollegeId, collegeId)
                .eq(User::getId, userId));
    }

    @Override
    public void updateAvatar(Long userId, String avatar) {
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getAvatar, avatar)
                .eq(User::getId, userId));
    }

    @Override
    public void updatePassword(Long userId, String passwordHash) {
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getPasswordHash, passwordHash)
                .eq(User::getId, userId));
    }
}
