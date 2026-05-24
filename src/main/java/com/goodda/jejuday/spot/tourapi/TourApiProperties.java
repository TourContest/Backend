package com.goodda.jejuday.spot.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourapi")
public class TourApiProperties {
    private String baseUrl;
    private String serviceKey;
    private String korServicePath;
    private Long systemUserId;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }

    public String getKorServicePath() { return korServicePath; }
    public void setKorServicePath(String korServicePath) { this.korServicePath = korServicePath; }

    public Long getSystemUserId() { return systemUserId; }
    public void setSystemUserId(Long systemUserId) { this.systemUserId = systemUserId; }
}
