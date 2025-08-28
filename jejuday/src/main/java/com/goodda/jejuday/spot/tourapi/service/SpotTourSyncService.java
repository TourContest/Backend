package com.goodda.jejuday.spot.tourapi.service;

import com.goodda.jejuday.auth.entity.User;
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


@Service
@RequiredArgsConstructor
public class SpotTourSyncService {

    private final TourApiClient client;
    private final SpotTourRepository repo;
    private final TourApiProperties props;   // systemUserId 읽기용
    private final com.goodda.jejuday.auth.repository.UserRepository userRepository;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String PROVIDER_PREFIX = "KTO:"; // externalPlaceId = "KTO:<contentid>"

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

    /** 외부 ID로 업서트(Spot에 있는 필드만 갱신). modifiedTime 비교는 하지 않고 항상 최신 값으로 upsert */
    private Upsert upsert(TourItem it) {
        String externalId = PROVIDER_PREFIX + it.getContentid();

        Spot ex = repo.findByExternalPlaceId(externalId).orElse(null);
        if (ex == null) {
            Spot s = new Spot();
            mapSpot(s, it, externalId);
            repo.save(s);
            return Upsert.insert();
        } else {
            mapSpot(ex, it, externalId);
            return Upsert.update();
        }
    }

    /** Spot 엔티티에 '있는' 속성만 안전하게 반영 */
    private void mapSpot(Spot s, TourItem t, String externalId) {
        // 필수/기본
        s.setType(Spot.SpotType.SPOT);   // 인증된 콘텐츠 → SPOT
        s.setUserCreated(false);         // 사용자 작성 아님
        s.setExternalPlaceId(externalId);

        // 필수 관계: systemUserId를 가진 User 프록시 세팅 (DB에 해당 ID가 존재해야 함)
//        Long sysUserId = props.getSystemUserId();      // application.yml 로 주입
        Long sysUserId = props.getSystemUserId();
        if (sysUserId == null) {
            throw new IllegalStateException("tourapi.system-user-id 가 설정되어야 합니다.");
        }
        s.setUser(userRepository.getReferenceById(sysUserId));

        // 이름
        if (t.getTitle() != null) s.setName(t.getTitle());

        // 좌표: mapy=위도, mapx=경도 (BigDecimal 변환)
        BigDecimal lat = toBigDecimal(t.getMapy());
        BigDecimal lon = toBigDecimal(t.getMapx());
        if (lat != null) s.setLatitude(lat);
        if (lon != null) s.setLongitude(lon);

        // 카테고리 메타(코드만 존재. 이름은 null 유지 가능)
        // group_code: cat1(또는 lclsSystm1), name 계열은 API에 '이름'이 없어 null로 둡니다.
        if (t.getCat1() != null) s.setCategoryGroupCode(t.getCat1());
        else if (t.getLclsSystm1() != null) s.setCategoryGroupCode(t.getLclsSystm1());
        // 소분류명은 없어 code만 들어옵니다. 원하시면 code를 임시로 name에 그대로 저장할 수도 있습니다.
        if (t.getCat3() != null) s.setCategoryName(t.getCat3());

        // 이미지: Spot에 사진 필드가 있으면 넣고, 없으면 무시
        writeIfPresent(s, t.getFirstimage(),  "image1", "thumbnailUrl");
        writeIfPresent(s, t.getFirstimage2(), "image2", "imageUrl2");

        // 설명, 주소 등은 Spot에 필드가 없으면 생략 (필요 시 추가 매핑하세요)
    }

    // --------- 유틸 ---------
    private static BigDecimal toBigDecimal(String s) {
        try { return (s==null || s.isBlank()) ? null : new BigDecimal(s); }
        catch (Exception e) { return null; }
    }
    private static Long parseLong(String s){ try { return s==null?null:Long.parseLong(s);} catch(Exception e){ return null; } }
    private static Integer parseInt(String s){ try { return s==null?null:Integer.parseInt(s);} catch(Exception e){ return null; } }
    private static LocalDateTime parseDateTime(String s){ try { return s==null?null:LocalDateTime.parse(s, DT);} catch(Exception e){ return null; } }

    private void writeIfPresent(Object target, Object value, String... candidates) {
        if (target == null || value == null) return;
        BeanWrapper w = new BeanWrapperImpl(target);
        for (String n : candidates) {
            if (w.isWritableProperty(n)) {
                try { w.setPropertyValue(n, value); return; } catch (Exception ignore) {}
            }
        }
    }

    public record Result(int imported, int updated, int skipped, int pages, int total, String since) {}
    private record Upsert(int i, int u, int s) { static Upsert insert(){return new Upsert(1,0,0);} static Upsert update(){return new Upsert(0,1,0);} static Upsert skip(){return new Upsert(0,0,1);} }
}
