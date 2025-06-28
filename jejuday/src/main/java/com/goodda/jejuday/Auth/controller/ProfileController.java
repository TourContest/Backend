package com.goodda.jejuday.Auth.controller;

import com.goodda.jejuday.Auth.dto.ApiResponse;
import com.goodda.jejuday.Auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Profile", description = "프로필 이미지 관련 API")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/v1/users/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @Operation(summary = "프로필 이미지 업데이트", description = "새로운 프로필 이미지를 업로드하고 기존 이미지를 삭제합니다.")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> updateProfileImage(
            @RequestPart("newProfileImage") MultipartFile newProfileImage) {
        Long userId = userService.getAuthenticatedUserId();
        String existingProfileUrl = userService.getProfileImageUrl(userId);
        if (existingProfileUrl != null && !existingProfileUrl.isEmpty()) {
            userService.deleteFile(existingProfileUrl);
        }
        String newProfileUrl = userService.uploadProfileImage(newProfileImage);
        userService.updateUserProfileImage(userId, newProfileUrl);
        return new ResponseEntity<>(ApiResponse.onSuccess("프로필 이미지가 업데이트되었습니다."), HttpStatus.OK);
    }

    @Operation(summary = "프로필 이미지 삭제", description = "현재 설정된 프로필 이미지를 삭제합니다.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<String>> deleteProfileImage() {
        Long userId = userService.getAuthenticatedUserId();
        String existingProfileUrl = userService.getProfileImageUrl(userId);
        if (existingProfileUrl == null || existingProfileUrl.isEmpty()) {
            throw new IllegalArgumentException("삭제할 프로필 이미지가 존재하지 않습니다.");
        }
        userService.deleteFile(existingProfileUrl);
        userService.updateUserProfileImage(userId, null);
        return new ResponseEntity<>(ApiResponse.onSuccess("프로필 이미지가 삭제되었습니다."), HttpStatus.OK);
    }

    @Operation(summary = "프로필 이미지 조회", description = "현재 사용자의 프로필 이미지 URL을 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<String>> getProfileImage() {
        Long userId = userService.getAuthenticatedUserId();
        String imageUrl = userService.getProfileImageUrl(userId);
        return new ResponseEntity<>(ApiResponse.onSuccess(imageUrl), HttpStatus.OK);
    }
}
