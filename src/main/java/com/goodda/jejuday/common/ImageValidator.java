package com.goodda.jejuday.common;

import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드 공통 검증. HEIC/HEIF(아이폰 기본 촬영 포맷)는 대부분의 브라우저/안드로이드에서
 * 렌더링이 안 되므로 업로드 시점에 막는다 - {@link com.goodda.jejuday.spot.service.SpotServiceImpl},
 * {@link com.goodda.jejuday.spot.service.ChallengeActionService}에서 공용으로 쓴다.
 */
public final class ImageValidator {

    private static final Set<String> UNSUPPORTED_IMAGE_EXTENSIONS = Set.of("heic", "heif");

    private ImageValidator() {}

    public static void validate(MultipartFile file, String emptyMessage) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase()
                : "";
        boolean isHeic = contentType.equalsIgnoreCase("image/heic")
                || contentType.equalsIgnoreCase("image/heif")
                || UNSUPPORTED_IMAGE_EXTENSIONS.contains(extension);
        if (isHeic) {
            throw new IllegalArgumentException("HEIC/HEIF 형식은 지원하지 않습니다. JPEG 또는 PNG로 업로드해주세요.");
        }
    }
}
