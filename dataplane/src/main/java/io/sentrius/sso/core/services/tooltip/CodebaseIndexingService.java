package io.sentrius.sso.core.services.tooltip;

import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.services.documents.DocumentService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for indexing codebase and documentation files for tooltip context.
 * Scans Java source files, markdown documentation, and other relevant files.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CodebaseIndexingService {

    private final DocumentService documentService;
    
    @Value("${sentrius.tooltip.index.codebase-path:}")
    private String codebasePath;
    
    @Value("${sentrius.tooltip.index.enabled:false}")
    private boolean indexingEnabled;

    public CodebaseIndexingService(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Index the entire codebase and documentation
     */
    public IndexingResult indexCodebase() {
        if (!indexingEnabled) {
            log.info("Codebase indexing is disabled");
            return IndexingResult.builder()
                    .success(false)
                    .message("Indexing is disabled")
                    .build();
        }

        if (codebasePath == null || codebasePath.isEmpty()) {
            log.warn("Codebase path not configured for indexing");
            return IndexingResult.builder()
                    .success(false)
                    .message("Codebase path not configured")
                    .build();
        }

        Path basePath = Paths.get(codebasePath);
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            log.error("Codebase path does not exist or is not a directory: {}", codebasePath);
            return IndexingResult.builder()
                    .success(false)
                    .message("Invalid codebase path")
                    .build();
        }

        log.info("Starting codebase indexing from: {}", codebasePath);
        
        int totalFiles = 0;
        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();

        try {
            // Index markdown documentation
            List<Path> mdFiles = findFiles(basePath, "**/*.md");
            log.info("Found {} markdown files to index", mdFiles.size());
            for (Path file : mdFiles) {
                totalFiles++;
                try {
                    indexDocumentationFile(file, basePath);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    errors.add("Error indexing " + file + ": " + e.getMessage());
                    log.warn("Error indexing file: {}", file, e);
                }
            }

            // Index Java source files (controllers, services with annotations)
            List<Path> javaFiles = findFiles(basePath, "**/*Controller.java", "**/*Service.java");
            log.info("Found {} Java files to index", javaFiles.size());
            for (Path file : javaFiles) {
                totalFiles++;
                try {
                    indexJavaFile(file, basePath);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    errors.add("Error indexing " + file + ": " + e.getMessage());
                    log.warn("Error indexing file: {}", file, e);
                }
            }

            log.info("Codebase indexing completed: {} files processed, {} successful, {} errors",
                    totalFiles, successCount, errorCount);

            return IndexingResult.builder()
                    .success(true)
                    .totalFiles(totalFiles)
                    .successCount(successCount)
                    .errorCount(errorCount)
                    .errors(errors)
                    .message("Indexing completed successfully")
                    .build();

        } catch (Exception e) {
            log.error("Error during codebase indexing", e);
            return IndexingResult.builder()
                    .success(false)
                    .message("Indexing failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Index a markdown documentation file
     */
    private void indexDocumentationFile(Path file, Path basePath) throws IOException {
        String content = Files.readString(file);
        String relativePath = basePath.relativize(file).toString();
        String fileName = file.getFileName().toString();

        // Extract title from first heading or filename
        String title = extractMarkdownTitle(content, fileName);
        
        // Extract summary from first paragraph
        String summary = extractMarkdownSummary(content);

        // Store document
        documentService.storeDocument(
                title,
                "DOCUMENTATION",
                content,
                "text/markdown",
                summary,
                new String[]{"documentation", "markdown", "sentrius"},
                "PUBLIC",
                null,
                "system"
        );

        log.debug("Indexed documentation file: {}", relativePath);
    }

    /**
     * Index a Java source file
     */
    private void indexJavaFile(Path file, Path basePath) throws IOException {
        String content = Files.readString(file);
        String relativePath = basePath.relativize(file).toString();
        String fileName = file.getFileName().toString();

        // Extract class name and package
        String className = extractJavaClassName(content, fileName);
        String packageName = extractJavaPackage(content);
        
        // Extract class-level JavaDoc
        String summary = extractJavaDocSummary(content);

        // Create title from package and class name
        String title = packageName != null ? packageName + "." + className : className;

        // Store document
        documentService.storeDocument(
                title,
                "SOURCE_CODE",
                content,
                "text/x-java",
                summary != null ? summary : "Java source file: " + className,
                new String[]{"java", "source", "sentrius", determineFileType(fileName)},
                "PUBLIC",
                null,
                "system"
        );

        log.debug("Indexed Java file: {}", relativePath);
    }

    /**
     * Find files matching glob patterns
     */
    private List<Path> findFiles(Path basePath, String... patterns) throws IOException {
        List<Path> results = new ArrayList<>();
        
        for (String pattern : patterns) {
            try (Stream<Path> paths = Files.walk(basePath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> matchesGlob(basePath.relativize(p).toString(), pattern))
                     .forEach(results::add);
            }
        }
        
        return results;
    }

    /**
     * Simple glob pattern matching.
     * Converts glob patterns to regex for matching file paths.
     * Supports: ** (any path), * (any non-separator chars), ? (single char)
     * 
     * Note: This is a simplified implementation. For production use with complex patterns,
     * consider using Java NIO's PathMatcher or Apache Commons IO.
     */
    private boolean matchesGlob(String path, String pattern) {
        // Convert glob to regex
        // ** matches any path including separators
        // * matches any characters except path separator
        // ? matches single character
        String regex = pattern
                .replace("**", "DOUBLESTAR")
                .replace("*", "[^/]*")
                .replace("DOUBLESTAR", ".*")
                .replace("?", ".");
        return path.matches(regex);
    }

    /**
     * Extract title from markdown content
     */
    private String extractMarkdownTitle(String content, String fileName) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return fileName.replace(".md", "").replace("-", " ").replace("_", " ");
    }

    /**
     * Extract summary from markdown content
     */
    private String extractMarkdownSummary(String content) {
        String[] lines = content.split("\n");
        StringBuilder summary = new StringBuilder();
        boolean foundContent = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                if (foundContent) break;
                continue;
            }
            foundContent = true;
            summary.append(line).append(" ");
            if (summary.length() > 200) break;
        }
        
        String result = summary.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Extract Java class name
     */
    private String extractJavaClassName(String content, String fileName) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.contains("class ") || line.contains("interface ") || line.contains("enum ")) {
                // Simple extraction
                String trimmed = line.trim();
                if (trimmed.startsWith("public ") || trimmed.startsWith("class ") || 
                    trimmed.startsWith("interface ") || trimmed.startsWith("enum ")) {
                    String[] parts = trimmed.split("\\s+");
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i].equals("class") || parts[i].equals("interface") || parts[i].equals("enum")) {
                            return parts[i + 1].split("[<{]")[0];
                        }
                    }
                }
            }
        }
        return fileName.replace(".java", "");
    }

    /**
     * Extract Java package name
     */
    private String extractJavaPackage(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("package ")) {
                return line.trim().substring(8).replace(";", "").trim();
            }
        }
        return null;
    }

    /**
     * Extract JavaDoc summary
     */
    private String extractJavaDocSummary(String content) {
        int start = content.indexOf("/**");
        if (start == -1) return null;
        
        int end = content.indexOf("*/", start);
        if (end == -1) return null;
        
        String javadoc = content.substring(start + 3, end);
        String[] lines = javadoc.split("\n");
        StringBuilder summary = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("*")) line = line.substring(1).trim();
            if (line.isEmpty() || line.startsWith("@")) break;
            summary.append(line).append(" ");
        }
        
        String result = summary.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Determine file type from filename
     */
    private String determineFileType(String fileName) {
        if (fileName.contains("Controller")) return "controller";
        if (fileName.contains("Service")) return "service";
        if (fileName.contains("Repository")) return "repository";
        if (fileName.contains("DTO")) return "dto";
        return "other";
    }

    /**
     * Result of indexing operation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexingResult {
        private boolean success;
        private String message;
        private int totalFiles;
        private int successCount;
        private int errorCount;
        private List<String> errors;
    }
}
