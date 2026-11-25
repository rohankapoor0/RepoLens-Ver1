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
 * Project-specific engineering intelligence for Backend and REST API services.
 */
public class BackendCheckModule implements ProjectCheckModule {

    @Override
    public boolean appliesTo(Set<ProjectType> types, List<DetectedTechnology> technologies) {
        return types.contains(ProjectType.BACKEND_API) || types.contains(ProjectType.FULLSTACK_APP);
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
        String readme = snapshot.readmeText() != null ? snapshot.readmeText().toLowerCase(Locale.ROOT) : "";

        // 1. Layered Architecture Observation
        boolean hasControllers = hasPath(paths, "controller/") || hasPath(paths, "controllers/")
                || hasPath(paths, "routes/") || hasPath(paths, "endpoints/");
        boolean hasServices = hasPath(paths, "service/") || hasPath(paths, "services/");
        boolean hasRepositoryOrDao = hasPath(paths, "repository/") || hasPath(paths, "repositories/")
                || hasPath(paths, "dao/") || hasPath(paths, "db/");

        if (hasControllers && (hasServices || hasRepositoryOrDao)) {
            observations.add(new ArchitectureObservation(
                    "Layered Service Architecture",
                    "Application follows clean separation of concerns across presentation (controllers/routes), business logic (services), and persistence layers.",
                    "Backend Architecture",
                    "Detected dedicated controller and service/persistence package structure."
            ));
            positiveFindings.add(new PositiveFinding(
                    "BE_LAYERED_ARCHITECTURE",
                    "Separation of Concerns Across Layers",
                    "Backend structure separates routing/HTTP concerns from domain business logic.",
                    "Backend Architecture",
                    "Found distinct controller and service layers."
            ));
        }

        // 2. API Contract & Endpoint Documentation
        boolean hasOpenApi = hasFile(paths, "openapi.yaml") || hasFile(paths, "openapi.json")
                || hasFile(paths, "swagger.yaml") || hasFile(paths, "swagger.json")
                || readme.contains("/api/v") || readme.contains("endpoints") || readme.contains("swagger");

        if (hasOpenApi) {
            positiveFindings.add(new PositiveFinding(
                    "BE_API_DOCUMENTATION",
                    "Documented API Contracts",
                    "API endpoints, parameters, and payloads are explicitly documented for client integration.",
                    "API Design",
                    "Found OpenAPI/Swagger specification or documented endpoints in README."
            ));
        } else {
            recommendations.add(new ProjectImprovement(
                    "BE_API_DOCUMENTATION",
                    "Document REST Endpoints with OpenAPI / Swagger Specifications",
                    "Client developers and consumers cannot integrate with your API without explicit route and schema specifications.",
                    "Detected backend route modules, but no OpenAPI/Swagger specification or endpoint table was found in documentation.",
                    "Integrate automated OpenAPI/Swagger generation (e.g. springdoc-openapi, FastAPI auto-docs) or document key routes in README.",
                    ImprovementPriority.HIGH,
                    20,
                    "README.md",
                    """
                    ## API Endpoints
                    | Method | Route | Description |
                    |---|---|---|
                    | GET | `/api/v1/health` | Service health status |
                    | POST | `/api/v1/analyze` | Submit profile for analysis |
                    """.stripIndent()
            ));
        }

        // 3. Centralized Exception Handling
        boolean hasErrorHandler = paths.stream().anyMatch(p -> {
            String lower = p.toLowerCase(Locale.ROOT);
            return lower.contains("exceptionhandler") || lower.contains("globalexception")
                    || lower.contains("errorhandle") || lower.contains("errormiddleware")
                    || lower.contains("exceptions.py") || lower.contains("errors.py");
        });

        if (hasErrorHandler) {
            positiveFindings.add(new PositiveFinding(
                    "BE_ERROR_HANDLING",
                    "Centralized Error and Exception Handling",
                    "API uses structured error middleware or ControllerAdvice to return consistent error payloads rather than unhandled stack traces.",
                    "Reliability",
                    "Detected centralized exception handling / error middleware component."
            ));
        } else {
            recommendations.add(new ProjectImprovement(
                    "BE_ERROR_HANDLING",
                    "Implement Centralized Exception Handling Middleware",
                    "Unhandled exceptions in REST endpoints expose internal stack traces and server details to API consumers.",
                    "No centralized exception handler (ControllerAdvice, error middleware, or error handler module) was detected.",
                    "Add global error handling middleware to catch unhandled exceptions and return consistent JSON error envelopes (timestamp, error, status, message).",
                    ImprovementPriority.MEDIUM,
                    25,
                    "src/error/GlobalExceptionHandler",
                    """
                    // Standard JSON error envelope:
                    {
                      "status": 404,
                      "error": "Not Found",
                      "message": "Requested resource does not exist",
                      "timestamp": "2026-11-25T01:00:00Z"
                    }
                    """.stripIndent()
            ));
        }

        // 4. Database Migrations
        boolean hasMigrations = hasPath(paths, "db/migration") || hasPath(paths, "migrations/")
                || hasFile(paths, "schema.prisma") || hasFile(paths, "alembic.ini")
                || hasFile(paths, "flyway.conf");

        if (hasMigrations) {
            positiveFindings.add(new PositiveFinding(
                    "BE_DB_MIGRATIONS",
                    "Automated Database Migrations",
                    "Database schema versioning is managed through structured migration scripts, ensuring deterministic deployments.",
                    "Persistence",
                    "Detected database schema migrations directory/configuration."
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
