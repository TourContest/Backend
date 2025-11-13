package com.goodda.jejuday.auth.dto.register.response;

import com.nimbusds.openid.connect.sdk.claims.Gender;
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
public class FinalAppRegisterResponse {
    private Long userId;
    private String email;
    private String nickname;
    private List<String> themes;
    private String birthYear;
    private String message;
    private Gender gender;
}
