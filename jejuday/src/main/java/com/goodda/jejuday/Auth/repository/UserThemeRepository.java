package com.goodda.jejuday.Auth.repository;

import com.goodda.jejuday.Auth.entity.UserTheme;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserThemeRepository extends JpaRepository<UserTheme, Long> {
    Optional<UserTheme> findByName(String name);
}
