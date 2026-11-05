package com.flowmind.platform.system;

import com.flowmind.platform.system.service.SysAuditAndConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flowmind_login_failure;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginDatabaseFailureTest {
    @Autowired MockMvc mvc;
    @MockBean SysAuditAndConfigService audit;

    @Test void databaseFailureReturnsServerErrorBeforeLoginInsteadOfTokenError() throws Exception {
        when(audit.sensitiveWords()).thenThrow(new DataAccessResourceFailureException("Database unavailable"));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(50000))
            .andExpect(jsonPath("$.message").value("数据库连接或查询失败，请检查数据库配置后重试"))
            .andExpect(jsonPath("$.data").isEmpty());
    }
}
