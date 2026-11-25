package com.repolens.web.cache.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * H2 JPA Entity for persisting HTTP ETags and responses per GitHub REST API URL.
 */
@Entity
@Table(name = "cached_etags")
public class CachedETagEntity {

    @Id
    @Column(name = "url", length = 500, nullable = false)
    private String url;

    @Column(name = "etag", length = 150, nullable = false)
    private String etag;

    @Lob
    @Column(name = "body", columnDefinition = "CLOB", nullable = false)
    private String body;

    @Column(name = "rate_limit_limit")
    private Integer rateLimitLimit;

    @Column(name = "rate_limit_remaining")
    private Integer rateLimitRemaining;

    @Column(name = "rate_limit_reset")
    private Instant rateLimitReset;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public CachedETagEntity() {}

    public CachedETagEntity(String url, String etag, String body, Integer rateLimitLimit,
                            Integer rateLimitRemaining, Instant rateLimitReset, Instant fetchedAt) {
        this.url = url;
        this.etag = etag;
        this.body = body;
        this.rateLimitLimit = rateLimitLimit;
        this.rateLimitRemaining = rateLimitRemaining;
        this.rateLimitReset = rateLimitReset;
        this.fetchedAt = fetchedAt;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Integer getRateLimitLimit() { return rateLimitLimit; }
    public void setRateLimitLimit(Integer rateLimitLimit) { this.rateLimitLimit = rateLimitLimit; }

    public Integer getRateLimitRemaining() { return rateLimitRemaining; }
    public void setRateLimitRemaining(Integer rateLimitRemaining) { this.rateLimitRemaining = rateLimitRemaining; }

    public Instant getRateLimitReset() { return rateLimitReset; }
    public void setRateLimitReset(Instant rateLimitReset) { this.rateLimitReset = rateLimitReset; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
