package com.goodda.jejuday.auth.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.goodda.jejuday.auth.security.JwtRequestFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtRequestFilter jwtRequestFilter;

//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//
//        http
//                .csrf(csrf -> csrf.disable())
//                .cors(withDefaults())
//                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
//                .authorizeHttpRequests(auth -> auth
//                        // Public routes
//                        .requestMatchers(
//                                "/",
//                                "/swagger-ui/**",
//                                "/v3/api-docs/**",
//                                "/ws/**",
//                                "/index.html",
//                                "/assets/**",
//                                "/favicon.ico",
//                                "/splash",
//                                "/register",
//                                "/login",
//                                "/mypage",
//                                "/notifications/test-send",
//                                "/notifications/*/fcm-token",
//                                "/v1/**"
//                        ).permitAll()
//
//                        // Admin-only routes
//                        .requestMatchers("/admin/**").hasRole("ADMIN")
//
//                        // Authenticated user routes
//                        .requestMatchers(
//                                "/openchat/**",
//                                "/private/**",
//                                "/user/**"
//                        ).hasAnyRole("USER", "ADMIN")
//
//                        // Everything else requires auth
//                        .anyRequest().authenticated()
//                )
//                .exceptionHandling(config -> config
//                        .authenticationEntryPoint(new Http403ForbiddenEntryPoint())
//                        .accessDeniedHandler(accessDeniedHandler())
//                );
//
//        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                // JWT 쿠키 기반 인증만 사용하므로 STATELESS로 고정한다.
                // IF_REQUIRED였을 때는 로그인 시 서버 세션(JSESSIONID)에 SecurityContext가 저장되고,
                // 로그아웃이 그 세션을 무효화하지 않아 재로그인 후에도 이전 계정의 인증이
                // 세션에서 복원되어 우선 적용되는 문제가 있었다 (JwtRequestFilter는 컨텍스트가
                // 비어있을 때만 쿠키의 JWT를 적용함).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // 🔓 모든 요청 허용
                )
                .exceptionHandling(config -> config
                        .authenticationEntryPoint(new Http403ForbiddenEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                );

         http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    // CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:8080",
                "http://localhost:5173",
                "https://jejuday.duckdns.org"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // 접근 거부 핸들러
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access Denied!");
        };
    }

    // 비밀번호 암호화 (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
