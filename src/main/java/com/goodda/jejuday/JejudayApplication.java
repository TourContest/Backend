package com.goodda.jejuday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
@EnableRetry
@SpringBootApplication
public class JejudayApplication {

    public static void main(String[] args) {
        SpringApplication.run(JejudayApplication.class, args);
    }

}
