package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** 전국 어디서 앱을 열어도 커뮤니티/주변 장소 화면을 확인할 수 있게 하는 데모 데이터. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "demo-spots", havingValue = "true")
@RequiredArgsConstructor
public class NationwideCommunitySeedRunner {

    static final String COMMUNITY_MARKER_PREFIX = "SEED:nationwide-community-v2-";
    static final String SPOT_MARKER_PREFIX = "SEED:nationwide-spot-v2-";
    static final int DEFAULT_ITEMS_PER_REGION = 20;
    static final int CAPITAL_ITEMS_PER_REGION = 100;
    private static final long RANDOM_SEED = 20260818L;

    private final SpotRepository spotRepository;
    private final UserRepository userRepository;

    record Region(String name, double latitude, double longitude, boolean capitalArea) {}
    record PostSeed(String region, String name, String title, String description,
                    String tag1, String tag2, String tag3,
                    BigDecimal latitude, BigDecimal longitude, int likeCount, int viewCount) {}

    // 특별시·광역시·특별자치시·도의 생활권 중심 좌표. 각 좌표 주변 약 6km에 글을 분산한다.
    private static final List<Region> REGIONS = List.of(
            new Region("서울", 37.5665, 126.9780, true), new Region("부산", 35.1796, 129.0756, false),
            new Region("대구", 35.8714, 128.6014, false), new Region("인천", 37.4563, 126.7052, true),
            new Region("광주", 35.1595, 126.8526, false), new Region("대전", 36.3504, 127.3845, false),
            new Region("울산", 35.5395, 129.3114, false), new Region("세종", 36.4800, 127.2890, false),
            new Region("경기", 37.2636, 127.0286, true), new Region("강원 춘천", 37.8813, 127.7298, false),
            new Region("충북 청주", 36.6424, 127.4890, false), new Region("충남 천안", 36.8151, 127.1139, false),
            new Region("전북 전주", 35.8242, 127.1480, false), new Region("전남 순천", 34.9506, 127.4872, false),
            new Region("경북 포항", 36.0190, 129.3435, false), new Region("경남 창원", 35.2279, 128.6811, false),
            new Region("제주", 33.4996, 126.5312, false)
    );

    private static final String[] PLACE_TYPES = {"산책길", "카페", "공원", "맛집", "전망대", "시장", "문화공간", "포토존"};
    private static final String[] MOODS = {"힐링", "데이트", "가족여행", "혼자여행", "주말나들이", "로컬추천"};
    private static final String[] DESCRIPTIONS = {
            "근처를 걷다가 발견했는데 잠깐 쉬어가기 좋았어요.",
            "사람이 너무 붐비지 않고 사진도 잘 나오는 곳이에요.",
            "주말에 가볍게 들르기 좋아서 주변 분들께 추천해요.",
            "동네 분위기를 느끼며 천천히 둘러보기 좋았습니다.",
            "친구와 방문했는데 다음에도 다시 오고 싶은 장소예요.",
            "해 질 무렵 풍경이 특히 좋아서 여유 있게 방문해 보세요."
    };

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public void seed() {
        boolean communityExists = spotRepository.existsByExternalPlaceId(COMMUNITY_MARKER_PREFIX + "1");
        boolean mapSpotsExist = spotRepository.existsByExternalPlaceId(SPOT_MARKER_PREFIX + "1");
        if (communityExists && mapSpotsExist) {
            return;
        }

        List<User> users = userRepository.findAll(PageRequest.of(0, 10)).getContent();
        if (users.isEmpty()) {
            log.warn("전국 커뮤니티 시드: 작성자로 쓸 유저가 없어 시딩을 건너뜁니다.");
            return;
        }

        List<PostSeed> seeds = generateSeeds();
        List<Spot> spots = new ArrayList<>(seeds.size() * 2);
        if (!communityExists) {
            addSpots(spots, seeds, users, Spot.SpotType.POST, true, COMMUNITY_MARKER_PREFIX);
        }
        if (!mapSpotsExist) {
            addSpots(spots, seeds, users, Spot.SpotType.SPOT, false, SPOT_MARKER_PREFIX);
        }

        spotRepository.saveAll(spots);
        log.info("전국 초기 시드: {}개 지역에 커뮤니티/지도용 데이터 총 {}개 생성 완료", REGIONS.size(), spots.size());
    }

    private static void addSpots(List<Spot> target, List<PostSeed> seeds, List<User> users,
                                 Spot.SpotType type, boolean userCreated, String markerPrefix) {
        for (int i = 0; i < seeds.size(); i++) {
            PostSeed seed = seeds.get(i);
            Spot spot = new Spot(seed.name(), seed.description(), seed.latitude(), seed.longitude(), users.get(i % users.size()));
            spot.setType(type);
            spot.setTitle(userCreated ? seed.title() : null);
            spot.setTag1(seed.tag1());
            spot.setTag2(seed.tag2());
            spot.setTag3(seed.tag3());
            spot.setCategoryGroupCode("SEED");
            spot.setCategoryGroupName("테스트 장소");
            spot.setCategoryName(seed.tag2());
            spot.setLikeCount(seed.likeCount());
            spot.setViewCount(seed.viewCount());
            spot.setUserCreated(userCreated);
            spot.setIsDeleted(false);
            spot.setExternalPlaceId(markerPrefix + (i + 1));
            target.add(spot);
        }
    }

    static List<PostSeed> generateSeeds() {
        SplittableRandom random = new SplittableRandom(RANDOM_SEED);
        List<PostSeed> result = new ArrayList<>(580);
        for (Region region : REGIONS) {
            int itemCount = region.capitalArea() ? CAPITAL_ITEMS_PER_REGION : DEFAULT_ITEMS_PER_REGION;
            for (int number = 1; number <= itemCount; number++) {
                String placeType = PLACE_TYPES[random.nextInt(PLACE_TYPES.length)];
                String mood = MOODS[random.nextInt(MOODS.length)];
                // 위도/경도에 동일한 값을 더하지 않아 점들이 대각선으로 늘어서지 않게 한다.
                BigDecimal latitude = coordinate(region.latitude() + random.nextDouble(-0.055, 0.055));
                BigDecimal longitude = coordinate(region.longitude() + random.nextDouble(-0.065, 0.065));
                String suffix = String.format("%02d", number);
                result.add(new PostSeed(
                        region.name(), region.name() + " " + placeType + " " + suffix,
                        region.name() + "에서 찾은 " + mood + " " + placeType,
                        DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)],
                        region.name(), placeType, mood, latitude, longitude,
                        random.nextInt(3, 151), random.nextInt(30, 1501)
                ));
            }
        }
        return List.copyOf(result);
    }

    private static BigDecimal coordinate(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
