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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import java.util.stream.Collectors;

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

    @ExceptionHandler(InsufficientStepsException.class)
    public ResponseEntity<ApiResponse<String>> handleInsufficientStepsException(InsufficientStepsException e) {
        log.warn("걸음수 포인트 전환 잔액 부족: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure("INSUFFICIENT_STEPS", e.getMessage()));
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

    /*
     * ===== multipart(파일 업로드) 관련 =====
     * 아래 4건은 모두 "클라이언트 요청이 잘못된 경우"라서 4xx 로 내려야 한다.
     * 기존에는 핸들러가 없어 맨 아래 Exception 핸들러로 떨어지며 500 + Sentry 알림으로 잡혔음.
     */

    /** 업로드 용량 초과 - 413 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("업로드 용량 초과: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.onFailure("FILE_TOO_LARGE",
                        "이미지 용량이 너무 큽니다. 한 장당 10MB, 전체 35MB 이하로 올려주세요."));
    }

    /**
     * 필수 파트 누락 - 400.
     * 어떤 파트가 실제로 도착했는지 응답과 로그에 함께 남긴다.
     * (클라이언트가 필드명을 다르게 보내는 경우를 재현 없이 바로 특정하기 위함)
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<String>> handleMissingPart(MissingServletRequestPartException ex,
                                                                 HttpServletRequest request) {
        String received = receivedPartNames(request);
        log.warn("필수 파트 누락: 요구={}, 실제 수신={}", ex.getRequestPartName(), received);
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("MISSING_PART",
                        "필수 항목 '" + ex.getRequestPartName() + "' 이(가) 없습니다. (수신된 항목: " + received + ")"));
    }

    /** 파트에 Content-Type 이 없거나 지원하지 않는 형식으로 온 경우 - 400 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<String>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("지원하지 않는 Content-Type: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.onFailure("UNSUPPORTED_MEDIA_TYPE",
                        "요청 형식이 올바르지 않습니다. data 항목은 JSON 문자열로 보내주세요."));
    }

    /** multipart 파싱 자체 실패(끊긴 업로드, 잘못된 boundary 등) - 400 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<String>> handleMultipart(MultipartException ex) {
        log.warn("multipart 파싱 실패: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("INVALID_MULTIPART",
                        "파일 전송에 실패했습니다. 네트워크 상태를 확인 후 다시 시도해주세요."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<String>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure("MISSING_PARAMETER",
                        "필수 파라미터 '" + ex.getParameterName() + "' 이(가) 없습니다."));
    }

    /** 디버깅용: 실제로 도착한 파트 이름 목록 */
    private String receivedPartNames(HttpServletRequest request) {
        try {
            String names = request.getParts().stream()
                    .map(p -> p.getName() + (p.getSubmittedFileName() != null ? "(file)" : ""))
                    .collect(Collectors.joining(", "));
            return names.isBlank() ? "없음" : names;
        } catch (Exception e) {
            return "확인 불가";
        }
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