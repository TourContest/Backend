package com.goodda.jejuday.auth.util.exception;

import com.goodda.jejuday.auth.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.context.support.DefaultMessageSourceResolvable;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<String>> handleDuplicateEmailException(DuplicateEmailException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)  // 409 Conflict
                .body(ApiResponse.onFailure(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.onFailure(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneralException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.onFailure("서버 오류가 발생했습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("잘못된 요청입니다.");

        return ResponseEntity.badRequest().body(ApiResponse.onFailure(errorMessage));
    }

    @ExceptionHandler(KakaoAuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleKakaoAuthException(KakaoAuthException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("Kakao 인증 실패: " + e.getMessage()));
    }
}
