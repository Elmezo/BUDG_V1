# Plan: Unison sidebar facet counts (unrelated zeros + Role denominator)

## Problem A (original)

After selecting an object from suggestions (or Enter with suggestions open), unrelated facets keep preload counts until the user clicks another facet. **Cause:** [`_loadRelatedCountsForItem`](d:/BUDG_V2/src/main/webapp/assets/js/UnisonSearch/search-input.js) only calls `updateCategoryCount` for facets present in the API response; it never zeros the rest.

**Fix:** After updating returned facets, iterate visible `.category-item` elements and set count to `0` for facets not in the normalized result key set, **skipping** the canonical `activeCategory` so the selected row’s local `1 of 1` (baseline) is not wiped.

**Denominator for those zeros (Problem A2 — user request):** Unrelated facets must show **`0 of Y`**, where **Y** is the segment/accessible **total** for that module — **not `0 of 0`**.

- Today, `getDisplayCounts` may return `total: 0` after search if [`updateCategoryCount`](d:/BUDG_V2/src/main/webapp/assets/js/UnisonSearch/search-utils.js) was last called with `totalCount === 0`, or if neither `categoryCounts` nor `baselineCounts` has a non-zero total for that key.
- Culprits to audit:
  - Synthesized empty facet payloads with `totalCount: 0` in [`executeMultiConditionSearch`](d:/BUDG_V2/src/main/webapp/assets/js/UnisonSearch/search-input.js) (~2618–2634).
  - [`freezeFacetResultShape`](d:/BUDG_V2/src/main/webapp/assets/js/UnisonSearch/search-input.js): `totalCount = Math.max(backendTotal, count)` collapses to **0** when the backend omits totals for missing facets.
  - Zeroing branches that use `preservedTotal` from `getDisplayCounts` when baseline was never populated for that category.
- **Implementation direction:** When applying `count = 0` for a facet not in results, resolve **Y** as: `baselineCounts.get(canonical).total` if > 0, else last known search total for that facet, else keep calling segment-aware preload (Problem B / `ModuleRowCountServlet`) so baseline always carries a real **Y**. Optionally have the search API return **per-facet `totalCount` even when `ids` is empty** so the client never invents `0` for the denominator.

---

## Problem B (new — Role: 425 → 1 of 29)

**Observed:** Before search, Role shows **425 of 425** (preload). After object search, Role shows **1 of 29**.

### Why the denominator changes

1. **425 (preload)**  
   [`ModuleRowCountServlet`](d:/BUDG_V2/src/main/java/com/example/budg_v2/ModuleRowCountServlet.java) / [`preloadModuleRowCounts`](d:/BUDG_V2/src/main/webapp/assets/js/modules.js) uses `COUNT(*)` on the module’s table (Roles), **not** segment-filtered and **not** aligned with Unison’s ROLE facet row model (`object_x_people` enrichment).

2. **29 (after search)**  
   [`computeAccessibleTotalCount`](d:/BUDG_V2/src/main/java/com/example/unisonsearch/service/UnisonSearchService.java) resolves the ROLE facet’s module to object type via [`moduleToObjectType`](d:/BUDG_V2/src/main/java/com/example/unisonsearch/service/UnisonSearchService.java): **`case "role" -> "People"`** (line ~2266).  
   Totals then come from [`SegmentAccessService.getAccessibleObjectIds`](d:/BUDG_V2/src/main/java/com/example/budg_v2/service/SegmentAccessService.java) + cube selection — i.e. **count of accessible People IDs**, not count of rows in the `role` / `object_role` table and not necessarily the same as “role assignment” rows.

So the sidebar mixes **three different meanings**: raw role-table rows (425), segment-filtered **People** count (29), and filtered hit count from graph enrichment (e.g. 1 `object_x_people`-backed row). That is why the total “changes” in a way that looks wrong.

### Fix directions (**chosen: segment-filtered universe**)

**User decision:** The denominator should reflect the **users’ segment / cube-filtered universe** (the “29-style” number from search), not the raw table preload (425).

**Implications**

1. **ROLE `totalCount` in search** may still need a **semantic fix**: today it uses `moduleToObjectType("role") -> "People"`, so “29” is a **People** accessible count, not necessarily the right universe for the Role facet UI. Align ROLE’s `computeAccessibleTotalCount` path with whatever entity best represents “roles visible under segment rules” (may still require a dedicated query or correct `segment_object_type`).

2. **Preload must match search:** Extend [`GET /api/module-row-count`](d:/BUDG_V2/src/main/java/com/example/budg_v2/ModuleRowCountServlet.java) (and [`preloadModuleRowCounts`](d:/BUDG_V2/src/main/webapp/assets/js/modules.js) if needed) so baseline “Y” uses the **same** user + segment logic as [`computeAccessibleTotalCount`](d:/BUDG_V2/src/main/java/com/example/unisonsearch/service/UnisonSearchService.java) / `SegmentAccessService`, per module. Then Role shows ~**29 of 29** (or correct segment total) **before** search, not 425 of 425.

---

## Suggested implementation order

1. **Frontend:** `_loadRelatedCountsForItem` zeroing pass (Problem A), using a **non-zero denominator** (baseline / last total / API), never `0 of 0` (Problem A2).  
2. **Frontend (optional but strong):** When synthesizing empty facet results or freezing shape, preserve or inject `totalCount` from backend or `baselineCounts`.  
3. **Backend (optional):** Include `totalCount` for every visible facet in [`/api/unison/search`](d:/BUDG_V2/src/main/java/com/example/unisonsearch/servlet/UnisonSearchApiServlet.java) responses even when `count === 0` (e.g. run `computeAccessibleTotalCount` for missing keys).  
4. **Backend:** Align ROLE facet `totalCount` with segment universe (fix `People` mismatch if still wrong for “Role” rows).  
5. **Backend:** [`ModuleRowCountServlet`](d:/BUDG_V2/src/main/java/com/example/budg_v2/ModuleRowCountServlet.java) segment-aware preload so **Y** is always defined before search.

## Files

- [`search-input.js`](d:/BUDG_V2/src/main/webapp/assets/js/UnisonSearch/search-input.js) — `_loadRelatedCountsForItem`  
- [`UnisonSearchService.java`](d:/BUDG_V2/src/main/java/com/example/unisonsearch/service/UnisonSearchService.java) — `moduleToObjectType`, `computeAccessibleTotalCount`, ROLE enrichment / `roleTotal`  
- [`SegmentAccessService.java`](d:/BUDG_V2/src/main/java/com/example/budg_v2/service/SegmentAccessService.java) — only if new accessible-ID logic is needed for ROLE / assignments  
- [`ModuleRowCountServlet.java`](d:/BUDG_V2/src/main/java/com/example/budg_v2/ModuleRowCountServlet.java) — **required:** segment-aware preload (user + cube) to match search denominators  

## Implementation todos

1. **Frontend:** Extend `_loadRelatedCountsForItem` to zero facets not in API results (skip canonical `activeCategory`); pass **Y** from `baselineCounts` or equivalent so label is **`0 of Y`**, not `0 of 0`.
2. **Frontend:** Fix paths that produce `0 of 0` (empty-result synthesis, `freezeFacetResultShape`, `updateAllFacets` zero branches) to preserve denominator from baseline or backend `totalCount`.
3. **Backend (optional):** Search response includes accessible `totalCount` for facets with zero hits.
4. **Backend:** Correct ROLE facet `totalCount` if `People` mapping is wrong for “Role” semantics under segments.
5. **Backend:** `ModuleRowCountServlet` (+ caller/auth): segment-filtered totals matching search so preload **Y** is always correct.

## References

- [docs/facet-count-investigation.md](d:/BUDG_V2/docs/facet-count-investigation.md) — preload vs search paths
