package com.goodda.jejuday.attendance.service;

import com.goodda.jejuday.attendance.util.HallabongConstants;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HallabongService {

    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional
    public void addHallabong(Long userId, int amount) {
        User user = userService.getUserById(userId);
        user.setHallabong(user.getHallabong() + amount);
        userRepository.save(user);
    }

    /**
     * hallabong 원자적 차감.
     * 잔액 부족이면 IllegalStateException을 던진다.
     */
    @Transactional
    public void deductHallabong(Long userId, int amount) {
        int updated = userRepository.decrementHallabong(userId, amount);
        if (updated == 0) {
            throw new IllegalStateException("한라봉이 부족합니다.");
        }
    }

    public int getHallabong(Long userId) {
        return userService.getUserById(userId).getHallabong();
    }

    @Transactional
    public void convertStepsToHallabong(Long userId, int steps) {
        int hallabong = steps / HallabongConstants.STEP_CONVERT_UNIT;
        addHallabong(userId, hallabong);
    }
}
