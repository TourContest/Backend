package com.goodda.jejuday.common.aop;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AdGateAspect {

    private final SecurityUtil securityUtil;

    @Around("@annotation(com.goodda.jejuday.common.aop.AdGatedRefresh)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        User me = securityUtil.getAuthenticatedUser();
        // TODO: 구독자 우회 / 일반 사용자 광고 검증
        // boolean isSubscriber = false;
        // if (!isSubscriber) { /* AdService.verify... */ }
        return pjp.proceed();
    }
}