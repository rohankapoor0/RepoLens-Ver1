package com.repolens.core.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-crafted Trie (Prefix Tree) for efficient string pattern matching.
 * <p>
 * Used in RepoLens for safety scanning of file paths from GitHub repo file trees.
 * Enables instant detection of:
 * <ul>
 *   <li>Exposed sensitive/secret files (e.g., {@code .env}, {@code id_rsa}, {@code .pem}, {@code credentials.json}).</li>
 *   <li>Junk/bloat files committed by accident (e.g., {@code node_modules/}, {@code .DS_Store}, {@code target/}, {@code dist/}).</li>
 * </ul>
 *
 * <h3>Complexity Analysis (Viva Reference):</h3>
 * <ul>
 *   <li><b>Insertion:</b> $O(L)$ where $L$ is the length of the string pattern. Independent of total patterns stored.</li>
 *   <li><b>Search (Exact):</b> $O(L)$ where $L$ is the length of the query string.</li>
 *   <li><b>Prefix Check:</b> $O(L)$ where $L$ is the length of the prefix query.</li>
 *   <li><b>Path Matching:</b> $O(L)$ to check if any known risk pattern forms a prefix of, or matches, a file path component.</li>
 *   <li><b>Space Complexity:</b> $O(\Sigma \cdot L \cdot N)$ where $N$ is number of patterns, $L$ is average length, and $\Sigma$ is the alphabet size. Re-uses common prefixes to save space.</li>
 * </ul>
 */
public class Trie {

    public static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean isEndOfWord = false;
        private String payload = null; // Description / risk category associated with the pattern

        public Map<Character, TrieNode> getChildren() {
            return children;
        }

        public boolean isEndOfWord() {
            return isEndOfWord;
        }

        public String getPayload() {
            return payload;
        }
    }

    private final TrieNode root;
    private int wordCount;

    /**
     * Constructs an empty Trie.
     */
    public Trie() {
        this.root = new TrieNode();
        this.wordCount = 0;
    }

    /**
     * Inserts a pattern into the Trie.
     * <p>Time Complexity: $O(L)$</p>
     *
     * @param word the string pattern to insert
     */
    public void insert(String word) {
        insert(word, null);
    }

    /**
     * Inserts a pattern along with an associated payload (e.g. risk severity / category).
     * <p>Time Complexity: $O(L)$</p>
     *
     * @param word    the string pattern
     * @param payload metadata associated with this pattern
     */
    public void insert(String word, String payload) {
        if (word == null || word.isEmpty()) {
            return;
        }
        TrieNode current = root;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            current = current.children.computeIfAbsent(ch, c -> new TrieNode());
        }
        if (!current.isEndOfWord) {
            wordCount++;
        }
        current.isEndOfWord = true;
        current.payload = payload;
    }

    /**
     * Performs an exact search for a pattern in the Trie.
     * <p>Time Complexity: $O(L)$</p>
     *
     * @param word string to search
     * @return true if the exact pattern exists, false otherwise
     */
    public boolean search(String word) {
        TrieNode node = findNode(word);
        return node != null && node.isEndOfWord;
    }

    /**
     * Checks if there is any pattern in the Trie that starts with the given prefix.
     * <p>Time Complexity: $O(L)$</p>
     *
     * @param prefix the prefix to query
     * @return true if at least one stored pattern starts with prefix
     */
    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }

    /**
     * Checks if the given text has any Trie word as its prefix.
     * <p>Example: if ".env" is in the Trie, {@code hasPrefixOf(".env.production")} returns true.</p>
     * <p>Time Complexity: $O(L)$ where $L$ is text length.</p>
     *
     * @param text string to inspect
     * @return true if text starts with any pattern present in the Trie
     */
    public boolean hasPrefixOf(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        TrieNode current = root;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            current = current.children.get(ch);
            if (current == null) {
                return false;
            }
            if (current.isEndOfWord) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the matching payload for the shortest prefix of the given text present in the Trie.
     *
     * @param text string to inspect
     * @return payload of the matched prefix, or null if no prefix matches
     */
    public String getPrefixPayload(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        TrieNode current = root;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            current = current.children.get(ch);
            if (current == null) {
                return null;
            }
            if (current.isEndOfWord) {
                return current.payload;
            }
        }
        return null;
    }

    /**
     * Returns all patterns stored in the Trie that begin with the given prefix.
     *
     * @param prefix query prefix
     * @return list of matching complete words
     */
    public List<String> getWordsWithPrefix(String prefix) {
        if (prefix == null) {
            return Collections.emptyList();
        }
        TrieNode node = findNode(prefix);
        if (node == null) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        collectWords(node, new StringBuilder(prefix), results);
        return results;
    }

    /**
     * Returns the total number of unique patterns inserted in the Trie.
     *
     * @return pattern count
     */
    public int size() {
        return wordCount;
    }

    private TrieNode findNode(String str) {
        if (str == null) {
            return null;
        }
        TrieNode current = root;
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            current = current.children.get(ch);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private void collectWords(TrieNode node, StringBuilder currentPath, List<String> results) {
        if (node.isEndOfWord) {
            results.add(currentPath.toString());
        }
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            currentPath.append(entry.getKey());
            collectWords(entry.getValue(), currentPath, results);
            currentPath.deleteCharAt(currentPath.length() - 1);
        }
    }
}
