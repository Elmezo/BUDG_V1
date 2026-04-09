/**
 * Main Dashboard Controller
 * Initializes and manages all dashboard widgets
 */

class Dashboard {
    constructor() {
        this.widgets = {};
        this.currentUser = null;
        this.isAuthenticated = false;
        this.dragDrop = null;
        this.currentDashboardId = null; // Track current dashboard ID
        this.dashboardOwnerId = null; // Track dashboard owner ID
        
        // Store reference globally for other modules
        window.dashboard = this;
    }

    /**
     * Initialize the dashboard
     */
    async init() {
        // Check if export libraries are loaded
        this.checkExportLibraries();
        
        try {
            // Check authentication
            await this.checkAuthentication();
            
            if (!this.isAuthenticated) {
                const subNav = document.getElementById('subNav');
                if (subNav) {
                    subNav.style.display = 'none';
                    document.body.classList.remove('has-sub-nav');
                }
                this.showHeroSection();
                return;
            }

            // Show sub-navigation and initialize tab switching (async)
            await this.initSubNavigation();
            
            // Load existing user dashboards and create tabs for them
            await this.loadUserDashboards();
            
            // Apply dashboard visibility state (including main dashboard)
            this.applyDashboardVisibility();
            
            // Check URL parameter or default to home
            const urlParams = new URLSearchParams(window.location.search);
            const tab = urlParams.get('tab') || 'home';
            
            // Set active tab
            const homeTab = document.getElementById('homeTab');
            const dashboardTab = document.getElementById('dashboardTab');
            
            if (tab === 'dashboard') {
                // Check if main dashboard is hidden
                let isMainHidden = false;
                try {
                    const saved = localStorage.getItem('dashboardHiddenDashboards');
                    if (saved !== null && saved !== '') {
                        const hiddenDashboards = JSON.parse(saved);
                        isMainHidden = hiddenDashboards.includes('main');
                    }
                } catch (e) {
                    console.error('Error parsing hiddenDashboards:', e);
                }
                
                if (isMainHidden) {
                    // Main dashboard is hidden, switch to first visible custom dashboard or stay on home
                    const visibleCustomTab = document.querySelector('.sub-nav-tabs [data-dashboard-id]:not([data-dashboard-id="main"]):not([data-dashboard-id="null"])');
                    if (visibleCustomTab && visibleCustomTab.style.display !== 'none') {
                        const dashboardId = parseInt(visibleCustomTab.getAttribute('data-dashboard-id'));
                        await this.switchToDashboard(dashboardId);
                    } else {
                        // No visible dashboards, show home
                        if (homeTab) homeTab.classList.add('active');
                        if (dashboardTab) dashboardTab.classList.remove('active');
                        this.showHeroSection();
                        this.updateActionsMenu('home');
                    }
                } else {
                    // Main dashboard is visible, switch to it
                if (dashboardTab) dashboardTab.classList.add('active');
                if (homeTab) homeTab.classList.remove('active');
                // Reset to default dashboard and switch to it
                this.currentDashboardId = null;
                // Remove active from all custom dashboard tabs
                    const customTabs = document.querySelectorAll('.sub-nav-tabs [data-dashboard-id]');
                customTabs.forEach(t => t.classList.remove('active'));
                // Switch to main dashboard using switchToDashboard to ensure proper loading
                await this.switchToDashboard(null);
                }
            } else {
                if (homeTab) homeTab.classList.add('active');
                if (dashboardTab) dashboardTab.classList.remove('active');
                this.showHeroSection();
                // Update actions menu for home tab
                this.updateActionsMenu('home');
            }
        } catch (error) {
            console.error('Error initializing dashboard:', error);
            this.showHeroSection();
        }
    }

    /**
     * Check if dashboard sub-navigation should be shown
     * @returns {Promise<boolean>} True if sub-nav should be shown
     */
    async shouldShowSubNavigation() {
        try {
            const response = await fetch('/api/system-settings/Dashboard', {
                credentials: 'include'
            });
            
            if (response.ok) {
                const settings = await response.json();
                // Default to true if setting doesn't exist (backward compatibility)
                return settings.enable_my_dashboard !== false && settings.enable_my_dashboard !== 'false';
            }
        } catch (error) {
            console.warn('Could not load dashboard visibility setting, defaulting to show:', error);
        }
        // Default to showing the sub-nav if setting can't be loaded
        return true;
    }

    /**
     * Initialize sub-navigation tabs
     */
    async initSubNavigation() {
        const subNav = document.getElementById('subNav');
        if (!subNav) return;

        if (!this.isAuthenticated) {
            subNav.style.display = 'none';
            document.body.classList.remove('has-sub-nav');
            return;
        }

        // Check if sub-navigation should be shown based on system setting
        const shouldShow = await this.shouldShowSubNavigation();
        
        if (!shouldShow) {
            // Hide sub-navigation
            subNav.style.display = 'none';
            document.body.classList.remove('has-sub-nav');
            return;
        }

        // Show sub-navigation for authenticated users
        subNav.style.display = 'block';
        
        // Add class to body for CSS styling
        document.body.classList.add('has-sub-nav');

        const homeTab = document.getElementById('homeTab');
        const dashboardTab = document.getElementById('dashboardTab');

        if (homeTab) {
            homeTab.addEventListener('click', (e) => {
                e.preventDefault();
                this.switchTab('home');
            });
        }

        if (dashboardTab) {
            dashboardTab.addEventListener('click', (e) => {
                e.preventDefault();
                this.switchTab('dashboard');
            });
        }
        
        // Initialize actions menu in sub-nav
        this.initSubNavActionsMenu();
    }

    /**
     * Switch between Home and Dashboard tabs
     */
    async switchTab(tab) {
        // Update active tab styling
        const homeTab = document.getElementById('homeTab');
        const dashboardTab = document.getElementById('dashboardTab');
        
        if (homeTab && dashboardTab) {
            if (tab === 'home') {
                homeTab.classList.add('active');
                dashboardTab.classList.remove('active');
                this.showHeroSection();
                // Update actions menu for home tab
                this.updateActionsMenu('home');
                // Update URL without reload
                window.history.pushState({}, '', '/index.html?tab=home');
            } else {
                // Check if main dashboard is hidden
                let isMainHidden = false;
                try {
                    const saved = localStorage.getItem('dashboardHiddenDashboards');
                    if (saved !== null && saved !== '') {
                        const hiddenDashboards = JSON.parse(saved);
                        isMainHidden = hiddenDashboards.includes('main');
                    }
                } catch (e) {
                    console.error('Error parsing hiddenDashboards:', e);
                }
                
                if (isMainHidden) {
                    // Main dashboard is hidden, stay on home or switch to first visible custom dashboard
                    const visibleCustomTab = document.querySelector('.sub-nav-tabs [data-dashboard-id]:not([data-dashboard-id="main"]):not([data-dashboard-id="null"])');
                    if (visibleCustomTab && visibleCustomTab.style.display !== 'none') {
                        const dashboardId = parseInt(visibleCustomTab.getAttribute('data-dashboard-id'));
                        await this.switchToDashboard(dashboardId);
                    } else {
                        // No visible dashboards, stay on home
                        homeTab.classList.add('active');
                        dashboardTab.classList.remove('active');
                        this.showHeroSection();
                        this.updateActionsMenu('home');
                        window.history.pushState({}, '', '/index.html?tab=home');
                    }
                } else {
                    // Main dashboard is visible, switch to it
                dashboardTab.classList.add('active');
                homeTab.classList.remove('active');
                // Reset to default dashboard and switch to it
                this.currentDashboardId = null;
                // Remove active from all custom dashboard tabs
                    const customTabs = document.querySelectorAll('.sub-nav-tabs [data-dashboard-id]');
                customTabs.forEach(t => t.classList.remove('active'));
                // Switch to main dashboard using switchToDashboard
                await this.switchToDashboard(null);
                // Update URL without reload
                window.history.pushState({}, '', '/index.html?tab=dashboard');
                }
            }
        }
    }

    /**
     * Check if user is authenticated
     */
    async checkAuthentication() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                this.currentUser = null;
                this.isAuthenticated = false;
                return;
            }

            const me = await response.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isGuest = role.includes('guest');
            const isAuth = me.authenticated === true && !isGuest;

            this.currentUser = isAuth ? me : null;
            this.isAuthenticated = isAuth;
        } catch (error) {
            console.error('Authentication check failed:', error);
            this.isAuthenticated = false;
        }
    }

    /**
     * Show hero section (Home tab)
     */
    showHeroSection() {
        const heroSection = document.querySelector('.hero-section');
        const dashboardSection = document.getElementById('dashboardSection');
        
        if (heroSection) {
            heroSection.style.display = 'block';
        }
        if (dashboardSection) {
            dashboardSection.style.display = 'none';
        }
        
        // Update actions menu for home tab
        this.updateActionsMenu('home');
    }

    /**
     * Show dashboard (My Dashboard tab)
     */
    async showDashboard() {
        const heroSection = document.querySelector('.hero-section');
        const dashboardSection = document.getElementById('dashboardSection');
        
        if (heroSection) {
            heroSection.style.display = 'none';
        }
        if (dashboardSection) {
            dashboardSection.style.display = 'block';
        }
        
        // Update actions menu for dashboard tab
        this.updateActionsMenu('dashboard');
        
        // Load and display dashboard info from database
        await this.loadDashboardInfo();
        
        // Apply saved description visibility preference
        this.applyDescriptionVisibilityPreference();
    }

    /**
     * Load and display dashboard info from database
     */
    async loadDashboardInfo() {
        try {
            const response = await fetch('/api/dashboard/info', {
                credentials: 'include'
            });

            if (response.ok) {
                const dashboardInfo = await response.json();
                // Store dashboard owner ID
                this.dashboardOwnerId = dashboardInfo.createdBy || null;
                await this.updateDashboardDisplay(
                    dashboardInfo.title || 'My Dashboard',
                    dashboardInfo.description || 'Welcome back! Here\'s an overview of your activities and responsibilities.',
                    dashboardInfo.creatorName || null,
                    dashboardInfo.sharedWith || [],
                    dashboardInfo.isPublic || false,
                    dashboardInfo.createdBy || null
                );
            } else {
                // If no dashboard found, use default values
                this.dashboardOwnerId = null;
                await this.updateDashboardDisplay(
                    'My Dashboard',
                    'Welcome back! Here\'s an overview of your activities and responsibilities.',
                    null,
                    [],
                    false,
                    null
                );
            }
        } catch (error) {
            console.error('Error loading dashboard info:', error);
            // Use default values on error
            this.dashboardOwnerId = null;
            await this.updateDashboardDisplay(
                'My Dashboard',
                'Welcome back! Here\'s an overview of your activities and responsibilities.',
                null,
                [],
                false,
                null
            );
        }
    }

    /**
     * Apply saved description visibility preference
     */
    applyDescriptionVisibilityPreference() {
        const savedPreference = localStorage.getItem('dashboardDescriptionHidden');
        const dashboardHeader = document.querySelector('.dashboard-header');
        if (!dashboardHeader) return;
        
        const descriptionElement = dashboardHeader.querySelector('.dashboard-description');
        if (!descriptionElement) return;
        
        if (savedPreference === 'true') {
            // Hide description
            descriptionElement.style.display = 'none';
            descriptionElement.classList.add('hidden');
            this.updateDescriptionToggleButton(true);
        } else {
            // Show description (default)
            descriptionElement.style.display = '';
            descriptionElement.classList.remove('hidden');
            this.updateDescriptionToggleButton(false);
        }
    }

    /**
     * Initialize sub-navigation actions menu
     */
    initSubNavActionsMenu() {
        const actionsButton = document.getElementById('subNavActionsButton');
        const actionsDropdown = document.getElementById('subNavActionsDropdown');
        
        if (!actionsButton || !actionsDropdown) return;
        
        // Toggle dropdown
        actionsButton.addEventListener('click', (e) => {
            e.stopPropagation();
            const isOpen = actionsDropdown.classList.contains('show');
            
            // Close all other dropdowns
            document.querySelectorAll('.sub-nav-actions-dropdown').forEach(menu => {
                menu.classList.remove('show');
            });
            document.querySelectorAll('.sub-nav-actions-button').forEach(btn => {
                btn.classList.remove('active');
            });
            
            if (!isOpen) {
                actionsDropdown.classList.add('show');
                actionsButton.classList.add('active');
            }
        });
        
        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!actionsButton.contains(e.target) && !actionsDropdown.contains(e.target)) {
                actionsDropdown.classList.remove('show');
                actionsButton.classList.remove('active');
            }
        });
        
        // Close submenus when clicking outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.dashboard-actions-item.has-submenu') && 
                !e.target.closest('.dashboard-actions-submenu')) {
                document.querySelectorAll('.dashboard-actions-submenu').forEach(sm => {
                    sm.style.display = 'none';
                    sm.style.visibility = 'hidden';
                    sm.style.opacity = '0';
                    sm.style.maxHeight = '0';
                    sm.classList.remove('show');
                });
                document.querySelectorAll('.dashboard-actions-item.has-submenu').forEach(i => {
                    i.classList.remove('expanded');
                });
            }
        });
        
        // Also close submenu when main dropdown closes
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
                    const dropdown = mutation.target;
                    if (!dropdown.classList.contains('show')) {
                        // Main dropdown closed, close all submenus
                        document.querySelectorAll('.dashboard-actions-submenu').forEach(sm => {
                            sm.style.display = 'none';
                            sm.style.visibility = 'hidden';
                            sm.style.opacity = '0';
                            sm.style.maxHeight = '0';
                            sm.classList.remove('show');
                        });
                        document.querySelectorAll('.dashboard-actions-item.has-submenu').forEach(i => {
                            i.classList.remove('expanded');
                        });
                    }
                }
            });
        });
        
        if (actionsDropdown) {
            observer.observe(actionsDropdown, { attributes: true, attributeFilter: ['class'] });
        }
        
        // Add document-level event listener for export submenu items (since submenu is in body)
        document.addEventListener('click', (e) => {
            const submenuItem = e.target.closest('.dashboard-actions-submenu-item');
            if (submenuItem) {
                const exportFormat = submenuItem.getAttribute('data-export');
                if (exportFormat) {
                    console.log('[Dashboard] Export submenu item clicked (document listener):', exportFormat);
                    e.stopPropagation();
                    e.preventDefault();
                    this.exportDashboard(exportFormat);
                    
                    // Close submenu and main dropdown
                    const submenu = document.getElementById('exportSubmenu');
                    if (submenu) {
                        submenu.style.display = 'none';
                        submenu.style.visibility = 'hidden';
                        submenu.style.opacity = '0';
                        submenu.style.maxHeight = '0';
                        submenu.classList.remove('show');
                    }
                    
                    if (actionsDropdown) {
                        actionsDropdown.classList.remove('show');
                    }
                    if (actionsButton) {
                        actionsButton.classList.remove('active');
                    }
                    
                    // Remove expanded class
                    document.querySelectorAll('.dashboard-actions-item.has-submenu').forEach(i => {
                        i.classList.remove('expanded');
                    });
                }
            }
        });
        
        // Handle menu item clicks
        actionsDropdown.addEventListener('click', (e) => {
            console.log('[Dashboard] Click event in actions dropdown:', e.target);
            
            // Check for submenu item clicks FIRST (before checking parent items)
            const submenuItem = e.target.closest('.dashboard-actions-submenu-item');
            if (submenuItem) {
                const exportFormat = submenuItem.getAttribute('data-export');
                console.log('[Dashboard] Export submenu item clicked:', exportFormat, submenuItem);
                if (exportFormat) {
                    console.log('[Dashboard] Calling exportDashboard with format:', exportFormat);
                    this.exportDashboard(exportFormat);
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                    // Hide all submenus and remove expanded class
                    document.querySelectorAll('.dashboard-actions-submenu').forEach(sm => {
                        sm.style.display = 'none';
                        sm.style.visibility = 'hidden';
                        sm.style.opacity = '0';
                        sm.style.maxHeight = '0';
                        sm.classList.remove('show');
                    });
                    document.querySelectorAll('.dashboard-actions-item.has-submenu').forEach(i => {
                        i.classList.remove('expanded');
                    });
                }
                e.stopPropagation();
                return;
            }
            
            const item = e.target.closest('.dashboard-actions-item');
            if (!item) {
                console.log('[Dashboard] Click was not on a dashboard action item');
                return;
            }
            
            e.stopPropagation();
            const actionText = item.querySelector('span')?.textContent;
            console.log('[Dashboard] Action item clicked:', actionText);
            
            // Handle submenu items
            if (item.classList.contains('has-submenu')) {
                const submenuText = actionText.trim();
                const submenuId = item.getAttribute('data-submenu');
                
                // Handle "Export Dashboard" submenu - show submenu on click
                if (submenuId === 'export') {
                    console.log('[Dashboard] Export Dashboard clicked, showing submenu');
                    
                    // Get or create the export submenu (append to body for proper positioning)
                    let submenu = document.getElementById('exportSubmenu');
                    if (!submenu) {
                        // Create submenu if it doesn't exist
                        submenu = document.createElement('div');
                        submenu.id = 'exportSubmenu';
                        submenu.className = 'dashboard-actions-submenu';
                        submenu.innerHTML = `
                            <button class="dashboard-actions-submenu-item" data-export="pdf">
                                <span>PDF</span>
                            </button>
                            <button class="dashboard-actions-submenu-item" data-export="png">
                                <span>PNG</span>
                            </button>
                            <button class="dashboard-actions-submenu-item" data-export="jpeg">
                                <span>JPEG</span>
                            </button>
                        `;
                        document.body.appendChild(submenu);
                        
                        // Add event listeners to submenu items
                        submenu.addEventListener('click', (e) => {
                            const submenuItem = e.target.closest('.dashboard-actions-submenu-item');
                            if (submenuItem) {
                                const exportFormat = submenuItem.getAttribute('data-export');
                                console.log('[Dashboard] Export submenu item clicked:', exportFormat);
                                if (exportFormat) {
                                    console.log('[Dashboard] Calling exportDashboard with format:', exportFormat);
                                    this.exportDashboard(exportFormat);
                                    
                                    // Close submenu and main dropdown
                                    submenu.style.display = 'none';
                                    submenu.style.visibility = 'hidden';
                                    submenu.style.opacity = '0';
                                    submenu.style.maxHeight = '0';
                                    submenu.classList.remove('show');
                                    
                                    actionsDropdown.classList.remove('show');
                                    actionsButton.classList.remove('active');
                                    
                                    // Remove expanded class
                                    document.querySelectorAll('.dashboard-actions-item.has-submenu').forEach(i => {
                                        i.classList.remove('expanded');
                                    });
                                    
                                    e.stopPropagation();
                                }
                            }
                        });
                    }
                    
                    // Toggle submenu visibility
                    const isVisible = submenu.style.display === 'block' && submenu.style.visibility === 'visible';
                    console.log('[Dashboard] Submenu currently visible:', isVisible);
                    
                    // Remove expanded class from all items
                    document.querySelectorAll('.dashboard-actions-item.has-submenu').forEach(i => {
                        i.classList.remove('expanded');
                    });
                    
                    // Hide all other submenus first
                    document.querySelectorAll('.dashboard-actions-submenu').forEach(sm => {
                        sm.style.display = 'none';
                        sm.style.visibility = 'hidden';
                        sm.style.opacity = '0';
                        sm.style.maxHeight = '0';
                        sm.classList.remove('show');
                    });
                    
                    // Toggle this submenu
                    if (!isVisible) {
                        const rect = item.getBoundingClientRect();
                        
                        // Position submenu using fixed positioning relative to viewport
                        submenu.style.position = 'fixed';
                        submenu.style.left = `${rect.right + 4}px`;
                        submenu.style.top = `${rect.top}px`;
                        submenu.style.zIndex = '1003';
                        submenu.style.display = 'block';
                        submenu.style.visibility = 'visible';
                        submenu.style.opacity = '1';
                        submenu.style.maxHeight = '500px';
                        submenu.classList.add('show');
                        item.classList.add('expanded');
                        
                        // Check if submenu would go off screen to the right
                        const submenuWidth = submenu.offsetWidth || 140;
                        if (rect.right + submenuWidth + 4 > window.innerWidth) {
                            // Position to the left instead
                            submenu.style.left = `${rect.left - submenuWidth - 4}px`;
                        }
                        
                        console.log('[Dashboard] Submenu expanded and positioned at:', submenu.style.left, submenu.style.top);
                    } else {
                        submenu.style.display = 'none';
                        submenu.style.visibility = 'hidden';
                        submenu.style.opacity = '0';
                        submenu.style.maxHeight = '0';
                        submenu.classList.remove('show');
                        item.classList.remove('expanded');
                        console.log('[Dashboard] Submenu collapsed');
                    }
                    
                    e.stopPropagation();
                    return;
                }
                // Handle "Choose Widgets" submenu
                else if (submenuText === 'Choose Widgets') {
                    if (window.ChooseWidgetsModal) {
                        window.ChooseWidgetsModal.open();
                    }
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
                // Handle "Choose Dashboards" submenu
                else if (submenuText === 'Choose Dashboards') {
                    this.openChooseDashboardModal();
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
            } else {
                console.log('Action clicked:', actionText);
                
                // Handle "New Widget" action
                if (actionText === 'New Widget') {
                    // Check if user is the owner of the dashboard
                    const isMainDashboard = this.currentDashboardId === null;
                    const currentUserId = this.currentUser ? (this.currentUser.id || this.currentUser.ID) : null;
                    const isOwner = isMainDashboard || (currentUserId && this.dashboardOwnerId && 
                        parseInt(currentUserId) === parseInt(this.dashboardOwnerId));
                    
                    if (!isOwner) {
                        alert('You do not have permission to add widgets to this shared dashboard.');
                        actionsDropdown.classList.remove('show');
                        actionsButton.classList.remove('active');
                        return;
                    }
                    
                    if (window.NewWidgetModal) {
                        window.NewWidgetModal.open(this.currentDashboardId);
                    }
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
                // Handle "New Dashboard" action
                else if (actionText === 'New Dashboard') {
                    this.openNewDashboardModal();
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
                // Handle "Clone Dashboard" action
                else if (actionText === 'Clone Dashboard') {
                    this.handleCloneDashboard();
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
                // Handle "Edit Dashboard" action
                else if (actionText === 'Edit Dashboard') {
                    this.openEditDashboardModal();
                    // Don't close dropdown for edit action
                }
                // Handle "Delete Dashboard" action
                else if (actionText === 'Delete Dashboard') {
                    this.handleDeleteDashboard();
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
                // Handle "Choose Dashboards" action
                else if (actionText === 'Choose Dashboards') {
                    this.openChooseDashboardModal();
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
                // Handle "Modify Sharing" action
                else if (actionText === 'Modify Sharing') {
                    this.handleModifySharing();
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
                // Handle "Set as Default" action
                else if (actionText === 'Set as Default') {
                    this.openSetDefaultConfirmModal();
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
                // Handle "Hide Description" / "Show Description" action (identify by data-action so it works in any language)
                else if (item.getAttribute('data-action') === 'toggleDescription') {
                    this.toggleDescriptionVisibility();
                    // Don't close dropdown for toggle actions
                    e.stopPropagation();
                    return; // Exit early to prevent dropdown from closing
                }
                // TODO: Implement other action handlers
                else {
                    // Close dropdown after other actions
                    actionsDropdown.classList.remove('show');
                    actionsButton.classList.remove('active');
                }
            }
        });
        
        // Initialize with home tab menu
        this.updateActionsMenu('home');
    }

    /**
     * Check if current user is super admin
     */
    isSuperAdmin() {
        if (!this.currentUser) return false;
        const role = (this.currentUser.role || this.currentUser.Role || this.currentUser.userRole || '').toString().toLowerCase().trim();
        const normalizedRole = role.replace(/[_\s-]/g, ' ').replace(/\s+/g, ' ');
        return normalizedRole === 'super admin' || 
               normalizedRole === 'superadmin' ||
               normalizedRole === 'suber admin'; // Handle typo in DB
    }

    /**
     * Update actions menu based on active tab
     */
    updateActionsMenu(activeTab) {
        const actionsDropdown = document.getElementById('subNavActionsDropdown');
        if (!actionsDropdown) return;
        
        // Check if user is guest
        const role = this.currentUser ? (this.currentUser.role || '').toString().toLowerCase() : 'guest';
        const isGuest = role.includes('guest');
        
        // All users can create new dashboards (except guests)
        const newDashboardButton = '<button class="dashboard-actions-item"><span>New Dashboard</span></button>';
        
        if (activeTab === 'home') {
            // Home tab menu options
            if (isGuest) {
                // Guests only see MANAGE > Choose Dashboards
                actionsDropdown.innerHTML = `
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">MANAGE</div>
                        <button class="dashboard-actions-item has-submenu">
                            <span>Choose Dashboards</span>
                            <i class="fas fa-chevron-right"></i>
                        </button>
                    </div>
                `;
            } else {
                // Full menu for authenticated users
                actionsDropdown.innerHTML = `
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">CREATE</div>
                        ${newDashboardButton}
                    </div>
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">MANAGE</div>
                        <button class="dashboard-actions-item has-submenu">
                            <span>Choose Dashboards</span>
                            <i class="fas fa-chevron-right"></i>
                        </button>
                    </div>
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">OTHER ACTIONS</div>
                        <button class="dashboard-actions-item">
                            <span>Set as Default</span>
                        </button>
                    </div>
                `;
            }
        } else if (activeTab === 'dashboard') {
            // Dashboard tab menu options
            if (isGuest) {
                // Guests only see MANAGE > Choose Dashboards
                actionsDropdown.innerHTML = `
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">MANAGE</div>
                        <button class="dashboard-actions-item has-submenu">
                            <span>Choose Dashboards</span>
                            <i class="fas fa-chevron-right"></i>
                        </button>
                    </div>
                `;
            } else {
                // Full menu for authenticated users
                const isMainDashboard = this.currentDashboardId === null;
                const deleteDisabledClass = isMainDashboard ? 'disabled' : '';
                const deleteDisabledAttr = isMainDashboard ? 'disabled' : '';
                const cloneDisabledClass = isMainDashboard ? 'disabled' : '';
                const cloneDisabledAttr = isMainDashboard ? 'disabled' : '';
                const sharingDisabledClass = isMainDashboard ? 'disabled' : '';
                const sharingDisabledAttr = isMainDashboard ? 'disabled' : '';
                
                // Check if current user is the owner of the dashboard
                const currentUserId = this.currentUser ? (this.currentUser.id || this.currentUser.ID) : null;
                const isOwner = isMainDashboard || (currentUserId && this.dashboardOwnerId && 
                    parseInt(currentUserId) === parseInt(this.dashboardOwnerId));
                const newWidgetDisabledClass = isOwner ? '' : 'disabled';
                const newWidgetDisabledAttr = isOwner ? '' : 'disabled';
                
                actionsDropdown.innerHTML = `
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">CREATE</div>
                        <button class="dashboard-actions-item ${newWidgetDisabledClass}" ${newWidgetDisabledAttr}>
                            <span>New Widget</span>
                        </button>
                        ${newDashboardButton}
                        <button class="dashboard-actions-item ${cloneDisabledClass}" ${cloneDisabledAttr}>
                            <span>Clone Dashboard</span>
                        </button>
                    </div>
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">EDIT</div>
                        <button class="dashboard-actions-item">
                            <span>Edit Dashboard</span>
                        </button>
                        <button class="dashboard-actions-item ${deleteDisabledClass}" ${deleteDisabledAttr}>
                            <span>Delete Dashboard</span>
                        </button>
                    </div>
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">MANAGE</div>
                        <button class="dashboard-actions-item has-submenu">
                            <span>Choose Widgets</span>
                            <i class="fas fa-chevron-right"></i>
                        </button>
                        <button class="dashboard-actions-item has-submenu">
                            <span>Choose Dashboards</span>
                            <i class="fas fa-chevron-right"></i>
                        </button>
                    </div>
                    <div class="dashboard-actions-section">
                        <div class="dashboard-actions-section-title">OTHER ACTIONS</div>
                        <button class="dashboard-actions-item ${sharingDisabledClass}" ${sharingDisabledAttr}>
                            <span>Modify Sharing</span>
                        </button>
                        <button class="dashboard-actions-item has-submenu" data-submenu="export" id="exportDashboardBtn">
                            <span>Export Dashboard</span>
                            <i class="fas fa-chevron-right"></i>
                        </button>
                        <button class="dashboard-actions-item">
                            <span>Set as Default</span>
                        </button>
                        <button class="dashboard-actions-item" data-action="toggleDescription">
                            <span>Hide Description</span>
                        </button>
                    </div>
                `;
            }
            
            // Ensure all submenus are hidden by default after menu is created
            setTimeout(() => {
                // Hide submenus in dropdown
                const allSubmenus = actionsDropdown.querySelectorAll('.dashboard-actions-submenu');
                allSubmenus.forEach(submenu => {
                    submenu.style.display = 'none';
                    submenu.style.visibility = 'hidden';
                    submenu.style.opacity = '0';
                    submenu.style.maxHeight = '0';
                    submenu.classList.remove('show');
                });
                // Hide export submenu if it exists in body
                const exportSubmenu = document.getElementById('exportSubmenu');
                if (exportSubmenu) {
                    exportSubmenu.style.display = 'none';
                    exportSubmenu.style.visibility = 'hidden';
                    exportSubmenu.style.opacity = '0';
                    exportSubmenu.style.maxHeight = '0';
                    exportSubmenu.classList.remove('show');
                }
                // Remove any expanded classes
                const allSubmenuItems = actionsDropdown.querySelectorAll('.dashboard-actions-item.has-submenu');
                allSubmenuItems.forEach(item => {
                    item.classList.remove('expanded');
                });
                // Set translated label for Hide/Show Description button and sync with current state
                const descHidden = localStorage.getItem('dashboardDescriptionHidden') === 'true';
                this.updateDescriptionToggleButton(descHidden);
            }, 0);
        }
    }

    /**
     * Initialize all dashboard widgets
     * Only runs for main dashboard (currentDashboardId === null)
     * @param {boolean} forceReinit - Force re-initialization even if widgets already exist
     */
    async initializeWidgets(forceReinit = false) {
        // Only initialize default widgets for main dashboard
        if (this.currentDashboardId !== null) {
            return;
        }

        if (window.DashboardDragDrop) {
            if (!this.dragDrop) {
                this.dragDrop = new DashboardDragDrop();
                if (window.dashboard) {
                    window.dashboard.dragDrop = this.dragDrop;
                }
            }
            await this.dragDrop.loadPrefsFromServer();
        }

        const userId = this.currentUser ? this.currentUser.id : null;
        
        // If not forcing reinit and widgets already exist, just refresh custom widgets
        if (!forceReinit && this.widgets && Object.keys(this.widgets).length > 0) {
            // Widgets already initialized, just ensure they're visible and refresh custom widgets
            const defaultWidgetIds = ['objectCounts', 'rolesNotAccepted', 'stakeholdership', 'savedSearches', 'changeRequests', 'team', 'pendingTasks'];
            defaultWidgetIds.forEach(widgetId => {
                const container = document.getElementById(`${widgetId}Widget`);
                if (container) {
                    container.style.removeProperty('display');
                }
            });
            
            // Re-initialize default widgets to refresh their data
            const widgetPromises = [
                this.initWidget('objectCounts', ObjectCountsWidget, 'objectCountsWidget'),
                this.initWidget('rolesNotAccepted', RolesNotAcceptedWidget, 'rolesNotAcceptedWidget', userId),
                this.initWidget('stakeholdership', StakeholdershipWidget, 'stakeholdershipWidget', userId),
                this.initWidget('savedSearches', SavedSearchesWidget, 'savedSearchesWidget'),
                this.initWidget('changeRequests', ChangeRequestsWidget, 'changeRequestsWidget', userId),
                this.initWidget('team', TeamWidget, 'teamWidget', userId),
                this.initWidget('pendingTasks', PendingTasksWidget, 'pendingTasksWidget', userId)
            ];
            
            await Promise.all(widgetPromises);
            
            // Load custom widgets for main dashboard
            await this.loadDashboardWidgets(null);
            
            // Apply hidden widgets
            if (this.dragDrop) {
                this.dragDrop.applyHiddenWidgets();
            }
            
            return;
        }

        // Show widgets that should be visible BEFORE initializing (prevents flash)
        if (this.dragDrop) {
            const allWidgetIds = ['objectCounts', 'rolesNotAccepted', 'stakeholdership', 'savedSearches', 'changeRequests', 'team', 'pendingTasks'];
            allWidgetIds.forEach(widgetId => {
                const container = document.getElementById(`${widgetId}Widget`);
                if (container) {
                    // Show if not in hidden list
                    if (!this.dragDrop.hiddenWidgets.includes(widgetId)) {
                        container.style.removeProperty('display');
                    } else {
                        container.style.setProperty('display', 'none', 'important');
                    }
                }
            });
        }

        // Initialize default widgets in parallel
        const widgetPromises = [
            this.initWidget('objectCounts', ObjectCountsWidget, 'objectCountsWidget'),
            this.initWidget('rolesNotAccepted', RolesNotAcceptedWidget, 'rolesNotAcceptedWidget', userId),
            this.initWidget('stakeholdership', StakeholdershipWidget, 'stakeholdershipWidget', userId),
            this.initWidget('savedSearches', SavedSearchesWidget, 'savedSearchesWidget'),
            this.initWidget('changeRequests', ChangeRequestsWidget, 'changeRequestsWidget', userId),
            this.initWidget('team', TeamWidget, 'teamWidget', userId),
            this.initWidget('pendingTasks', PendingTasksWidget, 'pendingTasksWidget', userId)
        ];

        try {
            await Promise.all(widgetPromises);
            
            // Apply hidden widgets again after default widgets are initialized
            if (this.dragDrop) {
                this.dragDrop.applyHiddenWidgets();
            }
            
            // Load and render custom widgets from database for main dashboard
            await this.loadDashboardWidgets(null);
            
            // Apply hidden widgets again after custom widgets are loaded
            if (this.dragDrop) {
                this.dragDrop.applyHiddenWidgets();
            }
            
            // Setup widget controls after ALL widgets are rendered (including custom ones)
            // Use setTimeout to ensure DOM is fully updated
            setTimeout(() => {
                this.setupWidgetControls();
            }, 100);
            
            // Setup drag and drop after widgets are loaded
            // Use setTimeout to ensure DOM is fully updated
            setTimeout(() => {
                if (this.dragDrop) {
                    // Apply hidden widgets one more time to be safe
                    this.dragDrop.applyHiddenWidgets();
                    // Setup drag and drop
                    this.dragDrop.setupDragAndDrop();
                    // Restore order
                    this.dragDrop.restoreWidgetOrder();
                }
            }, 200);
        } catch (error) {
            console.error('Error initializing widgets:', error);
        }
    }
    
    /**
     * Load user dashboards from database and create tabs for them
     */
    async loadUserDashboards() {
        try {
            const response = await fetch('/api/dashboard/list', {
                credentials: 'include'
            });
            
            if (!response.ok) {
                console.error('Failed to load user dashboards');
                return;
            }
            
            const dashboards = await response.json();
            
            // Get the main dashboard ID (the one with isDefault = 1 that's used for the main dashboardTab)
            // This dashboard should NOT be created as a custom tab since it's already represented by dashboardTab
            const mainDashboardInfo = await this.getMainDashboardInfo();
            const mainDashboardId = mainDashboardInfo.id;
            
            // Only show dashboards where isDefault = true in tabs after reload
            // isDefault controls visibility: 1 = show in tab, 0 = don't show in tab
            // Exclude the main dashboard since it's already represented by the dashboardTab element
            const visibleDashboards = dashboards.filter(d => 
                d.isDefault === true && d.id !== mainDashboardId
            );
            
            // Create tabs for each visible dashboard
            for (const dashboard of visibleDashboards) {
                // Check if tab already exists to avoid duplicates
                const existingTab = document.querySelector(`.sub-nav-tabs [data-dashboard-id="${dashboard.id}"]`);
                if (!existingTab) {
                    await this.createDashboardTab(dashboard.id, dashboard.title, false); // false = don't switch to it
                }
            }
            
            // Apply visibility state after all tabs are created
            // This is done in init() after loadUserDashboards() completes
        } catch (error) {
            console.error('Error loading user dashboards:', error);
        }
    }
    
    /**
     * Render a custom widget
     */
    async renderCustomWidget(widgetData) {
        console.log(`[Widget] renderCustomWidget called with widgetData:`, widgetData);
        
        const dashboardGrid = document.querySelector('.dashboard-grid');
        if (!dashboardGrid) {
            console.error('[Widget] Dashboard grid not found!');
            return;
        }
        
        // Check if widget already exists to avoid duplicates
        const widgetId = `widget${widgetData.id}`;
        const containerId = `${widgetId}Widget`;
        const existingContainer = document.getElementById(containerId);
        
        if (existingContainer) {
            console.log(`[Widget] Widget already exists, skipping: ${containerId}`);
            return;
        }
        
        console.log(`[Widget] Creating new widget container: ${containerId}`);
        
        // Create container for the widget with consistent naming: widget{id}Widget
        const container = document.createElement('div');
        container.id = containerId;
        container.className = 'dashboard-widget-container';
        dashboardGrid.appendChild(container);
        
        console.log(`[Widget] Container created and added to grid. Widget type: ${widgetData.type}`);
        
        // Initialize widget based on type
        if (widgetData.type === 'text') {
            console.log(`[Widget] Initializing TextWidget for ${containerId}`);
            const widget = new TextWidget(containerId, widgetData);
            await widget.init();
            this.widgets[widgetId] = widget;
            console.log(`[Widget] TextWidget initialized and stored in widgets[${widgetId}]`);
        } else if (widgetData.type === 'saved_search') {
            console.log(`[Widget] Initializing SavedSearchWidget for ${containerId}`);
            // Handle saved search widgets
            const widget = new SavedSearchWidget(containerId, widgetData);
            await widget.init();
            this.widgets[widgetId] = widget;
            console.log(`[Widget] SavedSearchWidget initialized and stored in widgets[${widgetId}]`);
        } else {
            console.warn(`[Widget] Unknown widget type: ${widgetData.type}`);
        }
        
        console.log(`[Widget] Total widgets in this.widgets:`, Object.keys(this.widgets).length);
        
        // Immediately setup controls for this specific widget
        const widgetElement = container.querySelector('.dashboard-widget');
        if (widgetElement) {
            console.log(`[Widget] Setting up controls for newly rendered widget: ${containerId}`);
            this.addDragHandle(widgetElement);
            this.addWidgetMenu(widgetElement);
        }
    }

    /**
     * Setup widget controls (hide menu, drag handle)
     */
    setupWidgetControls() {
        const widgets = document.querySelectorAll('.dashboard-widget');
        console.log(`[Dashboard] Setting up controls for ${widgets.length} widgets`);
        
        widgets.forEach(widget => {
            const container = widget.closest('[id$="Widget"]');
            console.log(`[Dashboard] Setting up controls for widget: ${container?.id}`);
            
            // Add drag handle
            this.addDragHandle(widget);
            
            // Add dropdown menu
            this.addWidgetMenu(widget);
            
            // Widget ID is already set via data-widget-id attribute
            // Draggable is set on container in drag-drop.js
        });
        
        console.log(`[Dashboard] Widget controls setup complete`);
    }

    /**
     * Get widget ID from element
     */
    getWidgetIdFromElement(widget) {
        const container = widget.closest('[id$="Widget"]');
        if (container) {
            // Remove the "Widget" suffix to get the widget ID
            // For custom widgets like "widget123Widget", this gives us "widget123"
            // For default widgets like "objectCountsWidget", this gives us "objectCounts"
            return container.id.replace(/Widget$/, '');
        }
        return null;
    }

    /**
     * Add drag handle to widget
     */
    addDragHandle(widget) {
        const header = widget.querySelector('.dashboard-widget-header');
        if (!header) {
            console.log(`[Dashboard] No header found for widget, skipping drag handle`);
            return;
        }

        // Check if drag handle already exists
        if (header.querySelector('.widget-drag-handle')) {
            console.log(`[Dashboard] Drag handle already exists for widget`);
            return;
        }

        const container = widget.closest('[id$="Widget"]');
        console.log(`[Dashboard] Adding drag handle to widget: ${container?.id}`);

        const dragHandle = document.createElement('div');
        dragHandle.className = 'widget-drag-handle';
        dragHandle.innerHTML = '<i class="fas fa-grip-vertical"></i>';
        dragHandle.title = 'Drag to reorder';
        header.appendChild(dragHandle);
        console.log(`[Dashboard] Drag handle added successfully`);
    }

    /**
     * Add dropdown menu to widget
     */
    addWidgetMenu(widget) {
        const header = widget.querySelector('.dashboard-widget-header');
        if (!header) return;

        // Check if menu already exists
        if (header.querySelector('.widget-menu-container')) return;

        // Check if this is a custom widget
        const container = widget.closest('[id$="Widget"]');
        const isCustomWidget = container && container.id.startsWith('widget') && /^\d+/.test(container.id.replace('widget', '').replace('Widget', ''));
        
        console.log(`[Widget] Adding menu to widget. Container ID: ${container?.id}, isCustomWidget: ${isCustomWidget}`);
        
        const menuContainer = document.createElement('div');
        menuContainer.className = 'widget-menu-container';
        
        const menuButton = document.createElement('button');
        menuButton.className = 'widget-menu-button';
        menuButton.innerHTML = '<i class="fas fa-ellipsis-v"></i>';
        menuButton.setAttribute('aria-label', 'Widget options');
        
        const dropdown = document.createElement('div');
        dropdown.className = 'widget-menu-dropdown';
        
        // Add menu items based on widget type
        let menuItems = `
            <button class="widget-menu-item" data-action="hide">
                <i class="fas fa-eye-slash"></i> Hide
            </button>
        `;
        
        if (isCustomWidget) {
            menuItems += `
                <button class="widget-menu-item" data-action="edit">
                    <i class="fas fa-edit"></i> Edit
                </button>
                <button class="widget-menu-item" data-action="delete">
                    <i class="fas fa-trash"></i> Delete
                </button>
            `;
        }
        
        dropdown.innerHTML = menuItems;

        menuContainer.appendChild(menuButton);
        menuContainer.appendChild(dropdown);
        header.appendChild(menuContainer);

        // Toggle dropdown
        menuButton.addEventListener('click', (e) => {
            e.stopPropagation();
            const isOpen = dropdown.classList.contains('show');
            
            // Close all other dropdowns
            document.querySelectorAll('.widget-menu-dropdown').forEach(menu => {
                menu.classList.remove('show');
            });
            
            if (!isOpen) {
                dropdown.classList.add('show');
            }
        });

        // Handle menu item clicks
        dropdown.querySelectorAll('.widget-menu-item').forEach(item => {
            item.addEventListener('click', (e) => {
                e.stopPropagation();
                const action = item.dataset.action;
                const widgetId = this.getWidgetIdFromElement(widget);
                
                if (action === 'hide') {
                    if (widgetId && this.dragDrop) {
                        this.dragDrop.hideWidget(widgetId);
                        dropdown.classList.remove('show');
                    }
                } else if (action === 'edit') {
                    // Extract database ID from widgetId (e.g., "widget123" -> 123)
                    const dbId = widgetId.replace('widget', '');
                    this.handleEditCustomWidget(dbId);
                    dropdown.classList.remove('show');
                } else if (action === 'delete') {
                    // Extract database ID from widgetId
                    const dbId = widgetId.replace('widget', '');
                    this.handleDeleteCustomWidget(dbId, container);
                    dropdown.classList.remove('show');
                }
            });
        });

        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!menuContainer.contains(e.target)) {
                dropdown.classList.remove('show');
            }
        });
    }
    
    /**
     * Handle editing a custom widget
     */
    async handleEditCustomWidget(widgetId) {
        console.log('Edit custom widget:', widgetId);
        
        try {
            // Fetch widget data
            const response = await fetch(`/api/dashboard/widgets/${widgetId}`, {
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error('Failed to fetch widget data');
            }
            
            const widgetData = await response.json();
            console.log('Widget data:', widgetData);
            
            // Open appropriate edit modal based on widget type
            if (widgetData.type === 'text') {
                this.openTextWidgetEditModal(widgetData);
            } else if (widgetData.type === 'saved_search') {
                this.openSavedSearchWidgetEditModal(widgetData);
            } else {
                alert('Editing this widget type is not supported yet.');
            }
        } catch (error) {
            console.error('Error editing widget:', error);
            alert('Failed to edit widget. Please try again.');
        }
    }
    
    /**
     * Open text widget edit modal
     */
    openTextWidgetEditModal(widgetData) {
        console.log(`[Dashboard] Opening text widget edit modal for widget:`, widgetData);
        
        // Get the modal
        const modal = document.getElementById('newWidgetModal');
        if (!modal) return;
        
        // Set form to edit mode
        modal.setAttribute('data-edit-mode', 'true');
        modal.setAttribute('data-widget-id', widgetData.id);
        console.log(`[Dashboard] EDIT MODE SET - widget ID: ${widgetData.id}, edit-mode: true`);
        
        // Update modal title
        const modalTitle = modal.querySelector('.modal-header h2');
        if (modalTitle) {
            modalTitle.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.editTextWidget') : 'Edit Text Widget';
        }
        
        // Directly manipulate the display of form sections
        const savedSearchOptions = document.getElementById('savedSearchOptions');
        const textWidgetOptions = document.getElementById('textWidgetOptions');
        const visualizationSection = document.getElementById('visualizationSection');
        const previewSection = document.getElementById('savedSearchPreviewSection');
        
        // Hide saved search sections
        if (savedSearchOptions) savedSearchOptions.style.display = 'none';
        if (visualizationSection) visualizationSection.style.display = 'none';
        if (previewSection) previewSection.style.display = 'none';
        
        // Show text widget section
        if (textWidgetOptions) textWidgetOptions.style.display = 'block';
        
        // Set widget source to text
        const textRadio = document.querySelector('input[name="widgetSource"][value="text"]');
        const savedSearchRadio = document.querySelector('input[name="widgetSource"][value="savedSearch"]');
        
        if (textRadio) {
            textRadio.checked = true;
            if (savedSearchRadio) {
                savedSearchRadio.checked = false;
            }
        }
        
        // Set title
        const titleInput = document.getElementById('widgetTitle');
        if (titleInput) {
            titleInput.value = widgetData.title || '';
        }
        
        // Set description if available (prefer widgetData.description, fallback to config.description for backward compatibility)
        const descriptionInput = document.getElementById('widgetDescription');
        if (descriptionInput) {
            const description = widgetData.description || (widgetData.config && widgetData.config.description) || '';
            descriptionInput.value = description;
        }
        
        // Set text content
        const textEditor = document.getElementById('textWidgetEditorContent');
        if (textEditor) {
            textEditor.innerHTML = widgetData.config.content || '';
        }
        
        // Update OK button text
        const okBtn = document.getElementById('newWidgetOK');
        if (okBtn) {
            okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.updateWidget') : 'Update Widget';
        }
        
        // Show modal
        modal.style.display = 'flex';
    }
    
    /**
     * Open saved search widget edit modal
     */
    openSavedSearchWidgetEditModal(widgetData) {
        console.log(`[Dashboard] Opening saved search widget edit modal for widget:`, widgetData);
        
        // Get the modal
        const modal = document.getElementById('newWidgetModal');
        if (!modal) return;
        
        // Set form to edit mode
        modal.setAttribute('data-edit-mode', 'true');
        modal.setAttribute('data-widget-id', widgetData.id);
        console.log(`[Dashboard] SAVED SEARCH EDIT MODE SET - widget ID: ${widgetData.id}, edit-mode: true`);
        
        // Update modal title
        const modalTitle = modal.querySelector('.modal-header h2');
        if (modalTitle) {
            modalTitle.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.editSavedSearchWidget') : 'Edit Saved Search Widget';
        }
        
        // Set title and description first (these don't depend on async loading)
        const titleInput = document.getElementById('widgetTitle');
        if (titleInput) {
            titleInput.value = widgetData.title || '';
        }
        
        const descriptionInput = document.getElementById('widgetDescription');
        if (descriptionInput) {
            const description = widgetData.description || (widgetData.config && widgetData.config.description) || '';
            descriptionInput.value = description;
        }
        
        // Ensure preview section is visible
        const previewSection = document.getElementById('savedSearchPreviewSection');
        if (previewSection) {
            previewSection.style.display = 'block';
        }
        
        // Set widget source to saved search
        const savedSearchRadio = document.getElementById('widgetSourceSavedSearch');
        if (savedSearchRadio) {
            savedSearchRadio.checked = true;
            
            // Trigger source change to show saved search options and load saved searches
            const event = new Event('change', { bubbles: true });
            savedSearchRadio.dispatchEvent(event);
        }
        
        // Wait for saved searches to load, then set the source dropdown
        const sourceId = widgetData.config.source || widgetData.config.dataSource?.searchId || widgetData.config.dataSource || '';
        console.log('Edit widget - Source ID:', sourceId, 'Config:', widgetData.config);
        
        // Ensure modal is initialized (check both naming conventions)
        const modalInstance = window.NewWidgetModal || window.newWidgetModal;
        if (!modalInstance) {
            console.error('NewWidgetModal not initialized');
            return;
        }
        
        // Function to load saved searches and then set source
        const loadAndSetSource = async () => {
            try {
                // Ensure saved searches are loaded
                if (!modalInstance.savedSearches || modalInstance.savedSearches.length === 0) {
                    console.log('Loading saved searches...');
                    await modalInstance.loadSavedSearches();
                }
                
                console.log('Saved searches loaded:', modalInstance.savedSearches?.length || 0);
                
                // Now set the source dropdown (this will also load facets)
                if (sourceId && typeof modalInstance.setCustomDropdownValue === 'function') {
                    console.log('Setting custom dropdown value:', sourceId);
                    await modalInstance.setCustomDropdownValue(sourceId);
                    console.log('Source set and facets should be loading...');
                } else {
                    // Fallback to hidden input
                    const sourceSelect = document.getElementById('widgetSourceSelect');
                    if (sourceSelect && sourceId) {
                        sourceSelect.value = sourceId;
                        const changeEvent = new Event('change', { bubbles: true });
                        sourceSelect.dispatchEvent(changeEvent);
                        // Also trigger loadFacets for fallback
                        if (typeof modalInstance.loadFacets === 'function') {
                            await modalInstance.loadFacets();
                        }
                    }
                }
            } catch (error) {
                console.error('Error loading saved searches:', error);
            }
        };
        
        // Start loading saved searches
        loadAndSetSource();
            
        // Function to set focus and visualization after source is set and facets are loaded
        const setFocusAndVisualization = async (attempts = 0) => {
            if (attempts > 50) {
                console.error('Timeout waiting for facets to load');
                return;
            }
            
            const focusSelect = document.getElementById('widgetFocusSelect');
            const focusValue = widgetData.config.focus || widgetData.config.facet || '';
            
            // Check if focus dropdown has options (facets are loaded)
            if (focusSelect && focusSelect.options.length > 1) {
                console.log('Facets loaded, setting focus:', focusValue);
                
                if (focusValue) {
                    focusSelect.value = focusValue;
                    
                    // Trigger change event to load visualize by options
                    const changeEvent = new Event('change', { bubbles: true });
                    focusSelect.dispatchEvent(changeEvent);
                }
                
                // Set visualization type
                const visualizeAsValue = widgetData.config.visualizeAs || widgetData.config.visualization?.type || 'table';
                console.log('Setting visualization type:', visualizeAsValue);
                
                const visualizeAsRadios = document.querySelectorAll('input[name="visualizeAs"]');
                visualizeAsRadios.forEach(radio => {
                    if (radio.value === visualizeAsValue) {
                        radio.checked = true;
                        
                        // Trigger change event to update form
                        const radioEvent = new Event('change', { bubbles: true });
                        radio.dispatchEvent(radioEvent);
                    }
                });
                
                // Set visualize by and display columns after a delay
                setTimeout(() => {
                    const visualizeBySelect = document.getElementById('widgetVisualizeBy');
                    const visualizeByValue = widgetData.config.visualizeBy || widgetData.config.visualization?.visualizeBy || '';
                    console.log('Setting visualize by:', visualizeByValue);
                    
                    if (visualizeBySelect && visualizeByValue) {
                        visualizeBySelect.value = visualizeByValue;
                        
                        // Trigger change event
                        const visualizeByEvent = new Event('change', { bubbles: true });
                        visualizeBySelect.dispatchEvent(visualizeByEvent);
                    }
                    
                    // Set display columns for table visualization
                    const displayColumns = widgetData.config.display || 
                                         (widgetData.config.visualization?.displayColumns || []);
                    console.log('Setting display columns:', displayColumns);
                    
                    if (displayColumns.length > 0) {
                        // Wait for display chips to be populated
                        setTimeout(() => {
                            displayColumns.forEach(columnName => {
                                const checkbox = document.getElementById(`display-${columnName}`);
                                if (checkbox) {
                                    checkbox.checked = true;
                                    // Trigger change event to update preview
                                    const checkboxEvent = new Event('change', { bubbles: true });
                                    checkbox.dispatchEvent(checkboxEvent);
                                }
                            });
                        }, 500);
                    }
                    
                    // Trigger preview after all fields are set
                    setTimeout(() => {
                        console.log('Triggering preview with config:', {
                            source: sourceId,
                            focus: focusValue,
                            visualizeAs: visualizeAsValue,
                            visualizeBy: visualizeByValue,
                            display: displayColumns
                        });
                        
                        // Verify form fields are set before preview
                        const formSource = document.getElementById('widgetSourceSelect')?.value;
                        const formFocus = document.getElementById('widgetFocusSelect')?.value;
                        const formVisualizeAs = document.querySelector('input[name="visualizeAs"]:checked')?.value;
                        const formVisualizeBy = document.getElementById('widgetVisualizeBy')?.value;
                        
                        console.log('Form field values:', {
                            source: formSource,
                            focus: formFocus,
                            visualizeAs: formVisualizeAs,
                            visualizeBy: formVisualizeBy
                        });
                        
                        // Mark preview as shown so auto-refresh will work
                        const modalInstance = window.NewWidgetModal || window.newWidgetModal;
                        if (modalInstance) {
                            modalInstance.previewShown = true;
                            
                            // Trigger preview
                            if (typeof modalInstance.handlePreview === 'function') {
                                console.log('Calling handlePreview...');
                                modalInstance.handlePreview().catch(error => {
                                    console.error('Error in handlePreview:', error);
                                });
                            } else {
                                console.error('handlePreview is not a function');
                            }
                        } else {
                            console.error('Modal instance not found');
                        }
                    }, 1200);
                }, 800);
            } else {
                // Wait a bit more for facets to load
                setTimeout(() => setFocusAndVisualization(attempts + 1), 200);
            }
        };
        
        // Start checking for facets after source is set (wait for loadAndSetSource to complete)
        loadAndSetSource().then(() => {
            // Wait a bit for facets to populate, then start checking
            setTimeout(() => {
                setFocusAndVisualization();
            }, 500);
        }).catch(error => {
            console.error('Error in loadAndSetSource:', error);
            // Still try to set focus and visualization even if source loading failed
            setTimeout(() => {
                setFocusAndVisualization();
            }, 1000);
        });
        
        // Update OK button text
        const okBtn = document.getElementById('newWidgetOK');
        if (okBtn) {
            okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.updateWidget') : 'Update Widget';
        }
        
        // Show modal
        modal.style.display = 'flex';
    }
    
    /**
     * Handle deleting a dashboard
     */
    async handleDeleteDashboard() {
        // Prevent deletion of main dashboard
        if (this.currentDashboardId === null) {
            return;
        }
        
        // Show confirmation dialog styled like delete widget
        const confirmResult = await this.showDeleteConfirmation((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.confirmDeleteDashboard') : 'Do you want to permanently delete this dashboard?');
        if (!confirmResult) {
            return;
        }
        
        try {
            const response = await fetch(`/api/dashboard/delete?id=${this.currentDashboardId}`, {
                method: 'DELETE',
                credentials: 'include'
            });
            
            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to delete dashboard');
            }
            
            // Close the dashboard tab
            this.closeDashboardTab(this.currentDashboardId);
            
            console.log('Dashboard deleted successfully');
        } catch (error) {
            console.error('Error deleting dashboard:', error);
            alert('Failed to delete dashboard: ' + error.message);
        }
    }
    
    /**
     * Handle cloning a dashboard
     */
    async handleCloneDashboard() {
        // Prevent cloning of main dashboard
        if (this.currentDashboardId === null) {
            return;
        }
        
        try {
            // Fetch current dashboard info
            const response = await fetch(`/api/dashboard/info?id=${this.currentDashboardId}`, {
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error('Failed to fetch dashboard info');
            }
            
            const dashboardInfo = await response.json();
            const originalTitle = dashboardInfo.title || 'Dashboard';
            const originalDescription = dashboardInfo.description || '';
            
            // Show clone modal with pre-filled values
            this.openCloneDashboardModal(originalTitle, originalDescription);
        } catch (error) {
            console.error('Error fetching dashboard info for cloning:', error);
            alert('Failed to load dashboard information. Please try again.');
        }
    }
    
    /**
     * Open Clone Dashboard Modal
     */
    openCloneDashboardModal(originalTitle, originalDescription) {
        const modal = document.getElementById('cloneDashboardModal');
        if (!modal) return;

        // Pre-fill form with original values + " - Copy"
        const nameInput = document.getElementById('cloneDashboardName');
        const descriptionInput = document.getElementById('cloneDashboardDescription');
        
        if (nameInput) {
            nameInput.value = originalTitle + ' - Copy';
        }
        if (descriptionInput) {
            descriptionInput.value = originalDescription;
        }

        // Reset checkbox
        const setAsDefaultCheckbox = document.getElementById('cloneDashboardSetAsDefault');
        if (setAsDefaultCheckbox) {
            setAsDefaultCheckbox.checked = false;
        }

        // Show modal
        modal.style.display = 'flex';
        
        // Setup listeners if not already done
        this.setupCloneDashboardModalListeners();
    }
    
    /**
     * Setup Clone Dashboard Modal Listeners
     */
    setupCloneDashboardModalListeners() {
        const modal = document.getElementById('cloneDashboardModal');
        if (!modal) return;

        // Close button
        const closeBtn = document.getElementById('cloneDashboardModalClose');
        if (closeBtn && !closeBtn.dataset.listenerAdded) {
            closeBtn.dataset.listenerAdded = 'true';
            closeBtn.addEventListener('click', () => {
                modal.style.display = 'none';
            });
        }

        // Cancel button
        const cancelBtn = document.getElementById('cloneDashboardCancel');
        if (cancelBtn && !cancelBtn.dataset.listenerAdded) {
            cancelBtn.dataset.listenerAdded = 'true';
            cancelBtn.addEventListener('click', () => {
                modal.style.display = 'none';
            });
        }

        // Form submit
        const form = document.getElementById('cloneDashboardForm');
        if (form && !form.dataset.listenerAdded) {
            form.dataset.listenerAdded = 'true';
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                this.cloneDashboard();
            });
        }

        // Close on overlay click
        if (!modal.dataset.listenerAdded) {
            modal.dataset.listenerAdded = 'true';
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    modal.style.display = 'none';
                }
            });
        }
    }
    
    /**
     * Clone Dashboard
     */
    async cloneDashboard() {
        const nameInput = document.getElementById('cloneDashboardName');
        const descriptionInput = document.getElementById('cloneDashboardDescription');
        const setAsDefaultCheckbox = document.getElementById('cloneDashboardSetAsDefault');
        const okBtn = document.getElementById('cloneDashboardOK');
        const cancelBtn = document.getElementById('cloneDashboardCancel');

        const name = nameInput?.value?.trim();
        const description = descriptionInput?.value?.trim() || '';
        const setAsDefault = setAsDefaultCheckbox?.checked || false;

        if (!name) {
            alert('Please enter a dashboard name.');
            return;
        }

        // Disable buttons during request
        if (okBtn) {
            const originalText = okBtn.textContent;
            okBtn.disabled = true;
            okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.cloning') : 'Cloning...';
        }
        if (cancelBtn) {
            cancelBtn.disabled = true;
        }

        try {
            const response = await fetch('/api/dashboard/clone', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    sourceDashboardId: this.currentDashboardId,
                    name: name,
                    description: description,
                    setAsDefault: setAsDefault
                })
            });

            const result = await response.json();

            if (response.ok && result.success) {
                // Close modal
                const modal = document.getElementById('cloneDashboardModal');
                if (modal) {
                    modal.style.display = 'none';
                }

                // Create tab and switch to cloned dashboard
                const dashboardId = result.dashboardId;
                await this.createDashboardTab(dashboardId, name);
                await this.switchToDashboard(dashboardId);
            } else {
                alert(result.error || ((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.cloneFailed') : 'Failed to clone dashboard. Please try again.'));
                if (okBtn) {
                    okBtn.disabled = false;
                    okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.ok') : 'OK';
                }
                if (cancelBtn) {
                    cancelBtn.disabled = false;
                }
            }
        } catch (error) {
            console.error('Error cloning dashboard:', error);
            alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.cloneError') : 'An error occurred while cloning the dashboard. Please try again.');
            if (okBtn) {
                okBtn.disabled = false;
                okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.ok') : 'OK';
            }
            if (cancelBtn) {
                cancelBtn.disabled = false;
            }
        }
    }
    
    /**
     * Handle modifying sharing for a dashboard
     */
    async handleModifySharing() {
        // Prevent sharing modification of main dashboard
        if (this.currentDashboardId === null) {
            return;
        }
        
        console.log('Opening sharing modal for dashboard:', this.currentDashboardId);
        await this.openSharingModal();
    }

    /**
     * Open sharing modal
     */
    async openSharingModal() {
        const modal = document.getElementById('modifySharingModal');
        if (!modal) return;

        try {
            // Load current sharing settings
            const response = await fetch(`/api/dashboard/sharing?dashboardId=${this.currentDashboardId}`, {
                credentials: 'include'
            });

            if (response.ok) {
                const sharingInfo = await response.json();
                this.populateSharingModal(sharingInfo);
            } else {
                console.error('Failed to load sharing info');
                // Set default to stop sharing
                document.getElementById('stopSharing').checked = true;
            }

            // Setup modal event listeners
            this.setupSharingModalListeners();
            
            // Show modal
            modal.style.display = 'block';
            
        } catch (error) {
            console.error('Error opening sharing modal:', error);
            alert('Failed to load sharing settings. Please try again.');
        }
    }

    /**
     * Populate sharing modal with current settings
     */
    populateSharingModal(sharingInfo) {
        // Reset all radio buttons
        document.querySelectorAll('input[name="sharingType"]').forEach(radio => {
            radio.checked = false;
        });

        // Set the appropriate radio button
        const sharingType = sharingInfo.sharingType || 'stop';
        const radioButton = document.getElementById(
            sharingType === 'public' ? 'sharePublic' :
            sharingType === 'selected' ? 'shareSelected' : 'stopSharing'
        );
        
        if (radioButton) {
            radioButton.checked = true;
            this.toggleSharingSubOptions(sharingType);
        }

        // If sharing with selected users, populate the user list
        if (sharingType === 'selected' && sharingInfo.sharedUsers) {
            this.populateSelectedUsers(sharingInfo.sharedUsers);
        }
    }

    /**
     * Setup sharing modal event listeners
     */
    setupSharingModalListeners() {
        // Remove existing listeners to prevent duplicates
        this.removeSharingModalListeners();

        // Radio button change listeners
        const radioButtons = document.querySelectorAll('input[name="sharingType"]');
        radioButtons.forEach(radio => {
            radio.addEventListener('change', this.handleSharingTypeChange.bind(this));
        });

        // User search input
        const userSearchInput = document.getElementById('userSearchInput');
        if (userSearchInput) {
            userSearchInput.addEventListener('input', this.handleUserSearch.bind(this));
        }

        // Modal close listeners
        const closeBtn = document.getElementById('modifySharingModalClose');
        const cancelBtn = document.getElementById('modifySharingCancel');
        const saveBtn = document.getElementById('modifySharingSave');

        if (closeBtn) {
            closeBtn.addEventListener('click', this.closeSharingModal.bind(this));
        }
        if (cancelBtn) {
            cancelBtn.addEventListener('click', this.closeSharingModal.bind(this));
        }
        if (saveBtn) {
            saveBtn.addEventListener('click', this.saveSharingSettings.bind(this));
        }

        // Click outside to close
        const modal = document.getElementById('modifySharingModal');
        if (modal) {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    this.closeSharingModal();
                }
            });
        }
    }

    /**
     * Remove sharing modal event listeners
     */
    removeSharingModalListeners() {
        const radioButtons = document.querySelectorAll('input[name="sharingType"]');
        radioButtons.forEach(radio => {
            radio.removeEventListener('change', this.handleSharingTypeChange.bind(this));
        });
    }

    /**
     * Handle sharing type change
     */
    handleSharingTypeChange(event) {
        const sharingType = event.target.value;
        this.toggleSharingSubOptions(sharingType);
    }

    /**
     * Toggle sharing sub-options based on selected type
     */
    toggleSharingSubOptions(sharingType) {
        const publicSubOption = document.getElementById('publicSubOption');
        const selectedSubOption = document.getElementById('selectedSubOption');

        if (publicSubOption) {
            publicSubOption.style.display = sharingType === 'public' ? 'block' : 'none';
        }
        if (selectedSubOption) {
            selectedSubOption.style.display = sharingType === 'selected' ? 'block' : 'none';
        }

        // Load users if selecting specific users
        if (sharingType === 'selected') {
            this.loadUsers();
        }
    }

    /**
     * Load users for selection
     */
    async loadUsers(searchTerm = '') {
        try {
            const url = `/api/users/list${searchTerm ? `?search=${encodeURIComponent(searchTerm)}` : ''}`;
            const response = await fetch(url, {
                credentials: 'include'
            });

            if (response.ok) {
                const users = await response.json();
                this.displayUsers(users);
            } else {
                console.error('Failed to load users');
            }
        } catch (error) {
            console.error('Error loading users:', error);
        }
    }

    /**
     * Display users in the selection list
     */
    displayUsers(users) {
        const userList = document.getElementById('userList');
        if (!userList) {
            console.error('[Sharing] userList element not found');
            return;
        }

        console.log('[Sharing] Displaying users:', users);
        userList.innerHTML = '';

        if (!users || users.length === 0) {
            console.log('[Sharing] No users to display');
            return;
        }

        users.forEach((user, index) => {
            console.log(`[Sharing] User ${index}:`, user);
            console.log(`[Sharing] User ${index} name field:`, user.name, 'type:', typeof user.name);
            
            // Handle different possible field names - check for null/undefined explicitly
            const userId = user.id || user.ID || '';
            let userName = user.name;
            if (!userName || userName === null || userName === undefined) {
                userName = user.Name || '';
            }
            if (!userName || userName === null || userName === undefined) {
                if (user.firstName && user.lastName) {
                    userName = `${user.firstName} ${user.lastName}`;
                } else if (user.firstName) {
                    userName = user.firstName;
                } else if (user.lastName) {
                    userName = user.lastName;
                } else {
                    userName = 'Unknown User';
                }
            }
            
            const userEmail = user.email || user.Email || '';
            const orgUnit = user.orgUnit || user.org_unit || '';

            console.log(`[Sharing] User ${index} processed - ID: ${userId}, Name: ${userName}, Email: ${userEmail}`);

            if (!userId) {
                console.warn('[Sharing] Skipping user without ID:', user);
                return;
            }

            const userItem = document.createElement('div');
            userItem.className = 'user-item';
            
            // Use textContent for safety and visibility
            const nameDiv = document.createElement('div');
            nameDiv.className = 'user-name';
            nameDiv.textContent = userName;
            
            const detailsDiv = document.createElement('div');
            detailsDiv.className = 'user-details';
            detailsDiv.textContent = userEmail + (orgUnit ? ` • ${orgUnit}` : '');
            
            const infoDiv = document.createElement('div');
            infoDiv.className = 'user-info';
            infoDiv.appendChild(nameDiv);
            infoDiv.appendChild(detailsDiv);
            
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.value = userId;
            checkbox.setAttribute('data-user-name', userName);
            checkbox.setAttribute('data-user-email', userEmail);
            checkbox.addEventListener('change', this.handleUserSelection.bind(this));
            
            const label = document.createElement('label');
            label.className = 'user-checkbox-label';
            label.appendChild(checkbox);
            label.appendChild(infoDiv);
            
            userItem.appendChild(label);
            userList.appendChild(userItem);
            
            console.log(`[Sharing] User ${index} rendered - Name visible:`, nameDiv.textContent);
        });
        
        console.log('[Sharing] Finished displaying users');
    }

    /**
     * Handle user search
     */
    async handleUserSearch(event) {
        const searchTerm = event.target.value;
        await this.loadUsers(searchTerm);
    }

    /**
     * Handle user selection
     */
    handleUserSelection(event) {
        const checkbox = event.target;
        const userId = checkbox.value;
        const userName = checkbox.dataset.userName;
        const userEmail = checkbox.dataset.userEmail;

        if (checkbox.checked) {
            this.addSelectedUser(userId, userName, userEmail);
        } else {
            this.removeSelectedUser(userId);
        }
    }

    /**
     * Add selected user to the list
     */
    addSelectedUser(userId, userName, userEmail) {
        const selectedUsers = document.getElementById('selectedUsers');
        if (!selectedUsers) return;

        // Check if already added
        if (selectedUsers.querySelector(`[data-user-id="${userId}"]`)) {
            return;
        }

        // Prevent sharing with self
        const currentUserId = this.currentUser ? (this.currentUser.id || this.currentUser.ID) : null;
        if (currentUserId && parseInt(userId) === parseInt(currentUserId)) {
            alert('You cannot share the dashboard with yourself. Please choose another user.');
            // Uncheck the checkbox in the user list
            const checkbox = document.querySelector(`#userList input[value="${userId}"]`);
            if (checkbox) {
                checkbox.checked = false;
            }
            return;
        }

        const userTag = document.createElement('div');
        userTag.className = 'selected-user-tag';
        userTag.dataset.userId = userId;
        userTag.innerHTML = `
            <span class="user-tag-name">${userName}</span>
            <button type="button" class="user-tag-remove" onclick="dashboard.removeSelectedUser('${userId}')">
                <i class="fas fa-times"></i>
            </button>
        `;

        selectedUsers.appendChild(userTag);
    }

    /**
     * Remove selected user from the list
     */
    removeSelectedUser(userId) {
        const selectedUsers = document.getElementById('selectedUsers');
        if (!selectedUsers) return;

        const userTag = selectedUsers.querySelector(`[data-user-id="${userId}"]`);
        if (userTag) {
            userTag.remove();
        }

        // Uncheck the checkbox in the user list
        const checkbox = document.querySelector(`#userList input[value="${userId}"]`);
        if (checkbox) {
            checkbox.checked = false;
        }
    }

    /**
     * Populate selected users (when loading existing settings)
     */
    populateSelectedUsers(sharedUsers) {
        const selectedUsers = document.getElementById('selectedUsers');
        if (!selectedUsers) return;

        selectedUsers.innerHTML = '';

        sharedUsers.forEach(user => {
            this.addSelectedUser(user.id, user.name, user.email);
        });
    }

    /**
     * Save sharing settings
     */
    async saveSharingSettings() {
        const saveBtn = document.getElementById('modifySharingSave');
        if (saveBtn) {
            saveBtn.disabled = true;
            saveBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.saving') : 'Saving...';
        }

        try {
            console.log('[Sharing] Save button clicked');
            const sharingType = document.querySelector('input[name="sharingType"]:checked')?.value;
            console.log('[Sharing] Selected sharing type:', sharingType);
            if (!sharingType) {
                alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.sharing.selectSharingOption') : 'Please select a sharing option.');
                return;
            }

            const requestData = {
                dashboardId: this.currentDashboardId,
                sharingType: sharingType
            };

            // Add selected users if sharing with specific users
            if (sharingType === 'selected') {
                const selectedUserTags = document.querySelectorAll('#selectedUsers .selected-user-tag');
                const selectedUsers = Array.from(selectedUserTags).map(tag => 
                    parseInt(tag.dataset.userId)
                );
                
                // Validate: prevent sharing with self
                const currentUserId = this.currentUser ? (this.currentUser.id || this.currentUser.ID) : null;
                if (currentUserId && selectedUsers.includes(parseInt(currentUserId))) {
                    alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.sharing.cannotShareWithSelf') : 'You cannot share the dashboard with yourself. Please choose another user.');
                    if (saveBtn) {
                        saveBtn.disabled = false;
                        saveBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.save') : 'Save';
                    }
                    return;
                }
                
                requestData.selectedUsers = selectedUsers;
            }

            // Add search sharing options
            if (sharingType === 'public') {
                requestData.makeSearchesPublic = document.getElementById('makeSearchesPublic')?.checked || false;
                console.log('[Sharing] Public sharing - makeSearchesPublic:', requestData.makeSearchesPublic);
            } else if (sharingType === 'selected') {
                requestData.shareSearches = document.getElementById('shareSearchesSelected')?.checked || false;
                console.log('[Sharing] Selected sharing - shareSearches:', requestData.shareSearches);
            }

            console.log('[Sharing] Sending request:', requestData);
            
            const response = await fetch('/api/dashboard/sharing', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(requestData)
            });

            console.log('[Sharing] Response status:', response.status);
            const result = await response.json();
            console.log('[Sharing] Response result:', result);

            if (response.ok && result.success) {
                alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.sharing.sharingUpdated') : 'Sharing settings updated successfully!');
                this.closeSharingModal();
                // Reload dashboard info to update the display
                await this.loadDashboardInfoForId(this.currentDashboardId);
            } else {
                alert(result.error || ((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.sharing.sharingUpdateFailed') : 'Failed to update sharing settings. Please try again.'));
            }

        } catch (error) {
            console.error('Error saving sharing settings:', error);
            alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.sharing.sharingSaveError') : 'An error occurred while saving sharing settings. Please try again.');
        } finally {
            if (saveBtn) {
                saveBtn.disabled = false;
                saveBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.save') : 'Save';
            }
        }
    }

    /**
     * Close sharing modal
     */
    closeSharingModal() {
        const modal = document.getElementById('modifySharingModal');
        if (modal) {
            modal.style.display = 'none';
        }
        this.removeSharingModalListeners();
    }
    
    /**
     * Handle deleting a custom widget
     */
    async handleDeleteCustomWidget(widgetId, container) {
        // Create and show custom confirmation dialog
        const confirmResult = await this.showDeleteConfirmation((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.confirmDeleteWidget') : 'Do you want to permanently delete this widget?');
        if (!confirmResult) {
            return;
        }
        
        try {
            const response = await fetch(`/api/dashboard/widgets/${widgetId}`, {
                method: 'DELETE',
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error('Failed to delete widget');
            }
            
            // Remove from DOM
            if (container) {
                container.remove();
            }
            
            // Remove from widgets object
            delete this.widgets[`widget${widgetId}`];
            
            // Update drag and drop
            if (this.dragDrop) {
                this.dragDrop.setupDragAndDrop();
            }
            
            console.log('Widget deleted successfully');
        } catch (error) {
            console.error('Error deleting widget:', error);
            alert('Failed to delete widget. Please try again.');
        }
    }
    
    /**
     * Show custom delete confirmation dialog
     * @param {string} message - The confirmation message to display
     * @returns {Promise<boolean>} - Resolves to true if confirmed, false if canceled
     */
    showDeleteConfirmation(message) {
        return new Promise(resolve => {
            // Create overlay
            const overlay = document.createElement('div');
            overlay.className = 'modal-overlay';
            
            // Create dialog container
            const dialog = document.createElement('div');
            dialog.className = 'confirm-dialog';
            
            // Create dialog header
            const header = document.createElement('div');
            header.className = 'confirm-dialog-header';
            header.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.confirmDelete') : 'Confirm Delete';
            
            // Create dialog body
            const body = document.createElement('div');
            body.className = 'confirm-dialog-body';
            body.textContent = message;
            
            // Create dialog footer
            const footer = document.createElement('div');
            footer.className = 'confirm-dialog-footer';
            
            // Create OK button
            const okButton = document.createElement('button');
            okButton.className = 'confirm-dialog-btn confirm-dialog-btn-primary';
            okButton.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.ok') : 'OK';
            
            // Create Cancel button
            const cancelButton = document.createElement('button');
            cancelButton.className = 'confirm-dialog-btn confirm-dialog-btn-secondary';
            cancelButton.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.cancel') : 'Cancel';
            
            // Add event listeners
            okButton.addEventListener('click', () => {
                document.body.removeChild(overlay);
                resolve(true);
            });
            
            cancelButton.addEventListener('click', () => {
                document.body.removeChild(overlay);
                resolve(false);
            });
            
            // Close on Escape key
            document.addEventListener('keydown', function escListener(e) {
                if (e.key === 'Escape') {
                    document.body.removeChild(overlay);
                    document.removeEventListener('keydown', escListener);
                    resolve(false);
                }
            });
            
            // Close on overlay click
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    document.body.removeChild(overlay);
                    resolve(false);
                }
            });
            
            // Assemble dialog
            footer.appendChild(cancelButton);
            footer.appendChild(okButton);
            
            dialog.appendChild(header);
            dialog.appendChild(body);
            dialog.appendChild(footer);
            
            overlay.appendChild(dialog);
            
            // Add to DOM
            document.body.appendChild(overlay);
            
            // Focus OK button
            okButton.focus();
        });
    }

    /**
     * Initialize a single widget
     */
    async initWidget(key, WidgetClass, containerId, userId = null) {
        try {
            // Check if widget should be hidden BEFORE initializing
            if (this.dragDrop && this.dragDrop.hiddenWidgets.includes(key)) {
                const container = document.getElementById(containerId);
                if (container) {
                    container.style.setProperty('display', 'none', 'important');
                }
            }
            
            const widget = new WidgetClass(containerId);
            this.widgets[key] = widget;
            
            if (userId) {
                await widget.init(userId);
            } else {
                await widget.init();
            }
            
            // Apply visibility again after widget is initialized
            if (this.dragDrop && this.dragDrop.hiddenWidgets.includes(key)) {
                const container = document.getElementById(containerId);
                if (container) {
                    container.style.setProperty('display', 'none', 'important');
                }
            }
        } catch (error) {
            console.error(`Error initializing ${key} widget:`, error);
        }
    }

    /**
     * Destroy all widgets
     */
    destroy() {
        Object.values(this.widgets).forEach(widget => {
            if (widget && typeof widget.destroy === 'function') {
                widget.destroy();
            }
        });
        this.widgets = {};
    }

    /**
     * Refresh all widgets
     */
    async refresh() {
        this.destroy();
        await this.initializeWidgets();
    }

    /**
     * Open Edit Dashboard modal
     */
    async openEditDashboardModal() {
        const modal = document.getElementById('editDashboardModal');
        if (!modal) {
            console.error('Edit dashboard modal not found');
            return;
        }

        try {
            // Load current dashboard info - use currentDashboardId if available
            let url = '/api/dashboard/info';
            if (this.currentDashboardId !== null) {
                url += `?id=${this.currentDashboardId}`;
            }
            
            const response = await fetch(url, {
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error('Failed to load dashboard info');
            }

            const dashboardInfo = await response.json();
            
            // Populate form fields
            const titleInput = document.getElementById('editDashboardTitle');
            const descriptionInput = document.getElementById('editDashboardDescription');
            const setAsDefaultInput = document.getElementById('editDashboardSetAsDefault');
            
            if (titleInput) {
                titleInput.value = dashboardInfo.title || 'My Dashboard';
            }
            if (descriptionInput) {
                descriptionInput.value = dashboardInfo.description || '';
            }
            if (setAsDefaultInput) {
                setAsDefaultInput.checked = dashboardInfo.isDefault || false;
            }

            // Show modal
            modal.style.display = 'flex';
            
            // Setup event listeners
            this.setupEditDashboardModalListeners();
        } catch (error) {
            console.error('Error opening edit dashboard modal:', error);
            alert('Failed to load dashboard information. Please try again.');
        }
    }

    /**
     * Setup event listeners for edit dashboard modal
     */
    setupEditDashboardModalListeners() {
        const modal = document.getElementById('editDashboardModal');
        const closeBtn = document.getElementById('editDashboardModalClose');
        const cancelBtn = document.getElementById('editDashboardCancel');
        const saveBtn = document.getElementById('editDashboardSave');
        const form = document.getElementById('editDashboardForm');

        // Remove existing listeners by cloning
        const newCloseBtn = closeBtn.cloneNode(true);
        closeBtn.parentNode.replaceChild(newCloseBtn, closeBtn);
        
        const newCancelBtn = cancelBtn.cloneNode(true);
        cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);
        
        const newSaveBtn = saveBtn.cloneNode(true);
        saveBtn.parentNode.replaceChild(newSaveBtn, saveBtn);

        // Close modal handlers
        const closeModal = () => {
            modal.style.display = 'none';
        };

        newCloseBtn.addEventListener('click', closeModal);
        newCancelBtn.addEventListener('click', closeModal);

        // Click outside modal to close
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });

        // Save handler
        newSaveBtn.addEventListener('click', async () => {
            await this.saveDashboardInfo();
        });

        // Form submit handler
        if (form) {
            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                await this.saveDashboardInfo();
            });
        }
    }

    /**
     * Save dashboard info
     */
    async saveDashboardInfo() {
        const titleInput = document.getElementById('editDashboardTitle');
        const descriptionInput = document.getElementById('editDashboardDescription');
        const setAsDefaultInput = document.getElementById('editDashboardSetAsDefault');
        const saveBtn = document.getElementById('editDashboardSave');
        const cancelBtn = document.getElementById('editDashboardCancel');

        if (!titleInput || !titleInput.value.trim()) {
            alert('Title is required');
            titleInput.focus();
            return;
        }

        const title = titleInput.value.trim();
        const description = descriptionInput ? descriptionInput.value.trim() : '';
        const setAsDefault = setAsDefaultInput ? setAsDefaultInput.checked : false;

        // Disable buttons and show loading state
        if (saveBtn) {
            saveBtn.disabled = true;
            const originalText = saveBtn.textContent;
            saveBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.saving') : 'Saving...';
            saveBtn.style.opacity = '0.7';
        }
        if (cancelBtn) {
            cancelBtn.disabled = true;
        }

        try {
            // Include dashboardId in request body if editing a specific dashboard
            const requestBody = {
                title: title,
                description: description,
                setAsDefault: setAsDefault
            };
            
            // Only include dashboardId if it's not the main dashboard (null)
            if (this.currentDashboardId !== null) {
                requestBody.dashboardId = this.currentDashboardId;
            }
            
            const response = await fetch('/api/dashboard/info', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to save dashboard');
            }

            const result = await response.json();
            
            // Reload dashboard info to get updated creator and shared with info
            await this.loadDashboardInfoForId(this.currentDashboardId);

            // Close modal
            const modal = document.getElementById('editDashboardModal');
            if (modal) {
                modal.style.display = 'none';
            }

            console.log('Dashboard updated successfully');
        } catch (error) {
            console.error('Error saving dashboard:', error);
            alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.saveFailed', { error: error.message }) : 'Failed to save dashboard: ' + error.message);
        } finally {
            // Re-enable buttons
            if (saveBtn) {
                saveBtn.disabled = false;
                saveBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.save') : 'Save';
                saveBtn.style.opacity = '1';
            }
            if (cancelBtn) {
                cancelBtn.disabled = false;
            }
        }
    }

    /**
     * Update dashboard display with new title, description, creator, and shared with info
     */
    async updateDashboardDisplay(title, description, creatorName = null, sharedWith = [], isPublic = false, createdBy = null) {
        const dashboardHeader = document.querySelector('.dashboard-header');
        if (dashboardHeader) {
            const titleElement = dashboardHeader.querySelector('h1');
            const descriptionElement = dashboardHeader.querySelector('.dashboard-description');
            
            if (titleElement) {
                titleElement.textContent = title;
            }
            if (descriptionElement) {
                descriptionElement.textContent = description || '';
            }
            
            // Update creator name
            const creatorElement = document.getElementById('dashboardCreator');
            if (creatorElement) {
                if (creatorName) {
                    creatorElement.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.createdBy', { name: creatorName }) : `Created by: ${creatorName}`;
                    creatorElement.style.display = '';
                } else {
                    creatorElement.style.display = 'none';
                }
            }
            
            // Update shared with information
            // For default dashboard (home dashboard), never show "Shared with" - it's personal
            const sharedWithElement = document.getElementById('dashboardSharedWith');
            if (sharedWithElement) {
                // Hide "Shared with" for default dashboard (main dashboard)
                if (this.currentDashboardId === null) {
                    // This is the default/home dashboard - never show sharing info
                    sharedWithElement.style.display = 'none';
                } else {
                    // This is a custom dashboard - show sharing info if applicable
                    if (isPublic) {
                        // Dashboard is shared publicly
                        sharedWithElement.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.sharedWithPublic') : 'Shared with: Public';
                        sharedWithElement.style.display = '';
                    } else if (sharedWith && sharedWith.length > 0) {
                        // Dashboard is shared with specific users
                        const sharedNames = sharedWith.map(user => user.name || user).join(', ');
                        sharedWithElement.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.sharedWith', { names: sharedNames }) : `Shared with: ${sharedNames}`;
                        sharedWithElement.style.display = '';
                    } else {
                        // Dashboard is not shared
                        sharedWithElement.style.display = 'none';
                    }
                }
            }
        }
        
        // Update the subtab name based on which dashboard is being edited
        if (this.currentDashboardId === null) {
            // Main dashboard - update main dashboard tab
        const dashboardTab = document.getElementById('dashboardTab');
            if (dashboardTab) {
            const tabSpan = dashboardTab.querySelector('span');
            if (tabSpan) {
                tabSpan.textContent = title;
                }
            }
        } else {
            // Custom dashboard - update custom dashboard tab
            const customTab = document.querySelector(`[data-dashboard-id="${this.currentDashboardId}"]`);
            if (customTab) {
                const tabSpan = customTab.querySelector('span');
                if (tabSpan) {
                    tabSpan.textContent = title;
                }
            }
        }
    }

    /**
     * Open Choose Dashboard modal
     */
    async openChooseDashboardModal() {
        const modal = document.getElementById('chooseDashboardModal');
        if (!modal) {
            console.error('Choose dashboard modal not found');
            return;
        }

        // Show modal
        modal.style.display = 'flex';
        
        // Load dashboards
        await this.loadDashboardsForChoose();
        
        // Setup event listeners
        this.setupChooseDashboardModalListeners();
    }

    /**
     * Load dashboards for choose modal
     */
    async loadDashboardsForChoose() {
        const dashboardList = document.getElementById('chooseDashboardList');
        if (!dashboardList) return;

        try {
            const response = await fetch('/api/dashboard/list', {
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error('Failed to load dashboards');
            }

            const dashboards = await response.json();
            
            // Get the main dashboard ID to avoid duplication
            const mainDashboardInfo = await this.getMainDashboardInfo();
            const mainDashboardId = mainDashboardInfo.id;
            
            // Include ALL dashboards in the choose modal (not filtered by isDefault)
            // Only exclude the main dashboard (the one used for dashboardTab) to avoid duplication
            const customDashboards = dashboards.filter(d => d.id !== mainDashboardId);
            
            // Add main dashboard (null) to the list for display
            const allDashboards = [
                {
                    id: null,
                    title: mainDashboardInfo.title || 'My Dashboard',
                    description: mainDashboardInfo.description || '',
                    isDefault: mainDashboardInfo.isDefault || false
                },
                ...customDashboards
            ];
            
            // Store dashboards for filtering
            this.availableDashboards = allDashboards;
            
            // Render dashboards
            this.renderDashboardList(allDashboards);
        } catch (error) {
            console.error('Error loading dashboards:', error);
            dashboardList.innerHTML = '<p style="padding: 1rem; color: var(--text-secondary);">Failed to load dashboards</p>';
        }
    }

    /**
     * Get main dashboard info
     */
    async getMainDashboardInfo() {
        try {
            const response = await fetch('/api/dashboard/info', {
                credentials: 'include'
            });
            if (response.ok) {
                return await response.json();
            }
        } catch (error) {
            console.error('Error loading main dashboard info:', error);
        }
        return { title: 'My Dashboard', description: '', isDefault: false };
    }

    /**
     * Render dashboard list
     */
    renderDashboardList(dashboards) {
        const dashboardList = document.getElementById('chooseDashboardList');
        if (!dashboardList) return;

        if (dashboards.length === 0) {
            dashboardList.innerHTML = '<p style="padding: 1rem; color: var(--text-secondary);">No dashboards found</p>';
            return;
        }

        // Load hidden dashboards from localStorage
        let hiddenDashboards = [];
        try {
            const saved = localStorage.getItem('dashboardHiddenDashboards');
            if (saved !== null && saved !== '') {
                hiddenDashboards = JSON.parse(saved);
            }
        } catch (e) {
            console.error('Error parsing hiddenDashboards from localStorage:', e);
            hiddenDashboards = [];
        }

        dashboardList.innerHTML = dashboards.map(dashboard => {
            const dashboardId = dashboard.id;
            const isMainDashboard = dashboardId === null || dashboardId === undefined;
            
            // Check if dashboard tab is visible
            let isTabVisible = true;
            if (!isMainDashboard) {
                const dashboardTab = document.querySelector(`.sub-nav-tabs [data-dashboard-id="${dashboardId}"]`);
                isTabVisible = dashboardTab && dashboardTab.style.display !== 'none' && dashboardTab.offsetParent !== null;
            } else {
                // Check main dashboard tab visibility
                const dashboardTab = document.getElementById('dashboardTab');
                isTabVisible = dashboardTab && dashboardTab.style.display !== 'none' && dashboardTab.offsetParent !== null;
            }
            
            // Check if in hidden list (use 'main' as key for main dashboard)
            const hiddenKey = isMainDashboard ? 'main' : dashboardId;
            const isInHiddenList = hiddenDashboards.includes(hiddenKey);
            
            // Dashboard is shown if it's not in hidden list AND tab is visible
            const isChecked = !isInHiddenList && isTabVisible;
            
            return `
                <div class="dashboard-item" data-dashboard-id="${dashboardId !== null && dashboardId !== undefined ? dashboardId : 'main'}">
                    <input type="checkbox" id="dashboard-${dashboardId !== null && dashboardId !== undefined ? dashboardId : 'main'}" ${isChecked ? 'checked' : ''}>
                    <label for="dashboard-${dashboardId !== null && dashboardId !== undefined ? dashboardId : 'main'}" class="dashboard-item-label">
                    <i class="fas fa-th-large dashboard-icon"></i>
                    <div class="dashboard-item-content">
                        <div class="dashboard-item-title">${this.escapeHtml(dashboard.title)}</div>
                        <div class="dashboard-item-description">${this.escapeHtml(dashboard.description || '')}</div>
                    </div>
                </label>
            </div>
            `;
        }).join('');
    }

    /**
     * Setup event listeners for choose dashboard modal
     */
    setupChooseDashboardModalListeners() {
        const modal = document.getElementById('chooseDashboardModal');
        const closeBtn = document.getElementById('chooseDashboardModalClose');
        const cancelBtn = document.getElementById('chooseDashboardCancel');
        const okBtn = document.getElementById('chooseDashboardOK');
        const searchInput = document.getElementById('chooseDashboardSearch');

        // Close modal
        const closeModal = () => {
            modal.style.display = 'none';
        };

        if (closeBtn) {
            closeBtn.addEventListener('click', closeModal);
        }

        if (cancelBtn) {
            cancelBtn.addEventListener('click', closeModal);
        }

        if (okBtn) {
            okBtn.addEventListener('click', () => {
                this.handleSaveDashboardVisibility();
                closeModal();
            });
        }

        // Click outside modal to close
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });

        // Search functionality
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                const searchTerm = e.target.value.toLowerCase().trim();
                const filtered = this.availableDashboards.filter(dashboard => {
                    const title = (dashboard.title || '').toLowerCase();
                    const description = (dashboard.description || '').toLowerCase();
                    return title.includes(searchTerm) || description.includes(searchTerm);
                });
                this.renderDashboardList(filtered);
            });
        }
    }

    /**
     * Apply dashboard visibility state from localStorage
     */
    applyDashboardVisibility() {
        // Load hidden dashboards from localStorage
        let hiddenDashboards = [];
        try {
            const saved = localStorage.getItem('dashboardHiddenDashboards');
            if (saved !== null && saved !== '') {
                hiddenDashboards = JSON.parse(saved);
            }
        } catch (e) {
            console.error('Error parsing hiddenDashboards from localStorage:', e);
            hiddenDashboards = [];
        }

        // Apply visibility to main dashboard tab
        const mainDashboardTab = document.getElementById('dashboardTab');
        if (mainDashboardTab) {
            if (hiddenDashboards.includes('main')) {
                mainDashboardTab.style.display = 'none';
            } else {
                mainDashboardTab.style.display = '';
            }
        }

        // Apply visibility to custom dashboard tabs
        document.querySelectorAll('.sub-nav-tabs [data-dashboard-id]').forEach(tab => {
            const dashboardId = tab.getAttribute('data-dashboard-id');
            if (dashboardId && dashboardId !== 'main' && dashboardId !== 'null') {
                const id = isNaN(dashboardId) ? dashboardId : parseInt(dashboardId);
                if (hiddenDashboards.includes(id)) {
                    tab.style.display = 'none';
                } else {
                    tab.style.display = '';
                }
            }
        });
    }

    /**
     * Handle saving dashboard visibility
     */
    handleSaveDashboardVisibility() {
        console.log(`[Dashboard] === SAVING DASHBOARD VISIBILITY ===`);
        
        const dashboardList = document.getElementById('chooseDashboardList');
        if (!dashboardList) {
            console.error(`[Dashboard] Dashboard list not found`);
            return;
        }

        const checkboxes = dashboardList.querySelectorAll('input[type="checkbox"]');
        const hiddenDashboards = [];
        const visibleDashboards = [];

        console.log(`[Dashboard] Found ${checkboxes.length} checkboxes`);

        checkboxes.forEach((checkbox, index) => {
            const dashboardItem = checkbox.closest('.dashboard-item');
            const dashboardIdAttr = dashboardItem?.getAttribute('data-dashboard-id');
            
            console.log(`[Dashboard] Checkbox ${index}:`, {
                element: checkbox,
                dashboardIdAttr: dashboardIdAttr,
                checked: checkbox.checked,
                dashboardItem: dashboardItem,
                checkboxId: checkbox.id
            });
            
            if (dashboardIdAttr) {
                if (!checkbox.checked) {
                    // Store as 'main' for main dashboard, or parse ID for custom dashboards
                    if (dashboardIdAttr === 'main' || dashboardIdAttr === 'null') {
                        hiddenDashboards.push('main');
                        console.log(`[Dashboard] Added 'main' to hidden dashboards`);
                    } else {
                        // Parse dashboard ID (could be string or number)
                        const id = isNaN(dashboardIdAttr) ? dashboardIdAttr : parseInt(dashboardIdAttr);
                        hiddenDashboards.push(id);
                        console.log(`[Dashboard] Added ${id} to hidden dashboards`);
                    }
                } else {
                    // Track visible dashboards too
                    if (dashboardIdAttr === 'main' || dashboardIdAttr === 'null') {
                        visibleDashboards.push('main');
                        console.log(`[Dashboard] Added 'main' to visible dashboards`);
                    } else {
                        const id = isNaN(dashboardIdAttr) ? dashboardIdAttr : parseInt(dashboardIdAttr);
                        visibleDashboards.push(id);
                        console.log(`[Dashboard] Added ${id} to visible dashboards`);
                    }
                }
            } else {
                console.warn(`[Dashboard] No dashboard ID found for checkbox ${index}`);
            }
        });

        console.log(`[Dashboard] Hidden dashboards:`, hiddenDashboards);
        console.log(`[Dashboard] Visible dashboards:`, visibleDashboards);
        console.log(`[Dashboard] Available dashboards:`, this.availableDashboards);

        // Save to localStorage
        try {
            localStorage.setItem('dashboardHiddenDashboards', JSON.stringify(hiddenDashboards));
        } catch (e) {
            console.error('Error saving hiddenDashboards to localStorage:', e);
        }

        // Check if current dashboard is being hidden
        const currentDashboardId = this.currentDashboardId;
        const isCurrentDashboardHidden = currentDashboardId === null 
            ? hiddenDashboards.includes('main')
            : hiddenDashboards.includes(currentDashboardId);

        // Apply visibility changes to dashboard tabs
        // Handle main dashboard
        const dashboardTab = document.getElementById('dashboardTab');
        if (dashboardTab) {
            if (hiddenDashboards.includes('main')) {
                dashboardTab.style.display = 'none';
            } else {
                dashboardTab.style.display = '';
            }
        }

        // Handle custom dashboards
        console.log(`[Dashboard] Processing ${this.availableDashboards.length} available dashboards`);
        
        this.availableDashboards.forEach(dashboard => {
            const dashboardId = dashboard.id;
            console.log(`[Dashboard] Processing dashboard ${dashboardId} (${dashboard.title})`);
            
            // Skip main dashboard (already handled above)
            if (dashboardId === null || dashboardId === undefined) {
                console.log(`[Dashboard] Skipping main dashboard (id is null/undefined)`);
                return;
            }
            
            const customDashboardTab = document.querySelector(`.sub-nav-tabs [data-dashboard-id="${dashboardId}"]`);
            console.log(`[Dashboard] Tab exists for dashboard ${dashboardId}:`, !!customDashboardTab);
            
            if (customDashboardTab) {
                // Tab exists - show or hide it
                if (hiddenDashboards.includes(dashboardId)) {
                    // Hide dashboard tab
                    customDashboardTab.style.display = 'none';
                    console.log(`[Dashboard] Hidden existing tab for dashboard ${dashboardId}`, {
                        tab: customDashboardTab,
                        currentDisplay: customDashboardTab.style.display,
                        offsetParent: customDashboardTab.offsetParent,
                        isVisible: customDashboardTab.offsetParent !== null
                    });
                } else {
                    // Show dashboard tab
                    customDashboardTab.style.display = '';
                    console.log(`[Dashboard] Shown existing tab for dashboard ${dashboardId}`, {
                        tab: customDashboardTab,
                        currentDisplay: customDashboardTab.style.display,
                        offsetParent: customDashboardTab.offsetParent,
                        isVisible: customDashboardTab.offsetParent !== null,
                        innerHTML: customDashboardTab.innerHTML
                    });
                }
            } else {
                // Tab doesn't exist - create it if dashboard is checked (visible)
                const isHidden = hiddenDashboards.includes(dashboardId);
                console.log(`[Dashboard] Dashboard ${dashboardId} hidden check:`, {
                    dashboardId: dashboardId,
                    hiddenDashboards: hiddenDashboards,
                    isHidden: isHidden,
                    includes: hiddenDashboards.includes(dashboardId)
                });
                
                if (!isHidden) {
                    // Dashboard is visible but tab doesn't exist - create it
                    console.log(`[Dashboard] Creating new tab for dashboard ${dashboardId} (${dashboard.title})`);
                    this.createDashboardTab(dashboardId, dashboard.title, false).catch(err => {
                        console.error('Error creating dashboard tab:', err);
                    });
                } else {
                    console.log(`[Dashboard] Dashboard ${dashboardId} is hidden, not creating tab`);
                }
            }
        });

        // If current dashboard was hidden, switch to main dashboard or home
        if (isCurrentDashboardHidden) {
            const isMainHidden = hiddenDashboards.includes('main');
            
            if (!isMainHidden) {
                // Main dashboard is visible, switch to it
                this.currentDashboardId = null;
                const mainTab = document.getElementById('dashboardTab');
                if (mainTab) {
                    // Remove active from all tabs
                    document.querySelectorAll('.sub-nav-item').forEach(t => t.classList.remove('active'));
                    mainTab.classList.add('active');
                    // Switch to main dashboard
                    this.switchToDashboard(null);
                }
            } else {
                // Main dashboard is also hidden, switch to home
                this.currentDashboardId = null;
                const homeTab = document.getElementById('homeTab');
                if (homeTab) {
                    // Remove active from all tabs
                    document.querySelectorAll('.sub-nav-item').forEach(t => t.classList.remove('active'));
                    homeTab.classList.add('active');
                    // Show home section
                    this.showHeroSection();
                    this.updateActionsMenu('home');
                    // Update URL
                    window.history.pushState({}, '', '/index.html?tab=home');
                }
            }
        }

        console.log('Dashboard visibility saved:', { hiddenDashboards });
        
        // Debug: Check all dashboard tabs in DOM
        console.log(`[Dashboard] === FINAL TAB STATE CHECK ===`);
        const allTabs = document.querySelectorAll('.sub-nav-tabs [data-dashboard-id]');
        console.log(`[Dashboard] Found ${allTabs.length} dashboard tabs in DOM:`);
        allTabs.forEach((tab, index) => {
            const dashboardId = tab.getAttribute('data-dashboard-id');
            const tabTitle = tab.querySelector('span')?.textContent || 'No title';
            console.log(`[Dashboard] Tab ${index}: ID=${dashboardId}, Title="${tabTitle}", Display="${tab.style.display}", Visible=${tab.offsetParent !== null}`);
        });
    }

    /**
     * Open Set as Default confirmation modal
     */
    async openSetDefaultConfirmModal() {
        const modal = document.getElementById('setDefaultConfirmModal');
        if (!modal) {
            console.error('Set default confirm modal not found');
            return;
        }

        try {
            // Get current dashboard info
            const response = await fetch('/api/dashboard/info', {
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error('Failed to load dashboard info');
            }

            const dashboardInfo = await response.json();
            
            // Check if already default
            if (dashboardInfo.isDefault) {
                alert('This dashboard is already set as default.');
                return;
            }

            // Store dashboard ID for confirmation
            this.pendingDefaultDashboardId = dashboardInfo.id;

            // Show modal
            modal.style.display = 'flex';
            
            // Setup event listeners
            this.setupSetDefaultConfirmModalListeners();
        } catch (error) {
            console.error('Error opening set default confirm modal:', error);
            alert('Failed to load dashboard information. Please try again.');
        }
    }

    /**
     * Setup event listeners for set default confirm modal
     */
    setupSetDefaultConfirmModalListeners() {
        const modal = document.getElementById('setDefaultConfirmModal');
        const closeBtn = document.getElementById('setDefaultConfirmModalClose');
        const cancelBtn = document.getElementById('setDefaultConfirmCancel');
        const okBtn = document.getElementById('setDefaultConfirmOK');

        // Close modal
        const closeModal = () => {
            modal.style.display = 'none';
            this.pendingDefaultDashboardId = null;
        };

        if (closeBtn) {
            closeBtn.addEventListener('click', closeModal);
        }
        if (cancelBtn) {
            cancelBtn.addEventListener('click', closeModal);
        }

        // Click outside modal to close
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });

        // OK button handler
        if (okBtn) {
            okBtn.addEventListener('click', async () => {
                await this.confirmSetAsDefault();
            });
        }
    }

    /**
     * Confirm set as default
     */
    async confirmSetAsDefault() {
        if (!this.pendingDefaultDashboardId) {
            return;
        }

        const okBtn = document.getElementById('setDefaultConfirmOK');
        const cancelBtn = document.getElementById('setDefaultConfirmCancel');

        // Disable buttons
        if (okBtn) {
            okBtn.disabled = true;
            okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.setting') : 'Setting...';
        }
        if (cancelBtn) {
            cancelBtn.disabled = true;
        }

        try {
            const response = await fetch('/api/dashboard/set-default', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    dashboardId: this.pendingDefaultDashboardId
                })
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to set dashboard as default');
            }

            const result = await response.json();
            
            // Close modal
            const modal = document.getElementById('setDefaultConfirmModal');
            if (modal) {
                modal.style.display = 'none';
            }

            // Reload page to reflect changes
            window.location.reload();
        } catch (error) {
            console.error('Error setting dashboard as default:', error);
            alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.setDefaultFailed', { error: error.message }) : 'Failed to set dashboard as default: ' + error.message);
        } finally {
            // Re-enable buttons
            if (okBtn) {
                okBtn.disabled = false;
                okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.ok') : 'OK';
            }
            if (cancelBtn) {
                cancelBtn.disabled = false;
            }
            this.pendingDefaultDashboardId = null;
        }
    }

    /**
     * Toggle description visibility
     */
    toggleDescriptionVisibility() {
        const dashboardHeader = document.querySelector('.dashboard-header');
        if (!dashboardHeader) return;

        const descriptionElement = dashboardHeader.querySelector('.dashboard-description');
        if (!descriptionElement) return;

        // Toggle visibility
        const isCurrentlyVisible = descriptionElement.style.display !== 'none' && 
                                    !descriptionElement.classList.contains('hidden');
        
        if (isCurrentlyVisible) {
            // Hide description
            descriptionElement.style.display = 'none';
            descriptionElement.classList.add('hidden');
            this.updateDescriptionToggleButton(true); // true = isHidden
            // Save preference
            localStorage.setItem('dashboardDescriptionHidden', 'true');
        } else {
            // Show description
            descriptionElement.style.display = '';
            descriptionElement.classList.remove('hidden');
            this.updateDescriptionToggleButton(false); // false = isVisible
            // Save preference
            localStorage.setItem('dashboardDescriptionHidden', 'false');
        }
    }

    /**
     * Update the description toggle button text
     */
    updateDescriptionToggleButton(isHidden) {
        const actionsDropdown = document.getElementById('subNavActionsDropdown');
        if (!actionsDropdown) return;

        // Find the Hide/Show Description button by data-action so it works in any language
        const button = actionsDropdown.querySelector('.dashboard-actions-item[data-action="toggleDescription"]');
        if (button) {
            const span = button.querySelector('span');
            if (span && (window.I18n && window.I18n.t)) {
                span.textContent = isHidden ? window.I18n.t('dashboard.showDescription') : window.I18n.t('dashboard.hideDescription');
            } else if (span) {
                span.textContent = isHidden ? 'Show Description' : 'Hide Description';
            }
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Open New Dashboard Modal
     */
    openNewDashboardModal() {
        const modal = document.getElementById('newDashboardModal');
        if (!modal) return;

        // Reset form
        const form = document.getElementById('newDashboardForm');
        if (form) {
            form.reset();
        }

        // Show modal
        modal.style.display = 'flex';
        
        // Setup listeners if not already done
        this.setupNewDashboardModalListeners();
    }

    /**
     * Setup New Dashboard Modal Listeners
     */
    setupNewDashboardModalListeners() {
        const modal = document.getElementById('newDashboardModal');
        if (!modal) return;

        // Close button
        const closeBtn = document.getElementById('newDashboardModalClose');
        if (closeBtn && !closeBtn.dataset.listenerAdded) {
            closeBtn.dataset.listenerAdded = 'true';
            closeBtn.addEventListener('click', () => {
                modal.style.display = 'none';
            });
        }

        // Cancel button
        const cancelBtn = document.getElementById('newDashboardCancel');
        if (cancelBtn && !cancelBtn.dataset.listenerAdded) {
            cancelBtn.dataset.listenerAdded = 'true';
            cancelBtn.addEventListener('click', () => {
                modal.style.display = 'none';
            });
        }

        // Form submit
        const form = document.getElementById('newDashboardForm');
        if (form && !form.dataset.listenerAdded) {
            form.dataset.listenerAdded = 'true';
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                this.createNewDashboard();
            });
        }

        // Close on overlay click
        if (!modal.dataset.listenerAdded) {
            modal.dataset.listenerAdded = 'true';
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    modal.style.display = 'none';
                }
            });
        }
    }

    /**
     * Create New Dashboard
     */
    async createNewDashboard() {
        const nameInput = document.getElementById('newDashboardName');
        const descriptionInput = document.getElementById('newDashboardDescription');
        const setAsDefaultCheckbox = document.getElementById('newDashboardSetAsDefault');
        const okBtn = document.getElementById('newDashboardOK');
        const cancelBtn = document.getElementById('newDashboardCancel');

        if (!nameInput || !descriptionInput) {
            alert('Form fields not found');
            return;
        }

        const name = nameInput.value.trim();
        const description = descriptionInput.value.trim();
        const setAsDefault = setAsDefaultCheckbox ? setAsDefaultCheckbox.checked : false;

        if (!name || !description) {
            alert('Please fill in all required fields');
            return;
        }

        // Disable buttons
        if (okBtn) {
            okBtn.disabled = true;
            okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.creating') : 'Creating...';
        }
        if (cancelBtn) {
            cancelBtn.disabled = true;
        }

        try {
            const response = await fetch('/api/dashboard/create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    name: name,
                    description: description,
                    setAsDefault: setAsDefault
                })
            });

            const result = await response.json();

            if (response.ok && result.success) {
                // Close modal
                const modal = document.getElementById('newDashboardModal');
                if (modal) {
                    modal.style.display = 'none';
                }

                // Create tab and switch to new dashboard
                const dashboardId = result.dashboardId;
                await this.createDashboardTab(dashboardId, name);
                await this.switchToDashboard(dashboardId);
            } else {
                alert(result.error || ((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.createFailed') : 'Failed to create dashboard. Please try again.'));
                if (okBtn) {
                    okBtn.disabled = false;
                    okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.ok') : 'OK';
                }
                if (cancelBtn) {
                    cancelBtn.disabled = false;
                }
            }
        } catch (error) {
            console.error('Error creating dashboard:', error);
            alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.createError') : 'An error occurred while creating the dashboard. Please try again.');
            if (okBtn) {
                okBtn.disabled = false;
                okBtn.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('button.ok') : 'OK';
            }
            if (cancelBtn) {
                cancelBtn.disabled = false;
            }
        }
    }

    /**
     * Create Dashboard Tab
     * @param {number} dashboardId - The dashboard ID
     * @param {string} title - The dashboard title
     * @param {boolean} switchToIt - Whether to switch to this dashboard after creating tab (default: true)
     */
    async createDashboardTab(dashboardId, title, switchToIt = true) {
        console.log(`[Dashboard] === CREATING DASHBOARD TAB ===`);
        console.log(`[Dashboard] Dashboard ID: ${dashboardId}, Title: ${title}, Switch: ${switchToIt}`);
        
        const subNavTabs = document.querySelector('.sub-nav-tabs');
        if (!subNavTabs) {
            console.error(`[Dashboard] sub-nav-tabs not found`);
            return;
        }

        // Check if tab already exists - remove it first to avoid duplicates
        const existingTab = document.querySelector(`.sub-nav-tabs [data-dashboard-id="${dashboardId}"]`);
        if (existingTab) {
            console.log(`[Dashboard] Removing existing tab for dashboard ${dashboardId}`);
            existingTab.remove();
        }

        // Create new tab
        const tab = document.createElement('button');
        tab.className = 'sub-nav-item';
        tab.setAttribute('data-dashboard-id', dashboardId);
        tab.innerHTML = `
            <i class="fas fa-th-large"></i>
            <span>${this.escapeHtml(title)}</span>
            <button class="dashboard-tab-close" data-dashboard-id="${dashboardId}">
                <i class="fas fa-times"></i>
            </button>
        `;

        // Add click handler
        tab.addEventListener('click', (e) => {
            if (!e.target.closest('.dashboard-tab-close')) {
                this.switchToDashboard(dashboardId);
            }
        });

        // Add close handler
        const closeBtn = tab.querySelector('.dashboard-tab-close');
        if (closeBtn) {
            closeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.closeDashboardTab(dashboardId);
            });
        }

        // Insert after dashboard tab (main dashboard tab should always stay first)
        const dashboardTab = document.getElementById('dashboardTab');
        if (dashboardTab) {
            // Insert after dashboard tab
            subNavTabs.insertBefore(tab, dashboardTab.nextSibling);
        } else {
            // If dashboard tab doesn't exist, insert after home tab
            const homeTab = document.getElementById('homeTab');
            if (homeTab) {
                subNavTabs.insertBefore(tab, homeTab.nextSibling);
            } else {
                subNavTabs.appendChild(tab);
            }
        }

        // Switch to new tab only if requested
        if (switchToIt) {
            this.switchToDashboard(dashboardId);
        }
    }

    /**
     * Switch to Dashboard
     */
    async switchToDashboard(dashboardId) {
        // Update current dashboard ID
        this.currentDashboardId = dashboardId;

        // Get main dashboard tab first to ensure it's always visible
        const defaultDashboardTab = document.getElementById('dashboardTab');
        const homeTab = document.getElementById('homeTab');
        
        // Update tab active states - remove active from all tabs first (including home tab)
        const allTabs = document.querySelectorAll('.sub-nav-item');
        allTabs.forEach(tab => {
            tab.classList.remove('active');
        });
        // Also remove active from home tab
        if (homeTab) {
            homeTab.classList.remove('active');
        }

        // Check if main dashboard is hidden
        let isMainHidden = false;
        try {
            const saved = localStorage.getItem('dashboardHiddenDashboards');
            if (saved !== null && saved !== '') {
                const hiddenDashboards = JSON.parse(saved);
                isMainHidden = hiddenDashboards.includes('main');
            }
        } catch (e) {
            console.error('Error parsing hiddenDashboards:', e);
        }

        // Activate the correct tab based on dashboard type with smooth transition
        if (dashboardId === null) {
            // Switching to main dashboard - but check if it's hidden first
            if (isMainHidden) {
                // Main dashboard is hidden, switch to home instead
                const homeTab = document.getElementById('homeTab');
                if (homeTab) {
                    homeTab.classList.add('active');
                    this.showHeroSection();
                    this.updateActionsMenu('home');
                    window.history.pushState({}, '', '/index.html?tab=home');
                }
                return; // Don't load main dashboard content
            }
            
            // Main dashboard is visible, proceed with switching
            if (defaultDashboardTab) {
                defaultDashboardTab.classList.add('active');
                defaultDashboardTab.style.display = '';
            }
        } else {
            // Switching to custom dashboard - activate matching tab
            const customTab = document.querySelector(`.sub-nav-tabs [data-dashboard-id="${dashboardId}"]`);
            if (customTab) {
                customTab.classList.add('active');
            }
            // Don't force main dashboard tab visibility - respect hidden state
            if (defaultDashboardTab) {
                defaultDashboardTab.classList.remove('active');
                // Only show main dashboard tab if it's not hidden
                if (!isMainHidden) {
                defaultDashboardTab.style.display = '';
                } else {
                    defaultDashboardTab.style.display = 'none';
                }
                // Restore original text if it was changed
                const tabSpan = defaultDashboardTab.querySelector('span');
                if (tabSpan && !tabSpan.dataset.originalText) {
                    tabSpan.dataset.originalText = tabSpan.textContent;
                }
            }
        }

        // Load dashboard content with smooth transition
        await this.loadDashboardContent(dashboardId);
    }

    /**
     * Load Dashboard Content
     */
    async loadDashboardContent(dashboardId) {
        // Show dashboard section
        const dashboardSection = document.getElementById('dashboardSection');
        const heroSection = document.querySelector('.hero-section');
        
        if (heroSection) {
            heroSection.style.display = 'none';
        }
        if (dashboardSection) {
            dashboardSection.style.display = 'block';
        }

        // Load dashboard info - this will update the title and description
        await this.loadDashboardInfoForId(dashboardId);
        
        // Also update the tab title if it exists and we have the title from availableDashboards
        if (dashboardId !== null && this.availableDashboards) {
            const dashboard = this.availableDashboards.find(d => d.id === dashboardId);
            if (dashboard && dashboard.title) {
                const tab = document.querySelector(`.sub-nav-tabs [data-dashboard-id="${dashboardId}"]`);
                if (tab) {
                    const tabSpan = tab.querySelector('span');
                    if (tabSpan && tabSpan.textContent !== dashboard.title) {
                        console.log(`[Dashboard] Updating tab title from "${tabSpan.textContent}" to "${dashboard.title}"`);
                        tabSpan.textContent = dashboard.title;
                    }
                }
            }
        }

            // Clear existing widgets - ALWAYS clear custom widgets when switching dashboards
            // This ensures widgets from one dashboard don't appear in another
        const dashboardGrid = document.querySelector('.dashboard-grid');
        if (dashboardGrid) {
                console.log(`[Dashboard] Clearing widgets for dashboard: ${dashboardId === null ? 'main' : dashboardId}`);
                
                // Reset drag initialization flags for all widgets
                const allWidgets = dashboardGrid.querySelectorAll('[id$="Widget"]');
                allWidgets.forEach(widget => {
                    widget.removeAttribute('data-drag-initialized');
                });
                
                // Clear ALL custom widgets (they'll be reloaded for the current dashboard)
            const customWidgets = dashboardGrid.querySelectorAll('.dashboard-widget-container');
                console.log(`[Dashboard] Found ${customWidgets.length} custom widgets to clear`);
                
                customWidgets.forEach(widget => {
                    // Only remove if it's a custom widget (starts with 'widget' and has a number)
                    const widgetId = widget.id;
                    if (widgetId && widgetId.startsWith('widget') && /^\d+/.test(widgetId.replace('widget', '').replace('Widget', ''))) {
                        console.log(`[Dashboard] Removing widget: ${widgetId}`);
                        widget.remove();
                        // Also remove from widgets object
                        const widgetKey = widgetId.replace('Widget', '');
                        if (this.widgets[widgetKey]) {
                            delete this.widgets[widgetKey];
                            console.log(`[Dashboard] Removed widget from widgets object: ${widgetKey}`);
                        }
                    }
                });
            
            // Remove empty state placeholder if exists
            const emptyPlaceholder = dashboardGrid.querySelector('.dashboard-empty-placeholder');
            if (emptyPlaceholder) {
                emptyPlaceholder.remove();
            }
            
            // Handle default widgets based on dashboard type
            const defaultWidgetIds = ['objectCountsWidget', 'rolesNotAcceptedWidget', 'stakeholdershipWidget', 
                                     'savedSearchesWidget', 'changeRequestsWidget', 'teamWidget', 'pendingTasksWidget'];
            
            if (dashboardId !== null) {
                // New dashboard - hide all default widgets
                defaultWidgetIds.forEach(widgetId => {
                    const container = document.getElementById(widgetId);
                    if (container) {
                        container.style.display = 'none';
                    }
                });

                if (!this.dragDrop && window.DashboardDragDrop) {
                    this.dragDrop = new DashboardDragDrop();
                    if (window.dashboard) {
                        window.dashboard.dragDrop = this.dragDrop;
                    }
                }
                if (this.dragDrop) {
                    await this.dragDrop.loadPrefsFromServer();
                }
                
                // Load widgets for new dashboard
                await this.loadDashboardWidgets(dashboardId);
                
                // Setup widget controls and drag and drop for new dashboard
                setTimeout(() => {
                    this.setupWidgetControls();
                    
                    // Initialize drag and drop if not already initialized
                    if (!this.dragDrop && window.DashboardDragDrop) {
                        this.dragDrop = new DashboardDragDrop();
                    }
                    
                    if (this.dragDrop) {
                        this.dragDrop.setupDragAndDrop();
                    }
                }, 200);
            } else {
                // Main dashboard - show default widgets and initialize them
                console.log(`[Dashboard] Loading main dashboard widgets`);
                
                defaultWidgetIds.forEach(widgetId => {
                    const container = document.getElementById(widgetId);
                    if (container) {
                        container.style.removeProperty('display');
                        console.log(`[Dashboard] Showing default widget: ${widgetId}`);
                    }
                });
                
                // Load custom widgets for main dashboard
                console.log(`[Dashboard] Loading custom widgets for main dashboard`);
                await this.loadDashboardWidgets(null);
                
                // Always reinitialize default widgets to ensure data is fresh when returning to main dashboard
                await this.initializeWidgets(true);
            }
        }

        // Update actions menu
        this.updateActionsMenu('dashboard');
    }

    /**
     * Load Dashboard Info for Specific Dashboard ID
     */
    async loadDashboardInfoForId(dashboardId) {
        try {
            // For main dashboard (null), use default endpoint
            let url = '/api/dashboard/info';
            if (dashboardId !== null) {
                url += `?id=${dashboardId}`;
            }
            
            console.log(`[Dashboard] Loading dashboard info from: ${url}`);
            
            const response = await fetch(url, {
                credentials: 'include'
            });

            if (response.ok) {
                const dashboardInfo = await response.json();
                console.log(`[Dashboard] Dashboard info loaded:`, dashboardInfo);
                
                // Use the actual title from the response, don't default to 'Dashboard'
                const title = dashboardInfo.title || dashboardInfo.Title || 'Dashboard';
                const description = dashboardInfo.description || dashboardInfo.Description || '';
                const creatorName = dashboardInfo.creatorName || null;
                const sharedWith = dashboardInfo.sharedWith || [];
                const isPublic = dashboardInfo.isPublic || false;
                const createdBy = dashboardInfo.createdBy || null;
                
                // Store dashboard owner ID
                this.dashboardOwnerId = createdBy;
                
                console.log(`[Dashboard] Updating display with title: "${title}"`);
                await this.updateDashboardDisplay(title, description, creatorName, sharedWith, isPublic, createdBy);
            } else {
                console.error(`[Dashboard] Failed to load dashboard info: ${response.status}`);
                // Try to get title from available dashboards if API fails
                if (dashboardId !== null && this.availableDashboards) {
                    const dashboard = this.availableDashboards.find(d => d.id === dashboardId);
                    if (dashboard && dashboard.title) {
                        console.log(`[Dashboard] Using title from availableDashboards: "${dashboard.title}"`);
                        this.dashboardOwnerId = null;
                        await this.updateDashboardDisplay(dashboard.title, dashboard.description || '', null, [], false, null);
                    }
                }
            }
        } catch (error) {
            console.error('Error loading dashboard info:', error);
            // Try to get title from available dashboards if API fails
            if (dashboardId !== null && this.availableDashboards) {
                const dashboard = this.availableDashboards.find(d => d.id === dashboardId);
                if (dashboard && dashboard.title) {
                    console.log(`[Dashboard] Using title from availableDashboards after error: "${dashboard.title}"`);
                    this.dashboardOwnerId = null;
                    await this.updateDashboardDisplay(dashboard.title, dashboard.description || '', null, [], false, null);
                }
            }
        }
    }

    /**
     * Load Dashboard Widgets for Specific Dashboard ID
     * @param {number|null} dashboardId - The dashboard ID (null for main dashboard)
     */
    async loadDashboardWidgets(dashboardId) {
        try {
            // Build URL with dashboard ID
            let url = '/api/dashboard/widgets';
            if (dashboardId !== null) {
                url += `?dashboardId=${dashboardId}`;
            }
            
            const response = await fetch(url, {
                credentials: 'include'
            });

            if (!response.ok) {
                console.error('Failed to load dashboard widgets');
                return;
            }

            const customWidgets = await response.json();

            // Get dashboard grid
            const dashboardGrid = document.querySelector('.dashboard-grid');
            if (!dashboardGrid) return;

            // Remove empty state placeholder if it exists (before adding widgets)
            const emptyPlaceholder = dashboardGrid.querySelector('.dashboard-empty-placeholder');
            if (emptyPlaceholder) {
                emptyPlaceholder.remove();
            }

            // For new dashboards, clear existing custom widgets before loading new ones
            // This ensures we don't have duplicates when adding new widgets
            if (dashboardId !== null) {
                console.log(`[Dashboard] Loading widgets for dashboard ${dashboardId}. Found ${customWidgets.length} widgets from API`);
                
                const existingCustomWidgets = dashboardGrid.querySelectorAll('.dashboard-widget-container');
                console.log(`[Dashboard] Found ${existingCustomWidgets.length} existing custom widgets in DOM`);
                
                // Create a set of widget IDs that should exist (from API)
                const newWidgetIds = new Set(customWidgets.map(w => `widget${w.id}Widget`));
                console.log(`[Dashboard] Widget IDs that should exist:`, Array.from(newWidgetIds));
                
                existingCustomWidgets.forEach(widget => {
                    const widgetId = widget.id;
                    // Only remove if it's not in the new list of widgets
                    if (!newWidgetIds.has(widgetId)) {
                        console.log(`[Dashboard] Removing widget not in API list: ${widgetId}`);
                        widget.remove();
                        // Also remove from widgets object
                        const widgetKey = widgetId.replace('Widget', '');
                        if (this.widgets[widgetKey]) {
                            delete this.widgets[widgetKey];
                            console.log(`[Dashboard] Removed widget from widgets object: ${widgetKey}`);
                        }
                    } else {
                        console.log(`[Dashboard] Keeping widget (in API list): ${widgetId}`);
                    }
                });
            }

            // Render each custom widget
            if (customWidgets.length > 0) {
                console.log(`[Dashboard] Rendering ${customWidgets.length} custom widgets for dashboard ${dashboardId === null ? 'main' : dashboardId}`);
                for (const widgetData of customWidgets) {
                    console.log(`[Dashboard] Rendering widget:`, widgetData);
                    await this.renderCustomWidget(widgetData);
                }
                console.log(`[Dashboard] Finished rendering all widgets`);
            } else if (dashboardId !== null) {
                // For new dashboards (not main), show empty state if no widgets
                console.log(`[Dashboard] No widgets found, showing empty state`);
                this.showEmptyDashboardState();
            } else {
                console.log(`[Dashboard] Main dashboard - no custom widgets to render`);
            }
        } catch (error) {
            console.error('Error loading dashboard widgets:', error);
        }
    }

    /**
     * Show Empty Dashboard State
     */
    showEmptyDashboardState() {
        const dashboardGrid = document.querySelector('.dashboard-grid');
        if (!dashboardGrid) return;

        // Remove existing empty state
        const existingEmpty = dashboardGrid.querySelector('.dashboard-empty-placeholder');
        if (existingEmpty) {
            existingEmpty.remove();
        }

        // Check if user is the owner of the dashboard
        const isMainDashboard = this.currentDashboardId === null;
        const currentUserId = this.currentUser ? (this.currentUser.id || this.currentUser.ID) : null;
        const isOwner = isMainDashboard || (currentUserId && this.dashboardOwnerId && 
            parseInt(currentUserId) === parseInt(this.dashboardOwnerId));

        // Only show empty state placeholder if user is the owner
        if (!isOwner) {
            return;
        }

        // Create empty state placeholder
        const emptyPlaceholder = document.createElement('div');
        emptyPlaceholder.className = 'dashboard-empty-placeholder';
        const clickToAddText = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.clickToAddWidget') : 'Click to add a new widget';
        emptyPlaceholder.innerHTML = `
            <div class="empty-placeholder-content">
                <i class="fas fa-th-large"></i>
                <p>${clickToAddText}</p>
            </div>
        `;

        // Add click handler to open new widget modal
        emptyPlaceholder.addEventListener('click', () => {
            if (window.NewWidgetModal) {
                window.NewWidgetModal.open(this.currentDashboardId);
            }
        });

        dashboardGrid.appendChild(emptyPlaceholder);
    }

    /**
     * Check if export libraries are loaded
     */
    checkExportLibraries() {
        console.log('[Dashboard] Checking export libraries...');
        console.log('[Dashboard] html2canvas available:', typeof html2canvas !== 'undefined');
        console.log('[Dashboard] jsPDF available:', typeof window.jspdf !== 'undefined');
        
        if (typeof html2canvas === 'undefined') {
            console.warn('[Dashboard] html2canvas library not loaded. Export functionality may not work.');
        }
        if (typeof window.jspdf === 'undefined') {
            console.warn('[Dashboard] jsPDF library not loaded. PDF export may not work.');
        }
    }

    /**
     * Export Dashboard
     * @param {string} format - Export format: 'pdf', 'png', or 'jpeg'
     */
    async exportDashboard(format) {
        console.log('[Dashboard] exportDashboard called with format:', format);
        
        const dashboardSection = document.getElementById('dashboardSection');
        if (!dashboardSection) {
            console.error('[Dashboard] Dashboard section not found');
            alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.sectionNotFound') : 'Dashboard section not found');
            return;
        }

        // Check if dashboard section is visible
        if (dashboardSection.style.display === 'none') {
            console.error('[Dashboard] Dashboard section is hidden');
            alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.notVisible') : 'Dashboard is not visible. Please open a dashboard first.');
            return;
        }

        try {
            // Get dashboard title for filename
            const dashboardTitle = document.querySelector('.dashboard-header h1')?.textContent || 'Dashboard';
            const sanitizedTitle = dashboardTitle.replace(/[^a-z0-9]/gi, '_').toLowerCase();
            const filename = `${sanitizedTitle}_${new Date().toISOString().split('T')[0]}`;

            console.log('[Dashboard] Starting export, filename:', filename);

            // Show loading indicator
            const loadingMsg = document.createElement('div');
            loadingMsg.style.cssText = 'position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); background: rgba(0,0,0,0.8); color: white; padding: 20px; border-radius: 8px; z-index: 10000;';
            loadingMsg.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.exportingAs', { format: format.toUpperCase() }) : `Exporting dashboard as ${format.toUpperCase()}...`;
            document.body.appendChild(loadingMsg);

            if (format === 'pdf') {
                console.log('[Dashboard] Exporting as PDF...');
                await this.exportAsPDF(dashboardSection, filename);
            } else if (format === 'png' || format === 'jpeg') {
                console.log('[Dashboard] Exporting as image:', format);
                await this.exportAsImage(dashboardSection, filename, format);
            } else {
                throw new Error(`Unsupported export format: ${format}`);
            }

            console.log('[Dashboard] Export completed successfully');
            // Remove loading indicator
            loadingMsg.remove();
        } catch (error) {
            console.error('[Dashboard] Error exporting dashboard:', error);
            console.error('[Dashboard] Error stack:', error.stack);
            alert((window.I18n && window.I18n.t) ? window.I18n.t('dashboard.exportFailed', { format: format.toUpperCase(), error: error.message }) : `Failed to export dashboard as ${format.toUpperCase()}. Error: ${error.message}`);
            // Remove loading indicator if still present
            const loadingMsg = document.querySelector('div[style*="z-index: 10000"]');
            if (loadingMsg) {
                loadingMsg.remove();
            }
        }
    }

    /**
     * Export Dashboard as PDF
     */
    async exportAsPDF(element, filename) {
        console.log('[Dashboard] exportAsPDF called');
        
        // Check if libraries are loaded
        if (typeof html2canvas === 'undefined') {
            console.error('[Dashboard] html2canvas is not defined');
            alert('html2canvas library not loaded. Please refresh the page.');
            return;
        }
        
        if (typeof window.jspdf === 'undefined') {
            console.error('[Dashboard] jsPDF is not defined');
            alert('jsPDF library not loaded. Please refresh the page.');
            return;
        }

        console.log('[Dashboard] Libraries loaded, capturing canvas...');
        
        try {
            // Capture the dashboard as canvas
            const canvas = await html2canvas(element, {
                scale: 2,
                useCORS: true,
                logging: true,
                backgroundColor: '#ffffff',
                allowTaint: true
            });

            console.log('[Dashboard] Canvas captured, dimensions:', canvas.width, 'x', canvas.height);

            const imgData = canvas.toDataURL('image/png');
            console.log('[Dashboard] Image data URL created, length:', imgData.length);
            
            const { jsPDF } = window.jspdf;
            const pdf = new jsPDF('p', 'mm', 'a4');
            
            const pdfWidth = pdf.internal.pageSize.getWidth();
            const pdfHeight = pdf.internal.pageSize.getHeight();
            const imgWidth = canvas.width;
            const imgHeight = canvas.height;
            const ratio = Math.min(pdfWidth / imgWidth, pdfHeight / imgHeight);
            const imgScaledWidth = imgWidth * ratio;
            const imgScaledHeight = imgHeight * ratio;
            
            // Center the image
            const xOffset = (pdfWidth - imgScaledWidth) / 2;
            const yOffset = (pdfHeight - imgScaledHeight) / 2;

            console.log('[Dashboard] Adding image to PDF...');
            pdf.addImage(imgData, 'PNG', xOffset, yOffset, imgScaledWidth, imgScaledHeight);
            console.log('[Dashboard] Saving PDF...');
            pdf.save(`${filename}.pdf`);
            console.log('[Dashboard] PDF saved successfully');
        } catch (error) {
            console.error('[Dashboard] Error in exportAsPDF:', error);
            throw error;
        }
    }

    /**
     * Export Dashboard as Image (PNG or JPEG)
     */
    async exportAsImage(element, filename, format) {
        console.log('[Dashboard] exportAsImage called with format:', format);
        
        if (typeof html2canvas === 'undefined') {
            console.error('[Dashboard] html2canvas is not defined');
            alert('html2canvas library not loaded. Please refresh the page.');
            return;
        }

        console.log('[Dashboard] html2canvas loaded, capturing canvas...');

        try {
            // Capture the dashboard as canvas
            const canvas = await html2canvas(element, {
                scale: 2,
                useCORS: true,
                logging: true,
                backgroundColor: '#ffffff',
                allowTaint: true
            });

            console.log('[Dashboard] Canvas captured, dimensions:', canvas.width, 'x', canvas.height);

            // Convert to desired format and download
            const mimeType = format === 'png' ? 'image/png' : 'image/jpeg';
            const extension = format === 'png' ? 'png' : 'jpg';
            const dataUrl = canvas.toDataURL(mimeType, 1.0);

            console.log('[Dashboard] Image data URL created, length:', dataUrl.length);

            // Create download link
            const link = document.createElement('a');
            link.download = `${filename}.${extension}`;
            link.href = dataUrl;
            document.body.appendChild(link);
            console.log('[Dashboard] Triggering download...');
            link.click();
            document.body.removeChild(link);
            console.log('[Dashboard] Image export completed successfully');
        } catch (error) {
            console.error('[Dashboard] Error in exportAsImage:', error);
            throw error;
        }
    }

    /**
     * Close Dashboard Tab
     */
    closeDashboardTab(dashboardId) {
        // Find and remove tab
        const tab = document.querySelector(`.sub-nav-tabs [data-dashboard-id="${dashboardId}"]`);
        if (tab) {
            tab.remove();
        }

        // If this was the current dashboard, switch to default
        if (this.currentDashboardId == dashboardId) {
            this.currentDashboardId = null;
            // Switch to default dashboard tab
            const defaultTab = document.getElementById('dashboardTab');
            if (defaultTab) {
                defaultTab.click();
            } else {
                // Switch to home if no default dashboard
                const homeTab = document.getElementById('homeTab');
                if (homeTab) {
                    homeTab.click();
                }
            }
        }
    }
}

// Initialize dashboard when DOM is ready
document.addEventListener('DOMContentLoaded', async () => {
    // Check if Chart.js is loaded
    if (typeof Chart === 'undefined') {
        console.error('Chart.js is not loaded. Dashboard widgets will not work properly.');
        return;
    }

    // Create and initialize dashboard
    const dashboard = new Dashboard();
    await dashboard.init();
    
    // Make dashboard available globally for debugging
    window.dashboard = dashboard;
});

