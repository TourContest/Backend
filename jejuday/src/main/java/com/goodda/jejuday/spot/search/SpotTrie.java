package com.goodda.jejuday.spot.search;

import java.util.*;

public class SpotTrie {
    private final TrieNode root = new TrieNode();

    public void insert(String name, Long spotId) {
        TrieNode node = root;
        for (char c : name.toLowerCase().toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
            node.spotIds.add(spotId);
        }
    }

    public Set<Long> searchByPrefix(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toLowerCase().toCharArray()) {
            node = node.children.get(c);
            if (node == null) return Collections.emptySet();
        }
        return node.spotIds;
    }
}