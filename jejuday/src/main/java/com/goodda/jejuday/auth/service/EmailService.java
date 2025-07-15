package com.goodda.jejuday.auth.service;

import com.goodda.jejuday.auth.entity.TemporaryUser;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.TemporaryUserRepository;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.EmailSendingException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final TemporaryUserRepository temporaryUserRepository;
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;

    private final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void sendRegistrationVerificationEmail(String email) {
        TemporaryUser user = temporaryUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("임시 사용자를 찾을 수 없습니다."));

        emailVerificationService.deleteVerificationByTemporaryUserEmail(email);
        String code = generateCode();
        emailVerificationService.saveVerificationForTemporaryUser(user, code);

        sendEmail(email, "회원가입 인증코드", buildHtml(code));
    }

    public void sendPasswordResetVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        emailVerificationService.deleteVerificationByUserEmail(email);
        String code = generateCode();
        emailVerificationService.saveVerificationForUser(user, code);

        sendEmail(email, "비밀번호 재설정 인증코드", buildHtml(code));
    }

    private void sendEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);

            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage(), e);
            throw new EmailSendingException("이메일 전송에 실패했습니다.");
        }
    }

    private String generateCode() {
        return String.format("%06d", new Random().nextInt(1_000_000));
    }

    private String buildHtml(String code) {
        Context context = new Context();
        context.setVariable("verificationCode", code);
        return templateEngine.process("verificationEmail", context);
    }
}