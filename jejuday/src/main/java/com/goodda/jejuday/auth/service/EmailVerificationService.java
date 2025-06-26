package com.goodda.jejuday.auth.service;

import com.goodda.jejuday.auth.entity.TemporaryUser;
import com.goodda.jejuday.auth.entity.User;

public interface EmailVerificationService {
    void deleteVerificationByTemporaryUserEmail(String email);

    void deleteVerificationByUserEmail(String email);

    void saveVerificationForTemporaryUser(TemporaryUser temporaryUser, String code);

    void saveVerificationForUser(User user, String code);

    boolean verifyTemporaryUserCode(String email, String code);

    boolean verifyUserCode(String email, String code);
}
