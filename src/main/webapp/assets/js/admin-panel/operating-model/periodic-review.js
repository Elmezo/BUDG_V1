var PR_PREFIX = 'adminPanel.operatingModel.periodicReview';
function prT(key, fallback) {
    if (typeof adminT === 'function') return adminT(PR_PREFIX + '.' + key, fallback);
    return fallback;
}
function prEscape(s) {
    if (s == null) return '';
    var d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}
function prTpl(str, vars) {
    if (!str || !vars) return str;
    var out = str;
    Object.keys(vars).forEach(function (k) {
        out = out.split('{{' + k + '}}').join(String(vars[k]));
    });
    return out;
}

function showPeriodicReviewContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.periodicReview');
    var h = prEscape;
    var t = prT;

    contentArea.innerHTML = `
        <div class="periodic-review-container" style="padding: 20px;">
            <div class="card">
                <div class="card-header" style="background-color: #248567; padding: 15px; border-bottom: 1px solid #ddd;">
                    <h5 style="margin: 0; color: #333; font-weight: bold;">${h(t('pageHeading', 'PERIODIC REVIEW OF OBJECTS'))}</h5>
                </div>
                <div class="card-body" style="padding: 30px;">
                    <div class="form-group" style="margin-bottom: 60px; display: grid; grid-template-columns: 200px 1fr; align-items: center; gap: 20px;">
                        <label style="margin: 0; color: #333; font-weight: 500;">
                            ${h(t('facetLabel', 'Facet'))}<span style="color: red;">*</span>
                        </label>
                        <select id="facetSelect" class="form-control" style="padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                            <option value="">${h(t('loading', 'Loading...'))}</option>
                        </select>
                    </div>

                    <div class="form-group" style="display: grid; grid-template-columns: 200px 1fr; align-items: center; gap: 20px;">
                        <label style="margin: 0; color: #333; font-weight: 500;">
                            ${h(t('enablePeriodicReview', 'Enable Periodic Review'))}<span style="color: red;">*</span>
                        </label>
                        <label class="switch">
                            <input type="checkbox" id="enableToggle">
                            <span class="slider round"></span>
                        </label>
                    </div>
                </div>
            </div>

            <!-- Configurations Card - Hidden by default -->
            <div class="card" id="configurationsCard" style="margin-top: 20px; display: none;">
                <div class="card-header" style="background-color: #248567; padding: 15px; border-bottom: 1px solid #ddd; display: flex; justify-content: space-between; align-items: center;">
                    <h5 style="margin: 0; color: #333; font-weight: bold;">${h(t('configCardTitle', 'PERIODIC REVIEW CONFIGURATIONS FOR'))} <span id="selectedFacetName">DATA SET</span></h5>
                    <div style="display: flex; gap: 10px; align-items: center;">
                        <button class="btn btn-sm" id="addConfigBtn" style="background-color: #fff; border: 1px solid #ddd; padding: 5px 15px; display: flex; align-items: center; gap: 5px;">
                            <i class="fas fa-plus"></i> Add
                        </button>
                        <button class="btn btn-sm" id="deleteConfigBtn" style="background-color: #fff; border: 1px solid #ddd; padding: 5px 15px; color: #999; display: flex; align-items: center; gap: 5px;">
                            <i class="fas fa-trash"></i> Delete
                        </button>
                        <div class="dropdown" style="position: relative;">
                            <button class="btn btn-sm" id="settingsDropdown" style="background-color: #fff; border: 1px solid #ddd; padding: 5px 10px; display: flex; align-items: center; gap: 5px;">
                                <i class="fas fa-cog"></i> <i class="fas fa-chevron-down"></i>
                            </button>
                            <div class="dropdown-menu" id="settingsMenu" style="display: none; position: absolute; right: 0; top: 100%; background: white; border: 1px solid #ddd; box-shadow: 0 2px 10px rgba(0,0,0,0.1); min-width: 200px; z-index: 1000; margin-top: 5px;">
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-filter"></i> ${h(t('showFilters', 'Show Filters'))}
                                </a>
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-columns"></i> ${h(t('columns', 'Columns'))} <i class="fas fa-chevron-right" style="margin-left: auto;"></i>
                                </a>
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-layer-group"></i> ${h(t('groupBy', 'Group By'))} <i class="fas fa-chevron-right" style="margin-left: auto;"></i>
                                </a>
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-file-export"></i> ${h(t('export', 'Export'))} <i class="fas fa-chevron-right" style="margin-left: auto;"></i>
                                </a>
                                <div style="border-top: 1px solid #ddd; margin: 5px 0;"></div>
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-save"></i> ${h(t('saveLayout', 'Save Layout'))} <i class="fas fa-question-circle" style="margin-left: auto;"></i>
                                </a>
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-undo"></i> ${h(t('resetLayout', 'Reset Layout'))} <i class="fas fa-question-circle" style="margin-left: auto;"></i>
                                </a>
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-sync"></i> ${h(t('reloadGrid', 'Reload Grid'))}
                                </a>
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-arrows-alt-v"></i> ${h(t('increaseHeight', 'Increase Height'))}
                                </a>
                                <a class="dropdown-item" href="#" style="padding: 10px 15px; display: flex; align-items: center; gap: 10px; text-decoration: none; color: #333;">
                                    <i class="fas fa-clipboard"></i> ${h(t('copyGridClipboard', 'Copy grid to clipboard'))}
                                </a>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="card-body" style="padding: 0;">
                    <table class="table table-bordered" style="margin: 0; width: 100%; border-collapse: collapse;">
                        <thead style="background-color: #f5f5f5;">
                            <tr>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${h(t('colName', 'Name'))}</th>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${h(t('colDescription', 'Description'))}</th>
                                <th style="padding: 12px 15px; text-align: left; font-weight: 600; color: #333; border: 1px solid #e0e0e0;">${h(t('colLastUpdated', 'Last Updated'))}</th>
                            </tr>
                        </thead>
                        <tbody id="configurationsTableBody">
                            <tr>
                                <td colspan="3" style="padding: 40px 20px; text-align: center; color: #999; border: 1px solid #e0e0e0;">${h(t('noConfigurationsFound', 'No configurations found'))}</td>
                            </tr>
                        </tbody>
                    </table>
                    <div style="padding: 12px 15px; text-align: right; color: #666; font-size: 14px; border-top: 1px solid #e0e0e0; background-color: #fafafa;">
                        <span id="recordCount">${h(prTpl(t('recordsCount', '{{count}} records'), { count: 0 }))}</span>
                    </div>
                </div>
            </div>
        </div>

        <style>
            .switch {
                position: relative;
                display: inline-block;
                width: 50px;
                height: 24px;
            }
            .switch input {
                opacity: 0;
                width: 0;
                height: 0;
            }
            .slider {
                position: absolute;
                cursor: pointer;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background-color: #ccc;
                -webkit-transition: .4s;
                transition: .4s;
            }
            .slider:before {
                position: absolute;
                content: "";
                height: 16px;
                width: 16px;
                left: 4px;
                bottom: 4px;
                background-color: white;
                -webkit-transition: .4s;
                transition: .4s;
            }
            input:checked + .slider {
                background-color: #4CAF50;
            }
            input:focus + .slider {
                box-shadow: 0 0 1px #4CAF50;
            }
            input:checked + .slider:before {
                -webkit-transform: translateX(26px);
                -ms-transform: translateX(26px);
                transform: translateX(26px);
            }
            .slider.round {
                border-radius: 34px;
            }
            .slider.round:before {
                border-radius: 50%;
            }

            .table tbody tr:hover {
                background-color: #f5f5f5;
            }

            .table tbody tr.selected {
                background-color: #248567;
            }

            .table tbody tr.selected td {
                color: white !important;
            }

            .table tbody tr.selected td a {
                color: white !important;
            }

            .table tbody td {
                padding: 12px 15px;
                border: 1px solid #e0e0e0;
                color: #333;
                cursor: pointer;
            }

            .table tbody tr td:first-child a {
                color: #1976d2;
                text-decoration: none;
            }

            .table tbody tr td:first-child a:hover {
                text-decoration: underline;
            }

            .dropdown-item:hover {
                background-color: #f5f5f5;
            }
        </style>
    `;

    // Store modules data
    let modulesData = [];

    // Fetch Modules and Status
    fetch('/api/periodic-review')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const facetSelect = document.getElementById('facetSelect');
                const enableToggle = document.getElementById('enableToggle');

                facetSelect.innerHTML = ''; // Clear loading message

                if (data.data && data.data.length > 0) {
                    modulesData = data.data;

                    // Populate dropdown with modules
                    data.data.forEach(module => {
                        const option = document.createElement('option');
                        option.value = module.id;
                        option.textContent = module.name;
                        option.dataset.isEnabled = module.isEnabled;
                        facetSelect.appendChild(option);
                    });

                    // Select first module by default
                    if (modulesData.length > 0) {
                        facetSelect.value = modulesData[0].id;
                        enableToggle.checked = modulesData[0].isEnabled;
                        updateConfigurationsCard(modulesData[0].isEnabled, modulesData[0].name);
                    }

                    // Handle selection change
                    facetSelect.addEventListener('change', function () {
                        const selectedModule = modulesData.find(m => m.id == this.value);
                        if (selectedModule) {
                            enableToggle.checked = selectedModule.isEnabled;
                            updateConfigurationsCard(selectedModule.isEnabled, selectedModule.name);
                        }
                    });

                    // Handle toggle change
                    enableToggle.addEventListener('change', function () {
                        const selectedModuleId = facetSelect.value;
                        if (selectedModuleId) {
                            updatePeriodicReviewStatus(selectedModuleId, this.checked, this);
                            const selectedModule = modulesData.find(m => m.id == selectedModuleId);
                            if (selectedModule) {
                                updateConfigurationsCard(this.checked, selectedModule.name);
                            }
                        }
                    });

                    // Settings dropdown toggle
                    const settingsDropdown = document.getElementById('settingsDropdown');
                    const settingsMenu = document.getElementById('settingsMenu');

                    if (settingsDropdown && settingsMenu) {
                        settingsDropdown.addEventListener('click', function (e) {
                            e.stopPropagation();
                            settingsMenu.style.display = settingsMenu.style.display === 'none' ? 'block' : 'none';
                        });

                        // Close dropdown when clicking outside
                        document.addEventListener('click', function () {
                            settingsMenu.style.display = 'none';
                        });

                        // Prevent dropdown from closing when clicking inside
                        settingsMenu.addEventListener('click', function (e) {
                            e.stopPropagation();
                        });
                    }
                } else {
                    facetSelect.innerHTML = '<option value="">' + prEscape(prT('noModulesFound', 'No modules found')) + '</option>';
                }
            } else {
                console.error('Failed to load data');
                document.getElementById('facetSelect').innerHTML = '<option value="">' + prEscape(prT('failedLoadModules', 'Failed to load modules')) + '</option>';
            }
        })
        .catch(error => {
            console.error('Error fetching modules:', error);
            document.getElementById('facetSelect').innerHTML = '<option value="">Error loading modules</option>';
        });

    function updatePeriodicReviewStatus(moduleId, isEnabled, checkbox) {
        fetch('/api/periodic-review', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                moduleId: parseInt(moduleId),
                isEnabled: isEnabled
            })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    console.log(`Module ${moduleId} updated to ${isEnabled}`);
                    // Update local data
                    const module = modulesData.find(m => m.id == moduleId);
                    if (module) {
                        module.isEnabled = isEnabled;
                    }
                } else {
                    showNotification(prT('alertFailedUpdateStatus', 'Failed to update status'), 'error');
                    checkbox.checked = !isEnabled; // Revert
                }
            })
            .catch(error => {
                console.error('Error updating status:', error);
                showNotification(prT('alertErrorUpdateStatus', 'Error updating status'), 'error');
                checkbox.checked = !isEnabled; // Revert
            });
    }

    function updateConfigurationsCard(isEnabled, facetName) {
        const configurationsCard = document.getElementById('configurationsCard');
        const selectedFacetName = document.getElementById('selectedFacetName');

        if (configurationsCard) {
            configurationsCard.style.display = isEnabled ? 'block' : 'none';
        }

        if (selectedFacetName && facetName) {
            selectedFacetName.textContent = facetName.toUpperCase();
        }

        // If enabled, fetch configurations
        if (isEnabled) {
            const moduleId = document.getElementById('facetSelect').value;
            if (moduleId) {
                fetchConfigurations(moduleId);
            }
        }
    }

    function fetchConfigurations(moduleId) {
        const tableBody = document.getElementById('configurationsTableBody');
        const recordCount = document.getElementById('recordCount');

        // Show loading state
        tableBody.innerHTML = '<tr><td colspan="3" style="padding: 40px 20px; text-align: center; color: #999; border: 1px solid #e0e0e0;">' + prEscape(prT('loading', 'Loading...')) + '</td></tr>';

        fetch(`/api/periodic-review-config?moduleId=${moduleId}`)
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    if (data.data && data.data.length > 0) {
                        tableBody.innerHTML = '';
                        data.data.forEach(config => {
                            const row = document.createElement('tr');
                            row.dataset.configId = config.id;
                            var cn = prEscape(config.name || '-');
                            var cd = prEscape(config.description || '-');
                            var cdt = prEscape(formatDate(config.lastUpdated));
                            row.innerHTML = `
                                <td style="padding: 12px 15px; border: 1px solid #e0e0e0;"><a href="#" class="config-name-link" data-config-id="${config.id}">${cn}</a></td>
                                <td style="padding: 12px 15px; border: 1px solid #e0e0e0; color: #333;">${cd}</td>
                                <td style="padding: 12px 15px; border: 1px solid #e0e0e0; color: #333;">${cdt}</td>
                            `;
                            tableBody.appendChild(row);

                            // Add click handler for the name link to open edit modal
                            const nameLink = row.querySelector('.config-name-link');
                            nameLink.addEventListener('click', function (e) {
                                e.preventDefault();
                                e.stopPropagation();
                                const configId = this.dataset.configId;
                                const moduleName = document.getElementById('selectedFacetName').textContent;
                                showConfigurePeriodicReview(contentArea, moduleId, moduleName, configId);
                            });

                            // Add click handler for row selection (for delete)
                            row.addEventListener('click', function (e) {
                                // Don't select if clicking on the name link
                                if (e.target.classList.contains('config-name-link')) {
                                    return;
                                }

                                // Single selection: Remove 'selected' class from all rows first
                                const allRows = document.querySelectorAll('#configurationsTableBody tr');
                                allRows.forEach(r => r.classList.remove('selected'));

                                // Toggle selection for current row
                                this.classList.toggle('selected');
                                
                                // Update delete button state after selection
                                updateDeleteButtonState();
                            });
                        });
                        recordCount.textContent = data.count === 1
                            ? prT('recordCountOne', '1 record')
                            : prTpl(prT('recordsCount', '{{count}} records'), { count: data.count });
                        // Update delete button state after loading configurations
                        updateDeleteButtonState();
                    } else {
                        tableBody.innerHTML = '<tr><td colspan="3" style="padding: 40px 20px; text-align: center; color: #999; border: 1px solid #e0e0e0;">' + prEscape(prT('noConfigurationsFound', 'No configurations found')) + '</td></tr>';
                        recordCount.textContent = prTpl(prT('recordsCount', '{{count}} records'), { count: 0 });
                        // Update delete button state when no configurations
                        updateDeleteButtonState();
                    }
                } else {
                    tableBody.innerHTML = '<tr><td colspan="3" style="padding: 40px 20px; text-align: center; color: #d32f2f; border: 1px solid #e0e0e0;">' + prEscape(prT('failedLoadConfigurations', 'Failed to load configurations')) + '</td></tr>';
                    recordCount.textContent = prTpl(prT('recordsCount', '{{count}} records'), { count: 0 });
                    // Update delete button state on error
                    updateDeleteButtonState();
                }
            })
            .catch(error => {
                console.error('Error fetching configurations:', error);
                tableBody.innerHTML = '<tr><td colspan="3" style="padding: 40px 20px; text-align: center; color: #d32f2f; border: 1px solid #e0e0e0;">' + prEscape(prT('errorLoadingConfigurations', 'Error loading configurations')) + '</td></tr>';
                recordCount.textContent = prTpl(prT('recordsCount', '{{count}} records'), { count: 0 });
                // Update delete button state on error
                updateDeleteButtonState();
            });
    }

    function formatDate(dateString) {
        if (!dateString) return '-';
        const date = new Date(dateString);
        const day = String(date.getDate()).padStart(2, '0');
        const month = date.toLocaleString('en-US', { month: 'short' });
        const year = date.getFullYear();
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');

        return `${day}-${month}-${year} ${hours}:${minutes}:00`;
    }

    // ====== Utility functions ======
    function updateRecordCountUI() {
        const recordCount = document.getElementById('recordCount');
        // Count only rows that represent actual configs (have data-config-id)
        const remainingRows = document.querySelectorAll('#configurationsTableBody tr[data-config-id]').length;

        if (remainingRows === 0) {
            const tableBody = document.getElementById('configurationsTableBody');
            tableBody.innerHTML = '<tr><td colspan="3" style="padding: 40px 20px; text-align: center; color: #999; border: 1px solid #e0e0e0;">' + prEscape(prT('noConfigurationsFound', 'No configurations found')) + '</td></tr>';
            recordCount.textContent = prTpl(prT('recordsCount', '{{count}} records'), { count: 0 });
        } else {
            recordCount.textContent = remainingRows === 1
                ? prT('recordCountOne', '1 record')
                : prTpl(prT('recordsCount', '{{count}} records'), { count: remainingRows });
        }
    }

    function updateDeleteButtonState() {
        const deleteConfigBtn = document.getElementById('deleteConfigBtn');
        if (!deleteConfigBtn) return;

        const selectedRows = document.querySelectorAll('#configurationsTableBody tr.selected');
        if (selectedRows.length > 0) {
            // Enable button - make it visible and clickable
            deleteConfigBtn.style.color = '#d32f2f';
            deleteConfigBtn.style.cursor = 'pointer';
            deleteConfigBtn.style.opacity = '1';
            deleteConfigBtn.disabled = false;
            deleteConfigBtn.style.pointerEvents = 'auto';
        } else {
            // Disable button - make it grayed out
            deleteConfigBtn.style.color = '#999';
            deleteConfigBtn.style.cursor = 'not-allowed';
            deleteConfigBtn.style.opacity = '0.6';
            deleteConfigBtn.disabled = true;
            deleteConfigBtn.style.pointerEvents = 'none';
        }
    }

    // ====== Setup event listeners after content is injected ======
    // Use setTimeout to ensure DOM is ready after innerHTML injection
    setTimeout(function () {
        // Ensure delete button reference exists
        const deleteConfigBtn = document.getElementById('deleteConfigBtn');

        // 1) Table row click => toggle 'selected' (using event delegation)
        const tableBody = document.getElementById('configurationsTableBody');
        if (tableBody) {
            tableBody.addEventListener('click', function (e) {
                const row = e.target.closest('tr');
                if (!row || !tableBody.contains(row)) return;

                // If the row is the "No configurations found" placeholder it won't have data-config-id
                if (!row.dataset.configId) return;

                // Don't select if clicking on the name link
                if (e.target.classList.contains('config-name-link')) {
                    return;
                }

                // Single selection: Remove 'selected' class from all rows first
                const allRows = document.querySelectorAll('#configurationsTableBody tr');
                allRows.forEach(r => r.classList.remove('selected'));

                // Toggle selection for current row
                row.classList.toggle('selected');

                // Update delete button state after toggling
                updateDeleteButtonState();
            });
        }

        // 2) Initialize delete button state and record count
        updateDeleteButtonState();
        updateRecordCountUI();

        // 3) Add button handler (if present)
        const addConfigBtn = document.getElementById('addConfigBtn');
        if (addConfigBtn) {
            addConfigBtn.addEventListener('click', function () {
                const moduleId = document.getElementById('facetSelect').value;
                const moduleName = document.getElementById('selectedFacetName').textContent;
                showConfigurePeriodicReview(contentArea, moduleId, moduleName);
            });
        }

        // 4) DELETE button handler (only removes rows after successful API response)
        if (deleteConfigBtn) {
            deleteConfigBtn.addEventListener('click', function () {
                const selectedRows = Array.from(document.querySelectorAll('#configurationsTableBody tr.selected'));

                if (selectedRows.length === 0) {
                    showNotification(prT('alertSelectConfigToDelete', 'Please select a configuration to delete.'), 'error');
                    return;
                }

                const configIds = selectedRows.map(row => row.dataset.configId).filter(Boolean);
                const configNames = selectedRows.map(row => {
                    const nameLink = row.querySelector('.config-name-link');
                    return nameLink ? nameLink.textContent : prT('unknown', 'Unknown');
                });

                const confirmMessage = selectedRows.length === 1
                    ? prTpl(prT('confirmDeleteSingle', 'Are you sure you want to delete "{{name}}"?'), { name: configNames[0] })
                    : prTpl(prT('confirmDeleteMultiple', 'Are you sure you want to delete the following configuration(s)?\n\n{{names}}'), { names: configNames.join('\n') });

                if (!confirm(confirmMessage)) return;

                // Send one request per ID (or change to bulk API if available)
                Promise.all(configIds.map(id => {
                    return fetch(`/api/periodic-review-config?id=${encodeURIComponent(id)}`, {
                        method: 'DELETE'
                    })
                    .then(res => {
                        // if backend sometimes returns non-JSON (e.g., 204), handle that
                        return res.ok ? res.json().catch(() => ({ success: true })) : res.json().then(j => ({ success: false, ...j }));
                    })
                    .then(data => ({ id, data }))
                    .catch(err => ({ id, error: true, err }));
                }))
                .then(results => {
                    let anyFailed = false;
                    results.forEach(r => {
                        if (r.error || !r.data || !r.data.success) {
                            anyFailed = true;
                            console.error('Failed delete:', r);
                            showNotification(prTpl(prT('alertFailedDeleteConfig', 'Failed to delete configuration (ID: {{id}})'), { id: r.id }), 'error');
                        } else {
                            // remove row from UI
                            const row = document.querySelector(`#configurationsTableBody tr[data-config-id="${r.id}"]`);
                            if (row) row.remove();
                        }
                    });

                    // Update counts and button state after removals
                    updateRecordCountUI();
                    updateDeleteButtonState();

                    if (!anyFailed) {
                        // optional: show success toast
                        console.log('All selected configurations deleted (soft deleted).');
                    }
                })
                .catch(err => {
                    console.error('Error during delete requests', err);
                    showNotification(prT('alertServerErrorDelete', 'Server error while deleting. Please try again.'), 'error');
                });
            });
        }
    }, 0);

}

function showConfigurePeriodicReview(contentArea, moduleId, moduleName, configId = null) {
    var h = prEscape;
    var t = prT;
    contentArea.innerHTML = `
           <div style="padding: 20px; background-color: #f5f5f5; min-height: 100vh;">
               <!-- Header -->
               <div style="background-color: #37474f; color: white; padding: 15px 20px; display: flex; justify-content: space-between; align-items: center; margin: -20px -20px 20px -20px;">
                   <div style="display: flex; align-items: center; gap: 10px;">
                       <button id="backBtn" style="background: none; border: none; color: white; cursor: pointer; font-size: 20px;">
                           <i class="fas fa-arrow-left"></i>
                       </button>
                       <h5 style="margin: 0;">${h(t('configureTitleNew', 'Configure New Periodic Review'))}</h5>
                   </div>
                   <div style="display: flex; gap: 10px;">
                       <button id="saveBtn" class="btn btn-sm" style="background-color: #fff; color: #333; border: none; padding: 8px 20px;">${h(t('save', 'Save'))}</button>
                       <button id="saveCloseBtn" class="btn btn-sm" style="background-color: #fff; color: #333; border: none; padding: 8px 20px;">${h(t('saveAndClose', 'Save & Close'))}</button>
                       <button id="closeBtn" class="btn btn-sm" style="background-color: #fff; color: #333; border: none; padding: 8px 20px;">${h(t('close', 'Close'))}</button>
                   </div>
               </div>

               <!-- Definition Section -->
               <div class="card" style="margin-bottom: 20px; border: 1px solid #ddd;">
                   <div class="card-header" style="background-color: #248567; padding: 12px 20px; border-bottom: 1px solid #ddd;">
                       <strong>${h(t('definition', 'DEFINITION'))}</strong>
                   </div>
                   <div class="card-body" style="padding: 30px; background-color: white;">
                       <div class="form-group" style="margin-bottom: 25px; display: grid; grid-template-columns: 250px 1fr; align-items: start; gap: 20px;">
                           <label style="margin: 0; padding-top: 8px;">
                               ${h(t('colName', 'Name'))}<span style="color: red;">*</span>
                           </label>
                           <div>
                               <input type="text" id="configName" class="form-control" placeholder="${h(t('namePlaceholder', 'Name of the Configuration'))}" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <small style="color: #666; font-size: 12px;">${h(t('hintLength6to256', 'Enter a value between 6 and 256 characters.'))}</small>
                           </div>
                       </div>

                       <div class="form-group" style="display: grid; grid-template-columns: 250px 1fr; align-items: start; gap: 20px;">
                           <label style="margin: 0; padding-top: 8px;">
                               ${h(t('colDescription', 'Description'))}<span style="color: red;">*</span>
                           </label>
                           <div>
                               <textarea id="configDescription" class="form-control" placeholder="${h(t('descriptionPlaceholder', 'Description of the configuration'))}" rows="3" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px; resize: vertical;"></textarea>
                               <small style="color: #666; font-size: 12px;">${h(t('hintLength6to256', 'Enter a value between 6 and 256 characters.'))}</small>
                           </div>
                       </div>
                   </div>
               </div>

               <!-- Periodic Review Date and Recurrence Section -->
               <div class="card" style="margin-bottom: 20px; border: 1px solid #ddd;">
                   <div class="card-header" style="background-color: #248567; padding: 12px 20px; border-bottom: 1px solid #ddd;">
                       <strong>${h(t('periodicReviewDateRecurrence', 'PERIODIC REVIEW DATE AND RECURRENCE'))}</strong>
                   </div>
                   <div class="card-body" style="padding: 30px; background-color: white;">
                      <div style="display: grid; grid-template-columns: 250px 1fr; gap: 40px; margin-bottom: 25px;">
                          <div class="form-group">
                              <label style="margin-bottom: 8px; display: block;">
                                  ${h(t('startDateLabel', 'Periodic Review Start Date'))}<span style="color: red;">*</span>
                              </label>
                              <input type="date" id="startDate" class="form-control" value="2025-11-26" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                          </div>

                          <div class="form-group">
                              <label style="margin-bottom: 8px; display: block;">
                                  ${h(t('recurrenceLabel', 'Periodic Review Recurrence'))}<span style="color: red;">*</span>
                              </label>
                              <div id="recurrenceBtn" style="display: flex; align-items: center; gap: 0; border: 1px solid #ccc; border-radius: 4px; overflow: hidden; cursor: pointer; background-color: #fff;">
                                  <input type="text" id="recurrenceSummary" class="form-control" value="${h(t('doNotRecur', 'Do Not Recur'))}" readonly
                                         style="border: none; box-shadow: none; cursor: pointer; background-color: #fff;">
                                  <div style="width: 40px; display: flex; align-items: center; justify-content: center; border-left: 1px solid #ccc; background-color: #f5f5f5;">
                                      <i class="fas fa-pencil-alt" style="color: #666;"></i>
                                  </div>
                              </div>
                          </div>
                      </div>

                      <div class="form-group" style="display: grid; grid-template-columns: 250px 1fr; align-items: center; gap: 20px;">
                           <label style="margin: 0;">
                               ${h(t('generateChangeRequest', 'Generate Change Request'))}<span style="color: red;">*</span>
                           </label>
                           <div style="display: flex; align-items: center; gap: 10px;">
                               <input type="number" id="generateDays" class="form-control" value="15" style="width: 150px; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <span style="color: #666;">${h(t('daysBeforeReview', 'days before periodic review date'))}</span>
                           </div>
                       </div>
                   </div>
               </div>

               <!-- Filters Section -->
               <div class="card" style="margin-bottom: 20px; border: 1px solid #ddd;">
                   <div class="card-header" style="background-color: #248567; padding: 12px 20px; border-bottom: 1px solid #ddd; display: flex; align-items: center; gap: 10px;">
                       <strong>${h(t('filters', 'FILTERS'))}</strong>
                       <i class="fas fa-info-circle" style="color: #666; cursor: help;" title="${h(t('filterInfoTitle', 'Filter information'))}"></i>
                   </div>
                   <div class="card-body" style="padding: 30px; background-color: white;">
                       <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 20px;">
                           <div class="form-group">
                               <label style="margin-bottom: 8px; display: block;">${h(t('type', 'Type'))}</label>
                               <select id="filterType" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                                   <option value="">${h(t('selectType', 'Select Type'))}</option>
                               </select>
                           </div>

                           <div class="form-group">
                               <label style="margin-bottom: 8px; display: block;">${h(t('budgStatus', 'BUDG Status'))}</label>
                               <select id="filterStatus" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                                   <option value="">${h(t('selectBudgStatus', 'Select BUDG Status'))}</option>
                               </select>
                           </div>

                           <div class="form-group">
                               <label style="margin-bottom: 8px; display: block;">${h(t('lifecycle', 'Lifecycle'))}</label>
                               <select id="filterLifecycle" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                                   <option value="">${h(t('selectLifecycle', 'Select Lifecycle'))}</option>
                               </select>
                           </div>
                       </div>
                   </div>
               </div>

               <!-- Workflow and Change Request Configuration Section -->
               <div class="card" style="margin-bottom: 20px; border: 1px solid #ddd;">
                   <div class="card-header" style="background-color: #248567; padding: 12px 20px; border-bottom: 1px solid #ddd;">
                       <strong>${h(t('workflowCrConfig', 'WORKFLOW AND CHANGE REQUEST CONFIGURATION'))}</strong>
                   </div>
                   <div class="card-body" style="padding: 30px; background-color: white;">
                       <div class="form-group" style="margin-bottom: 25px; display: grid; grid-template-columns: 250px 1fr; align-items: start; gap: 20px;">
                           <label style="margin: 0; padding-top: 8px;">
                               ${h(t('crTitleLabel', 'Default Change Request Title'))}<span style="color: red;">*</span>
                           </label>
                           <div>
                               <input type="text" id="crTitle" class="form-control" placeholder="${h(t('crTitlePlaceholder', 'Title of the default change request'))}" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <small style="color: #666; font-size: 12px;">${h(t('hintLength6to256', 'Enter a value between 6 and 256 characters.'))}</small>
                           </div>
                       </div>

                       <div class="form-group" style="margin-bottom: 25px; display: grid; grid-template-columns: 250px 1fr; align-items: start; gap: 20px;">
                           <label style="margin: 0; padding-top: 8px;">
                               ${h(t('crSummaryLabel', 'Default Change Request Summary'))}<span style="color: red;">*</span>
                           </label>
                           <div>
                               <textarea id="crSummary" class="form-control" placeholder="${h(t('crSummaryPlaceholder', 'Summary of the default change request'))}" rows="3" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px; resize: vertical;"></textarea>
                               <small style="color: #666; font-size: 12px;">Enter a value between 6 and 256 characters.</small>
                           </div>
                       </div>

                       <div class="form-group" id="crSystemGroup" style="margin-bottom: 25px; display: grid; grid-template-columns: 250px 1fr; align-items: center; gap: 20px;">
                           <label style="margin: 0;">
                               ${h(t('crSystemLabel', 'Default Change Request System'))}<span style="color: red;">*</span>
                           </label>
                           <select id="crSystem" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <option value="">Name</option>
                           </select>
                       </div>

                       <div class="form-group" style="margin-bottom: 25px; display: grid; grid-template-columns: 250px 1fr; align-items: center; gap: 20px;">
                           <label style="margin: 0;">
                               ${h(t('defaultWorkflow', 'Default workflow'))}<span style="color: red;">*</span>
                           </label>
                           <select id="crWorkflow" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <option value="">DefaultWorkflow->DataSet</option>
                           </select>
                       </div>

                       <div class="form-group" style="margin-bottom: 25px; display: grid; grid-template-columns: 250px 1fr; align-items: center; gap: 20px;">
                           <label style="margin: 0;">
                               ${h(t('crTypeLabel', 'Default Change Request Type'))}<span style="color: red;">*</span>
                           </label>
                           <select id="crType" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <option value="">${h(t('selectDefaultType', 'Select Default Type'))}</option>
                           </select>
                       </div>

                       <div class="form-group" style="margin-bottom: 25px; display: grid; grid-template-columns: 250px 1fr; align-items: center; gap: 20px;">
                           <label style="margin: 0;">
                               ${h(t('crUrgencyLabel', 'Default Change Request Urgency'))}<span style="color: red;">*</span>
                           </label>
                           <select id="crUrgency" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <option value="">${h(t('selectDefaultUrgency', 'Select Default Urgency'))}</option>
                           </select>
                       </div>

                       <div class="form-group" style="margin-bottom: 25px; display: grid; grid-template-columns: 250px 1fr; align-items: center; gap: 20px;">
                           <label style="margin: 0;">
                               Default Change Request Severity<span style="color: red;">*</span>
                           </label>
                           <select id="crSeverity" class="form-control" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <option value="">Select Default Severity</option>
                           </select>
                       </div>

                       <div class="form-group" style="display: grid; grid-template-columns: 250px 1fr; align-items: start; gap: 20px;">
                           <label style="margin: 0; padding-top: 8px;">
                               User Email<span style="color: red;">*</span>
                           </label>
                           <div>
                               <input type="email" id="crInitiatorEmail" class="form-control" placeholder="${h(t('crInitiatorEmailPlaceholder', 'Enter User Email'))}" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;">
                               <small style="color: #666; font-size: 12px;">Enter the specific email address of the BUDG user that creates the change request before BUDG starts a periodic review.</small>
                           </div>
                       </div>
                   </div>
               </div>

             <!-- Recurrence Modal (custom overlay to behave as popup window) -->
             <div id="recurrenceModal" class="prr-modal-overlay">
                 <div class="prr-modal-dialog">
                     <div class="prr-modal-header">
                         <h5 class="prr-modal-title">Periodic Review Recurrence</h5>
                         <button id="recurrenceCloseBtn" type="button" class="prr-modal-close">×</button>
                     </div>

                    <div class="prr-modal-body">
                        <div class="row">
                            <div class="col-sm-3 form-group">
                                <label for="prrFrequencyType" class="label-hide">Periodic Review Recurrence</label>
                                <select id="prrFrequencyType" class="form-control">
                                    <option value="yearly">Yearly</option>
                                    <option value="monthly">Monthly</option>
                                    <option value="none">Do not recur</option>
                                </select>
                            </div>

                            <div class="col-sm-9 form-group" id="periodic_recur_config" role="group" aria-labelledby="prrFrequencyType" style="display: block; width: 100%;">
                                <!-- Row 1: every N year(s)/month(s) after start date -->
                                <div class="periodic-recur-margin" id="yearly_prr" style="display: none;">
                                    <span class="prr-modal-radio">
                                        <input type="radio" id="prr_radio_yearly" name="prrFrequencyRadio" value="yearly_prr" checked>
                                    </span>
                                    <label for="prr_radio_yearly" id="everyLabel_yearly">every</label>

                                    <input type="number" id="prrYearlyMonths" class="prr-int-type-width" min="1" value="1">
                                    <label for="prrYearlyMonths" id="everyMonthLabel_yearly">year(s) after the periodic review start date</label>
                                </div>

                                <!-- Row 2: on day of month every N year(s)/month(s) -->
                                <div class="periodic-recur-margin" id="monthly_prr" style="display: none;">
                                    <span class="prr-modal-radio">
                                        <input type="radio" id="prr_radio_monthly" name="prrFrequencyRadio" value="monthly_prr">
                                    </span>
                                    <label for="prr_radio_monthly" id="onLabel_monthly">on</label>

                                    <input type="number" id="prrMonthlyDay" class="prr-int-type-width" min="1" max="31" value="1">
                                    <label for="prrMonthlyDay" id="ofLabel_monthly">of</label>

                                    <select id="prrMonthlyMonth" class="prr-int-type-width">
                                        <option value="January">January</option>
                                        <option value="February">February</option>
                                        <option value="March">March</option>
                                        <option value="April">April</option>
                                        <option value="May">May</option>
                                        <option value="June">June</option>
                                        <option value="July">July</option>
                                        <option value="August">August</option>
                                        <option value="September">September</option>
                                        <option value="October">October</option>
                                        <option value="November">November</option>
                                        <option value="December">December</option>
                                    </select>

                                    <label id="everyLabel_monthly">every</label>
                                    <input type="number" id="prrMonthlyEvery" class="prr-int-type-width" min="1" value="1">
                                    <label for="prrMonthlyEvery" id="yearsLabel_monthly">year(s)</label>
                                </div>

                                <!-- Row 3: on First Sunday of January every N year(s) -->
                                <div class="periodic-recur-margin" id="weekly_prr" style="display: none;">
                                    <span class="prr-modal-radio">
                                        <input type="radio" id="prr_radio_weekly" name="prrFrequencyRadio" value="weekly_prr">
                                    </span>
                                    <label for="prr_radio_weekly" id="onLabel_weekly">on</label>

                                    <select id="prrWeeklyWeek" class="prr-int-type-width">
                                        <option value="First">First</option>
                                        <option value="Second">Second</option>
                                        <option value="Third">Third</option>
                                        <option value="Fourth">Fourth</option>
                                    </select>

                                    <select id="prrWeeklyDay" class="prr-int-type-width">
                                        <option value="Sunday">Sunday</option>
                                        <option value="Monday">Monday</option>
                                        <option value="Tuesday">Tuesday</option>
                                        <option value="Wednesday">Wednesday</option>
                                        <option value="Thursday">Thursday</option>
                                        <option value="Friday">Friday</option>
                                        <option value="Saturday">Saturday</option>
                                    </select>

                                    <label id="ofMonthLabel_weekly">of</label>

                                    <select id="prrWeeklyMonth" class="prr-int-type-width">
                                        <option value="January">January</option>
                                        <option value="February">February</option>
                                        <option value="March">March</option>
                                        <option value="April">April</option>
                                        <option value="May">May</option>
                                        <option value="June">June</option>
                                        <option value="July">July</option>
                                        <option value="August">August</option>
                                        <option value="September">September</option>
                                        <option value="October">October</option>
                                        <option value="November">November</option>
                                        <option value="December">December</option>
                                    </select>

                                    <label id="everyYearLabel_weekly">of every</label>
                                    <input type="number" id="prrWeeklyEvery" class="prr-int-type-width" min="1" value="1">
                                    <label id="yearlyLabel_weekly" for="prrWeeklyEvery">year(s)</label>
                                </div>
                            </div>
                        </div>
                    </div>

                     <div class="prr-modal-footer" style="text-align: right;">
                         <button class="btn btn-primary" id="saveRecurrence">Save</button>
                         <button class="btn btn-secondary" id="cancelRecurrence">Cancel</button>
                     </div>
                 </div>
             </div>

             <style>
                 .prr-modal-overlay {
                     position: fixed;
                     top: 0;
                     left: 0;
                     width: 100%;
                     height: 100%;
                     background: rgba(0, 0, 0, 0.4);
                     display: none;
                     align-items: center;
                     justify-content: center;
                     z-index: 1050;
                 }

                 .prr-modal-overlay.show {
                     display: flex;
                 }

                 .prr-modal-dialog {
                     background: #fff;
                     border-radius: 4px;
                     width: 90%;
                     max-width: 900px;
                     max-height: 80vh;
                     display: flex;
                     flex-direction: column;
                     box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
                 }

                 .prr-modal-header,
                 .prr-modal-footer {
                     padding: 15px 20px;
                     border-bottom: 1px solid #ddd;
                 }

                 .prr-modal-footer {
                     border-top: 1px solid #ddd;
                     border-bottom: none;
                 }

                 .prr-modal-body {
                     padding: 20px;
                     overflow-y: auto;
                 }

                 .prr-modal-close {
                     border: none;
                     background: transparent;
                     font-size: 20px;
                     line-height: 1;
                     cursor: pointer;
                     color: #777;
                 }

                .prr-modal-close:hover {
                    color: #333;
                }

                #periodic_recur_config {
                    display: block !important;
                    width: 100% !important;
                }

                #yearly_prr,
                #monthly_prr,
                #weekly_prr {
                    display: block !important;
                    width: 100% !important;
                    margin-bottom: 15px !important;
                    clear: both !important;
                    float: none !important;
                    box-sizing: border-box !important;
                }

                #yearly_prr[style*="display: none"],
                #monthly_prr[style*="display: none"],
                #weekly_prr[style*="display: none"] {
                    display: none !important;
                    visibility: hidden !important;
                }

                .periodic-recur-margin {
                    display: flex !important;
                    align-items: center !important;
                    width: 100% !important;
                    margin-bottom: 20px !important;
                    clear: both !important;
                    float: none !important;
                    box-sizing: border-box !important;
                    gap: 8px;
                    flex-wrap: wrap;
                }

                .periodic-recur-margin > * {
                    display: inline-block;
                    vertical-align: middle;
                }

                .periodic-recur-margin label {
                    display: inline-block;
                    margin: 0;
                    white-space: nowrap;
                }

                .periodic-recur-margin input[type="number"] {
                    display: inline-block;
                    vertical-align: middle;
                    width: 60px;
                    padding: 6px 8px;
                    border: 1px solid #ccc;
                    border-radius: 4px;
                    text-align: center;
                }

                .periodic-recur-margin select {
                    display: inline-block;
                    vertical-align: middle;
                    min-width: 100px;
                    padding: 6px 8px;
                    border: 1px solid #ccc;
                    border-radius: 4px;
                    background-color: white;
                }

                #prrMonthlyMonth {
                    min-width: 80px;
                    width: 80px;
                    padding: 6px 4px;
                }

                #prrWeeklyWeek,
                #prrWeeklyDay,
                #prrWeeklyMonth {
                    min-width: 80px;
                    width: 80px;
                    padding: 6px 4px;
                }

                .prr-modal-radio {
                    display: inline-flex;
                    align-items: center;
                    margin-right: 8px;
                    vertical-align: middle;
                }

                .prr-modal-radio input[type="radio"] {
                    width: 18px;
                    height: 18px;
                    margin: 0;
                    cursor: pointer;
                }
            </style>
         </div>
     `;
    // Initialize form with lookup data
    const entityId = moduleId; // or get from another source if needed

    if (configId) {
        // Edit mode
        window.periodicReviewCRUD.initializeEditConfigForm(configId, moduleId, entityId);
    } else {
        // Create mode
        window.periodicReviewCRUD.initializeNewConfigForm(moduleId, entityId);
    }
    // Back button handler
    document.getElementById('backBtn').addEventListener('click', function () {
        showPeriodicReviewContent(contentArea);
    });

    // Close button handler
    document.getElementById('closeBtn').addEventListener('click', function () {
        showPeriodicReviewContent(contentArea);
    });

    // Save button handler
    document.getElementById('saveBtn').addEventListener('click', async function () {
        const saved = await window.periodicReviewCRUD.saveConfiguration();
        // Stay on page after save
    });

    // Save & Close button handler
    document.getElementById('saveCloseBtn').addEventListener('click', async function () {
        const saved = await window.periodicReviewCRUD.saveConfiguration();
        if (saved) {
            showPeriodicReviewContent(contentArea);
        }
    });

    // Show/Hide Default Change Request System based on module name
    const crSystemGroup = document.getElementById('crSystemGroup');
    if (crSystemGroup) {
        // Check if moduleName is 'System' (case-insensitive)
        if (moduleName && moduleName.toLowerCase().trim() === 'system') {
            crSystemGroup.style.display = 'grid';
        } else {
            crSystemGroup.style.display = 'none';
        }
    }

    // Recurrence Modal Logic
    const prrFrequencyTypeSelect = document.getElementById('prrFrequencyType');
    const yearlyBlock = document.getElementById('yearly_prr');
    const monthlyBlock = document.getElementById('monthly_prr');
    const weeklyBlock = document.getElementById('weekly_prr');
    const recurrenceSummaryInput = document.getElementById('recurrenceSummary');

    const recurrenceBtn = document.getElementById('recurrenceBtn');
    const recurrenceCloseBtn = document.getElementById('recurrenceCloseBtn');
    const cancelRecurrenceBtn = document.getElementById('cancelRecurrence');

    const monthlyRadio = document.getElementById('prr_radio_monthly');
    const weeklyRadio = document.getElementById('prr_radio_weekly');
    const yearlyRadio = document.getElementById('prr_radio_yearly'); // Added missing yearlyRadio definition
    const yearlyLabelWeekly = document.getElementById('yearlyLabel_weekly');
    const ofMonthLabelWeekly = document.getElementById('ofMonthLabel_weekly');
    const prrWeeklyMonth = document.getElementById('prrWeeklyMonth');
    const everyTextWeekly = document.getElementById('everyText_weekly');
    const everyYearLabelWeekly = document.getElementById('everyYearLabel_weekly');
    const prrMonthlyMonth = document.getElementById('prrMonthlyMonth');
    const everyTextMonthly = document.getElementById('everyText_monthly');
    const ofLabelMonthly = document.getElementById('ofLabel_monthly');

    let savedState = {};

    function saveCurrentState() {
        savedState = {
            mode: prrFrequencyTypeSelect.value,
            radio: document.querySelector('input[name="prrFrequencyRadio"]:checked')?.value,
            prrYearlyMonths: document.getElementById('prrYearlyMonths').value,
            prrMonthlyDay: document.getElementById('prrMonthlyDay').value,
            prrMonthlyMonth: document.getElementById('prrMonthlyMonth') ? document.getElementById('prrMonthlyMonth').value : '',
            prrMonthlyEvery: document.getElementById('prrMonthlyEvery').value,
            prrWeeklyWeek: document.getElementById('prrWeeklyWeek').value,
            prrWeeklyDay: document.getElementById('prrWeeklyDay').value,
            prrWeeklyMonth: document.getElementById('prrWeeklyMonth') ? document.getElementById('prrWeeklyMonth').value : '',
            prrWeeklyEvery: document.getElementById('prrWeeklyEvery').value
        };
    }

    function restoreState() {
        if (savedState.mode) {
            setMode(savedState.mode);
            prrFrequencyTypeSelect.value = savedState.mode;
        }

        if (savedState.radio) {
            const radio = document.querySelector(`input[name="prrFrequencyRadio"][value="${savedState.radio}"]`);
            if (radio) {
                radio.checked = true;
                // Trigger change event to update UI
                radio.dispatchEvent(new Event('change'));
            }
        }

        if (document.getElementById('prrYearlyMonths')) document.getElementById('prrYearlyMonths').value = savedState.prrYearlyMonths || 1;
        if (document.getElementById('prrMonthlyDay')) document.getElementById('prrMonthlyDay').value = savedState.prrMonthlyDay || 1;
        if (document.getElementById('prrMonthlyMonth') && savedState.prrMonthlyMonth) document.getElementById('prrMonthlyMonth').value = savedState.prrMonthlyMonth;
        if (document.getElementById('prrMonthlyEvery')) document.getElementById('prrMonthlyEvery').value = savedState.prrMonthlyEvery || 1;
        if (document.getElementById('prrWeeklyWeek')) document.getElementById('prrWeeklyWeek').value = savedState.prrWeeklyWeek || 'First';
        if (document.getElementById('prrWeeklyDay')) document.getElementById('prrWeeklyDay').value = savedState.prrWeeklyDay || 'Sunday';
        if (document.getElementById('prrWeeklyMonth') && savedState.prrWeeklyMonth) document.getElementById('prrWeeklyMonth').value = savedState.prrWeeklyMonth;
        if (document.getElementById('prrWeeklyEvery')) document.getElementById('prrWeeklyEvery').value = savedState.prrWeeklyEvery || 1;
    }

    function showRecurrenceModal() {
        saveCurrentState();
        const modal = document.getElementById('recurrenceModal');
        if (modal) modal.classList.add('show');
    }

    function hideRecurrenceModal() {
        const modal = document.getElementById('recurrenceModal');
        if (modal) modal.classList.remove('show');
    }

    function cancelRecurrence() {
        restoreState();
        hideRecurrenceModal();
    }

    if (recurrenceBtn) recurrenceBtn.addEventListener('click', showRecurrenceModal);
    if (recurrenceCloseBtn) recurrenceCloseBtn.addEventListener('click', hideRecurrenceModal); // Close button just closes, keeping state (optional, usually close = cancel)
    if (cancelRecurrenceBtn) cancelRecurrenceBtn.addEventListener('click', cancelRecurrence);

    function setMode(mode) {
        // frequency select
        prrFrequencyTypeSelect.value = mode;

        // Enable all options in dropdown - no disabling
        const yearlyOption = prrFrequencyTypeSelect.querySelector('option[value="yearly"]');
        const monthlyOption = prrFrequencyTypeSelect.querySelector('option[value="monthly"]');
        const noneOption = prrFrequencyTypeSelect.querySelector('option[value="none"]');
        if (yearlyOption) yearlyOption.disabled = false;
        if (monthlyOption) monthlyOption.disabled = false;
        if (noneOption) noneOption.disabled = false;

        // Hide all rows by default
        yearlyBlock.style.display = 'none';
        monthlyBlock.style.display = 'none';
        weeklyBlock.style.display = 'none';

        // Uncheck all radios
        yearlyRadio.checked = false;
        monthlyRadio.checked = false;
        weeklyRadio.checked = false;

        const muted = 'rgb(211, 211, 211)';
        const normal = 'inherit';

        if (mode === 'yearly') {
            // Show all 3 rows for Yearly
            yearlyBlock.style.display = 'block';
            yearlyBlock.style.visibility = 'visible';
            yearlyBlock.style.clear = 'both';
            monthlyBlock.style.display = 'block';
            monthlyBlock.style.visibility = 'visible';
            monthlyBlock.style.clear = 'both';
            weeklyBlock.style.display = 'block';
            weeklyBlock.style.visibility = 'visible';
            weeklyBlock.style.clear = 'both';

            // Select Row 1 by default
            yearlyRadio.checked = true;

            // Update Row 1 label to "year(s)"
            document.getElementById('everyMonthLabel_yearly').textContent = prT('everyYearAfterStart', 'year(s) after the periodic review start date');

            // Update Row 2 - show month dropdown for Yearly
            if (prrMonthlyMonth) prrMonthlyMonth.style.display = 'inline-block';
            if (everyTextMonthly) everyTextMonthly.style.display = 'none';
            if (ofLabelMonthly) ofLabelMonthly.style.display = 'inline';
            document.getElementById('yearsLabel_monthly').textContent = prT('yearsLabel', 'year(s)');

            // Update Row 3 label to "year(s)" and show month dropdown
            if (prrWeeklyMonth) prrWeeklyMonth.style.display = 'inline-block';
            if (ofMonthLabelWeekly) ofMonthLabelWeekly.style.display = 'inline';
            document.getElementById('yearlyLabel_weekly').textContent = prT('yearsLabel', 'year(s)');

            // Enable/disable fields based on selected radio
            updateFieldStates('yearly_prr', mode);

            // Set colors - Row 1 active, others muted
            yearlyBlock.style.color = normal;
            monthlyBlock.style.color = muted;
            weeklyBlock.style.color = muted;

        } else if (mode === 'monthly') {
            // Show all 3 rows for Monthly
            yearlyBlock.style.display = 'block';
            yearlyBlock.style.visibility = 'visible';
            yearlyBlock.style.clear = 'both';
            monthlyBlock.style.display = 'block';
            monthlyBlock.style.visibility = 'visible';
            monthlyBlock.style.clear = 'both';
            weeklyBlock.style.display = 'block';
            weeklyBlock.style.visibility = 'visible';
            weeklyBlock.style.clear = 'both';

            // Select Row 1 by default
            yearlyRadio.checked = true;

            // Update Row 1 label to "month(s)"
            document.getElementById('everyMonthLabel_yearly').textContent = prT('everyMonthAfterStart', 'month(s) after the periodic review start date');

            // Update Row 2 - hide month dropdown, show "every" text for Monthly
            if (prrMonthlyMonth) prrMonthlyMonth.style.display = 'none';
            if (everyTextMonthly) everyTextMonthly.style.display = 'inline';
            if (ofLabelMonthly) ofLabelMonthly.style.display = 'none';
            document.getElementById('yearsLabel_monthly').textContent = prT('monthsLabel', 'month(s)');

            // Update Row 3 label to "month(s)" and hide month dropdown
            if (prrWeeklyMonth) prrWeeklyMonth.style.display = 'none';
            if (ofMonthLabelWeekly) ofMonthLabelWeekly.style.display = 'none';
            if (everyTextWeekly) everyTextWeekly.style.display = 'none';
            if (everyYearLabelWeekly) everyYearLabelWeekly.style.display = 'inline';
            document.getElementById('yearlyLabel_weekly').textContent = prT('monthsLabel', 'month(s)');

            // Enable/disable fields based on selected radio
            updateFieldStates('yearly_prr', mode);

            // Set colors - Row 1 active, others muted
            yearlyBlock.style.color = normal;
            monthlyBlock.style.color = muted;
            weeklyBlock.style.color = muted;

        } else if (mode === 'none') {
            // Hide all rows for "Do Not Recur"
            yearlyBlock.style.display = 'none';
            yearlyBlock.style.visibility = 'hidden';
            monthlyBlock.style.display = 'none';
            monthlyBlock.style.visibility = 'hidden';
            weeklyBlock.style.display = 'none';
            weeklyBlock.style.visibility = 'hidden';

            // Uncheck all radios
            yearlyRadio.checked = false;
            monthlyRadio.checked = false;
            weeklyRadio.checked = false;
        }
    }

    function updateFieldStates(selectedRadio, mode) {
        const isYearlyRow = selectedRadio === 'yearly_prr';
        const isMonthlyRow = selectedRadio === 'monthly_prr';
        const isWeeklyRow = selectedRadio === 'weekly_prr';

        // Row 1 fields
        document.getElementById('prrYearlyMonths').disabled = !isYearlyRow;

        // Row 2 fields
        document.getElementById('prrMonthlyDay').disabled = !isMonthlyRow;
        document.getElementById('prrMonthlyMonth').disabled = !isMonthlyRow;
        document.getElementById('prrMonthlyEvery').disabled = !isMonthlyRow;

        // Row 3 fields
        document.getElementById('prrWeeklyWeek').disabled = !isWeeklyRow;
        document.getElementById('prrWeeklyDay').disabled = !isWeeklyRow;
        document.getElementById('prrWeeklyMonth').disabled = !isWeeklyRow;
        document.getElementById('prrWeeklyEvery').disabled = !isWeeklyRow;
    }

    // init default mode - set to "none" (Do Not Recur) by default
    prrFrequencyTypeSelect.value = 'none';
    setMode('none');

    // change by select
    prrFrequencyTypeSelect.addEventListener('change', () => {
        const val = prrFrequencyTypeSelect.value;
        setMode(val);
    });

    // change by radios - update field states and colors
    yearlyRadio.addEventListener('change', () => {
        if (yearlyRadio.checked) {
            const mode = prrFrequencyTypeSelect.value;
            updateFieldStates('yearly_prr', mode);
            const muted = 'rgb(211, 211, 211)';
            const normal = 'inherit';
            yearlyBlock.style.color = normal;
            monthlyBlock.style.color = muted;
            weeklyBlock.style.color = muted;
        }
    });
    monthlyRadio.addEventListener('change', () => {
        if (monthlyRadio.checked) {
            const mode = prrFrequencyTypeSelect.value;
            updateFieldStates('monthly_prr', mode);
            const muted = 'rgb(211, 211, 211)';
            const normal = 'inherit';
            yearlyBlock.style.color = muted;
            monthlyBlock.style.color = normal;
            weeklyBlock.style.color = muted;
        }
    });
    weeklyRadio.addEventListener('change', () => {
        if (weeklyRadio.checked) {
            const mode = prrFrequencyTypeSelect.value;
            updateFieldStates('weekly_prr', mode);
            const muted = 'rgb(211, 211, 211)';
            const normal = 'inherit';
            yearlyBlock.style.color = muted;
            monthlyBlock.style.color = muted;
            weeklyBlock.style.color = normal;
        }
    });

    // Save Recurrence and Display
    const saveRecurrenceBtn = document.getElementById('saveRecurrence');
    if (saveRecurrenceBtn) {
        saveRecurrenceBtn.addEventListener('click', () => {
            let text = "";
            const mode = prrFrequencyTypeSelect.value;

            if (mode === "none") {
                text = "Do Not Recur";
            } else {
                // Check which radio button is selected
                const selectedRadio = document.querySelector('input[name="prrFrequencyRadio"]:checked');
                const radioVal = selectedRadio ? selectedRadio.value : null;

                if (radioVal === "yearly_prr") {
                    // Row 1: "every N year(s)/month(s) after the periodic review start date"
                    const every = document.getElementById("prrYearlyMonths").value;
                    const unit = mode === "yearly" ? "Year(s)" : "Month(s)";
                    text = `Every ${every} ${unit} After Periodic Review Start Date`;
                } else if (radioVal === "monthly_prr") {
                    // Row 2: "on day of month every N year(s)/month(s)"
                    const day = document.getElementById("prrMonthlyDay").value;
                    const every = document.getElementById("prrMonthlyEvery").value;
                    const unit = mode === "yearly" ? "Year(s)" : "Month(s)";
                    if (mode === "yearly") {
                        const month = document.getElementById("prrMonthlyMonth").value;
                        text = `On ${day} Of ${month} Every ${every} ${unit}`;
                    } else {
                        text = `On ${day} Of Every ${every} ${unit}`;
                    }
                } else if (radioVal === "weekly_prr") {
                    // Row 3: "on First Sunday of January every N year(s)/month(s)"
                    const week = document.getElementById("prrWeeklyWeek").value;
                    const weekday = document.getElementById("prrWeeklyDay").value;
                    const month = document.getElementById("prrWeeklyMonth").value;
                    const every = document.getElementById("prrWeeklyEvery").value;
                    const unit = mode === "yearly" ? "Year(s)" : "Month(s)";
                    if (mode === "yearly") {
                        text = `On ${week} ${weekday} Of ${month} Every ${every} ${unit}`;
                    } else {
                        text = `On ${week} ${weekday} Of Every ${every} ${unit}`;
                    }
                }
            }

            recurrenceSummaryInput.value = text;
            hideRecurrenceModal();
        });
    }

    // Generate Days validation
    const generateDaysInput = document.getElementById('generateDays');
    if (generateDaysInput) {
        generateDaysInput.setAttribute('min', '1');

        generateDaysInput.addEventListener('change', function () {
            if (this.value <= 0) {
                this.value = 1;
            }
        });

        generateDaysInput.addEventListener('input', function () {
            if (this.value <= 0 && this.value !== '') {
                this.value = 1;
            }
        });
    }
}
