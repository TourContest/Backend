package com.goodda.jejuday.Auth.repository;

import com.goodda.jejuday.Auth.entity.EmailVerification;
import com.goodda.jejuday.Auth.entity.TemporaryUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findTopByTemporaryUser_EmailOrderByCreatedAtDesc(String email); //  인증 코드 유효성 검사

    Optional<EmailVerification> findTopByTemporaryUserAndIsVerifiedFalseOrderByCreatedAtDesc(
            TemporaryUser temporaryUser);   //  아직 인증되지 않은 코드 조회

    void deleteByTemporaryUser_TemporaryUserId(Long temporaryUserId);   //  인증 실패 누적 후 삭제

    void deleteByTemporaryUser_Email(String email); //  회원가입 취소 또는 만료 시 삭제

    Optional<EmailVerification> findByUser_Email(String email); //  비밀번호 재설정 등 정회원 인증용

    Optional<EmailVerification> findByTemporaryUser_Email(String email);    //  임시 회원의 인증 기록 조회
}
