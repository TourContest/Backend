package com.goodda.jejuday.Auth.dto.register.request;

import com.goodda.jejuday.Auth.entity.Language;
import jakarta.validation.constraints.NotNull;
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
public class LanguageSelectRequest {

    @NotNull
    private Language language;
}
