package com.goodda.jejuday.spot.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "tourapi")
public class TourApiProperties {
    private String baseUrl;
    private String serviceKey;
    private String korServicePath;
    private String congestionServicePath;
    private String relatedServicePath;
    private String dataLabServicePath;
    private String relatedBaseYm;
    private Long systemUserId;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }

    /**
     * 공공데이터포털은 Encoding/Decoding 키를 모두 노출한다. URI builder가 쿼리를 인코딩하므로
     * Encoding 키(%2B, %2F, %3D)를 받은 경우에만 먼저 한 번 디코딩해 이중 인코딩을 막는다.
     */
    public String getNormalizedServiceKey() {
        if (serviceKey == null || !serviceKey.contains("%")) return serviceKey;
        return URLDecoder.decode(serviceKey, StandardCharsets.UTF_8);
    }

    public String getKorServicePath() { return korServicePath; }
    public void setKorServicePath(String korServicePath) { this.korServicePath = korServicePath; }

    public Long getSystemUserId() { return systemUserId; }
    public void setSystemUserId(Long systemUserId) { this.systemUserId = systemUserId; }
    public String getCongestionServicePath() { return congestionServicePath; }
    public void setCongestionServicePath(String value) { this.congestionServicePath = value; }
    public String getRelatedServicePath() { return relatedServicePath; }
    public void setRelatedServicePath(String value) { this.relatedServicePath = value; }
    public String getDataLabServicePath() { return dataLabServicePath; }
    public void setDataLabServicePath(String value) { this.dataLabServicePath = value; }
    public String getRelatedBaseYm() { return relatedBaseYm; }
    public void setRelatedBaseYm(String value) { this.relatedBaseYm = value; }
}
