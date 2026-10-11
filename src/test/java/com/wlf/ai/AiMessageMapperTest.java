package com.wlf.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.entity.AiMessage;
import com.wlf.entity.College;
import com.wlf.entity.User;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link AiMessage} 与 ai_message 表的列映射，以及「取自己的会话」那条查询的隔离性。
 *
 * <p><b>值得为它单独起真库</b>：{@code AiServiceTest} 把 mapper 整个 mock 掉了，
 * 列名映射错了不会有任何用例变红——{@code conversation_id} 映射不上会让取历史安静地返回空，
 * 模型从某一轮起就丢掉了全部上下文，不报任何错。所以第一条用例冲的就是列映射。
 *
 * <p>{@code @Transactional} 让用例结束自动回滚，不往库里留测试数据。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AiMessageMapperTest {

    private static final String MARKER = "ZZTEST-AI";

    @Autowired
    private AiMessageMapper aiMessageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    private Long ownerId;
    private Long strangerId;
    private String conversationId;

    @BeforeEach
    void setUp() {
        ownerId = insertUser();
        strangerId = insertUser();
        conversationId = UUID.randomUUID().toString();
    }

    @Test
    void roundTripsEveryColumn() {
        aiMessageMapper.insert(row(ownerId, conversationId, AiMessageRole.USER, "问"));

        AiMessage loaded = selectConversation(ownerId, conversationId).get(0);

        assertThat(loaded.getUserId()).isEqualTo(ownerId);
        assertThat(loaded.getConversationId()).isEqualTo(conversationId);
        assertThat(loaded.getRole()).isEqualTo(AiMessageRole.USER.name());
        assertThat(loaded.getContent()).isEqualTo("问");
        // created_at 由 DDL 的 DEFAULT CURRENT_TIMESTAMP 兜底，Java 侧不赋值
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void historyComesBackInIdOrder() {
        aiMessageMapper.insert(row(ownerId, conversationId, AiMessageRole.USER, "第一个问题"));
        aiMessageMapper.insert(row(ownerId, conversationId, AiMessageRole.ASSISTANT, "第一个回答"));
        aiMessageMapper.insert(row(ownerId, conversationId, AiMessageRole.USER, "第二个问题"));

        assertThat(selectConversation(ownerId, conversationId))
                .extracting(AiMessage::getContent)
                .containsExactly("第一个问题", "第一个回答", "第二个问题");
    }

    /** 归属是查询条件的一部分：同一条 SQL 换个人来查就该是零行 */
    @Test
    void anotherUserSeesNothing() {
        aiMessageMapper.insert(row(ownerId, conversationId, AiMessageRole.USER, "问"));

        assertThat(selectConversation(strangerId, conversationId)).isEmpty();
    }

    @Test
    void conversationsAreKeptApart() {
        String otherConversation = UUID.randomUUID().toString();
        aiMessageMapper.insert(row(ownerId, conversationId, AiMessageRole.USER, "A 会话"));
        aiMessageMapper.insert(row(ownerId, otherConversation, AiMessageRole.USER, "B 会话"));

        assertThat(selectConversation(ownerId, conversationId))
                .extracting(AiMessage::getContent)
                .containsExactly("A 会话");
    }

    // ==================== 辅助 ====================

    /** 与 {@code AiService#loadHistory} 同一条查询 */
    private List<AiMessage> selectConversation(Long userId, String conversation) {
        return aiMessageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getUserId, userId)
                .eq(AiMessage::getConversationId, conversation)
                .orderByAsc(AiMessage::getId));
    }

    private static AiMessage row(Long userId, String conversationId, AiMessageRole role, String content) {
        AiMessage row = new AiMessage();
        row.setUserId(userId);
        row.setConversationId(conversationId);
        row.setRole(role.name());
        row.setContent(content);
        return row;
    }

    private Long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + "-" + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + "-" + suffix);
        user.setEmail(MARKER + "-" + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER);
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user.getId();
    }
}
