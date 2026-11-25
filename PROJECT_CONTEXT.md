# RepoLens — Complete Project Context & Architectural Specification
> **Instructions for ChatGPT / AI Assistant**:
> You are acting as the **Lead Architect & Full-Stack Engineer** on **RepoLens**.
> This document contains the **entire architecture, algorithmic foundations, scoring rubric, database persistence, REST API contracts, frontend design, and hard technical constraints** of the project.
> Read this document completely to gain 100% full context before generating code, proposing refactors, or adding new features.

---

## 1. Project Overview & Core Philosophy

**RepoLens** is a high-performance, deterministic web application that evaluates public GitHub repositories and profiles for **engineering hygiene, open-source standards, and maintenance health**.

### What RepoLens IS:
- A tool that evaluates **repository hygiene habits**: documentation completeness, license validity, automated CI pipelines, unit test presence, commit cadence, and avoidance of committed secrets/junk.
- **Arbitrary Public Username Support**: Evaluates ANY public GitHub user when entered as `username` or `@username`.
- **Backend Authentication Decoupling**: Uses RepoLens's own backend `GITHUB_TOKEN` exclusively for GitHub API quota; target users never authenticate. If no token is provided, safely falls back to unauthenticated public requests (60 req/hr).
- **Empty Repo & HTTP 409 Resilience**: HTTP 409 Conflict in GitHub REST API specifically indicates an empty repository (no commits/tree). RepoLens catches this gracefully without confusing it with authentication identity.
- **Deterministic & Explainable**: Uses mathematical rubrics (0–100 score) with explicit, itemized point deductions and actionable remediation fixes.
- **Zero-Login**: Requires no OAuth authorization, user credentials, or write permissions. Evaluates public data only.
- **Algorithmic Showcase**: Core ranking, duplicate grouping, fix planning, string scanning, and cache verification are powered by **8 hand-crafted, textbook-grade algorithms and data structures** with **zero external algorithmic dependencies**.

### What RepoLens IS NOT:
- **Not a measure of developer intelligence or coding skill**: It scores hygiene and repository management practices, not code quality or algorithmic complexity.
- **No LLM in the loop**: All scoring, deduction generation, ranking, and planning is 100% deterministic code.
- **No private repo access**: Operates strictly on public repository metadata, file trees, and READMEs.

---

## 2. Multi-Module Project Architecture

The project is structured as a Maven multi-module reactor with a strict separation of concerns:

```
Repofy (Root Parent POM)
├── core/   (Pure Java library — ZERO framework dependencies)
└── web/    (Spring Boot 3.3.5 application — REST API, H2 JPA, Static UI)
```

### Architectural Guardrails (MUST NEVER BE VIOLATED):
1. **Zero Spring in `core`**: The `core` module must **never** import Spring, Hibernate, or web framework packages. It must remain standard Java 17+ so it can be compiled, benchmarked, and embedded into any CLI, daemon, or lambda without Spring overhead.
2. **Untouchable Algorithms Package**: The package `com.repolens.core.algorithms` contains hand-crafted data structures. Do not replace them with Java standard collections (e.g. do not replace `BinaryHeap` with `PriorityQueue`, do not replace `CountingSort` with `Arrays.sort`).
3. **Bloom Filter Confirmation Invariant**: The hand-written `BloomFilter` is a probabilistic pre-check with **zero false negatives**. However, because it has a ~1% false positive rate, **any positive `mightContain()` result MUST BE CONFIRMED against the database** before skipping work.
4. **Token Security**: The GitHub Personal Access Token (`GITHUB_TOKEN`) must never be hardcoded, logged, or sent to the frontend. It is read strictly from system environment variables or local `.env` (gitignored).
5. **XSS Prevention**: The frontend must never insert unsanitized GitHub strings into `innerHTML`. All dynamic strings must use `document.createTextNode` or `element.textContent`.

---

## 3. The 8 Hand-Crafted Algorithms (`com.repolens.core.algorithms`)

| Algorithm | Class | RepoLens Application | Time Complexity | Space Complexity |
| :--- | :--- | :--- | :--- | :--- |
| **1. Binary Heap** | `BinaryHeap.java` | Ranks repos best-to-worst ($O(n \log n)$ total extraction) and extracts Top-$K$ priority actions in $O(n \log k)$. | Push/Pop: $O(\log n)$, Peek: $O(1)$, BuildHeap: $O(n)$ | $O(n)$ array |
| **2. Trie** | `Trie.java` | Fast prefix and exact matching on repo file paths to detect committed secrets (`.env`, `id_rsa`) and junk (`node_modules/`, `.DS_Store`). | Search / Prefix: $O(L)$ where $L$ = path length | $O(\Sigma \cdot L \cdot N)$ |
| **3. Knuth-Morris-Pratt** | `KmpMatcher.java` | Scans README text for sensitive tokens without backtracking using an LPS ($\pi$) failure table. | Search: $O(n + m)$, LPS build: $O(m)$ | $O(m)$ auxiliary |
| **4. Disjoint Set Union** | `DisjointSetUnion.java` | Clusters duplicate or forked repositories using union by rank and path compression. | $O(\alpha(n)) \approx O(1)$ per operation | $O(n)$ |
| **5. Greedy Weighted Set Cover** | `GreedyWeightedSetCover.java` | Computes the optimal "Fix Plan": finds the minimal-cost set of developer fixes that resolves the most deficiencies across all repos. | $O(\|U\| \cdot m)$ polynomial time (Chvátal heuristic) | $O(\|U\| + m)$ |
| **6. Bloom Filter** | `BloomFilter.java` | In-memory probabilistic cache pre-check using Kirsch-Mitzenmacher MurmurHash3 double hashing. | $O(k)$ bit operations | $O(m)$ bits (~11.7 KB for 10k items) |
| **7. Quickselect** | `Quickselect.java` | Calculates median hygiene scores and quartiles across a profile using in-place partitioning with median-of-three pivot. | Average: $O(n)$ linear, Worst: $O(n^2)$ (mitigated) | $O(1)$ in-place |
| **8. Counting Sort** | `CountingSort.java` | Non-comparison distribution sort for integer scores $[0, 100]$ to build score bands and sort repos in strictly linear time. | $O(n + k) = O(n)$ since $k = 101$ is constant | $O(n + k)$ |

---

## 4. The 100-Point Scoring Engine (`com.repolens.scoring`)

A `RepoSnapshot` represents raw repo data (metadata, file tree paths, decoded README string, recent commit dates). `RepoScorer` invokes 6 modular sub-scorers:

### 1. Documentation (`DocumentationScorer.java` — 25 Points)
- **README Exists**: 8 pts.
- **README Length**: 4 pts (>200 words: 4 pts, >50 words: 2 pts, $\le 50$ words: 0 pts).
- **Install & Usage Sections**: 4 pts (2 pts for installation heading detection, 2 pts for usage heading detection).
- **Screenshots / Visuals**: 3 pts (detects Markdown `![]()` or HTML `<img>` tags in README).
- **Repo Description**: 3 pts (non-empty description in repo metadata).
- **Topics**: 3 pts (GitHub repository topics/tags defined).

### 2. Licensing & Community (`LicensingCommunityScorer.java` — 10 Points)
- **License**: 5 pts (valid `LICENSE`, `LICENSE.md`, or recognized SPDX key).
- **Contributing Guidelines**: 2 pts (`CONTRIBUTING.md` in root, `.github/`, or `docs/`).
- **Issue Templates**: 2 pts (`.github/ISSUE_TEMPLATE/` or `issue_template.md`).
- **Pull Request Template**: 1 pt (`.github/PULL_REQUEST_TEMPLATE/` or `pull_request_template.md`).

### 3. Maintenance (`MaintenanceScorer.java` — 20 Points)
- **Commit Recency**: 10 pts (<30 days: 10 pts, <90 days: 7 pts, <180 days: 4 pts, <365 days: 2 pts, >365 days: 0 pts).
- **Commit Cadence**: 5 pts ($\ge 10$ commits in last 30 commits window: 5 pts, $\ge 5$: 3 pts, $\ge 1$: 1 pt).
- **Active Status**: 5 pts (0 pts if repo is archived by owner).

### 4. Engineering Hygiene (`EngineeringHygieneScorer.java` — 25 Points)
- **Automated Tests**: 8 pts (detects `test/`, `tests/`, `src/test/`, `*_test.go`, `*.test.js`, etc.).
- **CI / Workflows**: 7 pts (detects workflows in `.github/workflows/` or `.gitlab-ci.yml`).
- **Git Ignore**: 5 pts (presence of `.gitignore`).
- **Build / Dependency Manifest**: 5 pts (`pom.xml`, `package.json`, `build.gradle`, `requirements.txt`, `Cargo.toml`, `go.mod`, etc.).

### 5. Safety & Security (`SafetyScorer.java` — 15 Points)
- Uses `Trie` and `KmpMatcher` to inspect file paths and README contents.
- **Forbidden committed files**: Deductions for `.env`, `.env.local`, `id_rsa`, `id_ed25519`, `credentials.json`, `node_modules/`, `.DS_Store`, `target/classes/`, `dist/`.
- **Secret Substrings in README**: Detects AWS keys (`AKIA...`), RSA private keys, GitHub tokens (`ghp_...`).
- Starts with 15 pts; loses points per violation down to minimum 0.

### 6. Project Structure (`StructureScorer.java` — 5 Points)
- **Standard Directory Organization**: 3 pts (presence of standard layout like `src/`, `lib/`, `app/`, `pkg/`, etc.).
- **No Monolithic Single-File Bloat**: 2 pts (deducts if repository contains only 1 or 2 flat files without proper structure).

---

## 5. Remediation & Duplicate Analysis Systems

### 1. Fix Planner (`com.repolens.core.planner.FixPlanner`)
- Formulates repository hygiene remediation as a **Weighted Set Cover** problem:
  - **Universe ($U$)**: All $(repo, deficiency)$ pairs across the user's analyzed repositories.
  - **Candidate Fixes ($\mathcal{S}$)**: Remediations with realistic engineering effort weights:
    - Add LICENSE (Cost: 1.0)
    - Add `.gitignore` (Cost: 1.0)
    - Add Repository Topics (Cost: 1.0)
    - Add README Install/Usage Sections (Cost: 2.0)
    - Configure GitHub Actions CI (Cost: 3.0)
    - Add Automated Tests (Cost: 5.0)
- Executes `GreedyWeightedSetCover.solve(universe, candidateSets)` using Chvátal's greedy heuristic:
  $$\alpha_j = \frac{\text{cost}(S_j)}{\text{newly covered defects}}$$
- Produces prioritized action items and executive headlines (e.g., *"3 fixes improve 9 of your 12 repositories"*).

### 2. Duplicate Finder (`com.repolens.core.duplicate.DuplicateFinder`)
- Identifies redundant, cloned, or obsolete repositories across a user's account using `DisjointSetUnion`:
  - **Exact README SHA-256 Hash**: Links repos with identical documentation.
  - **Jaccard File-Tree Overlap**: Computes $\frac{|A \cap B|}{|A \cup B|}$ on normalized file paths. Links repos with $\ge 70\%$ path overlap.
  - **Normalized Name Similarity**: Links repos with matching stripped names (e.g. `repo-v1` and `repo-v2`).
- Aggregates connected components into `DuplicateGroup` records with clear human-readable explanations.

---

## 6. Two-Tiered Cache & Database Persistence

### Two-Tier Cache Flow
1. **Tier 1 (In-Memory Bloom Filter)**:
   - Initialized at startup and seeded from existing database records.
   - Key format: `{owner}/{repo}@{commit_sha}`.
   - If `bloomFilter.mightContain(key) == false`, the repo is **definitely not cached**. We immediately trigger GitHub API fetching without hitting disk.
2. **Tier 2 (Persistent H2 Database - `data/repolens.mv.db`)**:
   - If `bloomFilter.mightContain(key) == true`, a JPA query checks `CachedRepoScoreRepository`.
   - On a hit: deserializes JSON score, updates metrics, and returns cached score with zero API calls.
   - On a miss (false positive ~1%): fetches from GitHub, saves to H2, and adds key to Bloom filter.

### JPA Entities (`com.repolens.web.cache.entity`)
- `CachedRepoScoreEntity`: Keyed by `cacheKey` (`{owner}/{repo}@{commit_sha}`), stores snapshot summary, score JSON, and fetch timestamp.
- `CachedUserAnalysisEntity`: Keyed by `username`, stores the complete aggregated profile analysis with a configurable TTL (default: 6 hours).
- `CachedETagEntity`: Keyed by GitHub API request URL, stores `ETag` headers for HTTP 304 Not Modified request preservation.

---

## 7. Web API Endpoints & Contracts

| Endpoint | Method | Description |
| :--- | :---: | :--- |
| `GET /api/analyze/{username}`<br>`POST /api/analyze/{username}` | `GET/POST` | Runs full analysis on user's public repos. Returns heap-ranked repos, CountingSort score bands, Quickselect median score, stale repos list, fix plan, and duplicate clusters. |
| `GET /api/repo/{owner}/{repo}` | `GET` | Returns single repository snapshot, score breakdown, category subtotals, and itemized deductions. |
| `GET /api/status` | `GET` | Returns GitHub API remaining quota, rate limit reset time, and cache hits/saved request metrics. |
| `GET /badge/{owner}/{repo}.svg` | `GET` | Returns dynamic SVG shields-style badge for README embedding with score and color tier. |
| `GET /u/{username}` | `GET` | Serves shareable, neutral report card HTML page. |
| `GET /rubric` | `GET` | Serves the interactive 100-point scoring rubric HTML page. |

### Error Handling & Throttling
- **Visitor Throttling (`VisitorThrottleService`)**: Restricts each visitor IP to analyzing at most $N$ distinct usernames per hour (default: 10). Returns friendly 429 JSON on limit breach.
- **Username Validation (`GithubUsernameValidator`)**: Enforces GitHub naming rules (1-39 chars, alphanumeric and single hyphens, no leading/trailing hyphens). Returns 400 on invalid input.
- **Repo Capping**: Caps analysis at 50 repositories per user (configurable) to prevent API exhaustion.
- **Rate Limit Resilience**: If GitHub returns HTTP 403/429, RepoLens automatically serves cached analyses (annotated with age) rather than failing.

---

## 8. Complete Project File Tree & Responsibilities

```
Repofy/
├── pom.xml                                   # Root Maven reactor POM
├── README.md                                 # Public documentation with Mermaid architecture
├── PROJECT_CONTEXT.md                        # Master LLM context file (THIS FILE)
├── .env                                      # Gitignored local environment file (GITHUB_TOKEN=...)
├── data/
│   └── repolens.mv.db                        # Persistent H2 file database
├── docs/
│   ├── ALGORITHMS.md                         # Deep dive into the 8 hand-crafted algorithms
│   ├── BENCHMARK.md                          # Empirical benchmarks (Cache savings & Set Cover)
│   └── screenshots/                          # Dashboard & badge UI screenshots
│
├── core/                                     # ZERO Spring dependencies
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/repolens/
│       │   ├── algorithms/                   # 8 Custom algorithms
│       │   │   ├── BinaryHeap.java
│       │   │   ├── BloomFilter.java
│       │   │   ├── CountingSort.java
│       │   │   ├── DisjointSetUnion.java
│       │   │   ├── GreedyWeightedSetCover.java
│       │   │   ├── KmpMatcher.java
│       │   │   ├── Quickselect.java
│       │   │   └── Trie.java
│       │   ├── cache/                        # Cache contracts & metrics
│       │   │   ├── AnalysisCache.java
│       │   │   ├── BloomFilterRepoScoreCache.java
│       │   │   ├── CacheMetrics.java
│       │   │   ├── CachedAnalysis.java
│       │   │   ├── CachedRepoScoreRecord.java
│       │   │   └── RepoScoreCache.java
│       │   ├── duplicate/                    # DSU Duplicate Finder
│       │   │   ├── DuplicateFinder.java
│       │   │   ├── DuplicateGroup.java
│       │   │   ├── DuplicateReport.java
│       │   │   └── RepoProfile.java
│       │   ├── github/                       # GitHub client & models
│       │   │   ├── GithubClient.java
│       │   │   ├── etag/                     # ETag storage contracts
│       │   │   ├── exception/                # Domain exceptions (RateLimit, UserNotFound)
│       │   │   └── model/                    # GithubRepo, GithubTree, RateLimitInfo
│       │   ├── planner/                      # Greedy Set Cover Fix Planner
│       │   │   ├── FixItem.java
│       │   │   ├── FixPlan.java
│       │   │   ├── FixPlanner.java
│       │   │   └── RepoDeficiency.java
│       │   └── scoring/                      # 100-Point Scoring Engine
│       │       ├── CategoryResult.java
│       │       ├── CategoryScorer.java
│       │       ├── Deduction.java
│       │       ├── RepoScore.java
│       │       ├── RepoScorer.java
│       │       ├── RepoSnapshot.java
│       │       ├── ScoreCategory.java
│       │       └── rules/                    # 6 Modular Scorer Rules
│       │           ├── DocumentationScorer.java
│       │           ├── EngineeringHygieneScorer.java
│       │           ├── LicensingCommunityScorer.java
│       │           ├── MaintenanceScorer.java
│       │           ├── SafetyScorer.java
│       │           └── StructureScorer.java
│       └── test/java/com/repolens/core/
│           ├── algorithms/                   # Rigorous unit tests for each algorithm
│           ├── benchmark/
│           │   └── RepoLensBenchmarkTest.java# Cache reduction & Chvátal bound tests
│           ├── cache/
│           ├── duplicate/
│           ├── planner/
│           └── scoring/                      # Fixture snapshot tests (great, empty, messy)
│
└── web/                                      # Spring Boot web service & frontend
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/repolens/web/
        │   │   ├── RepoLensApplication.java  # Spring Boot entry point
        │   │   ├── cache/                    # H2 JPA Persistence
        │   │   │   ├── entity/               # CachedRepoScoreEntity, etc.
        │   │   │   ├── repository/           # Spring Data JPA Repositories
        │   │   │   └── service/              # JpaRepoScoreCache, JpaETagStorage
        │   │   ├── config/                   # CacheConfig, WebMvcConfig
        │   │   ├── controller/               # REST Endpoints
        │   │   │   ├── AnalysisController.java
        │   │   │   ├── BadgeController.java
        │   │   │   ├── DebugRepoController.java
        │   │   │   ├── DebugScoreController.java
        │   │   │   ├── HealthController.java
        │   │   │   ├── RepoDetailController.java
        │   │   │   ├── ReportCardController.java
        │   │   │   └── StatusController.java
        │   │   ├── dto/                      # EvaluatedRepo, API DTOs
        │   │   ├── exception/                # Global exception handling & 429
        │   │   ├── service/                  # AnalysisService (orchestrator)
        │   │   ├── throttle/                 # VisitorThrottleService (IP throttling)
        │   │   └── validation/               # GithubUsernameValidator
        │   └── resources/
        │       ├── application.properties    # H2, TTL, and throttling config
        │       └── static/                   # Plain HTML/CSS/JS frontend
        │           ├── index.html            # Primary Interactive Dashboard
        │           ├── report.html           # Shareable Report Card (/u/{username})
        │           └── rubric.html           # 100-Point Scoring Rubric
        └── test/java/com/repolens/web/
            └── controller/                   # SpringBootTest & MockMvc tests
```

---

## 9. Technology Stack & Runtime Configuration

- **Language / Runtime**: Java 17+ (Compiles on Java 17, 21, and 25).
- **Build System**: Maven 3.8+ Multi-Module Reactor.
- **Framework**: Spring Boot 3.3.5 (Spring MVC, Spring Data JPA, HikariCP, Tomcat).
- **Database**: H2 Database (File mode at `data/repolens.mv.db`, zero external DB setup needed).
- **Frontend**: Vanilla JavaScript (ES6+), Modern Responsive CSS with CSS Custom Properties, Chart.js for score band distributions, FontAwesome icons. Zero node/npm build step required.
- **Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test (`MockMvc`).
- **Java 25 ByteBuddy Support**: Configured with `<argLine>-Dnet.bytebuddy.experimental=true</argLine>` in root Surefire configuration.

### Common Commands
```bash
# Run all unit tests and benchmarks across both modules:
mvn clean test

# Run only algorithm and benchmark tests:
mvn test -pl core -Dtest=RepoLensBenchmarkTest

# Run the Spring Boot web application locally:
mvn spring-boot:run -pl web

# Build standalone executable JAR:
mvn clean package -DskipTests
java -jar web/target/web-1.0.0-SNAPSHOT.jar
```

---

## 10. Future Extension Ideas & Development Roadmap

When developing next iterations of RepoLens with ChatGPT, here are high-value enhancement ideas:

1. **PDF / JSON Report Export**: Add an endpoint (`GET /api/export/{username}.pdf` or `.json`) allowing students or developers to download a professional audit report.
2. **Scheduled Profile Monitoring & Webhooks**: Periodically re-evaluate a set of tracked profiles and deliver alerts when a repo drops into "stale" status or introduces an exposed key.
3. **Automated Pull Request Hygiene Bot**: A GitHub Action or webhook bot that automatically evaluates a PR's changed files against the RepoLens safety Trie and Engineering Hygiene rubric.
4. **GitLab & Bitbucket Support**: Abstract `GithubClient` into a general `GitPlatformClient` interface to support GitLab and Bitbucket APIs.
5. **Interactive Fix Script Generator**: In the Fix Planner tab, provide a "Generate Shell Fix Script" button that outputs a bash script to automatically generate standard `.gitignore`, `LICENSE`, and GitHub Actions workflow stubs.
