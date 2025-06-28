package com.goodda.jejuday.Auth.security;

import com.goodda.jejuday.Auth.entity.CookieRule;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class CookieUtil {

    public String resolveTokenFromCookie(Cookie[] cookies, CookieRule cookieRule) {
        return Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals(cookieRule.getValue()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse("");
    }

    public void addJwtCookie(HttpServletResponse response, String name, String token, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("None")
                .maxAge(1000 * 60 * 60 * 48) // 2일
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearJwtCookie(HttpServletResponse response, String name, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("None")
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}