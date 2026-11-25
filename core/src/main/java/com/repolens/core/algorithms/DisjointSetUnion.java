package com.repolens.core.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-crafted Disjoint Set Union (DSU / Union-Find) data structure.
 * <p>
 * Features:
 * <ul>
 *   <li><b>Union by Rank:</b> Keeps the tree shallow by attaching smaller trees under root of larger trees.</li>
 *   <li><b>Path Compression:</b> Flattens the structure during {@code find()} operations so nodes point directly to the representative root.</li>
 * </ul>
 * <p>
 * Used in RepoLens for:
 * <ul>
 *   <li>Clustering duplicate or near-duplicate repositories (based on shared forks, identical descriptions, or matching name roots) into connected components.</li>
 * </ul>
 *
 * <h3>Complexity Analysis (Viva Reference):</h3>
 * <ul>
 *   <li><b>Find with Path Compression:</b> $O(\alpha(N))$ amortized time where $\alpha$ is the Inverse Ackermann function. In practice, $\alpha(N) \le 4$ for all realistic values of $N$, making it effectively $O(1)$ constant time.</li>
 *   <li><b>Union by Rank:</b> $O(\alpha(N))$ amortized time.</li>
 *   <li><b>Space Complexity:</b> $O(N)$ space to maintain parent, rank, and size arrays for $N$ elements.</li>
 * </ul>
 *
 * @param <T> the element type
 */
public class DisjointSetUnion<T> {

    private final Map<T, Integer> itemToId;
    private final List<T> idToItem;
    private final List<Integer> parent;
    private final List<Integer> rank;
    private final List<Integer> componentSize;
    private int componentCount;

    /**
     * Constructs an empty DisjointSetUnion structure.
     */
    public DisjointSetUnion() {
        this.itemToId = new HashMap<>();
        this.idToItem = new ArrayList<>();
        this.parent = new ArrayList<>();
        this.rank = new ArrayList<>();
        this.componentSize = new ArrayList<>();
        this.componentCount = 0;
    }

    /**
     * Adds an element as a new disjoint singleton set if not already present.
     * <p>Time Complexity: $O(1)$</p>
     *
     * @param item the element to add
     * @return the integer id assigned to the element
     */
    public int makeSet(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot add null element to DSU");
        }
        Integer existingId = itemToId.get(item);
        if (existingId != null) {
            return existingId;
        }

        int id = idToItem.size();
        itemToId.put(item, id);
        idToItem.add(item);
        parent.add(id);
        rank.add(0);
        componentSize.add(1);
        componentCount++;
        return id;
    }

    /**
     * Finds the representative element of the set containing the given item.
     * Applies path compression.
     * <p>Time Complexity: $O(\alpha(N))$ amortized</p>
     *
     * @param item the item to find
     * @return the representative element of the set
     */
    public T find(T item) {
        int id = makeSet(item);
        int rootId = findRoot(id);
        return idToItem.get(rootId);
    }

    /**
     * Merges the sets containing items {@code a} and {@code b} using union by rank.
     * <p>Time Complexity: $O(\alpha(N))$ amortized</p>
     *
     * @param a first element
     * @param b second element
     * @return true if sets were merged, false if they were already in the same set
     */
    public boolean union(T a, T b) {
        int rootA = findRoot(makeSet(a));
        int rootB = findRoot(makeSet(b));

        if (rootA == rootB) {
            return false; // Already in the same set
        }

        // Union by rank: attach smaller rank tree under root of higher rank tree
        int rankA = rank.get(rootA);
        int rankB = rank.get(rootB);

        if (rankA < rankB) {
            parent.set(rootA, rootB);
            componentSize.set(rootB, componentSize.get(rootB) + componentSize.get(rootA));
        } else if (rankA > rankB) {
            parent.set(rootB, rootA);
            componentSize.set(rootA, componentSize.get(rootA) + componentSize.get(rootB));
        } else {
            parent.set(rootB, rootA);
            rank.set(rootA, rankA + 1);
            componentSize.set(rootA, componentSize.get(rootA) + componentSize.get(rootB));
        }

        componentCount--;
        return true;
    }

    /**
     * Checks if two items belong to the same connected component.
     * <p>Time Complexity: $O(\alpha(N))$ amortized</p>
     *
     * @param a first item
     * @param b second item
     * @return true if both belong to the same component, false otherwise
     */
    public boolean connected(T a, T b) {
        return findRoot(makeSet(a)) == findRoot(makeSet(b));
    }

    /**
     * Returns the size of the connected component containing the given item.
     *
     * @param item the item
     * @return number of elements in the component
     */
    public int getComponentSize(T item) {
        int rootId = findRoot(makeSet(item));
        return componentSize.get(rootId);
    }

    /**
     * Returns the number of distinct connected components.
     *
     * @return component count
     */
    public int getComponentCount() {
        return componentCount;
    }

    /**
     * Groups all items into their respective connected components.
     *
     * @return a map from representative root element to the list of elements in that component
     */
    public Map<T, List<T>> getComponents() {
        Map<T, List<T>> groups = new HashMap<>();
        for (int i = 0; i < idToItem.size(); i++) {
            T item = idToItem.get(i);
            T rootItem = idToItem.get(findRoot(i));
            groups.computeIfAbsent(rootItem, k -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    /* ---------------- Internal Path Compression ---------------- */

    private int findRoot(int i) {
        int root = i;
        while (root != parent.get(root)) {
            root = parent.get(root);
        }
        // Path compression: make every visited node point directly to the root
        int curr = i;
        while (curr != root) {
            int next = parent.get(curr);
            parent.set(curr, root);
            curr = next;
        }
        return root;
    }
}
