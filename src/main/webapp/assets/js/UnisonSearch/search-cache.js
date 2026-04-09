// Selected Item State Management (for related search functionality)
// Note: Data caching has been removed - all data is fetched fresh from server

let __BUDG_CACHE = {
    selectedItem: null // Store selected item for related search
};

/**
 * LRU Cache for Search Results
 * Features:
 * - Maximum 50 entries
 * - 5-minute expiration
 * - Least Recently Used eviction
 */
class SearchCache {
    constructor(maxSize = 50) {
        this.cache = new Map();
        this.maxSize = maxSize;
        this.accessOrder = []; // Track access order for LRU
    }

    /**
     * Store data in cache
     */
    set(key, value) {
        // Remove oldest entry if at capacity
        if (this.cache.size >= this.maxSize && !this.cache.has(key)) {
            const oldestKey = this.accessOrder.shift();
            this.cache.delete(oldestKey);
            console.log(`[Cache] Evicted oldest entry: ${oldestKey}`);
        }

        this.cache.set(key, {
            data: value,
            timestamp: Date.now(),
            hits: 0
        });

        // Update access order
        this.accessOrder = this.accessOrder.filter(k => k !== key);
        this.accessOrder.push(key);
    }

    /**
     * Retrieve data from cache
     */
    get(key) {
        if (!this.cache.has(key)) {
            return null;
        }

        const entry = this.cache.get(key);

        // Check expiration (5 minutes)
        const age = Date.now() - entry.timestamp;
        if (age > 5 * 60 * 1000) {
            this.cache.delete(key);
            this.accessOrder = this.accessOrder.filter(k => k !== key);
            console.log(`[Cache] Expired entry: ${key}`);
            return null;
        }

        // Update statistics
        entry.hits++;

        // Update access order
        this.accessOrder = this.accessOrder.filter(k => k !== key);
        this.accessOrder.push(key);

        console.log(`[Cache] Hit for ${key} (${entry.hits} hits, age: ${Math.round(age/1000)}s)`);
        return entry.data;
    }

    /**
     * Invalidate entries matching a pattern
     */
    invalidate(pattern) {
        let count = 0;
        for (const [key, value] of this.cache.entries()) {
            if (key.includes(pattern)) {
                this.cache.delete(key);
                this.accessOrder = this.accessOrder.filter(k => k !== key);
                count++;
            }
        }
        console.log(`[Cache] Invalidated ${count} entries matching: ${pattern}`);
    }

    /**
     * Clear all cache entries
     */
    clear() {
        const size = this.cache.size;
        this.cache.clear();
        this.accessOrder = [];
        console.log(`[Cache] Cleared ${size} entries`);
    }

    /**
     * Get cache statistics
     */
    getStats() {
        return {
            size: this.cache.size,
            maxSize: this.maxSize,
            entries: Array.from(this.cache.entries()).map(([key, value]) => ({
                key,
                hits: value.hits,
                age: Math.round((Date.now() - value.timestamp) / 1000)
            }))
        };
    }
}

// Create global cache instance
const searchCache = new SearchCache(50);

// Make it available globally
if (typeof window !== 'undefined') {
    window.searchCache = searchCache;
}

function setSelectedItem(item) {
    __BUDG_CACHE.selectedItem = item;

    if (!item) {
        // Clearing selection: hide the clear button and remove any indicator
        const clearBtn = document.querySelector('.clear-selection-btn');
        if (clearBtn) clearBtn.style.display = 'none';
        if (typeof removeRelatedSearchIndicator === 'function') removeRelatedSearchIndicator();
        return;
    }

    // Show clear selection button
    const clearBtn = document.querySelector('.clear-selection-btn');
    if (clearBtn) {
        clearBtn.style.display = 'inline-block';
    }

    // Create or update the related search indicator
    if (typeof createRelatedSearchIndicator === 'function') {
        createRelatedSearchIndicator(item);
    }
}

function getSelectedItem() {
    return __BUDG_CACHE.selectedItem;
}

function clearSelectedItem() {
    __BUDG_CACHE.selectedItem = null;

    // Hide clear selection button
    const clearBtn = document.querySelector('.clear-selection-btn');
    if (clearBtn) {
        clearBtn.style.display = 'none';
    }

    // Remove selected row styling
    document.querySelectorAll('.search-table tbody tr').forEach(r => {
        r.classList.remove('selected-row');
    });

    // Remove related search indicator
    removeRelatedSearchIndicator();

    // Clear Unison Search results
    if (typeof window !== 'undefined') {
        window.currentUnisonSearchResults = null;
    }

    // Reload all data for current category (remove filtering)
    const currentCategory = getActiveCategoryWithFallback();
    if (currentCategory) {
        loadCategoryData(currentCategory);
    }
}


