package com.goodda.jejuday.spot.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "report", indexes = {
        @Index(name = "ix_report_target", columnList = "target_type,target_id"),
        @Index(name = "ix_report_spot", columnList = "spot_id")
})
@Getter @Setter
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    // targetType=SPOT 이면 spotId, targetType=REPLY 이면 replyId
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    // REPLY 신고여도 어느 게시글에 달린 댓글인지 바로 알 수 있도록 항상 저장(관리자 화면에서 게시글 기준 그룹핑용)
    @Column(name = "spot_id", nullable = false)
    private Long spotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum TargetType { SPOT, REPLY }

    public enum ReportReason { SPAM, ABUSE, ADULT_CONTENT, MISINFORMATION, ETC }

    public enum Status { PENDING, REVIEWED, REJECTED }
}
