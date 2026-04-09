// Live search with debounce and local pagination per group
(function() {
    function setupLiveSearch() {
        const input = document.querySelector('.search-input');
        if (!input) return false;

        // Listen for custom search events from header
        document.addEventListener('searchQuery', (e) => {
            if (input && e.detail && e.detail.query) {
                input.value = e.detail.query;
                input.focus();
                search(e.detail.query);
            }
        });

    const container = document.createElement('div');
    container.className = 'live-search-results';
    input.parentElement.appendChild(container);
    container.style.display = 'none';

    // Add loading indicator
    const loadingIndicator = document.createElement('div');
    loadingIndicator.className = 'search-loading';
    loadingIndicator.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading...';
    loadingIndicator.style.display = 'none';
    container.appendChild(loadingIndicator);

    const friendly = {
        'org_unit': 'Organization Units',
        'people': 'People',
        'role': 'Roles',
        'status': 'Statuses',
        'system': 'Systems',
        'dataset': 'Data Sets',
        'interface': 'Interfaces',
        'glossary': 'Glossary',
        'process': 'Processes',
        'project': 'Projects',
        'product': 'Products',
        'policy': 'Policies',
        'legal_entity': 'Legal Entities',
        'legal': 'Legal Entities',
        'attribute': 'Attributes',
        'geography': 'Geography',
        'regulation': 'Regulations',
        'regulator': 'Regulators',
        'regulatory_theme': 'Regulatory Themes',
        'regulatory-theme': 'Regulatory Themes',
        'business_area': 'Business Areas',
        'capability': 'Capabilities',
        'client': 'Clients',
        'committee': 'Committees',
        'dataquality': 'Data Quality',
        'data_quality': 'Data Quality'
    };

    let debounceTimer = null;
    let lastResponse = {};
    let hasSearched = false;
    let isLoading = false;
    let lastSearchQuery = '';
    const pageState = {}; // index -> {page: 1, pageSize: 3}

    function debounce(fn, delay) {
        return function(...args) {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => fn.apply(this, args), delay);
        }
    }

    function render() {
        // Clear previous results but keep loading indicator and error messages
        const results = container.querySelectorAll('.search-group, .search-empty, .quick-search-table-wrapper');
        results.forEach(el => el.remove());

        const keys = Object.keys(lastResponse).filter(k => {
            // Filter out error keys and ensure we have valid data
            const group = lastResponse[k];
            return group && typeof group === 'object' && !group.error;
        });

        if (keys.length === 0) {
            if (hasSearched) {
                container.style.display = 'block';
                const empty = document.createElement('div');
                empty.className = 'search-empty';
                empty.innerHTML = '<i class="fas fa-search"></i> No results found.';
                container.appendChild(empty);
            } else {
                container.style.display = 'none';
            }
            return;
        }

        container.style.display = 'block';

        // Create a single unified table for all results
        const tableWrapper = document.createElement('div');
        tableWrapper.className = 'quick-search-table-wrapper';
        
        const table = document.createElement('table');
        table.className = 'quick-search-table';
        
        // Create table header with sortable columns
        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        
        const nameHeader = document.createElement('th');
        nameHeader.className = 'sortable';
        nameHeader.setAttribute('data-column', 'name');
        nameHeader.setAttribute('data-sort', 'none');
        nameHeader.innerHTML = '<div class="th-content"><span>Name</span><i class="fas fa-sort"></i></div>';
        headerRow.appendChild(nameHeader);
        
        const refHeader = document.createElement('th');
        refHeader.className = 'sortable';
        refHeader.setAttribute('data-column', 'ref');
        refHeader.setAttribute('data-sort', 'none');
        refHeader.innerHTML = '<div class="th-content"><span>Ref</span><i class="fas fa-sort"></i></div>';
        headerRow.appendChild(refHeader);
        
        const typeHeader = document.createElement('th');
        typeHeader.className = 'sortable';
        typeHeader.setAttribute('data-column', 'type');
        typeHeader.setAttribute('data-sort', 'none');
        typeHeader.innerHTML = '<div class="th-content"><span>Type</span><i class="fas fa-sort"></i></div>';
        headerRow.appendChild(typeHeader);
        
        thead.appendChild(headerRow);
        table.appendChild(thead);
        
        const tbody = document.createElement('tbody');
        
        // Collect all results from all modules into a flat array
        const allResults = [];
        
        keys.forEach(index => {
            const group = lastResponse[index];
            
            // Skip if group has error
            if (group.error) {
                console.warn(`Error in group ${index}:`, group.error);
                return;
            }
            
            const total = group.total || 0;
            const data = group.data || [];
            
            // Validate data is an array
            if (!Array.isArray(data)) {
                console.warn(`Invalid data format for group ${index}:`, data);
                return;
            }
            
            if (total === 0 && data.length === 0) return;

            // Use all data for table (no pagination for now, can be added later if needed)
            // Limit to reasonable number to avoid performance issues
            const maxItems = 100;
            const slice = data.slice(0, maxItems);
            
            // Add each item to allResults with module index
            slice.forEach(item => {
                if (item && typeof item === 'object') {
                    allResults.push({
                        item: item,
                        moduleIndex: index,
                        moduleName: friendly[index] || index
                    });
                }
            });
        });

        // Only create table if we have results (empty state is handled after filtering below)
        if (allResults.length === 0) {
            if (hasSearched) {
                container.style.display = 'block';
                const empty = document.createElement('div');
                empty.className = 'search-empty';
                empty.innerHTML = '<i class="fas fa-search"></i> No results found.';
                container.appendChild(empty);
            }
            return;
        }

        // Helper function to get item name (needed for filtering)
        function getItemName(item, index) {
            if (index === 'org_unit') return item.Name || '';
            if (index === 'people') {
                const fn = item.First_Name || '';
                const ln = item.Last_Name || '';
                return (fn + ' ' + ln).trim();
            }
            if (index === 'role') {
                const role = item.Role || item.primaryname || '';
                const fullName = item.FullName || '';
                return fullName ? `${role} - ${fullName}` : role;
            }
            if (index === 'status') return item.primaryname || '';
            if (index === 'system') return item.Name || '';
            if (index === 'dataset') return item.PrimaryName || item.primaryname || item.Name || '';
            if (index === 'interface') return item.Name || '';
            if (index === 'glossary') return item.Name || '';
            if (index === 'process') return item.primaryname || item.PrimaryName || item.Name || '';
            if (index === 'project') return item.primaryname || item.PrimaryName || item.Name || '';
            if (index === 'product') return item.primaryname || item.PrimaryName || item.Name || '';
            if (index === 'policy') return item.PrimaryName || item.primaryname || item.Name || '';
            if (index === 'business_area' || index === 'business area' || index === 'business areas') {
                return item.PrimaryName || item.primaryname || item.Name || '';
            }
            if (index === 'legal_entity' || index === 'legal entity' || index === 'legal') {
                return item.ShortName || item.shortname || item.PrimaryName || item.primaryname || '';
            }
            if (index === 'committee' || index === 'committees') return item.PrimaryName || item.primaryname || item.Name || '';
            if (index === 'client' || index === 'clients') return item.PrimaryName || item.primaryname || item.Name || '';
            if (index === 'attribute' || index === 'attributes') return item.PrimaryName || item.primaryname || item.Name || '';
            if (index === 'geography') return item.PrimaryName || item.Name || '';
            if (index === 'regulation' || index === 'regulations') return item.Name || item.primaryName || item.PrimaryName || '';
            if (index === 'regulator' || index === 'regulators') return item.PrimaryName || item.Name || '';
            if (index === 'regulatory_theme' || index === 'regulatory-theme' || index === 'regulatory themes') {
                return item.PrimaryName || item.Name || '';
            }
            if (index === 'dataquality' || index === 'data_quality') {
                return item.primaryname || item.PrimaryName || item.Name || '';
            }
            return item.Name || item.primaryname || item.PrimaryName || '';
        }

        // Helper function to get item ref
        function getItemRef(item, index) {
            if (index === 'dataset') return item.RefNumber || '';
            if (index === 'glossary') return item.Ref_Number || '';
            if (index === 'process') return item.refnumber || item.RefNumber || '';
            if (index === 'project') return item.refnumber || item.RefNumber || '';
            if (index === 'product') return item.refnumber || item.RefNumber || '';
            if (index === 'policy') return item.refNumber || item.RefNumber || '';
            if (index === 'interface') return item.Ref_number || item.RefNumber || '';
            if (index === 'system') return item.AssetID || '';
            if (index === 'dataquality' || index === 'data_quality') {
                return item.ref || item.Ref || item.RefNumber || '';
            }
            return item.RefNumber || item.Ref_Number || item.refnumber || item.refNumber || '';
        }

        // Helper function to get icon class
        function getIconClass(index) {
            const iconMap = {
                'org_unit': 'fa-building',
                'people': 'fa-user',
                'role': 'fa-user-tag',
                'status': 'fa-info-circle',
                'system': 'fa-desktop',
                'dataset': 'fa-database',
                'interface': 'fa-plug',
                'glossary': 'fa-book',
                'process': 'fa-cogs',
                'project': 'fa-project-diagram',
                'product': 'fa-box',
                'policy': 'fa-file-alt',
                'business_area': 'fa-sitemap',
                'business area': 'fa-sitemap',
                'business areas': 'fa-sitemap',
                'legal_entity': 'fa-building',
                'legal': 'fa-building',
                'committee': 'fa-users',
                'committees': 'fa-users',
                'client': 'fa-user-friends',
                'clients': 'fa-user-friends',
                'attribute': 'fa-tag',
                'attributes': 'fa-tag',
                'geography': 'fa-globe',
                'regulation': 'fa-balance-scale',
                'regulations': 'fa-balance-scale',
                'regulator': 'fa-gavel',
                'regulators': 'fa-gavel',
                'regulatory_theme': 'fa-themeisle',
                'regulatory-theme': 'fa-themeisle',
                'regulatory themes': 'fa-themeisle',
                'dataquality': 'fa-check-circle',
                'data_quality': 'fa-check-circle'
            };
            return iconMap[index] || 'fa-circle';
        }

        function buildViewUrl(index, item) {
            if (!item || item.id == null) return null;
            switch (index) {
                case 'system':
                case 'systems':
                    return `/view/system/${encodeURIComponent(item.id)}`;
                case 'dataset':
                case 'datasets':
                    return `/view/dataset/${encodeURIComponent(item.id)}`;
                case 'interface':
                case 'interfaces':
                case 'system-interface':
                    return `/view/system-interface/${encodeURIComponent(item.id)}`;
                case 'glossary':
                case 'glossaries':
                    return `/view/glossary/${encodeURIComponent(item.id)}`;
                case 'people':
                    return `/view/people/${encodeURIComponent(item.id)}`;
                case 'org_unit':
                    return `/view/org-unit/${encodeURIComponent(item.id)}`;
                case 'role':
                    return `/view/role/${encodeURIComponent(item.id)}`;
                case 'process':
                case 'processes':
                    return `/view/process/${encodeURIComponent(item.id)}`;
                case 'project':
                case 'projects':
                    return `/view/project/${encodeURIComponent(item.id)}`;
                case 'product':
                case 'products':
                    return `/view/product/${encodeURIComponent(item.id)}`;
                case 'policy':
                case 'policies':
                    return `/view/policy/${encodeURIComponent(item.id)}`;
                case 'capability':
                case 'capabilities':
                    return `/view/capability/${encodeURIComponent(item.id)}`;
                case 'business_area':
                case 'business area':
                case 'business areas':
                    return `/view/business-area/business-area.html?id=${encodeURIComponent(item.id)}`;
                case 'legal_entity':
                case 'legal':
                    return `/view/LegalEntity/legal-entity.html?id=${encodeURIComponent(item.id)}`;
                case 'committee':
                case 'committees':
                    return `/view/committee/${encodeURIComponent(item.id)}`;
                case 'client':
                case 'clients':
                    return `/view/client/client.html?id=${encodeURIComponent(item.id)}`;
                case 'attribute':
                case 'attributes':
                    return `/view/attribute/${encodeURIComponent(item.id)}`;
                case 'dataquality':
                case 'data_quality':
                    return `/view/data-quality/data-quality.html?id=${encodeURIComponent(item.id)}`;
                case 'geography':
                    return `/view/geography/geography.html?id=${encodeURIComponent(item.id)}`;
                case 'regulation':
                case 'regulations':
                    return `/view/regulation/regulation.html?id=${encodeURIComponent(item.id)}`;
                case 'regulator':
                case 'regulators':
                    return `/view/regulator/regulator.html?id=${encodeURIComponent(item.id)}`;
                case 'regulatory_theme':
                case 'regulatory-theme':
                case 'regulatory themes':
                    return `/view/regulatory-theme/regulatory-theme.html?id=${encodeURIComponent(item.id)}`;
                default:
                    return null;
            }
        }

        // Filter to only results that contain the search term in name, ref, or type (so irrelevant hits are hidden)
        const searchTerm = (lastSearchQuery || '').trim().toLowerCase();
        const displayResults = searchTerm
            ? allResults.filter(r => {
                const name = getItemName(r.item, r.moduleIndex);
                const ref = getItemRef(r.item, r.moduleIndex);
                const type = (r.moduleName || '').toLowerCase();
                return name.toLowerCase().includes(searchTerm) || ref.toLowerCase().includes(searchTerm) || type.includes(searchTerm);
            })
            : allResults;

        if (displayResults.length === 0) {
            container.style.display = 'block';
            const empty = document.createElement('div');
            empty.className = 'search-empty';
            empty.innerHTML = '<i class="fas fa-search"></i> No results found.';
            container.appendChild(empty);
            return;
        }

        // Create table rows for filtered results
        displayResults.forEach(result => {
            const { item, moduleIndex, moduleName } = result;
            const name = getItemName(item, moduleIndex);
            const ref = getItemRef(item, moduleIndex);

            const row = document.createElement('tr');
            row.className = 'quick-search-row';
            row.setAttribute('data-index', moduleIndex);
            row.setAttribute('data-id', item.id || item.ID || '');
            row.setAttribute('data-name', name);
            row.setAttribute('data-ref', ref);
            row.setAttribute('data-type', moduleName);

            // Add click handler to navigate
            row.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                try {
                    const url = buildViewUrl(moduleIndex, item);
                    if (url) {
                        window.location.href = url;
                        return;
                    }
                } catch (err) {
                    console.warn('Error building view URL:', err);
                }
            });

            // Name cell with icon
            const nameCell = document.createElement('td');
            nameCell.setAttribute('data-column', 'name');
            const iconClass = getIconClass(moduleIndex);
            nameCell.innerHTML = `<i class="fas ${iconClass}"></i> ${name}`;
            row.appendChild(nameCell);

            // Ref cell
            const refCell = document.createElement('td');
            refCell.setAttribute('data-column', 'ref');
            refCell.textContent = getItemRef(item, moduleIndex);
            row.appendChild(refCell);

            // Type cell
            const typeCell = document.createElement('td');
            typeCell.setAttribute('data-column', 'type');
            typeCell.textContent = moduleName;
            row.appendChild(typeCell);

            tbody.appendChild(row);
        });

        table.appendChild(tbody);
        tableWrapper.appendChild(table);
        container.appendChild(tableWrapper);
        
        // Initialize sorting after table is created
        initQuickSearchSorting(table);
    }

    // Initialize sorting for Quick Search table
    function initQuickSearchSorting(table) {
        if (!table) return;
        
        const headers = table.querySelectorAll('th.sortable');
        headers.forEach(header => {
            header.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                
                const column = header.getAttribute('data-column');
                const currentOrder = header.getAttribute('data-sort') || 'none';
                const newOrder = currentOrder === 'asc' ? 'desc' : 'asc';

                // Reset all headers
                headers.forEach(h => {
                    h.setAttribute('data-sort', 'none');
                    const iconEl = h.querySelector('i');
                    if (iconEl) iconEl.className = 'fas fa-sort';
                });

                // Set current header
                header.setAttribute('data-sort', newOrder);
                const icon = header.querySelector('i');
                if (icon) {
                    icon.className = newOrder === 'asc' ? 'fas fa-sort-up' : 'fas fa-sort-down';
                }

                // Sort the table
                sortQuickSearchTable(table, column, newOrder);
            });
        });
    }

    // Sort Quick Search table data
    function sortQuickSearchTable(table, column, order) {
        if (!table) return;
        
        const tbody = table.querySelector('tbody');
        if (!tbody) return;
        
        const rows = Array.from(tbody.querySelectorAll('tr'));
        
        rows.sort((a, b) => {
            const aCell = a.querySelector(`td[data-column="${column}"]`);
            const bCell = b.querySelector(`td[data-column="${column}"]`);
            
            if (!aCell || !bCell) return 0;
            
            // Get text content (strip HTML for name column)
            let aVal = aCell.textContent.trim() || '';
            let bVal = bCell.textContent.trim() || '';
            
            // For name column, also check data attribute (cleaner)
            if (column === 'name') {
                aVal = a.getAttribute('data-name') || aVal;
                bVal = b.getAttribute('data-name') || bVal;
            }
            
            // For ref column, check data attribute
            if (column === 'ref') {
                aVal = a.getAttribute('data-ref') || aVal;
                bVal = b.getAttribute('data-ref') || bVal;
            }
            
            // For type column, check data attribute
            if (column === 'type') {
                aVal = a.getAttribute('data-type') || aVal;
                bVal = b.getAttribute('data-type') || bVal;
            }
            
            // String comparison (case-insensitive)
            aVal = aVal.toLowerCase();
            bVal = bVal.toLowerCase();
            
            if (order === 'asc') {
                return aVal.localeCompare(bVal);
            } else {
                return bVal.localeCompare(aVal);
            }
        });
        
        // Re-append sorted rows
        rows.forEach(row => tbody.appendChild(row));
    }

    let firstSearchDone = false;
    let forceSyncOnce = true;

    // Allow other pages to force the next search to sync Elasticsearch with MySQL
    window.addEventListener('forceSearchSync', () => {
        forceSyncOnce = true;
    });
    // Optional helper for imperative use
    window.BUDG = window.BUDG || {};
    window.BUDG.forceSearchSyncNext = function() { window.dispatchEvent(new CustomEvent('forceSearchSync')); };
    async function search(q) {
        if (!q) {
            lastResponse = {};
            hasSearched = false;
            lastSearchQuery = '';
            render();
            return;
        }

        // Prevent multiple simultaneous searches or duplicate searches
        if (isLoading || lastSearchQuery === q) return;

        try {
            isLoading = true;
            lastSearchQuery = q;
            loadingIndicator.style.display = 'block';

            // Clear any previous error messages
            const existingError = container.querySelector('.search-error');
            if (existingError) existingError.remove();

            // Determine sync parameter: sync on first search or when forced
            const sync = (forceSyncOnce || !firstSearchDone) ? 'true' : 'false';
            
            // Build URL with proper encoding
            const queryParams = new URLSearchParams({
                q: q.trim(),
                size: '50',
                sync: sync
            });
            
            const res = await fetch(`/api/search?${queryParams.toString()}`, {
                headers: { 
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                credentials: 'include' // Include credentials for authenticated requests
            });

            if (!res.ok) {
                // Try to get error message from response
                let errorMessage = 'Search failed';
                try {
                    const errorData = await res.json();
                    errorMessage = errorData.error || errorMessage;
                } catch (e) {
                    // If response is not JSON, use status text
                    errorMessage = res.statusText || errorMessage;
                }
                throw new Error(errorMessage);
            }

            const responseData = await res.json();
            
            // Handle error response from backend
            if (responseData.error) {
                throw new Error(responseData.error);
            }

            // Ensure response is a map/object
            if (typeof responseData !== 'object' || Array.isArray(responseData)) {
                console.warn('Unexpected response format:', responseData);
                lastResponse = {};
            } else {
                lastResponse = responseData;
            }

            // Reset paging state per new query
            Object.keys(lastResponse).forEach(k => {
                if (!pageState[k]) {
                    pageState[k] = { page: 1, pageSize: 3 };
                } else {
                    pageState[k].page = 1; // Reset to first page on new query
                }
            });
            
            hasSearched = true;
            render();
            firstSearchDone = true;
            if (forceSyncOnce) forceSyncOnce = false;
            
            // Ensure dropdown is visible after search completes (especially when triggered by Enter)
            if (Object.keys(lastResponse).length > 0 || hasSearched) {
                container.style.display = 'block';
            }
        } catch (e) {
            console.error('Search error:', e);
            hasSearched = true;
            lastResponse = {};
            lastSearchQuery = '';
            render();

            // Show user-friendly error message
            const errorMsg = document.createElement('div');
            errorMsg.className = 'search-error';
            errorMsg.innerHTML = `
                <i class="fas fa-exclamation-triangle"></i>
                <span>${escapeHtml(e.message || 'Error loading results. Please try again.')}</span>
            `;
            container.appendChild(errorMsg);
            
            // Ensure dropdown is visible even on error (to show error message)
            container.style.display = 'block';
        } finally {
            isLoading = false;
            loadingIndicator.style.display = 'none';
        }
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    const onInput = debounce((e) => {
        const v = e.target.value.trim();
        if (v.length === 0) {
            lastResponse = {};
            hasSearched = false;
            lastSearchQuery = '';
            render();
            container.style.display = 'none';
            loadingIndicator.style.display = 'none';
            return;
        }
        
        // Minimum search length check (optional - can be removed if not needed)
        if (v.length < 1) {
            return;
        }
        
        // Show dropdown immediately when user types (before results arrive)
        container.style.display = 'block';
        loadingIndicator.style.display = 'block';
        
        search(v);
    }, 300);

    input.addEventListener('input', onInput);

    // Show/hide on focus/blur
    input.addEventListener('focus', () => {
        // Show dropdown if there's text in input, even if no results yet
        const v = input.value.trim();
        if (v.length > 0 || Object.keys(lastResponse).length > 0) {
            container.style.display = 'block';
            // Show loading indicator if search is in progress
            if (isLoading) {
                loadingIndicator.style.display = 'block';
            }
        }
    });

    // Hide on clicking outside
    document.addEventListener('click', (e) => {
        const wrapper = input.parentElement;
        if (wrapper && !wrapper.contains(e.target)) {
            container.style.display = 'none';
        }
    });

    // Hide on Escape
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            container.style.display = 'none';
            input.blur();
        }
    });
        return true;
    }

    // Try immediately (in case header already present)
    if (setupLiveSearch()) return;

    // If header is injected later, wait for it
    window.addEventListener('headerReady', () => {
        setupLiveSearch();
    });
})();