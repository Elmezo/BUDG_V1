/**
 * Segments Cube Panel Component
 * Accessible from main header navigation
 * Shows all accessible segments for the current user
 * Works like Global Lock UI
 */

class SegmentsCubePanel {
    constructor() {
        this.panel = null;
        this.cubeButton = null;
        this.badge = null;
        this.isInitialized = false;
        this.isAuthenticated = false;
        this.segmentsData = [];
        this.selectedSegmentIds = new Set();
        this.persistedSegmentIds = new Set(); // Selection loaded from database
        this.totalSegments = 0;
        this.selectAllCheckbox = null;
        this.validationMessageEl = null;
        this.selectionSummaryEl = null;
        this.segmentCheckboxMap = new Map();
        this.showValidationError = false;
        this.selectionLoaded = false;
        this._initializePromise = null;
    }

    /**
     * Initialize the Segments Cube Panel component
     * Serialized so header.js + DOMContentLoaded cannot attach duplicate listeners.
     */
    async initialize() {
        if (this.isInitialized) {
            return;
        }
        if (this._initializePromise) {
            return this._initializePromise;
        }
        this._initializePromise = this._initializeOnce();
        try {
            await this._initializePromise;
        } finally {
            this._initializePromise = null;
        }
    }

    async _initializeOnce() {
        this.isAuthenticated = await this.checkAuthentication();

        if (!this.isAuthenticated) {
            const cube = document.getElementById('segmentsCubeToggle');
            if (cube) {
                cube.style.display = 'none';
                cube.style.visibility = 'hidden';
            }
            return;
        }

        await this.waitForHeader();

        this.cubeButton = document.getElementById('segmentsCubeToggle');
        this.badge = document.getElementById('segmentsBadge');

        if (!this.cubeButton || !this.badge) {
            return;
        }

        this.cubeButton.style.display = 'flex';
        this.cubeButton.style.visibility = 'visible';
        this.cubeButton.style.opacity = '1';

        this.createPanel();
        this.setupEventListeners();
        this.isInitialized = true;

        await this.updateSegmentCount();
    }

    /**
     * GET /api/segments/accessible with optional token refresh (matches updateSegmentCount behavior).
     */
    async fetchAccessibleSegments(signal) {
        const opts = (extra) => ({
            method: 'GET',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            ...extra
        });

        let response = await fetch('/api/segments/accessible', opts({ signal }));

        if (response.status === 401) {
            const refreshResponse = await fetch('/api/refresh', {
                method: 'POST',
                credentials: 'include',
                signal
            });
            if (refreshResponse.ok) {
                response = await fetch('/api/segments/accessible', opts({ signal }));
            }
        }

        return response;
    }

    /**
     * Check if user is authenticated
     */
    async checkAuthentication() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });
            if (!response.ok) {
                return false;
            }
            const me = await response.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            return me.authenticated === true && !role.includes('guest');
        } catch (error) {
            return false;
        }
    }

    /**
     * Wait for header to be injected
     */
    waitForHeader() {
        return new Promise((resolve) => {
            if (document.getElementById('segmentsCubeToggle')) {
                resolve();
                return;
            }
            
            window.addEventListener('headerReady', () => {
                resolve();
            }, { once: true });
            
            // Timeout after 5 seconds
            setTimeout(resolve, 5000);
        });
    }

    /**
     * Create the Segments panel
     */
    createPanel() {
        // Remove existing panel if any
        const existing = document.getElementById('segmentsCubePanel');
        if (existing) {
            existing.remove();
        }
        
        const panel = document.createElement('div');
        panel.id = 'segmentsCubePanel';
        panel.className = 'segments-cube-panel';
        panel.innerHTML = `
            <div class="panel-header">
                <h3><i class="fas fa-cube"></i> MY SEGMENTS</h3>
                <button class="close-btn" id="closeSegmentsCubePanelBtn">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="panel-content">
                <div class="segments-loading">
                    <i class="fas fa-spinner fa-pulse"></i>
                    <p>Loading segments...</p>
                </div>
            </div>
        `;
        
        document.body.appendChild(panel);
        this.panel = panel;
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Toggle panel on cube button click
        if (this.cubeButton) {
            this.cubeButton.addEventListener('click', (e) => {
                e.stopPropagation();
                this.togglePanel();
            });
        }

        // Close button
        const closeBtn = this.panel.querySelector('#closeSegmentsCubePanelBtn');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                this.closePanel();
            });
        }

        // Close on outside click
        document.addEventListener('click', (e) => {
            if (this.panel && 
                this.panel.classList.contains('open') && 
                !this.panel.contains(e.target) && 
                !this.cubeButton.contains(e.target)) {
                this.closePanel();
            }
        });

        // Close on escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.panel && this.panel.classList.contains('open')) {
                this.closePanel();
            }
        });

        window.addEventListener('resize', () => {
            if (this.panel && this.panel.classList.contains('open') && this.cubeButton) {
                this.positionPanelUnderTrigger(this.panel, this.cubeButton);
            }
        });
    }

    /**
     * Position panel under its trigger button (works in LTR and RTL).
     * On narrow viewports, center in the viewport — anchor-to-trigger + wide width clips off-screen (LTR uses right:...).
     */
    positionPanelUnderTrigger(panel, trigger) {
        if (!panel || !trigger) return;
        const isMobile = typeof window.matchMedia === 'function'
            ? window.matchMedia('(max-width: 768px)').matches
            : window.innerWidth <= 768;

        if (isMobile) {
            panel.style.top = '';
            panel.style.left = '';
            panel.style.right = '';
            panel.style.bottom = '';
            panel.style.width = '';
            panel.style.maxHeight = '';
            panel.style.transform = '';
            panel.classList.add('panel-mobile-centered');
            return;
        }

        panel.classList.remove('panel-mobile-centered');
        const rect = trigger.getBoundingClientRect();
        const gap = 8;
        panel.style.top = (rect.bottom + gap) + 'px';
        const isRtl = document.documentElement.getAttribute('dir') === 'rtl';
        if (isRtl) {
            panel.style.left = rect.left + 'px';
            panel.style.right = 'auto';
        } else {
            panel.style.right = (window.innerWidth - rect.right) + 'px';
            panel.style.left = 'auto';
        }
    }

    /**
     * Close other header panels (notification, profile) so only one is open
     */
    closeOtherHeaderPanels() {
        if (window.notificationPanel && typeof window.notificationPanel.closePanel === 'function') {
            window.notificationPanel.closePanel();
        }
        if (window.globalLockUI && typeof window.globalLockUI.closePanel === 'function') {
            window.globalLockUI.closePanel();
        }
        const profileDropdown = document.getElementById('profileDropdown');
        if (profileDropdown) profileDropdown.classList.remove('show');
        const myItemsDd = document.getElementById('myItemsDropdown');
        if (myItemsDd) { myItemsDd.classList.remove('show'); myItemsDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active')); }
        const createDd = document.getElementById('createDropdown');
        if (createDd) { createDd.classList.remove('show'); createDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active')); }
    }

    /**
     * Toggle panel visibility
     */
    async togglePanel() {
        if (this.panel.classList.contains('open')) {
            this.closePanel();
        } else {
            await this.openPanel();
        }
    }

    /**
     * Open the panel and load segments
     */
    async openPanel() {
        this.closeOtherHeaderPanels();
        this.positionPanelUnderTrigger(this.panel, this.cubeButton);
        this.panel.classList.add('open');
        await this.loadSegments();
    }

    /**
     * Close the panel
     */
    closePanel() {
        if (this.panel) {
            this.panel.classList.remove('open');
            this.panel.classList.remove('panel-mobile-centered');
        }
    }

    /**
     * Update segment count and show/hide button
     * CRITICAL: All authenticated users should see at least Enterprise segment (ID=1)
     * Even users with no assigned segments should see the cube button with Enterprise
     */
    async updateSegmentCount() {
        // Ensure button is visible before making API call
        if (this.cubeButton) {
            this.cubeButton.style.display = 'flex';
            this.cubeButton.style.visibility = 'visible';
        }
        
        try {
            const response = await fetch('/api/segments/accessible', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (response.status === 401) {
                // Try to refresh token and retry
                const refreshResponse = await fetch('/api/refresh', {
                    method: 'POST',
                    credentials: 'include'
                });
                
                if (refreshResponse.ok) {
                    // Retry the request
                    const retryResponse = await fetch('/api/segments/accessible', {
                        method: 'GET',
                        credentials: 'include',
                        headers: {
                            'Content-Type': 'application/json'
                        }
                    });
                    
                    if (!retryResponse.ok) {
                        const errorText = await retryResponse.text();
                        console.warn('❌ Failed to fetch segments after refresh:', retryResponse.status, errorText);
                        // Even if API fails, show button with Enterprise (default for all users)
                        await this.handleSegmentData({ segments: [{ id: 1, name: 'Enterprise', description: 'Default enterprise-wide segment accessible to all users' }], count: 1 });
                        return;
                    }
                    
                    // Use the retry response
                    const data = await retryResponse.json();
                    // Ensure Enterprise is included
                    await this.ensureEnterpriseIncluded(data);
                    await this.handleSegmentData(data);
                    return;
                } else {
                    console.warn('❌ Token refresh failed, user needs to login');
                    // Even if refresh fails, show button with Enterprise (default for all users)
                    await this.handleSegmentData({ segments: [{ id: 1, name: 'Enterprise', description: 'Default enterprise-wide segment accessible to all users' }], count: 1 });
                    return;
                }
            }
            
            if (!response.ok) {
                const errorText = await response.text();
                console.warn('❌ Failed to fetch segments:', response.status, errorText);
                // Even if API fails, show button with Enterprise (default for all users)
                await this.handleSegmentData({ segments: [{ id: 1, name: 'Enterprise', description: 'Default enterprise-wide segment accessible to all users' }], count: 1 });
                return;
            }
            
            const data = await response.json();
            
            // CRITICAL: Ensure Enterprise (ID=1) is always included for all users
            await this.ensureEnterpriseIncluded(data);
            
            await this.handleSegmentData(data);
            
        } catch (error) {
            console.error('❌ Failed to load segments:', error);
            console.error('Error details:', error.stack);
            // Even on error, show button with Enterprise (default for all users)
            await this.handleSegmentData({ segments: [{ id: 1, name: 'Enterprise', description: 'Default enterprise-wide segment accessible to all users' }], count: 1 });
        }
    }
    
    /**
     * Ensure Enterprise segment (ID=1) is always included in the segments list
     * All authenticated users should have access to Enterprise segment
     */
    async ensureEnterpriseIncluded(data) {
        if (!data.segments || !Array.isArray(data.segments)) {
            data.segments = [];
        }
        
        // Check if Enterprise (ID=1) is already in the list
        const hasEnterprise = data.segments.some(s => {
            const id = s.id;
            return id === 1 || id === '1' || String(id) === '1';
        });
        
        if (!hasEnterprise) {
            // Add Enterprise as the first segment
            data.segments.unshift({
                id: 1,
                name: 'Enterprise',
                description: 'Default enterprise-wide segment accessible to all users'
            });
            data.count = data.segments.length;
        }
    }

    /**
     * Handle segment data and update UI
     * 
     * Rules:
     * - Super Admins: See ALL segments (no assignment needed) - they can create/delete all segments
     * - Regular users: See Enterprise + segments they're assigned to
     * - Cube appears if there are segments OTHER than Enterprise (ID=1)
     * - Badge shows total number of ACCESSIBLE segments (like notification bell shows total notifications)
     */
    async handleSegmentData(data) {
        const segments = data.segments || [];
        const totalCount = segments.length;
        
        // Store segments data
        this.segmentsData = segments;
        this.totalSegments = totalCount;
        
        // Load persisted selection from database (only once)
        if (!this.selectionLoaded) {
            await this.loadPersistedSelection();
            this.selectionLoaded = true;
        }
        
        // Always show cube button for authenticated users (like notification bell)
        // Each user should see their assigned segments - at minimum Enterprise (ID=1)
        // Only the badge shows/hides based on segment count
        if (this.cubeButton) {
            this.cubeButton.style.display = 'flex';
            this.cubeButton.style.visibility = 'visible';
            
            if (this.badge) {
                if (totalCount > 0) {
                    this.badge.textContent = totalCount;
                    this.badge.style.display = 'flex';
                } else {
                    this.badge.textContent = '';
                    this.badge.style.display = 'none';
                }
            }
        }
    }
    
    /**
     * Load persisted segment selection from database
     */
    async loadPersistedSelection(signal) {
        try {
            const fetchOpts = {
                method: 'GET',
                credentials: 'include'
            };
            if (signal) {
                fetchOpts.signal = signal;
            }
            const response = await fetch('/api/user/segment-selection', fetchOpts);
            
            if (response.ok) {
                const data = await response.json();
                if (data.success && data.selectedSegmentIds && data.selectedSegmentIds.length > 0) {
                    this.persistedSegmentIds = new Set(data.selectedSegmentIds.map(id => String(id)));
                    this.selectedSegmentIds = new Set(this.persistedSegmentIds);
                } else {
                    this.persistedSegmentIds = new Set(['1']);
                    this.selectedSegmentIds = new Set(['1']);
                }
            } else {
                console.warn('⚠️ Failed to load persisted selection:', response.status);
                // Default to Enterprise
                this.persistedSegmentIds = new Set(['1']);
                this.selectedSegmentIds = new Set(['1']);
            }
        } catch (error) {
            console.warn('⚠️ Error loading persisted selection:', error);
            // Default to Enterprise
            this.persistedSegmentIds = new Set(['1']);
            this.selectedSegmentIds = new Set(['1']);
        }
    }
    
    /**
     * Save segment selection to database
     */
    async saveSelectionToDatabase(segmentIds) {
        try {
            let response = await fetch('/api/user/segment-selection', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    selectedSegmentIds: segmentIds.map(id => parseInt(id, 10))
                })
            });

            if (response.status === 401) {
                const refreshResponse = await fetch('/api/refresh', {
                    method: 'POST',
                    credentials: 'include'
                });

                if (refreshResponse.ok) {
                    response = await fetch('/api/user/segment-selection', {
                        method: 'POST',
                        credentials: 'include',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({
                            selectedSegmentIds: segmentIds.map(id => parseInt(id, 10))
                        })
                    });
                } else {
                    console.warn('❌ Token refresh failed during save');
                    return false;
                }
            }

            if (response.ok) {
                await response.json();
                return true;
            } else {
                const errorText = await response.text();
                console.error('❌ Failed to save segment selection:', response.status, errorText);
                return false;
            }
        } catch (error) {
            console.error('❌ Error saving segment selection:', error);
            return false;
        }
    }

    /**
     * Load and display segments
     */
    async loadSegments() {
        const content = this.panel.querySelector('.panel-content');
        if (!content) return;

        const controller = new AbortController();
        const timeoutMs = 30000;
        const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

        try {
            content.innerHTML = `
                <div class="segments-loading">
                    <i class="fas fa-spinner fa-pulse"></i>
                    <p>Loading segments...</p>
                </div>
            `;

            const response = await this.fetchAccessibleSegments(controller.signal);

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const data = await response.json();
            const segments = data.segments || [];

            if (segments.length === 0) {
                content.innerHTML = `
                    <div class="no-segments-message">
                        <i class="fas fa-cube"></i>
                        <p>No segments available</p>
                    </div>
                `;
                return;
            }

            // Load persisted selection if not already loaded
            if (!this.selectionLoaded) {
                await this.loadPersistedSelection(controller.signal);
                this.selectionLoaded = true;
            }
            
            // Use persisted selection, or default to Enterprise (ID=1) if no saved selection
            this.segmentsData = segments.map((segment, index) => {
                const idKey = this.getSegmentKey(segment, index);
                const isSelected = this.persistedSegmentIds.size > 0 
                    ? this.persistedSegmentIds.has(idKey) 
                    : (segment.id === 1 || segment.id === '1'); // Default to Enterprise
                return {
                    ...segment,
                    _idKey: idKey,
                    selected: isSelected
                };
            });
            this.totalSegments = this.segmentsData.length;
            
            // Initialize selectedSegmentIds from persisted selection or default to Enterprise
            if (this.persistedSegmentIds.size > 0) {
                this.selectedSegmentIds = new Set(this.persistedSegmentIds);
            } else {
                this.selectedSegmentIds = new Set();
                const enterpriseSegment = this.segmentsData.find(s => s.id === 1 || s.id === '1');
                if (enterpriseSegment) {
                    this.selectedSegmentIds.add(enterpriseSegment._idKey);
                }
            }
            
            this.showValidationError = false;
            this.renderSegmentsSelectionUI();

        } catch (error) {
            const isTimeout = error && error.name === 'AbortError';
            console.error('❌ Failed to load segments:', error);
            const msg = isTimeout
                ? `Request timed out (${timeoutMs / 1000}s). Check the server and network, then retry.`
                : 'Failed to load segments';
            content.innerHTML = `
                <div class="error-message">
                    <i class="fas fa-exclamation-triangle"></i>
                    <p>${this.escapeHtml(msg)}</p>
                    <button class="btn-retry" onclick="window.globalSegmentsCubePanel.loadSegments()">
                        <i class="fas fa-redo"></i> Retry
                    </button>
                </div>
            `;
        } finally {
            clearTimeout(timeoutId);
        }
    }

    /**
     * Render a single segment item
     */
    renderSegmentItem(segment) {
        const segmentName = this.escapeHtml(segment.name || 'Unnamed Segment');
        const segmentDescription = this.escapeHtml(segment.description || 'No description');
        const isEnterprise = segment.id === 1;
        const segmentKey = this.escapeHtml(segment._idKey);
        const checkedAttribute = segment.selected ? 'checked' : '';
        
        return `
            <label class="segment-item ${isEnterprise ? 'enterprise-segment' : ''}" data-segment-id="${segmentKey}">
                <div class="segment-checkbox">
                    <input 
                        type="checkbox" 
                        class="segment-select-checkbox" 
                        data-segment-id="${segmentKey}"
                        ${checkedAttribute}
                        aria-label="Select ${segmentName}"
                    />
                </div>
                <div class="segment-icon">
                    <i class="fas fa-cube"></i>
                </div>
                <div class="segment-info">
                    <div class="segment-name">
                        ${segmentName}
                        ${isEnterprise ? '<span class="enterprise-badge">Default</span>' : ''}
                    </div>
                    <div class="segment-description">${segmentDescription}</div>
                </div>
            </label>
        `;
    }

    /**
     * Render the full selection UI with validation and actions
     */
    renderSegmentsSelectionUI() {
        const content = this.panel.querySelector('.panel-content');
        if (!content) return;

        content.innerHTML = `
            <div class="segments-selection-panel">
                <div class="segments-selection-header">
                    <h4>SELECT SEGMENTS</h4>
                </div>
                <div 
                    class="segments-validation-bar" 
                    id="segmentsValidationMessage" 
                    role="alert" 
                    aria-live="assertive"
                    hidden
                >
                    <i class="fas fa-exclamation-triangle"></i>
                    <span>Select at least one segment</span>
                </div>
                <div class="segments-select-all-row">
                    <label class="segments-checkbox-label" for="segmentsSelectAll">
                        <input type="checkbox" id="segmentsSelectAll" />
                        <span>All</span>
                    </label>
                </div>
                <div class="segments-checkbox-scroll">
                    ${this.segmentsData.map(segment => this.renderSegmentItem(segment)).join('')}
                </div>
                <div class="segments-selection-footer">
                    <div class="segments-selection-summary" id="segmentsSelectionSummary">
                        0 of ${this.totalSegments} segments selected
                    </div>
                    <div class="segments-selection-actions">
                        <button type="button" class="segments-btn segments-btn-secondary" id="segmentsCancelBtn">
                            Cancel
                        </button>
                        <button type="button" class="segments-btn segments-btn-primary" id="segmentsApplyBtn">
                            Apply
                        </button>
                    </div>
                </div>
            </div>
        `;

        this.cacheSelectionElements();
        this.bindSelectionEvents();
        this.updateSelectionSummary();
        this.updateSelectAllState(); // Ensure "All" checkbox reflects current state
    }

    /**
     * Cache frequently used DOM references
     */
    cacheSelectionElements() {
        const content = this.panel.querySelector('.panel-content');
        if (!content) return;

        this.selectAllCheckbox = content.querySelector('#segmentsSelectAll');
        this.validationMessageEl = content.querySelector('#segmentsValidationMessage');
        this.selectionSummaryEl = content.querySelector('#segmentsSelectionSummary');
        this.segmentCheckboxMap = new Map();

        const checkboxNodes = content.querySelectorAll('.segment-select-checkbox');
        checkboxNodes.forEach((checkbox) => {
            this.segmentCheckboxMap.set(checkbox.dataset.segmentId, checkbox);
        });
    }

    /**
     * Wire up events for selection UI
     */
    bindSelectionEvents() {
        if (this.selectAllCheckbox) {
            this.selectAllCheckbox.addEventListener('change', (event) => {
                this.handleSelectAll(event.target.checked);
            });
        }

        this.segmentCheckboxMap.forEach((checkbox, segmentId) => {
            checkbox.addEventListener('change', (event) => {
                this.handleSegmentToggle(segmentId, event.target.checked);
            });
        });

        const applyBtn = this.panel.querySelector('#segmentsApplyBtn');
        if (applyBtn) {
            applyBtn.addEventListener('click', () => this.handleApply());
        }

        const cancelBtn = this.panel.querySelector('#segmentsCancelBtn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => this.handleCancel());
        }
    }

    /**
     * Handle toggling the "All" checkbox
     */
    handleSelectAll(isChecked) {
        this.selectedSegmentIds = new Set();
        this.segmentsData.forEach((segment) => {
            segment.selected = isChecked;
            if (isChecked) {
                this.selectedSegmentIds.add(segment._idKey);
            }
        });

        this.segmentCheckboxMap.forEach((checkbox) => {
            checkbox.checked = isChecked;
        });

        if (this.selectAllCheckbox) {
            this.selectAllCheckbox.indeterminate = false;
        }
        this.updateSelectionSummary();
        if (isChecked) {
            this.toggleValidationMessage(false);
        }
    }

    /**
     * Handle toggling individual segment checkboxes
     */
    handleSegmentToggle(segmentId, isSelected) {
        const targetSegment = this.segmentsData.find(segment => segment._idKey === segmentId);
        if (!targetSegment) {
            return;
        }

        targetSegment.selected = isSelected;
        if (isSelected) {
            this.selectedSegmentIds.add(segmentId);
            this.toggleValidationMessage(false);
        } else {
            this.selectedSegmentIds.delete(segmentId);
        }

        this.updateSelectAllState();
        this.updateSelectionSummary();
    }

    /**
     * Keep "All" checkbox in sync with item checkboxes
     */
    updateSelectAllState() {
        if (!this.selectAllCheckbox) return;

        const selectedCount = this.selectedSegmentIds.size;
        if (selectedCount === this.totalSegments && this.totalSegments > 0) {
            this.selectAllCheckbox.checked = true;
            this.selectAllCheckbox.indeterminate = false;
        } else if (selectedCount === 0) {
            this.selectAllCheckbox.checked = false;
            this.selectAllCheckbox.indeterminate = false;
        } else {
            this.selectAllCheckbox.checked = false;
            this.selectAllCheckbox.indeterminate = true;
        }
    }

    /**
     * Update the "X of Y segments selected" summary
     */
    updateSelectionSummary() {
        if (!this.selectionSummaryEl) return;
        const selectedCount = this.selectedSegmentIds.size;
        this.selectionSummaryEl.textContent = `${selectedCount} of ${this.totalSegments} segments selected`;
    }

    /**
     * Show or hide the validation bar
     */
    toggleValidationMessage(shouldShow) {
        this.showValidationError = shouldShow;
        if (!this.validationMessageEl) return;

        if (shouldShow) {
            this.validationMessageEl.hidden = false;
            this.validationMessageEl.classList.add('visible');
        } else {
            this.validationMessageEl.hidden = true;
            this.validationMessageEl.classList.remove('visible');
        }
    }

    /**
     * Handle Apply button click
     */
    async handleApply() {
        const selectedSegments = this.segmentsData.filter(segment => segment.selected);
        if (selectedSegments.length === 0) {
            this.toggleValidationMessage(true);
            return;
        }

        this.toggleValidationMessage(false);
        
        // Get the selected segment IDs
        const selectedIds = Array.from(this.selectedSegmentIds);
        
        // Save to database
        const saved = await this.saveSelectionToDatabase(selectedIds);
        if (saved) {
            // Update persisted selection in memory
            this.persistedSegmentIds = new Set(this.selectedSegmentIds);
            
            // Update badge to show total number of accessible segments (not selected count)
            if (this.badge && this.totalSegments > 0) {
                this.badge.textContent = this.totalSegments;
                this.badge.style.display = 'flex';
            }
            
            // Dispatch event for any listeners
            window.dispatchEvent(new CustomEvent('segmentsCubeApply', {
                detail: {
                    selectedSegments
                }
            }));
            
            this.closePanel();
            window.location.reload();
        } else {
            // Save failed - show error but don't close
            console.error('❌ Failed to save segment selection');
            alert('Failed to save segment selection. Please try again.');
        }
    }

    /**
     * Handle Cancel button click
     */
    handleCancel() {
        this.toggleValidationMessage(false);
        this.closePanel();
    }

    /**
     * Build a consistent key for each segment
     */
    getSegmentKey(segment, index) {
        if (segment && segment.id !== undefined && segment.id !== null) {
            return String(segment.id);
        }
        if (segment && segment.uuid) {
            return String(segment.uuid);
        }
        return `segment-${index}`;
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Public method to refresh the cube count
     */
    async refresh() {
        await this.updateSegmentCount();
    }
}

// Initialize only when header is ready (to avoid duplicate initialization)
// The header must be ready before we can access header elements
async function initSegmentsCubePanel() {
    if (window.globalSegmentsCubePanel && window.globalSegmentsCubePanel.isInitialized) {
        return;
    }
    
    if (!window.globalSegmentsCubePanel) {
        window.globalSegmentsCubePanel = new SegmentsCubePanel();
    }
    
    await window.globalSegmentsCubePanel.initialize();
}

window.addEventListener('headerReady', () => {
    initSegmentsCubePanel();
});

if (document.readyState === 'complete' || document.readyState === 'interactive') {
    setTimeout(initSegmentsCubePanel, 1000);
}

// Global function to update cube count (callable from anywhere)
window.updateSegmentsCubeCount = async function() {
    if (window.globalSegmentsCubePanel) {
        await window.globalSegmentsCubePanel.refresh();
    }
};

