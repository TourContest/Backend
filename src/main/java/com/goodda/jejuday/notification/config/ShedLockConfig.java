package com.goodda.jejuday.notification.config;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void createShedLockTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS shedlock (
                name        VARCHAR(64)  NOT NULL PRIMARY KEY,
                lock_until  TIMESTAMP    NOT NULL,
                locked_at   TIMESTAMP    NOT NULL,
                locked_by   VARCHAR(255) NOT NULL
            )
        """);
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        );
    }
}
