package com.goodda.jejuday.Auth.dto.register.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class EmailSenderRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String code;
}
