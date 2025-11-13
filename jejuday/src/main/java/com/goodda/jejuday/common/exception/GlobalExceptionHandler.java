package com.goodda.jejuday.common.exception;

import com.goodda.jejuday.auth.dto.ApiResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.context.support.DefaultMessageSourceResolvable;

@Slf4j
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

//    @ExceptionHandler(InsufficientHallabongException.class)
//    public ResponseEntity<Map<String, String>> handleInsufficientHallabong(InsufficientHallabongException e) {
//        return ResponseEntity.badRequest().body(Map.of(
//                "code", "HALLABONG_INSUFFICIENT",
//                "message", e.getMessage()
//        ));
//    }

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

//    @ExceptionHandler(OutOfStockException.class)
//    public ResponseEntity<Map<String, String>> handleStock(OutOfStockException e) {
//        return ResponseEntity.badRequest().body(Map.of(
//                "code", "OUT_OF_STOCK",
//                "message", e.getMessage()
//        ));
//    }
}
