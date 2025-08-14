package com.goodda.jejuday.steps.entity;

public enum MoodGrade {
    BALBADAK(0),    //  발바닥
    DDUBUCKI(50),   //  뚜벅이
    OREUMKKUN(50),  //  오름꾼
    HALLAMAN(50),   //  한라맨
    TAMLAWANG(50);  //  탐라왕

    private final int reward;

    MoodGrade(int reward) {
        this.reward = reward;
    }

    public int getReward() {
        return reward;
    }

    public static MoodGrade fromSteps(long totalSteps) {
        if (totalSteps < 20_000) return BALBADAK;
        else if (totalSteps < 40_000) return DDUBUCKI;
        else if (totalSteps < 60_000) return OREUMKKUN;
        else if (totalSteps < 80_000) return HALLAMAN;
        else return TAMLAWANG;
    }
}

