package com.goodda.jejuday.spot.tourapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.SpotDetail;
import com.goodda.jejuday.spot.repository.SpotDetailRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.tourapi.TourApiClient;
import com.goodda.jejuday.spot.tourapi.dto.TourDetailCommon;
import com.goodda.jejuday.spot.tourapi.dto.TourDetailIntro;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** name/categoryName만으로는 임베딩 입력 텍스트가 빈약해서, TourAPI 상세 엔드포인트(개요/이용시간/이미지)로 Spot을 보강한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotDetailSyncService {

    private static final String DEFAULT_CONTENT_TYPE_ID = "12"; // 관광지 (contentTypeId 미상일 때 기본값)

    private final TourApiClient client;
    private final SpotRepository spotRepository;
    private final SpotDetailRepository spotDetailRepository;
    private final ObjectMapper objectMapper;

    /**
     * 스팟당 외부 API를 3번 호출하므로 배치 전체를 하나의 트랜잭션으로 묶지 않는다 -
     * 묶으면 느린 외부 호출들이 DB 트랜잭션을 오래 붙잡고 있다가 타임아웃으로 전체가 실패한다.
     * repository 저장 호출 각각이 자체 트랜잭션을 갖게 둔다.
     */
    public Result syncAllMissing(int limit) {
        List<Spot> targets = spotRepository.findMissingDetailSpots(PageRequest.of(0, limit));
        int updated = 0, failed = 0;
        for (Spot spot : targets) {
            try {
                syncOne(spot);
                updated++;
            } catch (Exception e) {
                failed++;
                log.warn("SpotDetail 동기화 실패 spotId={}, externalPlaceId={}: {}",
                        spot.getId(), spot.getExternalPlaceId(), e.toString());
            }
        }
        return new Result(targets.size(), updated, failed);
    }

    private void syncOne(Spot spot) throws Exception {
        String contentId = spot.getExternalPlaceId();
        String contentTypeId = (spot.getContentTypeId() != null) ? spot.getContentTypeId() : DEFAULT_CONTENT_TYPE_ID;

        TourDetailCommon common = client.detailCommon(contentId);
        TourDetailIntro intro = client.detailIntro(contentId, contentTypeId);
        List<String> images = client.detailImage(contentId);

        SpotDetail detail = spotDetailRepository.findBySpotId(spot.getId()).orElseGet(SpotDetail::new);
        detail.setSpotId(spot.getId());
        detail.setOverview(stripHtml(common.overview()));
        detail.setHomepage(stripHtml(common.homepage()));
        detail.setUseTime(intro.useTime());
        detail.setRestDate(intro.restDate());
        detail.setParking(intro.parking());
        detail.setExtraImagesJson(toJson(images));
        detail.setSyncedAt(LocalDateTime.now());

        spotDetailRepository.save(detail);
    }

    private String stripHtml(String text) {
        if (text == null || text.isBlank()) return null;
        return Jsoup.parse(text).text();
    }

    private String toJson(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            return "[]";
        }
    }

    public record Result(int processed, int updated, int failed) {}
}
