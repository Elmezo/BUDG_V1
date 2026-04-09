/**
 * User API Tokens Management Module
 * Handles display, generation, and deletion of user refresh tokens
 * Security: Only operates on the authenticated user's tokens
 */

(function(window) {
    'use strict';

    // Track selected tokens for bulk operations
    let selectedTokens = new Set();
    const TOKENS_PAGE_SIZE = 50;

    async function fetchUserTokensPage(offset, limit = TOKENS_PAGE_SIZE) {
        const params = new URLSearchParams({
            limit: String(limit),
            offset: String(offset)
        });
        const response = await fetch(`/api/user/tokens?${params}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        if (!response.ok) {
            if (response.status === 401) {
                throw new Error('You must be logged in to view API tokens');
            }
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        const data = await response.json();
        if (!data.success) {
            throw new Error(data.error || 'Failed to load tokens');
        }
        return {
            tokens: Array.isArray(data.tokens) ? data.tokens : [],
            total: Number.isFinite(data.total) ? data.total : 0,
            hasMore: data.has_more === true
        };
    }

    /**
     * Main function to load and display user tokens
     * @param {HTMLElement} container - Container element to render tokens table
     */
    async function loadUserTokens(container) {
        if (!container) {
            console.error('Container element is required');
            return;
        }

        try {
            // Show loading state
            container.innerHTML = `
                <div class="people-empty" style="padding: 2rem;">
                    <i class="fas fa-spinner fa-spin"></i>
                    <span>Loading tokens...</span>
                </div>
            `;

            // Reset selection on fresh load
            selectedTokens.clear();
            const firstPage = await fetchUserTokensPage(0, TOKENS_PAGE_SIZE);
            container.__userTokensState = {
                tokens: firstPage.tokens,
                total: firstPage.total,
                offset: firstPage.tokens.length,
                hasMore: firstPage.hasMore,
                loadingMore: false
            };

            // Render first page
            renderTokensTable(container, container.__userTokensState.tokens, container.__userTokensState);

        } catch (error) {
            console.error('Error loading tokens:', error);
            container.innerHTML = `
                <div class="people-empty" style="padding: 2rem;">
                    <i class="fas fa-exclamation-triangle" style="color: #ef4444;"></i>
                    <span>Failed to load tokens: ${escapeHtml(error.message)}</span>
                </div>
            `;
        }
    }

    /**
     * Render tokens table with Actions dropdown
     * @param {HTMLElement} container - Container element
     * @param {Array} tokens - Array of token objects
     */
    function renderTokensTable(container, tokens, state = {}) {

        // Check if there are any tokens
        if (!tokens || tokens.length === 0) {
            container.innerHTML = `
                <div style="padding: 1.5rem;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                        <h3 style="margin: 0; font-size: 1rem; font-weight: 600;">GENERATED REFRESH TOKENS</h3>
                        <button onclick="window.userTokensModule.generateNewToken()" 
                                class="btn-primary" 
                                style="padding: 0.5rem 1rem; border: none; border-radius: 4px; background: #2563eb; color: white; cursor: pointer; font-size: 0.875rem;">
                            <i class="fas fa-plus" style="margin-right: 0.5rem;"></i>Generate New
                        </button>
                    </div>
                    <div class="people-empty">
                        <i class="fas fa-key"></i>
                        <span>No API tokens found</span>
                    </div>
                </div>
            `;
            return;
        }

        // Count expired tokens
        const now = new Date();
        const expiredCount = tokens.filter(token => new Date(token.expires_at) < now || token.is_revoked).length;

        // Build table HTML
        const tableHtml = `
            <div style="padding: 1.5rem; position: relative; overflow: visible;">
                <!-- Header with Actions dropdown -->
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; position: relative; z-index: 1;">
                    <h3 style="margin: 0; font-size: 1rem; font-weight: 600;">GENERATED REFRESH TOKENS</h3>
                    <div style="display: flex; gap: 0.5rem; align-items: center; position: relative; z-index: 10001;">
                        <span style="font-size: 0.875rem; color: #6b7280;">${tokens.length} of ${state.total || tokens.length} record${(state.total || tokens.length) !== 1 ? 's' : ''}</span>
                        <div class="dropdown" style="position: relative; z-index: 10001;">
                            <button type="button"
                                    onclick="window.userTokensModule.toggleActionsMenu(event)" 
                                    class="btn-actions" 
                                    style="padding: 0.5rem 1rem; border: 1px solid #d1d5db; border-radius: 4px; background: white; cursor: pointer; display: flex; align-items: center; gap: 0.5rem; font-size: 0.875rem;">
                                <i class="fas fa-bars"></i>
                                Actions
                                <i class="fas fa-chevron-down" style="font-size: 0.75rem;"></i>
                            </button>
                            <div id="actionsDropdown" class="dropdown-menu" style="display: none; position: absolute; right: 0; top: 100%; margin-top: 0.25rem; background: white; border: 1px solid #d1d5db; border-radius: 4px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 10px 15px -3px rgba(0,0,0,0.1); min-width: 200px; z-index: 10001 !important; visibility: visible; opacity: 1; overflow: visible;">
                                <button type="button" onclick="window.userTokensModule.generateNewToken()" class="dropdown-item" style="width: 100%; text-align: left; padding: 0.75rem 1rem; border: none; background: none; cursor: pointer; display: flex; align-items: center; gap: 0.75rem; font-size: 0.875rem;">
                                    <i class="fas fa-plus" style="width: 1.25rem;"></i>
                                    <span>Generate New</span>
                                </button>
                                <button type="button" id="deleteSelectedBtn" onclick="window.userTokensModule.deleteSelected()" class="dropdown-item" style="width: 100%; text-align: left; padding: 0.75rem 1rem; border: none; background: none; cursor: pointer; display: flex; align-items: center; gap: 0.75rem; font-size: 0.875rem;" ${selectedTokens.size === 0 ? 'disabled' : ''}>
                                    <i class="fas fa-trash" style="width: 1.25rem;"></i>
                                    <span>Delete Selected</span>
                                </button>
                                <button type="button" onclick="window.userTokensModule.deleteExpired()" class="dropdown-item" style="width: 100%; text-align: left; padding: 0.75rem 1rem; border: none; background: none; cursor: pointer; display: flex; align-items: center; gap: 0.75rem; font-size: 0.875rem;" ${expiredCount === 0 ? 'disabled' : ''}>
                                    <i class="fas fa-calendar-times" style="width: 1.25rem;"></i>
                                    <span>Delete Expired</span>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Tokens Table -->
                <div style="overflow-x: auto; border: 1px solid #e5e7eb; border-radius: 4px;">
                    <table style="width: 100%; border-collapse: collapse; font-size: 0.875rem;">
                        <thead>
                            <tr style="background: #f9fafb; border-bottom: 1px solid #e5e7eb;">
                                <th style="padding: 0.75rem 1rem; text-align: left; width: 50px;">
                                    <input type="checkbox" 
                                           id="selectAllTokens" 
                                           onchange="window.userTokensModule.toggleSelectAll(this.checked)"
                                           style="cursor: pointer;">
                                </th>
                                <th style="padding: 0.75rem 1rem; text-align: left; font-weight: 600;">Refresh token</th>
                                <th style="padding: 0.75rem 1rem; text-align: left; font-weight: 600; width: 200px;">Valid until</th>
                                <th style="padding: 0.75rem 1rem; text-align: center; font-weight: 600; width: 100px;">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${tokens.map(token => renderTokenRow(token)).join('')}
                        </tbody>
                    </table>
                </div>
                <div style="margin-top: 1rem; display: flex; justify-content: flex-end;">
                    ${state.hasMore ? `
                        <button type="button" id="loadMoreTokensBtn" ${state.loadingMore ? 'disabled' : ''}
                                style="padding: 0.5rem 1rem; border: 1px solid #d1d5db; border-radius: 4px; background: white; color: #374151; cursor: pointer; font-size: 0.875rem;">
                            ${state.loadingMore ? 'Loading...' : 'Load 50 more'}
                        </button>
                    ` : '<span style="font-size: 0.875rem; color: #6b7280;">All loaded</span>'}
                </div>
            </div>
        `;

        container.innerHTML = tableHtml;
        
        // Restore checkbox states after rendering
        selectedTokens.forEach(tokenId => {
            const checkbox = document.querySelector(`.token-checkbox[data-token-id="${tokenId}"]`);
            if (checkbox && !checkbox.disabled) {
                checkbox.checked = true;
            }
        });
        
        // Update actions menu after rendering
        updateActionsMenu();
        const loadMoreBtn = document.getElementById('loadMoreTokensBtn');
        if (loadMoreBtn) {
            loadMoreBtn.addEventListener('click', () => loadMoreUserTokens(container));
        }
    }

    async function loadMoreUserTokens(container) {
        if (!container || !container.__userTokensState) return;
        const state = container.__userTokensState;
        if (state.loadingMore || !state.hasMore) return;

        state.loadingMore = true;
        renderTokensTable(container, state.tokens, state);
        try {
            const nextPage = await fetchUserTokensPage(state.offset, TOKENS_PAGE_SIZE);
            state.tokens = state.tokens.concat(nextPage.tokens);
            state.offset += nextPage.tokens.length;
            state.total = nextPage.total;
            state.hasMore = nextPage.hasMore;
        } catch (error) {
            console.error('Error loading more tokens:', error);
            showNotification(`Failed to load more tokens: ${error.message}`, 'error');
        } finally {
            state.loadingMore = false;
            renderTokensTable(container, state.tokens, state);
        }
    }

    /**
     * Render a single token row
     * @param {Object} token - Token object
     * @returns {string} HTML string for the row
     */
    function renderTokenRow(token) {
        const isExpired = new Date(token.expires_at) < new Date() || token.is_revoked;
        const tokenId = escapeHtml(token.token_id);
        const expiresAt = formatDate(token.expires_at);
        const rowStyle = isExpired ? 'opacity: 0.6; background-color: #fef2f2;' : '';

        return `
            <tr style="border-bottom: 1px solid #e5e7eb; ${rowStyle}">
                <td style="padding: 0.75rem 1rem;">
                    <input type="checkbox" 
                           class="token-checkbox" 
                           data-token-id="${tokenId}"
                           onchange="window.userTokensModule.toggleTokenSelection('${tokenId}', this.checked)"
                           style="cursor: pointer;"
                           ${isExpired ? 'disabled' : ''}>
                </td>
                <td style="padding: 0.75rem 1rem; font-family: monospace; font-size: 0.8rem;">
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                        <span id="token-${tokenId}" style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${tokenId}</span>
                        <button onclick="window.userTokensModule.copyToken('${tokenId}')" 
                                title="Copy token ID"
                                style="padding: 0.25rem 0.5rem; border: 1px solid #d1d5db; border-radius: 3px; background: white; cursor: pointer; font-size: 0.75rem;">
                            <i class="fas fa-copy"></i>
                        </button>
                    </div>
                </td>
                <td style="padding: 0.75rem 1rem;">
                    ${expiresAt}
                    ${isExpired ? '<span style="color: #ef4444; font-weight: 500; margin-left: 0.5rem;">(Expired)</span>' : ''}
                </td>
                <td style="padding: 0.75rem 1rem; text-align: center;">
                    <button onclick="window.userTokensModule.deleteToken('${tokenId}')" 
                            title="Delete token"
                            style="padding: 0.375rem 0.75rem; border: 1px solid #ef4444; border-radius: 4px; background: white; color: #ef4444; cursor: pointer; font-size: 0.75rem;">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }

    /**
     * Toggle Actions dropdown menu
     */
    function toggleActionsMenu(event) {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }
        
        const dropdown = document.getElementById('actionsDropdown');
        if (!dropdown) {
            console.error('Actions dropdown not found');
            return;
        }
        
        const isVisible = dropdown.style.display === 'block';
        dropdown.style.display = isVisible ? 'none' : 'block';
        dropdown.style.visibility = isVisible ? 'hidden' : 'visible';
        dropdown.style.opacity = isVisible ? '0' : '1';
        
        console.log('Actions menu toggled:', dropdown.style.display);
        console.log('Dropdown element:', dropdown);
        console.log('Dropdown computed style:', window.getComputedStyle(dropdown).display);
        
        // Update menu button states when opening
        if (!isVisible) {
            updateActionsMenu();
        }
        
        // Setup document click handler after current event completes
        if (!isVisible) {
            // Use setTimeout to add listener after current event bubbling completes
            setTimeout(() => {
                setupDropdownCloseHandler();
            }, 10);
        } else {
            // Remove handler when closing manually
            removeDropdownCloseHandler();
        }
    }
    
    /**
     * Setup document click handler to close dropdown
     */
    function setupDropdownCloseHandler() {
        // Remove any existing handler first
        removeDropdownCloseHandler();
        // Add new handler
        document.addEventListener('click', closeDropdownOnClickOutside);
        console.log('Dropdown close handler added');
    }
    
    /**
     * Remove document click handler
     */
    function removeDropdownCloseHandler() {
        document.removeEventListener('click', closeDropdownOnClickOutside);
        console.log('Dropdown close handler removed');
    }

    /**
     * Close dropdown when clicking outside
     */
    function closeDropdownOnClickOutside(event) {
        const dropdown = document.getElementById('actionsDropdown');
        if (!dropdown || dropdown.style.display !== 'block') {
            removeDropdownCloseHandler();
            return;
        }
        
        // Check if click is inside the dropdown container
        const dropdownContainer = event.target.closest('.dropdown');
        
        if (!dropdownContainer) {
            // Click is outside - close dropdown
            dropdown.style.display = 'none';
            removeDropdownCloseHandler();
            console.log('Dropdown closed by outside click');
        }
    }

    /**
     * Toggle selection of all tokens
     */
    function toggleSelectAll(checked) {
        const checkboxes = document.querySelectorAll('.token-checkbox:not([disabled])');
        checkboxes.forEach(checkbox => {
            checkbox.checked = checked;
            const tokenId = checkbox.getAttribute('data-token-id');
            if (checked) {
                selectedTokens.add(tokenId);
            } else {
                selectedTokens.delete(tokenId);
            }
        });
        updateActionsMenu();
    }

    /**
     * Toggle selection of a single token
     */
    function toggleTokenSelection(tokenId, checked) {
        if (checked) {
            selectedTokens.add(tokenId);
        } else {
            selectedTokens.delete(tokenId);
        }
        console.log('Token selection toggled - tokenId:', tokenId, 'checked:', checked, 'selectedTokens.size:', selectedTokens.size);
        updateSelectAllCheckbox();
        updateActionsMenu();
    }

    /**
     * Update "Select All" checkbox state
     */
    function updateSelectAllCheckbox() {
        const selectAllCheckbox = document.getElementById('selectAllTokens');
        const checkboxes = document.querySelectorAll('.token-checkbox:not([disabled])');
        if (selectAllCheckbox && checkboxes.length > 0) {
            const allChecked = Array.from(checkboxes).every(cb => cb.checked);
            selectAllCheckbox.checked = allChecked;
        }
    }

    /**
     * Update Actions menu button states
     */
    function updateActionsMenu() {
        const deleteSelectedBtn = document.getElementById('deleteSelectedBtn');
        if (deleteSelectedBtn) {
            const hasSelection = selectedTokens.size > 0;
            deleteSelectedBtn.disabled = !hasSelection;
            deleteSelectedBtn.style.opacity = hasSelection ? '1' : '0.5';
            deleteSelectedBtn.style.cursor = hasSelection ? 'pointer' : 'not-allowed';
            console.log('Updated deleteSelectedBtn - selectedTokens.size:', selectedTokens.size, 'disabled:', !hasSelection);
        } else {
            console.warn('deleteSelectedBtn not found');
        }
    }

    /**
     * Generate a new API token
     */
    async function generateNewToken() {
        closeActionsDropdown();
        removeDropdownCloseHandler();

        if (!confirm('Are you sure you want to generate a new API token?')) {
            return;
        }

        try {
            const response = await fetch('/api/user/tokens', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.error || `HTTP ${response.status}`);
            }

            if (!data.success) {
                throw new Error(data.error || 'Failed to generate token');
            }

            // Show success message with the new token (only shown once)
            showTokenCreatedModal(data.token);

            // Reload tokens list
            const container = document.getElementById('myAPITokensContent');
            if (container) {
                await loadUserTokens(container);
            }

        } catch (error) {
            console.error('Error generating token:', error);
            alert(`Failed to generate token: ${error.message}`);
        }
    }

    /**
     * Show modal with newly created token
     */
    function showTokenCreatedModal(token) {
        const modal = document.createElement('div');
        modal.style.cssText = 'position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 9999;';
        
        modal.innerHTML = `
            <div style="background: white; padding: 2rem; border-radius: 8px; max-width: 600px; width: 90%;">
                <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem;">
                    <i class="fas fa-check-circle" style="color: #10b981; font-size: 1.5rem;"></i>
                    <h3 style="margin: 0; font-size: 1.25rem;">Token Created Successfully</h3>
                </div>
                <div style="background: #f9fafb; padding: 1rem; border-radius: 4px; margin-bottom: 1rem; border: 1px solid #e5e7eb;">
                    <p style="margin: 0 0 0.5rem 0; font-size: 0.875rem; color: #6b7280;">Token ID:</p>
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                        <code id="newTokenId" style="flex: 1; font-size: 0.875rem; word-break: break-all;">${escapeHtml(token.token_id)}</code>
                        <button onclick="window.userTokensModule.copyToClipboard('newTokenId')" 
                                style="padding: 0.375rem 0.75rem; border: 1px solid #d1d5db; border-radius: 4px; background: white; cursor: pointer;">
                            <i class="fas fa-copy"></i>
                        </button>
                    </div>
                </div>
                <div style="background: #fef3c7; padding: 1rem; border-radius: 4px; margin-bottom: 1rem; border: 1px solid #fbbf24;">
                    <p style="margin: 0; font-size: 0.875rem; color: #92400e;">
                        <i class="fas fa-exclamation-triangle" style="margin-right: 0.5rem;"></i>
                        <strong>Important:</strong> This is the only time you'll see this token. Store it securely.
                    </p>
                </div>
                <div style="text-align: right;">
                    <button onclick="this.closest('[style*=fixed]').remove()" 
                            style="padding: 0.5rem 1.5rem; border: none; border-radius: 4px; background: #2563eb; color: white; cursor: pointer; font-size: 0.875rem;">
                        Close
                    </button>
                </div>
            </div>
        `;
        
        document.body.appendChild(modal);
    }

    /**
     * Delete a single token
     */
    async function deleteToken(tokenId) {
        if (!confirm('Are you sure you want to delete this token? This action cannot be undone.')) {
            return;
        }

        try {
            const response = await fetch(`/api/user/tokens/${encodeURIComponent(tokenId)}`, {
                method: 'DELETE',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.error || `HTTP ${response.status}`);
            }

            if (!data.success) {
                throw new Error(data.error || 'Failed to delete token');
            }

            // Reload tokens list
            const container = document.getElementById('myAPITokensContent');
            if (container) {
                await loadUserTokens(container);
            }

            showNotification('Token deleted successfully', 'success');

        } catch (error) {
            console.error('Error deleting token:', error);
            showNotification(`Failed to delete token: ${error.message}`, 'error');
        }
    }

    /**
     * Delete selected tokens
     */
    async function deleteSelected() {
        closeActionsDropdown();
        removeDropdownCloseHandler();

        if (selectedTokens.size === 0) {
            showNotification('No tokens selected', 'warning');
            return;
        }

        if (!confirm(`Are you sure you want to delete ${selectedTokens.size} selected token(s)? This action cannot be undone.`)) {
            return;
        }

        try {
            const response = await fetch('/api/user/tokens/selected', {
                method: 'DELETE',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    token_ids: Array.from(selectedTokens)
                })
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.error || `HTTP ${response.status}`);
            }

            if (!data.success) {
                throw new Error(data.error || 'Failed to delete tokens');
            }

            // Reload tokens list
            const container = document.getElementById('myAPITokensContent');
            if (container) {
                await loadUserTokens(container);
            }

            showNotification(`${data.deleted_count} token(s) deleted successfully`, 'success');

        } catch (error) {
            console.error('Error deleting tokens:', error);
            showNotification(`Failed to delete tokens: ${error.message}`, 'error');
        }
    }

    /**
     * Delete all expired tokens
     */
    async function deleteExpired() {
        closeActionsDropdown();
        removeDropdownCloseHandler();

        if (!confirm('Are you sure you want to delete all expired tokens? This action cannot be undone.')) {
            return;
        }

        try {
            const response = await fetch('/api/user/tokens/expired', {
                method: 'DELETE',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.error || `HTTP ${response.status}`);
            }

            if (!data.success) {
                throw new Error(data.error || 'Failed to delete expired tokens');
            }

            // Reload tokens list
            const container = document.getElementById('myAPITokensContent');
            if (container) {
                await loadUserTokens(container);
            }

            showNotification(`${data.deleted_count} expired token(s) deleted successfully`, 'success');

        } catch (error) {
            console.error('Error deleting expired tokens:', error);
            showNotification(`Failed to delete expired tokens: ${error.message}`, 'error');
        }
    }

    /**
     * Copy token ID to clipboard
     */
    function copyToken(tokenId) {
        copyToClipboard('token-' + tokenId);
    }

    /**
     * Copy text to clipboard from element
     */
    function copyToClipboard(elementId) {
        const element = document.getElementById(elementId);
        if (!element) return;

        const text = element.textContent;
        
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(() => {
                showNotification('Copied to clipboard', 'success');
            }).catch(err => {
                console.error('Failed to copy:', err);
                fallbackCopyToClipboard(text);
            });
        } else {
            fallbackCopyToClipboard(text);
        }
    }

    /**
     * Fallback copy method for older browsers
     */
    function fallbackCopyToClipboard(text) {
        const textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.style.position = 'fixed';
        textArea.style.left = '-9999px';
        document.body.appendChild(textArea);
        textArea.select();
        
        try {
            document.execCommand('copy');
            showNotification('Copied to clipboard', 'success');
        } catch (err) {
            console.error('Failed to copy:', err);
            showNotification('Failed to copy to clipboard', 'error');
        }
        
        document.body.removeChild(textArea);
    }

    /**
     * Close actions dropdown
     */
    function closeActionsDropdown() {
        const dropdown = document.getElementById('actionsDropdown');
        if (dropdown) {
            dropdown.style.display = 'none';
        }
    }

    /**
     * Show notification message
     */
    function showNotification(message, type = 'info') {
        const colors = {
            success: { bg: '#10b981', icon: 'fa-check-circle' },
            error: { bg: '#ef4444', icon: 'fa-exclamation-circle' },
            warning: { bg: '#f59e0b', icon: 'fa-exclamation-triangle' },
            info: { bg: '#3b82f6', icon: 'fa-info-circle' }
        };

        const color = colors[type] || colors.info;

        const notification = document.createElement('div');
        notification.style.cssText = `
            position: fixed; 
            top: 20px; 
            right: 20px; 
            background: ${color.bg}; 
            color: white; 
            padding: 1rem 1.5rem; 
            border-radius: 4px; 
            box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); 
            z-index: 10000;
            display: flex;
            align-items: center;
            gap: 0.75rem;
            animation: slideIn 0.3s ease-out;
        `;

        notification.innerHTML = `
            <i class="fas ${color.icon}"></i>
            <span>${escapeHtml(message)}</span>
        `;

        document.body.appendChild(notification);

        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease-in';
            setTimeout(() => notification.remove(), 300);
        }, 3000);
    }

    /**
     * Format date for display
     */
    function formatDate(dateString) {
        if (!dateString) return 'N/A';
        
        try {
            const date = new Date(dateString);
            const day = String(date.getDate()).padStart(2, '0');
            const month = date.toLocaleString('en-US', { month: 'short' });
            const year = date.getFullYear();
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            
            return `${day}-${month}-${year} ${hours}:${minutes}`;
        } catch (e) {
            console.error('Error formatting date:', e);
            return dateString;
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Add CSS animations
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideIn {
            from {
                transform: translateX(100%);
                opacity: 0;
            }
            to {
                transform: translateX(0);
                opacity: 1;
            }
        }
        @keyframes slideOut {
            from {
                transform: translateX(0);
                opacity: 1;
            }
            to {
                transform: translateX(100%);
                opacity: 0;
            }
        }
        .dropdown-item:hover:not([disabled]) {
            background-color: #f3f4f6;
        }
        .dropdown-item[disabled] {
            opacity: 0.5;
            cursor: not-allowed;
        }
        #actionsDropdown {
            z-index: 10001 !important;
            position: absolute !important;
            display: none !important;
        }
        #actionsDropdown[style*="display: block"] {
            display: block !important;
            visibility: visible !important;
            opacity: 1 !important;
        }
        .dropdown {
            position: relative !important;
            z-index: 10001 !important;
        }
    `;
    document.head.appendChild(style);

    // Export functions to global scope
    window.userTokensModule = {
        loadUserTokens,
        toggleActionsMenu,
        toggleSelectAll,
        toggleTokenSelection,
        generateNewToken,
        deleteToken,
        deleteSelected,
        deleteExpired,
        copyToken,
        copyToClipboard
    };

})(window);

