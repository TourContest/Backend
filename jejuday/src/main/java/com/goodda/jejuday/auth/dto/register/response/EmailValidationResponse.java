package com.goodda.jejuday.auth.dto.register.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailValidationResponse {
    private String massage;
    private String email;
    private boolean isVerified;
}
