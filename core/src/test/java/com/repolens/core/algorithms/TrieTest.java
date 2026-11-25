package com.repolens.core.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrieTest {

    @Test
    @DisplayName("Exact search and prefix matching work accurately")
    void shouldInsertAndSearchWords() {
        Trie trie = new Trie();
        trie.insert(".env", "CRITICAL_SECRET");
        trie.insert(".gitignore", "HYGIENE_OK");
        trie.insert("node_modules", "BLOAT_JUNK");

        assertThat(trie.search(".env")).isTrue();
        assertThat(trie.search(".gitignore")).isTrue();
        assertThat(trie.search("node_modules")).isTrue();
        assertThat(trie.search(".git")).isFalse(); // only prefix, not complete word

        assertThat(trie.startsWith(".git")).isTrue();
        assertThat(trie.startsWith("node_")).isTrue();
        assertThat(trie.startsWith("target")).isFalse();
    }

    @Test
    @DisplayName("hasPrefixOf detects filenames starting with dangerous prefixes")
    void shouldDetectPathPrefixes() {
        Trie trie = new Trie();
        trie.insert(".env", "SECRET");
        trie.insert("id_rsa", "PRIVATE_KEY");

        // .env.local and .env.production should be flagged because .env is in the trie
        assertThat(trie.hasPrefixOf(".env.local")).isTrue();
        assertThat(trie.hasPrefixOf(".env.production")).isTrue();
        assertThat(trie.hasPrefixOf("id_rsa.pub")).isTrue();
        assertThat(trie.hasPrefixOf("safe_file.txt")).isFalse();

        assertThat(trie.getPrefixPayload(".env.local")).isEqualTo("SECRET");
        assertThat(trie.getPrefixPayload("id_rsa.bak")).isEqualTo("PRIVATE_KEY");
    }

    @Test
    @DisplayName("getWordsWithPrefix retrieves all matching words")
    void shouldReturnWordsWithPrefix() {
        Trie trie = new Trie();
        trie.insert("deploy");
        trie.insert("deployment");
        trie.insert("dependabot");
        trie.insert("docker");

        List<String> depWords = trie.getWordsWithPrefix("dep");
        assertThat(depWords).containsExactlyInAnyOrder("deploy", "deployment", "dependabot");
        assertThat(trie.size()).isEqualTo(4);
    }
}
