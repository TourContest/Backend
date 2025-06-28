package com.goodda.jejuday.Auth.dto.register.request;

import com.goodda.jejuday.Auth.entity.Gender;
import com.goodda.jejuday.Auth.util.valid.ValidNickname;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class FinalAppRegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank(message = "닉네임은 필수입니다.")
    @ValidNickname
    private String nickname;

    private List<String> themes;

    private String profile;
}
