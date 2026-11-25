package com.repolens.core.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Hand-crafted Binary Heap (Min-Heap / Max-Heap / Priority Queue).
 * <p>
 * Implemented using an internal contiguous {@link ArrayList} as the underlying array representation.
 * Used in RepoLens for:
 * <ul>
 *   <li>Ranking repositories from best to worst hygiene score ($O(n \log n)$ total extraction).</li>
 *   <li>Top-K lowest scoring repositories needing urgent remediation in $O(n \log k)$ time.</li>
 * </ul>
 *
 * <h3>Complexity Analysis (Viva Reference):</h3>
 * <ul>
 *   <li><b>Insertion (push):</b> $O(\log n)$ worst-case, $O(1)$ amortized average. Restores heap invariant using {@code siftUp}.</li>
 *   <li><b>Extraction of Top (pop):</b> $O(\log n)$ worst-case. Swaps root with last element and restores heap invariant using {@code siftDown}.</li>
 *   <li><b>Peek:</b> $O(1)$ worst-case. Directly inspects the root element at index 0.</li>
 *   <li><b>Heap Construction (buildHeap):</b> $O(n)$ linear time when bulk-inserting via bottom-up heapify.</li>
 *   <li><b>Space Complexity:</b> $O(n)$ linear space to store $n$ elements in contiguous memory.</li>
 * </ul>
 *
 * @param <T> the type of elements held in this heap
 */
public class BinaryHeap<T> {

    private final ArrayList<T> heap;
    private final Comparator<? super T> comparator;

    /**
     * Constructs an empty heap with natural ordering (min-heap if natural).
     */
    @SuppressWarnings("unchecked")
    public BinaryHeap() {
        this((a, b) -> ((Comparable<? super T>) a).compareTo(b));
    }

    /**
     * Constructs an empty heap using the specified comparator.
     *
     * @param comparator the comparator that defines priority (root element has highest priority / minimum value according to comparator)
     */
    public BinaryHeap(Comparator<? super T> comparator) {
        if (comparator == null) {
            throw new IllegalArgumentException("Comparator cannot be null");
        }
        this.heap = new ArrayList<>();
        this.comparator = comparator;
    }

    /**
     * Builds a heap in $O(n)$ time from an existing collection of elements.
     *
     * @param elements   initial elements
     * @param comparator the priority comparator
     */
    public BinaryHeap(List<T> elements, Comparator<? super T> comparator) {
        this(comparator);
        if (elements != null && !elements.isEmpty()) {
            this.heap.addAll(elements);
            buildHeap();
        }
    }

    /**
     * Inserts a new element into the heap.
     * <p>Time Complexity: $O(\log n)$</p>
     *
     * @param element the element to insert
     */
    public void insert(T element) {
        if (element == null) {
            throw new IllegalArgumentException("Heap does not permit null elements");
        }
        heap.add(element);
        siftUp(heap.size() - 1);
    }

    /**
     * Retrieves and removes the top element (highest priority root).
     * <p>Time Complexity: $O(\log n)$</p>
     *
     * @return the top element
     * @throws NoSuchElementException if the heap is empty
     */
    public T extractTop() {
        if (heap.isEmpty()) {
            throw new NoSuchElementException("Cannot extract from an empty heap");
        }
        T root = heap.get(0);
        T lastElement = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) {
            heap.set(0, lastElement);
            siftDown(0);
        }
        return root;
    }

    /**
     * Retrieves, but does not remove, the top element.
     * <p>Time Complexity: $O(1)$</p>
     *
     * @return the top element
     * @throws NoSuchElementException if the heap is empty
     */
    public T peek() {
        if (heap.isEmpty()) {
            throw new NoSuchElementException("Cannot peek into an empty heap");
        }
        return heap.get(0);
    }

    /**
     * Returns the number of elements in the heap.
     * <p>Time Complexity: $O(1)$</p>
     *
     * @return heap size
     */
    public int size() {
        return heap.size();
    }

    /**
     * Checks if the heap is empty.
     * <p>Time Complexity: $O(1)$</p>
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    /**
     * Clears all elements from the heap.
     */
    public void clear() {
        heap.clear();
    }

    /**
     * Returns all elements sorted in priority order by extracting them.
     * <p>Time Complexity: $O(n \log n)$</p>
     *
     * @return list of elements in ascending priority order
     */
    public List<T> toSortedList() {
        BinaryHeap<T> copy = new BinaryHeap<>(this.heap, this.comparator);
        List<T> result = new ArrayList<>(copy.size());
        while (!copy.isEmpty()) {
            result.add(copy.extractTop());
        }
        return result;
    }

    /**
     * Computes the top-K elements from a given stream/list without sorting all $N$ elements.
     * Keeps a bounded heap of size at most $k$.
     * <p>Time Complexity: $O(N \log k)$</p>
     * <p>Space Complexity: $O(k)$</p>
     *
     * @param elements input elements
     * @param k        number of elements to retrieve
     * @param rankComp comparator determining which elements should be in top-k
     * @param <E>      element type
     * @return list containing up to $k$ top elements
     */
    public static <E> List<E> topK(List<E> elements, int k, Comparator<? super E> rankComp) {
        if (k <= 0 || elements == null || elements.isEmpty()) {
            return Collections.emptyList();
        }
        // Min-heap of size K using rankComp so that the smallest among the top-k is at the root
        BinaryHeap<E> boundedHeap = new BinaryHeap<>(rankComp);
        for (E item : elements) {
            if (boundedHeap.size() < k) {
                boundedHeap.insert(item);
            } else if (rankComp.compare(item, boundedHeap.peek()) > 0) {
                boundedHeap.extractTop();
                boundedHeap.insert(item);
            }
        }
        List<E> result = boundedHeap.toSortedList();
        Collections.reverse(result); // Highest rank first
        return result;
    }

    /* ---------------- Internal Heap Maintenance ---------------- */

    private void buildHeap() {
        // Start from last internal parent node down to root
        for (int i = (heap.size() / 2) - 1; i >= 0; i--) {
            siftDown(i);
        }
    }

    private void siftUp(int index) {
        int current = index;
        T currentItem = heap.get(current);

        while (current > 0) {
            int parent = (current - 1) / 2;
            T parentItem = heap.get(parent);

            if (comparator.compare(currentItem, parentItem) < 0) {
                heap.set(current, parentItem);
                current = parent;
            } else {
                break;
            }
        }
        heap.set(current, currentItem);
    }

    private void siftDown(int index) {
        int current = index;
        int half = heap.size() / 2;
        T currentItem = heap.get(current);

        while (current < half) {
            int leftChild = 2 * current + 1;
            int rightChild = leftChild + 1;
            int bestChild = leftChild;
            T bestChildItem = heap.get(leftChild);

            if (rightChild < heap.size() && comparator.compare(heap.get(rightChild), bestChildItem) < 0) {
                bestChild = rightChild;
                bestChildItem = heap.get(rightChild);
            }

            if (comparator.compare(bestChildItem, currentItem) < 0) {
                heap.set(current, bestChildItem);
                current = bestChild;
            } else {
                break;
            }
        }
        heap.set(current, currentItem);
    }
}
