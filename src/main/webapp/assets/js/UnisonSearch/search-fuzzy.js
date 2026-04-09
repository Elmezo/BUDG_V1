// Advanced Fuzzy Search - Frontend Implementation
// Supports:
// - Character transposition (e.g., "Cutsomer" → "Customer")
// - Typo tolerance (character substitution, insertion, deletion)
// - Phonetic matching (e.g., "acao" matches "Ação")
// - Multi-language support (English and non-English characters)
// - Wildcard support (*) at beginning or end

/**
 * Calculate Levenshtein distance between two strings.
 * Measures edit distance (insertions, deletions, substitutions).
 */
function levenshteinDistance(str1, str2) {
    const len1 = str1.length;
    const len2 = str2.length;
    const matrix = [];

    // Initialize matrix
    for (let i = 0; i <= len1; i++) {
        matrix[i] = [i];
    }
    for (let j = 0; j <= len2; j++) {
        matrix[0][j] = j;
    }

    // Fill matrix
    for (let i = 1; i <= len1; i++) {
        for (let j = 1; j <= len2; j++) {
            const cost = str1[i - 1] === str2[j - 1] ? 0 : 1;
            matrix[i][j] = Math.min(
                matrix[i - 1][j] + 1,      // deletion
                matrix[i][j - 1] + 1,      // insertion
                matrix[i - 1][j - 1] + cost // substitution
            );
        }
    }

    return matrix[len1][len2];
}

/**
 * Generate all adjacent character transpositions.
 * Example: "Cutsomer" → ["uCtsomer", "Ctuosmer", "Cutsomer", "Cusotmer", "Cutsmoer", "Cutsoemr"]
 */
function generateTranspositions(str) {
    if (!str || str.length < 2) return [];
    
    const transpositions = [];
    const chars = str.split('');
    
    // Generate all adjacent swaps
    for (let i = 0; i < chars.length - 1; i++) {
        const swapped = [...chars];
        // Swap adjacent characters
        const temp = swapped[i];
        swapped[i] = swapped[i + 1];
        swapped[i + 1] = temp;
        
        const transposed = swapped.join('');
        if (transposed !== str) {
            transpositions.push(transposed);
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
 */
function normalizeForPhonetic(str) {
    if (!str) return str;
    
    // Step 1: Unicode normalization (NFD = decompose accented characters)
    // "é" becomes "e" + combining accent mark
    let normalized = str.normalize('NFD');
    
    // Step 2: Remove all combining diacritical marks (accents)
    // Regex \p{M} matches any combining mark (requires unicode flag)
    normalized = normalized.replace(/[\u0300-\u036f]/g, '');
    
    // Step 3: Handle special phonetic substitutions
    normalized = normalized.toLowerCase();
    
    // Common phonetic substitutions (sounds similar)
    normalized = normalized.replace(/ph/g, 'f');
    normalized = normalized.replace(/ç/g, 'c');
    normalized = normalized.replace(/ñ/g, 'n');
    normalized = normalized.replace(/ß/g, 'ss');
    
    // Arabic normalization (presentation forms)
    normalized = normalized.replace(/ﺍ/g, 'ا');
    normalized = normalized.replace(/ﺎ/g, 'ا');
    
    return normalized;
}

/**
 * Advanced fuzzy match with multiple strategies.
 * Supports typos, transpositions, phonetic matching, and wildcards.
 */
function fuzzyMatch(text, query, threshold = null) {
    if (!text || !query) return false;

    const textLower = text.toLowerCase();
    const queryLower = query.toLowerCase();
    const queryLen = queryLower.length;

    // 1. Fast path: exact match or substring match
    if (textLower.includes(queryLower)) return true;

    // 2. Handle wildcards
    if (query.includes('*')) {
        const regex = new RegExp(
            '^' + query.replace(/\*/g, '.*').replace(/[.+?^${}()|[\]\\]/g, '\\$&') + '$',
            'i'
        );
        if (regex.test(text)) return true;
    }

    // 3. Check for multi-word queries → use flexible word order matching
    // This allows "Data Party" to match "Party Data" and "legal record" to match "legal entity record"
    const queryWords = queryLower.trim().split(/\s+/);
    if (queryWords.length > 1) {
        // Use the existing fuzzyMatchMultiWord function for flexible word order
        return fuzzyMatchMultiWord(text, query, threshold);
    }

    // 4. Phonetic normalization (for multi-language support)
    const normalizedText = normalizeForPhonetic(textLower);
    const normalizedQuery = normalizeForPhonetic(queryLower);
    
    if (normalizedText.includes(normalizedQuery)) return true;

    // 5. Check transpositions (for typos like "Cutsomer" → "Customer")
    const transpositions = generateTranspositions(queryLower);
    for (const transposition of transpositions) {
        if (textLower.includes(transposition)) return true;
    }

    // 6. Dynamic threshold: allow 1-2 character errors based on query length
    let maxErrors;
    if (threshold !== null) {
        maxErrors = threshold;
    } else {
        maxErrors = queryLen <= 2 ? 1 : 2;
    }

    // 7. Check Levenshtein distance on individual words
    const words = textLower.split(/\s+/);
    for (const word of words) {
        if (word.length < queryLen - maxErrors) continue;

        // Check if query matches the word directly
        const wordDistance = levenshteinDistance(queryLower, word);
        if (wordDistance <= maxErrors) {
            return true;
        }

        // Check if query matches any substring of the word
        if (checkSubstringMatch(word, queryLower, maxErrors)) {
            return true;
        }
    }

    // 8. Also check the full text (for multi-word matches)
    if (checkSubstringMatch(textLower, queryLower, maxErrors)) {
        return true;
    }

    return false;
}

/**
 * Check if query matches a substring within text with allowed errors.
 */
function checkSubstringMatch(text, query, maxErrors) {
    const queryLen = query.length;
    
    // Check substrings of length: queryLen-maxErrors to queryLen+maxErrors
    const minLen = Math.max(queryLen - maxErrors, 1);
    const maxLen = Math.min(queryLen + maxErrors, text.length);
    
    for (let startPos = 0; startPos <= text.length - minLen; startPos++) {
        for (let len = minLen; len <= maxLen; len++) {
            if (startPos + len > text.length) break;
            
            const substring = text.substring(startPos, startPos + len);
            const distance = levenshteinDistance(query, substring);
            
            if (distance <= maxErrors) {
                return true;
            }
        }
    }
    
    return false;
}

/**
 * Fuzzy match for multi-word queries.
 * Each word in the query can match anywhere in the text.
 */
function fuzzyMatchMultiWord(text, query, threshold = null) {
    if (!text || !query) return false;
    
    // Split query into words
    const queryWords = query.trim().split(/\s+/);
    
    // Each word in query must match somewhere in text
    for (const word of queryWords) {
        if (!fuzzyMatch(text, word, threshold)) {
            return false;
        }
    }
    
    return true;
}

/**
 * Filter an array of items by fuzzy matching.
 * 
 * @param {Array} items - Array of items to filter
 * @param {String} query - Search query
 * @param {Function|String} getTextFn - Function to get text from item, or property name
 * @param {Number} threshold - Maximum edit distance (default: 2)
 * @returns {Array} Filtered items
 */
function fuzzyFilter(items, query, getTextFn, threshold = 2) {
    if (!query) return items;
    
    const getText = typeof getTextFn === 'function' 
        ? getTextFn 
        : (item => item[getTextFn] || '');
    
    return items.filter(item => {
        const text = getText(item);
        return fuzzyMatchMultiWord(text, query, threshold);
    });
}

/**
 * Highlight matched portions of text.
 * Useful for displaying search results with highlighted matches.
 */
function highlightMatch(text, query) {
    if (!text || !query) return text;
    
    const textLower = text.toLowerCase();
    const queryLower = query.toLowerCase();
    
    // Try exact match first
    const index = textLower.indexOf(queryLower);
    if (index !== -1) {
        return text.substring(0, index) +
               '<mark>' + text.substring(index, index + query.length) + '</mark>' +
               text.substring(index + query.length);
    }
    
    // Try phonetic match
    const normalizedText = normalizeForPhonetic(textLower);
    const normalizedQuery = normalizeForPhonetic(queryLower);
    const normalizedIndex = normalizedText.indexOf(normalizedQuery);
    
    if (normalizedIndex !== -1) {
        return text.substring(0, normalizedIndex) +
               '<mark>' + text.substring(normalizedIndex, normalizedIndex + query.length) + '</mark>' +
               text.substring(normalizedIndex + query.length);
    }
    
    // For fuzzy matches, just return original text (exact position is ambiguous)
    return text;
}

/**
 * Sort items by relevance to query.
 * Items with exact matches rank higher than fuzzy matches.
 */
function sortByRelevance(items, query, getTextFn) {
    if (!query) return items;
    
    const getText = typeof getTextFn === 'function' 
        ? getTextFn 
        : (item => item[getTextFn] || '');
    
    const scored = items.map(item => {
        const text = getText(item);
        const textLower = text.toLowerCase();
        const queryLower = query.toLowerCase();
        
        let score = 0;
        
        // Exact match: highest score
        if (textLower === queryLower) {
            score = 1000;
        }
        // Starts with query: high score
        else if (textLower.startsWith(queryLower)) {
            score = 500;
        }
        // Contains query: medium score
        else if (textLower.includes(queryLower)) {
            score = 250;
        }
        // Phonetic match: lower score
        else {
            const normalizedText = normalizeForPhonetic(textLower);
            const normalizedQuery = normalizeForPhonetic(queryLower);
            
            if (normalizedText.includes(normalizedQuery)) {
                score = 100;
            } else {
                // Fuzzy match: lowest score
                score = 50;
            }
        }
        
        // Bonus for shorter text (more relevant)
        score += (1000 - text.length) / 10;
        
        return { item, score };
    });
    
    // Sort by score (descending)
    scored.sort((a, b) => b.score - a.score);
    
    return scored.map(s => s.item);
}
