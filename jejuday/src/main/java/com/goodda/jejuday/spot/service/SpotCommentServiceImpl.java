package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.dto.ReplyDTO;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.service.SpotCommentService;
import com.goodda.jejuday.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpotCommentServiceImpl implements SpotCommentService {

    private final ReplyRepository replyRepo;
    private final SpotRepository spotRepo;
    private final UserService userService;

    @Override
    public ReplyDTO create(ReplyDTO dto) {
        // 스팟 존재 확인
        spotRepo.findById(dto.getContentId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid spot ID"));

        // 현재 유저 조회
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Reply r = new Reply();
        r.setContentId(dto.getContentId());
        // 부모댓글이 있으면 depth 재설정
        if (dto.getParentReplyId() != null) {
            Reply parent = replyRepo.findById(dto.getParentReplyId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid parent reply ID"));
            r.setParentReply(parent);
            r.setDepth(parent.getDepth() + 1);
        } else {
            r.setDepth(0);
        }
        r.setUser(user);
        r.setText(dto.getText());

        return map(replyRepo.save(r));
    }

    @Override
    public List<ReplyDTO> findTopLevelBySpot(Long spotId) {
        return replyRepo.findByContentIdAndDepthOrderByCreatedAtDesc(spotId, 0)
                .stream().map(this::map).collect(Collectors.toList());
    }

    @Override
    public List<ReplyDTO> findReplies(Long parentReplyId) {
        return replyRepo.findByParentReplyIdOrderByCreatedAtAsc(parentReplyId)
                .stream().map(this::map).collect(Collectors.toList());
    }

    @Override
    public ReplyDTO update(ReplyDTO dto) {
        Reply r = replyRepo.findById(dto.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid reply ID"));
        // 권한 체크 필요시 추가
        r.setText(dto.getText());
        return map(replyRepo.save(r));
    }


    @Override
    public void delete(Long replyId) {
        Reply r = replyRepo.findById(replyId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reply ID"));
        // 항상 소프트 삭제 처리
        r.setIsDeleted(true);
        // 최상위 댓글이며 대댓글이 있을 때 content 변경 (UI용)
        if (r.getDepth() == 0 && !replyRepo.findByParentReplyIdOrderByCreatedAtAsc(r.getId()).isEmpty()) {
            r.setText("삭제된 댓글입니다.");
        }
        replyRepo.save(r);
    }

    private ReplyDTO map(Reply r) {
        ReplyDTO dto = new ReplyDTO();
        dto.setId(r.getId());
        dto.setContentId(r.getContentId());
        dto.setParentReplyId(r.getParentReply() != null ? r.getParentReply().getId() : null);
        dto.setDepth(r.getDepth());
        dto.setText(r.getText());
        dto.setMemberId(r.getUser().getId());
        dto.setMemberNickname(r.getUser().getNickname());
        dto.setRelativeTime(r.getCreatedAt().toString());
        dto.setIsDeleted(r.getIsDeleted());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }
}