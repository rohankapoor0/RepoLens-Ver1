package com.repolens.scoring;

import com.repolens.core.algorithms.BinaryHeap;
import com.repolens.scoring.intelligence.DetectedTechnology;
import com.repolens.scoring.intelligence.IntelligenceEngine;
import com.repolens.scoring.intelligence.ProjectType;
import com.repolens.scoring.intelligence.RepoIntelligence;
import com.repolens.scoring.rules.DocumentationScorer;
import com.repolens.scoring.rules.EngineeringHygieneScorer;
import com.repolens.scoring.rules.LicensingCommunityScorer;
import com.repolens.scoring.rules.MaintenanceScorer;
import com.repolens.scoring.rules.SafetyScorer;
import com.repolens.scoring.rules.StructureScorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic 100-point quality and hygiene scoring engine for GitHub repositories.
 * <p>
 * Aggregates evaluations from six modular category scorers and integrates
 * repository-specific engineering intelligence.
 */
public class RepoScorer {

    private final List<CategoryScorer> scorers;
    private final IntelligenceEngine intelligenceEngine;

    public RepoScorer() {
        this(List.of(
                new DocumentationScorer(),
                new LicensingCommunityScorer(),
                new MaintenanceScorer(),
                new EngineeringHygieneScorer(),
                new SafetyScorer(),
                new StructureScorer()
        ));
    }

    public RepoScorer(List<CategoryScorer> scorers) {
        this.scorers = scorers != null ? scorers : List.of();
        this.intelligenceEngine = new IntelligenceEngine();
    }

    /**
     * Evaluates a repository snapshot and returns its itemized score card and engineering intelligence.
     *
     * @param snapshot complete repository snapshot
     * @return deterministic score out of 100 with deduction details, summary, and repository intelligence
     */
    public RepoScore score(RepoSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("RepoSnapshot cannot be null");
        }

        // 1. Generate repository-specific intelligence first
        RepoIntelligence intelligence = intelligenceEngine.analyze(snapshot);

        Map<ScoreCategory, Integer> categoryScores = new LinkedHashMap<>();
        Map<ScoreCategory, Integer> categoryMaxPoints = new LinkedHashMap<>();
        Map<ScoreCategory, CategoryBreakdown> categoryBreakdowns = new LinkedHashMap<>();
        List<Deduction> rawDeductions = new ArrayList<>();
        int total = 0;

        for (CategoryScorer scorer : scorers) {
            ScoreCategory category = scorer.getCategory();
            CategoryResult result = scorer.score(snapshot);

            categoryScores.put(category, result.score());
            categoryMaxPoints.put(category, category.getMaxPoints());
            rawDeductions.addAll(result.deductions());
            total += result.score();

            CategoryBreakdown breakdown = new CategoryBreakdown(
                    category,
                    category.getDisplayName(),
                    result.score(),
                    category.getMaxPoints(),
                    result.passedChecks(),
                    result.failedChecks()
            );
            categoryBreakdowns.put(category, breakdown);
        }

        int clampedTotal = Math.max(0, Math.min(100, total));

        // 2. Enrich deductions with project-specific intelligence
        List<Deduction> enrichedDeductions = enrichDeductionsWithIntelligence(rawDeductions, intelligence);

        // 3. Order deductions by efficiency (pointsGainIfFixed / effortMinutes) using hand-crafted BinaryHeap
        Comparator<Deduction> efficiencyComparator = (d1, d2) -> {
            double eff1 = (double) d1.pointsGainIfFixed() / d1.effortMinutes();
            double eff2 = (double) d2.pointsGainIfFixed() / d2.effortMinutes();
            int cmp = Double.compare(eff2, eff1); // Higher efficiency extracts first
            if (cmp != 0) {
                return cmp;
            }
            int gainCmp = Integer.compare(d2.pointsGainIfFixed(), d1.pointsGainIfFixed());
            if (gainCmp != 0) {
                return gainCmp;
            }
            return d1.ruleId().compareTo(d2.ruleId());
        };

        BinaryHeap<Deduction> heap = new BinaryHeap<>(efficiencyComparator);
        for (Deduction deduction : enrichedDeductions) {
            heap.insert(deduction);
        }
        List<Deduction> sortedDeductions = heap.toSortedList();

        // 4. Add RepoSummary per repo
        RepoSummary summary = buildSummary(clampedTotal, categoryScores, categoryBreakdowns, sortedDeductions, intelligence);

        return new RepoScore(
                clampedTotal,
                categoryScores,
                categoryMaxPoints,
                categoryBreakdowns,
                sortedDeductions,
                summary,
                intelligence,
                RepoScore.HYGIENE_DISCLAIMER
        );
    }

    private List<Deduction> enrichDeductionsWithIntelligence(List<Deduction> deductions, RepoIntelligence intelligence) {
        List<Deduction> enriched = new ArrayList<>();
        Set<String> techNames = new java.util.HashSet<>();
        for (DetectedTechnology t : intelligence.technologies()) {
            techNames.add(t.name());
        }

        for (Deduction d : deductions) {
            if ("AUTOMATED_TESTS".equals(d.ruleId())) {
                if (techNames.contains("FastAPI")) {
                    enriched.add(new Deduction(
                            d.category(),
                            d.ruleId(),
                            d.evidence(),
                            "FastAPI endpoint tests using TestClient and pytest validate status codes, query parameters, and response schemas before deployment.",
                            d.pointsLost(),
                            d.pointsAvailable(),
                            "Your FastAPI application has separate route modules, but no recognized integration test structure was detected. Consider adding endpoint tests for the main API routes.",
                            List.of("Install pytest and httpx: `pip install pytest httpx`", "Create `tests/test_api.py`", "Add TestClient test asserting GET/POST endpoints"),
                            """
                            from fastapi.testclient import TestClient
                            from main import app

                            client = TestClient(app)

                            def test_health_check():
                                response = client.get("/")
                                assert response.status_code == 200
                            """.stripIndent(),
                            d.effortMinutes(),
                            d.pointsGainIfFixed()
                    ));
                    continue;
                } else if (techNames.contains("React") || techNames.contains("Next.js")) {
                    enriched.add(new Deduction(
                            d.category(),
                            d.ruleId(),
                            d.evidence(),
                            "Component unit tests verify component rendering, user clicks, and state transitions without breaking UI flows during refactoring.",
                            d.pointsLost(),
                            d.pointsAvailable(),
                            "Your React application has component hierarchy, but no recognized component test suite (*.test.tsx / Vitest) was detected. Consider adding component interaction tests.",
                            List.of("Install testing dependencies: `npm install -D vitest @testing-library/react jsdom`", "Add test file `src/components/__tests__/App.test.tsx`", "Run tests in CI pipeline"),
                            """
                            import { render, screen } from '@testing-library/react';
                            import App from '../App';

                            test('renders main view', () => {
                              render(<App />);
                              expect(screen.getByRole('main')).toBeInTheDocument();
                            });
                            """.stripIndent(),
                            d.effortMinutes(),
                            d.pointsGainIfFixed()
                    ));
                    continue;
                } else if (techNames.contains("Spring Boot")) {
                    enriched.add(new Deduction(
                            d.category(),
                            d.ruleId(),
                            d.evidence(),
                            "Spring Boot slice tests with @WebMvcTest or MockMvc ensure endpoint status codes and JSON deserialization remain valid without starting a full server.",
                            d.pointsLost(),
                            d.pointsAvailable(),
                            "Your Spring Boot application defines controller endpoints, but no MockMvc or @SpringBootTest classes were detected. Consider adding web layer slice tests.",
                            List.of("Create `src/test/java/` directory", "Add MockMvc slice test testing primary REST endpoints", "Verify builds pass with `mvn test`"),
                            """
                            @WebMvcTest(ApiController.class)
                            class ApiControllerTest {
                                @Autowired private MockMvc mockMvc;
                                @Test void healthEndpointReturnsOk() throws Exception {
                                    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
                                }
                            }
                            """.stripIndent(),
                            d.effortMinutes(),
                            d.pointsGainIfFixed()
                    ));
                    continue;
                } else if (intelligence.primaryType() == ProjectType.AI_MACHINE_LEARNING || intelligence.primaryType() == ProjectType.DATA_SCIENCE) {
                    enriched.add(new Deduction(
                            d.category(),
                            d.ruleId(),
                            d.evidence(),
                            "Automated testing for ML data pipelines prevents dimension mismatch, data leakage, and feature corruption during model training and serving.",
                            d.pointsLost(),
                            d.pointsAvailable(),
                            "Your ML/Data repository lacks automated test suites to verify data transformations and inference pipelines. Add pytest tests covering preprocessing and model inference.",
                            List.of("Create `tests/test_pipeline.py`", "Add unit tests verifying preprocessing transformations and tensor shapes", "Execute pytest before pushing"),
                            """
                            def test_preprocessing_output_shape():
                                sample_input = {"feature1": 1.0, "feature2": 2.0}
                                transformed = preprocess(sample_input)
                                assert len(transformed) == 2
                            """.stripIndent(),
                            d.effortMinutes(),
                            d.pointsGainIfFixed()
                    ));
                    continue;
                }
            }
            enriched.add(d);
        }
        return enriched;
    }

    private RepoSummary buildSummary(
            int score,
            Map<ScoreCategory, Integer> categoryScores,
            Map<ScoreCategory, CategoryBreakdown> categoryBreakdowns,
            List<Deduction> deductions,
            RepoIntelligence intelligence
    ) {
        String gradeBand;
        if (score >= 90) {
            gradeBand = "Excellent";
        } else if (score >= 75) {
            gradeBand = "Good";
        } else if (score >= 50) {
            gradeBand = "Fair";
        } else {
            gradeBand = "Needs Attention";
        }

        ScoreCategory strongest = null;
        ScoreCategory weakest = null;
        double maxRatio = -1.0;
        double minRatio = 2.0;

        for (Map.Entry<ScoreCategory, Integer> entry : categoryScores.entrySet()) {
            ScoreCategory cat = entry.getKey();
            int earned = entry.getValue();
            int max = cat.getMaxPoints();
            double ratio = (double) earned / max;

            if (ratio > maxRatio) {
                maxRatio = ratio;
                strongest = cat;
            }
            if (ratio < minRatio) {
                minRatio = ratio;
                weakest = cat;
            }
        }

        List<Deduction> topByGain = new ArrayList<>(deductions);
        topByGain.sort((a, b) -> {
            int cmp = Integer.compare(b.pointsGainIfFixed(), a.pointsGainIfFixed());
            if (cmp != 0) return cmp;
            return Integer.compare(a.effortMinutes(), b.effortMinutes());
        });

        List<Deduction> top3 = topByGain.size() > 3 ? topByGain.subList(0, 3) : topByGain;
        int gainTop3 = top3.stream().mapToInt(Deduction::pointsGainIfFixed).sum();
        int projectedScore = Math.min(100, score + gainTop3);

        String explanation = generateExplanation(score, gradeBand, strongest, weakest, categoryScores, top3, gainTop3, projectedScore, intelligence);

        return new RepoSummary(
                score,
                gradeBand,
                strongest,
                weakest,
                explanation,
                top3,
                projectedScore
        );
    }

    private String generateExplanation(
            int score,
            String gradeBand,
            ScoreCategory strongest,
            ScoreCategory weakest,
            Map<ScoreCategory, Integer> scores,
            List<Deduction> top3,
            int gainTop3,
            int projectedScore,
            RepoIntelligence intelligence
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("This ")
                .append(intelligence.primaryType().getDisplayName())
                .append(" achieves a hygiene score of ")
                .append(score)
                .append("/100 (")
                .append(gradeBand)
                .append(" tier). ");

        if (strongest != null) {
            int sScore = scores.getOrDefault(strongest, 0);
            int sMax = strongest.getMaxPoints();
            sb.append("Its strongest engineering discipline is ")
                    .append(strongest.getDisplayName())
                    .append(" (")
                    .append(sScore)
                    .append("/")
                    .append(sMax)
                    .append(" pts). ");
        }

        if (weakest != null && weakest != strongest) {
            int wScore = scores.getOrDefault(weakest, 0);
            int wMax = weakest.getMaxPoints();
            sb.append("The primary opportunity for improvement is in ")
                    .append(weakest.getDisplayName())
                    .append(" (")
                    .append(wScore)
                    .append("/")
                    .append(wMax)
                    .append(" pts). ");
        }

        if (!top3.isEmpty()) {
            sb.append("High-impact remediations include resolving ")
                    .append(top3.get(0).ruleId().toLowerCase().replace('_', ' '));
            if (top3.size() > 1) {
                sb.append(" and ")
                        .append(top3.get(1).ruleId().toLowerCase().replace('_', ' '));
            }
            sb.append(". ");
        }

        if (gainTop3 > 0) {
            sb.append("Implementing these prioritized fixes would elevate the projected hygiene score to ")
                    .append(projectedScore)
                    .append("/100.");
        } else {
            sb.append("The repository meets high engineering standards across all evaluated hygiene categories.");
        }

        return sb.toString();
    }
}
