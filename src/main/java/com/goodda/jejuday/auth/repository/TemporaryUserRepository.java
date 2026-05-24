package com.goodda.jejuday.auth.repository;

import com.goodda.jejuday.auth.entity.TemporaryUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemporaryUserRepository extends JpaRepository<TemporaryUser, Long> {
    Optional<TemporaryUser> findByEmail(String email);  // 인증

    boolean existsByEmail(String email);    //  중복확인

    void deleteByTemporaryUserId(Long temporaryUserId); //  수동 삭제

    List<TemporaryUser> findByCreatedAtBefore(LocalDateTime time);  //  스케줄러
}
