package com.goodda.jejuday.spot.tourapi.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.service.SystemUserProvider;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.tourapi.SpotTourRepository;
import com.goodda.jejuday.spot.tourapi.TourApiClient;
import com.goodda.jejuday.spot.tourapi.TourApiProperties;
import com.goodda.jejuday.spot.tourapi.dto.TourApiPage;
import com.goodda.jejuday.spot.tourapi.dto.TourItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.goodda.jejuday.spot.service.SpotSearchService;

@Service
@RequiredArgsConstructor
public class SpotTourSyncService {

    private final TourApiClient client;
    private final SpotTourRepository repo;
    private final TourApiProperties props;   // systemUserId 읽기용
    private final UserRepository userRepository;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String PROVIDER_PREFIX = "KTO:"; // externalPlaceId = "KTO:<contentid>"
    private final SystemUserProvider systemUserProvider;
    private final SpotSearchService spotSearchService;

    private static final long SYSTEM_USER_ID = 10L;

    @jakarta.annotation.PostConstruct
    void checkProps() {
        System.out.println(">>> baseUrl=" + props.getBaseUrl()
                + ", korServicePath=" + props.getKorServicePath()
                + ", systemUserId=" + props.getSystemUserId());
    }

    @Transactional
    public Result initialImport(String arrange, String areaCode, String lDongRegnCd, String lDongSignguCd, int rows) {
        int page = 1, imported=0, updated=0, skipped=0, pages=0, total=0;
        for (;;) {
            TourApiPage p = client.areaBasedList(page, rows, arrange, areaCode, lDongRegnCd, lDongSignguCd);
            pages++; total = p.getTotalCount();
            if (p.getItems().isEmpty()) break;

            for (TourItem it : p.getItems()) {
                Upsert u = upsert(it);
                imported += u.i; updated += u.u; skipped += u.s;
            }
            if (page * rows >= total) break;
            page++;
        }
        return new Result(imported, updated, skipped, pages, total, null);
    }

    @Transactional
    public Result syncSince(String sinceYmd, String arrange, String areaCode, String lDongRegnCd, String lDongSignguCd, int rows) {
        int page = 1, imported=0, updated=0, skipped=0, pages=0, total=0;
        String last = null;
        for (;;) {
            TourApiPage p = client.areaBasedSyncList(sinceYmd, page, rows, arrange, areaCode, lDongRegnCd, lDongSignguCd, last);
            pages++; total = p.getTotalCount();
            if (p.getItems().isEmpty()) break;

            for (TourItem it : p.getItems()) {
                last = it.getContentid();
                Upsert u = upsert(it);
                imported += u.i; updated += u.u; skipped += u.s;
            }
            if (page * rows >= total) break;
            page++;
        }
        return new Result(imported, updated, skipped, pages, total, sinceYmd);
    }

    /** 위치 기반 조회 결과를 로컬 관광지 캐시에 upsert한다. */
    @Transactional
    public Result cacheAround(BigDecimal latitude, BigDecimal longitude, int radiusMeters, int rows) {
        TourApiPage page = client.locationBasedList(1, rows, "E", longitude.toPlainString(),
                latitude.toPlainString(), radiusMeters);
        int imported = 0, updated = 0, skipped = 0;
        for (TourItem item : page.getItems()) {
            Upsert result = upsert(item);
            imported += result.i; updated += result.u; skipped += result.s;
        }
        return new Result(imported, updated, skipped, 1, page.getTotalCount(), null);
    }

    /** 외부 ID 기준 upsert (항상 최신 값으로 갱신) */
    private Upsert upsert(TourItem it) {
        String externalId = it.getContentid();
        Spot s = repo.findByExternalPlaceId(externalId).orElse(null);

        if (s == null) {
            s = new Spot();
            mapSpot(s, it, externalId);   // 여기서 setSystemUser(s) 호출
            repo.save(s);
            spotSearchService.indexSpot(s);
            return Upsert.insert();
        } else {
            mapSpot(s, it, externalId);
            if (getUserId(s) == null) setSystemUser(s); // 보정
            spotSearchService.indexSpot(s);
            return Upsert.update();
        }
    }

    /** Spot 엔티티에 '있는' 속성만 안전하게 반영 */
    private void mapSpot(Spot s, TourItem t, String externalId) {
        // 외부 ID
        try {
            s.setType(Spot.SpotType.SPOT);
        } catch (Throwable ignore) {
            // setter 시그니처가 다르거나 String 컬럼이면 여기로
            writeIfPresent(s, Spot.SpotType.SPOT, "type"); // enum 그대로 시도
            writeIfPresent(s, "SPOT", "type");             // 문자열로도 시도
        }

        // 시스템 계정 귀속 + 사용자 작성 아님
        setSystemUser(s);
        writeIfPresent(s, false, "userCreated", "isUserCreated");

        // 외부 ID
        writeIfPresent(s, externalId, "externalPlaceId");

        // 이름
        writeIfPresent(s, t.getTitle(), "name");

        // 좌표
        BigDecimal lat = toBigDecimal(t.getMapy());
        BigDecimal lon = toBigDecimal(t.getMapx());
        if (lat != null) writeIfPresent(s, lat, "latitude");
        if (lon != null) writeIfPresent(s, lon, "longitude");

        // 카테고리 코드
        if (t.getCat1() != null) writeIfPresent(s, t.getCat1(), "categoryGroupCode");
        else if (t.getLclsSystm1() != null) writeIfPresent(s, t.getLclsSystm1(), "categoryGroupCode");
        if (t.getCat3() != null) writeIfPresent(s, t.getCat3(), "categoryName");

        // contentTypeId (detailIntro2 재호출 시 필요)
        if (t.getContenttypeid() != null) writeIfPresent(s, t.getContenttypeid(), "contentTypeId");
        if (t.getAreacode() != null) writeIfPresent(s, t.getAreacode(), "areaCode");
        if (t.getSigungucode() != null) writeIfPresent(s, t.getSigungucode(), "sigunguCode");
        if (t.getAddr1() != null) writeIfPresent(s, t.getAddr1(), "address");

        // 이미지
        writeIfPresent(s, t.getFirstimage(),  "img1");
        writeIfPresent(s, t.getFirstimage2(), "img2");
    }

    // --------- 유틸 ---------
    private static BigDecimal toBigDecimal(String s) {
        try { return (s==null || s.isBlank()) ? null : new BigDecimal(s); }
        catch (Exception e) { return null; }
    }
    private static LocalDateTime parseDateTime(String s){
        try { return s==null?null:LocalDateTime.parse(s, DT); }
        catch(Exception e){ return null; }
    }

    private void writeIfPresent(Object target, Object value, String... candidates) {
        if (target == null || value == null) return;
        BeanWrapper w = new BeanWrapperImpl(target);
        for (String n : candidates) {
            if (w.isWritableProperty(n)) {
                try { w.setPropertyValue(n, value); return; } catch (Exception ignore) {}
            }
        }
    }
    private void setSystemUser(Spot s) {
        Long sysUserId = props.getSystemUserId(); // application.yml: tourapi.system-user-id: 10
        if (sysUserId == null) {
            throw new IllegalStateException("tourapi.system-user-id 가 설정되어야 합니다.");
        }
        var userOpt = userRepository.findById(sysUserId);
        if (userOpt.isEmpty()) {
            throw new IllegalStateException("SYSTEM user가 없음: users.user_id=" + sysUserId
                    + "  (먼저 users 테이블에 생성하세요)");
        }
        // 연관/스칼라 둘 다 커버
        var w = new org.springframework.beans.BeanWrapperImpl(s);
        if (w.isWritableProperty("user")) {
            w.setPropertyValue("user", userOpt.get());
        } else if (w.isWritableProperty("userId")) {
            w.setPropertyValue("userId", sysUserId);
        } else {
            throw new IllegalStateException("Spot에 user 또는 userId 필드가 필요합니다.");
        }

        // 사용자 작성 아님
        if (w.isWritableProperty("userCreated")) w.setPropertyValue("userCreated", false);
        if (w.isWritableProperty("isUserCreated")) w.setPropertyValue("isUserCreated", false);
    }
    private Long getUserId(Spot s) {
        BeanWrapper w = new BeanWrapperImpl(s);
        if (w.isReadableProperty("userId")) {
            try { return (Long) w.getPropertyValue("userId"); } catch (Exception ignore) {}
        }
        if (w.isReadableProperty("user")) {
            try {
                Object u = w.getPropertyValue("user");
                if (u == null) return null;
                BeanWrapper uw = new BeanWrapperImpl(u);
                if (uw.isReadableProperty("userId")) {
                    return (Long) uw.getPropertyValue("userId");
                }
            } catch (Exception ignore) {}
        }
        return null;
    }
    public record Result(int imported, int updated, int skipped, int pages, int total, String since) {}
    private record Upsert(int i, int u, int s) {
        static Upsert insert(){ return new Upsert(1,0,0); }
        static Upsert update(){ return new Upsert(0,1,0); }
        static Upsert skip(){ return new Upsert(0,0,1); }
    }
}
