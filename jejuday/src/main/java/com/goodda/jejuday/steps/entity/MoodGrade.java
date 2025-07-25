package com.goodda.jejuday.steps.entity;

public enum MoodGrade {
    VERY_SAD(0),
    SAD(50),
    NORMAL(100),
    HAPPY(200),
    VERY_HAPPY(300);

    private final int reward;

    MoodGrade(int reward) {
        this.reward = reward;
    }

    public int getReward() {
        return reward;
    }

    public static MoodGrade fromSteps(long totalSteps) {
        if (totalSteps < 10_000) return VERY_SAD;
        else if (totalSteps < 50_000) return SAD;
        else if (totalSteps < 100_000) return NORMAL;
        else if (totalSteps < 300_000) return HAPPY;
        else return VERY_HAPPY;
    }
}

