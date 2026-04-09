// Tabs handling

function initCategoryTabs() {
    document.querySelectorAll('.category-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.category-tab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            document.title = `${tab.textContent.trim()} - BUDG`;
        });
    });
}

function initViewTabs() {
    document.querySelectorAll('.view-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.view-tab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            switchView(tab.getAttribute('data-view'));
        });
    });
}

function switchView(viewName) {
    const contentMain = document.querySelector('.content-main');
    const dataTableWrapper = document.querySelector('.data-table-wrapper');
    
    if (!contentMain) {
        return;
    }

    if (viewName === 'dashboard') {
        // Store the table wrapper HTML before clearing (if it exists and is visible)
        if (dataTableWrapper && dataTableWrapper.style.display !== 'none') {
            dataTableWrapper.setAttribute('data-stored-display', dataTableWrapper.style.display || 'block');
            dataTableWrapper.style.display = 'none';
        }
        
        // Get current category and render dashboard
        const category = getActiveCategoryWithFallback();
        if (category) {
            // Check if we have filtered data from search results
            // Access currentFilteredData from window or global scope
            const filteredData = (typeof window !== 'undefined' && window.currentFilteredData) || 
                                (typeof currentFilteredData !== 'undefined' ? currentFilteredData : null);
            const filteredCategory = (typeof window !== 'undefined' && window.currentFilteredCategory) || 
                                    (typeof currentFilteredCategory !== 'undefined' ? currentFilteredCategory : null);
            
            if (filteredData && filteredCategory === category && typeof updateDashboardWithFilteredData === 'function') {
                // Use filtered data from search
                updateDashboardWithFilteredData(category, filteredData);
            } else {
                // Use regular dashboard render (all data)
                renderDashboard(category);
            }
        } else {
            contentMain.innerHTML = `
                <div class="dashboard-container">
                    <div class="dashboard-message">
                        <i class="fas fa-info-circle"></i>
                        <p>Please select a category to view the dashboard.</p>
                    </div>
                </div>
            `;
        }
    } else if (viewName === 'list') {
        // Clear dashboard content from content-main
        // Find and remove any dashboard containers
        const dashboardContainers = contentMain.querySelectorAll('.dashboard-container');
        dashboardContainers.forEach(container => container.remove());
        
        // Destroy dashboard charts when switching back to list
        if (typeof destroyAllCharts === 'function') {
            destroyAllCharts();
        }
        
        // Show table wrapper - it should still exist in the DOM, just hidden
        if (dataTableWrapper) {
            dataTableWrapper.style.display = 'block';
        } else {
            // Recreate data-table-wrapper if it doesn't exist (shouldn't happen, but safety check)
            const tableWrapper = document.createElement('div');
            tableWrapper.className = 'data-table-wrapper';
            tableWrapper.innerHTML = `
                <table class="search-table">
                    <thead>
                        <tr>
                            <th class="sortable" data-column="ref"><div class="th-content"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                            <th class="sortable" data-column="name"><div class="th-content"><span>Name</span><i class="fas fa-sort"></i></div></th>
                            <th class="sortable" data-column="parent"><div class="th-content"><span>Parent</span><i class="fas fa-sort"></i></div></th>
                            <th data-column="description"><div class="th-content"><span>Description</span></div></th>
                            <th class="sortable" data-column="status"><div class="th-content"><span>Status</span><i class="fas fa-sort"></i></div></th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr class="no-data-row">
                            <td colspan="5" class="no-data-message">
                                <div class="no-data-content">
                                    <i class="fas fa-database"></i>
                                    <p>Select a category from the sidebar to view data</p>
                                </div>
                            </td>
                        </tr>
                    </tbody>
                </table>
            `;
            contentMain.appendChild(tableWrapper);
        }
        
        // Reload category data to show the list
        const category = getActiveCategoryWithFallback();
        if (category && typeof loadCategoryData === 'function') {
            loadCategoryData(category);
        }
    }
}

/**
 * Check if dashboard view is currently active
 * @returns {boolean} True if dashboard view is active
 */
function isDashboardViewActive() {
    const dashboardTab = document.querySelector('.view-tab[data-view="dashboard"]');
    return dashboardTab && dashboardTab.classList.contains('active');
}

/**
 * Reload dashboard if it's currently active
 * This should be called when category changes
 */
function reloadDashboardIfActive() {
    if (isDashboardViewActive()) {
        const category = getActiveCategoryWithFallback();
        if (category) {
            // Check if we have filtered data from search results
            // Access currentFilteredData from window or global scope
            const filteredData = (typeof window !== 'undefined' && window.currentFilteredData) || 
                                (typeof currentFilteredData !== 'undefined' ? currentFilteredData : null);
            const filteredCategory = (typeof window !== 'undefined' && window.currentFilteredCategory) || 
                                    (typeof currentFilteredCategory !== 'undefined' ? currentFilteredCategory : null);
            
            if (filteredData && filteredCategory === category && typeof updateDashboardWithFilteredData === 'function') {
                // Use filtered data from search
                updateDashboardWithFilteredData(category, filteredData);
            } else if (typeof renderDashboard === 'function') {
                // Use regular dashboard render (all data)
                renderDashboard(category);
            }
        }
    }
}


