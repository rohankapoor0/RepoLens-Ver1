package com.repolens.web.cache.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * H2 JPA Entity for persisting evaluated repository quality scores.
 */
@Entity
@Table(name = "cached_repo_scores")
public class CachedRepoScoreEntity {

    @Id
    @Column(name = "repo_key", length = 300, nullable = false)
    private String repoKey;

    @Column(name = "owner", length = 100, nullable = false)
    private String owner;

    @Column(name = "repo", length = 150, nullable = false)
    private String repo;

    @Column(name = "commit_or_pushed_at", length = 100, nullable = false)
    private String commitOrPushedAt;

    @Lob
    @Column(name = "snapshot_summary_json", columnDefinition = "CLOB")
    private String snapshotSummaryJson;

    @Lob
    @Column(name = "score_json", columnDefinition = "CLOB", nullable = false)
    private String scoreJson;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public CachedRepoScoreEntity() {}

    public CachedRepoScoreEntity(String repoKey, String owner, String repo, String commitOrPushedAt,
                                 String snapshotSummaryJson, String scoreJson, Instant fetchedAt) {
        this.repoKey = repoKey;
        this.owner = owner;
        this.repo = repo;
        this.commitOrPushedAt = commitOrPushedAt;
        this.snapshotSummaryJson = snapshotSummaryJson;
        this.scoreJson = scoreJson;
        this.fetchedAt = fetchedAt;
    }

    public String getRepoKey() { return repoKey; }
    public void setRepoKey(String repoKey) { this.repoKey = repoKey; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }

    public String getCommitOrPushedAt() { return commitOrPushedAt; }
    public void setCommitOrPushedAt(String commitOrPushedAt) { this.commitOrPushedAt = commitOrPushedAt; }

    public String getSnapshotSummaryJson() { return snapshotSummaryJson; }
    public void setSnapshotSummaryJson(String snapshotSummaryJson) { this.snapshotSummaryJson = snapshotSummaryJson; }

    public String getScoreJson() { return scoreJson; }
    public void setScoreJson(String scoreJson) { this.scoreJson = scoreJson; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
