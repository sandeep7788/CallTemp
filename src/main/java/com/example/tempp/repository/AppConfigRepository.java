package com.example.tempp.repository;

import com.example.tempp.model.AppConfig;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Firebase Firestore repository for {@link AppConfig}.
 * Collection: {@code app_config}, keyed by {@code configKey}.
 */
@Slf4j
@Repository
public class AppConfigRepository extends BaseFirebaseRepository {

    private static final String COLLECTION = "app_config";
    private final Firestore db;

    public AppConfigRepository(Firestore db) {
        this.db = db;
    }

    public Optional<AppConfig> findByConfigKey(String configKey) {
        try {
            DocumentSnapshot doc = db.collection(COLLECTION).document(configKey).get().get();
            return doc.exists() ? Optional.of(fromDoc(doc)) : Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findByConfigKey failed", e);
        }
    }

    public boolean existsByConfigKey(String configKey) {
        try {
            return db.collection(COLLECTION).document(configKey).get().get().exists();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase existsByConfigKey failed", e);
        }
    }

    public List<AppConfig> findAll() {
        try {
            return db.collection(COLLECTION).get().get().getDocuments().stream().map(this::fromDoc).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase findAll AppConfig failed", e);
        }
    }

    public AppConfig save(AppConfig config) {
        try {
            Instant now = Instant.now();
            if (config.getCreatedAt() == null)
                config.setCreatedAt(now);
            config.setUpdatedAt(now);
            db.collection(COLLECTION).document(config.getConfigKey()).set(toMap(config)).get();
            return config;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase save AppConfig failed", e);
        }
    }

    public void delete(AppConfig config) {
        try {
            db.collection(COLLECTION).document(config.getConfigKey()).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firebase delete AppConfig failed", e);
        }
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private AppConfig fromDoc(DocumentSnapshot doc) {
        AppConfig c = new AppConfig();
        c.setConfigKey(doc.getId());
        c.setConfigValue(getString(doc, "configValue"));
        c.setConfigType(getString(doc, "configType"));
        c.setDescription(getString(doc, "description"));
        c.setEnabled(getBoolean(doc, "isEnabled", true));
        c.setCreatedAt(toInstant(doc.get("createdAt")));
        c.setUpdatedAt(toInstant(doc.get("updatedAt")));
        return c;
    }

    private Map<String, Object> toMap(AppConfig c) {
        Map<String, Object> m = new HashMap<>();
        m.put("configValue", c.getConfigValue());
        m.put("configType", c.getConfigType());
        m.put("description", c.getDescription());
        m.put("isEnabled", c.isEnabled());
        m.put("createdAt", fromInstant(c.getCreatedAt()));
        m.put("updatedAt", fromInstant(c.getUpdatedAt()));
        return m;
    }
}
