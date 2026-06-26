package com.goodda.jejuday.attendance.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "user_bonus_log", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "bonus_type", "given_date"})
})
public class UserBonusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bonus_type", nullable = false)
    private String bonusType;

    @Column(name = "given_date", nullable = false)
    private LocalDate givenDate;

    public UserBonusLog() {
    }

    public UserBonusLog(User user, String bonusType, LocalDate givenDate) {
        this.user = user;
        this.bonusType = bonusType;
        this.givenDate = givenDate;
    }
}
