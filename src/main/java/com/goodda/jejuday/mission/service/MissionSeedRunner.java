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
 * <p>DB 초기화/재수집 시 PK가 바뀔 수 있으므로 장소명을 기준으로 공식 TourAPI 스팟을 연결한다.
 * 과거 spotId는 이름까지 일치하는 경우에만 호환용 폴백으로 사용한다.
 *
 * <p>제목 기준으로 upsert한다 — 이미 있는 테마는 coverImageUrl 등 비어있는 필드만 보정하고
 * 스텝은 다시 만들지 않는다(중복 생성 방지). 없으면 새로 만든다. 이 방식 덕분에 시드 데이터를
 * 고쳐 재배포해도 안전하게 반영된다(예: coverImageUrl 뒤늦게 추가).
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
        seedTheme(
                "오름 5선",
                "제주의 대표 오름 다섯 곳을 방문 인증하고 완주 스탬프를 모아보세요.",
                "http://tong.visitkorea.or.kr/cms/resource/32/3528332_image2_1.jpg", // 아끈다랑쉬 오름
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
                "http://tong.visitkorea.or.kr/cms/resource/07/3384607_image2_1.jpg", // 제주해녀항일운동기념탑
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
                "http://tong.visitkorea.or.kr/cms/resource/38/2678438_image2_1.jpg", // 동문재래시장
                List.of(
                        new StepSeed(1100L, "동문재래시장"),
                        new StepSeed(820L, "서귀포매일올레시장"),
                        new StepSeed(1409L, "대정오일시장"),
                        new StepSeed(1332L, "표선오일시장")
                )
        );
    }

    private void seedTheme(String title, String description, String coverImageUrl, List<StepSeed> stepSeeds) {
        MissionTheme existing = themeRepository.findByTitle(title).orElse(null);
        if (existing != null) {
            boolean changed = false;
            if (existing.getCoverImageUrl() == null) {
                existing.setCoverImageUrl(coverImageUrl);
                changed = true;
            }
            if (existing.getCompletionRewardHallabong() != COMPLETION_REWARD) {
                existing.setCompletionRewardHallabong(COMPLETION_REWARD);
                changed = true;
            }
            if (changed) {
                themeRepository.save(existing);
                log.info("미션 시드: '{}' 테마 정보 보정 완료", title);
            }
            return;
        }

        List<StepSeed> resolvedSeeds = new ArrayList<>();
        List<Spot> resolvedSpots = new ArrayList<>();
        for (StepSeed s : stepSeeds) {
            resolveSpot(s).ifPresentOrElse(
                    spot -> {
                        resolvedSeeds.add(s);
                        resolvedSpots.add(spot);
                    },
                    () -> log.warn("미션 시드: 공식 관광지 '{}'를 찾을 수 없어 '{}' 테마에서 제외합니다.", s.label(), title)
            );
        }

        if (resolvedSpots.isEmpty()) {
            log.warn("미션 시드: '{}' 테마에 유효한 스팟이 하나도 없어 생성을 건너뜁니다.", title);
            return;
        }

        MissionTheme theme = new MissionTheme();
        theme.setTitle(title);
        theme.setDescription(description);
        theme.setCoverImageUrl(coverImageUrl);
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

    private java.util.Optional<Spot> resolveSpot(StepSeed seed) {
        java.util.Optional<Spot> byName = spotRepository
                .findFirstByNameIgnoreCaseAndUserCreatedFalse(seed.label());
        if (byName.isPresent()) return byName;

        return spotRepository.findById(seed.spotId())
                .filter(spot -> !spot.isUserCreated())
                .filter(spot -> spot.getName() != null && spot.getName().equalsIgnoreCase(seed.label()));
    }
}
