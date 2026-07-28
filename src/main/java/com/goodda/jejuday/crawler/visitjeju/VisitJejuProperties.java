package com.goodda.jejuday.crawler.visitjeju;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "visitjeju")
public class VisitJejuProperties {
    private String baseUrl;
    private String apiKey;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}