package com.goodda.jejuday.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;

/**
 * multipart 요청의 JSON 파트를 읽기 위한 컨버터.
 *
 * <p>모바일 클라이언트가 {@code formData.append("data", JSON.stringify(...))} 로 보내면
 * 해당 파트에 Content-Type 이 붙지 않아 서버는 {@code application/octet-stream} 으로 인식한다.
 * (일부 라이브러리는 {@code text/plain} 으로 붙인다.)
 * 두 경우 모두 JSON 으로 역직렬화할 수 있게 한다. 응답 쓰기에는 절대 관여하지 않는다.
 */
@Component
public class MultipartJackson2HttpMessageConverter extends AbstractJackson2HttpMessageConverter {

    public MultipartJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        super(objectMapper, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN);
    }

    /** String·byte[] 본문까지 가로채면 기존 text/plain 처리가 깨지므로 제외한다. */
    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        if (type instanceof Class<?> clazz && (String.class.equals(clazz) || byte[].class.equals(clazz))) {
            return false;
        }
        return super.canRead(type, contextClass, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    protected boolean canWrite(MediaType mediaType) {
        return false;
    }
}