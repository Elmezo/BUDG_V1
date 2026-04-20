/**
 * Draggable column widths for .search-table (Unison search).
 * Uses a dedicated <style> sheet (#search-table-col-widths) to assign
 * widths per data-column via CSS rules — one DOM write per frame,
 * and !important so auto-layout can't undo them on mouseup.
 */
(function (global) {
    'use strict';

    var MIN_WIDTH = 40;
    var STYLE_ID = 'search-table-col-widths';
    var DRAG_STYLE_ID = 'search-table-col-drag';
    var DEBUG = true;

    function dlog() {
        if (!DEBUG) return;
        try {
            var args = Array.prototype.slice.call(arguments);
            var out = args.map(function (a) {
                if (a && typeof a === 'object') {
                    try { return JSON.stringify(a); } catch (_) { return String(a); }
                }
                return String(a);
            }).join(' ');
            console.log('[RESIZE] ' + out);
        } catch (_) { /* noop */ }
    }

    function getTable() {
        return document.querySelector('.data-table-wrapper .search-table') ||
            document.querySelector('.search-table');
    }

    function getCategory() {
        return typeof getActiveCategoryWithFallback === 'function'
            ? getActiveCategoryWithFallback()
            : null;
    }

    function ensureStyleEl() {
        var el = document.getElementById(STYLE_ID);
        if (!el) {
            el = document.createElement('style');
            el.id = STYLE_ID;
            el.type = 'text/css';
            document.head.appendChild(el);
        }
        return el;
    }

    function ensureDragStyleEl() {
        var el = document.getElementById(DRAG_STYLE_ID);
        if (!el) {
            el = document.createElement('style');
            el.id = DRAG_STYLE_ID;
            el.type = 'text/css';
            document.head.appendChild(el);
        }
        return el;
    }

    function setDragStyleForColumn(columnKey, widthPx) {
        var el = ensureDragStyleEl();
        if (!columnKey) {
            el.textContent = '';
            return;
        }
        var esc = escSelectorValue(columnKey);
        var decl = 'width:' + widthPx + 'px!important;min-width:' + widthPx + 'px!important;max-width:' + widthPx + 'px!important;';
        el.textContent =
            '.search-table th[data-column="' + esc + '"]{' + decl + '}' +
            '.search-table td[data-column="' + esc + '"]{' + decl + '}';
    }

    function clearDragStyle() {
        var el = document.getElementById(DRAG_STYLE_ID);
        if (el) el.textContent = '';
    }

    // Map: category -> { columnKey: widthPx }
    var widthsByCategory = Object.create(null);

    function escSelectorValue(value) {
        if (value == null) return '';
        if (typeof CSS !== 'undefined' && CSS.escape) return CSS.escape(String(value));
        return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    }

    function rebuildStyleSheet() {
        var el = ensureStyleEl();
        // Only emit rules for the currently-active category so column names
        // that repeat across categories (e.g. "Name", "Description") keep
        // per-category widths without bleeding across tables.
        var cat = getCategory();
        var map = (cat && widthsByCategory[cat]) ? widthsByCategory[cat] : {};
        var chunks = [];
        Object.keys(map).forEach(function (colKey) {
            var w = parseInt(map[colKey], 10);
            if (isNaN(w) || w < MIN_WIDTH) return;
            var esc = escSelectorValue(colKey);
            var decl = 'width:' + w + 'px!important;min-width:' + w + 'px!important;max-width:' + w + 'px!important;';
            chunks.push('.search-table th[data-column="' + esc + '"]{' + decl + '}');
            chunks.push('.search-table td[data-column="' + esc + '"]{' + decl + '}');
        });
        el.textContent = chunks.join('\n');
    }

    function setWidth(category, columnKey, widthPx) {
        if (!category || !columnKey) return;
        var w = Math.max(MIN_WIDTH, parseInt(widthPx, 10) || 0);
        if (!widthsByCategory[category]) widthsByCategory[category] = {};
        widthsByCategory[category][columnKey] = w;
        rebuildStyleSheet();
    }

    function loadWidthsFromStore(category) {
        if (!category) return;
        var map = null;
        if (typeof getColumnWidthsForCategory === 'function') {
            map = getColumnWidthsForCategory(category);
        }
        if (map && typeof map === 'object') {
            var clean = {};
            Object.keys(map).forEach(function (k) {
                var n = parseInt(map[k], 10);
                if (!isNaN(n) && n >= MIN_WIDTH) clean[k] = n;
            });
            widthsByCategory[category] = clean;
        } else {
            widthsByCategory[category] = {};
        }
        rebuildStyleSheet();
        dlog('loadWidthsFromStore', category, widthsByCategory[category]);
    }

    function attachResizersToTable(table) {
        if (!table) return;
        var count = 0;
        table.querySelectorAll('th[data-column]').forEach(function (th) {
            if (th.querySelector('.col-resizer')) return;
            if (th.style.display === 'none') return;
            var col = th.getAttribute('data-column');
            if (!col) return;
            var resizer = document.createElement('span');
            resizer.className = 'col-resizer';
            resizer.setAttribute('aria-hidden', 'true');
            resizer.addEventListener('mousedown', function (e) {
                e.preventDefault();
                e.stopPropagation();
                startResize(e, table, th, col);
            });
            th.appendChild(resizer);
            count++;
        });
        dlog('attachResizersToTable attached=', count);
    }

    function startResize(e, table, th, columnKey) {
        var cat = getCategory();
        // Snapshot current widths of all other visible columns so they stay put
        // while this one is resized. Without this, table-layout: auto would
        // redistribute the available width across all columns.
        if (cat) {
            if (!widthsByCategory[cat]) widthsByCategory[cat] = {};
            var bucket = widthsByCategory[cat];
            var headerThs = table.querySelectorAll('thead th[data-column]');
            for (var i = 0; i < headerThs.length; i++) {
                var otherTh = headerThs[i];
                if (otherTh === th) continue;
                if (otherTh.style.display === 'none') continue;
                var key = otherTh.getAttribute('data-column');
                if (!key) continue;
                if (bucket[key] != null) continue;
                var curW = otherTh.offsetWidth;
                if (curW >= MIN_WIDTH) bucket[key] = curW;
            }
            rebuildStyleSheet();
        }
        table.classList.add('is-resizing');
        var startX = e.clientX;
        var startW = th.offsetWidth;
        dlog('startResize', { category: cat, column: columnKey, startW: startW, startX: startX });

        var latestX = startX;
        var rafId = null;
        var currentW = startW;

        // Fast-path during drag: one textContent write on a dedicated <style>
        // scoped to this column. Applies to th AND td with !important so it
        // overrides any CSS min-width and content-based auto-layout minimum.
        function flushMove() {
            rafId = null;
            var dx = latestX - startX;
            currentW = Math.max(MIN_WIDTH, startW + dx);
            setDragStyleForColumn(columnKey, currentW);
        }

        function onMove(ev) {
            latestX = ev.clientX;
            if (rafId == null) {
                rafId = global.requestAnimationFrame(flushMove);
            }
        }

        function onUp(ev) {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
            if (rafId != null) {
                global.cancelAnimationFrame(rafId);
                rafId = null;
            }
            var endX = ev && typeof ev.clientX === 'number' ? ev.clientX : latestX;
            currentW = Math.max(MIN_WIDTH, startW + (endX - startX));
            // Persistent rule wins → clear the transient drag rule.
            if (cat) setWidth(cat, columnKey, currentW);
            clearDragStyle();
            table.classList.remove('is-resizing');
            dlog('onUp committed width', { category: cat, column: columnKey, width: currentW });
            if (cat && typeof global.onSearchColumnWidthCommitted === 'function') {
                // Persist widths of *all* currently visible columns so the
                // rest don't snap back to auto-distribution on next render.
                var bucket = widthsByCategory[cat] || {};
                Object.keys(bucket).forEach(function (k) {
                    global.onSearchColumnWidthCommitted(cat, k, bucket[k]);
                });
            }
        }

        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
    }

    function refreshSearchColumnResizers() {
        var table = getTable();
        dlog('refreshSearchColumnResizers', { hasTable: !!table, category: getCategory() });
        attachResizersToTable(table);
        loadWidthsFromStore(getCategory());
    }

    global.initSearchColumnResizers = function () {
        dlog('initSearchColumnResizers');
        refreshSearchColumnResizers();
        var wrap = document.querySelector('.data-table-wrapper');
        if (!wrap || wrap._colResizeObs) return;
        var scheduled = false;
        var obs = new MutationObserver(function () {
            if (scheduled) return;
            scheduled = true;
            setTimeout(function () {
                scheduled = false;
                refreshSearchColumnResizers();
            }, 120);
        });
        obs.observe(wrap, { childList: true, subtree: true });
        wrap._colResizeObs = obs;
    };

    global.refreshSearchColumnResizers = refreshSearchColumnResizers;
    global.searchTableWidths = {
        setWidth: setWidth,
        loadWidthsFromStore: loadWidthsFromStore,
        rebuildStyleSheet: rebuildStyleSheet,
        dump: function () { return JSON.parse(JSON.stringify(widthsByCategory)); }
    };
}(typeof window !== 'undefined' ? window : this));
