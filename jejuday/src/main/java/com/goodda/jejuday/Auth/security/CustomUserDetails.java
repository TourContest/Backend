package com.goodda.jejuday.Auth.security;

import com.goodda.jejuday.Auth.entity.User;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Getter
public class CustomUserDetails extends org.springframework.security.core.userdetails.User {
    private final Long userId;

    public CustomUserDetails(User user) {
        super(
                user.getEmail(),               // username 으로 이메일
                user.getPassword(),            // password
                List.of(new SimpleGrantedAuthority("USER"))
        );
        this.userId = user.getId();
    }
}