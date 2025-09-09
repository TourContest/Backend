package com.goodda.jejuday.spot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.dto.ChallengeResponse;
import com.goodda.jejuday.spot.entity.ChallengeParticipation;
import com.goodda.jejuday.spot.entity.ChallengeRecoItem;
import com.goodda.jejuday.spot.entity.ChallengeRecoSnapshot;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.ChallengeParticipationRepository;
import com.goodda.jejuday.spot.repository.ChallengeRecoItemRepository;
import com.goodda.jejuday.spot.repository.ChallengeRecoSnapshotRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.auth.repository.UserThemeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.ThreadLocalRandom;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeRecoFacade {

	private static final int SLOT_COUNT = 4;
	private static final int TTL_DAYS = 2;
	private static final int CANDIDATE_WINDOW = 200; // 최근 N개에서 후보 뽑기
	private static final int MAX_RETRY = 5;          // 테마 제외 랜덤 재시도

	private final SecurityUtil securityUtil;
	private final UserThemeRepository userThemeRepository;

	private final ChallengeRecoItemRepository itemRepo;
	private final ChallengeRecoSnapshotRepository snapshotRepo;
	private final ChallengeParticipationRepository participationRepo;
	private final SpotRepository spotRepository;
	private final UserThemeRepository userThemeRepo;

	private final ObjectMapper objectMapper;

	@Lazy
	private final ChallengeRecoFacade unusedForCtorOnly = null;

	private ChallengeRecoFacade self() {
		return (ChallengeRecoFacade) AopContext.currentProxy();
	}

	/** 진행전 4개: 부족/만료 시 자동 보충 + 새 트랜잭션 재조회 */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public List<ChallengeResponse> getUpcomingWithAutoRefresh() {
		Long userId = securityUtil.getAuthenticatedUser().getId();
		LocalDateTime now = LocalDateTime.now();

		List<Long> ids = loadActiveSpotIds(userId, now);
		if (ids.size() < SLOT_COUNT) {
			log.info("Auto refresh needed for user {} ({} / {}).", userId, ids.size(), SLOT_COUNT);
			self().refreshSlotsIfNeeded(userId); // REQUIRES_NEW
		}
		return self().requeryAndUpdateSnapshotNewTx(userId);
	}

	/** 강제 새로고침 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public List<ChallengeResponse> forceRefreshAndGet() {
		Long userId = securityUtil.getAuthenticatedUser().getId();
		log.info("Force refreshing recommendations for user {}", userId);

		self().purgeUserItems(userId);
		self().refreshSlotsIfNeeded(userId);
		return self().requeryAndUpdateSnapshotNewTx(userId);
	}

	/** 기존 추천 삭제 + 스냅샷 더티 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void purgeUserItems(Long userId) {
		itemRepo.deleteByUserId(userId);
		markSnapshotDirty(userId);
	}

	/**
	 * 4개 보장 보충 로직
	 * - 슬롯 0..2: 선호 테마 → 랜덤(테마제외) → 아무거나
	 * - 슬롯 3: 랜덤(테마제외) → 유니크 백업 → 최종 중복허용
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void refreshSlotsIfNeeded(Long userId) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime expiresAt = now.plus(TTL_DAYS, ChronoUnit.DAYS);

		// 현재 유효 아이템
		List<ChallengeRecoItem> active = itemRepo.findActiveByUser(userId, now);
		Map<Integer, ChallengeRecoItem> bySlot = active.stream()
				.collect(Collectors.toMap(ChallengeRecoItem::getSlotIndex, it -> it, (a, b) -> a));

		// 선호 테마 (LazyInit 회피: 리포지토리에서 ID만)
		List<Long> prefThemeIds = resolvePrefThemes(3);
		log.info("User {} preferred themes: {}", userId, prefThemeIds);

		// 사용된 spot (이번 리프레시 내 중복 방지)
		Set<Long> usedSpotIds = active.stream()
				.map(ChallengeRecoItem::getSpotId)
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		// 직전 스냅샷도 배제 → 동일 4개 반복 감소
		ChallengeRecoSnapshot snap = snapshotRepo.findById(userId).orElse(null);
		if (snap != null && snap.getSpotIdsJson() != null) {
			usedSpotIds.addAll(safeReadIds(snap.getSpotIdsJson()));
		}

		// 슬롯 0..2: 선호테마 우선
		for (int slot = 0; slot < 3; slot++) {
			if (bySlot.containsKey(slot)) continue;

			Long preferTheme = (slot < prefThemeIds.size()) ? prefThemeIds.get(slot) : null;
			Spot pick = pickForSlot(preferTheme, prefThemeIds, usedSpotIds);

			if (pick != null) {
				addItem(userId, slot, pick, reasonFor(preferTheme), now, expiresAt);
				usedSpotIds.add(pick.getId());
				bySlot.put(slot, dummyItem(slot));
				log.info("Added spot {} to slot {} for user {}", pick.getId(), slot, userId);
			} else {
				log.warn("Failed to find spot for slot {} user {}", slot, userId);
			}
		}

		// 슬롯 3: 랜덤(테마제외) → 유니크 백업 → 최종 중복허용
		fillAnyRemainingSlots(userId, bySlot, prefThemeIds, usedSpotIds, now, expiresAt);
	}

	// ==================== 재조회/스냅샷/변환 ====================

	/** 새 트랜잭션 재조회 + 스냅샷 갱신 + DTO 변환(트랜잭션 내라 Lazy 안전) */
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public List<ChallengeResponse> requeryAndUpdateSnapshotNewTx(Long userId) {
		LocalDateTime now = LocalDateTime.now();
		List<Long> ids = loadActiveSpotIds(userId, now);
		saveSnapshot(userId, ids, now, now.plusDays(TTL_DAYS));

		List<ChallengeResponse> out = toResponsesPreservingOrder(ids);
		log.info("Converted {} items to {} responses", ids.size(), out.size());
		return out;
	}

	private void addItem(Long userId,
						 int slot,
						 Spot pick,
						 String reason,
						 LocalDateTime now,
						 LocalDateTime expiresAt) {
		ChallengeRecoItem item = new ChallengeRecoItem();
		item.setUserId(userId);
		item.setSpotId(pick.getId());
		item.setThemeId(pick.getTheme() != null ? pick.getTheme().getId() : null);
		item.setSlotIndex(slot);
		item.setGeneratedAt(now);
		item.setExpiresAt(expiresAt);
		item.setReason(reason);
		itemRepo.save(item);
	}

	private List<Long> loadActiveSpotIds(Long userId, LocalDateTime now) {
		return itemRepo.findActiveByUser(userId, now).stream()
				.sorted(Comparator.comparingInt(ChallengeRecoItem::getSlotIndex))
				.map(ChallengeRecoItem::getSpotId)
				.filter(Objects::nonNull)
				.toList();
	}

	private void saveSnapshot(Long userId, List<Long> spotIds, LocalDateTime now, LocalDateTime expiresAt) {
		try {
			ChallengeRecoSnapshot snap = snapshotRepo.findById(userId).orElse(null);
			String json = objectMapper.writeValueAsString(spotIds != null ? spotIds : List.of());
			if (snap == null) {
				snap = new ChallengeRecoSnapshot();
				snap.setUserId(userId);
			}
			snap.setGeneratedAt(now);
			snap.setExpiresAt(expiresAt);
			snap.setDirty(false);
			snap.setSpotIdsJson(json);
			snapshotRepo.save(snap);
		} catch (Exception e) {
			log.warn("Snapshot save failed for user {}: {}", userId, e.toString());
		}
	}

	private void markSnapshotDirty(Long userId) {
		ChallengeRecoSnapshot snap = snapshotRepo.findById(userId).orElse(null);
		if (snap != null) {
			snap.setDirty(true);
			snapshotRepo.save(snap);
		}
	}

	/** Spot IDs → 순서 유지하여 DTO 변환 (트랜잭션 내) */
	@Transactional(readOnly = true)
	protected List<ChallengeResponse> toResponsesPreservingOrder(List<Long> spotIdsInOrder) {
		if (spotIdsInOrder == null || spotIdsInOrder.isEmpty()) return List.of();

		List<Spot> spots = spotRepository.findAllById(spotIdsInOrder);
		Map<Long, Spot> byId = spots.stream()
				.filter(s -> s != null && s.getType() == Spot.SpotType.CHALLENGE && !Boolean.TRUE.equals(s.getIsDeleted()))
				.collect(Collectors.toMap(Spot::getId, s -> s, (a, b) -> a));

		List<ChallengeResponse> out = new ArrayList<>(spotIdsInOrder.size());
		for (Long id : spotIdsInOrder) {
			Spot s = byId.get(id);
			if (s != null) out.add(ChallengeResponse.of(s));
		}
		return out;
	}

	// ==================== Picking Methods ====================

	private String reasonFor(Long preferTheme) {
		return (preferTheme != null) ? "PREF_THEME" : "BACKFILL_RANDOM";
	}

	private Spot pickForSlot(Long preferTheme, List<Long> prefThemeIds, Set<Long> usedSpotIds) {
		Spot pick = null;
		if (preferTheme != null) {
			pick = pickOneByTheme(preferTheme, usedSpotIds);
		}
		if (pick == null) {
			pick = pickOneRandomExcludingThemes(prefThemeIds, usedSpotIds);
		}
		if (pick == null) {
			pick = pickOneRandom(usedSpotIds);
		}
		return pick;
	}

	/** 최근 N개 아이디 중 exclude 아닌 것 랜덤 1개 (메모리 랜덤) */
	@Transactional(readOnly = true)
	protected Spot pickFromRecent(Long themeId, Set<Long> exclude) {
		List<Long> ids = spotRepository.findRecentChallengeIds(themeId, PageRequest.of(0, CANDIDATE_WINDOW));
		if (ids.isEmpty()) return null;

		// exclude 제거
		List<Long> pool = ids.stream().filter(id -> !exclude.contains(id)).toList();
		if (pool.isEmpty()) return null;

		Long pickId = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
		return spotRepository.findById(pickId).orElse(null);
	}

	@Transactional(readOnly = true)
	protected Spot pickOneByTheme(Long themeId, Set<Long> excludeSpotIds) {
		if (themeId == null) return null;
		return pickFromRecent(themeId, excludeSpotIds);
	}

	/** 테마 제외 랜덤: 충돌 시 여러번 재시도 (MAX_RETRY) */
	@Transactional(readOnly = true)
	protected Spot pickOneRandomExcludingThemes(List<Long> excludedThemeIds, Set<Long> exclude) {
		for (int i = 0; i < MAX_RETRY; i++) {
			Spot s = pickFromRecent(null, exclude);
			if (s == null) return null;
			Long t = (s.getTheme() != null) ? s.getTheme().getId() : null;
			boolean ok = (t == null) || excludedThemeIds == null || !excludedThemeIds.contains(t);
			if (ok) return s;
			exclude.add(s.getId()); // 다음 시도에서 제외
		}
		return null;
	}

	@Transactional(readOnly = true)
	protected Spot pickOneRandom(Set<Long> excludeSpotIds) {
		return pickFromRecent(null, excludeSpotIds);
	}

	/** 최근 목록에서 '아직 안 쓴 것'을 순차로 하나 (유니크 보장) */
	@Transactional(readOnly = true)
	protected Spot pickNextUniqueFromRecent(Set<Long> used) {
		List<Long> ids = spotRepository.findRecentChallengeIds(null, PageRequest.of(0, CANDIDATE_WINDOW));
		for (Long id : ids) {
			if (!used.contains(id)) {
				return spotRepository.findById(id).orElse(null);
			}
		}
		return null;
	}

	/** 최종 중복 허용 백업: 최근 목록에서 첫 번째 아무거나 */
	@Transactional(readOnly = true)
	protected Spot pickAnyIgnoringExcludes() {
		List<Long> ids = spotRepository.findRecentChallengeIds(null, PageRequest.of(0, Math.max(10, SLOT_COUNT)));
		if (ids.isEmpty()) return null;
		return spotRepository.findById(ids.get(0)).orElse(null);
	}

	/** 남은 슬롯 백필: 재시도 → 유니크 백업 → 최종 중복허용 */
	private void fillAnyRemainingSlots(Long userId, Map<Integer, ChallengeRecoItem> bySlot,
									   List<Long> prefThemeIds, Set<Long> usedSpotIds,
									   LocalDateTime now, LocalDateTime expiresAt) {
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			if (bySlot.containsKey(slot)) continue;

			// 1) 테마 제외 랜덤 (여러 번 재시도)
			Spot pick = pickRandomExclThenAny(prefThemeIds, usedSpotIds);

			// 2) 그래도 실패면 유니크 보장
			if (pick == null) {
				pick = pickNextUniqueFromRecent(usedSpotIds);
			}

			// 3) 그래도 실패면 최종 중복 허용
			if (pick == null) {
				pick = pickAnyIgnoringExcludes();
			}

			if (pick != null) {
				addItem(userId, slot, pick, "BACKFILL", now, expiresAt);
				usedSpotIds.add(pick.getId());
				bySlot.put(slot, dummyItem(slot));
				log.info("Backfilled slot {} with spot {} for user {}", slot, pick.getId(), userId);
			} else {
				log.error("Backfill failed for slot {} user {} (no candidates at all)", slot, userId);
			}
		}
	}

	private Spot pickRandomExclThenAny(List<Long> prefThemeIds, Set<Long> usedSpotIds) {
		Spot pick = pickOneRandomExcludingThemes(prefThemeIds, usedSpotIds);
		return (pick != null) ? pick : pickOneRandom(usedSpotIds);
	}

	// ==================== Pref Themes / Util ====================

	/** 선호 테마 ID 안전 조회 (Lazy 컬렉션 접근 금지) */
	private List<Long> resolvePrefThemes(int limit) {
		Long userId = securityUtil.getAuthenticatedUser().getId();

		// 동시 진행 방지: 진행중인 테마 ID들
		List<ChallengeParticipation.Status> ongoingStatuses = Arrays.asList(
				ChallengeParticipation.Status.JOINED,
				ChallengeParticipation.Status.SUBMITTED,
				ChallengeParticipation.Status.APPROVED
		);
		List<Long> ongoingThemeIds = participationRepo.findOngoingThemeIds(userId, ongoingStatuses);

		// 선호 테마 ID를 N:M 조인으로 안전 조회 (지연로딩 X)
		List<Long> preferred = userThemeRepository.findThemeIdsByUserId(userId);

		return preferred.stream()
				.filter(Objects::nonNull)
				.filter(id -> !ongoingThemeIds.contains(id)) // 진행중 테마 제외
				.distinct()
				.limit(limit)
				.toList();
	}

	private ChallengeRecoItem dummyItem(int slot) {
		ChallengeRecoItem d = new ChallengeRecoItem();
		d.setSlotIndex(slot);
		return d;
	}

	private List<Long> safeReadIds(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
		} catch (Exception e) {
			return List.of();
		}
	}
}
