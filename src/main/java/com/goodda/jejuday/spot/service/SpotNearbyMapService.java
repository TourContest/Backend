package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.tourapi.service.SpotTourSyncService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotNearbyMapService {
    private static final int MIN_OFFICIAL_SPOTS = 10;
    private static final int MAX_RESULTS = 100;
    private final SpotRepository spotRepository;
    private final SpotTourSyncService tourSyncService;

    public List<Spot> find(BigDecimal latitude, BigDecimal longitude, int requestedRadiusKm) {
        int radiusKm = Math.max(1, Math.min(5, requestedRadiusKm));
        List<Spot> local = activeMapSpots(latitude, longitude, radiusKm);
        long official = local.stream().filter(s -> s.getType() == Spot.SpotType.SPOT && !s.isUserCreated()).count();
        if (official < MIN_OFFICIAL_SPOTS) {
            try {
                tourSyncService.cacheAround(latitude, longitude, radiusKm * 1_000, MAX_RESULTS);
                local = activeMapSpots(latitude, longitude, radiusKm);
            } catch (Exception e) {
                log.warn("지도 주변 TourAPI 캐시 실패, 기존 데이터 반환: {}", e.toString());
            }
        }
        return local.stream()
                .sorted(Comparator.comparingDouble(s -> distance(latitude, longitude, s.getLatitude(), s.getLongitude())))
                .limit(MAX_RESULTS).toList();
    }

    private List<Spot> activeMapSpots(BigDecimal lat, BigDecimal lng, int radiusKm) {
        return spotRepository.findWithinRadius(lat, lng, radiusKm).stream()
                .filter(s -> s.getType() == Spot.SpotType.SPOT || s.getType() == Spot.SpotType.CHALLENGE)
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted())).toList();
    }

    private static double distance(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double y = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double x = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(y / 2) * Math.sin(y / 2) + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue())) * Math.sin(x / 2) * Math.sin(x / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
