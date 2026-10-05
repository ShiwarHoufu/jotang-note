package com.wlf.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.entity.Tag;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签查询。见《概要设计》§5.3。
 *
 * <p>这里只做读。标签的**创建**发生在笔记上传时（用户自由打标、同名复用），
 * 那部分逻辑等 note 模块落地时再加进来，此处不预先铺开。
 */
@Service
public class TagService {

    private final TagMapper tagMapper;

    public TagService(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    /**
     * 全部标签，用作筛选栏与上传表单的候选。
     *
     * <p>按 id 升序：先是 data.sql 里的常用标签，其后是用户新增的。
     * 与学院、课程同理，不按 name 排——默认排序规则对中文按 Unicode 码点而非拼音排序。
     *
     * <p>不分页：标签量级有限，前端整份拉走做本地筛选。
     */
    public List<TagResponse> list() {
        return tagMapper.selectList(
                        Wrappers.<Tag>lambdaQuery().orderByAsc(Tag::getId))
                .stream()
                .map(TagResponse::from)
                .toList();
    }
}
