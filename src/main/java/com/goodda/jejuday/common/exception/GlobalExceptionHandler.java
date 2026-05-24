package com.goodda.jejuday.common.exception;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.notification.exception.NotificationException;
import com.goodda.jejuday.notification.exception.NotificationNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 알림 예외 ─────────────────────────────────────────────

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.onFailure(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ApiResponse<String>> handleNotificationException(NotificationException ex) {
        log.warn("알림 처리 오류: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure(ex.getErrorCode(), ex.getMessage()));
    }

    // ─── 인증/도메인 예외 ──────────────────────────────────────

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<String>> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.onFailure(ex.getMessage()));
    }

    @ExceptionHandler(KakaoAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleKakaoAuth(KakaoAuthException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("KAKAO_AUTH_FAILED", "Kakao 인증 실패: " + ex.getMessage()));
    }

    @ExceptionHandler(InsufficientGradeException.class)
    public ResponseEntity<ApiResponse<String>> handleInsufficientGrade(InsufficientGradeException ex) {
        log.warn("굿즈 구매 등급 부족: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.onFailure("INSUFFICIENT_GRADE", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientHallabongException.class)
    public ResponseEntity<ApiResponse<String>> handleInsufficientHallabong(InsufficientHallabongException ex) {
        log.warn("한라봉 포인트 부족: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure("INSUFFICIENT_HALLABONG", ex.getMessage()));
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ApiResponse<String>> handleOutOfStock(OutOfStockException ex) {
        log.warn("상품 재고 부족: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure("OUT_OF_STOCK", ex.getMessage()));
    }

    // ─── 입력값 검증 예외 ─────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidation(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("VALIDATION_FAILED", errorMessage));
    }

    // ─── 폴백 ─────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneral(Exception ex) {
        log.error("처리되지 않은 예외: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.onFailure("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
