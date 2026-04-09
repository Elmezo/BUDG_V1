/**
 * Segment Dropdown Component
 * Reusable component for selecting segments on any facet form
 * 
 * Usage:
 * const segmentDropdown = new SegmentDropdown('container-id', {
 *     label: 'Segment',
 *     required: true,
 *     defaultValue: 1, // Enterprise
 *     onChange: (segmentId) => console.log('Segment changed:', segmentId)
 * });
 */

class SegmentDropdown {
    constructor(containerId, options = {}) {
        this.containerId = containerId;
        this.selectedSegmentId = options.defaultValue || 1; // Enterprise by default
        this.onChange = options.onChange || (() => {});
        this.label = options.label || 'Segment';
        this.required = options.required !== undefined ? options.required : true;
        this.disabled = options.disabled || false;
        this.userSegments = [];
        
        this.init();
    }
    
    async init() {
        await this.loadAccessibleSegments();
        this.render();
    }
    
    async loadAccessibleSegments() {
        try {
            const response = await fetch('/api/segments/accessible');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            this.userSegments = data.segments || [];
            
            // Ensure Enterprise is always first
            this.userSegments.sort((a, b) => {
                if (a.id === 1) return -1;
                if (b.id === 1) return 1;
                return (a.name || '').localeCompare(b.name || '');
            });
            
        } catch (error) {
            console.error('Error loading segments:', error);
            // Fallback to Enterprise only
            this.userSegments = [{ 
                id: 1, 
                name: 'Enterprise', 
                description: 'Default enterprise segment' 
            }];
        }
    }
    
    render() {
        const container = document.getElementById(this.containerId);
        if (!container) {
            console.error(`Container ${this.containerId} not found`);
            return;
        }
        
        const html = `
            <div class="segment-dropdown-container form-group">
                <label for="segment-select-${this.containerId}" class="form-label">
                    ${this.label}
                    ${this.required ? '<span class="required-star">*</span>' : ''}
                </label>
                <select 
                    id="segment-select-${this.containerId}" 
                    class="segment-select form-control" 
                    ${this.required ? 'required' : ''}
                    ${this.disabled ? 'disabled' : ''}
                >
                    ${this.userSegments.map(seg => `
                        <option 
                            value="${seg.id}" 
                            ${seg.id === this.selectedSegmentId ? 'selected' : ''}
                            data-description="${this.escapeHtml(seg.description || '')}"
                        >
                            ${this.escapeHtml(seg.name || 'Unnamed Segment')}
                        </option>
                    `).join('')}
                </select>
                <small class="segment-description text-muted" id="segment-description-${this.containerId}">
                    ${this.getSelectedSegmentDescription()}
                </small>
            </div>
        `;
        
        container.innerHTML = html;
        this.attachEventListeners();
    }
    
    attachEventListeners() {
        const select = document.getElementById(`segment-select-${this.containerId}`);
        if (select) {
            select.addEventListener('change', (e) => {
                this.selectedSegmentId = parseInt(e.target.value);
                this.updateDescription();
                this.onChange(this.selectedSegmentId);
            });
        }
    }
    
    getSelectedSegmentDescription() {
        const segment = this.userSegments.find(s => s.id === this.selectedSegmentId);
        return segment ? (segment.description || '') : '';
    }
    
    updateDescription() {
        const descEl = document.getElementById(`segment-description-${this.containerId}`);
        if (descEl) {
            descEl.textContent = this.getSelectedSegmentDescription();
        }
    }
    
    getValue() {
        return this.selectedSegmentId;
    }
    
    setValue(segmentId) {
        this.selectedSegmentId = segmentId;
        const select = document.getElementById(`segment-select-${this.containerId}`);
        if (select) {
            select.value = segmentId;
            this.updateDescription();
        }
    }
    
    enable() {
        const select = document.getElementById(`segment-select-${this.containerId}`);
        if (select) {
            select.disabled = false;
            this.disabled = false;
        }
    }
    
    disable() {
        const select = document.getElementById(`segment-select-${this.containerId}`);
        if (select) {
            select.disabled = true;
            this.disabled = true;
        }
    }
    
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Make globally accessible
window.SegmentDropdown = SegmentDropdown;

