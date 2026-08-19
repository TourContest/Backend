package com.goodda.jejuday.spot.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.goodda.jejuday.common.ImageValidator;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.mission.dto.CompletedMissionResponse;
import com.goodda.jejuday.mission.service.MissionProgressService;
import com.goodda.jejuday.notification.service.NotificationFactory;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.pay.entity.LedgerReason;
import com.goodda.jejuday.pay.service.PointLedgerService;
import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.entity.ChallengeParticipation;
import com.goodda.jejuday.spot.entity.ChallengeParticipation.Status;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.ChallengeParticipationRepository;
import com.goodda.jejuday.spot.repository.ChallengeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChallengeActionService {

    private static final double EARTH_RADIUS_M = 6371000.0;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipationRepository cpRepository;
    private final SecurityUtil securityUtil;
    private final PointLedgerService pointLedgerService;
    private final UserRepository userRepository;
    private final AmazonS3 amazonS3;
    private final MissionProgressService missionProgressService;
    private final NotificationService notificationService;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Value("${challenge.spot-visit.default-point:300}")
    private int defaultSpotVisitPoint;

    @Value("${challenge.spot-visit.complete-radius-meters:500}")
    private double completeRadiusMeters;

    /** 모든 챌린지에 동일하게 적용되는 완료 보상. */
    private int resolveAwardPoint(Spot spot) {
        return defaultSpotVisitPoint;
    }

    @Transactional
    public ChallengeStartResponse start(Long challengeId, ChallengeStartRequest req) {
        User me = securityUtil.getAuthenticatedUser();
        Spot spot = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new EntityNotFoundException("Challenge not found"));

        boolean missionEligible = spot.getType() == Spot.SpotType.CHALLENGE || spot.getType() == Spot.SpotType.SPOT;
        if (!missionEligible || Boolean.TRUE.equals(spot.getIsDeleted())) {
            throw new IllegalArgumentException("유효한 챌린지/스팟이 아닙니다.");
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
        } else if (cp.getStatus() == Status.COMPLETED) {
            // 완료한 챌린지는 중복 보상 방지를 위해 다시 시작할 수 없다.
            throw new IllegalStateException("이미 완료한 참여입니다.");
        } else if (cp.getStatus() == Status.CANCELLED || cp.getStatus() == Status.REJECTED) {
            // 취소/거절된 참여는 기존 행을 초기화해 다시 시작한다.
            cp.setStatus(Status.JOINED);
            cp.setStartDate(LocalDate.now());
            cp.setEndDate(null);
            cp.setCompletedAt(null);
            cp.setProofUrl(null);
        }

        // 현재 위치 → 목표지점 거리 계산(저장은 선택)
        double dist = distanceMeters(req.getLatitude(), req.getLongitude(), spot.getLatitude(), spot.getLongitude());

        return new ChallengeStartResponse(
                spot.getId(),
                spot.getLatitude(),
                spot.getLongitude(),
                dist,
                cp.getStatus().name(),
                resolveAwardPoint(spot)
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
        boolean ok = dist <= completeRadiusMeters;

        if (!ok) {
            // 프론트에서 막더라도 서버에서도 방어
            throw new IllegalStateException("목표 지점과의 거리가 너무 멉니다. (" + Math.round(dist) + "m)");
        }

        // 사진 인증 (uploadProofImage로 먼저 업로드한 URL을 받아 이 요청에 실어 보냄)
        if (req.getProofUrl() == null || req.getProofUrl().isBlank()) {
            throw new IllegalArgumentException("인증 사진이 필요합니다.");
        }
        cp.setProofUrl(req.getProofUrl());

        // 포인트 지급 — 멱등 키: userId:CHALLENGE:challengeId (챌린지당 1회)
        int award = resolveAwardPoint(spot);
        if (award > 0) {
            String idemKey = me.getId() + ":CHALLENGE:" + challengeId;
            pointLedgerService.record(me.getId(), award, LedgerReason.CHALLENGE_AWARD, challengeId, idemKey);
        }

        // 챌린지 완료 알림
        notificationService.send(NotificationFactory.challengeComplete(
                me, String.format("챌린지를 완료해서 한라봉 %,d개를 받았어요!", award), challengeId));

        cp.setStatus(Status.COMPLETED);
        cp.setEndDate(LocalDate.now());
        cp.setCompletedAt(LocalDateTime.now());

        // 테마 미션(스탬프 투어) 진행도 갱신 — 이번 완료로 미션을 다 채웠으면 완주 보상까지 지급
        List<CompletedMissionResponse> completedMissions = missionProgressService.recordSpotVisit(me, spot.getId());

        // record()가 벌크 UPDATE로 반영한 잔액은 이미 로드된 me 엔티티에 즉시 반영되지 않으므로
        // 스칼라 프로젝션으로 다시 읽는다 (findById는 1차 캐시의 stale한 me를 반환할 수 있음).
        // 미션 완주 보상도 같은 트랜잭션에서 지급됐으므로 이 값에 함께 반영된다.
        int totalHallabong = userRepository.findHallabongById(me.getId());

        return new ChallengeCompleteResponse(
                spot.getId(),
                true,
                dist,
                award,
                totalHallabong,
                cp.getCompletedAt(),
                completedMissions
        );
    }

    @Transactional
    public void cancel(Long challengeId) {
        User me = securityUtil.getAuthenticatedUser();
        ChallengeParticipation cp = cpRepository.findByChallenge_IdAndUser_Id(challengeId, me.getId())
                .orElseThrow(() -> new EntityNotFoundException("참여 이력이 없습니다."));

        // 진행중 상태만 취소 가능
        if (!(cp.getStatus() == Status.JOINED || cp.getStatus() == Status.SUBMITTED || cp.getStatus() == Status.APPROVED)) {
            throw new IllegalStateException("취소할 수 없는 상태입니다: " + cp.getStatus());
        }

        cp.setStatus(Status.CANCELLED);
        cp.setEndDate(LocalDate.now());
    }

    /** 방문 인증 사진 업로드. 반환된 URL을 complete() 요청의 proofUrl로 실어 보낸다. */
    public String uploadProofImage(Long challengeId, MultipartFile file) {
        User me = securityUtil.getAuthenticatedUser();
        validateProofImage(file);

        String key = "challenge-proof/" + me.getId() + "/" + challengeId + "/"
                + UUID.randomUUID() + "-" + file.getOriginalFilename();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(ImageValidator.resolveContentType(file));

        try {
            amazonS3.putObject(bucketName, key, file.getInputStream(), metadata);
        } catch (IOException e) {
            throw new RuntimeException("S3 인증사진 업로드에 실패했습니다.", e);
        }
        return amazonS3.getUrl(bucketName, key).toString();
    }

    private void validateProofImage(MultipartFile f) {
        ImageValidator.validate(f, "인증 사진이 비어있습니다.");
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
