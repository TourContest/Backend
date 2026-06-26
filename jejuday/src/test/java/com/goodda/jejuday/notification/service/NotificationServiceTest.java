package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.attendance.repository.ReminderTarget;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.dto.NotificationRequest;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.notification.service.OutboxBulkInserter.BulkOutboxRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private OutboxBulkInserter bulkInserter;

    @InjectMocks
    private NotificationService notificationService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .fcmToken("valid-token-longer-than-20-chars")
                .isNotificationEnabled(true)
                .build();
    }

    // --- send() ---

    @Test
    void send_알림비활성유저면_저장안함() {
        user.setNotificationEnabled(false);
        NotificationRequest req = new NotificationRequest(user, "msg", NotificationType.STEP, "key");

        notificationService.send(req);

        verifyNoInteractions(notificationRepository, outboxRepository);
    }

    @Test
    void send_유효토큰이면_알림저장과outbox삽입() {
        NotificationRequest req = new NotificationRequest(user, "msg", NotificationType.STEP, "step-key");

        notificationService.send(req);

        verify(notificationRepository).save(any(NotificationEntity.class));
        verify(outboxRepository).insertIfNotDuplicate(
                eq(1L), eq(user.getFcmToken()), eq("제주데이"),
                eq("msg"), eq("STEP"), eq("step-key"), any());
    }

    @Test
    void send_토큰길이20이하면_outbox삽입안함() {
        user.setFcmToken("short");
        NotificationRequest req = new NotificationRequest(user, "msg", NotificationType.STEP, "key");

        notificationService.send(req);

        verify(notificationRepository).save(any(NotificationEntity.class));
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void send_토큰null이면_outbox삽입안함() {
        user.setFcmToken(null);
        NotificationRequest req = new NotificationRequest(user, "msg", NotificationType.STEP, "key");

        notificationService.send(req);

        verifyNoInteractions(outboxRepository);
    }

    // --- scheduleAttendanceReminders() ---

    @Test
    void scheduleAttendanceReminders_출석자제외후_벌크삽입() {
        ReminderTarget attended = reminderTarget(10L, "valid-token-longer-than-20-chars-A");
        ReminderTarget notAttended = reminderTarget(20L, "valid-token-longer-than-20-chars-B");
        Set<Long> attendedIds = Set.of(10L);

        when(bulkInserter.insertChunk(anyList())).thenReturn(1);

        int result = notificationService.scheduleAttendanceReminders(
                List.of(attended, notAttended), attendedIds, LocalDate.of(2024, 1, 1));

        assertThat(result).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BulkOutboxRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkInserter).insertChunk(captor.capture());
        List<BulkOutboxRow> inserted = captor.getValue();
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).userId()).isEqualTo(20L);
    }

    @Test
    void scheduleAttendanceReminders_유효하지않은토큰제외() {
        ReminderTarget shortToken = reminderTarget(1L, "short");
        ReminderTarget nullToken = reminderTarget(2L, null);
        ReminderTarget validToken = reminderTarget(3L, "valid-token-longer-than-20-chars");

        when(bulkInserter.insertChunk(anyList())).thenReturn(1);

        notificationService.scheduleAttendanceReminders(
                List.of(shortToken, nullToken, validToken), Set.of(), LocalDate.now());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BulkOutboxRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkInserter).insertChunk(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).userId()).isEqualTo(3L);
    }

    @Test
    void scheduleAttendanceReminders_전원출석이면_삽입없음() {
        ReminderTarget t = reminderTarget(1L, "valid-token-longer-than-20-chars");

        int result = notificationService.scheduleAttendanceReminders(
                List.of(t), Set.of(1L), LocalDate.now());

        assertThat(result).isZero();
        verifyNoInteractions(bulkInserter);
    }

    @Test
    void scheduleAttendanceReminders_dedupKey는날짜포함() {
        ReminderTarget t = reminderTarget(1L, "valid-token-longer-than-20-chars");
        LocalDate date = LocalDate.of(2024, 6, 1);
        when(bulkInserter.insertChunk(anyList())).thenReturn(1);

        notificationService.scheduleAttendanceReminders(List.of(t), Set.of(), date);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BulkOutboxRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkInserter).insertChunk(captor.capture());
        assertThat(captor.getValue().get(0).dedupKey()).isEqualTo("attendance:2024-06-01");
    }

    // --- markAsRead() ---

    @Test
    void markAsRead_존재하지않으면_예외() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markAsRead_성공() {
        NotificationEntity entity = NotificationEntity.builder().id(1L).isRead(false).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(entity));

        notificationService.markAsRead(1L);

        assertThat(entity.isRead()).isTrue();
    }

    // --- isValidToken() ---

    @Test
    void isValidToken_경계값() {
        assertThat(NotificationService.isValidToken(null)).isFalse();
        assertThat(NotificationService.isValidToken("")).isFalse();
        assertThat(NotificationService.isValidToken("   ")).isFalse();
        assertThat(NotificationService.isValidToken("a".repeat(20))).isFalse(); // 정확히 20자 → false (> 20)
        assertThat(NotificationService.isValidToken("a".repeat(21))).isTrue();
    }

    private ReminderTarget reminderTarget(Long id, String token) {
        return new ReminderTarget() {
            @Override public Long getId() { return id; }
            @Override public String getFcmToken() { return token; }
        };
    }
}
