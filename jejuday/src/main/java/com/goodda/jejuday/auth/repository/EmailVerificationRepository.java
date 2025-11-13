package com.goodda.jejuday.auth.repository;

import com.goodda.jejuday.auth.entity.EmailVerification;
import com.goodda.jejuday.auth.entity.TemporaryUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findTopByTemporaryUser_EmailOrderByCreatedAtDesc(String email); //  인증 코드 유효성 검사

    Optional<EmailVerification> findTopByTemporaryUserAndIsVerifiedFalseOrderByCreatedAtDesc(
            TemporaryUser temporaryUser);   //  아직 인증되지 않은 코드 조회

    Optional<EmailVerification> findTopByTemporaryUserAndIsVerifiedTrueOrderByCreatedAtDesc(
            TemporaryUser temporaryUser);
    void deleteByTemporaryUser_TemporaryUserId(Long temporaryUserId);   //  인증 실패 누적 후 삭제

    void deleteByTemporaryUser_Email(String email); //  회원가입 취소 또는 만료 시 삭제

    Optional<EmailVerification> findByUser_Email(String email); //  비밀번호 재설정 등 정회원 인증용

    Optional<EmailVerification> findByTemporaryUser_Email(String email);    //  임시 회원의 인증 기록 조회

    // 새로운 회원가입 플로우를 위한 메서드들 (email 필드 기반)
    Optional<EmailVerification> findTopByEmailAndIsVerifiedFalseOrderByCreatedAtDesc(String email);
    Optional<EmailVerification> findTopByEmailAndIsVerifiedTrueOrderByCreatedAtDesc(String email);
    List<EmailVerification> findByEmailAndIsVerifiedFalse(String email);
}
