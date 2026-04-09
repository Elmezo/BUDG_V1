// Interactive Stakeholders Table Component
class StakeholdersTable {
    constructor(containerId, entityType, entityId) {
        this.containerId = containerId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.stakeholders = [];
        this.nextRowId = 1;
        this.isEditing = false;
        
        // Sample data for dropdowns
        this.roles = [
            'System Steward',
            'System Business Owner',
            'Data Steward',
            'Technical Owner',
            'Business Analyst',
            'System Administrator'
        ];
        
        this.people = [
            { id: 1, name: 'John Admin', email: 'admin@informatica.com' },
            { id: 2, name: 'Mary Smyth', email: 'marysmyth@informatica.com' },
            { id: 3, name: 'David Johnson', email: 'david.johnson@company.com' },
            { id: 4, name: 'Sarah Wilson', email: 'sarah.wilson@company.com' },
            { id: 5, name: 'Michael Brown', email: 'michael.brown@company.com' },
            { id: 6, name: 'Lisa Anderson', email: 'lisa.anderson@company.com' },
            { id: 7, name: 'Robert Taylor', email: 'robert.taylor@company.com' },
            { id: 8, name: 'Jennifer Martinez', email: 'jennifer.martinez@company.com' },
            { id: 9, name: 'William Garcia', email: 'william.garcia@company.com' },
            { id: 10, name: 'Emily Davis', email: 'emily.davis@company.com' }
        ];
        
        this.statuses = ['Active', 'Transitional', 'Deleted', 'Pending'];
    }

    async loadStakeholders() {
        try {
            let stakeholders = [];
            switch (this.entityType) {
                case 'system':
                    stakeholders = await window.BUDG_API_SERVICE.getSystemStakeholders(this.entityId);
                    break;
                case 'dataset':
                    stakeholders = await window.BUDG_API_SERVICE.getDatasetStakeholders(this.entityId);
                    break;
                case 'interface':
                    stakeholders = await window.BUDG_API_SERVICE.getInterfaceStakeholders(this.entityId);
                    break;
                case 'glossary':
                    stakeholders = await window.BUDG_API_SERVICE.getGlossaryStakeholders(this.entityId);
                    break;
            }
            
            // Map the API response to our internal format
            this.stakeholders = (stakeholders || []).map(stakeholder => ({
                role: stakeholder.role || '',
                name: stakeholder.name || '',
                delegateOf: '', // Not provided by API, will be empty
                roleAccepted: stakeholder.roleAccepted || 'True',
                orgUnit: stakeholder.orgUnit || ''
            }));
            
            this.render();
        } catch (error) {
            console.error('Failed to load stakeholders:', error);
            this.stakeholders = [];
            this.render();
        }
    }

    render() {
        const container = document.getElementById(this.containerId);
        if (!container) return;

        container.innerHTML = `
            <div class="stakeholders-table-container">
                <div class="stakeholders-header">
                    <div class="section-title" style="display:flex;align-items:center;justify-content:space-between;gap:.75rem;">
                        <span>Direct Stakeholders</span>
                        <div class="stakeholders-actions">
                            <button type="button" class="btn btn-secondary" id="editStakeholdersBtn">
                                <i class="fas fa-pen"></i> Edit
                            </button>
                            <div class="edit-actions" id="editActions" style="display:none; gap: 0.5rem;">
                                <button type="button" class="btn btn-primary" id="saveStakeholdersBtn" style="display: inline-flex; align-items: center; gap: 0.5rem;">
                                    <i class="fas fa-save"></i> Save
                                </button>
                                <button type="button" class="btn btn-primary" id="saveAndCloseStakeholdersBtn" style="display: inline-flex; align-items: center; gap: 0.5rem;">
                                    <i class="fas fa-save"></i> Save & Close
                                </button>
                                <button type="button" class="btn btn-secondary" id="closeStakeholdersBtn" style="display: inline-flex; align-items: center; gap: 0.5rem;">
                                    <i class="fas fa-times"></i> Close
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="data-table-wrapper">
                    <table class="data-table stakeholders-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>Role</span></div></th>
                                <th><div class="th-content"><span>Name</span></div></th>
                                <th><div class="th-content"><span>Delegate Of</span></div></th>
                                <th><div class="th-content"><span>Role Status</span></div></th>
                                <th><div class="th-content"><span>Action</span></div></th>
                            </tr>
                        </thead>
                        <tbody id="stakeholdersTableBody">
                            ${this.renderTableBody()}
                        </tbody>
                    </table>
                </div>
            </div>
        `;

        this.attachEventListeners();
    }

    renderTableBody() {
        if (this.stakeholders.length === 0 && !this.isEditing) {
            return `<tr><td colspan="5" style="color:var(--text-muted,#9ca3af);padding:1rem;">No stakeholders</td></tr>`;
        }

        let html = '';
        
        // Render existing stakeholders
        this.stakeholders.forEach((stakeholder, index) => {
            html += this.renderStakeholderRow(stakeholder, index, false);
        });

        // Add new row if editing
        if (this.isEditing) {
            html += this.renderStakeholderRow({}, this.stakeholders.length, true);
        }

        return html;
    }

    renderStakeholderRow(stakeholder, index, isNew = false) {
        const rowClass = isNew ? 'editing-row' : '';
        const role = stakeholder.role || '';
        const name = stakeholder.name || '';
        const delegateOf = stakeholder.delegateOf || '';
        const status = stakeholder.roleAccepted === 'True' ? 'Active' : (stakeholder.roleAccepted === 'False' ? 'Inactive' : 'Active');

        return `
            <tr class="${rowClass}" data-index="${index}">
                <td>
                    ${this.isEditing ? this.renderRoleDropdown(role, index) : role}
                </td>
                <td>
                    ${this.isEditing ? this.renderPersonDropdown(name, index, 'involved') : name}
                </td>
                <td>
                    ${this.isEditing ? this.renderPersonDropdown(delegateOf, index, 'delegate') : delegateOf}
                </td>
                <td>
                    ${this.isEditing ? this.renderStatusDropdown(status, index) : status}
                </td>
                <td>
                    <div class="action-buttons">
                        ${this.isEditing ? `
                            <button type="button" class="action-btn add-row" title="Insert New Row" data-index="${index}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="action-btn delete-row" title="Delete Current Row" data-index="${index}">
                                <i class="fas fa-minus"></i>
                            </button>
                        ` : ''}
                    </div>
                </td>
            </tr>
        `;
    }

    renderRoleDropdown(value, index) {
        const options = this.roles.map(role => 
            `<option value="${role}" ${role === value ? 'selected' : ''}>${role}</option>`
        ).join('');
        
        return `
            <select class="form-select role-select" data-index="${index}" data-field="role">
                <option value="">Select Role</option>
                ${options}
            </select>
        `;
    }

    renderPersonDropdown(value, index, type) {
        const fieldName = type === 'delegate' ? 'delegateOf' : 'involvedParty';
        const placeholder = type === 'delegate' ? 'Select Delegate' : 'Select Person';
        
        return `
            <div class="person-dropdown-container">
                <div class="person-select-wrapper">
                    <input type="text" 
                           class="form-input person-input" 
                           data-index="${index}" 
                           data-field="${fieldName}"
                           data-type="${type}"
                           value="${value}" 
                           placeholder="${placeholder}"
                           autocomplete="off"
                           readonly>
                    <div class="person-dropdown-arrow">
                        <i class="fas fa-chevron-down"></i>
                    </div>
                </div>
                <div class="person-dropdown" style="display:none;"></div>
            </div>
        `;
    }

    renderStatusDropdown(value, index) {
        const options = this.statuses.map(status => 
            `<option value="${status}" ${status === value ? 'selected' : ''}>${status}</option>`
        ).join('');
        
        return `
            <select class="form-select status-select" data-index="${index}" data-field="status">
                ${options}
            </select>
        `;
    }

    attachEventListeners() {
        // Edit button
        const editBtn = document.getElementById('editStakeholdersBtn');
        if (editBtn) {
            editBtn.addEventListener('click', () => this.startEditing());
        }

        // Save button
        const saveBtn = document.getElementById('saveStakeholdersBtn');
        if (saveBtn) {
            saveBtn.addEventListener('click', () => this.saveStakeholders());
        }

        // Save & Close button
        const saveAndCloseBtn = document.getElementById('saveAndCloseStakeholdersBtn');
        if (saveAndCloseBtn) {
            saveAndCloseBtn.addEventListener('click', () => this.saveAndCloseStakeholders());
        }

        // Close button
        const closeBtn = document.getElementById('closeStakeholdersBtn');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.cancelEditing());
        }

        // Add row buttons
        document.querySelectorAll('.add-row').forEach(btn => {
            btn.addEventListener('click', (e) => this.addRow(parseInt(e.target.dataset.index)));
        });

        // Delete row buttons
        document.querySelectorAll('.delete-row').forEach(btn => {
            btn.addEventListener('click', (e) => this.deleteRow(parseInt(e.target.dataset.index)));
        });

        // Person input search
        document.querySelectorAll('.person-input').forEach(input => {
            input.addEventListener('click', (e) => this.togglePersonDropdown(e));
            input.addEventListener('input', (e) => this.handlePersonSearch(e));
            input.addEventListener('focus', (e) => this.showPersonDropdown(e));
            input.addEventListener('blur', (e) => this.hidePersonDropdown(e));
        });

        // Dropdown change events
        document.querySelectorAll('.role-select, .status-select').forEach(select => {
            select.addEventListener('change', (e) => this.handleDropdownChange(e));
        });
    }

    startEditing() {
        this.isEditing = true;
        
        // First render to create the HTML structure
        this.render();
        
        // Then show/hide buttons
        setTimeout(() => {
            const editBtn = document.getElementById('editStakeholdersBtn');
            const editActions = document.getElementById('editActions');
            
            console.log('Starting edit mode...');
            console.log('Edit button element:', editBtn);
            console.log('Edit actions element:', editActions);
            
            if (editBtn) {
                editBtn.style.display = 'none';
                console.log('Edit button hidden');
            } else {
                console.log('Edit button not found!');
            }
            
            if (editActions) {
                editActions.style.display = 'flex';
                console.log('Edit actions shown');
            } else {
                console.log('Edit actions not found!');
            }
        }, 10);
    }

    cancelEditing() {
        this.isEditing = false;
        
        // First render to create the HTML structure
        this.render();
        
        // Then show/hide buttons
        setTimeout(() => {
            const editBtn = document.getElementById('editStakeholdersBtn');
            const editActions = document.getElementById('editActions');
            
            console.log('Canceling edit mode...');
            console.log('Edit button element:', editBtn);
            console.log('Edit actions element:', editActions);
            
            if (editBtn) {
                editBtn.style.display = 'inline-flex';
                console.log('Edit button shown');
            } else {
                console.log('Edit button not found!');
            }
            
            if (editActions) {
                editActions.style.display = 'none';
                console.log('Edit actions hidden');
            } else {
                console.log('Edit actions not found!');
            }
        }, 10);
    }

    addRow(afterIndex) {
        this.stakeholders.splice(afterIndex + 1, 0, {
            role: '',
            name: '',
            delegateOf: '',
            roleAccepted: 'True'
        });
        this.render();
    }

    deleteRow(index) {
        if (this.stakeholders.length > 0) {
            this.stakeholders.splice(index, 1);
            this.render();
        }
    }

    togglePersonDropdown(e) {
        const input = e.target;
        const container = input.closest('.person-dropdown-container');
        const dropdown = container.querySelector('.person-dropdown');
        
        if (dropdown.style.display === 'none' || dropdown.style.display === '') {
            this.showPersonDropdown(e);
        } else {
            this.hidePersonDropdown(e);
        }
    }

    handlePersonSearch(e) {
        const input = e.target;
        const query = input.value.toLowerCase();
        const container = input.closest('.person-dropdown-container');
        const dropdown = container.querySelector('.person-dropdown');
        
        // Remove readonly to allow typing
        input.removeAttribute('readonly');
        
        if (query.length < 1) {
            this.populatePersonDropdown(dropdown, this.people);
            return;
        }

        const filtered = this.people.filter(person => 
            person.name.toLowerCase().includes(query) || 
            person.email.toLowerCase().includes(query)
        );

        this.populatePersonDropdown(dropdown, filtered);
    }

    populatePersonDropdown(dropdown, people) {
        if (people.length > 0) {
            dropdown.innerHTML = people.map(person => 
                `<div class="person-option" data-value="${person.name} (${person.email})">
                    <strong>${person.name}</strong><br>
                    <small>${person.email}</small>
                </div>`
            ).join('');
            
            dropdown.style.display = 'block';
            
            // Add click listeners to options
            dropdown.querySelectorAll('.person-option').forEach(option => {
                option.addEventListener('click', (e) => {
                    const input = dropdown.previousElementSibling.querySelector('.person-input');
                    input.value = option.dataset.value;
                    input.setAttribute('readonly', 'readonly');
                    dropdown.style.display = 'none';
                    container.classList.remove('open');
                });
            });
        } else {
            dropdown.style.display = 'none';
        }
    }

    showPersonDropdown(e) {
        const input = e.target;
        const container = input.closest('.person-dropdown-container');
        const dropdown = container.querySelector('.person-dropdown');
        
        // Populate with all people initially
        this.populatePersonDropdown(dropdown, this.people);
        container.classList.add('open');
    }

    hidePersonDropdown(e) {
        // Delay to allow option clicks
        setTimeout(() => {
            const input = e.target;
            const container = input.closest('.person-dropdown-container');
            const dropdown = container.querySelector('.person-dropdown');
            
            dropdown.style.display = 'none';
            container.classList.remove('open');
            
            // Make input readonly again if it has a value
            if (input.value) {
                input.setAttribute('readonly', 'readonly');
            }
        }, 200);
    }

    handleDropdownChange(e) {
        const select = e.target;
        const index = parseInt(select.dataset.index);
        const field = select.dataset.field;
        
        if (this.stakeholders[index]) {
            this.stakeholders[index][field] = select.value;
        }
    }

    async saveStakeholders() {
        // Here you would typically send the data to the backend
        console.log('Saving stakeholders:', this.stakeholders);
        
        // For now, just exit editing mode
        this.isEditing = false;
        
        // Hide edit actions and show edit button
        const editBtn = document.getElementById('editStakeholdersBtn');
        const editActions = document.getElementById('editActions');
        
        if (editBtn) editBtn.style.display = 'inline-flex';
        if (editActions) editActions.style.display = 'none';
        
        this.render();
        
        // Show success message
        this.showMessage('Stakeholders saved successfully!', 'success');
    }

    async saveAndCloseStakeholders() {
        // Save first
        await this.saveStakeholders();
        
        // Then close (go back to summary tab)
        // This would typically close the stakeholders tab and go back to summary
        console.log('Save & Close - would close stakeholders tab');
    }

    showMessage(message, type = 'info') {
        // Simple message display - you can enhance this
        const container = document.getElementById(this.containerId);
        const messageDiv = document.createElement('div');
        messageDiv.className = `message message-${type}`;
        messageDiv.textContent = message;
        messageDiv.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 10px 20px;
            background: ${type === 'success' ? '#248567' : '#248567'};
            color: white;
            border-radius: 4px;
            z-index: 1000;
        `;
        
        document.body.appendChild(messageDiv);
        
        setTimeout(() => {
            document.body.removeChild(messageDiv);
        }, 3000);
    }
}

// Export for use in other files
window.StakeholdersTable = StakeholdersTable;
