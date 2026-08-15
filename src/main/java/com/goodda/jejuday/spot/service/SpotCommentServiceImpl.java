package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.service.UserBlockService;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.notification.service.NotificationFactory;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.spot.ranking.EngagementChangedEvent;
import com.goodda.jejuday.spot.dto.ReplyPageResponse;
import com.goodda.jejuday.spot.dto.ReplyRequest;
import com.goodda.jejuday.spot.dto.ReplyResponse;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.dto.ReplyDTO;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.service.SpotCommentService;
import com.goodda.jejuday.auth.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpotCommentServiceImpl implements SpotCommentService {

    private final ReplyRepository replyRepo;
    private final SpotRepository spotRepo;
    private final SecurityUtil securityUtil;
    private final UserService userService;
    private final UserBlockService userBlockService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ReplyResponse createComment(Long spotId, ReplyRequest request) {
        User user = securityUtil.getAuthenticatedUser();
        Spot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        Reply r = new Reply();
        r.setContentId(spot.getId());
        r.setUser(user);
        r.setText(request.getText());
        r.setDepth(0);                           // 최상위 댓글
        r.setCreatedAt(LocalDateTime.now());
        ReplyResponse response = toResponse(replyRepo.save(r));
        eventPublisher.publishEvent(new EngagementChangedEvent(spotId));

        // 게시글 작성자에게 댓글 알림 (본인 글에 본인이 댓글 단 경우는 제외)
        User author = spot.getUser();
        if (author != null && !author.getId().equals(user.getId())) {
            notificationService.send(NotificationFactory.reply(
                    author, user.getNickname() + "님이 게시글에 댓글을 남겼어요.", spotId));
        }

        return response;
    }


    @Override
    @Transactional
    public ReplyResponse createReply(Long spotId, Long parentReplyId, ReplyRequest request) {
        User user = securityUtil.getAuthenticatedUser();
        Reply parent = replyRepo.findById(parentReplyId)
                .orElseThrow(() -> new EntityNotFoundException("Parent reply not found"));
        Reply r = new Reply();
        r.setContentId(spotId);
        r.setUser(user);
        r.setParentReply(parent);
        r.setText(request.getText());
        r.setDepth(parent.getDepth() + 1);      // 부모 깊이+1
        r.setCreatedAt(LocalDateTime.now());
        ReplyResponse response = toResponse(replyRepo.save(r));
        eventPublisher.publishEvent(new EngagementChangedEvent(spotId));

        // 부모 댓글 작성자에게 답글 알림 (본인 댓글에 본인이 답글 단 경우는 제외)
        User parentAuthor = parent.getUser();
        if (parentAuthor != null && !parentAuthor.getId().equals(user.getId())) {
            notificationService.send(NotificationFactory.commentReply(
                    parentAuthor, parentReplyId, user.getNickname() + "님이 답글을 남겼어요."));
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReplyResponse> findTopLevelBySpot(Long spotId) {
        return replyRepo.findByContentIdAndDepthOrderByCreatedAtDesc(spotId, 0)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ReplyPageResponse findTopLevelBySpot(Long spotId, int page, int size) {
        Pageable pg = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Reply> p = replyRepo.findByContentIdAndDepth(spotId, 0, userBlockService.getBlockedUserIdsOrSentinel(), pg);
        List<ReplyResponse> list = p.stream().map(this::toResponse).toList();
        return new ReplyPageResponse(list, p.getTotalElements(), p.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReplyResponse> findReplies(Long parentReplyId) {
        return replyRepo.findByParentReplyIdOrderByCreatedAtAsc(parentReplyId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }


    @Override
    @Transactional(readOnly = true)
    public ReplyPageResponse findReplies(Long parentReplyId, int page, int size) {
        Pageable pg = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        Page<Reply> p = replyRepo.findByParentReplyId(parentReplyId, userBlockService.getBlockedUserIdsOrSentinel(), pg);
        List<ReplyResponse> list = p.stream().map(this::toResponse).toList();
        return new ReplyPageResponse(list, p.getTotalElements(), p.hasNext());
    }


    @Override
    @Transactional
    public ReplyResponse update(Long replyId, String newText) {
        Reply r = replyRepo.findById(replyId)
                .orElseThrow(() -> new EntityNotFoundException("Reply not found"));
        r.setText(newText);
        return toResponse(replyRepo.save(r));
    }


    @Override
    @Transactional
    public void delete(Long replyId) {
        Reply r = replyRepo.findById(replyId)
                .orElseThrow(() -> new EntityNotFoundException("Reply not found"));
        r.setIsDeleted(true);
        // 대댓글이 있는 최상위 댓글은 텍스트만 치환
        if (r.getDepth() == 0 &&
                !replyRepo.findByParentReplyIdOrderByCreatedAtAsc(replyId).isEmpty()) {
            r.setText("삭제된 댓글입니다.");
        }
        replyRepo.save(r);
        eventPublisher.publishEvent(new EngagementChangedEvent(r.getContentId()));
    }


    /** Entity → DTO 변환 헬퍼 */
    private ReplyResponse toResponse(Reply r) {
        return ReplyResponse.builder()
                .id(r.getId())
                .contentId(r.getContentId())
                .parentReplyId(r.getParentReply() != null ? r.getParentReply().getId() : null)
                .depth(r.getDepth())
                .text(r.getIsDeleted() ? "삭제된 댓글입니다." : r.getText())
                .userId(r.getUser() != null ? r.getUser().getId() : null)
                .nickname(r.getUser().getNickname())
                .profileImageUrl(r.getUser() != null ? userService.getProfileImageUrl(r.getUser().getId()) : null)
                .createdAt(r.getCreatedAt())
                .isDeleted(r.getIsDeleted())
                .build();
    }
}