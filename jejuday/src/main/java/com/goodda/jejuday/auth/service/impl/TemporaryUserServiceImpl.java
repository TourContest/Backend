package com.goodda.jejuday.auth.service.impl;

import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.TemporaryUser;
import com.goodda.jejuday.auth.repository.TemporaryUserRepository;
import com.goodda.jejuday.auth.service.TemporaryUserService;
import com.goodda.jejuday.auth.util.exception.DuplicateEmailException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemporaryUserServiceImpl implements TemporaryUserService {

    private final TemporaryUserRepository temporaryUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void save(Language language, Platform platform, String name, String email, String passwordOrProfileUrl) {
        if (temporaryUserRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다.");
        }

        String encodedPassword = null;
        String profile = null;

        if (platform == Platform.APP) {
            encodedPassword = passwordEncoder.encode(passwordOrProfileUrl);
            profile = null;
        } else {
            encodedPassword = UUID.randomUUID().toString(); // ← 더미 비밀번호 강제로 설정
            profile = passwordOrProfileUrl;
        }


        TemporaryUser temporaryUser = TemporaryUser.builder()
                .language(language)
                .platform(platform)
                .name(name)
                .email(email)
                .password(encodedPassword)
                .profile(profile)
                .build();

        temporaryUserRepository.save(temporaryUser);
    }

    @Override
    public Optional<TemporaryUser> findByEmail(String email) {
        return temporaryUserRepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return temporaryUserRepository.existsByEmail(email);
    }

    @Override
    public void deleteByTemporaryUserId(Long temporaryUserId) {
        temporaryUserRepository.deleteByTemporaryUserId(temporaryUserId);
    }

    @Override
    public List<TemporaryUser> findByCreatedAtBefore(LocalDateTime time) {
        return temporaryUserRepository.findByCreatedAtBefore(time);
    }
}
