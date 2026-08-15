package com.goodda.jejuday.auth.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.dto.BlockedUserResponse;
import com.goodda.jejuday.auth.service.UserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User Block", description = "사용자 차단 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserBlockController {

    private final UserBlockService userBlockService;

    @Operation(summary = "사용자 차단", description = "해당 유저를 차단합니다. 차단하면 이 유저의 글/댓글이 내 피드·검색·댓글 목록에서 보이지 않습니다.")
    @PostMapping("/{id}/block")
    public ResponseEntity<ApiResponse<String>> block(@PathVariable Long id) {
        userBlockService.block(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess("차단되었습니다."));
    }

    @Operation(summary = "사용자 차단 해제", description = "차단을 해제합니다.")
    @DeleteMapping("/{id}/block")
    public ResponseEntity<ApiResponse<String>> unblock(@PathVariable Long id) {
        userBlockService.unblock(id);
        return ResponseEntity.ok(ApiResponse.onSuccess("차단이 해제되었습니다."));
    }

    @Operation(summary = "차단한 사용자 목록 조회", description = "내가 차단한 사용자 목록을 조회합니다.")
    @GetMapping("/blocked")
    public ResponseEntity<ApiResponse<List<BlockedUserResponse>>> getBlockedUsers() {
        return ResponseEntity.ok(ApiResponse.onSuccess(userBlockService.getBlockedUsers()));
    }
}
