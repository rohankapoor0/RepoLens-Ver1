package com.repolens.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.core.algorithms.BinaryHeap;
import com.repolens.core.algorithms.CountingSort;
import com.repolens.core.algorithms.Quickselect;
import com.repolens.core.cache.BloomFilterRepoScoreCache;
import com.repolens.core.cache.CachedRepoScoreRecord;
import com.repolens.core.duplicate.DuplicateFinder;
import com.repolens.core.duplicate.DuplicateReport;
import com.repolens.core.duplicate.RepoProfile;
import com.repolens.core.planner.FixPlan;
import com.repolens.core.planner.FixPlanner;
import com.repolens.github.GithubClient;
import com.repolens.github.model.GithubRepo;
import com.repolens.github.model.GithubTree;
import com.repolens.github.model.GithubTreeEntry;
import com.repolens.github.model.GithubUser;
import com.repolens.github.model.RateLimitInfo;
import com.repolens.github.exception.UserNotFoundException;
import com.repolens.scoring.Deduction;
import com.repolens.scoring.RepoScore;
import com.repolens.scoring.RepoScorer;
import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.ScoreCategory;
import com.repolens.web.cache.service.JpaAnalysisCache;
import com.repolens.web.dto.EvaluatedRepo;
import com.repolens.web.exception.NoPublicReposException;
import com.repolens.web.throttle.VisitorThrottleService;
import com.repolens.web.validation.GithubUsernameValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core analysis orchestrator performing concurrent repository evaluations,
 * hand-crafted BinaryHeap ranking, Quickselect median analysis, and CountingSort score distribution.
 */
@Service
public class AnalysisService {

    private final GithubClient githubClient;
    private final BloomFilterRepoScoreCache repoCache;
    private final JpaAnalysisCache userAnalysisCache;
    private final VisitorThrottleService throttleService;
    private final RepoScorer repoScorer;
    private final FixPlanner fixPlanner;
    private final DuplicateFinder duplicateFinder;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    private final int maxRepos;
    private final int staleMonths;

    public AnalysisService(
            GithubClient githubClient,
            BloomFilterRepoScoreCache repoCache,
            JpaAnalysisCache userAnalysisCache,
            VisitorThrottleService throttleService,
            @Value("${repolens.analysis.max-repos:50}") int maxRepos,
            @Value("${repolens.analysis.stale-months:12}") int staleMonths,
            @Value("${repolens.analysis.concurrency:4}") int concurrency
    ) {
        this.githubClient = githubClient;
        this.repoCache = repoCache;
        this.userAnalysisCache = userAnalysisCache;
        this.throttleService = throttleService;
        this.maxRepos = Math.max(1, maxRepos);
        this.staleMonths = Math.max(1, staleMonths);
        this.repoScorer = new RepoScorer();
        this.fixPlanner = new FixPlanner();
        this.duplicateFinder = new DuplicateFinder();
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
    }

    /**
     * Executes complete quality analysis for a GitHub user.
     *
     * @param username target GitHub username
     * @param clientIp client IP for visitor throttling
     * @return analysis dashboard data
     */
    public Map<String, Object> analyzeUser(String username, String clientIp) {
        // 1. Validate & normalize username (supports 'username' and '@username')
        String cleanUser = GithubUsernameValidator.normalize(username);

        // 2. Enforce per-visitor rate limiting (by IP)
        throttleService.checkAndRecordAccess(clientIp, cleanUser);

        // 3. Check cached user analysis
        Optional<com.repolens.core.cache.CachedAnalysis> cachedAnalysis = userAnalysisCache.get(cleanUser);
        if (cachedAnalysis.isPresent()) {
            try {
                Map<String, Object> cachedData = objectMapper.readValue(
                        cachedAnalysis.get().dataJson(),
                        new TypeReference<>() {}
                );
                if (cachedData.containsKey("fixPlan") && cachedData.containsKey("duplicates")) {
                    cachedData.put("cached", true);
                    cachedData.put("cachedAt", cachedAnalysis.get().cachedAt().toString());
                    long ageSeconds = Duration.between(cachedAnalysis.get().cachedAt(), Instant.now()).toSeconds();
                    cachedData.put("cacheAgeSeconds", ageSeconds);
                    return cachedData;
                }
            } catch (Exception ignored) {
                // On deserialization error, re-evaluate fresh
            }
        }

        // 4. Verify user exists on GitHub (GET /users/{username}) and fetch public repos
        GithubUser userProfile = null;
        try {
            userProfile = githubClient.getUser(cleanUser);
        } catch (UserNotFoundException e) {
            throw e; // Target user does not exist on GitHub
        } catch (Exception ignored) {
            // If secondary user metadata call fails or rate limited, proceed to repos
        }

        List<GithubRepo> publicRepos = githubClient.listPublicRepos(cleanUser);
        if (publicRepos.isEmpty()) {
            throw new NoPublicReposException(cleanUser);
        }

        int totalPublicRepos = publicRepos.size();
        boolean isCapped = totalPublicRepos > maxRepos;
        List<GithubRepo> reposToAnalyze = isCapped ? publicRepos.subList(0, maxRepos) : publicRepos;

        // 5. Evaluate repositories concurrently (modest thread pool)
        List<CompletableFuture<SingleRepoEvalResult>> futures = reposToAnalyze.stream()
                .map(repo -> CompletableFuture.supplyAsync(() -> evaluateSingleRepo(repo), executor))
                .toList();

        List<SingleRepoEvalResult> evalResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        List<EvaluatedRepo> evaluatedList = new ArrayList<>();
        Map<String, List<Deduction>> deductionsByRepo = new LinkedHashMap<>();
        List<RepoProfile> profiles = new ArrayList<>();

        for (SingleRepoEvalResult r : evalResults) {
            evaluatedList.add(r.evaluatedRepo());
            deductionsByRepo.put(r.evaluatedRepo().name(), r.deductions());
            profiles.add(r.duplicateProfile());
        }

        // 6. Hand-crafted BinaryHeap: Rank from Best to Worst (O(N log N))
        // Max-heap: root is highest score
        BinaryHeap<EvaluatedRepo> maxHeap = new BinaryHeap<>(
                Comparator.comparingInt(EvaluatedRepo::score).reversed()
        );
        for (EvaluatedRepo r : evaluatedList) {
            maxHeap.insert(r);
        }

        List<EvaluatedRepo> rankedRepos = new ArrayList<>();
        int rankCounter = 1;
        while (!maxHeap.isEmpty()) {
            EvaluatedRepo top = maxHeap.extractTop();
            rankedRepos.add(new EvaluatedRepo(
                    rankCounter++,
                    top.name(), top.fullName(), top.owner(), top.score(),
                    top.description(), top.language(), top.projectType(), top.stars(), top.forks(),
                    top.openIssues(), top.pushedAt(), top.archived(),
                    top.categoryScores(), top.deductionsCount(), top.cached()
            ));
        }

        // 7. Hand-crafted Quickselect: Compute Median & Percentiles in O(N) average time
        int[] scoreArray = evaluatedList.stream().mapToInt(EvaluatedRepo::score).toArray();
        double medianScore = Quickselect.median(scoreArray);
        double percentile25 = Quickselect.percentile(scoreArray, 25.0);
        double percentile75 = Quickselect.percentile(scoreArray, 75.0);
        double avgScore = Math.round(evaluatedList.stream().mapToInt(EvaluatedRepo::score).average().orElse(0) * 10.0) / 10.0;

        // 8. Hand-crafted CountingSort: Compute distribution bands and histogram in O(N) time
        int[] sortedScores = CountingSort.sort(scoreArray);
        Map<String, Integer> scoreBands = computeScoreBands(sortedScores);
        Map<String, Integer> histogram = computeHistogram(sortedScores);

        // 9. Stale Repositories Detection
        List<Map<String, Object>> staleRepos = findStaleRepos(publicRepos);

        // 10. FixPlanner: Optimal prioritized fix plan using hand-crafted GreedyWeightedSetCover
        FixPlan fixPlan = fixPlanner.plan(deductionsByRepo, totalPublicRepos);

        // 11. DuplicateFinder: Group similar/duplicate repositories using hand-crafted DisjointSetUnion
        DuplicateReport duplicates = duplicateFinder.findDuplicates(profiles);

        // 12. Build Response
        RateLimitInfo rateLimit = githubClient.getLastRateLimitInfo();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", cleanUser);
        result.put("analyzedAt", Instant.now().toString());
        result.put("cached", false);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPublicRepos", totalPublicRepos);
        stats.put("reposAnalyzed", evaluatedList.size());
        stats.put("isCapped", isCapped);
        stats.put("maxRepoCap", maxRepos);
        stats.put("capNotice", isCapped ? "Analysis capped at " + maxRepos + " repositories (user has " + totalPublicRepos + " public repositories)." : null);
        stats.put("averageScore", avgScore);
        stats.put("medianScore", medianScore);
        stats.put("percentile25", percentile25);
        stats.put("percentile75", percentile75);
        stats.put("scoreBands", scoreBands);
        stats.put("scoreHistogram", histogram);
        result.put("stats", stats);

        result.put("fixPlan", fixPlan);
        result.put("duplicates", duplicates);
        result.put("staleReposCount", staleRepos.size());
        result.put("staleRepos", staleRepos);
        result.put("rankedRepos", rankedRepos);

        if (userProfile != null) {
            Map<String, Object> profileMap = new LinkedHashMap<>();
            profileMap.put("login", userProfile.login());
            profileMap.put("name", userProfile.name());
            profileMap.put("avatarUrl", userProfile.avatarUrl());
            profileMap.put("htmlUrl", userProfile.htmlUrl());
            profileMap.put("bio", userProfile.bio());
            profileMap.put("publicRepos", userProfile.publicRepos());
            profileMap.put("followers", userProfile.followers());
            result.put("profile", profileMap);
        }

        result.put("rateLimit", Map.of(
                "limit", rateLimit.limit(),
                "remaining", rateLimit.remaining(),
                "secondsUntilReset", rateLimit.secondsUntilReset()
        ));

        result.put("metrics", Map.of(
                "savedApiRequests", repoCache.getMetrics().getSavedApiRequests(),
                "cacheHits", repoCache.getMetrics().getCacheHits(),
                "cacheMisses", repoCache.getMetrics().getCacheMisses()
        ));

        // Cache user analysis
        try {
            userAnalysisCache.put(cleanUser, null, objectMapper.writeValueAsString(result), null);
        } catch (Exception ignored) {}

        return result;
    }

    private record SingleRepoEvalResult(
            EvaluatedRepo evaluatedRepo,
            List<Deduction> deductions,
            RepoProfile duplicateProfile
    ) {}

    @SuppressWarnings("unchecked")
    private SingleRepoEvalResult evaluateSingleRepo(GithubRepo repo) {
        String owner = repo.owner();
        String repoName = repo.name();
        String commitOrPushedAt = repo.pushedAt() != null ? repo.pushedAt().toString() : "HEAD";

        // Check Bloom + DB cache
        Optional<CachedRepoScoreRecord> cached = repoCache.get(owner, repoName, commitOrPushedAt);
        if (cached.isPresent()) {
            try {
                RepoScore score = objectMapper.readValue(cached.get().scoreJson(), RepoScore.class);
                if (score.summary() != null && score.intelligence() != null && (score.deductions().isEmpty() || score.deductions().get(0).evidence() != null)) {
                    List<String> filePaths = List.of();
                    String readme = null;
                    try {
                        Map<String, Object> summary = objectMapper.readValue(cached.get().snapshotSummaryJson(), new TypeReference<>() {});
                        if (summary.containsKey("filePaths") && summary.get("filePaths") instanceof List) {
                            filePaths = (List<String>) summary.get("filePaths");
                        }
                        if (summary.containsKey("readmeText") && summary.get("readmeText") instanceof String) {
                            readme = (String) summary.get("readmeText");
                        }
                    } catch (Exception ignored) {}

                    String projectType = score.intelligence() != null && score.intelligence().primaryType() != null
                            ? score.intelligence().primaryType().getDisplayName()
                            : (repo.language() != null && !repo.language().isBlank() ? repo.language() : "General Software");

                    EvaluatedRepo evaluated = new EvaluatedRepo(
                            0, repo.name(), repo.fullName(), repo.owner(), score.totalScore(),
                            repo.description(), repo.language(), projectType, repo.stargazersCount(), repo.forksCount(),
                            repo.openIssuesCount(), repo.pushedAt(), repo.archived(),
                            score.categoryScores(), score.deductions().size(), true
                    );
                    RepoProfile profile = new RepoProfile(repo.name(), repo.description(), filePaths, readme, repo.language());
                    return new SingleRepoEvalResult(evaluated, score.deductions(), profile);
                }
            } catch (Exception ignored) {}
        }

        // Cache miss: evaluate fresh
        GithubTree tree;
        try {
            tree = githubClient.getTree(owner, repoName, repo.defaultBranch());
        } catch (Exception e) {
            tree = new GithubTree("", false, List.of());
        }

        List<String> filePaths = tree.entries().stream().map(GithubTreeEntry::path).toList();
        Map<String, Long> fileSizes = new HashMap<>();
        for (GithubTreeEntry entry : tree.entries()) {
            if (entry.size() != null) fileSizes.put(entry.path(), entry.size());
        }

        String readme = null;
        try {
            readme = githubClient.getReadme(owner, repoName).orElse(null);
        } catch (Exception e) {
            readme = null;
        }

        List<Instant> commits = Collections.emptyList();
        try {
            commits = githubClient.getRecentCommitDates(owner, repoName);
        } catch (Exception e) {
            commits = Collections.emptyList();
        }

        RepoSnapshot snapshot = new RepoSnapshot(repo, filePaths, fileSizes, readme, commits);
        RepoScore score = repoScorer.score(snapshot);

        // Put in H2 + Bloom filter
        try {
            String scoreJson = objectMapper.writeValueAsString(score);
            String summaryJson = objectMapper.writeValueAsString(Map.of(
                    "filesScanned", filePaths.size(),
                    "hasReadme", readme != null && !readme.isBlank(),
                    "filePaths", filePaths,
                    "readmeText", readme != null ? readme : ""
            ));
            String key = CachedRepoScoreRecord.buildKey(owner, repoName, commitOrPushedAt);
            repoCache.put(new CachedRepoScoreRecord(key, owner, repoName, commitOrPushedAt, summaryJson, scoreJson, Instant.now()));
        } catch (Exception ignored) {}

        String projectType = score.intelligence() != null && score.intelligence().primaryType() != null
                ? score.intelligence().primaryType().getDisplayName()
                : (repo.language() != null && !repo.language().isBlank() ? repo.language() : "General Software");

        EvaluatedRepo evaluated = new EvaluatedRepo(
                0, repo.name(), repo.fullName(), repo.owner(), score.totalScore(),
                repo.description(), repo.language(), projectType, repo.stargazersCount(), repo.forksCount(),
                repo.openIssuesCount(), repo.pushedAt(), repo.archived(),
                score.categoryScores(), score.deductions().size(), false
        );
        RepoProfile profile = new RepoProfile(repo.name(), repo.description(), filePaths, readme, repo.language());
        return new SingleRepoEvalResult(evaluated, score.deductions(), profile);
    }

    private Map<String, Integer> computeScoreBands(int[] sortedScores) {
        int excellent = 0; // 90-100
        int good = 0;      // 75-89
        int fair = 0;      // 50-74
        int needsWork = 0; // 0-49

        for (int s : sortedScores) {
            if (s >= 90) excellent++;
            else if (s >= 75) good++;
            else if (s >= 50) fair++;
            else needsWork++;
        }

        Map<String, Integer> bands = new LinkedHashMap<>();
        bands.put("excellent", excellent);
        bands.put("good", good);
        bands.put("fair", fair);
        bands.put("needsWork", needsWork);
        return bands;
    }

    private Map<String, Integer> computeHistogram(int[] sortedScores) {
        Map<String, Integer> histogram = new LinkedHashMap<>();
        for (int i = 0; i <= 90; i += 10) {
            String bucket = (i == 90) ? "90-100" : (i + "-" + (i + 9));
            histogram.put(bucket, 0);
        }

        for (int s : sortedScores) {
            int bucketStart = Math.min(90, (s / 10) * 10);
            String bucket = (bucketStart == 90) ? "90-100" : (bucketStart + "-" + (bucketStart + 9));
            histogram.put(bucket, histogram.get(bucket) + 1);
        }
        return histogram;
    }

    private List<Map<String, Object>> findStaleRepos(List<GithubRepo> repos) {
        List<Map<String, Object>> staleList = new ArrayList<>();
        Instant now = Instant.now();
        long staleDaysThreshold = staleMonths * 30L;

        for (GithubRepo repo : repos) {
            if (repo.pushedAt() == null) {
                staleList.add(Map.of(
                        "name", repo.name(),
                        "fullName", repo.fullName(),
                        "lastPushedAt", "Never",
                        "daysSinceLastPush", -1,
                        "monthsInactive", -1,
                        "archived", repo.archived()
                ));
            } else {
                long days = Duration.between(repo.pushedAt(), now).toDays();
                if (days >= staleDaysThreshold) {
                    staleList.add(Map.of(
                            "name", repo.name(),
                            "fullName", repo.fullName(),
                            "lastPushedAt", repo.pushedAt().toString(),
                            "daysSinceLastPush", days,
                            "monthsInactive", days / 30,
                            "archived", repo.archived()
                    ));
                }
            }
        }
        return staleList;
    }
}
