/**
 * Grid Settings - Reusable table toolbar component
 * =================================================
 * Full-featured grid gear menu for any HTML table.
 *
 * Features:
 *   - Show Filters        (adds filter input row below headers)
 *   - Columns             (show/hide columns via checkboxes)
 *   - Export              (CSV or copy to clipboard)
 *   - Expand / Collapse   (toggle body rows)
 *   - Save / Reset Layout (persists column visibility to localStorage)
 *   - Reload Grid
 *   - Increase Height
 *   - Copy grid to clipboard
 *
 * Usage:
 *   <link rel="stylesheet" href="/assets/css/grid-settings.css">
 *   <script src="/assets/js/grid-settings.js"></script>
 *   HTML:  GridSettings.html('myId')
 *   Init:  GridSettings.init(containerElement)
 */
(function () {
    'use strict';

    // ===================== HTML GENERATOR =====================
    // Menu is NO LONGER rendered inside the wrapper.
    // It is created dynamically and appended to document.body (portal)
    // so that parent overflow:hidden never clips it.
    function html(id) {
        return (
            '<div class="grid-settings-wrapper" style="margin-left:auto;">' +
                '<button type="button" class="grid-settings-btn" data-grid-id="' + id + '" aria-haspopup="true" aria-expanded="false">' +
                    '<i class="fas fa-cog"></i> <span style="font-size:0.6rem;margin-left:2px;"><i class="fas fa-chevron-down"></i></span>' +
                '</button>' +
            '</div>'
        );
    }

    // ===================== MENU ELEMENT FACTORY =====================
    // Creates a menu and appends it to document.body (portal pattern).
    // Uses position:fixed so it floats above everything.
    function createPortalMenu(id) {
        var d = document.createElement('div');
        d.className = 'grid-settings-menu gs-portal';
        if (id) d.id = 'gsMenu_' + id;
        d.innerHTML =
            '<div class="gs-item" data-action="showFilters"><i class="fas fa-filter"></i> Show Filters</div>' +
            '<div class="gs-item" data-action="columns"><i class="fas fa-columns"></i> Columns <i class="fas fa-chevron-right gs-arrow"></i></div>' +
            '<div class="gs-item" data-action="export"><i class="fas fa-file-export"></i> Export <i class="fas fa-chevron-right gs-arrow"></i></div>' +
            '<div class="gs-divider"></div>' +
            '<div class="gs-item" data-action="expandRows"><i class="fas fa-expand-alt"></i> Expand Rows</div>' +
            '<div class="gs-item" data-action="collapseRows"><i class="fas fa-compress-alt"></i> Collapse Rows</div>' +
            '<div class="gs-divider"></div>' +
            '<div class="gs-item" data-action="saveLayout"><i class="fas fa-save"></i> Save Layout</div>' +
            '<div class="gs-item" data-action="resetLayout"><i class="fas fa-undo"></i> Reset Layout</div>' +
            '<div class="gs-divider"></div>' +
            '<div class="gs-item" data-action="reloadGrid"><i class="fas fa-sync"></i> Reload Grid</div>' +
            '<div class="gs-item" data-action="increaseHeight"><i class="fas fa-arrows-alt-v"></i> Increase Height</div>' +
            '<div class="gs-item" data-action="copyGrid"><i class="fas fa-clipboard"></i> Copy to Clipboard</div>';
        document.body.appendChild(d);
        return d;
    }

    // Position the portal menu relative to its trigger button (absolute on body)
    function positionMenu(menu, btn) {
        var rect = btn.getBoundingClientRect();
        var scrollX = window.pageXOffset || document.documentElement.scrollLeft;
        var scrollY = window.pageYOffset || document.documentElement.scrollTop;
        var menuW = 230; // min-width from CSS
        var left = rect.right + scrollX - menuW;
        if (left < 4) left = 4;
        menu.style.top = (rect.bottom + scrollY + 4) + 'px';
        menu.style.left = left + 'px';
    }

    // ===================== FIND TABLE =====================
    function findTable(wrapper) {
        var btn = wrapper.querySelector('.grid-settings-btn');
        if (btn) {
            var tid = btn.dataset.gridTarget;
            if (tid) {
                var el = document.getElementById(tid);
                if (el) { var t = el.closest('table') || el.querySelector('table'); if (t) return t; }
            }
        }
        var selectors = [
            '.collapsible-section', '.view-section', '.form-card',
            '.relationships-hierarchy', '.sub-tab-content',
            '.stakeholders-edit-container', '.relationships-section',
            '.stakeholder-community-table-container'
        ];
        var p = wrapper.parentElement;
        while (p && p !== document.body) {
            for (var i = 0; i < selectors.length; i++) {
                if (p.matches && p.matches(selectors[i])) {
                    var tbl = p.querySelector('table');
                    if (tbl) return tbl;
                }
            }
            p = p.parentElement;
        }
        var c = wrapper.closest('div');
        while (c) {
            var tbl = c.querySelector('table');
            if (tbl) return tbl;
            c = c.parentElement ? c.parentElement.closest('div') : null;
        }
        return null;
    }

    // ===================== LAYOUT KEY =====================
    function layoutKey(wrapper) {
        var btn = wrapper.querySelector('.grid-settings-btn');
        var id = btn ? (btn.dataset.gridId || btn.dataset.gridTarget || '') : '';
        return 'gs_layout_' + window.location.pathname.replace(/\//g, '_') + '_' + id;
    }

    // ===================== TOAST =====================
    function toast(text) {
        var msg = document.createElement('div');
        msg.style.cssText =
            'position:fixed;top:80px;left:50%;transform:translateX(-50%);' +
            'background-color:#248567;color:white;padding:12px 24px;border-radius:6px;' +
            'font-size:14px;font-weight:500;z-index:10000;box-shadow:0 4px 12px rgba(0,0,0,0.15);' +
            'transition:opacity 0.5s;';
        msg.textContent = text;
        document.body.appendChild(msg);
        setTimeout(function () { msg.style.opacity = '0'; setTimeout(function () { msg.remove(); }, 500); }, 2000);
    }

    // ===================== SHOW FILTERS =====================
    function toggleFilters(table) {
        var thead = table.querySelector('thead');
        if (!thead) return;

        var existing = thead.querySelector('.gs-filter-row');
        if (existing) {
            existing.remove();
            return;
        }

        var ths = thead.querySelectorAll('tr:first-child th');
        var filterRow = document.createElement('tr');
        filterRow.className = 'gs-filter-row';

        ths.forEach(function (th, idx) {
            var td = document.createElement('th');
            if (th.style.display === 'none') td.style.display = 'none';
            var input = document.createElement('input');
            input.type = 'text';
            input.placeholder = 'Filter...';
            input.setAttribute('data-col-idx', idx);
            input.addEventListener('input', function () {
                var val = this.value.toLowerCase();
                table.querySelectorAll('tbody tr').forEach(function (row) {
                    var cells = row.querySelectorAll('td');
                    if (!cells[idx]) return;
                    var text = cells[idx].textContent.toLowerCase();
                    // Only hide if this specific filter doesn't match
                    // Check all filters together
                    var allFilters = filterRow.querySelectorAll('input');
                    var show = true;
                    allFilters.forEach(function (f) {
                        var ci = parseInt(f.dataset.colIdx);
                        var fv = f.value.toLowerCase();
                        if (fv && cells[ci]) {
                            if (cells[ci].textContent.toLowerCase().indexOf(fv) === -1) show = false;
                        }
                    });
                    row.style.display = show ? '' : 'none';
                });
            });
            td.appendChild(input);
            filterRow.appendChild(td);
        });

        thead.appendChild(filterRow);
    }

    // ===================== POSITION SUB-PANEL =====================
    function positionSubPanel(panel, triggerItem, menu) {
        var menuRect = menu.getBoundingClientRect();
        var scrollX = window.pageXOffset || document.documentElement.scrollLeft;
        var scrollY = window.pageYOffset || document.documentElement.scrollTop;
        var triggerRect = triggerItem.getBoundingClientRect();
        // Always place to the right of the menu
        panel.style.top = (triggerRect.top + scrollY) + 'px';
        panel.style.left = (menuRect.right + scrollX + 4) + 'px';
    }

    // ===================== COLUMNS PANEL =====================
    function buildColumnsPanel(table, menu) {
        var existing = document.querySelector('.gs-sub-panel[data-owner="' + (menu.id || '') + '"][data-panel="columns"]');
        if (existing) {
            var colItem = menu.querySelector('[data-action="columns"]');
            if (colItem) positionSubPanel(existing, colItem, menu);
            existing.classList.toggle('open');
            return;
        }

        var ths = table.querySelectorAll('thead tr:first-child th');
        if (!ths.length) return;

        var panel = document.createElement('div');
        panel.className = 'gs-sub-panel open';
        panel.setAttribute('data-panel', 'columns');
        panel.setAttribute('data-owner', menu.id || '');
        panel.addEventListener('click', function (e) { e.stopPropagation(); });

        var header = document.createElement('div');
        header.className = 'gs-panel-header';
        header.innerHTML = '<span>Select Columns</span>';
        var resetBtn = document.createElement('button');
        resetBtn.type = 'button';
        resetBtn.className = 'gs-panel-action';
        resetBtn.textContent = 'Show All';
        resetBtn.addEventListener('click', function () {
            panel.querySelectorAll('input[type="checkbox"]').forEach(function (cb) {
                if (!cb.checked) { cb.checked = true; cb.dispatchEvent(new Event('change')); }
            });
        });
        header.appendChild(resetBtn);
        panel.appendChild(header);

        var uid = 'gsc_' + Math.random().toString(36).slice(2, 8);
        ths.forEach(function (th, idx) {
            var colName = th.textContent.trim().replace(/[*↕↑↓]/g, '').trim();
            if (!colName) return;

            var row = document.createElement('div');
            row.className = 'gs-check-item';
            var cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.checked = th.style.display !== 'none';
            cb.id = uid + '_' + idx;
            cb.addEventListener('change', function () {
                var d = this.checked ? '' : 'none';
                th.style.display = d;
                table.querySelectorAll('tbody tr').forEach(function (tr) {
                    var cells = tr.querySelectorAll('td');
                    if (cells[idx]) cells[idx].style.display = d;
                });
                var filterRow = table.querySelector('.gs-filter-row');
                if (filterRow) {
                    var fths = filterRow.querySelectorAll('th');
                    if (fths[idx]) fths[idx].style.display = d;
                }
            });
            var label = document.createElement('label');
            label.htmlFor = cb.id;
            label.textContent = colName;
            row.appendChild(cb);
            row.appendChild(label);
            panel.appendChild(row);
        });

        // Append to body (portal) and position next to the trigger
        document.body.appendChild(panel);
        var colItem = menu.querySelector('[data-action="columns"]');
        if (colItem) positionSubPanel(panel, colItem, menu);
    }

    // ===================== EXPORT PANEL =====================
    function buildExportPanel(table, menu) {
        var existing = document.querySelector('.gs-sub-panel[data-owner="' + (menu.id || '') + '"][data-panel="export"]');
        if (existing) {
            var expItem = menu.querySelector('[data-action="export"]');
            if (expItem) positionSubPanel(existing, expItem, menu);
            existing.classList.toggle('open');
            return;
        }

        var panel = document.createElement('div');
        panel.className = 'gs-sub-panel open';
        panel.setAttribute('data-panel', 'export');
        panel.setAttribute('data-owner', menu.id || '');
        panel.addEventListener('click', function (e) { e.stopPropagation(); });

        var header = document.createElement('div');
        header.className = 'gs-panel-header';
        header.innerHTML = '<span>Export As</span>';
        panel.appendChild(header);

        var csvItem = document.createElement('div');
        csvItem.className = 'gs-action-item';
        csvItem.innerHTML = '<i class="fas fa-file-csv" style="color:#248567;"></i> CSV (.csv)';
        csvItem.addEventListener('click', function () { exportTable(table, 'csv'); closeAllMenus(null); });
        panel.appendChild(csvItem);

        var tsvItem = document.createElement('div');
        tsvItem.className = 'gs-action-item';
        tsvItem.innerHTML = '<i class="fas fa-file-alt" style="color:#6366f1;"></i> Tab-separated (.tsv)';
        tsvItem.addEventListener('click', function () { exportTable(table, 'tsv'); closeAllMenus(null); });
        panel.appendChild(tsvItem);

        var jsonItem = document.createElement('div');
        jsonItem.className = 'gs-action-item';
        jsonItem.innerHTML = '<i class="fas fa-file-code" style="color:#f59e0b;"></i> JSON (.json)';
        jsonItem.addEventListener('click', function () { exportTable(table, 'json'); closeAllMenus(null); });
        panel.appendChild(jsonItem);

        // Append to body (portal) and position next to the trigger
        document.body.appendChild(panel);
        var expTrigger = menu.querySelector('[data-action="export"]');
        if (expTrigger) positionSubPanel(panel, expTrigger, menu);
    }

    function exportTable(table, format) {
        var headers = [];
        var rows = [];
        var ths = table.querySelectorAll('thead tr:first-child th');
        var visibleCols = [];

        ths.forEach(function (th, idx) {
            if (th.style.display !== 'none') {
                headers.push(th.textContent.trim().replace(/[*↕↑↓]/g, '').trim());
                visibleCols.push(idx);
            }
        });

        table.querySelectorAll('tbody tr').forEach(function (tr) {
            if (tr.style.display === 'none') return;
            var cells = tr.querySelectorAll('td');
            var row = [];
            visibleCols.forEach(function (idx) {
                row.push(cells[idx] ? cells[idx].textContent.trim() : '');
            });
            rows.push(row);
        });

        var content, mime, ext;

        if (format === 'csv') {
            content = [headers.map(q).join(',')].concat(rows.map(function (r) { return r.map(q).join(','); })).join('\n');
            mime = 'text/csv;charset=utf-8;';
            ext = 'csv';
        } else if (format === 'tsv') {
            content = [headers.join('\t')].concat(rows.map(function (r) { return r.join('\t'); })).join('\n');
            mime = 'text/tab-separated-values;charset=utf-8;';
            ext = 'tsv';
        } else if (format === 'json') {
            var arr = rows.map(function (r) {
                var obj = {};
                headers.forEach(function (h, i) { obj[h] = r[i]; });
                return obj;
            });
            content = JSON.stringify(arr, null, 2);
            mime = 'application/json;charset=utf-8;';
            ext = 'json';
        }

        var blob = new Blob([content], { type: mime });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'grid-export.' + ext;
        a.click();
        URL.revokeObjectURL(url);
        toast('Exported as ' + ext.toUpperCase());
    }

    function q(val) { return '"' + String(val).replace(/"/g, '""') + '"'; }

    // ===================== SAVE / RESET LAYOUT =====================
    function saveLayout(wrapper, table) {
        var key = layoutKey(wrapper);
        var ths = table.querySelectorAll('thead tr:first-child th');
        var state = [];
        ths.forEach(function (th) { state.push(th.style.display !== 'none'); });
        try { localStorage.setItem(key, JSON.stringify(state)); } catch (e) { /* quota */ }
        toast('Layout saved');
    }

    function resetLayout(wrapper, table) {
        var key = layoutKey(wrapper);
        try { localStorage.removeItem(key); } catch (e) {}
        // Show all columns
        var ths = table.querySelectorAll('thead tr:first-child th');
        ths.forEach(function (th, idx) {
            th.style.display = '';
            table.querySelectorAll('tbody tr').forEach(function (tr) {
                var cells = tr.querySelectorAll('td');
                if (cells[idx]) cells[idx].style.display = '';
            });
        });
        // Reset any existing columns panel checkboxes
        var panel = wrapper.querySelector('.gs-sub-panel[data-panel="columns"]');
        if (panel) {
            panel.querySelectorAll('input[type="checkbox"]').forEach(function (cb) { cb.checked = true; });
        }
        toast('Layout reset');
    }

    function restoreLayout(wrapper, table) {
        var key = layoutKey(wrapper);
        try {
            var saved = localStorage.getItem(key);
            if (!saved) return;
            var state = JSON.parse(saved);
            var ths = table.querySelectorAll('thead tr:first-child th');
            ths.forEach(function (th, idx) {
                if (idx < state.length && !state[idx]) {
                    th.style.display = 'none';
                    table.querySelectorAll('tbody tr').forEach(function (tr) {
                        var cells = tr.querySelectorAll('td');
                        if (cells[idx]) cells[idx].style.display = 'none';
                    });
                }
            });
        } catch (e) {}
    }

    // ===================== HANDLE ACTION =====================
    function handleAction(action, wrapper, menu) {
        var table = findTable(wrapper);

        switch (action) {
            case 'showFilters':
                if (table) toggleFilters(table);
                break;

            case 'columns':
                if (table) buildColumnsPanel(table, menu);
                return true;

            case 'export':
                if (table) buildExportPanel(table, menu);
                return true;

            case 'expandRows':
                if (table) table.querySelectorAll('tbody tr').forEach(function (r) { if (r.style.display === 'none') r.style.display = ''; });
                break;

            case 'collapseRows':
                if (table) {
                    var rows = table.querySelectorAll('tbody tr');
                    rows.forEach(function (r, i) { if (i > 0) r.style.display = 'none'; });
                }
                break;

            case 'saveLayout':
                if (table) saveLayout(wrapper, table);
                break;

            case 'resetLayout':
                if (table) resetLayout(wrapper, table);
                break;

            case 'reloadGrid':
                window.location.reload();
                return true;

            case 'increaseHeight':
                var wrappers = [
                    '.data-table-wrapper', '.hierarchy-table-wrapper',
                    '.interfaces-table-wrapper', '.stakeholders-table-container',
                    '.relationships-table-wrapper', '.stakeholder-community-table-container'
                ];
                var tw = null;
                var par = wrapper.parentElement;
                while (par && !tw) {
                    for (var i = 0; i < wrappers.length; i++) {
                        tw = par.querySelector(wrappers[i]);
                        if (tw) break;
                    }
                    par = par.parentElement;
                }
                if (tw) {
                    var h = tw.style.maxHeight ? parseInt(tw.style.maxHeight) : 500;
                    tw.style.maxHeight = (h + 200) + 'px';
                    tw.style.overflow = 'auto';
                }
                break;

            case 'copyGrid':
                if (table) {
                    var text = Array.from(table.querySelectorAll('tr')).map(function (r) {
                        return Array.from(r.querySelectorAll('th, td'))
                            .filter(function (c) { return c.style.display !== 'none'; })
                            .map(function (c) { return c.textContent.trim(); })
                            .join('\t');
                    }).join('\n');
                    navigator.clipboard.writeText(text).then(function () { toast('Copied to clipboard'); });
                }
                break;
        }
        return false;
    }

    // ===================== CLOSE HELPERS =====================
    function closeAllMenus(except) {
        // Close all portal menus in body
        document.querySelectorAll('.grid-settings-menu.open').forEach(function (m) {
            if (m !== except) {
                m.classList.remove('open');
            }
        });
        // Close all portal sub-panels in body
        document.querySelectorAll('.gs-sub-panel.open').forEach(function (p) {
            p.classList.remove('open');
        });
    }

    function closeMenu(menu) {
        menu.classList.remove('open');
        // Close sub-panels owned by this menu
        var ownerId = menu.id || '';
        if (ownerId) {
            document.querySelectorAll('.gs-sub-panel[data-owner="' + ownerId + '"]').forEach(function (p) {
                p.classList.remove('open');
            });
        }
    }

    // ===================== INIT =====================
    function init(containerEl) {
        if (!containerEl) return;

        containerEl.querySelectorAll('.grid-settings-btn').forEach(function (btn) {
            if (btn._gsInit) return;
            btn._gsInit = true;

            var wrapper = btn.closest('.grid-settings-wrapper');
            if (!wrapper) return;

            var gridId = btn.dataset.gridId || ('gs_' + Math.random().toString(36).slice(2, 8));

            // Create portal menu in document.body
            var menu = createPortalMenu(gridId);
            // Store reference on wrapper for findTable etc.
            wrapper._gsMenu = menu;
            menu._gsWrapper = wrapper;

            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                closeAllMenus(menu);
                positionMenu(menu, btn);
                var isOpen = menu.classList.toggle('open');
                btn.setAttribute('aria-expanded', isOpen);
                if (!isOpen) {
                    // Close owned sub-panels
                    document.querySelectorAll('.gs-sub-panel[data-owner="' + menu.id + '"]').forEach(function (p) {
                        p.classList.remove('open');
                    });
                }
            });

            menu.addEventListener('click', function (e) {
                var item = e.target.closest('.gs-item');
                if (!item) return;
                e.stopPropagation();
                var keepOpen = handleAction(item.dataset.action, wrapper, menu);
                if (!keepOpen) {
                    closeMenu(menu);
                    btn.setAttribute('aria-expanded', 'false');
                }
            });

            // Restore saved layout on init
            var table = findTable(wrapper);
            if (table) restoreLayout(wrapper, table);
        });
    }

    // ===================== OUTSIDE CLICK =====================
    document.addEventListener('click', function (e) {
        // Don't close if clicking inside a menu or sub-panel
        if (e.target.closest('.grid-settings-menu') || e.target.closest('.gs-sub-panel')) return;
        closeAllMenus(null);
    });

    // ===================== PUBLIC API =====================
    window.GridSettings = { html: html, init: init };
    window._gridSettingsHtml = html;
    window._initGridSettings = init;
})();
