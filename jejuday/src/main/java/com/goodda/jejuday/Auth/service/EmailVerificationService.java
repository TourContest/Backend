package com.goodda.jejuday.Auth.service;

import com.goodda.jejuday.Auth.entity.TemporaryUser;
import com.goodda.jejuday.Auth.entity.User;

public interface EmailVerificationService {
    void deleteVerificationByTemporaryUserEmail(String email);

    void deleteVerificationByUserEmail(String email);

    void saveVerificationForTemporaryUser(TemporaryUser temporaryUser, String code);

    void saveVerificationForUser(User user, String code);

    boolean verifyTemporaryUserCode(String email, String code);

    boolean verifyUserCode(String email, String code);
}
