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
                new ProductSeed("흑돼지 돼랑이", "감귤 모자 흑돼지", ProductCategory.JEJU_TICON, 2000, 999, "ticon-orange-pig.png"),
                new ProductSeed("해녀 흑돼지", "해녀 흑돼지", ProductCategory.JEJU_TICON, 2000, 999, "ticon-haenyeo-pig.png"),
                new ProductSeed("감귤이", "한라산 등산 흑돼지", ProductCategory.JEJU_TICON, 2000, 999, "ticon-hallasan-pig.png")
        );
        products.forEach(this::upsert);
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
