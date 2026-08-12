package com.goodda.jejuday.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @Configuration 이 빠져 있어 그동안 이 설정 전체(CORS + multipart 컨버터 우선순위)가
 * 적용되지 않고 있었다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 커스텀 Multipart 메시지 컨버터를 주입받음
    private final MultipartJackson2HttpMessageConverter multipartConverter;

    // 생성자 주입을 통해 빈을 받아옴
    public WebConfig(MultipartJackson2HttpMessageConverter multipartConverter) {
        this.multipartConverter = multipartConverter;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")   // allowCredentials(true) 와 allowedOrigins("*") 는 함께 못 씀
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Custom-Header")
                .allowCredentials(true)
                .maxAge(3600);
    }

    // extendMessageConverters를 오버라이드하여 커스텀 컨버터를 등록함
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 커스텀 컨버터를 가장 앞에 추가하여 우선순위를 높임
        converters.add(0, multipartConverter);
    }
}