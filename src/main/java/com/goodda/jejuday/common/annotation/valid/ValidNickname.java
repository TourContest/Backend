package com.goodda.jejuday.common.annotation.valid;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = NicknameValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNickname {
    String message() default "닉네임 형식이 올바르지 않습니다. (한글 조합은 2~8자, 영문 조합은 2~12자, 특수문자는 ! _ @ - 만 허용)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}