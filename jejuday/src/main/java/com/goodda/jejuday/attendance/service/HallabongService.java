package com.goodda.jejuday.attendance.service;

import com.goodda.jejuday.attendance.util.HallabongConstants;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HallabongService {

    private final UserRepository userRepository;
    private final UserService userService;

    public void addHallabong(Long userId, int amount) {
        User user = userService.getUserById(userId);
        user.setHallabong(user.getHallabong() + amount);
        userRepository.save(user);
    }

    public void deductHallabong(Long userId, int amount) {
        User user = userService.getUserById(userId);
        if (user.getHallabong() < amount) {
            throw new IllegalStateException("한라봉이 부족합니다.");
        }
        user.setHallabong(user.getHallabong() - amount);
        userRepository.save(user);
    }

    public int getHallabong(Long userId) {
        return userService.getUserById(userId).getHallabong();
    }

    public void convertStepsToHallabong(Long userId, int steps) {
        int hallabong = steps / HallabongConstants.STEP_CONVERT_UNIT;
        addHallabong(userId, hallabong);
    }
}
