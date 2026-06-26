package com.goodda.jejuday.auth.service;

import com.goodda.jejuday.auth.entity.TemporaryUser;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TemporaryUserCleanupScheduler {

    private final TemporaryUserService temporaryUserService;

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void cleanupExpiredTemporaryUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(3); // 3분 이상 된 유저 삭제
        List<TemporaryUser> expired = temporaryUserService.findByCreatedAtBefore(threshold);

        if (!expired.isEmpty()) {
            expired.forEach(
                    temporaryUser -> temporaryUserService.deleteByTemporaryUserId(temporaryUser.getTemporaryUserId()));
            System.out.println("Deleted " + expired.size() + " expired temporary users.");
        }
    }
}
