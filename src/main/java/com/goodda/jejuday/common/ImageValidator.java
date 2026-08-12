package com.goodda.jejuday.common;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드 공통 검증.
 *
 * <p>정책
 * <ul>
 *   <li>허용 형식은 화이트리스트로 관리한다: JPEG / PNG / WEBP / GIF.</li>
 *   <li>모바일 클라이언트(React Native·Capacitor)는 파트의 Content-Type 을 비워두거나
 *       {@code application/octet-stream} 으로 보내는 경우가 많다. 이때 Content-Type 만 보고
 *       거절하면 정상 이미지도 막히므로, 확장자를 보조 판정에 사용한다.</li>
 *   <li>HEIC/HEIF(아이폰 기본 촬영 포맷)는 안드로이드·브라우저에서 렌더링되지 않으므로 계속 막는다.
 *       클라이언트에서 촬영/선택 직후 JPEG 으로 변환해 업로드해야 한다.</li>
 * </ul>
 *
 * {@link com.goodda.jejuday.spot.service.SpotServiceImpl},
 * {@link com.goodda.jejuday.spot.service.ChallengeActionService},
 * {@link com.goodda.jejuday.auth.service.impl.UserServiceImpl} 에서 공용으로 쓴다.
 */
public final class ImageValidator {

    /** 확장자 → 표준 MIME 타입. S3 메타데이터 보정에도 사용한다. */
    private static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/pjpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    /** 형식은 이미지지만 클라이언트/뷰어 호환이 안 되어 막는 포맷 */
    private static final Set<String> UNSUPPORTED_IMAGE_EXTENSIONS = Set.of("heic", "heif");
    private static final Set<String> UNSUPPORTED_CONTENT_TYPES = Set.of("image/heic", "image/heif");

    /** Content-Type 을 신뢰할 수 없다고 보고 확장자로 판정할 값들 */
    private static final Set<String> AMBIGUOUS_CONTENT_TYPES = Set.of(
            "application/octet-stream", "binary/octet-stream", "content/unknown", ""
    );

    private ImageValidator() {}

    public static void validate(MultipartFile file, String emptyMessage) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }

        String contentType = normalize(file.getContentType());
        String extension = extensionOf(file.getOriginalFilename());

        // 1) 렌더링 불가 포맷 우선 차단
        if (UNSUPPORTED_CONTENT_TYPES.contains(contentType) || UNSUPPORTED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "HEIC/HEIF 형식은 지원하지 않습니다. JPEG 또는 PNG로 변환 후 업로드해주세요.");
        }

        // 2) Content-Type 이 명확하면 그것으로, 애매하면 확장자로 판정
        boolean contentTypeOk = ALLOWED_CONTENT_TYPES.contains(contentType);
        boolean extensionOk = ALLOWED_EXTENSIONS.containsKey(extension);
        boolean contentTypeUnknown = AMBIGUOUS_CONTENT_TYPES.contains(contentType);

        if (contentTypeOk || (contentTypeUnknown && extensionOk)) {
            return;
        }

        throw new IllegalArgumentException(
                "지원하지 않는 이미지 형식입니다. JPG, PNG, WEBP, GIF만 업로드 가능합니다.");
    }

    /**
     * S3 에 저장할 Content-Type 을 결정한다.
     * 클라이언트가 Content-Type 을 비워 보내면 octet-stream 으로 저장되어
     * 브라우저·앱에서 이미지가 아닌 다운로드로 취급되므로 확장자 기준으로 보정한다.
     */
    public static String resolveContentType(MultipartFile file) {
        String contentType = normalize(file.getContentType());
        if (ALLOWED_CONTENT_TYPES.contains(contentType) && !contentType.equals("image/jpg")) {
            return contentType;
        }
        return ALLOWED_EXTENSIONS.getOrDefault(extensionOf(file.getOriginalFilename()), "image/jpeg");
    }

    private static String normalize(String contentType) {
        if (contentType == null) return "";
        int semicolon = contentType.indexOf(';');           // "image/jpeg; charset=..." 대응
        String value = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "";
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}