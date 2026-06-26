package com.goodda.jejuday.spot.tourapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {
    private String systemUserEmail = "system@yourapp.local";
    private String systemUserName  = "System";
}