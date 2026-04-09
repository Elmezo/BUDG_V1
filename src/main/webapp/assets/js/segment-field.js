/**
 * Segment Field Component
 * Reusable component for adding segment selection to any facet form
 * 
 * Usage:
 * 1. Add container in HTML: <div id="segmentFieldContainer"></div>
 * 2. Initialize: const segmentField = await SegmentField.init('segmentFieldContainer');
 * 3. Get value: const segmentId = segmentField.getValue();
 * 4. Set value: segmentField.setValue(segmentId);
 * 5. Validate: const isValid = segmentField.validate();
 * 
 * SEGMENTATION RULES (BUDG v7.0 - v7.2):
 * 1. HIERARCHY RULE: Parent-child relationships must be within the SAME segment
 * 2. CROSS-SEGMENT: Enterprise ↔ Private = OK, Private ↔ Private (different) = NOT OK
 * 3. VISIBILITY: Dataset cannot be public if System is private
 */

class SegmentField {
    static INVALID_PARENT_SEGMENT_MESSAGE = 'This parent is not valid for the selected segment. Please remove the parent first.';
    static INVALID_RELATIONSHIP_SEGMENT_MESSAGE = 'Segment cannot be changed because this object has relationships with objects in another private segment.';

    static resolveContextSegmentId() {
        try {
            const fromUrl = parseInt(new URLSearchParams(window.location.search).get('segmentId'), 10);
            if (Number.isInteger(fromUrl) && fromUrl > 0) {
                return fromUrl;
            }
        } catch (_) { /* ignore */ }

        try {
            const cube = window.globalSegmentsCubePanel;
            if (cube && cube.selectedSegmentIds && typeof cube.selectedSegmentIds.size === 'number' && cube.selectedSegmentIds.size === 1) {
                const [single] = Array.from(cube.selectedSegmentIds);
                const parsed = parseInt(single, 10);
                if (Number.isInteger(parsed) && parsed > 0) {
                    return parsed;
                }
            }
        } catch (_) { /* ignore */ }

        return null;
    }

    constructor(containerId, options = {}) {
        this.containerId = containerId;
        this.container = document.getElementById(containerId);
        this.options = {
            label: options.label || 'Segment',
            required: options.required !== false, // Default true
            defaultValue: options.defaultValue !== undefined ? options.defaultValue : null, // No default unless explicitly set
            showSectionHeader: options.showSectionHeader !== false, // Default true
            sectionTitle: options.sectionTitle || 'OTHER INFORMATION',
            onChange: options.onChange || null,
            fieldId: options.fieldId || 'dsSegment',
            errorId: options.errorId || 'dsSegmentError',
            // Parent-child hierarchy validation
            objectType: options.objectType || null, // e.g., 'Glossary', 'Policy', 'System'
            getObjectId: options.getObjectId || null, // Function to get current object ID on edit pages
            getParentId: options.getParentId || null, // Function to get parent ID
            onHierarchyConflict: options.onHierarchyConflict || null // Callback for hierarchy conflicts
        };
        this.segments = [];
        const contextSegmentId = SegmentField.resolveContextSegmentId();
        this.selectedValue = (Number.isInteger(contextSegmentId) && contextSegmentId > 0)
            ? contextSegmentId
            : this.options.defaultValue;
        this.parentSegmentId = null;
        this.parentSegmentName = null;
    }

    /**
     * Static factory method for easy initialization
     */
    static async init(containerId, options = {}) {
        const field = new SegmentField(containerId, options);
        await field.initialize();
        return field;
    }

    /**
     * Initialize the component
     */
    async initialize() {
        if (!this.container) {
            console.error(`SegmentField: Container '${this.containerId}' not found`);
            return;
        }

        await this.loadSegments();
        this.render();
        this.attachEventListeners();

        this.container._segmentFieldInstance = this;
        if (typeof window !== 'undefined') {
            window.segmentField = this;
        }
        
        if (window.__BUDG_DEBUG__) console.log('✅ SegmentField initialized with', this.segments.length, 'segments');
    }

    /**
     * Load user accessible segments from API
     */
    async loadSegments() {
        try {
            const response = await fetch('/api/segments/accessible', {
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            this.segments = data.segments || [];

            // Sort: Enterprise first, then alphabetically
            this.segments.sort((a, b) => {
                if (a.id === 1) return -1;
                if (b.id === 1) return 1;
                return (a.name || '').localeCompare(b.name || '');
            });

        } catch (error) {
            console.error('SegmentField: Failed to load segments:', error);
            // Fallback to Enterprise only
            this.segments = [{ 
                id: 1, 
                name: 'Enterprise', 
                description: 'Default enterprise segment' 
            }];
        }
    }

    /**
     * Render the segment field HTML
     */
    render() {
        if (window.__BUDG_DEBUG__) console.log('🔧 SegmentField.render() called with selectedValue:', this.selectedValue);
        
        const sectionHeader = this.options.showSectionHeader ? `
            <div class="form-section-header" style="margin-bottom: 0.75rem; border-top: 1px solid var(--border-color, #e5e7eb);">
                <span class="section-label" style="font-size: 0.75rem; font-weight: 600; color: #248567; text-transform: uppercase; letter-spacing: 0.5px;">${this.escapeHtml(this.options.sectionTitle)}</span>
            </div>
        ` : '';

        const optionsHtml = this.segments.map(seg => {
            const isSelected = this.selectedValue !== null && seg.id === this.selectedValue;
            console.log(`🔧   Option: ${seg.name} (ID: ${seg.id}) - Selected: ${isSelected}`);
            return `
            <option value="${seg.id}" ${isSelected ? 'selected' : ''}>
                ${this.escapeHtml(seg.name || 'Unnamed Segment')}
            </option>`;
        }).join('');

        this.container.innerHTML = `
            ${sectionHeader}
            <div class="form-group">
                <label class="form-label" for="${this.options.fieldId}">
                    ${this.escapeHtml(this.options.label)}
                    ${this.options.required ? '<span class="required">*</span>' : ''}
                </label>
                <select class="form-select" id="${this.options.fieldId}">
                    ${optionsHtml}
                </select>
                <span class="field-error" id="${this.options.errorId}" style="display:none"></span>
            </div>
        `;
        
        // After rendering, log what the browser actually selected
        setTimeout(() => {
            if (!window.__BUDG_DEBUG__) return;
            const select = document.getElementById(this.options.fieldId);
            if (select) console.log('🔧 After render, browser selected value:', select.value);
        }, 0);
    }

    /**
     * Attach event listeners
     */
    attachEventListeners() {
        const select = document.getElementById(this.options.fieldId);
        if (select) {
            select.addEventListener('change', async (e) => {
                const previousValue = this.selectedValue;
                const nextValue = parseInt(e.target.value, 10);

                const parentId = this.resolveParentId();
                const isParentValid = await this.validateParentForSegment(parentId, nextValue);
                if (!isParentValid) {
                    this.setValue(previousValue);
                    this.showError(SegmentField.INVALID_PARENT_SEGMENT_MESSAGE);
                    alert(SegmentField.INVALID_PARENT_SEGMENT_MESSAGE);
                    return;
                }

                const relationshipValidation = await this.validateSegmentChangeForRelationships(nextValue);
                if (!relationshipValidation.isValid) {
                    const message = relationshipValidation.message || SegmentField.INVALID_RELATIONSHIP_SEGMENT_MESSAGE;
                    this.setValue(previousValue);
                    this.showError(message);
                    alert(message);
                    return;
                }

                this.selectedValue = nextValue;
                this.hideError();

                if (this.options.onChange) {
                    const result = await this.options.onChange(this.selectedValue, previousValue);
                    if (result === false) {
                        this.setValue(previousValue);
                        return;
                    }
                }
            });
        }
    }

    resolveParentId() {
        if (typeof this.options.getParentId === 'function') {
            const explicitParentId = parseInt(this.options.getParentId(), 10);
            if (Number.isInteger(explicitParentId) && explicitParentId > 0) {
                return explicitParentId;
            }
        }

        const parentIdSelectors = [
            '#parentId',
            '#parentid',
            '#parent_id',
            '#parentClientSelect',
            '#parentLegalEntitySelect',
            '#parentCommitteeSelect',
            'input[data-parent-id]'
        ];

        for (const selector of parentIdSelectors) {
            const element = document.querySelector(selector);
            if (!element) continue;

            const directValue = parseInt(element.value, 10);
            if (Number.isInteger(directValue) && directValue > 0) {
                return directValue;
            }

            const datasetParentId = parseInt(element.dataset?.parentId, 10);
            if (Number.isInteger(datasetParentId) && datasetParentId > 0) {
                return datasetParentId;
            }
        }

        return null;
    }

    async validateParentForSegment(parentId, segmentId) {
        if (!Number.isInteger(parentId) || parentId <= 0) {
            return true;
        }
        if (!Number.isInteger(segmentId) || segmentId <= 0) {
            return true;
        }
        if (!this.options.objectType) {
            return true;
        }

        try {
            const response = await fetch('/api/segments/validate-hierarchy', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    parentId: parentId,
                    childSegmentId: segmentId,
                    objectType: this.options.objectType
                })
            });

            if (!response.ok) {
                return true;
            }

            const result = await response.json();
            return !!result?.isValid;
        } catch (error) {
            console.error('SegmentField: parent/segment validation failed:', error);
            return true;
        }
    }

    resolveObjectId() {
        if (typeof this.options.getObjectId === 'function') {
            const explicitObjectId = parseInt(this.options.getObjectId(), 10);
            if (Number.isInteger(explicitObjectId) && explicitObjectId > 0) {
                return explicitObjectId;
            }
        }

        try {
            const fromQuery = parseInt(new URLSearchParams(window.location.search).get('id'), 10);
            if (Number.isInteger(fromQuery) && fromQuery > 0) {
                return fromQuery;
            }
        } catch (_) { /* ignore */ }

        try {
            const path = window.location.pathname || '';
            const matches = path.match(/\/(\d+)(?:\/)?$/);
            if (matches && matches[1]) {
                const parsed = parseInt(matches[1], 10);
                if (Number.isInteger(parsed) && parsed > 0) {
                    return parsed;
                }
            }
        } catch (_) { /* ignore */ }

        return null;
    }

    async validateSegmentChangeForRelationships(segmentId) {
        if (!Number.isInteger(segmentId) || segmentId <= 0) {
            return { isValid: true, message: '' };
        }
        if (!this.options.objectType) {
            return { isValid: true, message: '' };
        }

        const objectId = this.resolveObjectId();
        if (!Number.isInteger(objectId) || objectId <= 0) {
            // Create page (or unknown context): defer to save-time backend validation.
            return { isValid: true, message: '' };
        }

        try {
            const response = await fetch('/api/segments/validate-segment-change', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    objectId: objectId,
                    objectType: this.options.objectType,
                    newSegmentId: segmentId
                })
            });

            const result = await response.json();
            if (!response.ok || !result?.isValid) {
                return {
                    isValid: false,
                    message: result?.message || SegmentField.INVALID_RELATIONSHIP_SEGMENT_MESSAGE
                };
            }

            return { isValid: true, message: '' };
        } catch (error) {
            console.error('SegmentField: relationship/segment validation failed:', error);
            return { isValid: true, message: '' };
        }
    }

    /**
     * Get the selected segment ID
     */
    getValue() {
        const select = document.getElementById(this.options.fieldId);
        if (select) {
            return parseInt(select.value, 10);
        }
        return this.selectedValue;
    }

    /**
     * Check if segments/options have been loaded
     */
    hasOptions() {
        return this.segments && this.segments.length > 0;
    }

    /**
     * Set the segment value
     */
    setValue(segmentId) {
        const numericId = typeof segmentId === 'string' ? parseInt(segmentId, 10) : segmentId;
        this.selectedValue = numericId;
        const select = document.getElementById(this.options.fieldId);
        if (window.__BUDG_DEBUG__) console.log('🔧 SegmentField.setValue called with:', segmentId, 'numericId:', numericId, 'select found:', !!select);
        if (select) {
            select.value = String(numericId);
            if (select.value !== String(numericId)) {
                const options = Array.from(select.options);
                const targetOption = options.find(o => parseInt(o.value, 10) === numericId);
                if (targetOption) targetOption.selected = true;
            }
        }
    }

    /**
     * Validate the field including hierarchy constraints
     */
    validate() {
        if (!this.options.required) {
            return true;
        }

        const value = this.getValue();
        if (!Number.isInteger(value) || value < 1) {
            this.showError('Segment is required');
            return false;
        }

        this.hideError();
        return true;
    }

    /**
     * Validate parent-child segment hierarchy
     * Per BUDG v7.0-7.2: Parent-child relationships must be within the SAME segment
     * 
     * @param {number} parentId - The parent object's ID (null if no parent)
     * @param {string} objectType - The object type (e.g., 'Glossary', 'Policy')
     * @returns {Promise<{isValid: boolean, message: string, canProceed: boolean, parentSegmentId: number, parentSegmentName: string}>}
     */
    async validateHierarchy(parentId, objectType) {
        if (!parentId) {
            return { isValid: true, message: '', canProceed: true };
        }

        const childSegmentId = this.getValue();
        
        try {
            const response = await fetch(`/api/segments/validate-hierarchy`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    parentId: parentId,
                    childSegmentId: childSegmentId,
                    objectType: objectType
                })
            });

            if (!response.ok) {
                const error = await response.json();
                return {
                    isValid: false,
                    message: error.message || 'Validation failed',
                    canProceed: false
                };
            }

            const result = await response.json();
            
            // Store parent segment info for reference
            if (result.parentSegmentId) {
                this.parentSegmentId = result.parentSegmentId;
                this.parentSegmentName = result.parentSegmentName;
            }
            
            return result;
        } catch (error) {
            console.error('Segment hierarchy validation error:', error);
            return {
                isValid: true, // Allow to proceed on network errors
                message: 'Unable to validate segment hierarchy',
                canProceed: true
            };
        }
    }

    /**
     * Validate cross-segment relationship
     * Per BUDG v7.0-7.2: Enterprise↔Private = OK, Private↔Private (different) = NOT OK
     * 
     * @param {number} sourceSegmentId - Source object's segment ID
     * @param {number} targetSegmentId - Target object's segment ID
     * @returns {Promise<{isValid: boolean, message: string}>}
     */
    async validateCrossSegmentRelationship(sourceSegmentId, targetSegmentId) {
        // Same segment - always valid
        if (sourceSegmentId === targetSegmentId) {
            return { isValid: true, message: '' };
        }
        
        // Enterprise (ID=1) ↔ Anything - always valid
        if (sourceSegmentId === 1 || targetSegmentId === 1) {
            return { isValid: true, message: '' };
        }
        
        // Different private segments - NOT allowed
        try {
            const response = await fetch(`/api/segments/validate-relationship`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    sourceSegmentId: sourceSegmentId,
                    targetSegmentId: targetSegmentId
                })
            });

            if (!response.ok) {
                const error = await response.json();
                return {
                    isValid: false,
                    message: error.message || 'Cross-segment relationship not allowed'
                };
            }

            return await response.json();
        } catch (error) {
            console.error('Cross-segment validation error:', error);
            return {
                isValid: true, // Allow to proceed on network errors
                message: 'Unable to validate cross-segment relationship'
            };
        }
    }

    /**
     * Show hierarchy conflict dialog
     * When a child is in a different segment than its parent
     * 
     * @param {Object} validationResult - The validation result with parent segment info
     * @returns {Promise<boolean>} - true if user wants to proceed (move parent), false to cancel
     */
    showHierarchyConflictDialog(validationResult) {
        return new Promise((resolve) => {
            const message = 
                `⚠️ Segment Hierarchy Conflict\n\n` +
                `The parent is in segment "${validationResult.parentSegmentName}" ` +
                `but this object is in segment "${validationResult.childSegmentName}".\n\n` +
                `📋 Per BUDG Segmentation Rules (v7.0-7.2):\n` +
                `Parent-child relationships must be within the SAME segment.\n\n` +
                `Click OK to move the PARENT to segment "${validationResult.childSegmentName}".\n` +
                `Click Cancel to change this object's segment instead.`;
            
            const userConfirmed = confirm(message);
            
            if (userConfirmed) {
                // User wants to proceed - parent will be moved
                console.log('✅ User confirmed: Moving parent to child segment');
                resolve(true);
            } else {
                // User cancelled - they should change the segment
                console.log('❌ User cancelled: Will not move parent');
                resolve(false);
            }
        });
    }

    /**
     * Check and handle hierarchy validation with user interaction
     * 
     * @param {number} parentId - Parent object ID
     * @param {string} objectType - Object type
     * @returns {Promise<{proceed: boolean, moveParent: boolean}>}
     */
    async checkAndHandleHierarchy(parentId, objectType) {
        if (!parentId) {
            return { proceed: true, moveParent: false };
        }

        const result = await this.validateHierarchy(parentId, objectType);
        
        if (result.isValid) {
            return { proceed: true, moveParent: false };
        }

        if (result.canProceed) {
            // Show dialog to user
            const userWantsToMoveParent = await this.showHierarchyConflictDialog(result);
            return {
                proceed: userWantsToMoveParent,
                moveParent: userWantsToMoveParent,
                parentId: parentId,
                targetSegmentId: this.getValue()
            };
        }

        // Cannot proceed at all
        this.showError(result.message);
        return { proceed: false, moveParent: false };
    }

    /**
     * Get parent segment info
     */
    getParentSegmentInfo() {
        return {
            id: this.parentSegmentId,
            name: this.parentSegmentName
        };
    }

    /**
     * Show error message
     */
    showError(message) {
        const errorEl = document.getElementById(this.options.errorId);
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = 'block';
        }
    }

    /**
     * Hide error message
     */
    hideError() {
        const errorEl = document.getElementById(this.options.errorId);
        if (errorEl) {
            errorEl.style.display = 'none';
        }
    }

    /**
     * Get the selected segment object
     */
    getSelectedSegment() {
        const value = this.getValue();
        return this.segments.find(s => s.id === value) || null;
    }

    /**
     * Refresh segments from API
     */
    async refresh() {
        const currentValue = this.getValue();
        await this.loadSegments();
        this.render();
        this.attachEventListeners();
        
        // Try to restore previous selection
        if (this.segments.some(s => s.id === currentValue)) {
            this.setValue(currentValue);
        }
    }

    /**
     * Enable the field
     */
    enable() {
        const select = document.getElementById(this.options.fieldId);
        if (select) {
            select.disabled = false;
        }
    }

    /**
     * Disable the field
     */
    disable() {
        const select = document.getElementById(this.options.fieldId);
        if (select) {
            select.disabled = true;
        }
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
}

// Make globally accessible
window.SegmentField = SegmentField;

