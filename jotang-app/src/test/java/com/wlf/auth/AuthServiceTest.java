package com.wlf.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.auth.dto.RegisterRequest;
import com.wlf.auth.dto.UserInfo;
import com.wlf.catalog.CollegeMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.College;
import com.wlf.entity.User;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证注册链路对学院的绑定与校验（决策 D8）。
 *
 * <p>学院由用例自己插入，不依赖开发库里已有哪些学院，用完随事务回滚。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Test
    void registerBindsCollegeAndReturnsItsId() {
        Long collegeId = insertCollege("计算机学院");

        UserInfo info = authService.register(request("d8_register_ok", collegeId));

        assertThat(info.collegeId()).isEqualTo(collegeId);

        // 必须回查数据库：UserInfo 是拿内存里的 User 拼出来的，
        // 只断言返回值的话，即便 collegeId 根本没写进 INSERT 也照样能过。
        User saved = userMapper.selectById(info.id());
        assertThat(saved.getCollegeId()).isEqualTo(collegeId);
        assertThat(saved.getNickname()).isEqualTo("d8_register_ok");
    }

    /**
     * 学院不存在必须是 40001（客户端参数错误），不能漏到外键上变成 50000。
     * 这条正是 {@code CollegeService.exists} 存在的理由。
     */
    @Test
    void unknownCollegeIsRejectedAsParamInvalid() {
        RegisterRequest request = request("d8_register_bad", 999_999_999L);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    // data 形状与 @Valid 失败时保持一致，前端一套代码解析
                    assertThat(ex.getData())
                            .isEqualTo(List.of(new FieldViolation("collegeId", "学院不存在")));
                });
    }

    /** 未传学院时不该落库——否则会撞上 college_id 的 NOT NULL 变成 50000 */
    @Test
    void nullCollegeIsRejected() {
        RegisterRequest request = request("d8_register_null", null);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class);

        assertThat(userMapper.exists(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, "d8_register_null")))
                .isFalse();
    }

    private RegisterRequest request(String username, Long collegeId) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("Passw0rd!23");
        request.setCollegeId(collegeId);
        return request;
    }

    private Long insertCollege(String name) {
        College college = new College();
        college.setName(name);
        collegeMapper.insert(college);
        return college.getId();
    }
}
