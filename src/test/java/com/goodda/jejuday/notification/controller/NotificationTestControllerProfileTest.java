package com.goodda.jejuday.notification.controller;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationTestController가 운영 프로파일에서 빈으로 등록되지 않음을 검증.
 * 완료 기준: @Profile 어노테이션에 "prod"가 포함되지 않고 dev/local/test 중 하나 이상 포함.
 */
class NotificationTestControllerProfileTest {

    @Test
    void notificationTestController_hasProfile_annotation() {
        Profile profile = NotificationTestController.class.getAnnotation(Profile.class);
        assertThat(profile)
                .as("@Profile 어노테이션이 존재해야 한다")
                .isNotNull();
    }

    @Test
    void notificationTestController_profile_excludes_prod() {
        Profile profile = NotificationTestController.class.getAnnotation(Profile.class);
        assertThat(profile.value())
                .as("prod 프로파일에서는 활성화되면 안 된다")
                .doesNotContain("prod");
    }

    @Test
    void notificationTestController_profile_includes_dev_or_local_or_test() {
        Profile profile = NotificationTestController.class.getAnnotation(Profile.class);
        assertThat(profile.value())
                .as("dev, local, test 중 하나 이상 포함해야 한다")
                .containsAnyOf("dev", "local", "test");
    }
}
