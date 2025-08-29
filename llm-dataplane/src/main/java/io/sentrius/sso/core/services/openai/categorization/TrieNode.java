package io.sentrius.sso.core.services.openai.categorization;

import java.util.HashMap;
import java.util.Map;
import io.sentrius.sso.core.model.categorization.CommandCategory;

public class TrieNode {
    Map<String, TrieNode> children = new HashMap<>();
    CommandCategory commandCategory; // Store the CommandCategory at the end node
    boolean isEndOfCommand = false;
    boolean isWildcard = false; // Marks this node as a wildcard
}
