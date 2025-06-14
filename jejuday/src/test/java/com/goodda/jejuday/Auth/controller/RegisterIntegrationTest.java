package com.goodda.jejuday.Auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodda.jejuday.Auth.dto.register.request.EmailSenderRequest;
import com.goodda.jejuday.Auth.dto.register.request.EmailValidationRequest;
import com.goodda.jejuday.Auth.dto.register.request.FinalAppRegisterRequest;
import com.goodda.jejuday.Auth.dto.register.request.TempAppRegisterRequest;
import com.goodda.jejuday.Auth.entity.Language;
import com.goodda.jejuday.Auth.entity.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RegisterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. 임시 가입 성공")
    void registerAppUser_success() throws Exception {
        TempAppRegisterRequest request = TempAppRegisterRequest.builder()
                .name("홍길동")
                .email("temp@jejuday.com")
                .password("Test1234!")
                .platform(Platform.APP)
                .language(Language.KOREAN)
                .build();

        mockMvc.perform(post("/v1/users/register/app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("2. 인증 이메일 전송")
    void sendVerificationEmail_success() throws Exception {
        EmailValidationRequest request = EmailValidationRequest.builder()
                .email("temp@jejuday.com")
                .build();

        mockMvc.perform(post("/v1/users/register/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("3. 인증코드 확인 성공")
    void verifyEmail_success() throws Exception {
        EmailSenderRequest request = EmailSenderRequest.builder()
                .email("temp@jejuday.com")
                .code("123456") // 실제 로직에서는 mock 또는 DB 설정 필요
                .build();

        mockMvc.perform(post("/v1/users/register/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("4. 최종 회원가입 완료")
    void completeFinalRegistration_success() throws Exception {
        FinalAppRegisterRequest data = FinalAppRegisterRequest.builder()
                .email("temp@jejuday.com")
                .nickname("테스트닉")
                .themes(List.of("산책", "힐링"))
                .build();

        MockMultipartFile json = new MockMultipartFile(
                "data", "data.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(data)
        );

        MockMultipartFile image = new MockMultipartFile(
                "profile", "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake image data".getBytes()
        );

        mockMvc.perform(multipart("/v1/users/register/final")
                        .file(json)
                        .file(image))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }
}
