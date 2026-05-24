package com.goodda.jejuday.auth.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.spot.dto.ReplyDTO;
import com.goodda.jejuday.spot.dto.SpotResponse;
import com.goodda.jejuday.spot.service.SpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MyPage", description = "마이페이지 관련 API")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/v1/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final SpotService spotService;

    @Operation(
            summary = "내가 쓴 게시글 조회",
            description = """
                    현재 사용자가 작성한 게시글 목록을 페이지네이션으로 조회합니다.
                    
                    **정렬 옵션:**
                    - `latest` (기본값): 최신순 정렬
                    - `views`: 조회수 많은 순 정렬
                    - `comments`: 댓글 많은 순 정렬 (표시만, 실제 구현 예정)
                    
                    **페이징 파라미터:**
                    - `page`: 페이지 번호 (0부터 시작, 기본값: 0)
                    - `size`: 페이지당 항목 수 (기본값: 20)
                    """
    )
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<Page<SpotResponse>>> getMyPosts(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(
                    description = "정렬 기준: latest(최신순), views(조회수 많은 순), comments(댓글 많은 순)", 
                    example = "latest",
                    schema = @Schema(allowableValues = {"latest", "views", "comments"}, defaultValue = "latest")
            )
            @RequestParam(value = "sort", defaultValue = "latest") String sort
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(spotService.getMyPosts(pageable, sort))
        );
    }

    @Operation(
            summary = "내가 쓴 댓글 조회",
            description = """
                    현재 사용자가 작성한 댓글 목록을 페이지네이션으로 조회합니다.
                    최신순으로 정렬됩니다.
                    
                    **페이징 파라미터:**
                    - `page`: 페이지 번호 (0부터 시작, 기본값: 0)
                    - `size`: 페이지당 항목 수 (기본값: 20)
                    """
    )
    @GetMapping("/comments")
    public ResponseEntity<ApiResponse<Page<ReplyDTO>>> getMyComments(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(spotService.getMyComments(pageable))
        );
    }

    @Operation(
            summary = "내가 좋아요한 글 조회",
            description = """
                    현재 사용자가 좋아요한 게시글 목록을 페이지네이션으로 조회합니다.
                    좋아요한 시간순(최신순)으로 정렬됩니다.
                    
                    **페이징 파라미터:**
                    - `page`: 페이지 번호 (0부터 시작, 기본값: 0)
                    - `size`: 페이지당 항목 수 (기본값: 20)
                    """
    )
    @GetMapping("/liked")
    public ResponseEntity<ApiResponse<Page<SpotResponse>>> getMyLikedSpots(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "페이지당 항목 수", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("likedAt").descending());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(spotService.getMyLikedSpots(pageable))
        );
    }
}

