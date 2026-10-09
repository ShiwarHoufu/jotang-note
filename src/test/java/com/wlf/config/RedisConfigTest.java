package com.wlf.config;

import com.wlf.catalog.dto.TagResponse;
import com.wlf.note.NoteDetailRow;
import com.wlf.note.NoteMeta;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存值序列化规则的验证。见 {@link RedisConfig}。
 *
 * <p><b>纯单测，不起 Spring 上下文、不连 Redis。</b>要验的是「对象 → 字节 → 对象」这一环；
 * 模板本身是 Spring Data 的东西，没什么好测的，出问题的永远是序列化配置。
 *
 * <p>值得单独立测试，是因为这里的失败方式很隐蔽：配置错了应用照常启动、缓存写入也照常成功
 * （序列化那一侧不报错），<b>直到读缓存时才炸</b>——运行时才暴露，编译期毫无征兆。
 *
 * <p>载荷用的是真实的 {@link NoteMeta}，而不是随手造的 record。它恰好由三块<b>形状各异</b>的东西
 * 组成，正好各自压住一个软肋：
 *
 * <ul>
 *   <li>{@link NoteDetailRow} 是 Lombok POJO——靠无参构造器 + setter 回填，与 record 的
 *       构造器注入走的是<b>不同的反序列化路径</b></li>
 *   <li>它带 {@code LocalDateTime}，依赖 Jackson 3 内置的 javatime 支持</li>
 *   <li>{@link TagResponse} 是 record（final 类），且装在 {@code List.of(...)} 里——
 *       default typing 若漏掉 final 类型、或放行不了集合实现，这里就会退化成 Map</li>
 * </ul>
 */
class RedisConfigTest {

    private final RedisSerializer<Object> serializer = new RedisConfig().redisJsonSerializer();

    @Test
    void roundTripsTheRealDetailCachePayload() {
        NoteMeta restored = roundTrip(sampleMeta());

        // 断言逐字段而不是整体 isEqualTo：NoteDetailRow 只挂了 @Getter/@Setter，没有 equals，
        // 整体比较退化成引用相等，必然是 false——那样这个用例会以假红的方式误导人。
        assertThat(restored.row().getId()).isEqualTo(7L);
        assertThat(restored.row().getTitle()).isEqualTo("笔记标题");
        assertThat(restored.row().getStatus()).isEqualTo("ONLINE");
        assertThat(restored.row().getContentType()).isEqualTo("application/pdf");
        assertThat(restored.row().getCollegeName()).isEqualTo("计算机学院");
        // 时间类型：序列化丢精度的话这里最先红
        assertThat(restored.row().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 9, 12, 30, 45));
        // 嵌套的 record 集合（TagResponse 有值语义，可以整体比）
        assertThat(restored.tags())
                .containsExactly(new TagResponse(5L, "期末"), new TagResponse(6L, "复习"));
    }

    /**
     * 存进去的必须是可读 JSON，且带 {@code @class} 类型信息。
     *
     * <p>单独钉住 {@code @class}：没有它，上面那个往返用例会以 {@code ClassCastException}
     * 的形式失败，报错指向不清；这里直接断言机制本身。
     */
    @Test
    void storesReadableJsonCarryingTypeInfo() {
        String json = new String(serializer.serialize(sampleMeta()), StandardCharsets.UTF_8);

        assertThat(json).contains("@class");
        // 中文按原样写入、不被转义成 Unicode 码点——这是「redis-cli 里直接看得懂」的一半
        assertThat(json).contains("笔记标题");
    }

    /**
     * 强转本身就是断言：拿不到类型信息时读回来的是 Map，这一步就抛 {@code ClassCastException}。
     * 与 {@code NoteService#loadMeta} 读缓存是同一个道理。
     */
    private NoteMeta roundTrip(NoteMeta meta) {
        return (NoteMeta) serializer.deserialize(serializer.serialize(meta));
    }

    /** 形状与线上一致：把 {@code selectDetail} 的 16 个字段与标签都填满。 */
    private static NoteMeta sampleMeta() {
        NoteDetailRow row = new NoteDetailRow();
        row.setId(7L);
        row.setTitle("笔记标题");
        row.setSummary("简介");
        row.setTeacher("张老师");
        row.setStatus("ONLINE");
        row.setViewCount(12L);
        row.setDownloadCount(3L);
        row.setFavoriteCount(1L);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 9, 12, 30, 45));
        row.setUpdatedAt(LocalDateTime.of(2026, 10, 9, 12, 30, 45));
        row.setOriginalName("课件.pdf");
        row.setSize(1048576L);
        row.setContentType("application/pdf");
        row.setCourseId(2L);
        row.setCourseName("数据结构");
        row.setUploaderId(3L);
        row.setNickname("上传者");
        row.setAvatar("avatars/3/abc.png");
        row.setCollegeName("计算机学院");

        return new NoteMeta(row, List.of(new TagResponse(5L, "期末"), new TagResponse(6L, "复习")));
    }
}
