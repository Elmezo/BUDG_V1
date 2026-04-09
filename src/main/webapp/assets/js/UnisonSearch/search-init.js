// Initialization and auto-load

async function autoLoadFirstModule() {
    // Get the first category item from category-sidebar-container
    const sidebarContainer = document.querySelector('.category-sidebar-container');
    let firstItem = null;
    
    if (sidebarContainer) {
        // Get the first .category-item within the container
        firstItem = sidebarContainer.querySelector('.category-item');
    }
    
    // Fallback: if no container or no item in container, use first category-item in document
    if (!firstItem) {
        const categoryItems = document.querySelectorAll('.category-item');
        if (categoryItems.length > 0) {
            firstItem = categoryItems[0];
        }
    }

    if (firstItem) {
        const categoryName = firstItem.getAttribute('data-category');

        if (categoryName) {
            // تعيين كفئة نشطة
            document.querySelectorAll('.category-item').forEach(item => item.classList.remove('active', 'current'));
            firstItem.classList.add('active');

            try {
                await loadCategoryData(categoryName);

                const dataTable = document.querySelector('.data-table-wrapper');
                if (dataTable) {
                    dataTable.style.display = 'block';
                    const noDataRow = dataTable.querySelector('.no-data-row');
                    if (noDataRow) {
                        noDataRow.style.display = 'none';
                    }
                }
            } catch (error) {
                // Silent fail
            }
        }
    } else {
        // Fallback to default categories (use first available)
        const defaultCategories = ['dataset', 'attribute', 'people', 'system', 'glossary'];
        for (const category of defaultCategories) {
            try {
                await loadCategoryData(category);

                const dataTable = document.querySelector('.data-table-wrapper');
                if (dataTable) {
                    dataTable.style.display = 'block';
                    const noDataRow = dataTable.querySelector('.no-data-row');
                    if (noDataRow) {
                        noDataRow.style.display = 'none';
                    }
                }
                break;
            } catch (error) {
                // Silent fail
            }
        }
    }
}

document.addEventListener('DOMContentLoaded', async function() {
    // Wait for i18n so facet.* and other keys resolve before building category tabs and filter UI
    await new Promise(function(resolve) {
        if (window.I18n && window.I18n.translations && Object.keys(window.I18n.translations).length > 0) {
            resolve();
            return;
        }
        if (window.i18nReadyPromise) {
            window.i18nReadyPromise.then(resolve).catch(function() { resolve(); });
            return;
        }
        window.addEventListener('i18nReady', function() { resolve(); }, { once: true });
        setTimeout(resolve, 3000);
    });

    tableContainer = document.querySelector('.data-table-wrapper');

    if (window.searchColumnControl && typeof window.searchColumnControl.loadUnisonDefaultsForColumns === 'function') {
        try {
            await window.searchColumnControl.loadUnisonDefaultsForColumns();
        } catch (e) {
            console.error('[INIT] CRITICAL: Failed to load UNISON_DEFAULTS:', e);
        }
    } else {
        console.error('[INIT] CRITICAL: searchColumnControl not available!');
    }

    if (typeof initializeColumnPreferencesFromDatabase === 'function') {
        try {
            await initializeColumnPreferencesFromDatabase();
        } catch (e) {
            console.error('[INIT] Failed to initialize column preferences:', e);
        }
    }

    await fetchFuzzySearchConfig();

    // Initialize functions only if they exist (defensive programming)
    if (typeof initSearchFunctionality === 'function') {
        initSearchFunctionality();
    } else {
        console.warn('[INIT] initSearchFunctionality not available yet');
    }
    
    if (typeof initCategoryTabs === 'function') {
        initCategoryTabs();
    }
    
    if (typeof initViewTabs === 'function') {
        initViewTabs();
    }
    
    if (typeof initTableSorting === 'function') {
        initTableSorting();
    }
    
    if (typeof initSearchSuggestions === 'function') {
        initSearchSuggestions();
    }
    
    if (typeof initHelpButton === 'function') {
        initHelpButton();
    }
    
    if (typeof initSettingsDropdown === 'function') {
        initSettingsDropdown();
    }
    
    // Initialize saved searches functionality (recent views, saved searches)
    if (typeof initSavedSearches === 'function') {
        initSavedSearches();
    }
    
    // Initialize history button
    if (typeof initHistoryButton === 'function') {
        initHistoryButton();
    }

    // Initialize Quick Link button (admin-configured shortcut)
    if (typeof initQuickLinkButton === 'function') {
        initQuickLinkButton();
    }

    // Initialize filter button
    if (typeof initFilterButton === 'function') {
        initFilterButton();
    }

    // Check if searchId parameter exists in URL
    const urlParams = new URLSearchParams(window.location.search);
    const searchId = urlParams.get('searchId');
    if (searchId) {
        // Load and execute saved search
        if (typeof runSavedSearch === 'function') {
            runSavedSearch(parseInt(searchId));
        }
    } else {
        // Auto-load first available module
        setTimeout(() => {
            console.log('[INIT] Auto-loading first module...');
            autoLoadFirstModule();
        }, 100);
    }

    // Preload common entity data for better performance
    setTimeout(() => {
        preloadCommonEntities();
    }, 500);
    
    // Listen for language changes and reload data
    window.addEventListener('languageChanged', async function(e) {
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            setTimeout(() => {
                window.I18n.applyTranslations();
            }, 100);
        }
        
        const activeCategory = typeof getActiveCategoryWithFallback === 'function' 
            ? getActiveCategoryWithFallback() 
            : null;
        
        if (activeCategory && typeof loadCategoryData === 'function') {
            try {
                await loadCategoryData(activeCategory);
            } catch (error) {
                console.error('[INIT] Error reloading data after language change:', error);
            }
        }
    });
});


