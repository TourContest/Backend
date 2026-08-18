package com.goodda.jejuday.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 사용자와 사용자가 만든 콘텐츠를 참조하는 행을 FK 안전 순서로 물리 삭제한다. */
@Service
@RequiredArgsConstructor
public class UserHardDeleteService {

    private final JdbcTemplate jdbcTemplate;

    public void deleteDependencies(Long userId) {
        deleteOwnedSpotDependencies(userId);

        update("DELETE FROM user_block WHERE blocker_id = ? OR blocked_id = ?", userId, userId);
        update("DELETE FROM email_verification WHERE user_id = ?", userId);
        update("DELETE FROM notification_entity WHERE user_id = ?", userId);
        update("DELETE FROM notification_outbox WHERE user_id = ?", userId);
        update("DELETE FROM report WHERE reporter_id = ?", userId);
        update("DELETE FROM bookmark WHERE user_id = ?", userId);
        update("DELETE FROM spot_likes WHERE user_id = ?", userId);
        update("DELETE FROM report WHERE target_type = 'REPLY' AND target_id IN "
                + "(SELECT id FROM (SELECT id FROM reply WHERE user_id = ?) deleted_replies)", userId);
        update("UPDATE reply child JOIN reply parent ON child.parent_reply_id = parent.id "
                + "SET child.parent_reply_id = NULL WHERE parent.user_id = ?", userId);
        update("DELETE FROM reply WHERE user_id = ?", userId);
        update("DELETE FROM challenge_participation WHERE user_id = ?", userId);
        update("DELETE FROM search_history WHERE user_id = ?", userId);
        update("DELETE FROM step_daily WHERE user_id = ?", userId);
        update("DELETE FROM user_attendance WHERE user_id = ?", userId);
        update("DELETE FROM user_bonus_log WHERE user_id = ?", userId);
        update("DELETE FROM user_mission_step WHERE user_id = ?", userId);
        update("DELETE FROM user_mission_completion WHERE user_id = ?", userId);
        update("DELETE FROM product_exchanges WHERE user_id = ?", userId);
        update("DELETE FROM point_ledger WHERE user_id = ?", userId);
        update("DELETE FROM challenge_reco_item WHERE user_id = ?", userId);
        update("DELETE FROM challenge_reco_snapshot WHERE user_id = ?", userId);
        update("DELETE FROM spot_view_log WHERE user_id = ?", userId);
        update("DELETE FROM user_theme WHERE user_id = ?", userId);
        update("DELETE FROM user_mood_rewards WHERE user_id = ?", userId);
        update("UPDATE users SET referrer_id = NULL WHERE referrer_id = ?", userId);
        update("UPDATE spot SET deleted_by = NULL WHERE deleted_by = ?", userId);
    }

    private void deleteOwnedSpotDependencies(Long userId) {
        String ownedSpots = "SELECT id FROM spot WHERE user_id = ?";

        update("DELETE FROM report WHERE spot_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM spot_view_log WHERE spot_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM challenge_participation WHERE challenge_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM bookmark WHERE spot_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM spot_likes WHERE spot_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM challenge_reco_item WHERE spot_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM spot_detail WHERE spot_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM spot_embedding WHERE spot_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM spot_congestion WHERE spot_id IN (" + ownedSpots + ")", userId);
        update("UPDATE reply SET parent_reply_id = NULL WHERE content_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM reply WHERE content_id IN (" + ownedSpots + ")", userId);
        update("DELETE FROM spot WHERE user_id = ?", userId);
    }

    private void update(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
    }
}
