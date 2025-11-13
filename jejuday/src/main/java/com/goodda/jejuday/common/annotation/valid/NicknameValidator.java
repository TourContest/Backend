package com.goodda.jejuday.common.annotation.valid;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class NicknameValidator implements ConstraintValidator<ValidNickname, String> {

    private static final Pattern KOREAN_PATTERN = Pattern.compile("^[가-힣0-9!_@-]{2,8}$");
    private static final Pattern ENGLISH_PATTERN = Pattern.compile("^[a-zA-Z0-9!_@-]{2,12}$");

    @Override
    public boolean isValid(String nickname, ConstraintValidatorContext context) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return false;
        }

        return KOREAN_PATTERN.matcher(nickname).matches() || ENGLISH_PATTERN.matcher(nickname).matches();
    }
}