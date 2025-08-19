package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.dto.ChallengeResponse;
import com.goodda.jejuday.spot.dto.ChallengeStatus;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.ChallengeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ChallengeQueryService {

    private final ChallengeRepository challengeRepository;

    public ChallengeQueryService(ChallengeRepository challengeRepository) {
        this.challengeRepository = challengeRepository;
    }

    // 진행전: 랜덤 1개
    public ChallengeResponse getUpcomingRandom() {
        LocalDate today = LocalDate.now();
        Spot s = challengeRepository.pickRandomUpcoming(today);
        if (s == null) return null; // 없을 수도 있음
        return ChallengeResponse.from(s, ChallengeStatus.UPCOMING);
    }

    // 진행중: lastId 기반 무한스크롤
    public List<ChallengeResponse> getOngoing(Long lastId, Integer size) {
        int limit = normalizeSize(size);
        Pageable pv = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id"));
        LocalDate today = LocalDate.now();
        return challengeRepository.findOngoing(today, lastId, pv)
                .stream()
                .map(s -> ChallengeResponse.from(s, ChallengeStatus.ONGOING))
                .toList();
    }

    // 완료: lastId 기반 무한스크롤
    public List<ChallengeResponse> getCompleted(Long lastId, Integer size) {
        int limit = normalizeSize(size);
        Pageable pv = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id"));
        LocalDate today = LocalDate.now();
        return challengeRepository.findCompleted(today, lastId, pv)
                .stream()
                .map(s -> ChallengeResponse.from(s, ChallengeStatus.COMPLETED))
                .toList();
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) return 20;
        return Math.min(size, 50); // 과도한 요청 방지
    }
}