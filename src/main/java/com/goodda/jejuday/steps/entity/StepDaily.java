package com.goodda.jejuday.steps.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "step_daily")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_steps", nullable = false)
    private long totalSteps;

    @Column(name = "converted_points", nullable = false)
    private int convertedPoints;

    @Column(name = "level_reward_claimed", nullable = false)
    private boolean levelRewardClaimed = false;

    // 시작 보너스 걸음수
    @Builder.Default
    @Column(name = "start_bonus_steps", nullable = false)
    private long startBonusSteps = 0;

    // 시작 보너스 적용 여부
    @Builder.Default
    @Column(name = "start_bonus_applied", nullable = false)
    private boolean startBonusApplied = false;

    // 교환 횟수 제한 관련 필드 추가
    @Builder.Default
    @Column(name = "exchange_count", nullable = false)
    private int exchangeCount = 0; // 일일 교환 횟수

    public boolean isRewardAvailable() {
        return !levelRewardClaimed;
    }

    public void markRewardClaimed() {
        this.levelRewardClaimed = true;
    }

    public void addSteps(long stepCount) {
        this.totalSteps += stepCount;
    }

    public void addConvertedPoints(int points) {
        this.convertedPoints += points;
    }

    // 교환 횟수 증가
    public void incrementExchangeCount() {
        this.exchangeCount++;
    }

    // 더 교환 가능한지 확인 (포인트 한도와 횟수 한도 모두 체크)
    public boolean canConvertMore() {
        return convertedPoints < 2000 && exchangeCount < 20; // 20회 제한 추가
    }

    public int getRemainingExchangeCount() {
        return Math.max(0, 20 - exchangeCount);
    }

    // 시작 보너스 적용
    public void applyStartBonus(long bonusSteps) {
        if (!startBonusApplied) {
            this.startBonusSteps = bonusSteps;
            this.totalSteps += bonusSteps;
            this.startBonusApplied = true;
        }
    }

    // 실제 걸은 걸음수 (보너스 제외)
    public long getActualSteps() {
        return totalSteps - startBonusSteps;
    }
}

