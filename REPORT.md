# RepoLens: Deterministic GitHub Repository Quality & Hygiene Dashboard
# Formal Engineering & Architectural Project Report

---

**Project Title:** RepoLens — Deterministic GitHub Repository Quality & Hygiene Dashboard  
**Academic / Industry Project Report**  
**Core Technologies:** Java 17 (LTS), Spring Boot 3.3.5, Model Context Protocol (MCP), Docker, Vercel Fluid Compute  
**Target Repository:** [https://github.com/rohankapoor0/RepoLens-Ver1.git](https://github.com/rohankapoor0/RepoLens-Ver1.git)  
**Branch:** `main`  
**Document Classification:** Software Engineering Project Report & System Specification  

---

## Abstract

Evaluating developer portfolios on GitHub is a cornerstone of modern software recruitment, academic grading, and open-source contribution audits. However, existing evaluation methods rely on either subjective human reviews, rigid single-file linters, or non-deterministic Large Language Model (LLM) evaluations prone to hallucinations. 

**RepoLens** is an open-source, deterministic static analysis system engineered to audit public GitHub repositories and entire user profiles against standardized engineering hygiene, documentation completeness, and maintenance rubrics. The system is designed with a strict zero-dependency philosophy in its core algorithmic engine, implementing **8 custom, hand-crafted data structures and algorithms** in standard Java 17 with zero third-party algorithmic libraries. To overcome GitHub REST API rate limits (60 unauthenticated or 5,000 authenticated requests/hour), RepoLens implements a **two-tier caching architecture** pairing an in-memory Kirsch-Mitzenmacher MurmurHash3 Bloom Filter with a persistent H2 relational store, achieving a **90.0% reduction in API calls** and accelerating response times from ~4,200 ms to **1.8 ms**.

Furthermore, RepoLens features a **Model Context Protocol (MCP)** STDIO server allowing AI developer environments (Cursor, Claude Desktop, Claude Code, Antigravity) to invoke live audits over JSON-RPC 2.0, and deploys as a unified container on **Vercel Fluid Compute** via a root `Dockerfile.vercel`. This report presents the motivation, theoretical foundations, architectural design, empirical benchmark evaluations, and deployment topology of RepoLens.

---

## Table of Contents
1. [Introduction & Problem Statement](#1-introduction--problem-statement)
2. [Prior Art & Comparative Analysis](#2-prior-art--comparative-analysis)
3. [System Architecture & Multi-Module Reactor](#3-system-architecture--multi-module-reactor)
4. [Algorithmic Foundations (8 Custom DSA Implementations)](#4-algorithmic-foundations-8-custom-dsa-implementations)
5. [The Deterministic 100-Point Scoring Engine](#5-the-deterministic-100-point-scoring-engine)
6. [Automated Remediation Planning & Duplicate Detection](#6-automated-remediation-planning--duplicate-detection)
7. [Multi-Tiered Caching & Storage Persistence](#7-multi-tiered-caching--storage-persistence)
8. [Model Context Protocol (MCP) Tool Server](#8-model-context-protocol-mcp-tool-server)
9. [Web Application & Frontend User Experience](#9-web-application--frontend-user-experience)
10. [Cloud Deployment & Containerization](#10-cloud-deployment--containerization)
11. [Empirical Benchmarks & Experimental Results](#11-empirical-benchmarks--experimental-results)
12. [Security, Privacy & Ethical Considerations](#12-security-privacy--ethical-considerations)
13. [Complete Codebase File Tree & Developer Reference](#13-complete-codebase-file-tree--developer-reference)
14. [Conclusion & Future Roadmap](#14-conclusion--future-roadmap)

---

## 1. Introduction & Problem Statement

### 1.1 Context & Motivation
Modern developer hiring and academic assessment increasingly center on public GitHub profiles. A student or candidate's repositories serve as tangible evidence of software craftsmanship. However, inspecting a candidate with 15 to 30 repositories presents substantial challenges:
- **Time Consumption**: Manually reviewing READMEs, license files, build configurations, test suites, and commit logs across multiple repositories takes 20–30 minutes per candidate.
- **Inconsistent Standards**: Different reviewers apply divergent rubrics. One reviewer might forgive missing documentation if code looks sophisticated, while another might strictly penalize missing license headers or committed `.DS_Store` junk files.
- **API Quota Bottlenecks**: Automated portfolio scrapers frequently exhaust the GitHub REST API's rate limit (60 requests/hour unauthenticated, 5,000 authenticated). Fetching file trees, commit timelines, and READMEs for 20 repos requires up to 80 network requests.

### 1.2 Core Project Philosophy
RepoLens was developed to resolve these bottlenecks through the following core tenets:
1. **Strict Determinism**: Zero stochastic or generative LLM scoring in the evaluation loop. Every score is derived through mathematical rubrics with itemized point deductions and actionable remediation steps.
2. **Zero Algorithmic External Dependencies**: Core data structures (heaps, tries, pattern matchers, set cover solvers, bloom filters) are built entirely from scratch in pure Java 17 to guarantee deep educational and architectural rigor.
3. **Zero-Login & Privacy First**: RepoLens requires no user OAuth permissions, personal credentials, or write access. It audits public repositories using its own backend token exclusively for rate quota expansion.
4. **Multi-Interface Portability**: Whether a user prefers an interactive browser dashboard, a shareable resume report card, or seamless CLI/IDE integration via the Model Context Protocol (MCP), RepoLens provides identical deterministic evaluations.

---

## 2. Prior Art & Comparative Analysis

| Feature / Metric | Manual Human Review | Generic Linter (e.g. ESLint/Sonar) | Pure LLM Prompting | **RepoLens Engine** |
| :--- | :---: | :---: | :---: | :---: |
| **Speed per Profile** | 20–30 minutes | 5–10 minutes (requires clone) | 10–30 seconds | **<2 seconds** (cached: **1.8 ms**) |
| **Local Clone Required?** | Yes | Yes | No | **No (REST API Tree Inspection)** |
| **Deterministic Consistency** | Low | High (per-file) | Very Low (Hallucinations) | **100% Deterministic** |
| **Portfolio-Wide Set Cover Fix Plan** | None | None | Heuristic Advice | **Optimal Weighted Set Cover** |
| **Duplicate Repository Detection** | Visual / Guesswork | None | Poor | **Disjoint Set Union (DSU)** |
| **AI Editor Integration (MCP)** | None | None | Proprietary Chat | **Native STDIO MCP Server (7 Tools)** |

---

## 3. System Architecture & Multi-Module Design

RepoLens is architected as a high-cohesion, decoupled **Maven Multi-Module Reactor** partitioned into three discrete submodules:

```
RepoLens (Root Parent POM - com.repolens:repolens:1.0.0-SNAPSHOT)
├── core/         (Pure Java 17 Library — ZERO Framework Dependencies)
├── web/          (Spring Boot 3.3.5 Web Application & Embedded Tomcat)
└── mcp-server/   (Standalone Shaded Fat JAR — Model Context Protocol Server)
```

```mermaid
graph TD
    subgraph "Parent Module (Root POM)"
        POM["Dependency Management, Compiler Config (Java 17), Surefire Setup"]
    end

    subgraph "Core Module (com.repolens:core)"
        ALG["algorithms/ (8 Custom DSA Implementations)"]
        SCORE["scoring/ (100-Point Scoring Engine & 6 Modular Rules)"]
        PLAN["planner/ (FixPlanner - Greedy Weighted Set Cover)"]
        DUP["duplicate/ (DuplicateFinder - Disjoint Set Union)"]
        GH["github/ (GithubClient, ETag Cache Contracts, Domain Models)"]
    end

    subgraph "Web Module (com.repolens:web)"
        APP["RepoLensApplication (Spring Boot 3.3.5 Entrypoint)"]
        CTRL["controller/ (AnalysisController, RepoDetailController, ReportCardController)"]
        CACHE["cache/ (Spring Data JPA, CachedRepoScoreEntity, H2 Database)"]
        STATIC["resources/static/ (index.html, report.html, rubric.html, mcp.html)"]
    end

    subgraph "MCP Module (com.repolens:mcp-server)"
        BOOT["McpServerApplication (Java MCP SDK Runtime)"]
        TOOLS["tools/ (7 Registered MCP Tool Handlers)"]
        CLIENT["client/ (RepoLensBackendClient HTTP Transport)"]
    end

    POM --> core
    POM --> web
    POM --> mcp-server

    core --> web
    core --> mcp-server
    web -.->|"Local HTTP REST (:8080)"| mcp-server
```

### Architectural Separation of Concerns:
- **`core`**: Contains zero framework annotations (no `@Component`, no `@Entity`, no Spring Web). It implements pure domain logic, custom data structures, and mathematical algorithms.
- **`web`**: Encapsulates Spring Boot 3.3.5, embedded Apache Tomcat, Spring Data JPA, H2 relational caching, and static frontend assets.
- **`mcp-server`**: Packaged as a standalone runnable Fat JAR (`repolens-mcp.jar`) via `maven-shade-plugin`. It communicates with `core` directly and queries `web` over HTTP, maintaining strict protocol purity on standard I/O.

---

## 4. Algorithmic Foundations (8 Custom DSA Implementations)

The defining hallmark of RepoLens is that **no third-party algorithmic libraries** are used. All eight fundamental algorithms and data structures are hand-crafted within `com.repolens.core.algorithms`.

### 4.1 Summary of Custom Algorithms

| # | Algorithm | Class | Average Complexity | Worst-Case Complexity | Auxiliary Space | Architectural Function |
|---|---|---|---|---|---|---|
| **1** | **Binary Heap** | `BinaryHeap<T>` | $O(\log n)$ | $O(\log n)$ | $O(n)$ array | Best-to-worst ranking & Top-$K$ priority extraction. |
| **2** | **Trie (Prefix Tree)** | `Trie` | $O(L)$ | $O(L)$ | $O(\Sigma \cdot L \cdot N)$ | Scans 10,000+ paths for forbidden secrets and junk. |
| **3** | **Knuth-Morris-Pratt** | `KmpMatcher` | $O(n + m)$ | $O(n + m)$ | $O(m)$ table | Scans README text for sensitive tokens without backtracking. |
| **4** | **Disjoint Set Union** | `DisjointSetUnion<T>` | $O(\alpha(n))$ | $O(\alpha(n))$ | $O(n)$ array | Clusters duplicate repositories via Jaccard overlap & SHA-256. |
| **5** | **Greedy Set Cover** | `GreedyWeightedSetCover<E>` | $O(\|U\| \cdot m)$ | $O(\|U\| \cdot m)$ | $O(\|U\| + m)$ | Minimal-cost hygiene remediation plan with Chvátal bound. |
| **6** | **Bloom Filter** | `BloomFilter` | $O(k)$ | $O(k)$ | $O(m)$ bits (~11.7 KB) | In-memory cache pre-check with zero false negatives. |
| **7** | **Quickselect** | `Quickselect` | $O(n)$ | $O(n^2)$ (mitigated) | $O(1)$ in-place | Linear-time median hygiene score & percentile calculation. |
| **8** | **Counting Sort** | `CountingSort` | $O(n + k)$ | $O(n + k)$ | $O(n + k)$ | Integer distribution sorting for discrete score band histograms. |

---

### 4.2 Mathematical Design & Implementation Details

#### 1. Binary Heap (`BinaryHeap.java`)
- **Structure**: Complete binary tree mapped into a 0-indexed dynamic array `ArrayList<T>`. For element at index $i$, children are at $2i + 1$ and $2i + 2$, with parent at $\lfloor (i - 1)/2 \rfloor$.
- **Linear-Time Build**: Bottom-up construction (`buildHeap`) runs in $O(n)$ time by performing `siftDown` from index $\lfloor n/2 \rfloor - 1$ down to 0:
  $$\sum_{h=0}^{\lfloor \log n \rfloor} \frac{n}{2^{h+1}} O(h) = O\left(n \sum_{h=0}^{\infty} \frac{h}{2^h}\right) = O(n)$$
- **Application**: Ranks evaluated repositories from highest to lowest score in $O(n \log n)$ total time and extracts the Top-$K$ priority deficiencies in $O(n \log K)$.

#### 2. Trie (Prefix Tree - `Trie.java`)
- **Structure**: Multi-way tree where each node maintains a child map and a terminal boolean flag.
- **Application**: The `SafetyScorer` evaluates thousands of recursive file paths against blacklisted patterns (`.env`, `id_rsa`, `id_ed25519`, `credentials.json`) and directories (`node_modules/`, `.DS_Store`, `target/classes/`, `__pycache__/`). Matching completes in $O(L)$ time where $L$ is path string length, completely independent of the dictionary size.

#### 3. Knuth-Morris-Pratt Algorithm (`KmpMatcher.java`)
- **Structure**: Exact substring pattern matcher that precomputes the Longest Proper Prefix which is also a Suffix (LPS array, or $\pi$-table) in $O(m)$ time and space.
- **Application**: Scans raw README markdown strings for accidental secret leaks (e.g. `-----BEGIN RSA PRIVATE KEY-----`, `AKIA[0-9A-Z]{16}`) in strictly linear $O(n + m)$ time without backtracking, rendering it immune to algorithmic complexity attacks on repetitive text.

#### 4. Disjoint Set Union (`DisjointSetUnion.java`)
- **Structure**: Disjoint set forest combining **Path Compression** and **Union by Rank**:
  ```java
  public T find(T item) {
      T parent = parentMap.get(item);
      if (!parent.equals(item)) {
          parentMap.put(item, find(parent)); // Path compression
      }
      return parentMap.get(item);
  }
  ```
- **Application**: Merges cloned, forked, or duplicate repositories into connected equivalence classes. The amortized time complexity per operation is $O(\alpha(n))$ where $\alpha$ is the inverse Ackermann function ($\alpha(n) \le 4$ for all practical inputs), effectively delivering $O(1)$ constant-time clustering.

#### 5. Greedy Weighted Set Cover (`GreedyWeightedSetCover.java`)
- **Mathematical Formulation**: Let $U$ be the universe of all $(repo, defect)$ deficiencies discovered across a user's repositories. Let $\mathcal{S}$ be the candidate set of engineering remediations, where each fix $S_j \in \mathcal{S}$ covers a subset of defects with associated effort weight $w(S_j) > 0$.
- **Chvátal's Greedy Heuristic (1979)**: At each step, select candidate fix $S^*$ that minimizes the cost-per-uncovered-defect ratio:
  $$\alpha_j = \frac{w(S_j)}{|S_j \setminus C_{\text{already\_covered}}|}$$
- **Approximation Bound**: While Minimum Weighted Set Cover is an NP-complete problem, Chvátal proved that the greedy heuristic guarantees an approximation ratio bounded by the harmonic number $H(|U|)$:
  $$\frac{\text{Cost}(\text{Greedy})}{\text{Cost}(\text{OPT})} \le H(|U|) = \sum_{i=1}^{|U|} \frac{1}{i} \le \ln |U| + 1$$
- **Application**: Computes prioritized action items in the "Fix Planner" (e.g. *"Adding a LICENSE and .gitignore fixes 9 of your 12 repositories for minimal effort"*).

#### 6. Bloom Filter (`BloomFilter.java`)
- **Structure**: Bit-array of size $m$ with $k$ independent hash functions using Kirsch-Mitzenmacher MurmurHash3 double hashing:
  $$g_i(x) = h_1(x) + i \cdot h_2(x) \pmod m, \quad 0 \le i < k$$
- **Mathematical Parameters**: Sized for $n = 10,000$ expected items with false positive probability $p = 0.01$ (1%):
  $$m = -\frac{n \ln p}{(\ln 2)^2} \approx 95,851 \text{ bits} \ (\approx 11.7 \text{ KB RAM}), \quad k = \frac{m}{n} \ln 2 \approx 7 \text{ hashes}$$
- **Invariant**: Guarantees **zero false negatives**. If `mightContain(key) == false`, the repository is definitively uncached, bypassing disk lookups entirely.

#### 7. Quickselect (`Quickselect.java`)
- **Algorithm**: Hoare's selection algorithm utilizing **median-of-three pivot selection** (first, middle, last element) to eliminate $O(n^2)$ worst-case degradation on sorted or semi-sorted arrays.
- **Application**: Calculates median hygiene scores and 25th/75th percentiles across a user's public portfolio in average $O(n)$ time with $O(1)$ auxiliary space.

#### 8. Counting Sort (`CountingSort.java`)
- **Algorithm**: Non-comparison distribution sort for integer keys spanning range $[0, 100]$ using an auxiliary frequency array of size $k = 101$.
- **Application**: Sorts repository scores and compiles discrete compliance bands in strict $O(n + k) = O(n)$ linear time.

---

## 5. The Deterministic 100-Point Scoring Engine

RepoLens scores every repository on an absolute scale from 0 to 100 points divided across **six modular scoring disciplines**:

```mermaid
pie title RepoLens 100-Point Quality Weighting
    "Documentation (25 pts)" : 25
    "Engineering Hygiene (25 pts)" : 25
    "Maintenance Cadence (20 pts)" : 20
    "Safety & Secret Prevention (15 pts)" : 15
    "Licensing & Community (10 pts)" : 10
    "Project Structure (5 pts)" : 5
```

### Detailed Rubric Breakdown

| Category | Points | Specific Rules & Evidence Requirements |
| :--- | :---: | :--- |
| **1. Documentation** | **25** | • **README Existence (8 pts)**: Presence of root or docs README.<br>• **README Word Count (4 pts)**: >200 words: 4 pts, >50 words: 2 pts, $\le 50$ words: 0 pts.<br>• **Installation & Usage (4 pts)**: 2 pts for install heading, 2 pts for usage heading.<br>• **Visual Artifacts (3 pts)**: Markdown `![]()` or HTML `<img>` screenshots.<br>• **Metadata Completeness (6 pts)**: Non-empty description (3 pts) & repository topics (3 pts). |
| **2. Engineering Hygiene** | **25** | • **Automated Tests (8 pts)**: Test files (`test/`, `src/test/`, `*.test.js`, `*_test.go`).<br>• **CI/CD Automation (7 pts)**: Workflows in `.github/workflows/` or `.gitlab-ci.yml`.<br>• **Git Ignore (5 pts)**: Presence of `.gitignore` file.<br>• **Build Configuration (5 pts)**: Manifest (`pom.xml`, `package.json`, `Cargo.toml`, etc.). |
| **3. Maintenance** | **20** | • **Commit Recency (10 pts)**: Days since last push (<30d: 10, <90d: 7, <180d: 4, <365d: 2).<br>• **Commit Cadence (5 pts)**: Frequency in last 30 commits window ($\ge 10$: 5, $\ge 5$: 3).<br>• **Active Status (5 pts)**: Deducts all 5 points if repository is archived. |
| **4. Safety & Security** | **15** | • Evaluates file trees via `Trie` and README strings via `KmpMatcher`.<br>• **Forbidden Files**: Immediate point deductions for committed `.env`, `id_rsa`, `id_ed25519`, `credentials.json`, `node_modules/`, `.DS_Store`, `target/classes/`.<br>• **Committed Secrets**: Detects AWS keys (`AKIA...`), RSA headers, GitHub tokens. Starts at 15 pts; drops per violation to minimum 0. |
| **5. Licensing & Community** | **10** | • **Open Source License (5 pts)**: Valid `LICENSE` file or recognized SPDX key.<br>• **Contributing Guide (2 pts)**: Presence of `CONTRIBUTING.md`.<br>• **Issue & PR Templates (3 pts)**: `.github/ISSUE_TEMPLATE`, `PULL_REQUEST_TEMPLATE`. |
| **6. Project Structure** | **5** | • **Standard Directory Layout (3 pts)**: Presence of standard layout (`src/`, `lib/`, `pkg/`).<br>• **Anti-Bloat Check (2 pts)**: Deducts if repo is a flat dump of 1-2 unstructured files. |

---

## 6. Automated Remediation Planning & Duplicate Detection

### 6.1 Fix Planner (Weighted Set Cover Optimization)
Developers often experience alert fatigue when presented with hundreds of individual repository warnings. The `FixPlanner` formulates remediation as a mathematical Set Cover problem:
1. **Deficiency Universe ($U$)**: Aggregates all tuples $(repo_i, defect_j)$ across a developer's public repositories.
2. **Effort Weights ($\mathcal{S}$)**: Assigns engineering effort costs:
   - Add LICENSE (Cost: 1.0)
   - Add `.gitignore` (Cost: 1.0)
   - Add Repository Topics (Cost: 1.0)
   - Add Installation & Usage Sections to README (Cost: 2.0)
   - Configure CI/CD Workflow (Cost: 3.0)
   - Add Automated Test Suite (Cost: 5.0)
3. **Execution**: Solved with `GreedyWeightedSetCover.java`, producing a prioritized list of actionable remediations maximizing projected score gain per unit of engineering effort.

### 6.2 Duplicate Finder (Disjoint Set Union Clustering)
Redundant repositories, unmaintained experiments, and duplicate clones clutter developer profiles. `DuplicateFinder.java` constructs clusters using three heuristic criteria:
- **README Fingerprint**: Exact SHA-256 hash match on decoded README content.
- **Jaccard File-Tree Overlap**: Path similarity metric $\frac{|Tree_A \cap Tree_B|}{|Tree_A \cup Tree_B|} \ge 0.70$.
- **Normalized Name Similarity**: Matching alphanumeric stems (e.g., `project-v1` vs. `project-v2`).
Equivalence classes are merged via `DisjointSetUnion<String>` and surfaced with clear human-readable explanations.

---

## 7. Multi-Tiered Caching & Storage Persistence

### 7.1 Two-Tier Cache Topology

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

### 7.2 Database Entities & Caching Strategy
- `CachedRepoScoreEntity`: Keyed by `{owner}/{repo}@{commit_sha}`, stores full JSON score snapshots and itemized deductions.
- `CachedUserAnalysisEntity`: Keyed by `username`, stores aggregated portfolio evaluations with configurable TTL (default: 6 hours).
- `CachedETagEntity`: Keyed by API endpoint URL, stores GitHub `ETag` headers for HTTP 304 Not Modified rate-limit preservation.

### 7.3 Ephemeral Cloud Storage & Production Migration Path
- **Local Development**: H2 database runs in embedded file mode (`./data/repolens.mv.db`), requiring zero setup.
- **Cloud Containers (Vercel Fluid Compute)**: Containers run as stateless, auto-scaling ephemeral functions. Local disk state is discarded upon scale-down.
- **Production PostgreSQL Migration**: Connect to **Neon Serverless Postgres** or **Supabase** with zero code changes:
  1. Add runtime dependency `org.postgresql:postgresql` to `web/pom.xml`.
  2. Set Vercel environment variables:
     - `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>/repolens?sslmode=require`
     - `SPRING_DATASOURCE_USERNAME=<username>`
     - `SPRING_DATASOURCE_PASSWORD=<password>`

---

## 8. Model Context Protocol (MCP) Tool Server

RepoLens provides a native **Model Context Protocol (MCP)** server (`mcp-server/`), allowing AI coding assistants to execute live repository audits over standard input/output.

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / AI Agent (Cursor / Claude)
    participant MCP as RepoLens MCP Server (repolens-mcp.jar)
    participant Web as RepoLens Web Backend (:8080)
    participant GH as GitHub REST API v3

    Dev->>MCP: JSON-RPC 2.0 (tools/call: score_repo)
    MCP->>Web: GET /api/repo/{owner}/{repository}
    alt Cached in Bloom Filter + H2
        Web-->>MCP: 200 OK (Cached RepoScore JSON)
    else Cold Cache
        Web->>GH: GET /repos/{owner}/{repo} + tree + readme
        GH-->>Web: GitHub API Data
        Web-->>MCP: 200 OK (Calculated RepoScore JSON)
    end
    MCP-->>Dev: JSON-RPC 2.0 result (Structured Score & Deductions)
```

### 8.1 Technical Specifications
- **Pure Java SDK**: Built with `io.modelcontextprotocol.sdk:mcp` (Zero Spring dependencies).
- **Executable Fat JAR**: Shaded with all dependencies via `maven-shade-plugin` (`repolens-mcp.jar`, 4.7 MB).
- **STDIO Isolation**: All diagnostic logs emit to `stderr`. `stdout` carries strictly JSON-RPC 2.0 messages.
- **Independent Cache**: Utilizes isolated directory (`REPOLENS_CACHE_DIR`, default: `~/.repolens/mcp-cache`) to prevent database locking conflicts with the web server.

### 8.2 The 7 Registered Tools

| Tool Name | Parameters | Target Backend Operation | Underlying Algorithm / Engine |
| :--- | :--- | :--- | :--- |
| `1. analyze_profile` | `username` (string) | `GET /api/analyze/{username}` | Portfolio scoring, Top Repos, Category breakdowns |
| `2. score_repo` | `owner` (string), `repository` (string) | `GET /api/repo/{owner}/{repository}` | Hygiene rubric, deductions, actionable remediation steps |
| `3. compare_repos` | `repositories` (array of strings) | `GET /api/repo/{owner}/{repo}` (fan-out) | Multi-repo side-by-side comparison, rank & matrix |
| `4. fix_plan` | `username` (string) | `GET /api/analyze/{username}` | **Greedy Weighted Set Cover** (Chvátal approximation) |
| `5. explain_deduction` | `owner` (string), `repository` (string), `category` (string, optional) | `GET /api/repo/{owner}/{repository}` | Rule deductions, category breakdown, remediation advice |
| `6. find_duplicates` | `username` (string) | `GET /api/analyze/{username}` | **Disjoint Set Union (DSU)** with path compression |
| `7. get_status` | *(none)* | `GET /api/status` & environment | Token detection (boolean), rate limits, isolated cache path |

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

## 10. Cloud Deployment & Containerization

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

## 11. Empirical Benchmarks & Experimental Results

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

## 12. Security, Privacy & Ethical Considerations

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
├── PROJECT_CONTEXT.md                        # Master architectural report for LLMs
├── REPORT.md                                 # Formal Engineering Project Report (THIS FILE)
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
