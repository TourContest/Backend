package com.goodda.jejuday.auth.dto.register.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class TempAppRegisterRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
            regexp = "^(?=.*[a-zA-Z\\d])(?=.*[!_@-])[a-zA-Z\\d!_@-]{8,12}$",
            message = "비밀번호는 8~12자 이내이며, 영문 또는 숫자를 포함하고, 특수문자(!, _, @, -)를 하나 이상 포함해야 합니다."
    )
    private String password;
}
