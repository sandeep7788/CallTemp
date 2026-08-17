package com.example.tempp.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Application-level configuration setting.
 * Stored in the Firestore {@code app_config} collection, keyed by {@code configKey}.
 */
public class AppConfig {

    @JsonProperty("config_key")
    @JsonAlias({"configKey", "key"})
    private String configKey;

    @JsonProperty("config_value")
    @JsonAlias({"configValue", "value"})
    private String configValue;

    @JsonProperty("config_type")
    @JsonAlias({"configType", "type"})
    private String configType;

    private String description;

    @JsonProperty("is_enabled")
    @JsonAlias({"isEnabled", "enabled"})
    private boolean isEnabled = true;

    @JsonProperty("created_at")
    @JsonAlias("createdAt")
    private Instant createdAt;

    @JsonProperty("updated_at")
    @JsonAlias("updatedAt")
    private Instant updatedAt;

    public AppConfig() {
    }

    public AppConfig(String configKey, String configValue, String configType) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.configType = configType;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
