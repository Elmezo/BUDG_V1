/**
 * Header Authentication Management
 * Handles user authentication state and profile display across all pages
 */

class HeaderAuthManager {
    constructor() {
        this.refreshDomRefs();
        this.init();
    }

    /** Re-query header nodes (needed after async partial inject or headerReady re-init). */
    refreshDomRefs() {
        this.userProfile = document.getElementById('userProfile');
        this.loginInlineContainer = document.getElementById('loginInlineContainer');
        this.loginButton = document.getElementById('loginButton');
        this.userName = document.getElementById('userName');
        this.userRole = document.getElementById('userRole');
        this.userAvatarImg = document.getElementById('userAvatarImg');
        this.userAvatarText = document.getElementById('userAvatarText');
        this.userAvatarImgDropdown = document.getElementById('userAvatarImgDropdown');
        this.userAvatarTextDropdown = document.getElementById('userAvatarTextDropdown');
        this.logoutButton = document.getElementById('logoutButton');
        this.itemsNavItem = document.getElementById('itemsNavItem');
        this.createNavItem = document.getElementById('createNavItem');
    }

    async init() {
        this.refreshDomRefs();
        // Always check auth status, but handle errors gracefully on public pages
        const path = window.location.pathname.toLowerCase();
        const isPublic = path === '/' || path.endsWith('/index.html') || path.endsWith('/login.html') || path.endsWith('/search.html') || path.endsWith('/error/permission.html') || path.startsWith('/view/') || path.includes('/view/');

        try {
            await this.checkAuthStatus();
        } catch (error) {
            // On public pages, silently show login button if auth fails
            if (isPublic) {
                this.showLoginButton();
            } else {
                // On protected pages, redirect to login
                this.redirectToLogin();
            }
        }

        // Set up logout button event listener
        if (this.logoutButton) {
            this.logoutButton.addEventListener('click', (e) => {
                e.preventDefault();
                this.logout();
            });
        }

        // Set up profile dropdown functionality
        this.initProfileDropdown();
    }

    async checkAuthStatus() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include' // Include cookies in request
            });

            if (response.ok) {
                const userData = await response.json();
                // MeServlet returns { authenticated: false } with no role when there is no session
                if (userData.authenticated !== true) {
                    this.showLoginButton();
                    return;
                }
                const role = (userData.role || '').toString().toLowerCase();
                // Guest JWT is not "logged in" — show login so they can sign in as a real user
                if (role.includes('guest')) {
                    this.showLoginButton();
                    return;
                }
                this.showUserProfile(userData);
            } else if (response.status === 401) {
                // Try to refresh access token then retry /api/me
                const refreshed = await this.tryRefresh();
                if (refreshed) {
                    const meAgain = await fetch('/api/me', { method: 'GET', credentials: 'include' });
                    if (meAgain.ok) {
                        const userData = await meAgain.json();
                        if (userData.authenticated !== true) {
                            this.showLoginButton();
                            return;
                        }
                        const role = (userData.role || '').toString().toLowerCase();
                        if (role.includes('guest')) {
                            this.showLoginButton();
                            return;
                        }
                        this.showUserProfile(userData);
                        return;
                    }
                }
                // If still unauthorized, keep user on page and show login button (no redirect)
                this.showLoginButton();
            } else {
                this.showLoginButton();
            }
        } catch (error) {
            // Silently handle auth errors - don't log to console for unauthenticated users
            this.showLoginButton();
        }
    }

    async tryRefresh() {
        try {
            const resp = await fetch('/api/refresh', { method: 'POST', credentials: 'include' });
            return resp.ok;
        } catch (_) {
            return false;
        }
    }

    showUserProfile(userData) {
        // Role-based visibility for Create and Admin Panel
        const role = (userData.role || '').toString().toLowerCase();

        // Signal auth-error-handler that this page-load is authenticated.
        // Only do this for real (non-guest) users — guest tokens must NOT set this flag,
        // because guests legitimately get 401 from many endpoints and we must never
        // show the "Session Ended" modal for them.
        const isGuest = role.includes('guest');
        if (!isGuest && typeof window.markUserAuthenticated === 'function') {
            window.markUserAuthenticated();
        }

        // Hide login trigger and show user profile
        if (this.loginButton) {
            this.loginButton.style.display = 'none';
        }
        if (this.loginInlineContainer) {
            this.loginInlineContainer.style.display = 'none';
        }
        if (this.userProfile) {
            this.userProfile.style.display = 'block';
        }
        // My Items only for logged-in users who are not guests
        if (this.itemsNavItem) {
            this.itemsNavItem.style.display = isGuest ? 'none' : 'block';
        }
        // Treat any role containing "admin" as admin (covers Admin, Super Admin, Suber Admin typos, etc.)
        const isAdmin = role.includes('admin');

        // Toggle Create menu based on user permissions
        // Show Create menu for all authenticated users, but filter items based on permissions
        if (this.createNavItem) {
            // Always show Create menu for authenticated users
            // We'll filter the items inside based on permissions
            this.createNavItem.style.display = 'block';
            
            // Load user permissions and filter Create menu items
            this.filterCreateMenuItems(userData.id || userData.ID);
        }

        // Toggle Admin Panel link in profile dropdown
        const adminPanelLink = document.querySelector('a[href="/admin-panel.html"]');
        if (adminPanelLink) {
            adminPanelLink.style.display = isAdmin ? '' : 'none';
        }

        // Update user information
        if (this.userName) {
            this.userName.textContent = `${userData.firstName} ${userData.lastName}`;
        }

        if (this.userRole) {
            this.userRole.textContent = userData.role || 'User';
        }

        // Update "My Profile" link to point to user's profile page
        const myProfileLink = document.getElementById('myProfileLink');
        if (myProfileLink) {
            const userId = userData.id || userData.ID;
            if (userId) {
                myProfileLink.href = `/view/people/${userId}`;
            }
        }

        // Handle avatar
        this.updateAvatar(userData.avatarPath, userData.firstName, userData.lastName);

        // Apply user locale from server (people.Locale)
        if (window.I18n && typeof window.I18n.setLocaleFromMe === 'function') {
            window.I18n.setLocaleFromMe({ authenticated: true, locale: userData.locale });
        }

        // Show segments cube button, lock toggle, and notifications only for real (non-guest) users.
        // Guest users have no assigned segments, locks, or notifications.
        const cubeButton = document.getElementById('segmentsCubeToggle');
        const lockButton = document.getElementById('globalLockToggle');
        const notificationButton = document.getElementById('notificationToggle');
        if (!isGuest) {
            if (cubeButton) {
                cubeButton.style.display = 'flex';
                cubeButton.style.visibility = 'visible';
            }
            if (lockButton) {
                lockButton.style.display = 'flex';
                lockButton.style.visibility = 'visible';
            }
            if (notificationButton) {
                notificationButton.style.display = 'flex';
                notificationButton.style.visibility = 'visible';
            }
            this.initSegmentsCubePanel();
            this.initGlobalLockUI();
        } else {
            if (cubeButton) {
                cubeButton.style.display = 'none';
                cubeButton.style.visibility = 'hidden';
            }
            if (lockButton) {
                lockButton.style.display = 'none';
                lockButton.style.visibility = 'hidden';
            }
            if (notificationButton) {
                notificationButton.style.display = 'none';
                notificationButton.style.visibility = 'hidden';
            }
        }
    }
    
    /**
     * Initialize Segments Cube Panel
     * Dynamically loads the script if not already present
     */
    initSegmentsCubePanel() {
        // Check if already initialized
        if (window.globalSegmentsCubePanel && window.globalSegmentsCubePanel.isInitialized) {
            return;
        }
        
        // Check if script is already loaded
        const existingScript = document.querySelector('script[src*="segments-cube-panel.js"]');
        if (existingScript) {
            // Script exists, just initialize
            if (typeof window.globalSegmentsCubePanel !== 'undefined') {
                window.globalSegmentsCubePanel.initialize();
            }
            return;
        }
        
        // Dynamically load the segments cube panel script
        const script = document.createElement('script');
        script.src = '/assets/js/segments-cube-panel.js';
        script.async = true;
        script.onload = () => {
        };
        script.onerror = () => {
            console.warn('⚠️ Failed to load Segments Cube Panel script');
        };
        document.head.appendChild(script);
        
        // Also load the CSS if not present
        const existingCss = document.querySelector('link[href*="segments-cube-panel.css"]');
        if (!existingCss) {
            const css = document.createElement('link');
            css.rel = 'stylesheet';
            css.href = '/assets/css/segments-cube-panel.css';
            document.head.appendChild(css);
        }
    }
    
    /**
     * Initialize Global Lock UI
     * Dynamically loads the script if not already present
     * Shows lock icon in header when user has locked items
     */
    initGlobalLockUI() {
        // Check if already initialized
        if (window.globalLockUI && window.globalLockUI.isInitialized) {
            // Already initialized, just update the lock count
            window.globalLockUI.updateLockCount();
            return;
        }
        
        // Check if script is already loaded
        const existingScript = document.querySelector('script[src*="global-lock-ui.js"]');
        if (existingScript) {
            // Script exists, just initialize
            if (typeof window.globalLockUI !== 'undefined') {
                window.globalLockUI.initialize();
            }
            return;
        }
        
        // Dynamically load the global lock UI script
        const script = document.createElement('script');
        script.src = '/assets/js/global-lock-ui.js';
        script.async = true;
        script.onload = () => {
        };
        script.onerror = () => {
            console.warn('⚠️ Failed to load Global Lock UI script');
        };
        document.head.appendChild(script);
        
        // Also load the CSS if not present
        const existingCss = document.querySelector('link[href*="global-lock-ui.css"]');
        if (!existingCss) {
            const css = document.createElement('link');
            css.rel = 'stylesheet';
            css.href = '/assets/css/global-lock-ui.css';
            document.head.appendChild(css);
        }
    }

    showLoginButton() {
        // Hide user profile and show login button
        if (this.userProfile) {
            this.userProfile.style.display = 'none';
        }
        if (this.loginButton) {
            this.loginButton.style.display = 'flex';
        }
        if (this.loginInlineContainer) {
            this.loginInlineContainer.style.display = 'flex';
        }
        if (this.itemsNavItem) {
            this.itemsNavItem.style.display = 'none';
        }
        if (this.createNavItem) {
            this.createNavItem.style.display = 'none';
        }
        
        // Hide segments cube, lock, and notifications when not logged in (or guest)
        const cubeButton = document.getElementById('segmentsCubeToggle');
        if (cubeButton) {
            cubeButton.style.display = 'none';
            cubeButton.style.visibility = 'hidden';
        }
        const lockButton = document.getElementById('globalLockToggle');
        if (lockButton) {
            lockButton.style.display = 'none';
            lockButton.style.visibility = 'hidden';
        }
        const notificationButton = document.getElementById('notificationToggle');
        if (notificationButton) {
            notificationButton.style.display = 'none';
            notificationButton.style.visibility = 'hidden';
        }
        
        this.initLoginDropdown();
    }

    redirectToLogin() {
        window.location.href = '/login.html';
    }

    updateAvatar(avatarPath, firstName, lastName) {
        const initials = this.getInitials(firstName, lastName);

        // Update main avatar
        if (avatarPath && avatarPath.trim() !== '') {
            if (this.userAvatarImg) {
                this.userAvatarImg.onerror = () => {
                    // If image fails to load, fallback to initials
                    this.userAvatarImg.style.display = 'none';
                    this.userAvatarText.style.display = 'block';
                    this.userAvatarText.textContent = initials;
                };
                this.userAvatarImg.onload = () => {
                    // Image loaded successfully
                    this.userAvatarImg.style.display = 'block';
                    this.userAvatarText.style.display = 'none';
                };
                this.userAvatarImg.src = avatarPath;
            }
            if (this.userAvatarImgDropdown) {
                this.userAvatarImgDropdown.onerror = () => {
                    // If image fails to load, fallback to initials
                    this.userAvatarImgDropdown.style.display = 'none';
                    this.userAvatarTextDropdown.style.display = 'block';
                    this.userAvatarTextDropdown.textContent = initials;
                };
                this.userAvatarImgDropdown.onload = () => {
                    // Image loaded successfully
                    this.userAvatarImgDropdown.style.display = 'block';
                    this.userAvatarTextDropdown.style.display = 'none';
                };
                this.userAvatarImgDropdown.src = avatarPath;
            }
        } else {
            // Use initials
            if (this.userAvatarImg) {
                this.userAvatarImg.style.display = 'none';
                this.userAvatarText.style.display = 'block';
                this.userAvatarText.textContent = initials;
            }
            if (this.userAvatarImgDropdown) {
                this.userAvatarImgDropdown.style.display = 'none';
                this.userAvatarTextDropdown.style.display = 'block';
                this.userAvatarTextDropdown.textContent = initials;
            }
        }
    }

    getInitials(firstName, lastName) {
        const firstInitial = firstName ? firstName.charAt(0).toUpperCase() : '';
        const lastInitial = lastName ? lastName.charAt(0).toUpperCase() : '';
        return firstInitial + lastInitial;
    }

    async logout() {
        try {
            // Prevent beforeunload handler from releasing locks on logout
            // by temporarily disabling the lock manager's beforeunload handler
            // Release any active locks before logging out
                        // This ensures the object is not left locked when the user logs out
                        if (window.currentLockManager && window.currentLockManager.isLockAcquired) {
                            console.log('🔓 Releasing lock on logout...');
                            try {
                                await window.currentLockManager.releaseLock();
                            } catch (lockError) {
                                console.warn('Failed to release lock on logout:', lockError);
                            }
                        }

                        // Also release via LockInitHelper if available
                        if (window.LockInitHelper && window.LockInitHelper.releaseLock) {
                            try {
                                await window.LockInitHelper.releaseLock();
                            } catch (lockError) {
                                console.warn('Failed to release lock via LockInitHelper:', lockError);
                            }
                        }
            
            // Hide segments cube panel on logout
            const cubeButton = document.getElementById('segmentsCubeToggle');
            if (cubeButton) {
                cubeButton.style.display = 'none';
            }
            const cubePanel = document.getElementById('segmentsCubePanel');
            if (cubePanel) {
                cubePanel.classList.remove('open');
            }
            
            // Hide lock icon and panel on logout
            const lockButton = document.getElementById('globalLockToggle');
            if (lockButton) {
                lockButton.style.display = 'none';
            }
            const lockPanel = document.getElementById('globalLockedItemsPanel');
            if (lockPanel) {
                lockPanel.classList.remove('open');
            }
            
            const response = await fetch('/logout', {
                method: 'POST',
                credentials: 'include'
            });

            if (response.ok) {
                // Show logout message modal, which will redirect to login
                if (window.showLogoutMessage) {
                    window.showLogoutMessage('You have been logged out');
                } else {
                    // Fallback if modal utility not loaded
                    this.redirectToLogin();
                }
            } else {
                console.error('Logout failed');
                // Still show logout message even if logout request failed
                if (window.showLogoutMessage) {
                    window.showLogoutMessage('You have been logged out');
                } else {
                    this.redirectToLogin();
                }
            }
        } catch (error) {
            console.error('Error during logout:', error);
            // Still show logout message even if logout request failed
            if (window.showLogoutMessage) {
                window.showLogoutMessage('You have been logged out');
            } else {
                this.redirectToLogin();
            }
        }
    }

    /**
     * Position the profile dropdown using fixed positioning so it escapes overflow: hidden on parent containers.
     * @param {HTMLElement} trigger - The user profile trigger element
     * @param {HTMLElement} menu - The profile dropdown element
     */
    positionProfileDropdown(trigger, menu) {
        if (!trigger || !menu) return;
        const rect = trigger.getBoundingClientRect();
        const isRTL = document.documentElement.getAttribute('dir') === 'rtl' ||
            document.body.getAttribute('dir') === 'rtl' ||
            getComputedStyle(document.documentElement).direction === 'rtl';
        const menuWidth = menu.offsetWidth || 200;
        const gap = 8;

        menu.style.top = (rect.bottom + gap) + 'px';

        if (isRTL) {
            menu.style.left = rect.left + 'px';
            menu.style.right = 'auto';
        } else {
            const rightEdge = rect.right;
            const leftPos = rightEdge - menuWidth;
            menu.style.left = Math.max(8, leftPos) + 'px';
            menu.style.right = 'auto';
        }

        requestAnimationFrame(() => {
            const menuRect = menu.getBoundingClientRect();
            if (menuRect.bottom > window.innerHeight) {
                menu.style.top = (rect.top - menuRect.height - gap) + 'px';
            }
            if (menuRect.right > window.innerWidth) {
                menu.style.left = (window.innerWidth - menuRect.width - 8) + 'px';
            }
            if (menuRect.left < 8) {
                menu.style.left = '8px';
            }
        });
    }

    initProfileDropdown() {
        const profileDropdown = document.getElementById('profileDropdown');

        if (this.userProfile && profileDropdown) {
            // Avoid closing dropdown on the same click that opened it (first-click bug)
            this._profileJustOpened = false;

            // Toggle profile dropdown visibility when clicking avatar/profile
            this.userProfile.addEventListener('click', (e) => {
                const clickedInsideDropdown = e.target.closest('#profileDropdown');
                if (clickedInsideDropdown) {
                    // Allow default behavior for dropdown links (navigation)
                    return;
                }
                e.preventDefault();
                e.stopPropagation();
                if (!profileDropdown.classList.contains('show')) {
                    this._profileJustOpened = true;
                    setTimeout(() => { this._profileJustOpened = false; }, 80);
                    if (window.notificationPanel && typeof window.notificationPanel.closePanel === 'function') {
                        window.notificationPanel.closePanel();
                    }
                    if (window.globalSegmentsCubePanel && typeof window.globalSegmentsCubePanel.closePanel === 'function') {
                        window.globalSegmentsCubePanel.closePanel();
                    }
                    if (window.globalLockUI && typeof window.globalLockUI.closePanel === 'function') {
                        window.globalLockUI.closePanel();
                    }
                    const myItemsDd = document.getElementById('myItemsDropdown');
                    if (myItemsDd) { myItemsDd.classList.remove('show'); myItemsDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active')); }
                    const createDd = document.getElementById('createDropdown');
                    if (createDd) { createDd.classList.remove('show'); createDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active')); }
                    this.positionProfileDropdown(this.userProfile, profileDropdown);
                }
                profileDropdown.classList.toggle('show');
            });

            // Close profile dropdown when clicking outside (ignore the click that just opened it)
            document.addEventListener('click', (e) => {
                if (this._profileJustOpened) return;
                if (!this.userProfile) return;
                if (!this.userProfile.contains(e.target) && !profileDropdown.contains(e.target)) {
                    profileDropdown.classList.remove('show');
                }
            });

            // Close profile dropdown when pressing Escape key
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    profileDropdown.classList.remove('show');
                }
            });

            // Close profile dropdown when language changes so it doesn't stay in wrong position (RTL/LTR)
            window.addEventListener('languageChanged', () => {
                profileDropdown.classList.remove('show');
            });
        }
    }

    initLoginDropdown() {
        const loginBtn = document.getElementById('loginButton');
        const dropdown = document.getElementById('loginDropdown');
        const form = document.getElementById('headerLoginForm');
        const emailInput = document.getElementById('headerEmail');
        const passwordInput = document.getElementById('headerPassword');
        const errorBox = document.getElementById('headerLoginError');
        const submitBtn = document.getElementById('headerLoginSubmit');

        if (!loginBtn || !dropdown) return;

        // Toggle dropdown
        if (!loginBtn._loginBound) {
            loginBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                const isOpen = dropdown.classList.contains('show');
                if (isOpen) {
                    dropdown.classList.remove('show');
                } else {
                    dropdown.classList.add('show');
                    setTimeout(() => emailInput && emailInput.focus(), 0);
                }
            });
            loginBtn._loginBound = true;
        }

        // Refresh button: clear form and error, focus email
        const refreshBtn = document.getElementById('loginDropdownRefresh');
        if (refreshBtn && !refreshBtn._refreshBound) {
            refreshBtn.addEventListener('click', (e) => {
                e.preventDefault();
                if (form) form.reset();
                if (errorBox) {
                    errorBox.style.display = 'none';
                    errorBox.textContent = '';
                }
                setTimeout(() => emailInput && emailInput.focus(), 0);
            });
            refreshBtn._refreshBound = true;
        }

        // Close on outside click / Esc
        if (!dropdown._outsideBound) {
            document.addEventListener('click', (e) => {
                if (!dropdown.contains(e.target) && e.target !== loginBtn) {
                    dropdown.classList.remove('show');
                }
            });
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    dropdown.classList.remove('show');
                }
            });
            dropdown._outsideBound = true;
        }

        // Submit handler
        if (form && !form._submitBound) {
            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                if (!emailInput || !passwordInput) return;
                const email = emailInput.value.trim();
                const password = passwordInput.value;
                
                // Clear previous errors
                if (errorBox) {
                    errorBox.style.display = 'none';
                    errorBox.textContent = '';
                }
                
                // Client-side validation
                if (!email || email === '') {
                    if (errorBox) {
                        errorBox.textContent = 'Email address is required';
                        errorBox.style.display = 'block';
                    }
                    if (emailInput) emailInput.focus();
                    return;
                }
                
                if (!password || password === '') {
                    if (errorBox) {
                        errorBox.textContent = 'Password is required';
                        errorBox.style.display = 'block';
                    }
                    if (passwordInput) passwordInput.focus();
                    return;
                }
                
                if (submitBtn) submitBtn.disabled = true;

                try {
                    const resp = await fetch('/login', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        credentials: 'include',
                        body: JSON.stringify({ email, password })
                    });
                    
                    const contentType = resp.headers.get('content-type') || '';
                    let data = {};
                    let text = '';
                    
                    try {
                        if (contentType.includes('application/json')) {
                            data = await resp.json();
                        } else {
                            text = await resp.text();
                        }
                    } catch (parseErr) {
                        try { text = await resp.text(); } catch (_) {}
                    }
                    
                    if (resp.ok) {
                        dropdown.classList.remove('show');
                        // Refresh header to show user panel
                        await this.refreshUserData();
                        try { window.dispatchEvent(new Event('auth:login')); } catch (_) { }
                    } else {
                        // Parse error to get proper message
                        const errorMessage = (data && data.error) ? data.error : 
                                           (data && data.message) ? data.message : 
                                           text || 'Invalid email or password. Please check your credentials and try again.';
                        
                        if (errorBox) {
                            errorBox.textContent = errorMessage;
                            errorBox.style.display = 'block';
                        }
                    }
                } catch (err) {
                    console.error('Login error:', err);
                    const errorMessage = err.message && err.message.includes('fetch') 
                        ? 'Network error. Please check your internet connection and try again.' 
                        : 'An unexpected error occurred. Please try again.';
                    
                    if (errorBox) {
                        errorBox.textContent = errorMessage;
                        errorBox.style.display = 'block';
                    }
                } finally {
                    if (submitBtn) submitBtn.disabled = false;
                }
            });
            form._submitBound = true;
        }
    }

    // Public method to refresh user data
    async refreshUserData() {
        await this.checkAuthStatus();
    }
    
    /**
     * Filter Create menu items based on user permissions
     * Only show facets that the user has "New" (Create) permission for
     * @param {number} userId - User ID
     */
    async filterCreateMenuItems(userId) {
        if (!userId || userId <= 0) {
            // Hide Create menu if no user ID
            if (this.createNavItem) {
                this.createNavItem.style.display = 'none';
            }
            return;
        }
        
        try {
            // Fetch all user permissions
            const permResp = await fetch('/api/user/permissions', { 
                method: 'GET', 
                credentials: 'include' 
            });
            
            if (!permResp.ok) {
                console.warn('Failed to fetch user permissions for Create menu filtering');
                // If we can't fetch permissions, show all items (fail open)
                return;
            }
            
            const permData = await permResp.json();
            if (!permData.success || !permData.permissions) {
                console.warn('Invalid permissions response');
                return;
            }
            
            const permissions = permData.permissions;
            const isAdmin = permData.isAdmin || false;
            
            // Map of facet URLs to module names for permission checking
            // Note: Module names must match the normalized names in PermissionService.normalizeModuleName()
            const facetModuleMap = {
                '/committee.html': 'Committee',
                '/policy.html': 'Policy',
                '/process.html': 'Process',
                '/project.html': 'Project',
                '/dataset.html': 'Data Sets',
                '/system.html': 'System',
                '/system-interface.html': 'Interface',
                '/glossary.html': 'Glossary',
                '/business-area.html': 'Business Area', // Note: singular to match PermissionService
                '/capability.html': 'Capability',
                '/client.html': 'Client',
                '/legal-entity.html': 'Legal Entity',
                '/org-unit.html': 'Org Unit', // Note: singular to match PermissionService
                '/people.html': 'People',
                '/product.html': 'Product',
                '/regulation.html': 'Regulation',
                '/regulatorytheme.html': 'Regulatory Theme',
                '/regulatory-theme.html': 'Regulatory Theme', // Alternative URL format
                '/regulator.html': 'Regulator',
                '/geography.html': 'Geography'
            };
            
            // Get all sub-dropdown items
            const createDropdown = document.getElementById('createDropdown');
            if (!createDropdown) return;
            
            const allSubItems = createDropdown.querySelectorAll('.sub-dropdown-item');
            let visibleItemsCount = 0;
            
            // Check each item and hide if user doesn't have Create permission
            allSubItems.forEach(item => {
                const href = item.getAttribute('href');
                if (!href) return;
                
                const moduleName = facetModuleMap[href];
                if (!moduleName) {
                    // Unknown facet, show it (fail open)
                    item.style.display = '';
                    visibleItemsCount++;
                    return;
                }
                
                // Check if user has "New" permission for this module
                const modulePermissions = permissions[moduleName];
                const hasCreatePermission = isAdmin || 
                    (modulePermissions && (
                        modulePermissions.includes('New') || 
                        modulePermissions.includes('Create') ||
                        modulePermissions.includes('Write')
                    ));
                
                if (hasCreatePermission) {
                    item.style.display = '';
                    visibleItemsCount++;
                } else {
                    item.style.display = 'none';
                }
            });
            
            // Hide entire Create menu if no items are visible
            // But keep it visible if user is admin (they should see all)
            if (visibleItemsCount === 0 && !isAdmin) {
                if (this.createNavItem) {
                    this.createNavItem.style.display = 'none';
                }
            } else {
                // Also hide empty submenu sections
                const subMenus = createDropdown.querySelectorAll('.sub-dropdown-menu');
                subMenus.forEach(subMenu => {
                    const visibleItems = subMenu.querySelectorAll('.sub-dropdown-item:not([style*="display: none"])');
                    if (visibleItems.length === 0) {
                        // Hide the parent dropdown-item that contains this submenu
                        const parentItem = subMenu.closest('.dropdown-item.has-submenu');
                        if (parentItem) {
                            parentItem.style.display = 'none';
                        }
                    }
                });
            }
            
        } catch (error) {
            console.error('Error filtering Create menu items:', error);
            // On error, show all items (fail open)
        }
    }
}

// Initialize header auth manager when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    // Wait for header to be injected if using shared header
    const initHeaderAuth = () => {
        // Check if header elements exist
        const userProfile = document.getElementById('userProfile');
        const loginButton = document.getElementById('loginButton');

        if (userProfile || loginButton) {
            // Header is loaded, initialize auth manager
            if (!window.headerAuthManager) {
                window.headerAuthManager = new HeaderAuthManager();
            }
        } else {
            // Header not yet loaded, retry after a short delay
            setTimeout(initHeaderAuth, 100);
        }
    };

    // Start checking for header elements
    initHeaderAuth();
});

// Listen for headerReady event from main.js (when header is injected)
document.addEventListener('headerReady', () => {
    if (!window.headerAuthManager) {
        window.headerAuthManager = new HeaderAuthManager();
    } else {
        // Re-initialize if already exists
        window.headerAuthManager.init();
    }
});

// Also check periodically in case header loads after DOMContentLoaded
let headerCheckAttempts = 0;
const maxHeaderCheckAttempts = 50; // 5 seconds max wait

const checkForHeader = () => {
    if (headerCheckAttempts >= maxHeaderCheckAttempts) {
        console.warn('Header elements not found after maximum attempts');
        return;
    }

    const userProfile = document.getElementById('userProfile');
    const loginButton = document.getElementById('loginButton');

    if ((userProfile || loginButton) && !window.headerAuthManager) {
        window.headerAuthManager = new HeaderAuthManager();
    } else if (!userProfile && !loginButton) {
        headerCheckAttempts++;
        setTimeout(checkForHeader, 100);
    }
};

// Start checking for header elements
setTimeout(checkForHeader, 200);

/**
 * Header Tooltip Management
 * Handles fetching and displaying tooltips for menu items
 */
class HeaderTooltipManager {
    constructor() {
        this.tooltips = {};
        this.tooltipElement = null;
        this.hideTimeoutId = null;
        this.activeTooltipTarget = null;
        this.pinnedParentMenuItem = null;
        this.pinnedParentMenuItemWasActive = false;
        this.structuredHelpLinks = new Set([
            'menu-committee-tip',
            'menu-policy-tip',
            'menu-process-tip',
            'menu-project-tip',
            'menu-dataset-tip',
            'menu-system-tip',
            'menu-interface-tip',
            'menu-glossary-tip',
            'menu-regulation-tip',
            'menu-regulatorytheme-tip',
            'menu-regulator-tip',
            'menu-geography-tip',
            'menu-business-tip',
            'menu-capability-tip',
            'menu-client-tip',
            'menu-legalentity-tip',
            'menu-orgunit-tip',
            'menu-people-tip',
            'menu-product-tip'
        ]);
        this.init();
    }

    async init() {
        // Create tooltip element
        this.createTooltipElement();

        // Fetch tooltip data
        await this.fetchTooltipData();

        // Add event listeners
        this.addEventListeners();
    }

    createTooltipElement() {
        this.tooltipElement = document.createElement('div');
        this.tooltipElement.className = 'header-menu-tooltip';
        this.tooltipElement.style.display = 'none';
        this.tooltipElement.style.position = 'absolute';
        this.tooltipElement.style.zIndex = '10000';
        this.tooltipElement.style.maxWidth = '500px';
        this.tooltipElement.style.minWidth = '360px';
        // Must be interactive so links inside tooltip can be clicked.
        this.tooltipElement.style.pointerEvents = 'auto';
        document.body.appendChild(this.tooltipElement);
    }

    async fetchTooltipData() {
        try {
            const response = await fetch('/admin/api/static-pages');
            if (response.ok) {
                const pages = await response.json();
                pages.forEach(page => {
                    // Map link to content. The link in DB is like 'budg.local:9999/menu-committee-tip'
                    // We need to extract the last part 'menu-committee-tip' to match data-tip-link

                    const linkParts = page.link.split('/');
                    const key = linkParts[linkParts.length - 1];
                    this.tooltips[key] = page.content;
                });
            }
        } catch (error) {
            console.error('Error fetching tooltip data:', error);
        }
    }

    addEventListeners() {
        document.body.addEventListener('mouseover', (e) => {
            if (e.target.classList.contains('menu-tooltip-icon')) {
                const link = e.target.getAttribute('data-tip-link');
                const content = this.resolveTooltipContent(link);
                if (content) {
                    this.clearHideTimer();
                    this.activeTooltipTarget = e.target;
                    this.showTooltip(e.target, content);
                }
            }
        });

        document.body.addEventListener('mouseout', (e) => {
            if (e.target.classList.contains('menu-tooltip-icon')) {
                const nextElement = e.relatedTarget;
                if (nextElement && this.tooltipElement && this.tooltipElement.contains(nextElement)) {
                    return;
                }
                this.scheduleHideTooltip();
            }
        });

        if (this.tooltipElement) {
            this.tooltipElement.addEventListener('mouseenter', () => {
                this.clearHideTimer();
            });

            this.tooltipElement.addEventListener('mouseleave', () => {
                this.scheduleHideTooltip();
            });
        }
    }

    resolveTooltipContent(link) {
        if (!link) return null;

        if (this.structuredHelpLinks.has(link)) {
            return this.buildStructuredHelpCard(link);
        }

        return this.tooltips[link] || null;
    }

    buildStructuredHelpCard(link) {
        const cardConfig = this.getStructuredHelpConfig()[link];
        if (!cardConfig) return null;

        const sectionTips = this.escapeHtml(this.getI18nText('headerHelpCards.sections.tips', 'TIPS'));
        const sectionRelationships = this.escapeHtml(this.getI18nText('headerHelpCards.sections.relationships', 'RELATIONSHIPS'));
        const sectionRecommended = this.escapeHtml(this.getI18nText('headerHelpCards.sections.recommended', 'Recommended'));
        const moreInfoPrefix = this.escapeHtml(this.getI18nText('headerHelpCards.sections.moreInfoPrefix', 'More information available in the'));
        const onlineHelpLabel = this.escapeHtml(this.getI18nText('headerHelpCards.sections.onlineHelp', 'Online Help'));

        const title = this.escapeHtml(this.getI18nText(cardConfig.titleKey, cardConfig.titleFallback));
        const definition = this.escapeHtml(this.getI18nText(cardConfig.definitionKey, cardConfig.definitionFallback));

        const tips = (cardConfig.tips || [])
            .map((tip) => this.escapeHtml(this.getI18nText(tip.key, tip.fallback)))
            .filter(Boolean);

        const relationshipGroups = this.buildRelationshipGroups(cardConfig);

        const tipsSectionHtml = tips.length
            ? `
                <div class="menu-help-section">
                    <div class="menu-help-section-header">
                        <i class="fas fa-info-circle" aria-hidden="true"></i>
                        <span>${sectionTips}</span>
                    </div>
                    <ul class="menu-help-list">
                        ${tips.map((tipText) => `<li>${tipText}</li>`).join('')}
                    </ul>
                </div>
            `
            : '';

        const relationshipsSectionHtml = relationshipGroups.length
            ? `
                    <div class="menu-help-section">
                        <div class="menu-help-section-header">
                            <i class="fas fa-tools" aria-hidden="true"></i>
                            <span>${sectionRelationships}</span>
                        </div>
                        <div class="menu-help-relationship-groups ${relationshipGroups.length > 1 ? 'menu-help-relationship-groups-two-columns' : ''}">
                            ${relationshipGroups.map((group) => `
                                <div class="menu-help-relationship-group">
                                    <div class="menu-help-subtitle">${this.escapeHtml(group.title)}</div>
                                    <div class="menu-help-links">
                                        ${group.links.map((link) => `<a href="${this.escapeHtml(link.href)}" class="menu-help-link">${this.escapeHtml(link.label)}</a>`).join('')}
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                `
            : '';

        return `
            <div class="menu-help-card">
                <div class="menu-help-title">${title}</div>
                <div class="menu-help-body">
                    <p class="menu-help-definition">${definition}</p>
                    ${tipsSectionHtml}
                    ${relationshipsSectionHtml}
                </div>
                <div class="menu-help-footer">
                    ${moreInfoPrefix} <a href="/WEB-HELP/OnlineHelp.html" target="_blank" rel="noopener noreferrer">${onlineHelpLabel}</a>.
                </div>
            </div>
        `;
    }

    getStructuredHelpConfig() {
        return {
            'menu-committee-tip': {
                titleKey: 'headerHelpCards.facets.committee.title',
                titleFallback: 'Committee Help',
                definitionKey: 'headerHelpCards.facets.committee.definition',
                definitionFallback: 'A Committee is a group or forum entrusted with certain responsibilities or decision making duties.',
                tips: [],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-policy-tip': {
                titleKey: 'headerHelpCards.facets.policy.title',
                titleFallback: 'Policy Help',
                definitionKey: 'headerHelpCards.facets.policy.definition',
                definitionFallback: 'A Policy describes the agreed manner in which an organisation should act in certain situations, e.g. to meet the needs of regulatory imperatives.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.policy.tips.breakdownRules',
                        fallback: 'Policies can be broken down into rule sets to capture a more granular view.'
                    },
                    {
                        key: 'headerHelpCards.facets.policy.tips.linkDependencies',
                        fallback: 'Policies can be linked to other Policies to document dependencies.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-process-tip': {
                titleKey: 'headerHelpCards.facets.process.title',
                titleFallback: 'Process Help',
                definitionKey: 'headerHelpCards.facets.process.definition',
                definitionFallback: 'A Process describes a set of manual or automated activities required to achieve a given outcome.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.process.tips.predecessorFlow',
                        fallback: 'Use edit mode and the process predecessor field to link process steps together and describe process flow.'
                    },
                    {
                        key: 'headerHelpCards.facets.process.tips.linkContext',
                        fallback: 'Link Processes to Glossary and System items to describe their data and system context.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.businessArea',
                        labelFallback: 'Business Area',
                        href: '/business-area.html'
                    },
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-project-tip': {
                titleKey: 'headerHelpCards.facets.project.title',
                titleFallback: 'Project Help',
                definitionKey: 'headerHelpCards.facets.project.definition',
                definitionFallback: 'A Project describes a change effort (big or small) outside of Business As Usual activities.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.project.tips.linkResources',
                        fallback: 'Link Projects to the data, system and business resources they are looking to affect or rely on.'
                    },
                    {
                        key: 'headerHelpCards.facets.project.tips.breakdownSubprojects',
                        fallback: 'Projects can be broken down into sub-projects to capture a more granular view.'
                    },
                    {
                        key: 'headerHelpCards.facets.project.tips.linkDependencies',
                        fallback: 'Projects can be linked to other Projects to document dependencies.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-dataset-tip': {
                titleKey: 'headerHelpCards.facets.dataset.title',
                titleFallback: 'Data Set Help',
                definitionKey: 'headerHelpCards.facets.dataset.definition',
                definitionFallback: 'Data sets commonly describe tables of data that reside in systems. Data sets are also used to describe reports, value lists, records or any other data structures.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.dataset.tips.createSystemGlossaryFirst',
                        fallback: 'Make sure System and Glossary objects are created first.'
                    },
                    {
                        key: 'headerHelpCards.facets.dataset.tips.attributesAddedHere',
                        fallback: 'Attributes and attribute flows are added here.'
                    },
                    {
                        key: 'headerHelpCards.facets.dataset.tips.bulkUploader',
                        fallback: 'When adding lots of Data Sets / Attributes, consider using the Bulk-Uploader.'
                    }
                ],
                relationshipGroups: [
                    {
                        titleKey: 'headerHelpCards.sections.required',
                        titleFallback: 'Required',
                        items: [
                            {
                                labelKey: 'headerHelpCards.relationshipItems.system',
                                labelFallback: 'System',
                                href: '/system.html'
                            },
                            {
                                labelKey: 'headerHelpCards.relationshipItems.glossaryForDataSet',
                                labelFallback: 'Glossary (for Data Set)',
                                href: '/glossary.html'
                            }
                        ]
                    },
                    {
                        titleKey: 'headerHelpCards.sections.recommended',
                        titleFallback: 'Recommended',
                        items: [
                            {
                                labelKey: 'headerHelpCards.relationshipItems.glossaryForAttributes',
                                labelFallback: 'Glossary (for Attributes)',
                                href: '/glossary.html'
                            },
                            {
                                labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                                labelFallback: 'People (Stakeholders)',
                                href: '/people.html'
                            }
                        ]
                    }
                ]
            },
            'menu-system-tip': {
                titleKey: 'headerHelpCards.facets.system.title',
                titleFallback: 'System Help',
                definitionKey: 'headerHelpCards.facets.system.definition',
                definitionFallback: 'A System is a container of data, either in the traditional sense of a IT supported software application or a more tactical user application like Ms Excel, Ms Access etc.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.system.tips.endUserComputing',
                        fallback: 'Systems can be used to describe Ms Excel, Sharepoints, and other end user computing.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.glossary',
                        labelFallback: 'Glossary',
                        href: '/glossary.html'
                    },
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-interface-tip': {
                titleKey: 'headerHelpCards.facets.systemInterface.title',
                titleFallback: 'System Interface Help',
                definitionKey: 'headerHelpCards.facets.systemInterface.definition',
                definitionFallback: 'A system interface represents the flow of information between systems. After you create an interface, you can specify the flow of attributes through the interface.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.systemInterface.tips.oneWay',
                        fallback: 'Interfaces flow one way. To represent information moving in two directions, create two Interfaces.'
                    },
                    {
                        key: 'headerHelpCards.facets.systemInterface.tips.relatedSystemsDotted',
                        fallback: 'When you create an interface, the related systems are represented as dotted lines in Maps.'
                    },
                    {
                        key: 'headerHelpCards.facets.systemInterface.tips.attributeFlows',
                        fallback: 'Attribute level flows can be linked to interfaces (in Data Sets)'
                    }
                ],
                relationshipGroups: [
                    {
                        titleKey: 'headerHelpCards.sections.required',
                        titleFallback: 'Required',
                        items: [
                            {
                                labelKey: 'headerHelpCards.relationshipItems.systemsSourceTarget',
                                labelFallback: 'Systems (Source and Target)',
                                href: '/system.html'
                            }
                        ]
                    },
                    {
                        titleKey: 'headerHelpCards.sections.recommended',
                        titleFallback: 'Recommended',
                        items: [
                            {
                                labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                                labelFallback: 'People (Stakeholders)',
                                href: '/people.html'
                            }
                        ]
                    }
                ]
            },
            'menu-glossary-tip': {
                titleKey: 'headerHelpCards.facets.glossary.title',
                titleFallback: 'Glossary Help',
                definitionKey: 'headerHelpCards.facets.glossary.definition',
                definitionFallback: 'The Glossary captures an organisation\'s agreed definitions for different concepts, allowing for the creation of a common language.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.glossary.tips.linkDataToBusiness',
                        fallback: 'Glossary is how BUDG links data to the business viewpoints (e.g. process). A well populated glossary is essential for understanding and connecting data to its business context.'
                    },
                    {
                        key: 'headerHelpCards.facets.glossary.tips.structuredHierarchically',
                        fallback: 'Glossary items can be structured hierarchically and be related to one another.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-regulation-tip': {
                titleKey: 'headerHelpCards.facets.regulation.title',
                titleFallback: 'Regulation Help',
                definitionKey: 'headerHelpCards.facets.regulation.definition',
                definitionFallback: 'Regulation holds the organisation\'s interpretation of regulatory documentation',
                tips: [
                    {
                        key: 'headerHelpCards.facets.regulation.tips.linkExternalToInternal',
                        fallback: 'BUDG can help you to link external regulation to internal policies to ensure compliance.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-regulatorytheme-tip': {
                titleKey: 'headerHelpCards.facets.regulatoryTheme.title',
                titleFallback: 'Regulatory Theme Help',
                definitionKey: 'headerHelpCards.facets.regulatoryTheme.definition',
                definitionFallback: 'A regulatory theme classifies a regulation based on the type of legislation.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.regulatoryTheme.tips.classifyRegulations',
                        fallback: 'This is used to classify regulations that have been created in BUDG, such as privacy and capital adequacy. The facet is hierarchical and allows grouping of themes.'
                    }
                ],
                relationships: []
            },
            'menu-regulator-tip': {
                titleKey: 'headerHelpCards.facets.regulator.title',
                titleFallback: 'Regulator Help',
                definitionKey: 'headerHelpCards.facets.regulator.definition',
                definitionFallback: 'A regulator is typically an official body that publishes or enforces a regulation.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.regulator.tips.linkToRegulations',
                        fallback: 'Enter the name of the regulatory body. After you create a regulator, you can link it to the regulations that it publishes or controls.'
                    }
                ],
                relationships: []
            },
            'menu-geography-tip': {
                titleKey: 'headerHelpCards.facets.geography.title',
                titleFallback: 'Geography Help',
                definitionKey: 'headerHelpCards.facets.geography.definition',
                definitionFallback: 'Geography describes geographical regions or countries that are relevant to the aims of your data governance requirements.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.geography.tips.identifyCountries',
                        fallback: 'Geography can be used to identify the countries that are affected by different regulations. Use the facet to show that a regulation applies only to certain countries or regions. The administrator must create one overall structure of geography to suit the needs of the whole BUDG instance. The facet is hierarchical. For example, countries such as France, Germany, and Spain can be grouped under Europe or Western Europe for easy group searches.'
                    }
                ],
                relationships: []
            },
            'menu-business-tip': {
                titleKey: 'headerHelpCards.facets.businessArea.title',
                titleFallback: 'Business Help',
                definitionKey: 'headerHelpCards.facets.businessArea.definition',
                definitionFallback: 'A Business Area is a department or team with certain assigned responsibilities within the wider organisation.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.businessArea.tips.notOrgUnit',
                        fallback: 'Business Areas should not be confused with Org Units. Org Units contain the offical structure from an HR perspective whereas Business Areas are typically more fluid and in line with how the business is run.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-capability-tip': {
                titleKey: 'headerHelpCards.facets.capability.title',
                titleFallback: 'Capability Help',
                definitionKey: 'headerHelpCards.facets.capability.definition',
                definitionFallback: 'A Capability describes an organisational-level skill that is realised through people, process, and/or technology.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.capability.tips.topLevelView',
                        fallback: 'Capabilities provide a top level view on how the firms data, IT assets and business resources interlink to make up organizational capabilities.'
                    },
                    {
                        key: 'headerHelpCards.facets.capability.tips.linkCommittees',
                        fallback: 'Capabilities can be linked to Committees to capture the corporate governance arrangements.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-client-tip': {
                titleKey: 'headerHelpCards.facets.client.title',
                titleFallback: 'Client Help',
                definitionKey: 'headerHelpCards.facets.client.definition',
                definitionFallback: 'The Client inventory allows you to capture client segmentations and link those to the data, system and business resources that supports those client segments.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.client.tips.topLevelView',
                        fallback: 'Client segments provide a top level view on how the firm\'s data, IT assets and business resources support given Client segments.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-legalentity-tip': {
                titleKey: 'headerHelpCards.facets.legalEntity.title',
                titleFallback: 'Legal Entity Help',
                definitionKey: 'headerHelpCards.facets.legalEntity.definition',
                definitionFallback: 'Legal Entity describes the legal structure of the firm and / or its partners.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.legalEntity.tips.linkAssets',
                        fallback: 'Link Legal Entities to the data, system and business assets they rely on to capture their relevance from a Legal Entity perspective.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            },
            'menu-orgunit-tip': {
                titleKey: 'headerHelpCards.facets.orgUnit.title',
                titleFallback: 'Org Unit Help',
                definitionKey: 'headerHelpCards.facets.orgUnit.definition',
                definitionFallback: 'Org Unit represents HR\'s view of the organisation\'s structure.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.orgUnit.tips.integrateHr',
                        fallback: 'BUDG can integrate with the firm\'s HR systems to source and refresh Org Unit information.'
                    }
                ],
                relationships: []
            },
            'menu-people-tip': {
                titleKey: 'headerHelpCards.facets.people.title',
                titleFallback: 'People Help',
                definitionKey: 'headerHelpCards.facets.people.definition',
                definitionFallback: 'The People inventory holds a list of the people in the organisation.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.people.tips.assignRoles',
                        fallback: 'Once created, People can be assigned different roles and responsibilities against each of the objects in BUDG.'
                    },
                    {
                        key: 'headerHelpCards.facets.people.tips.singleSignOn',
                        fallback: 'BUDG can integrate with the firm\'s HR and autentication systems to support single sign-on.'
                    }
                ],
                relationshipGroups: [
                    {
                        titleKey: 'headerHelpCards.sections.required',
                        titleFallback: 'Required',
                        items: [
                            {
                                labelKey: 'headerHelpCards.relationshipItems.orgUnit',
                                labelFallback: 'Org Unit',
                                href: '/org-unit.html'
                            }
                        ]
                    }
                ]
            },
            'menu-product-tip': {
                titleKey: 'headerHelpCards.facets.product.title',
                titleFallback: 'Product Help',
                definitionKey: 'headerHelpCards.facets.product.definition',
                definitionFallback: 'The Product inventory allows you to capture Product segmentations and link those to the data, system and business resources that supports those client segments.',
                tips: [
                    {
                        key: 'headerHelpCards.facets.product.tips.topLevelView',
                        fallback: 'Product segments provide a top level view on how the firm\'s data, IT assets and business resources support given Product segments.'
                    }
                ],
                relationships: [
                    {
                        labelKey: 'headerHelpCards.relationshipItems.peopleStakeholders',
                        labelFallback: 'People (Stakeholders)',
                        href: '/people.html'
                    }
                ]
            }
        };
    }

    buildRelationshipGroups(cardConfig) {
        const directRelationships = Array.isArray(cardConfig.relationships) ? cardConfig.relationships : [];

        if ((!Array.isArray(cardConfig.relationshipGroups) || cardConfig.relationshipGroups.length === 0) && directRelationships.length === 0) {
            return [];
        }

        const defaultGroup = {
            title: this.getI18nText('headerHelpCards.sections.recommended', 'Recommended'),
            links: directRelationships.map((item) => ({
                label: this.getI18nText(item.labelKey, item.labelFallback),
                href: item.href || '#'
            }))
        };

        if (!Array.isArray(cardConfig.relationshipGroups) || cardConfig.relationshipGroups.length === 0) {
            return [defaultGroup];
        }

        return cardConfig.relationshipGroups.map((group) => ({
            title: this.getI18nText(group.titleKey, group.titleFallback),
            links: (group.items || []).map((item) => ({
                label: this.getI18nText(item.labelKey, item.labelFallback),
                href: item.href || '#'
            }))
        }));
    }

    getI18nText(key, fallback) {
        if (!window.I18n || typeof window.I18n.t !== 'function') return fallback;
        const translated = window.I18n.t(key);
        return translated && translated !== key ? translated : fallback;
    }

    escapeHtml(text) {
        if (text == null) return '';
        const div = document.createElement('div');
        div.textContent = String(text);
        return div.innerHTML;
    }

    showTooltip(target, content) {
        if (!this.tooltipElement) return;

        this.clearHideTimer();
        this.pinParentCreateSubmenu(target);
        this.tooltipElement.innerHTML = content;
        this.tooltipElement.style.display = 'block';

        const rect = target.getBoundingClientRect();
        const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
        const scrollLeft = window.pageXOffset || document.documentElement.scrollLeft;
        const isRtl = document.documentElement.getAttribute('dir') === 'rtl';
        this.tooltipElement.setAttribute('dir', isRtl ? 'rtl' : 'ltr');
        const tooltipRect = this.tooltipElement.getBoundingClientRect();
        const tooltipWidth = Math.ceil(tooltipRect.width) || 420;
        const gap = 10;

        let top = rect.top + scrollTop;
        let left;

        const maxTop = scrollTop + window.innerHeight - tooltipRect.height - gap;
        if (top > maxTop) {
            top = Math.max(scrollTop + gap, maxTop);
        }

        if (isRtl) {
            // RTL: show tooltip to the left of the icon
            left = rect.left + scrollLeft - tooltipWidth - gap;
            if (left < 0) {
                left = rect.right + scrollLeft + gap; // Fallback: show to the right
            }
        } else {
            // LTR: position to the right of the icon
            left = rect.right + scrollLeft + gap;
            if (left + tooltipWidth > window.innerWidth) {
                left = rect.left + scrollLeft - tooltipWidth - gap; // Show to the left
            }
        }

        this.tooltipElement.style.top = `${top}px`;
        this.tooltipElement.style.left = `${left}px`;
    }

    hideTooltip() {
        this.clearHideTimer();
        this.activeTooltipTarget = null;
        if (this.tooltipElement) {
            this.tooltipElement.style.display = 'none';
        }
        this.unpinParentCreateSubmenu();
    }

    scheduleHideTooltip(delayMs = 140) {
        this.clearHideTimer();
        this.hideTimeoutId = window.setTimeout(() => {
            this.hideTooltip();
        }, delayMs);
    }

    clearHideTimer() {
        if (this.hideTimeoutId) {
            clearTimeout(this.hideTimeoutId);
            this.hideTimeoutId = null;
        }
    }

    pinParentCreateSubmenu(target) {
        const parentMenuItem = target
            ?.closest('.sub-dropdown-menu')
            ?.closest('.dropdown-item.has-submenu');

        if (!parentMenuItem) {
            this.unpinParentCreateSubmenu();
            return;
        }

        // If switching between facets, release previous pin first.
        if (this.pinnedParentMenuItem && this.pinnedParentMenuItem !== parentMenuItem) {
            this.unpinParentCreateSubmenu();
        }

        if (!this.pinnedParentMenuItem) {
            this.pinnedParentMenuItem = parentMenuItem;
            this.pinnedParentMenuItemWasActive = parentMenuItem.classList.contains('active');
        }

        parentMenuItem.classList.add('active');
    }

    unpinParentCreateSubmenu() {
        if (!this.pinnedParentMenuItem) return;

        if (!this.pinnedParentMenuItemWasActive) {
            this.pinnedParentMenuItem.classList.remove('active');
        }

        this.pinnedParentMenuItem = null;
        this.pinnedParentMenuItemWasActive = false;
    }
}

// Initialize HeaderTooltipManager
document.addEventListener('DOMContentLoaded', () => {
    new HeaderTooltipManager();
});