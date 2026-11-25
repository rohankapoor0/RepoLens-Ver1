package com.repolens.core.planner;

import com.repolens.scoring.Deduction;
import com.repolens.scoring.ScoreCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FixPlannerTest {

    @Test
    @DisplayName("Hand-computed example: verifies exact greedy set cover pick order, ratios, points, and headline")
    void shouldProduceExactHandComputedFixPlan() {
        // Hand-computed setup:
        // alpha: fails LICENSE (5 pts), CI (7 pts)
        // beta:  fails LICENSE (5 pts), CI (7 pts)
        // gamma: fails CI (7 pts), TESTS (10 pts)
        //
        // Costs: LICENSE = 1.0, CI = 3.0, TESTS = 5.0
        // Pick 1: LICENSE covers 2 elements (alpha, beta) -> ratio = 1.0 / 2 = 0.5.
        // Uncovered: (alpha, CI), (beta, CI), (gamma, CI), (gamma, TESTS)
        // Pick 2: CI covers 3 elements (alpha, beta, gamma) -> ratio = 3.0 / 3 = 1.0 vs TESTS (5.0 / 1 = 5.0).
        // Uncovered: (gamma, TESTS)
        // Pick 3: TESTS covers 1 element (gamma) -> ratio = 5.0.

        Deduction licenseAlpha = new Deduction(ScoreCategory.LICENSING_COMMUNITY, 5, "No open-source LICENSE detected", "Add LICENSE");
        Deduction ciAlpha = new Deduction(ScoreCategory.ENGINEERING_HYGIENE, 7, "No CI/CD automation workflow found", "Add CI");

        Deduction licenseBeta = new Deduction(ScoreCategory.LICENSING_COMMUNITY, 5, "No open-source LICENSE detected", "Add LICENSE");
        Deduction ciBeta = new Deduction(ScoreCategory.ENGINEERING_HYGIENE, 7, "No CI/CD automation workflow found", "Add CI");

        Deduction ciGamma = new Deduction(ScoreCategory.ENGINEERING_HYGIENE, 7, "No CI/CD automation workflow found", "Add CI");
        Deduction testsGamma = new Deduction(ScoreCategory.ENGINEERING_HYGIENE, 10, "No automated test files found", "Add tests");

        Map<String, List<Deduction>> deductionsByRepo = Map.of(
                "alpha", List.of(licenseAlpha, ciAlpha),
                "beta", List.of(licenseBeta, ciBeta),
                "gamma", List.of(ciGamma, testsGamma)
        );

        FixPlanner planner = new FixPlanner();
        FixPlan plan = planner.plan(deductionsByRepo, 3);

        assertThat(plan.headline()).isEqualTo("3 fixes improve 3 of your 3 repos");
        assertThat(plan.distinctReposImproved()).isEqualTo(3);
        assertThat(plan.totalReposCount()).isEqualTo(3);
        assertThat(plan.totalPointsRecoverable()).isEqualTo(10 + 21 + 10); // 41
        assertThat(plan.totalEffort()).isEqualTo(1.0 + 3.0 + 5.0); // 9.0

        List<FixItem> fixes = plan.orderedFixes();
        assertThat(fixes).hasSize(3);

        // 1st pick: LICENSE
        assertThat(fixes.get(0).checkKey()).isEqualTo("ADD_LICENSE");
        assertThat(fixes.get(0).effortCost()).isEqualTo(1.0);
        assertThat(fixes.get(0).improvedRepos()).containsExactly("alpha", "beta");
        assertThat(fixes.get(0).totalPointsGained()).isEqualTo(10);

        // 2nd pick: CI
        assertThat(fixes.get(1).checkKey()).isEqualTo("ADD_CI");
        assertThat(fixes.get(1).effortCost()).isEqualTo(3.0);
        assertThat(fixes.get(1).improvedRepos()).containsExactly("alpha", "beta", "gamma");
        assertThat(fixes.get(1).totalPointsGained()).isEqualTo(21);

        // 3rd pick: TESTS
        assertThat(fixes.get(2).checkKey()).isEqualTo("ADD_TESTS");
        assertThat(fixes.get(2).effortCost()).isEqualTo(5.0);
        assertThat(fixes.get(2).improvedRepos()).containsExactly("gamma");
        assertThat(fixes.get(2).totalPointsGained()).isEqualTo(10);
    }

    @Test
    @DisplayName("Custom effort costs invert pick order when cost-per-covered element changes")
    void shouldRespectCustomEffortCosts() {
        // If LICENSE cost is artificially raised to 10.0 and CI reduced to 0.5:
        // LICENSE covers 2 -> ratio 5.0
        // CI covers 3 -> ratio 0.5 / 3 = 0.166
        // CI MUST be picked first!

        Map<String, Double> customCosts = Map.of(
                "ADD_LICENSE", 10.0,
                "ADD_CI", 0.5
        );

        Deduction licenseAlpha = new Deduction(ScoreCategory.LICENSING_COMMUNITY, 5, "No open-source LICENSE detected", "Add LICENSE");
        Deduction ciAlpha = new Deduction(ScoreCategory.ENGINEERING_HYGIENE, 7, "No CI/CD automation workflow found", "Add CI");

        Map<String, List<Deduction>> deductions = Map.of(
                "alpha", List.of(licenseAlpha, ciAlpha),
                "beta", List.of(licenseAlpha, ciAlpha)
        );

        FixPlanner planner = new FixPlanner(customCosts);
        FixPlan plan = planner.plan(deductions, 2);

        assertThat(plan.orderedFixes().get(0).checkKey()).isEqualTo("ADD_CI");
        assertThat(plan.orderedFixes().get(1).checkKey()).isEqualTo("ADD_LICENSE");
    }

    @Test
    @DisplayName("Empty deductions list returns clean celebration headline")
    void shouldHandleZeroDeficiencies() {
        FixPlanner planner = new FixPlanner();
        FixPlan plan = planner.plan(Map.of("repo-a", List.of(), "repo-b", List.of()), 2);

        assertThat(plan.headline()).contains("All 2 repositories meet standard hygiene guidelines!");
        assertThat(plan.orderedFixes()).isEmpty();
        assertThat(plan.totalPointsRecoverable()).isEqualTo(0);
    }
}
