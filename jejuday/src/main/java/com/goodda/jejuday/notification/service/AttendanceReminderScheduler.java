package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.attendance.repository.UserAttendanceRepository;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.entity.NotificationType;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceReminderScheduler {

    private final UserRepository userRepository;
    private final UserAttendanceRepository attendanceRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 12 * * *") // 매일 12시 정각
    public void sendAttendanceReminders() {
        LocalDate today = LocalDate.now();

        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            boolean isChecked = attendanceRepository.findByUserIdAndCheckDate(user.getId(), today).isPresent();

            if (!isChecked && user.isNotificationEnabled()) {
                String token = user.getFcmToken();
                String message = "아직 오늘 출석하지 않으셨어요! 한라봉 받으러 오세요 🍊";

                notificationService.sendNotificationInternal(
                        user,
                        message,
                        NotificationType.ATTENDANCE,
                        "attendance:" + today,
                        token
                );
            }
        }
    }
}
