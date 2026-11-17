package com.wlf.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 学院列表必须免登录——注册页在拿到 token 之前就要取下拉数据。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CollegeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void collegesArePublic() throws Exception {
        mockMvc.perform(get("/api/colleges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
