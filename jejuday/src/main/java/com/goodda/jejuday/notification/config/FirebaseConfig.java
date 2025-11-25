package com.goodda.jejuday.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        try {
            InputStream serviceAccount;

            String path = System.getenv("FIREBASE_CREDENTIALS_PATH");
            if (path != null && !path.isBlank()) {
                serviceAccount = new FileInputStream(path);
            } else {
                ClassPathResource resource = new ClassPathResource("firebase/jejuday.json");
                serviceAccount = resource.getInputStream();
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            return FirebaseMessaging.getInstance();

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }
}
