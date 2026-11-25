# RepoLens: Core Algorithms and Data Structures Documentation

RepoLens is built with **zero external algorithmic dependencies** in its `core` module. Every data structure and algorithmic routine used for repository scoring, ranking, duplicate detection, fix planning, and cache management is hand-crafted and tested.

This document details the design, mathematical complexity, and RepoLens integration for each of the **8 fundamental algorithms**.

---

## Algorithm Summary Matrix

| # | Algorithm | Class | Primary Usage in RepoLens | Time Complexity (Avg) | Time Complexity (Worst) | Space Complexity |
|---|---|---|---|---|---|---|
| **1** | **Binary Heap** | `BinaryHeap<T>` | Best-to-worst repository ranking & Top-$K$ priority queuing | $O(\log n)$ push/pop | $O(\log n)$ push/pop | $O(n)$ contiguous |
| **2** | **Trie (Prefix Tree)** | `Trie` | File path safety scanning (`.env`, `node_modules/`, private keys) | $O(L)$ per path lookup | $O(L)$ per path lookup | $O(\Sigma \cdot L \cdot N)$ |
| **3** | **Knuth-Morris-Pratt (KMP)** | `KmpMatcher` | Substring scanning in README text for tokens/keys without backtracking | $O(n + m)$ | $O(n + m)$ | $O(m)$ for $\pi$-table |
| **4** | **Disjoint Set Union (DSU)** | `DisjointSetUnion<T>` | Duplicate repository clustering via README hash & tree similarity | $O(\alpha(n)) \approx O(1)$ | $O(\alpha(n))$ | $O(n)$ |
| **5** | **Greedy Weighted Set Cover** | `GreedyWeightedSetCover<E>` | Optimal hygiene remediation fix planning (Chvátal heuristic) | $O(\|U\| \cdot m)$ | $O(\|U\| \cdot m)$ | $O(\|U\| + m)$ |
| **6** | **Bloom Filter** | `BloomFilter` | Cache pre-check with MurmurHash3 double hashing to avoid DB lookups | $O(k)$ bit ops | $O(k)$ bit ops | $O(m)$ bits (~11.7 KB) |
| **7** | **Quickselect** | `Quickselect` | Median hygiene score & percentile calculation with median-of-three | $O(n)$ linear | $O(n^2)$ (mitigated) | $O(1)$ in-place |
| **8** | **Counting Sort** | `CountingSort` | Sorting repos by hygiene score $[0, 100]$ & generating score bands | $O(n + k) = O(n)$ | $O(n + k) = O(n)$ | $O(n + k)$ auxiliary |

---

## 1. Binary Heap (`BinaryHeap.java`)

### Purpose in RepoLens
Used in the web API and ranking pipeline to order evaluated repositories from highest hygiene score (100) down to lowest (0), and to extract the Top-$K$ repositories requiring urgent hygiene remediation.

### Design & Implementation
- Backed by a dynamic array (`ArrayList<T>`) representing a complete binary tree.
- For node at index $i$:
  - Left child: $2i + 1$
  - Right child: $2i + 2$
  - Parent: $\lfloor (i - 1) / 2 \rfloor$
- Supports both **Min-Heap** and **Max-Heap** invariants through parameterized `Comparator<T>`.
- Implements linear-time **bottom-up heap construction** (`buildHeap`) in $O(n)$ time by running `siftDown` from index $\lfloor n/2 \rfloor - 1$ down to 0:
  $$\sum_{h=0}^{\lfloor \log n \rfloor} \frac{n}{2^{h+1}} O(h) = O\left(n \sum_{h=0}^{\infty} \frac{h}{2^h}\right) = O(n)$$

### Complexity
- **Push (`insert`):** $O(\log n)$ worst-case, $O(1)$ amortized average.
- **Pop (`extractTop`):** $O(\log n)$ worst-case (swaps root with last element and calls `siftDown`).
- **Peek:** $O(1)$ directly inspecting root.
- **Space:** $O(n)$ contiguous memory with zero pointer overhead.

---

## 2. Trie (`Trie.java`)

### Purpose in RepoLens
RepoLens must scan thousands of file paths returned in GitHub's recursive tree API to flag committed security risks (e.g., `.env`, `.env.local`, `id_rsa`, `id_ed25519`, `credentials.json`) and engineering junk files (e.g., `node_modules/`, `.DS_Store`, `target/classes/`, `__pycache__/`).

### Design & Implementation
- Each node stores a child map (`Map<Character, TrieNode>`) and a terminal boolean flag.
- Enables two scanning modes:
  1. **Exact Matching**: Checks if a single path component matches a blacklisted file name.
  2. **Prefix Matching (`hasPrefix` / `isPrefixOfAny`)**: Checks if any directory prefix in a path belongs to a forbidden directory (e.g. `frontend/node_modules/package.json` immediately triggers on `node_modules/`).
- Prevents expensive regex evaluations on large repository trees (often containing 10,000+ files).

### Complexity
- **Insert:** $O(L)$ where $L$ is string length.
- **Search / Prefix Match:** $O(L)$, strictly independent of the total number of patterns $N$ stored in the Trie.
- **Space:** $O(\Sigma \cdot L \cdot N)$ where $\Sigma$ is the alphabet size. Re-uses common prefixes (e.g. `.env`, `.env.prod`, `.env.staging` share the first 4 nodes).

---

## 3. Knuth-Morris-Pratt Algorithm (`KmpMatcher.java`)

### Purpose in RepoLens
The `SafetyScorer` analyzes the raw text of repository `README.md` files to detect accidental disclosures of sensitive tokens, private key headers (e.g. `-----BEGIN RSA PRIVATE KEY-----`), or AWS keys (`AKIA[0-9A-Z]{16}`) without quadratic slowdowns.

### Design & Implementation
- Computes the **Longest Proper Prefix which is also a Suffix (LPS)** array ($\pi$-table) in $O(m)$ preprocessing time.
- When a mismatch occurs at character $j$, instead of rolling back the text pointer $i$ to $i - j + 1$ (which degrades naive string search to $O(n \cdot m)$ on repetitive text), KMP shifts the pattern pointer to $\pi[j - 1]$ and continues without re-examining matched characters.

### Complexity
- **Preprocessing ($\pi$-table):** $O(m)$ time and $O(m)$ auxiliary space.
- **Text Search:** $O(n)$ time where $n$ is the length of the README text.
- **Total Time:** $O(n + m)$, optimal and immune to malicious adversarial text.

---

## 4. Disjoint Set Union (`DisjointSetUnion.java`)

### Purpose in RepoLens
The `DuplicateFinder` groups identical, forked, or heavily cloned repositories into clusters. Repositories are linked if they share:
1. Exact SHA-256 README hash, OR
2. Jaccard file-tree similarity index $\ge 70\%$, OR
3. Normalized repository name similarity.

### Design & Implementation
Implements the two standard optimizations:
1. **Path Compression**: During `find(x)`, recursively points every visited ancestor directly to the cluster representative root.
2. **Union by Rank**: Always attaches the root of the tree with smaller depth under the root of the tree with greater depth, preventing unbalanced tree chains.

### Complexity
- **Amortized Time per Operation:** $O(\alpha(n))$ where $\alpha$ is the inverse Ackermann function. For all practical inputs $n \le 10^{80}$, $\alpha(n) \le 4$, making operations effectively $O(1)$ constant time.
- **Space:** $O(n)$ for parent and rank arrays/maps.

---

## 5. Greedy Weighted Set Cover (`GreedyWeightedSetCover.java`)

### Purpose in RepoLens
The `FixPlanner` aggregates all hygiene deductions across all repositories of a user. It computes the smallest and most cost-effective sequence of developer interventions (e.g. "Add LICENSE", "Add .gitignore", "Configure CI", "Add Tests") that recovers the maximum lost points across the profile.

### Design & Implementation
- Models the problem as a **Weighted Set Cover**:
  - Universe $U$: Set of all $(repo, defect)$ pairs.
  - Candidate Sets $\mathcal{S}$: Actions with developer effort weights $w(S_j) > 0$.
- Uses **Chvátal's Greedy Heuristic**:
  At each step, selects the action minimizing $\frac{\text{cost}}{\text{newly covered elements}}$.

### Complexity & Approximation
- **Time Complexity:** $O(|U| \cdot |\mathcal{S}|)$ polynomial time.
- **Space Complexity:** $O(|U| + |\mathcal{S}|)$.
- **Approximation Ratio:** Guarantees $\text{Cost}_{\text{Greedy}} \le \text{Cost}_{\text{OPT}} \cdot (\ln |U| + 1)$. In empirical benchmarks on repository datasets, it matches the exact exponential brute-force optimum ($\text{ratio} = 1.000$).

---

## 6. Bloom Filter (`BloomFilter.java`)

### Purpose in RepoLens
Protects the GitHub API rate limit (60 unauthenticated / 5,000 authenticated) and speeds up cache evaluation. Every repository analysis checks the Bloom filter first to determine if the repo at its current `commit_sha` has already been scored.

### Design & Implementation
- Implements **Kirsch-Mitzenmacher double hashing**:
  $$g_i(x) = (h_1(x) + i \cdot h_2(x)) \pmod m \quad \text{for } 0 \le i < k$$
- Two independent 64-bit MurmurHash3 hashes simulate $k$ independent hash functions without extra hashing passes.
- **Zero False Negative Invariant**:
  - If `mightContain(key)` is `false`, the repo is **definitely not cached**.
  - If `mightContain(key)` is `true`, the repo **might be cached**. RepoLens **always** confirms with an H2 database lookup before serving cached scores, completely preventing false positive cache corruption.

### Complexity
- **Time Complexity:** $O(k)$ bit operations for both `add()` and `mightContain()`.
- **Space Complexity:** $O(m)$ bits (~11.7 KB for 10,000 repos at 1% error rate).

---

## 7. Quickselect (`Quickselect.java`)

### Purpose in RepoLens
Computes statistical medians and quartile percentiles (25th and 75th percentiles) across all repository scores of a user profile without sorting the entire array.

### Design & Implementation
- Hoare's Selection Algorithm using in-place Lomuto/Hoare partitioning.
- **Median-of-Three Pivot Selection**: Selects the median of the first, middle, and last elements as the pivot. This eliminates the worst-case $O(n^2)$ behavior on pre-sorted or reverse-sorted input arrays.

### Complexity
- **Average Time Complexity:** $O(n)$ linear time. The recurrence relation is $T(n) = T(n/2) + O(n) = O(n)$.
- **Worst-case Time Complexity:** $O(n^2)$ (practically impossible with median-of-three).
- **Space Complexity:** $O(1)$ auxiliary space (operates in-place).

---

## 8. Counting Sort (`CountingSort.java`)

### Purpose in RepoLens
Sorts repositories by their hygiene score and computes score band distributions (`90-100`, `75-89`, `50-74`, `25-49`, `0-24`) for the Chart.js visual dashboard.

### Design & Implementation
- Because RepoLens hygiene scores are deterministically bounded within the integer range $[0, 100]$, the alphabet size $k = 101$.
- Non-comparison distribution sort:
  1. Counts frequencies in an array of size $k = 101$.
  2. Computes prefix cumulative sums in descending order.
  3. Places elements into an output array from right-to-left to maintain **stability**.

### Complexity
- **Time Complexity:** $O(n + k)$. Because $k = 101$ is constant, this is **strictly $O(n)$ linear time**, outperforming Quicksort ($O(n \log n)$).
- **Space Complexity:** $O(n + k)$ auxiliary space.
- **Stability:** Guaranteed stable. Equal scoring repositories preserve their initial ordering.

---

## Viva & Technical Defense Questions

### Q1: Why implement custom data structures instead of standard Java collections?
Standard library collections are general-purpose:
- `java.util.PriorityQueue` does not expose in-place bottom-up $O(n)$ construction (`buildHeap`).
- Java has no built-in `Trie`, `BloomFilter`, or `DisjointSetUnion`.
- `Collections.sort()` uses TimSort ($O(n \log n)$), whereas RepoLens scores are bounded $[0, 100]$, allowing `CountingSort` to run in $O(n)$ linear time.
- Hand-crafting these structures guarantees zero external runtime dependencies in `core`, deterministic performance, and exact alignment with theoretical bounds.

### Q2: Why is Bloom Filter confirmed with a database lookup?
A Bloom filter is a probabilistic data structure. While it has **0% false negatives** (if it says "absent", it is guaranteed to be absent), it has a non-zero **false positive probability** $p \approx 1\%$. If RepoLens trusted a positive Bloom filter result without database confirmation, a false positive would cause the system to skip scanning a repository that had never actually been scored. Confirming against H2 provides the speed of $O(k)$ memory filtering on misses while ensuring 100% data correctness.

### Q3: Why is Weighted Set Cover NP-hard and how good is the greedy approximation?
Minimum Set Cover was proven NP-hard by Richard Karp (1972) via reduction from Vertex Cover. No polynomial-time algorithm can find the exact minimum unless $P = NP$. Vasek Chvátal proved that the greedy heuristic provides an approximation ratio of $H_n = \sum_{i=1}^n \frac{1}{i} \le \ln n + 1$, which is the best possible polynomial-time approximation unless $P = NP$ (Feige, 1998). In practice, as confirmed in `RepoLensBenchmarkTest`, it achieves an empirical ratio of **1.000** (matching the exact brute-force optimum).
