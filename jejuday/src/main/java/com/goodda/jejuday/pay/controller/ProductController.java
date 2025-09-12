package com.goodda.jejuday.pay.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.pay.dto.GoodsEligibilityResponse;
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
    @Operation(summary = "상품 교환", description = "사용자가 보유한 포인트로 상품을 교환합니다. 굿즈는 4만보 이상 등급만 구매 가능합니다.")
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

//    @GetMapping("/available")
//    @Operation(summary = "구매 가능한 상품 목록 조회", description = "사용자 등급에 따라 구매 가능한 상품 리스트를 조회합니다.")
//    public ResponseEntity<ApiResponse<List<ProductDto>>> getAvailableProducts(
//            @Parameter(description = "사용자 ID") @RequestParam Long userId,
//            @Parameter(description = "상품 카테고리") @RequestParam ProductCategory category) {
//        List<ProductDto> products = productService.getAvailableProductsByCategory(userId, category);
//        return ResponseEntity.ok(ApiResponse.onSuccess(products));
//    }

    @GetMapping("/{productId}")
    @Operation(summary = "(구매전) 단일 상품 조회", description = "상품 ID로 특정 상품 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(
            @Parameter(description = "상품 ID") @PathVariable Long productId) {
        ProductDto product = productService.getProduct(productId);
        return ResponseEntity.ok(ApiResponse.onSuccess(product));
    }

//    @GetMapping("/goods/eligibility")
//    @Operation(summary = "굿즈 구매 자격 확인", description = "사용자의 굿즈 구매 자격과 필요한 걸음수를 확인합니다.")
//    public ResponseEntity<ApiResponse<GoodsEligibilityResponse>> checkGoodsEligibility(
//            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
//
//        boolean canPurchase = productService.canUserPurchaseGoods(userId);
//        long stepsNeeded = productService.getStepsNeededForGoods(userId);
//
//        GoodsEligibilityResponse response = new GoodsEligibilityResponse(
//                canPurchase,
//                stepsNeeded,
//                canPurchase ? "굿즈 구매 가능" : "오름꾼 등급(4만보) 달성 후 구매 가능"
//        );
//
//        return ResponseEntity.ok(ApiResponse.onSuccess(response));
//    }

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

    @PostMapping("/{exchangeId}/accept-toggle")
    @Operation(summary = "상품 수락 상태 토글", description = "상품의 수락 상태를 토글합니다.")
    public ResponseEntity<ApiResponse<String>> toggleProductAccepted(
            @Parameter(description = "상품 ID") @PathVariable Long exchangeId) {
        productService.toggleProductAccepted(exchangeId);
        return ResponseEntity.ok(ApiResponse.onSuccess("상품 수락 상태 변경 완료"));
    }

    @Operation(summary = "미수령 상품 목록 조회", description = "사용자가 교환했지만 아직 수령하지 않은 상품 목록을 조회합니다.")
    @GetMapping("/my/unaccepted")
    public ResponseEntity<ApiResponse<List<ProductDetailDto>>> getMyUnacceptedProducts(
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
        List<ProductDetailDto> products = productService.getUserUnacceptedProductHistory(userId);
        return ResponseEntity.ok(ApiResponse.onSuccess(products));
    }
    
    @Operation(summary = "구매한 굿즈 목록 조회", description = "사용자가 교환한 굿즈 상품 목록을 조회합니다.")
    @GetMapping("/my/goods")
    public ResponseEntity<ApiResponse<List<ProductDetailDto>>> getMyGoodsProducts(
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
        List<ProductDetailDto> products = productService.getUserProductHistoryByCategory(userId, ProductCategory.GOODS);
        return ResponseEntity.ok(ApiResponse.onSuccess(products));
    }

    @Operation(summary = "구매한 제주티콘 목록 조회", description = "사용자가 교환한 제주티콘 상품 목록을 조회합니다.")
    @GetMapping("/my/jeju-ticon")
    public ResponseEntity<ApiResponse<List<ProductDetailDto>>> getMyJejuTiconProducts(
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
        List<ProductDetailDto> products = productService.getUserProductHistoryByCategory(userId, ProductCategory.JEJU_TICON);
        return ResponseEntity.ok(ApiResponse.onSuccess(products));
    }
}