package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.dto.ChallengeResponse;
import com.goodda.jejuday.spot.dto.MyChallengeResponse;
import com.goodda.jejuday.spot.entity.ChallengeParticipation;
import com.goodda.jejuday.spot.entity.ChallengeParticipation.Status;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.ChallengeParticipationRepository;
import com.goodda.jejuday.spot.repository.ChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeQueryService {

	private final ChallengeRepository challengeRepository;
	private final ChallengeParticipationRepository cpRepository;
	private final SecurityUtil securityUtil;

	// ==== 기존 진행중/완료는 그대로 ====
	public List<MyChallengeResponse> ongoingMine() {
		User me = securityUtil.getAuthenticatedUser();
		List<Status> ongoingStatuses = Arrays.asList(Status.JOINED, Status.SUBMITTED, Status.APPROVED);
		List<ChallengeParticipation> cps =
				cpRepository.findOngoing(me.getId(), ongoingStatuses, PageRequest.of(0, 4));

		List<MyChallengeResponse> list = new ArrayList<>();
		for (ChallengeParticipation cp : cps) {
			Spot s = cp.getChallenge();
			list.add(MyChallengeResponse.of(s, cp));
		}
		return list;
	}

	public List<MyChallengeResponse> completedMine(String sort, Long lastId, Integer size) {
		User me = securityUtil.getAuthenticatedUser();
		int limit = (size == null || size <= 0) ? 20 : Math.min(size, 50);

		List<ChallengeParticipation> cps;
		if ("views".equalsIgnoreCase(sort)) {
			cps = cpRepository.findCompletedByViews(me.getId(), lastId, PageRequest.of(0, limit));
		} else if ("likes".equalsIgnoreCase(sort)) {
			cps = cpRepository.findCompletedByLikes(me.getId(), lastId, PageRequest.of(0, limit));
		} else {
			cps = cpRepository.findCompletedLatest(me.getId(), lastId, PageRequest.of(0, limit));
		}

		List<MyChallengeResponse> list = new ArrayList<>();
		for (ChallengeParticipation cp : cps) {
			Spot s = cp.getChallenge();
			list.add(MyChallengeResponse.of(s, cp));
		}
		return list;
	}

	public ChallengeResponse mapSpotIdToResponse(Long spotId) {
		if (spotId == null) return null;
		Optional<Spot> opt = challengeRepository.findById(spotId);
		if (opt.isEmpty()) return null;

		Spot s = opt.get();
		// 유효성: 챌린지 타입 & 삭제 아님
		if (s.getType() != Spot.SpotType.CHALLENGE) return null;
		if (Boolean.TRUE.equals(s.getIsDeleted())) return null;

		return ChallengeResponse.of(s);
	}

	// Spot 조회 유틸 (null 허용)
	public Spot findSpot(Long spotId) {
		if (spotId == null) return null;
		return challengeRepository.findById(spotId).orElse(null);
	}
}