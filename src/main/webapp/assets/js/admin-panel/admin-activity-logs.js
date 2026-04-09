// Admin Activity Logs functionality for Admin Panel

let currentFilters = {
    setting: '',
    component: '',
    changeType: '',
    userName: '',
    fromDate: '',
    toDate: '',
    searchDetails: ''
};

let currentPage = 0;
const pageSize = 50;
let totalRecords = 0;

function showActivityLogsContent(contentArea) {
    highlightSubmenuItem('adminPanel.navigation.adminActivityLogs');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    addToNavigationHistory(T('adminPanel.activityLogsPage.pageTitle', 'ADMIN ACTIVITY LOGS'), showActivityLogsContent);

    // Add class to content-area for specific styling
    contentArea.classList.add('has-activity-logs');
    
    const loadingText = T('adminPanel.activityLogsPage.loadingActivityLogs', window.I18n ? window.I18n.t('adminPanel.common.loadingActivityLogs') : 'Loading activity logs...');
    
    contentArea.innerHTML = `
        <div class="activity-logs-container">
            <div class="activity-logs-header">
                <div class="header-left">
                    <h2>${T('adminPanel.activityLogsPage.pageTitle', 'ADMIN ACTIVITY LOGS')}</h2>
                </div>
            </div>

            <div class="activity-logs-filters">
                <div class="filters-row">
                    <div class="filter-group">
                        <label>${T('adminPanel.activityLogsPage.selectConfiguration', 'Select Configuration')}</label>
                        <select id="filterSetting" class="filter-select">
                            <option value="">${T('adminPanel.activityLogsPage.allSettings', 'All Settings')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>${T('adminPanel.activityLogsPage.selectComponent', 'Select a component.')}</label>
                        <select id="filterComponent" class="filter-select">
                            <option value="">${T('adminPanel.activityLogsPage.allComponents', 'All Components')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>${T('adminPanel.activityLogsPage.selectChangeType', 'Select Change Type')}</label>
                        <select id="filterChangeType" class="filter-select">
                            <option value="">${T('adminPanel.activityLogsPage.allChangeTypes', 'All Change Types')}</option>
                        </select>
                    </div>
                    <div class="filter-group">
                        <label>${T('adminPanel.activityLogsPage.selectUserName', 'Select User Name')}</label>
                        <select id="filterUserName" class="filter-select">
                            <option value="">${T('adminPanel.activityLogsPage.allUsers', 'All Users')}</option>
                        </select>
                    </div>
                </div>
                <div class="filters-row">
                    <div class="filter-group">
                        <label>${T('adminPanel.activityLogsPage.searchDetails', 'Search Details')}</label>
                        <input type="text" id="searchDetails" class="filter-input" placeholder="${T('adminPanel.activityLogsPage.searchPlaceholder', 'Search in any column...')}">
                    </div>
                    <div class="filter-group">
                        <label>${T('adminPanel.activityLogsPage.fromDate', 'From Date')}</label>
                        <input type="datetime-local" id="fromDate" class="filter-input">
                    </div>
                    <div class="filter-group">
                        <label>${T('adminPanel.activityLogsPage.toDate', 'To Date')}</label>
                        <input type="datetime-local" id="toDate" class="filter-input">
                    </div>
                    <div class="filter-actions">
                        <button id="searchBtn" class="btn-search">
                            <i class="fas fa-search"></i> ${T('adminPanel.activityLogsPage.search', 'Search')}
                        </button>
                        <button id="clearBtn" class="btn-clear">${T('adminPanel.activityLogsPage.clear', 'Clear')}</button>
                    </div>
                </div>
            </div>

            <div class="activity-logs-table-wrapper">
                <div class="activity-logs-table-container">
                    <table class="activity-logs-table">
                        <thead>
                            <tr>
                                <th>${T('adminPanel.activityLogsPage.sr', 'Sr')}</th>
                                <th>${T('adminPanel.activityLogsPage.setting', 'Setting')}</th>
                                <th>${T('adminPanel.activityLogsPage.component', 'Component')}</th>
                                <th>${T('adminPanel.activityLogsPage.user', 'User')}</th>
                                <th>${T('adminPanel.activityLogsPage.changeType', 'Change Type')}</th>
                                <th>${T('adminPanel.activityLogsPage.timestamp', 'Timestamp')}</th>
                                <th>${T('adminPanel.activityLogsPage.details', 'Details')}</th>
                            </tr>
                        </thead>
                        <tbody id="logsTableBody">
                            <tr>
                                <td colspan="7" class="loading-message">${loadingText}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    <div class="records-count" id="recordsCount">${T('adminPanel.staticPageEditor.recordsCount', '{count} records').replace('{count}', '0')}</div>
                    <div class="export-actions">
                        <button id="exportBtn" class="btn-export" title="${T('adminPanel.activityLogsPage.exportCsvTitle', 'Export to CSV')}">
                            <i class="fas fa-download"></i>
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <!-- Details Modal -->
        <div id="detailsModal" class="modal">
            <div class="modal-content">
                <div class="modal-header">
                    <h3>${T('adminPanel.activityLogsPage.changeDetails', 'Change Details')}</h3>
                    <div class="modal-actions">
                        <button class="btn-icon" id="closeModalBtn">
                            <i class="fas fa-times"></i>
                        </button>
                    </div>
                </div>
                <div class="modal-body">
                    <table class="details-table">
                        <thead>
                            <tr>
                                <th>Field Name</th>
                                <th>Old Value</th>
                                <th>New Value</th>
                            </tr>
                        </thead>
                        <tbody id="detailsTableBody">
                        </tbody>
                    </table>
                    <!-- Workflow Diagram Section (separate from table) -->
                    <div id="workflowDiagramSection" style="display: none; margin-top: 30px;">
                        <h4 style="margin-bottom: 20px; font-size: 14px; font-weight: 600; color: #333;">Workflow Diagram:</h4>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
                            <div>
                                <div style="font-weight: 600; margin-bottom: 10px; color: #666; font-size: 13px;">Old Value</div>
                                <div id="oldDiagramContainer" class="bpmn-diagram-container"></div>
                            </div>
                            <div>
                                <div style="font-weight: 600; margin-bottom: 10px; color: #666; font-size: 13px;">New Value</div>
                                <div id="newDiagramContainer" class="bpmn-diagram-container"></div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <div class="details-count" id="detailsCount">0 records</div>
                    <button id="closeDetailsBtn" class="btn-close">Close</button>
                </div>
            </div>
        </div>
    `;

    initializeActivityLogs();
    
    // Force scrollbar to appear by ensuring content-area has proper height
    setTimeout(() => {
        const contentArea = document.querySelector('.content-area');
        if (contentArea) {
            // Force recalculation of height
            contentArea.style.height = '';
            contentArea.style.maxHeight = '';
            contentArea.style.overflowY = 'auto';
            
            // Ensure parent has constrained height
            const adminMainContent = contentArea.closest('.admin-main-content');
            if (adminMainContent) {
                adminMainContent.style.height = `calc(100vh - 70px)`;
                adminMainContent.style.maxHeight = `calc(100vh - 70px)`;
                adminMainContent.style.overflow = 'hidden';
            }
        }
    }, 100);
}

function initializeActivityLogs() {
    loadFilterOptions();
    loadActivityLogs();
    setupActivityLogsEventListeners();
}

function setupActivityLogsEventListeners() {
    // Check if activity logs elements exist before adding listeners
    const filterSetting = document.getElementById('filterSetting');
    const filterComponent = document.getElementById('filterComponent');
    const filterChangeType = document.getElementById('filterChangeType');
    const filterUserName = document.getElementById('filterUserName');
    const searchDetails = document.getElementById('searchDetails');
    const fromDate = document.getElementById('fromDate');
    const toDate = document.getElementById('toDate');
    const searchBtn = document.getElementById('searchBtn');
    const clearBtn = document.getElementById('clearBtn');
    
    // If elements don't exist, return early (activity logs not displayed)
    if (!filterSetting || !filterComponent || !filterChangeType || !filterUserName || 
        !searchDetails || !fromDate || !toDate || !searchBtn || !clearBtn) {
        return;
    }
    
    // Filter change listeners
    filterSetting.addEventListener('change', () => {
        updateComponentFilter();
        loadActivityLogs();
    });
    filterComponent.addEventListener('change', loadActivityLogs);
    filterChangeType.addEventListener('change', loadActivityLogs);
    filterUserName.addEventListener('change', loadActivityLogs);
    searchDetails.addEventListener('input', debounce(loadActivityLogs, 500));
    fromDate.addEventListener('change', loadActivityLogs);
    toDate.addEventListener('change', loadActivityLogs);

    // Action buttons
    searchBtn.addEventListener('click', loadActivityLogs);
    clearBtn.addEventListener('click', clearFilters);
    
    const exportBtn = document.getElementById('exportBtn');
    if (exportBtn) {
        exportBtn.addEventListener('click', exportToCSV);
    }

    // Modal
    const closeModalBtn = document.getElementById('closeModalBtn');
    const closeDetailsBtn = document.getElementById('closeDetailsBtn');
    const detailsModal = document.getElementById('detailsModal');
    
    if (closeModalBtn) {
        closeModalBtn.addEventListener('click', closeDetailsModal);
    }
    if (closeDetailsBtn) {
        closeDetailsBtn.addEventListener('click', closeDetailsModal);
    }
    
    // Close modal when clicking outside
    if (detailsModal) {
        detailsModal.addEventListener('click', (e) => {
            if (e.target.id === 'detailsModal') {
                closeDetailsModal();
            }
        });
    }
}

async function loadFilterOptions() {
    try {
        const response = await fetch('/admin/api/activity-logs/filters');
        if (!response.ok) {
            throw new Error('Failed to load filter options');
        }
        const data = await response.json();

        // Populate Settings dropdown
        const settingSelect = document.getElementById('filterSetting');
        data.settings.forEach(setting => {
            const option = document.createElement('option');
            option.value = setting;
            option.textContent = setting;
            settingSelect.appendChild(option);
        });

        // Populate Components dropdown
        const componentSelect = document.getElementById('filterComponent');
        data.components.forEach(component => {
            const option = document.createElement('option');
            option.value = component;
            option.textContent = component;
            componentSelect.appendChild(option);
        });

        // Populate Change Types dropdown
        const changeTypeSelect = document.getElementById('filterChangeType');
        data.changeTypes.forEach(changeType => {
            const option = document.createElement('option');
            option.value = changeType;
            option.textContent = changeType;
            changeTypeSelect.appendChild(option);
        });

        // Populate Users dropdown
        const userSelect = document.getElementById('filterUserName');
        data.users.forEach(user => {
            const option = document.createElement('option');
            option.value = user.id;
            option.textContent = user.name;
            option.dataset.email = user.email;
            userSelect.appendChild(option);
        });

    } catch (error) {
        console.error('Error loading filter options:', error);
    }
}

function updateComponentFilter() {
    // When setting changes, filter components (if needed)
    // For now, just reload components
    loadFilterOptions();
}

async function loadActivityLogs() {
    const tbody = document.getElementById('logsTableBody');
    const loadingText = window.I18n ? window.I18n.t('adminPanel.common.loadingActivityLogs') : 'Loading activity logs...';
    tbody.innerHTML = `<tr><td colspan="7" class="loading-message">${loadingText}</td></tr>`;

    try {
        const params = buildFilterParams();
        const response = await fetch(`/admin/api/activity-logs?${params}`);
        if (!response.ok) {
            throw new Error('Failed to load activity logs');
        }
        const data = await response.json();

        totalRecords = data.totalCount || 0;
        updateRecordsCount();

        if (data.logs && data.logs.length > 0) {
            renderLogsTable(data.logs);
        } else {
            tbody.innerHTML = '<tr><td colspan="7" class="no-data">' + (typeof adminT === 'function' ? adminT('adminPanel.common.noActivityLogsFound', 'No activity logs found') : 'No activity logs found') + '</td></tr>';
        }
    } catch (error) {
        console.error('Error loading activity logs:', error);
        tbody.innerHTML = '<tr><td colspan="7" class="error-message">' + (typeof adminT === 'function' ? adminT('adminPanel.common.errorLoadingActivityLogs', 'Error loading activity logs. Please try again.') : 'Error loading activity logs. Please try again.') + '</td></tr>';
    }
}

function buildFilterParams() {
    const params = new URLSearchParams();
    
    const setting = document.getElementById('filterSetting').value;
    if (setting) params.append('setting', setting);
    
    const component = document.getElementById('filterComponent').value;
    if (component) params.append('component', component);
    
    const changeType = document.getElementById('filterChangeType').value;
    if (changeType) params.append('changeType', changeType);
    
    const userName = document.getElementById('filterUserName').value;
    if (userName) {
        const userOption = document.getElementById('filterUserName').options[document.getElementById('filterUserName').selectedIndex];
        params.append('userEmail', userOption.dataset.email || '');
    }
    
    const searchDetails = document.getElementById('searchDetails').value;
    if (searchDetails) params.append('searchDetails', searchDetails);
    
    const fromDate = document.getElementById('fromDate').value;
    if (fromDate) {
        // Convert to ISO format
        const date = new Date(fromDate);
        params.append('fromDate', date.toISOString().slice(0, 19));
    }
    
    const toDate = document.getElementById('toDate').value;
    if (toDate) {
        const date = new Date(toDate);
        params.append('toDate', date.toISOString().slice(0, 19));
    }
    
    params.append('offset', currentPage * pageSize);
    params.append('limit', pageSize);
    
    return params.toString();
}

function renderLogsTable(logs) {
    const tbody = document.getElementById('logsTableBody');
    tbody.innerHTML = '';

    logs.forEach((log, index) => {
        const row = document.createElement('tr');
        const sr = (currentPage * pageSize) + index + 1;
        
        const timestamp = log.timestamp ? formatTimestamp(log.timestamp) : '';
        const hasDetails = log.changeType !== 'Other Actions';
        
        row.innerHTML = `
            <td>${sr}</td>
            <td>${escapeHtml(log.setting || '')}</td>
            <td>${escapeHtml(log.component || '')}</td>
            <td>
                <a href="#" class="user-link" data-user-id="${log.userId}">${escapeHtml(log.userName || '')}</a><br>
                <a href="#" class="user-email-link">${escapeHtml(log.userEmail || '')}</a>
            </td>
            <td>${escapeHtml(log.changeType || '')}</td>
            <td>${timestamp}</td>
            <td>
                ${hasDetails ? `<a href="#" class="details-link" data-log-id="${log.id}">Details</a>` : ''}
            </td>
        `;
        
        tbody.appendChild(row);
    });

    // Add click handlers for details links
    document.querySelectorAll('.details-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const logId = e.target.closest('.details-link').dataset.logId;
            showDetailsModal(logId);
        });
    });
}

function formatTimestamp(timestamp) {
    if (!timestamp) return '';
    try {
        const date = new Date(timestamp);
        const day = String(date.getDate()).padStart(2, '0');
        const month = date.toLocaleString('en-US', { month: 'short' });
        const year = date.getFullYear();
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        return `${day}-${month}-${year} ${hours}:${minutes}:${seconds}`;
    } catch (e) {
        return timestamp;
    }
}

async function showDetailsModal(logId) {
    try {
        const response = await fetch(`/admin/api/activity-logs/${logId}/details`);
        if (!response.ok) {
            throw new Error('Failed to load details');
        }
        const data = await response.json();

        const tbody = document.getElementById('detailsTableBody');
        tbody.innerHTML = '';
        
        // Hide diagram section initially
        const diagramSection = document.getElementById('workflowDiagramSection');
        diagramSection.style.display = 'none';
        const oldDiagramContainer = document.getElementById('oldDiagramContainer');
        const newDiagramContainer = document.getElementById('newDiagramContainer');
        oldDiagramContainer.innerHTML = '';
        newDiagramContainer.innerHTML = '';

        let workflowDiagramDetail = null;

        if (data.details && data.details.length > 0) {
            data.details.forEach(detail => {
                const fieldName = detail.fieldName || '';
                const isWorkflowDiagram = fieldName === 'Workflow Diagram:';
                
                if (isWorkflowDiagram) {
                    // Store diagram detail for separate rendering below table
                    workflowDiagramDetail = detail;
                } else {
                    // Regular text fields - add to table
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td>${escapeHtml(fieldName)}</td>
                        <td>${escapeHtml(detail.oldValue || '')}</td>
                        <td>${escapeHtml(detail.newValue || '')}</td>
                    `;
                    tbody.appendChild(row);
                }
            });
            
            // Render workflow diagrams in separate section if present
            if (workflowDiagramDetail) {
                diagramSection.style.display = 'block';
                
                // Render old diagram
                if (workflowDiagramDetail.oldValue && workflowDiagramDetail.oldValue.startsWith('DIAGRAM_REF:')) {
                    const processDefId = workflowDiagramDetail.oldValue.replace('DIAGRAM_REF:', '');
                    renderBpmnDiagram(oldDiagramContainer, processDefId);
                } else if (workflowDiagramDetail.oldValue) {
                    oldDiagramContainer.innerHTML = '<div class="empty-value" style="padding: 20px; text-align: center; color: #999;">—</div>';
                } else {
                    oldDiagramContainer.innerHTML = '<div class="empty-value" style="padding: 20px; text-align: center; color: #999;">—</div>';
                }
                
                // Render new diagram
                if (workflowDiagramDetail.newValue && workflowDiagramDetail.newValue.startsWith('DIAGRAM_REF:')) {
                    const processDefId = workflowDiagramDetail.newValue.replace('DIAGRAM_REF:', '');
                    renderBpmnDiagram(newDiagramContainer, processDefId);
                } else if (workflowDiagramDetail.newValue) {
                    newDiagramContainer.innerHTML = '<div class="empty-value" style="padding: 20px; text-align: center; color: #999;">—</div>';
                } else {
                    newDiagramContainer.innerHTML = '<div class="empty-value" style="padding: 20px; text-align: center; color: #999;">—</div>';
                }
            }
        } else {
            tbody.innerHTML = '<tr><td colspan="3" class="no-data">' + (typeof adminT === 'function' ? adminT('adminPanel.common.noDetailsAvailable', 'No details available') : 'No details available') + '</td></tr>';
        }

        document.getElementById('detailsCount').textContent = `${data.count || 0} record${data.count !== 1 ? 's' : ''}`;
        document.getElementById('detailsModal').style.display = 'flex';
    } catch (error) {
        console.error('Error loading details:', error);
        const msg = typeof adminT === 'function' ? adminT('adminPanel.common.failedToLoadDetails', 'Failed to load details. Please try again.') : 'Failed to load details. Please try again.';
        if (window.showAdminNotification) window.showAdminNotification(msg, 'error');
    }
}

function closeDetailsModal() {
    document.getElementById('detailsModal').style.display = 'none';
}

function clearFilters() {
    document.getElementById('filterSetting').value = '';
    document.getElementById('filterComponent').value = '';
    document.getElementById('filterChangeType').value = '';
    document.getElementById('filterUserName').value = '';
    document.getElementById('searchDetails').value = '';
    document.getElementById('fromDate').value = '';
    document.getElementById('toDate').value = '';
    currentPage = 0;
    loadActivityLogs();
}

function exportToCSV() {
    const params = buildFilterParams();
    window.location.href = `/admin/api/activity-logs/export?${params}`;
}

function updateRecordsCount() {
    const recTemplate = typeof adminT === 'function' ? adminT('adminPanel.staticPageEditor.recordsCount', '{count} records') : '{count} records';
    document.getElementById('recordsCount').textContent = recTemplate.replace('{count}', String(totalRecords));
}

async function renderBpmnDiagram(container, processDefId) {
    try {
        // Create canvas container
        container.innerHTML = `
            <div class="bpmn-viewer-container" style="min-height: 400px; border: 1px solid #ddd; background: #f9f9f9; position: relative; border-radius: 4px;">
                <div class="bpmn-canvas" style="width: 100%; height: 100%; min-height: 400px;"></div>
                <div class="bpmn-loading" style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: #666; z-index: 10;">
                    <i class="fas fa-spinner fa-spin"></i> Loading diagram...
                </div>
            </div>
        `;
        
        const canvas = container.querySelector('.bpmn-canvas');
        const loading = container.querySelector('.bpmn-loading');
        
        if (!canvas) return;
        
        // Load BPMN XML
        const response = await fetch(`/api/process_definitions/${processDefId}/bpmn`);
        if (!response.ok) {
            throw new Error('Failed to load BPMN diagram');
        }
        
        const data = await response.json();
        if (!data.xml) {
            throw new Error('No BPMN XML found');
        }
        
        // Load BPMN.js viewer library if not already loaded
        if (typeof BpmnJS === 'undefined') {
            await loadBpmnViewerLibrary();
        }
        
        // Initialize BPMN viewer
        const viewer = new BpmnJS({ 
            container: canvas,
            width: '100%',
            height: '100%'
        });
        
        await viewer.importXML(data.xml);
        
        // Fit to viewport
        const canvasInstance = viewer.get('canvas');
        canvasInstance.zoom('fit-viewport');
        
        // Hide loading indicator
        if (loading) {
            loading.style.display = 'none';
        }
        
    } catch (error) {
        console.error('Error rendering BPMN diagram:', error);
        container.innerHTML = `
            <div style="padding: 20px; text-align: center; color: #dc2626; border: 1px solid #ddd; border-radius: 4px;">
                <i class="fas fa-exclamation-triangle"></i> Failed to load diagram
            </div>
        `;
    }
}

function loadBpmnViewerLibrary() {
    return new Promise((resolve, reject) => {
        if (typeof BpmnJS !== 'undefined') {
            resolve();
            return;
        }
        
        // Load BPMN.js from CDN
        const script = document.createElement('script');
        script.src = 'https://unpkg.com/bpmn-js@14.0.0/dist/bpmn-viewer.production.min.js';
        script.onload = resolve;
        script.onerror = reject;
        document.head.appendChild(script);
    });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}
