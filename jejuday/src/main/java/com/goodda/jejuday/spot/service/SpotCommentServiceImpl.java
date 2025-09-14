package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.util.SecurityUtil;
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

    @Override
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
        return toResponse(replyRepo.save(r));
    }


    @Override
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
        return toResponse(replyRepo.save(r));
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
        Page<Reply> p = replyRepo.findByContentIdAndDepth(spotId, 0, pg);
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
        Page<Reply> p = replyRepo.findByParentReplyId(parentReplyId, pg);
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
    }


    /** Entity → DTO 변환 헬퍼 */
    private ReplyResponse toResponse(Reply r) {
        return ReplyResponse.builder()
                .id(r.getId())
                .contentId(r.getContentId())
                .parentReplyId(r.getParentReply() != null ? r.getParentReply().getId() : null)
                .depth(r.getDepth())
                .text(r.getIsDeleted() ? "삭제된 댓글입니다." : r.getText())
                .nickname(r.getUser().getNickname())
                .profileImageUrl(r.getUser() != null ? userService.getProfileImageUrl(r.getUser().getId()) : null)
                .createdAt(r.getCreatedAt())
                .isDeleted(r.getIsDeleted())
                .build();
    }
}