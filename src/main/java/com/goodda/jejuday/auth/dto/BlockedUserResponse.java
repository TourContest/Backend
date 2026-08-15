package com.goodda.jejuday.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BlockedUserResponse {
    private Long userId;
    private String nickname;
    private String profile;
    private LocalDateTime blockedAt;
}
