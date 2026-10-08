package com.wlf.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.Tag;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 标签查询与按名创建。见《概要设计》§5.3、§3.2。
 *
 * <p>与学院、课程不同，标签不是纯预置数据：上传时用户可以自由打标，
 * 遇到没见过的名字就当场建一条（同名复用，靠 {@code tag.name} 上的 {@code uk_name} 兜底）。
 * 所以本类比 catalog 里另外两个 Service 多一条写路径。
 *
 * <p>标签**只增不改**：没有重命名、合并、删除（需求 §4.2）。这让「同名复用」成为一条
 * 永远成立的规则——名字一旦用上就固定指向同一行，不必担心别的模块拿着旧 id 跑空。
 */
@Service
public class TagService {

    /** 与 {@code tag.name VARCHAR(32)} 对齐。utf8mb4 下 VARCHAR 单位是字符不是字节 */
    private static final int MAX_TAG_NAME_LENGTH = 32;

    /**
     * 单篇笔记的标签数上限。
     *
     * <p>需求与设计文档都没定这个数，这里取 10 作护栏：不设上限的话，一个请求带上万个标签
     * 就会变成上万次 INSERT，是个现成的放大器。取值偏高是为了不误伤正常使用，
     * 真要放宽，改这一个常量即可。
     */
    private static final int MAX_TAGS_PER_NOTE = 10;

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

    /**
     * 把标签<b>名字</b>解析成 id，不存在的当场创建。上传笔记时用。
     *
     * <p>入参是名字而不是 id，因为用户打标是自由输入——前端虽有候选标签，
     * 但输入框允许直接敲新词。这样契约也更简单：调用方不必先建标签、再回填 id。
     *
     * <p>规则全部收在本方法内（去空白、去重、个数与长度上限、同名复用），
     * 而不是拆成「DTO 校验个数、Service 校验长度」两处——这些规则同属一件事
     * （什么样的标签集合是合法的），分散后迟早会不一致。
     *
     * <p>返回值与入参去重后的顺序一一对应，调用方按位置取用即可。
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
        tagMapper.selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getName, wanted))
                .forEach(tag -> idByName.put(tag.getName(), tag.getId()));

        for (String name : wanted) {
            idByName.computeIfAbsent(name, this::createOrGetId);
        }
        return wanted.stream().map(idByName::get).toList();
    }

    /**
     * 去空白、丢空项、按输入顺序去重，并校验长度与个数。
     *
     * <p>用 {@link LinkedHashSet}：既要去掉「标签 A」和「 标签 A 」这类重复，
     * 又要保住用户输入的顺序——返回的 id 顺序要与之一致，前端才会按打标顺序渲染。
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
     *
     * <p>先查后插之间存在竞态：两个请求同时上传、都要建「期末复习」，双方都没查到，
     * 于是双双 INSERT，其中一个必然撞上 {@code uk_name}。这里不引锁，直接接住冲突再读回来——
     * 「唯一索引兜底 + 捕获后重读」比「先加锁再插」简单，且这个冲突本身极罕见。
     *
     * <p>能这么接是因为 MySQL 下唯一键冲突只回滚该条语句，**事务本身仍然可用**，
     * 后续语句照常执行。这一点与 PostgreSQL 不同，那边一旦出错整个事务即废。
     * 另外，冲突异常在这里被就地捕获、没有外抛，所以 Spring 也不会把事务标记成 rollback-only。
     */
    private Long createOrGetId(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        try {
            tagMapper.insert(tag);
            // MyBatis-Plus 回填自增主键，无需再查一次
            return tag.getId();
        } catch (DuplicateKeyException e) {
            return requireExistingId(name);
        }
    }

    private Long requireExistingId(String name) {
        Tag existing = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, name));
        if (existing == null) {
            // 冲突之后又查不到：只可能是这行被并发删掉了，而标签只增不删（见类注释）。
            // 属带外改动，按服务端错误处理，不静默吞掉
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
        return existing.getId();
    }

    private static BusinessException violation(String field, String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, message,
                List.of(new FieldViolation(field, message)));
    }
}
