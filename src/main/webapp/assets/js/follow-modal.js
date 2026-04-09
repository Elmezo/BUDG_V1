/**
 * Follow Modal Component
 * Handles the follow functionality for all entity types
 */
(function() {
    'use strict';

    class FollowModal {
        constructor() {
            this.modal = null;
            this.entityType = null;
            this.entityId = null;
            this.userId = null;
            this.followTypes = [];
            this.isFollowing = false;
            this.currentFollowData = null;
        }

        /**
         * Initialize the follow modal (load data only; modal DOM is created on first show() so I18n is ready)
         */
        init() {
            this._listenersAttached = false;
            this.loadFollowTypes();
        }

        /**
         * Create the modal HTML structure
         */
        createModal() {
            const t = (key) => (window.I18n && window.I18n.t(key)) || key;
            const modalHtml = `
                <div id="followModal" class="follow-modal" style="display: none;">
                    <div class="follow-modal-content">
                        <div class="follow-modal-header">
                            <h3 class="follow-modal-title">${t('follow.title')}</h3>
                            <button type="button" class="follow-modal-close" id="followModalClose">
                                <i class="fas fa-times"></i>
                            </button>
                        </div>
                        <div class="follow-modal-body">
                            <form id="followForm">
                                <div class="follow-form-group">
                                    <label for="followTypeSelect">${t('follow.followType')}</label>
                                    <select id="followTypeSelect" class="follow-form-control" required>
                                        <option value="">${t('follow.selectType')}</option>
                                    </select>
                                </div>
                                <div class="follow-form-group">
                                    <label for="withChildrenSelect">${t('follow.withChildren')}</label>
                                    <select id="withChildrenSelect" class="follow-form-control" required>
                                        <option value="false">${t('follow.onlyThisItem')}</option>
                                        <option value="true">${t('follow.includeChildren')}</option>
                                    </select>
                                </div>
                                <div class="follow-form-group">
                                    <label for="followDescription">${t('follow.additionalInfo')}</label>
                                    <textarea id="followDescription" class="follow-form-control" rows="3"
                                              placeholder="${t('follow.additionalInfo')}"></textarea>
                                </div>
                            </form>
                        </div>
                        <div class="follow-modal-footer">
                            <button type="button" class="follow-btn follow-btn-secondary" id="followModalCancel">${t('button.cancel')}</button>
                            <button type="button" class="follow-btn follow-btn-primary" id="followModalUpdate" style="display:none;">${t('follow.update')}</button>
                            <button type="button" class="follow-btn follow-btn-primary" id="followModalSubmit">${t('follow.submit')}</button>
                        </div>
                    </div>
                </div>
            `;

            // Add modal to body if it doesn't exist
            if (!document.getElementById('followModal')) {
                document.body.insertAdjacentHTML('beforeend', modalHtml);
                this.modal = document.getElementById('followModal');
            } else {
                this.modal = document.getElementById('followModal');
            }
        }

        /**
         * Load follow types from the API
         */
        async loadFollowTypes() {
            try {
                const response = await fetch('/api/follow-types');
                if (response.ok) {
                    this.followTypes = await response.json();
                    this.populateFollowTypeSelect();
                } else {
                    console.error('Failed to load follow types:', response.statusText);
                }
            } catch (error) {
                console.error('Error loading follow types:', error);
            }
        }

        /**
         * Populate the follow type dropdown
         */
        populateFollowTypeSelect() {
            const select = document.getElementById('followTypeSelect');
            if (!select) return;

            // Clear existing options except the first one
            const selectPlaceholder = (window.I18n && window.I18n.t('follow.selectType')) || 'Select follow type...';
            select.innerHTML = '<option value="">' + selectPlaceholder + '</option>';

            this.followTypes.forEach(type => {
                const option = document.createElement('option');
                option.value = type.id;
                option.textContent = type.name;
                select.appendChild(option);
            });
        }

        /**
         * Setup event listeners
         */
        setupEventListeners() {
            // Close modal events
            document.getElementById('followModalClose')?.addEventListener('click', () => this.close());
            document.getElementById('followModalCancel')?.addEventListener('click', () => this.close());

            // Submit form
            document.getElementById('followModalSubmit')?.addEventListener('click', () => this.submitFollow());

            // Update follow
            document.getElementById('followModalUpdate')?.addEventListener('click', () => this.updateFollow());

            // Close modal when clicking outside
            this.modal?.addEventListener('click', (e) => {
                if (e.target === this.modal) {
                    this.close();
                }
            });

            // Close modal on Escape key
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape' && this.modal && this.modal.style.display !== 'none') {
                    this.close();
                }
            });

            // Track form changes to toggle Update button enabled state
            const watchChange = () => this.updateUpdateButtonState();
            document.getElementById('followTypeSelect')?.addEventListener('change', watchChange);
            document.getElementById('withChildrenSelect')?.addEventListener('change', watchChange);
            document.getElementById('followDescription')?.addEventListener('input', watchChange);
        }

        /**
         * Show the follow modal
         * @param {string} entityType - The type of entity (system, dataset, etc.)
         * @param {number} entityId - The ID of the entity
         * @param {number} userId - The ID of the current user
         */
        async show(entityType, entityId, userId) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.userId = userId;

            // Create modal on first show so I18n translations are loaded (avoids showing raw keys)
            if (!document.getElementById('followModal')) {
                this.createModal();
            } else {
                this.modal = document.getElementById('followModal');
            }
            if (!this._listenersAttached) {
                this.setupEventListeners();
                this._listenersAttached = true;
            }
            this.populateFollowTypeSelect();

            // Check if already following
            await this.checkFollowStatus();

            // Reset form
            this.resetForm();

            // If already following, populate the form with existing data
            if (this.isFollowing && this.currentFollowData) {
                this.populateFormWithExistingData();
            }

            // Disable form if followed through parent
            if (this.currentFollowData && this.currentFollowData.followedThroughParent) {
                this.disableFormForParentFollow();
            } else {
                this.enableForm();
            }

            // Show modal
            this.modal.style.display = 'flex';
            document.body.style.overflow = 'hidden';

            // Focus on first input (if form is enabled)
            if (!(this.currentFollowData && this.currentFollowData.followedThroughParent)) {
                setTimeout(() => {
                    document.getElementById('followTypeSelect')?.focus();
                }, 100);
            }

            // Ensure Update button reflects current state
            this.updateUpdateButtonState();
        }

        /**
         * Disable form when followed through parent
         */
        disableFormForParentFollow() {
            const followTypeSelect = document.getElementById('followTypeSelect');
            const withChildrenSelect = document.getElementById('withChildrenSelect');
            const descriptionField = document.getElementById('followDescription');
            const submitBtn = document.getElementById('followModalSubmit');
            const updateBtn = document.getElementById('followModalUpdate');

            if (followTypeSelect) followTypeSelect.disabled = true;
            if (withChildrenSelect) withChildrenSelect.disabled = true;
            if (descriptionField) descriptionField.disabled = true;
            if (submitBtn) submitBtn.style.display = 'none';
            if (updateBtn) updateBtn.style.display = 'none';
        }

        /**
         * Enable form for direct follow
         */
        enableForm() {
            const followTypeSelect = document.getElementById('followTypeSelect');
            const withChildrenSelect = document.getElementById('withChildrenSelect');
            const descriptionField = document.getElementById('followDescription');
            const submitBtn = document.getElementById('followModalSubmit');

            if (followTypeSelect) followTypeSelect.disabled = false;
            if (withChildrenSelect) withChildrenSelect.disabled = false;
            if (descriptionField) descriptionField.disabled = false;
            if (submitBtn) submitBtn.style.display = '';
        }

        /**
         * Close the modal
         */
        close() {
            this.modal.style.display = 'none';
            document.body.style.overflow = '';
            this.resetForm();
        }

        /**
         * Reset the form to default values
         */
        resetForm() {
            document.getElementById('followTypeSelect').value = '';
            document.getElementById('withChildrenSelect').value = 'false';
            document.getElementById('followDescription').value = '';

            // Reset modal title
            const modalTitle = document.querySelector('.follow-modal-title');
            if (modalTitle) {
                modalTitle.textContent = (window.I18n && window.I18n.t('follow.title')) || 'Follow item';
            }

            const submitBtn = document.getElementById('followModalSubmit');
            const updateBtn = document.getElementById('followModalUpdate');
            const followLabel = (window.I18n && window.I18n.t('follow.submit')) || 'Follow';
            const updateLabel = (window.I18n && window.I18n.t('follow.update')) || 'Update';
            if (this.isFollowing) {
                submitBtn.textContent = 'Unfollow';
                submitBtn.className = 'follow-btn follow-btn-danger';
                if (updateBtn) {
                    updateBtn.textContent = updateLabel;
                    updateBtn.style.display = '';
                }
            } else {
                submitBtn.textContent = followLabel;
                submitBtn.className = 'follow-btn follow-btn-primary';
                if (updateBtn) updateBtn.style.display = 'none';
            }

            // Hide parent follow message
            this.hideFollowingThroughParentMessage();
        }

        /**
         * Populate the form with existing follow data
         */
        populateFormWithExistingData() {
            if (!this.currentFollowData) return;

            // Update modal title
            const modalTitle = document.querySelector('.follow-modal-title');
            if (modalTitle) {
                modalTitle.textContent = (window.I18n && window.I18n.t('follow.editTitle')) || 'Edit follow';
            }

            // Set follow type
            const followTypeSelect = document.getElementById('followTypeSelect');
            if (followTypeSelect && this.currentFollowData.followType) {
                followTypeSelect.value = this.currentFollowData.followType;
            }

            // Set with children
            const withChildrenSelect = document.getElementById('withChildrenSelect');
            if (withChildrenSelect && this.currentFollowData.withChildren !== undefined) {
                withChildrenSelect.value = this.currentFollowData.withChildren ? 'true' : 'false';
            }

            // Set description
            const descriptionField = document.getElementById('followDescription');
            if (descriptionField && this.currentFollowData.description) {
                descriptionField.value = this.currentFollowData.description;
            }

            // When editing, ensure Update button is visible and state evaluated
            const updateBtn = document.getElementById('followModalUpdate');
            if (updateBtn) updateBtn.style.display = '';
            this.updateUpdateButtonState();
        }

        /**
         * Check if the user is already following this entity
         */
        async checkFollowStatus() {
            try {
                const response = await fetch(`/api/follow/${this.entityType}/${this.entityId}/${this.userId}`);
                if (response.ok) {
                    const data = await response.json();
                    this.isFollowing = data.exists;
                    this.currentFollowData = data.exists ? data : null;
                    
                    // If followed through parent, show appropriate message
                    if (data.exists && data.followedThroughParent) {
                        this.showFollowingThroughParentMessage();
                    } else {
                        this.hideFollowingThroughParentMessage();
                    }
                } else {
                    this.isFollowing = false;
                    this.currentFollowData = null;
                    this.hideFollowingThroughParentMessage();
                }
            } catch (error) {
                console.error('Error checking follow status:', error);
                this.isFollowing = false;
                this.currentFollowData = null;
                this.hideFollowingThroughParentMessage();
            }
        }

        /**
         * Show message indicating item is followed through a parent
         */
        showFollowingThroughParentMessage() {
            let messageContainer = document.getElementById('followingThroughParentMessage');
            if (!messageContainer && this.modal) {
                messageContainer = document.createElement('div');
                messageContainer.id = 'followingThroughParentMessage';
                messageContainer.className = 'following-through-parent-message';
                const parentMsg = (window.I18n && window.I18n.t('follow.followingThroughParent')) || 'You are following this item or one of its parents.';
                const parentHint = (window.I18n && window.I18n.t('follow.followingThroughParentHint')) || 'This item is being followed because a parent item is followed with children.';
                messageContainer.innerHTML = `
                    <div class="following-message-box">
                        <i class="fas fa-info-circle"></i>
                        <div class="following-message-content">
                            <strong>${parentMsg}</strong>
                            <p>${parentHint}</p>
                        </div>
                    </div>
                `;
                const modalBody = this.modal.querySelector('.follow-modal-body');
                if (modalBody) {
                    modalBody.insertBefore(messageContainer, modalBody.firstChild);
                }
            }
            if (messageContainer) {
                messageContainer.style.display = 'block';
            }
        }

        /**
         * Hide message indicating item is followed through a parent
         */
        hideFollowingThroughParentMessage() {
            const messageContainer = document.getElementById('followingThroughParentMessage');
            if (messageContainer) {
                messageContainer.style.display = 'none';
            }
        }

        /**
         * Submit the follow form
         */
        async submitFollow() {
            if (this.isFollowing) {
                // Check if we're updating existing follow or unfollowing
                const followType = document.getElementById('followTypeSelect').value;
                const withChildren = document.getElementById('withChildrenSelect').value === 'true';
                const description = document.getElementById('followDescription').value;

                // If form has changes, update the follow relationship
                if (this.hasFormChanges()) {
                    await this.updateFollow();
                } else {
                    // No changes, just unfollow
                    await this.unfollow();
                }
            } else {
                await this.follow();
            }
        }

        /**
         * Create a new follow relationship
         */
        async follow() {
            const followType = document.getElementById('followTypeSelect').value;
            const withChildren = document.getElementById('withChildrenSelect').value === 'true';
            const description = document.getElementById('followDescription').value;

            if (!followType) {
                alert('Please select a follow type');
                return;
            }

            try {
                const response = await fetch(`/api/follow/${this.entityType}/${this.entityId}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        userId: this.userId,
                        followType: parseInt(followType),
                        withChildren: withChildren,
                        description: description
                    })
                });

                if (response.ok) {
                    const result = await response.json();
                    if (result.success) {
                        this.isFollowing = true;
                        this.close();
                        this.updateFollowButton(true);
                        this.showNotification('Successfully started following this item', 'success');
                        // Refresh the Following tab in My Items dropdown
                        this.refreshFollowingTab();
                    } else {
                        this.showNotification('Failed to follow item: ' + (result.message || 'Unknown error'), 'error');
                    }
                } else {
                    const error = await response.json();
                    this.showNotification('Failed to follow item: ' + (error.error || 'Unknown error'), 'error');
                }
            } catch (error) {
                console.error('Error following item:', error);
                this.showNotification('Failed to follow item: ' + error.message, 'error');
            }
        }

        /**
         * Check if the form has changes from the original data
         */
        hasFormChanges() {
            if (!this.currentFollowData) return false;

            const followType = document.getElementById('followTypeSelect').value;
            const withChildren = document.getElementById('withChildrenSelect').value === 'true';
            const description = document.getElementById('followDescription').value;

            return (
                parseInt(followType) !== this.currentFollowData.followType ||
                withChildren !== this.currentFollowData.withChildren ||
                description !== (this.currentFollowData.description || '')
            );
        }

        /**
         * Enable/disable and show/hide the Update button appropriately
         */
        updateUpdateButtonState() {
            const updateBtn = document.getElementById('followModalUpdate');
            if (!updateBtn) return;

            if (!this.isFollowing) {
                updateBtn.style.display = 'none';
                return;
            }

            updateBtn.style.display = '';
            updateBtn.disabled = !this.hasFormChanges();
        }

        /**
         * Update an existing follow relationship
         */
        async updateFollow() {
            const followType = document.getElementById('followTypeSelect').value;
            const withChildren = document.getElementById('withChildrenSelect').value === 'true';
            const description = document.getElementById('followDescription').value;

            if (!followType) {
                alert('Please select a follow type');
                return;
            }

            if (!this.currentFollowData) {
                this.showNotification('Unable to update follow: missing current data', 'error');
                return;
            }

            try {
                const payload = { userId: this.userId };
                const followTypeInt = parseInt(followType, 10);
                const followTypeChanged = followTypeInt !== this.currentFollowData.followType;
                const withChildrenChanged = withChildren !== this.currentFollowData.withChildren;
                const descriptionChanged = description !== (this.currentFollowData.description || '');

                if (followTypeChanged) {
                    payload.followType = followTypeInt;
                }
                if (withChildrenChanged) {
                    payload.withChildren = withChildren;
                }
                if (descriptionChanged) {
                    payload.description = description;
                }

                if (Object.keys(payload).length === 1) {
                    this.showNotification('No changes detected to update', 'info');
                    return;
                }

                const response = await fetch(`/api/follow/${this.entityType}/${this.entityId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify(payload)
                });

                if (response.ok) {
                    const result = await response.json();
                    if (result.success) {
                        this.currentFollowData = {
                            ...this.currentFollowData,
                            followType: followTypeInt,
                            withChildren,
                            description
                        };
                        this.isFollowing = true;
                        this.close();
                        this.updateFollowButton(true);
                        this.showNotification('Successfully updated follow settings', 'success');
                        // Refresh the Following tab in My Items dropdown
                        this.refreshFollowingTab();
                    } else {
                        this.showNotification('Failed to update follow: ' + (result.message || 'Unknown error'), 'error');
                    }
                } else {
                    const error = await response.json();
                    this.showNotification('Failed to update follow: ' + (error.error || 'Unknown error'), 'error');
                }
            } catch (error) {
                console.error('Error updating follow:', error);
                this.showNotification('Failed to update follow: ' + error.message, 'error');
            }
        }

        /**
         * Delete a follow relationship (internal method)
         */
        async deleteFollowRelationship(options = {}) {
            const { purgeAuditHistory = false } = options;
            const query = purgeAuditHistory ? '?purgeAuditHistory=true' : '';

            const response = await fetch(`/api/follow/${this.entityType}/${this.entityId}/${this.userId}${query}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || 'Failed to delete follow relationship');
            }
        }

        /**
         * Remove the follow relationship
         */
        async unfollow() {
            try {
                const response = await fetch(`/api/follow/${this.entityType}/${this.entityId}/${this.userId}?purgeAuditHistory=true`, {
                    method: 'DELETE'
                });

                if (response.ok) {
                    const result = await response.json();
                    if (result.success) {
                        this.isFollowing = false;
                        this.close();
                        this.updateFollowButton(false);
                        this.showNotification('Successfully stopped following this item', 'success');
                        // Refresh the Following tab in My Items dropdown
                        this.refreshFollowingTab();
                    } else {
                        this.showNotification('Failed to unfollow item: ' + (result.message || 'Unknown error'), 'error');
                    }
                } else {
                    const error = await response.json();
                    this.showNotification('Failed to unfollow item: ' + (error.error || 'Unknown error'), 'error');
                }
            } catch (error) {
                console.error('Error unfollowing item:', error);
                this.showNotification('Failed to unfollow item: ' + error.message, 'error');
            }
        }

        /**
         * Update the follow button state
         * @param {boolean} isFollowing - Whether the user is following
         */
        updateFollowButton(isFollowing) {
            const followBtn = document.getElementById('followBtn');
            if (followBtn) {
                const followingLabel = (window.I18n && window.I18n.t('follow.following')) || 'Following';
                const followLabel = (window.I18n && window.I18n.t('follow.followLabel')) || 'FOLLOW';
                if (isFollowing) {
                    followBtn.innerHTML = '<i class="fas fa-check"></i> ' + followingLabel;
                    followBtn.className = 'follow-btn follow-btn-success follow-btn-sm';
                } else {
                    followBtn.innerHTML = '<i class="fas fa-plus"></i> ' + followLabel;
                    followBtn.className = 'follow-btn follow-btn-outline-primary follow-btn-sm';
                }
            }
        }

        /**
         * Show a notification message
         * @param {string} message - The message to show
         * @param {string} type - The type of notification (success, error, info)
         */
        showNotification(message, type = 'info') {
            // Create notification element
            const notification = document.createElement('div');
            notification.className = `follow-notification follow-notification-${type}`;
            notification.innerHTML = `
                <div class="follow-notification-content">
                    <i class="fas fa-${type === 'success' ? 'check-circle' : type === 'error' ? 'exclamation-circle' : 'info-circle'}"></i>
                    <span>${message}</span>
                </div>
            `;

            // Add to page
            document.body.appendChild(notification);

            // Show notification
            setTimeout(() => {
                notification.classList.add('show');
            }, 100);

            // Hide and remove notification after 3 seconds
            setTimeout(() => {
                notification.classList.remove('show');
                setTimeout(() => {
                    if (notification.parentNode) {
                        notification.parentNode.removeChild(notification);
                    }
                }, 300);
            }, 3000);
        }

        /**
         * Refresh the Following tab in My Items dropdown
         */
        refreshFollowingTab() {
            // Trigger a refresh of the Following tab by simulating a click on the dropdown
            const myItemsToggle = document.getElementById('myItemsDropdownToggle');
            const myItemsDropdown = document.getElementById('myItemsDropdown');
            
            if (myItemsToggle && myItemsDropdown) {
                // Check if the dropdown is open
                if (myItemsDropdown.classList.contains('show')) {
                    // Trigger the submenu setup again
                    const followingSubmenu = document.getElementById('myItemsFollowingSubmenu');
                    if (followingSubmenu) {
                        // Force refresh by clearing and reloading
                        const container = followingSubmenu.querySelector('.following-items');
                        if (container) {
                            container.innerHTML = '<div class="sub-dropdown-item empty">Loading...</div>';
                        }
                        
                        // Trigger the setup function again
                        setTimeout(() => {
                            if (window.setupMyItemsSubmenu) {
                                // This will be handled by the main.js setupMyItemsSubmenu function
                                console.log('Refreshing Following tab...');
                            }
                        }, 100);
                    }
                }
            }
        }
    }

    // Create global instance
    window.FollowModal = new FollowModal();

    // Initialize when DOM is ready
    document.addEventListener('DOMContentLoaded', function() {
        window.FollowModal.init();
    });

})();
