package com.goodda.jejuday.spot.search;

import java.util.*;

class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    Set<Long> spotIds = new HashSet<>();
}