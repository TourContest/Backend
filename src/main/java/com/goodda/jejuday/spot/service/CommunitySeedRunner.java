package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 커뮤니티 피드/검색 데모용 샘플 글(POST) 시딩.
 *
 * <p>커뮤니티 화면(피드+검색)이 관광공사 데이터를 제외하고 유저 작성 글만 보여주도록
 * 바뀌면서, 실제 유저 글이 적은 환경에서는 화면이 텅 비게 된다. 그걸 채우기 위한
 * 데모/초기 콘텐츠. 기존 유저(가장 먼저 등록된 계정)를 작성자로 재사용하고,
 * externalPlaceId에 "SEED:community-*" 마커를 남겨 재배포 시 중복 생성을 막는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "demo-spots", havingValue = "true")
@RequiredArgsConstructor
public class CommunitySeedRunner {

    private final SpotRepository spotRepository;
    private final UserRepository userRepository;

    private record PostSeed(String name, String title, String description,
                             String tag1, String tag2, String tag3,
                             double lat, double lng, int likeCount, int viewCount) {}

    private static final List<PostSeed> SEEDS = List.of(
            new PostSeed("애월 카페거리", "애월 바다 보면서 커피 한잔", "노을질 때 가면 진짜 예뻐요 다들 가보세요", "카페", "애월", "노을", 33.4633, 126.3306, 42, 310),
            new PostSeed("함덕 서우봉해변", "함덕 바다 색깔 미쳤다", "에메랄드빛 진짜 실화임 여름에 꼭 가세요", "바다", "함덕", "여행", 33.5433, 126.6698, 87, 654),
            new PostSeed("사려니숲길", "사려니숲길 산책하고 왔어요", "피톤치드 뿜뿜, 힐링 그 자체였습니다", "숲길", "힐링", "산책", 33.4126, 126.6764, 35, 220),
            new PostSeed("동문시장", "동문시장에서 흑돼지 먹었어요", "가성비 최고, 줄서서 먹을만함", "맛집", "시장", "흑돼지", 33.5138, 126.5262, 61, 402),
            new PostSeed("성산일출봉 근처", "성산일출봉 일출 보고옴", "새벽에 일어난 보람 있었습니다", "일출", "성산", "여행", 33.4587, 126.9425, 95, 730),
            new PostSeed("이호테우해변", "이호테우 목마등대 인생샷", "노을이랑 같이 찍으면 진짜 예뻐요", "인생샷", "해변", "노을", 33.5077, 126.4664, 53, 388),
            new PostSeed("우도 산호해수욕장", "우도 하루종일 놀다옴", "자전거 대여해서 한바퀴 돌았어요 강추", "우도", "자전거", "여행", 33.5054, 126.9526, 74, 511),
            new PostSeed("한라산 뷰 카페", "한라산 뷰 맛집 카페 발견", "여기 뷰 미쳤어요 사진 스팟 많음", "카페", "뷰맛집", "한라산", 33.3617, 126.5292, 29, 190),
            new PostSeed("협재해수욕장", "협재 바다 인생 뷰", "비양도 보이는 자리 찾아서 앉았어요", "협재", "바다", "인생샷", 33.3939, 126.2397, 68, 455),
            new PostSeed("한라산 어리목코스", "한라산 등반 완주 후기", "생각보다 힘들었지만 정상뷰가 다 보상해줌", "한라산", "등산", "완주", 33.3742, 126.5297, 44, 301),
            new PostSeed("도두동 무지개해안도로", "도두 무지개해안도로 야경", "공항 비행기 뜨는거랑 같이 찍으면 예술", "야경", "도두", "인생샷", 33.5019, 126.4931, 39, 260),
            new PostSeed("김녕해수욕장", "김녕 바다색 실화냐", "여기 진짜 물색 미쳤음 스노클링도 가능", "김녕", "바다", "스노클링", 33.5578, 126.7592, 57, 402),
            new PostSeed("오설록티뮤지엄", "오설록에서 녹차아이스크림", "녹차밭 산책하기 좋아요 사진도 잘나옴", "오설록", "녹차", "카페", 33.3051, 126.2895, 31, 205),
            new PostSeed("새별오름", "새별오름 억새 보고왔어요", "가을에 가면 진짜 예술입니다 강추", "오름", "억새", "가을", 33.3467, 126.3559, 48, 330),
            new PostSeed("곽지해수욕장", "곽지 과물해변 용천수", "차가운 용천수에 발 담그니 시원해요", "곽지", "해변", "용천수", 33.4517, 126.3086, 26, 175),
            new PostSeed("서귀포 매일올레시장", "서귀포 매일올레시장 먹부림", "감귤초콜릿이랑 오메기떡 꼭 드세요", "시장", "먹부림", "서귀포", 33.2496, 126.5642, 55, 390),
            new PostSeed("카멜리아힐", "카멜리아힐 동백꽃 보러감", "겨울에 동백꽃 만개하면 장관입니다", "동백꽃", "카멜리아힐", "겨울", 33.2871, 126.3757, 22, 140),
            new PostSeed("협재 오션뷰 카페", "협재 앞바다 오션뷰 카페", "커피값 아깝지 않은 뷰였습니다", "카페", "오션뷰", "협재", 33.3941, 126.2401, 33, 210),
            new PostSeed("산굼부리", "산굼부리 분화구 산책", "억새랑 분화구 조합이 진짜 신비로워요", "산굼부리", "분화구", "자연", 33.4436, 126.6600, 19, 130),
            new PostSeed("표선해수욕장", "표선 바다 물빠지면 진짜 넓어짐", "썰물 때 가면 백사장이 끝도 없어요", "표선", "해변", "썰물", 33.3255, 126.8335, 41, 280),
            new PostSeed("세화해변 카페거리", "세화 바다 보이는 카페", "여기 앉아서 하루종일 있고 싶었어요", "세화", "카페", "바다뷰", 33.5262, 126.8564, 27, 176),
            new PostSeed("정방폭포", "정방폭포 바다로 떨어지는 폭포", "국내에 몇 없는 해안폭포라던데 신기했어요", "정방폭포", "폭포", "서귀포", 33.2436, 126.5714, 36, 240),
            new PostSeed("애월 한담해안산책로", "한담 산책로 걷고 왔어요", "노을 시간대에 걸으면 최고입니다", "한담", "산책로", "노을", 33.4614, 126.3095, 45, 310),
            new PostSeed("성산 광치기해변", "광치기해변 물빠지면 신세계", "일출봉 배경으로 사진 찍기 최고 스팟", "광치기", "성산", "인생샷", 33.4368, 126.9269, 63, 420)
    );

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        String firstMarker = "SEED:community-1";
        if (spotRepository.existsByExternalPlaceId(firstMarker)) {
            return; // 이미 시딩됨
        }

        Optional<User> authorOpt = userRepository.findAll(PageRequest.of(0, 1)).getContent().stream().findFirst();
        if (authorOpt.isEmpty()) {
            log.warn("커뮤니티 시드: 작성자로 쓸 유저가 없어 시딩을 건너뜁니다.");
            return;
        }
        User author = authorOpt.get();

        int i = 1;
        for (PostSeed seed : SEEDS) {
            Spot s = new Spot(seed.name(), seed.description(), BigDecimal.valueOf(seed.lat()), BigDecimal.valueOf(seed.lng()), author);
            s.setTitle(seed.title());
            s.setTag1(seed.tag1());
            s.setTag2(seed.tag2());
            s.setTag3(seed.tag3());
            s.setLikeCount(seed.likeCount());
            s.setViewCount(seed.viewCount());
            s.setUserCreated(true);
            s.setIsDeleted(false);
            s.setExternalPlaceId("SEED:community-" + i);
            spotRepository.save(s);
            i++;
        }

        log.info("커뮤니티 시드: 샘플 글 {}개 생성 완료 (작성자: userId={})", SEEDS.size(), author.getId());
    }
}
