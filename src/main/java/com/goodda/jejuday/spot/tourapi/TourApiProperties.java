package com.goodda.jejuday.spot.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URLEncoder;
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

    /** URI를 build(true)로 만들기 위해 인증키를 항상 포털의 Encoding 형태로 맞춘다. */
    public String getEncodedServiceKey() {
        if (serviceKey == null || serviceKey.contains("%")) return serviceKey;
        return URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
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
