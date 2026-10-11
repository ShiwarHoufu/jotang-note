package com.wlf.note;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 孤儿对象对账打真桶的集成测试：<b>对象是不是真的从 OSS 里消失了</b>。
 *
 * <p><b>为什么必须有这一层</b>：{@code OrphanObjectSweeperTest} 把 {@link StorageService} 换成 mock，
 * 验的是「哪些行被选中、{@code is_purged} 变成什么」——而「对账跑完了，桶里的东西没了」
 * 恰恰是 mock 永远验不到的那一半。这在改动之前不重要（删除接口自己会同步清一次，对账只是兜底），
 * <b>现在对账是被删对象的唯一回收路径</b>（§3.3），这条链路断了只会表现为「存储慢慢涨」，
 * 不报错、不影响任何用户可见的行为。
 *
 * <p>与 {@code OssStorageServiceTest} 的分工：那边验的是存储原语本身（签名 URL、元数据、
 * 公共读），这边验的是「对账选出的行 ↔ 桶里那个对象」的对应关系。两者都打真桶，
 * 都靠 {@code finally} / {@code @AfterEach} 收尾，不在桶里留残留。
 *
 * <p>打上 {@code @Tag("oss")}，pom 的 surefire 默认排除（依赖网络与有效 AccessKey）。单独运行：
 * <pre>./mvnw test -DexcludedGroups= -Dgroups=oss</pre>
 *
 * <p><b>本类同时用事务与真 OSS，两者的生命周期不一样</b>：库里的行随测试事务回滚，
 * OSS 上的对象不会——所以每个用例自己把键记进 {@link #uploadedKeys}，由 {@code @AfterEach}
 * 兜底删除。用例失败时也一样会清，不留垃圾。
 */
@Tag("oss")
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class OrphanObjectSweeperOssTest {

    private static final String MARKER = "ZZTEST-SWEEP-OSS-";

    @Autowired
    private NoteService noteService;

    @Autowired
    private OrphanObjectSweeper sweeper;

    /** 生产实现，刻意不 mock——本类的全部意义就是让真的对象被真的删掉 */
    @Autowired
    private StorageService storageService;

    /** 直接注入数据面客户端用于读回校验：StorageService 只提供存/签/删，刻意不提供「存在吗」 */
    @Autowired
    @Qualifier("ossServer")
    private OSS ossServer;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteFileMapper noteFileMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${aliyun.oss.bucket-note}")
    private String bucketNote;

    /** 本用例真传上去的键，{@code @AfterEach} 逐个删掉 */
    private final List<String> uploadedKeys = new ArrayList<>();

    private Long courseId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();

        // 候选集查的是整张表。开发库里若残留着别处删过却没清掉的行，既会占掉批量的名额、
        // 也可能让本用例顺带删掉别人的对象。先在事务内把它们全标成已清理——用例结束回滚，
        // 不会真的动到那些行。
        jdbcTemplate.update("""
                UPDATE note_file nf JOIN note n ON n.id = nf.note_id
                SET nf.is_purged = 1
                WHERE n.status = ? AND nf.is_purged = 0
                """, NoteStatus.DELETED.name());
    }

    @AfterEach
    void cleanUpBucket() {
        uploadedKeys.forEach(key -> storageService.delete(Bucket.NOTE, key));
        uploadedKeys.clear();
    }

    /**
     * 主用例：真传一个对象 → 造一行「已删除、没清过」→ 跑一轮对账 →
     * <b>桶里查不到了</b>，且 {@code is_purged} 补上。
     */
    @Test
    void sweepReallyDeletesTheObjectFromTheBucket() {
        Fixture fixture = createNoteWithRealObject(NoteStatus.DELETED);

        sweeper.sweep();

        assertThat(ossServer.doesObjectExist(bucketNote, fixture.storageKey()))
                .withFailMessage("对账跑完了对象还在桶里，key=%s", fixture.storageKey())
                .isFalse();
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged()).isEqualTo(1);
    }

    /**
     * 反向用例：<b>在线笔记的对象一个都不能被碰。</b>
     *
     * <p>{@code is_purged = 0} 是 {@code note_file} 的建表默认值，每一篇活着的笔记都是 0——
     * 候选集漏掉 {@code status = DELETED} 就会把站上所有文件删光，且全程没有任何报错。
     * mock 版也盯着这条，但在这里它的分量不同：这是真桶，删掉就真的没了。
     */
    @Test
    void sweepNeverTouchesAnOnlineNotesObject() {
        Fixture fixture = createNoteWithRealObject(NoteStatus.ONLINE);

        sweeper.sweep();

        assertThat(ossServer.doesObjectExist(bucketNote, fixture.storageKey()))
                .withFailMessage("在线笔记的对象被对账删掉了，key=%s", fixture.storageKey())
                .isTrue();
        assertThat(noteFileMapper.selectById(fixture.fileId()).getIsPurged()).isZero();
    }

    // ==================== 夹具 ====================

    private record Fixture(Long noteId, Long fileId, String storageKey) {
    }

    /** 造一篇笔记，并<b>真的</b>往桶里传一个对象，返回它的键供断言与收尾使用 */
    private Fixture createNoteWithRealObject(NoteStatus status) {
        Long uploaderId = insertUser();

        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus(status.name());
        noteMapper.insert(note);

        // 键走 test/ 前缀而不是生产格式 notes/yyyy/MM/...：对账把它当不透明字符串，
        // 键长什么样不影响结论，而 test/ 前缀让桶里万一残留的垃圾一眼可辨、好清理。
        String storageKey = "test/sweep/" + UUID.randomUUID() + ".pdf";
        byte[] payload = "孤儿对象对账集成测试".getBytes(StandardCharsets.UTF_8);
        storageService.upload(Bucket.NOTE, storageKey, new ByteArrayInputStream(payload),
                payload.length, "application/pdf", null);
        uploadedKeys.add(storageKey);

        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey(storageKey);
        file.setOriginalName("课件.pdf");
        file.setSize((long) payload.length);
        file.setContentType("application/pdf");
        noteFileMapper.insert(file);

        return new Fixture(note.getId(), file.getId(), storageKey);
    }

    private Long insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER);
        user.setCollegeId(insertCollege());
        userMapper.insert(user);
        return user.getId();
    }
}
