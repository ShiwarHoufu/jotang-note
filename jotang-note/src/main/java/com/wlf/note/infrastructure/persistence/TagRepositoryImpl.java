package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.note.domain.catalog.Tag;
import com.wlf.note.domain.catalog.TagRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 标签仓储的 MyBatis-Plus 实现。
 */
@Repository
public class TagRepositoryImpl implements TagRepository {

    private final TagMapper tagMapper;

    public TagRepositoryImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Override
    public List<Tag> selectMostUsed(int limit) {
        return tagMapper.selectMostUsed(limit);
    }

    @Override
    public List<Tag> findByNameIn(Collection<String> names) {
        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getName, names));
    }

    @Override
    public Optional<Tag> findByName(String name) {
        return Optional.ofNullable(tagMapper.selectOne(
                Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name)));
    }

    @Override
    public void insert(Tag tag) {
        tagMapper.insert(tag);
    }
}
