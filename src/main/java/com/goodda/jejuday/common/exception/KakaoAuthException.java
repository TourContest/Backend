package com.goodda.jejuday.common.exception;

import lombok.Getter;

@Getter
public class KakaoAuthException extends RuntimeException {
    public KakaoAuthException(String message) {
        super(message);
    }

    public KakaoAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}