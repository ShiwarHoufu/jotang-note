package com.wlf.note.dto;

import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.note.NoteStatus;
import com.wlf.note.PreviewMode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NoteDetailResponse} 的序列化形状测试——锁住的是<b>前端将要解析的那串 key</b>，
 * 不是 Java 侧的字段名。两者会不一致的地方正是本用例存在的理由：
 *
 * <ul>
 *   <li><b>{@code isFavorited} 的前缀</b>。这个仓库对 {@code is*} 开头的布尔字段有过教训
 *       （{@code Course.isOther} / {@code NoteFile.isPurged} 的注释都提到 Lombok 生成的
 *       {@code isOther()} 会被 Jackson 判成属性名 {@code other}）。record 走的是另一套内省路径
 *       （Jackson 3 以 record 组件名称为准），所以这条不能照搬结论，得实测钉住</li>
 *   <li><b>{@code status} / {@code previewMode} 序列化为枚举名</b>（{@code "ONLINE"} /
 *       {@code "PDF_INLINE"}），前端 switch 的就是这两个字面量</li>
 *   <li><b>非 ONLINE 时 {@code file} 是 JSON {@code null}</b>，而其余字段照常——
 *       这是 §4.1「详情页提示已下架」与「文件字段不外露」两条结论的交界处</li>
 * </ul>
 *
 * <p>用裸 {@link JsonMapper} 而非注入容器里的 ObjectMapper：本应用没有做任何 Jackson 定制
 * （无 {@code Jackson2ObjectMapperBuilderCustomizer}、application.yaml 里没有 {@code spring.jackson}），
 * 默认行为即线上行为，起 Spring 上下文换不来任何额外的可信度。
 */
class NoteDetailResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void onlineNoteSerializesEverySection() {
        JsonNode root = mapper.readTree(mapper.writeValueAsString(online()));

        assertThat(root.get("id").asLong()).isEqualTo(12L);
        assertThat(root.get("title").asString()).isEqualTo("第一章 绪论");
        assertThat(root.get("status").asString()).isEqualTo("ONLINE");

        // 课程与标签复用了 catalog 已发布的形状，这里顺带锁住它们的 key 不变
        assertThat(root.get("course").get("id").asLong()).isEqualTo(3L);
        assertThat(root.get("course").get("name").asString()).isEqualTo("离散数学");
        assertThat(root.get("tags").get(0).get("name").asString()).isEqualTo("复习");

        // 学院在上传者那一层，不在顶层（决策 D8）
        assertThat(root.get("uploader").get("collegeName").asString()).isEqualTo("计算机学院");
        assertThat(root.has("collegeName")).isFalse();

        // avatarUrl 是拼好的完整 URL，不是 OSS 对象键
        assertThat(root.get("uploader").get("avatarUrl").asString()).startsWith("https://");

        // 文件：对象键不外露
        assertThat(root.get("file").get("originalName").asString()).isEqualTo("课件.pdf");
        assertThat(root.get("file").get("previewMode").asString()).isEqualTo("PDF_INLINE");
        assertThat(root.get("file").has("storageKey")).isFalse();
    }

    /**
     * 这条是本用例的主要目的：确认 {@code isFavorited} 不会在序列化时被剥掉 {@code is} 前缀。
     * 若哪天换掉 Jackson 或改了命名策略导致它变成 {@code "favorited"}，前端会静默读不到收藏态。
     */
    @Test
    void favoritedKeepsItsIsPrefixInJson() {
        JsonNode root = mapper.readTree(mapper.writeValueAsString(online()));

        assertThat(root.has("isFavorited")).isTrue();
        assertThat(root.get("isFavorited").asBoolean()).isTrue();
        assertThat(root.has("favorited")).isFalse();
    }

    /** 已下架 / 已删除：file 整体为 null，其余元数据照常返回（不是 40301） */
    @Test
    void nonOnlineNoteKeepsMetadataButDropsTheFile() {
        NoteDetailResponse offline = new NoteDetailResponse(
                12L, "第一章 绪论", "简介", "张老师", NoteStatus.OFFLINE,
                new CourseResponse(3L, "离散数学"),
                new UploaderResponse(7L, "小明", "https://oss.example.com/a.png", "计算机学院"),
                List.of(new TagResponse(3L, "复习")),
                43L, 3L, 5L,
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 2, 9, 30),
                null,
                true);

        JsonNode root = mapper.readTree(mapper.writeValueAsString(offline));

        assertThat(root.get("status").asString()).isEqualTo("OFFLINE");
        assertThat(root.hasNonNull("file")).isFalse();

        // 元数据一条不少
        assertThat(root.get("title").asString()).isEqualTo("第一章 绪论");
        assertThat(root.get("uploader").get("collegeName").asString()).isEqualTo("计算机学院");
        assertThat(root.get("tags").get(0).get("id").asLong()).isEqualTo(3L);

        // §6.5：非 ONLINE 不解除收藏关系，这个值如实反映表里的状态
        assertThat(root.get("isFavorited").asBoolean()).isTrue();
    }

    private static NoteDetailResponse online() {
        return new NoteDetailResponse(
                12L, "第一章 绪论", "简介", "张老师", NoteStatus.ONLINE,
                new CourseResponse(3L, "离散数学"),
                new UploaderResponse(7L, "小明", "https://oss.example.com/a.png", "计算机学院"),
                List.of(new TagResponse(3L, "复习"), new TagResponse(8L, "期末")),
                43L, 3L, 5L,
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 2, 9, 30),
                new NoteDetailResponse.FileInfo("课件.pdf", 1048576L, "application/pdf", PreviewMode.PDF_INLINE),
                true);
    }
}
