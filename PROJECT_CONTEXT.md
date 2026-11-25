# RepoLens: Deterministic GitHub Repository Quality & Hygiene Dashboard
## Comprehensive Engineering Project Report & Architectural Specification

```
Project Title:        RepoLens (Deterministic Repository Quality & Hygiene Engine)
Project Subtitle:     Multi-Module Static Analysis, Algorithmic Remediation, and MCP Integration
Runtime & Platform:   Java 17 (LTS), Spring Boot 3.3.5, Model Context Protocol (MCP)
Deployment:           Vercel Fluid Compute (Single Container OCI Runtime)
Repository:           https://github.com/rohankapoor0/RepoLens-Ver1.git
Branch:               main
Document Status:      Production-Ready Comprehensive Engineering Report
```

> [!NOTE]
> **Instructions for AI Assistants (ChatGPT / Claude / Gemini)**:
> You are acting as the **Lead Architect & Principal Systems Engineer** for RepoLens. This document serves as the single source of truth (SSOT) detailing the complete mathematical foundations, multi-module architecture, custom algorithm implementations, database persistence strategies, REST API contracts, Model Context Protocol (MCP) server specifications, and cloud deployment topology. Read this document thoroughly to gain 100% comprehensive context before contributing to this codebase.

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Introduction, Problem Statement & Objectives](#2-introduction-problem-statement--objectives)
3. [System Architecture & Multi-Module Design](#3-system-architecture--multi-module-design)
4. [Algorithmic Foundations (8 Custom Algorithms)](#4-algorithmic-foundations-8-custom-algorithms)
5. [The Deterministic 100-Point Scoring Engine](#5-the-deterministic-100-point-scoring-engine)
6. [Remediation Planning & Duplicate Detection Systems](#6-remediation-planning--duplicate-detection-systems)
7. [Multi-Tiered Caching & Database Persistence](#7-multi-tiered-caching--database-persistence)
8. [Model Context Protocol (MCP) STDIO Tool Server](#8-model-context-protocol-mcp-stdio-tool-server)
9. [Web Application & Frontend User Experience](#9-web-application--frontend-user-experience)
10. [Cloud Deployment & Containerization (Vercel)](#10-cloud-deployment--containerization-vercel)
11. [Testing, Empirical Verification & Benchmarks](#11-testing-empirical-verification--benchmarks)
12. [Security, Sanitization & Privacy Standards](#12-security-sanitization--privacy-standards)
13. [Complete Codebase File Tree & Developer Reference](#13-complete-codebase-file-tree--developer-reference)
14. [Conclusion & Future Roadmap](#14-conclusion--future-roadmap)

---

## 1. Executive Summary

In contemporary software engineering education and industry recruitment, GitHub profiles are universally utilized as primary digital portfolios. However, recruiters, hiring managers, and students face a critical evaluation bottleneck: assessing multiple repositories manually is subjective, error-prone, and time-intensive.

**RepoLens** is an open-source, deterministic static analysis system engineered to evaluate public GitHub repositories and profiles against standardized engineering hygiene, documentation completeness, and maintenance rubrics. Unlike arbitrary heuristics or non-deterministic Large Language Model (LLM) evaluations, RepoLens operates on a **strictly deterministic, mathematical 100-point scoring rubric**.

### Key System Achievements:
- **Zero Algorithmic External Dependencies**: Powered by **8 hand-crafted, textbook-grade data structures and algorithms** in pure Java 17 (Binary Heap, Trie, KMP, Disjoint Set Union, Greedy Weighted Set Cover, Bloom Filter, Quickselect, and Counting Sort).
- **Two-Tiered Cache with 90% Quota Reduction**: Combines an in-memory Kirsch-Mitzenmacher MurmurHash3 Bloom Filter with a persistent H2 relational database, saving over 90% of GitHub API requests while reducing average evaluation latency from ~4,200 ms to **1.8 ms**.
- **Dual Interface Architecture**: Seamlessly accessible through a modern, responsive web dashboard or directly inside AI coding assistants (Cursor, Claude Desktop, Claude Code, Antigravity) via a native **Model Context Protocol (MCP)** STDIO server.
- **Production-Grade Containerization**: Containerized via a multi-stage `Dockerfile.vercel` running on Vercel Fluid Compute, serving both the static frontend assets and REST APIs from a single domain.

---

## 2. Introduction, Problem Statement & Objectives

### 2.1 Problem Statement
Evaluating a developer's GitHub portfolio presents several systemic engineering challenges:
1. **GitHub API Rate Limits**: Unauthenticated requests are restricted to 60 requests/hour; authenticated personal access tokens are capped at 5,000 requests/hour. Evaluating a portfolio of 15–20 repositories requires analyzing trees, commit timelines, and documentation across 60–80 individual API calls, rapidly exhausting quotas.
2. **Subjectivity & Inconsistency**: Manual code reviews lack deterministic criteria. Evaluators grade inconsistently on whether a project contains proper licensing, test suites, or CI automation.
3. **Black-Box AI Hallucinations**: Prompting generative AI models directly with repository links often results in hallucinated dependencies, fabricated test coverage, or inconsistent grades.
4. **Tooling Silos**: Static analysis tools typically require local repository cloning or complex CI installation, preventing instant browser-based or editor-integrated evaluations.

### 2.2 Project Objectives
- **Deterministic Transparency**: Implement an explainable 100-point rubric with itemized deductions, category scores, and direct remediation snippets.
- **Algorithmic Showcase**: Demonstrate the practical application of classical computer science algorithms to real-world software engineering data without relying on third-party algorithmic libraries.
- **Arbitrary Public Profile Support**: Evaluate ANY public GitHub user without requiring user login, OAuth permissions, or write access.
- **Multi-Client Interoperability**: Support standard browsers, REST clients, and modern AI developer environments via the Model Context Protocol.

```mermaid
flowchart LR
    A["Developer / Recruiter / AI Agent"] --> B{"Interface"}
    B -->|"Browser UI"| C["Web Dashboard (/index.html)"]
    B -->|"Report Card"| D["Shareable Card (/u/:username)"]
    B -->|"IDE / Assistant"| E["MCP STDIO Server (repolens-mcp.jar)"]
    
    C --> F["Spring Boot REST API"]
    D --> F
    E --> F
    
    F --> G["Core Scoring & Algorithms Engine"]
    G --> H[("Bloom Filter & H2 Cache")]
    G --> I["GitHub REST API v3"]
```

---

## 3. System Architecture & Multi-Module Design

RepoLens is organized as a high-cohesion, low-coupling **Maven Multi-Module Reactor**:

```
RepoLens (Root Parent POM - com.repolens:repolens:1.0.0-SNAPSHOT)
├── core/         (Pure Java 17 Library — ZERO Framework Dependencies)
├── web/          (Spring Boot 3.3.5 Application — REST API, JPA, Embedded Tomcat)
└── mcp-server/   (Standalone Shaded Fat JAR — Model Context Protocol STDIO Server)
```

```mermaid
graph TD
    subgraph "Parent Module (com.repolens:repolens)"
        POM["Root pom.xml (Dependency Management, Java 17, Plugin Management)"]
    end

    subgraph "core Module (com.repolens:core)"
        ALG["algorithms/ (8 Custom DSA Implementations)"]
        SCORE["scoring/ (100-Point Scoring Engine & Rules)"]
        PLAN["planner/ (Greedy Set Cover Fix Planner)"]
        DUP["duplicate/ (DSU Duplicate Clustering)"]
        GH["github/ (REST Client, Models, ETag Cache)"]
    end

    subgraph "web Module (com.repolens:web)"
        CTRL["controller/ (REST & Forwarding Controllers)"]
        JPA["cache/ (Spring Data JPA & H2 Database)"]
        UI["resources/static/ (Vanilla HTML/CSS/JS Frontend)"]
        THROT["throttle/ (IP Throttling & Username Validation)"]
    end

    subgraph "mcp-server Module (com.repolens:mcp-server)"
        MCP["McpServerApplication (Java MCP SDK Bootstrap)"]
        TOOLS["tools/ (7 Registered MCP Tool Handlers)"]
        CLIENT["client/ (RepoLensBackendClient)"]
    end

    POM --> core
    POM --> web
    POM --> mcp-server

    core --> web
    core --> mcp-server
    web -.->|"REST HTTP (:8080)"| mcp-server
```

### Architectural Guardrails (Non-Negotiable Constraints):
1. **Zero Framework Dependencies in `core`**: The `core` module contains **no Spring, no Hibernate, and no web annotations**. It remains standard Java 17+, allowing it to be compiled and benchmarked independently.
2. **Untouchable Algorithms**: The classes in `com.repolens.core.algorithms` are written from scratch. Standard library shortcuts (e.g. `java.util.PriorityQueue` instead of `BinaryHeap`) are strictly prohibited.
3. **Strict STDIO Purity in MCP**: The Model Context Protocol mandates that `stdout` carries **only valid JSON-RPC 2.0 frames**. Any log messages, diagnostic prints, or banner text must be emitted to `stderr`.

---

## 4. Algorithmic Foundations (8 Custom Algorithms)

Every computational step in RepoLens—from path scanning to multi-repository optimization—is driven by hand-crafted data structures implemented in package `com.repolens.core.algorithms`.

### Algorithm Summary Matrix

| # | Algorithm | Class Name | Time Complexity (Avg) | Time Complexity (Worst) | Space Complexity | Practical Purpose in RepoLens |
|---|---|---|---|---|---|---|
| **1** | **Binary Heap** | `BinaryHeap.java` | $O(\log n)$ push/pop | $O(\log n)$ push/pop | $O(n)$ array | Best-to-worst repository ranking and Top-$K$ priority action extraction. |
| **2** | **Trie** | `Trie.java` | $O(L)$ search/prefix | $O(L)$ search/prefix | $O(\Sigma \cdot L \cdot N)$ | Scans 10,000+ repo paths for committed secrets (`.env`) and junk (`node_modules/`). |
| **3** | **Knuth-Morris-Pratt** | `KmpMatcher.java` | $O(n + m)$ search | $O(n + m)$ search | $O(m)$ auxiliary | Inspects README text for sensitive keys (`AKIA...`, private keys) without backtracking. |
| **4** | **Disjoint Set Union** | `DisjointSetUnion.java` | $O(\alpha(n)) \approx O(1)$ | $O(\alpha(n))$ | $O(n)$ array | Clusters duplicate and cloned repositories via path Jaccard similarity and hash overlap. |
| **5** | **Greedy Weighted Set Cover** | `GreedyWeightedSetCover.java`| $O(\|U\| \cdot m)$ | $O(\|U\| \cdot m)$ | $O(\|U\| + m)$ | Computes minimal-effort developer remediation plan with bounded Chvátal approximation. |
| **6** | **Bloom Filter** | `BloomFilter.java` | $O(k)$ bit operations | $O(k)$ bit operations | $O(m)$ bits (~11.7 KB) | In-memory probabilistic pre-check preventing cold disk I/O with 0 false negatives. |
| **7** | **Quickselect** | `Quickselect.java` | $O(n)$ linear | $O(n^2)$ (mitigated) | $O(1)$ in-place | Computes median profile hygiene score and quartile percentiles with median-of-three. |
| **8** | **Counting Sort** | `CountingSort.java` | $O(n + k) = O(n)$ | $O(n + k) = O(n)$ | $O(n + k)$ auxiliary | Sorts integer scores $[0, 100]$ in strict linear time to render histogram score bands. |

---

### Detailed Mathematical & Implementation Profiles

#### 1. Binary Heap (`BinaryHeap.java`)
- **Structure**: Complete binary tree maintained in a contiguous `ArrayList<T>`. Node at index $i$ has left child $2i + 1$, right child $2i + 2$, and parent $\lfloor (i - 1)/2 \rfloor$.
- **Linear-Time Build**: Implements bottom-up `buildHeap` in $O(n)$ time using `siftDown` from index $\lfloor n/2 \rfloor - 1$ down to 0:
  $$\sum_{h=0}^{\lfloor \log n \rfloor} \frac{n}{2^{h+1}} O(h) = O\left(n \sum_{h=0}^{\infty} \frac{h}{2^h}\right) = O(n)$$
- **Application**: Ranks evaluated repositories from highest to lowest score in $O(n \log n)$ total extraction time, and extracts the top $K$ worst deficiencies in $O(n \log K)$.

#### 2. Trie (Prefix Tree - `Trie.java`)
- **Structure**: Multi-way tree where edges represent characters.
- **Prefix & Exact Search**: Provides exact token matching and prefix testing (`hasPrefix(path)`).
- **Application**: Evaluates entire repository file trees against blacklisted file patterns (`.env`, `id_rsa`, `id_ed25519`, `credentials.json`) and blacklisted directories (`node_modules/`, `.DS_Store`, `target/classes/`, `__pycache__/`). The runtime is $O(L)$ where $L$ is path length, completely independent of the number of patterns in the database.

#### 3. Knuth-Morris-Pratt Algorithm (`KmpMatcher.java`)
- **Structure**: Linear-time exact pattern matching using the Longest Proper Prefix which is also a Suffix (LPS, or $\pi$-table).
- **Application**: Scans raw README markdown strings for accidental secret disclosures (e.g., `-----BEGIN RSA PRIVATE KEY-----`, `AKIA[0-9A-Z]{16}`) in $O(n + m)$ time without backtracking, rendering it immune to algorithmic complexity attacks on repeated inputs.

#### 4. Disjoint Set Union (`DisjointSetUnion.java`)
- **Structure**: Disjoint set forest parameterized with **Union by Rank** and **Path Compression**:
  ```java
  public T find(T item) {
      T parent = parentMap.get(item);
      if (!parent.equals(item)) {
          parentMap.put(item, find(parent)); // Path compression
      }
      return parentMap.get(item);
  }
  ```
- **Application**: Groups duplicate and cloned repositories into connected equivalence classes using README SHA-256 hashes, file tree Jaccard overlap ($\ge 70\%$), and normalized repository name similarity. Amortized runtime per operation is $O(\alpha(n)) \le 4$, effectively constant time.

#### 5. Greedy Weighted Set Cover (`GreedyWeightedSetCover.java`)
- **Formulation**: Let $U$ be the universe of all $(repo, defect)$ deficiencies discovered across a user's repositories. Let $\mathcal{S}$ be the set of candidate engineering fixes, where each fix $S_j \in \mathcal{S}$ covers a subset of defects with cost $w(S_j) > 0$.
- **Algorithm (Vasek Chvátal, 1979)**: At each iteration, choose the fix $S^*$ that minimizes the cost-effectiveness ratio:
  $$\alpha_j = \frac{w(S_j)}{|S_j \setminus C_{\text{already\_covered}}|}$$
- **Approximation Guarantee**: While Minimum Weighted Set Cover is NP-complete, Chvátal proved that the greedy heuristic guarantees an approximation ratio bounded by the $k$-th harmonic number:
  $$\frac{\text{Cost}(\text{Greedy})}{\text{Cost}(\text{OPT})} \le H(|U|) = \sum_{i=1}^{|U|} \frac{1}{i} \le \ln |U| + 1$$
- **Application**: Computes prioritized action items in the "Fix Planner" (e.g. *"Adding a LICENSE and .gitignore fixes 9 of your 12 repositories for minimal effort"*).

#### 6. Bloom Filter (`BloomFilter.java`)
- **Structure**: Space-efficient probabilistic bit-array of size $m$ with $k$ independent hash functions based on Kirsch-Mitzenmacher double hashing:
  $$g_i(x) = h_1(x) + i \cdot h_2(x) \pmod m, \quad 0 \le i < k$$
- **Parameters**: For $n = 10,000$ expected items with false positive rate $p = 0.01$ (1%):
  $$m = -\frac{n \ln p}{(\ln 2)^2} \approx 95,851 \text{ bits} \ (\approx 11.7 \text{ KB RAM}), \quad k = \frac{m}{n} \ln 2 \approx 7 \text{ hashes}$$
- **Invariant**: Guarantees **zero false negatives**. If `mightContain(key) == false`, the repository is definitively uncached, bypassing disk lookups entirely.

#### 7. Quickselect (`Quickselect.java`)
- **Algorithm**: Hoare's selection algorithm utilizing **median-of-three pivot selection** (first, middle, last element) to eliminate $O(n^2)$ degradation on sorted arrays.
- **Application**: Calculates the median hygiene score and 25th/75th percentiles across a user's public portfolio in average $O(n)$ time with $O(1)$ auxiliary space.

#### 8. Counting Sort (`CountingSort.java`)
- **Algorithm**: Non-comparison integer sort operating over range $[0, 100]$ with frequency counting array of size $k = 101$.
- **Application**: Sorts repository scores and builds discrete score bands (Needs Attention, Fair, Good, Excellent) in strict $O(n + k) = O(n)$ linear time.

---

## 5. The Deterministic 100-Point Scoring Engine

RepoLens scores every repository on an absolute scale of 0 to 100 across **six modular scoring disciplines**:

```mermaid
pie title RepoLens 100-Point Quality Weighting
    "Documentation (25 pts)" : 25
    "Engineering Hygiene (25 pts)" : 25
    "Maintenance Cadence (20 pts)" : 20
    "Safety & Secret Prevention (15 pts)" : 15
    "Licensing & Community (10 pts)" : 10
    "Project Structure (5 pts)" : 5
```

### Modular Scoring Breakdown

| Category | Max Pts | Evaluated Rules & Evidence Checks | Point Allocation |
| :--- | :---: | :--- | :--- |
| **Documentation** | **25** | • **README Existence**: Presence of root or docs README.<br>• **README Word Count**: Comprehensive documentation (>200 words: 4 pts, >50 words: 2 pts).<br>• **Installation & Usage**: Detected headings for Install & Usage.<br>• **Visual Artifacts**: Markdown `![]()` or HTML `<img>` screenshots.<br>• **Metadata Completeness**: Non-empty description & repository topics. | 8 pts<br>4 pts<br><br>4 pts<br>3 pts<br>6 pts |
| **Engineering Hygiene**| **25** | • **Automated Tests**: Test files (`test/`, `src/test/`, `*.test.js`, `*_test.go`).<br>• **CI/CD Automation**: GitHub Actions workflows or `.gitlab-ci.yml`.<br>• **Git Ignore**: Presence of `.gitignore` file.<br>• **Build Configuration**: Manifest (`pom.xml`, `package.json`, `Cargo.toml`, etc.). | 8 pts<br>7 pts<br>5 pts<br>5 pts |
| **Maintenance** | **20** | • **Commit Recency**: Days since last push (<30d: 10, <90d: 7, <180d: 4, <365d: 2).<br>• **Commit Cadence**: Commit frequency ($\ge 10$ commits in last 30: 5, $\ge 5$: 3).<br>• **Active Status**: Deducts if repository is archived by owner. | 10 pts<br>5 pts<br>5 pts |
| **Safety & Secrets** | **15** | • Evaluates file trees via `Trie` and README text via `KmpMatcher`.<br>• **Forbidden Files**: Immediate point deductions for committed `.env`, `id_rsa`, `id_ed25519`, `credentials.json`, `node_modules/`, `.DS_Store`, `target/classes/`.<br>• **Committed Tokens**: Detects AWS keys (`AKIA...`), RSA headers, GitHub tokens. | Starts at 15 pts; loses points per violation (Min: 0) |
| **Licensing & Community** | **10** | • **Open Source License**: Valid `LICENSE` file or recognized SPDX key.<br>• **Contributing Guide**: Presence of `CONTRIBUTING.md`.<br>• **Issue & PR Templates**: `.github/ISSUE_TEMPLATE`, `PULL_REQUEST_TEMPLATE`. | 5 pts<br>2 pts<br>3 pts |
| **Project Structure** | **5** | • **Standard Layout**: Standard directory structure (`src/`, `lib/`, `pkg/`).<br>• **Anti-Bloat Check**: Deducts if repo is a flat dump of 1-2 unstructured files. | 3 pts<br>2 pts |

---

## 6. Remediation Planning & Duplicate Detection Systems

### 6.1 Fix Planner (Weighted Set Cover Optimization)
Rather than presenting users with an overwhelming list of 50+ individual repository problems, RepoLens consolidates remediation into an optimal action plan:
1. **Universe of Defects**: Formulated as pairs $(repo_i, defect_j)$.
2. **Effort Weights**: Realistic engineering effort costs:
   - Add LICENSE (Cost: 1.0)
   - Add `.gitignore` (Cost: 1.0)
   - Add Repository Topics (Cost: 1.0)
   - Add Installation & Usage Sections to README (Cost: 2.0)
   - Configure CI/CD Workflow (Cost: 3.0)
   - Add Automated Test Suite (Cost: 5.0)
3. **Execution**: Solved using `GreedyWeightedSetCover.java`, producing an ordered list of high-ROI actions with estimated point gains.

### 6.2 Duplicate Finder (Disjoint Set Union Clustering)
Redundant and abandoned forks clutter developer profiles. `DuplicateFinder.java` constructs clusters using three heuristic criteria:
- **README Fingerprint**: Identical SHA-256 hashes of decoded README content.
- **Jaccard File-Tree Similarity**: Overlap metric $\frac{|Tree_A \cap Tree_B|}{|Tree_A \cup Tree_B|} \ge 0.70$.
- **Normalized Name Similarity**: Matching alphanumeric stems (e.g., `project-v1` vs. `project-v2`).
Clusters are merged via `DisjointSetUnion<String>` and presented with actionable explanations.

---

## 7. Multi-Tiered Caching & Database Persistence

### 7.1 Two-Tier Cache Architecture

```
Incoming Request (owner, repo, commit_sha)
                 │
                 ▼
     ┌────────────────────────┐
     │   Bloom Filter Check   │  <── In-memory MurmurHash3 Bit-Array (k=7)
     │   O(k) ~ 0.1 µs        │
     └───────────┬────────────┘
                 │
        ┌────────┴────────┐
   "DEFINITE NO"     "MIGHT EXIST" (99% probability)
        │                 │
        ▼                 ▼
┌──────────────┐   ┌───────────────────────────┐
│ Cache Miss   │   │  Persistent H2 Database   │
│ Fetch GitHub │   │  Confirmation Lookup      │
└──────────────┘   └─────────────┬─────────────┘
                                 │
                        ┌────────┴────────┐
                   Confirmed HIT     Confirmed MISS
                        │            (1% false positive)
                        ▼                 │
             ┌─────────────────────┐      ▼
             │ Serve Cached Score  │ ┌──────────────┐
             │ (<2 ms, 0 API calls)│ │ Fetch GitHub │
             └─────────────────────┘ └──────────────┘
```

### 7.2 Database Entities (`com.repolens.web.cache.entity`)
- `CachedRepoScoreEntity`: Keyed by `{owner}/{repo}@{commit_sha}`, stores complete JSON score snapshots.
- `CachedUserAnalysisEntity`: Keyed by `username`, stores aggregated portfolio evaluations with configurable TTL (default: 6 hours).
- `CachedETagEntity`: Keyed by API endpoint URL, stores GitHub `ETag` headers for HTTP 304 Not Modified rate-limit preservation.

### 7.3 Ephemeral Cloud Storage Analysis & PostgreSQL Migration
- **Local Development**: H2 database runs in file mode (`./data/repolens.mv.db`), requiring zero setup.
- **Cloud Containers (Vercel Fluid Compute)**: Containers run as stateless ephemeral instances. Files written to local disk are reclaimed when instances scale down to zero.
- **Production Migration Path**: Connect to **Neon Serverless Postgres** or **Supabase** with zero code changes:
  1. Add runtime dependency `org.postgresql:postgresql` to `web/pom.xml`.
  2. Set Vercel environment variables:
     - `SPRING_DATASOURCE_URL=jdbc:postgresql://<neon-host>/repolens?sslmode=require`
     - `SPRING_DATASOURCE_USERNAME=<username>`
     - `SPRING_DATASOURCE_PASSWORD=<password>`

---

## 8. Model Context Protocol (MCP) STDIO Tool Server

RepoLens provides a native **Model Context Protocol (MCP)** server (`mcp-server/`), allowing AI coding assistants to execute live repository audits over standard input/output.

### 8.1 Technical Specifications
- **Pure Java SDK**: Built with `io.modelcontextprotocol.sdk:mcp` (Zero Spring dependencies).
- **Executable Fat JAR**: Shaded with all dependencies via `maven-shade-plugin` (`repolens-mcp.jar`, 4.7 MB).
- **STDIO Isolation**: All diagnostic logs emit to `stderr`. `stdout` carries strictly JSON-RPC 2.0 messages.
- **Independent Cache**: Utilizes isolated directory (`REPOLENS_CACHE_DIR`, default: `~/.repolens/mcp-cache`) to prevent database locking conflicts with the web server.

### 8.2 The 7 Registered Tools

```mermaid
classDiagram
    class RepoLensMcpServer {
        +analyze_profile(username)
        +score_repo(owner, repository)
        +compare_repos(repositories)
        +fix_plan(username)
        +explain_deduction(owner, repository, category)
        +find_duplicates(username)
        +get_status()
    }
```

1. **`analyze_profile(username)`**: Comprehensive profile evaluation returning portfolio score, category distributions, top repositories, and remediation summary.
2. **`score_repo(owner, repository)`**: Complete repository score (0–100), grade tier, rubric sub-scores, and itemized deductions with fix snippets.
3. **`compare_repos(repositories)`**: Side-by-side comparative analysis of $\ge 2$ repositories, ranked by score descending with comparative matrices.
4. **`fix_plan(username)`**: Greedy Weighted Set Cover remediation plan prioritizing highest point gain per effort.
5. **`explain_deduction(owner, repository, category)`**: Detailed diagnostic explanation for deductions within a specific scoring category.
6. **`find_duplicates(username)`**: Disjoint Set Union clustering report identifying duplicate, cloned, or obsolete repositories.
7. **`get_status()`**: Server health check, GitHub token presence (boolean flag only; token value is never exposed), rate limits, and backend connectivity.

### 8.3 Client Configuration (Cursor & Claude Desktop)
File: `~/.cursor/mcp.json` or `claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "repolens": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/repolens-mcp.jar"
      ],
      "env": {
        "GITHUB_TOKEN": "YOUR_TOKEN_HERE"
      }
    }
  }
}
```

---

## 9. Web Application & Frontend User Experience

The `web` module serves a responsive, modern web dashboard built with **Vanilla HTML5, CSS custom properties, and JavaScript (ES6+)** with zero node/npm build dependencies.

### Key Pages and Endpoints:
- **Interactive Dashboard (`/` -> `index.html`)**: Search any public username, inspect score distributions, trigger interactive fix planners, and view duplicate clusters.
- **Shareable Developer Report Card (`/u/{username}` -> `report.html`)**: Clean, public report card suitable for inclusion on resumes or portfolios.
- **Scoring Rubric Documentation (`/rubric` -> `rubric.html`)**: Interactive reference explaining all 100 rubric points, deductions, and fix recommendations.
- **Clean Evaluator (`/evaluator`, `/app` -> `RepoLensClean.html`)**: Alternate clean interface for focused repository inspection.
- **MCP Server Tab (`/mcp` -> `mcp.html`)**: Setup guides, stdio protocol diagrams, and browser-only configuration snippet generator.
- **Dynamic README Badges (`/badge/{owner}/{repo}.svg`)**: SVG badge endpoint generating shields.io-style score badges for repository READMEs.

---

## 10. Cloud Deployment & Containerization (Vercel)

RepoLens is configured for deployment on **Vercel Fluid Compute** as a single, unified Docker container.

### 10.1 Single-Container Architecture
Because the Spring Boot executable JAR packages `web/src/main/resources/static/` directly into the classpath, embedded Tomcat serves both the static pages and the `/api/*` endpoints from a single domain.

### 10.2 `Dockerfile.vercel` Multi-Stage Build:
```dockerfile
# Multi-stage Docker build for RepoLens Java Spring Boot
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml .
COPY core/pom.xml core/
COPY web/pom.xml web/
COPY mcp-server/pom.xml mcp-server/
COPY core/src core/src
COPY web/src web/src

RUN mvn clean package -DskipTests -pl core,web -am

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN mkdir -p /app/data && chmod 777 /app/data
COPY --from=builder /build/web/target/web-1.0.0-SNAPSHOT.jar /app/app.jar

ENV PORT=8080
EXPOSE 8080 80

ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -Djava.security.egd=file:/dev/./urandom -XX:+UseContainerSupport -jar /app/app.jar"]
```

### 10.3 Dynamic Port Binding
Spring Boot reads the injected environment variable dynamically in `application.yml`:
```yaml
server:
  port: ${PORT:8080}
```

---

## 11. Testing, Empirical Verification & Benchmarks

The project maintains comprehensive test suites spanning unit tests, algorithm proofs, web integration tests, and live benchmarks.

### 11.1 Benchmark 1: Cache & Latency Optimization
Empirical test evaluating a portfolio of 17 repositories visited 10 times (`RepoLensBenchmarkTest.java`):

| Metric | Without Cache | With Bloom Filter + H2 Cache | Absolute Improvement |
| :--- | :---: | :---: | :---: |
| **Total GitHub API Calls** | 680 | 68 | **-90.0% (-612 calls)** |
| **Cache Hits** | 0 | 153 repository lookups | **153 hits** |
| **Average Response Latency** | ~4,200 ms | **1.8 ms** | **>2,300x faster** |
| **Subsequent Visit API Calls** | 68 per visit | **0 per visit** | **100% saved on re-visits** |

### 11.2 Benchmark 2: Greedy Set Cover vs. Brute-Force Optimum
Comparison of Chvátal greedy heuristic against global brute-force exponential optimum:
- Universe Size $|U|$: 12 deficiencies
- Candidate Sets $|S|$: 8 remediations
- Brute-Force Optimal Cost (OPT): 6.2
- Greedy Heuristic Cost: 7.7
- Theoretical Bound Factor ($\ln |U| + 1$): 3.485
- **Empirical Approximation Ratio**: **1.242** (comfortably within the theoretical bound of 3.485)

### 11.3 Test Suite Execution Summary
- `core`: 33 unit and algorithm benchmark tests passed.
- `web`: 37 Spring Boot MVC, ETag, and validation tests passed.
- `mcp-server`: 3 STDIO protocol, JSON-RPC, and mock backend tests passed.
- Reactor Total: **73 tests passed, 0 failures, 0 errors**.

---

## 12. Security, Sanitization & Privacy Standards

1. **Zero Raw Token Exposure**: `GITHUB_TOKEN` is loaded strictly server-side. It is never emitted in API responses, logs, or client-side bundles.
2. **Safe MCP Config Generator**: The in-browser config generator on `/mcp.html` uses placeholders (`YOUR_TOKEN_HERE`). Users are never requested to paste tokens into the web page.
3. **IP Visitor Throttling**: `VisitorThrottleService` limits each visitor IP to evaluating a maximum of 10 distinct usernames per hour, protecting backend quotas from scraping.
4. **XSS Defense**: All dynamic DOM insertions utilize `document.createTextNode` or `element.textContent`, preventing script injection via malicious repository descriptions or commit messages.
5. **Read-Only Scope**: RepoLens requests read-only public endpoints. It never seeks write access, user credentials, or OAuth tokens.

---

## 13. Complete Codebase File Tree & Developer Reference

```
RepoLens/
├── pom.xml                                   # Root Maven reactor POM
├── README.md                                 # Public overview & architecture diagrams
├── README_MCP.md                             # MCP server guide & setup instructions
├── PROJECT_CONTEXT.md                        # Master architectural report (THIS FILE)
├── Dockerfile.vercel                         # Multi-stage container build for Vercel
├── .dockerignore                             # Build context exclusions
├── .env.example                              # Environment template (GITHUB_TOKEN=YOUR_TOKEN_HERE)
├── mcp-config-example.json                   # Client MCP config template
├── repolens-mcp-v0.1.0.zip                   # MCP standalone release package
│
├── core/                                     # ZERO Spring dependencies
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/repolens/
│       │   ├── algorithms/                   # 8 Custom DSA implementations
│       │   │   ├── BinaryHeap.java
│       │   │   ├── BloomFilter.java
│       │   │   ├── CountingSort.java
│       │   │   ├── DisjointSetUnion.java
│       │   │   ├── GreedyWeightedSetCover.java
│       │   │   ├── KmpMatcher.java
│       │   │   ├── Quickselect.java
│       │   │   └── Trie.java
│       │   ├── cache/                        # Cache contracts & metrics
│       │   ├── duplicate/                    # DSU Duplicate Finder
│       │   ├── github/                       # GitHub REST client & models
│       │   ├── planner/                      # Greedy Set Cover Fix Planner
│       │   └── scoring/                      # 100-Point Scoring Engine & Rules
│       └── test/java/com/repolens/core/      # Algorithm and benchmark tests
│
├── web/                                      # Spring Boot 3.3.5 web application
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/repolens/web/
│       │   │   ├── RepoLensApplication.java  # Spring Boot main class
│       │   │   ├── cache/                    # H2 JPA entities & repositories
│       │   │   ├── controller/               # REST API & forwarding controllers
│       │   │   ├── dto/                      # Data Transfer Objects
│       │   │   ├── service/                  # AnalysisService orchestrator
│       │   │   ├── throttle/                 # VisitorThrottleService
│       │   │   └── validation/               # GithubUsernameValidator
│       │   └── resources/
│       │       ├── application.yml           # H2, dynamic PORT, and cache config
│       │       └── static/                   # Plain HTML/CSS/JS frontend
│       │           ├── index.html            # Main dashboard
│       │           ├── mcp.html              # MCP server documentation
│       │           ├── report.html           # Developer report card (/u/{username})
│       │           ├── rubric.html           # 100-point rubric guide
│       │           ├── evaluator.html        # Evaluator interface
│       │           └── repolens-clean/       # Standalone clean evaluator
│       └── test/java/com/repolens/web/       # Web integration tests
│
└── mcp-server/                               # Standalone STDIO MCP Server
    ├── pom.xml                               # Shaded runnable fat JAR build
    └── src/
        ├── main/java/com/repolens/mcp/
        │   ├── McpServerApplication.java     # MCP server entry point & 7 tool registrations
        │   ├── client/
        │   │   └── RepoLensBackendClient.java# HTTP client to web backend
        │   └── tools/
        │       └── RepoLensToolHandlers.java # Handlers for all 7 MCP tools
        └── test/java/com/repolens/mcp/       # STDIO protocol integration tests
```

### Essential Developer Commands
```bash
# Build and run all tests across all three modules:
mvn clean test

# Run algorithm benchmarks specifically:
mvn test -pl core -Dtest=RepoLensBenchmarkTest

# Run web application locally (http://localhost:8080):
mvn spring-boot:run -pl web

# Build executable web JAR:
mvn clean package -DskipTests -pl core,web -am
java -jar web/target/web-1.0.0-SNAPSHOT.jar

# Build standalone MCP server JAR:
mvn clean package -DskipTests -pl core,mcp-server -am
java -jar mcp-server/target/repolens-mcp.jar

# Build and test container locally:
docker build -f Dockerfile.vercel -t repolens .
docker run -p 8080:8080 -e GITHUB_TOKEN="your_token" repolens
```

---

## 14. Conclusion & Future Roadmap

RepoLens demonstrates that modern software engineering applications can achieve high performance, extreme cost efficiency, and rigorous determinism through the thoughtful application of classical algorithms and clean modular design.

### Future Roadmap:
1. **Managed Serverless PostgreSQL Migration**: Add production Neon Postgres integration for cross-instance persistent caching on Vercel.
2. **Automated Pull Request Reviewer**: Package RepoLens as a lightweight GitHub Action to flag hygiene defects in pull request CI checks.
3. **Multi-Platform Support**: Abstract the GitHub client to support GitLab, Bitbucket, and Codeberg repositories.
4. **PDF Portfolio Export**: Endpoints generating download-ready PDF executive audit reports.
