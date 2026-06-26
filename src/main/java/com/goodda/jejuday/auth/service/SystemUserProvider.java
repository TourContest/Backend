package com.goodda.jejuday.auth.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.spot.tourapi.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SystemUserProvider {

    private final UserRepository userRepository;
    private final AppProperties props;

    @Transactional
    public User getOrCreate() {
        return userRepository.findByEmail(props.getSystemUserEmail())
            .orElseGet(() -> {
                User u = new User();
                u.setEmail(props.getSystemUserEmail());
                u.setName(props.getSystemUserName());
                // 필요 기본값 세팅 (platform/role 등)
                return userRepository.save(u);
            });
    }
}