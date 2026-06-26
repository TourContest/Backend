package com.goodda.jejuday.auth.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CookieRule {
    JWT_ISSUE_HEADER("Set-Cookie"),
    JWT_RESOLVE_HEADER("Cookie"),
    ACCESS_TOKEN_NAME("accessToken"),
    REFRESH_TOKEN_NAME("refreshToken"),
    LANGUAGE_NAME("language");

    private final String value;
}
