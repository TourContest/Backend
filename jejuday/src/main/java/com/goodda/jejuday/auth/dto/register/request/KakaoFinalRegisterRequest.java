package com.goodda.jejuday.auth.dto.register.request;

import com.goodda.jejuday.auth.entity.Gender;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KakaoFinalRegisterRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    private String email;

    //    private String code;
    private String nickname;
    private List<String> themes;
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @NotBlank(message = "출생연도는 필수입니다.")
    @Pattern(regexp = "^(19[0-9]{2}|20[0-2][0-9])$", message = "1900~2029 사이의 연도를 입력해주세요")
    private String birthYear;

    // 추천인 닉네임 (선택사항)
    @Size(max = 20, message = "추천인 닉네임은 20자 이하여야 합니다.")
    @Builder.Default
    private String referrerNickname = "제주데이";
}

