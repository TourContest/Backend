package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.util.SecurityUtil;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpotCommentServiceImpl implements SpotCommentService {

    private final ReplyRepository replyRepo;
    private final SpotRepository spotRepo;
    private final UserService userService;
    private final SecurityUtil securityUtil;

    @Override
    public ReplyResponse createComment(Long spotId, ReplyRequest request) {
        User user = securityUtil.getAuthenticatedUser();

        Spot spot = spotRepo.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        Reply reply = new Reply();
        reply.setContentId(spot.getId());
        reply.setUser(user);
        reply.setText(request.getText());
        reply.setDepth(0);
        reply.setCreatedAt(LocalDateTime.now());

        return toResponse(replyRepo.save(reply));
    }

    @Override
    public ReplyResponse createReply(Long spotId, Long parentReplyId, ReplyRequest request) {
        User user = securityUtil.getAuthenticatedUser();

        Reply parent = replyRepo.findById(parentReplyId)
                .orElseThrow(() -> new EntityNotFoundException("Parent reply not found"));

        Reply reply = new Reply();
        reply.setContentId(spotId);
        reply.setUser(user);
        reply.setParentReply(parent);
        reply.setText(request.getText());
        reply.setDepth(parent.getDepth() + 1);
        reply.setCreatedAt(LocalDateTime.now());

        return toResponse(replyRepo.save(reply));
    }


    @Override
    public List<ReplyResponse> findTopLevelBySpot(Long spotId) {
        return replyRepo.findByContentIdAndDepthOrderByCreatedAtDesc(spotId, 0)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<ReplyResponse> findReplies(Long parentReplyId) {
        return replyRepo.findByParentReplyIdOrderByCreatedAtAsc(parentReplyId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public ReplyResponse update(Long replyId, String newText) {
        Reply reply = replyRepo.findById(replyId)
                .orElseThrow(() -> new EntityNotFoundException("Reply not found"));
        reply.setText(newText);
        return toResponse(replyRepo.save(reply));
    }


    @Override
    public void delete(Long replyId) {
        Reply reply = replyRepo.findById(replyId)
                .orElseThrow(() -> new EntityNotFoundException("Reply not found"));

        reply.setIsDeleted(true);
        if (reply.getDepth() == 0 && !replyRepo.findByParentReplyIdOrderByCreatedAtAsc(replyId).isEmpty()) {
            reply.setText("삭제된 댓글입니다.");
        }
        replyRepo.save(reply);
    }

    private ReplyResponse toResponse(Reply r) {
        return ReplyResponse.builder()
                .id(r.getId())
                .contentId(r.getContentId())
                .parentReplyId(r.getParentReply() != null ? r.getParentReply().getId() : null)
                .depth(r.getDepth())
                .text(r.getIsDeleted() ? "삭제된 댓글입니다." : r.getText())
                .nickname(r.getUser().getNickname())
                .createdAt(r.getCreatedAt())
                .isDeleted(r.getIsDeleted())
                .build();
    }
}