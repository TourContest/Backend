package com.goodda.jejuday.auth.repository;

import com.goodda.jejuday.auth.entity.UserTheme;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserThemeRepository extends JpaRepository<UserTheme, Long> {
    Optional<UserTheme> findByName(String name);

    @Query("""
        select t.id
        from com.goodda.jejuday.auth.entity.User u
        join u.userThemes t
        where u.id = :userId
        order by t.id desc
    """)
    List<Long> findThemeIdsByUserId(@Param("userId") Long userId);
}
