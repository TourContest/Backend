package com.goodda.jejuday.notification.loadtest;

import com.goodda.jejuday.notification.service.FcmGateway;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.SendResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 부하테스트 전용 FCM 스텁 (profile=loadtest).
 *
 * <p>실제 FCM 호출 없이 sendEach 1회당 {@code loadtest.fcm.latency-ms}(기본 200ms)만큼 지연시킨 뒤
 * 모든 메시지를 성공으로 응답한다. OutboxPoller 의 배치 크기/폴링 주기가 만드는 처리량을
 * 외부 의존 없이 재현하기 위한 용도이며, 운영 프로파일에서는 로드되지 않는다.
 */
@Slf4j
@Primary
@Profile("loadtest")
@Component
public class StubFcmGateway extends FcmGateway {

    private final long latencyMs;

    public StubFcmGateway(@Value("${loadtest.fcm.latency-ms:200}") long latencyMs) {
        super(null);
        this.latencyMs = latencyMs;
    }

    @Override
    public BatchResponse sendEach(List<Message> messages) {
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        List<SendResponse> responses = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            responses.add(successResponse("stub-" + i));
        }
        return new BatchResponse() {
            @Override
            public List<SendResponse> getResponses() {
                return responses;
            }

            @Override
            public int getSuccessCount() {
                return responses.size();
            }

            @Override
            public int getFailureCount() {
                return 0;
            }
        };
    }

    // SendResponse 는 final + package-private 팩토리라 리플렉션으로 성공 응답을 만든다
    private static SendResponse successResponse(String messageId) {
        try {
            Method m = SendResponse.class.getDeclaredMethod("fromMessageId", String.class);
            m.setAccessible(true);
            return (SendResponse) m.invoke(null, messageId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("SendResponse 스텁 생성 실패", e);
        }
    }
}
