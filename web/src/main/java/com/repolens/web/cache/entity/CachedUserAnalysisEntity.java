package com.repolens.web.cache.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * H2 JPA Entity for persisting user profile analyses with TTL.
 */
@Entity
@Table(name = "cached_user_analyses")
public class CachedUserAnalysisEntity {

    @Id
    @Column(name = "username", length = 150, nullable = false)
    private String username;

    @Column(name = "etag", length = 150)
    private String etag;

    @Lob
    @Column(name = "data_json", columnDefinition = "CLOB", nullable = false)
    private String dataJson;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public CachedUserAnalysisEntity() {}

    public CachedUserAnalysisEntity(String username, String etag, String dataJson, Instant cachedAt, Instant expiresAt) {
        this.username = username != null ? username.trim().toLowerCase() : null;
        this.etag = etag;
        this.dataJson = dataJson;
        this.cachedAt = cachedAt;
        this.expiresAt = expiresAt;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }

    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }

    public Instant getCachedAt() { return cachedAt; }
    public void setCachedAt(Instant cachedAt) { this.cachedAt = cachedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
