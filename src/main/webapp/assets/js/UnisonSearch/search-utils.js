// Helper Functions

// ============================================================================
// UNIFIED CANONICAL MAPPING - Single Source of Truth
// ============================================================================

// Category slug aliases (UI keys: lowercase with hyphens)
const CATEGORY_SLUG_ALIASES = {
    // Dataset
    'dataset': 'data-sets',
    'datasets': 'data-sets',
    'data_set': 'data-sets',
    'data_sets': 'data-sets',
    'data-set': 'data-sets',
    'datasetentity': 'data-sets',
    
    // Attribute (UI uses "attributes" plural)
    'attribute': 'attributes',
    'attr': 'attributes',
    
    // Change Requests
    'changerequest': 'change-requests',
    'change_request': 'change-requests',
    'change_requests': 'change-requests',
    'change-request': 'change-requests',
    'changerequests': 'change-requests',
    
    // Active Tasks
    'active_tasks': 'active-tasks',
    'active_task': 'active-tasks',
    'activetasks': 'active-tasks',
    'activetask': 'active-tasks',
    'tasks': 'active-tasks',
    'task': 'active-tasks',
    
    // Org Unit
    'orgunit': 'org-unit',
    'org_unit': 'org-unit',
    'orgunits': 'org-unit',
    'org_units': 'org-unit',
    'org-units': 'org-unit',
    
    // Business Area
    'business_area': 'business-area',
    'business_areas': 'business-area',
    'businessarea': 'business-area',
    'businessareas': 'business-area',
    
    // Legal Entity
    'legal_entity': 'legal-entity',
    'legal_entities': 'legal-entity',
    'legalentity': 'legal-entity',
    'legalentities': 'legal-entity',
    
    // Regulatory Theme
    'regulatory_theme': 'regulatory-theme',
    'regulatory_themes': 'regulatory-theme',
    'regulatorytheme': 'regulatory-theme',
    'regulatorythemes': 'regulatory-theme',
    
    // Standard (keep as-is)
    'system': 'system',
    'systems': 'system',
    'glossary': 'glossary',
    'glossaries': 'glossary',
    'people': 'people',
    'person': 'people',
    'role': 'role',
    'roles': 'role',
    'client': 'client',
    'clients': 'client',
    'committee': 'committee',
    'committees': 'committee',
    'policy': 'policy',
    'policies': 'policy',
    'process': 'process',
    'processes': 'process',
    'project': 'project',
    'projects': 'project',
    'interface': 'interface',
    'interfaces': 'interface',
    'product': 'product',
    'products': 'product',
    'geography': 'geography',
    'geographies': 'geography',
    'regulation': 'regulation',
    'regulations': 'regulation',
    'regulator': 'regulator',
    'regulators': 'regulator',
    'capability': 'capability',
    'capabilities': 'capability'
};

// Facet ID aliases (Backend keys: UPPERCASE with underscores)
// Facet ID aliases to normalize mismatches (e.g., ATTRIBUTES → ATTRIBUTE)
const FACET_ID_ALIASES = {
    "ATTRIBUTES": "ATTRIBUTE",  // Frontend uses ATTRIBUTES, backend returns ATTRIBUTE
    "DATA_SETS": "DATASET",
    "DATA SETS": "DATASET",
    "DATASETS": "DATASET",
    "DATA_SET": "DATASET",
    // Dataset variations
    'DATA_SETS': 'DATASET',
    'DATA-SETS': 'DATASET',
    'DATASETS': 'DATASET',
    'DATA_SET': 'DATASET',
    'DATA-SET': 'DATASET',
    
    // Attribute variations
    'ATTRIBUTES': 'ATTRIBUTE',
    'ATTR': 'ATTRIBUTE',
    
    // Change Requests
    'CHANGE_REQUESTS': 'CHANGE_REQUESTS', // Keep
    'CHANGE-REQUESTS': 'CHANGE_REQUESTS',
    'CHANGEREQUESTS': 'CHANGE_REQUESTS',
    'CHANGEREQUEST': 'CHANGE_REQUESTS',
    'CHANGE_REQUEST': 'CHANGE_REQUESTS',
    'CHANGE-REQUEST': 'CHANGE_REQUESTS',
    
    // Active Tasks
    'ACTIVE_TASKS': 'ACTIVE_TASKS', // Keep
    'ACTIVE-TASKS': 'ACTIVE_TASKS',
    'ACTIVETASKS': 'ACTIVE_TASKS',
    'ACTIVE_TASK': 'ACTIVE_TASKS',
    'ACTIVE-TASK': 'ACTIVE_TASKS',
    'ACTIVETASK': 'ACTIVE_TASKS',
    
    // Org Unit
    'ORG_UNIT': 'ORG_UNIT', // Keep
    'ORG-UNIT': 'ORG_UNIT',
    'ORGUNIT': 'ORG_UNIT',
    
    // Business Area
    'BUSINESS_AREA': 'BUSINESS_AREA', // Keep
    'BUSINESS-AREA': 'BUSINESS_AREA',
    'BUSINESSAREA': 'BUSINESS_AREA',
    
    // Legal Entity
    'LEGAL_ENTITY': 'LEGAL_ENTITY', // Keep
    'LEGAL-ENTITY': 'LEGAL_ENTITY',
    'LEGALENTITY': 'LEGAL_ENTITY',
    
    // Regulatory Theme
    'REGULATORY_THEME': 'REGULATORY_THEME', // Keep
    'REGULATORY-THEME': 'REGULATORY_THEME',
    'REGULATORYTHEME': 'REGULATORY_THEME'
};

// Bidirectional mapping: Facet ID ↔ Category Slug
const FACET_TO_CATEGORY = {
    'DATASET': 'data-sets',
    'ATTRIBUTE': 'attributes',
    'SYSTEM': 'system',
    'GLOSSARY': 'glossary',
    'DATAQUALITY': 'data-quality',
    'PEOPLE': 'people',
    'ROLE': 'role',
    'INTERFACE': 'interface',
    'ORG_UNIT': 'org-unit',
    'BUSINESS_AREA': 'business-area',
    'LEGAL_ENTITY': 'legal-entity',
    'CLIENT': 'client',
    'COMMITTEE': 'committee',
    'POLICY': 'policy',
    'PROCESS': 'process',
    'PROJECT': 'project',
    'PRODUCT': 'product',
    'CAPABILITY': 'capability',
    'GEOGRAPHY': 'geography',
    'REGULATION': 'regulation',
    'REGULATOR': 'regulator',
    'REGULATORY_THEME': 'regulatory-theme',
    'ACTIVE_TASKS': 'active-tasks',
    'CHANGE_REQUESTS': 'change-requests'
};

/**
 * Canonical category slug (UI key: lowercase with hyphens)
 * @param {string} input - Category from UI/DOM
 * @returns {string} Canonical category slug
 */
function canonicalCategoryKey(input) {
    if (!input) return '';
    
    // Lowercase, trim, replace spaces/underscores with hyphens
    let key = input.toString().toLowerCase().trim()
        .replace(/\s+/g, '-')
        .replace(/_/g, '-');
    
    // Map via aliases
    return CATEGORY_SLUG_ALIASES[key] || key;
}

/**
 * Canonical facet ID (Backend key: UPPERCASE with underscores)
 * @param {string} input - Facet ID from Unison API
 * @returns {string} Canonical facet ID
 */
/**
 * Normalize facet ID to canonical form (handles ATTRIBUTES → ATTRIBUTE, etc.)
 * Uses normalizeFacetIdToCanonical from facet-normalization.js if available,
 * otherwise falls back to local FACET_ID_ALIASES for backward compatibility.
 * @param {string} facetId - Facet ID to normalize
 * @returns {string} Normalized facet ID
 */
function normalizeFacetId(facetId) {
    if (!facetId) return facetId;
    
    // Use facet-normalization.js if available (single source of truth)
    if (typeof window !== 'undefined' && typeof window.normalizeFacetIdToCanonical === 'function') {
        return window.normalizeFacetIdToCanonical(facetId) || facetId;
    }
    
    // Fallback to local mapping for backward compatibility
    const upper = facetId.toString().toUpperCase().trim()
        .replace(/\s+/g, '_')
        .replace(/-/g, '_');
    return FACET_ID_ALIASES[upper] || upper;
}

/**
 * Canonical facet ID (Backend key: UPPERCASE with underscores)
 * Uses normalizeFacetIdToCanonical from facet-normalization.js if available.
 * @param {string} input - Facet ID from Unison API
 * @returns {string} Canonical facet ID
 */
function canonicalFacetId(input) {
    if (!input) return '';
    
    // Use facet-normalization.js if available (single source of truth)
    if (typeof window !== 'undefined' && typeof window.normalizeFacetIdToCanonical === 'function') {
        return window.normalizeFacetIdToCanonical(input) || '';
    }
    
    // Fallback to local normalization for backward compatibility
    let key = input.toString().toUpperCase().trim()
        .replace(/\s+/g, '_')
        .replace(/-/g, '_');
    
    // Map via aliases (uses normalizeFacetId)
    return normalizeFacetId(key);
}

/**
 * Convert facet ID to category slug
 * @param {string} facetId - Backend facet ID (e.g., "DATASET")
 * @returns {string|null} UI category slug (e.g., "data-sets"), or null if no mapping exists
 */
function facetIdToCategorySlug(facetId) {
    const canonical = canonicalFacetId(facetId);
    // ✅ Return null if no mapping exists (don't fallback to lowercase)
    return FACET_TO_CATEGORY[canonical] || null;
}

/**
 * Convert category slug to facet ID
 * @param {string} categorySlug - UI category slug (e.g., "data-sets")
 * @returns {string} Backend facet ID (e.g., "DATASET")
 */
function categorySlugToFacetId(categorySlug) {
    const canonical = canonicalCategoryKey(categorySlug);
    
    // Reverse lookup
    for (const [facetId, slug] of Object.entries(FACET_TO_CATEGORY)) {
        if (slug === canonical) {
            return facetId;
        }
    }
    
    // ✅ No fallback - return null if no mapping exists (strict mapping)
    console.warn(`[categorySlugToFacetId] No facet ID mapping found for category: ${categorySlug} (canonical: ${canonical}). Available mappings:`, FACET_TO_CATEGORY);
    return null;
}

// Track logged missing categories to prevent spam
const loggedMissingCategories = new Set();

// Track facets updated from Unison in current search cycle (to prevent cache overwrite)
const unisonUpdatedFacets = new Set();

// Pending counts queue (for when sidebar not yet rendered)
const pendingCounts = new Map();

// Search results only (single source of truth after search). Never mixed with preload.
const categoryCounts = new Map();

// Preload/baseline counts only. Never written by search. Never used as fallback when search is active.
const baselineCounts = new Map();

// "baseline" = only preload has run; "search" = search has run — preload must not override.
let facetCountSource = 'baseline';

/**
 * Single source for display: search wins, then baseline. Used for "X of Y" render.
 * @param {string} canonicalKey - Canonical category key
 * @returns {{ count: number, total: number }}
 */
function getDisplayCounts(canonicalKey) {
    const fromSearch = categoryCounts.get(canonicalKey);
    if (fromSearch && typeof fromSearch.count === 'number' && typeof fromSearch.total === 'number') {
        return { count: fromSearch.count, total: fromSearch.total };
    }
    const fromBaseline = baselineCounts.get(canonicalKey);
    if (fromBaseline && typeof fromBaseline.count === 'number' && typeof fromBaseline.total === 'number') {
        return { count: fromBaseline.count, total: fromBaseline.total };
    }
    return { count: 0, total: 0 };
}

/**
 * Denominator (Y) for "0 of Y" when a facet has no hits: prefer preload baseline, then last search total.
 * @param {string} canonicalKey - canonical category slug (e.g. role, data-sets)
 * @returns {number}
 */
function getFacetCountDenominator(canonicalKey) {
    if (!canonicalKey) return 0;
    const c = canonicalCategoryKey(canonicalKey);
    const base = baselineCounts.get(c);
    if (base && typeof base.total === 'number' && !isNaN(base.total) && base.total > 0) {
        return base.total;
    }
    if (base && typeof base.count === 'number' && !isNaN(base.count) && base.count > 0) {
        return base.count;
    }
    const sch = categoryCounts.get(c);
    if (sch && typeof sch.total === 'number' && !isNaN(sch.total) && sch.total > 0) {
        return sch.total;
    }
    return 0;
}

/**
 * Total to use when forcing count to 0 (avoids "0 of 0" when baseline exists).
 */
function resolveZeroFacetTotal(canonicalKey) {
    const d = getFacetCountDenominator(canonicalKey);
    if (d > 0) return d;
    const disp = getDisplayCounts(canonicalCategoryKey(canonicalKey));
    const t = disp && typeof disp.total === 'number' && !isNaN(disp.total) ? disp.total : 0;
    return t > 0 ? t : 0;
}

// ----- Unison Search diagnostics (for bug reports: console + dumpUnisonSearchDebugState) -----
function isUnisonSearchDebugEnabled() {
    try {
        if (typeof window === 'undefined') return false;
        if (window.__UNISON_SEARCH_DEBUG__ === true) return true;
        if (window.localStorage && window.localStorage.getItem('unisonSearchDebug') === '1') return true;
        return false;
    } catch (e) {
        return false;
    }
}

function unisonSearchDebugLog(phase, detail) {
    if (!isUnisonSearchDebugEnabled()) return;
    const ts = new Date().toISOString();
    console.log('[UnisonSearch ' + ts + '] [' + phase + ']', detail === undefined ? '' : detail);
}

function unisonSearchErrorLog(phase, detail) {
    const ts = new Date().toISOString();
    console.error('[UnisonSearch ' + ts + '] [ERROR][' + phase + ']', detail);
}

function snapshotCategoryCountsMap(mapRef) {
    if (!mapRef || typeof mapRef.forEach !== 'function') return null;
    const out = {};
    try {
        mapRef.forEach((v, k) => {
            if (v && typeof v === 'object') {
                out[k] = { count: v.count, total: v.total, lastSource: v.lastSource };
            } else {
                out[k] = v;
            }
        });
    } catch (e) {
        return { _error: String(e) };
    }
    return out;
}

/**
 * Run in DevTools: dumpUnisonSearchDebugState()
 * Copy the printed JSON when reporting a search/facet bug.
 */
function dumpUnisonSearchDebugState() {
    if (typeof window === 'undefined') {
        console.warn('dumpUnisonSearchDebugState: no window');
        return null;
    }
    const help = {
        verboseLogs: 'localStorage.setItem("unisonSearchDebug","1") then reload; disable: localStorage.removeItem("unisonSearchDebug")',
        orFlag: 'Or: window.__UNISON_SEARCH_DEBUG__ = true'
    };
    const o = {
        help,
        capturedAt: new Date().toISOString(),
        href: window.location && window.location.href,
        userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
        debugVerboseEnabled: isUnisonSearchDebugEnabled(),
        lastRequest: window.__lastUnisonSearchRequest || null,
        lastResponseMeta: window.__lastUnisonSearchResponseMeta || null,
        lastError: window.__lastUnisonSearchError || null,
        facetCountSource: typeof window.getFacetCountSource === 'function' ? window.getFacetCountSource() : null,
        categoryCounts: snapshotCategoryCountsMap(window.categoryCounts),
        searchConditions: window.searchConditions || null
    };
    console.info('[UnisonSearch] === dump (copy JSON below) ===');
    try {
        console.log(JSON.stringify(o, null, 2));
    } catch (e) {
        console.log(o);
    }
    console.info('[UnisonSearch] === end dump ===');
    return o;
}

if (typeof window !== 'undefined') {
    window.categoryCounts = categoryCounts;
    window.baselineCounts = baselineCounts;
    window.facetCountSource = facetCountSource;
    window.getFacetCountSource = () => facetCountSource;
    window.setFacetCountSource = (source) => { if (source === 'search' || source === 'baseline') facetCountSource = source; };
    window.getDisplayCounts = getDisplayCounts;
    window.getFacetCountDenominator = getFacetCountDenominator;
    window.resolveZeroFacetTotal = resolveZeroFacetTotal;
    window.pendingCounts = pendingCounts;
    window.canonicalCategoryKey = canonicalCategoryKey;
    window.canonicalFacetId = canonicalFacetId;
    window.normalizeFacetId = normalizeFacetId;
    window.facetIdToCategorySlug = facetIdToCategorySlug;
    window.categorySlugToFacetId = categorySlugToFacetId;
    window.FACET_TO_CATEGORY = FACET_TO_CATEGORY;
    window.unisonUpdatedFacets = unisonUpdatedFacets;
    window.applyPendingCounts = applyPendingCounts;
    window.isUnisonSearchDebugEnabled = isUnisonSearchDebugEnabled;
    window.unisonSearchDebugLog = unisonSearchDebugLog;
    window.unisonSearchErrorLog = unisonSearchErrorLog;
    window.dumpUnisonSearchDebugState = dumpUnisonSearchDebugState;
}

function getCurrentQuery() {
    const searchInput = document.querySelector('.search-main-input');
    return searchInput ? searchInput.value.trim() : '';
}

function getSearchSignature() {
    const selectedItem = typeof getSelectedItem === 'function' ? getSelectedItem() : null;
    return JSON.stringify({
        q: getCurrentQuery(),
        relatedId: selectedItem?.id || null,
        relatedCategory: selectedItem?.category || null
    });
}

/**
 * Mark category as updated from Unison (prevents cache overwrite)
 */
function markUnisonUpdated(category) {
    if (!category) return;
    const canonicalKey = canonicalCategoryKey(category);
    unisonUpdatedFacets.add(canonicalKey);
}

/**
 * Check if category was updated from Unison in current cycle
 */
function wasUnisonUpdated(category) {
    if (!category) return false;
    const canonicalKey = canonicalCategoryKey(category);
    return unisonUpdatedFacets.has(canonicalKey);
}

/**
 * Clear Unison update tracking (call at start of new search)
 */
function clearUnisonUpdates() {
    unisonUpdatedFacets.clear();
}

/**
 * Apply pending counts (call after sidebar is rendered)
 */
function applyPendingCounts() {
    if (pendingCounts.size === 0) {
        return;
    }
    
    const pending = Array.from(pendingCounts.entries());
    pendingCounts.clear();
    
    for (const [canonicalKey, data] of pending) {
        updateCategoryCount(canonicalKey, data.count, data.total, data.fromUnison);
    }
}

/**
 * Pure renderer: updates store and DOM from final (filteredCount, totalCount) only.
 * No fallback, no business logic, no cache inspection. Caller must pass correct values.
 * Invariant: 0 <= filteredCount <= totalCount.
 *
 * @param {string} category - The category key
 * @param {number} filteredCount - The filtered result count (X in "X of Y")
 * @param {number} totalCount - The segment-filtered total (Y in "X of Y")
 * @param {boolean} fromUnison - true = from search API (writes categoryCounts, sets source=search); false = preload (writes baselineCounts only, does not override if source=search)
 */
function updateCategoryCount(category, filteredCount, totalCount, fromUnison = false) {
    if (!category) return;

    const canonicalKey = canonicalCategoryKey(category);
    const isNumber = (v) => typeof v === 'number' && !isNaN(v);
    const c = isNumber(filteredCount) ? filteredCount : 0;
    const t = isNumber(totalCount) ? totalCount : 0;

    // Defensive: backend must never return count > totalCount
    if (c > t) {
        console.error('[Facet count] Facet count invariant violated: count > totalCount', { category: canonicalKey, count: c, totalCount: t });
    }
    const safeCount = Math.min(c, t);
    const safeTotal = Math.max(t, safeCount);

    if (fromUnison) {
        categoryCounts.set(canonicalKey, { count: safeCount, total: safeTotal, lastSource: 'unison', updatedAt: Date.now() });
        facetCountSource = 'search';
        if (typeof window !== 'undefined') window.facetCountSource = 'search';
    } else {
        baselineCounts.set(canonicalKey, { count: safeCount, total: safeTotal, updatedAt: Date.now() });
        if (facetCountSource === 'search') {
            return; // Never let preload overwrite search state
        }
    }

    // Pending queue when sidebar not yet rendered
    const allCategoryItems = document.querySelectorAll('.category-item');
    if (allCategoryItems.length === 0) {
        pendingCounts.set(canonicalKey, { count: safeCount, total: safeTotal, fromUnison });
        if (fromUnison) {
            categoryCounts.set(canonicalKey, { count: safeCount, total: safeTotal, lastSource: 'unison', updatedAt: Date.now() });
        }
        return;
    }

    const categoryItem = document.querySelector(`.category-item[data-category="${canonicalKey}"]`);
    if (!categoryItem) {
        return;
    }

    const display = getDisplayCounts(canonicalKey);
    let countEl = categoryItem.querySelector('.count');
    if (!countEl) {
        countEl = document.createElement('span');
        countEl.className = 'count';
        categoryItem.appendChild(countEl);
    }
    countEl.replaceChildren();
    const highlightFilteredNumerator = fromUnison && display.total > 0 && display.count < display.total;
    if (highlightFilteredNumerator) {
        countEl.classList.add('facet-count-filtered');
        const I18n = window.I18n;
        const ofText = (I18n && typeof I18n.t === 'function') ? (I18n.t('label.of') || 'of') : 'of';
        const fmt = (n) => (I18n && typeof I18n.toArabicIndicDigits === 'function')
            ? I18n.toArabicIndicDigits(n)
            : String(n);
        const num = document.createElement('span');
        num.className = 'facet-count-numerator';
        num.textContent = fmt(display.count);
        const sep = document.createElement('span');
        sep.className = 'facet-count-sep';
        sep.textContent = ` ${ofText} `;
        const den = document.createElement('span');
        den.className = 'facet-count-denominator';
        den.textContent = fmt(display.total);
        countEl.appendChild(num);
        countEl.appendChild(sep);
        countEl.appendChild(den);
    } else {
        countEl.classList.remove('facet-count-filtered');
        if (window.I18n && typeof window.I18n.formatFacetCountLabel === 'function') {
            countEl.textContent = window.I18n.formatFacetCountLabel(display.count, display.total);
        } else {
            const ofText = (window.I18n && typeof window.I18n.t === 'function') ? (window.I18n.t('label.of') || 'of') : 'of';
            countEl.textContent = `${display.count} ${ofText} ${display.total}`;
        }
    }
    countEl.style.display = 'inline-block';
}

function getActiveCategory() {
    const active = document.querySelector('.category-item.current, .category-item.active');
    return active ? active.getAttribute('data-category') : null;
}

function getActiveCategoryWithFallback() {
    // Read DOM directly — do NOT call global getActiveCategory() because
    // search-table.js overwrites it with a version that calls this function
    // again (infinite recursion / stack overflow).
    const activeEl = document.querySelector('.category-item.current, .category-item.active');
    const active = activeEl ? activeEl.getAttribute('data-category') : null;
    if (active) return active;

    // Fallback: البحث عن أول فئة متاحة في category-sidebar-container
    const sidebarContainer = document.querySelector('.category-sidebar-container');
    let firstCategory = null;
    
    if (sidebarContainer) {
        firstCategory = sidebarContainer.querySelector('.category-item');
    }
    
    // Fallback: if no container or no item in container, use first category-item in document
    if (!firstCategory) {
        firstCategory = document.querySelector('.category-item');
    }
    
    if (firstCategory) {
        const category = firstCategory.getAttribute('data-category');
        if (category) {
            // تعيينها كفئة نشطة
            setActiveCategory(category);
            return category;
        }
    }

    return 'dataset'; // fallback افتراضي
}

function setActiveCategory(category) {
    if (!category) return;
    const canonical = typeof canonicalCategoryKey === 'function'
        ? canonicalCategoryKey(category)
        : category;
    document.querySelectorAll('.category-item').forEach(i => {
        i.classList.remove('active', 'current');
    });
    const el = document.querySelector(`.category-item[data-category="${canonical}"]`);
    if (el) el.classList.add('active');
}

function categoryToModule(category) {
    // Handle null or undefined input
    if (!category) return null;

    // Clean and normalize the input
    const c = category.toString()
        .toLowerCase()
        .trim()
        .replace(/\s+/g, '-');  // Replace spaces with hyphens

    // Define a comprehensive mapping of all possible category variations
    const categoryMap = {
        // Dataset variations
        'dataset': 'dataset',
        'datasets': 'dataset',
        'data-set': 'dataset',
        'data-sets': 'dataset',
        'datasetentity': 'dataset',
        'data': 'dataset',

        // Attribute variations
        'attribute': 'attribute',
        'attributes': 'attribute',
        'attr': 'attribute',
        'field': 'attribute',
        'fields': 'attribute',
        'column': 'attribute',
        'columns': 'attribute',

        // System variations
        'system': 'system',
        'systems': 'system',

        // Glossary variations
        'glossary': 'glossary',
        'glossaries': 'glossary',

        // People variations
        'people': 'people',
        'person': 'people',
        'user': 'people',
        'users': 'people',
        'employee': 'people',
        'employees': 'people',
        'staff': 'people',

        // Interface variations
        'interface': 'interface',
        'interfaces': 'interface',

        // Org Unit variations
        'orgunit': 'orgunit',
        'org_unit': 'orgunit',
        'org_units': 'orgunit',
        'org-unit': 'orgunit',
        'org-units': 'orgunit',
        'orgunits': 'orgunit',
        'organization': 'orgunit',
        'organizations': 'orgunit',
        'department': 'orgunit',
        'departments': 'orgunit',
        'division': 'orgunit',
        'divisions': 'orgunit',

        // Role variations
        'role': 'role',
        'roles': 'role',

        // Active Tasks variations
        'active-tasks': 'activeTasks',
        'activetasks': 'activeTasks',
        'active-tasks': 'activeTasks',
        'tasks': 'activeTasks',
        'task': 'activeTasks',

        // Process variations
        'process': 'process',
        'processes': 'process',


        // Project variations
        'project': 'project',
        'projects': 'project',


        // Product variations
        'product': 'product',
        'products': 'product',
   

        // Policy variations
        'policy': 'policy',
        'policies': 'policy',

        // Legal Entity variations
        'legal-entity': 'legal-entity',
        'legal-entities': 'legal-entity',
        'legalentity': 'legal-entity',
        'legalentities': 'legal-entity',
        'legal': 'legal-entity',

        // Client variations
        'client': 'client',
        'clients': 'client',

        // Committee variations
        'committee': 'committee',
        'committees': 'committee',

        // Business Area variations
        'business-area': 'business-area',
        'businessarea': 'business-area',
        'business-areas': 'business-area',
        'businessareas': 'business-area',

        // Capability variations
        'capability': 'capability',
        'capabilities': 'capability',
        'competency': 'capability',
        'competencies': 'capability',

        // Geography variations
        'geography': 'geography',
        'geographies': 'geography',
        'location': 'geography',
        'locations': 'geography',
        'region': 'geography',
        'regions': 'geography',

        // Regulation variations
        'regulation': 'regulation',
        'regulations': 'regulation',
        'regulatory': 'regulation',

        // Regulator variations
        'regulator': 'regulator',
        'regulators': 'regulator',
        'regulatory-body': 'regulator',
        'regulatory-bodies': 'regulator',

        // Regulatory Theme variations
        'regulatory-theme': 'regulatory-theme',
        'regulatory-themes': 'regulatory-theme',
        'regulatorytheme': 'regulatory-theme',
        'regulatorythemes': 'regulatory-theme',

        // Change Requests variations
        'change-requests': 'change-requests',
        'changerequests': 'change-requests',
        'change-request': 'change-requests',
        'changerequest': 'change-requests'
    };

    // Look up the normalized module name
    return categoryMap[c] || c;
}

function getRefKey(row, category) {
    if (!row || typeof row !== 'object') return null;

    const fields = getCategoryFields(category);
    return fields.ref || null;
}

function normalizeColumnKey(category, rawKey) {
    // Keep original key as-is for dynamic display
    return rawKey;
}

// Mapping between JSON facet IDs and module display names
// IMPORTANT: These names must match EXACTLY the primaryName from the API modules endpoint
function facetIdToModuleName(facetId) {
    // Map facet IDs to translation keys
    const facetKeyMap = {
        'DATASET': 'facet.dataset',
        'ATTRIBUTE': 'facet.attribute',
        'SYSTEM': 'facet.system',
        'GLOSSARY': 'facet.glossary',
        'DATAQUALITY': 'facet.dataQuality',
        'PEOPLE': 'facet.people',
        'ROLE': 'facet.role',
        'BUSINESS_AREA': 'facet.businessArea',
        'LEGAL_ENTITY': 'facet.legalEntity',
        'CLIENT': 'facet.client',
        'COMMITTEE': 'facet.committee',
        'POLICY': 'facet.policy',
        'PROCESS': 'facet.process',
        'INTERFACE': 'facet.interface',
        'CAPABILITY': 'facet.capability',
        'PRODUCT': 'facet.product',
        'ORG_UNIT': 'facet.orgUnit',
        'GEOGRAPHY': 'facet.geography',
        'REGULATION': 'facet.regulation',
        'REGULATOR': 'facet.regulator',
        'REGULATORY_THEME': 'facet.regulatoryTheme',
        'ACTIVE_TASKS': 'facet.activeTasks',
        'PROJECT': 'facet.project',
        'CHANGE_REQUESTS': 'facet.changeRequests',
        'PHYSICAL_FIELDS': 'label.physicalFields'
    };

    const missingTranslations = {
        "facet.dataset": "Data Sets",
        "facet.attribute": "Attribute",
        "facet.system": "System",
        "facet.glossary": "Glossary",
        "facet.dataQuality": "Data Quality",
        "facet.people": "People",
        "facet.role": "Role",
        "facet.businessArea": "Business Area",
        "facet.legalEntity": "Legal Entity",
        "facet.client": "Client",
        "facet.committee": "Committee",
        "facet.policy": "Policy",
        "facet.process": "Process",
        "facet.interface": "Interface",
        "facet.capability": "Capability",
        "facet.product": "Product",
        "facet.orgUnit": "Org Unit",
        "facet.geography": "Geography",
        "facet.regulation": "Regulation",
        "facet.regulator": "Regulator",
        "facet.regulatoryTheme": "Regulatory Theme",
        "facet.activeTasks": "Active Tasks",
        "facet.changeRequests": "Change Requests",
        "facet.project": "Project"
    };
    
    // Fallback names if translation is not available (must match API module names)
    const fallbackNames = {
        'DATASET': 'Data Sets',
        'ATTRIBUTE': 'Attribute',
        'SYSTEM': 'System',
        'GLOSSARY': 'Glossary',
        'DATAQUALITY': 'Data Quality',
        'PEOPLE': 'People',
        'ROLE': 'Role',
        'BUSINESS_AREA': 'Business Area',
        'LEGAL_ENTITY': 'Legal Entity',
        'CLIENT': 'Client',
        'COMMITTEE': 'Committee',
        'POLICY': 'Policy',
        'PROCESS': 'Process',
        'INTERFACE': 'Interface',
        'CAPABILITY': 'Capability',
        'PRODUCT': 'Product',
        'ORG_UNIT': 'Org Unit',
        'GEOGRAPHY': 'Geography',
        'REGULATION': 'Regulation',
        'REGULATOR': 'Regulator',
        'REGULATORY_THEME': 'Regulatory Theme',
        'ACTIVE_TASKS': 'Active Tasks',
        'PROJECT': 'Project',
        'CHANGE_REQUESTS': 'Change Requests',
        'PHYSICAL_FIELDS': 'Physical Fields'
    };
    
    const translationKey = facetKeyMap[facetId];
    // Resolve without calling I18n.t() when JSON not loaded yet — t() logs "key not found" on localhost
    function resolveFacetLabel(key) {
        if (!key || !window.I18n || !window.I18n.translations) return null;
        const parts = key.split('.');
        let v = window.I18n.translations;
        for (const p of parts) {
            if (v && typeof v === 'object' && p in v) v = v[p];
            else return null;
        }
        return typeof v === 'string' ? v : null;
    }
    if (translationKey) {
        let translated = resolveFacetLabel(translationKey);
        if (!translated && window.I18n && window.I18n.defaultTranslations) {
            let v = window.I18n.defaultTranslations;
            const parts = translationKey.split('.');
            for (const p of parts) {
                if (v && typeof v === 'object' && p in v) v = v[p];
                else { v = null; break; }
            }
            if (typeof v === 'string') translated = v;
        }
        if (translated) return translated;
        if (missingTranslations[translationKey]) return missingTranslations[translationKey];
    }
    
    // Fallback to English name or facetId
    return fallbackNames[facetId] || facetId;
}

// Mapping between module display names and JSON facet IDs
function moduleNameToFacetId(moduleName) {
    if (!moduleName) return null;
    
    const normalized = moduleName.toString().trim().toUpperCase().replace(/\s+/g, '-');
    
    // Comprehensive mapping covering all possible formats
    const mapping = {
        // Dataset variations
        'DATA-SETS': 'DATASET',
        'DATASETS': 'DATASET',
        'DATASET': 'DATASET',
        'DATA-SET': 'DATASET',
        'DATASETENTITY': 'DATASET',
        
        // Attribute variations
        'ATTRIBUTES': 'ATTRIBUTE',
        'ATTRIBUTE': 'ATTRIBUTE',
        
        // System variations
        'SYSTEM': 'SYSTEM',
        'SYSTEMS': 'SYSTEM',
        
        // Glossary variations
        'GLOSSARY': 'GLOSSARY',
        'GLOSSARIES': 'GLOSSARY',
        
        // Data Quality variations
        'DATA-QUALITY': 'DATAQUALITY',
        'DATAQUALITY': 'DATAQUALITY',
        
        // People variations
        'PEOPLE': 'PEOPLE',
        'PERSON': 'PEOPLE',
        
        // Role variations
        'ROLE': 'ROLE',
        'ROLES': 'ROLE',
        
        // Business Area variations
        'BUSINESS-AREA': 'BUSINESS_AREA',
        'BUSINESSAREA': 'BUSINESS_AREA',
        'BUSINESS_AREA': 'BUSINESS_AREA',
        
        // Legal Entity variations
        'LEGAL-ENTITY': 'LEGAL_ENTITY',
        'LEGALENTITY': 'LEGAL_ENTITY',
        'LEGAL_ENTITY': 'LEGAL_ENTITY',
        
        // Client variations
        'CLIENT': 'CLIENT',
        'CLIENTS': 'CLIENT',
        
        // Committee variations
        'COMMITTEE': 'COMMITTEE',
        'COMMITTEES': 'COMMITTEE',
        
        // Policy variations
        'POLICY': 'POLICY',
        'POLICIES': 'POLICY',
        
        // Process variations
        'PROCESS': 'PROCESS',
        'PROCESSES': 'PROCESS',
        
        // Interface variations
        'INTERFACE': 'INTERFACE',
        'INTERFACES': 'INTERFACE',
        
        // Capability variations
        'CAPABILITY': 'CAPABILITY',
        'CAPABILITIES': 'CAPABILITY',
        
        // Product variations
        'PRODUCT': 'PRODUCT',
        'PRODUCTS': 'PRODUCT',
        
        // Org Unit variations
        'ORG-UNIT': 'ORG_UNIT',
        'ORGUNIT': 'ORG_UNIT',
        'ORG_UNIT': 'ORG_UNIT',
        
        // Geography variations
        'GEOGRAPHY': 'GEOGRAPHY',
        'GEOGRAPHIES': 'GEOGRAPHY',
        
        // Regulation variations
        'REGULATION': 'REGULATION',
        'REGULATIONS': 'REGULATION',
        
        // Regulator variations
        'REGULATOR': 'REGULATOR',
        'REGULATORS': 'REGULATOR',
        
        // Regulatory Theme variations
        'REGULATORY-THEME': 'REGULATORY_THEME',
        'REGULATORYTHEME': 'REGULATORY_THEME',
        'REGULATORY_THEME': 'REGULATORY_THEME',
        
        // Active Tasks variations
        'ACTIVE-TASKS': 'ACTIVE_TASKS',
        'ACTIVETASKS': 'ACTIVE_TASKS',
        'ACTIVE_TASKS': 'ACTIVE_TASKS',
        'ACTIVE-TASK': 'ACTIVE_TASKS',
        'ACTIVETASK': 'ACTIVE_TASKS',
        'TASKS': 'ACTIVE_TASKS',
        'TASK': 'ACTIVE_TASKS',
        
        // Change Requests variations
        'CHANGE-REQUESTS': 'CHANGE_REQUESTS',
        'CHANGEREQUESTS': 'CHANGE_REQUESTS',
        'CHANGE_REQUESTS': 'CHANGE_REQUESTS',
        'CHANGE-REQUEST': 'CHANGE_REQUESTS',
        'CHANGEREQUEST': 'CHANGE_REQUESTS',
        
        // Project variations
        'PROJECT': 'PROJECT',
        'PROJECTS': 'PROJECT',
        
        // Physical Fields variations
        'PHYSICAL-FIELDS': 'PHYSICAL_FIELDS',
        'PHYSICALFIELDS': 'PHYSICAL_FIELDS',
        'PHYSICAL_FIELDS': 'PHYSICAL_FIELDS',
        'PHYSICAL-FIELD': 'PHYSICAL_FIELDS',
        'PHYSICALFIELD': 'PHYSICAL_FIELDS'
    };
    
    let result = mapping[normalized];
    if (result && result !== normalized) {
        return result;
    }

    // Resolve by translated display label (AR/EN) so UI stays correct when label is Arabic etc.
    const label = moduleName.toString().trim();
    const byLabel = TRANSLATED_LABEL_TO_FACET_ID[label] || TRANSLATED_LABEL_TO_FACET_ID[label.toLowerCase()];
    if (byLabel) {
        return byLabel;
    }

    return mapping[normalized] || normalized;
}

/**
 * Any localized facet label -> canonical facet ID (for clicks/filters after i18n).
 * Keys = exact strings from en.json / ar.json facet.* values + common English API names.
 */
const TRANSLATED_LABEL_TO_FACET_ID = (function () {
    const m = {};
    function add(facetId, labels) {
        (labels || []).forEach(function (s) {
            if (s && typeof s === 'string') {
                m[s] = facetId;
                m[s.toLowerCase()] = facetId;
            }
        });
    }
    // English (API primaryName + JSON fallbacks)
    add('DATASET', ['Data Sets', 'Dataset', 'Datasets', 'Data Set']);
    add('ATTRIBUTE', ['Attribute', 'Attributes']);
    add('SYSTEM', ['System', 'Systems']);
    add('GLOSSARY', ['Glossary', 'Glossaries']);
    add('DATAQUALITY', ['Data Quality']);
    add('PEOPLE', ['People', 'Person']);
    add('ROLE', ['Role', 'Roles']);
    add('ORG_UNIT', ['Org Unit', 'Org Units', 'OrgUnit']);
    add('BUSINESS_AREA', ['Business Area', 'Business Areas']);
    add('LEGAL_ENTITY', ['Legal Entity', 'Legal Entities']);
    add('CLIENT', ['Client', 'Clients']);
    add('COMMITTEE', ['Committee', 'Committees']);
    add('POLICY', ['Policy', 'Policies']);
    add('PROCESS', ['Process', 'Processes']);
    add('INTERFACE', ['Interface', 'Interfaces']);
    add('CAPABILITY', ['Capability', 'Capabilities']);
    add('PRODUCT', ['Product', 'Products']);
    add('GEOGRAPHY', ['Geography', 'Geographies']);
    add('REGULATION', ['Regulation', 'Regulations']);
    add('REGULATOR', ['Regulator', 'Regulators']);
    add('REGULATORY_THEME', ['Regulatory Theme', 'Regulatory Themes']);
    add('ACTIVE_TASKS', ['Active Tasks', 'Active Task', 'Tasks', 'Task']);
    add('CHANGE_REQUESTS', ['Change Requests', 'Change Request']);
    add('PROJECT', ['Project', 'Projects']);
    add('PHYSICAL_FIELDS', ['Physical Fields', 'Physical Field']);
    // Arabic (ar.json facet.*)
    add('DATASET', ['مجموعات البيانات', 'مجموعة البيانات']);
    add('ATTRIBUTE', ['الخاصية', 'الخصائص']);
    add('SYSTEM', ['النظام', 'الأنظمة']);
    add('GLOSSARY', ['المسرد', 'المسارد']);
    add('DATAQUALITY', ['جودة البيانات']);
    add('PEOPLE', ['الأشخاص', 'الشخص']);
    add('ROLE', ['الدور', 'الأدوار']);
    add('ORG_UNIT', ['الوحدة التنظيمية', 'الوحدات التنظيمية']);
    add('BUSINESS_AREA', ['مجال العمل', 'مجالات العمل']);
    add('LEGAL_ENTITY', ['الكيان القانوني', 'الكيانات القانونية']);
    add('CLIENT', ['العميل', 'العملاء']);
    add('COMMITTEE', ['اللجنة', 'اللجان']);
    add('POLICY', ['السياسة', 'السياسات']);
    add('PROCESS', ['العملية', 'العمليات']);
    add('INTERFACE', ['الواجهة', 'الواجهات']);
    add('CAPABILITY', ['القدرة', 'القدرات']);
    add('PRODUCT', ['المنتج', 'المنتجات']);
    add('GEOGRAPHY', ['الجغرافيا']);
    add('REGULATION', ['التنظيم', 'التنظيمات']);
    add('REGULATOR', ['المنظم', 'المنظمون']);
    add('REGULATORY_THEME', ['الموضوع التنظيمي', 'المواضيع التنظيمية']);
    add('ACTIVE_TASKS', ['المهام النشطة', 'المهمة النشطة']);
    add('CHANGE_REQUESTS', ['طلبات التغيير', 'طلب التغيير']);
    add('PROJECT', ['المشروع', 'المشاريع']);
    return m;
})();

// Mapping between JSON field names and column display names
function fieldNameToColumnName(fieldName) {
    // Field names from JSON are already in display format (e.g., "Ref.", "Name")
    // But we need to handle variations
    const mapping = {
        'refNumber': 'Ref.',
        'ref': 'Ref.',
        'name': 'Name',
        'definition': 'Definition',
        'lifecycle': 'Lifecycle',
        'systemId': 'System Short Name',
        'systemName': 'System Short Name',
        'dataSetId': 'Data Set Name',
        'dataSetName': 'Data Set Name',
        'shortName': 'Short Name',
        'description': 'Description',
        'type': 'Type',
        'classification': 'Classification',
        'ciaRating': 'CIA Rating',
        'parentName': 'Parent Name',
        'parentType': 'Parent Type',
        'kde': 'KDE',
        'firstName': 'First Name',
        'lastName': 'Last Name',
        'email': 'Email',
        'function': 'Function',
        'orgUnit': 'Org Unit',
        'role': 'Role',
        'fullName': 'Full Name',
        'objectType': 'Object Type',
        'object': 'Object',
        'roleAccepted': 'Role Accepted',
        'parent': 'Parent',
        'parentShortName': 'Parent Short Name',
        'automation': 'Automation',
        'frequency': 'Frequency',
        'sourceSystemShortName': 'Source System Short Name',
        'targetSystemShortName': 'Target System Short Name',
        'budgStatus': 'BUDG Status'
    };
    
    // If already in display format, return as is
    if (fieldName.includes(' ') || fieldName.includes('.')) {
        return fieldName;
    }
    
    return mapping[fieldName] || fieldName;
}

// Mapping between column display names and JSON field names
function columnNameToFieldName(columnName) {
    const mapping = {
        'Ref.': 'refNumber',
        'Name': 'name',
        'Definition': 'definition',
        'Lifecycle': 'lifecycle',
        'System Short Name': 'systemName',
        'Data Set Name': 'dataSetName',
        'Short Name': 'shortName',
        'Description': 'description',
        'Type': 'type',
        'Classification': 'classification',
        'CIA Rating': 'ciaRating',
        'Parent Name': 'parentName',
        'Parent Type': 'parentType',
        'KDE': 'kde',
        'First Name': 'firstName',
        'Last Name': 'lastName',
        'Email': 'email',
        'Function': 'function',
        'Org Unit': 'orgUnit',
        'Role': 'role',
        'Full Name': 'fullName',
        'Object Type': 'objectType',
        'Object': 'object',
        'Role Accepted': 'roleAccepted',
        'Parent': 'parent',
        'Parent Short Name': 'parentShortName',
        'Automation': 'automation',
        'Frequency': 'frequency',
        'Source System Short Name': 'sourceSystemShortName',
        'Target System Short Name': 'targetSystemShortName',
        'BUDG Status': 'budgStatus'
    };
    return mapping[columnName] || columnName;
}

// Make mapping functions available globally
/**
 * Get display name for category (converts any category format to proper display name)
 * Examples: "attribute" -> "Attribute", "data-sets" -> "Data Sets", "ATTRIBUTE" -> "Attribute"
 */
function getCategoryDisplayName(category) {
    if (!category) {
        const unknownKey = 'label.unknown';
        if (window.I18n && typeof window.I18n.t === 'function') {
            const translated = window.I18n.t(unknownKey);
            return translated !== unknownKey ? translated : 'Unknown';
        }
        return 'Unknown';
    }
    
    // Normalize to lowercase with hyphens
    const normalized = category.toString()
        .toLowerCase()
        .trim()
        .replace(/\s+/g, '-');
    
    // Mapping from normalized category to translation key
    const categoryToTranslationKey = {
        // Dataset variations
        'dataset': 'facet.dataset',
        'datasets': 'facet.dataset',
        'data-set': 'facet.dataset',
        'data-sets': 'facet.dataset',
        'datasetentity': 'facet.dataset',
        'data': 'facet.dataset',
        
        // Attribute variations
        'attribute': 'facet.attribute',
        'attributes': 'facet.attribute',
        'attr': 'facet.attribute',
        'field': 'facet.attribute',
        'fields': 'facet.attribute',
        'column': 'facet.attribute',
        'columns': 'facet.attribute',
        
        // System variations
        'system': 'facet.system',
        'systems': 'facet.system',
        
        // Glossary variations
        'glossary': 'facet.glossary',
        'glossaries': 'facet.glossary',
        
        // Data Quality variations
        'data-quality': 'facet.dataQuality',
        'dataquality': 'facet.dataQuality',
        
        // People variations
        'people': 'facet.people',
        'person': 'facet.people',
        'user': 'facet.people',
        'users': 'facet.people',
        'employee': 'facet.people',
        'employees': 'facet.people',
        'staff': 'facet.people',
        
        // Role variations
        'role': 'facet.role',
        'roles': 'facet.role',
        
        // Business Area variations
        'business-area': 'facet.businessArea',
        'businessarea': 'facet.businessArea',
        'business_area': 'facet.businessArea',
        
        // Legal Entity variations
        'legal-entity': 'facet.legalEntity',
        'legalentity': 'facet.legalEntity',
        'legal_entity': 'facet.legalEntity',
        
        // Client variations
        'client': 'facet.client',
        'clients': 'facet.client',
        
        // Committee variations
        'committee': 'facet.committee',
        'committees': 'facet.committee',
        
        // Policy variations
        'policy': 'facet.policy',
        'policies': 'facet.policy',
        
        // Process variations
        'process': 'facet.process',
        'processes': 'facet.process',
        
        // Interface variations
        'interface': 'facet.interface',
        'interfaces': 'facet.interface',
        
        // Capability variations
        'capability': 'facet.capability',
        'capabilities': 'facet.capability',
        
        // Product variations
        'product': 'facet.product',
        'products': 'facet.product',
        
        // Org Unit variations
        'org-unit': 'facet.orgUnit',
        'orgunit': 'facet.orgUnit',
        'org_unit': 'facet.orgUnit',
        'organization-unit': 'facet.orgUnit',
        'organizationunit': 'facet.orgUnit',
        
        // Geography variations
        'geography': 'facet.geography',
        'geographies': 'facet.geography',
        
        // Regulation variations
        'regulation': 'facet.regulation',
        'regulations': 'facet.regulation',
        
        // Regulator variations
        'regulator': 'facet.regulator',
        'regulators': 'facet.regulator',
        
        // Regulatory Theme variations
        'regulatory-theme': 'facet.regulatoryTheme',
        'regulatorytheme': 'facet.regulatoryTheme',
        'regulatory_theme': 'facet.regulatoryTheme',
        
        // Active Tasks variations
        'active-tasks': 'facet.activeTasks',
        'activetasks': 'facet.activeTasks',
        'active_tasks': 'facet.activeTasks',
        'active-task': 'facet.activeTasks',
        'activetask': 'facet.activeTasks',
        'tasks': 'facet.activeTasks',
        'task': 'facet.activeTasks'
    };
    
    // Fallback display names if translation is not available
    const fallbackNames = {
        'dataset': 'Data Sets',
        'datasets': 'Data Sets',
        'data-set': 'Data Sets',
        'data-sets': 'Data Sets',
        'datasetentity': 'Data Sets',
        'data': 'Data Sets',
        'attribute': 'Attribute',
        'attributes': 'Attribute',
        'attr': 'Attribute',
        'field': 'Attribute',
        'fields': 'Attribute',
        'column': 'Attribute',
        'columns': 'Attribute',
        'system': 'System',
        'systems': 'System',
        'glossary': 'Glossary',
        'glossaries': 'Glossary',
        'data-quality': 'Data Quality',
        'dataquality': 'Data Quality',
        'people': 'People',
        'person': 'People',
        'user': 'People',
        'users': 'People',
        'employee': 'People',
        'employees': 'People',
        'staff': 'People',
        'role': 'Role',
        'roles': 'Role',
        'business-area': 'Business Area',
        'businessarea': 'Business Area',
        'business_area': 'Business Area',
        'legal-entity': 'Legal Entity',
        'legalentity': 'Legal Entity',
        'legal_entity': 'Legal Entity',
        'client': 'Client',
        'clients': 'Client',
        'committee': 'Committee',
        'committees': 'Committee',
        'policy': 'Policy',
        'policies': 'Policy',
        'process': 'Process',
        'processes': 'Process',
        'interface': 'Interface',
        'interfaces': 'Interface',
        'capability': 'Capability',
        'capabilities': 'Capability',
        'product': 'Product',
        'products': 'Product',
        'org-unit': 'Org Unit',
        'orgunit': 'Org Unit',
        'org_unit': 'Org Unit',
        'organization-unit': 'Org Unit',
        'organizationunit': 'Org Unit',
        'geography': 'Geography',
        'geographies': 'Geography',
        'regulation': 'Regulation',
        'regulations': 'Regulation',
        'regulator': 'Regulator',
        'regulators': 'Regulator',
        'regulatory-theme': 'Regulatory Theme',
        'regulatorytheme': 'Regulatory Theme',
        'regulatory_theme': 'Regulatory Theme',
        'active-tasks': 'Active Tasks',
        'activetasks': 'Active Tasks',
        'active_tasks': 'Active Tasks',
        'active-task': 'Active Tasks',
        'activetask': 'Active Tasks',
        'tasks': 'Active Tasks',
        'task': 'Active Tasks'
    };
    
    // Try to get translation
    const translationKey = categoryToTranslationKey[normalized];
    if (translationKey && window.I18n && typeof window.I18n.t === 'function') {
        const translated = window.I18n.t(translationKey);
        // If translation exists (doesn't return the key itself), use it
        if (translated !== translationKey) {
            return translated;
        }
    }
    
    // Fallback to English name or original category
    return fallbackNames[normalized] || category;
}

if (typeof window !== 'undefined') {
    window.facetIdToModuleName = facetIdToModuleName;
    window.moduleNameToFacetId = moduleNameToFacetId;
    // Single entry for sidebar + related UI: category slug or facet id -> localized label
    window.getFacetDisplayName = getCategoryDisplayName;
    window.fieldNameToColumnName = fieldNameToColumnName;
    window.columnNameToFieldName = columnNameToFieldName;
    window.getCategoryDisplayName = getCategoryDisplayName;
    window.markUnisonUpdated = markUnisonUpdated;
    window.wasUnisonUpdated = wasUnisonUpdated;
    window.clearUnisonUpdates = clearUnisonUpdates;
}

// ============================================================================
// USER AUTHENTICATION UTILITIES
// ============================================================================

/**
 * Check if user is logged in
 * @returns {boolean} True if user is logged in, false otherwise
 */
function isUserLoggedIn() {
    const userId = getCurrentUserId();
    return userId !== null && userId !== undefined;
}

/**
 * Get current user ID from session storage
 * @returns {number|null} User ID or null if not found
 */
function getCurrentUserId() {
    try {
        const userStr = sessionStorage.getItem('currentUser');
        if (userStr) {
            const user = JSON.parse(userStr);
            if (user && (user.id || user.ID || user.userId)) {
                return user.id || user.ID || user.userId;
            }
        }
    } catch (e) {
        console.error('[SEARCH-UTILS] Error parsing user from session:', e);
    }
    return null;
}

/**
 * Get current user reference ID (same as user ID in this system)
 * @returns {number|null} User reference ID or null if not found
 */
function getCurrentUserReference() {
    return getCurrentUserId();
}

// Expose to window for global access
if (typeof window !== 'undefined') {
    window.isUserLoggedIn = isUserLoggedIn;
    window.getCurrentUserId = getCurrentUserId;
    window.getCurrentUserReference = getCurrentUserReference;
}


