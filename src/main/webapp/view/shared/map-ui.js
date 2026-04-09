/**
 * map-ui.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Unified UI layer for the map system.
 *
 * Defines (single bundle — do not load separate overlay-panel / overlay-columns):
 *   • window.MapOverlayPanel
 *   • window.OverlayColumns
 *   • window.SharedMapHTML + window.SharedMapDropdowns
 *   • window.SharedMapLayoutRichControlsHtml + window.SharedMapSpacingEdgeControlsHtml
 *
 * Load map-ui.js once before map-engine / facet map scripts.
 */

// =============================================================================
// SECTION 1 – MapOverlayPanel
// Source: overlay-panel.js
// =============================================================================
(function () {
    'use strict';

    /**
     * Global overlay panel for all maps (glossary-style layout).
     * - Header (title), body (scrollable), footer with "Page X of Y" and prev/next.
     * - For overlay type "stakeholders": table with columns Name / Role, Accepted, Org Unit.
     * - For other overlay types: list of items.
     * Usage: MapOverlayPanel.create(overlayType, data, nodeId, callbacks)
     * callbacks: { getTitle(overlayType), getItemText(overlayType, item), getItemId(overlayType, item),
     *              escapeHtml(str), onItemClick(panel, el)?, overlayColumnDefs?,
     *              getItemField(overlayType, item, fieldId)? }
     */

    const OVERLAY_PAGE_SIZE = 10;

    function defaultEscapeHtml(str) {
        if (str == null) return '';
        const div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    /** Normalize stakeholder item for Name/Role, Accepted, Org Unit (API may return PascalCase) */
    function normalizeStakeholderItem(item) {
        const personName = (item.personName || item.PersonName || item.name || item.Name || '').toString().trim();
        const roleName   = (item.roleName   || item.RoleName   || item.role || item.Role || '').toString().trim();
        const nameRole   = personName ? (roleName ? `${personName} (${roleName})` : personName) : (roleName || '—');
        const accepted   = (item.accepted || item.Accepted || item.acceptedStatus || item.RoleAccepted || '').toString().trim() || '—';
        const orgUnit    = (item.orgUnit || item.OrgUnit || item.orgUnitName || item.OrgUnitName || item.organizationUnit || item.OrganizationUnit || '').toString().trim() || '—';
        return { nameRole, accepted, orgUnit };
    }

    /**
     * Create a glossary-style overlay panel.
     * @param {string}   overlayType - e.g. 'stakeholders', 'description', 'glossary'
     * @param {Array}    data        - array of overlay items
     * @param {string}   nodeId      - node id for data-node-id
     * @param {Object}   callbacks   - getTitle, getItemText, getItemId, escapeHtml, onItemClick?,
     *                                 overlayColumnDefs?, getItemField?
     * @returns {HTMLElement|null}   panel element (null when data is empty)
     */
    function createOverlayPanel(overlayType, data, nodeId, callbacks) {
        const getTitle          = callbacks.getTitle          || (() => overlayType);
        const getItemText       = callbacks.getItemText       || (() => '');
        const getItemId         = callbacks.getItemId         || (() => null);
        const escapeHtml        = callbacks.escapeHtml        || defaultEscapeHtml;
        const onItemClick       = callbacks.onItemClick       || (() => {});
        const overlayColumnDefs = callbacks.overlayColumnDefs || [];
        const getItemField      = callbacks.getItemField      || (() => '');

        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return null;

        const panel = document.createElement('div');
        panel.className = 'map-node-overlay-panel';
        panel.setAttribute('data-node-id', nodeId);
        panel._overlayData     = arr;
        panel._overlayType     = overlayType;
        panel._overlayPage     = 1;
        panel._overlayNodeId   = nodeId;
        panel._overlayFilter   = '';
        panel._overlayMaxItems = 0;

        panel.style.cssText = [
            'position: absolute;',
            'background: var(--card-bg, #ffffff);',
            'border: 1px solid var(--border-color, #e5e7eb);',
            'border-radius: 8px;',
            'box-shadow: 0 4px 12px rgba(0,0,0,0.15);',
            'min-width: 200px; max-width: 300px; max-height: 400px;',
            'overflow: hidden; pointer-events: auto; z-index: 61;',
            'display: flex; flex-direction: column;'
        ].join(' ');

        // ── Header ──
        const header = document.createElement('div');
        header.className = 'map-node-overlay-header';
        header.style.cssText = [
            'padding: 8px 12px;',
            'background: var(--background-secondary, #f8fafc);',
            'border-bottom: 1px solid var(--border-color, #e5e7eb);',
            'font-weight: 600; font-size: 12px;',
            'display: flex; justify-content: space-between; align-items: center;'
        ].join(' ');
        const titleSpan = document.createElement('span');
        titleSpan.textContent = getTitle(overlayType);
        header.appendChild(titleSpan);

        const gridBtn = document.createElement('button');
        gridBtn.type      = 'button';
        gridBtn.innerHTML = '<i class="fas fa-th"></i>';
        gridBtn.style.cssText = 'border:none;background:transparent;cursor:pointer;padding:2px 4px;font-size:12px;color:var(--text-secondary,#6b7280);';
        gridBtn.title = 'Filter by name and set max objects';
        header.appendChild(gridBtn);
        panel.appendChild(header);

        // ── Settings bar (filter + max-items) ──
        var settingsBar = document.createElement('div');
        settingsBar.className    = 'map-overlay-settings-bar';
        settingsBar.style.display = 'none';

        var filterRow   = document.createElement('div');
        filterRow.style.cssText = 'position:relative;margin-bottom:6px;';
        var filterInput = document.createElement('input');
        filterInput.type        = 'text';
        filterInput.placeholder = 'Filter entries...';
        filterInput.style.cssText = 'width:100%;box-sizing:border-box;padding:4px 8px 4px 26px;border:1px solid var(--border-color,#d1d5db);border-radius:4px;font-size:11px;outline:none;';
        var searchIcon = document.createElement('i');
        searchIcon.className  = 'fas fa-search';
        searchIcon.style.cssText = 'position:absolute;left:8px;top:50%;transform:translateY(-50%);font-size:10px;color:var(--text-secondary,#9ca3af);pointer-events:none;';
        filterRow.appendChild(searchIcon);
        filterRow.appendChild(filterInput);
        settingsBar.appendChild(filterRow);

        var showRow    = document.createElement('div');
        showRow.style.cssText = 'display:flex;align-items:center;justify-content:space-between;font-size:11px;color:var(--text-secondary,#4b5563);';
        var totalLabel = document.createElement('span');
        totalLabel.className = 'map-overlay-total-label';
        showRow.appendChild(totalLabel);
        var showWrap = document.createElement('span');
        showWrap.style.cssText = 'display:flex;align-items:center;gap:4px;';
        var showLabel  = document.createElement('span');
        showLabel.textContent = 'Show';
        var showSelect = document.createElement('select');
        showSelect.style.cssText = 'border:1px solid var(--border-color,#d1d5db);border-radius:4px;font-size:11px;padding:2px 4px;outline:none;cursor:pointer;';
        [{ v: '0', t: 'All' }, { v: '5', t: '5' }, { v: '10', t: '10' }, { v: '15', t: '15' }, { v: '20', t: '20' }].forEach(function (o) {
            var opt = document.createElement('option');
            opt.value       = o.v;
            opt.textContent = o.t;
            showSelect.appendChild(opt);
        });
        showWrap.appendChild(showLabel);
        showWrap.appendChild(showSelect);
        showRow.appendChild(showWrap);
        settingsBar.appendChild(showRow);
        panel.appendChild(settingsBar);

        gridBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            e.preventDefault();
            var isOpen = settingsBar.style.display !== 'none';
            settingsBar.style.display = isOpen ? 'none' : '';
            if (!isOpen) filterInput.focus();
        });

        function matchesFilter(item, q) {
            var text = (getItemText(overlayType, item) || '').toLowerCase();
            if (text.indexOf(q) !== -1) return true;
            if (overlayType === 'stakeholders') {
                var s = normalizeStakeholderItem(item);
                if (s.nameRole.toLowerCase().indexOf(q) !== -1) return true;
                if (s.orgUnit.toLowerCase().indexOf(q) !== -1)  return true;
            }
            if (overlayColumnDefs.length > 0 && typeof getItemField === 'function') {
                for (var ci = 0; ci < overlayColumnDefs.length; ci++) {
                    if ((getItemField(overlayType, item, overlayColumnDefs[ci].id) || '').toLowerCase().indexOf(q) !== -1) return true;
                }
            }
            return false;
        }

        function getFilteredData() {
            var filtered = arr;
            if (panel._overlayFilter) {
                var q = panel._overlayFilter.toLowerCase();
                filtered = arr.filter(function (item) { return matchesFilter(item, q); });
            }
            if (panel._overlayMaxItems > 0) {
                filtered = filtered.slice(0, panel._overlayMaxItems);
            }
            return filtered;
        }

        filterInput.addEventListener('click',     function (e) { e.stopPropagation(); });
        filterInput.addEventListener('mousedown',  function (e) { e.stopPropagation(); });
        filterInput.addEventListener('input', function (e) {
            e.stopPropagation();
            panel._overlayFilter = filterInput.value;
            panel._overlayPage   = 1;
            renderPage(1);
        });
        showSelect.addEventListener('click',     function (e) { e.stopPropagation(); });
        showSelect.addEventListener('mousedown',  function (e) { e.stopPropagation(); });
        showSelect.addEventListener('change', function (e) {
            e.stopPropagation();
            panel._overlayMaxItems = parseInt(showSelect.value, 10) || 0;
            panel._overlayPage     = 1;
            renderPage(1);
        });

        // ── Body ──
        const body = document.createElement('div');
        body.className  = 'map-node-overlay-body';
        body.style.cssText = 'padding: 4px; max-height: 280px; overflow-y: auto; font-size: 11px; flex: 1;';
        panel.appendChild(body);

        // ── Footer (pagination) ──
        const footer = document.createElement('div');
        footer.className  = 'map-node-overlay-footer';
        footer.style.cssText = [
            'padding: 6px 10px;',
            'background: var(--overlay-footer-bg, #e5e7eb);',
            'border-top: 1px solid var(--border-color, #d1d5db);',
            'font-size: 11px; color: var(--text-secondary, #4b5563);',
            'display: flex; align-items: center; justify-content: space-between; gap: 8px;'
        ].join(' ');
        const pageLabel = document.createElement('span');
        pageLabel.className = 'map-overlay-page-label';
        footer.appendChild(pageLabel);
        const prevBtn = document.createElement('button');
        prevBtn.type        = 'button';
        prevBtn.textContent = '‹';
        prevBtn.className   = 'map-overlay-page-btn';
        prevBtn.style.cssText = 'border: none; background: transparent; cursor: pointer; padding: 2px 6px; font-size: 14px;';
        const nextBtn = document.createElement('button');
        nextBtn.type        = 'button';
        nextBtn.textContent = '›';
        nextBtn.className   = 'map-overlay-page-btn';
        nextBtn.style.cssText = 'border: none; background: transparent; cursor: pointer; padding: 2px 6px; font-size: 14px;';
        footer.appendChild(prevBtn);
        footer.appendChild(nextBtn);
        panel.appendChild(footer);

        function renderPage(page) {
            var visibleData  = getFilteredData();
            var totalPages   = Math.max(1, Math.ceil(visibleData.length / OVERLAY_PAGE_SIZE));
            panel._overlayPage = Math.max(1, Math.min(totalPages, page));
            const start    = (panel._overlayPage - 1) * OVERLAY_PAGE_SIZE;
            const pageData = visibleData.slice(start, start + OVERLAY_PAGE_SIZE);
            totalLabel.textContent = 'Total entries: ' + visibleData.length;
            body.innerHTML = '';

            if (overlayColumnDefs.length > 0 && pageData.length > 0 && typeof getItemField === 'function') {
                // Custom-column table
                const table = document.createElement('table');
                table.className  = 'map-overlay-table';
                const thead  = document.createElement('thead');
                const trHead = document.createElement('tr');
                overlayColumnDefs.forEach(function (c) {
                    const th = document.createElement('th');
                    th.textContent   = c.label;
                    trHead.appendChild(th);
                });
                thead.appendChild(trHead);
                table.appendChild(thead);
                const tbody = document.createElement('tbody');
                pageData.forEach(function (item, i) {
                    const origIndex = arr.indexOf(item);
                    const tr = document.createElement('tr');
                    tr.className    = 'map-node-overlay-item';
                    tr.setAttribute('data-item-index', origIndex >= 0 ? origIndex : (start + i));
                    tr.dataset.overlayValue = getItemText(overlayType, item);
                    const itemId = getItemId(overlayType, item);
                    if (itemId) {
                        tr.dataset.itemId = String(itemId);
                        if (overlayType === 'attributes' || overlayType === 'linking-attributes') tr.dataset.attributeId = String(itemId);
                    }
                    overlayColumnDefs.forEach(function (c) {
                        const td = document.createElement('td');
                        td.textContent   = getItemField(overlayType, item, c.id);
                        tr.appendChild(td);
                    });
                    tbody.appendChild(tr);
                });
                table.appendChild(tbody);
                body.appendChild(table);

            } else if (overlayType === 'stakeholders' && pageData.length > 0) {
                // Stakeholders table
                const table = document.createElement('table');
                table.className  = 'map-overlay-table map-overlay-table-stakeholders';
                const thead = document.createElement('thead');
                thead.innerHTML = '<tr>' +
                    '<th>Name / Role</th>' +
                    '<th>Accepted</th>' +
                    '<th>Org Unit</th>' +
                    '</tr>';
                table.appendChild(thead);
                const tbody = document.createElement('tbody');
                pageData.forEach((item, i) => {
                    const origIndex = arr.indexOf(item);
                    const { nameRole, accepted, orgUnit } = normalizeStakeholderItem(item);
                    const tr = document.createElement('tr');
                    tr.className    = 'map-node-overlay-item';
                    tr.innerHTML =
                        '<td>' + escapeHtml(String(nameRole)) + '</td>' +
                        '<td>' + escapeHtml(String(accepted))  + '</td>' +
                        '<td>' + escapeHtml(String(orgUnit))   + '</td>';
                    tr.setAttribute('data-item-index', origIndex >= 0 ? origIndex : (start + i));
                    tr.dataset.overlayValue = getItemText(overlayType, item);
                    const itemId = getItemId(overlayType, item);
                    if (itemId) {
                        tr.dataset.itemId = String(itemId);
                        if (overlayType === 'attributes' || overlayType === 'linking-attributes') tr.dataset.attributeId = String(itemId);
                    }
                    tbody.appendChild(tr);
                });
                table.appendChild(tbody);
                body.appendChild(table);

            } else {
                // Simple list
                pageData.forEach((item, i) => {
                    const origIndex = arr.indexOf(item);
                    const row = document.createElement('div');
                    row.className  = 'map-node-overlay-item';
                    row.setAttribute('data-item-index', origIndex >= 0 ? origIndex : (start + i));
                    const itemText = getItemText(overlayType, item);
                    row.textContent         = itemText;
                    row.dataset.overlayValue = itemText;
                    const itemId = getItemId(overlayType, item);
                    if (itemId) {
                        row.dataset.itemId = String(itemId);
                        if (overlayType === 'attributes' || overlayType === 'linking-attributes') row.dataset.attributeId = String(itemId);
                    }
                    body.appendChild(row);
                });
            }

            pageLabel.textContent = 'Page ' + panel._overlayPage + ' of ' + totalPages;
            prevBtn.disabled = panel._overlayPage <= 1;
            nextBtn.disabled = panel._overlayPage >= totalPages;

            body.querySelectorAll('.map-node-overlay-item').forEach(function (el) {
                el.addEventListener('mouseenter', function () {
                    el.style.background = 'var(--background-secondary, #f8fafc)';
                });
                el.addEventListener('mouseleave', function () {
                    if (!el.classList.contains('overlay-item-selected') &&
                        !el.classList.contains('highlighted-source') &&
                        !el.classList.contains('highlighted-related')) {
                        el.style.background = 'transparent';
                    }
                });
                el.addEventListener('click', function (ev) {
                    ev.stopPropagation();
                    onItemClick(panel, el);
                });
            });
        }

        prevBtn.addEventListener('click', function () { renderPage(panel._overlayPage - 1); });
        nextBtn.addEventListener('click', function () { renderPage(panel._overlayPage + 1); });
        renderPage(1);
        return panel;
    }

    if (typeof window !== 'undefined') {
        window.MapOverlayPanel = {
            OVERLAY_PAGE_SIZE:       OVERLAY_PAGE_SIZE,
            create:                  createOverlayPanel,
            normalizeStakeholderItem: normalizeStakeholderItem
        };
    }
})();


// =============================================================================
// SECTION 2 – OverlayColumns
// Source: overlay-columns.js
// =============================================================================
(function () {
    'use strict';

    /**
     * Overlay column/field definitions per overlay type.
     * Used by the Grid icon dropdown: "Adds overlay fields as columns".
     * Each overlay type has a list of { id, label } for optional columns.
     */
    const OVERLAY_COLUMNS = {
        glossary: [
            { id: 'name',                   label: 'Name' },
            { id: 'source',                 label: 'Source' },
            { id: 'aliasNames',             label: 'Alias Names' },
            { id: 'parentName',             label: 'Parent Name' },
            { id: 'lifecycle',              label: 'Lifecycle' },
            { id: 'securityClassification', label: 'Security Classification' }
        ],
        description: [
            { id: 'value', label: 'Value' }
        ],
        datasets: [
            { id: 'name',      label: 'Name' },
            { id: 'refNumber', label: 'Ref Number' },
            { id: 'type',      label: 'Type' },
            { id: 'lifecycle', label: 'Lifecycle' }
        ],
        attributes: [
            { id: 'name',      label: 'Name' },
            { id: 'type',      label: 'Type' },
            { id: 'glossary',  label: 'Glossary' },
            { id: 'refNumber', label: 'Ref Number' }
        ],
        'linking-attributes': [
            { id: 'name',             label: 'Name' },
            { id: 'type',             label: 'Type' },
            { id: 'glossary',         label: 'Glossary' },
            { id: 'direction',        label: 'Direction' },
            { id: 'relatedDataset',   label: 'Related Dataset' },
            { id: 'relatedAttribute', label: 'Related Attribute' }
        ],
        stakeholders: [
            { id: 'name', label: 'Name' },
            { id: 'role', label: 'Role' }
        ],
        processes: [
            { id: 'name',      label: 'Name' },
            { id: 'refNumber', label: 'Ref Number' },
            { id: 'lifecycle', label: 'Lifecycle' }
        ],
        projects: [
            { id: 'name',      label: 'Name' },
            { id: 'refNumber', label: 'Ref Number' },
            { id: 'status',    label: 'Status' }
        ],
        policies: [
            { id: 'name',   label: 'Name' },
            { id: 'type',   label: 'Type' },
            { id: 'status', label: 'Status' }
        ],
        'business-area': [
            { id: 'name',      label: 'Name' },
            { id: 'refNumber', label: 'Ref Number' }
        ],
        products: [
            { id: 'name',      label: 'Name' },
            { id: 'refNumber', label: 'Ref Number' }
        ],
        'legal-entities': [
            { id: 'name',      label: 'Name' },
            { id: 'refNumber', label: 'Ref Number' }
        ],
        'data-quality': [
            { id: 'name',     label: 'Name' },
            { id: 'ruleName', label: 'Rule' },
            { id: 'rating',   label: 'Rating' }
        ],
        'data-privacy': [
            { id: 'name',           label: 'Name' },
            { id: 'classification', label: 'Classification' }
        ],
        geography: [
            { id: 'name',    label: 'Name' },
            { id: 'region',  label: 'Region' },
            { id: 'country', label: 'Country' }
        ],
        systems: [
            { id: 'name',      label: 'Name' },
            { id: 'refNumber', label: 'Ref Number' },
            { id: 'type',      label: 'Type' },
            { id: 'lifecycle', label: 'Lifecycle' }
        ]
    };

    /**
     * Get column definitions for an overlay type.
     * @param {string} overlayType - e.g. 'glossary', 'attributes', 'stakeholders'
     * @returns {Array<{id: string, label: string}>}
     */
    function getOverlayColumns(overlayType) {
        if (!overlayType || overlayType === 'none') return [];
        const cols = OVERLAY_COLUMNS[overlayType];
        if (cols) return cols.slice();
        return [{ id: 'name', label: 'Name' }];
    }

    /**
     * Default selected column id for each overlay type (usually 'name').
     * @param {string} overlayType
     * @returns {string[]}
     */
    function getDefaultOverlayColumnIds(overlayType) {
        const cols = getOverlayColumns(overlayType);
        if (cols.length === 0) return [];
        if (overlayType === 'glossary') return ['name', 'source'];
        return [cols[0].id];
    }

    if (typeof window !== 'undefined') {
        window.OverlayColumns = {
            getOverlayColumns:         getOverlayColumns,
            getDefaultOverlayColumnIds: getDefaultOverlayColumnIds
        };
    }
})();


// =============================================================================
// SECTION 3 – SharedMapHTML + SharedMapDropdowns
// Source: shared-map-html.js
// =============================================================================
(function () {
    'use strict';

    /**
     * Rich layout picker + hidden LayoutSelect + spacing/edge controls.
     * @param {string} mapId
     * @param {{ richItems?: 'three'|'four' }} [options] - 'four' adds Right-To-Left row in the menu.
     */
    function sharedMapLayoutRichControlsHtml(mapId, options) {
        const id = mapId || 'map';
        const opts = options && typeof options === 'object' ? options : {};
        const four = opts.richItems === 'four';
        const rtlRow = four
            ? `
                                <div class="map-layout-rich-item" data-layout="right-to-left">
                                <div class="map-layout-rich-icon"><i class="fas fa-arrow-left"></i></div>
                                <div class="map-layout-rich-content">
                                <div class="map-layout-rich-title">Right-To-Left</div>
                                <div class="map-layout-rich-desc">Flow from right to left; useful for RTL workflows.</div>
                                </div>
                                </div>`
            : '';
        const spacingEdge = typeof window.SharedMapSpacingEdgeControlsHtml === 'function'
            ? window.SharedMapSpacingEdgeControlsHtml(id)
            : '';
        return `
                            <div class="map-btn-dropdown-wrapper map-layout-dropdown-wrapper">
                                <button type="button" class="map-select" id="${id}LayoutBtn">
                                <span id="${id}LayoutBtnText">Top-To-Bottom</span>
                                <i class="fas fa-chevron-down" aria-hidden="true"></i>
                                </button>
                                <div class="map-btn-dropdown-menu map-layout-rich-menu" id="${id}LayoutMenu">
                                <div class="map-layout-rich-item active" data-layout="top-to-bottom">
                                <div class="map-layout-rich-icon"><i class="fas fa-arrow-down"></i></div>
                                <div class="map-layout-rich-content">
                                <div class="map-layout-rich-title">Top-To-Bottom</div>
                                <div class="map-layout-rich-desc">Most useful in representing information flows that aggregate into a central point.</div>
                                </div>
                                </div>
                                <div class="map-layout-rich-item" data-layout="left-to-right">
                                <div class="map-layout-rich-icon"><i class="fas fa-arrow-right"></i></div>
                                <div class="map-layout-rich-content">
                                <div class="map-layout-rich-title">Left-To-Right</div>
                                <div class="map-layout-rich-desc">The best way to view your information flow for small to medium sized maps.</div>
                                </div>
                                </div>
                                ${rtlRow}
                                <div class="map-layout-rich-item" data-layout="force">
                                <div class="map-layout-rich-icon"><i class="fas fa-project-diagram"></i></div>
                                <div class="map-layout-rich-content">
                                <div class="map-layout-rich-title">Organic</div>
                                <div class="map-layout-rich-desc">Ideal for larger maps or maps with no overriding direction in the information flow.</div>
                                </div>
                                </div>
                                </div>
                                </div>
                                <select id="${id}LayoutSelect" class="map-select" style="display:none;" aria-hidden="true" tabindex="-1">
                                <option value="top-to-bottom" selected>Top-To-Bottom</option>
                                <option value="left-to-right">Left-To-Right</option>
                                <option value="right-to-left">Right-To-Left</option>
                                <option value="force">Organic</option>
                                </select>
                            ${spacingEdge}`;
    }

    window.SharedMapLayoutRichControlsHtml = sharedMapLayoutRichControlsHtml;

    /**
     * Generate map HTML structure.
     *
     * @param {Object}  config
     * @param {string}  config.mapId               - Unique ID for this map instance
     * @param {string}  config.defaultMapType       - Default map type ('system-lineage', etc.)
     * @param {Array}   config.mapTypeOptions       - [{ value, label }]
     * @param {boolean} config.showMapTypeSelector  - Show map type dropdown (default: true)
     * @param {boolean} config.showLayoutControls   - Show layout controls (default: true)
     * @param {boolean} config.showOverlayControls  - Show overlay controls (default: true)
     * @param {boolean} config.showFilterControls   - Show filter controls (default: true)
     * @param {boolean} config.showToolbar          - Show toolbar buttons (default: true)
     * @param {Object}  config.overlayConfig        - Overlay menu configuration
     * @param {Object}  config.filterConfig         - Filter menu configuration
     * @returns {string} HTML string
     */
    window.SharedMapHTML = function (config) {
        const {
            mapId           = 'map',
            /** When false, omit canvas/side-panel (e.g. fullscreen page uses #mapTabCanvas). */
            includeMapBody  = true,
            defaultMapType  = 'system-lineage',
            mapTypeOptions  = [
                { value: 'system-lineage',  label: 'System Lineage' },
                { value: 'dataset-lineage', label: 'Dataset Lineage' }
            ],
            overlayConfig = {
                systemLineage: {
                    columns: [
                        {
                            header: 'Data',
                            items: [
                                { overlay: 'description',        icon: 'fa-info-circle', label: 'Description' },
                                { overlay: 'glossary',           icon: 'fa-book',        label: 'Glossary' },
                                { overlay: 'datasets',           icon: 'fa-layer-group', label: 'Data Sets' },
                                { overlay: 'attributes',         icon: 'fa-th',          label: 'Attributes' },
                                { overlay: 'linking-attributes', icon: 'fa-th',          label: 'Linking Attributes' },
                                { overlay: 'data-quality',       icon: 'fa-bullseye',    label: 'Data Quality' },
                                { overlay: 'data-privacy',       icon: 'fa-lock',        label: 'Data Privacy' }
                            ]
                        },
                        {
                            header: 'Business',
                            items: [
                                { overlay: 'stakeholders', icon: 'fa-users',          label: 'Stakeholders' },
                                { overlay: 'processes',    icon: 'fa-play',           label: 'Processes' },
                                { overlay: 'projects',     icon: 'fa-project-diagram',label: 'Projects' },
                                { overlay: 'policies',     icon: 'fa-file-alt',       label: 'Policies' }
                            ]
                        },
                        {
                            header: 'Organizational',
                            items: [
                                { overlay: 'business-area',   icon: 'fa-briefcase', label: 'Business Area' },
                                { overlay: 'products',        icon: 'fa-tag',       label: 'Products' },
                                { overlay: 'legal-entities',  icon: 'fa-landmark',  label: 'Legal Entities' }
                            ]
                        },
                        {
                            header: 'Regulatory',
                            items: [
                                { overlay: 'geography', icon: 'fa-globe', label: 'Geography' }
                            ]
                        }
                    ]
                },
                datasetLineage: {
                    columns: [
                        {
                            header: 'Data',
                            items: [
                                { overlay: 'description',  icon: 'fa-info-circle', label: 'Description' },
                                { overlay: 'glossary',     icon: 'fa-book',        label: 'Glossary' },
                                { overlay: 'attributes',   icon: 'fa-th',          label: 'Attributes' },
                                { overlay: 'data-quality', icon: 'fa-bullseye',    label: 'Data Quality' }
                            ]
                        },
                        {
                            header: 'Business',
                            items: [
                                { overlay: 'stakeholders', icon: 'fa-users', label: 'Stakeholders' },
                                { overlay: 'processes',    icon: 'fa-play',  label: 'Processes' }
                            ]
                        }
                    ]
                }
            },
            filterConfig = {
                systemLineage: {
                    categories: [
                        {
                            header: 'LINKS',
                            options: [
                                { id: 'systemInterfaces',    label: 'System Interfaces',    checked: true },
                                { id: 'dataAttributeLinks',  label: 'Data Attribute Links', checked: true }
                            ]
                        },
                        {
                            header: 'CLASSIFICATION',
                            dynamic: true,
                            containerId: 'filterClassificationOptions'
                        },
                        {
                            header: 'TYPE',
                            dynamic: true,
                            containerId: 'filterTypeOptions'
                        },
                        {
                            header: 'LIFECYCLE',
                            dynamic: true,
                            containerId: 'filterLifecycleOptions'
                        }
                    ]
                },
                datasetLineage: {
                    categories: [
                        {
                            header: 'TYPE',
                            dynamic: true,
                            containerId: 'filterTypeOptions'
                        },
                        {
                            header: 'LIFECYCLE',
                            dynamic: true,
                            containerId: 'filterLifecycleOptions'
                        }
                    ]
                }
            }
        } = config;

        // Generate map type select options
        const mapTypeOptionsHtml = mapTypeOptions.map(opt =>
            `<option value="${opt.value}" ${opt.value === defaultMapType ? 'selected' : ''}>${opt.label}</option>`
        ).join('');

        // Extract configured map types from mapTypeOptions
        const configuredMapTypes = new Set(mapTypeOptions.map(opt => opt.value));

        // Generate overlay menu HTML
        function generateOverlayMenu(mapType) {
            if (!configuredMapTypes.has(mapType)) return '';

            let overlayData;
            if (mapType === 'system-lineage') {
                overlayData = overlayConfig.systemLineage;
            } else if (mapType === 'glossary-lineage') {
                overlayData = overlayConfig.glossaryLineage;
            } else if (mapType === 'multi-node-lineage') {
                overlayData = overlayConfig.multiNodeLineage;
            } else {
                overlayData = overlayConfig.datasetLineage;
            }
            if (!overlayData) return '';

            const columnsHtml = overlayData.columns.map(col => `
                <div class="overlay-menu-column">
                    <div class="overlay-menu-header">${col.header}</div>
                    ${col.items.map(item => `
                        <div class="overlay-menu-item" data-overlay="${item.overlay}">
                            <i class="fas ${item.icon}"></i> ${item.label}
                        </div>
                    `).join('')}
                </div>
            `).join('');

            let menuSuffix = 'Dataset';
            if (mapType === 'system-lineage')     menuSuffix = 'System';
            else if (mapType === 'glossary-lineage')   menuSuffix = 'Glossary';
            else if (mapType === 'multi-node-lineage') menuSuffix = 'MultiNode';

            return `
                <div class="map-overlay-menu" id="${mapId}OverlayMenu${menuSuffix}" data-map-type="${mapType}">
                    <div class="overlay-menu-grid">
                        ${columnsHtml}
                    </div>
                    <div class="overlay-menu-footer">
                        <button type="button" class="overlay-clear-btn" id="${mapId}ClearOverlaysBtn${menuSuffix}">Clear Overlays</button>
                    </div>
                </div>
            `;
        }

        // Generate filter menu HTML
        function generateFilterMenu(mapType) {
            if (!configuredMapTypes.has(mapType)) return '';

            let filterData;
            if (mapType === 'system-lineage') {
                filterData = filterConfig.systemLineage;
            } else if (mapType === 'glossary-lineage') {
                filterData = filterConfig.glossaryLineage;
            } else if (mapType === 'multi-node-lineage') {
                filterData = filterConfig.multiNodeLineage;
            } else {
                filterData = filterConfig.datasetLineage;
            }
            if (!filterData) return '';

            const categoriesHtml = filterData.categories.map((cat, idx) => {
                let optionsHtml = '';
                if (cat.dynamic) {
                    let dynId = cat.containerId;
                    if (mapType === 'dataset-lineage' && mapId === 'interfaceMap') {
                        if (dynId === 'filterTypeOptions') dynId = 'datasetFilterTypeOptions';
                        else if (dynId === 'filterLifecycleOptions') dynId = 'datasetFilterLifecycleOptions';
                    }
                    optionsHtml = `<div id="${dynId}"><!-- Dynamic options will be added here --></div>`;
                } else {
                    optionsHtml = cat.options.map(opt => {
                        const linkAttr = (opt.id === 'systemInterfaces' || opt.id === 'dataAttributeLinks')
                            ? ` data-filter-link="${opt.id}"`
                            : '';
                        const linkCheckboxId = (mapId === 'interfaceMap' && (opt.id === 'systemInterfaces' || opt.id === 'dataAttributeLinks'))
                            ? (opt.id === 'systemInterfaces' ? 'filterSystemInterfaces' : 'filterDataAttributeLinks')
                            : ('filter' + mapId + opt.id);
                        return `
                        <div class="map-filter-option${opt.hidden ? ' map-filter-option--hidden' : ''}">
                            <input type="checkbox" id="${linkCheckboxId}"${linkAttr} ${opt.checked ? 'checked' : ''}>
                            <label for="${linkCheckboxId}">${opt.label}</label>
                        </div>
                    `;
                    }).join('');
                }
                return `
                    ${idx > 0 ? '<div class="map-filter-separator"></div>' : ''}
                    <div class="map-filter-category">
                        <div class="map-filter-category-header">${cat.header}</div>
                        ${optionsHtml}
                    </div>
                `;
            }).join('');

            let menuSuffix = 'Dataset';
            if (mapType === 'system-lineage')     menuSuffix = 'System';
            else if (mapType === 'glossary-lineage')   menuSuffix = 'Glossary';
            else if (mapType === 'multi-node-lineage') menuSuffix = 'MultiNode';

            return `
                <div class="map-filter-menu" id="${mapId}FilterMenu${menuSuffix}" data-map-type="${mapType}">
                    ${categoriesHtml}
                </div>
            `;
        }

        return `
            <div class="map-section map-section--grid-span" id="${mapId}Section">
                <div class="map-section-header">
                    <div class="map-section-title">MAP</div>
                    <button type="button" class="map-collapse-btn" id="${mapId}CollapseBtn" title="Collapse/Expand">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
                <!-- Map Toolbar -->
                <div class="interface-map-toolbar">
                    <!-- Map Type -->
                    <div class="map-control-group">
                        <label>Map type:</label>
                        <select id="${mapId}TypeSelect" class="map-select">
                            ${mapTypeOptionsHtml}
                        </select>
                    </div>

                    <!-- Layout -->
                    <div class="map-control-group">
                        <label>Layout:</label>
                        <div class="map-layout-controls">
                            ${sharedMapLayoutRichControlsHtml(mapId)}
                        </div>
                    </div>

                    <!-- Hops Count (lineage depth 1-99, recommend 15) -->
                    <div class="map-control-group map-hops-group" id="${mapId}HopsGroup">
                        <label>Hops:</label>
                        <input type="number" id="${mapId}HopsCount" class="map-hops-input" min="1" max="99" value="15"
                               title="Upstream/downstream lineage depth (1-99, recommend 15)">
                    </div>

                    <!-- Overlay -->
                    <div class="map-control-group">
                        <label>Overlay:</label>
                        <div class="map-overlay-controls">
                            <div class="map-overlay-dropdown">
                                <button type="button" class="map-select-btn" id="${mapId}OverlayBtn">
                                    <span id="${mapId}OverlayBtnText">None</span>
                                    <i class="fas fa-chevron-down"></i>
                                </button>
                                ${generateOverlayMenu('system-lineage')}
                                ${generateOverlayMenu('dataset-lineage')}
                                ${generateOverlayMenu('glossary-lineage')}
                                ${generateOverlayMenu('multi-node-lineage')}
                            </div>
                            <button type="button" class="map-toolbar-btn-sm" id="${mapId}OverlayGrid" title="Overlay fields as columns">
                                <i class="fas fa-th"></i>
                                <i class="fas fa-chevron-down map-toolbar-chevron"></i>
                            </button>
                            <div class="map-overlay-columns-menu map-filter-menu map-menu--closed" id="${mapId}OverlayColumnsMenu">
                                <div class="map-filter-category">
                                    <div class="map-filter-category-header">OVERLAY FIELDS AS COLUMNS</div>
                                    <div id="${mapId}OverlayColumnsOptions" class="map-filter-options-container"></div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Filters -->
                    <div class="map-control-group">
                        <label>Filters:</label>
                        <div class="map-filter-dropdown">
                            <button type="button" class="map-select-btn" id="${mapId}FilterBtn">
                                <span id="${mapId}FilterBtnText">All selected</span>
                                <i class="fas fa-chevron-down"></i>
                            </button>
                            ${generateFilterMenu('system-lineage')}
                            ${generateFilterMenu('dataset-lineage')}
                            ${generateFilterMenu('glossary-lineage')}
                            ${generateFilterMenu('multi-node-lineage')}
                        </div>
                    </div>

                    <!-- Toolbar Buttons -->
                    <div class="map-toolbar-buttons">
                        <button type="button" class="map-toolbar-btn" id="${mapId}ToggleLabels" title="Toggle labels">
                            <i class="fas fa-exchange-alt"></i>
                        </button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${mapId}ZoomIn" title="Zoom in">
                            <i class="fas fa-search-plus"></i>
                        </button>
                        <button type="button" class="map-toolbar-btn" id="${mapId}ZoomOut" title="Zoom out">
                            <i class="fas fa-search-minus"></i>
                        </button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${mapId}Redraw" title="Redraw">
                            <i class="fas fa-sync-alt"></i>
                        </button>
                        <button type="button" class="map-toolbar-btn" id="${mapId}Reset" title="Reset">
                            <i class="fas fa-undo"></i>
                        </button>
                        <button type="button" class="map-toolbar-btn" id="${mapId}Export" title="Export as PNG">
                            <i class="fas fa-save"></i>
                        </button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${mapId}Navigator" title="Map navigator">
                            <i class="fas fa-eye"></i>
                        </button>
                        <button type="button" class="map-toolbar-btn" id="${mapId}Fullscreen" title="Fullscreen">
                            <i class="fas fa-external-link-alt"></i>
                        </button>
                        <button type="button" class="map-toolbar-btn" id="${mapId}Legend"
                                title="Open the Legend"
                                aria-label="Open the Legend – refer to the legend to identify the symbols used in the map (Insight Maps Palette)">
                            <i class="fas fa-list-ul"></i>
                        </button>
                    </div>
                </div>
                ${includeMapBody ? `
                <!-- Map Body -->
                <div class="interface-map-body">
                    <div class="interface-map-canvas" id="${mapId}Canvas">
                        <div class="interface-map-loading map-js-hidden" data-map-loading>
                            <i class="fas fa-spinner fa-spin"></i>
                            <span>Building lineage map...</span>
                        </div>
                    </div>
                    <div class="interface-map-side-panel map-js-hidden" id="${mapId}SidePanel">
                        <div class="map-side-panel-section">
                            <h4><i class="fas fa-info-circle"></i> Selection</h4>
                            <div class="selection-placeholder" data-map-placeholder>
                                Select a node to see its details.
                            </div>
                            <div class="selection-info map-js-hidden" data-map-details></div>
                        </div>
                        <div class="map-side-panel-section">
                            <h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>
                            <div data-map-legend></div>
                        </div>
                    </div>
                </div>` : ''}
            </div>
        `;
    };

    /**
     * Initialize spacing and edge-style dropdown menus for a map instance.
     * Call this after the map HTML is in the DOM.
     *
     * @param {Object}   opts
     * @param {string}   opts.mapId       - The mapId used in SharedMapHTML
     * @param {Function} opts.getNetwork  - Returns the cytoscape instance (or null)
     * @param {Function} opts.setLayout   - Called with the direction string when user picks one
     * @param {Function} [opts.getCanvas] - Returns the canvas DOM element to resize
     * @returns {Object} API — getSpacingFactor(), getSpacingPadding(), getCurveStyle()
     */
    window.SharedMapDropdowns = function (opts) {
        const { mapId, getNetwork, setLayout } = opts;

        const spacingBtn  = document.getElementById(mapId + 'Spacing');
        const spacingMenu = document.getElementById(mapId + 'SpacingMenu');
        const edgeBtn     = document.getElementById(mapId + 'EdgeStyle');
        const edgeMenu    = document.getElementById(mapId + 'EdgeStyleMenu');
        const layoutSel   = document.getElementById(mapId + 'LayoutSelect');
        const layoutBtn   = document.getElementById(mapId + 'LayoutBtn');
        const layoutMenu  = document.getElementById(mapId + 'LayoutMenu');
        const layoutText  = document.getElementById(mapId + 'LayoutBtnText');

        let currentSpacing   = 'normal';
        let currentEdgeStyle = 'direct';

        function syncLayoutRichUi() {
            if (!layoutSel) return;
            const v = layoutSel.value;
            if (layoutMenu) {
                layoutMenu.querySelectorAll('.map-layout-rich-item').forEach(it => {
                    it.classList.toggle('active', it.dataset.layout === v);
                });
            }
            if (!layoutText) return;
            const activeItem = layoutMenu && layoutMenu.querySelector('.map-layout-rich-item.active');
            const titleEl = activeItem && activeItem.querySelector('.map-layout-rich-title');
            if (titleEl) layoutText.textContent = titleEl.textContent;
            else {
                const opt = layoutSel.options[layoutSel.selectedIndex];
                if (opt) layoutText.textContent = opt.textContent;
            }
        }

        function closeMenus() {
            document.querySelectorAll('.map-btn-dropdown-menu.show').forEach(m => m.classList.remove('show'));
        }

        document.addEventListener('click', (e) => {
            if (!e.target.closest('.map-btn-dropdown-wrapper')) closeMenus();
        });

        function spacingFactor() {
            if (currentSpacing === 'compact') return 1.2;
            if (currentSpacing === 'spacey')  return 3.5;
            return 2.2;
        }

        function spacingPadding() {
            if (currentSpacing === 'compact') return 40;
            if (currentSpacing === 'spacey')  return 150;
            return 90;
        }

        function curveStyle() {
            switch (currentEdgeStyle) {
                case 'angle':      return 'segments';
                case 'square':     return 'taxi';
                case 'loop':       return 'unbundled-bezier';
                case 'top-down':   return 'bezier';
                case 'left-right': return 'bezier';
                default:           return 'bezier';
            }
        }

        // ── Spacing dropdown ──
        if (spacingBtn && spacingMenu) {
            spacingBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const wasOpen = spacingMenu.classList.contains('show');
                closeMenus();
                if (!wasOpen) spacingMenu.classList.add('show');
            });
            spacingMenu.addEventListener('click', (e) => {
                const item = e.target.closest('.map-btn-dropdown-item');
                if (!item) return;
                const sp = item.dataset.spacing;
                if (!sp) return;
                currentSpacing = sp;
                spacingMenu.querySelectorAll('.map-btn-dropdown-item').forEach(it => {
                    it.classList.toggle('active', it.dataset.spacing === sp);
                });
                if (typeof setLayout === 'function') setLayout(layoutSel ? layoutSel.value : 'left-to-right');
                closeMenus();
            });
        }

        // ── Edge Style dropdown ──
        if (edgeBtn && edgeMenu) {
            edgeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const wasOpen = edgeMenu.classList.contains('show');
                closeMenus();
                if (!wasOpen) edgeMenu.classList.add('show');
            });
            edgeMenu.addEventListener('click', (e) => {
                const item = e.target.closest('.map-btn-dropdown-item');
                if (!item) return;
                const es = item.dataset.edgeStyle;
                if (!es) return;
                currentEdgeStyle = es;
                edgeMenu.querySelectorAll('.map-btn-dropdown-item').forEach(it => {
                    it.classList.toggle('active', it.dataset.edgeStyle === es);
                });
                const cy = typeof getNetwork === 'function' ? getNetwork() : null;
                if (cy) cy.edges().style('curve-style', curveStyle());
                if (es === 'top-down') {
                    if (layoutSel) layoutSel.value = 'top-to-bottom';
                    syncLayoutRichUi();
                    if (typeof setLayout === 'function') setLayout('top-to-bottom');
                } else if (es === 'left-right') {
                    if (layoutSel) layoutSel.value = 'left-to-right';
                    syncLayoutRichUi();
                    if (typeof setLayout === 'function') setLayout('left-to-right');
                }
                closeMenus();
            });
        }

        if (layoutSel) {
            layoutSel.addEventListener('change', () => {
                syncLayoutRichUi();
                if (typeof setLayout === 'function') setLayout(layoutSel.value);
            });
        }

        if (layoutBtn && layoutMenu && layoutSel) {
            layoutBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const wasOpen = layoutMenu.classList.contains('show');
                closeMenus();
                if (!wasOpen) layoutMenu.classList.add('show');
            });
            layoutMenu.addEventListener('click', (e) => {
                const item = e.target.closest('.map-layout-rich-item');
                if (!item || !item.dataset.layout) return;
                const dir = item.dataset.layout;
                layoutMenu.querySelectorAll('.map-layout-rich-item').forEach(it => {
                    it.classList.toggle('active', it.dataset.layout === dir);
                });
                const title = item.querySelector('.map-layout-rich-title');
                if (layoutText && title) layoutText.textContent = title.textContent;
                layoutSel.value = dir;
                if (typeof setLayout === 'function') setLayout(dir);
                closeMenus();
            });
            syncLayoutRichUi();
        }

        const api = {
            getSpacingFactor:  spacingFactor,
            getSpacingPadding: spacingPadding,
            getCurveStyle:     curveStyle,
            getSpacing:        () => currentSpacing,
            getEdgeStyle:      () => currentEdgeStyle
        };

        if (spacingBtn) spacingBtn._dropdownApi = api;
        window._sharedDropdownApis         = window._sharedDropdownApis || {};
        window._sharedDropdownApis[mapId]  = api;

        return api;
    };

    /**
     * Spacing + edge-style dropdown markup (paired with SharedMapLayoutRichControlsHtml).
     */
    window.SharedMapSpacingEdgeControlsHtml = function (mapId) {
        const id = mapId || 'map';
        return `
                            <div class="map-btn-dropdown-wrapper">
                                <button type="button" class="map-toolbar-btn-sm" id="${id}Spacing" title="Node spacing">
                                    <i class="fas fa-expand-arrows-alt" id="${id}SpacingIcon"></i>
                                    <i class="fas fa-chevron-down map-toolbar-chevron"></i>
                                </button>
                                <div class="map-btn-dropdown-menu" id="${id}SpacingMenu">
                                    <div class="map-btn-dropdown-item" data-spacing="compact">Compact</div>
                                    <div class="map-btn-dropdown-item active" data-spacing="normal">Normal</div>
                                    <div class="map-btn-dropdown-item" data-spacing="spacey">Spacey</div>
                                </div>
                            </div>
                            <div class="map-btn-dropdown-wrapper">
                                <button type="button" class="map-toolbar-btn-sm" id="${id}EdgeStyle" title="Edge routing style">
                                    <i class="fas fa-arrow-right" id="${id}EdgeStyleIcon"></i>
                                    <i class="fas fa-chevron-down map-toolbar-chevron"></i>
                                </button>
                                <div class="map-btn-dropdown-menu" id="${id}EdgeStyleMenu">
                                    <div class="map-btn-dropdown-item" data-edge-style="angle">Angle</div>
                                    <div class="map-btn-dropdown-item" data-edge-style="square">Square</div>
                                    <div class="map-btn-dropdown-item active" data-edge-style="direct">Direct</div>
                                    <div class="map-btn-dropdown-item" data-edge-style="loop">Loop</div>
                                    <div class="map-btn-dropdown-item" data-edge-style="top-down">Top-Down</div>
                                    <div class="map-btn-dropdown-item" data-edge-style="left-right">Left-Right</div>
                                </div>
                            </div>`;
    };

})();
