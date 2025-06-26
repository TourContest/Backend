package com.goodda.jejuday.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodda.jejuday.auth.dto.register.request.FinalAppRegisterRequest;
import com.goodda.jejuday.auth.dto.KakaoDTO;
import com.goodda.jejuday.auth.util.exception.KakaoAuthException;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kakao.client.id}")
    private String kakaoClientId;

    @Value("${kakao.client.secret}")
    private String kakaoClientSecret;

    @Value("${kakao.redirect.url}")
    private String kakaoRedirectUrl;

    private static final String KAKAO_AUTH_URI = "https://kauth.kakao.com";
    private static final String KAKAO_API_URI = "https://kapi.kakao.com";
    private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

    public String getKakaoLoginUrl() {
        return String.format("%s/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code",
                KAKAO_AUTH_URI, kakaoClientId, kakaoRedirectUrl);
    }

    public KakaoDTO getKakaoUserInfo(String code) {
        validateAuthorizationCode(code);

        String accessToken = getAccessToken(code);
        return getUserInfoWithToken(accessToken);
    }

    private void validateAuthorizationCode(String code) {
        if (StringUtils.isBlank(code)) {
            throw new KakaoAuthException("Authorization code is required");
        }
    }

    private String getAccessToken(String code) {
        try {
            HttpHeaders headers = createFormHeaders();
            MultiValueMap<String, String> params = createTokenRequestParams(code);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    KAKAO_AUTH_URI + "/oauth/token",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            return parseAccessTokenFromResponse(response.getBody());

        } catch (Exception e) {
            log.error("Failed to get access token from Kakao", e);
            throw new KakaoAuthException("Failed to get access token", e);
        }
    }

    private HttpHeaders createFormHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private MultiValueMap<String, String> createTokenRequestParams(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("client_secret", kakaoClientSecret);
        params.add("code", code);
        params.add("redirect_uri", kakaoRedirectUrl);
        return params;
    }

    private String parseAccessTokenFromResponse(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            log.error("Failed to parse access token from response", e);
            throw new KakaoAuthException("Failed to parse token response", e);
        }
    }

    private KakaoDTO getUserInfoWithToken(String accessToken) {
        try {
            HttpHeaders headers = createBearerHeaders(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    KAKAO_API_URI + "/v2/user/me",
                    HttpMethod.GET,
                    request,
                    String.class
            );

            return parseUserInfoFromResponse(response.getBody());

        } catch (Exception e) {
            log.error("Failed to get user info from Kakao", e);
            throw new KakaoAuthException("Failed to get user info", e);
        }
    }

    private HttpHeaders createBearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private KakaoDTO parseUserInfoFromResponse(String responseBody) {
        try {
            log.info("Kakao user response: {}", responseBody);

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode account = jsonNode.get("kakao_account");
            JsonNode profile = account.get("profile");

            long id = jsonNode.get("id").asLong();
            String email = account.has("email") ? account.get("email").asText() : null;
            String nickname = profile.has("nickname") ? profile.get("nickname").asText() : null;
            String profileImageUrl =
                    profile.has("profile_image_url") ? profile.get("profile_image_url").asText() : null;

            return KakaoDTO.builder()
                    .id(id)
                    .accountEmail(email)
                    .nickname(nickname)
                    .profileImageUrl(profileImageUrl)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse user info from response", e);
            throw new KakaoAuthException("Failed to parse user info", e);
        }
    }

    public FinalAppRegisterRequest convertToFinalRequest(KakaoDTO kakaoDTO) {
        return FinalAppRegisterRequest.builder()
                .email(kakaoDTO.getAccountEmail())
                .nickname(kakaoDTO.getNickname())
                .profile(kakaoDTO.getProfileImageUrl())
                .build();
    }

}