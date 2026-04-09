/**
 * Authentication Helper Utility
 * Provides centralized authentication handling with silent error management
 */

class AuthHelper {
    static async checkAuthStatus() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });
            
            if (response.ok) {
                return await response.json();
            } else if (response.status === 401) {
                // Try to refresh token
                const refreshed = await this.tryRefreshToken();
                if (refreshed) {
                    const meAgain = await fetch('/api/me', { 
                        method: 'GET', 
                        credentials: 'include' 
                    });
                    if (meAgain.ok) {
                        return await meAgain.json();
                    }
                }
            }
            return null;
        } catch (error) {
            // Silently handle auth errors
            return null;
        }
    }

    static async tryRefreshToken() {
        try {
            const response = await fetch('/api/refresh', { 
                method: 'POST', 
                credentials: 'include' 
            });
            return response.ok;
        } catch (error) {
            return false;
        }
    }

    static async isAuthenticated() {
        const userData = await this.checkAuthStatus();
        return userData !== null;
    }

    static async isAdmin() {
        const userData = await this.checkAuthStatus();
        if (!userData) return false;
        
        const role = (userData.role || '').toString().toLowerCase();
        return role === 'admin' || 
               role === 'super admin' || 
               role === 'super-admin' || 
               role === 'super_admin' || 
               role === 'suber admin';
    }

    static async getCurrentUser() {
        return await this.checkAuthStatus();
    }

    static async logVisit(entity, entityId, route) {
        try {
            const userData = await this.checkAuthStatus();
            if (userData && window.BUDG_API_SERVICE) {
                await window.BUDG_API_SERVICE.logVisit({ 
                    entity: entity, 
                    entityId: String(entityId), 
                    route: route 
                });
            }
        } catch (error) {
            // Silently handle visit logging errors
        }
    }

    static async hideEditControlsIfNotAdmin(elementSelector = null) {
        const isAdmin = await this.isAdmin();
        if (!isAdmin) {
            if (elementSelector) {
                const elements = document.querySelectorAll(elementSelector);
                elements.forEach(el => {
                    el.style.display = 'none';
                    el.style.visibility = 'hidden';
                });
            } else {
                // Hide common edit controls
                const editSelectors = [
                    '.edit-button',
                    '.delete-button',
                    '.add-button',
                    '.create-button',
                    '.update-button',
                    '[data-action="edit"]',
                    '[data-action="delete"]',
                    '[data-action="create"]'
                ];
                
                editSelectors.forEach(selector => {
                    const elements = document.querySelectorAll(selector);
                    elements.forEach(el => {
                        el.style.display = 'none';
                        el.style.visibility = 'hidden';
                    });
                });
            }
        }
    }
}

// Make AuthHelper available globally
window.AuthHelper = AuthHelper;
