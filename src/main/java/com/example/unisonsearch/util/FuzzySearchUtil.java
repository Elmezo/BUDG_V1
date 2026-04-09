package com.example.unisonsearch.util;

import java.text.Normalizer;
import java.util.*;

/**
 * Advanced fuzzy search utility for generating SQL patterns.
 * Supports:
 * - Character transposition (e.g., "Cutsomer" → "Customer")
 * - Typo tolerance (character substitution, insertion, deletion)
 * - Phonetic matching (e.g., "acao" matches "Ação")
 * - Multi-language support (English and non-English characters)
 * - Wildcard support (*) at beginning or end
 */
public class FuzzySearchUtil {

    // Maximum number of patterns to generate per query (performance limit)
    // Increased to support 2 non-adjacent character errors (e.g., "legal" → "ligol")
    private static final int MAX_PATTERNS = 15;
    
    // Maximum query length for transposition generation (performance limit)
    private static final int MAX_TRANSPOSITION_LENGTH = 15;

    /**
     * Generate multiple SQL LIKE patterns for fuzzy matching.
     * Allows 1-3 character errors while maintaining word similarity.
     * Examples: "age" matches "age", "oge", "ega", "aeg" but not "gender"
     * 
     * @param query The search query
     * @return List of SQL LIKE patterns that allow small typos (1-3 chars) but keep word similar
     */
    public static List<String> generateFuzzyPatterns(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.singletonList("%%");
        }

        Set<String> patterns = new LinkedHashSet<>(); // Preserve order, avoid duplicates
        String cleanQuery = query.trim();
        
        // Extract wildcards
        boolean hasStartWildcard = cleanQuery.startsWith("*");
        boolean hasEndWildcard = cleanQuery.endsWith("*");
        
        if (hasStartWildcard) {
            cleanQuery = cleanQuery.substring(1);
        }
        if (hasEndWildcard) {
            cleanQuery = cleanQuery.substring(0, cleanQuery.length() - 1);
        }
        
        if (cleanQuery.isEmpty()) {
            return Collections.singletonList("%%");
        }

        int queryLen = cleanQuery.length();
        
        // 1. Original query pattern (exact match with wildcards) - always include this first
        patterns.add(buildPattern(cleanQuery, hasStartWildcard, hasEndWildcard));

        // 2. Generate patterns that allow 1-3 character errors using SQL wildcards (_)
        // This maintains word similarity while allowing small typos
        // Strategy: Replace 1-3 characters with _ wildcard in different positions
        
        if (queryLen >= 2) {
            // Allow 1 character error: replace each position with _
            for (int i = 0; i < queryLen && patterns.size() < MAX_PATTERNS; i++) {
                String pattern = cleanQuery.substring(0, i) + "_" + cleanQuery.substring(i + 1);
                patterns.add(buildPattern(pattern, hasStartWildcard, hasEndWildcard));
            }
        }
        
        if (queryLen >= 3 && patterns.size() < MAX_PATTERNS) {
            // Allow 2 character errors: replace two adjacent positions
            for (int i = 0; i < queryLen - 1 && patterns.size() < MAX_PATTERNS; i++) {
                String pattern = cleanQuery.substring(0, i) + "__" + cleanQuery.substring(i + 2);
                patterns.add(buildPattern(pattern, hasStartWildcard, hasEndWildcard));
            }
        }
        
        if (queryLen >= 4 && patterns.size() < MAX_PATTERNS) {
            // Allow 3 character errors: replace three adjacent positions (middle section)
            for (int i = 1; i < queryLen - 2 && patterns.size() < MAX_PATTERNS; i++) {
                String pattern = cleanQuery.substring(0, i) + "___" + cleanQuery.substring(i + 3);
                patterns.add(buildPattern(pattern, hasStartWildcard, hasEndWildcard));
            }
        }
        
        // Allow 2 character errors in non-adjacent positions (e.g., "legal" → "ligol")
        // This covers cases where 2 characters are wrong but not next to each other
        if (queryLen >= 4 && patterns.size() < MAX_PATTERNS) {
            // Generate patterns with 2 wildcards in different positions
            // Limit to a few strategic combinations to avoid too many patterns
            for (int i = 0; i < queryLen - 1 && patterns.size() < MAX_PATTERNS; i++) {
                for (int j = i + 2; j < queryLen && patterns.size() < MAX_PATTERNS; j++) {
                    // Skip if too many patterns already
                    if (patterns.size() >= MAX_PATTERNS) break;
                    
                    // Create pattern with wildcards at positions i and j
                    StringBuilder patternBuilder = new StringBuilder();
                    patternBuilder.append(cleanQuery.substring(0, i));
                    patternBuilder.append("_");
                    patternBuilder.append(cleanQuery.substring(i + 1, j));
                    patternBuilder.append("_");
                    patternBuilder.append(cleanQuery.substring(j + 1));
                    
                    patterns.add(buildPattern(patternBuilder.toString(), hasStartWildcard, hasEndWildcard));
                }
                // Limit combinations to avoid too many patterns
                if (patterns.size() >= MAX_PATTERNS - 2) break;
            }
        }

        // 3. Character transposition patterns (for typos like "age" → "aeg", "oge")
        // Only generate a few transpositions to maintain similarity
        if (queryLen >= 2 && queryLen <= MAX_TRANSPOSITION_LENGTH && patterns.size() < MAX_PATTERNS) {
            List<String> transpositions = generateTranspositions(cleanQuery);
            // Limit transpositions to first 2-3 to maintain word similarity
            int maxTranspositions = Math.min(3, transpositions.size());
            for (int i = 0; i < maxTranspositions && patterns.size() < MAX_PATTERNS; i++) {
                patterns.add(buildPattern(transpositions.get(i), hasStartWildcard, hasEndWildcard));
            }
        }

        // 4. Phonetic normalization (for multi-language support) - only if not too many patterns
        if (patterns.size() < MAX_PATTERNS) {
            String normalized = normalizeForPhonetic(cleanQuery);
            if (!normalized.equals(cleanQuery)) {
                patterns.add(buildPattern(normalized, hasStartWildcard, hasEndWildcard));
            }
        }

        return new ArrayList<>(patterns);
    }

    /**
     * Build a SQL condition with multiple OR clauses for fuzzy matching.
     * 
     * @param columnName The column name to search
     * @param query The search query
     * @return SQL condition like: (column LIKE ? OR column LIKE ? OR column LIKE ?)
     */
    public static String buildFuzzyCondition(String columnName, String query) {
        List<String> patterns = generateFuzzyPatterns(query);
        
        if (patterns.size() == 1) {
            return columnName + " LIKE ?";
        }
        
        StringBuilder condition = new StringBuilder("(");
        for (int i = 0; i < patterns.size(); i++) {
            if (i > 0) {
                condition.append(" OR ");
            }
            condition.append(columnName).append(" LIKE ?");
        }
        condition.append(")");
        
        return condition.toString();
    }

    /**
     * Build a SQL LIKE pattern with appropriate wildcards.
     * 
     * @param query The query string
     * @param hasStartWildcard Whether to add % at the start
     * @param hasEndWildcard Whether to add % at the end
     * @return SQL LIKE pattern (e.g., "%query%", "query%", "%query")
     */
    private static String buildPattern(String query, boolean hasStartWildcard, boolean hasEndWildcard) {
        StringBuilder pattern = new StringBuilder();
        
        if (hasStartWildcard && hasEndWildcard) {
            pattern.append("%").append(query).append("%");
        } else if (hasStartWildcard) {
            pattern.append("%").append(query);
        } else if (hasEndWildcard) {
            pattern.append(query).append("%");
        } else {
            // No explicit wildcards - add % on both sides for fuzzy matching
            pattern.append("%").append(query).append("%");
        }
        
        return pattern.toString();
    }

    /**
     * Generate all adjacent character transpositions.
     * Example: "Cutsomer" → ["uCtsomer", "Ctuosmer", "Cutsomer", "Cusotmer", "Cutsmoer", "Cutsoemr"]
     * 
     * @param str The input string
     * @return List of transposed variations (limited to avoid performance issues)
     */
    private static List<String> generateTranspositions(String str) {
        List<String> transpositions = new ArrayList<>();
        
        if (str == null || str.length() < 2) {
            return transpositions;
        }
        
        char[] chars = str.toCharArray();
        
        // Generate all adjacent swaps
        for (int i = 0; i < chars.length - 1; i++) {
            char[] swapped = Arrays.copyOf(chars, chars.length);
            // Swap adjacent characters
            char temp = swapped[i];
            swapped[i] = swapped[i + 1];
            swapped[i + 1] = temp;
            
            String transposed = new String(swapped);
            if (!transposed.equals(str)) {
                transpositions.add(transposed);
            }
        }
        
        return transpositions;
    }

    /**
     * Normalize string for phonetic matching.
     * Supports multi-language characters (Portuguese, Spanish, Arabic, etc.).
     * 
     * Examples:
     * - "Ação" → "Acao"
     * - "résumé" → "resume"
     * - "café" → "cafe"
     * 
     * @param str The input string
     * @return Normalized string without accents
     */
    private static String normalizeForPhonetic(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        // Step 1: Unicode normalization (NFD = decompose accented characters)
        // "é" becomes "e" + combining accent mark
        String normalized = Normalizer.normalize(str, Normalizer.Form.NFD);
        
        // Step 2: Remove all combining diacritical marks (accents)
        // Regex \p{M} matches any combining mark
        normalized = normalized.replaceAll("\\p{M}", "");
        
        // Step 3: Handle special phonetic substitutions
        normalized = normalized.toLowerCase();
        
        // Common phonetic substitutions (sounds similar)
        normalized = normalized.replace("ph", "f");
        normalized = normalized.replace("ç", "c");
        normalized = normalized.replace("ñ", "n");
        normalized = normalized.replace("ß", "ss");
        
        // Arabic normalization (optional - keep Arabic letters as-is for exact matching)
        // But normalize Arabic presentation forms
        normalized = normalized.replace("ﺍ", "ا");
        normalized = normalized.replace("ﺎ", "ا");
        
        return normalized;
    }

    /**
     * Calculate Levenshtein distance between two strings.
     * Used to measure how similar two strings are (number of edits needed).
     * 
     * @param str1 First string
     * @param str2 Second string
     * @return Number of edits (insertions, deletions, substitutions) needed
     */
    public static int levenshteinDistance(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return Math.max(
                str1 == null ? 0 : str1.length(),
                str2 == null ? 0 : str2.length()
            );
        }

        int len1 = str1.length();
        int len2 = str2.length();

        // Create matrix for dynamic programming
        int[][] dp = new int[len1 + 1][len2 + 1];

        // Initialize first row and column
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // Fill matrix
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = str1.charAt(i - 1) == str2.charAt(j - 1) ? 0 : 1;
                
                dp[i][j] = Math.min(
                    Math.min(
                        dp[i - 1][j] + 1,      // deletion
                        dp[i][j - 1] + 1       // insertion
                    ),
                    dp[i - 1][j - 1] + cost    // substitution
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * Check if text matches query with fuzzy logic.
     * Used for in-memory filtering (when results are already loaded).
     * 
     * @param text The text to search in
     * @param query The search query
     * @param threshold Maximum allowed edit distance (default: 2)
     * @return true if matches, false otherwise
     */
    public static boolean fuzzyMatch(String text, String query, int threshold) {
        if (text == null || query == null) {
            return false;
        }

        String textLower = text.toLowerCase();
        String queryLower = query.toLowerCase();

        // Fast path: exact substring match
        if (textLower.contains(queryLower)) {
            return true;
        }

        // Check phonetic normalization
        String normalizedText = normalizeForPhonetic(textLower);
        String normalizedQuery = normalizeForPhonetic(queryLower);
        
        if (normalizedText.contains(normalizedQuery)) {
            return true;
        }

        // Check transpositions
        List<String> transpositions = generateTranspositions(queryLower);
        for (String transposition : transpositions) {
            if (textLower.contains(transposition)) {
                return true;
            }
        }

        // Check Levenshtein distance for individual words
        String[] words = textLower.split("\\s+");
        for (String word : words) {
            if (word.length() >= queryLower.length() - threshold) {
                int distance = levenshteinDistance(queryLower, word);
                if (distance <= threshold) {
                    return true;
                }
            }
        }

        return false;
    }
}




















