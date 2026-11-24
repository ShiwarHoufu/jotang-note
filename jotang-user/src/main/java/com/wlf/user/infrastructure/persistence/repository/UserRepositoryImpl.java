package com.wlf.user.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.user.domain.user.User;
import com.wlf.user.domain.user.UserRepository;
import com.wlf.user.domain.user.model.Email;
import com.wlf.user.domain.user.model.Username;
import com.wlf.user.infrastructure.persistence.converter.UserConverter;
import com.wlf.user.infrastructure.persistence.mapper.UserMapper;
import com.wlf.user.infrastructure.persistence.po.UserPO;
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
    private final UserConverter converter;

    public UserRepositoryImpl(UserMapper userMapper, UserConverter converter) {
        this.userMapper = userMapper;
        this.converter = converter;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(converter::toDomain);
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<UserPO>lambdaQuery().eq(UserPO::getUsername, username.value())))
                .map(converter::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.<UserPO>lambdaQuery().eq(UserPO::getEmail, email.value())))
                .map(converter::toDomain);
    }

    @Override
    public boolean existsByUsername(Username username) {
        return userMapper.exists(
                Wrappers.<UserPO>lambdaQuery().eq(UserPO::getUsername, username.value()));
    }

    @Override
    public boolean existsByEmail(Email email) {
        return userMapper.exists(
                Wrappers.<UserPO>lambdaQuery().eq(UserPO::getEmail, email.value()));
    }

    @Override
    public List<User> findByIds(Collection<Long> ids) {
        return userMapper.selectList(Wrappers.<UserPO>lambdaQuery().in(UserPO::getId, ids))
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public void insert(User user) {
        UserPO po = converter.toPO(user);
        userMapper.insert(po);
        user.assignId(po.getId());
    }

    @Override
    public void updateProfile(User user) {
        userMapper.update(null, Wrappers.<UserPO>lambdaUpdate()
                .set(UserPO::getNickname, user.nickname().value())
                .set(UserPO::getCollegeId, user.collegeId().value())
                .eq(UserPO::getId, user.id()));
    }

    @Override
    public void updateAvatar(User user) {
        userMapper.update(null, Wrappers.<UserPO>lambdaUpdate()
                .set(UserPO::getAvatar, user.avatar() == null ? null : user.avatar().value())
                .eq(UserPO::getId, user.id()));
    }

    @Override
    public void updatePassword(User user) {
        userMapper.update(null, Wrappers.<UserPO>lambdaUpdate()
                .set(UserPO::getPasswordHash, user.passwordHash().value())
                .eq(UserPO::getId, user.id()));
    }
}
