package com.goodda.jejuday.auth.service;

import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.TemporaryUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TemporaryUserService {
    void save(Language language, Platform platform, String name, String email, String passwordOrProfileUrl);    //  저장

    Optional<TemporaryUser> findByEmail(String email);  // 인증

    boolean existsByEmail(String email);    //  중복확인

    void deleteByTemporaryUserId(Long temporaryUserId); //  수동 삭제

    List<TemporaryUser> findByCreatedAtBefore(LocalDateTime time);  //  스케줄러
}
