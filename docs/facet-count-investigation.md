# Facet Count Investigation: Unison Search Sidebar

> **Objective:** Trace the "X of Y" count displayed next to each facet in the Unison Search sidebar from backend origin to frontend render, and identify every code segment that modifies or influences it.

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Architecture Overview](#2-architecture-overview)
3. [Data Flow Summary](#3-data-flow-summary)
4. [Backend: How Counts Originate](#4-backend-how-counts-originate)
   - 4.1 [Preload Path (`/api/module-row-count`)](#41-preload-path-apimodule-row-count)
   - 4.2 [Search Path (`/api/unison/search`)](#42-search-path-apiunisonsearch)
   - 4.3 [`FacetResult` Model](#43-facetresult-model)
   - 4.4 [`populateFacetRows` — Row Hydration & Re-count](#44-populatefacetrows--row-hydration--re-count)
   - 4.5 [`applyAccessibleTotals` — Total Count Injection](#45-applyaccessibletotals--total-count-injection)
   - 4.6 [`computeAccessibleTotalCount` — Segment-Filtered Total](#46-computeaccessibletotalcount--segment-filtered-total)
   - 4.7 [`applyFacetPreferences` — User Config](#47-applyfacetpreferences--user-config)
5. [Frontend: How Counts Flow & Transform](#5-frontend-how-counts-flow--transform)
   - 5.1 [`preloadModuleRowCounts` — Initial Baseline](#51-preloadmodulerowcounts--initial-baseline)
   - 5.2 [`executeUnisonSearch` — Search API Call](#52-executeunisonsearch--search-api-call)
   - 5.3 [`updateAllFacets` — Post-Search Fan-Out](#53-updateallfacets--post-search-fan-out)
   - 5.4 [`loadCategoryDataFromUnisonResults` — Active Category Render](#54-loadcategorydatafromunisonresults--active-category-render)
   - 5.5 [`updateCategoryCount` — DOM Update (Single Source of Truth)](#55-updatecategorycount--dom-update-single-source-of-truth)
   - 5.6 [`window.categoryCounts` — Global Cache Map](#56-windowcategorycounts--global-cache-map)
6. [Complete Call Graph](#6-complete-call-graph)
7. [Key Variables & Their Roles](#7-key-variables--their-roles)
8. [Identified Issues & Root Cause Candidates](#8-identified-issues--root-cause-candidates)
9. [Recommendations](#9-recommendations)

---

## 1. Problem Statement

The sidebar in Unison Search shows a badge like **"41 of 41"** next to the **Policy** facet. The reported issue is that the **filtered count** (`41`) equals the **total count** (`41`), even when a search filter should have narrowed the results. The count may be stale, sourced from the wrong pipeline, or overwritten by a race condition.

---

## 2. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                             FRONTEND                                 │
│                                                                      │
│  ┌────────────────┐   ┌──────────────────┐   ┌────────────────────┐  │
│  │ preloadModule  │   │ executeUnison    │   │ updateAllFacets   │  │
│  │ RowCounts()    │──▶│ Search()         │──▶│ ()                │  │
│  │ (GET)          │   │ (POST)           │   │                    │  │
│  └───────┬────────┘   └──────┬───────────┘   └────────┬───────────┘  │
│          │                   │                         │             │
│          ▼                   ▼                         ▼             │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │               updateCategoryCount(category, count,          │    │
│  │                                   total, fromUnison)        │    │
│  │                    (search-utils.js:368)                     │    │
│  └──────────────────────────┬───────────────────────────────────┘    │
│                             │                                        │
│                             ▼                                        │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │            window.categoryCounts (Map)                       │    │
│  │            DOM: .category-item .count  →  "X of Y"          │    │
│  └──────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────┘
                              │
                   HTTP/REST  │
                              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                              BACKEND                                  │
│                                                                       │
│  ┌────────────────────┐         ┌──────────────────────────────┐     │
│  │ ModuleRowCount     │         │ UnisonSearchApiServlet       │     │
│  │ Servlet            │         │ (POST /api/unison/search)    │     │
│  │ (GET /api/module-  │         │                              │     │
│  │  row-count)        │         │  ┌─ UnisonSearchService      │     │
│  │                    │         │  │  .executeUnisonSearch()    │     │
│  │  COUNT(*) per      │         │  │                           │     │
│  │  module table      │         │  ├─ populateFacetRows()      │     │
│  │  (no segment       │         │  ├─ applyAccessibleTotals()  │     │
│  │   filter!)         │         │  └─ applyFacetPreferences()  │     │
│  └────────────────────┘         └──────────────────────────────┘     │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │   FacetResult { ids, count, totalCount, rows, depthById, ... } │ │
│  └──────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Flow Summary

The count takes two distinct paths:

| Path | Trigger | Backend Endpoint | Frontend Entry | What it Sets |
|------|---------|------------------|----------------|-------------|
| **Baseline / Preload** | Page load | `GET /api/module-row-count` | `preloadModuleRowCounts()` | `total` = raw table count (⚠️ no segment filter) |
| **Search / Filter** | User searches | `POST /api/unison/search` | `updateAllFacets()` → `updateCategoryCount()` | `count` = filtered hits; `total` = `computeAccessibleTotalCount` (segment-filtered) |

**The core problem:** The "Preload" path returns **un-segmented** totals (raw `COUNT(*)` from the database table). The "Search" path returns **segment-filtered** totals from `computeAccessibleTotalCount`. If the preload value gets used as the `total` when the search should have replaced it, the sidebar shows `"count of preloadTotal"` instead of `"count of segmentTotal"`.

---

## 4. Backend: How Counts Originate

### 4.1 Preload Path (`/api/module-row-count`)

**File:** `ModuleRowCountServlet.java` (lines 16–215)

This servlet runs on page load. It:
1. Queries the `module` table for all `primaryname` values.
2. Maps each module name to a table name via `formatTableName()`.
3. Runs `SELECT COUNT(*) FROM <table> WHERE <deletedCol> IS NULL`.
4. Returns JSON: `{ "rowCounts": [{ "moduleName": "Policies", "tableName": "policy", "rowCount": 41 }] }`.

**Critical observation:** This count is **NOT segment-filtered**. It counts all non-deleted rows regardless of user segment access. The code comment at `modules.js:63` confirms:
```js
// NOTE: These counts are NOT segment-filtered! They represent total counts across all segments.
// TODO: Backend should return segment-filtered counts based on user's selected segments
```

### 4.2 Search Path (`/api/unison/search`)

**File:** `UnisonSearchApiServlet.java` (lines 172–289)

The `doPost()` method:
1. Parses `UnisonSearchRequest` from the request body.
2. Determines `maxDepth` (default 1).
3. Routes to either `UnifiedSearchService` (for supported facets with `objectId`) or `UnisonSearchService`.
4. Calls `executeUnisonSearch(searches, maxDepth, userId)`.
5. Applies `applyFacetPreferences()` to filter by user visibility/active fields.
6. Serializes the `UnisonSearchResponse` as JSON.

**`UnisonSearchService.executeUnisonSearch()`** (lines 109–708):
This is the main search orchestrator. After processing all search conditions, it passes through a pipeline:

```java
// Line 598: Hydrate rows and recalculate counts
accumulatedResults = populateFacetRows(accumulatedResults, facetFilters);

// Line 603: Enrich with CRs and Active Tasks
accumulatedResults = enrichWithRelatedCRsAndTasks(accumulatedResults);

// Line 610: Deduplicate facet IDs (DATA_SETS → DATASET)
accumulatedResults = canonicalizeFacetResults(accumulatedResults);

// Line 620: Filter to single facet if maxDepth=0
accumulatedResults = filterSingleFacetExactKeyword(searches, accumulatedResults);

// Line 624: ★★★ Attach totalCount to every FacetResult ★★★
accumulatedResults = applyAccessibleTotals(accumulatedResults);
```

### 4.3 `FacetResult` Model

**File:** `FacetResult.java` (62 lines)

```java
public class FacetResult {
    private final Set<Integer> ids;        // Matched object IDs
    private final int count;               // rows.size() or ids.size()
    private final boolean hasActiveFilter;
    private final int totalCount;          // Total accessible objects (segment-filtered)
    private final Map<Integer, Integer> depthById;
    private final List<Map<String, Object>> rows;  // Full row data

    // Constructor (5-arg):
    public FacetResult(Set<Integer> ids, boolean hasActiveFilter,
                       Map<Integer, Integer> depthById,
                       List<Map<String, Object>> rows, int totalCount) {
        int rowCount = rows != null ? rows.size() : (ids != null ? ids.size() : 0);
        this.count = rowCount;                              // ← count = actual rows
        this.totalCount = totalCount >= 0 ? totalCount : rowCount;  // ← totalCount from param, fallback to rowCount
    }
}
```

**Key point:** `count` is always derived from `rows.size()` or `ids.size()` — it's the number of hydrated rows, not a separate field that can be set independently.

### 4.4 `populateFacetRows` — Row Hydration & Re-count

**File:** `UnisonSearchService.java` (lines 1597–1795)

This method takes the raw `FacetResult` (which has `ids` but no `rows`) and hydrates it:
1. For each facet, looks up the module name.
2. Calls `searchService.searchWithDefinition(module, defWithIds, accessCtx)` to fetch full rows.
3. Extracts `idsFromRows` = IDs actually returned by the query.
4. **Re-computes `total`** = `computeAccessibleTotalCount(facetId, idsFromRows.size())`.
5. Creates a **new** `FacetResult(idsFromRows, hasActiveFilter, trimmedDepth, rows, total)`.

**⚠️ Critical:** After `populateFacetRows`, the `count` (= `rows.size()`) may differ from the original `ids.size()` because:
- **Segment filtering** in `searchWithDefinition` removes IDs the user cannot access.
- Some IDs from graph traversal may have been soft-deleted.

### 4.5 `applyAccessibleTotals` — Total Count Injection

**File:** `UnisonSearchService.java` (lines 1936–1961)

This method runs **after** `populateFacetRows` and rebuilds each `FacetResult` with a fresh `totalCount`:

```java
private Map<String, FacetResult> applyAccessibleTotals(Map<String, FacetResult> results) {
    for (Map.Entry<String, FacetResult> entry : results.entrySet()) {
        String facetId = entry.getKey();
        FacetResult fr = entry.getValue();

        int filteredCount = fr.getRows() != null ? fr.getRows().size()
                : (fr.getIds() != null ? fr.getIds().size() : 0);
        int total = computeAccessibleTotalCount(facetId, filteredCount);

        FacetResult updated = new FacetResult(
                fr.getIds(), fr.isHasActiveFilter(), fr.getDepthById(),
                fr.getRows(), total);  // ← totalCount = segment-accessible total
        withTotals.put(facetId, updated);
    }
    return withTotals;
}
```

### 4.6 `computeAccessibleTotalCount` — Segment-Filtered Total

**File:** `UnisonSearchService.java` (lines 2117–2166)

This is the **definitive source** of the "Y" in "X of Y" for search results:

```java
private int computeAccessibleTotalCount(String facetId, int filteredCount) {
    String canonicalFacet = canonFacet(facetId);
    String module = facetIdToModuleName(canonicalFacet);
    String objectType = moduleToObjectType(module);

    // For Policy: objectType = "Policy"
    int total = SegmentAccessService.getAccessibleObjectIds(currentUserId, objectType).size();
    return total;
}
```

This queries the segment access table to count how many objects of type "Policy" the current user can see. This is the **correct total** for a segment-filtered view.

### 4.7 `applyFacetPreferences` — User Config

**File:** `UnisonSearchApiServlet.java` (lines 337–384)

After search results are computed, this method:
1. Retrieves user-specific facet configurations (`unisonFacetService.getFacetsForUser(userId)`).
2. Filters out facets where `active = false` (hidden by user).
3. Projects rows to only include `activeFields`.
4. **Does NOT modify counts** — it creates new `FacetResult` objects with the same `ids` and `depthById`.

**However:** It **does** re-create the `FacetResult` with `projectedRows`, which means `count` will now reflect `projectedRows.size()`. Since projection doesn't remove rows (only columns), the count should remain the same. This is **safe** in terms of counts.

---

## 5. Frontend: How Counts Flow & Transform

### 5.1 `preloadModuleRowCounts` — Initial Baseline

**File:** `modules.js` (lines 65–160)

Called on page load. Fetches `GET /api/module-row-count` and:
1. Parses the response into `{moduleName, rowCount}` pairs.
2. Normalizes the module name to a canonical category key via `normalizeModuleKeyForCounts()`.
3. Stores in `window.categoryCounts` as:
   ```js
   { count: rowCount, total: rowCount, fromModuleApi: true, preliminary: true }
   ```
4. Then calls `updateCategoryCount(key, count, total, false)` for each visible module.

**⚠️ Critical issue:** `count` and `total` are **both** set to the raw table `rowCount`. This means:
- On initial load, every facet shows `"41 of 41"` (e.g., for Policy with 41 rows).
- These counts are **NOT segment-filtered**.
- They are flagged `preliminary: true` and `fromModuleApi: true`.

### 5.2 `executeUnisonSearch` — Search API Call

**File:** `search-api.js` (lines 727–781)

Simple `fetch` wrapper that calls `POST /api/unison/search` and returns the raw JSON response. Does **not** modify counts.

The returned response structure (from `UnisonSearchResponse` serialized by Gson):
```json
{
  "success": true,
  "results": {
    "POLICY": {
      "ids": [1, 2, 3],
      "count": 3,
      "totalCount": 41,
      "hasActiveFilter": true,
      "depthById": { "1": 0, "2": 1, "3": 1 },
      "rows": [ { "ID": 1, "Ref.": "POL-001", "Name": "...", ... }, ... ]
    },
    ...
  },
  "searchCounter": 1,
  "executionTimeMs": 150
}
```

### 5.3 `updateAllFacets` — Post-Search Fan-Out

**File:** `search-input.js` (lines 1906–2310+)

This is the **primary orchestrator** for updating all sidebar counts after a search. It:

1. **Normalizes** facet IDs (DATA_SETS → DATASET) and **merges** duplicate results.
2. Iterates over all facets **in search results** (lines 2130–2189):
   ```js
   const searchResultCount = facetResult.count !== undefined ? facetResult.count : 0;
   
   let baseTotal = undefined;
   if (facetResult.total !== undefined && typeof facetResult.total === 'number') {
       baseTotal = facetResult.total;  // ← Backend totalCount
   } else {
       // Fallback to cache
       const cached = window.categoryCounts.get(category.toLowerCase());
       if (cached && typeof cached.total === 'number') {
           baseTotal = cached.total;
       }
   }
   
   updateCategoryCount(canonicalCategory, searchResultCount, baseTotal, true);
   ```
   
   **⚠️ CRITICAL BUG CANDIDATE:** The code reads `facetResult.total` but the backend serializes it as `totalCount` (the field name in `FacetResult.java`). **Gson serializes Java field names as-is**, so the JSON will have `"totalCount"`, not `"total"`. This means `facetResult.total` will be `undefined` and the code falls through to the cache fallback, which may hold the **un-segmented preload value**.

3. Iterates over all **visible sidebar categories NOT in results** (lines 2204–2246):
   - If there is an active search and this facet is not in results, sets count to `0`.
   - Otherwise, refreshes from cache: `updateCategoryCount(canonicalCategory, cVal, tVal, true)`.

4. Iterates over **facets in search conditions but not in results** (lines 2264–2310):
   - Sets count to `0` with cached base total preserved.

### 5.4 `loadCategoryDataFromUnisonResults` — Active Category Render

**File:** `search-input.js` (lines 346–900)

Called when the user clicks on a facet in the sidebar. This function:
1. Extracts `facetResult` for the clicked category from `currentUnisonSearchResults`.
2. Checks if rows have correct field names; if not, calls `fetchFacetDataByIds()` for a full data fetch.
3. Updates the count (line 797–825):
   ```js
   const countToUse = (data && data.length > 0)
       ? data.length  // ★ Uses actual displayed row count
       : (facetResult.count !== undefined ? facetResult.count : 0);

   // For total, tries cache first, then facetResult.total
   let baseTotalForCategory = undefined;
   if (isActiveTasks && facetResult && typeof facetResult.total === 'number') {
       baseTotalForCategory = facetResult.total;
   } else {
       const cached = window.categoryCounts.get(category.toLowerCase());
       if (cached && typeof cached.total === 'number') {
           baseTotalForCategory = cached.total;
       }
   }
   if (baseTotalForCategory === undefined) {
       baseTotalForCategory = facetResult.total !== undefined ? facetResult.total : countToUse;
   }

   updateCategoryCount(category, countToUse, baseTotalForCategory, true);
   ```

   **⚠️ Issue:** Again uses `facetResult.total` which is likely `undefined` (should be `facetResult.totalCount`). Falls back to cache, which holds the preload value.

### 5.5 `updateCategoryCount` — DOM Update (Single Source of Truth)

**File:** `search-utils.js` (lines 356–456)

This is the **single function** that writes to the DOM. Every frontend path converges here.

```js
function updateCategoryCount(category, count, total, fromUnison = false) {
    const canonicalKey = canonicalCategoryKey(category);
    const cachedData = categoryCounts.get(canonicalKey) || {};

    // Determine baseTotal:
    // - If fromUnison: prefer provided total, fallback to cache
    // - If preload: prefer provided total, fallback to cache
    let baseTotal;
    if (fromUnison) {
        if (isNumber(total))       baseTotal = total;
        else if (isNumber(cachedData.total)) baseTotal = cachedData.total;
        else                       baseTotal = 0;
    } else {
        if (isNumber(total))       baseTotal = total;
        else if (isNumber(cachedData.total)) baseTotal = cachedData.total;
        else                       baseTotal = 0;
    }

    // Determine filteredCount:
    let filteredCount = isNumber(count) ? count : baseTotal;

    // Update cache
    categoryCounts.set(canonicalKey, {
        count: filteredCount,
        total: baseTotal,
        fromUnison,
        lastSource: fromUnison ? "unison" : "baseline",
        updatedAt: Date.now()
    });

    // Update DOM
    let countEl = categoryItem.querySelector('.count');
    const showText = `${filteredCount} of ${baseTotal}`;
    countEl.textContent = showText;
}
```

### 5.6 `window.categoryCounts` — Global Cache Map

A `Map<string, object>` available globally. Each entry:
```js
{
    count: number,         // Filtered / displayed count
    total: number,         // Base total (hopefully segment-filtered, but may be raw)
    fromUnison: boolean,   // Was last updated from search results?
    fromModuleApi: boolean, // Was loaded from /api/module-row-count?
    preliminary: boolean,  // Was set by preload (may be incorrect)?
    fromSegmentFilter: boolean,
    lastSource: "unison" | "baseline",
    updatedAt: number      // Timestamp
}
```

---

## 6. Complete Call Graph

```
Page Load
  └─ modules.js: preloadModuleRowCounts()
       ├─ GET /api/module-row-count
       │    └─ ModuleRowCountServlet.doGet()
       │         ├─ formatTableName("Policies") → "policy"
       │         ├─ getTableRowCount("policy") → SELECT COUNT(*) WHERE DeletedDatetime IS NULL
       │         └─ Returns: { rowCount: 41 }
       │
       ├─ window.categoryCounts.set("policy", { count: 41, total: 41, preliminary: true })
       └─ updateCategoryCount("policy", 41, 41, false)
            └─ DOM: "41 of 41"

User Performs Search
  └─ search-input.js: performSearch()
       └─ search-api.js: executeUnisonSearch(searches, options)
            └─ POST /api/unison/search
                 └─ UnisonSearchApiServlet.doPost()
                      └─ UnisonSearchService.executeUnisonSearch(searches, maxDepth, userId)
                           ├─ executeSingleSearch("POLICY", keyword, filters)
                           │    └─ Returns Set<Integer> seedIds = {1, 2, 3}
                           │
                           ├─ graphTraversalService.findConnectedObjects(...)
                           │    └─ Returns Map<String, FacetResult> with ids per facet
                           │
                           ├─ populateFacetRows(accumulatedResults, facetFilters)
                           │    ├─ searchService.searchWithDefinition("policy", def, accessCtx)
                           │    │    └─ Returns List<Map> rows (segment-filtered!)
                           │    ├─ count = rows.size() = 3
                           │    └─ total = computeAccessibleTotalCount("POLICY", 3) = 41
                           │
                           ├─ applyAccessibleTotals(accumulatedResults)
                           │    └─ Re-wraps FacetResult with totalCount from computeAccessibleTotalCount
                           │
                           └─ Response JSON:
                                { results: { POLICY: { count: 3, totalCount: 41, ... } } }

       └─ search-input.js: updateAllFacets(result, activeCategory)
            ├─ facetResult.count = 3           ✅ correct
            ├─ facetResult.total = undefined   ⚠️ backend sent "totalCount", not "total"
            │
            ├─ Fallback: cache.total = 41      ★ from preload (un-segmented!)
            │    (may also be correct if preload total == segment total, e.g. SuperAdmin)
            │
            └─ updateCategoryCount("policy", 3, 41, true)
                 └─ DOM: "3 of 41"   ✅ (if preload and segment totals happen to match)
```

---

## 7. Key Variables & Their Roles

| Variable | Location | Meaning |
|----------|----------|---------|
| `FacetResult.count` | Backend model | = `rows.size()` or `ids.size()` (auto-computed in constructor) |
| `FacetResult.totalCount` | Backend model | Segment-filtered total from `computeAccessibleTotalCount()` |
| `facetResult.count` | Frontend JSON | Deserialized `count` from backend response — **filtered hit count** |
| `facetResult.totalCount` | Frontend JSON | Deserialized `totalCount` from backend — **segment-filtered total** |
| `facetResult.total` | Frontend code | **⚠️ Used in code but does NOT exist in JSON response** (should be `totalCount`) |
| `window.categoryCounts[key].count` | Frontend cache | Last-written filtered count |
| `window.categoryCounts[key].total` | Frontend cache | Last-written base total |
| `filteredCount` (in `updateCategoryCount`) | Frontend | The "X" in "X of Y" |
| `baseTotal` (in `updateCategoryCount`) | Frontend | The "Y" in "X of Y" |

---

## 8. Identified Issues & Root Cause Candidates

### Issue 1: **Field Name Mismatch (`total` vs `totalCount`)**
- **Severity:** 🔴 HIGH
- **Location:** `search-input.js` lines 2158, 820; `FacetResult.java` line 15
- **Description:** The backend serializes the total as `totalCount` (Java field name). The frontend reads `facetResult.total` which is `undefined`. This causes **every fallback to the cache**, which holds the preload (un-segmented) value.
- **Evidence:**
  ```java
  // FacetResult.java:
  private final int totalCount;  // ← Gson serializes as "totalCount"
  ```
  ```js
  // search-input.js:2158
  if (facetResult.total !== undefined && ...) {  // ← Always fails! Should be facetResult.totalCount
      baseTotal = facetResult.total;
  }
  ```
- **Impact:** The "Y" in "X of Y" always comes from the preload cache, never from the backend's segment-filtered calculation.

### Issue 2: **Preload Counts Are NOT Segment-Filtered**
- **Severity:** 🟡 MEDIUM (mitigated if Issue 1 is fixed)
- **Location:** `ModuleRowCountServlet.java` lines 68–85
- **Description:** The `getTableRowCount()` method runs a raw `SELECT COUNT(*) FROM table WHERE deleted IS NULL`. It has no join to the segment access table. This means the preload counts include objects the user cannot access.
- **Impact:** If the frontend falls back to preload totals (due to Issue 1), the denominator will be inflated for non-SuperAdmin users.

### Issue 3: **Race Condition: Preload Overwrites Search Results**
- **Severity:** 🟡 MEDIUM
- **Location:** `modules.js` lines 142–156
- **Description:** After updating from a search (`fromUnison = true, lastSource = "unison"`), `preloadModuleRowCounts` skips keys where `lastSource === "unison"`. However, if preload runs **before** the search completes, the preload value is set with `preliminary: true`. The search then uses this preload value as the fallback total (due to Issue 1).
- **Sequence:**
  1. Page loads → preload sets `{ count: 41, total: 41 }` for Policy.
  2. User searches → `updateAllFacets` reads `facetResult.total` → `undefined`.
  3. Falls back to cache → `41` (un-segmented preload value).
  4. Sets count properly to `3`, but total stays `41`.
  5. DOM shows "3 of 41" — may be correct accidentally but is wrong for non-SuperAdmin users.

### Issue 4: **`loadCategoryDataFromUnisonResults` Uses `data.length` as Count**
- **Severity:** 🟢 LOW
- **Location:** `search-input.js` lines 797–798
- **Description:** When the user clicks on a facet, this function uses `data.length` (number of displayed rows) as the count rather than `facetResult.count`. These could differ if:
  - `fetchFacetDataByIds()` returns more/fewer rows than `facetResult.count`.
  - Rows were added/removed during field validation (role transformation, etc.).
- **Impact:** Minor inconsistency: the count might momentarily flicker between `facetResult.count` and `data.length`.

### Issue 5: **`applyAccessibleTotals` Runs AFTER `populateFacetRows` — Double Computation**
- **Severity:** 🟢 LOW
- **Location:** `UnisonSearchService.java` lines 598, 624
- **Description:** `populateFacetRows` already calls `computeAccessibleTotalCount` at line 1763. Then `applyAccessibleTotals` calls it again at line 1950. This is redundant but not incorrect — the second call simply overwrites the first with the same value.
- **Impact:** Minor performance waste (extra DB call per facet), no functional impact.

---

## 9. Recommendations

### Fix 1: Correct the field name mismatch (CRITICAL)

In all frontend locations that read `facetResult.total`, change to `facetResult.totalCount`:

**Files to modify:**
- `search-input.js` — `updateAllFacets()` (line 2158)
- `search-input.js` — `loadCategoryDataFromUnisonResults()` (lines 406, 409, 740, 804, 820, 860, 863)

Example fix:
```js
// BEFORE:
if (facetResult.total !== undefined && typeof facetResult.total === 'number') {
    baseTotal = facetResult.total;
}

// AFTER:
if (facetResult.totalCount !== undefined && typeof facetResult.totalCount === 'number') {
    baseTotal = facetResult.totalCount;
}
```

### Fix 2: Make `/api/module-row-count` segment-aware

Modify `ModuleRowCountServlet` to accept a `userId` parameter and compute segment-filtered counts, mirroring the logic in `computeAccessibleTotalCount`. This ensures preload totals match search totals.

### Fix 3: Add backend field aliasing (Optional)

Add a Gson `@SerializedName("total")` annotation to `FacetResult.totalCount` so the frontend sees `"total"` in the JSON:
```java
@SerializedName("total")
private final int totalCount;
```
This is an alternative to Fix 1 but changes the API contract.

---

## Appendix: File Index

| File | Path | Role |
|------|------|------|
| `ModuleRowCountServlet.java` | `src/main/java/com/example/budg_v2/ModuleRowCountServlet.java` | Preload counts endpoint |
| `UnisonSearchApiServlet.java` | `src/main/java/com/example/unisonsearch/servlet/UnisonSearchApiServlet.java` | Search API entry point |
| `UnisonSearchService.java` | `src/main/java/com/example/unisonsearch/service/UnisonSearchService.java` | Core search orchestrator |
| `FacetResult.java` | `src/main/java/com/example/unisonsearch/model/FacetResult.java` | Result model with `count` + `totalCount` |
| `UnisonSearchResponse.java` | `src/main/java/com/example/unisonsearch/model/UnisonSearchResponse.java` | Response wrapper |
| `modules.js` | `src/main/webapp/assets/js/modules.js` | Preload frontend logic |
| `search-input.js` | `src/main/webapp/assets/js/UnisionSearch/search-input.js` | Search UI + facet update orchestration |
| `search-utils.js` | `src/main/webapp/assets/js/UnisionSearch/search-utils.js` | `updateCategoryCount()` + category normalization |
| `search-api.js` | `src/main/webapp/assets/js/UnisionSearch/search-api.js` | `executeUnisonSearch()` API wrapper |
