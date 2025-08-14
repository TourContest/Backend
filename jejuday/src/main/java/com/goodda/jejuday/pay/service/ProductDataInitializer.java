package com.goodda.jejuday.pay.service;

import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductDataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) return; // 이미 데이터가 있다면 실행하지 않음

        List<Product> products = List.of(
                // 제주굿즈 (tamnao)
                Product.builder().name("예쁜 돌하르방 이중 유리컵").category(ProductCategory.GOODS).hallabongCost(12000).stock(50).imageUrl("https://cdn.example.com/cup_double_glass.jpg").build(),
                Product.builder().name("예쁜 돌하르방 이중 미니컵").category(ProductCategory.GOODS).hallabongCost(6000).stock(80).imageUrl("https://cdn.example.com/cup_mini.jpg").build(),
                Product.builder().name("돌하르방 내열 샴페인잔 2P 세트").category(ProductCategory.GOODS).hallabongCost(30000).stock(30).imageUrl("https://cdn.example.com/cup_champagne.jpg").build(),
                Product.builder().name("성산일출봉 유리컵").category(ProductCategory.GOODS).hallabongCost(9000).stock(60).imageUrl("https://cdn.example.com/cup_seongsan.jpg").build(),
                Product.builder().name("제주 애퐁당 제주 캐릭터 볼펜 3종").category(ProductCategory.GOODS).hallabongCost(3500).stock(150).imageUrl("https://cdn.example.com/pen_character.jpg").build(),
                Product.builder().name("제주 민속식품 꿩엿 250g").category(ProductCategory.GOODS).hallabongCost(25000).stock(40).imageUrl("https://cdn.example.com/hwangyeot_250g.jpg").build(),
                Product.builder().name("제주 민속식품 꿩엿 650g").category(ProductCategory.GOODS).hallabongCost(65000).stock(20).imageUrl("https://cdn.example.com/hwangyeot_650g.jpg").build(),
                Product.builder().name("수제 귤잼").category(ProductCategory.GOODS).hallabongCost(10000).stock(70).imageUrl("https://cdn.example.com/orange_jam.jpg").build(),
                Product.builder().name("제주도다 돌하르방 인형키링").category(ProductCategory.GOODS).hallabongCost(6000).stock(100).imageUrl("https://cdn.example.com/keyring_harubang.jpg").build(),
                Product.builder().name("제주도다 캐릭터 미니소주잔").category(ProductCategory.GOODS).hallabongCost(3000).stock(120).imageUrl("https://cdn.example.com/mini_shot_glass.jpg").build(),

                // 제주티콘
                Product.builder().name("제주티콘 하르방").category(ProductCategory.JEJU_TICON).hallabongCost(500).stock(999).imageUrl("https://cdn.example.com/ticon_harubang.png").build(),
                Product.builder().name("제주티콘 귤").category(ProductCategory.JEJU_TICON).hallabongCost(500).stock(999).imageUrl("https://cdn.example.com/ticon_orange.png").build(),
                Product.builder().name("제주티콘 해녀").category(ProductCategory.JEJU_TICON).hallabongCost(700).stock(999).imageUrl("https://cdn.example.com/ticon_haenyeo.png").build(),
                Product.builder().name("제주티콘 까마귀").category(ProductCategory.JEJU_TICON).hallabongCost(600).stock(999).imageUrl("https://cdn.example.com/ticon_crow.png").build(),
                Product.builder().name("제주티콘 말").category(ProductCategory.JEJU_TICON).hallabongCost(600).stock(999).imageUrl("https://cdn.example.com/ticon_horse.png").build(),
                Product.builder().name("제주티콘 산방산").category(ProductCategory.JEJU_TICON).hallabongCost(500).stock(999).imageUrl("https://cdn.example.com/ticon_sanbang.png").build(),
                Product.builder().name("제주티콘 오름").category(ProductCategory.JEJU_TICON).hallabongCost(550).stock(999).imageUrl("https://cdn.example.com/ticon_oreum.png").build(),
                Product.builder().name("제주티콘 무지개").category(ProductCategory.JEJU_TICON).hallabongCost(500).stock(999).imageUrl("https://cdn.example.com/ticon_rainbow.png").build(),
                Product.builder().name("제주티콘 고래").category(ProductCategory.JEJU_TICON).hallabongCost(700).stock(999).imageUrl("https://cdn.example.com/ticon_whale.png").build(),
                Product.builder().name("제주티콘 귤꽃").category(ProductCategory.JEJU_TICON).hallabongCost(500).stock(999).imageUrl("https://cdn.example.com/ticon_flower.png").build()
        );

        productRepository.saveAll(products);
    }
}

