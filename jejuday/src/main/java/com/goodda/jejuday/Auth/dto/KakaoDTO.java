package com.goodda.jejuday.Auth.dto;


import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class KakaoDTO {

    private Long id;
    private String accountEmail;
    private String nickname;
    private String profileImageUrl;
}