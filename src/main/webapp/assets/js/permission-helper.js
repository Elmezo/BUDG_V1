/**
 * Permission Helper - Client-side permission checking
 * 
 * This helper fetches and caches user permissions from the server
 * and provides utility functions to check permissions for showing/hiding UI elements
 * 
 * Usage:
 * 1. Call initPermissions() on page load
 * 2. Use canCreate(module), canEdit(module), canDelete(module) to check permissions
 * 3. Use applyPermissions(module, options) to automatically show/hide elements
 */

class PermissionHelper {
    constructor() {
        this.permissions = {};
        this.isAdmin = false;
        this.userId = null;
        this.loaded = false;
        this.loadPromise = null;
    }

    /**
     * Initialize permissions by fetching from server
     * @param {string} moduleName Optional specific module to fetch
     * @returns {Promise} Resolves when permissions are loaded
     */
    async init(moduleName = null) {
        if (this.loadPromise) {
            return this.loadPromise;
        }

        this.loadPromise = this._fetchPermissions(moduleName);
        await this.loadPromise;
        this.loaded = true;
        return this.permissions;
    }

    /**
     * Fetch permissions from server
     */
    async _fetchPermissions(moduleName = null) {
        try {
            const url = moduleName 
                ? `/api/user/permissions/${encodeURIComponent(moduleName)}`
                : '/api/user/permissions';
            
            const resp = await fetch(url, {
                method: 'GET',
                credentials: 'include'
            });

            if (!resp.ok) {
                console.error('Failed to fetch permissions:', resp.status);
                return;
            }

            const data = await resp.json();
            if (data.success) {
                this.userId = data.userId;
                this.isAdmin = data.isAdmin || false;
                
                if (moduleName) {
                    // Store module-specific permissions
                    this.permissions[moduleName] = {
                        canView: data.canView,
                        canCreate: data.canCreate,
                        canEdit: data.canEdit,
                        canDelete: data.canDelete
                    };
                } else {
                    // Store all permissions
                    this.permissions = data.permissions || {};
                }
            }
        } catch (error) {
            console.error('Error fetching permissions:', error);
        }
    }

    /**
     * Get permissions for a specific module
     * @param {string} moduleName Module name
     * @returns {Promise<Object>} Module permissions
     */
    async getModulePermissions(moduleName) {
        // If we already have this module's permissions cached
        if (this.permissions[moduleName]) {
            return this.permissions[moduleName];
        }

        // Fetch specific module permissions
        try {
            const resp = await fetch(`/api/user/permissions/${encodeURIComponent(moduleName)}`, {
                method: 'GET',
                credentials: 'include'
            });

            if (resp.ok) {
                const data = await resp.json();
                if (data.success) {
                    this.isAdmin = data.isAdmin || false;
                    this.permissions[moduleName] = {
                        canView: data.canView,
                        canCreate: data.canCreate,
                        canEdit: data.canEdit,
                        canDelete: data.canDelete
                    };
                    return this.permissions[moduleName];
                }
            }
        } catch (error) {
            console.error('Error fetching module permissions:', error);
        }

        // Default permissions if fetch fails
        return {
            canView: true,
            canCreate: false,
            canEdit: false,
            canDelete: false
        };
    }

    /**
     * Check if user is admin or super admin
     * @returns {boolean}
     */
    checkIsAdmin() {
        return this.isAdmin;
    }

    /**
     * Check if user can create in a module
     * @param {string} moduleName Module name
     * @returns {boolean}
     */
    canCreate(moduleName) {
        if (this.isAdmin) return true;
        const perms = this.permissions[moduleName];
        if (!perms) return false;
        
        // Check for array format (from all permissions) or object format
        if (Array.isArray(perms)) {
            return perms.includes('New') || perms.includes('Create') || perms.includes('Write');
        }
        return perms.canCreate || false;
    }

    /**
     * Check if user can edit in a module
     * @param {string} moduleName Module name
     * @returns {boolean}
     */
    canEdit(moduleName) {
        if (this.isAdmin) return true;
        const perms = this.permissions[moduleName];
        if (!perms) return false;
        
        if (Array.isArray(perms)) {
            return perms.includes('Edit') || perms.includes('Write');
        }
        return perms.canEdit || false;
    }

    /**
     * Check if user can view in a module
     * All authenticated users can view
     * @param {string} moduleName Module name
     * @returns {boolean}
     */
    canView(moduleName) {
        return this.userId > 0; // All authenticated users can view
    }

    /**
     * Check if user can delete in a module
     * Only admins can delete
     * @param {string} moduleName Module name
     * @returns {boolean}
     */
    canDelete(moduleName) {
        return this.isAdmin; // Only admins can delete
    }

    /**
     * Apply permissions to UI elements
     * Hides elements that the user doesn't have permission for
     * 
     * @param {string} moduleName Module name
     * @param {Object} options Element selectors
     * @param {string} options.createBtn Selector for create button
     * @param {string} options.editBtn Selector for edit button
     * @param {string} options.deleteBtn Selector for delete button
     */
    async applyPermissions(moduleName, options = {}) {
        const perms = await this.getModulePermissions(moduleName);
        
        // Hide/show create button
        if (options.createBtn) {
            const createBtns = document.querySelectorAll(options.createBtn);
            createBtns.forEach(btn => {
                btn.style.display = perms.canCreate ? '' : 'none';
            });
        }
        
        // Hide/show edit button
        if (options.editBtn) {
            const editBtns = document.querySelectorAll(options.editBtn);
            editBtns.forEach(btn => {
                btn.style.display = perms.canEdit ? '' : 'none';
            });
        }
        
        // Hide/show delete button
        if (options.deleteBtn) {
            const deleteBtns = document.querySelectorAll(options.deleteBtn);
            deleteBtns.forEach(btn => {
                btn.style.display = perms.canDelete ? '' : 'none';
            });
        }
        
        return perms;
    }

    /**
     * Show/hide element based on permission
     * @param {HTMLElement|string} element Element or selector
     * @param {boolean} hasPermission Whether user has permission
     */
    static toggleElement(element, hasPermission) {
        const el = typeof element === 'string' ? document.querySelector(element) : element;
        if (el) {
            el.style.display = hasPermission ? '' : 'none';
        }
    }

    /**
     * Enable/disable element based on permission
     * @param {HTMLElement|string} element Element or selector
     * @param {boolean} hasPermission Whether user has permission
     */
    static setEnabled(element, hasPermission) {
        const el = typeof element === 'string' ? document.querySelector(element) : element;
        if (el) {
            el.disabled = !hasPermission;
            if (!hasPermission) {
                el.classList.add('disabled');
                el.title = 'You do not have permission for this action';
            } else {
                el.classList.remove('disabled');
                el.title = '';
            }
        }
    }

    /**
     * Check if user is a stakeholder on a specific object
     * @param {string} moduleName Module name (e.g., "System", "Data Sets", "Glossary")
     * @param {number} objectId Object ID
     * @returns {Promise<boolean>} True if user is stakeholder or admin, false otherwise
     */
    async checkIsStakeholder(moduleName, objectId) {
        if (!moduleName || !objectId || objectId <= 0) {
            return false;
        }

        // Admins bypass stakeholder check
        if (this.isAdmin) {
            return true;
        }

        try {
            const resp = await fetch(
                `/api/check-stakeholder/${encodeURIComponent(moduleName)}/${objectId}`,
                {
                    method: 'GET',
                    credentials: 'include'
                }
            );

            if (resp.ok) {
                const data = await resp.json();
                return data.isStakeholder === true;
            } else {
                console.warn(`[PermissionHelper] Failed to check stakeholder status: ${resp.status}`);
                return false; // Fail securely - deny access if check fails
            }
        } catch (error) {
            console.error('[PermissionHelper] Error checking stakeholder status:', error);
            return false; // Fail securely
        }
    }

    /**
     * Check if user can edit a specific object (has role permission AND is stakeholder)
     * @param {string} moduleName Module name
     * @param {number} objectId Object ID
     * @returns {Promise<boolean>} True if user can edit (has permission AND is stakeholder), false otherwise
     */
    async canEditObject(moduleName, objectId) {
        // Admins can always edit
        if (this.isAdmin) {
            return { canEdit: true, isAdmin: true, isStakeholder: true };
        }

        // Check role-based edit permission first
        const hasRolePermission = this.canEdit(moduleName);
        if (!hasRolePermission) {
            return { canEdit: false, isAdmin: false, isStakeholder: false };
        }

        // If has role permission, also check stakeholder status
        const isStakeholder = await this.checkIsStakeholder(moduleName, objectId);
        return { canEdit: isStakeholder, isAdmin: false, isStakeholder: isStakeholder };
    }
}

// Global instance
window.permissionHelper = new PermissionHelper();

// Convenience functions
window.initPermissions = async (moduleName) => window.permissionHelper.init(moduleName);
window.checkCanCreate = (moduleName) => window.permissionHelper.canCreate(moduleName);
window.checkCanEdit = (moduleName) => window.permissionHelper.canEdit(moduleName);
window.checkCanDelete = (moduleName) => window.permissionHelper.canDelete(moduleName);
window.checkIsAdminUser = () => window.permissionHelper.checkIsAdmin();
window.applyModulePermissions = async (moduleName, options) => window.permissionHelper.applyPermissions(moduleName, options);
window.checkIsStakeholder = async (moduleName, objectId) => window.permissionHelper.checkIsStakeholder(moduleName, objectId);
window.checkCanEditObject = async (moduleName, objectId) => window.permissionHelper.canEditObject(moduleName, objectId);