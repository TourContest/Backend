package com.goodda.jejuday.pay.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.pay.dto.ProductDetailDto;
import com.goodda.jejuday.pay.dto.ProductDto;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
@Tag(name = "상품 API", description = "상품 조회 및 교환 관련 API")
public class ProductController {

    private final ProductService productService;

    @PostMapping("/{productId}/exchange")
    @Operation(summary = "상품 교환", description = "사용자가 보유한 포인트로 상품을 교환합니다.")
    public ResponseEntity<ApiResponse<String>> exchangeProduct(
            @Parameter(description = "상품 ID") @PathVariable Long productId,
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
        productService.exchangeProduct(userId, productId);
        return ResponseEntity.ok(ApiResponse.onSuccess("상품 교환 완료"));
    }

    @GetMapping
    @Operation(summary = "상품 목록 조회", description = "카테고리에 따른 상품 리스트를 조회합니다.")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getProducts(
            @Parameter(description = "상품 카테고리") @RequestParam ProductCategory category) {
        List<ProductDto> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(ApiResponse.onSuccess(products));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "(구매전) 단일 상품 조회", description = "상품 ID로 특정 상품 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(
            @Parameter(description = "상품 ID") @PathVariable Long productId) {
        ProductDto product = productService.getProduct(productId);
        return ResponseEntity.ok(ApiResponse.onSuccess(product));
    }

    @Operation(summary = "구매한 상품 보관함 조회", description = "사용자가 교환한 상품 목록을 전체 조회합니다.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ProductDetailDto>>> getMyProducts(
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
        List<ProductDetailDto> products = productService.getUserProductHistory(userId);
        return ResponseEntity.ok(ApiResponse.onSuccess(products));
    }

    @GetMapping("/exchanges/{exchangeId}/detail")
    @Operation(summary = "교환 완료 상품 상세 조회", description = "상품 교환 내역 ID로 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ProductDetailDto>> getProductDetailByExchange(
            @Parameter(description = "교환 ID") @PathVariable Long exchangeId) {
        ProductDetailDto detail = productService.getProductDetailByExchange(exchangeId);
        return ResponseEntity.ok(ApiResponse.onSuccess(detail));
    }

    @PostMapping("/{productId}/accept-toggle")
    @Operation(summary = "상품 수락 상태 토글", description = "상품의 수락 상태를 토글합니다.")
    public ResponseEntity<ApiResponse<String>> toggleProductAccepted(
            @Parameter(description = "상품 ID") @PathVariable Long productId) {
        productService.toggleProductAccepted(productId);
        return ResponseEntity.ok(ApiResponse.onSuccess("상품 수락 상태 변경 완료"));
    }

    @Operation(summary = "미수령 상품 목록 조회", description = "사용자가 교환했지만 아직 수령하지 않은 상품 목록을 조회합니다.")
    @GetMapping("/my/unaccepted")
    public ResponseEntity<ApiResponse<List<ProductDetailDto>>> getMyUnacceptedProducts(
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
        List<ProductDetailDto> products = productService.getUserUnacceptedProductHistory(userId);
        return ResponseEntity.ok(ApiResponse.onSuccess(products));
    }
}
