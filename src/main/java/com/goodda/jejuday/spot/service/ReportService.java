package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.dto.ReportRequest;
import com.goodda.jejuday.spot.entity.Report;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.ReportRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final SpotRepository spotRepository;
    private final ReplyRepository replyRepository;
    private final SecurityUtil securityUtil;

    @Transactional
    public void reportSpot(Long spotId, ReportRequest req) {
        User me = securityUtil.getAuthenticatedUser();
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        if (Boolean.TRUE.equals(spot.getIsDeleted())) {
            throw new EntityNotFoundException("Spot not found");
        }
        if (spot.getUser() != null && Objects.equals(spot.getUser().getId(), me.getId())) {
            throw new IllegalArgumentException("본인이 작성한 게시글은 신고할 수 없습니다.");
        }
        createReport(me, Report.TargetType.SPOT, spotId, spotId, req);
    }

    @Transactional
    public void reportReply(Long spotId, Long replyId, ReportRequest req) {
        User me = securityUtil.getAuthenticatedUser();
        Reply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new EntityNotFoundException("Reply not found"));
        if (!spotId.equals(reply.getContentId())) {
            throw new IllegalArgumentException("해당 스팟의 댓글이 아닙니다.");
        }
        if (Boolean.TRUE.equals(reply.getIsDeleted())) {
            throw new EntityNotFoundException("Reply not found");
        }
        if (Objects.equals(reply.getUser().getId(), me.getId())) {
            throw new IllegalArgumentException("본인이 작성한 댓글은 신고할 수 없습니다.");
        }
        createReport(me, Report.TargetType.REPLY, replyId, spotId, req);
    }

    private void createReport(User reporter, Report.TargetType targetType, Long targetId, Long spotId, ReportRequest req) {
        if (reportRepository.existsByReporterAndTargetTypeAndTargetId(reporter, targetType, targetId)) {
            throw new IllegalStateException("이미 신고한 항목입니다.");
        }

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setSpotId(spotId);
        report.setReason(req.getReason());
        report.setDescription(req.getDescription());
        reportRepository.save(report);
    }
}
