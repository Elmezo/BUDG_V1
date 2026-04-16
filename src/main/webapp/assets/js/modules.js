(function () {
    /** Set localStorage.setItem('budg-debug-modules','1') to silence these logs */
    const MODULES_DEBUG = (function () {
        try {
            return window.__BUDG_DEBUG__ === true ||
                (typeof localStorage !== 'undefined' && localStorage.getItem('budg-debug-modules') !== '0');
        } catch (e) {
            return true;
        }
    })();
    function logModules(step, detail) {
        if (!MODULES_DEBUG) return;
        if (detail !== undefined) {
            console.log('[MODULES][AddCategory]', step, detail);
        } else {
            console.log('[MODULES][AddCategory]', step);
        }
    }

    const btn = document.getElementById('module-container'); // Add Category button
    if (!btn) {
        console.warn('[MODULES] #module-container not found; modules menu disabled');
        return;
    }
    logModules('init: button bound', { id: btn.id, className: btn.className });
    let menuEl = null;
    let submenuEl = null; // Side submenu panel

    const cache = {
        apiLoaded: false,
        groups: [],
        modules: [],
        // helper maps
        modulesByGroup: new Map(),
        countByModuleName: new Map(),
        countByTableName: new Map(),
        // UNISON_DEFAULTS cache
        unisonDefaults: null,
        unisonDefaultsLoaded: false
    };


    // Icon mapping for module groups
    const groupIcons = {
        'Business & Change': 'fas fa-briefcase',
        'Catalog': 'fas fa-book-open',
        'Data & Technology': 'fas fa-layer-group',
        'Organizational': 'fas fa-sitemap',
        'Regulatory': 'fas fa-bookmark'
    };

    // Load UNISON_DEFAULTS from database
    async function loadUnisonDefaults() {
        if (cache.unisonDefaultsLoaded) {
            return cache.unisonDefaults;
        }
        
        try {
            const response = await fetch('/UnisonSearch/api/defaults');
            if (response.ok) {
                const data = await response.json();
                cache.unisonDefaults = data;
                cache.unisonDefaultsLoaded = true;
                return data;
            } else {
                console.error('[MODULES] Failed to load UNISON_DEFAULTS, status:', response.status);
            }
        } catch (e) {
            console.error('[MODULES] Exception loading UNISON_DEFAULTS:', e);
        }
        
        // Return null if failed
        cache.unisonDefaultsLoaded = true;
        console.warn('[MODULES] UNISON_DEFAULTS not available, will use empty defaults');
        return null;
    }

    // Normalize module/facet names to sidebar category keys
    function normalizeModuleKeyForCounts(raw) {
        const name = (raw || '').toString().trim();
        if (!name) return '';
        return moduleNameToCategoryKey(name); // reuse existing mapping (adds hyphens, etc.)
    }

    // Preload facet totals from backend into window.baselineCounts only (never categoryCounts).
    // When the user is authenticated, /api/module-row-count returns segment/cube-filtered totals
    // (aligned with Unison search denominators). Anonymous users get raw table counts.
    // Once search runs (facetCountSource === 'search'), baseline is not used for display until clear.
    async function preloadModuleRowCounts() {
        try {
            const res = await fetch('/api/module-row-count');
            if (!res.ok) return;
            const data = await res.json();
            if (typeof window === 'undefined') return;
            if (!window.baselineCounts) window.baselineCounts = new Map();

            const upsertCount = (rawKey, rawVal) => {
                const key = normalizeModuleKeyForCounts(rawKey);
                if (!key) return;
                const totalNum = Number(rawVal);
                if (isNaN(totalNum)) return;
                window.baselineCounts.set(key, { count: totalNum, total: totalNum, updatedAt: Date.now() });
            };

            if (Array.isArray(data)) {
                data.forEach(entry => {
                    const k = entry.module || entry.moduleName || entry.name || entry.id || entry.facet;
                    const v = entry.total || entry.count || entry.rowCount || entry.value || entry.rows;
                    upsertCount(k, v);
                });
            } else if (data && typeof data === 'object') {
                // If payload has rowCounts array, consume it
                if (Array.isArray(data.rowCounts)) {
                    data.rowCounts.forEach(entry => {
                        const k = entry.module || entry.moduleName || entry.name || entry.id || entry.facet;
                        const v = entry.total || entry.count || entry.rowCount || entry.value || entry.rows;
                        upsertCount(k, v);
                    });
                }
                // Support map-like responses: { SYSTEM: 58, GLOSSARY: 0, ... }
                Object.entries(data).forEach(([k, v]) => upsertCount(k, v));
                // Or nested list { items: [...] }
                if (Array.isArray(data.items)) {
                    data.items.forEach(entry => {
                        const k = entry.module || entry.moduleName || entry.name || entry.id || entry.facet;
                        const v = entry.total || entry.count || entry.rowCount || entry.value || entry.rows;
                        upsertCount(k, v);
                    });
                }
            }

            // Apply baseline to DOM only when search has not run yet (preload must not override search)
            if (typeof window.updateCategoryCount === 'function' && window.baselineCounts) {
                const visibleCanonicals = new Set();
                if (cache.unisonDefaults && cache.unisonDefaults.facets) {
                    cache.unisonDefaults.facets.forEach(facet => {
                        if (facet.visibility === true) {
                            const moduleName = window.facetIdToModuleName ? window.facetIdToModuleName(facet.id) : facet.id;
                            const canonical = (typeof window.canonicalCategoryKey === 'function')
                                ? window.canonicalCategoryKey(moduleName)
                                : moduleName.toLowerCase().replace(/\s+/g, '-').replace(/_/g, '-');
                            visibleCanonicals.add(canonical);
                        }
                    });
                }
                window.baselineCounts.forEach((val, key) => {
                    if (window.getFacetCountSource && window.getFacetCountSource() === 'search') return;
                    if (visibleCanonicals.size > 0 && !visibleCanonicals.has(key)) return;
                    const cVal = val && typeof val.count !== 'undefined' ? val.count : 0;
                    const tVal = val && typeof val.total !== 'undefined' ? val.total : cVal;
                    window.updateCategoryCount(key, cVal, tVal, false);
                });
            }
        } catch (e) {
            console.warn('[MODULES] Failed to preload module row counts:', e.message);
        }
    }
    // Expose to other scripts (search-input) to refresh totals on search/clear
    if (typeof window !== 'undefined') {
        window.preloadModuleRowCounts = preloadModuleRowCounts;
    }
    // Get visible modules from UNISON_DEFAULTS
    function getVisibleModulesFromDefaults() {
        if (!cache.unisonDefaults || !cache.unisonDefaults.facets) {
            return null;
        }
        
        const visibleModules = [];
        cache.unisonDefaults.facets.forEach(facet => {
            if (facet.visibility === true) {
                const moduleName = window.facetIdToModuleName ? 
                    window.facetIdToModuleName(facet.id) : 
                    facet.id;
                visibleModules.push({ name: moduleName, facetId: facet.id });
            }
        });
        
        return visibleModules.length > 0 ? visibleModules : null;
    }

    function tOr(key, fallback) {
        if (window.I18n && typeof window.I18n.t === 'function') {
            const s = window.I18n.t(key);
            if (s && s !== key) return s;
        }
        return fallback;
    }

    const GROUP_NAME_TO_SUFFIX = {
        'Business & Change': 'businessChange',
        'Catalog': 'catalog',
        'Data & Technology': 'dataTechnology',
        'Organizational': 'organizational',
        'Regulatory': 'regulatory'
    };

    function translateGroupDisplayName(primaryName) {
        const suffix = GROUP_NAME_TO_SUFFIX[primaryName];
        if (!suffix || !window.I18n || typeof window.I18n.t !== 'function') {
            return primaryName;
        }
        const k1 = 'group.' + suffix;
        let translated = window.I18n.t(k1);
        if (translated && translated !== k1) {
            return translated;
        }
        const k2 = 'label.group.' + suffix;
        translated = window.I18n.t(k2);
        if (translated && translated !== k2) {
            return translated;
        }
        return primaryName;
    }

    function ensureMenu() {
        if (menuEl) return menuEl;
        menuEl = document.createElement('div');
        menuEl.className = 'modules-menu';
        menuEl.style.display = 'none';
        menuEl.setAttribute('data-modules-menu-root', 'true');
        // Append to body so position:fixed + getBoundingClientRect() match the viewport.
        // If the menu stays inside .category-sidebar, backdrop-filter on the sidebar can create
        // a containing block and fixed coordinates are wrong (menu off-screen or invisible).
        document.body.appendChild(menuEl);
        return menuEl;
    }
    function ensureMenuFooter() {
        const menu = ensureMenu();
        let footer = menu.querySelector('.modules-menu-footer');
        if (footer) return footer;
        footer = document.createElement('div');
        footer.className = 'modules-menu-footer';
        const resetLabel = tOr('button.resetToDefault', 'Reset to Default');
        const saveLabel = tOr('button.saveLayout', 'Save Layout');
        footer.innerHTML = `
            <div class="menu-footer-actions">
                <button type="button" class="reset-btn"><i class="fas fa-undo"></i> ${resetLabel}</button>
                <button type="button" class="save-layout-btn"><i class="fas fa-save"></i> ${saveLabel}</button>
            </div>`;
        footer.querySelector('.reset-btn')?.addEventListener('click', (ev) => {
            ev.stopPropagation();
            resetToDefault();
        });
        footer.querySelector('.save-layout-btn')?.addEventListener('click', (ev) => {
            ev.stopPropagation();
            saveLayoutAsDefaults();
        });
        menu.appendChild(footer);
        return footer;
    }
    function ensureSubmenu() {
        if (submenuEl) return submenuEl;
        submenuEl = document.createElement('div');
        submenuEl.className = 'modules-submenu';
        submenuEl.style.display = 'none';
        submenuEl.setAttribute('data-modules-submenu-root', 'true');
        document.body.appendChild(submenuEl);
        return submenuEl;
    }

    // Selection state and rendering under sidebar
    const DEFAULT_MODULE_NAMES = new Set([
        'DATA SETS',
        'ATTRIBUTES',
        'SYSTEM',
        'GLOSSARY',
        'DATA QUALITY',
        'PEOPLE',
        'ROLE',
        'BUSINESS AREA',
        'LEGAL ENTITY',
        'CLIENT',
        'COMMITTEE',
        'ACTIVE TASKS',
        'CHANGE REQUESTS',
        'POLICY',
        'PROJECT',
        'PROCESS',
        'INTERFACE',
        'CAPABILITY',
        'ORG UNIT',
        'PRODUCT',
        'GEOGRAPHY',
        'REGULATION',
        'REGULATOR'
    ]);

    const DEFAULT_MODULE_ICONS = new Map([
        ['DATA SETS', 'fas fa-database'],
        ['ATTRIBUTES', 'fas fa-tags'],
        ['SYSTEM', 'fas fa-desktop'],
        ['GLOSSARY', 'fas fa-book'],
        ['DATA QUALITY', 'fas fa-check-circle'],
        ['PEOPLE', 'fas fa-users'],
        ['ROLE', 'fas fa-user-tie'],
        ['BUSINESS AREA', 'fas fa-building'],
        ['LEGAL ENTITY', 'fas fa-gavel'],
        ['CLIENT', 'fas fa-users'],
        ['COMMITTEE', 'fas fa-users-cog'],
        ['ACTIVE TASKS', 'fas fa-tasks'],
        ['CHANGE REQUESTS', 'fas fa-exchange-alt'],
        ['POLICY', 'fas fa-file-contract'],
        ['PROJECT', 'fas fa-project-diagram'],
        ['PROCESS', 'fas fa-sitemap'],
        ['INTERFACE', 'fas fa-plug'],
        ['CAPABILITY', 'fas fa-lightbulb'],
        ['ORG UNIT', 'fas fa-sitemap'],
        ['PRODUCT', 'fas fa-box'],
        ['GEOGRAPHY', 'fas fa-globe'],
        ['REGULATION', 'fas fa-balance-scale'],
        ['REGULATOR', 'fas fa-shield-alt']
    ]);

    function normalizeName(name) {
        if (!name) return '';
        // Convert to string if not already
        const str = typeof name === 'string' ? name : String(name);
        return str.trim().toUpperCase();
    }

    const DEFAULT_MODULE_NAMES_NORM = new Set(Array.from(DEFAULT_MODULE_NAMES.values()).map(normalizeName));

    // Map module display names to normalized category keys used by search tables
    function moduleNameToCategoryKey(name) {
        const raw = (name || '').trim();
        const n = raw.toUpperCase();
        const dash = (str) => (str || '').toLowerCase().replace(/[\s_]+/g, '-');
        switch (n) {
            case 'DATA SETS':
            case 'DATASETS':
            case 'DATASET':
            case 'DATA_SET':
            case 'DATA_SETS':
                return 'data-sets';
            case 'ATTRIBUTES':
            case 'ATTRIBUTE':
                return 'attributes';
            case 'SYSTEM':
                return 'system';
            case 'GLOSSARY':
                return 'glossary';
            case 'DATA QUALITY':
            case 'DATAQUALITY':
                return 'data-quality';
            case 'PEOPLE':
            case 'PERSON':
                return 'people';
            case 'ROLE':
            case 'ROLES':
                return 'role';
            case 'BUSINESS AREA':
            case 'BUSINESS_AREA':
                return 'business-area';
            case 'ORG UNIT':
            case 'ORG_UNIT':
            case 'ORGUNIT':
                return 'org-unit';
            case 'LEGAL ENTITY':
            case 'LEGAL_ENTITY':
                return 'legal-entity';
            case 'CLIENT':
                return 'client';
            case 'COMMITTEE':
                return 'committee';
            case 'REGULATORY THEME':
            case 'REGULATORY_THEME':
                return 'regulatory-theme';
            case 'ACTIVE TASKS':
            case 'ACTIVE_TASKS':
            case 'ACTIVETASKS':
            case 'ACTIVE-TASKS':
                return 'active-tasks';
            case 'CHANGE REQUESTS':
            case 'CHANGE_REQUESTS':
            case 'CHANGEREQUESTS':
            case 'CHANGE-REQUESTS':
                return 'change-requests';
            case 'POLICY':
            case 'POLICIES':
                return 'policy';
            case 'PROJECT':
            case 'PROJECTS':
                return 'project';
            case 'PROCESS':
            case 'PROCESSES':
                return 'process';
            case 'INTERFACE':
            case 'INTERFACES':
                return 'interface';
            case 'CAPABILITY':
            case 'CAPABILITIES':
                return 'capability';
            case 'PRODUCT':
            case 'PRODUCTS':
                return 'product';
            case 'GEOGRAPHY':
            case 'GEOGRAPHIES':
                return 'geography';
            case 'REGULATION':
            case 'REGULATIONS':
                return 'regulation';
            case 'REGULATOR':
            case 'REGULATORS':
                return 'regulator';
            default:
                return dash(raw);
        }
    }

    const STORAGE_KEY = 'budg-selected-modules';
    const STORAGE_SAVED_DEFAULTS = 'budg-saved-default-modules';
    const selectedModules = new Map(); // key: normalizedName, value: { name, icon }
    const selectedContainer = document.getElementById('selected-modules');
    let lastSelectedModuleName = '';

    function loadSelectedFromStorage() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) return;
            const arr = JSON.parse(raw);
            if (Array.isArray(arr)) {
                arr.forEach(name => {
                    if (typeof name === 'string') {
                        const key = normalizeName(name);
                        const icon = DEFAULT_MODULE_ICONS.get(key) || DEFAULT_MODULE_ICONS.get(name) || 'fas fa-cube';
                        selectedModules.set(key, { name, icon });
                    }
                });
            }
        } catch (_) {}
    }

    function initDefaultsIfNeeded() {
        if (selectedModules.size > 0) return;
        DEFAULT_MODULE_NAMES.forEach((name) => {
            const icon = DEFAULT_MODULE_ICONS.get(name) || 'fas fa-cube';
            selectedModules.set(name, { name, icon });
        });
        saveSelectedToStorage();
    }

    function saveSelectedToStorage() {
        // Do not persist transient selection across refresh; defaults are restored unless user saves layout
    }

    function loadSavedDefaults() {
        try {
            const raw = localStorage.getItem(STORAGE_SAVED_DEFAULTS);
            if (!raw) return null;
            const arr = JSON.parse(raw);
            if (Array.isArray(arr) && arr.length) {
                return new Set(arr.map(s => s.toString().toUpperCase()));
            }
        } catch (_) {}
        return null;
    }

    function getCurrentDefaultNames() {
        const saved = loadSavedDefaults();
        if (saved && saved.size) return saved;
        return new Set(Array.from(DEFAULT_MODULE_NAMES.values()).map(s => s.toString().toUpperCase()));
    }

    // Load user facet preferences from database
    async function loadUserFacetPreferences() {
        // Check if user is logged in
        const isLoggedIn = typeof window.isUserLoggedIn === 'function' ? window.isUserLoggedIn() : false;
        if (!isLoggedIn) {
            // For guests, load from localStorage
            const saved = loadSavedDefaults();
            if (saved && saved.size > 0) {
                return Array.from(saved).map(name => {
                    const originalName = Array.from(DEFAULT_MODULE_NAMES.values()).find(n => n.toUpperCase() === name) || name;
                    const icon = DEFAULT_MODULE_ICONS.get(originalName.toUpperCase()) || 'fas fa-cube';
                    return { name: originalName, icon };
                });
            }
            return null;
        }
        
        try {
            const response = await fetch('/api/unison/facets', {
                method: 'GET',
                headers: {
                    'Accept': 'application/json'
                },
                credentials: 'include'
            });
            
            if (!response.ok) {
                console.error('[MODULES] Failed to load user facets:', response.status);
                return null;
            }
            
            const result = await response.json();
            if (result.success && result.data && Array.isArray(result.data)) {
                // Filter active facets and map to module names
                const activeFacets = result.data
                    .filter(f => f.active === true)
                    .sort((a, b) => (a.ordering || 0) - (b.ordering || 0));
                
                const modules = [];
                const seenCategories = new Set();
                activeFacets.forEach(facet => {
                    // Map facetId to module name
                    let moduleName = null;
                    if (window.facetIdToModuleName && typeof window.facetIdToModuleName === 'function') {
                        moduleName = window.facetIdToModuleName(facet.facetId);
                    }
                    
                    if (moduleName) {
                        const catKey = moduleNameToCategoryKey(moduleName);
                        if (seenCategories.has(catKey)) {
                            return;
                        }
                        seenCategories.add(catKey);
                        const icon = DEFAULT_MODULE_ICONS.get(moduleName.toUpperCase()) || 'fas fa-cube';
                        modules.push({ 
                            name: moduleName, 
                            icon: icon,
                            facetId: facet.facetId 
                        });
                    }
                });
                
                if (modules.length > 0) {
                    return modules;
                }
            }
        } catch (error) {
            console.error('[MODULES] Error loading user facet preferences:', error);
        }
        
        return null;
    }

    async function initSelectionFromCurrentDefaults() {
        selectedModules.clear();
        
        // Try to load user preferences from database first
        const userModules = await loadUserFacetPreferences();
        if (userModules && userModules.length > 0) {
            userModules.forEach(moduleData => {
                const normName = normalizeName(moduleData.name);
                selectedModules.set(normName, moduleData);
            });
        } else {
            // Fall back to defaults
            const cur = getCurrentDefaultNames();
            cur.forEach(normName => {
                const originalName = Array.from(DEFAULT_MODULE_NAMES.values()).find(n => n.toUpperCase() === normName) || normName;
                const icon = DEFAULT_MODULE_ICONS.get(originalName.toUpperCase()) || 'fas fa-cube';
                selectedModules.set(normName, { name: originalName, icon });
            });
        }
        
        // Preload totals once defaults are known
        await preloadModuleRowCounts();
    }

    function renderSelectedModules() {
        if (!selectedContainer) return;
        const frag = document.createDocumentFragment();

        // Render only the currently selected modules (defaults can be removed)
        selectedModules.forEach((moduleData) => {
            // Handle backward compatibility: moduleData might be { name, icon } or { name, icon, facetId }
            const name = moduleData.name;
            const icon = moduleData.icon;
            const facetId = moduleData.facetId;
            
            const item = document.createElement('div');
            item.className = 'category-item';
            
            // Use facetId to get category key if available, otherwise fall back to moduleNameToCategoryKey
            let catKey;
            if (facetId) {
                // Map facet ID directly to category key (this is the source of truth)
                const facetToCategoryMap = {
                    'DATASET': 'data-sets',
                    'ATTRIBUTE': 'attributes',
                    'SYSTEM': 'system',
                    'GLOSSARY': 'glossary',
                    'DATAQUALITY': 'data-quality',
                    'PEOPLE': 'people',
                    'ROLE': 'role',
                    'BUSINESS_AREA': 'business-area',
                    'LEGAL_ENTITY': 'legal-entity',
                    'CLIENT': 'client',
                    'COMMITTEE': 'committee',
                    'POLICY': 'policy',
                    'PROCESS': 'process',
                    'INTERFACE': 'interface',
                    'CAPABILITY': 'capability',
                    'PRODUCT': 'product',
                    'ORG_UNIT': 'org-unit',
                    'GEOGRAPHY': 'geography',
                    'REGULATION': 'regulation',
                    'REGULATOR': 'regulator',
                    'REGULATORY_THEME': 'regulatory-theme',
                    'ACTIVE_TASKS': 'active-tasks',
                    'PROJECT': 'project',
                    'CHANGE_REQUESTS': 'change-requests'
                };
                catKey = facetToCategoryMap[facetId];
            }
            
            // Fallback: if no facetId or mapping failed, try moduleNameToCategoryKey
            if (!catKey) {
                catKey = moduleNameToCategoryKey(name);
            }
            
            item.dataset.category = catKey;
            if (facetId) {
                item.setAttribute('data-facet-id', facetId);
            }
            const resolvedIcon = icon || getModuleIconByName(name);
            // Single source for display: search (categoryCounts) then baseline (baselineCounts)
            let showText = (window.I18n && typeof window.I18n.toArabicIndicDigits === 'function')
                ? window.I18n.toArabicIndicDigits(0)
                : '0';
            if (typeof window !== 'undefined' && typeof window.getDisplayCounts === 'function') {
                const canonical = (typeof window.canonicalCategoryKey === 'function') ? window.canonicalCategoryKey(catKey) : catKey;
                const stored = window.getDisplayCounts(canonical);
                if (typeof stored.count === 'number' && typeof stored.total === 'number') {
                    if (window.I18n && typeof window.I18n.formatFacetCountLabel === 'function') {
                        showText = window.I18n.formatFacetCountLabel(stored.count, stored.total);
                    } else {
                        const ofText = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t('label.of') || 'of' : 'of';
                        showText = `${stored.count} ${ofText} ${stored.total}`;
                    }
                }
            }
            // Display name: localized facet label; logic always uses data-category + data-facet-id + stored English name
            const displayName = (typeof window.getFacetDisplayName === 'function')
                ? window.getFacetDisplayName(catKey)
                : (typeof window.getCategoryDisplayName === 'function')
                    ? window.getCategoryDisplayName(catKey)
                    : (name || '').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
            item.innerHTML = `<i class="${resolvedIcon}"></i><span class="category-name">${displayName}</span><span class="count" style="display:inline-block">${showText}</span>`;

            // Long-hover (3s) show remove X button
            let hoverTimer = null;
            const removeBtn = document.createElement('button');
            removeBtn.className = 'cat-remove-btn';
            removeBtn.type = 'button';
            removeBtn.title = 'Remove';
            removeBtn.innerHTML = '&times;';
            removeBtn.style.display = 'none';
            removeBtn.addEventListener('click', (ev) => {
                ev.stopPropagation();
                const key = normalizeName(name);
                if (selectedModules.has(key)) {
                    selectedModules.delete(key);
                    renderSelectedModules();
                    syncSubmenuCheckboxes();
                }
            });
            item.appendChild(removeBtn);

            item.addEventListener('mouseenter', () => {
                if (hoverTimer) clearTimeout(hoverTimer);
                hoverTimer = setTimeout(() => {
                    removeBtn.style.display = 'inline-flex';
                }, 1000);
            });
            item.addEventListener('mouseleave', () => {
                if (hoverTimer) clearTimeout(hoverTimer);
                removeBtn.style.display = 'none';
            });

            // Keep last selected label synced when user clicks a sidebar item.
            // Do not call renderSelectedModules() here so that category counts updated by
            // loadCategoryDataFromUnisonResults (e.g. after fallback fetch) are not overwritten with stale values.
            item.addEventListener('click', () => {
                lastSelectedModuleName = name;
                setOrgUnitBtnLabel(name);
                // Update current highlight without re-rendering the whole sidebar
                const container = item.closest('.category-sidebar-container') || item.closest('.sidebar') || document;
                if (container) {
                    container.querySelectorAll('.category-item').forEach(i => i.classList.remove('current'));
                    item.classList.add('current');
                }
                updateActiveModuleHighlights();
            });
            // highlight current opened/selected module in sidebar
            if (lastSelectedModuleName && normalizeName(name) === normalizeName(lastSelectedModuleName)) {
                item.classList.add('current');
            }
            frag.appendChild(item);
        });

        selectedContainer.innerHTML = '';
        selectedContainer.appendChild(frag);

        // Re-bind click handlers so dynamic items trigger table loading
        if (typeof window.initOrgUnitTable === 'function') {
            try { window.initOrgUnitTable(); } catch (_) {}
        }



        // Ensure the org-unit button reflects the current/last selection
        if (orgUnitBtn) {
            if (lastSelectedModuleName) {
                setOrgUnitBtnLabel(lastSelectedModuleName);
            } else {
                const first = selectedModules.values().next();
                if (!first.done && first.value && first.value.name) {
                    setOrgUnitBtnLabel(first.value.name);
                }
            }
        }

        updateActiveModuleHighlights();
    }

    function toggleSelected(moduleObj) {
        const name = moduleObj.primaryName;
        if (!name) return;
        const key = normalizeName(name);
        if (selectedModules.has(key)) {
            selectedModules.delete(key);
        } else {
            // Get facetId from module name
            let facetId = null;
            if (window.moduleNameToFacetId && typeof window.moduleNameToFacetId === 'function') {
                facetId = window.moduleNameToFacetId(name);
            }
            // Also check if moduleObj has facetId property
            if (!facetId && moduleObj.facetId) {
                facetId = moduleObj.facetId;
            }
            
            selectedModules.set(key, { 
                name, 
                icon: moduleObj.icon || getModuleIconByName(name),
                facetId: facetId
            });
        }
        lastSelectedModuleName = name;
        setOrgUnitBtnLabel(name);
        renderSelectedModules();
        saveSelectedToStorage();
        syncSubmenuCheckboxes();
        updateActiveModuleHighlights();
    }

    function syncSubmenuCheckboxes() {
        if (!submenuEl) return;
        const inputs = submenuEl.querySelectorAll('input[type="checkbox"][data-module]');
        inputs.forEach((inp) => {
            const modName = inp.getAttribute('data-module') || '';
            const normalizedModName = normalizeName(modName);
            
            // Check if this module is selected (compare normalized names)
            let isSelected = false;
            for (const [key, value] of selectedModules.entries()) {
                if (key === normalizedModName || normalizeName(value.name) === normalizedModName) {
                    isSelected = true;
                    break;
                }
            }
            
            inp.checked = isSelected;
        });
    }

    async function resetToDefault() {
        // Clear all non-default selections
        selectedModules.clear();
        if (typeof window !== 'undefined') {
            if (window.categoryCounts) window.categoryCounts.clear();
            if (window.setFacetCountSource) window.setFacetCountSource('baseline');
        }

        // Force-reload UNISON_DEFAULTS (bypass cache so we always get the latest admin settings)
        cache.unisonDefaultsLoaded = false;
        await loadUnisonDefaults();
        const visibleModules = getVisibleModulesFromDefaults();
        
        if (visibleModules && visibleModules.length > 0) {
            visibleModules.forEach((module) => {
                const moduleName = typeof module === 'string' ? module : (module.name || module);
                if (!moduleName || typeof moduleName !== 'string') {
                    console.warn('[MODULES] Invalid module name in visibleModules:', module);
                    return;
                }
                const normalizedName = normalizeName(moduleName);
                const icon = DEFAULT_MODULE_ICONS.get(normalizedName) || DEFAULT_MODULE_ICONS.get(moduleName.toUpperCase()) || 'fas fa-cube';
                const facetId = typeof module === 'object' ? module.facetId : null;
                selectedModules.set(normalizedName, { name: moduleName, icon, facetId });
            });

            // Remove the "user explicitly saved" marker so admin defaults take
            // priority again on subsequent page loads.
            localStorage.removeItem('budg-layout-saved-at');
            try {
                const moduleNames = visibleModules.map(m => typeof m === 'string' ? m : (m.name || m));
                localStorage.setItem(STORAGE_SAVED_DEFAULTS, JSON.stringify(moduleNames));
            } catch (_) {}

            // Persist the reset to the database so the sidebar survives a page
            // refresh even for users who had an old saved layout in unison_facets.
            const isLoggedIn = typeof window.isUserLoggedIn === 'function' ? window.isUserLoggedIn() : false;
            if (isLoggedIn) {
                try {
                    await fetch('/api/unison/facets/reset', {
                        method: 'POST',
                        credentials: 'include'
                    });
                } catch (e) {
                    console.warn('[MODULES] Could not persist reset to DB:', e.message);
                }
            }
        } else {
            console.error('[MODULES] NO DATABASE DEFAULTS FOUND! Database query may have failed.');
            console.error('[MODULES] Check if UNISON_DEFAULTS exists in app_config table');
        }
        
        renderSelectedModules();
        syncSubmenuCheckboxes();
        // Reload counts from backend and apply
        await preloadModuleRowCounts();
        // Close the menu after applying reset
        try { closeMenu(); } catch (_) {}
    }

    async function saveLayoutAsDefaults() {
        try {
            const names = Array.from(selectedModules.values()).map(v => v.name);
            
            // Check if user is logged in
            const isLoggedIn = typeof window.isUserLoggedIn === 'function' ? window.isUserLoggedIn() : false;
            
            if (isLoggedIn) {
                // Save to database for logged-in users
                try {
                    // Get all available facets - first try to get from UNISON_DEFAULTS
                    let allFacetIds = [];
                    if (cache.unisonDefaults && cache.unisonDefaults.facets) {
                        allFacetIds = cache.unisonDefaults.facets.map(f => f.id);
                    } else {
                        // Fallback to hardcoded list
                        allFacetIds = [
                            'DATASET', 'ATTRIBUTE', 'SYSTEM', 'GLOSSARY', 'DATAQUALITY',
                            'PEOPLE', 'ROLE', 'BUSINESS_AREA', 'LEGAL_ENTITY', 'CLIENT',
                            'COMMITTEE', 'POLICY', 'PROCESS', 'INTERFACE', 'CAPABILITY',
                            'PRODUCT', 'ORG_UNIT', 'GEOGRAPHY', 'REGULATION', 'REGULATOR',
                            'REGULATORY_THEME', 'ACTIVE_TASKS', 'PROJECT', 'CHANGE_REQUESTS'
                        ];
                    }
                    
                    // Ensure PROJECT and CHANGE_REQUESTS are always included (they might be missing from UNISON_DEFAULTS)
                    if (!allFacetIds.includes('PROJECT')) {
                        allFacetIds.push('PROJECT');
                    }
                    if (!allFacetIds.includes('CHANGE_REQUESTS')) {
                        allFacetIds.push('CHANGE_REQUESTS');
                    }
                    
                    // Create a map of selected module names (normalized) to their facetIds
                    const selectedModuleMap = new Map();
                    selectedModules.forEach((moduleData, normName) => {
                        const moduleName = moduleData.name;
                        const facetId = moduleData.facetId;
                        if (moduleName) {
                            const key = normalizeName(moduleName).toUpperCase();
                            selectedModuleMap.set(key, {
                                name: moduleName,
                                facetId: facetId
                            });
                        }
                    });
                    
                    // Build facets array - preserve existing activeFields from database
                    const facets = [];
                    let ordering = 1;
                    
                    // First, get current facets from database to preserve activeFields
                    let currentFacets = null;
                    try {
                        const currentResponse = await fetch('/api/unison/facets', {
                            method: 'GET',
                            headers: { 'Accept': 'application/json' },
                            credentials: 'include'
                        });
                        if (currentResponse.ok) {
                            const currentResult = await currentResponse.json();
                            if (currentResult.success && currentResult.data) {
                                currentFacets = new Map();
                                currentResult.data.forEach(f => {
                                    currentFacets.set(f.facetId, f);
                                });
                            }
                        }
                    } catch (e) {
                        console.warn('[MODULES] Could not load current facets:', e);
                    }
                    
                    allFacetIds.forEach(facetId => {
                        // Get module name for this facet
                        let moduleName = null;
                        if (window.facetIdToModuleName && typeof window.facetIdToModuleName === 'function') {
                            moduleName = window.facetIdToModuleName(facetId);
                        }
                        
                        // Check if this facet's module is selected
                        let isActive = false;
                        
                        // First, check by facetId directly (most reliable)
                        if (selectedModuleMap.size > 0) {
                            for (const [key, value] of selectedModuleMap.entries()) {
                                if (value.facetId === facetId) {
                                    isActive = true;
                                    break;
                                }
                            }
                        }
                        
                        // If not found by facetId, check by module name
                        if (!isActive && moduleName) {
                            const normName = normalizeName(moduleName).toUpperCase();
                            if (selectedModuleMap.has(normName)) {
                                isActive = true;
                            }
                        }
                        
                        // Get activeFields from current database state (preserve column preferences)
                        let activeFields = '';
                        if (currentFacets && currentFacets.has(facetId)) {
                            activeFields = currentFacets.get(facetId).activeFields || '';
                        }
                        
                        facets.push({
                            facetId: facetId,
                            active: isActive,
                            activeFields: activeFields,
                            ordering: isActive ? ordering++ : 0
                        });
                    });
                    
                    // Save to database
                    const response = await fetch('/api/unison/facets/save', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'Accept': 'application/json'
                        },
                        credentials: 'include',
                        body: JSON.stringify({ facets: facets })
                    });
                    
                    if (response.ok) {
                        const result = await response.json();
                        if (result.success) {
                            // After successful save, reload from database to ensure consistency
                            const reloadedModules = await loadUserFacetPreferences();
                            if (reloadedModules && reloadedModules.length > 0) {
                                selectedModules.clear();
                                reloadedModules.forEach(moduleData => {
                                    const normName = normalizeName(moduleData.name);
                                    selectedModules.set(normName, moduleData);
                                });
                            } else {
                                // If reload failed, keep current selection
                                console.warn('[MODULES] Could not reload from database, keeping current selection');
                            }
                        } else {
                            console.error('[MODULES] Failed to save sidebar layout:', result);
                            // Fall back to localStorage
                            localStorage.setItem(STORAGE_SAVED_DEFAULTS, JSON.stringify(names));
                        }
                    } else {
                        console.error('[MODULES] Failed to save sidebar layout, status:', response.status);
                        // Fall back to localStorage
                        localStorage.setItem(STORAGE_SAVED_DEFAULTS, JSON.stringify(names));
                    }
                } catch (error) {
                    console.error('[MODULES] Error saving sidebar layout to database:', error);
                    // Fall back to localStorage
                    localStorage.setItem(STORAGE_SAVED_DEFAULTS, JSON.stringify(names));
                }
            } else {
                // Save to localStorage for guests
                localStorage.setItem(STORAGE_SAVED_DEFAULTS, JSON.stringify(names));
            }
            
            // Record the time of the explicit user save so we can compare against
            // admin's lastUpdated later (see initializeModules).
            localStorage.setItem('budg-layout-saved-at', Date.now().toString());

            // Render and sync - don't call initSelectionFromCurrentDefaults as it clears and reloads
            renderSelectedModules();
            syncSubmenuCheckboxes();
            // Close the menu after saving layout
            try { closeMenu(); } catch (_) {}
        } catch (error) {
            console.error('[MODULES] Error in saveLayoutAsDefaults:', error);
        }
    }

    // Load groups/modules and row counts once
    async function loadIndex() {
        if (cache.apiLoaded) {
            logModules('loadIndex: skipped (cache.apiLoaded already true)', {
                groups: cache.groups?.length,
                modules: cache.modules?.length
            });
            return;
        }

        // 1) Fetch groups and modules (root-relative URL — works from any page path)
        const modulesUrl = '/api/modules';
        logModules('loadIndex: fetching', modulesUrl);
        const res = await fetch(modulesUrl, { credentials: 'same-origin' });
        logModules('loadIndex: /api/modules response', { ok: res.ok, status: res.status, statusText: res.statusText });
        if (!res.ok) {
            const text = await res.text().catch(() => '');
            logModules('loadIndex: /api/modules body (first 200 chars)', text ? text.slice(0, 200) : '(empty)');
            throw new Error('api/modules failed: HTTP ' + res.status);
        }
        const data = await res.json();
        cache.groups = data.groups || [];
        cache.modules = data.modules || [];
        logModules('loadIndex: parsed payload', { groups: cache.groups.length, modules: cache.modules.length });

        // Map modules by their group
        cache.groups.forEach(g => {
            const groupModules = cache.modules.filter(m => m.groupId === g.id);
            cache.modulesByGroup.set(g.id, groupModules);
        });

        // 2) Fetch row counts (bulk endpoint)
        try {
            const countUrl = '/api/module-row-count';
            logModules('loadIndex: fetching counts', countUrl);
            const countRes = await fetch(countUrl, { credentials: 'same-origin' });
            logModules('loadIndex: module-row-count response', { ok: countRes.ok, status: countRes.status });
            if (!countRes.ok) {
                throw new Error('module-row-count HTTP ' + countRes.status);
            }
            const countJson = await countRes.json();
            const list = Array.isArray(countJson.rowCounts) ? countJson.rowCounts : [];

            // Store by moduleName and by tableName for flexible matching
            list.forEach(({ moduleName, tableName, rowCount }) => {
                if (moduleName) {
                    cache.countByModuleName.set(moduleName.trim(), rowCount ?? 0);
                }
                if (tableName) {
                    cache.countByTableName.set(tableName.trim().toLowerCase(), rowCount ?? 0);
                }
            });
        } catch (e) {
            // If the bulk endpoint fails, hide counts gracefully
            cache.countByModuleName.clear();
            cache.countByTableName.clear();
            console.warn('[MODULES] Bulk module-row-count failed. Hiding counts.', e);
            logModules('loadIndex: module-row-count failed (non-fatal)', e && e.message ? e.message : e);
        }

        cache.apiLoaded = true;
        logModules('loadIndex: done', { apiLoaded: cache.apiLoaded });
    }

    // Helper: get row count for a module
    function getCountFor(moduleObj) {
        const byName = cache.countByModuleName.get(moduleObj.primaryName?.trim());
        if (byName != null) return byName;

        // Fallback: guess table name from module name (spaces -> underscores)
        const tableGuess = (moduleObj.primaryName || '')
            .trim()
            .toLowerCase()
            .replace(/\s+/g, '_');
        const byTable = cache.countByTableName.get(tableGuess);
        if (byTable != null) return byTable;

        return '—';
    }

    // Helper: get icon for a group
    function getGroupIcon(groupName) {
        return groupIcons[groupName] || 'fas fa-folder';
    }

    function renderGroups() {
        const container = ensureMenu();
        container.innerHTML = '';

        if (!cache.groups.length) {
            logModules('renderGroups: no groups in cache — menu will show empty-state', {
                modulesInCache: cache.modules?.length || 0
            });
            const noGroupsText = (window.I18n && typeof window.I18n.t === 'function') 
                ? window.I18n.t('message.noGroups') || 'No groups available.' 
                : 'No groups available.';
            container.innerHTML = `<div class="error">${noGroupsText}</div>`;
            return;
        }
        logModules('renderGroups: building menu', { groups: cache.groups.length, modules: cache.modules.length });

        cache.groups.forEach(g => {
            const wrap = document.createElement('div');
            wrap.className = 'group-item';

            const header = document.createElement('button');
            header.className = 'group-header';
            header.type = 'button';
            header.setAttribute('aria-expanded', 'false');
            
            const groupIcon = getGroupIcon(g.primaryName);
            const displayGroupName = translateGroupDisplayName(g.primaryName);
            header.innerHTML = `
                <span>
                    <i class="${groupIcon}"></i>
                    ${displayGroupName}
                </span>
                <span class="chev">▾</span>
            `;

            const list = document.createElement('div');
            list.className = 'modules-list';
            list.style.display = 'none';
            list.style.maxHeight = '0';
            list.style.opacity = '0';

            header.addEventListener('click', () => {
                // Always open side submenu for this group; do not expand below
                const sub = ensureSubmenu();
                const mods = cache.modulesByGroup.get(g.id) || [];
                const frag = document.createDocumentFragment();

                if (!mods.length) {
                    const empty = document.createElement('div');
                    empty.className = 'error';
                    const noModulesText = (window.I18n && typeof window.I18n.t === 'function') 
                        ? window.I18n.t('message.noModulesInGroup') || 'No modules in this group.' 
                        : 'No modules in this group.';
                    empty.textContent = noModulesText;
                    frag.appendChild(empty);
                } else {
                    mods.forEach(m => {
                        const row = document.createElement('label');
                        row.className = 'module-row';
                        const count = getCountFor(m);
                        const normalizedPrimaryName = normalizeName(m.primaryName);
                        const isDefault = DEFAULT_MODULE_NAMES_NORM.has(normalizedPrimaryName);
                        
                        // Check if this module is selected
                        let isChecked = false;
                        
                        // Check if selected by key or by normalized value name
                        if (selectedModules.has(normalizedPrimaryName)) {
                            isChecked = true;
                        } else {
                            // Also check if any selected module's name matches
                            for (const [key, value] of selectedModules.entries()) {
                                if (normalizeName(value.name) === normalizedPrimaryName) {
                                    isChecked = true;
                                    break;
                                }
                            }
                        }
                        
                        const checked = isChecked ? 'checked' : '';
                        const resolvedIcon = m.icon || getModuleIconByName(m.primaryName);
                        
                        // Translate module name for display
                        let displayModuleName = m.primaryName;
                        if (window.I18n && typeof window.I18n.t === 'function') {
                            // First try to translate using facetIdToModuleName
                            if (window.moduleNameToFacetId) {
                                const facetId = window.moduleNameToFacetId(m.primaryName);
                                if (facetId && window.facetIdToModuleName) {
                                    const translated = window.facetIdToModuleName(facetId);
                                    if (translated && translated !== facetId) {
                                        displayModuleName = translated;
                                    }
                                }
                            }
                            // Fallback: try direct translation for common module names
                            if (displayModuleName === m.primaryName) {
                                const directTranslationKeys = {
                                    'Change Requests': 'facet.changeRequests',
                                    'Physical Fields': 'label.physicalFields',
                                    'Active Tasks': 'facet.activeTasks',
                                    'Project': 'facet.project'
                                };
                                const directKey = directTranslationKeys[m.primaryName];
                                if (directKey) {
                                    const translated = window.I18n.t(directKey);
                                    if (translated && translated !== directKey) {
                                        displayModuleName = translated;
                                    }
                                }
                            }
                        }
                        
                        row.innerHTML = `
                            <input type="checkbox" class="module-check" data-module="${m.primaryName}" ${isDefault ? 'data-default="true"' : ''} ${checked} />
                            <i class="${resolvedIcon}"></i>
                            <span>${displayModuleName}</span>
                            ${Number.isFinite(count) ? `<span class="count">(${count})</span>` : ''}
                        `;
                        if (lastSelectedModuleName && normalizeName(m.primaryName) === normalizeName(lastSelectedModuleName)) {
                            row.classList.add('active');
                        }
                        row.addEventListener('click', (ev) => {
                            if ((ev.target instanceof HTMLInputElement)) return;
                            toggleSelected(m);
                            ev.preventDefault();
                        });
                        const input = row.querySelector('input');
                        if (input) {
                            input.addEventListener('change', (ev) => {
                                toggleSelected(m);
                                ev.stopPropagation();
                            });
                        }
                        frag.appendChild(row);
                    });
                }
                
                // Add header with translated group name
                const headerFrag = document.createDocumentFragment();
                const submenuHeader = document.createElement('div');
                submenuHeader.className = 'submenu-header';
                // Use the already translated displayGroupName
                const groupIconForHeader = getGroupIcon(g.primaryName);
                submenuHeader.innerHTML = `
                    <i class="${groupIconForHeader}"></i>
                    <span>${displayGroupName}</span>
                `;
                headerFrag.appendChild(submenuHeader);
                headerFrag.appendChild(frag);
                
                sub.innerHTML = '';
                sub.appendChild(headerFrag);

                // Do not auto-add defaults here; respect user's current selection only
                setSubmenuMaxHeight();
                positionSubmenu();
                sub.style.display = 'block';
                syncSubmenuCheckboxes();
                renderSelectedModules();
            });

            wrap.appendChild(header);
            wrap.appendChild(list);
            container.appendChild(wrap);
        });
        // Ensure footer with Reset button exists at the end of the menu
        ensureMenuFooter();
    }

    // Menu positioning and open/close logic
    function setMenuMaxHeight() {
        if (!menuEl) return;
        const safePadding = 20; // margin from viewport edges
        const maxHeightPx = Math.max(240, window.innerHeight - safePadding);
        menuEl.style.maxHeight = `${maxHeightPx}px`;
    }
    function setSubmenuMaxHeight() {
        if (!submenuEl) return;
        const safePadding = 20;
        const maxHeightPx = Math.max(240, window.innerHeight - safePadding);
        submenuEl.style.maxHeight = `${maxHeightPx}px`;
    }
    function isSearchPageRTL() {
        return document.documentElement.getAttribute('dir') === 'rtl' ||
            (typeof getComputedStyle !== 'undefined' &&
                getComputedStyle(document.documentElement).direction === 'rtl');
    }

    function positionMenu() {
        const rect = btn.getBoundingClientRect();
        const gap = 10;

        menuEl.style.position = 'fixed';
        menuEl.style.display = 'block';

        // Use the actual rendered size when available (may be 0 on first paint; one rAF reflow below fixes it)
        const measuredWidth = menuEl.offsetWidth || 320;
        const measuredHeight = menuEl.offsetHeight || 300;

        // Prefer opening below the button (clear association with trigger; avoids shoving the
        // whole panel to y=~16 when centering + clamp fights a fixed header / tall menu).
        let top = rect.bottom + gap;
        let left;
        const rtl = isSearchPageRTL();

        if (rtl) {
            // Sidebar is on the visual right: open toward the center (to the left of the button)
            left = rect.left - measuredWidth - gap;
            if (left < 10) {
                left = rect.right + gap;
            }
            if (left + measuredWidth > window.innerWidth - 10) {
                left = Math.max(10, window.innerWidth - measuredWidth - 10);
            }
        } else {
            left = rect.right + gap; // place to the right of the button
            if (left + measuredWidth > window.innerWidth - 10) {
                left = rect.left - measuredWidth - gap;
            }
        }

        // Clamp vertically within viewport (reserve top for fixed app header when needed)
        const viewportTop = 56;
        const viewportBottom = window.innerHeight - 16;
        if (top + measuredHeight > viewportBottom) {
            const aboveTop = rect.top - measuredHeight - gap;
            if (aboveTop >= viewportTop) {
                top = aboveTop;
            } else {
                top = Math.max(viewportTop, viewportBottom - measuredHeight);
            }
        }
        if (top < viewportTop) {
            top = viewportTop;
        }

        // Ensure stays on screen horizontally
        if (left < 10) left = 10;
        if (left + measuredWidth > window.innerWidth - 10) {
            left = window.innerWidth - measuredWidth - 10;
        }

        menuEl.style.top = `${top}px`;
        menuEl.style.left = `${left}px`;
    }
    function positionSubmenu() {
        if (!menuEl) return;
        ensureSubmenu();
        const gap = 8;
        const menuRect = menuEl.getBoundingClientRect();

        submenuEl.style.position = 'fixed';
        submenuEl.style.display = 'block';

        const measuredWidth = submenuEl.offsetWidth || 360;
        const measuredHeight = submenuEl.offsetHeight || 300;

        // Align visual top edge with modules-menu content top accounting for borders/padding
        const menuStyles = window.getComputedStyle(menuEl);
        const submenuStyles = window.getComputedStyle(submenuEl);
        const menuBorderTop = parseFloat(menuStyles.borderTopWidth || '0') || 0;
        const menuPaddingTop = parseFloat(menuStyles.paddingTop || '0') || 0;
        const submenuBorderTop = parseFloat(submenuStyles.borderTopWidth || '0') || 0;
        const submenuMarginTop = parseFloat(submenuStyles.marginTop || '0') || 0;

        // Align submenu content top with main menu content top (no large negative offset)
        let top = menuRect.top + menuBorderTop + menuPaddingTop - submenuBorderTop - submenuMarginTop;
        let left = menuRect.right + gap;

        if (left + measuredWidth > window.innerWidth - 10) {
            left = Math.max(10, menuRect.left - measuredWidth - gap);
        }

        const pad = 12;
        const vBottom = window.innerHeight - pad;
        if (top + measuredHeight > vBottom) {
            top = Math.max(pad, vBottom - measuredHeight);
        }
        if (top < pad) {
            top = pad;
        }

        submenuEl.style.top = `${Math.round(top)}px`;
        submenuEl.style.left = `${left}px`;
    }
    
    function openMenu() {
        logModules('openMenu');
        const menu = ensureMenu();
        menu.style.display = 'block';
        menu.style.opacity = '0';
        menu.style.transform = 'translateY(-10px)';
        
        // Position the menu relative to the button
        setMenuMaxHeight();
        positionMenu();
        if (submenuEl && submenuEl.style.display !== 'none') {
            setSubmenuMaxHeight();
            positionSubmenu();
        }
        // Second pass after layout so offsetHeight/width reflect real content
        requestAnimationFrame(() => {
            setMenuMaxHeight();
            positionMenu();
            if (submenuEl && submenuEl.style.display !== 'none') {
                setSubmenuMaxHeight();
                positionSubmenu();
            }
        });
        
        // Smooth entrance animation
        setTimeout(() => {
            menu.style.opacity = '1';
            menu.style.transform = 'translateY(0)';
        }, 10);
        
        // Defer outside-click listener so the same user gesture cannot close the menu (capture quirks / RTL overlays)
        document.removeEventListener('click', onDocClick, true);
        setTimeout(() => {
            document.addEventListener('click', onDocClick, true);
        }, 0);
        window.addEventListener('resize', () => {
            setMenuMaxHeight();
            positionMenu();
            if (submenuEl && submenuEl.style.display !== 'none') {
                setSubmenuMaxHeight();
                positionSubmenu();
            }
        });
    }
    
    function closeMenu() {
        if (!menuEl) return;
        
        // Smooth exit animation
        menuEl.style.opacity = '0';
        menuEl.style.transform = 'translateY(-10px)';
        
        setTimeout(() => {
            if (menuEl) {
                menuEl.style.display = 'none';
            }
        }, 200);
        
        document.removeEventListener('click', onDocClick, true);
        window.removeEventListener('resize', positionMenu);
        if (submenuEl) {
            submenuEl.style.display = 'none';
        }
    }
    
    function onDocClick(e) {
        const t = e.target;
        if (t === btn || (btn && btn.contains(t))) return;
        if (t && typeof t.closest === 'function' && t.closest('#module-container')) return;
        if (menuEl && menuEl.contains(t)) return;
        if (submenuEl && submenuEl.contains(t)) return;
        closeMenu();
    }

    // ============ Org Unit Quick Menu (under org-unit-btn) ============
    let orgUnitMenuEl = null;
    let orgUnitSubmenuEl = null;
    let orgUnitDocHandlerBound = false;
    const orgUnitBtn = document.querySelector('.org-unit-btn');

    function ensureOrgUnitMenu() {
        if (orgUnitMenuEl) return orgUnitMenuEl;
        orgUnitMenuEl = document.createElement('div');
        orgUnitMenuEl.className = 'modules-submenu orgunit-quickmenu';
        orgUnitMenuEl.style.display = 'none';
        document.body.appendChild(orgUnitMenuEl);
        return orgUnitMenuEl;
    }

    function ensureOrgUnitSubmenu() {
        if (orgUnitSubmenuEl) return orgUnitSubmenuEl;
        orgUnitSubmenuEl = document.createElement('div');
        orgUnitSubmenuEl.className = 'modules-submenu orgunit-quickmenu-sub';
        orgUnitSubmenuEl.style.display = 'none';
        document.body.appendChild(orgUnitSubmenuEl);
        return orgUnitSubmenuEl;
    }

    function positionOrgUnitMenu() {
        if (!orgUnitMenuEl || !orgUnitBtn) return;
        const rect = orgUnitBtn.getBoundingClientRect();
        const gap = 8;
        const measuredWidth = orgUnitMenuEl.offsetWidth || 360;
        const measuredHeight = orgUnitMenuEl.offsetHeight || 300;

        let top = rect.bottom + gap;
        let left = rect.left;

        if (left + measuredWidth > window.innerWidth - 10) {
            left = Math.max(10, window.innerWidth - measuredWidth - 10);
        }
        const viewportBottom = window.innerHeight - 16;
        if (top + measuredHeight > viewportBottom) {
            top = Math.max(16, viewportBottom - measuredHeight);
        }

        orgUnitMenuEl.style.position = 'fixed';
        orgUnitMenuEl.style.top = `${top}px`;
        orgUnitMenuEl.style.left = `${left}px`;
        orgUnitMenuEl.style.display = 'block';
    }

    function positionOrgUnitSubmenu() {
        if (!orgUnitMenuEl) return;
        ensureOrgUnitSubmenu();
        const gap = 8;
        const menuRect = orgUnitMenuEl.getBoundingClientRect();
        const measuredWidth = orgUnitSubmenuEl.offsetWidth || 360;
        const measuredHeight = orgUnitSubmenuEl.offsetHeight || 300;

        let top = menuRect.top;
        let left = menuRect.right + gap;
        if (left + measuredWidth > window.innerWidth - 10) {
            left = Math.max(10, menuRect.left - measuredWidth - gap);
        }
        const viewportTop = 16;
        const viewportBottom = window.innerHeight - 16;
        if (top + measuredHeight > viewportBottom) {
            top = Math.max(viewportTop, viewportBottom - measuredHeight);
        }
        if (top < viewportTop) top = viewportTop;

        orgUnitSubmenuEl.style.position = 'fixed';
        orgUnitSubmenuEl.style.top = `${top}px`;
        orgUnitSubmenuEl.style.left = `${left}px`;
        orgUnitSubmenuEl.style.display = 'block';
    }

    function closeOrgUnitMenu() {
        if (orgUnitMenuEl) orgUnitMenuEl.style.display = 'none';
        if (orgUnitSubmenuEl) orgUnitSubmenuEl.style.display = 'none';
        if (orgUnitDocHandlerBound) {
            document.removeEventListener('click', onOrgUnitDocClick, true);
            orgUnitDocHandlerBound = false;
        }
    }

    function onOrgUnitDocClick(e) {
        if (orgUnitBtn && (e.target === orgUnitBtn || orgUnitBtn.contains(e.target))) return;
        if (orgUnitMenuEl && orgUnitMenuEl.contains(e.target)) return;
        closeOrgUnitMenu();
    }

    async function openOrgUnitMenu() {
        try {
            await loadIndex();
            const menu = ensureOrgUnitMenu();
            const frag = document.createDocumentFragment();

            if (!cache.groups.length) {
                const empty = document.createElement('div');
                empty.className = 'error';
                const noGroupsText = (window.I18n && typeof window.I18n.t === 'function') 
                    ? window.I18n.t('message.noGroups') || 'No groups available.' 
                    : 'No groups available.';
                empty.textContent = noGroupsText;
                frag.appendChild(empty);
            } else {
                cache.groups.forEach(g => {
                    const wrap = document.createElement('div');
                    wrap.className = 'group-item';

                    const header = document.createElement('button');
                    header.className = 'group-header';
                    header.type = 'button';
                    header.setAttribute('aria-expanded', 'false');

                    const groupIcon = getGroupIcon(g.primaryName);
                    const displayGroupName = translateGroupDisplayName(g.primaryName);
                    header.innerHTML = `
                        <span>
                            <i class="${groupIcon}"></i>
                            ${displayGroupName}
                        </span>
                        <span class="chev">▾</span>
                    `;

                    header.addEventListener('click', () => {
                        // Populate modules to the side submenu instead of below
                        const mods = cache.modulesByGroup.get(g.id) || [];
                        const innerFrag = document.createDocumentFragment();
                        if (!mods.length) {
                            const empty = document.createElement('div');
                            empty.className = 'error';
                            const noModulesText = (window.I18n && typeof window.I18n.t === 'function') 
                                ? window.I18n.t('message.noModulesInGroup') || 'No modules in this group.' 
                                : 'No modules in this group.';
                            empty.textContent = noModulesText;
                            innerFrag.appendChild(empty);
                        } else {
                            mods.forEach(m => {
                                const row = document.createElement('label');
                                row.className = 'module-row';
                                // Translate module name
                                let displayModuleName = m.primaryName;
                                if (window.I18n && typeof window.I18n.t === 'function') {
                                    // First try to translate using facetIdToModuleName
                                    if (window.moduleNameToFacetId) {
                                        const facetId = window.moduleNameToFacetId(m.primaryName);
                                        if (facetId && window.facetIdToModuleName) {
                                            const translated = window.facetIdToModuleName(facetId);
                                            if (translated && translated !== facetId) {
                                                displayModuleName = translated;
                                            }
                                        }
                                    }
                                    // Fallback: try direct translation for common module names
                                    if (displayModuleName === m.primaryName) {
                                        const directTranslationKeys = {
                                            'Change Requests': 'facet.changeRequests',
                                            'Physical Fields': 'label.physicalFields',
                                            'Active Tasks': 'facet.activeTasks',
                                            'Project': 'facet.project'
                                        };
                                        const directKey = directTranslationKeys[m.primaryName];
                                        if (directKey) {
                                            const translated = window.I18n.t(directKey);
                                            if (translated && translated !== directKey) {
                                                displayModuleName = translated;
                                            }
                                        }
                                    }
                                }
                                row.innerHTML = `
                                    <i class="${m.icon || getModuleIconByName(m.primaryName)}"></i>
                                    <span>${displayModuleName}</span>
                                `;
                                if (lastSelectedModuleName && normalizeName(m.primaryName) === normalizeName(lastSelectedModuleName)) {
                                    row.classList.add('active');
                                }
                                row.addEventListener('click', (ev) => {
                                    ev.preventDefault();
                                    const key = (m.primaryName || '').toUpperCase().trim();
                                    if (!selectedModules.has(key)) {
                                        selectedModules.set(key, { name: m.primaryName, icon: m.icon || 'fas fa-cube' });
                                    }
                                    lastSelectedModuleName = m.primaryName;
                                    // Translate module name for button label
                                    let displayModuleNameForBtn = m.primaryName;
                                    if (window.I18n && typeof window.I18n.t === 'function' && window.moduleNameToFacetId) {
                                        const facetId = window.moduleNameToFacetId(m.primaryName);
                                        if (facetId && window.facetIdToModuleName) {
                                            const translated = window.facetIdToModuleName(facetId);
                                            if (translated && translated !== facetId) {
                                                displayModuleNameForBtn = translated;
                                            }
                                        }
                                    }
                                    setOrgUnitBtnLabel(displayModuleNameForBtn);
                                    renderSelectedModules();
                                    syncSubmenuCheckboxes();
                                    // Trigger table load for the selected module
                                    const cat = moduleNameToCategoryKey(m.primaryName);
                                    const el = document.querySelector(`.category-item[data-category="${cat}"]`);
                                    if (el) {
                                        el.click();
                                    }
                                    closeOrgUnitMenu();
                                });
                                innerFrag.appendChild(row);
                            });
                        }
                        ensureOrgUnitSubmenu();
                        
                        // Add header with translated group name
                        const headerFrag = document.createDocumentFragment();
                        const submenuHeader = document.createElement('div');
                        submenuHeader.className = 'submenu-header';
                        // Use the already translated displayGroupName from the outer scope
                        const groupIconForHeader = getGroupIcon(g.primaryName);
                        submenuHeader.innerHTML = `
                            <i class="${groupIconForHeader}"></i>
                            <span>${displayGroupName}</span>
                        `;
                        headerFrag.appendChild(submenuHeader);
                        headerFrag.appendChild(innerFrag);
                        
                        orgUnitSubmenuEl.innerHTML = '';
                        orgUnitSubmenuEl.appendChild(headerFrag);
                        positionOrgUnitSubmenu();
                        orgUnitSubmenuEl.style.display = 'block';
                    });

                    wrap.appendChild(header);
                    frag.appendChild(wrap);
                });
            }

            menu.innerHTML = '';
            menu.appendChild(frag);
            setSubmenuMaxHeight();
            positionOrgUnitMenu();
            // Hide the side submenu until a group is clicked
            if (orgUnitSubmenuEl) { orgUnitSubmenuEl.style.display = 'none'; }
            if (!orgUnitDocHandlerBound) {
                document.addEventListener('click', onOrgUnitDocClick, true);
                orgUnitDocHandlerBound = true;
            }
        } catch (_) {
            // ignore
        }
    }

    if (orgUnitBtn) {
        orgUnitBtn.addEventListener('click', (e) => {
            e.preventDefault();
            const menu = ensureOrgUnitMenu();
            if (menu.style.display === 'block') {
                closeOrgUnitMenu();
            } else {
                openOrgUnitMenu();
            }
        });
        window.addEventListener('resize', () => {
            if (orgUnitMenuEl && orgUnitMenuEl.style.display === 'block') {
                positionOrgUnitMenu();
            }
        });
    }

    // Button click handler
    btn.addEventListener('click', async (e) => {
        e.preventDefault();
        e.stopPropagation();
        logModules('click: Add Category', { target: e.target && e.target.tagName, defaultPrevented: e.defaultPrevented });
        try {
            const loadingMsg = tOr('modulesMenu.loadingGroups', 'Loading groups...');
            ensureMenu().innerHTML = '<div class="loading">' + loadingMsg + '</div>';
            openMenu();
            await loadIndex();
            renderGroups();
            // Re-position after content renders to account for actual height
            positionMenu();
            logModules('click: menu ready', {
                menuDisplay: menuEl && menuEl.style.display,
                groupsRendered: cache.groups.length
            });
            // Do not re-render sidebar selections on open to avoid CSS changes
        } catch (err) {
            console.error('[MODULES][AddCategory] click handler failed:', err);
            logModules('click: ERROR', err && err.message ? err.message : String(err));
            const errMsg = tOr('modulesMenu.errorLoadingData', 'Error loading data.');
            ensureMenu().innerHTML = '<div class="error">' + errMsg + '</div>';
        }
    });

    // Initial render on page load: load user preferences first, then defaults
    async function initializeModules() {
        // Load UNISON_DEFAULTS from database FIRST
        await loadUnisonDefaults();

        // If admin has saved Display Settings, check whether those settings are
        // newer than the user's last explicit "Save Layout" action.
        // lastUpdated is written by DisplaySettingsServlet every time admin saves.
        const adminLastUpdated = (cache.unisonDefaults && cache.unisonDefaults.lastUpdated)
            ? parseInt(cache.unisonDefaults.lastUpdated) || 0
            : 0;
        const userSavedAt = parseInt(localStorage.getItem('budg-layout-saved-at') || '0') || 0;
        // Admin defaults are "newer" → they should take priority over the user's DB rows
        const adminHasNewerDefaults = adminLastUpdated > 0 && adminLastUpdated > userSavedAt;

        // Only load per-user DB rows when admin hasn't changed settings since user last saved
        let userModules = null;
        if (!adminHasNewerDefaults) {
            userModules = await loadUserFacetPreferences();
        }

        if (userModules && userModules.length > 0) {
            selectedModules.clear();
            userModules.forEach(moduleData => {
                const normName = normalizeName(moduleData.name);
                selectedModules.set(normName, moduleData);
            });
        } else {
            // Fall back to admin's UNISON_DEFAULTS
            const visibleModules = getVisibleModulesFromDefaults();
            
            if (visibleModules && visibleModules.length > 0) {
                selectedModules.clear();
                visibleModules.forEach((module) => {
                    // Handle both old format (string) and new format (object with name and facetId)
                    const moduleName = typeof module === 'string' ? module : module.name;
                    const facetId = typeof module === 'string' ? null : module.facetId;
                    const normalizedName = normalizeName(moduleName);
                    const icon = DEFAULT_MODULE_ICONS.get(normalizedName) || DEFAULT_MODULE_ICONS.get(moduleName) || 'fas fa-cube';
                    selectedModules.set(normalizedName, { name: moduleName, icon, facetId });
                });
            } else {
                console.error('[MODULES] CRITICAL: No database defaults loaded on initialization!');
                console.error('[MODULES] Please ensure UNISON_DEFAULTS exists in app_config table');
                console.error('[MODULES] System will not display any modules until this is fixed');
            }
        }
        
        // Preload totals before first render so counts show immediately
        await preloadModuleRowCounts();
        
        renderSelectedModules();
    }
    
    // Initialize modules; search-init.js awaits this so the first facet can be selected after the sidebar exists
    window.budgSearchModulesInitialized = initializeModules().catch(function (e) {
        console.error('[MODULES] initializeModules failed:', e);
    });

    function setOrgUnitBtnLabel(name) {
        if (!orgUnitBtn) return;
        const labelSpan = orgUnitBtn.querySelector('span');
        if (labelSpan) {
            // Show translated facet name; filtering still uses primaryName / facetId from selectedModules
            let catKey = null;
            if (window.moduleNameToFacetId && typeof window.moduleNameToFacetId === 'function') {
                const fid = window.moduleNameToFacetId(name);
                if (fid && window.facetIdToCategorySlug) {
                    catKey = window.facetIdToCategorySlug(fid);
                }
            }
            if (!catKey && typeof moduleNameToCategoryKey === 'function') {
                catKey = moduleNameToCategoryKey(name);
            }
            if (catKey && typeof window.getFacetDisplayName === 'function') {
                labelSpan.textContent = window.getFacetDisplayName(catKey);
            } else if (catKey && typeof window.getCategoryDisplayName === 'function') {
                labelSpan.textContent = window.getCategoryDisplayName(catKey);
            } else {
                labelSpan.textContent = name;
            }
        }
    }

    function getModuleIconByName(name) {
        if (!name) return 'fas fa-cube';
        
        const n = normalizeName(name);
        
        // First check DEFAULT_MODULE_ICONS Map
        const iconFromMap = DEFAULT_MODULE_ICONS.get(n);
        if (iconFromMap) {
            return iconFromMap;
        }
        
        // Then check switch statement for additional mappings
        switch (n) {
            case 'DATA SETS':
            case 'DATASETS':
            case 'DATASET':
            case 'DATA_SET':
            case 'DATA_SETS':
                return 'fas fa-database';
            case 'ATTRIBUTES':
            case 'ATTRIBUTE':
                return 'fas fa-tags';
            case 'SYSTEM':
                return 'fas fa-desktop';
            case 'GLOSSARY':
                return 'fas fa-book';
            case 'DATA QUALITY':
            case 'DATAQUALITY':
                return 'fas fa-check-circle';
            case 'PEOPLE':
            case 'PERSON':
                return 'fas fa-users';
            case 'ROLE':
            case 'ROLES':
                return 'fas fa-user-tie';
            case 'BUSINESS AREA':
            case 'BUSINESS_AREA':
                return 'fas fa-building';
            case 'ORG UNIT':
            case 'ORG_UNIT':
            case 'ORGUNIT':
                return 'fas fa-sitemap';
            case 'LEGAL ENTITY':
            case 'LEGAL_ENTITY':
                return 'fas fa-gavel';
            case 'PRODUCT':
            case 'PRODUCTS':
                return 'fas fa-box';
            case 'PROJECT':
            case 'PROJECTS':
                return 'fas fa-project-diagram';
            case 'POLICY':
            case 'POLICIES':
                return 'fas fa-file-contract';
            case 'PROCESS':
            case 'PROCESSES':
                return 'fas fa-sitemap';
            case 'CLIENT':
            case 'CLIENTS':
                return 'fas fa-users';
            case 'CAPABILITY':
            case 'CAPABILITIES':
                return 'fas fa-lightbulb';
            case 'GEOGRAPHY':
            case 'GEOGRAPHIES':
                return 'fas fa-globe';
            case 'REGULATION':
            case 'REGULATIONS':
                return 'fas fa-balance-scale';
            case 'REGULATOR':
            case 'REGULATORS':
                return 'fas fa-shield-alt';
            case 'REGULATORY THEME':
            case 'REGULATORY_THEME':
            case 'REGULATORY-THEME':
                return 'fas fa-bookmark';
            case 'CHANGE REQUESTS':
            case 'CHANGE_REQUESTS':
            case 'CHANGEREQUESTS':
            case 'CHANGE-REQUESTS':
                return 'fas fa-exchange-alt';
            case 'INTERFACE':
            case 'INTERFACES':
                return 'fas fa-plug';
            case 'COMMITTEE':
            case 'COMMITTEES':
                return 'fas fa-users-cog';
            case 'ACTIVE TASKS':
            case 'ACTIVE_TASKS':
            case 'ACTIVETASKS':
            case 'ACTIVE-TASKS':
                return 'fas fa-tasks';
            default:
                // Fallback: try to get from DEFAULT_MODULE_ICONS with different variations
                const variations = [
                    n,
                    n.replace(/\s+/g, ' '),
                    n.replace(/_/g, ' '),
                    n.replace(/-/g, ' ')
                ];
                for (const variation of variations) {
                    const icon = DEFAULT_MODULE_ICONS.get(variation);
                    if (icon) return icon;
                }
                return 'fas fa-cube';
        }
    }

    function updateActiveModuleHighlights() {
        const current = normalizeName(lastSelectedModuleName || '');
        const menus = [submenuEl, orgUnitMenuEl, orgUnitSubmenuEl].filter(Boolean);
        menus.forEach(menu => {
            const rows = menu.querySelectorAll('.module-row');
            rows.forEach(row => {
                const text = normalizeName(row.querySelector('span')?.textContent || '');
                if (text && text === current) {
                    row.classList.add('active');
                } else {
                    row.classList.remove('active');
                }
            });
        });
    }
})();
