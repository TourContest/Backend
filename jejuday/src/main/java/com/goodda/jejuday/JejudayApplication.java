package com.goodda.jejuday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableScheduling
public class JejudayApplication {

    public static void main(String[] args) {
        SpringApplication.run(JejudayApplication.class, args);
    }

}
