package com.goodda.jejuday.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // null 값은 JSON에서 제외
@Schema(description = "API 응답 공통 형식")
public class ApiResponse<T> {

    @Schema(description = "요청 성공 여부", example = "true")
    private boolean success;

    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
    private String message;

    @Schema(description = "응답 데이터")
    private T data;

    @Schema(description = "에러 코드 (실패 시)", example = "USER_NOT_FOUND")
    private String errorCode;

    @Schema(description = "응답 시간", example = "2025-08-10T14:30:00")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // 성공 응답 생성 메서드들 - 메서드명으로 명확하게 구분

    // 1. 제네릭 데이터용
    public static <T> ApiResponse<T> onSuccess(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("요청이 성공적으로 처리되었습니다.")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 2. 데이터 없는 성공 응답
    public static ApiResponse<Object> onSuccess() {
        return ApiResponse.<Object>builder()
                .success(true)
                .message("요청이 성공적으로 처리되었습니다.")
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 3. 커스텀 메시지 + 데이터
    public static <T> ApiResponse<T> onSuccess(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 4. 커스텀 메시지만 (데이터 없음) - 메서드명 변경
    public static ApiResponse<Object> onSuccessWithMessage(String message) {
        return ApiResponse.<Object>builder()
                .success(true)
                .message(message)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 실패 응답 생성 메서드들
    public static <T> ApiResponse<T> onFailure(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> onFailure(String errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> onFailure(String errorCode, String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Void 타입 전용 메서드 추가
    public static ApiResponse<Void> onSuccessVoid() {
        return ApiResponse.<Void>builder()
                .success(true)
                .message("요청이 성공적으로 처리되었습니다.")
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ApiResponse<Void> onSuccessVoid(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 편의 메서드들
    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public boolean hasData() {
        return data != null;
    }

    public boolean hasErrorCode() {
        return errorCode != null && !errorCode.trim().isEmpty();
    }

    // 체이닝을 위한 메서드들
    public ApiResponse<T> withErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public ApiResponse<T> withTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    @Override
    public String toString() {
        return String.format("ApiResponse{success=%s, message='%s', errorCode='%s', hasData=%s, timestamp=%s}",
                success, message, errorCode, hasData(), timestamp);
    }
}