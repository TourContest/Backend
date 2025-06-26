package com.goodda.jejuday.auth.dto.register.response;

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
public class UserProfileResponse {
    private String profile;

    public boolean isEmpty() {
        return profile == null || profile.trim().isEmpty();
    }
}
