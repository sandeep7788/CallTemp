package com.example.tempp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Firebase Admin SDK configuration.
 *
 * <p>Credential resolution order:
 * <ol>
 *   <li>Env var {@code FIREBASE_CREDENTIALS_JSON} – raw JSON text (useful in containerised envs)
 *   <li>{@code firebase.credentials.path} property – file-system path or classpath resource
 *   <li>Application Default Credentials (GCP / Cloud Run automatic auth)
 * </ol>
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials.path:firebase-service-account.json}")
    private String credentialsPath;

    @Value("${firebase.project.id:}")
    private String projectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialised – reusing existing instance");
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = resolveCredentials();
        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder().setCredentials(credentials);

        if (projectId != null && !projectId.isBlank() && !"your-firebase-project-id".equals(projectId.trim())) {
            optionsBuilder.setProjectId(projectId.trim());
        }

        FirebaseApp app = FirebaseApp.initializeApp(optionsBuilder.build());
        log.info("Firebase Admin SDK initialised successfully (project={})", projectId);
        return app;
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private GoogleCredentials resolveCredentials() throws IOException {
        // 1. Raw JSON in environment variable (highest priority – CI/CD, containers)
        String credentialsJson = System.getenv("FIREBASE_CREDENTIALS_JSON");
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            log.info("Loading Firebase credentials from env var FIREBASE_CREDENTIALS_JSON");
            try (InputStream is = new java.io.ByteArrayInputStream(credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(is);
            }
        }

        // 2. File path / classpath resource
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            // Try absolute / relative file-system path first
            if (Files.exists(Paths.get(credentialsPath))) {
                log.info("Loading Firebase credentials from file: {}", credentialsPath);
                try (InputStream is = new FileInputStream(credentialsPath)) {
                    return GoogleCredentials.fromStream(is);
                }
            }
            // Fall back to classpath resource
            Resource resource = new ClassPathResource(credentialsPath);
            if (resource.exists()) {
                log.info("Loading Firebase credentials from classpath: {}", credentialsPath);
                try (InputStream is = resource.getInputStream()) {
                    return GoogleCredentials.fromStream(is);
                }
            }
            log.warn("Firebase credentials file not found at '{}' – falling back to Application Default Credentials", credentialsPath);
        }

        // 3. Application Default Credentials (GCP / Cloud Run)
        log.info("Using Firebase Application Default Credentials");
        return GoogleCredentials.getApplicationDefault();
    }
}

