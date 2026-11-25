package com.repolens.scoring.intelligence.checks;

import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.intelligence.ArchitectureObservation;
import com.repolens.scoring.intelligence.DetectedTechnology;
import com.repolens.scoring.intelligence.ImprovementPriority;
import com.repolens.scoring.intelligence.PositiveFinding;
import com.repolens.scoring.intelligence.ProjectImprovement;
import com.repolens.scoring.intelligence.ProjectType;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Project-specific engineering intelligence for React and modern Frontend repositories.
 */
public class FrontendCheckModule implements ProjectCheckModule {

    @Override
    public boolean appliesTo(Set<ProjectType> types, List<DetectedTechnology> technologies) {
        return types.contains(ProjectType.REACT_FRONTEND) || types.contains(ProjectType.FULLSTACK_APP);
    }

    @Override
    public void evaluate(
            RepoSnapshot snapshot,
            Set<ProjectType> types,
            List<DetectedTechnology> technologies,
            List<ArchitectureObservation> observations,
            List<PositiveFinding> positiveFindings,
            List<ProjectImprovement> recommendations
    ) {
        List<String> paths = snapshot.filePaths();

        // 1. Component Hierarchy & Organization
        boolean hasComponents = hasPath(paths, "components/") || hasPath(paths, "ui/");
        boolean hasPagesOrApp = hasPath(paths, "pages/") || hasPath(paths, "app/") || hasPath(paths, "views/");

        if (hasComponents && hasPagesOrApp) {
            observations.add(new ArchitectureObservation(
                    "Modular Component & Page Structure",
                    "Frontend codebase cleanly separates presentational components from routing pages/views.",
                    "Frontend Architecture",
                    "Detected dedicated components directory alongside route/page tree."
            ));
            positiveFindings.add(new PositiveFinding(
                    "FE_COMPONENT_ORGANIZATION",
                    "Structured Component Hierarchy",
                    "Presentational UI components are organized into dedicated component folders, promoting reusability.",
                    "Frontend Architecture",
                    "Found distinct components/ and view/page directory structure."
            ));
        } else if (!hasComponents && hasPagesOrApp) {
            recommendations.add(new ProjectImprovement(
                    "FE_COMPONENT_ORGANIZATION",
                    "Extract Reusable UI Components into Dedicated Directory",
                    "Inlining component logic directly into page views leads to massive files, duplicate styling, and difficult testing.",
                    "Detected pages or views, but no dedicated `components/` or `ui/` directory was found.",
                    "Extract common UI widgets (buttons, modals, cards, navbars) into `src/components/`.",
                    ImprovementPriority.MEDIUM,
                    25,
                    "src/components/",
                    "mkdir -p src/components"
            ));
        }

        // 2. Dependency Lockfile Present
        boolean hasLockfile = hasFile(paths, "package-lock.json") || hasFile(paths, "yarn.lock") || hasFile(paths, "pnpm-lock.yaml");
        if (hasLockfile) {
            positiveFindings.add(new PositiveFinding(
                    "FE_DETERMINISTIC_LOCKFILE",
                    "Deterministic Dependency Lockfile",
                    "Package lockfile guarantees identical dependency resolution across development and CI/CD pipelines.",
                    "Dependency Management",
                    "Found package-lock.json, yarn.lock, or pnpm-lock.yaml."
            ));
        } else if (hasFile(paths, "package.json")) {
            recommendations.add(new ProjectImprovement(
                    "FE_DETERMINISTIC_LOCKFILE",
                    "Commit Dependency Lockfile for Reproducible Builds",
                    "Without a committed lockfile (`package-lock.json` or `pnpm-lock.yaml`), non-pinned subdependencies can break deployments unexpectedly.",
                    "Found package.json manifest, but no package-lock.json, yarn.lock, or pnpm-lock.yaml is committed.",
                    "Run `npm install` (or `pnpm install` / `yarn install`) and commit the generated lockfile to version control.",
                    ImprovementPriority.HIGH,
                    10,
                    "package-lock.json",
                    "npm install\ngit add package-lock.json\ngit commit -m \"Add package-lock.json\""
            ));
        }

        // 3. Environment Variable Documentation (.env.example)
        boolean hasEnvExample = hasFile(paths, ".env.example") || hasFile(paths, ".env.sample") || hasFile(paths, ".env.template");
        if (hasEnvExample) {
            positiveFindings.add(new PositiveFinding(
                    "FE_ENV_TEMPLATE",
                    "Environment Configuration Template",
                    "Provided .env template clearly specifies client-side environment variable contracts without leaking secrets.",
                    "Configuration",
                    "Found .env.example / .env.sample template file."
            ));
        } else {
            recommendations.add(new ProjectImprovement(
                    "FE_ENV_TEMPLATE",
                    "Add .env.example Template for Client Configuration",
                    "Frontend apps frequently depend on API URLs (e.g. VITE_API_URL, NEXT_PUBLIC_API_URL). A template lets new developers spin up the app immediately.",
                    "No .env.example or configuration template found in repository root.",
                    "Create a `.env.example` documenting all required client environment variables with non-secret dummy placeholders.",
                    ImprovementPriority.MEDIUM,
                    10,
                    ".env.example",
                    """
                    VITE_API_BASE_URL=http://localhost:8080/api
                    VITE_APP_ENV=development
                    """.stripIndent()
            ));
        }

        // 4. UI Testing & Test Automation
        boolean hasUiTests = paths.stream().anyMatch(p -> {
            String lower = p.toLowerCase(Locale.ROOT);
            return lower.endsWith(".test.tsx") || lower.endsWith(".test.jsx")
                    || lower.endsWith(".spec.tsx") || lower.endsWith(".spec.jsx")
                    || lower.contains("__tests__/") || lower.contains("cypress/") || lower.contains("playwright/");
        });

        if (hasUiTests) {
            positiveFindings.add(new PositiveFinding(
                    "FE_UI_TESTS",
                    "Automated Component / E2E Tests",
                    "UI components and user flows are verified with automated test suites.",
                    "Quality Assurance",
                    "Found component test files (*.test.tsx / *.spec.jsx or cypress/playwright specs)."
            ));
        } else {
            recommendations.add(new ProjectImprovement(
                    "FE_UI_TESTS",
                    "Implement Component Unit and Interaction Tests",
                    "Frontend applications with zero component tests are vulnerable to visual regressions and broken user interactions upon state refactors.",
                    "No component test files (*.test.tsx / *.spec.jsx) or E2E test suites detected.",
                    "Install Vitest or Jest along with React Testing Library, and add unit tests verifying core interactive components.",
                    ImprovementPriority.HIGH,
                    35,
                    "src/__tests__/",
                    """
                    import { render, screen } from '@testing-library/react';
                    import App from '../App';

                    test('renders main application header', () => {
                      render(<App />);
                      expect(screen.getByRole('heading')).toBeInTheDocument();
                    });
                    """.stripIndent()
            ));
        }
    }

    private static boolean hasFile(List<String> paths, String target) {
        for (String p : paths) {
            int idx = p.lastIndexOf('/');
            String filename = idx >= 0 ? p.substring(idx + 1) : p;
            if (filename.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private static boolean hasPath(List<String> paths, String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        for (String p : paths) {
            if (p.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }
}
