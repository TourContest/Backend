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

    private final ProductRepository productRepository;
    private final ProductExchangeRepository productExchangeRepository;

    @Override
    public void run(String... args) {
//        productExchangeRepository.deleteAllInBatch();
//        productRepository.deleteAllInBatch();

        if (productRepository.count() > 0) return; // 이미 데이터가 있다면 실행하지 않음

        List<Product> products = List.of(
                // 제주굿즈 (tamnao)
                Product.builder().name("큐티 ver. 제주캐릭터 테디제주 인형/키링 3종").category(ProductCategory.GOODS).hallabongCost(15000).stock(50).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/KakaoTalk_Image_2025-09-07-21-54-28_006.jpeg").build(),
                Product.builder().name("제주 선물 추천 제주이야기 액상차 3종 세트").category(ProductCategory.GOODS).hallabongCost(15000).stock(80).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/KakaoTalk_Image_2025-09-07-21-54-28_005.jpeg").build(),
                Product.builder().name("한라산의 기운을 담아 한라봉 매듭 팔찌").category(ProductCategory.GOODS).hallabongCost(15000).stock(30).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/KakaoTalk_Image_2025-09-07-21-54-28_004.jpeg").build(),
                Product.builder().name("제주 도르멍 돼지빵 2종 (황금돼지, 흑돼지)").category(ProductCategory.GOODS).hallabongCost(15000).stock(60).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/KakaoTalk_Image_2025-09-07-21-54-28_002.jpeg").build(),
                Product.builder().name("토마토 복주머니 스트링 누빔 퀼팅 파우치").category(ProductCategory.GOODS).hallabongCost(15000).stock(150).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/KakaoTalk_Image_2025-09-07-21-54-28_003.jpeg").build(),
                Product.builder().name("핸드메이드 토끼 뜨개 스트링 파우치").category(ProductCategory.GOODS).hallabongCost(15000).stock(40).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/KakaoTalk_Image_2025-09-07-21-54-28_001.jpeg").build(),

                // 제주티콘
                Product.builder().name("흑돼지 돼랑이").category(ProductCategory.JEJU_TICON).hallabongCost(2000).stock(999).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/%E1%84%89%E1%85%B3%E1%84%8F%E1%85%B3%E1%84%85%E1%85%B5%E1%86%AB%E1%84%89%E1%85%A3%E1%86%BA+2025-09-07+%E1%84%8B%E1%85%A9%E1%84%92%E1%85%AE+9.46.13.png").build(),
                Product.builder().name("해녀 흑돼지").category(ProductCategory.JEJU_TICON).hallabongCost(2000).stock(999).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/%E1%84%89%E1%85%B3%E1%84%8F%E1%85%B3%E1%84%85%E1%85%B5%E1%86%AB%E1%84%89%E1%85%A3%E1%86%BA+2025-09-07+%E1%84%8B%E1%85%A9%E1%84%92%E1%85%AE+9.46.23.png").build(),
                Product.builder().name("감귤이").category(ProductCategory.JEJU_TICON).hallabongCost(2000).stock(999).imageUrl("https://jejudaybucket123.s3.ap-northeast-2.amazonaws.com/item-images/%E1%84%89%E1%85%B3%E1%84%8F%E1%85%B3%E1%84%85%E1%85%B5%E1%86%AB%E1%84%89%E1%85%A3%E1%86%BA+2025-09-07+%E1%84%8B%E1%85%A9%E1%84%92%E1%85%AE+9.46.39.png").build()
        );

        productRepository.saveAll(products);
    }
}

