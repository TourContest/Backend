package com.goodda.jejuday.common.exception;

import com.goodda.jejuday.auth.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import io.sentry.Sentry;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<String>> handleDuplicateEmailException(DuplicateEmailException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.onFailure("DUPLICATE_EMAIL", ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<String>> handleBadRequestException(BadRequestException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.onFailure(ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleUserNotFoundException(UserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.onFailure("USER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.onFailure(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.onFailure(ex.getMessage()));
    }

    @ExceptionHandler(KakaoAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleKakaoAuthException(KakaoAuthException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("KAKAO_AUTH_FAILED", "Kakao 인증 실패: " + e.getMessage()));
    }

    @ExceptionHandler(InsufficientGradeException.class)
    public ResponseEntity<ApiResponse<String>> handleInsufficientGradeException(InsufficientGradeException e) {
        log.warn("굿즈 구매 등급 부족: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.onFailure("INSUFFICIENT_GRADE", e.getMessage()));
    }

    @ExceptionHandler(InsufficientHallabongException.class)
    public ResponseEntity<ApiResponse<String>> handleInsufficientHallabongException(InsufficientHallabongException e) {
        log.warn("한라봉 포인트 부족: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure("INSUFFICIENT_HALLABONG", e.getMessage()));
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ApiResponse<String>> handleOutOfStockException(OutOfStockException e) {
        log.warn("상품 재고 부족: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure("OUT_OF_STOCK", e.getMessage()));
    }

    @ExceptionHandler(EmailSendingException.class)
    public ResponseEntity<ApiResponse<String>> handleEmailSendingException(EmailSendingException e) {
        log.error("이메일 발송 실패: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.onFailure("EMAIL_SEND_FAILED", "이메일 발송에 실패했습니다."));
    }

    @ExceptionHandler(CustomS3Exception.class)
    public ResponseEntity<ApiResponse<String>> handleCustomS3Exception(CustomS3Exception e) {
        log.error("S3 파일 처리 실패: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.onFailure("S3_ERROR", "파일 처리에 실패했습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("잘못된 요청입니다.");

        return ResponseEntity.badRequest().body(ApiResponse.onFailure(errorMessage));
    }

    /** 요청 본문 파싱 실패 (JSON 형식 오류, enum에 없는 값 등) */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<String>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("요청 본문 파싱 실패: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("INVALID_REQUEST_BODY", "요청 형식이 올바르지 않습니다."));
    }

    /** DB 제약조건 위반 (NOT NULL, UNIQUE 등) - 서비스 계층에서 못 걸러낸 경우의 방어선 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<String>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("DB 제약조건 위반: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("DATA_INTEGRITY_VIOLATION", "요청한 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneralException(Exception ex) {
        log.error("처리되지 않은 예외 발생: {}", ex.getMessage(), ex);
        Sentry.captureException(ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.onFailure("SERVER_ERROR", "서버 오류가 발생했습니다."));
    }

    /** 정적 리소스 미존재(robots.txt, favicon 등)는 장애가 아니므로 404 반환 + Sentry 제외 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.onFailure("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }
}
