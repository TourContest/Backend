package com.goodda.jejuday.pay.service;

import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.repository.ProductExchangeRepository;
import com.goodda.jejuday.pay.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductDataInitializer implements CommandLineRunner {

    private static final String IMAGE_BASE = "https://jejuday.duckdns.org/product-images/";

    private final ProductRepository productRepository;
    private final ProductExchangeRepository productExchangeRepository;

    @Override
    public void run(String... args) {
        List<ProductSeed> products = List.of(
                new ProductSeed("큐티 ver. 제주캐릭터 테디제주 인형/키링 3종", "제주 한라봉 아크릴 키링", ProductCategory.GOODS, 3500, 100, "hallabong-keyring.png"),
                new ProductSeed("제주 선물 추천 제주이야기 액상차 3종 세트", "제주 감귤 목도리 흑돼지 인형", ProductCategory.GOODS, 12000, 50, "black-pig-plush.png"),
                new ProductSeed("한라산의 기운을 담아 한라봉 매듭 팔찌", "돌하르방과 감귤 머그", ProductCategory.GOODS, 9000, 60, "stone-grandfather-mug.png"),
                new ProductSeed("제주 도르멍 돼지빵 2종 (황금돼지, 흑돼지)", "제주 바다 누빔 파우치", ProductCategory.GOODS, 8500, 80, "jeju-sea-pouch.png"),
                new ProductSeed("토마토 복주머니 스트링 누빔 퀼팅 파우치", "한라산 노을 텀블러", ProductCategory.GOODS, 16000, 40, "hallasan-tumbler.png"),
                new ProductSeed("핸드메이드 토끼 뜨개 스트링 파우치", "제주의 사계 엽서 4종 세트", ProductCategory.GOODS, 5000, 100, "jeju-postcard-set.png"),
                // 제휴 상점 할인 쿠폰 - 관광 소비를 유명 관광지 밖 제휴처로 분산시키는 게 목적.
                new ProductSeed("제주 흑돼지 카페 아메리카노 20% 할인", "제주 흑돼지 카페 아메리카노 20% 할인", ProductCategory.JEJU_TICON, 1500, 999, "ticon-cafe-discount.png"),
                new ProductSeed("조천 게스트하우스 1박 15% 할인", "조천 게스트하우스 1박 15% 할인", ProductCategory.JEJU_TICON, 5000, 300, "ticon-guesthouse-discount.png"),
                new ProductSeed("제주 렌터카 하루 대여 10% 할인", "제주 렌터카 하루 대여 10% 할인", ProductCategory.JEJU_TICON, 4000, 200, "ticon-rentcar-discount.png")
        );
        products.forEach(this::upsert);
        deleteDiscontinued();
    }

    // 실제 제휴 할인 쿠폰으로 대체된 캐릭터 전용 제주티콘 - 더 이상 시드하지 않고, 구매 이력이 없으면 정리한다.
    private static final List<String> DISCONTINUED_NAMES = List.of("감귤 모자 흑돼지", "해녀 흑돼지", "한라산 등산 흑돼지");

    private void deleteDiscontinued() {
        for (String name : DISCONTINUED_NAMES) {
            productRepository.findByName(name).ifPresent(product -> {
                if (productExchangeRepository.existsByProductId(product.getId())) {
                    // 이미 교환한 사람이 있으면 FK 제약 때문에 삭제 대신 재구매만 막는다.
                    product.setStock(0);
                    productRepository.save(product);
                } else {
                    productRepository.delete(product);
                }
            });
        }
    }

    private void upsert(ProductSeed seed) {
        Product product = productRepository.findByName(seed.name())
                .or(() -> productRepository.findByName(seed.previousName()))
                .orElseGet(Product::new);
        product.setName(seed.name());
        product.setCategory(seed.category());
        product.setHallabongCost(seed.cost());
        product.setStock(seed.stock());
        product.setImageUrl(IMAGE_BASE + seed.imageFile());
        productRepository.save(product);
    }

    private record ProductSeed(String previousName, String name, ProductCategory category,
                               int cost, int stock, String imageFile) {}
}
