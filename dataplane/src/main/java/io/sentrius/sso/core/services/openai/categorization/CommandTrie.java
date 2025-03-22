package io.sentrius.sso.core.services.openai.categorization;

import io.sentrius.sso.core.model.categorization.CommandCategory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommandTrie {
    private final TrieNode root = new TrieNode();

    private String normalizePath(String path) {
        return path.replaceAll("/+$", ""); // Remove trailing slashes
    }

    public void insert(String command, CommandCategory category) {
        String[] parts = command.split(" ");
        TrieNode current = root;
        for (String part : parts) {
            part = normalizePath(part); // Normalize each part
            current = current.children.computeIfAbsent(part, k -> new TrieNode());
        }
        current.isEndOfCommand = true;
        if (category.getPattern().endsWith("*")) {
            current.isWildcard = true;
        }
        else {
            current.isWildcard = false;
        }
        current.commandCategory = category;
    }

    public CommandCategory search(String command) {
        String[] parts = command.split(" ");
        TrieNode current = root;
        for (String part : parts) {
            current = current.children.get(part);
            if (current == null) {
                return null; // Command not found
            }
        }
        return current.isEndOfCommand ? current.commandCategory : null;
    }

    public CommandCategory searchByPrefix(String command) {
        String[] parts = command.split(" ");
        TrieNode current = root;
        CommandCategory lastCategory = null;

        for (String part : parts) {
            part = normalizePath(part); // Normalize each part
            log.info("Searching for part: {}", part);

            // Check if the part exists in the children
            if (current.children.containsKey(part)) {
                current = current.children.get(part);
                if (current.isEndOfCommand) {
                    lastCategory = current.commandCategory;
                }
            } else {
                // If no exact match, check for wildcard match (e.g., "cat /etc/")
                if (current.isEndOfCommand) {
                    log.info("Partial match found at: {}", part);
                    lastCategory = current.commandCategory;
                }
                break; // No further match possible
            }
        }

        return lastCategory;
    }
}
