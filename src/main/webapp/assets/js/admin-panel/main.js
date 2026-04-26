// Main Admin Panel JavaScript File
// This file contains core functions and loads other modules

// ===== ROLE DETECTION =====

async function getCurrentUser() {
    try {
        const response = await fetch('/api/me', {
            method: 'GET',
            credentials: 'include'
        });
        if (response.ok) {
            return await response.json();
        }
    } catch (error) {
        console.error('Error getting current user:', error);
    }
    return null;
}

function checkIsSuperAdmin(role) {
    if (typeof RoleUtils !== 'undefined' && RoleUtils.isSuperAdminRole) {
        return RoleUtils.isSuperAdminRole(role);
    }
    if (!role) return false;
    const compact = String(role).toLowerCase().trim().replace(/[\s_-]/g, '');
    return compact === 'superadmin' || compact === 'suberadmin';
}

/** i18n helper for admin panel: key -> translated string, fallback if missing */
function adminT(key, fallback) {
    if (window.I18n && typeof window.I18n.t === 'function') {
        const s = window.I18n.t(key);
        if (s && s !== key) return s;
    }
    return fallback != null ? fallback : key;
}
window.adminT = adminT;

/**
 * Highlight sidebar item by i18n key (e.g. adminPanel.submenu.staticPageEditor) or legacy English label.
 * Matches span[data-i18n] so Arabic UI still activates the correct item.
 */
function highlightSubmenuItem(itemTextOrKey) {
    document.querySelectorAll('.submenu-item').forEach(sub => sub.classList.remove('active'));
    document.querySelectorAll('.sidebar-navigation > .nav-item').forEach(nav => nav.classList.remove('active'));

    function tryMatch(sub) {
        const span = sub.querySelector('span');
        if (!span) return false;
        const i18nKey = span.getAttribute('data-i18n');
        if (i18nKey && i18nKey === itemTextOrKey) return true;
        if (span.textContent === itemTextOrKey) return true;
        if (i18nKey && window.I18n && window.I18n.t(i18nKey) === itemTextOrKey) return true;
        return false;
    }

    document.querySelectorAll('.submenu-item').forEach(sub => {
        if (!tryMatch(sub)) return;
        sub.classList.add('active');
        const container = sub.closest('.nav-item-container');
        if (container) {
            container.classList.add('active');
            container.classList.add('expanded');
            const adminPanelPage = document.querySelector('.admin-panel-page');
            if (adminPanelPage) adminPanelPage.classList.add('has-expanded-submenu');
        }
    });

    // Top-level nav only (e.g. Admin Activity Logs)
    document.querySelectorAll('.sidebar-navigation > a.nav-item').forEach(nav => {
        const span = nav.querySelector('span');
        if (!span) return;
        const i18nKey = span.getAttribute('data-i18n');
        if (i18nKey === itemTextOrKey || span.textContent === itemTextOrKey ||
            (i18nKey && window.I18n && window.I18n.t(i18nKey) === itemTextOrKey)) {
            nav.classList.add('active');
        }
    });
}

function updateSystemTitle(title, subtitle) {
    const systemTitle = document.querySelector('.system-title');
    const systemSubtitle = document.querySelector('.system-subtitle');

    if (systemTitle) {
        systemTitle.textContent = title;
    }

    if (systemSubtitle) {
        systemSubtitle.textContent = subtitle;
    }
}

function getApiUrl(endpoint, params = {}) {
    const url = new URL(endpoint, window.location.origin);
    Object.keys(params).forEach(key => {
        if (params[key] !== undefined && params[key] !== null) {
            url.searchParams.append(key, params[key]);
        }
    });
    return url.toString();
}

function restoreOriginalHeader() {
    const adminHeaderBar = document.querySelector('.admin-header-bar');
    if (adminHeaderBar) {
        const title = adminT('adminPanel.header.budgManagement', 'BUDG Management');
        const subtitle = adminT('adminPanel.header.dataGovernancePlatform', 'Data Governance Platform');
        adminHeaderBar.innerHTML = `
            <div class="header-left">
                <button class="back-button" onclick="goBack()">
                    <i class="fas fa-arrow-left"></i>
                </button>
            </div>
            <div class="header-center">
                <h1 class="system-title">${title}</h1>
                <div class="system-status">
                    <span class="status-dot"></span>
                    ${subtitle}
                </div>
            </div>
        `;
    }
}

// Load all required modules
function loadAdminPanelModules() {
    // Initialize navigation history
    initializeNavigationHistory();

    // Load modules sequentially to ensure proper loading order
    const modules = [
        'assets/js/admin-panel/admin-notifications.js',
        'assets/js/admin-panel/dashboard.js',
        'assets/js/admin-panel/operating-model/role-permissions.js',
        'assets/js/admin-panel/operating-model/role-assignment.js',
        'assets/js/admin-panel/operating-model/default-workflows.js',
        'assets/js/admin-panel/operating-model/default-change-requests.js',
        'assets/js/admin-panel/operating-model/roles-responsibilities.js',
        'assets/js/admin-panel/operating-model/change-request-systems.js',
        'assets/js/admin-panel/operating-model/licensed-users.js',
        'assets/js/admin-panel/operating-model/periodic-review-crud.js',
        'assets/js/admin-panel/operating-model/periodic-review.js',
        '/view/segments/segments-list.js',
        'assets/js/admin-panel/meta-model/dropdown-configs.js',
        'assets/js/admin-panel/meta-model/custom-fields.js',
        'assets/js/admin-panel/meta-model/static-page-editor.js',
        'assets/js/admin-panel/operational-management/data-onboarding-rules.js',
        'assets/js/admin-panel/operational-management/notification-rules.js',
        'assets/js/admin-panel/operational-management/email-settings.js',
        'assets/js/admin-panel/operational-management/administrators-panel.js',
        'assets/js/admin-panel/operational-management/manage-locks.js',
        'assets/js/admin-panel/operational-management/locked-users.js',
        'assets/js/admin-panel/operational-management/ownership-transfer.js',
        'assets/js/admin-panel/operational-management/download-logs.js',
        'assets/js/admin-panel/operational-management/bulk-import-env.js',
        'assets/js/admin-panel/customize/change-logo.js',
        'assets/js/admin-panel/customize/customize-styles.js',
        'assets/js/admin-panel/customize/system-settings.js',
        'assets/js/admin-panel/customize/app-settings.js',
        'assets/js/admin-panel/admin-activity-logs.js'
    ];

    function loadScript(src) {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = src;
            script.onload = () => {
                resolve();
            };
            script.onerror = (error) => {
                console.error(`Failed to load: ${src}`, error);
                reject(error);
            };
            document.head.appendChild(script);
        });
    }

    // Load all modules
    Promise.all(modules.map(loadScript))
        .then(() => {
            console.log('All admin panel modules loaded successfully');
            // Re-pin after all modules so no late script overwrites global goBack / restoreOriginalHeader
            window.goBack = goBack;
            window.restoreOriginalHeader = restoreOriginalHeader;
            // Check if required functions are available
            checkRequiredFunctions();
            // Initialize admin panel after modules are loaded
            if (typeof initAdminPanel === 'function') {
                initAdminPanel();
            }
        })
        .catch((error) => {
            console.error('Error loading admin panel modules:', error);
            window.goBack = goBack;
            window.restoreOriginalHeader = restoreOriginalHeader;
            // Initialize anyway with basic functionality
            if (typeof initAdminPanel === 'function') {
                initAdminPanel();
            }
        });
}

// Check if required functions are available
function checkRequiredFunctions() {
    const requiredFunctions = [
        'addRoleAssignment',
        'deleteRoleAssignment',
        'saveRoleAssignment',
        'goBackToRoleAssignment',
        'showRoleAssignmentContent'
    ];

    const missingFunctions = requiredFunctions.filter(func => typeof window[func] !== 'function');

    if (missingFunctions.length > 0) {
        console.warn('Missing functions:', missingFunctions);
    }

    // Check if API service is available
    if (window.BUDG_API_SERVICE) {
        // Test API endpoints
        testApiEndpoints();
    } else {
        console.warn('API service is not available - some features may not work');
    }
}

// Test API endpoints
function testApiEndpoints() {
    // Test role assignments endpoint
    window.BUDG_API_SERVICE.get('/role-assignments')
        .then(response => {
            // Endpoint is working
        })
        .catch(error => {
            console.warn('⚠️ Role assignments endpoint may not be available:', error.message);
        });
}

// highlightSubmenuItem defined once at top of file (i18n-aware)


async function initAdminPanel() {
    // Check if API service is available
    if (window.BUDG_API_SERVICE) {
        console.log('API service is available');
    } else {
        console.warn('API service is not available - some features may not work');
    }

    // Fetch current user role and store globally for all modules
    const user = await getCurrentUser();
    window._adminUserRole = user?.role || '';
    const isSuperAdmin = checkIsSuperAdmin(window._adminUserRole);

    // Populate sidebar user profile with real data
    if (user) {
        const sidebarName = document.getElementById('adminSidebarUserName');
        const sidebarEmail = document.getElementById('adminSidebarUserEmail');
        const sidebarRole = document.getElementById('adminSidebarUserRole');
        const sidebarAvatar = document.getElementById('adminSidebarAvatar');
        if (sidebarName) {
            sidebarName.textContent = ((user.firstName || '') + ' ' + (user.lastName || '')).trim() || user.name || 'User';
        }
        if (sidebarEmail) {
            sidebarEmail.textContent = user.email || '';
        }
        if (sidebarRole) {
            const roleFromApi = user.role || user.roleName || user.userRole || '';
            sidebarRole.textContent = String(roleFromApi).trim();
        }
        if (sidebarAvatar && user.firstName) {
            const initials = (user.firstName.charAt(0) + (user.lastName ? user.lastName.charAt(0) : '')).toUpperCase();
            sidebarAvatar.innerHTML = '<span>' + initials + '</span>';
        }
    }

    if (!isSuperAdmin) {
        // Hide nav tabs that Admin cannot see at all
        document.querySelectorAll('[data-super-admin-only="true"]').forEach(el => {
            el.style.display = 'none';
        });
    }

    initSidebarNavigation();
    initBackButton();
    initThemeSupport();
    initUserSettings();
    
    // Check Data Migration feature flag and show/hide menu item
    checkDataMigrationMenuVisibility();

    // Show dashboard content by default
    const contentArea = document.querySelector('.content-area');
    if (contentArea) {
        showDashboardContent(contentArea);
    } else {
        console.error('Content area not found!');
    }

    setTimeout(() => {
        if (document.querySelector('.header-search')) {
            enableSearchForBothTables();

            setTimeout(() => {
                const roleTypeSelect = document.querySelector('[data-column="role-type"]');
                if (!roleTypeSelect) {
                    const headerSearch = document.querySelector('.header-search');
                    if (headerSearch) {
                        const roleTypeHeader = headerSearch.querySelector('th:nth-child(5)');
                        if (roleTypeHeader) {
                            const allRoleTypes = window.I18n ? window.I18n.t('adminPanel.common.allRoleTypes') : 'All Role Types';
                            roleTypeHeader.innerHTML = `
                                <div class="search-header-cell">
                                    <select class="table-search-select" data-column="role-type">
                                        <option value="">${allRoleTypes}</option>
                                    </select>
                                </div>
                            `;

                            if (window.cachedRoleTypes) {
                                updateRoleTypeDropdown(window.cachedRoleTypes);
                            }
                        }
                    }
                }
            }, 1000);
        }
    }, 500);
}

function initSidebarNavigation() {
    const navItems = document.querySelectorAll('.sidebar-navigation .nav-item');
    const navContainers = document.querySelectorAll('.sidebar-navigation .nav-item-container');

    navItems.forEach(item => {
        if (!item.closest('.nav-item-container')) {
            item.addEventListener('click', function (e) {
                e.preventDefault();

                navItems.forEach(nav => nav.classList.remove('active'));
                navContainers.forEach(container => container.classList.remove('active'));
                document.querySelectorAll('.submenu-item').forEach(sub => sub.classList.remove('active'));

                this.classList.add('active');

                const itemText = this.querySelector('span').textContent;
                handleNavigation(itemText);
            });
        }
    });

    navContainers.forEach(container => {
        if (container.classList.contains('has-submenu')) {
            const navLink = container.querySelector('.nav-item');
            navLink.addEventListener('click', function (e) {
                e.preventDefault();

                // Close all other expanded submenus before toggling this one
                const wasExpanded = container.classList.contains('expanded');
                navContainers.forEach(cont => {
                    if (cont !== container) {
                        cont.classList.remove('expanded');
                    }
                });

                // Toggle current submenu
                if (wasExpanded) {
                    container.classList.remove('expanded');
                } else {
                    container.classList.add('expanded');
                }

                const adminPanelPage = document.querySelector('.admin-panel-page');
                if (container.classList.contains('expanded')) {
                    adminPanelPage.classList.add('has-expanded-submenu');
                } else {
                    if (container.classList.contains('active')) {
                        adminPanelPage.classList.add('has-expanded-submenu');
                    } else {
                        adminPanelPage.classList.remove('has-expanded-submenu');
                    }
                }

                navItems.forEach(nav => nav.classList.remove('active'));
                navContainers.forEach(cont => cont.classList.remove('active'));
                document.querySelectorAll('.submenu-item').forEach(sub => sub.classList.remove('active'));

                container.classList.add('active');

                const spanElement = container.querySelector('.nav-item span');
                if (spanElement) {
                    const itemText = spanElement.textContent;
                    handleNavigation(itemText);
                }
            });

            const submenuItems = container.querySelectorAll('.submenu-item');
            submenuItems.forEach(subItem => {
                subItem.addEventListener('click', function (e) {
                    e.stopPropagation();

                    navItems.forEach(nav => nav.classList.remove('active'));
                    navContainers.forEach(cont => cont.classList.remove('active'));
                    document.querySelectorAll('.sidebar-navigation .submenu-item').forEach(sub => sub.classList.remove('active'));

                    container.classList.add('active');
                    container.classList.add('expanded');
                    this.classList.add('active');
                    const adminPanelPage = document.querySelector('.admin-panel-page');
                    if (adminPanelPage) {
                        adminPanelPage.classList.add('has-expanded-submenu');
                    }

                    const spanElement = this.querySelector('span');
                    if (spanElement) {
                        const subItemText = spanElement.textContent;
                        handleSubmenuNavigation(subItemText);
                    }
                });
            });
        }
    });

    showWelcomeContent(document.querySelector('.content-area'));
}

function handleNavigation(section) {
    const contentArea = document.querySelector('.content-area');
    const adminPanelPage = document.querySelector('.admin-panel-page');

    // Restore original header for all navigation
    restoreOriginalHeader();

    // Get translated section names for comparison
    const adminDashboard = window.I18n ? window.I18n.t('adminPanel.navigation.adminDashboard') : 'Admin Dashboard';
    const dgOperatingModel = window.I18n ? window.I18n.t('adminPanel.navigation.dgOperatingModel') : 'DG Operating Model';
    const metaModelAdmin = window.I18n ? window.I18n.t('adminPanel.navigation.metaModelAdmin') : 'Meta-Model Administration';
    const operationalManagement = window.I18n ? window.I18n.t('adminPanel.navigation.operationalManagement') : 'Operational Management';
    const customizeConfigure = window.I18n ? window.I18n.t('adminPanel.navigation.customizeConfigure') : 'Customize & Configure';
    const adminActivityLogs = window.I18n ? window.I18n.t('adminPanel.navigation.adminActivityLogs') : 'Admin Activity Logs';
    
    switch (section) {
        case adminDashboard:
        case 'Admin Dashboard':
            resetSystemTitle();
            showDashboardContent(contentArea);
            adminPanelPage.classList.remove('has-expanded-submenu');
            break;
        case dgOperatingModel:
        case 'DG Operating Model':
            resetSystemTitle();
            showOperatingModelContent(contentArea);
            adminPanelPage.classList.add('has-expanded-submenu');
            break;
        case metaModelAdmin:
        case 'Meta-Model Administration':
            resetSystemTitle();
            showMetaModelContent(contentArea);
            adminPanelPage.classList.add('has-expanded-submenu');
            break;
        case operationalManagement:
        case 'Operational Management':
            resetSystemTitle();
            showOperationalContent(contentArea);
            adminPanelPage.classList.add('has-expanded-submenu');
            break;
        case customizeConfigure:
        case 'Customize & Configure':
            resetSystemTitle();
            showCustomizeContent(contentArea);
            adminPanelPage.classList.add('has-expanded-submenu');
            break;
        case adminActivityLogs:
        case 'Admin Activity Logs':
            resetSystemTitle();
            showActivityLogsContent(contentArea);
            adminPanelPage.classList.remove('has-expanded-submenu');
            break;
        default:
            resetSystemTitle();
            showWelcomeContent(contentArea);
            adminPanelPage.classList.remove('has-expanded-submenu');
    }
}

function handleSubmenuNavigation(submenuItem) {
    const contentArea = document.querySelector('.content-area');

    // Get translated submenu item names for comparison
    // Since HTML uses data-i18n, the textContent will already be translated
    // So we need to check both English and translated versions
    const roleAssignment = window.I18n ? window.I18n.t('adminPanel.submenu.roleAssignment') : 'Role Assignment';
    
    // Restore original header for all submenu navigation except Role Assignment
    if (submenuItem !== roleAssignment && submenuItem !== 'Role Assignment') {
        restoreOriginalHeader();
    }

    // Get all translated submenu names
    const rolePermissions = window.I18n ? window.I18n.t('adminPanel.submenu.rolePermissions') : 'Role Permissions';
    const defaultWorkflows = window.I18n ? window.I18n.t('adminPanel.submenu.defaultWorkflows') : 'Default Workflows';
    const defaultChangeRequests = window.I18n ? window.I18n.t('adminPanel.submenu.defaultChangeRequests') : 'Default Change Requests';
    const rolesResponsibilities = window.I18n ? window.I18n.t('adminPanel.submenu.rolesResponsibilities') : 'Roles & Responsibilities';
    const changeRequestSystems = window.I18n ? window.I18n.t('adminPanel.submenu.changeRequestSystems') : 'Change Request Systems';
    const licensedUsers = window.I18n ? window.I18n.t('adminPanel.submenu.licensedUsers') : 'Licensed Users';
    const periodicReview = window.I18n ? window.I18n.t('adminPanel.submenu.periodicReview') : 'Periodic Review of Objects';
    const segments = window.I18n ? window.I18n.t('adminPanel.submenu.segments') : 'Segments';
    const customFields = window.I18n ? window.I18n.t('adminPanel.submenu.customFields') : 'Custom Fields';
    const dropdownConfigs = window.I18n ? window.I18n.t('adminPanel.submenu.dropdownConfigs') : 'Dropdown Configurations';
    const staticPageEditor = window.I18n ? window.I18n.t('adminPanel.submenu.staticPageEditor') : 'Static Page Editor';
    const dataOnboardingRules = window.I18n ? window.I18n.t('adminPanel.submenu.dataOnboardingRules') : 'Data Onboarding Rules';
    const administratorsPanel = window.I18n ? window.I18n.t('adminPanel.submenu.administratorsPanel') : 'Administrator\'s Panel';
    const manageLocks = window.I18n ? window.I18n.t('adminPanel.submenu.manageLocks') : 'Manage Locks';
    const lockedUsers = window.I18n ? window.I18n.t('adminPanel.submenu.lockedUsers') : 'Locked Users';
    const ownershipTransfer = window.I18n ? window.I18n.t('adminPanel.submenu.ownershipTransfer') : 'Ownership Transfer';
    const downloadLogs = window.I18n ? window.I18n.t('adminPanel.submenu.downloadLogs') : 'Download Logs';
    const bulkImportEnv = window.I18n ? window.I18n.t('adminPanel.submenu.bulkImportEnv') : 'Bulk Import ENV';
    const changeLogo = window.I18n ? window.I18n.t('adminPanel.submenu.changeLogo') : 'Change Logo';
    const customizeStyles = window.I18n ? window.I18n.t('adminPanel.submenu.customizeStyles') : 'Customize Styles';
    const systemSettings = window.I18n ? window.I18n.t('adminPanel.submenu.systemSettings') : 'System Settings';
    const appSettings = window.I18n ? window.I18n.t('adminPanel.submenu.appSettings') : 'Application Settings';

    // Check both English and translated versions
    if (submenuItem === rolePermissions || submenuItem === 'Role Permissions') {
        showRolePermissionsContent(contentArea);
    } else if (submenuItem === defaultWorkflows || submenuItem === 'Default Workflows') {
        showDefaultWorkflowsContent(contentArea);
    } else if (submenuItem === defaultChangeRequests || submenuItem === 'Default Change Requests') {
        showDefaultChangeRequestsContent(contentArea);
    } else if (submenuItem === rolesResponsibilities || submenuItem === 'Roles & Responsibilities') {
        showRolesResponsibilitiesContent(contentArea);
    } else if (submenuItem === roleAssignment || submenuItem === 'Role Assignment') {
        showRoleAssignmentContent(contentArea);
    } else if (submenuItem === changeRequestSystems || submenuItem === 'Change Request Systems') {
        showChangeRequestSystemsContent(contentArea);
    } else if (submenuItem === licensedUsers || submenuItem === 'Licensed Users') {
        showLicensedUsersContent(contentArea);
    } else if (submenuItem === periodicReview || submenuItem === 'Periodic Review of Objects') {
        showPeriodicReviewContent(contentArea);
    } else if (submenuItem === segments || submenuItem === 'Segments') {
        showSegmentsContent(contentArea);
    } else if (submenuItem === customFields || submenuItem === 'Custom Fields') {
        if (typeof showCustomFieldsContent === 'function') {
            showCustomFieldsContent(contentArea);
        } else {
            console.error('showCustomFieldsContent function not found');
        }
    } else if (submenuItem === dropdownConfigs || submenuItem === 'Dropdown Configurations') {
        showDropdownConfigurationsContent(contentArea);
    } else if (submenuItem === staticPageEditor || submenuItem === 'Static Page Editor') {
        showStaticPageEditorContent(contentArea);
    } else if (submenuItem === dataOnboardingRules || submenuItem === 'Data Onboarding Rules') {
        showDataOnboardingRulesContent(contentArea);
    } else if (submenuItem === administratorsPanel || submenuItem === 'Administrator\'s Panel') {
        showLdapSyncPanel(contentArea);
    } else if (submenuItem === 'Notification Rules') {
        showNotificationRulesContent(contentArea);
    } else if (submenuItem === manageLocks || submenuItem === 'Manage Locks') {
        showManageLocksContent(contentArea);
    } else if (submenuItem === lockedUsers || submenuItem === 'Locked Users') {
        showLockedUsersContent(contentArea);
    } else if (submenuItem === ownershipTransfer || submenuItem === 'Ownership Transfer') {
        showOwnershipTransferContent(contentArea);
    } else if (submenuItem === downloadLogs || submenuItem === 'Download Logs') {
        showDownloadLogsContent(contentArea);
    } else if (submenuItem === bulkImportEnv || submenuItem === 'Bulk Import ENV') {
        if (typeof showBulkImportEnvContent === 'function') {
            showBulkImportEnvContent(contentArea);
        } else {
            console.error('showBulkImportEnvContent function not found');
        }
    } else if (submenuItem === changeLogo || submenuItem === 'Change Logo') {
        showChangeLogoContent(contentArea);
    } else if (submenuItem === customizeStyles || submenuItem === 'Customize Styles') {
        showCustomizeStylesContent(contentArea);
    } else if (submenuItem === systemSettings || submenuItem === 'System Settings') {
        showSystemSettingsContent(contentArea);
    } else if (submenuItem === appSettings || submenuItem === 'Application Settings') {
        showApplicationSettingsContent(contentArea);
    } else {
        showOperatingModelContent(contentArea);
    }
}

// System title and header management
function updateSystemTitle(title, subtitle) {
    const systemTitle = document.querySelector('.system-title');
    const systemSubtitle = document.querySelector('.system-subtitle');

    if (systemTitle) {
        systemTitle.textContent = title;
    }

    if (systemSubtitle) {
        systemSubtitle.textContent = subtitle;
    }
}

function resetSystemTitle() {
    const systemTitle = document.querySelector('.system-title');
    const systemSubtitle = document.querySelector('.system-subtitle');

    if (systemTitle) {
        systemTitle.textContent = window.I18n ? window.I18n.t('adminPanel.header.budgManagement') : 'BUDG Management';
    }

    if (systemSubtitle) {
        systemSubtitle.textContent = window.I18n ? window.I18n.t('adminPanel.header.dataGovernancePlatform') : 'Data Governance Platform';
    }
}

function restoreOriginalHeader() {
    const adminHeaderBar = document.querySelector('.admin-header-bar');
    if (adminHeaderBar) {
        const budgManagement = window.I18n ? window.I18n.t('adminPanel.header.budgManagement') : 'BUDG Management';
        const dataGovernancePlatform = window.I18n ? window.I18n.t('adminPanel.header.dataGovernancePlatform') : 'Data Governance Platform';
        adminHeaderBar.innerHTML = `
            <div class="header-left">
                <button class="back-button" onclick="goBack()">
                    <i class="fas fa-arrow-left"></i>
                </button>
            </div>
            <div class="header-center">
                <h1 class="system-title">${budgManagement}</h1>
                <div class="system-status">
                    <span class="status-dot"></span>
                    ${dataGovernancePlatform}
                </div>
            </div>
        `;
    }
}

// Back button functionality
function initBackButton() {
    // Back button functionality is handled in the header restoration
}

// Theme support
function initThemeSupport() {
    // Initialize theme support
    const savedTheme = localStorage.getItem('adminPanelTheme') || 'light';
    updateAdminPanelTheme(savedTheme);
}

function updateAdminPanelTheme(theme) {
    document.body.setAttribute('data-theme', theme);
    localStorage.setItem('adminPanelTheme', theme);
}

// User settings
function initUserSettings() {
    // Initialize user settings
}

// Check Data Migration feature flag and show/hide menu item
async function checkDataMigrationMenuVisibility() {
    try {
        const response = await fetch('/api/admin/environment/data-migration', {
            method: 'GET',
            credentials: 'include'
        });
        
        const envMenuItem = document.getElementById('bulk-import-env-menu-item');
        const setEnvVisible = (visible) => {
            if (envMenuItem) {
                envMenuItem.style.display = visible ? 'block' : 'none';
            }
            window._dataMigrationSnapshotVisible = visible;
        };
        if (envMenuItem) {
            if (response.ok) {
                const data = await response.json();
                if (data.enabled === true) {
                    setEnvVisible(true);
                    console.log('[AdminPanel] Bulk Import ENV menu item shown');
                } else {
                    setEnvVisible(false);
                    console.log('[AdminPanel] Bulk Import ENV hidden (feature disabled)');
                }
            } else if (response.status === 403) {
                setEnvVisible(false);
                console.log('[AdminPanel] Bulk Import ENV hidden (no access)');
            } else {
                setEnvVisible(false);
                console.warn('[AdminPanel] Could not check Data Migration status, hiding Bulk Import ENV');
            }
        }
    } catch (error) {
        console.warn('[AdminPanel] Error checking Data Migration status:', error);
        const envMenuItem = document.getElementById('bulk-import-env-menu-item');
        if (envMenuItem) {
            envMenuItem.style.display = 'none';
        }
        window._dataMigrationSnapshotVisible = false;
    }
}

// Make function available globally for system-settings.js to call
window.checkDataMigrationMenuVisibility = checkDataMigrationMenuVisibility;

// Welcome content
function showWelcomeContent(contentArea) {
    const welcomeText = window.I18n ? window.I18n.t('adminPanel.messages.welcomeToBudgManagement') : 'Welcome to BUDG Management';
    const selectSectionText = window.I18n ? window.I18n.t('adminPanel.messages.selectSection') : 'Select a section from the sidebar to begin managing your data governance platform.';
    contentArea.innerHTML = `
        <div class="welcome-content">
            <h2>${welcomeText}</h2>
            <p>${selectSectionText}</p>
        </div>
    `;
}

// ----- Admin card hub (shared layout like DG Operating Model) -----
function escapeAdminHtml(text) {
    if (text == null) return '';
    const s = String(text);
    return s
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

/**
 * Open a submenu destination by i18n key (adminPanel.submenu.*).
 * Resolves label via I18n and calls handleSubmenuNavigation so behavior matches sidebar clicks.
 */
function navigateAdminSubmenu(submenuI18nKey) {
    const label = window.I18n && submenuI18nKey
        ? window.I18n.t(submenuI18nKey)
        : '';
    if (label && label !== submenuI18nKey) {
        handleSubmenuNavigation(label);
        return;
    }
    // Fallback English labels when I18n missing
    const fallbacks = {
        'adminPanel.submenu.rolePermissions': 'Role Permissions',
        'adminPanel.submenu.defaultWorkflows': 'Default Workflows',
        'adminPanel.submenu.defaultChangeRequests': 'Default Change Requests',
        'adminPanel.submenu.rolesResponsibilities': 'Roles & Responsibilities',
        'adminPanel.submenu.roleAssignment': 'Role Assignment',
        'adminPanel.submenu.changeRequestSystems': 'Change Request Systems',
        'adminPanel.submenu.licensedUsers': 'Licensed Users',
        'adminPanel.submenu.periodicReview': 'Periodic Review of Objects',
        'adminPanel.submenu.segments': 'Segments',
        'adminPanel.submenu.customFields': 'Custom Fields',
        'adminPanel.submenu.dropdownConfigs': 'Dropdown Configurations',
        'adminPanel.submenu.staticPageEditor': 'Static Page Editor',
        'adminPanel.submenu.dataOnboardingRules': 'Data Onboarding Rules',
        'adminPanel.submenu.administratorsPanel': 'Administrator\'s Panel',
        'adminPanel.submenu.manageLocks': 'Manage Locks',
        'adminPanel.submenu.lockedUsers': 'Locked Users',
        'adminPanel.submenu.ownershipTransfer': 'Ownership Transfer',
        'adminPanel.submenu.downloadLogs': 'Download Logs',
        'adminPanel.submenu.bulkImportEnv': 'Bulk Import ENV',
        'adminPanel.submenu.changeLogo': 'Change Logo',
        'adminPanel.submenu.customizeStyles': 'Customize Styles',
        'adminPanel.submenu.systemSettings': 'System Settings',
        'adminPanel.submenu.appSettings': 'Application Settings'
    };
    handleSubmenuNavigation(fallbacks[submenuI18nKey] || label || submenuI18nKey);
}
window.navigateAdminSubmenu = navigateAdminSubmenu;

/**
 * Render card grid using same CSS as operating-model-content.
 * cards: [{ title, description, iconClass, submenuI18nKey, superAdminOnly?, requireDataMigrationSnapshot?, useViewButton? }]
 * Optional directOnclick: if set, used instead of navigateAdminSubmenu (e.g. goToRolesResponsibilitiesEditMode).
 */
function renderAdminCardHub(contentArea, options) {
    const title = escapeAdminHtml(options.title || '');
    const description = escapeAdminHtml(options.description || '');
    const manageBtn = window.I18n ? window.I18n.t('adminPanel.common.manage') : 'Manage';
    const viewBtn = window.I18n ? window.I18n.t('adminPanel.common.view') : 'View';
    const isSuperAdmin = typeof checkIsSuperAdmin === 'function' && checkIsSuperAdmin(window._adminUserRole);
    const cards = (options.cards || []).filter(function (c) {
        if (c.superAdminOnly && !isSuperAdmin) return false;
        if (c.requireDataMigrationSnapshot && !window._dataMigrationSnapshotVisible) {
            const el2 = document.getElementById('bulk-import-env-menu-item');
            const vis = (e) => e && e.style.display !== 'none' && e.offsetParent;
            if (!vis(el2)) return false;
        }
        return true;
    });
    let gridHtml = '';
    for (let i = 0; i < cards.length; i++) {
        const c = cards[i];
        const cardTitle = escapeAdminHtml(c.title);
        const cardDesc = escapeAdminHtml(c.description);
        const icon = c.iconClass || 'fas fa-cog';
        const btnClass = c.useViewButton ? 'view-btn' : 'manage-btn';
        const btnText = c.useViewButton ? viewBtn : manageBtn;
        let onClick;
        if (c.directOnclick) {
            onClick = c.directOnclick;
        } else if (c.submenuI18nKey) {
            const key = String(c.submenuI18nKey).replace(/'/g, '\\\'');
            onClick = "navigateAdminSubmenu('" + key + "')";
        } else {
            onClick = 'void(0)';
        }
        gridHtml += `
                <div class="operating-model-card">
                    <div class="card-icon">
                        <i class="${escapeAdminHtml(icon)}"></i>
                    </div>
                    <div class="card-content">
                        <h3>${cardTitle}</h3>
                        <p>${cardDesc}</p>
                    </div>
                    <div class="card-action">
                        <button type="button" class="action-btn ${btnClass}" onclick="${onClick}">${escapeAdminHtml(btnText)}</button>
                    </div>
                </div>`;
    }
    contentArea.innerHTML = `
        <div class="operating-model-content">
            <div class="operating-model-header">
                <h2>${title}</h2>
                <p>${description}</p>
            </div>
            <div class="operating-model-grid">
                ${gridHtml}
            </div>
        </div>
    `;
}

// Operating Model Content (card hub; Role Permissions hidden for non–SuperAdmin)
function showOperatingModelContent(contentArea) {
    const T = function (key, fb) { return window.I18n ? window.I18n.t(key) : fb; };
    renderAdminCardHub(contentArea, {
        title: T('adminPanel.operatingModel.title', 'DATA GOVERNANCE OPERATING MODEL'),
        description: T('adminPanel.operatingModel.description', 'This section allows you to configure the core components of your data governance operating model, such as roles, permissions, and workflows.'),
        cards: [
            {
                title: T('adminPanel.operatingModel.rolePermissions.title', 'Role Permissions'),
                description: T('adminPanel.operatingModel.rolePermissions.description', 'This panel allows you to control the permissions for each role.'),
                iconClass: 'fas fa-user-shield',
                submenuI18nKey: 'adminPanel.submenu.rolePermissions',
                superAdminOnly: true
            },
            {
                title: T('adminPanel.operatingModel.defaultWorkflows.title', 'Default Workflows'),
                description: T('adminPanel.operatingModel.defaultWorkflows.description', 'Create default workflows for change, approvals, and escalations.'),
                iconClass: 'fas fa-project-diagram',
                submenuI18nKey: 'adminPanel.submenu.defaultWorkflows'
            },
            {
                title: T('adminPanel.operatingModel.defaultChangeRequests.title', 'Default Change Requests'),
                description: T('adminPanel.operatingModel.defaultChangeRequests.description', 'Configure Mandatory Workflow Approval and Default Change Requests.'),
                iconClass: 'fas fa-edit',
                submenuI18nKey: 'adminPanel.submenu.defaultChangeRequests'
            },
            {
                title: T('adminPanel.operatingModel.rolesResponsibilities.title', 'Roles & Responsibilities'),
                description: T('adminPanel.operatingModel.rolesResponsibilities.description', 'Capture and describe governance roles per facet.'),
                iconClass: 'fas fa-user-tie',
                directOnclick: 'goToRolesResponsibilitiesEditMode()'
            },
            {
                title: T('adminPanel.operatingModel.roleAssignment.title', 'Role Assignment'),
                description: T('adminPanel.operatingModel.roleAssignment.description', 'Assign users to each role for a facet.'),
                iconClass: 'fas fa-user-plus',
                submenuI18nKey: 'adminPanel.submenu.roleAssignment'
            },
            {
                title: T('adminPanel.operatingModel.changeRequestSystems.title', 'Change Request Systems'),
                description: T('adminPanel.operatingModel.changeRequestSystems.description', 'Connect BUDG to external change request systems.'),
                iconClass: 'fas fa-cogs',
                submenuI18nKey: 'adminPanel.submenu.changeRequestSystems'
            },
            {
                title: T('adminPanel.operatingModel.licensedUsers.title', 'Licensed Users'),
                description: T('adminPanel.operatingModel.licensedUsers.description', 'Super Admins, Admins, and Web Users who have Edit permission on at least one object (via the permissions table).'),
                iconClass: 'fas fa-id-card',
                submenuI18nKey: 'adminPanel.submenu.licensedUsers',
                useViewButton: true
            },
            {
                title: T('adminPanel.operatingModel.periodicReview.title', 'Periodic Review of Objects'),
                description: T('adminPanel.operatingModel.periodicReview.description', 'Configure automatic review of objects.'),
                iconClass: 'fas fa-clock',
                submenuI18nKey: 'adminPanel.submenu.periodicReview'
            }
        ]
    });
}

// Meta Model Content (card hub aligned with submenu)
function showMetaModelContent(contentArea) {
    const T = function (key, fb) { return window.I18n ? window.I18n.t(key) : fb; };
    renderAdminCardHub(contentArea, {
        title: T('adminPanel.metaModel.title', 'Meta-Model Administration'),
        description: T('adminPanel.metaModel.description', 'Configure the underlying data model, segments, and custom fields.'),
        cards: [
            {
                title: T('adminPanel.submenu.segments', 'Segments'),
                description: T('adminPanel.metaModel.cardSegmentsDescription', 'Manage segments and segment assignments.'),
                iconClass: 'fas fa-layer-group',
                submenuI18nKey: 'adminPanel.submenu.segments'
            },
            {
                title: T('adminPanel.submenu.customFields', 'Custom Fields'),
                description: T('adminPanel.metaModel.cardCustomFieldsDescription', 'Define and manage custom fields for object types.'),
                iconClass: 'fas fa-list-alt',
                submenuI18nKey: 'adminPanel.submenu.customFields'
            },
            {
                title: T('adminPanel.submenu.dropdownConfigs', 'Dropdown Configurations'),
                description: T('adminPanel.metaModel.cardDropdownConfigsDescription', 'Configure dropdown values used across the application.'),
                iconClass: 'fas fa-caret-square-down',
                submenuI18nKey: 'adminPanel.submenu.dropdownConfigs',
                superAdminOnly: true
            },
            {
                title: T('adminPanel.submenu.staticPageEditor', 'Static Page Editor'),
                description: T('adminPanel.metaModel.cardStaticPageEditorDescription', 'Edit static pages and content shown in the application.'),
                iconClass: 'fas fa-file-alt',
                submenuI18nKey: 'adminPanel.submenu.staticPageEditor'
            }
        ]
    });
}

// Operational Content (card hub aligned with submenu)
function showOperationalContent(contentArea) {
    const T = function (key, fb) { return window.I18n ? window.I18n.t(key) : fb; };
    renderAdminCardHub(contentArea, {
        title: T('adminPanel.operational.title', 'Operational Management'),
        description: T('adminPanel.operational.description', 'Manage day-to-day operational activities, locks, logs, and onboarding.'),
        cards: [
            {
                title: T('adminPanel.submenu.dataOnboardingRules', 'Data Onboarding Rules'),
                description: T('adminPanel.operational.cardDataOnboardingDescription', 'Configure rules for data onboarding.'),
                iconClass: 'fas fa-clipboard-check',
                submenuI18nKey: 'adminPanel.submenu.dataOnboardingRules'
            },
            {
                title: T('adminPanel.submenu.administratorsPanel', 'Administrator\'s Panel'),
                description: T('adminPanel.operational.cardAdministratorsPanelDescription', 'LDAP sync and administrator tools.'),
                iconClass: 'fas fa-user-shield',
                submenuI18nKey: 'adminPanel.submenu.administratorsPanel'
            },
            {
                title: T('adminPanel.submenu.manageLocks', 'Manage Locks'),
                description: T('adminPanel.operational.cardManageLocksDescription', 'View and release object locks.'),
                iconClass: 'fas fa-lock',
                submenuI18nKey: 'adminPanel.submenu.manageLocks'
            },
            {
                title: T('adminPanel.submenu.lockedUsers', 'Locked Users'),
                description: T('adminPanel.operational.cardLockedUsersDescription', 'Manage users locked out after failed attempts.'),
                iconClass: 'fas fa-user-lock',
                submenuI18nKey: 'adminPanel.submenu.lockedUsers'
            },
            {
                title: T('adminPanel.submenu.ownershipTransfer', 'Ownership Transfer'),
                description: T('adminPanel.operational.cardOwnershipTransferDescription', 'Transfer ownership of objects between users.'),
                iconClass: 'fas fa-exchange-alt',
                submenuI18nKey: 'adminPanel.submenu.ownershipTransfer'
            },
            {
                title: T('adminPanel.submenu.downloadLogs', 'Download Logs'),
                description: T('adminPanel.operational.cardDownloadLogsDescription', 'Download application and audit logs.'),
                iconClass: 'fas fa-download',
                submenuI18nKey: 'adminPanel.submenu.downloadLogs'
            }
        ]
    });
}

// Customize Content (card hub aligned with submenu; Bulk Import ENV when enabled)
function showCustomizeContent(contentArea) {
    const T = function (key, fb) { return window.I18n ? window.I18n.t(key) : fb; };
    const cards = [
        {
            title: T('adminPanel.submenu.changeLogo', 'Change Logo'),
            description: T('adminPanel.customize.cardChangeLogoDescription', 'Upload and manage system logos and favicon.'),
            iconClass: 'fas fa-image',
            submenuI18nKey: 'adminPanel.submenu.changeLogo'
        },
        {
            title: T('adminPanel.submenu.customizeStyles', 'Customize Styles'),
            description: T('adminPanel.customize.cardCustomizeStylesDescription', 'Customize themes and typography.'),
            iconClass: 'fas fa-paint-brush',
            submenuI18nKey: 'adminPanel.submenu.customizeStyles'
        },
        {
            title: T('adminPanel.submenu.systemSettings', 'System Settings'),
            description: T('adminPanel.customize.systemSettings.description', 'Configure system-wide settings and parameters.'),
            iconClass: 'fas fa-sliders-h',
            submenuI18nKey: 'adminPanel.submenu.systemSettings'
        },
        {
            title: T('adminPanel.submenu.appSettings', 'Application Settings'),
            description: T('adminPanel.customize.cardAppSettingsDescription', 'Application settings including notifications and glossary rollup.'),
            iconClass: 'fas fa-cog',
            submenuI18nKey: 'adminPanel.submenu.appSettings'
        },
        {
            title: T('adminPanel.submenu.bulkImportEnv', 'Bulk Import ENV'),
            description: T('adminPanel.customize.cardBulkImportEnvDescription', 'Import table snapshot ZIP (merge or replace) when enabled.'),
            iconClass: 'fas fa-box-open',
            submenuI18nKey: 'adminPanel.submenu.bulkImportEnv',
            requireDataMigrationSnapshot: true
        }
    ];
    renderAdminCardHub(contentArea, {
        title: T('adminPanel.customize.title', 'Customize & Configure'),
        description: T('adminPanel.customize.description', 'Customize system settings and configurations.'),
        cards: cards
    });
}

// Activity Logs Content
function showActivityLogsContent(contentArea) {
    const title = window.I18n ? window.I18n.t('adminPanel.activityLogs.title') : 'Admin Activity Logs';
    const description = window.I18n ? window.I18n.t('adminPanel.activityLogs.description') : 'View and manage administrative activity logs.';
    const timestamp = window.I18n ? window.I18n.t('adminPanel.common.timestamp') : 'Timestamp';
    const user = window.I18n ? window.I18n.t('adminPanel.common.user') : 'User';
    const action = window.I18n ? window.I18n.t('adminPanel.common.action') : 'Action';
    const details = window.I18n ? window.I18n.t('adminPanel.common.details') : 'Details';
    const userCreated = window.I18n ? window.I18n.t('adminPanel.activityLogs.userCreated') : 'User Created';
    const systemUpdate = window.I18n ? window.I18n.t('adminPanel.activityLogs.systemUpdate') : 'System Update';
    const newUserAccountCreated = window.I18n ? window.I18n.t('adminPanel.activityLogs.newUserAccountCreated') : 'New user account created';
    const systemConfigurationUpdated = window.I18n ? window.I18n.t('adminPanel.activityLogs.systemConfigurationUpdated') : 'System configuration updated';
    
    contentArea.innerHTML = `
        <div class="activity-logs-content">
            <h2>${title}</h2>
            <p>${description}</p>
            <div class="logs-table">
                <table>
                    <thead>
                        <tr>
                            <th>${timestamp}</th>
                            <th>${user}</th>
                            <th>${action}</th>
                            <th>${details}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>2024-01-15 10:30</td>
                            <td>Marwan</td>
                            <td>${userCreated}</td>
                            <td>${newUserAccountCreated}</td>
                        </tr>
                        <tr>
                            <td>2024-01-15 09:15</td>
                            <td>Admin</td>
                            <td>${systemUpdate}</td>
                            <td>${systemConfigurationUpdated}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    `;
}

// Show dashboard content — defined in dashboard.js (loaded via loadAdminPanelModules)

// Navigation history management
let navigationHistory = [];
let currentNavigationIndex = -1;

// Add navigation entry to history
function addToNavigationHistory(title, contentFunction, headerFunction = null) {
    // Remove any entries after current index (when navigating back and then forward)
    navigationHistory = navigationHistory.slice(0, currentNavigationIndex + 1);

    // Add new entry
    navigationHistory.push({
        title: title,
        contentFunction: contentFunction,
        headerFunction: headerFunction,
        timestamp: Date.now()
    });

    currentNavigationIndex = navigationHistory.length - 1;

    // Limit history to 10 entries
    if (navigationHistory.length > 10) {
        navigationHistory.shift();
        currentNavigationIndex--;
    }
}

// Go back function
function goBack() {
    // Check if there's an active submenu item
    const activeSubmenuItem = document.querySelector('.submenu-item.active');

    if (activeSubmenuItem) {
        // Get the parent container
        const parentContainer = activeSubmenuItem.closest('.nav-item-container');

        if (parentContainer) {
            // Get the parent menu name
            const parentNavItem = parentContainer.querySelector('.nav-item span');
            if (parentNavItem) {
                const parentText = parentNavItem.textContent;

                // Clear active submenu items
                document.querySelectorAll('.submenu-item').forEach(sub => sub.classList.remove('active'));

                // Navigate to parent menu
                handleNavigation(parentText);
                return;
            }
        }
    }

    // If no active submenu, use the original navigation history logic
    if (currentNavigationIndex > 0) {
        currentNavigationIndex--;
        const previousEntry = navigationHistory[currentNavigationIndex];

        if (previousEntry) {
            // Restore header if custom header function exists
            if (previousEntry.headerFunction) {
                previousEntry.headerFunction();
            } else {
                restoreOriginalHeader();
            }

            // Show previous content
            const contentArea = document.querySelector('.content-area');
            if (previousEntry.contentFunction && contentArea) {
                previousEntry.contentFunction(contentArea);
            }

            // Update active states based on the previous entry
            updateActiveStates(previousEntry.title);
        }
    } else {
        // If no history, go to dashboard
        restoreOriginalHeader();
        const contentArea = document.querySelector('.content-area');
        showDashboardContent(contentArea);
        const dashboardTitle = window.I18n ? window.I18n.t('adminPanel.navigation.adminDashboard') : 'Admin Dashboard';
        updateActiveStates(dashboardTitle);
    }
}

// Update active states based on navigation title
function updateActiveStates(title) {
    // Remove all active states
    document.querySelectorAll('.nav-item').forEach(nav => nav.classList.remove('active'));
    document.querySelectorAll('.nav-item-container').forEach(container => container.classList.remove('active'));
    document.querySelectorAll('.submenu-item').forEach(sub => sub.classList.remove('active'));

    // Set active state based on title
    const adminDashboard = window.I18n ? window.I18n.t('adminPanel.navigation.adminDashboard') : 'Admin Dashboard';
    if (title === adminDashboard || title === 'Admin Dashboard') {
        document.querySelector('.nav-item[data-nav="dashboard"]')?.classList.add('active');
    } else if (title === 'Application Settings') {
        const customizeContainer = document.querySelector('.nav-item-container[data-submenu="customize-configure"]');
        if (customizeContainer) {
            customizeContainer.classList.add('active');
            customizeContainer.classList.add('expanded');
        }
        document.querySelector('.submenu-item[data-submenu="Application Settings"]')?.classList.add('active');
    } else if (title === 'Quick Links') {
        const customizeContainer = document.querySelector('.nav-item-container[data-submenu="customize-configure"]');
        if (customizeContainer) {
            customizeContainer.classList.add('active');
            customizeContainer.classList.add('expanded');
        }
        document.querySelector('.submenu-item[data-submenu="Application Settings"]')?.classList.add('active');
    }
    // Add more conditions for other navigation items as needed
}

// Initialize navigation history with dashboard
function initializeNavigationHistory() {
    const dashboardTitle = window.I18n ? window.I18n.t('adminPanel.navigation.adminDashboard') : 'Admin Dashboard';
    // Use indirect reference so it works even if dashboard.js hasn't loaded yet
    addToNavigationHistory(dashboardTitle, function(contentArea) {
        if (typeof showDashboardContent === 'function') {
            showDashboardContent(contentArea);
        }
    });
}

// Enable search for both tables
function enableSearchForBothTables() {
    // Implementation for enabling search functionality
    console.log('Enabling search for both tables...');
}

// Update role type dropdown
function updateRoleTypeDropdown(roleTypes) {
    const roleTypeSelect = document.querySelector('[data-column="role-type"]');
    if (!roleTypeSelect) return;

    const currentValue = roleTypeSelect.value;
    const allRoleTypes = window.I18n ? window.I18n.t('adminPanel.common.allRoleTypes') : 'All Role Types';
    roleTypeSelect.innerHTML = `<option value="">${allRoleTypes}</option>`;

    roleTypes.forEach(roleType => {
        const option = document.createElement('option');
        option.value = roleType;
        option.textContent = roleType;
        roleTypeSelect.appendChild(option);
    });

    if (currentValue) {
        roleTypeSelect.value = currentValue;
    }
}

// Go to Roles & Responsibilities edit mode
function goToRolesResponsibilitiesEditMode() {
    console.log('🚀 Going directly to Roles & Responsibilities edit mode...');

    try {
        // تحديث العنوان
        updateSystemTitle('Roles & Responsibilities - Edit Mode', 'Edit and manage organizational roles and their responsibilities.');

        // الحصول على منطقة المحتوى
        const contentArea = document.querySelector('.content-area');
        if (!contentArea) {
            return;
        }

        highlightSubmenuItem('Roles & Responsibilities');

        showRolesResponsibilitiesContent(contentArea);

        setTimeout(() => {
            enterEditMode();
        }, 100);

    } catch (error) {
        console.error('❌ Error in goToRolesResponsibilitiesEditMode:', error);
    }
}

// Show role permissions content (wrapper function)
function showRolePermissionsContent() {
    try {
        updateSystemTitle('Role Permissions', 'Control permissions for each role in the system.');

        highlightSubmenuItem('adminPanel.submenu.rolePermissions');

        const contentArea = document.querySelector('.content-area');
        if (!contentArea) {
            return;
        }

        // Delegate to the main renderer that accepts the container
        showRolePermissionsContent(contentArea);

    } catch (error) {
        console.error('Error in showRolePermissionsContent:', error);
    }
}

// Show default workflows content
function showDefaultWorkflowsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.defaultWorkflows');

    const pageTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultWorkflows.pageTitle') : 'Default Workflows';
    const pageDescription = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultWorkflows.pageDescription') : 'Configure and manage default workflows for system processes.';
    const approvalWorkflowsTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultWorkflows.approvalWorkflows.title') : 'Approval Workflows';
    const approvalWorkflowsDesc = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultWorkflows.approvalWorkflows.description') : 'Standard approval processes for system changes';
    const changeWorkflowsTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultWorkflows.changeWorkflows.title') : 'Change Workflows';
    const changeWorkflowsDesc = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultWorkflows.changeWorkflows.description') : 'Processes for managing system modifications';

    contentArea.innerHTML = `
        <div class="default-workflows-content">
            <h2>${pageTitle}</h2>
            <p>${pageDescription}</p>
            <div class="workflow-types">
                <div class="workflow-type">
                    <i class="fas fa-cogs"></i>
                    <h3>${approvalWorkflowsTitle}</h3>
                    <p>${approvalWorkflowsDesc}</p>
                </div>
                <div class="workflow-type">
                    <i class="fas fa-exchange-alt"></i>
                    <h3>${changeWorkflowsTitle}</h3>
                    <p>${changeWorkflowsDesc}</p>
                </div>
            </div>
        </div>
    `;
}

// Show default change requests content
function showDefaultChangeRequestsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.defaultChangeRequests');

    const pageTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultChangeRequests.pageTitle') : 'Default Change Requests';
    const pageDescription = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultChangeRequests.pageDescription') : 'Manage standard change request templates and processes.';
    const systemUpdatesTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultChangeRequests.systemUpdates.title') : 'System Updates';
    const systemUpdatesDesc = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultChangeRequests.systemUpdates.description') : 'Standard template for system modifications';
    const dataChangesTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultChangeRequests.dataChanges.title') : 'Data Changes';
    const dataChangesDesc = window.I18n ? window.I18n.t('adminPanel.operatingModel.defaultChangeRequests.dataChanges.description') : 'Template for data structure modifications';

    contentArea.innerHTML = `
        <div class="change-requests-content">
            <h2>${pageTitle}</h2>
            <p>${pageDescription}</p>
            <div class="change-request-types">
                <div class="request-type">
                    <i class="fas fa-exchange-alt"></i>
                    <h3>${systemUpdatesTitle}</h3>
                    <p>${systemUpdatesDesc}</p>
                </div>
                <div class="request-type">
                    <i class="fas fa-exchange-alt"></i>
                    <h3>${dataChangesTitle}</h3>
                    <p>${dataChangesDesc}</p>
                </div>
            </div>
        </div>
    `;
}

// Show roles responsibilities content
function showRolesResponsibilitiesContent(contentArea) {
    highlightSubmenuItem('Roles & Responsibilities');

    const pageTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.rolesResponsibilities.pageTitle') : 'Roles & Responsibilities';
    const pageSubtitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.rolesResponsibilities.pageSubtitle') : 'Define and manage organizational roles and their responsibilities within the data governance framework.';
    updateSystemTitle(pageTitle, pageSubtitle);

    const enterEditMode = window.I18n ? window.I18n.t('adminPanel.common.enterEditMode') : 'Enter Edit Mode';
    const primaryName = window.I18n ? window.I18n.t('adminPanel.common.primaryName') : 'Primary Name';
    const description = window.I18n ? window.I18n.t('adminPanel.common.description') : 'Description';
    const defaultCol = window.I18n ? window.I18n.t('adminPanel.common.default') : 'Default';
    const facet = window.I18n ? window.I18n.t('adminPanel.common.facet') : 'Facet';
    const roleType = window.I18n ? window.I18n.t('adminPanel.common.roleType') : 'Role Type';
    const searchPrimaryName = window.I18n ? window.I18n.t('adminPanel.common.searchPrimaryName') : 'Search Primary Name...';
    const searchDescription = window.I18n ? window.I18n.t('adminPanel.common.searchDescription') : 'Search Description...';
    const searchFacet = window.I18n ? window.I18n.t('adminPanel.common.searchFacet') : 'Search Facet...';
    const all = window.I18n ? window.I18n.t('adminPanel.common.all') : 'All';
    const yes = window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes';
    const no = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
    const allRoleTypes = window.I18n ? window.I18n.t('adminPanel.common.allRoleTypes') : 'All Role Types';
    const loadingRolesData = window.I18n ? window.I18n.t('adminPanel.common.loadingRolesData') : 'Loading roles data...';

    contentArea.innerHTML = `
        <div class="roles-responsibilities-content">
            <div class="content-header">
                <div class="header-actions">
                    <button class="btn btn-primary enter-edit-mode-btn" onclick="enterEditMode()">
                        <i class="fas fa-edit"></i>
                        ${enterEditMode}
                    </button>
                </div>
            </div>

            <div class="content-body">
                <div class="data-table-container">
                    <div class="table-responsive">
                        <table class="data-table" id="rolesTable">
                            <thead>
                                <tr class="header-labels">
                                    <th>${primaryName}</th>
                                    <th>${description}</th>
                                    <th>${defaultCol}</th>
                                    <th>${facet}</th>
                                    <th>${roleType}</th>
                                </tr>
                                <tr class="header-search">
                                    <th>
                                        <div class="search-header-cell">
                                            <input type="text" class="table-search-input" placeholder="${searchPrimaryName}" data-column="primary-name">
                                        </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                            <input type="text" class="table-search-input" placeholder="${searchDescription}" data-column="description">
                                        </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                        <select class="table-search-select" data-column="default">
                                                <option value="">${all}</option>
                                            <option value="yes">${yes}</option>
                                            <option value="no">${no}</option>
                                        </select>
                                        </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                            <input type="text" class="table-search-input" placeholder="${searchFacet}" data-column="facet">
                                        </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                            <select class="table-search-select" data-column="role-type">
                                                <option value="">${allRoleTypes}</option>
                                        </select>
                                        </div>
                                    </th>
                                </tr>
                            </thead>
                            <tbody id="rolesTableBody">
                                <tr>
                                    <td colspan="5" class="loading-row">
                                        <div class="loading-spinner">
                                            <i class="fas fa-spinner fa-spin"></i>
                                            ${loadingRolesData}
                                        </div>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    `;

    // جلب البيانات من قاعدة البيانات
    loadRolesData();

    setTimeout(() => {
        const primaryNameInput = document.querySelector('[data-column="primary-name"]');
        const descriptionInput = document.querySelector('[data-column="description"]');
        const defaultSelect = document.querySelector('[data-column="default"]');
        const facetInput = document.querySelector('[data-column="facet"]');
        const roleTypeSelect = document.querySelector('[data-column="role-type"]');

        if (roleTypeSelect) {
        } else {
            const headerSearch = document.querySelector('.header-search');
            if (headerSearch) {
                const roleTypeHeader = headerSearch.querySelector('th:nth-child(5)');
                if (roleTypeHeader) {
                    const allRoleTypes = window.I18n ? window.I18n.t('adminPanel.common.allRoleTypes') : 'All Role Types';
                    roleTypeHeader.innerHTML = `
                        <div class="search-header-cell">
                            <select class="table-search-select" data-column="role-type">
                                <option value="">${allRoleTypes}</option>
                            </select>
                        </div>
                    `;

                    if (window.cachedRoleTypes) {
                        updateRoleTypeDropdown(window.cachedRoleTypes);
                    }
                }
            }
        }
    }, 500);
}

// Show role assignment content - wrapper function
function showRoleAssignmentContent(contentArea) {
    // This function is now handled by role-assignment.js
}

// Role assignments functions moved to role-assignment.js

// All role assignment functions moved to role-assignment.js

// Show change request systems content
function showChangeRequestSystemsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.changeRequestSystems');

    const pageTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.changeRequestSystems.pageTitle') : 'Change Request Systems';
    const pageDescription = window.I18n ? window.I18n.t('adminPanel.operatingModel.changeRequestSystems.pageDescription') : 'Configure and manage change request systems and workflows.';
    const itChangeManagementTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.changeRequestSystems.itChangeManagement.title') : 'IT Change Management';
    const itChangeManagementDesc = window.I18n ? window.I18n.t('adminPanel.operatingModel.changeRequestSystems.itChangeManagement.description') : 'System for managing IT infrastructure changes';
    const businessProcessChangesTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.changeRequestSystems.businessProcessChanges.title') : 'Business Process Changes';
    const businessProcessChangesDesc = window.I18n ? window.I18n.t('adminPanel.operatingModel.changeRequestSystems.businessProcessChanges.description') : 'System for managing business process modifications';

    contentArea.innerHTML = `
        <div class="change-systems-content">
            <h2>${pageTitle}</h2>
            <p>${pageDescription}</p>
            <div class="systems-list">
                <div class="system-item">
                    <i class="fas fa-cogs"></i>
                    <div class="system-info">
                        <h3>${itChangeManagementTitle}</h3>
                        <p>${itChangeManagementDesc}</p>
                    </div>
                </div>
                <div class="system-item">
                    <i class="fas fa-cogs"></i>
                    <div class="system-info">
                        <h3>${businessProcessChangesTitle}</h3>
                        <p>${businessProcessChangesDesc}</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}

// Show licensed users content
function showLicensedUsersContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.licensedUsers');
}

// Show periodic review content
function showPeriodicReviewContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.periodicReview');

    const pageTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.periodicReview.pageTitle') : 'Periodic Review of Objects';
    const pageDescription = window.I18n ? window.I18n.t('adminPanel.operatingModel.periodicReview.pageDescription') : 'Schedule and manage periodic reviews of system objects and data.';
    const monthlyReviewsTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.periodicReview.monthlyReviews.title') : 'Monthly Reviews';
    const monthlyReviewsDesc = window.I18n ? window.I18n.t('adminPanel.operatingModel.periodicReview.monthlyReviews.description') : 'Critical system components reviewed monthly';
    const quarterlyReviewsTitle = window.I18n ? window.I18n.t('adminPanel.operatingModel.periodicReview.quarterlyReviews.title') : 'Quarterly Reviews';
    const quarterlyReviewsDesc = window.I18n ? window.I18n.t('adminPanel.operatingModel.periodicReview.quarterlyReviews.description') : 'Comprehensive reviews every quarter';

    contentArea.innerHTML = `
        <div class="periodic-review-content">
            <h2>${pageTitle}</h2>
            <p>${pageDescription}</p>
            <div class="review-schedule">
                <div class="review-item">
                    <i class="fas fa-calendar-check"></i>
                    <div class="review-info">
                        <h3>${monthlyReviewsTitle}</h3>
                        <p>${monthlyReviewsDesc}</p>
                    </div>
                </div>
                <div class="review-item">
                    <i class="fas fa-calendar-check"></i>
                    <div class="review-info">
                        <h3>${quarterlyReviewsTitle}</h3>
                        <p>${quarterlyReviewsDesc}</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}

// Placeholder functions for roles responsibilities
function enterEditMode() {
    console.log('Entering edit mode...');
}

function loadRolesData() {
    console.log('Loading roles data...');
}

// All role assignment functions moved to role-assignment.js

// Meta-Model Administration Content Functions
// These functions are now loaded from their respective JS files:
// - showSegmentsContent() from view/segments/segments-list.js
// - showDropdownConfigurationsContent() from meta-model/dropdown-configs.js
// - showStaticPageEditorContent() from meta-model/static-page-editor.js

// Operational Management Content Functions
// These functions are now loaded from their respective JS files:
// - showDataOnboardingRulesContent() from operational-management/data-onboarding-rules.js
// - showManageLocksContent() from operational-management/manage-locks.js
// - showLockedUsersContent() from operational-management/locked-users.js
// - showOwnershipTransferContent() from operational-management/ownership-transfer.js
// - showDownloadLogsContent() from operational-management/download-logs.js

// Customize & Configure Content Functions
// These functions are now loaded from their respective JS files:
// - showChangeLogoContent() from customize/change-logo.js
// - showCustomizeStylesContent() from customize/customize-styles.js
// - showSystemSettingsContent() from customize/system-settings.js
// - showApplicationSettingsContent() from customize/app-settings.js

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', function () {
    // Check if API service is available
    if (window.BUDG_API_SERVICE) {
        console.log('API service is available');
    } else {
        console.warn('API service is not available - some features may not work');
    }

    // Load modules (initialization will happen after modules are loaded)
    loadAdminPanelModules();
});

// Global error handler
window.addEventListener('error', function (event) {
    console.error('Global error:', event.error);
    console.error('Error details:', {
        message: event.message,
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno
    });
});

// Handle unhandled promise rejections
window.addEventListener('unhandledrejection', function (event) {
    console.error('Unhandled promise rejection:', event.reason);
});
