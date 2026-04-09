/**
 * Notification Panel Component
 * Displays workflow notifications in a dropdown panel
 */

class NotificationPanel {
    constructor() {
        this.panel = null;
        this.notificationButton = null;
        this.isInitialized = false;
        this.isAuthenticated = false;
        this.refreshInterval = null;
        window.notificationPanel = this;
    }

    /**
     * Initialize the Notification Panel component
     */
    async initialize() {
        if (this.isInitialized) return;

        // Check if user is authenticated
        this.isAuthenticated = await this.checkAuthentication();

        if (!this.isAuthenticated) {
            const bell = document.getElementById('notificationToggle');
            if (bell) {
                bell.style.display = 'none';
                bell.style.visibility = 'hidden';
            }
            return;
        }

        // Wait for header to be ready
        await this.waitForHeader();

        // Get notification button from header
        this.notificationButton = document.getElementById('notificationToggle');

        if (!this.notificationButton) {
            console.error('Notification button not found in header');
            return;
        }

        // Create panel structure if it doesn't exist
        if (!this.panel) {
            this.createPanel();
        }

        // Setup event listeners
        this.setupEventListeners();

        this.isInitialized = true;

        // Load initial notifications and start auto-refresh
        await this.updateNotificationCount();
        const activeTab = this.panel.querySelector('.notification-tab.active');
        const initialCategory = activeTab ? activeTab.getAttribute('data-category') : 'catalog';
        this.updateFooterButtons(initialCategory);
        await this.loadNotifications(initialCategory);
        this.startAutoRefresh();
    }

    /**
     * Check if user is authenticated
     */
    async checkAuthentication() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });
            if (!response.ok) {
                return false;
            }
            const me = await response.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            return me.authenticated === true && !role.includes('guest');
        } catch (error) {
            return false;
        }
    }

    /**
     * Wait for header to be injected
     */
    waitForHeader() {
        return new Promise((resolve) => {
            if (document.getElementById('notificationToggle')) {
                resolve();
                return;
            }

            window.addEventListener('headerReady', () => {
                resolve();
            }, { once: true });

            // Timeout after 5 seconds
            setTimeout(resolve, 5000);
        });
    }

    /**
     * Create the notification panel
     */
    createPanel() {
        // Check if panel already exists
        const existingPanel = document.getElementById('notificationPanel');
        if (existingPanel) {
            this.panel = existingPanel;
            return;
        }

        const panel = document.createElement('div');
        panel.id = 'notificationPanel';
        panel.className = 'notification-panel';
        const t = (key, fallback) => (window.I18n && window.I18n.t(key)) || fallback;
        panel.innerHTML = `
            <div class="notification-panel-header">
                <h3><i class="fas fa-bell"></i> ${t('notification.panel.title', 'NOTIFICATIONS')}</h3>
                <button class="notification-panel-close" id="closeNotificationPanelBtn">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="notification-tabs">
                <button class="notification-tab active" data-category="catalog">
                    ${t('notification.panel.tabs.catalog', 'Catalog')} <span class="notification-tab-badge" id="catalogBadge">0</span>
                </button>
                <button class="notification-tab" data-category="roles">
                    ${t('notification.panel.tabs.roles', 'Roles')} <span class="notification-tab-badge" id="rolesBadge">0</span>
                </button>
                <button class="notification-tab" data-category="workflow">
                    ${t('notification.panel.tabs.workflow', 'Workflow')} <span class="notification-tab-badge" id="workflowBadge">0</span>
                </button>
                <button class="notification-tab" data-category="bulk_upload">
                    ${t('notification.panel.tabs.bulkUpload', 'Bulk Upload')} <span class="notification-tab-badge" id="bulkUploadBadge">0</span>
                </button>
            </div>
            <div class="notification-content">
                <ul class="notification-list" id="notificationList">
                    <li class="notification-empty">${t('notification.panel.loading', 'Loading notifications...')}</li>
                </ul>
            </div>
            <div class="notification-footer" id="notificationFooter">
                <button class="notification-clear-all" id="clearAllNotificationsBtn">${t('notification.panel.clearAll', 'Clear All')}</button>
            </div>
        `;

        document.body.appendChild(panel);
        this.panel = panel;
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Toggle panel on button click
        if (this.notificationButton) {
            // Remove any existing listeners to avoid duplicates
            const newButton = this.notificationButton.cloneNode(true);
            this.notificationButton.parentNode.replaceChild(newButton, this.notificationButton);
            this.notificationButton = newButton;

            this.notificationButton.addEventListener('click', (e) => {
                e.stopPropagation();
                e.preventDefault();
                this.togglePanel();
            });
        } else {
            console.error('Notification button not found when setting up event listeners');
        }

        // Close panel button
        const closeBtn = document.getElementById('closeNotificationPanelBtn');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                this.closePanel();
            });
        }

        // Tab switching
        const tabs = this.panel.querySelectorAll('.notification-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const category = tab.getAttribute('data-category');
                this.switchTab(category);
            });
        });

        // Clear all button
        const clearAllBtn = document.getElementById('clearAllNotificationsBtn');
        if (clearAllBtn) {
            clearAllBtn.addEventListener('click', () => {
                this.clearAllNotifications();
            });
        }

        // Setup role-specific buttons (will be shown/hidden based on active tab)
        this.setupRoleNotificationButtons();

        // Close panel when clicking outside
        document.addEventListener('click', (e) => {
            if (this.panel && this.panel.classList.contains('open')) {
                if (!this.panel.contains(e.target) &&
                    !this.notificationButton.contains(e.target)) {
                    this.closePanel();
                }
            }
        });

        window.addEventListener('resize', () => {
            if (this.panel && this.panel.classList.contains('open') && this.notificationButton) {
                this.positionPanelUnderTrigger(this.panel, this.notificationButton);
            }
        });
    }

    /**
     * Position panel under its trigger button (works in LTR and RTL).
     * On narrow screens, center in the viewport (same clipping issue as segments panel).
     */
    positionPanelUnderTrigger(panel, trigger) {
        if (!panel || !trigger) return;
        const isMobile = typeof window.matchMedia === 'function'
            ? window.matchMedia('(max-width: 768px)').matches
            : window.innerWidth <= 768;

        if (isMobile) {
            panel.style.top = '';
            panel.style.left = '';
            panel.style.right = '';
            panel.style.bottom = '';
            panel.style.width = '';
            panel.style.maxHeight = '';
            panel.style.transform = '';
            panel.classList.add('panel-mobile-centered');
            return;
        }

        panel.classList.remove('panel-mobile-centered');
        const rect = trigger.getBoundingClientRect();
        const gap = 8;
        panel.style.top = (rect.bottom + gap) + 'px';
        const isRtl = document.documentElement.getAttribute('dir') === 'rtl';
        if (isRtl) {
            panel.style.left = rect.left + 'px';
            panel.style.right = 'auto';
        } else {
            panel.style.right = (window.innerWidth - rect.right) + 'px';
            panel.style.left = 'auto';
        }
    }

    /**
     * Close other header panels (segments, profile) so only one is open
     */
    closeOtherHeaderPanels() {
        if (window.globalSegmentsCubePanel && typeof window.globalSegmentsCubePanel.closePanel === 'function') {
            window.globalSegmentsCubePanel.closePanel();
        }
        if (window.globalLockUI && typeof window.globalLockUI.closePanel === 'function') {
            window.globalLockUI.closePanel();
        }
        const profileDropdown = document.getElementById('profileDropdown');
        if (profileDropdown) profileDropdown.classList.remove('show');
        const myItemsDd = document.getElementById('myItemsDropdown');
        if (myItemsDd) { myItemsDd.classList.remove('show'); myItemsDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active')); }
        const createDd = document.getElementById('createDropdown');
        if (createDd) { createDd.classList.remove('show'); createDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active')); }
    }

    /**
     * Toggle panel visibility
     */
    togglePanel() {
        if (!this.panel) {
            // Try to create panel if it doesn't exist
            this.createPanel();
            if (!this.panel) {
                return;
            }
        }

        const isOpen = this.panel.classList.contains('open');
        if (isOpen) {
            this.closePanel();
        } else {
            this.closeOtherHeaderPanels();
            this.positionPanelUnderTrigger(this.panel, this.notificationButton);
            this.panel.classList.add('open');
            // Load notifications when opening
            const activeTab = this.panel.querySelector('.notification-tab.active');
            if (activeTab) {
                const category = activeTab.getAttribute('data-category');
                this.loadNotifications(category);
            }
        }
    }

    /**
     * Close the panel
     */
    closePanel() {
        if (this.panel) {
            this.panel.classList.remove('open');
            this.panel.classList.remove('panel-mobile-centered');
        }
    }

    /**
     * Switch to a different tab
     */
    switchTab(category) {
        const tabs = this.panel.querySelectorAll('.notification-tab');
        tabs.forEach(tab => {
            if (tab.getAttribute('data-category') === category) {
                tab.classList.add('active');
            } else {
                tab.classList.remove('active');
            }
        });

        // Update footer buttons based on category
        this.updateFooterButtons(category);

        this.loadNotifications(category);
    }

    /**
     * Update footer buttons based on active category
     */
    updateFooterButtons(category) {
        const footer = document.getElementById('notificationFooter');
        if (!footer) return;

        const t = (key, fallback) => (window.I18n && window.I18n.t(key)) || fallback;
        if (category === 'roles') {
            footer.innerHTML = `
                <button class="notification-all-roles" id="allRolesBtn">${t('notification.panel.allRoles', 'All Roles')}</button>
                <button class="notification-accept-clear-all" id="acceptAndClearAllBtn">${t('notification.panel.acceptAndClearAll', 'Accept and Clear All')}</button>
            `;

            // Setup event handlers for role buttons
            const allRolesBtn = document.getElementById('allRolesBtn');
            if (allRolesBtn) {
                allRolesBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.navigateToAllRoles();
                });
            }

            const acceptBtn = document.getElementById('acceptAndClearAllBtn');
            if (acceptBtn) {
                acceptBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.acceptAndClearAllRoles();
                });
            }
        } else if (category === 'bulk_upload') {
            footer.innerHTML = `
                <button class="notification-all-roles" id="allJobsBtn">${t('notification.panel.allJobs', 'All Jobs')}</button>
                <button class="notification-clear-all" id="clearAllNotificationsBtn">${t('notification.panel.clearAll', 'Clear All')}</button>
            `;

            const allJobsBtn = document.getElementById('allJobsBtn');
            if (allJobsBtn) {
                allJobsBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.navigateToMyJobs();
                });
            }

            const clearAllBtn = document.getElementById('clearAllNotificationsBtn');
            if (clearAllBtn) {
                clearAllBtn.addEventListener('click', () => {
                    this.clearAllNotifications();
                });
            }
        } else {
            footer.innerHTML = `
                <button class="notification-clear-all" id="clearAllNotificationsBtn">${t('notification.panel.clearAll', 'Clear All')}</button>
            `;

            // Re-setup clear all button
            const clearAllBtn = document.getElementById('clearAllNotificationsBtn');
            if (clearAllBtn) {
                clearAllBtn.addEventListener('click', () => {
                    this.clearAllNotifications();
                });
            }
        }
    }

    /**
     * Setup role notification buttons (called once during initialization)
     */
    setupRoleNotificationButtons() {
        // Buttons will be created dynamically in updateFooterButtons
    }

    /**
     * Navigate to All Roles (Responsibilities page)
     */
    async navigateToAllRoles() {
        try {
            // Get current user ID
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });

            if (response.ok) {
                const userData = await response.json();
                const userId = userData.id || userData.ID;
                if (userId) {
                    // Use the correct URL format: /view/people/{userId}?tab=responsibilities
                    window.location.href = `/view/people/${userId}?tab=responsibilities`;
                } else {
                    console.error('Could not get user ID');
                }
            } else {
                console.error('Failed to get user data');
            }
        } catch (error) {
            console.error('Error navigating to all roles:', error);
        }
    }

    /**
     * Navigate to My Jobs (Activity Stream -> My Jobs)
     */
    async navigateToMyJobs() {
        try {
            // Get current user ID
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });

            if (response.ok) {
                const userData = await response.json();
                const userId = userData.id || userData.ID;
                if (userId) {
                    // Navigate to people page with Activity Stream tab and My Jobs subtab
                    window.location.href = `/view/people/${userId}?tab=activity&subtab=my-jobs`;
                } else {
                    console.error('Could not get user ID');
                }
            } else {
                console.error('Failed to get user data');
            }
        } catch (error) {
            console.error('Error navigating to my jobs:', error);
        }
    }

    /**
     * Accept all roles and clear notifications
     */
    async acceptAndClearAllRoles() {
        if (!confirm('Are you sure you want to accept all roles and clear all role notifications?')) {
            return;
        }

        try {
            const response = await fetch('/api/role-notifications/accept-all', {
                method: 'PUT',
                credentials: 'include'
            });

            if (response.ok) {
                const result = await response.json();
                console.log('Accepted roles:', result);

                // Reload notifications and update count
                await this.loadNotifications('roles');
                await this.updateNotificationCount();
            } else {
                const errorData = await response.json().catch(() => ({}));
                alert(errorData.error || 'Failed to accept roles');
            }
        } catch (error) {
            console.error('Error accepting roles:', error);
            alert('Error accepting roles. Please try again.');
        }
    }

    /**
     * Update notification count badge
     */
    async updateNotificationCount() {
        // Check authentication before making request
        if (!this.isAuthenticated) {
            return;
        }

        try {
            // Add timeout to fetch request
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000); // 10 second timeout

            let response;
            try {
                response = await fetch('/api/notifications/unread-count', {
                    method: 'GET',
                    credentials: 'include',
                    signal: controller.signal
                });
                clearTimeout(timeoutId);
            } catch (fetchError) {
                clearTimeout(timeoutId);
                // Check if it's an abort error (timeout) or network error
                if (fetchError.name === 'AbortError' || fetchError.name === 'TimeoutError') {
                    // Timeout - silently fail for background updates
                    return;
                }
                // Re-throw other network errors to be handled by outer catch
                throw fetchError;
            }

            if (!response.ok) {
                // Handle 401 (unauthorized) - user is no longer authenticated
                if (response.status === 401) {
                    this.isAuthenticated = false;
                    this.stopAutoRefresh();
                    return;
                }

                let errorMessage = `Failed to fetch notification counts (HTTP ${response.status})`;
                try {
                    const errorData = await response.json();
                    if (errorData.error) {
                        errorMessage = `${errorData.error} (HTTP ${response.status})`;
                    }
                } catch (e) {
                    // If response is not JSON, use status text
                    errorMessage = `Failed to fetch notification counts: ${response.statusText || response.status}`;
                }
                throw new Error(errorMessage);
            }

            const counts = await response.json();

            // Update main badge
            const mainBadge = document.getElementById('notificationBadge');
            if (mainBadge) {
                const total = (counts.workflow || 0) + (counts.catalog || 0) +
                    (counts.roles || 0) + (counts.bulk_upload || 0);
                if (total > 0) {
                    mainBadge.textContent = total;
                    mainBadge.style.display = 'flex';
                } else {
                    mainBadge.textContent = '';
                    mainBadge.style.display = 'none';
                }
            }

            // Update tab badges
            const catalogBadge = document.getElementById('catalogBadge');
            const rolesBadge = document.getElementById('rolesBadge');
            const workflowBadge = document.getElementById('workflowBadge');
            const bulkUploadBadge = document.getElementById('bulkUploadBadge');

            if (catalogBadge) {
                catalogBadge.textContent = counts.catalog > 0 ? counts.catalog : '';
            }
            if (rolesBadge) {
                rolesBadge.textContent = counts.roles > 0 ? counts.roles : '';
            }
            if (workflowBadge) {
                workflowBadge.textContent = counts.workflow > 0 ? counts.workflow : '';
            }
            if (bulkUploadBadge) {
                bulkUploadBadge.textContent = counts.bulk_upload > 0 ? counts.bulk_upload : '';
            }

        } catch (error) {
            // Handle network errors (TypeError from fetch) vs HTTP errors
            // Network errors can have various messages: "Failed to fetch", "NetworkError", etc.
            const isNetworkError = error instanceof TypeError ||
                error.name === 'TypeError' ||
                error.name === 'NetworkError' ||
                (error.message && (
                    error.message.includes('Failed to fetch') ||
                    error.message.includes('NetworkError') ||
                    error.message.includes('Network request failed') ||
                    error.message.includes('Load failed')
                ));

            // Handle abort/timeout errors
            const isAbortError = error.name === 'AbortError' ||
                error.name === 'TimeoutError' ||
                (error.message && error.message.includes('aborted'));

            if (isNetworkError || isAbortError) {
                // Network/Timeout error - likely CORS, server down, or connection issue
                // Silently fail for background updates to avoid console spam
                // Only log in development mode or if explicitly debugging
                if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
                    console.warn('Network error fetching notification count (server may be down):', error.message || error.name);
                }
                return;
            }

            // Log other errors (HTTP errors, etc.) but only if they're not expected
            // Don't log 401 (unauthorized) as errors since user might not be logged in
            if (error.message && !error.message.includes('401') && !error.message.includes('not authenticated')) {
                console.error('Failed to update notification count:', error);
            }
            // Silently fail - don't show error to user for background updates
        }
    }

    /**
     * Load notifications for a category
     */
    async loadNotifications(category) {
        // Check authentication before making request
        const nt = (key, fallback) => (window.I18n && window.I18n.t(key)) || fallback;
        if (!this.isAuthenticated) {
            const listContainer = document.getElementById('notificationList');
            if (listContainer) {
                listContainer.innerHTML = '<li class="notification-empty">' + nt('notification.panel.pleaseLogIn', 'Please log in to view notifications') + '</li>';
            }
            return;
        }

        const listContainer = document.getElementById('notificationList');
        if (!listContainer) return;

        listContainer.innerHTML = '<li class="notification-empty">' + nt('notification.panel.loading', 'Loading notifications...') + '</li>';

        try {
            // Add timeout to fetch request
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000); // 10 second timeout

            let response;
            try {
                // Load all notifications (both read and unread)
                response = await fetch(`/api/notifications?category=${category}`, {
                    method: 'GET',
                    credentials: 'include',
                    signal: controller.signal
                });
                clearTimeout(timeoutId);
            } catch (fetchError) {
                clearTimeout(timeoutId);
                // Check if it's an abort error (timeout) or network error
                if (fetchError.name === 'AbortError' || fetchError.name === 'TimeoutError') {
                    // Timeout - show user-friendly message
                    listContainer.innerHTML = '<li class="notification-empty">' + (window.I18n?.t('notification.panel.requestTimedOut') || 'Request timed out. Please try again.') + '</li>';
                    return;
                }
                // Re-throw other network errors to be handled by outer catch
                throw fetchError;
            }

            if (!response.ok) {
                // Handle 401 (unauthorized) - user is no longer authenticated
                if (response.status === 401) {
                    this.isAuthenticated = false;
                    this.stopAutoRefresh();
                    listContainer.innerHTML = '<li class="notification-empty">' + (window.I18n?.t('notification.panel.pleaseLogIn') || 'Please log in to view notifications') + '</li>';
                    return;
                }

                let errorMessage = (window.I18n && window.I18n.t('notification.panel.failedToFetch', { status: response.status })) || `Failed to fetch notifications (HTTP ${response.status})`;
                try {
                    const errorData = await response.json();
                    if (errorData.error) {
                        errorMessage = `${errorData.error} (HTTP ${response.status})`;
                    }
                } catch (e) {
                    // If response is not JSON, use status text
                    errorMessage = (window.I18n && window.I18n.t('notification.panel.failedToFetchMessage', { statusText: response.statusText || response.status })) || `Failed to fetch notifications: ${response.statusText || response.status}`;
                }
                throw new Error(errorMessage);
            }

            const notifications = await response.json();

            if (notifications.length === 0) {
                listContainer.innerHTML = '<li class="notification-empty">' + (window.I18n?.t('notification.noNotifications') || 'No notifications') + '</li>';
                return;
            }

            listContainer.innerHTML = '';
            notifications.forEach(notification => {
                const item = this.createNotificationItem(notification);
                listContainer.appendChild(item);
            });

        } catch (error) {
            // Show user-friendly error message
            const nt = (key, fallback) => (window.I18n && window.I18n.t(key)) || fallback;
            let errorMessage = nt('notification.panel.errorLoading', 'Error loading notifications');

            // Handle network errors
            const isNetworkError = error instanceof TypeError ||
                error.name === 'TypeError' ||
                error.name === 'NetworkError' ||
                (error.message && (
                    error.message.includes('Failed to fetch') ||
                    error.message.includes('NetworkError') ||
                    error.message.includes('Network request failed') ||
                    error.message.includes('Load failed')
                ));

            // Handle abort/timeout errors
            const isAbortError = error.name === 'AbortError' ||
                error.name === 'TimeoutError' ||
                (error.message && error.message.includes('aborted'));

            if (isNetworkError) {
                errorMessage = nt('notification.panel.unableToConnect', 'Unable to connect to server. Please check your connection.');
            } else if (isAbortError) {
                errorMessage = nt('notification.panel.requestTimedOut', 'Request timed out. Please try again.');
            } else if (error.message) {
                // Check if it's an authentication error
                if (error.message.includes('401') || error.message.includes('not authenticated')) {
                    errorMessage = nt('notification.panel.pleaseLogIn', 'Please log in to view notifications');
                } else if (error.message.includes('403')) {
                    errorMessage = nt('notification.panel.noPermission', 'You do not have permission to view notifications');
                } else if (error.message.includes('500')) {
                    errorMessage = nt('notification.panel.serverError', 'Server error. Please try again later.');
                } else {
                    errorMessage = error.message;
                }
            }

            // Only log errors that aren't expected (network errors, timeouts, 401s are expected)
            if (!isNetworkError && !isAbortError && error.message && !error.message.includes('401') && !error.message.includes('not authenticated')) {
                console.error('Failed to load notifications:', error);
            }

            listContainer.innerHTML = `<li class="notification-empty">${this.escapeHtml(errorMessage)}</li>`;
        }
    }

    /**
     * Create a notification list item
     */
    createNotificationItem(notification) {
        const li = document.createElement('li');
        // Add 'read' class for read notifications to apply styling
        li.className = 'notification-item' + (notification.read ? ' read' : ' unread');
        li.setAttribute('data-notification-id', notification.id);

        // Add background color for read notifications
        if (notification.read) {
            li.style.backgroundColor = '#f5f5f5';
            li.style.opacity = '0.8';
        }

        // Format timestamp - use full format for bulk upload, relative for others
        const time = notification.category === 'bulk_upload' 
            ? this.formatTimestampFull(notification.createdAt)
            : this.formatTimestamp(notification.createdAt);

        // Handle role notifications specially
        if (notification.category === 'roles') {
            return this.createRoleNotificationItem(notification, time);
        } else if (notification.category === 'bulk_upload') {
            return this.createBulkUploadNotificationItem(notification, time);
        }

        // Process message and add Change Request link if available
        let message = notification.message || '';

        // If changeRequestId exists, ensure it's linked in the message
        if (notification.changeRequestId) {
            const crId = notification.changeRequestId;
            const crLink = `<a href="/view/change-request/change-request-view.html?id=${crId}" onclick="event.stopPropagation();" style="color: #248567; font-weight: 500; text-decoration: none;">Change Request #${crId}</a>`;

            // Check if message already mentions change request
            const changeRequestMatch = message.match(/change request (\d+)/i);
            if (changeRequestMatch) {
                message = message.replace(/change request \d+/i, crLink);
            } else {
                // Add change request link at the end of message
                message = message + (message.trim().endsWith('.') ? ' ' : '. ') + `View ${crLink}`;
            }
        }

        // Build link URL with taskId if available
        const nt = (key, fallback) => (window.I18n && window.I18n.t(key)) || fallback;
        let linkUrl = '';
        let linkText = '';
        if (notification.changeRequestId) {
            if (notification.workflowTaskId) {
                linkUrl = `/view/change-request/change-request-view.html?id=${notification.changeRequestId}&taskId=${notification.workflowTaskId}`;
                linkText = (window.I18n && window.I18n.t('notification.panel.openTask', { id: notification.workflowTaskId })) || `Open Task #${notification.workflowTaskId}`;
            } else {
                linkUrl = `/view/change-request/change-request-view.html?id=${notification.changeRequestId}`;
                linkText = (window.I18n && window.I18n.t('notification.panel.openChangeRequest', { id: notification.changeRequestId })) || `Open Change Request #${notification.changeRequestId}`;
            }
        }

        const defaultTitle = nt('notification.panel.defaultTitle', 'Notification');
        li.innerHTML = `
            <div class="notification-item-header">
                <h4 class="notification-title">${this.escapeHtml(notification.title || defaultTitle)}</h4>
                <span class="notification-time">${time}</span>
            </div>
            <p class="notification-message">${message}</p>
            ${linkUrl ? `<div style="margin-top: 8px;"><a href="${linkUrl}" onclick="event.stopPropagation();" style="color: #248567; font-weight: 500; text-decoration: none; font-size: 0.875rem;"><i class="fas fa-external-link-alt"></i> ${linkText}</a></div>` : ''}
        `;

        // Add click handler to mark as read and navigate (no deletion)
        li.addEventListener('click', (e) => {
            if (!e.target.closest('a')) {
                this.markAsRead(notification.id);
                // Navigate to change request/task if available
                if (linkUrl) {
                    window.location.href = linkUrl;
                }
            }
        });

        return li;
    }

    /**
     * Create a role notification item with special formatting
     */
    createRoleNotificationItem(notification, time) {
        const li = document.createElement('li');
        // Add 'read' class for read notifications to apply styling
        li.className = 'notification-item' + (notification.read ? ' read' : ' unread');
        li.setAttribute('data-notification-id', notification.id);

        // Add background color for read notifications
        if (notification.read) {
            li.style.backgroundColor = '#f5f5f5';
            li.style.opacity = '0.8';
        }

        // Parse message: "Facet Name: Object Name"
        const message = notification.message || '';
        const parts = message.split(':');
        const facetName = parts.length > 0 ? parts[0].trim() : '';
        const objectName = parts.length > 1 ? parts.slice(1).join(':').trim() : '';

        // Generate object link
        const objectLink = this.getObjectLink(notification.facetType, notification.objectId);

        // Format message with clickable object name
        let formattedMessage = '';
        if (facetName && objectName) {
            formattedMessage = `${this.escapeHtml(facetName)}: <a href="${objectLink}" onclick="event.stopPropagation();" style="color: #248567; font-weight: 500; text-decoration: none;">${this.escapeHtml(objectName)}</a>`;
        } else {
            formattedMessage = this.escapeHtml(message);
        }

        const roleTitle = (window.I18n && window.I18n.t('notification.panel.roleAssignment')) || 'Role Assignment';
        li.innerHTML = `
            <div class="notification-item-header">
                <h4 class="notification-title">${this.escapeHtml(notification.title || roleTitle)}</h4>
                <span class="notification-time">${time}</span>
            </div>
            <p class="notification-message">${formattedMessage}</p>
        `;

        // Add click handler to navigate to object (no deletion)
        li.addEventListener('click', (e) => {
            if (!e.target.closest('a')) {
                this.markAsRead(notification.id);
                if (objectLink) {
                    window.location.href = objectLink;
                }
            }
        });

        return li;
    }

    /**
     * Create a bulk upload notification item
     */
    createBulkUploadNotificationItem(notification, time) {
        const li = document.createElement('li');
        li.className = 'notification-item' + (notification.read ? ' read' : ' unread');
        li.setAttribute('data-notification-id', notification.id);

        // Format timestamp in full format for bulk upload
        const fullTime = this.formatTimestampFull(notification.createdAt);

        // Determine success/failure status from eventType or message
        const isSuccess = notification.eventType !== 'UPLOAD_FAILED' && 
                         !notification.message.toLowerCase().includes('failed') &&
                         !notification.message.toLowerCase().includes('error');
        
        const iconClass = isSuccess ? 'fas fa-check' : 'fas fa-times-circle';
        const iconColor = isSuccess ? '#4CAF50' : '#d32f2f';

        const deleteBtn = `<button class="notification-item-close" onclick="event.stopPropagation(); window.notificationPanel.deleteNotification(${notification.id}, this);" style="float: right; border: none; background: none; color: #888; cursor: pointer;"><i class="fas fa-times"></i></button>`;

        li.innerHTML = `
            <div class="notification-item-header" style="justify-content: space-between; align-items: flex-start;">
                <div>
                    <h4 class="notification-title" style="margin-right: 20px;">${this.escapeHtml(notification.title || ((window.I18n && window.I18n.t('notification.panel.bulkUploadTitle')) || 'Bulk Upload'))}</h4>
                    <span class="notification-time">${fullTime}</span>
                </div>
                ${deleteBtn}
            </div>
            <p class="notification-message" style="display: flex; align-items: flex-start; gap: 8px;">
                <i class="${iconClass}" style="color: ${iconColor}; margin-top: 3px;"></i>
                <span>${this.escapeHtml(notification.message)}</span>
            </p>
        `;

        // Click to navigate to people page activity log with my-jobs subtab
        li.addEventListener('click', async (e) => {
            if (!e.target.closest('button')) {
                this.markAsRead(notification.id);
                // Navigate to people page activity log with my-jobs subtab
                const userId = notification.recipientUserId || notification.recipient_user_id;
                if (userId) {
                    window.location.href = `/view/people/${userId}?tab=activity&subtab=my-jobs`;
                } else {
                    // Fallback: get current user ID
                    await this.navigateToMyJobs();
                }
            }
        });

        return li;
    }


    /**
     * Generate object view link based on facet type
     * Uses format: /view/{facet}/{id} for most facets
     */
    getObjectLink(facetType, objectId) {
        if (!facetType || !objectId) {
            return '#';
        }

        const normalizedType = this.normalizeFacetTypeForUrl(facetType);

        // Some facets use query params format, others use path format
        // Based on MODULE_VIEW_MAPPING in search-links.js
        switch (normalizedType) {
            case 'legal-entity':
                return `/view/LegalEntity/legal-entity.html?id=${objectId}`;
            case 'business-area':
                return `/view/business-area/business-area.html?id=${objectId}`;
            case 'capability':
                return `/view/capability/${objectId}`;
            case 'client':
                return `/view/client/client.html?id=${objectId}`;
            case 'geography':
                return `/view/geography/geography.html?id=${objectId}`;
            case 'regulation':
                return `/view/regulation/regulation.html?id=${objectId}`;
            case 'regulator':
                return `/view/regulator/regulator.html?id=${objectId}`;
            case 'regulatory-theme':
                return `/view/regulatory-theme/regulatory-theme.html?id=${objectId}`;
            default:
                // Most facets use path format: /view/{facet}/{id}
                return `/view/${normalizedType}/${objectId}`;
        }
    }

    /**
     * Normalize facet type for URL generation
     */
    normalizeFacetTypeForUrl(facetType) {
        if (!facetType) return 'dataset';

        const normalized = facetType.trim().toLowerCase();
        switch (normalized) {
            case 'data set':
            case 'dataset':
                return 'dataset';
            case 'system':
                return 'system';
            case 'glossary':
                return 'glossary';
            case 'process':
                return 'process';
            case 'system interface':
            case 'interface':
                return 'system-interface';
            case 'policy':
                return 'policy';
            case 'product':
                return 'product';
            case 'project':
                return 'project';
            case 'business area':
            case 'businessarea':
                return 'business-area';
            case 'client':
                return 'client';
            case 'committee':
                return 'committee';
            case 'legal entity':
            case 'legalentity':
                return 'legal-entity';
            case 'capability':
                return 'capability';
            case 'regulation':
                return 'regulation';
            case 'attribute':
                return 'attribute';
            default:
                return normalized.replace(/\s+/g, '-');
        }
    }

    /**
     * Mark notification as read
     */
    async markAsRead(notificationId) {
        try {
            const response = await fetch(`/api/notifications/${notificationId}/read`, {
                method: 'PUT',
                credentials: 'include'
            });

            if (response.ok) {
                // Update UI - add read styling
                const item = document.querySelector(`[data-notification-id="${notificationId}"]`);
                if (item) {
                    item.classList.remove('unread');
                    item.classList.add('read');
                    item.style.backgroundColor = '#f5f5f5';
                    item.style.opacity = '0.8';
                }
                await this.updateNotificationCount();
            }
        } catch (error) {
            console.error('Failed to mark notification as read:', error);
        }
    }

    /**
     * Delete a notification
     */
    async deleteNotification(notificationId) {
        try {
            const response = await fetch(`/api/notifications/${notificationId}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (response.ok) {
                // Remove from UI
                const item = document.querySelector(`[data-notification-id="${notificationId}"]`);
                if (item) {
                    item.remove();
                }
                await this.updateNotificationCount();

                // If no notifications left, show empty message
                const listContainer = document.getElementById('notificationList');
                if (listContainer && listContainer.children.length === 0) {
                    listContainer.innerHTML = '<li class="notification-empty">' + (window.I18n?.t('notification.noNotifications') || 'No notifications') + '</li>';
                }
            }
        } catch (error) {
            console.error('Failed to delete notification:', error);
        }
    }

    /**
     * Clear all notifications for current category (deletes from DB)
     */
    async clearAllNotifications() {
        const activeTab = this.panel.querySelector('.notification-tab.active');
        if (!activeTab) return;

        const category = activeTab.getAttribute('data-category');

        if (!confirm('Are you sure you want to delete all notifications? This action cannot be undone.')) {
            return;
        }

        try {
            const response = await fetch(`/api/notifications/delete-all?category=${category}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (response.ok) {
                const result = await response.json();
                console.log(`Deleted ${result.deletedCount || 0} notifications`);
                await this.loadNotifications(category);
                await this.updateNotificationCount();
            } else {
                const errorData = await response.json().catch(() => ({}));
                alert(errorData.error || 'Failed to delete notifications');
            }
        } catch (error) {
            console.error('Failed to clear all notifications:', error);
            alert('Error deleting notifications. Please try again.');
        }
    }

    /**
     * Start auto-refresh
     */
    startAutoRefresh() {
        // Refresh every 30 seconds
        this.refreshInterval = setInterval(async () => {
            // Check authentication before refreshing
            const isAuth = await this.checkAuthentication();
            if (!isAuth) {
                // User is no longer authenticated, stop auto-refresh
                this.stopAutoRefresh();
                this.isAuthenticated = false;
                return;
            }

            this.updateNotificationCount();
            if (this.panel && this.panel.classList.contains('open')) {
                const activeTab = this.panel.querySelector('.notification-tab.active');
                if (activeTab) {
                    const category = activeTab.getAttribute('data-category');
                    this.loadNotifications(category);
                }
            }
        }, 30000);
    }

    /**
     * Stop auto-refresh
     */
    stopAutoRefresh() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }
    }

    /**
     * Format timestamp (relative time for most notifications)
     */
    formatTimestamp(timestamp) {
        if (!timestamp) return '';
        const date = new Date(timestamp);
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        if (diffDays < 7) return `${diffDays}d ago`;

        return date.toLocaleDateString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    /**
     * Format timestamp in full format for bulk upload notifications
     * Format: "17-Dec-2025 04:16:03"
     */
    formatTimestampFull(timestamp) {
        if (!timestamp) return '';
        const date = new Date(timestamp);
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const day = String(date.getDate()).padStart(2, '0');
        const month = months[date.getMonth()];
        const year = date.getFullYear();
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        
        return `${day}-${month}-${year} ${hours}:${minutes}:${seconds}`;
    }

    /**
     * Escape HTML
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Initialize notification panel when DOM is ready
let notificationPanelInstance = null;

function initNotificationPanel() {
    if (!notificationPanelInstance) {
        notificationPanelInstance = new NotificationPanel();
        notificationPanelInstance.initialize();
    } else if (!notificationPanelInstance.isInitialized) {
        // Re-initialize if not already initialized
        notificationPanelInstance.initialize();
    }
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initNotificationPanel);
} else {
    // DOM is already ready
    initNotificationPanel();
}

// Also initialize if header is loaded later
window.addEventListener('headerReady', () => {
    if (!notificationPanelInstance) {
        notificationPanelInstance = new NotificationPanel();
        notificationPanelInstance.initialize();
    } else if (!notificationPanelInstance.isInitialized) {
        // Re-initialize if not already initialized
        notificationPanelInstance.initialize();
    }
});

