# Unison Search page — TestSprite / automation guide

This document describes the **BUDG Unison Search** UI served at **`/search.html`** (e.g. `http://localhost:8080/search.html`). In the codebase this feature is implemented under `assets/js/UnisonSearch/` and backed by `/api/unison/search` and related endpoints.

## Purpose

- **Unified search** across configurable **facets** (categories such as datasets, systems, people, glossary, etc.).
- Users build **compound queries** with operators **FIND**, **AND**, **OR**, **NOT**; narrow results with **filters**, **org unit** scope, and **search history**.
- Results appear in a **List** view (table) or **Dashboard** view (charts); optional geographic/map integration loads for relevant facets.

## Entry URL and deep links

| URL | Behavior |
|-----|----------|
| `http://localhost:8080/search.html` | Normal load; header injected via `main.js` / `header.js`. |
| `http://localhost:8080/search.html?q=<text>` | After DOM ready, fills `.search-main-input` with the query and programmatically clicks `.search-action-btn` after ~500 ms. |

## Page metadata

- **Document title:** `Search - BUDG`
- **Main landmark:** `<main class="search-main-content">`
- **Theme:** `data-theme` on `<html>` from `localStorage` key `budg-theme` (default `light`).

## Layout regions (high level)

1. **Header** — Empty `<header class="header"></header>` populated at runtime (navigation, user menu, etc.).
2. **Controls bar** — `.search-controls-bar` / `.search-controls-container`
   - **Operator:** `<select class="search-type-select">` — values: `find`, `and`, `or`, `not`.
   - **Query:** `<input type="text" class="search-main-input">`
   - **Condition stack:** `#searchCounter` (hidden until conditions exist), `#clearSearchBtn`, `#queryBuilderContainer`
   - **Org unit:** `.org-unit-btn` inside `.org-unit-dropdown`
   - **Filter:** `.filter-btn` opens `#filterPanel` (dialog)
   - **Search:** `.search-action-btn`
   - **History:** `.history-btn` inside `.history-dropdown`
3. **Category sidebar** — `#searchCategorySidebar` / `.category-sidebar`
   - **Add category:** `#module-container` (`.add-category-btn`)
   - **Selected tabs:** `#selected-modules` / `.selected-modules`
   - **Mobile:** `#mobileCategoryToggle` toggles sidebar visibility
4. **Content** — `.content-area`
   - **View tabs:** `.view-tab` with `data-view="list"` | `data-view="dashboard"`
   - **Bulk actions:** `.bulk-delete`, `.bulk-update`, `#bulkMigrateBtn` (when visible)
   - **Settings:** `#settingsBtn` → `#settingsDropdown` (Show defaults, Choose columns, Export submenus)
   - **Table:** `.data-table-wrapper` → `table.search-table` with sortable headers (`th.sortable`, `data-column`)

## Initial state and empty table

- Before a category is selected (or data loaded), the table body may show `.no-data-row` with message keyed by i18n (`message.selectCategory` — e.g. “Select a category from the sidebar to view data”).
- Initialization (`search-init.js`) waits for i18n, loads Unison column defaults, then may **auto-load the first category** if tabs already exist.

## Critical flows for tests

### 1. Select a facet (category)

- Interact with `.category-item` elements under the sidebar (attributes like `data-category` identify the facet, e.g. `dataset`, `people`).
- **Add Category** opens the module picker flow (see `modules.js` / related UI).

### 2. Run a text search

- Set operator in `.search-type-select` if not default FIND.
- Type in `.search-main-input`.
- Click `.search-action-btn` (or rely on `?q=` deep link).
- Multi-step queries: use counter / query builder (`#searchCounter`, `#queryBuilderContainer`) when visible.

### 3. Filters

- Open `.filter-btn` → `#filterPanel` (`role="dialog"`, `aria-modal="true"`).
- Add rows via `#filterAddNew`; apply with `#filterApplyFilters`; clear with `#filterClearAll`.
- Hierarchical facets may show `#hierarchicalFiltersSection` (parent/child relationship options).

### 4. List vs dashboard

- Click `.view-tab[data-view="list"]` or `.view-tab[data-view="dashboard"]`.
- Dashboard uses Chart.js (`chart.umd.min.js`, `search-dashboard.js`).

### 5. Row actions and navigation

- Table uses `.name-link`, `.status-badge`, and delegated click handling in `search-table.js` (detail/navigation behavior is facet-dependent).

### 6. Export and columns

- `#settingsBtn` → **Choose columns** (`#chooseColumnsBtn`, `#columnsCheckboxes`).
- **Export** PDF/Excel/CSV — People facet uses `#exportSubmenu`; other facets use `#exportSubmenuOtherFacets` (see `search-export.js`).

## Backend / API surfaces (for network assertions)

| Area | Typical path |
|------|----------------|
| Unison compound search | `POST /api/unison/search` (see `search-api.js` — `executeUnisonSearch`) |
| Filter field metadata | `GET /UnisonSearch/api/filter-fields?facetId=...` |
| Custom field metadata | `GET /api/custom-fields/metadata?facetId=...` |
| Filter value options | `GET /UnisonSearch/api/filter-values/{fieldId}?facetId=...` |
| People typeahead (filters) | `GET /UnisonSearch/api/people/search?query=...&limit=10` |
| Active tasks (special facet) | `GET /api/active-tasks` |
| Change requests (when facet applies) | `GET /api/changerequests` (and related) |

Exact payloads and responses follow the Java `UnisonSearchService` and front-end request builders.

## Dependencies and side effects

- **Auth:** `auth-error-handler.js`, session handling — unauthenticated users may be redirected or see errors; tests may need a logged-in session.
- **i18n:** `assets/i18n/i18n.js` and `head-locale.js` — labels use `data-i18n`; visible text may vary by locale.
- **Loading:** `loading-manager.js`, `loading-overlay.css`, `global-lock-ui.js` — expect loading overlays during fetches.
- **PWA:** `manifest.webmanifest` linked; not required for search logic.

## Suggested stable selectors for automation

Prefer **IDs** and **`data-*`** where present: `#searchCategorySidebar`, `.search-main-input`, `.search-action-btn`, `#filterPanel`, `#filterApplyFilters`, `.view-tab[data-view="list"]`, `table.search-table`, `th.sortable[data-column="name"]`.

Avoid brittle selectors on Font Awesome icons alone; pair with parent button roles or adjacent text spans where i18n allows.

## Source files (reference)

- Page: `src/main/webapp/search.html`
- Unison Search JS: `src/main/webapp/assets/js/UnisonSearch/*.js`
- Styles: `src/main/webapp/assets/css/search.css` (and shared `main.css`, etc.)
- Server: `src/main/java/com/example/unisonsearch/` (`UnisonSearchService`, controllers under the app’s servlet mappings)

---

*Generated for TestSprite and similar tools to plan journeys, assertions, and API stubs against the Unison Search experience at `search.html`.*

**See also:** `unison-search-testsprite-coverage.md` (full test matrix: every network call, E2E journeys, negative cases) and `unison-search-backend.md` (server APIs).
