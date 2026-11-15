package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.User;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 浏览量的并发自增：N 个线程同时打同一条笔记，计数必须<b>正好</b>是 N。
 *
 * <p>防的是「丢更新」。{@code NoteMapper#incrementViewCount} 是一条
 * {@code UPDATE note SET view_count = view_count + 1}，累加与写入在数据库里一次完成、
 * 靠行锁串联；若有人图方便改成「Java 里读出来 +1 再写回」，并发下这些读-改-写会互相覆盖，
 * 计数就小于 N——这条会红。
 *
 * <p><b>刻意不走 {@code noteService.detail()}</b>：自增的并发属性全在那一条语句上，
 * 套上 service（Redis 缓存、收藏查询、响应装配）只是给用例多背几个外部依赖，
 * 并不会多验到计数一分。detail 的其余行为由 {@code NoteDetailServiceTest} 覆盖。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>：并发线程各用各的连接，测试事务里尚未提交的行
 * 它们根本看不见，那样测的就不是同一条笔记了。代价是数据不会自动回滚，
 * 由 {@link #cleanUp()} 手工删干净（顺序不能反，都是外键）。
 */
@SpringBootTest
@ActiveProfiles("local")
class NoteViewCountConcurrencyTest {

    private static final String MARKER = "ZZTEST-VIEW-";
    private static final int THREADS = 32;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private CourseMapper courseMapper;

    private Long collegeId;
    private Long userId;
    private Long noteId;

    @BeforeEach
    void setUp() {
        College college = new College();
        college.setName(MARKER + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        collegeId = college.getId();

        User user = new User();
        user.setUsername(MARKER + "uploader");
        user.setEmail(MARKER + "uploader@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + "上传者");
        user.setCollegeId(collegeId);
        userMapper.insert(user);
        userId = user.getId();

        Note note = new Note();
        note.setUploaderId(userId);
        note.setCourseId(courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId());
        note.setTitle(MARKER + "并发浏览");
        // 自增的 WHERE 带 status 守卫，非 ONLINE 会一行不改，这里必须是 ONLINE
        note.setStatus(NoteStatus.ONLINE.name());
        noteMapper.insert(note);
        noteId = note.getId();
    }

    /** 删除顺序不能反：note 引用 user，user 引用 college，倒过来会撞外键 */
    @AfterEach
    void cleanUp() {
        if (noteId != null) {
            noteMapper.deleteById(noteId);
        }
        if (userId != null) {
            userMapper.deleteById(userId);
        }
        if (collegeId != null) {
            collegeMapper.deleteById(collegeId);
        }
    }

    @Test
    void concurrentIncrementsAreNotLost() throws Exception {
        runConcurrently(THREADS, () -> noteMapper.incrementViewCount(noteId, NoteStatus.ONLINE.name()));

        assertThat(noteMapper.selectById(noteId).getViewCount())
                .as("N 次并发自增就该涨 N，少于 N 说明有更新被覆盖了")
                .isEqualTo(THREADS);
    }

    /**
     * 让 n 个线程在栅栏处对齐后同时开跑，谁也不比谁早——不用栅栏的话，
     * 先起的线程可能已经跑完，压力根本叠不起来。
     *
     * <p>用 {@code invokeAll} 等全部结束，并逐个 {@code get()} 把线程里的异常带出来，
     * 免得某个自增炸了却被当成「没涨够」这种含糊的失败。
     */
    private static void runConcurrently(int threads, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CyclicBarrier startLine = new CyclicBarrier(threads);
            List<Callable<Void>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(() -> {
                    startLine.await(10, TimeUnit.SECONDS);
                    task.run();
                    return null;
                });
            }
            for (Future<Void> done : pool.invokeAll(calls)) {
                done.get();
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException("并发自增里有任务抛了异常", e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }
}
