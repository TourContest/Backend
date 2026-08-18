package com.goodda.jejuday.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UserHardDeleteServiceTest {

    @Test
    void deletesUserDependencies() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserHardDeleteService service = new UserHardDeleteService(jdbcTemplate);

        service.deleteDependencies(7L);

        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object[].class));
    }
}
