package com.wlf.catalog;

import com.wlf.catalog.dto.CollegeResponse;
import com.wlf.entity.College;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link CollegeService#list}：列映射、id 与 name 都不丢、按 id 升序。
 *
 * <p>断言用「包含刚插入的学院」而非「等于固定列表」，不受开发库已有数据影响；
 * 数据随用例回滚。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CollegeServiceTest {

    @Autowired
    private CollegeService collegeService;

    @Autowired
    private CollegeMapper collegeMapper;

    @Test
    void mapsIdAndNameAndSortsById() {
        College first = insert("计算机学院");
        College second = insert("数学学院");

        List<CollegeResponse> all = collegeService.list();

        // id 与 name 都要带出来：前端用 name 渲染、用 id 提交 collegeId
        assertThat(all).contains(
                new CollegeResponse(first.getId(), "计算机学院"),
                new CollegeResponse(second.getId(), "数学学院"));

        // 按 id 升序——插入顺序即展示顺序，由初始化脚本决定
        List<Long> ids = all.stream().map(CollegeResponse::id).toList();
        assertThat(ids).isSorted();
    }

    private College insert(String name) {
        College college = new College();
        college.setName(name);
        collegeMapper.insert(college);
        return college;
    }
}
