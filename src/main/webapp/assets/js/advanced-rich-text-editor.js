/**
 * Advanced Rich Text Editor - Shared Utility
 * Mirrors the dashboard → new widget → text editor toolbar exactly.
 *
 * Usage:
 *   window.toggleAdvancedRichTextEditor('myTextareaId', buttonElement);
 *   window.syncAdvancedRichTextToTextarea('myTextareaId');
 *   window.getAdvancedRichTextContent('myTextareaId');
 */
(function (window) {
    'use strict';

    function _syncEditorValue(textarea, editor) {
        if (!textarea || !editor) return;
        textarea.value = editor.innerHTML.trim();
    }

    /* ════════════════════════════════════════════════════════════
       TOGGLE  –  create / destroy the editor
       ════════════════════════════════════════════════════════════ */
    function toggleAdvancedRichTextEditor(textareaId, toggleBtn) {
        var textarea = document.getElementById(textareaId);
        if (!textarea) return;

        /* ── If editor already open → destroy & sync back ── */
        var existing = textarea.parentElement.querySelector('.arté-container');
        if (existing) {
            if (existing._selectionChangeHandler) {
                document.removeEventListener('selectionchange', existing._selectionChangeHandler);
            }
            if (existing._syncObserver) {
                existing._syncObserver.disconnect();
            }
            var editorDiv = existing.querySelector('.arté-editor-content');
            if (editorDiv) textarea.value = editorDiv.innerHTML.trim();
            existing.remove();

            // Remove any old preview
            var oldPreview = textarea.parentElement.querySelector('.arté-preview');
            if (oldPreview) oldPreview.remove();

            // If the textarea contains HTML → show a rendered preview div
            // instead of the raw textarea (which can't render HTML).
            var htmlVal = (textarea.value || '').trim();
            if (htmlVal && /<[a-z][\s\S]*>/i.test(htmlVal)) {
                textarea.style.display = 'none';  // keep hidden for form data
                var preview = document.createElement('div');
                preview.className = 'arté-preview';
                preview.innerHTML = htmlVal;
                // Clicking the preview opens the editor again
                preview.addEventListener('click', function () {
                    toggleAdvancedRichTextEditor(textareaId, toggleBtn);
                });
                textarea.parentElement.insertBefore(preview, textarea.nextSibling);
            } else {
                textarea.style.display = '';
            }

            if (toggleBtn) toggleBtn.textContent = window.I18n && window.I18n.t ? window.I18n.t('button.showEditor') : 'Show Editor';
            return;
        }

        /* ── If preview is showing → remove it before opening editor ── */
        var existingPreview = textarea.parentElement.querySelector('.arté-preview');
        if (existingPreview) existingPreview.remove();

        /* ══════════ Build editor UI ══════════ */
        var container = document.createElement('div');
        container.className = 'arté-container';

        /* ── Toolbar wrapper ── */
        var toolbar = document.createElement('div');
        toolbar.className = 'arté-toolbar';

        /* ── Row 1 ── */
        var row1 = document.createElement('div');
        row1.className = 'arté-toolbar-row';

        // --- Lightbulb / Suggestions dropdown ---
        row1.appendChild(_buildDropdown(
            '<i class="fas fa-lightbulb"></i>',
            'Suggestions',
            [
                { label: 'Template 1', action: function () { } },
                { label: 'Template 2', action: function () { } }
            ]
        ));

        // --- Bold ---
        row1.appendChild(_cmdBtn('B', 'Bold', 'bold'));

        // --- Italic ---
        row1.appendChild(_cmdBtn('I', 'Italic', 'italic'));

        // --- Underline ---
        row1.appendChild(_cmdBtn('U', 'Underline', 'underline'));

        // --- Pen/Marker (highlight) ---
        row1.appendChild(_cmdBtnIcon('fas fa-pen', 'Pen/Marker', function (editor) {
            document.execCommand('backColor', false, '#FFFF00');
            editor.focus();
        }));

        // --- Text Color dropdown with colour grid ---
        row1.appendChild(_buildColorPicker());

        // --- Bullet list ---
        row1.appendChild(_cmdBtnIcon('fas fa-list-ul', 'Bullet List', function (editor) {
            document.execCommand('insertUnorderedList', false, null);
            editor.focus();
        }));

        // --- Numbered list ---
        row1.appendChild(_cmdBtnIcon('fas fa-list-ol', 'Numbered List', function (editor) {
            document.execCommand('insertOrderedList', false, null);
            editor.focus();
        }));

        // --- Alignment dropdown ---
        row1.appendChild(_buildDropdown(
            '<i class="fas fa-align-left"></i>',
            'Text Alignment',
            [
                { label: '<i class="fas fa-align-left"></i> Align Left', action: function () { document.execCommand('justifyLeft', false, null); } },
                { label: '<i class="fas fa-align-center"></i> Align Center', action: function () { document.execCommand('justifyCenter', false, null); } },
                { label: '<i class="fas fa-align-right"></i> Align Right', action: function () { document.execCommand('justifyRight', false, null); } },
                { label: '<i class="fas fa-align-justify"></i> Justify', action: function () { document.execCommand('justifyFull', false, null); } }
            ]
        ));

        // --- Table dropdown (8×8 grid) ---
        row1.appendChild(_buildTableGridDropdown());

        /* ── Row 2 ── */
        var row2 = document.createElement('div');
        row2.className = 'arté-toolbar-row';

        // --- Link ---
        row2.appendChild(_cmdBtnIcon('fas fa-link', 'Link', function (editor) {
            var url = prompt('Enter URL:', '');
            if (url) {
                var formatted = _formatUrl(url);
                document.execCommand('createLink', false, formatted);
            }
            editor.focus();
        }));

        // --- Image (modal) ---
        row2.appendChild(_cmdBtnIcon('fas fa-image', 'Image', function (editor) {
            _showImageUploadModal(editor);
        }));

        // --- Video ---
        row2.appendChild(_cmdBtnIcon('fas fa-video', 'Video', function (editor) {
            var videoUrl = prompt('Enter video URL:', '');
            if (videoUrl) {
                var formatted = _formatUrl(videoUrl);
                var html = '<iframe src="' + formatted + '" width="560" height="315" frameborder="0" allowfullscreen></iframe>';
                document.execCommand('insertHTML', false, html);
            }
            editor.focus();
        }));

        // --- Fullscreen ---
        row2.appendChild(_cmdBtnIcon('fas fa-expand', 'Fullscreen', function (editor) {
            var c = editor.closest('.arté-container');
            if (c) c.classList.toggle('arté-fullscreen');
            editor.focus();
        }));

        // --- Code view ---
        row2.appendChild(_cmdBtnIcon('fas fa-code', 'Code View', function (editor) {
            if (editor.hasAttribute('data-code-view')) {
                editor.removeAttribute('data-code-view');
                editor.contentEditable = 'true';
                editor.innerHTML = editor.getAttribute('data-html-backup') || '';
            } else {
                editor.setAttribute('data-html-backup', editor.innerHTML);
                editor.setAttribute('data-code-view', 'true');
                editor.contentEditable = 'true';
                editor.textContent = editor.innerHTML;
            }
            editor.focus();
        }));

        // --- Help ---
        row2.appendChild(_cmdBtnIcon('fas fa-question-circle', 'Help', function () {
            alert(
                'Rich Text Editor Shortcuts:\n' +
                'Ctrl+B  Bold\n' +
                'Ctrl+I  Italic\n' +
                'Ctrl+U  Underline\n' +
                'Ctrl+Z  Undo\n' +
                'Ctrl+Y  Redo'
            );
        }));

        toolbar.appendChild(row1);
        toolbar.appendChild(row2);

        /* ── Editor content area ── */
        var editor = document.createElement('div');
        editor.className = 'arté-editor-content';
        editor.contentEditable = 'true';
        if (textarea.value) {
            editor.innerHTML = textarea.value;
        }
        var liveSync = function () { _syncEditorValue(textarea, editor); };
        var refreshStates = function () { _refreshCommandButtonStates(container); };

        // Keep textarea updated while editor is open so form saves don't depend on hiding the editor.
        editor.addEventListener('input', liveSync);
        editor.addEventListener('keyup', liveSync);
        editor.addEventListener('blur', liveSync);
        editor.addEventListener('keyup', refreshStates);
        editor.addEventListener('mouseup', refreshStates);
        editor.addEventListener('focus', refreshStates);
        editor.addEventListener('click', refreshStates);
        editor.addEventListener('paste', function () {
            setTimeout(liveSync, 0);
            setTimeout(refreshStates, 0);
        });

        // Capture DOM mutations caused by toolbar actions and programmatic inserts.
        var observer = new MutationObserver(liveSync);
        observer.observe(editor, { childList: true, subtree: true, characterData: true, attributes: true });
        container._syncObserver = observer;

        // Wire up all deferred button callbacks with editor reference
        toolbar.querySelectorAll('[data-arté-deferred]').forEach(function (btn) {
            // already wired during construction via closure
        });

        // Store editor reference on container for button callbacks
        container._editor = editor;

        /* ── Image click → show image toolbar ── */
        editor.addEventListener('click', function (e) {
            _removeImageToolbar(container);
            _removeTableToolbar(container);

            if (e.target.tagName === 'IMG') {
                _showImageToolbar(e.target, editor, container);
            }
            if (e.target.tagName === 'TD' || e.target.tagName === 'TH') {
                // Select cell contents
                var range = document.createRange();
                range.selectNodeContents(e.target);
                var sel = window.getSelection();
                sel.removeAllRanges();
                sel.addRange(range);
                _showTableToolbar(e.target, editor, container);
            }
            liveSync();
            refreshStates();
        });

        /* ── Footer ── */
        var footer = document.createElement('div');
        footer.className = 'arté-footer';
        var hideLink = document.createElement('a');
        hideLink.href = '#';
        hideLink.textContent = 'Hide editor';
        hideLink.className = 'arté-hide-link';
        hideLink.addEventListener('click', function (e) {
            e.preventDefault();
            liveSync();
            toggleAdvancedRichTextEditor(textareaId, toggleBtn);
        });
        footer.appendChild(hideLink);

        /* ── Assemble ── */
        container.appendChild(toolbar);
        container.appendChild(editor);
        container.appendChild(footer);

        textarea.style.display = 'none';
        textarea.parentElement.appendChild(container);
        if (toggleBtn) toggleBtn.textContent = window.I18n && window.I18n.t ? window.I18n.t('button.hideEditor') : 'Hide Editor';
        editor.focus();

        var selectionHandler = function () {
            var sel = window.getSelection();
            if (!sel || sel.rangeCount === 0) return;
            var node = sel.anchorNode;
            if (!node) return;
            if (node.nodeType === 3) node = node.parentNode;
            if (node && editor.contains(node)) {
                refreshStates();
            }
        };
        container._selectionChangeHandler = selectionHandler;
        document.addEventListener('selectionchange', selectionHandler);

        /* ── Close dropdowns on outside click ── */
        document.addEventListener('click', function _outsideClose(e) {
            if (!document.body.contains(container)) {
                if (container._syncObserver) container._syncObserver.disconnect();
                if (container._selectionChangeHandler) document.removeEventListener('selectionchange', container._selectionChangeHandler);
                document.removeEventListener('click', _outsideClose);
                return;
            }
            // Close image / table toolbars
            if (!e.target.closest('.arté-image-toolbar') && e.target.tagName !== 'IMG') {
                _removeImageToolbar(container);
            }
            if (!e.target.closest('.arté-table-toolbar') && e.target.tagName !== 'TD' && e.target.tagName !== 'TH') {
                _removeTableToolbar(container);
            }
            // Close dropdown menus
            if (!e.target.closest('.arté-dropdown')) {
                container.querySelectorAll('.arté-dropdown').forEach(function (d) {
                    d.classList.remove('active');
                });
            }
            liveSync();
            refreshStates();
        });

        /* ── Toolbar button mousedown → prevent editor blur ── */
        toolbar.querySelectorAll('.arté-btn').forEach(function (btn) {
            btn.addEventListener('mousedown', function (e) { e.preventDefault(); });
            btn.addEventListener('click', function () {
                setTimeout(liveSync, 0);
                setTimeout(refreshStates, 0);
            });
        });
        liveSync();
        refreshStates();
    }

    /* ════════════════════════════════════════════════════════════
       HELPERS — toolbar buttons
       ════════════════════════════════════════════════════════════ */

    /** Simple command button (text label) */
    function _cmdBtn(label, title, command) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'arté-btn';
        btn.title = title;
        btn.setAttribute('data-arté-command', command);
        btn.innerHTML = label;
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            var editor = _findEditor(btn);
            if (editor) {
                document.execCommand(command, false, null);
                editor.focus();
            }
        });
        return btn;
    }

    function _refreshCommandButtonStates(container) {
        if (!container) return;
        var editor = container.querySelector('.arté-editor-content');
        if (!editor) return;

        container.querySelectorAll('.arté-btn[data-arté-command]').forEach(function (btn) {
            var command = btn.getAttribute('data-arté-command');
            var isActive = false;
            try {
                isActive = document.queryCommandState(command);
            } catch (e) {
                isActive = false;
            }
            btn.classList.toggle('active', Boolean(isActive));
        });
    }

    /** Icon-based button with custom callback */
    function _cmdBtnIcon(iconClass, title, callback) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'arté-btn';
        btn.title = title;
        var icon = document.createElement('i');
        icon.className = iconClass;
        btn.appendChild(icon);
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            var editor = _findEditor(btn);
            if (editor) callback(editor);
        });
        return btn;
    }

    /** Find editor content div from any child of the container */
    function _findEditor(el) {
        var container = el.closest('.arté-container');
        return container ? container.querySelector('.arté-editor-content') : null;
    }

    /* ════════════════════════════════════════════════════════════
       Dropdown builder
       ════════════════════════════════════════════════════════════ */
    function _buildDropdown(triggerHTML, title, items) {
        var wrapper = document.createElement('div');
        wrapper.className = 'arté-dropdown';

        var trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'arté-btn';
        trigger.title = title;
        trigger.innerHTML = triggerHTML + '<i class="fas fa-chevron-down arté-caret"></i>';
        trigger.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            var isActive = wrapper.classList.contains('active');
            // Close all sibling dropdowns
            var row = wrapper.closest('.arté-toolbar-row');
            if (row) row.querySelectorAll('.arté-dropdown').forEach(function (d) { d.classList.remove('active'); });
            if (!isActive) wrapper.classList.add('active');
        });

        var menu = document.createElement('div');
        menu.className = 'arté-dropdown-menu';

        items.forEach(function (item) {
            var a = document.createElement('a');
            a.href = '#';
            a.className = 'arté-dropdown-item';
            a.innerHTML = item.label;
            a.addEventListener('click', function (e) {
                e.preventDefault();
                var editor = _findEditor(wrapper);
                if (editor) { item.action(editor); editor.focus(); }
                wrapper.classList.remove('active');
            });
            menu.appendChild(a);
        });

        wrapper.appendChild(trigger);
        wrapper.appendChild(menu);
        return wrapper;
    }

    /* ════════════════════════════════════════════════════════════
       Colour picker dropdown (grid of 8 preset colours)
       ════════════════════════════════════════════════════════════ */
    function _buildColorPicker() {
        var colors = ['#000000', '#FF0000', '#00FF00', '#0000FF', '#FFFF00', '#FF00FF', '#00FFFF', '#808080'];

        var wrapper = document.createElement('div');
        wrapper.className = 'arté-dropdown';

        var trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'arté-btn arté-color-trigger';
        trigger.title = 'Text Color';
        trigger.innerHTML = 'A<i class="fas fa-chevron-down arté-caret"></i>';
        trigger.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            var isActive = wrapper.classList.contains('active');
            var row = wrapper.closest('.arté-toolbar-row');
            if (row) row.querySelectorAll('.arté-dropdown').forEach(function (d) { d.classList.remove('active'); });
            if (!isActive) wrapper.classList.add('active');
        });

        var menu = document.createElement('div');
        menu.className = 'arté-dropdown-menu arté-color-menu';

        var grid = document.createElement('div');
        grid.className = 'arté-color-grid';

        colors.forEach(function (c) {
            var swatch = document.createElement('span');
            swatch.className = 'arté-color-option';
            swatch.style.background = c;
            swatch.setAttribute('data-color', c);
            swatch.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                var editor = _findEditor(wrapper);
                if (editor) {
                    document.execCommand('foreColor', false, c);
                    editor.focus();
                }
                wrapper.classList.remove('active');
            });
            grid.appendChild(swatch);
        });

        menu.appendChild(grid);
        wrapper.appendChild(trigger);
        wrapper.appendChild(menu);
        return wrapper;
    }

    /* ════════════════════════════════════════════════════════════
       Table grid dropdown  (8×8 visual grid like the dashboard)
       ════════════════════════════════════════════════════════════ */
    function _buildTableGridDropdown() {
        var wrapper = document.createElement('div');
        wrapper.className = 'arté-dropdown';

        var trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'arté-btn';
        trigger.title = 'Table';
        trigger.innerHTML = '<i class="fas fa-table"></i><i class="fas fa-chevron-down arté-caret"></i>';
        trigger.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            var isActive = wrapper.classList.contains('active');
            var row = wrapper.closest('.arté-toolbar-row');
            if (row) row.querySelectorAll('.arté-dropdown').forEach(function (d) { d.classList.remove('active'); });
            if (!isActive) wrapper.classList.add('active');
        });

        var menu = document.createElement('div');
        menu.className = 'arté-dropdown-menu arté-table-menu';

        var grid = document.createElement('div');
        grid.className = 'arté-table-grid';

        for (var r = 0; r < 8; r++) {
            for (var c = 0; c < 8; c++) {
                (function (row, col) {
                    var cell = document.createElement('div');
                    cell.className = 'arté-table-cell';
                    cell.setAttribute('data-rows', row + 1);
                    cell.setAttribute('data-cols', col + 1);

                    cell.addEventListener('mouseenter', function () {
                        _highlightTableGrid(grid, row + 1, col + 1);
                        if (dimLabel) dimLabel.textContent = (col + 1) + ' x ' + (row + 1);
                    });

                    cell.addEventListener('click', function (e) {
                        e.preventDefault();
                        var editor = _findEditor(wrapper);
                        if (editor) {
                            _insertTable(editor, row + 1, col + 1);
                            editor.focus();
                        }
                        wrapper.classList.remove('active');
                    });

                    grid.appendChild(cell);
                })(r, c);
            }
        }

        grid.addEventListener('mouseleave', function () {
            _clearTableHighlight(grid);
            if (dimLabel) dimLabel.textContent = '0 x 0';
        });

        var dimLabel = document.createElement('div');
        dimLabel.className = 'arté-table-dim';
        dimLabel.textContent = '0 x 0';

        menu.appendChild(grid);
        menu.appendChild(dimLabel);
        wrapper.appendChild(trigger);
        wrapper.appendChild(menu);
        return wrapper;
    }

    function _highlightTableGrid(grid, rows, cols) {
        _clearTableHighlight(grid);
        grid.querySelectorAll('.arté-table-cell').forEach(function (cell) {
            var cr = parseInt(cell.getAttribute('data-rows'));
            var cc = parseInt(cell.getAttribute('data-cols'));
            if (cr <= rows && cc <= cols) cell.classList.add('highlighted');
        });
    }

    function _clearTableHighlight(grid) {
        grid.querySelectorAll('.arté-table-cell').forEach(function (c) { c.classList.remove('highlighted'); });
    }

    function _insertTable(editor, rows, cols) {
        editor.focus();
        try {
            var table = document.createElement('table');
            table.setAttribute('border', '1');
            table.style.borderCollapse = 'collapse';
            table.style.width = '100%';
            for (var r = 0; r < rows; r++) {
                var tr = document.createElement('tr');
                for (var c = 0; c < cols; c++) {
                    var td = document.createElement('td');
                    td.style.border = '1px solid #000';
                    td.style.height = '24px';
                    td.style.minWidth = '50px';
                    td.innerHTML = '&nbsp;';
                    tr.appendChild(td);
                }
                table.appendChild(tr);
            }
            var sel = window.getSelection();
            if (sel.rangeCount > 0) {
                var range = sel.getRangeAt(0);
                range.deleteContents();
                range.insertNode(table);
                var p = document.createElement('p');
                p.innerHTML = '<br>';
                if (table.nextSibling) editor.insertBefore(p, table.nextSibling);
                else editor.appendChild(p);
                range.setStartAfter(p);
                range.setEndAfter(p);
                sel.removeAllRanges();
                sel.addRange(range);
            } else {
                editor.appendChild(table);
                var p2 = document.createElement('p');
                p2.innerHTML = '<br>';
                editor.appendChild(p2);
            }
        } catch (err) {
            var html = '<table border="1" style="border-collapse:collapse;width:100%;">';
            for (var r2 = 0; r2 < rows; r2++) {
                html += '<tr>';
                for (var c2 = 0; c2 < cols; c2++) {
                    html += '<td style="border:1px solid #000;height:24px;min-width:50px;">&nbsp;</td>';
                }
                html += '</tr>';
            }
            html += '</table><p><br></p>';
            editor.innerHTML += html;
        }
    }

    /* ════════════════════════════════════════════════════════════
       Image toolbar  –  appears when clicking an image
       ════════════════════════════════════════════════════════════ */
    function _removeImageToolbar(container) {
        var tb = container.querySelector('.arté-image-toolbar');
        if (tb) tb.remove();
    }

    function _showImageToolbar(imgEl, editor, container) {
        _removeImageToolbar(container);

        var tb = document.createElement('div');
        tb.className = 'arté-image-toolbar';
        tb.innerHTML =
            '<div class="arté-image-toolbar-info">Editing image...</div>' +
            '<div class="arté-toolbar-section">' +
            '  <button type="button" class="arté-toolbar-action-btn" data-size="100">100%</button>' +
            '  <button type="button" class="arté-toolbar-action-btn" data-size="50">50%</button>' +
            '  <button type="button" class="arté-toolbar-action-btn" data-size="25">25%</button>' +
            '</div>' +
            '<div class="arté-toolbar-section">' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="rotateLeft"><i class="fas fa-undo"></i></button>' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="rotateRight"><i class="fas fa-redo"></i></button>' +
            '</div>' +
            '<div class="arté-toolbar-section">' +
            '  <button type="button" class="arté-toolbar-action-btn" data-align="left"><i class="fas fa-align-left"></i></button>' +
            '  <button type="button" class="arté-toolbar-action-btn" data-align="center"><i class="fas fa-align-center"></i></button>' +
            '  <button type="button" class="arté-toolbar-action-btn" data-align="right"><i class="fas fa-align-right"></i></button>' +
            '</div>' +
            '<div class="arté-toolbar-section">' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="delete"><i class="fas fa-trash"></i></button>' +
            '</div>';

        // Give img an id if it doesn't have one
        if (!imgEl.id) imgEl.id = 'arté-img-' + Date.now();

        tb.querySelectorAll('.arté-toolbar-action-btn').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                var target = document.getElementById(imgEl.id);
                if (!target) return;

                if (btn.dataset.size) {
                    target.style.width = btn.dataset.size + '%';
                }
                if (btn.dataset.align) {
                    target.style.display = 'block';
                    if (btn.dataset.align === 'left') { target.style.marginLeft = '0'; target.style.marginRight = 'auto'; }
                    else if (btn.dataset.align === 'center') { target.style.marginLeft = 'auto'; target.style.marginRight = 'auto'; }
                    else if (btn.dataset.align === 'right') { target.style.marginLeft = 'auto'; target.style.marginRight = '0'; }
                }
                if (btn.dataset.action === 'delete') {
                    target.remove();
                    _removeImageToolbar(container);
                }
                if (btn.dataset.action === 'rotateLeft') {
                    var rot = _getRotation(target);
                    target.style.transform = 'rotate(' + (rot - 90) + 'deg)';
                }
                if (btn.dataset.action === 'rotateRight') {
                    var rot2 = _getRotation(target);
                    target.style.transform = 'rotate(' + (rot2 + 90) + 'deg)';
                }
            });
        });

        // Insert before editor content
        editor.parentNode.insertBefore(tb, editor);
    }

    function _getRotation(el) {
        var t = el.style.transform;
        if (!t) return 0;
        var m = t.match(/rotate\(([^)]+)deg\)/);
        return m ? parseInt(m[1]) : 0;
    }

    /* ════════════════════════════════════════════════════════════
       Table toolbar  –  appears when clicking a table cell
       ════════════════════════════════════════════════════════════ */
    function _removeTableToolbar(container) {
        var tb = container.querySelector('.arté-table-toolbar');
        if (tb) tb.remove();
    }

    function _showTableToolbar(cellEl, editor, container) {
        _removeTableToolbar(container);

        var tableEl = cellEl.closest('table');
        if (!tableEl) return;

        if (!tableEl.id) tableEl.id = 'arté-tbl-' + Date.now();
        var rowIdx = cellEl.parentElement ? Array.from(tableEl.rows).indexOf(cellEl.parentElement) : -1;
        var colIdx = cellEl.parentElement ? Array.from(cellEl.parentElement.cells).indexOf(cellEl) : -1;

        var tb = document.createElement('div');
        tb.className = 'arté-table-toolbar';
        tb.innerHTML =
            '<div class="arté-image-toolbar-info">Editing table...</div>' +
            '<div class="arté-toolbar-section">' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="addRowAbove"><i class="fas fa-plus"></i> Row Above</button>' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="addRowBelow"><i class="fas fa-plus"></i> Row Below</button>' +
            '</div>' +
            '<div class="arté-toolbar-section">' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="addColLeft"><i class="fas fa-plus"></i> Column Left</button>' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="addColRight"><i class="fas fa-plus"></i> Column Right</button>' +
            '</div>' +
            '<div class="arté-toolbar-section">' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="deleteRow"><i class="fas fa-minus"></i> Row</button>' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="deleteCol"><i class="fas fa-minus"></i> Column</button>' +
            '</div>' +
            '<div class="arté-toolbar-section">' +
            '  <button type="button" class="arté-toolbar-action-btn" data-action="deleteTable"><i class="fas fa-trash"></i> Table</button>' +
            '</div>';

        tb.querySelectorAll('.arté-toolbar-action-btn').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                var tbl = document.getElementById(tableEl.id);
                if (!tbl) return;

                switch (btn.dataset.action) {
                    case 'addRowAbove': _addTableRow(tbl, rowIdx); break;
                    case 'addRowBelow': _addTableRow(tbl, rowIdx + 1); break;
                    case 'addColLeft': _addTableCol(tbl, colIdx); break;
                    case 'addColRight': _addTableCol(tbl, colIdx + 1); break;
                    case 'deleteRow':
                        if (tbl.rows.length > 1) tbl.deleteRow(rowIdx);
                        break;
                    case 'deleteCol':
                        if (tbl.rows[0] && tbl.rows[0].cells.length > 1) {
                            for (var i = 0; i < tbl.rows.length; i++) {
                                if (colIdx >= 0 && colIdx < tbl.rows[i].cells.length) tbl.rows[i].deleteCell(colIdx);
                            }
                        }
                        break;
                    case 'deleteTable':
                        tbl.remove();
                        _removeTableToolbar(container);
                        break;
                }
            });
        });

        editor.parentNode.insertBefore(tb, editor);
    }

    function _addTableRow(table, idx) {
        var refIdx = idx > 0 ? idx - 1 : 0;
        var ref = table.rows[refIdx];
        var newRow = table.insertRow(idx);
        var colCount = ref ? ref.cells.length : 1;
        for (var i = 0; i < colCount; i++) {
            var cell = newRow.insertCell(i);
            cell.style.border = '1px solid #000';
            cell.style.height = '24px';
            cell.style.minWidth = '50px';
            cell.innerHTML = '&nbsp;';
        }
    }

    function _addTableCol(table, idx) {
        for (var i = 0; i < table.rows.length; i++) {
            var safeIdx = Math.min(idx, table.rows[i].cells.length);
            var cell = table.rows[i].insertCell(safeIdx);
            cell.style.border = '1px solid #000';
            cell.style.height = '24px';
            cell.style.minWidth = '50px';
            cell.innerHTML = '&nbsp;';
        }
    }

    /* ════════════════════════════════════════════════════════════
       Image upload modal  (file + URL, just like dashboard)
       ════════════════════════════════════════════════════════════ */
    function _showImageUploadModal(editorContent) {
        var modalHtml =
            '<div id="artéImageModal" class="arté-image-modal-overlay">' +
            '  <div class="arté-image-modal">' +
            '    <div class="arté-image-modal-header">' +
            '      <h2>Insert Image</h2>' +
            '      <button type="button" class="arté-image-modal-close" id="artéImgClose">&times;</button>' +
            '    </div>' +
            '    <div class="arté-image-modal-body">' +
            '      <div class="arté-image-section">' +
            '        <h3>Select from files</h3>' +
            '        <div class="arté-image-btn-container">' +
            '          <button type="button" class="arté-image-file-btn" id="artéImgFileBtn">' +
            '            Choose Files' +
            '            <input type="file" id="artéImgFileInput" accept="image/*" class="arté-image-file-input">' +
            '          </button>' +
            '        </div>' +
            '        <div id="artéImgPreviewContainer" class="arté-image-preview-container" style="display:none;">' +
            '          <img id="artéImgPreview" class="arté-image-preview">' +
            '        </div>' +
            '      </div>' +
            '      <div class="arté-image-separator"><span>OR</span></div>' +
            '      <div class="arté-image-section">' +
            '        <h3>Image URL</h3>' +
            '        <input type="url" id="artéImgUrlInput" placeholder="https://example.com/image.jpg" class="arté-image-url-input">' +
            '      </div>' +
            '    </div>' +
            '    <div class="arté-image-modal-footer">' +
            '      <button type="button" class="arté-image-cancel-btn" id="artéImgCancel">Cancel</button>' +
            '      <button type="button" class="arté-image-insert-btn" id="artéImgInsert">Insert Image</button>' +
            '    </div>' +
            '  </div>' +
            '</div>';

        var tmp = document.createElement('div');
        tmp.innerHTML = modalHtml;
        document.body.appendChild(tmp.firstElementChild);

        var modal = document.getElementById('artéImageModal');
        var fileInput = document.getElementById('artéImgFileInput');
        var urlInput = document.getElementById('artéImgUrlInput');
        var preview = document.getElementById('artéImgPreview');
        var previewContainer = document.getElementById('artéImgPreviewContainer');
        var insertBtn = document.getElementById('artéImgInsert');
        var cancelBtn = document.getElementById('artéImgCancel');
        var closeBtn = document.getElementById('artéImgClose');

        var selectedData = null;

        fileInput.addEventListener('change', function (e) {
            var file = e.target.files[0];
            if (file && file.type.startsWith('image/')) {
                var reader = new FileReader();
                reader.onload = function (ev) {
                    selectedData = ev.target.result;
                    preview.src = selectedData;
                    previewContainer.style.display = 'block';
                    urlInput.value = '';
                };
                reader.readAsDataURL(file);
            }
        });

        urlInput.addEventListener('input', function () {
            if (urlInput.value) {
                selectedData = _formatUrl(urlInput.value);
                preview.src = selectedData;
                previewContainer.style.display = 'block';
                fileInput.value = '';
            }
        });

        insertBtn.addEventListener('click', function () {
            if (selectedData) {
                editorContent.focus();
                document.execCommand('insertImage', false, selectedData);
                modal.remove();
            } else {
                alert('Please select an image file or enter an image URL.');
            }
        });

        var closeModal = function () { modal.remove(); };
        cancelBtn.addEventListener('click', closeModal);
        closeBtn.addEventListener('click', closeModal);
        modal.addEventListener('click', function (e) {
            if (e.target === modal) closeModal();
        });
    }

    /* ════════════════════════════════════════════════════════════
       URL helper
       ════════════════════════════════════════════════════════════ */
    function _formatUrl(url) {
        if (!url) return url;
        if (url.match(/^[a-zA-Z]+:\/\//)) return url;
        return 'https://' + url;
    }

    /* ════════════════════════════════════════════════════════════
       Public helpers
       ════════════════════════════════════════════════════════════ */
    function getAdvancedRichTextContent(textareaId) {
        var textarea = document.getElementById(textareaId);
        if (!textarea) return '';
        var container = textarea.parentElement.querySelector('.arté-container');
        if (container) {
            var ed = container.querySelector('.arté-editor-content');
            return ed ? ed.innerHTML.trim() : textarea.value;
        }
        return textarea.value;
    }

    function syncAdvancedRichTextToTextarea(textareaId) {
        var textarea = document.getElementById(textareaId);
        if (!textarea) return;
        var container = textarea.parentElement.querySelector('.arté-container');
        if (container) {
            var ed = container.querySelector('.arté-editor-content');
            if (ed) textarea.value = ed.innerHTML.trim();
        }
    }

    /**
     * Auto-initialise the editor if the textarea already contains HTML markup.
     * Call this after data is loaded into the textarea (e.g. on edit pages).
     * It will automatically open the editor so the user sees rendered content
     * instead of raw HTML tags.
     *
     * @param {string} textareaId   – the id of the textarea
     * @param {string} [toggleBtnId] – optional id of the "Show Editor" button
     */
    function autoInitAdvancedEditorIfHtml(textareaId, toggleBtnId) {
        var textarea = document.getElementById(textareaId);
        if (!textarea) return;

        var val = (textarea.value || '').trim();
        if (!val) return;

        // Quick check: does it contain any HTML tag?
        if (!/<[a-z][\s\S]*>/i.test(val)) return;

        // Already open?
        if (textarea.parentElement && textarea.parentElement.querySelector('.arté-container')) return;
        // Already has preview?
        if (textarea.parentElement && textarea.parentElement.querySelector('.arté-preview')) return;

        var toggleBtn = toggleBtnId ? document.getElementById(toggleBtnId) : null;
        // Also try to find button by common patterns if no explicit id given
        if (!toggleBtn) {
            toggleBtn = textarea.parentElement
                ? textarea.parentElement.querySelector('.editor-button, .show-editor-btn, .advanced-editor-toggle')
                : null;
        }
        // Show a rendered preview instead of raw HTML
        _showPreview(textarea, toggleBtn);
    }

    /**
     * Scan all textareas that have a sibling "Show Editor" button.
     * If the textarea value contains HTML, show a rendered preview
     * so the user sees formatted content instead of raw tags.
     */
    function _autoScanAndInitEditors() {
        // Prefer explicit mapping first (most stable): <button data-editor-for="textareaId">
        var explicitButtons = document.querySelectorAll('button[data-editor-for]');
        explicitButtons.forEach(function (btn) {
            _tryInitFromButton(btn);
        });

        // Fallback for legacy pages: known classes + common editor button id pattern.
        var fallbackButtons = document.querySelectorAll(
            '.editor-button, .show-editor-btn, .advanced-editor-toggle, button[id*="EditorBtn"]'
        );
        fallbackButtons.forEach(function (btn) {
            _tryInitFromButton(btn);
        });
    }

    function _resolveTextareaForButton(btn) {
        if (!btn) return null;

        // 1) Explicit mapping via data-editor-for
        var mappedId = btn.getAttribute('data-editor-for');
        if (mappedId) {
            var mapped = document.getElementById(mappedId);
            if (mapped && mapped.tagName && mapped.tagName.toLowerCase() === 'textarea') return mapped;
        }

        // 2) Look in parent / grandparent containers
        var containers = [btn.parentElement];
        if (btn.parentElement && btn.parentElement.parentElement) containers.push(btn.parentElement.parentElement);
        for (var i = 0; i < containers.length; i++) {
            var c = containers[i];
            if (!c) continue;
            var ta = c.querySelector('textarea');
            if (ta) return ta;
        }

        // 3) Nearby sibling textarea fallback
        var sib = btn.previousElementSibling;
        while (sib) {
            if (sib.tagName && sib.tagName.toLowerCase() === 'textarea') return sib;
            sib = sib.previousElementSibling;
        }
        return null;
    }

    function _tryInitFromButton(btn) {
        var textarea = _resolveTextareaForButton(btn);
        if (!textarea || !textarea.id) return;

        // Auto-tag button for deterministic future lookups
        if (!btn.getAttribute('data-editor-for')) {
            btn.setAttribute('data-editor-for', textarea.id);
        }

        // Skip if editor/preview already active around this textarea
        var scope = textarea.parentElement || textarea;
        if (scope.querySelector('.arté-container') || scope.querySelector('.arté-preview')) return;

        var val = (textarea.value || '').trim();
        if (!val) return;
        if (/<[a-z][\s\S]*>/i.test(val)) {
            _showPreview(textarea, btn);
        }
    }

    /**
     * Show a read-only HTML preview of the textarea content.
     * Clicking the preview opens the full editor.
     */
    function _showPreview(textarea, toggleBtn) {
        // Remove any existing preview
        var old = textarea.parentElement.querySelector('.arté-preview');
        if (old) old.remove();

        var htmlVal = (textarea.value || '').trim();
        if (!htmlVal) return;

        textarea.style.display = 'none';
        var preview = document.createElement('div');
        preview.className = 'arté-preview';
        preview.innerHTML = htmlVal;
        preview.addEventListener('click', function () {
            toggleAdvancedRichTextEditor(textarea.id, toggleBtn);
        });
        textarea.parentElement.insertBefore(preview, textarea.nextSibling);
    }

    /**
     * Auto-scan repeatedly after page load to catch async data loads.
     * Runs a few times with increasing delay then stops.
     */
    function _scheduleAutoScan() {
        var delays = [500, 1500, 3000, 5000, 8000];
        delays.forEach(function (ms) {
            setTimeout(function () {
                _autoScanAndInitEditors();
            }, ms);
        });
    }

    // Schedule auto-scan when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', _scheduleAutoScan);
    } else {
        _scheduleAutoScan();
    }

    /* ════════════════════════════════════════════════════════════
       Shared Rich-HTML sanitiser & renderer  (used by view pages)
       ════════════════════════════════════════════════════════════ */

    /**
     * Sanitise an HTML string for safe rendering on view pages.
     * Strips dangerous tags/attributes while keeping formatting.
     */
    function sanitizeRichHtml(html) {
        if (!html) return '';
        var template = document.createElement('template');
        template.innerHTML = String(html);

        var blockedTags = new Set(['script', 'style', 'iframe', 'object', 'embed', 'link', 'meta']);
        var allowedTags = new Set([
            'p', 'br', 'div', 'span',
            'strong', 'b', 'em', 'i', 'u', 's',
            'ul', 'ol', 'li',
            'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
            'blockquote', 'pre', 'code',
            'table', 'thead', 'tbody', 'tr', 'th', 'td',
            'img', 'a', 'sup', 'sub', 'hr'
        ]);
        var allowedAttrs = new Set([
            'href', 'src', 'alt', 'title', 'target', 'rel',
            'class', 'style',
            'width', 'height', 'colspan', 'rowspan', 'border'
        ]);

        var walker = document.createTreeWalker(template.content, NodeFilter.SHOW_ELEMENT, null);
        var nodes = [];
        while (walker.nextNode()) nodes.push(walker.currentNode);

        nodes.forEach(function (el) {
            var tag = el.tagName ? el.tagName.toLowerCase() : '';
            if (!tag) return;
            if (blockedTags.has(tag)) { el.remove(); return; }
            if (!allowedTags.has(tag)) {
                var parent = el.parentNode;
                if (!parent) return;
                while (el.firstChild) parent.insertBefore(el.firstChild, el);
                parent.removeChild(el);
                return;
            }
            Array.from(el.attributes).forEach(function (attr) {
                var name = attr.name.toLowerCase();
                var value = attr.value || '';
                if (name.indexOf('on') === 0) { el.removeAttribute(attr.name); return; }
                if (!allowedAttrs.has(name)) { el.removeAttribute(attr.name); return; }
                if ((name === 'href' || name === 'src') && /^\s*javascript:/i.test(value)) { el.removeAttribute(attr.name); return; }
                if (name === 'style' && /expression\s*\(|javascript:/i.test(value)) { el.removeAttribute(attr.name); }
            });
            if (tag === 'a') {
                var href = el.getAttribute('href');
                if (href && !/^(https?:|mailto:|tel:|\/|#)/i.test(href)) el.removeAttribute('href');
                if (el.getAttribute('target') === '_blank') el.setAttribute('rel', 'noopener noreferrer');
            }
        });
        return template.innerHTML;
    }

    /**
     * Render a value as rich HTML if it contains tags, otherwise escape it.
     * Wraps the result in a containment div so wide content doesn't break layout.
     */
    function renderRichHtml(value) {
        if (value == null || String(value).trim() === '') return '<span class="empty">-</span>';
        var str = String(value);
        if (/<[a-z][\s\S]*>/i.test(str)) {
            return '<div class="rich-html-content">' + sanitizeRichHtml(str) + '</div>';
        }
        // Plain text — escape it
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }

    /* ── Expose ── */
    window.toggleAdvancedRichTextEditor = toggleAdvancedRichTextEditor;
    window.getAdvancedRichTextContent = getAdvancedRichTextContent;
    window.syncAdvancedRichTextToTextarea = syncAdvancedRichTextToTextarea;
    window.autoInitAdvancedEditorIfHtml = autoInitAdvancedEditorIfHtml;
    window.autoScanRichEditors = _autoScanAndInitEditors;
    window.sanitizeRichHtml = sanitizeRichHtml;
    window.renderRichHtml = renderRichHtml;

})(window);
