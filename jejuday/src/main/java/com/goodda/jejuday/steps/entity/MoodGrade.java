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

//    /**
//     * 굿즈 구매 자격이 있는지 확인
//     * @return 4만보(OREUMKKUN) 이상이면 true
//     */
//    public boolean canPurchaseGoods() {
//        return this == OREUMKKUN || this == HALLAMAN || this == TAMLAWANG;
//    }

    /**
     * 등급명 반환 (한글)
     */
    public String getDisplayName() {
        return switch (this) {
            case BALBADAK -> "발바닥";
            case DDUBUCKI -> "뚜벅이";
            case OREUMKKUN -> "오름꾼";
            case HALLAMAN -> "한라맨";
            case TAMLAWANG -> "탐라왕";
        };
    }

//    /**
//     * 굿즈 구매까지 필요한 걸음수 계산
//     * @param currentSteps 현재 총 걸음수
//     * @return 굿즈 구매까지 필요한 걸음수 (이미 자격이 있으면 0)
//     */
//    public static long getStepsNeededForGoods(long currentSteps) {
//        if (currentSteps >= 40_000) {
//            return 0; // 이미 자격 있음
//        }
//        return 40_000 - currentSteps;
//    }

    /**
     * 해당 등급의 최소 걸음수 반환
     */
    public long getMinSteps() {
        return switch (this) {
            case BALBADAK -> 0;
            case DDUBUCKI -> 20_000;
            case OREUMKKUN -> 40_000;
            case HALLAMAN -> 60_000;
            case TAMLAWANG -> 80_000;
        };
    }
}