package com.wlf.note.application.catalog;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.note.application.catalog.dto.TagResponse;
import com.wlf.note.domain.catalog.Tag;
import com.wlf.note.domain.catalog.TagRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 标签查询与按名创建。
 *
 * <p>与学院、课程不同，标签不是纯预置数据：上传时用户可以自由打标。
 */
@Service
public class TagService {

    /** 与 {@code tag.name VARCHAR(32)} 对齐。utf8mb4 下 VARCHAR 单位是字符不是字节 */
    private static final int MAX_TAG_NAME_LENGTH = 32;

    /** 单篇笔记的标签数上限。*/
    private static final int MAX_TAGS_PER_NOTE = 10;

    /** 候选标签列表的条数上限。*/
    private static final int CANDIDATE_LIMIT = 20;

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * 候选标签：最常用的前 {@link #CANDIDATE_LIMIT} 个，按使用量倒序。
     */
    public List<TagResponse> list() {
        return tagRepository.selectMostUsed(CANDIDATE_LIMIT).stream()
                .map(TagResponse::from)
                .toList();
    }

    /**
     * 把标签名字解析成 id。不存在的当场创建。上传笔记时用。
     *
     * @param names 标签名，可含 null / 空白项（会被丢弃）
     * @return 标签 id，顺序同入参去重后的顺序；无有效标签时返回空列表
     * @throws BusinessException 40001 单个标签过长或标签过多（data 携带 {@link FieldViolation}）
     */
    public List<Long> resolveIds(Collection<String> names) {
        List<String> wanted = normalize(names);
        if (wanted.isEmpty()) {
            return List.of();
        }

        Map<String, Long> idByName = new HashMap<>();
        tagRepository.findByNameIn(wanted)
                .forEach(tag -> idByName.put(tag.getName(), tag.getId()));

        for (String name : wanted) {
            idByName.computeIfAbsent(name, this::createOrGetId);
        }
        return wanted.stream().map(idByName::get).toList();
    }

    /**
     * 去空白、丢空项、按输入顺序去重，并校验长度与个数。
     */
    private static List<String> normalize(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String raw : names) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > MAX_TAG_NAME_LENGTH) {
                throw violation("tags", "单个标签不超过 " + MAX_TAG_NAME_LENGTH + " 个字符：" + name);
            }
            distinct.add(name);
        }

        if (distinct.size() > MAX_TAGS_PER_NOTE) {
            throw violation("tags", "最多 " + MAX_TAGS_PER_NOTE + " 个标签");
        }
        return List.copyOf(distinct);
    }

    /**
     * 创建标签并返回其 id；若同名标签已存在（并发创建），改为读取已有那行。
     */
    private Long createOrGetId(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        try {
            tagRepository.insert(tag);
            return tag.getId();
        } catch (DuplicateKeyException e) {
            return requireExistingId(name);
        }
    }

    private Long requireExistingId(String name) {
        Tag existing = tagRepository.findByName(name).orElseThrow(
                () -> new BusinessException(ErrorCode.SERVER_ERROR));
        return existing.getId();
    }

    private static BusinessException violation(String field, String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message,
                List.of(new FieldViolation(field, message)));
    }
}
