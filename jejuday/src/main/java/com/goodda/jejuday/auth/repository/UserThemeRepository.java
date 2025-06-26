package com.goodda.jejuday.auth.repository;

import com.goodda.jejuday.auth.entity.UserTheme;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserThemeRepository extends JpaRepository<UserTheme, Long> {
    Optional<UserTheme> findByName(String name);
}
