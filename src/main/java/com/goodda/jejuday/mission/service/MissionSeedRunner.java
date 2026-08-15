package com.goodda.jejuday.mission.service;

import com.goodda.jejuday.mission.entity.MissionStep;
import com.goodda.jejuday.mission.entity.MissionTheme;
import com.goodda.jejuday.mission.repository.MissionStepRepository;
import com.goodda.jejuday.mission.repository.MissionThemeRepository;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 테마 미션(스탬프 투어) MVP 초기 콘텐츠 시딩.
 *
 * <p>여기서 참조하는 spotId는 (제주) 실제 운영 DB에 존재하는 TourAPI 동기화 스팟들의 ID다.
 * 다른 환경(로컬/테스트)에는 없을 수 있으므로, 존재하지 않는 spotId는 건너뛰고 로그만 남긴다.
 * mission_theme 테이블이 비어있을 때만(최초 1회) 시딩한다 — 재배포 시 중복 생성 방지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionSeedRunner {

    private final MissionThemeRepository themeRepository;
    private final MissionStepRepository stepRepository;
    private final SpotRepository spotRepository;

    private static final int COMPLETION_REWARD = 1000;

    private record StepSeed(Long spotId, String label) {}

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (themeRepository.count() > 0) {
            return;
        }

        seedTheme(
                "오름 5선",
                "제주의 대표 오름 다섯 곳을 방문 인증하고 완주 스탬프를 모아보세요.",
                List.of(
                        new StepSeed(1050L, "아끈다랑쉬 오름"),
                        new StepSeed(1063L, "높은오름"),
                        new StepSeed(989L, "따라비 오름"),
                        new StepSeed(809L, "금오름"),
                        new StepSeed(853L, "물영아리오름")
                )
        );

        seedTheme(
                "해녀문화 탐방",
                "제주 해녀 문화를 직접 만나고 체험하는 코스입니다.",
                List.of(
                        new StepSeed(1462L, "제주해녀항일운동기념탑"),
                        new StepSeed(1394L, "성산포 해녀물질공연장"),
                        new StepSeed(995L, "해녀촌"),
                        new StepSeed(715L, "해녀잠수촌")
                )
        );

        seedTheme(
                "전통시장 투어",
                "제주의 정겨운 전통시장을 둘러보는 코스입니다.",
                List.of(
                        new StepSeed(1100L, "동문재래시장"),
                        new StepSeed(820L, "서귀포매일올레시장"),
                        new StepSeed(1409L, "대정오일시장"),
                        new StepSeed(1332L, "표선오일시장")
                )
        );
    }

    private void seedTheme(String title, String description, List<StepSeed> stepSeeds) {
        List<StepSeed> resolvedSeeds = new ArrayList<>();
        List<Spot> resolvedSpots = new ArrayList<>();
        for (StepSeed s : stepSeeds) {
            spotRepository.findById(s.spotId()).ifPresentOrElse(
                    spot -> {
                        resolvedSeeds.add(s);
                        resolvedSpots.add(spot);
                    },
                    () -> log.warn("미션 시드: spotId={} 를 찾을 수 없어 '{}' 테마에서 제외합니다.", s.spotId(), title)
            );
        }

        if (resolvedSpots.isEmpty()) {
            log.warn("미션 시드: '{}' 테마에 유효한 스팟이 하나도 없어 생성을 건너뜁니다.", title);
            return;
        }

        MissionTheme theme = new MissionTheme();
        theme.setTitle(title);
        theme.setDescription(description);
        theme.setTotalSteps(resolvedSpots.size());
        theme.setCompletionRewardHallabong(COMPLETION_REWARD);
        themeRepository.save(theme);

        for (int i = 0; i < resolvedSpots.size(); i++) {
            MissionStep step = new MissionStep();
            step.setMissionTheme(theme);
            step.setSpot(resolvedSpots.get(i));
            step.setStepOrder(i + 1);
            step.setStepLabel(resolvedSeeds.get(i).label());
            stepRepository.save(step);
        }

        log.info("미션 시드: '{}' 테마 생성 완료 (스텝 {}개, 완주 보상 {} 한라봉)",
                title, resolvedSpots.size(), COMPLETION_REWARD);
    }
}
