// Main JavaScript functionality for BUDG Platform
// Global fetch URL rewriter to support /view, /create, /auth namespaces while backend migrates
(function installApiNamespaceMapper() {
    if (window.__apiMapperInstalled) return; window.__apiMapperInstalled = true;
    const originalFetch = window.fetch.bind(window);
    function rewrite(url, options) {
        try {
            const method = (options && options.method ? options.method : 'GET').toUpperCase();
            const u = typeof url === 'string' ? url : (url && url.url) || '';
            if (!u || u.startsWith('http')) return url; // external or absolute

            // New namespaces → legacy endpoints
            if (u.startsWith('/auth/')) {
                if (u === '/auth/validate') return '/api/me';
                if (u === '/auth/login') return '/login';
                if (u === '/auth/logout') return '/logout';
                if (u === '/auth/refresh') return '/api/refresh';
            }
            if (u.startsWith('/view/')) {
                // Don't rewrite HTML, CSS, JS, or image files - serve them as static assets
                if (u.match(/\.(html|css|js|png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|eot)$/i)) {
                    return url; // Keep original URL for static assets
                }
                return '/api/' + u.replace(/^\/view\//, '');
            }
            if (u.startsWith('/create/')) {
                return '/api/' + u.replace(/^\/create\//, '');
            }

        } catch (_) { /* no-op */ }
        return url;
    }
    window.fetch = function (url, options) {
        const rewritten = rewrite(url, options || {});
        const fetchPromise = originalFetch(rewritten, options);
        
        // Silently handle 404 errors for workflow_instances when workflow hasn't started yet
        // This prevents console pollution when CR is in "Pending Start" status
        if (typeof rewritten === 'string' && rewritten.includes('/api/workflow_instances/by-cr/')) {
            return fetchPromise.then(response => {
                // If 404, return a response object that won't cause console errors
                // The browser will still log the 404, but we handle it gracefully
                if (response.status === 404) {
                    // Return a response-like object that can be checked but won't throw
                    return {
                        ok: false,
                        status: 404,
                        statusText: 'Not Found',
                        json: () => Promise.resolve({}),
                        text: () => Promise.resolve(''),
                        headers: new Headers(),
                        clone: () => ({ ok: false, status: 404 })
                    };
                }
                return response;
            }).catch(err => {
                // Silently handle network errors for workflow instances
                return {
                    ok: false,
                    status: 0,
                    statusText: 'Network Error',
                    json: () => Promise.resolve({}),
                    text: () => Promise.resolve(''),
                    headers: new Headers(),
                    clone: () => ({ ok: false, status: 0 })
                };
            });
        }
        
        return fetchPromise;
    };
})();
// Early access guard: allow public pages without auth, restrict others
(async function guardAccess() {
    try {
        const path = window.location.pathname.toLowerCase();
        // Only guard HTML document pages
        const isHtmlDoc = path.endsWith('.html') || path === '/' || path === '';
        if (!isHtmlDoc) return;

        // Publicly accessible pages without login
        const publicPages = new Set([
            '/',
            '/index.html',
            '/login.html',

            '/search.html',
            '/error/permission.html'
        ]);

        // Normalize root to index.html for comparison
        const normalizedPath = path === '/' || path === '' ? '/index.html' : path;
        if (publicPages.has(normalizedPath)) return;

        // Allow all /view/ pages
        if (path.startsWith('/view/') || path.includes('/view/')) return;

        // Check authentication silently - don't log errors for unauthenticated users
        const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
        if (meResp.ok) return;
        if (meResp.status === 401) {
            // Try refresh silently
            try {
                const refresh = await fetch('/api/refresh', { method: 'POST', credentials: 'include' });
                if (refresh.ok) {
                    const meAgain = await fetch('/api/me', { method: 'GET', credentials: 'include' });
                    if (meAgain.ok) return;
                }
            } catch (refreshError) {
                // Silently handle refresh errors
            }
        }
        // Not authenticated → redirect to permission page
        if (!window.location.pathname.endsWith('/error/permission.html')) {
            window.location.replace('/error/permission.html');
        }
    } catch (_) {
        // On any error, fail-closed for non-public pages
        const path = window.location.pathname.toLowerCase();
        const normalizedPath = path === '/' || path === '' ? '/index.html' : path;
        const publicPages = new Set(['/index.html', '/login.html', '/search.html', '/error/permission.html']);

        // Allow all /view/ pages
        if (path.startsWith('/view/') || path.includes('/view/')) return;

        if (!publicPages.has(normalizedPath)) {
            window.location.replace('/error/permission.html');
        }
    }
})();
document.addEventListener('DOMContentLoaded', async function () {
    // Wait for i18n so header and static content are translated before first paint
    if (window.i18nReadyPromise) {
        await window.i18nReadyPromise;
    }
    await injectSharedHeader();
    // Load notification panel CSS and JS if not already loaded
    loadNotificationPanel();

    // Load language switcher CSS and JS if not already loaded
    loadLanguageSwitcher();
    loadEditNotifications();

    // Initialize all core functionality when DOM is fully loaded
    initThemeToggle();
    initMobileMenu();
    initGlobalSearchShortcut();
    initHeaderSearchInput();
    initSmoothScrolling();
    initAnimations();
    initDropdowns();
    // Initialize follow UI (hide for unauthorized users)
    await initializeFollowUI();
    // User profile is now handled by AuthManager in auth.js
});

/**
 * Load global lock UI CSS and JS if not already loaded
 */
function loadGlobalLockUI() {
    // Check if CSS is already loaded
    const cssLink = document.querySelector('link[href*="global-lock-ui.css"]');
    if (!cssLink) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = '/assets/css/global-lock-ui.css';
        document.head.appendChild(link);
    }

    // Check if JS is already loaded
    if (!window.globalLockUI && !document.querySelector('script[src*="global-lock-ui.js"]')) {
        const script = document.createElement('script');
        script.src = '/assets/js/global-lock-ui.js';
        script.async = true;
        document.body.appendChild(script);
    } else if (window.globalLockUI && !window.globalLockUI.isInitialized) {
        // If script is loaded but not initialized, initialize it
        window.globalLockUI.initialize();
    }
}

async function injectSharedHeader() {
    try {
        const headerEl = document.querySelector('header.header');
        if (!headerEl) return;
        // Only inject if header is empty or marked as container
        if (headerEl.getAttribute('data-shared') === 'true') return;

        // Ensure Font Awesome is loaded before injecting header
        await ensureFontAwesomeLoaded();

        const resp = await fetch('/partials/header.html', { cache: 'no-store' });
        if (!resp.ok) return;
        const html = await resp.text();
        headerEl.innerHTML = html;
        headerEl.setAttribute('data-shared', 'true');

        // Force re-application of Font Awesome styles to dynamically injected content
        reapplyFontAwesomeStyles(headerEl);

        // Load global lock UI if not already loaded
        loadGlobalLockUI();

        // Notify listeners that header markup is now available
        try { window.dispatchEvent(new Event('headerReady')); } catch (_) { }

        // Apply translations to the newly injected header
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations();
        }

        // Sub-nav (Home / My Dashboard) is only on main page (index.html); not injected elsewhere
        injectSubNavOnViewPages();

        // After injection, re-initialize header-bound behaviors
        // initHeaderSearchInput will be called automatically via headerReady event listener
        // or will retry if header input is not yet available
    } catch (e) {
        console.warn('Header injection failed:', e);
    }
}

/**
 * Sub-nav (Home / My Dashboard) is only shown on the main page (index.html).
 * We do not inject it on other pages.
 */
function injectSubNavOnViewPages() {
    // Sub-nav visible only on main page (index.html) - do not inject on other pages
    return;
}

// Load notification panel CSS and JS if not already loaded
function loadNotificationPanel() {
    // Check if CSS is already loaded
    if (!document.querySelector('link[href*="notification-panel.css"]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = '/assets/css/notification-panel.css';
        document.head.appendChild(link);
    }

    // Check if JS is already loaded
    if (!document.querySelector('script[src*="notification-panel.js"]') && !window.NotificationPanel) {
        const script = document.createElement('script');
        script.src = '/assets/js/notification-panel.js';
        script.async = true;
        document.body.appendChild(script);
    }
}

function loadEditNotifications() {
    const path = (window.location.pathname || '').toLowerCase();
    const isEditPage = path.includes('-edit.html') || path.includes('/view/') && path.includes('/edit');
    if (!isEditPage) return;

    if (!document.querySelector('link[href*="toast.css"]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = '/assets/css/toast.css';
        document.head.appendChild(link);
    }

    if (!document.querySelector('script[src*="admin-notifications.js"]') && typeof window.showNotification !== 'function') {
        const script = document.createElement('script');
        script.src = '/assets/js/admin-panel/admin-notifications.js';
        script.async = false;
        document.body.appendChild(script);
    }
}

// Load language switcher CSS and JS if not already loaded
function loadLanguageSwitcher() {
    // Derive base path from main.js location to support context paths
    let basePath = '';
    const mainScript = document.querySelector('script[src*="main.js"]');
    if (mainScript) {
        // e.g. http://localhost:8080/ctx/assets/js/main.js
        const src = mainScript.src;
        // We want to go up from js/main.js to assets/
        // If main.js is at .../assets/js/main.js, we want .../assets/
        const parts = src.split('/');
        // Remove 'main.js'
        parts.pop();
        // Remove 'js'
        if (parts.length > 0 && parts[parts.length - 1] === 'js') {
            parts.pop();
        }
        basePath = parts.join('/') + '/';
    } else {
        basePath = '/assets/';
    }

    // Check if CSS is already loaded
    if (!document.querySelector('link[href*="language-switcher.css"]')) {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = basePath + 'css/language-switcher.css';
        document.head.appendChild(link);
    }

    // Check if JS is already loaded
    if (!document.querySelector('script[src*="language-switcher.js"]')) {
        const script = document.createElement('script');
        script.src = basePath + 'js/language-switcher.js';
        script.async = true;
        document.body.appendChild(script);
    }
}

// Function to ensure Font Awesome is loaded
async function ensureFontAwesomeLoaded() {
    return new Promise((resolve) => {
        // Check if Font Awesome is already loaded
        const fontAwesomeLink = document.querySelector('link[href*="font-awesome"]');
        if (fontAwesomeLink) {
            // Check if the styles are actually applied by testing an icon
            const testIcon = document.createElement('i');
            testIcon.className = 'fas fa-check';
            testIcon.style.position = 'absolute';
            testIcon.style.left = '-9999px';
            testIcon.style.visibility = 'hidden';
            document.body.appendChild(testIcon);

            // Wait for the next frame to ensure styles are applied
            requestAnimationFrame(() => {
                const computedStyle = window.getComputedStyle(testIcon, '::before');
                const hasContent = computedStyle.content && computedStyle.content !== 'none' && computedStyle.content !== '""';
                document.body.removeChild(testIcon);

                if (hasContent) {
                    resolve();
                } else {
                    // If Font Awesome is not working, wait a bit more
                    setTimeout(resolve, 200);
                }
            });
        } else {
            // If Font Awesome link is not found, wait a bit
            setTimeout(resolve, 100);
        }
    });
}

// Function to reapply Font Awesome styles to dynamically injected content
function reapplyFontAwesomeStyles(container) {
    // Force browser to re-evaluate styles for the container
    const icons = container.querySelectorAll('.fas, .far, .fab, .fal, .fad');
    icons.forEach(icon => {
        // Trigger a reflow to ensure styles are applied
        icon.style.display = 'none';
        icon.offsetHeight; // Force reflow
        icon.style.display = '';

        // Add fallback text if icon doesn't load
        setTimeout(() => {
            const computedStyle = window.getComputedStyle(icon, '::before');
            const hasContent = computedStyle.content && computedStyle.content !== 'none' && computedStyle.content !== '""';

            if (!hasContent && !icon.getAttribute('data-fallback')) {
                // Add fallback text based on icon class
                const fallbackText = getIconFallback(icon.className);
                if (fallbackText) {
                    icon.setAttribute('data-fallback', fallbackText);
                    icon.title = fallbackText;

                    // Apply professional styling to fallback icons
                    icon.style.fontStyle = 'normal';
                    icon.style.fontWeight = '600';
                    icon.style.fontSize = '0.9em';
                    icon.style.lineHeight = '1';
                    icon.style.display = 'inline-flex';
                    icon.style.alignItems = 'center';
                    icon.style.justifyContent = 'center';
                    icon.style.minWidth = '1em';
                    icon.style.height = '1em';
                    icon.style.color = 'inherit';
                    icon.style.opacity = '0.8';
                    icon.style.transition = 'opacity 0.2s ease';

                    // Add hover effect
                    icon.addEventListener('mouseenter', () => {
                        icon.style.opacity = '1';
                    });
                    icon.addEventListener('mouseleave', () => {
                        icon.style.opacity = '0.8';
                    });

                    icon.textContent = fallbackText;
                }
            }
        }, 100);
    });
}

// Function to get fallback text for icons
function getIconFallback(className) {
    const iconMap = {
        'fa-database': '◉',
        'fa-search': '⌕',
        'fa-map': '🗺',
        'fa-box': '⊞',
        'fa-chevron-down': '▼',
        'fa-plus': '⊕',
        'fa-users': '👥',
        'fa-file-alt': '📄',
        'fa-sync-alt': '↻',
        'fa-folder': '📁',
        'fa-desktop': '🖥',
        'fa-plug': '🔌',
        'fa-book': '📖',
        'fa-building': '🏢',
        'fa-tools': '⚒',
        'fa-user': '👤',
        'fa-gavel': '⚖',
        'fa-sitemap': '🗺',
        'fa-balance-scale': '⚖',
        'fa-file-contract': '📋',
        'fa-landmark': '🏛',
        'fa-globe': '🌐',
        'fa-upload': '⬆',
        'fa-moon': '🌙',
        'fa-bell': '🔔',
        'fa-cog': '⚙',
        'fa-sign-out-alt': '↪',
        'fa-bars': '☰',
        'fa-eye': '👁',
        'fa-undo': '↶',
        'fa-save': '💾'
    };

    for (const [iconClass, fallback] of Object.entries(iconMap)) {
        if (className.includes(iconClass)) {
            return fallback;
        }
    }
    return null;
}

// Initialize theme toggle functionality
function initThemeToggle() {
    const themeToggle = document.getElementById('themeToggle');
    const html = document.documentElement;

    // Load saved theme preference from localStorage or default to light
    const savedTheme = localStorage.getItem('budg-theme') || 'light';
    html.setAttribute('data-theme', savedTheme);
    updateThemeIcon(savedTheme);

    function performThemeToggle() {
        const currentTheme = html.getAttribute('data-theme');
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        html.setAttribute('data-theme', newTheme);
        updateThemeIcon(newTheme);
        localStorage.setItem('budg-theme', newTheme);
        window.dispatchEvent(new CustomEvent('themeChanged', { detail: { theme: newTheme } }));
    }

    if (themeToggle) {
        themeToggle.addEventListener('click', performThemeToggle);
    }
    const profileThemeItem = document.getElementById('profileThemeItem');
    if (profileThemeItem) {
        profileThemeItem.addEventListener('click', function (e) {
            e.preventDefault();
            performThemeToggle();
        });
    }

    // Listen for theme changes from other browser tabs/windows
    window.addEventListener('storage', function (e) {
        if (e.key === 'budg-theme') {
            const newTheme = e.newValue || 'light';
            html.setAttribute('data-theme', newTheme);
            updateThemeIcon(newTheme);
        }
    });
}

// Update theme icon based on current theme
function updateThemeIcon(theme) {
    const themeToggle = document.getElementById('themeToggle');
    if (themeToggle) {
        const icon = themeToggle.querySelector('i');
        if (icon) {
            if (theme === 'dark') {
                icon.className = 'fas fa-sun';
            } else {
                icon.className = 'fas fa-moon';
            }
        }
    }
    const profileThemeItem = document.getElementById('profileThemeItem');
    if (profileThemeItem) {
        const icon = profileThemeItem.querySelector('i');
        if (icon) {
            if (theme === 'dark') {
                icon.className = 'fas fa-sun';
            } else {
                icon.className = 'fas fa-moon';
            }
        }
    }
}

// Initialize mobile menu functionality
function initMobileMenu() {
    const mobileToggle = document.getElementById('mobileMenuToggle');
    const navCenter = document.querySelector('.nav-center');
    const header = document.querySelector('.header');

    if (!mobileToggle || !navCenter || !header) {
        return;
    }
    if (mobileToggle.dataset.bound === '1') {
        return;
    }
    mobileToggle.dataset.bound = '1';

    function setMobileNavOpen(open) {
        header.classList.toggle('nav-mobile-open', open);
        const icon = mobileToggle.querySelector('i');
        if (icon) {
            icon.className = open ? 'fas fa-times' : 'fas fa-bars';
        }
        document.body.style.overflow = open ? 'hidden' : '';
    }

    function closeMobileNav() {
        if (!header.classList.contains('nav-mobile-open')) return;
        setMobileNavOpen(false);
        const createDd = document.getElementById('createDropdown');
        const myItemsDd = document.getElementById('myItemsDropdown');
        if (createDd) {
            createDd.classList.remove('show');
            createDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (el) {
                el.classList.remove('active');
            });
        }
        if (myItemsDd) {
            myItemsDd.classList.remove('show');
            myItemsDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (el) {
                el.classList.remove('active');
            });
        }
    }

    mobileToggle.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        const open = !header.classList.contains('nav-mobile-open');
        setMobileNavOpen(open);
    });

    document.addEventListener('click', function (e) {
        if (!header.classList.contains('nav-mobile-open')) return;
        if (header.contains(e.target)) return;
        closeMobileNav();
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            closeMobileNav();
        }
    });

    window.addEventListener('resize', function () {
        if (window.innerWidth > 768) {
            closeMobileNav();
        }
    });

    navCenter.addEventListener('click', function (e) {
        if (e.target.closest('a.sub-dropdown-item[href^="/"]')) {
            closeMobileNav();
            return;
        }
        if (e.target.closest('a.nav-link[href^="/"]')) {
            closeMobileNav();
        }
    });
}

// Initialize global handler to open search page when clicking any search icon
function initGlobalSearchShortcut() {
    // Use event delegation but scope to header only to avoid conflicts with page-level search UIs
    document.addEventListener('click', function (e) {
        const searchLink = e.target.closest('header a.nav-link[href$="search.html"], header a[href="/search.html"], header .fa-search');
        if (searchLink) {
            e.preventDefault();
            const headerInput = document.querySelector('header .search-input');
            const q = headerInput ? headerInput.value.trim() : '';
            if (location.pathname.endsWith('/search.html') || location.pathname.endsWith('search.html')) {
                // On search page: show live results behavior only; just sync/focus
                const input = document.querySelector('header .search-input') || document.querySelector('.search-input');
                if (input) { input.value = q; input.focus(); }
            } else {
                const url = '/search.html' + (q ? `?q=${encodeURIComponent(q)}` : '');
                window.location.href = url;
            }
        }
    });
}

// Enter on header search input should search in-place on search page, or navigate with q elsewhere
function initHeaderSearchInput() {
    const headerInput = document.querySelector('header .search-input');
    if (!headerInput) {
        // Retry after a short delay
        setTimeout(initHeaderSearchInput, 100);
        return;
    }

    // Prevent duplicate initialization
    if (headerInput.dataset.initialized === 'true') {
        return;
    }
    headerInput.dataset.initialized = 'true';

    headerInput.addEventListener('keypress', (e) => {
        if (e.key !== 'Enter') return;
        e.preventDefault(); // Prevent form submission or default behavior
        const q = headerInput.value.trim();

        if (location.pathname.endsWith('/search.html') || location.pathname.endsWith('search.html')) {
            // On search page: rely on live search; no content search trigger
            const input = document.querySelector('header .search-input') || document.querySelector('.search-input');
            if (input) {
                input.focus();
                // Trigger search if there's a query
                if (q) {
                    // Dispatch a custom event for live search
                    const searchEvent = new CustomEvent('searchQuery', { detail: { query: q } });
                    document.dispatchEvent(searchEvent);
                }
            }
        } else {
            // Not on search page: show dropdown with all matching items instead of navigating
            if (q) {
                // Dispatch search event to trigger search and show dropdown
                const searchEvent = new CustomEvent('searchQuery', { detail: { query: q } });
                document.dispatchEvent(searchEvent);
                
                // Ensure dropdown is visible after search
                setTimeout(() => {
                    const container = document.querySelector('.live-search-results');
                    if (container) {
                        container.style.display = 'block';
                    }
                }, 100);
            }
        }
    });

    // Add click handler to focus the input
    const searchContainer = document.querySelector('header .search-container');
    if (searchContainer) {
        searchContainer.addEventListener('click', () => {
            headerInput.focus();
        });
    }
}

// Search is now handled by assets/js/initSearch.js

// Initialize smooth scrolling for anchor links
function initSmoothScrolling() {
    const links = document.querySelectorAll('a[href^="#"]');

    links.forEach(link => {
        link.addEventListener('click', function (e) {
            const targetId = this.getAttribute('href');

            // Ignore placeholder links like "#" or empty
            if (!targetId || targetId === '#' || targetId === '#!') {
                return; // allow normal behavior; do not log
            }

            // Only handle real in-page anchors
            if (targetId.startsWith('#') && targetId.length > 1) {
                const targetElement = document.querySelector(targetId);
                if (!targetElement) {
                    console.warn('Target element not found:', targetId);
                    return;
                }
                e.preventDefault();
                targetElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
    });
}

// Initialize animations for page elements (hero shows in place immediately, no "run from bottom to top")
function initAnimations() {
    // Hero elements stay visible in final position from first paint (no fade-in-up on load)
    const heroElements = document.querySelectorAll('.animate-fade-in-up');
    heroElements.forEach((el) => {
        el.style.opacity = '1';
        el.style.transform = 'translateY(0)';
    });
}

// Object links helper: get "linked to" summary for any facet (used by My Items hover). Uses existing impact APIs.
window.BUDG_OBJECT_LINKS = window.BUDG_OBJECT_LINKS || (function () {
    var FACET_LINK_CONFIG = {
        policy: { apiPrefix: 'policy-impact', links: [{ path: 'processes', label: 'Process', nameKey: 'processName' }, { path: 'projects', label: 'Project', nameKey: 'projectName' }, { path: 'systems', label: 'System', nameKey: 'systemName' }, { path: 'products', label: 'Product', nameKey: 'productName' }, { path: 'clients', label: 'Client', nameKey: 'clientName' }, { path: 'businessareas', label: 'Business Area', nameKey: 'businessAreaName' }, { path: 'legals', label: 'Legal Entity', nameKey: 'legalName' }, { path: 'datasets', label: 'Data Set', nameKey: 'datasetName' }, { path: 'attributes', label: 'Data Attribute', nameKey: 'attributeName' }, { path: 'glossaries', label: 'Glossary', nameKey: 'glossaryName' }] },
        process: { apiPrefix: 'process-impact', links: [{ path: 'systems', label: 'System', nameKey: 'systemName' }, { path: 'products', label: 'Product', nameKey: 'productName' }, { path: 'clients', label: 'Client', nameKey: 'clientName' }, { path: 'glossaries', label: 'Glossary', nameKey: 'glossaryName' }, { path: 'projects', label: 'Project', nameKey: 'projectName' }, { path: 'policies', label: 'Policy', nameKey: 'policyName' }, { path: 'interfaces', label: 'System Interface', nameKey: 'interfaceName' }, { path: 'legals', label: 'Legal Entity', nameKey: 'legalName' }, { path: 'datasets', label: 'Data Set', nameKey: 'datasetName' }, { path: 'attributes', label: 'Data Attribute', nameKey: 'attributeName' }] },
        project: { apiPrefix: 'project-impact', links: [{ path: 'systems', label: 'System', nameKey: 'systemName' }, { path: 'processes', label: 'Process', nameKey: 'processName' }, { path: 'glossaries', label: 'Glossary', nameKey: 'glossaryName' }, { path: 'policies', label: 'Policy', nameKey: 'policyName' }, { path: 'products', label: 'Product', nameKey: 'productName' }, { path: 'clients', label: 'Client', nameKey: 'clientName' }, { path: 'capabilities', label: 'Capability', nameKey: 'capabilityName' }, { path: 'businessareas', label: 'Business Area', nameKey: 'businessAreaName' }, { path: 'datasets', label: 'Data Set', nameKey: 'datasetName' }, { path: 'attributes', label: 'Data Attribute', nameKey: 'attributeName' }] },
        system: { apiPrefix: 'system-impact', links: [{ path: 'products', label: 'Product', nameKey: 'productName' }, { path: 'clients', label: 'Client', nameKey: 'clientName' }, { path: 'legals', label: 'Legal Entity', nameKey: 'legalName' }] },
        dataset: { apiPrefix: 'dataset-impact', links: [{ path: 'products', label: 'Product', nameKey: 'productName' }, { path: 'clients', label: 'Client', nameKey: 'clientName' }, { path: 'legals', label: 'Legal Entity', nameKey: 'legalName' }] },
        glossary: { apiPrefix: 'glossary-impact', links: [{ path: 'products', label: 'Product', nameKey: 'productName' }, { path: 'clients', label: 'Client', nameKey: 'clientName' }] },
        product: { apiPrefix: 'product-impact', links: [{ path: 'legals', label: 'Legal Entity', nameKey: 'legalName' }, { path: 'clients', label: 'Client', nameKey: 'clientName' }, { path: 'businessareas', label: 'Business Area', nameKey: 'businessAreaName' }] },
        capability: { apiPrefix: 'capability-impact', links: [{ path: 'systems', label: 'System', nameKey: 'systemName' }, { path: 'clients', label: 'Client', nameKey: 'clientName' }, { path: 'products', label: 'Product', nameKey: 'productName' }, { path: 'processes', label: 'Process', nameKey: 'processName' }, { path: 'glossaries', label: 'Glossary', nameKey: 'glossaryName' }, { path: 'businessareas', label: 'Business Area', nameKey: 'businessAreaName' }, { path: 'legals', label: 'Legal Entity', nameKey: 'legalName' }, { path: 'projects', label: 'Project', nameKey: 'projectName' }] },
        businessarea: { apiPrefix: 'businessarea-impact', links: [{ path: 'glossaries', label: 'Glossary', nameKey: 'glossaryName' }, { path: 'systems', label: 'System', nameKey: 'systemName' }, { path: 'processes', label: 'Process', nameKey: 'processName' }] },
        business_area: { apiPrefix: 'businessarea-impact', links: [{ path: 'glossaries', label: 'Glossary', nameKey: 'glossaryName' }, { path: 'systems', label: 'System', nameKey: 'systemName' }, { path: 'processes', label: 'Process', nameKey: 'processName' }] },
        regulation: { apiPrefix: 'regulation-impact', links: [{ path: 'products', label: 'Product', nameKey: 'productName' }, { path: 'policies', label: 'Policy', nameKey: 'policyName' }, { path: 'projects', label: 'Project', nameKey: 'projectName' }, { path: 'regulatorythemes', label: 'Regulatory Theme', nameKey: 'regulatoryThemeName' }] },
        legalentity: { apiPrefix: 'legal-impact', links: [{ path: 'geographies', label: 'Geography', nameKey: 'geographyName' }] },
        legal_entity: { apiPrefix: 'legal-impact', links: [{ path: 'geographies', label: 'Geography', nameKey: 'geographyName' }] }
    };
    function normalizeFacet(entityType) {
        if (!entityType) return '';
        var s = String(entityType).toLowerCase().replace(/-/g, '_');
        if (s === 'legal_entity' || s === 'legalentity') return 'legalentity';
        if (s === 'business_area') return 'businessarea';
        return s.replace(/_/g, '');
    }
    function getConfigForFacet(facet) {
        var normalized = normalizeFacet(facet);
        return FACET_LINK_CONFIG[normalized] || null;
    }
    function itemDisplayName(item, nameKey) {
        if (!item) return '';
        if (nameKey && item[nameKey] != null) return item[nameKey];
        var keys = ['name', 'title', 'displayName', 'processName', 'systemName', 'projectName', 'productName', 'clientName', 'businessAreaName', 'legalName', 'legalShortName', 'legalLongName', 'datasetName', 'attributeName', 'glossaryName', 'policyName', 'capabilityName', 'regulatoryThemeName', 'geographyName', 'interfaceName'];
        for (var i = 0; i < keys.length; i++) {
            if (item[keys[i]] != null && item[keys[i]] !== '') return item[keys[i]];
        }
        return '#' + (item.id || item.ID || item.processId || item.systemId || item.projectId || '');
    }
    function getObjectLinksSummary(facet, objectId) {
        var config = getConfigForFacet(facet);
        if (!config || !config.links || config.links.length === 0) return Promise.resolve({});
        var base = '/api/' + config.apiPrefix + '/' + objectId + '/';
        var promises = config.links.map(function (link) {
            return fetch(base + link.path, { credentials: 'include' })
                .then(function (r) { return r.ok ? r.json() : []; })
                .then(function (data) {
                    var list = Array.isArray(data) ? data : (data && data.data) ? data.data : (data && data.items) ? data.items : [];
                    var items = list.map(function (item) {
                        // Derive entity ID key from nameKey (e.g. productName → productId)
                        // The 'id' field in impact APIs is the junction table row ID, NOT the entity ID
                        var entityIdKey = link.nameKey ? link.nameKey.replace(/Name$/, 'Id') : null;
                        var entityId = (entityIdKey && item[entityIdKey] != null) ? item[entityIdKey] : (item.id || item.ID);
                        return { name: itemDisplayName(item, link.nameKey), id: entityId };
                    });
                    return { label: link.label, count: items.length, items: items };
                })
                .catch(function () { return { label: link.label, count: 0, items: [] }; });
        });
        return Promise.all(promises).then(function (results) {
            var out = {};
            results.forEach(function (r) {
                if (r.count > 0) out[r.label] = { count: r.count, items: r.items };
            });
            return out;
        });
    }
    return { getObjectLinksSummary: getObjectLinksSummary, getConfigForFacet: getConfigForFacet, normalizeFacet: normalizeFacet };
})();

// Initialize dropdown menus
function initDropdowns() {
    const createDropdownToggle = document.getElementById('createDropdownToggle');
    const createDropdown = document.getElementById('createDropdown');
    const myItemsToggle = document.getElementById('myItemsDropdownToggle');
    const myItemsDropdown = document.getElementById('myItemsDropdown');
    const myItemsRecent = document.getElementById('myItemsRecentSubmenu');
    const myItemsFollowing = document.getElementById('myItemsFollowingSubmenu');
    const myItemsRoles = document.getElementById('myItemsRolesSubmenu');

    if (createDropdownToggle && createDropdown) {
        const DROPDOWN_EDGE_PADDING = 8;

        function placeCreateDropdown() {
            createDropdown.classList.remove('align-right');
            createDropdown.style.left = '0';
            createDropdown.style.right = 'auto';

            const rect = createDropdown.getBoundingClientRect();
            if (rect.right > window.innerWidth - DROPDOWN_EDGE_PADDING) {
                createDropdown.classList.add('align-right');
                createDropdown.style.left = 'auto';
                createDropdown.style.right = '0';
            }
        }

        function placeCreateSubmenu(menuItem) {
            if (!menuItem) return;
            const subMenu = menuItem.querySelector('.sub-dropdown-menu');
            if (!subMenu) return;

            subMenu.classList.remove('open-left');
            subMenu.style.left = '100%';
            subMenu.style.right = 'auto';

            let rect = subMenu.getBoundingClientRect();
            if (rect.right > window.innerWidth - DROPDOWN_EDGE_PADDING) {
                subMenu.classList.add('open-left');
                subMenu.style.left = 'auto';
                subMenu.style.right = '100%';
                rect = subMenu.getBoundingClientRect();
            }

            // If still clipped on very small widths, keep it fully visible by overlapping parent.
            if (rect.left < DROPDOWN_EDGE_PADDING) {
                subMenu.classList.remove('open-left');
                subMenu.style.left = '0';
                subMenu.style.right = 'auto';
            }
        }

        function placeAllActiveCreateSubmenus() {
            createDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (menuItem) {
                placeCreateSubmenu(menuItem);
            });
        }

        // Toggle Create dropdown visibility on click
        createDropdownToggle.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            const isOpening = !createDropdown.classList.contains('show');
            createDropdown.classList.toggle('show');
            if (isOpening) {
                requestAnimationFrame(function () {
                    placeCreateDropdown();
                    placeAllActiveCreateSubmenus();
                });
                // Close other nav dropdown
                if (myItemsDropdown) {
                    myItemsDropdown.classList.remove('show');
                    myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active'));
                }
                // Close header panels
                if (window.notificationPanel && typeof window.notificationPanel.closePanel === 'function') window.notificationPanel.closePanel();
                if (window.globalSegmentsCubePanel && typeof window.globalSegmentsCubePanel.closePanel === 'function') window.globalSegmentsCubePanel.closePanel();
                if (window.globalLockUI && typeof window.globalLockUI.closePanel === 'function') window.globalLockUI.closePanel();
                const profileDropdown = document.getElementById('profileDropdown');
                if (profileDropdown) profileDropdown.classList.remove('show');
            } else {
                // Closing dropdown — also close all submenus
                createDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (item) {
                    item.classList.remove('active');
                });
            }
        });

        // Submenu click handler for Create dropdown categories
        createDropdown.querySelectorAll('.dropdown-item.has-submenu').forEach(function (menuItem) {
            menuItem.addEventListener('click', function (e) {
                // Don't interfere if clicking an actual link inside the submenu
                if (e.target.closest('.sub-dropdown-item')) return;

                e.preventDefault();
                e.stopPropagation();

                const isActive = this.classList.contains('active');

                // Close all sibling submenus first
                createDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (item) {
                    item.classList.remove('active');
                });

                // Toggle this submenu
                if (!isActive) {
                    this.classList.add('active');
                    requestAnimationFrame(() => placeCreateSubmenu(this));
                }
            });

            menuItem.addEventListener('mouseenter', function () {
                requestAnimationFrame(() => placeCreateSubmenu(this));
            });
        });

        // Close dropdown when clicking outside
        document.addEventListener('click', function (e) {
            if (!createDropdownToggle.contains(e.target) && !createDropdown.contains(e.target)) {
                createDropdown.classList.remove('show');
                // Also close all submenus
                createDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (item) {
                    item.classList.remove('active');
                });
            }
        });

        // Close dropdown when pressing Escape key
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                createDropdown.classList.remove('show');
                createDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (item) {
                    item.classList.remove('active');
                });
            }
        });

        window.addEventListener('resize', function () {
            if (!createDropdown.classList.contains('show')) return;
            placeCreateDropdown();
            placeAllActiveCreateSubmenus();
        });
    }

    let recentItems = [];
    let recentFilteredItems = null;
    let recentSelectedFacet = 'All';

    if (myItemsToggle && myItemsDropdown) {
        let currentPage = 1;
        const itemsPerPage = 10;

        // Function to render recent items with pagination
        function renderRecentItems(page = 1) {
            const container = myItemsDropdown.querySelector('.recent-items');
            const paginationDots = myItemsDropdown.querySelector('#recentPaginationDots');

            if (!container) return;

            currentPage = page;

            // Use filtered items if available, otherwise use full list
            const itemsToRender = recentFilteredItems !== null ? recentFilteredItems : recentItems;

            if (!itemsToRender || itemsToRender.length === 0) {
                container.innerHTML = '<div class="sub-dropdown-item empty">No recent items</div>';
                if (paginationDots) paginationDots.style.display = 'none';
                return;
            }

            const startIndex = (page - 1) * itemsPerPage;
            const endIndex = startIndex + itemsPerPage;
            const pageItems = itemsToRender.slice(startIndex, endIndex);

            // Entity-to-icon mapping for recent items
            const entityIconMap = {
                'System': 'fas fa-server',
                'Process': 'fas fa-cogs',
                'CatalogueItem': 'fas fa-database',
                'CatItemCategory': 'fas fa-book',
                'InvolvedParty': 'fas fa-user',
                'Interface': 'fas fa-exchange-alt',
                'SystemInterface': 'fas fa-exchange-alt',
                'SystemXSystem': 'fas fa-exchange-alt',
                'Product': 'fas fa-box',
                'OrgUnit': 'fas fa-sitemap',
                'Capability': 'fas fa-lightbulb',
                'Client': 'fas fa-handshake',
                'Committee': 'fas fa-users',
                'Legal': 'fas fa-balance-scale',
                'LegalEntity': 'fas fa-balance-scale',
                'Policy': 'fas fa-file-alt',
                'Project': 'fas fa-project-diagram',
                'BusinessArea': 'fas fa-briefcase',
                'BusinessConnection': 'fas fa-briefcase',
                'Regulation': 'fas fa-gavel',
                'Regulator': 'fas fa-landmark',
                'RegulatoryTheme': 'fas fa-shield-alt',
                'Geography': 'fas fa-globe',
                'Role': 'fas fa-user-tag',
                'ChangeRequest': 'fas fa-clipboard-check',
                'Attribute': 'fas fa-tag'
            };

            container.innerHTML = pageItems.map(i => {
                const route = (i.route || '').replace(/^\/api\//, '/');
                const name = i.display_name || (i.entity + ' #' + i.entity_id);
                const entity = i.entity || 'unknown';
                const oid = (i.entity_id != null && i.entity_id !== '') ? i.entity_id : '';
                const iconClass = entityIconMap[entity] || 'fas fa-clock';
                return `<a href=\"${route}\" class=\"sub-dropdown-item\" data-entity-type=\"${entity}\" data-object-id=\"${oid}\" data-item-name=\"${(name || '').replace(/"/g, '&quot;')}\"><i class=\"${iconClass}\"></i> ${name}</a>`;
            }).join('') || '<div class=\"sub-dropdown-item empty\">No recent items</div>';

            // Show/hide pagination dots based on total items
            if (itemsToRender.length > itemsPerPage) {
                if (paginationDots) {
                    paginationDots.style.display = 'flex';
                    updatePaginationDots(page, Math.ceil(itemsToRender.length / itemsPerPage));
                }
            } else {
                if (paginationDots) paginationDots.style.display = 'none';
            }
        }

        // Function to update pagination dots
        function updatePaginationDots(currentPage, totalPages) {
            const paginationDots = myItemsDropdown.querySelector('#recentPaginationDots');
            if (!paginationDots) return;

            // Clear existing dots
            paginationDots.innerHTML = '';

            // Create dots for each page
            for (let i = 1; i <= totalPages; i++) {
                const dot = document.createElement('div');
                dot.className = `pagination-dot ${i === currentPage ? 'active' : ''}`;
                dot.setAttribute('data-page', i);
                paginationDots.appendChild(dot);
            }
        }

        // Use event delegation for all pagination dots in My Items dropdown
        myItemsDropdown.addEventListener('click', function (e) {
            // Recent pagination
            const recentDot = e.target.closest('#recentPaginationDots .pagination-dot');
            if (recentDot && !recentDot.classList.contains('active')) {
                e.preventDefault();
                e.stopPropagation();
                const page = parseInt(recentDot.getAttribute('data-page'));
                if (page && page !== currentPage) {
                    currentPage = page;
                    renderRecentItems(page);
                }
                return;
            }

            // Following pagination
            const followingDot = e.target.closest('#followingPaginationDots .pagination-dot');
            if (followingDot && !followingDot.classList.contains('active')) {
                e.preventDefault();
                e.stopPropagation();
                const page = parseInt(followingDot.getAttribute('data-page'));
                if (page && page !== followingCurrentPage) {
                    followingCurrentPage = page;
                    renderFollowingItems(page);
                }
                return;
            }

            // Roles pagination
            const rolesDot = e.target.closest('#rolesPaginationDots .pagination-dot');
            if (rolesDot && !rolesDot.classList.contains('active')) {
                e.preventDefault();
                e.stopPropagation();
                const page = parseInt(rolesDot.getAttribute('data-page'));
                if (page && page !== rolesCurrentPage) {
                    rolesCurrentPage = page;
                    renderRolesItems(page);
                }
                return;
            }
        });

        myItemsToggle.addEventListener('click', function (e) {
            e.preventDefault(); e.stopPropagation();
            
            const isOpening = !myItemsDropdown.classList.contains('show');
            
            // Toggle dropdown
            myItemsDropdown.classList.toggle('show');

            if (isOpening) {
                // Close other nav dropdown
                if (createDropdown) {
                    createDropdown.classList.remove('show');
                    createDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active'));
                }
                // Close header panels
                if (window.notificationPanel && typeof window.notificationPanel.closePanel === 'function') window.notificationPanel.closePanel();
                if (window.globalSegmentsCubePanel && typeof window.globalSegmentsCubePanel.closePanel === 'function') window.globalSegmentsCubePanel.closePanel();
                if (window.globalLockUI && typeof window.globalLockUI.closePanel === 'function') window.globalLockUI.closePanel();
                const profileDropdown = document.getElementById('profileDropdown');
                if (profileDropdown) profileDropdown.classList.remove('show');
            }
            
            if (isOpening) {
                // Close all submenus - none should be open by default
                myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(item => {
                    item.classList.remove('active');
                });
                
                // Pre-fetch recent data in background so it's ready on hover
                (async () => {
                    try {
                        const list = await window.BUDG_API_SERVICE.getRecentVisits(1, 30);
                        recentItems = Array.isArray(list) ? list : (list && list.data) ? list.data : [];
                        recentFilteredItems = null;
                        recentSelectedFacet = 'All';
                        currentPage = 1;
                        window.recentItems = recentItems;
                        // Reset filter input
                        const filterInput = document.getElementById('recentFilter');
                        if (filterInput) filterInput.value = '';
                        // Reset scope button text
                        const scopeBtn = document.getElementById('recentScopeBtn');
                        if (scopeBtn) {
                            const span = scopeBtn.querySelector('span');
                            if (span) span.textContent = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t('myItems.inAll') : 'in All';
                        }
                        setupRecentFilter();
                        // Only render if Recent submenu is currently open
                        const recentMenuItem = myItemsDropdown.querySelector('.dropdown-item.has-submenu[data-submenu="my-items-recent"]');
                        if (recentMenuItem && recentMenuItem.classList.contains('active')) {
                            renderRecentItems(1);
                        }
                    } catch (_) {
                        recentItems = [];
                    }
                })();
            } else {
                // Closing dropdown - also close all submenus
                myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(item => {
                    item.classList.remove('active');
                });
            }
        });
        document.addEventListener('click', function (e) {
            // Check if click is inside the main dropdown or toggle button
            const isInsideDropdown = myItemsToggle.contains(e.target) || myItemsDropdown.contains(e.target);
            
            // Check if click is inside any facet dropdown menu (filter dropdown)
            const clickedFacetDropdown = e.target.closest('.facet-dropdown-menu');
            const clickedFilterBtn = e.target.closest('.filter-dropdown-btn');
            
            // Check if click is inside the linked-to popovers
            const clickedPopover = e.target.closest('#myItemsLinkedToPopover') || e.target.closest('#myItemsLinkedToSubPopover');
            
            // Don't close if clicking inside dropdown, toggle, facet dropdown, filter button, or popovers
            if (!isInsideDropdown && !clickedFacetDropdown && !clickedFilterBtn && !clickedPopover) {
                myItemsDropdown.classList.remove('show');
                // Close all submenus
                myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(item => {
                    item.classList.remove('active');
                });
                // Close all facet dropdowns
                document.querySelectorAll('.facet-dropdown-menu').forEach(dd => {
                    dd.style.display = 'none';
                });
                document.querySelectorAll('.filter-dropdown-btn .fa-chevron-down').forEach(icon => {
                    icon.style.transform = 'rotate(0deg)';
                });
            }
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                myItemsDropdown.classList.remove('show');
                // Close all facet dropdowns
                document.querySelectorAll('.facet-dropdown-menu').forEach(dd => {
                    dd.style.display = 'none';
                });
                document.querySelectorAll('.filter-dropdown-btn .fa-chevron-down').forEach(icon => {
                    icon.style.transform = 'rotate(0deg)';
                });
                // Close all submenus
                document.querySelectorAll('.dropdown-item.has-submenu.active').forEach(item => {
                    item.classList.remove('active');
                });
            }
        });

        // Close dropdown when window loses focus (e.g. user switched tab) so subtabs don't stick
        window.addEventListener('blur', function () {
            if (myItemsDropdown.classList.contains('show')) {
                myItemsDropdown.classList.remove('show');
            }
        });

        // Submenu hover handler (Recent, Following, Roles) - open on hover, accordion style
        myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu').forEach(function (menuItem) {
            menuItem.addEventListener('mouseenter', function () {
                const alreadyActive = this.classList.contains('active');
                
                // Close ALL other submenus first (accordion) and hide their facet dropdowns so they don't stick
                myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (item) {
                    item.classList.remove('active');
                });
                myItemsDropdown.querySelectorAll('.facet-dropdown-menu').forEach(function (dd) {
                    dd.style.display = 'none';
                });
                myItemsDropdown.querySelectorAll('.filter-dropdown-btn .fa-chevron-down').forEach(function (icon) {
                    icon.style.transform = 'rotate(0deg)';
                });
                
                // Open this submenu
                this.classList.add('active');
                
                // Load data when opening submenu (only if it wasn't already active)
                if (!alreadyActive) {
                    const submenuId = this.getAttribute('data-submenu');
                    if (submenuId === 'my-items-recent') {
                        // Render recent items (data already pre-fetched)
                        const container = myItemsDropdown.querySelector('.recent-items');
                        if (container && recentItems && recentItems.length > 0) {
                            renderRecentItems(1);
                        } else if (container) {
                            container.innerHTML = '<div class="sub-dropdown-item empty">Loading...</div>';
                        }
                    } else if (submenuId === 'my-items-following') {
                        renderFollowingItems(followingCurrentPage);
                    } else if (submenuId === 'my-items-roles') {
                        renderRolesItems(rolesCurrentPage);
                    }
                }
            });
            
            // Click: desktop blocks only; mobile toggles accordion (no hover)
            menuItem.addEventListener('click', function (e) {
                if (e.target.closest('.sub-dropdown-menu')) return;
                e.preventDefault();
                e.stopPropagation();
                if (window.innerWidth > 768) return;

                const alreadyActive = this.classList.contains('active');
                myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (item) {
                    item.classList.remove('active');
                });
                myItemsDropdown.querySelectorAll('.facet-dropdown-menu').forEach(function (dd) {
                    dd.style.display = 'none';
                });
                myItemsDropdown.querySelectorAll('.filter-dropdown-btn .fa-chevron-down').forEach(function (icon) {
                    icon.style.transform = 'rotate(0deg)';
                });

                if (!alreadyActive) {
                    this.classList.add('active');
                    const submenuId = this.getAttribute('data-submenu');
                    if (submenuId === 'my-items-recent') {
                        const container = myItemsDropdown.querySelector('.recent-items');
                        if (container && recentItems && recentItems.length > 0) {
                            renderRecentItems(1);
                        } else if (container) {
                            container.innerHTML = '<div class="sub-dropdown-item empty">Loading...</div>';
                        }
                    } else if (submenuId === 'my-items-following') {
                        renderFollowingItems(followingCurrentPage);
                    } else if (submenuId === 'my-items-roles') {
                        renderRolesItems(rolesCurrentPage);
                    }
                }
            });
        });

        // Close submenus when clicking outside
        document.addEventListener('click', function (e) {
            // If click is outside the dropdown entirely, close submenus
            if (!myItemsDropdown.contains(e.target) && !myItemsToggle.contains(e.target)) {
                myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(item => {
                    item.classList.remove('active');
                });
            }
        });

        // Linked-to hover popover: show which objects this item is linked to (all facets)
        (function () {
            const popover = document.createElement('div');
            popover.id = 'myItemsLinkedToPopover';
            popover.className = 'my-items-linked-to-popover';
            popover.setAttribute('aria-hidden', 'true');
            document.body.appendChild(popover);

            const subPopover = document.createElement('div');
            subPopover.id = 'myItemsLinkedToSubPopover';
            subPopover.className = 'my-items-linked-to-subpopover';
            subPopover.setAttribute('aria-hidden', 'true');
            document.body.appendChild(subPopover);

            let hoverTimer = null;
            let hideTimer = null;
            let subHideTimer = null;
            let currentFetchAborted = false;

            // Map label to view URL path
            const labelToViewPath = {
                'Process': 'process',
                'Project': 'project',
                'System': 'system',
                'Product': 'product',
                'Client': 'client',
                'Business Area': 'business-area',
                'Legal Entity': 'legal-entity',
                'Data Set': 'dataset',
                'Data Attribute': 'data-attribute',
                'Glossary': 'glossary',
                'Policy': 'policy',
                'Capability': 'capability',
                'Regulatory Theme': 'regulatory-theme',
                'Geography': 'geography',
                'System Interface': 'interface',
                'Committee': 'committee',
                'Regulation': 'regulation'
            };

            function getViewUrl(label, itemId) {
                const path = labelToViewPath[label] || label.toLowerCase().replace(/\s+/g, '-');
                return '/view/' + path + '/' + itemId;
            }

            function hidePopover() {
                popover.style.display = 'none';
                popover.setAttribute('aria-hidden', 'true');
            }
            function hideSubPopover() {
                subPopover.style.display = 'none';
                subPopover.setAttribute('aria-hidden', 'true');
            }

            myItemsDropdown.addEventListener('mouseenter', function (e) {
                const item = e.target.closest('.sub-dropdown-item[data-entity-type][data-object-id]');
                if (!item) return;
                const entityType = item.getAttribute('data-entity-type');
                const objectId = item.getAttribute('data-object-id');
                const itemName = item.getAttribute('data-item-name') || (entityType + ' #' + objectId);
                if (!objectId) return;

                if (hideTimer) clearTimeout(hideTimer);
                hideTimer = null;
                if (hoverTimer) clearTimeout(hoverTimer);
                currentFetchAborted = true;

                hoverTimer = setTimeout(async function () {
                    hoverTimer = null;
                    currentFetchAborted = false;
                    const rect = item.getBoundingClientRect();
                    const isRTL = document.documentElement.getAttribute('dir') === 'rtl' || document.body.getAttribute('dir') === 'rtl';
                    popover.style.display = 'block';
                    popover.setAttribute('aria-hidden', 'false');
                    if (isRTL) {
                        popover.style.right = (window.innerWidth - rect.left + 4) + 'px';
                        popover.style.left = 'auto';
                        popover.style.paddingRight = '12px';
                        popover.style.paddingLeft = '';
                    } else {
                        popover.style.left = (rect.right + 4) + 'px';
                        popover.style.right = 'auto';
                        popover.style.paddingLeft = '12px';
                        popover.style.paddingRight = '';
                    }
                    popover.style.top = Math.max(8, rect.top) + 'px';
                    const t = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t.bind(window.I18n) : function (k) { return k; };
                    const facetLabel = (entityType.charAt(0).toUpperCase() + entityType.slice(1)).replace(/_/g, ' ');
                    popover.innerHTML = '<div class="my-items-linked-to-title">' + escapeHtml(itemName) + '</div><div class="my-items-linked-to-type">' + escapeHtml(facetLabel) + '</div><div class="my-items-linked-to-section">' + escapeHtml(t('myItems.linkedTo')) + '</div><div class="my-items-linked-to-list">' + escapeHtml(t('myItems.loading')) + '</div>';
                    let summary = {};
                    try {
                        if (window.BUDG_OBJECT_LINKS && typeof window.BUDG_OBJECT_LINKS.getObjectLinksSummary === 'function') {
                            summary = await window.BUDG_OBJECT_LINKS.getObjectLinksSummary(entityType, objectId);
                        }
                    } catch (err) {}
                    if (currentFetchAborted) return;
                    const listEl = popover.querySelector('.my-items-linked-to-list');
                    if (!listEl) return;
                    const labels = Object.keys(summary);
                    if (labels.length === 0) {
                        listEl.innerHTML = '<div class="my-items-linked-to-empty">' + escapeHtml(t('myItems.noLinkedObjects')) + '</div>';
                    } else {
                        listEl.innerHTML = labels.map(function (label) {
                            const d = summary[label];
                            const count = d.count;
                            const items = d.items || [];
                            // Store full item data with IDs for click navigation
                            const dataItems = JSON.stringify(items.slice(0, 20)).replace(/"/g, '&quot;');
                            return '<div class="my-items-linked-to-row" data-count="' + count + '" data-items="' + dataItems + '" data-label="' + escapeHtml(label) + '"><i class="fas fa-chevron-right my-items-linked-to-chevron"></i><span class="my-items-linked-to-count">' + count + ' ' + escapeHtml(label) + '</span></div>';
                        }).join('');
                        listEl.querySelectorAll('.my-items-linked-to-row').forEach(function (row) {
                            row.addEventListener('mouseenter', function () {
                                if (subHideTimer) clearTimeout(subHideTimer);
                                const items = JSON.parse(row.getAttribute('data-items') || '[]');
                                const label = row.getAttribute('data-label') || '';
                                subPopover.innerHTML = '<div class="my-items-linked-to-subtitle">' + escapeHtml(label) + '</div><div class="my-items-linked-to-names">' + items.map(function (item) {
                                    const itemId = item.id || '';
                                    const itemName = escapeHtml(item.name || 'Unnamed');
                                    if (itemId) {
                                        return '<a href="' + getViewUrl(label, itemId) + '" class="my-items-linked-to-name clickable" data-item-id="' + itemId + '">' + itemName + '</a>';
                                    }
                                    return '<div class="my-items-linked-to-name">' + itemName + '</div>';
                                }).join('') + '</div>';
                                const r = row.getBoundingClientRect();
                                const isRTL = document.documentElement.getAttribute('dir') === 'rtl' || document.body.getAttribute('dir') === 'rtl';
                                subPopover.style.display = 'block';
                                subPopover.setAttribute('aria-hidden', 'false');
                                if (isRTL) {
                                    subPopover.style.right = (window.innerWidth - r.left + 2) + 'px';
                                    subPopover.style.left = 'auto';
                                    subPopover.style.paddingRight = '10px';
                                    subPopover.style.paddingLeft = '';
                                } else {
                                    subPopover.style.left = (r.right + 2) + 'px';
                                    subPopover.style.right = 'auto';
                                    subPopover.style.paddingLeft = '10px';
                                    subPopover.style.paddingRight = '';
                                }
                                subPopover.style.top = r.top + 'px';
                            });
                            row.addEventListener('mouseleave', function () {
                                subHideTimer = setTimeout(hideSubPopover, 150);
                            });
                            // Click on row to navigate (if only one item, go directly; otherwise expand)
                            row.addEventListener('click', function (e) {
                                e.stopPropagation();
                                const items = JSON.parse(row.getAttribute('data-items') || '[]');
                                const label = row.getAttribute('data-label') || '';
                                if (items.length === 1 && items[0].id) {
                                    window.location.href = getViewUrl(label, items[0].id);
                                }
                                // If multiple items, the subpopover is already shown on hover
                            });
                        });
                    }
                }, 350);
            }, true);

            // Popovers stay visible as long as the My Items dropdown is open.
            // They only close when:
            //   - The user hovers over a DIFFERENT item in the dropdown (new popover replaces old)
            //   - The My Items dropdown itself closes (click outside / Escape)
            // No mouseleave auto-hide behavior — the user requested it to stay visible.

            // When My Items dropdown closes: hide popovers AND reset submenus/facet dropdowns so subtabs don't stick
            function closeMyItemsState() {
                hidePopover();
                hideSubPopover();
                if (hoverTimer) { clearTimeout(hoverTimer); hoverTimer = null; }
                currentFetchAborted = true;
                myItemsDropdown.querySelectorAll('.dropdown-item.has-submenu.active').forEach(function (item) {
                    item.classList.remove('active');
                });
                document.querySelectorAll('.facet-dropdown-menu').forEach(function (dd) {
                    dd.style.display = 'none';
                });
                document.querySelectorAll('.filter-dropdown-btn .fa-chevron-down').forEach(function (icon) {
                    icon.style.transform = 'rotate(0deg)';
                });
            }
            const dropdownObserver = new MutationObserver(function () {
                if (!myItemsDropdown.classList.contains('show')) {
                    closeMyItemsState();
                }
            });
            dropdownObserver.observe(myItemsDropdown, { attributes: true, attributeFilter: ['class'] });
            
            // Prevent clicks inside popovers from closing menus
            popover.addEventListener('click', function (e) {
                e.stopPropagation();
            });
            subPopover.addEventListener('click', function (e) {
                e.stopPropagation();
                // If clicking on a link, let it navigate
                const link = e.target.closest('a.my-items-linked-to-name');
                if (link) {
                    // Link will navigate naturally, no need to prevent default
                    return;
                }
            });
            // Keep subPopover visible while hovering over it
            subPopover.addEventListener('mouseenter', function () {
                if (subHideTimer) { clearTimeout(subHideTimer); subHideTimer = null; }
            });
            subPopover.addEventListener('mouseleave', function () {
                subHideTimer = setTimeout(hideSubPopover, 150);
            });

            function escapeHtml(s) {
                if (s == null) return '';
                var div = document.createElement('div');
                div.textContent = s;
                return div.innerHTML;
            }
        })();
    }

    // My Items submenu handlers
    function setupMyItemsSubmenu(submenuId, containerClass, loadFunction) {
        const submenu = document.getElementById(submenuId);
        if (!submenu) return;

        const parentItem = submenu.closest('.dropdown-item');
        if (!parentItem) return;

        parentItem.addEventListener('mouseenter', async function () {
            const container = submenu.querySelector(containerClass);
            if (container) {
                try {
                    await loadFunction(container);
                } catch (e) {
                    container.innerHTML = '<div class="sub-dropdown-item empty">Failed to load</div>';
                }
            }
        });
    }

    // Setup Following submenu with pagination
    let followingItems = [];
    let followingFilteredItems = null; // Track filtered items
    let followingCurrentPage = 1;
    const followingItemsPerPage = 7;

    function renderFollowingItems(page = 1) {
        const container = myItemsDropdown.querySelector('.following-items');
        const paginationDots = myItemsDropdown.querySelector('#followingPaginationDots');
        if (!container) return;

        followingCurrentPage = page;

        // Use filtered items if available, otherwise use full list
        const itemsToRender = followingFilteredItems !== null ? followingFilteredItems : followingItems;
        const startIndex = (page - 1) * followingItemsPerPage;
        const endIndex = startIndex + followingItemsPerPage;
        const pageItems = itemsToRender.slice(startIndex, endIndex);

        if (pageItems.length === 0) {
            container.innerHTML = '<div class="sub-dropdown-item empty">No followed items</div>';
            if (paginationDots) paginationDots.style.display = 'none';
            return;
        }

        container.innerHTML = pageItems.map(item => {
            const entityType = item.entityType || 'unknown';
            const entityName = item.name || item.title || `${entityType} #${item.objectId}`;
            const followTypeName = item.followTypeName || 'Following';

            const iconMap = {
                'system': 'fas fa-server',
                'dataset': 'fas fa-database',
                'policy': 'fas fa-file-contract',
                'process': 'fas fa-cogs',
                'project': 'fas fa-project-diagram',
                'business_area': 'fas fa-building',
                'capability': 'fas fa-lightbulb',
                'committee': 'fas fa-users'
            };
            const icon = iconMap[entityType] || 'fas fa-box';
            const route = `/view/${entityType}/${item.objectId}`;

            return `
                <a href="${route}" class="sub-dropdown-item" title="${followTypeName}" data-entity-type="${entityType}" data-object-id="${item.objectId || ''}" data-item-name="${(entityName || '').replace(/"/g, '&quot;')}" data-follow-type="${followTypeName}">
                    <i class="${icon}"></i>
                    <span class="item-name">${entityName}</span>
                    <span class="follow-type">${followTypeName}</span>
                </a>
            `;
        }).join('');

        // Show/hide pagination dots
        if (itemsToRender.length > followingItemsPerPage && paginationDots) {
            paginationDots.style.display = 'flex';
            updateFollowingPaginationDots(page, Math.ceil(itemsToRender.length / followingItemsPerPage));
        } else if (paginationDots) {
            paginationDots.style.display = 'none';
        }
    }

    function updateFollowingPaginationDots(currentPage, totalPages) {
        const paginationDots = myItemsDropdown.querySelector('#followingPaginationDots');
        if (!paginationDots) return;

        paginationDots.innerHTML = '';
        for (let i = 1; i <= totalPages; i++) {
            const dot = document.createElement('div');
            dot.className = `pagination-dot ${i === currentPage ? 'active' : ''}`;
            dot.setAttribute('data-page', i);
            paginationDots.appendChild(dot);
        }
    }

    setupMyItemsSubmenu('myItemsFollowingSubmenu', '.following-items', async function (container) {
        try {
            // Only reload if items are empty or if explicitly needed
            if (followingItems.length === 0) {
                const currentUser = await getCurrentUser();
                if (!currentUser || !currentUser.id) {
                    container.innerHTML = '<div class="sub-dropdown-item empty">Please log in to view followed items</div>';
                    return;
                }

                // Check if user is authorized to see follow functionality
                const isAuthorized = isUserAuthorizedForFollow(currentUser);
                if (!isAuthorized) {
                    // Hide the following submenu entirely for unauthorized users
                    const followingMenuItem = document.querySelector('[data-submenu="my-items-following"]');
                    if (followingMenuItem) {
                        followingMenuItem.style.display = 'none';
                    }
                    return;
                }

                const response = await fetch(`/api/user-followed-items?userId=${currentUser.id}`);
                if (!response.ok) {
                    throw new Error('Failed to fetch followed items');
                }

                const data = await response.json();
                followingItems = data.followedItems || [];
                followingFilteredItems = null; // Reset filter when loading new data
                followingSelectedFacet = 'All';
                followingCurrentPage = 1;

                window.followedItems = followingItems;
                setupFollowingFilter();
            }
            // Render with current page (don't reset to 1)
            renderFollowingItems(followingCurrentPage);
        } catch (error) {
            console.error('Error loading followed items:', error);
            container.innerHTML = '<div class="sub-dropdown-item empty">Failed to load followed items</div>';
        }
    });


    // Setup filter functionality for followed items
    let followingSelectedFacet = 'All';
    function setupFollowingFilter() {
        const filterInput = document.getElementById('followingFilter');
        const scopeBtn = document.getElementById('followingScopeBtn');
        const facetDropdown = document.getElementById('followingFacetDropdown');

        if (filterInput && !filterInput.dataset.listenerAdded) {
            filterInput.addEventListener('input', function () {
                const searchTerm = this.value.toLowerCase();
                filterFollowedItems(searchTerm);
            });
            filterInput.dataset.listenerAdded = 'true';
        }

        if (scopeBtn && !scopeBtn.dataset.listenerAdded) {
            scopeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                toggleFacetDropdown('following', facetDropdown, scopeBtn);
            });
            scopeBtn.dataset.listenerAdded = 'true';
        }

        // Populate facets dropdown
        if (facetDropdown && window.followedItems && window.followedItems.length > 0) {
            populateFacetDropdown('following', facetDropdown, window.followedItems);
        }
    }

    // Setup filter functionality for recent items
    function setupRecentFilter() {
        const filterInput = document.getElementById('recentFilter');
        const scopeBtn = document.getElementById('recentScopeBtn');
        const facetDropdown = document.getElementById('recentFacetDropdown');

        if (filterInput && !filterInput.dataset.listenerAdded) {
            filterInput.addEventListener('input', function () {
                const searchTerm = this.value.toLowerCase();
                filterRecentItems(searchTerm);
            });
            filterInput.dataset.listenerAdded = 'true';
        }

        if (scopeBtn && !scopeBtn.dataset.listenerAdded) {
            scopeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                toggleFacetDropdown('recent', facetDropdown, scopeBtn);
            });
            scopeBtn.dataset.listenerAdded = 'true';
        }

        // Populate facets dropdown
        if (facetDropdown && window.recentItems && window.recentItems.length > 0) {
            populateFacetDropdown('recent', facetDropdown, window.recentItems);
        } else if (facetDropdown) {
            // Clear dropdown if no items
            const content = facetDropdown.querySelector('.facet-dropdown-content');
            if (content) content.innerHTML = '';
        }
    }

    // Setup filter functionality for roles items
    let rolesSelectedFacet = 'All';
    let rolesFilteredItems = null;
    function setupRolesFilter() {
        const filterInput = document.getElementById('rolesFilter');
        const scopeBtn = document.getElementById('rolesScopeBtn');
        const facetDropdown = document.getElementById('rolesFacetDropdown');

        if (filterInput && !filterInput.dataset.listenerAdded) {
            filterInput.addEventListener('input', function () {
                const searchTerm = this.value.toLowerCase();
                filterRolesItems(searchTerm);
            });
            filterInput.dataset.listenerAdded = 'true';
        }

        if (scopeBtn && !scopeBtn.dataset.listenerAdded) {
            scopeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                toggleFacetDropdown('roles', facetDropdown, scopeBtn);
            });
            scopeBtn.dataset.listenerAdded = 'true';
        }

        // Populate facets dropdown
        if (facetDropdown && window.rolesItems && window.rolesItems.length > 0) {
            populateFacetDropdown('roles', facetDropdown, window.rolesItems);
        }
    }

    // Toggle facet dropdown
    function toggleFacetDropdown(type, dropdown, btn) {
        if (!dropdown || !btn) return;

        const isOpen = dropdown.style.display === 'block';

        // Close all facet dropdowns first
        document.querySelectorAll('.facet-dropdown-menu').forEach(dd => {
            dd.style.display = 'none';
        });
        document.querySelectorAll('.filter-dropdown-btn .fa-chevron-down').forEach(icon => {
            icon.style.transform = 'rotate(0deg)';
        });

        if (!isOpen) {
            // Populate dropdown before showing
            let items = null;
            if (type === 'following' && window.followedItems) {
                items = window.followedItems;
            } else if (type === 'recent' && window.recentItems) {
                items = window.recentItems;
            } else if (type === 'roles' && window.rolesItems) {
                items = window.rolesItems;
            }

            if (items && items.length > 0) {
                populateFacetDropdown(type, dropdown, items);
            }

            dropdown.style.display = 'block';
            btn.querySelector('.fa-chevron-down').style.transform = 'rotate(180deg)';
        } else {
            dropdown.style.display = 'none';
            btn.querySelector('.fa-chevron-down').style.transform = 'rotate(0deg)';
        }
    }

    // Map entity names to display names for facets
    function getFacetDisplayName(entityName) {
        const displayNameMap = {
            'CatItemCategory': 'Glossary',
            'CatalogueItem': 'Dataset',
            'CatalogItem': 'Dataset',
            'BusinessConnection': 'Business Area',
            'InvolvedParty': 'People',
            'SystemInterface': 'System Interface',
            'SystemXSystem': 'System Interface',
            'Legal': 'Legal Entity'
        };
        return displayNameMap[entityName] || entityName;
    }

    // Populate facet dropdown with available facets
    function populateFacetDropdown(type, dropdown, items) {
        if (!dropdown || !items || items.length === 0) return;

        const content = dropdown.querySelector('.facet-dropdown-content');
        if (!content) return;

        // Count facets
        const facetCounts = {};
        items.forEach(item => {
            let facet = 'All';
            if (type === 'following') {
                facet = item.entityType || 'unknown';
            } else if (type === 'recent') {
                facet = item.entity || 'unknown';
            } else if (type === 'roles') {
                facet = item.entity || 'unknown';
            }

            if (!facetCounts[facet]) {
                facetCounts[facet] = 0;
            }
            facetCounts[facet]++;
        });

        // Create facet list
        const facets = ['All', ...Object.keys(facetCounts).filter(f => f !== 'All' && f !== 'unknown').sort()];

        content.innerHTML = facets.map(facet => {
            const count = facet === 'All' ? items.length : facetCounts[facet] || 0;
            const displayName = facet === 'All' ? 'All' : getFacetDisplayName(facet);
            return `
                <div class="facet-dropdown-item" data-facet="${facet}">
                    <span>${displayName}</span>
                    <span class="facet-count">${count}</span>
                </div>
            `;
        }).join('');

        // Add click handlers
        content.querySelectorAll('.facet-dropdown-item').forEach(item => {
            item.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                const selectedFacet = this.getAttribute('data-facet');
                selectFacet(type, selectedFacet, dropdown);
            });
        });
    }

    // Select a facet and filter items
    function selectFacet(type, facet, dropdown) {
        const btn = type === 'recent' ? document.getElementById('recentScopeBtn') :
            type === 'following' ? document.getElementById('followingScopeBtn') :
                document.getElementById('rolesScopeBtn');

        if (btn) {
            const span = btn.querySelector('span');
            if (span) {
                const t = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t.bind(window.I18n) : function (k) { return k; };
                span.textContent = facet === 'All' ? t('myItems.inAll') : ('in ' + getFacetDisplayName(facet));
            }
            const chevron = btn.querySelector('.fa-chevron-down');
            if (chevron) chevron.style.transform = 'rotate(0deg)';
        }

        if (dropdown) dropdown.style.display = 'none';

        if (type === 'recent') {
            recentSelectedFacet = facet;
            const filterInput = document.getElementById('recentFilter');
            const searchTerm = filterInput ? (filterInput.value || '') : '';
            filterRecentItems(searchTerm);
        } else if (type === 'following') {
            followingSelectedFacet = facet;
            const filterInput = document.getElementById('followingFilter');
            const searchTerm = filterInput ? (filterInput.value || '') : '';
            filterFollowedItems(searchTerm);
        } else if (type === 'roles') {
            rolesSelectedFacet = facet;
            const filterInput = document.getElementById('rolesFilter');
            const searchTerm = filterInput ? (filterInput.value || '') : '';
            filterRolesItems(searchTerm);
        }
    }

    // Filter recent items
    function filterRecentItems(searchTerm) {
        if (!window.recentItems || !Array.isArray(window.recentItems)) {
            console.log('No recent items available');
            return;
        }

        // Normalize search term
        searchTerm = (searchTerm || '').toString().toLowerCase().trim();

        let filtered = [...window.recentItems];
        let hasFilter = false;

        // Filter by facet
        if (recentSelectedFacet && recentSelectedFacet !== 'All') {
            filtered = filtered.filter(item => {
                const entity = (item.entity || 'unknown').toString().trim();
                return entity === recentSelectedFacet;
            });
            hasFilter = true;
        }

        // Filter by search term
        if (searchTerm !== '') {
            filtered = filtered.filter(item => {
                const name = (item.display_name || item.entity + ' #' + item.entity_id || '').toString().toLowerCase();
                const entity = (item.entity || '').toString().toLowerCase();
                return name.includes(searchTerm) || entity.includes(searchTerm);
            });
            hasFilter = true;
        }

        // Only set filtered items if there's an active filter
        recentFilteredItems = hasFilter ? filtered : null;
        currentPage = 1;
        renderRecentItems(1);
    }

    // Filter roles items
    function filterRolesItems(searchTerm) {
        if (!window.rolesItems) return;

        let filtered = [...window.rolesItems];

        // Filter by facet
        if (rolesSelectedFacet !== 'All') {
            filtered = filtered.filter(item => {
                const entity = item.entity || 'unknown';
                return entity === rolesSelectedFacet;
            });
        }

        // Filter by search term
        if (searchTerm && searchTerm.trim() !== '') {
            filtered = filtered.filter(item => {
                const name = (item.name || item.entity + ' #' + item.entity_id || '').toLowerCase();
                const entity = (item.entity || '').toLowerCase();
                return name.includes(searchTerm) || entity.includes(searchTerm);
            });
        }

        rolesFilteredItems = filtered.length !== window.rolesItems.length ? filtered : null;
        rolesCurrentPage = 1;
        renderRolesItems(1);
    }

    // Filter followed items based on search term and facet
    function filterFollowedItems(searchTerm) {
        if (!window.followedItems) return;

        let filtered = [...window.followedItems];

        // Filter by facet
        if (followingSelectedFacet !== 'All') {
            filtered = filtered.filter(item => {
                const entityType = item.entityType || 'unknown';
                return entityType === followingSelectedFacet;
            });
        }

        // Filter by search term
        if (searchTerm && searchTerm.trim() !== '') {
            filtered = filtered.filter(item => {
                const entityName = (item.name || item.title || '').toLowerCase();
                const entityType = (item.entityType || '').toLowerCase();
                const followTypeName = (item.followTypeName || '').toLowerCase();

                return entityName.includes(searchTerm) ||
                    entityType.includes(searchTerm) ||
                    followTypeName.includes(searchTerm);
            });
        }

        followingFilteredItems = filtered.length !== window.followedItems.length ? filtered : null;
        followingCurrentPage = 1;
        renderFollowingItems(1);
    }

    // Setup Roles submenu with pagination
    let rolesItems = [];
    let rolesCurrentPage = 1;
    const rolesItemsPerPage = 7;

    function renderRolesItems(page = 1) {
        const container = myItemsDropdown.querySelector('.roles-items');
        const paginationDots = myItemsDropdown.querySelector('#rolesPaginationDots');
        if (!container) return;

        rolesCurrentPage = page;

        // Use filtered items if available, otherwise use full list
        const itemsToRender = rolesFilteredItems !== null ? rolesFilteredItems : rolesItems;
        const startIndex = (page - 1) * rolesItemsPerPage;
        const endIndex = startIndex + rolesItemsPerPage;
        const pageItems = itemsToRender.slice(startIndex, endIndex);

        if (pageItems.length === 0) {
            container.innerHTML = '<div class="sub-dropdown-item empty">No role items</div>';
            if (paginationDots) paginationDots.style.display = 'none';
            return;
        }

        container.innerHTML = pageItems.map(item => {
            const name = item.name || `${item.entity} #${item.entity_id}`;
            const route = (item.route || '').replace(/^\/?api\//, '/');
            const entity = item.entity || 'unknown';
            const oid = (item.entity_id != null && item.entity_id !== '') ? item.entity_id : '';
            return `
                <a href="${route}" class="sub-dropdown-item" title="${item.entity}" data-entity-type="${entity}" data-object-id="${oid}" data-item-name="${(name || '').replace(/"/g, '&quot;')}">
                    <i class="fas fa-user-tag"></i>
                    <span class="item-name">${name}</span>
                </a>
            `;
        }).join('');

        // Show/hide pagination dots
        if (itemsToRender.length > rolesItemsPerPage && paginationDots) {
            paginationDots.style.display = 'flex';
            updateRolesPaginationDots(page, Math.ceil(itemsToRender.length / rolesItemsPerPage));
        } else if (paginationDots) {
            paginationDots.style.display = 'none';
        }
    }

    function updateRolesPaginationDots(currentPage, totalPages) {
        const paginationDots = myItemsDropdown.querySelector('#rolesPaginationDots');
        if (!paginationDots) return;

        paginationDots.innerHTML = '';
        for (let i = 1; i <= totalPages; i++) {
            const dot = document.createElement('div');
            dot.className = `pagination-dot ${i === currentPage ? 'active' : ''}`;
            dot.setAttribute('data-page', i);
            paginationDots.appendChild(dot);
        }
    }

    setupMyItemsSubmenu('myItemsRolesSubmenu', '.roles-items', async function (container) {
        try {
            // Only reload if items are empty or if explicitly needed
            if (rolesItems.length === 0) {
                const currentUser = await getCurrentUser();
                if (!currentUser || !currentUser.id) {
                    container.innerHTML = '<div class="sub-dropdown-item empty">Please log in to view role items</div>';
                    return;
                }

                const response = await fetch(`/api/my-items/roles?userId=${currentUser.id}`);
                if (!response.ok) throw new Error('Failed to load role items');
                const data = await response.json();
                rolesItems = data.items || [];
                rolesFilteredItems = null;
                rolesSelectedFacet = 'All';
                rolesCurrentPage = 1;
                window.rolesItems = rolesItems;
                setupRolesFilter();
            }
            // Render with current page (don't reset to 1)
            renderRolesItems(rolesCurrentPage);
        } catch (e) {
            container.innerHTML = '<div class="sub-dropdown-item empty">Failed to load role items</div>';
        }
    });
}

// Performance optimization utility - debounce function
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

// Helper function to check if user is authorized for follow functionality
function isUserAuthorizedForFollow(userData) {
    if (!userData || !userData.authenticated) {
        return false;
    }

    // Treat missing/invalid identity as unauthorized (guest uses -1)
    const id = (userData.id ?? userData.ID);
    if (id == null || Number(id) <= 0) {
        return false;
    }

    // Role is required to authorize follow UI
    const roleRaw = (userData.role ?? '').toString().trim();
    if (!roleRaw) {
        return false;
    }

    // Check if user is a guest
    const role = roleRaw.toLowerCase();
    if (role.includes('guest')) {
        return false;
    }

    return true;
}

// Initialize follow-related UI elements based on user authorization
async function initializeFollowUI() {
    try {
        const currentUser = await getCurrentUser();
        if (!isUserAuthorizedForFollow(currentUser)) {
            // Hide the following submenu entirely for unauthorized users
            const followingMenuItem = document.querySelector('[data-submenu="my-items-following"]');
            if (followingMenuItem) {
                followingMenuItem.style.display = 'none';
            }
        }
    } catch (error) {
        console.error('Error initializing follow UI:', error);
    }
}

// Helper function to get current user
async function getCurrentUser() {
    try {
        const response = await fetch('/api/me', {
            method: 'GET',
            credentials: 'include'
        });
        if (response.ok) {
            const userData = await response.json();
            return userData;
        }
    } catch (error) {
        console.error('Error getting current user:', error);
    }
    return null;
}

// Export functions for use in other modules
window.BUDG = window.BUDG || {};
window.BUDG.utils = {
    debounce
};

// Auto-inject Stakeholders/Follower/Add Followers subtabs for all stakeholder containers
(function initStakeholderSubtabsWatcher() {
    const SUBTAB_CLASS = 'stakeholder-subtab';
    const ACTIVE_CLASS = 'active';

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    /**
     * Normalize entity type from URL format to Follow API format
     * Maps URL entity types (e.g., "system-interface") to API entity types (e.g., "interface")
     */
    function normalizeEntityTypeForFollowAPI(entityType) {
        if (!entityType) return null;
        
        const normalized = entityType.toLowerCase().trim();
        
        // Map URL entity types to Follow API entity types
        const mapping = {
            'system-interface': 'interface',
            'systeminterface': 'interface',
            'interface': 'interface',
            'dataset': 'dataset',
            'data-set': 'dataset',
            'glossary': 'glossary',
            'system': 'system',
            'capability': 'capability',
            'client': 'client',
            'legal-entity': 'legal-entity',
            'legalentity': 'legal-entity',
            'product': 'product',
            'policy': 'policy',
            'process': 'process',
            'project': 'project',
            'committee': 'committee',
            'regulation': 'regulation'
        };
        
        return mapping[normalized] || normalized;
    }

    /**
     * Check if an entity type is supported by the Follow API
     */
    function isEntityTypeSupportedForFollow(entityType) {
        if (!entityType) return false;
        const normalized = normalizeEntityTypeForFollowAPI(entityType);
        const supportedTypes = [
            'dataset', 'glossary', 'system', 'interface', 'capability', 
            'client', 'legal-entity', 'product', 'policy', 'process', 
            'project', 'committee', 'regulation'
        ];
        return supportedTypes.includes(normalized);
    }

    function getEntityInfo() {
        // Special handling for change request view page - get entity info from CR reference
        if (window.location.pathname.includes('/change-request/change-request-view.html')) {
            // Try to get reference from currentChangeRequest if available
            if (window.currentChangeRequest && window.currentChangeRequest.reference) {
                const reference = window.currentChangeRequest.reference;
                // Parse reference format: "FacetType FacetId" (e.g., "Regulation 15", "System 47")
                const match = reference.match(/^(.+?)\s+(\d+)$/);
                if (match) {
                    let entityType = match[1].trim().toLowerCase().replace(/\s+/g, '-');
                    // Normalize common facet type names
                    entityType = entityType
                        .replace(/^data-set$/, 'dataset')
                        .replace(/^system-interface$/, 'interface')
                        .replace(/^business-area$/, 'business-area');
                    const entityId = parseInt(match[2], 10);
                    if (!Number.isNaN(entityId)) {
                        return { entityType, entityId };
                    }
                }
            }
        }
        
        // Default behavior for other pages
        const path = window.location.pathname;
        const params = new URLSearchParams(window.location.search);
        let entityType = null;
        let entityId = null;
        
        // Try to extract from path pattern: /view/{entityType}/{id}
        const pathMatch = path.match(/\/view\/([^\/]+)\/(\d+)/);
        if (pathMatch) {
            entityType = pathMatch[1];
            entityId = parseInt(pathMatch[2], 10);
        } else {
            // Try to extract from path pattern: /view/{entityType}/{entityType}.html?id={id}
            const htmlMatch = path.match(/\/view\/([^\/]+)\/[^\/]+\.html/);
            if (htmlMatch) {
                entityType = htmlMatch[1];
                // Get ID from query parameter
                const queryId = params.get('id');
                if (queryId) {
                    entityId = parseInt(queryId, 10);
                }
            } else {
                // Fallback: try to get entity type from path and ID from query
                const parts = path.split('/').filter(Boolean);
                const viewIdx = parts.indexOf('view');
                if (viewIdx >= 0 && parts.length > viewIdx + 1) {
                    entityType = parts[viewIdx + 1];
                    // Remove .html if present
                    entityType = entityType.replace(/\.html$/, '');
                }
                const queryId = params.get('id');
                if (queryId) {
                    entityId = parseInt(queryId, 10);
                } else {
                    // Last resort: try to parse ID from last path segment
                    if (parts.length > 0) {
                        const maybeId = parseInt(parts[parts.length - 1], 10);
                        if (!Number.isNaN(maybeId)) {
                            entityId = maybeId;
                        }
                    }
                }
            }
        }
        
        // Validate results
        if (Number.isNaN(entityId)) {
            entityId = null;
        }
        
        return { entityType, entityId };
    }

    function attachSubtabs(container) {
        if (!container || container.dataset.subtabsAttached === 'true') return;

        // Get entity info - will be re-fetched when needed to ensure it's current
        let entityInfo = getEntityInfo();

        const wrapper = document.createElement('div');
        wrapper.className = 'stakeholder-subtabs';
        wrapper.innerHTML = `
            <button class="${SUBTAB_CLASS} ${ACTIVE_CLASS}" data-tab="stakeholders">Stakeholders</button>
            <button class="${SUBTAB_CLASS}" data-tab="followers">Followers</button>
        `;

        const followersContainer = document.createElement('div');
        followersContainer.id = `${container.id || 'stakeholders'}-followers`;
        // Match the container's class structure for proper styling
        const containerClasses = container.className || '';
        followersContainer.className = `stakeholder-followers-panel ${containerClasses}`.trim();
        // Match container's style attributes
        if (container.style.gridColumn) {
            followersContainer.style.gridColumn = container.style.gridColumn;
        }
        followersContainer.style.setProperty('display', 'none', 'important');
        followersContainer.style.setProperty('visibility', 'hidden', 'important');
        followersContainer.innerHTML = `
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="section-title">FOLLOWERS</div>
                <div class="followers-table-wrapper">
                    <table class="followers-table">
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th>Org Unit</th>
                                <th>Function</th>
                                <th>Following Since</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td colspan="4" class="empty">Loading followers...</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        `;

        const parent = container.parentNode;
        if (parent) {
            parent.insertBefore(wrapper, container);
            parent.insertBefore(followersContainer, container.nextSibling);
        }

        const tabs = Array.from(wrapper.querySelectorAll(`.${SUBTAB_CLASS}`));

        const isContainerVisible = () => {
            try {
                // If we're on the followers tab, the container is intentionally hidden
                // but the stakeholders section should still be considered "visible"
                const activeTab = wrapper.dataset.activeTab || 'stakeholders';
                if (activeTab === 'followers') {
                    // Check if the parent is visible instead
                    const parent = container.parentNode;
                    if (parent) {
                        const parentStyle = window.getComputedStyle(parent);
                        if (parentStyle.display === 'none' || parentStyle.visibility === 'hidden') {
                            return false;
                        }
                    }
                    // If parent is visible, consider the stakeholders section visible
                    // even though we've hidden the container itself
                    return true;
                }
                
                // Check if container has tab-visible class (used by CR view)
                if (container.classList.contains('tab-visible')) {
                    return true;
                }
                // Check if container has tab-hidden class
                if (container.classList.contains('tab-hidden')) {
                    return false;
                }
                // Fallback to computed style
                const cs = window.getComputedStyle(container);
                return cs && cs.display !== 'none' && cs.visibility !== 'hidden';
            } catch (_) {
                // If we're on followers tab, assume visible
                const activeTab = wrapper.dataset.activeTab || 'stakeholders';
                if (activeTab === 'followers') {
                    return true;
                }
                return container.style.display !== 'none' && !container.classList.contains('tab-hidden');
            }
        };

        const syncVisibility = () => {
            const activeTab = wrapper.dataset.activeTab || 'stakeholders';
            const mainTab = document.querySelector('.tab-container .tab.active');
            const mainTabName = mainTab ? mainTab.getAttribute('data-tab') : '';

            // Legal Entity view: only show Stakeholders/Followers when the main tab is STAKEHOLDERS.
            // Otherwise we would undo display:none and show the wrapper on Summary, Impact, etc.
            const isLegalEntityPage = window.location.pathname.indexOf('/LegalEntity/') !== -1 || window.location.pathname.indexOf('/legal-entity') !== -1;
            if (container.id === 'legalEntityStakeholdersContainer' && isLegalEntityPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }

            // Process view: only show Stakeholders/Followers when the main tab is STAKEHOLDERS.
            const isProcessPage = window.location.pathname.indexOf('/process/') !== -1 || window.location.pathname.indexOf('/Process/') !== -1;
            if (container.id === 'processStakeholdersContainer' && isProcessPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }

            // System view: only show Stakeholders/Followers when the main tab is STAKEHOLDERS.
            const isSystemPage = window.location.pathname.indexOf('/system/') !== -1 || window.location.pathname.indexOf('/System/') !== -1;
            if (container.id === 'systemStakeholdersContainer' && isSystemPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }

            // When switching back to Stakeholders sub-tab, restore container visibility *only*
            // when the main tab is actually STAKEHOLDERS. Otherwise we would undo the page's
            // display:none (e.g. Committee hiding containers on Impact) and show the stakeholders
            // table on top of other tabs.
            if (activeTab === 'stakeholders' && mainTabName === 'stakeholders') {
                container.style.removeProperty('display');
                container.style.removeProperty('visibility');
            }
            // Dataset view: only show Stakeholders/Followers when the main tab is STAKEHOLDERS.
            const isDatasetPage = window.location.pathname.indexOf('/dataset/') !== -1 || window.location.pathname.indexOf('/Dataset/') !== -1;
            if (container.id === 'datasetStakeholdersContainer' && isDatasetPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Policy view: only show when main tab is STAKEHOLDERS.
            const isPolicyPage = window.location.pathname.indexOf('/policy/') !== -1 || window.location.pathname.indexOf('/Policy/') !== -1;
            if (container.id === 'policyStakeholdersContainer' && isPolicyPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Project view: only show when main tab is STAKEHOLDERS.
            const isProjectPage = window.location.pathname.indexOf('/project/') !== -1 || window.location.pathname.indexOf('/Project/') !== -1;
            if (container.id === 'projectStakeholdersContainer' && isProjectPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Product view: only show when main tab is STAKEHOLDERS.
            const isProductPage = window.location.pathname.indexOf('/product/') !== -1 || window.location.pathname.indexOf('/Product/') !== -1;
            if (container.id === 'productStakeholdersContainer' && isProductPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Client view: only show when main tab is STAKEHOLDERS.
            const isClientPage = window.location.pathname.indexOf('/client/') !== -1 || window.location.pathname.indexOf('/Client/') !== -1;
            if (container.id === 'clientStakeholdersContainer' && isClientPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Capability view: only show when main tab is STAKEHOLDERS.
            const isCapabilityPage = window.location.pathname.indexOf('/capability/') !== -1 || window.location.pathname.indexOf('/Capability/') !== -1;
            if (container.id === 'capabilityStakeholdersContainer' && isCapabilityPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Business Area view: only show when main tab is STAKEHOLDERS.
            const isBusinessAreaPage = window.location.pathname.indexOf('/business-area/') !== -1 || window.location.pathname.indexOf('/business_area/') !== -1;
            if (container.id === 'businessAreaStakeholdersContainer' && isBusinessAreaPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Glossary view: only show when main tab is STAKEHOLDERS.
            const isGlossaryPage = window.location.pathname.indexOf('/glossary/') !== -1 || window.location.pathname.indexOf('/Glossary/') !== -1;
            if (container.id === 'glossaryStakeholdersContainer' && isGlossaryPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Regulation view: only show when main tab is STAKEHOLDERS.
            const isRegulationPage = window.location.pathname.indexOf('/regulation/') !== -1 || window.location.pathname.indexOf('/Regulation/') !== -1;
            if (container.id === 'regulationStakeholdersContainer' && isRegulationPage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // Committee view: only show when main tab is STAKEHOLDERS.
            const isCommitteePage = window.location.pathname.indexOf('/committee/') !== -1 || window.location.pathname.indexOf('/Committee/') !== -1;
            if (container.id === 'committeeStakeholdersContainer' && isCommitteePage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            // System Interface view: only show when main tab is STAKEHOLDERS.
            const isInterfacePage = window.location.pathname.indexOf('/system-interface/') !== -1 || window.location.pathname.indexOf('/systeminterface/') !== -1;
            if (container.id === 'interfaceStakeholdersContainer' && isInterfacePage && mainTabName !== 'stakeholders') {
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }
            const visible = isContainerVisible();
            
            if (!visible && activeTab !== 'followers') {
                // If the page hides the stakeholders container (e.g., another main tab is active),
                // hide our injected UI too so it doesn't appear on other pages/tabs.
                // BUT only if we're not on the followers tab (where we intentionally hide the container)
                wrapper.style.display = 'none';
                followersContainer.style.display = 'none';
                return;
            }

            // Show the wrapper (sub-tabs)
            wrapper.style.display = '';
            
            if (activeTab === 'followers') {
                // Hide stakeholders container and show followers container
                // Use !important to override any other styles
                container.style.setProperty('display', 'none', 'important');
                container.style.setProperty('visibility', 'hidden', 'important');
                
                // Match the original container's display style (block, grid, flex, etc.)
                const originalDisplay = window.getComputedStyle(container).display || 'block';
                // If container was using grid, use grid; otherwise use block
                const displayValue = originalDisplay === 'grid' ? 'grid' : 
                                     originalDisplay === 'flex' ? 'flex' : 'block';
                followersContainer.style.setProperty('display', displayValue, 'important');
                followersContainer.style.setProperty('visibility', 'visible', 'important');
            } else {
                // Show stakeholders container and hide followers container
                // Remove inline display/visibility to let CSS classes control it
                container.style.removeProperty('display');
                container.style.removeProperty('visibility');
                // Ensure followers container is hidden
                followersContainer.style.setProperty('display', 'none', 'important');
                followersContainer.style.setProperty('visibility', 'hidden', 'important');
            }
        };

        const setActive = (tabName) => {
            wrapper.dataset.activeTab = tabName;
            tabs.forEach((btn) => btn.classList.toggle(ACTIVE_CLASS, btn.dataset.tab === tabName));
            if (tabName === 'followers') {
                // Re-fetch entity info to ensure it's current
                entityInfo = getEntityInfo();
                const { entityType, entityId } = entityInfo;
                
                // Check if entity type is supported before loading
                if (!entityType || !entityId) {
                    const tbody = followersContainer.querySelector('tbody');
                    if (tbody) {
                        tbody.innerHTML = `<tr><td colspan="4" class="empty">Unable to determine entity information</td></tr>`;
                    }
                    followersContainer.dataset.loaded = 'true';
                } else if (!isEntityTypeSupportedForFollow(entityType)) {
                    const tbody = followersContainer.querySelector('tbody');
                    if (tbody) {
                        tbody.innerHTML = `<tr><td colspan="4" class="empty">Followers not available for this entity type</td></tr>`;
                    }
                    followersContainer.dataset.loaded = 'true';
                } else {
                    // Lazy-load followers when tab is first opened
                    if (followersContainer.dataset.loaded !== 'true') {
                        loadFollowersIntoPanel(followersContainer, entityType, entityId);
                    }
                }
            }
            syncVisibility();
        };

        tabs.forEach((btn) => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                setActive(btn.dataset.tab);
            });
        });

        container.dataset.subtabsAttached = 'true';

        // Registry so any view can trigger sync on main tab switch
        if (typeof window._stakeholderSyncFns !== 'object') {
            window._stakeholderSyncFns = {};
        }
        window._stakeholderSyncFns[container.id] = syncVisibility;
        if (typeof window.syncStakeholderVisibility !== 'function') {
            window.syncStakeholderVisibility = function (containerId) {
                if (window._stakeholderSyncFns && typeof window._stakeholderSyncFns[containerId] === 'function') {
                    window._stakeholderSyncFns[containerId]();
                }
            };
        }

        // Expose syncVisibility for Legal Entity so tab switch can trigger it immediately
        if (container.id === 'legalEntityStakeholdersContainer') {
            window._legalEntitySyncStakeholderVisibility = syncVisibility;
        }

        // Keep the injected UI hidden unless the stakeholders container is visible.
        // This prevents "Stakeholders / Followers" from appearing on non-stakeholder tabs/pages.
        try {
            const visObserver = new MutationObserver(() => syncVisibility());
            visObserver.observe(container, { attributes: true, attributeFilter: ['style', 'class', 'hidden'] });
        } catch (_) { }

        // Initial state
        wrapper.dataset.activeTab = 'stakeholders';
        syncVisibility();
    }

    // Expose attachSubtabs globally so it can be called from other scripts
    window.attachSubtabs = attachSubtabs;

    async function loadFollowersIntoPanel(panelEl, entityType, entityId) {
        try {
            const tbody = panelEl.querySelector('tbody');
            if (tbody) {
                tbody.innerHTML = `<tr><td colspan="4" class="empty">Loading...</td></tr>`;
            }

            if (!entityType || !entityId) {
                if (tbody) {
                    tbody.innerHTML = `<tr><td colspan="4" class="empty">Missing entity information</td></tr>`;
                }
                panelEl.dataset.loaded = 'true';
                return;
            }

            // Normalize entity type to match Follow API expectations
            const normalizedEntityType = normalizeEntityTypeForFollowAPI(entityType);
            
            // Check if entity type is supported
            if (!isEntityTypeSupportedForFollow(entityType)) {
                if (tbody) {
                    tbody.innerHTML = `<tr><td colspan="4" class="empty">Followers not available for this entity type</td></tr>`;
                }
                panelEl.dataset.loaded = 'true';
                return;
            }

            const apiUrl = `/api/follow/${encodeURIComponent(normalizedEntityType)}/${encodeURIComponent(entityId)}`;

            const resp = await fetch(apiUrl, {
                method: 'GET',
                credentials: 'include'
            });

            if (!resp.ok) {
                if (resp.status === 400) {
                    // Bad request - likely unsupported entity type
                    if (tbody) {
                        tbody.innerHTML = `<tr><td colspan="4" class="empty">Followers not available for this entity type</td></tr>`;
                    }
                    panelEl.dataset.loaded = 'true';
                    return;
                }
                const errorText = await resp.text().catch(() => 'Unknown error');
                throw new Error(`HTTP ${resp.status}: ${errorText}`);
            }

            const data = await resp.json();
            const followers = Array.isArray(data?.followers) ? data.followers : [];

            if (!tbody) {
                return;
            }

            if (!followers.length) {
                tbody.innerHTML = `<tr><td colspan="4" class="empty">No followers</td></tr>`;
                panelEl.dataset.loaded = 'true';
                return;
            }

            const fmtDate = (iso) => {
                if (!iso) return '';
                try { return new Date(iso).toLocaleDateString(); } catch (_) { return String(iso); }
            };

            tbody.innerHTML = followers.map(f => {
                const name = (f.name || '').trim();
                const personId = f.personId;
                const nameCell = personId ? `<a href="/view/people/${encodeURIComponent(personId)}">${escapeHtml(name)}</a>` : escapeHtml(name);
                return `
                    <tr>
                        <td>${nameCell || '<span class="empty">-</span>'}</td>
                        <td>${escapeHtml((f.orgUnit || '').trim()) || '<span class="empty">-</span>'}</td>
                        <td>${escapeHtml((f.function || '').trim()) || '<span class="empty">-</span>'}</td>
                        <td>${escapeHtml(fmtDate(f.followingSince)) || '<span class="empty">-</span>'}</td>
                    </tr>
                `;
            }).join('');

            panelEl.dataset.loaded = 'true';
        } catch (e) {
            const tbody = panelEl.querySelector('tbody');
            if (tbody) {
                tbody.innerHTML = `<tr><td colspan="4" class="empty">Failed to load followers: ${escapeHtml(e.message || 'Unknown error')}</td></tr>`;
            }
        }
    }

    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            mutation.addedNodes.forEach((node) => {
                if (!(node instanceof HTMLElement)) return;
                if (node.id && node.id.endsWith('StakeholdersContainer')) {
                    attachSubtabs(node);
                }
                node.querySelectorAll?.('[id$="StakeholdersContainer"]').forEach((el) => attachSubtabs(el));
            });
        });
    });

    document.addEventListener('DOMContentLoaded', () => {
        observer.observe(document.body, { childList: true, subtree: true });
        document.querySelectorAll('[id$="StakeholdersContainer"]').forEach((el) => attachSubtabs(el));
    });
})();

// Theme management utilities
window.BUDG.theme = {
    // Get current theme from localStorage
    getCurrentTheme: function () {
        return localStorage.getItem('budg-theme') || 'light';
    },

    // Set new theme and save preference
    setTheme: function (theme) {
        const html = document.documentElement;
        html.setAttribute('data-theme', theme);
        localStorage.setItem('budg-theme', theme);
        updateThemeIcon(theme);

        // Notify other components about theme change
        window.dispatchEvent(new CustomEvent('themeChanged', {
            detail: { theme: theme }
        }));
    },

    // Toggle between light and dark themes
    toggleTheme: function () {
        const currentTheme = this.getCurrentTheme();
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        this.setTheme(newTheme);
        return newTheme;
    },

    // Check if dark mode is currently active
    isDarkMode: function () {
        return this.getCurrentTheme() === 'dark';
    }
};