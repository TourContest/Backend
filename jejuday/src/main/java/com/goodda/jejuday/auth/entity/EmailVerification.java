package com.goodda.jejuday.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "email_verification")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "temporary_user_id", referencedColumnName = "temporary_user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TemporaryUser temporaryUser;

    // 비밀번호 찾기 시 User와 연관 (OneToMany 관계)
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "verification_code", nullable = false, length = 6)
    private String verificationCode;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
