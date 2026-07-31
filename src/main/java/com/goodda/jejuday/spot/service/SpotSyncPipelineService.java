package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.openai.service.SpotEmbeddingBatchService;
import com.goodda.jejuday.spot.ranking.SpotCongestionService;
import com.goodda.jejuday.spot.tourapi.service.SpotDetailSyncService;
import com.goodda.jejuday.spot.tourapi.service.SpotTourSyncService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * TourAPI 변경분 동기화 → 상세정보 보강 → 임베딩 생성을 순서대로 실행하는 파이프라인.
 * 각 단계는 이전 단계가 채운 데이터에 의존한다(예: 임베딩은 상세 overview가 있어야 품질이 좋음)
 * 순서를 바꾸지 않는다. 최초 전체 적재(TourApiController#importAll)는 일회성 작업이라 이 파이프라인에는
 * 포함하지 않는다 - 매일 전체를 다시 긁으면 낭비이기 때문.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotSyncPipelineService {

    private static final String DEFAULT_AREA_CODE = "39"; // 제주
    private static final String DEFAULT_ARRANGE = "C"; // 수정일순
    private static final int SYNC_ROWS = 100;
    private static final int DETAIL_SYNC_LIMIT = 100;
    private static final int EMBEDDING_SYNC_LIMIT = 200;

    private final SpotTourSyncService tourSyncService;
    private final SpotDetailSyncService detailSyncService;
    private final SpotEmbeddingBatchService embeddingBatchService;
    private final SpotCongestionService congestionService;

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시 실행
    @SchedulerLock(name = "spotSyncPipeline", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void runPeriodically() {
        PipelineResult result = runPipeline();
        log.info("스팟 동기화 파이프라인 완료: {}", result);
    }

    /** 관리자 수동 트리거 및 스케줄러가 공용으로 호출하는 파이프라인 본체. */
    public PipelineResult runPipeline() {
        String sinceYmd = LocalDate.now().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);

        SpotTourSyncService.Result syncResult =
                tourSyncService.syncSince(sinceYmd, DEFAULT_ARRANGE, DEFAULT_AREA_CODE, null, null, SYNC_ROWS);
        SpotDetailSyncService.Result detailResult = detailSyncService.syncAllMissing(DETAIL_SYNC_LIMIT);
        SpotEmbeddingBatchService.Result embeddingResult = embeddingBatchService.embedMissingOrStale(EMBEDDING_SYNC_LIMIT);
        int congestionUpdated = congestionService.recalculateAll();

        return new PipelineResult(syncResult, detailResult, embeddingResult, congestionUpdated);
    }

    public record PipelineResult(
            SpotTourSyncService.Result sync,
            SpotDetailSyncService.Result detailSync,
            SpotEmbeddingBatchService.Result embeddingSync,
            int congestionUpdated
    ) {}
}
