package com.goodda.jejuday.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodda.jejuday.auth.entity.UserTheme;
import com.goodda.jejuday.auth.repository.UserThemeRepository;
import com.goodda.jejuday.openai.OpenAiProperties;
import com.goodda.jejuday.openai.service.OpenAiEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * UserTheme find-or-create를 한 곳에서 처리한다(UserServiceImpl, KakaoService에서 각각
 * 중복 구현되던 로직 통합). 신규 테마 생성 시 추천 임베딩 매칭에 쓸 임베딩을 함께 계산해 캐싱한다 -
 * 테마는 재사용되는 소수 값이라 매번 계산할 필요가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserThemeResolver {

    private final UserThemeRepository userThemeRepository;
    private final OpenAiEmbeddingService embeddingService;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public UserTheme findOrCreate(String name) {
        return userThemeRepository.findByName(name)
                .orElseGet(() -> userThemeRepository.save(buildWithEmbedding(name)));
    }

    private UserTheme buildWithEmbedding(String name) {
        UserTheme theme = UserTheme.builder().name(name).build();
        try {
            float[] vector = embeddingService.embed(name);
            theme.setEmbeddingJson(objectMapper.writeValueAsString(vector));
            theme.setEmbeddingModel(openAiProperties.getEmbeddingModel());
        } catch (Exception e) {
            // 임베딩 실패해도 테마 생성/회원가입 자체는 막지 않음 - 나중에 배치로 재계산 가능
            log.warn("테마 임베딩 계산 실패 name={}: {}", name, e.toString());
        }
        return theme;
    }
}
