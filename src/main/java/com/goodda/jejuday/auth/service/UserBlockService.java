package com.goodda.jejuday.auth.service;

import com.goodda.jejuday.auth.dto.BlockedUserResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.entity.UserBlock;
import com.goodda.jejuday.auth.repository.UserBlockRepository;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.util.SecurityUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBlockService {

    // 차단한 사람이 아무도 없을 때 "NOT IN" 절이 항상 참이 되도록 쓰는 더미 ID (실제 유저 ID로 절대 나올 수 없음)
    private static final List<Long> NO_BLOCKS_SENTINEL = List.of(-1L);

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    @Transactional
    public void block(Long targetUserId) {
        User me = securityUtil.getAuthenticatedUser();
        if (me.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("본인을 차단할 수 없습니다.");
        }
        if (userBlockRepository.existsByBlocker_IdAndBlocked_Id(me.getId(), targetUserId)) {
            return; // 이미 차단된 상태면 그대로 성공 처리(멱등)
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        UserBlock block = new UserBlock();
        block.setBlocker(me);
        block.setBlocked(target);
        userBlockRepository.save(block);
    }

    @Transactional
    public void unblock(Long targetUserId) {
        Long myId = securityUtil.getAuthenticatedUser().getId();
        userBlockRepository.deleteByBlocker_IdAndBlocked_Id(myId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> getBlockedUsers() {
        Long myId = securityUtil.getAuthenticatedUser().getId();
        return userBlockRepository.findAllByBlockerIdWithBlockedUser(myId).stream()
                .map(b -> new BlockedUserResponse(
                        b.getBlocked().getId(),
                        b.getBlocked().getNickname(),
                        b.getBlocked().getProfile(),
                        b.getCreatedAt()
                ))
                .toList();
    }

    /**
     * 피드/검색/댓글 조회 쿼리의 "NOT IN" 절에 바로 꽂아 쓰기 위한 차단 목록.
     * 비로그인 요청이거나 차단한 사람이 없으면 항상 매치되지 않는 더미 ID를 반환한다
     * (JPQL의 빈 컬렉션 IN 절 문제를 피하기 위함).
     */
    @Transactional(readOnly = true)
    public List<Long> getBlockedUserIdsOrSentinel() {
        Long myId;
        try {
            myId = securityUtil.getAuthenticatedUser().getId();
        } catch (Exception e) {
            return NO_BLOCKS_SENTINEL;
        }
        List<Long> ids = userBlockRepository.findBlockedUserIds(myId);
        return ids.isEmpty() ? NO_BLOCKS_SENTINEL : ids;
    }
}
