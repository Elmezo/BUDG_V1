/**
 * New Widget Modal
 * Handles the creation of new dashboard widgets
 */
class NewWidgetModal {
    constructor() {
        this.modal = document.getElementById('newWidgetModal');
        this.form = document.getElementById('newWidgetForm');
        this.savedSearches = [];
        this.facets = [];
        this.facetColumns = {};
        this.currentFacet = null;
        this.previewShown = false;
        
        this.init();
    }

    init() {
        if (!this.modal) return;
        
        // Close button
        const closeBtn = document.getElementById('newWidgetModalClose');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.close());
        }
        
        // Cancel button
        const cancelBtn = document.getElementById('newWidgetCancel');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => this.close());
        }
        
        // OK button
        const okBtn = document.getElementById('newWidgetOK');
        if (okBtn) {
            console.log(`[NewWidget] Adding click listener to OK button`);
            okBtn.addEventListener('click', () => {
                console.log(`[NewWidget] OK button clicked - calling handleSubmit`);
                this.handleSubmit();
            });
        }
        
        // Close on overlay click
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) {
                this.close();
            }
        });
        
        // Widget source radio buttons
        const sourceRadios = this.form.querySelectorAll('input[name="widgetSource"]');
        sourceRadios.forEach(radio => {
            radio.addEventListener('change', (e) => this.handleSourceChange(e.target.value));
        });
        
        // Source select change
        const sourceSelect = document.getElementById('widgetSourceSelect');
        if (sourceSelect) {
            sourceSelect.addEventListener('change', (e) => this.handleSourceSelectChange(e.target.value));
        }
        
        // Focus select change
        const focusSelect = document.getElementById('widgetFocusSelect');
        if (focusSelect) {
            focusSelect.addEventListener('change', (e) => this.handleFocusChange(e.target.value));
        }
        
        // Visualization type change
        const visualizeRadios = this.form.querySelectorAll('input[name="visualizeAs"]');
        visualizeRadios.forEach(radio => {
            radio.addEventListener('change', (e) => this.handleVisualizationChange(e.target.value));
        });
        
        // VisualizeBy select change - auto-refresh preview
        const visualizeBySelect = document.getElementById('widgetVisualizeBy');
        if (visualizeBySelect) {
            visualizeBySelect.addEventListener('change', () => {
                this.autoRefreshPreview();
            });
        }
        
        // Preview button
        const previewBtn = document.getElementById('previewButton');
        if (previewBtn) {
            previewBtn.addEventListener('click', () => this.handlePreview());
        }
        
        // Initialize rich text editor for text widgets
        this.initRichTextEditor();
    }
    
    /**
     * Initialize rich text editor toolbar
     */
    initRichTextEditor() {
        const toolbar = document.getElementById('textWidgetEditorToolbar');
        const content = document.getElementById('textWidgetEditorContent');
        const preview = document.getElementById('textWidgetPreview');
        
        if (!toolbar || !content) return;
        
        // Initialize table grid
        this.initTableGrid();
        
        // Handle dropdowns
        this.initDropdowns();
        
        // Handle simple command buttons
        toolbar.querySelectorAll('.text-editor-btn[data-command]').forEach(btn => {
            const command = btn.getAttribute('data-command');
            if (command && !btn.closest('.text-editor-dropdown')) {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    this.handleCommand(command, content);
                    this.updatePreview();
                });
            }
        });
        
        // Handle dropdown menu items
        toolbar.querySelectorAll('.text-editor-dropdown-item[data-command]').forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const command = item.getAttribute('data-command');
                this.handleCommand(command, content);
                this.updatePreview();
                // Close dropdown
                item.closest('.text-editor-dropdown')?.classList.remove('active');
            });
        });
        
        // Handle color picker
        toolbar.querySelectorAll('.color-option').forEach(option => {
            option.addEventListener('click', (e) => {
                e.preventDefault();
                const color = option.getAttribute('data-color');
                document.execCommand('foreColor', false, color);
                content.focus();
                this.updatePreview();
                // Close dropdown
                option.closest('.text-editor-dropdown')?.classList.remove('active');
            });
        });
        
        // Update preview on input
        content.addEventListener('input', () => {
            this.updatePreview();
        });
        
        // Prevent default behavior for toolbar buttons
        toolbar.querySelectorAll('.text-editor-btn').forEach(btn => {
            btn.addEventListener('mousedown', (e) => e.preventDefault());
        });
        
        // Close dropdowns when clicking outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.text-editor-dropdown')) {
                toolbar.querySelectorAll('.text-editor-dropdown').forEach(dropdown => {
                    dropdown.classList.remove('active');
                });
            }
        });
        
        // Handle image clicks - add image toolbar
        content.addEventListener('click', (e) => {
            console.log('Editor content clicked:', e.target.tagName);
            
            // Remove any existing toolbars
            this.removeImageToolbar();
            this.removeTableToolbar();
            
            // Check if clicked on an image
            if (e.target.tagName === 'IMG') {
                console.log('Image clicked, showing toolbar');
                this.showImageToolbar(e.target);
            }
        });
        
        // Close image toolbar when clicking elsewhere
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.image-toolbar') && 
                e.target.tagName !== 'IMG' && 
                !e.target.closest('.image-toolbar-btn')) {
                this.removeImageToolbar();
            }
            
            if (!e.target.closest('.table-toolbar') && 
                e.target.tagName !== 'TD' && 
                e.target.tagName !== 'TH' && 
                !e.target.closest('.table-toolbar-btn')) {
                this.removeTableToolbar();
            }
        });
        
        // Handle table cell clicks
        content.addEventListener('click', (e) => {
            if (e.target.tagName === 'TD' || e.target.tagName === 'TH') {
                // Select the cell content
                const range = document.createRange();
                range.selectNodeContents(e.target);
                const selection = window.getSelection();
                selection.removeAllRanges();
                selection.addRange(range);
                
                // Show table toolbar
                this.showTableToolbar(e.target);
            }
        });
        
        // Initialize preview
        this.updatePreview();
    }
    
    /**
     * Update preview with current content - removed as per requirements
     */
    updatePreview() {
        // Preview functionality removed
        return;
    }
    
    /**
     * Initialize table grid selector
     */
    initTableGrid() {
        const tableGrid = document.getElementById('tableGrid');
        if (!tableGrid) return;
        
        // Clear existing content
        tableGrid.innerHTML = '';
        
        // Create 10x10 grid (but only show up to 8x8 as in the screenshot)
        for (let row = 0; row < 8; row++) {
            for (let col = 0; col < 8; col++) {
                const cell = document.createElement('div');
                cell.className = 'table-cell';
                cell.setAttribute('data-rows', row + 1);
                cell.setAttribute('data-cols', col + 1);
                tableGrid.appendChild(cell);
            }
        }
        
        // Add dimension label (e.g., "2 x 2")
        const dimensionLabel = document.createElement('div');
        dimensionLabel.id = 'tableDimensionLabel';
        dimensionLabel.style.textAlign = 'center';
        dimensionLabel.style.marginTop = '5px';
        dimensionLabel.style.fontSize = '14px';
        dimensionLabel.style.color = '#333';
        dimensionLabel.textContent = '0 x 0';
        tableGrid.parentNode.appendChild(dimensionLabel);
        
        // Handle cell hover and click
        tableGrid.querySelectorAll('.table-cell').forEach(cell => {
            cell.addEventListener('mouseenter', () => {
                const rows = parseInt(cell.getAttribute('data-rows'));
                const cols = parseInt(cell.getAttribute('data-cols'));
                this.highlightTableCells(tableGrid, rows, cols);
                
                // Update dimension label
                const dimensionLabel = document.getElementById('tableDimensionLabel');
                if (dimensionLabel) {
                    dimensionLabel.textContent = `${cols} x ${rows}`;
                }
            });
            
            cell.addEventListener('click', (e) => {
                e.preventDefault();
                const rows = parseInt(cell.getAttribute('data-rows'));
                const cols = parseInt(cell.getAttribute('data-cols'));
                this.insertTable(rows, cols);
                // Close dropdown
                cell.closest('.text-editor-dropdown')?.classList.remove('active');
            });
        });
        
        tableGrid.addEventListener('mouseleave', () => {
            this.clearTableHighlight(tableGrid);
            
            // Reset dimension label
            const dimensionLabel = document.getElementById('tableDimensionLabel');
            if (dimensionLabel) {
                dimensionLabel.textContent = '0 x 0';
            }
        });
    }
    
    /**
     * Highlight table cells on hover
     */
    highlightTableCells(grid, rows, cols) {
        this.clearTableHighlight(grid);
        grid.querySelectorAll('.table-cell').forEach(cell => {
            const cellRows = parseInt(cell.getAttribute('data-rows'));
            const cellCols = parseInt(cell.getAttribute('data-cols'));
            if (cellRows <= rows && cellCols <= cols) {
                cell.classList.add('highlighted');
            }
        });
    }
    
    /**
     * Create a table element directly in the DOM
     * This is a more reliable method than using execCommand
     */
    createTableElement(rows, cols) {
        // Create table element
        const table = document.createElement('table');
        table.setAttribute('border', '1');
        table.style.borderCollapse = 'collapse';
        table.style.width = '100%';
        
        // Create rows and cells
        for (let r = 0; r < rows; r++) {
            const tr = document.createElement('tr');
            for (let c = 0; c < cols; c++) {
                const td = document.createElement('td');
                td.style.border = '1px solid #000';
                td.style.height = '24px';
                td.style.minWidth = '50px';
                td.innerHTML = '&nbsp;';
                tr.appendChild(td);
            }
            table.appendChild(tr);
        }
        
        return table;
    }
    
    /**
     * Clear table highlight
     */
    clearTableHighlight(grid) {
        grid.querySelectorAll('.table-cell').forEach(cell => {
            cell.classList.remove('highlighted');
        });
    }
    
    /**
     * Insert table into editor
     */
    insertTable(rows, cols) {
        const content = document.getElementById('textWidgetEditorContent');
        if (!content) return;
        
        // Focus the editor to ensure we have a selection
        content.focus();
        
        try {
            // Create table element
            const table = this.createTableElement(rows, cols);
            
            // Get current selection
            const selection = window.getSelection();
            
            if (selection.rangeCount > 0) {
                // We have a selection, insert at cursor position
                const range = selection.getRangeAt(0);
                range.deleteContents();
                
                // Insert table
                range.insertNode(table);
                
                // Add a paragraph after the table for easier editing
                const p = document.createElement('p');
                p.innerHTML = '<br>';
                
                // Insert paragraph after table
                if (table.nextSibling) {
                    content.insertBefore(p, table.nextSibling);
                } else {
                    content.appendChild(p);
                }
                
                // Move cursor after table
                range.setStartAfter(p);
                range.setEndAfter(p);
                selection.removeAllRanges();
                selection.addRange(range);
            } else {
                // No selection, append to the end
                content.appendChild(table);
                
                // Add paragraph after table
                const p = document.createElement('p');
                p.innerHTML = '<br>';
                content.appendChild(p);
            }
            
            // Log success
            console.log('Table inserted successfully');
        } catch (error) {
            console.error('Error inserting table:', error);
            
            // Fallback to the old method using innerHTML
            let tableHtml = '<table border="1" style="border-collapse: collapse; width: 100%;">';
            for (let r = 0; r < rows; r++) {
                tableHtml += '<tr>';
                for (let c = 0; c < cols; c++) {
                    tableHtml += '<td style="border: 1px solid #000; height: 24px; min-width: 50px;">&nbsp;</td>';
                }
                tableHtml += '</tr>';
            }
            tableHtml += '</table><p><br></p>';
            
            // Append to content
            content.innerHTML += tableHtml;
        }
    }
    
    /**
     * Initialize dropdowns
     */
    initDropdowns() {
        const toolbar = document.getElementById('textWidgetEditorToolbar');
        if (!toolbar) return;
        
        toolbar.querySelectorAll('.text-editor-dropdown > .text-editor-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                const dropdown = btn.closest('.text-editor-dropdown');
                const isActive = dropdown.classList.contains('active');
                
                // Close all dropdowns
                toolbar.querySelectorAll('.text-editor-dropdown').forEach(d => {
                    d.classList.remove('active');
                });
                
                // Toggle current dropdown
                if (!isActive) {
                    dropdown.classList.add('active');
                }
            });
        });
    }
    
    /**
     * Handle editor commands
     */
    handleCommand(command, content) {
        switch (command) {
            case 'bold':
                document.execCommand('bold', false, null);
                content.focus();
                break;
            case 'underline':
                document.execCommand('underline', false, null);
                content.focus();
                break;
            case 'backColor':
                // Pen/Marker - highlight with yellow (default)
                document.execCommand('backColor', false, '#FFFF00');
                content.focus();
                break;
            case 'foreColor':
                // Text color - handled by color picker
                break;
            case 'insertUnorderedList':
                document.execCommand('insertUnorderedList', false, null);
                break;
            case 'insertOrderedList':
                document.execCommand('insertOrderedList', false, null);
                break;
            case 'justifyLeft':
                document.execCommand('justifyLeft', false, null);
                break;
            case 'justifyCenter':
                document.execCommand('justifyCenter', false, null);
                break;
            case 'justifyRight':
                document.execCommand('justifyRight', false, null);
                break;
            case 'justifyFull':
                document.execCommand('justifyFull', false, null);
                break;
            case 'createLink':
                const url = prompt('Enter URL:', '');
                if (url) {
                    // Add https:// if not present and not starting with a different protocol
                    const formattedUrl = this.formatUrl(url);
                    document.execCommand('createLink', false, formattedUrl);
                }
                break;
            case 'insertImage':
                this.showImageUploadModal(content);
                break;
            case 'insertVideo':
                const videoUrl = prompt('Enter video URL:', '');
                if (videoUrl) {
                    // Add https:// if not present and not starting with a different protocol
                    const formattedVideoUrl = this.formatUrl(videoUrl);
                    const videoHtml = `<iframe src="${formattedVideoUrl}" width="560" height="315" frameborder="0" allowfullscreen></iframe>`;
                    document.execCommand('insertHTML', false, videoHtml);
                }
                break;
            case 'fullscreen':
                this.toggleFullscreen();
                break;
            case 'codeView':
                this.toggleCodeView();
                break;
        }
        content.focus();
    }
    
    /**
     * Toggle fullscreen mode
     */
    toggleFullscreen() {
        const editor = document.getElementById('textWidgetEditor');
        if (!editor) return;
        
        if (editor.classList.contains('fullscreen')) {
            editor.classList.remove('fullscreen');
            document.exitFullscreen?.();
        } else {
            editor.classList.add('fullscreen');
            editor.requestFullscreen?.();
        }
    }
    
    /**
     * Toggle code view
     */
    /**
     * Format URL to ensure it has a protocol
     */
    formatUrl(url) {
        if (!url) return url;
        
        // If URL already has a protocol, return as is
        if (url.match(/^[a-zA-Z]+:\/\//)) {
            return url;
        }
        
        // Otherwise, add https:// protocol
        return 'https://' + url;
    }
    
    toggleCodeView() {
        const content = document.getElementById('textWidgetEditorContent');
        if (!content) return;
        
        // Remove image toolbar when switching to code view
        this.removeImageToolbar();
        
        if (content.hasAttribute('data-code-view')) {
            // Switch back to rich text
            content.removeAttribute('data-code-view');
            content.contentEditable = 'true';
            content.innerHTML = content.getAttribute('data-html-backup') || '';
            this.updatePreview();
        } else {
            // Switch to code view
            content.setAttribute('data-html-backup', content.innerHTML);
            content.setAttribute('data-code-view', 'true');
            content.contentEditable = 'true';
            content.textContent = content.innerHTML;
            this.updatePreview();
        }
    }
    
    /**
     * Show image toolbar when an image is clicked
     */
    showImageToolbar(imageElement) {
        console.log('showImageToolbar called');
        
        // Remove any existing toolbar first
        this.removeImageToolbar();
        
        // Create toolbar element
        const toolbar = document.createElement('div');
        toolbar.className = 'image-toolbar';
        toolbar.id = 'imageToolbar';
        toolbar.innerHTML = `
            <div class="image-toolbar-section">
                <button type="button" class="image-toolbar-btn" data-size="100">100%</button>
                <button type="button" class="image-toolbar-btn" data-size="50">50%</button>
                <button type="button" class="image-toolbar-btn" data-size="25">25%</button>
            </div>
            <div class="image-toolbar-section">
                <button type="button" class="image-toolbar-btn" data-action="rotateLeft"><i class="fas fa-undo"></i></button>
                <button type="button" class="image-toolbar-btn" data-action="rotateRight"><i class="fas fa-redo"></i></button>
            </div>
            <div class="image-toolbar-section">
                <button type="button" class="image-toolbar-btn" data-align="left"><i class="fas fa-align-left"></i></button>
                <button type="button" class="image-toolbar-btn" data-align="center"><i class="fas fa-align-center"></i></button>
                <button type="button" class="image-toolbar-btn" data-align="right"><i class="fas fa-align-right"></i></button>
            </div>
            <div class="image-toolbar-section">
                <button type="button" class="image-toolbar-btn" data-action="delete"><i class="fas fa-trash"></i></button>
            </div>
        `;
        
        // Store the current image element as a data attribute
        toolbar.dataset.targetImage = imageElement.id || 'img-' + Date.now();
        if (!imageElement.id) {
            imageElement.id = toolbar.dataset.targetImage;
        }
        
        // Place the toolbar at the top of the editor
        const content = document.getElementById('textWidgetEditorContent');
        if (content) {
            // Insert the toolbar at the beginning of the editor
            content.parentNode.insertBefore(toolbar, content);
            
            // Style the toolbar as fixed at the top
            toolbar.style.position = 'sticky';
            toolbar.style.top = '0';
            toolbar.style.left = '0';
            toolbar.style.width = '100%';
            toolbar.style.zIndex = '1000';
            toolbar.style.backgroundColor = '#f8f9fa';
            toolbar.style.borderBottom = '1px solid #dee2e6';
            toolbar.style.padding = '8px';
            toolbar.style.boxShadow = '0 2px 4px rgba(0,0,0,0.1)';
            
            // Add a message to indicate which image is being edited
            const infoSpan = document.createElement('div');
            infoSpan.className = 'image-toolbar-info';
            infoSpan.textContent = 'Editing image...';
            infoSpan.style.fontSize = '0.8rem';
            infoSpan.style.color = '#6c757d';
            infoSpan.style.marginBottom = '4px';
            toolbar.insertBefore(infoSpan, toolbar.firstChild);
        
        // Add event listeners to toolbar buttons
            
            // Add event listeners to toolbar buttons
            toolbar.querySelectorAll('.image-toolbar-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    
                    // Get the target image by ID
                    const targetImageId = toolbar.dataset.targetImage;
                    const targetImage = document.getElementById(targetImageId);
                    
                    if (!targetImage) {
                        console.error('Target image not found:', targetImageId);
                        return;
                    }
                    
                    // Handle size buttons
                    if (btn.dataset.size) {
                        const size = parseInt(btn.dataset.size);
                        targetImage.style.width = size + '%';
                    }
                    
                    // Handle alignment buttons
                    if (btn.dataset.align) {
                        targetImage.style.display = 'block';
                        
                        if (btn.dataset.align === 'left') {
                            targetImage.style.marginLeft = '0';
                            targetImage.style.marginRight = 'auto';
                        } else if (btn.dataset.align === 'center') {
                            targetImage.style.marginLeft = 'auto';
                            targetImage.style.marginRight = 'auto';
                        } else if (btn.dataset.align === 'right') {
                            targetImage.style.marginLeft = 'auto';
                            targetImage.style.marginRight = '0';
                        }
                    }
                    
                    // Handle action buttons
                    if (btn.dataset.action) {
                        if (btn.dataset.action === 'delete') {
                            targetImage.remove();
                            this.removeImageToolbar();
                        } else if (btn.dataset.action === 'rotateLeft') {
                            const currentRotation = this.getRotationDegrees(targetImage) || 0;
                            targetImage.style.transform = `rotate(${currentRotation - 90}deg)`;
                        } else if (btn.dataset.action === 'rotateRight') {
                            const currentRotation = this.getRotationDegrees(targetImage) || 0;
                            targetImage.style.transform = `rotate(${currentRotation + 90}deg)`;
                        }
                    }
                });
            });
        }
    }
    
    /**
     * Remove image toolbar
     */
    removeImageToolbar() {
        console.log('removeImageToolbar called');
        const toolbar = document.querySelector('.image-toolbar');
        if (toolbar) {
            console.log('Removing existing toolbar');
            toolbar.remove();
        }
    }
    
    /**
     * Get current rotation of an element
     */
    getRotationDegrees(element) {
        const transform = element.style.transform;
        if (!transform || transform === 'none') {
            return 0;
        }
        
        const match = transform.match(/rotate\(([^)]+)deg\)/);
        if (match && match[1]) {
            return parseInt(match[1]);
        }
        
        return 0;
    }
    
    /**
     * Show table toolbar when a table cell is clicked
     */
    showTableToolbar(cellElement) {
        console.log('showTableToolbar called');
        
        // Find the parent table
        const tableElement = cellElement.closest('table');
        if (!tableElement) {
            console.error('Table element not found');
            return;
        }
        
        // Create toolbar element
        const toolbar = document.createElement('div');
        toolbar.className = 'table-toolbar';
        toolbar.id = 'tableToolbar';
        toolbar.innerHTML = `
            <div class="table-toolbar-section">
                <button type="button" class="table-toolbar-btn" data-action="addRowAbove" title="Add row above"><i class="fas fa-plus"></i> Row Above</button>
                <button type="button" class="table-toolbar-btn" data-action="addRowBelow" title="Add row below"><i class="fas fa-plus"></i> Row Below</button>
            </div>
            <div class="table-toolbar-section">
                <button type="button" class="table-toolbar-btn" data-action="addColLeft" title="Add column left"><i class="fas fa-plus"></i> Column Left</button>
                <button type="button" class="table-toolbar-btn" data-action="addColRight" title="Add column right"><i class="fas fa-plus"></i> Column Right</button>
            </div>
            <div class="table-toolbar-section">
                <button type="button" class="table-toolbar-btn" data-action="deleteRow" title="Delete row"><i class="fas fa-minus"></i> Row</button>
                <button type="button" class="table-toolbar-btn" data-action="deleteCol" title="Delete column"><i class="fas fa-minus"></i> Column</button>
            </div>
            <div class="table-toolbar-section">
                <button type="button" class="table-toolbar-btn" data-action="deleteTable" title="Delete table"><i class="fas fa-trash"></i> Table</button>
            </div>
        `;
        
        // Generate unique IDs for table and cell if they don't have one
        const tableId = tableElement.id || 'table-' + Date.now();
        // Store cell position instead of relying on ID
        const rowIndex = cellElement.parentElement ? Array.from(tableElement.rows).indexOf(cellElement.parentElement) : -1;
        const cellIndex = cellElement.parentElement ? Array.from(cellElement.parentElement.cells).indexOf(cellElement) : -1;
        
        if (!tableElement.id) {
            tableElement.id = tableId;
        }
        
        // Store the table ID and cell position in the toolbar
        toolbar.dataset.targetTable = tableId;
        toolbar.dataset.targetRowIndex = rowIndex;
        toolbar.dataset.targetCellIndex = cellIndex;
        
        // Place the toolbar at the top of the editor
        const content = document.getElementById('textWidgetEditorContent');
        if (content) {
            // Insert the toolbar at the beginning of the editor
            content.parentNode.insertBefore(toolbar, content);
            
            // Style the toolbar as fixed at the top
            toolbar.style.position = 'sticky';
            toolbar.style.top = '0';
            toolbar.style.left = '0';
            toolbar.style.width = '100%';
            toolbar.style.zIndex = '1000';
            toolbar.style.backgroundColor = '#f8f9fa';
            toolbar.style.borderBottom = '1px solid #dee2e6';
            toolbar.style.padding = '8px';
            toolbar.style.boxShadow = '0 2px 4px rgba(0,0,0,0.1)';
            
            // Add a message to indicate which table is being edited
            const infoSpan = document.createElement('div');
            infoSpan.className = 'table-toolbar-info';
            infoSpan.textContent = 'Editing table...';
            infoSpan.style.fontSize = '0.8rem';
            infoSpan.style.color = '#6c757d';
            infoSpan.style.marginBottom = '4px';
            toolbar.insertBefore(infoSpan, toolbar.firstChild);
            
            // Add event listeners to toolbar buttons
            toolbar.querySelectorAll('.table-toolbar-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    
                    // Get the target table by ID
                    const targetTableId = toolbar.dataset.targetTable;
                    const targetTable = document.getElementById(targetTableId);
                    
                    if (!targetTable) {
                        console.error('Target table not found:', targetTableId);
                        return;
                    }
                    
                    // Get row and column index from data attributes
                    const rowIndex = parseInt(toolbar.dataset.targetRowIndex, 10);
                    const cellIndex = parseInt(toolbar.dataset.targetCellIndex, 10);
                    
                    if (isNaN(rowIndex) || isNaN(cellIndex) || rowIndex < 0 || cellIndex < 0) {
                        console.error('Invalid row or cell index:', rowIndex, cellIndex);
                        return;
                    }
                    
                    // Handle table actions
                    const action = btn.dataset.action;
                    
                    switch (action) {
                        case 'addRowAbove':
                            this.addTableRow(targetTable, rowIndex);
                            break;
                        case 'addRowBelow':
                            this.addTableRow(targetTable, rowIndex + 1);
                            break;
                        case 'addColLeft':
                            this.addTableColumn(targetTable, cellIndex);
                            break;
                        case 'addColRight':
                            this.addTableColumn(targetTable, cellIndex + 1);
                            break;
                        case 'deleteRow':
                            this.deleteTableRow(targetTable, rowIndex);
                            break;
                        case 'deleteCol':
                            this.deleteTableColumn(targetTable, cellIndex);
                            break;
                        case 'deleteTable':
                            targetTable.remove();
                            this.removeTableToolbar();
                            break;
                    }
                });
            });
        }
    }
    
    /**
     * Remove table toolbar
     */
    removeTableToolbar() {
        console.log('removeTableToolbar called');
        const toolbar = document.querySelector('.table-toolbar');
        if (toolbar) {
            console.log('Removing existing table toolbar');
            toolbar.remove();
        }
    }
    
    /**
     * Add a new row to a table
     */
    addTableRow(table, rowIndex) {
        try {
            // Get reference row to copy styles from
            const referenceRowIndex = rowIndex > 0 ? rowIndex - 1 : (table.rows.length > 0 ? 0 : -1);
            const referenceRow = referenceRowIndex >= 0 ? table.rows[referenceRowIndex] : null;
            
            // Create new row
            const newRow = table.insertRow(rowIndex);
            
            // Copy attributes from reference row if available
            if (referenceRow) {
                // Copy row attributes
                Array.from(referenceRow.attributes).forEach(attr => {
                    if (attr.name !== 'id') { // Don't copy ID
                        newRow.setAttribute(attr.name, attr.value);
                    }
                });
                
                // Copy style
                newRow.style.cssText = referenceRow.style.cssText;
                
                // Get column count from reference row
                const columnCount = referenceRow.cells.length;
                
                // Create cells
                for (let i = 0; i < columnCount; i++) {
                    const refCell = referenceRow.cells[i];
                    const newCell = newRow.insertCell(i);
                    
                    // Determine if this should be a header cell
                    const isHeader = refCell.tagName === 'TH';
                    
                    // If header, replace with proper element
                    if (isHeader) {
                        const newHeader = document.createElement('th');
                        newHeader.innerHTML = '&nbsp;';
                        
                        // Copy attributes from reference header
                        Array.from(refCell.attributes).forEach(attr => {
                            if (attr.name !== 'id') { // Don't copy ID
                                newHeader.setAttribute(attr.name, attr.value);
                            }
                        });
                        
                        // Copy style
                        newHeader.style.cssText = refCell.style.cssText;
                        
                        // Replace TD with TH
                        newCell.parentNode.replaceChild(newHeader, newCell);
                    } else {
                        // Copy attributes from reference cell
                        Array.from(refCell.attributes).forEach(attr => {
                            if (attr.name !== 'id') { // Don't copy ID
                                newCell.setAttribute(attr.name, attr.value);
                            }
                        });
                        
                        // Copy style
                        newCell.style.cssText = refCell.style.cssText;
                        newCell.innerHTML = '&nbsp;';
                    }
                }
            } else {
                // No reference row, create a basic row with one cell
                const cell = newRow.insertCell(0);
                cell.innerHTML = '&nbsp;';
            }
            
            console.log('Row added successfully at index', rowIndex);
        } catch (error) {
            console.error('Error adding row:', error);
        }
    }
    
    /**
     * Add a new column to a table
     */
    addTableColumn(table, colIndex) {
        const rows = table.rows;
        
        // Get reference column to copy styles from
        const referenceColIndex = colIndex > 0 ? colIndex - 1 : (rows[0] && rows[0].cells.length > 0 ? 0 : -1);
        
        for (let i = 0; i < rows.length; i++) {
            // Make sure colIndex is within valid range for this row
            const currentCellCount = rows[i].cells.length;
            const safeColIndex = Math.min(colIndex, currentCellCount);
            
            try {
                // Get reference cell if available
                const refCell = referenceColIndex >= 0 && referenceColIndex < rows[i].cells.length ? 
                    rows[i].cells[referenceColIndex] : null;
                
                // Insert new cell
                const cell = rows[i].insertCell(safeColIndex);
                cell.innerHTML = '&nbsp;';
                
                // Copy styles and attributes from reference cell if available
                if (refCell) {
                    // Determine if this should be a header cell
                    const isHeader = refCell.tagName === 'TH';
                    
                    if (isHeader) {
                        // Create a header cell to replace the regular cell
                        const newHeader = document.createElement('th');
                        newHeader.innerHTML = '&nbsp;';
                        
                        // Copy attributes from reference header
                        Array.from(refCell.attributes).forEach(attr => {
                            if (attr.name !== 'id') { // Don't copy ID
                                newHeader.setAttribute(attr.name, attr.value);
                            }
                        });
                        
                        // Copy style
                        newHeader.style.cssText = refCell.style.cssText;
                        
                        // Replace TD with TH
                        cell.parentNode.replaceChild(newHeader, cell);
                    } else {
                        // Copy attributes from reference cell
                        Array.from(refCell.attributes).forEach(attr => {
                            if (attr.name !== 'id') { // Don't copy ID
                                cell.setAttribute(attr.name, attr.value);
                            }
                        });
                        
                        // Copy style
                        cell.style.cssText = refCell.style.cssText;
                    }
                }
                
                // Special case for first row headers
                if (i === 0 && rows[0].cells[0].tagName === 'TH' && cell.tagName !== 'TH') {
                    const newHeader = document.createElement('th');
                    newHeader.innerHTML = '&nbsp;';
                    
                    // Copy attributes
                    Array.from(cell.attributes).forEach(attr => {
                        if (attr.name !== 'id') {
                            newHeader.setAttribute(attr.name, attr.value);
                        }
                    });
                    
                    // Copy style
                    newHeader.style.cssText = cell.style.cssText;
                    
                    // Replace TD with TH
                    cell.parentNode.replaceChild(newHeader, cell);
                }
                
            } catch (error) {
                console.warn(`Failed to insert cell at index ${safeColIndex} for row ${i}:`, error);
                // Add cell at the end as fallback
                try {
                    const cell = rows[i].insertCell(-1);
                    cell.innerHTML = '&nbsp;';
                } catch (fallbackError) {
                    console.error('Failed to insert cell even at the end:', fallbackError);
                }
            }
        }
        
        console.log('Column added successfully at index', colIndex);
    }
    
    /**
     * Delete a row from a table
     */
    deleteTableRow(table, rowIndex) {
        // Don't delete if it's the last row
        if (table.rows.length <= 1) {
            return;
        }
        
        table.deleteRow(rowIndex);
    }
    
    /**
     * Delete a column from a table
     */
    deleteTableColumn(table, colIndex) {
        const rows = table.rows;
        
        // Don't delete if it's the last column
        if (rows[0].cells.length <= 1) {
            return;
        }
        
        for (let i = 0; i < rows.length; i++) {
            try {
                // Make sure colIndex is within valid range for this row
                if (colIndex >= 0 && colIndex < rows[i].cells.length) {
                    rows[i].deleteCell(colIndex);
                } else {
                    console.warn(`Cannot delete cell at index ${colIndex} in row ${i} - out of range`);
                }
            } catch (error) {
                console.error(`Error deleting cell at index ${colIndex} in row ${i}:`, error);
            }
        }
    }
    
    /**
     * Show image upload modal
     */
    showImageUploadModal(editorContent) {
        // Create modal HTML
        const modalHtml = `
            <div id="imageUploadModal" class="image-upload-modal-overlay">
                <div class="image-upload-modal">
                    <div class="image-upload-modal-header">
                        <h2>Insert Image</h2>
                        <button type="button" class="image-upload-modal-close" id="imageUploadModalClose">&times;</button>
                    </div>
                    <div class="image-upload-modal-body">
                        <div class="image-upload-section">
                            <h3>Select from files</h3>
                            <div class="image-upload-button-container">
                                <button type="button" class="image-upload-button" id="imageFileButton">
                                    Choose Files
                                    <input type="file" id="imageFileInput" accept="image/*" class="image-file-input">
                                </button>
                            </div>
                            <div id="imagePreviewContainer" class="image-preview-container" style="display: none;">
                                <img id="imagePreview" class="image-preview">
                            </div>
                        </div>
                        
                        <div class="image-upload-separator">
                            <span>OR</span>
                        </div>
                        
                        <div class="image-upload-section">
                            <h3>Image URL</h3>
                            <input type="url" id="imageUrlInput" placeholder="https://example.com/image.jpg" class="image-url-input">
                        </div>
                    </div>
                    <div class="image-upload-modal-footer">
                        <button type="button" class="image-upload-cancel-btn" id="imageUploadCancel">Cancel</button>
                        <button type="button" class="image-upload-insert-btn" id="imageUploadInsert">Insert Image</button>
                    </div>
                </div>
            </div>
        `;
        
        // Append modal to body
        const modalContainer = document.createElement('div');
        modalContainer.innerHTML = modalHtml;
        document.body.appendChild(modalContainer.firstElementChild);
        
        const modal = document.getElementById('imageUploadModal');
        const fileInput = document.getElementById('imageFileInput');
        const urlInput = document.getElementById('imageUrlInput');
        const preview = document.getElementById('imagePreview');
        const previewContainer = document.getElementById('imagePreviewContainer');
        const insertBtn = document.getElementById('imageUploadInsert');
        const cancelBtn = document.getElementById('imageUploadCancel');
        const closeBtn = document.getElementById('imageUploadModalClose');
        
        let selectedImageData = null;
        
        // Handle file selection
        fileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file && file.type.startsWith('image/')) {
                const reader = new FileReader();
                reader.onload = (event) => {
                    selectedImageData = event.target.result;
                    preview.src = selectedImageData;
                    previewContainer.style.display = 'block';
                    urlInput.value = ''; // Clear URL input
                    
                    // Update button text to show selected file
                    const fileButton = document.getElementById('imageFileButton');
                    if (fileButton) {
                        // Store original file input handler
                        const originalFileInput = document.getElementById('imageFileInput');
                        const originalHandler = originalFileInput ? originalFileInput.onchange : null;
                        
                        // Update button text with filename
                        const fileName = file.name.length > 20 ? file.name.substring(0, 17) + '...' : file.name;
                        fileButton.textContent = fileName;
                        
                        // Re-add the input element that gets replaced when changing textContent
                        const newInput = document.createElement('input');
                        newInput.type = 'file';
                        newInput.id = 'imageFileInput';
                        newInput.accept = 'image/*';
                        newInput.className = 'image-file-input';
                        
                        // Add the same file change handler
                        newInput.addEventListener('change', (event) => {
                            const selectedFile = event.target.files[0];
                            if (selectedFile && selectedFile.type.startsWith('image/')) {
                                const fileReader = new FileReader();
                                fileReader.onload = (fileEvent) => {
                                    selectedImageData = fileEvent.target.result;
                                    preview.src = selectedImageData;
                                    previewContainer.style.display = 'block';
                                    urlInput.value = ''; // Clear URL input
                                    
                                    // Update button text
                                    const selectedFileName = selectedFile.name.length > 20 ? 
                                        selectedFile.name.substring(0, 17) + '...' : 
                                        selectedFile.name;
                                    fileButton.textContent = selectedFileName;
                                    fileButton.appendChild(newInput);
                                };
                                fileReader.readAsDataURL(selectedFile);
                            }
                        });
                        
                        fileButton.appendChild(newInput);
                    }
                };
                reader.readAsDataURL(file);
            }
        });
        
        // Handle URL input
        urlInput.addEventListener('input', () => {
            if (urlInput.value) {
                // Format URL properly
                const formattedUrl = this.formatUrl(urlInput.value);
                selectedImageData = formattedUrl;
                preview.src = formattedUrl;
                previewContainer.style.display = 'block';
                fileInput.value = ''; // Clear file input
            }
        });
        
        // Insert image
        insertBtn.addEventListener('click', () => {
            if (selectedImageData) {
                editorContent.focus();
                document.execCommand('insertImage', false, selectedImageData);
                modal.remove();
            } else {
                alert('Please select an image file or enter an image URL.');
            }
        });
        
        // Cancel
        const closeModal = () => modal.remove();
        cancelBtn.addEventListener('click', closeModal);
        closeBtn.addEventListener('click', closeModal);
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
    }

    async open(dashboardId = null) {
        if (!this.modal) return;
        
        // Set dashboard ID on modal
        if (dashboardId) {
            this.modal.setAttribute('data-dashboard-id', dashboardId);
        } else if (window.dashboard && window.dashboard.currentDashboardId) {
            this.modal.setAttribute('data-dashboard-id', window.dashboard.currentDashboardId);
        } else {
            this.modal.removeAttribute('data-dashboard-id');
        }
        
        this.modal.style.display = 'flex';
        this.resetForm();
        
        // Load initial data
        await Promise.all([
            this.loadSavedSearches(),
            this.loadFacets()
        ]);
    }

    close() {
        console.log(`[NewWidget] === CLOSING MODAL ===`);
        if (!this.modal) return;
        this.modal.style.display = 'none';
        this.resetForm();
        
        // Remove edit mode attribute if exists
        this.modal.removeAttribute('data-widget-id');
        this.modal.removeAttribute('data-edit-mode');
        
        // Clean up event listeners
        if (this.outsideClickHandler) {
            document.removeEventListener('click', this.outsideClickHandler);
            this.outsideClickHandler = null;
        }
    }

    resetForm() {
        if (!this.form) return;
        
        this.form.reset();
        this.previewShown = false;
        
        // Reset to default state
        const savedSearchRadio = this.form.querySelector('input[value="savedSearch"]');
        if (savedSearchRadio) {
            savedSearchRadio.checked = true;
        }
        
        this.handleSourceChange('savedSearch');
        
        // Clear selects
        const sourceSelect = document.getElementById('widgetSourceSelect');
        const focusSelect = document.getElementById('widgetFocusSelect');
        const visualizeBySelect = document.getElementById('widgetVisualizeBy');
        const displayChips = document.getElementById('widgetDisplay');
        
        if (sourceSelect) {
            sourceSelect.innerHTML = '<option value="">Select a saved search</option>';
        }
        if (focusSelect) {
            focusSelect.innerHTML = '<option value="">Select a facet</option>';
        }
        if (visualizeBySelect) {
            visualizeBySelect.innerHTML = '<option value="">Select a column</option>';
        }
        if (displayChips) {
            displayChips.innerHTML = '';
        }
        
        // Reset preview
        const preview = document.getElementById('widgetPreview');
        if (preview) {
            preview.innerHTML = '<button type="button" class="preview-button" id="previewButton">Preview</button>';
            const previewBtn = document.getElementById('previewButton');
            if (previewBtn) {
                previewBtn.addEventListener('click', () => this.handlePreview());
            }
        }
        
        // Reset text editor content
        const textEditor = document.getElementById('textWidgetEditorContent');
        if (textEditor) {
            textEditor.innerHTML = '';
        }
        
        // Reset edit mode
        if (this.modal) {
            this.modal.removeAttribute('data-edit-mode');
            this.modal.removeAttribute('data-widget-id');
            
            // Reset modal title
            const modalTitle = this.modal.querySelector('.modal-header h2');
            if (modalTitle) {
                modalTitle.textContent = 'New Widget';
            }
            
            // Reset OK button text
            const okBtn = document.getElementById('newWidgetOK');
            if (okBtn) {
                okBtn.textContent = 'OK';
            }
        }
        
        this.currentFacet = null;
        this.facetColumns = {};
    }

    async loadSavedSearches() {
        try {
            console.log('Loading saved searches...');
            const response = await fetch('/api/dashboard/new-widget/saved-searches', {
                credentials: 'include',
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('token')}`
                }
            });
            
            if (!response.ok) {
                throw new Error(`Failed to load saved searches: ${response.status} ${response.statusText}`);
            }
            
            this.savedSearches = await response.json();
            console.log('Loaded saved searches:', this.savedSearches?.length || 0, this.savedSearches);
            this.populateSourceSelect();
            return this.savedSearches;
        } catch (error) {
            console.error('Error loading saved searches:', error);
            // Show empty dropdown with error message
            this.savedSearches = [];
            this.populateSourceSelect();
            return [];
        }
    }

    async loadFacets() {
        try {
            const response = await fetch('/api/dashboard/new-widget/facets', {
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('token')}`
                }
            });
            
            if (!response.ok) {
                throw new Error('Failed to load facets');
            }
            
            this.facets = await response.json();
            this.populateFocusSelect();
        } catch (error) {
            console.error('Error loading facets:', error);
        }
    }

    populateSourceSelect() {
        const optionsContainer = document.getElementById('widgetSourceOptions');
        const hiddenInput = document.getElementById('widgetSourceSelect');
        const selectedDisplay = document.getElementById('widgetSourceSelected');
        
        console.log('Populating source select:', {
            optionsContainer: !!optionsContainer,
            hiddenInput: !!hiddenInput,
            selectedDisplay: !!selectedDisplay,
            savedSearches: this.savedSearches?.length || 0
        });
        
        if (!optionsContainer || !hiddenInput || !selectedDisplay) {
            console.error('Required elements not found for custom dropdown');
            return;
        }
        
        // Clear existing options
        optionsContainer.innerHTML = '';
        
        // Add search input
        const searchContainer = document.createElement('div');
        searchContainer.className = 'custom-dropdown-search';
        searchContainer.innerHTML = `
            <input type="text" placeholder="Search saved searches..." id="sourceSearchInput">
        `;
        optionsContainer.appendChild(searchContainer);
        
        // Check if we have saved searches
        if (!this.savedSearches || this.savedSearches.length === 0) {
            const noDataOption = document.createElement('div');
            noDataOption.className = 'custom-dropdown-option';
            noDataOption.innerHTML = '<div class="option-title" style="color: #6b7280; font-style: italic;">No saved searches available</div>';
            optionsContainer.appendChild(noDataOption);
        } else {
            // Create options
            this.savedSearches.forEach(search => {
                const option = document.createElement('div');
                option.className = 'custom-dropdown-option';
                option.setAttribute('data-value', search.id);
                option.setAttribute('data-search-type', search.searchType || 0);
                
                let optionHTML = `<div class="option-title">${this.escapeHtml(search.name)}</div>`;
                
                if (search.description) {
                    optionHTML += `<div class="option-description">${this.escapeHtml(search.description)}</div>`;
                }
                
                if (search.sharedBy) {
                    optionHTML += `<div class="option-shared-by"><span class="shared-by-label">Shared By :</span> ${this.escapeHtml(search.sharedBy)}</div>`;
                }
                
                option.innerHTML = optionHTML;
                optionsContainer.appendChild(option);
            });
        }
        
        // Initialize dropdown functionality
        this.initCustomDropdown();
    }

    initCustomDropdown() {
        const dropdown = document.getElementById('widgetSourceDropdown');
        const selected = document.getElementById('widgetSourceSelected');
        const options = document.getElementById('widgetSourceOptions');
        const hiddenInput = document.getElementById('widgetSourceSelect');
        const searchInput = document.getElementById('sourceSearchInput');
        
        if (!dropdown || !selected || !options || !hiddenInput) {
            console.error('Custom dropdown elements not found:', {
                dropdown: !!dropdown,
                selected: !!selected,
                options: !!options,
                hiddenInput: !!hiddenInput
            });
            return;
        }
        
        // Remove existing event listeners to prevent duplicates
        const newSelected = selected.cloneNode(true);
        selected.parentNode.replaceChild(newSelected, selected);
        
        const newOptions = options.cloneNode(true);
        options.parentNode.replaceChild(newOptions, options);
        
        // Get fresh references
        const freshSelected = document.getElementById('widgetSourceSelected');
        const freshOptions = document.getElementById('widgetSourceOptions');
        const freshSearchInput = document.getElementById('sourceSearchInput');
        
        // Toggle dropdown
        freshSelected.addEventListener('click', (e) => {
            e.stopPropagation();
            const isActive = freshSelected.classList.contains('active');
            
            // Close all other dropdowns
            document.querySelectorAll('.custom-dropdown-selected.active').forEach(el => {
                el.classList.remove('active');
            });
            document.querySelectorAll('.custom-dropdown-options.show').forEach(el => {
                el.classList.remove('show');
            });
            
            if (!isActive) {
                freshSelected.classList.add('active');
                freshOptions.classList.add('show');
                if (freshSearchInput) {
                    setTimeout(() => freshSearchInput.focus(), 100);
                }
            }
        });
        
        // Handle option selection
        freshOptions.addEventListener('click', (e) => {
            const option = e.target.closest('.custom-dropdown-option');
            if (!option) return;
            
            const value = option.getAttribute('data-value');
            const selectedSearch = this.savedSearches.find(search => search.id == value);
            
            if (selectedSearch) {
                // Update hidden input
                hiddenInput.value = value;
                
                // Update display
                freshSelected.innerHTML = `
                    <span class="selected-text">${this.escapeHtml(selectedSearch.name)}</span>
                    <i class="fas fa-chevron-down dropdown-arrow"></i>
                `;
                
                // Remove active states
                document.querySelectorAll('.custom-dropdown-option.selected').forEach(el => {
                    el.classList.remove('selected');
                });
                option.classList.add('selected');
                
                // Close dropdown
                freshSelected.classList.remove('active');
                freshOptions.classList.remove('show');
                
                // Trigger change event
                this.handleSourceSelectChange(value);
            }
        });
        
        // Handle search
        if (freshSearchInput) {
            freshSearchInput.addEventListener('input', (e) => {
                const searchTerm = e.target.value.toLowerCase();
                const optionElements = freshOptions.querySelectorAll('.custom-dropdown-option');
                
                optionElements.forEach(option => {
                    const title = option.querySelector('.option-title')?.textContent.toLowerCase() || '';
                    const description = option.querySelector('.option-description')?.textContent.toLowerCase() || '';
                    const sharedBy = option.querySelector('.option-shared-by')?.textContent.toLowerCase() || '';
                    
                    const matches = title.includes(searchTerm) || 
                                  description.includes(searchTerm) || 
                                  sharedBy.includes(searchTerm);
                    
                    option.style.display = matches ? 'block' : 'none';
                });
            });
            
            // Prevent dropdown close when clicking search input
            freshSearchInput.addEventListener('click', (e) => {
                e.stopPropagation();
            });
        }
        
        // Close dropdown when clicking outside (use a unique handler)
        if (!this.outsideClickHandler) {
            this.outsideClickHandler = (e) => {
                const currentDropdown = document.getElementById('widgetSourceDropdown');
                const currentSelected = document.getElementById('widgetSourceSelected');
                const currentOptions = document.getElementById('widgetSourceOptions');
                
                if (currentDropdown && !currentDropdown.contains(e.target)) {
                    if (currentSelected) currentSelected.classList.remove('active');
                    if (currentOptions) currentOptions.classList.remove('show');
                }
            };
            document.addEventListener('click', this.outsideClickHandler);
        }
    }

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    populateFocusSelect() {
        const select = document.getElementById('widgetFocusSelect');
        if (!select) return;
        
        // Clear existing options except the first one
        select.innerHTML = '<option value="">Select a facet</option>';
        
        this.facets.forEach(facet => {
            const option = document.createElement('option');
            option.value = facet.name;
            option.textContent = facet.name;
            select.appendChild(option);
        });
    }

    handleSourceChange(sourceType) {
        const savedSearchOptions = document.getElementById('savedSearchOptions');
        const textWidgetOptions = document.getElementById('textWidgetOptions');
        const visualizationSection = document.getElementById('visualizationSection');
        const descriptionField = document.getElementById('widgetDescription');
        const descriptionGroup = descriptionField?.closest('.form-group');
        const previewSection = document.getElementById('savedSearchPreviewSection');
        
        // Get form fields that should be required only for saved search widgets
        const sourceSelect = document.getElementById('widgetSourceSelect');
        const focusSelect = document.getElementById('widgetFocusSelect');
        const visualizeBySelect = document.getElementById('widgetVisualizeBy');
        
        if (sourceType === 'savedSearch') {
            if (savedSearchOptions) savedSearchOptions.style.display = 'block';
            if (textWidgetOptions) textWidgetOptions.style.display = 'none';
            if (visualizationSection) visualizationSection.style.display = 'block';
            if (previewSection) previewSection.style.display = 'block';
            
            // Set required attributes for saved search fields
            if (sourceSelect) sourceSelect.setAttribute('required', '');
            if (focusSelect) focusSelect.setAttribute('required', '');
            // visualizeBy required will be set/removed based on visualization type in handleVisualizationChange
            // Don't set it here - let handleVisualizationChange handle it
            
            // Show description field in left section
            if (descriptionGroup) descriptionGroup.style.display = 'block';
            if (descriptionField) {
                descriptionField.required = true;
                const requiredSpan = document.getElementById('descriptionRequired');
                if (requiredSpan) requiredSpan.style.display = 'inline';
            }
            
            // Trigger visualization change to set required correctly based on current selection
            const currentVisualizeAs = document.querySelector('input[name="visualizeAs"]:checked')?.value;
            if (currentVisualizeAs) {
                this.handleVisualizationChange(currentVisualizeAs);
            }
            
            // Load saved searches and initialize custom dropdown
            if (!this.savedSearches || this.savedSearches.length === 0) {
                this.loadSavedSearches();
            } else {
                // If searches are already loaded, just populate the dropdown
                this.populateSourceSelect();
            }
        } else {
            // Text widget mode
            if (savedSearchOptions) savedSearchOptions.style.display = 'none';
            if (textWidgetOptions) textWidgetOptions.style.display = 'block';
            if (visualizationSection) visualizationSection.style.display = 'none';
            if (previewSection) previewSection.style.display = 'none';
            
            // Remove required attributes for saved search fields
            if (sourceSelect) sourceSelect.removeAttribute('required');
            if (focusSelect) focusSelect.removeAttribute('required');
            if (visualizeBySelect) visualizeBySelect.removeAttribute('required');
            
            // Hide description field in left section when text widget is selected
            if (descriptionGroup) descriptionGroup.style.display = 'none';
            if (descriptionField) {
                descriptionField.required = false;
                const requiredSpan = document.getElementById('descriptionRequired');
                if (requiredSpan) requiredSpan.style.display = 'none';
            }
            
            // Clear title and description if they were auto-filled from saved search
            const titleField = document.getElementById('widgetTitle');
            const currentSourceId = sourceSelect?.value;
            
            if (currentSourceId && this.savedSearches) {
                const selectedSearch = this.savedSearches.find(search => search.id == currentSourceId || search.id == parseInt(currentSourceId));
                if (selectedSearch) {
                    // Clear title if it matches the saved search name
                    if (titleField && titleField.value === selectedSearch.name) {
                        titleField.value = '';
                    }
                    // Clear description if it matches the saved search description
                    if (descriptionField && descriptionField.value === (selectedSearch.description || '')) {
                        descriptionField.value = '';
                    }
                }
            }
            
            // Also clear the source select dropdown
            if (sourceSelect) {
                sourceSelect.value = '';
                const selectedDisplay = document.getElementById('widgetSourceSelected');
                if (selectedDisplay) {
                    selectedDisplay.innerHTML = `
                        <span class="selected-text">Select a saved search</span>
                        <i class="fas fa-chevron-down dropdown-arrow"></i>
                    `;
                }
                // Clear selected state in options
                document.querySelectorAll('.custom-dropdown-option.selected').forEach(option => {
                    option.classList.remove('selected');
                });
            }
        }
    }

    /**
     * Set custom dropdown value programmatically
     */
    async setCustomDropdownValue(searchId) {
        const hiddenInput = document.getElementById('widgetSourceSelect');
        const selectedDisplay = document.getElementById('widgetSourceSelected');
        
        if (!hiddenInput || !selectedDisplay) return;
        
        // Ensure saved searches are loaded
        if (!this.savedSearches || this.savedSearches.length === 0) {
            // Wait for saved searches to load
            setTimeout(async () => {
                await this.setCustomDropdownValue(searchId);
            }, 100);
            return;
        }
        
        const selectedSearch = this.savedSearches.find(search => search.id == searchId || search.id == parseInt(searchId));
        if (selectedSearch) {
            // Update hidden input
            hiddenInput.value = searchId;
            
            // Update display
            selectedDisplay.innerHTML = `
                <span class="selected-text">${this.escapeHtml(selectedSearch.name)}</span>
                <i class="fas fa-chevron-down dropdown-arrow"></i>
            `;
            
            // Update selected state in options
            document.querySelectorAll('.custom-dropdown-option').forEach(option => {
                option.classList.remove('selected');
                if (option.getAttribute('data-value') == searchId || option.getAttribute('data-value') == parseInt(searchId)) {
                    option.classList.add('selected');
                }
            });
            
            // Trigger change handler (which will load facets)
            await this.handleSourceSelectChange(searchId);
        } else {
            console.warn('Saved search not found:', searchId, 'Available searches:', this.savedSearches);
        }
    }

    async handleSourceSelectChange(searchId) {
        const viewSearchLink = document.getElementById('viewSearchLink');
        const titleField = document.getElementById('widgetTitle');
        const descriptionField = document.getElementById('widgetDescription');
        
        if (viewSearchLink && searchId) {
            viewSearchLink.href = `/search.html?searchId=${searchId}`;
            viewSearchLink.style.display = 'inline-block';
        } else if (viewSearchLink) {
            viewSearchLink.style.display = 'none';
        }
        
        // Check if saved search options section is visible (more reliable than radio button check)
        const savedSearchOptions = document.getElementById('savedSearchOptions');
        const isSavedSearchMode = savedSearchOptions && savedSearchOptions.style.display !== 'none';
        
        // Auto-fill title and description when a search is selected (ONLY if saved search section is visible)
        if (searchId && this.savedSearches && isSavedSearchMode) {
            const selectedSearch = this.savedSearches.find(search => search.id == searchId || search.id == parseInt(searchId));
            if (selectedSearch) {
                // Only auto-fill if the field is empty (to preserve edit values)
                if (titleField && (!titleField.value || titleField.value.trim() === '')) {
                    titleField.value = selectedSearch.name || '';
                }
                if (descriptionField && (!descriptionField.value || descriptionField.value.trim() === '')) {
                    descriptionField.value = selectedSearch.description || '';
                }
            }
        } else if (!searchId && isSavedSearchMode) {
            // Clear fields when no search is selected (only if saved search section is visible)
            if (titleField && (!titleField.value || titleField.value.trim() === '')) {
                titleField.value = '';
            }
            if (descriptionField && (!descriptionField.value || descriptionField.value.trim() === '')) {
                descriptionField.value = '';
            }
        }
        // Don't fill or clear fields when text widget section is visible
        
        // Load facets for the selected search
        if (searchId) {
            await this.loadFacets();
        }
    }

    async handleFocusChange(facetName) {
        if (!facetName) {
            this.currentFacet = null;
            this.updateVisualizationOptions();
            return;
        }
        
        this.currentFacet = facetName;
        console.log('handleFocusChange: Facet selected', facetName);
        
        // Load columns for this facet if not already loaded
        if (!this.facetColumns[facetName]) {
            try {
                console.log('Loading columns for facet:', facetName);
                const response = await fetch(`/api/dashboard/new-widget/facet-columns?facet=${encodeURIComponent(facetName)}`, {
                    headers: {
                        'Authorization': `Bearer ${localStorage.getItem('token')}`
                    }
                });
                
                if (response.ok) {
                    const columns = await response.json();
                    console.log('Loaded columns for facet:', facetName, columns);
                    this.facetColumns[facetName] = columns;
                } else {
                    console.error('Failed to load columns, status:', response.status);
                }
            } catch (error) {
                console.error('Error loading facet columns:', error);
            }
        } else {
            console.log('Using cached columns for facet:', facetName, this.facetColumns[facetName]);
        }
        
        this.updateVisualizationOptions();
    }

    updateVisualizationOptions() {
        const visualizeAs = this.form.querySelector('input[name="visualizeAs"]:checked')?.value || 'doughnut';
        this.handleVisualizationChange(visualizeAs);
    }

    handleVisualizationChange(visualizeType) {
        const visualizeBySection = document.getElementById('visualizeBySection');
        const displaySection = document.getElementById('displaySection');
        const visualizeBySelect = document.getElementById('widgetVisualizeBy');
        const displayChips = document.getElementById('widgetDisplay');
        
        // Always clear preview immediately when visualization type changes
        const preview = document.getElementById('widgetPreview');
        if (preview) {
            this.destroyExistingCharts(preview);
            // Show a message that preview will update
            if (this.previewShown) {
                preview.innerHTML = '<div class="preview-loading-message">Updating preview...</div>';
            }
        }
        
        if (visualizeType === 'table') {
            if (visualizeBySection) visualizeBySection.style.display = 'none';
            if (displaySection) displaySection.style.display = 'block';
            if (visualizeBySelect) visualizeBySelect.removeAttribute('required');
            this.populateDisplayChips();
        } else if (visualizeType === 'count') {
            if (visualizeBySection) visualizeBySection.style.display = 'none';
            if (displaySection) displaySection.style.display = 'none';
            if (visualizeBySelect) visualizeBySelect.removeAttribute('required');
        } else {
            // Chart types (doughnut, bar) - visualizeBy is required
            if (visualizeBySection) visualizeBySection.style.display = 'block';
            if (displaySection) displaySection.style.display = 'none';
            if (visualizeBySelect) {
                // Only add required if widget source is savedSearch
                const widgetSource = document.querySelector('input[name="widgetSource"]:checked')?.value;
                if (widgetSource === 'savedSearch') {
                    visualizeBySelect.setAttribute('required', '');
                }
            }
            this.populateVisualizeBySelect();
        }
        
        // Auto-refresh preview when visualization type changes
        // Use setTimeout to ensure DOM updates are complete
        setTimeout(() => {
            this.autoRefreshPreview();
        }, 100);
    }

    populateVisualizeBySelect() {
        const select = document.getElementById('widgetVisualizeBy');
        if (!select || !this.currentFacet) {
            console.log('populateVisualizeBySelect: Missing select or currentFacet', { select: !!select, currentFacet: this.currentFacet });
            return;
        }
        
        select.innerHTML = '<option value="">Select a column</option>';
        
        const columns = this.facetColumns[this.currentFacet] || [];
        console.log('populateVisualizeBySelect: Columns for facet', this.currentFacet, columns);
        
        if (columns.length === 0) {
            console.log('populateVisualizeBySelect: No columns found for facet', this.currentFacet);
            return;
        }
        
        // Get visualization columns prioritizing Lifecycle, BUDG Status, BUDG Viewing
        const visualizationColumns = this.getVisualizationColumnsForFacet(columns);
        console.log('populateVisualizeBySelect: Visualization columns', visualizationColumns);
        
        if (visualizationColumns.length === 0) {
            console.log('populateVisualizeBySelect: No visualization columns found');
            return;
        }
        
        visualizationColumns.forEach(column => {
            const option = document.createElement('option');
            option.value = column.value;
            option.textContent = column.label;
            if (column.priority) {
                option.style.fontWeight = 'bold';
            }
            select.appendChild(option);
        });
    }
    
    /**
     * Get visualization columns for a facet, prioritizing Lifecycle, BUDG Status, BUDG Viewing
     * Same logic as Unison search dashboard
     */
    getVisualizationColumnsForFacet(allColumns) {
        // Get the current facet name
        const facetName = this.currentFacet;
        if (!facetName) {
            console.warn('No current facet set');
            return [];
        }
        
        console.log('getVisualizationColumnsForFacet: Getting mandatory dropdowns for facet:', facetName);
        
        // Define mandatory visualization dropdown options for each facet
        // These are the standard dropdown fields used in Unison search dashboard
        const mandatoryVisualizationFields = {
            'Dataset': [
                { value: 'lifecycle', label: 'Lifecycle' },
                { value: 'status', label: 'BUDG Status' },
                { value: 'MasterSource', label: 'System' },
                { value: 'DatasetType', label: 'Type' },
                { value: 'glossary', label: 'Glossary' }
            ],
            'Data Sets': [  // Handle plural form
                { value: 'lifecycle', label: 'Lifecycle' },
                { value: 'status', label: 'BUDG Status' },
                { value: 'MasterSource', label: 'System' },
                { value: 'DatasetType', label: 'Type' },
                { value: 'glossary', label: 'Glossary' }
            ],
            'Attribute': [
                { value: 'Requirement_ID', label: 'Lifecycle' },
                { value: 'Editability', label: 'BUDG Status' },
                { value: 'Dataset_ID', label: 'Dataset' },
                { value: 'Origination', label: 'Origin' },
                { value: 'Editability', label: 'Editability' },
                { value: 'Editability_role', label: 'Editability Role' },
                { value: 'Is_Mandatory', label: 'Is Mandatory' },
                { value: 'Is_PrimaryKey', label: 'Is Primary Key' },
                { value: 'Data_type_ID', label: 'Data Type' }
            ],
            'System': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' },
                { value: 'Type', label: 'Type' },
                { value: 'Classification', label: 'Classification' },
                { value: 'External', label: 'External' }
            ],
            'Glossary': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' },
                { value: 'Type', label: 'Type' },
                { value: 'Security_Classification', label: 'Security Classification' },
                { value: 'Format_type', label: 'Format Type' },
                { value: 'KDE', label: 'KDE' }
            ],
            'People': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' },
                { value: 'Function', label: 'Function' },
                { value: 'Profile_ID', label: 'Profile' },
                { value: 'Employee_Type', label: 'Employee Type' }
            ],
            'Role': [
                { value: 'Role_Type', label: 'Role Type' },
                { value: 'Object_Type', label: 'Object Type' },
                { value: 'Role_Accepted', label: 'Role Accepted' }
            ],
            'Business Area': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' }
            ],
            'Legal Entity': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' }
            ],
            'Client': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' }
            ],
            'Committee': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' },
                { value: 'Type', label: 'Type' },
                { value: 'Classification', label: 'Classification' }
            ],
            'Policy': [
                { value: 'Lifecycle_Status', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' },
                { value: 'Policy_Type', label: 'Type' },
                { value: 'Internal', label: 'Internal' }
            ],
            'Process': [
                { value: 'lifecycle_status', label: 'Lifecycle' },
                { value: 'status', label: 'BUDG Status' },
                { value: 'type', label: 'Type' },
                { value: 'processautomation_id', label: 'Automation' },
                { value: 'processclass_id', label: 'Classification' }
            ],
            'Interface': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' },
                { value: 'Automation', label: 'Automation' },
                { value: 'Frequency', label: 'Frequency' },
                { value: 'Transfer_Method', label: 'Transfer Method' },
                { value: 'Transfer_Format', label: 'Transfer Format' },
                { value: 'Classification', label: 'Classification' }
            ],
            'Capability': [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'BUDG Status' }
            ],
            'Product': [
                { value: 'lifecycle_status', label: 'Lifecycle' },
                { value: 'status', label: 'BUDG Status' }
            ],
            'Org Unit': [
                { value: 'Status', label: 'BUDG Status' }
            ],
            'Geography': [
                // Geography typically doesn't have status/lifecycle fields
            ],
            'Regulation': [
                { value: 'Status', label: 'BUDG Status' },
                { value: 'Stage', label: 'Stage' },
                { value: 'Maturity', label: 'Maturity' },
                { value: 'Probability', label: 'Probability' },
                { value: 'Compliance_Level', label: 'Compliance Level' }
            ],
            'Regulator': [
                // Regulator typically doesn't have status/lifecycle fields
            ],
            'Regulatory Theme': [
                { value: 'Status', label: 'BUDG Status' }
            ]
        };
        
        // Get mandatory fields for the current facet
        const mandatoryFields = mandatoryVisualizationFields[facetName] || [];
        console.log('Mandatory visualization fields for', facetName + ':', mandatoryFields);
        
        // Create a map of available columns for quick lookup
        const availableColumns = new Map();
        allColumns.forEach(col => {
            const colName = col.name || col;
            availableColumns.set(colName, col);
        });
        
        // Filter mandatory fields to only include those that exist in the database
        const validFields = [];
        mandatoryFields.forEach(field => {
            if (availableColumns.has(field.value)) {
                validFields.push(field);
                console.log(`✓ Found mandatory field: ${field.label} (${field.value})`);
            } else {
                console.log(`✗ Mandatory field not available: ${field.label} (${field.value})`);
            }
        });
        
        // If no mandatory fields are available, fall back to common fields
        if (validFields.length === 0) {
            console.log('No mandatory fields available, using fallback fields');
            const fallbackFields = [
                { value: 'Lifecycle', label: 'Lifecycle' },
                { value: 'lifecycle', label: 'Lifecycle' },
                { value: 'Status', label: 'Status' },
                { value: 'status', label: 'Status' },
                { value: 'Type', label: 'Type' },
                { value: 'type', label: 'Type' },
                { value: 'Name', label: 'Name' },
                { value: 'PrimaryName', label: 'Name' }
            ];
            
            fallbackFields.forEach(field => {
                if (availableColumns.has(field.value) && !validFields.some(v => v.value === field.value)) {
                    validFields.push(field);
                    console.log(`✓ Added fallback field: ${field.label} (${field.value})`);
                }
            });
        }
        
        console.log('getVisualizationColumnsForFacet: Final mandatory fields', validFields);
        return validFields;
    }

    populateDisplayChips() {
        const container = document.getElementById('widgetDisplay');
        if (!container || !this.currentFacet) return;
        
        container.innerHTML = '';
        
        const columns = this.facetColumns[this.currentFacet] || [];
        const selectedColumns = new Set();
        
        columns.forEach(column => {
            const chip = document.createElement('div');
            chip.className = 'display-chip';
            chip.dataset.column = column.name;
            
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.id = `display-${column.name}`;
            checkbox.value = column.name;
            checkbox.addEventListener('change', (e) => {
                if (e.target.checked) {
                    selectedColumns.add(column.name);
                } else {
                    selectedColumns.delete(column.name);
                }
                // Auto-refresh preview when display columns change (for table visualization)
                setTimeout(() => {
                    this.autoRefreshPreview();
                }, 100);
            });
            
            const label = document.createElement('label');
            label.htmlFor = checkbox.id;
            label.textContent = column.displayName || column.name;
            
            chip.appendChild(checkbox);
            chip.appendChild(label);
            container.appendChild(chip);
        });
    }

    async handlePreview() {
        const formData = new FormData(this.form);
        const data = {
            widgetSource: formData.get('widgetSource'),
            source: formData.get('source'),
            focus: formData.get('focus'),
            title: formData.get('title'),
            description: formData.get('description'),
            visualizeAs: formData.get('visualizeAs'),
            visualizeBy: formData.get('visualizeBy'),
            display: this.getSelectedDisplayColumns()
        };
        
        // Validate required fields
        if (data.widgetSource === 'savedSearch') {
            if (!data.source || !data.focus) {
                console.warn('Preview validation failed:', { source: data.source, focus: data.focus });
                // Don't show alert in edit mode, just log
                if (!this.modal?.getAttribute('data-edit-mode')) {
                    alert('Please select Source and Focus before previewing');
                }
                return;
            }
            
            // For chart types, visualizeBy is required
            if ((data.visualizeAs === 'doughnut' || data.visualizeAs === 'bar') && !data.visualizeBy) {
                console.warn('Preview validation failed: visualizeBy required for chart types');
                // Don't show alert in edit mode, just log
                if (!this.modal?.getAttribute('data-edit-mode')) {
                    alert('Please select "Visualize By" before previewing');
                }
                return;
            }
            
            // For table type, at least one display column is required
            if (data.visualizeAs === 'table' && (!data.display || data.display.length === 0)) {
                console.warn('Preview validation failed: display columns required for table');
                // Don't show alert in edit mode, just log
                if (!this.modal?.getAttribute('data-edit-mode')) {
                    alert('Please select at least one display column before previewing');
                }
                return;
            }
        }
        
        try {
            const response = await fetch('/api/dashboard/new-widget/preview', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${localStorage.getItem('token')}`
                },
                body: JSON.stringify(data)
            });
            
            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to generate preview');
            }
            
            const previewData = await response.json();
            console.log('Preview data received:', previewData);
            
            // Ensure preview section is visible
            const previewSection = document.getElementById('savedSearchPreviewSection');
            if (previewSection) {
                previewSection.style.display = 'block';
            }
            
            this.renderPreview(previewData);
            // Mark that preview has been shown
            this.previewShown = true;
        } catch (error) {
            console.error('Error generating preview:', error);
            
            // Show error in preview area instead of alert
            const preview = document.getElementById('widgetPreview');
            if (preview) {
                preview.innerHTML = `
                    <div class="preview-error">
                        <p style="color: #dc3545;">Error generating preview: ${error.message}</p>
                    </div>
                `;
            }
            
            // Only show alert if not in edit mode
            if (!this.modal?.getAttribute('data-edit-mode')) {
                alert('Error generating preview: ' + error.message);
            }
        }
    }
    
    /**
     * Auto-refresh preview when visualization options change
     * Only refreshes if preview has already been shown once
     */
    autoRefreshPreview() {
        // Only auto-refresh if preview has been shown at least once
        if (!this.previewShown) {
            return;
        }
        
        const formData = new FormData(this.form);
        const widgetSource = formData.get('widgetSource');
        const source = formData.get('source');
        const focus = formData.get('focus');
        const visualizeAs = formData.get('visualizeAs');
        const visualizeBy = formData.get('visualizeBy');
        const display = this.getSelectedDisplayColumns();
        
        // Only auto-refresh for saved search widgets
        if (widgetSource !== 'savedSearch') {
            return;
        }
        
        // Check if required fields are filled
        if (!source || !focus) {
            return;
        }
        
        // For count type, we can refresh immediately (no visualizeBy needed)
        if (visualizeAs === 'count') {
            console.log('Auto-refreshing preview for count type...');
            this.handlePreview();
            return;
        }
        
        // For chart types (doughnut, bar), visualizeBy is required
        if ((visualizeAs === 'doughnut' || visualizeAs === 'bar') && !visualizeBy) {
            // Don't refresh yet, wait for visualizeBy to be selected
            return;
        }
        
        // For table type, display columns are required
        if (visualizeAs === 'table' && (!display || display.length === 0)) {
            // Clear preview and show message that display columns need to be selected
            const preview = document.getElementById('widgetPreview');
            if (preview) {
                preview.innerHTML = '<div class="preview-loading-message">Please select at least one column to display</div>';
            }
            return;
        }
        
        // All conditions met, refresh preview
        console.log('Auto-refreshing preview...');
        this.handlePreview();
    }

    getSelectedDisplayColumns() {
        const checkboxes = document.querySelectorAll('#widgetDisplay input[type="checkbox"]:checked');
        return Array.from(checkboxes).map(cb => cb.value);
    }

    renderPreview(previewData) {
        const preview = document.getElementById('widgetPreview');
        if (!preview) return;
        
        // Clear any existing chart instances first
        this.destroyExistingCharts(preview);
        
        // Clear the preview container
        preview.innerHTML = '';
        
        if (previewData.error) {
            preview.innerHTML = `
                <div class="preview-error">
                    <p style="color: #dc3545;">${previewData.error}</p>
                </div>
            `;
            return;
        }
        
        if (previewData.type === 'count') {
            preview.innerHTML = `
                <div class="preview-count">
                    <div class="preview-count-number">${previewData.count || 0}</div>
                    <div class="preview-count-label">out of ${previewData.count || 0}</div>
                    <div class="preview-count-facet">${previewData.facet || ''}</div>
                </div>
            `;
        } else if (previewData.type === 'text') {
            preview.innerHTML = `
                <div class="preview-text">
                    <p>Text widget preview will be displayed here</p>
                </div>
            `;
        } else if (previewData.type === 'table') {
            this.renderTablePreview(preview, previewData);
        } else if (previewData.type === 'doughnut' || previewData.type === 'bar') {
            this.renderChartPreview(preview, previewData);
        } else {
            preview.innerHTML = `
                <div class="preview-chart">
                    <p>${previewData.message || 'Chart preview will be displayed here'}</p>
                </div>
            `;
        }
    }
    
    /**
     * Destroy any existing Chart.js instances in the preview container
     */
    destroyExistingCharts(container) {
        if (!container) return;
        
        // Find all canvas elements
        const canvases = container.querySelectorAll('canvas');
        canvases.forEach(canvas => {
            // Destroy Chart.js instance if it exists
            if (canvas.chartInstance) {
                try {
                    canvas.chartInstance.destroy();
                } catch (e) {
                    console.warn('Error destroying chart instance:', e);
                }
                canvas.chartInstance = null;
            }
        });
    }
    
    renderTablePreview(container, previewData) {
        console.log('renderTablePreview called with:', previewData);
        console.log('previewData.columns:', previewData.columns);
        console.log('previewData.data:', previewData.data);
        
        if (!previewData.columns || !previewData.data) {
            container.innerHTML = '<div class="preview-error"><p>No data available</p></div>';
            return;
        }
        
        const facetName = previewData.facet || previewData.focus || '';
        
        let html = '<div class="preview-table-container">';
        html += '<table class="preview-table"><thead><tr>';
        previewData.columns.forEach(col => {
            html += `<th>${this.formatColumnName(col)}</th>`;
        });
        html += '</tr></thead><tbody>';
        
        if (previewData.data.length === 0) {
            html += `<tr><td colspan="${previewData.columns.length}" style="text-align: center; padding: 2rem;">No data found</td></tr>`;
        } else {
            previewData.data.forEach(row => {
                html += '<tr>';
                previewData.columns.forEach(col => {
                    const value = row[col] || '';
                    html += `<td>${this.escapeHtml(String(value))}</td>`;
                });
                html += '</tr>';
            });
        }
        
        html += '</tbody></table>';
        if (previewData.count > 0) {
            html += `<div class="preview-table-footer">Showing ${previewData.data.length} of ${previewData.count} results</div>`;
        }
        if (facetName) {
            html += `<div class="preview-table-facet-name">${this.escapeHtml(facetName)}</div>`;
        }
        html += '</div>';
        
        container.innerHTML = html;
    }
    
    renderChartPreview(container, previewData) {
        console.log('renderChartPreview called with:', previewData);
        console.log('previewData.data:', previewData.data);
        console.log('previewData.facet:', previewData.facet);
        console.log('previewData.data keys length:', previewData.data ? Object.keys(previewData.data).length : 'data is null/undefined');
        
        if (!previewData.data || Object.keys(previewData.data).length === 0) {
            console.log('No chart data available - showing error message');
            container.innerHTML = '<div class="preview-error"><p>No data available for chart</p></div>';
            return;
        }
        
        // Get facet name - try multiple sources
        const facetName = previewData.facet || previewData.focus || '';
        console.log('Facet name to display:', facetName);
        
        const canvasId = 'preview-chart-' + Date.now();
        
        // Create container with chart and facet name
        let html = '<div class="preview-chart-container">';
        html += `<canvas id="${canvasId}" style="max-width: 100%; max-height: 300px;"></canvas>`;
        if (facetName) {
            html += `<div class="preview-chart-facet-name">${this.escapeHtml(facetName)}</div>`;
        }
        html += '</div>';
        
        container.innerHTML = html;
        
        // Wait for DOM to update
        setTimeout(() => {
            const canvas = document.getElementById(canvasId);
            if (!canvas || !window.Chart) {
                container.innerHTML = '<div class="preview-error"><p>Chart.js not loaded</p></div>';
                return;
            }
            
            const labels = Object.keys(previewData.data);
            const data = Object.values(previewData.data);
            
            const config = {
                type: previewData.type === 'doughnut' ? 'doughnut' : 'bar',
                data: {
                    labels: labels,
                    datasets: [{
                        label: previewData.column || 'Count',
                        data: data,
                        backgroundColor: this.generateColors(labels.length),
                        borderColor: '#ffffff',
                        borderWidth: 1
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: true,
                    plugins: {
                        legend: {
                            position: 'right',
                            display: previewData.type === 'doughnut'
                        }
                    },
                    scales: previewData.type === 'bar' ? {
                        y: {
                            beginAtZero: true
                        }
                    } : undefined
                }
            };
            
            // Store chart instance for cleanup
            canvas.chartInstance = new Chart(canvas, config);
        }, 100);
    }
    
    generateColors(count) {
        const colors = [
            '#248567', '#48bb78', '#38a169', '#2f855a', '#22543d',
            '#4299e1', '#3182ce', '#2c5282', '#2a4365', '#1a365d',
            '#ed8936', '#dd6b20', '#c05621', '#9c4221', '#7c2d12',
            '#9f7aea', '#805ad5', '#6b46c1', '#553c9a', '#44337a'
        ];
        
        const result = [];
        for (let i = 0; i < count; i++) {
            result.push(colors[i % colors.length]);
        }
        return result;
    }
    
    formatColumnName(columnName) {
        return columnName.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase()).trim();
    }
    
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async handleSubmit() {
        console.log(`[NewWidget] === HANDLE SUBMIT CALLED ===`);
        
        try {
            const formData = new FormData(this.form);
            const widgetSource = formData.get('widgetSource');
            console.log(`[NewWidget] Form data extracted, widgetSource: ${widgetSource}`);
        
        // Remove required attribute from fields that aren't needed for the current widget type
        if (widgetSource === 'text') {
            // For text widgets, remove required from saved search fields
            const sourceSelect = document.getElementById('widgetSourceSelect');
            const focusSelect = document.getElementById('widgetFocusSelect');
            const visualizeBySelect = document.getElementById('widgetVisualizeBy');
            
            if (sourceSelect) sourceSelect.removeAttribute('required');
            if (focusSelect) focusSelect.removeAttribute('required');
            if (visualizeBySelect) visualizeBySelect.removeAttribute('required');
        }
        
        // Now check validity
        if (!this.form.checkValidity()) {
            this.form.reportValidity();
            return;
        }
        
        // Get dashboard ID from modal attribute or current dashboard
        // For new dashboards, dashboardId must be provided
        // For main dashboard, it can be null (will use default dashboard)
        let dashboardId = this.modal.getAttribute('data-dashboard-id');
        if (dashboardId === null || dashboardId === '') {
            dashboardId = (window.dashboard && window.dashboard.currentDashboardId) || null;
        }
        
        // Convert to number if it's a string
        if (dashboardId !== null && dashboardId !== '') {
            dashboardId = parseInt(dashboardId);
            if (isNaN(dashboardId)) {
                dashboardId = null;
            }
        }

        const data = {
            widgetSource: widgetSource,
            title: formData.get('title'),
            description: formData.get('description') || '' // Optional for text widgets
        };
        
        // Always add dashboard ID (can be null for main dashboard)
        data.dashboardId = dashboardId;
        
        // Add source-specific data
        if (widgetSource === 'savedSearch') {
            data.source = formData.get('source');
            data.focus = formData.get('focus');
            data.visualizeAs = formData.get('visualizeAs');
            data.visualizeBy = formData.get('visualizeBy');
            data.display = this.getSelectedDisplayColumns();
            
            // Validate saved search fields
            if (!data.source || !data.focus) {
                alert('Please select a saved search and facet');
                return;
            }
            
            if (data.visualizeAs !== 'count' && data.visualizeAs !== 'table' && !data.visualizeBy) {
                alert('Please select a column to visualize by');
                return;
            }
            
            if (data.visualizeAs === 'table' && (!data.display || data.display.length === 0)) {
                alert('Please select at least one column to display');
                return;
            }
        } else if (widgetSource === 'text') {
            // Get text content from rich text editor
            const editorContent = document.getElementById('textWidgetEditorContent');
            if (editorContent) {
                // If in code view, get the HTML from backup, otherwise get innerHTML
                if (editorContent.hasAttribute('data-code-view')) {
                    data.textContent = editorContent.getAttribute('data-html-backup') || editorContent.textContent;
                } else {
                    data.textContent = editorContent.innerHTML;
                }
            } else {
                data.textContent = '';
            }
        }
        
        // Show loading state
        const okBtn = document.getElementById('newWidgetOK');
        const originalText = okBtn ? okBtn.textContent : 'OK';
        console.log(`[NewWidget] Setting loading state on OK button`);
        if (okBtn) {
            okBtn.disabled = true;
            okBtn.textContent = this.isEditMode() ? 'Updating...' : 'Creating...';
            console.log(`[NewWidget] OK button disabled and text changed to: ${okBtn.textContent}`);
        }
        
        try {
            let url, method;
            
            // Check if we're in edit mode
            if (this.isEditMode()) {
                const widgetId = this.modal.getAttribute('data-widget-id');
                url = `/api/dashboard/widgets/update/${widgetId}`;
                method = 'PUT';
            } else {
                url = '/api/dashboard/create-widget';
                method = 'POST';
            }
            
            const response = await fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(data)
            });
            
            const result = await response.json();
            
            if (response.ok && result.success) {
                // Get dashboard ID BEFORE closing modal (to preserve it)
                let dashboardId = null;
                if (window.dashboard) {
                    dashboardId = this.modal.getAttribute('data-dashboard-id');
                    if (dashboardId === null || dashboardId === '') {
                        dashboardId = window.dashboard.currentDashboardId;
                    }
                    
                    // Convert to number if it's a string
                    if (dashboardId !== null && dashboardId !== '') {
                        dashboardId = parseInt(dashboardId);
                        if (isNaN(dashboardId)) {
                            dashboardId = null;
                        }
                    }
                }
                
                // Success - close modal
                this.close();
                
                // Small delay to ensure modal is closed before refreshing
                await new Promise(resolve => setTimeout(resolve, 100));
                
                // Refresh dashboard widgets if dashboard instance exists
                if (window.dashboard) {
                    console.log(`[NewWidget] Widget created successfully. Dashboard ID: ${dashboardId}`);
                    console.log(`[NewWidget] Full API response:`, result);
                    
                    // The API returns widgetId, not the full widget data
                    // So we need to fetch the widget data after creation
                    const widgetId = result.widgetId;
                    
                    if (widgetId) {
                        console.log(`[NewWidget] Fetching widget data for widgetId: ${widgetId}`);
                        
                        // Small delay to ensure widget is fully saved in database
                        await new Promise(resolve => setTimeout(resolve, 200));
                        
                        try {
                            // Fetch the full widget data
                            const widgetResponse = await fetch(`/api/dashboard/widgets/${widgetId}`, {
                                credentials: 'include'
                            });
                            
                            console.log(`[NewWidget] Widget fetch response status: ${widgetResponse.status}`);
                            
                            if (widgetResponse.ok) {
                                const widgetData = await widgetResponse.json();
                                console.log(`[NewWidget] Widget data fetched:`, widgetData);
                                
                                if (widgetData && widgetData.id) {
                                    console.log(`[NewWidget] Calling renderCustomWidget with widgetData`);
                                    await window.dashboard.renderCustomWidget(widgetData);
                                    console.log(`[NewWidget] Widget rendered successfully - NO RELOAD NEEDED`);
                                    
                                    // Setup widget controls after rendering
                                    setTimeout(() => {
                                        if (window.dashboard) {
                                            window.dashboard.setupWidgetControls();
                                            // Setup drag and drop
                                            if (window.dashboard.dragDrop) {
                                                window.dashboard.dragDrop.setupDragAndDrop();
                                            }
                                            console.log(`[NewWidget] Widget controls and drag-drop setup complete`);
                                        }
                                    }, 100);
                                    
                                    // SUCCESS - widget rendered, don't call any reload functions
                                    console.log(`[NewWidget] Widget addition complete, exiting without reload`);
                                    return; // Exit here to prevent any fallback reloads
                                } else {
                                    console.warn(`[NewWidget] Widget data is invalid:`, widgetData);
                                    console.warn(`[NewWidget] Falling back to reload`);
                                    await window.dashboard.loadDashboardWidgets(dashboardId);
                                }
                            } else {
                                const errorText = await widgetResponse.text();
                                console.error(`[NewWidget] FAILED to fetch widget data (${widgetResponse.status}):`, errorText);
                                console.error(`[NewWidget] This should not happen - check if widget was actually created`);
                                console.warn(`[NewWidget] Falling back to reload`);
                                await window.dashboard.loadDashboardWidgets(dashboardId);
                            }
                        } catch (error) {
                            console.error(`[NewWidget] EXCEPTION while fetching widget data:`, error);
                            console.error(`[NewWidget] This indicates a network or API issue`);
                            console.warn(`[NewWidget] Falling back to reload`);
                            await window.dashboard.loadDashboardWidgets(dashboardId);
                        }
                    } else {
                        console.error(`[NewWidget] NO WIDGET ID in response:`, result);
                        console.error(`[NewWidget] Widget creation may have failed silently`);
                        console.warn(`[NewWidget] Falling back to reload`);
                        // Fallback: reload widgets if widgetId not available
                        await window.dashboard.loadDashboardWidgets(dashboardId);
                    }
                } else {
                    console.error(`[NewWidget] Dashboard instance not found, reloading page`);
                    // Fallback to page reload
                    window.location.reload();
                }
            } else {
                // Error
                alert(result.error || `Failed to ${this.isEditMode() ? 'update' : 'create'} widget. Please try again.`);
                if (okBtn) {
                    okBtn.disabled = false;
                    okBtn.textContent = originalText;
                }
            }
        } catch (error) {
            console.error(`[NewWidget] CRITICAL ERROR in ${this.isEditMode() ? 'updating' : 'creating'} widget:`, error);
            console.error(`[NewWidget] Error stack:`, error.stack);
            alert(`An error occurred while ${this.isEditMode() ? 'updating' : 'creating'} the widget. Please try again.`);
            if (okBtn) {
                okBtn.disabled = false;
                okBtn.textContent = originalText;
            }
        }
    }
    
    /**
     * Check if modal is in edit mode
     */
    isEditMode() {
        return this.modal && this.modal.getAttribute('data-edit-mode') === 'true';
    }
}

// Initialize modal when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.NewWidgetModal = new NewWidgetModal();
    });
} else {
    window.NewWidgetModal = new NewWidgetModal();
}

