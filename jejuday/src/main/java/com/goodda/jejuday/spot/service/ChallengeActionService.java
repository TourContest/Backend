package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.entity.ChallengeParticipation;
import com.goodda.jejuday.spot.entity.ChallengeParticipation.Status;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.ChallengeParticipationRepository;
import com.goodda.jejuday.spot.repository.ChallengeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChallengeActionService {

    private static final double EARTH_RADIUS_M = 6371000.0;
    private static final double COMPLETE_THRESHOLD_METERS = 100.0;

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipationRepository cpRepository;
    private final SecurityUtil securityUtil;

    @Transactional
    public ChallengeStartResponse start(Long challengeId, ChallengeStartRequest req) {
        User me = securityUtil.getAuthenticatedUser();
        Spot spot = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new EntityNotFoundException("Challenge not found"));

        if (spot.getType() != Spot.SpotType.CHALLENGE || Boolean.TRUE.equals(spot.getIsDeleted())) {
            throw new IllegalArgumentException("유효한 챌린지가 아닙니다.");
        }

        // 같은 테마 동시 진행 금지(다른 챌린지에 대해 진행중인 경우)
        Long themeId = (spot.getTheme() != null) ? spot.getTheme().getId() : null;
        if (themeId != null) {
            List<Status> ongoing = Arrays.asList(Status.JOINED, Status.SUBMITTED, Status.APPROVED);
            List<Long> ongoingThemeIds = cpRepository.findOngoingThemeIds(me.getId(), ongoing);
            boolean blocked = ongoingThemeIds.stream().anyMatch(id -> id.equals(themeId));
            if (blocked && !cpRepository.existsByChallenge_IdAndUser_Id(challengeId, me.getId())) {
                throw new IllegalStateException("해당 테마의 챌린지가 진행중입니다.");
            }
        }

        // 멱등: 이미 참여중이면 그대로 반환
        ChallengeParticipation cp = cpRepository
                .findByChallenge_IdAndUser_Id(challengeId, me.getId())
                .orElse(null);
        if (cp == null) {
            cp = new ChallengeParticipation();
            cp.setChallenge(spot);
            cp.setUser(me);
            cp.setStatus(Status.JOINED);
            cp.setStartDate(LocalDate.now());
            cpRepository.save(cp);
        } else if (cp.getStatus() == Status.COMPLETED || cp.getStatus() == Status.CANCELLED || cp.getStatus() == Status.REJECTED) {
            // 완료/취소/거절 상태면 새로 시작 금지(정책상 허용하려면 새 기록을 생성하는 별도 로직 필요)
            throw new IllegalStateException("이미 종료된 참여입니다.");
        }

        // 현재 위치 → 목표지점 거리 계산(저장은 선택)
        double dist = distanceMeters(req.getLatitude(), req.getLongitude(), spot.getLatitude(), spot.getLongitude());

        return new ChallengeStartResponse(
                spot.getId(),
                spot.getLatitude(),
                spot.getLongitude(),
                dist,
                cp.getStatus().name()
        );
    }

    @Transactional
    public ChallengeCompleteResponse complete(Long challengeId, ChallengeCompleteRequest req) {
        User me = securityUtil.getAuthenticatedUser();
        Spot spot = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new EntityNotFoundException("Challenge not found"));

        ChallengeParticipation cp = cpRepository.findByChallenge_IdAndUser_Id(challengeId, me.getId())
                .orElseThrow(() -> new EntityNotFoundException("참여 이력이 없습니다."));

        // 진행중 상태만 완료 가능
        if (!(cp.getStatus() == Status.JOINED || cp.getStatus() == Status.SUBMITTED || cp.getStatus() == Status.APPROVED)) {
            throw new IllegalStateException("완료할 수 없는 상태입니다: " + cp.getStatus());
        }

        // 위치 근접성 검사
        double dist = distanceMeters(req.getLatitude(), req.getLongitude(), spot.getLatitude(), spot.getLongitude());
        boolean ok = dist <= COMPLETE_THRESHOLD_METERS;

        if (!ok) {
            // 프론트에서 막더라도 서버에서도 방어
            throw new IllegalStateException("목표 지점과의 거리가 너무 멉니다. (" + Math.round(dist) + "m)");
        }

        // 포인트 지급
        int award = (spot.getPoint() != null) ? spot.getPoint() : 0;
        me.setHallabong(me.getHallabong() + award);

        cp.setStatus(Status.COMPLETED);
        cp.setEndDate(LocalDate.now());
        cp.setCompletedAt(LocalDateTime.now());

        return new ChallengeCompleteResponse(
                spot.getId(),
                true,
                dist,
                award,
                me.getHallabong(),
                cp.getCompletedAt()
        );
    }

    // === util ===
    private static double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return Double.POSITIVE_INFINITY;
        return distanceMeters(lat1.doubleValue(), lon1.doubleValue(), lat2.doubleValue(), lon2.doubleValue());
    }

    private static double distanceMeters(double lat1, double lon1, BigDecimal lat2, BigDecimal lon2) {
        return distanceMeters(lat1, lon1, lat2.doubleValue(), lon2.doubleValue());
    }

    private static double distanceMeters(BigDecimal lat1, BigDecimal lon1, double lat2, double lon2) {
        return distanceMeters(lat1.doubleValue(), lon1.doubleValue(), lat2, lon2);
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return EARTH_RADIUS_M * c;
    }
}