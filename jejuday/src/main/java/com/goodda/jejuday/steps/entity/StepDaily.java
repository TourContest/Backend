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

    public boolean canConvertMore() {
        return convertedPoints < 2000;
    }
}

