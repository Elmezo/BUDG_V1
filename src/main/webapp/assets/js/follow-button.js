/**
 * Follow Button Component
 * Provides a reusable follow button for all entity view pages
 */
(function() {
    'use strict';

    class FollowButton {
        constructor() {
            this.userId = null;
            this.entityType = null;
            this.entityId = null;
            this.isFollowing = false;
            this.followedThroughParent = false;
            this.followData = null;
        }

        /**
         * Initialize the follow button
         * @param {string} entityType - The type of entity (system, dataset, etc.)
         * @param {number} entityId - The ID of the entity
         * @param {number} userId - The ID of the current user
         * @param {string} containerSelector - CSS selector for the container to add the button to
         */
        async init(entityType, entityId, userId, containerSelector = '.form-actions') {
            this.entityType = entityType;
            this.entityId = entityId;
            this.userId = userId;

            // Get current user information
            let currentUser = null;
            if (!this.userId) {
                currentUser = await this.getCurrentUser();
                this.userId = currentUser ? (currentUser.id || currentUser.ID) : null;
            } else {
                // If userId was provided, still get user data to check authorization
                currentUser = await this.getCurrentUser();
            }

            if (!this.userId || !currentUser) {
                console.warn('No user information available for follow button');
                return;
            }

            // Check if user is authorized to see the follow button
            if (!this.isUserAuthorizedForFollow(currentUser)) {
                console.log('User is not authorized to see follow button');
                // Defensive: remove button if some other script already added it
                this.remove();
                return;
            }

            // Check follow status
            await this.checkFollowStatus();

            // Create and add the button
            this.createButton(containerSelector);
        }

        /**
         * Get the current user information from the API
         */
        async getCurrentUser() {
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

        /**
         * Check if the current user is authorized to see the follow button
         */
        isUserAuthorizedForFollow(userData) {
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

        /**
         * Check if the user is currently following this entity
         */
        async checkFollowStatus() {
            try {
                const response = await fetch(`/api/follow/${this.entityType}/${this.entityId}/${this.userId}`);
                if (response.ok) {
                    const data = await response.json();
                    this.isFollowing = data.exists;
                    this.followedThroughParent = data.followedThroughParent === true;
                    this.followData = data;
                } else {
                    this.isFollowing = false;
                    this.followedThroughParent = false;
                    this.followData = null;
                }
            } catch (error) {
                console.error('Error checking follow status:', error);
                this.isFollowing = false;
                this.followedThroughParent = false;
                this.followData = null;
            }
        }

        /**
         * Create and add the follow button to the specified container
         * @param {string} containerSelector - CSS selector for the container
         */
        createButton(containerSelector) {
            const container = document.querySelector(containerSelector);
            if (!container) {
                console.warn('Follow button container not found:', containerSelector);
                return;
            }

            // Remove existing follow button if it exists
            const existingBtn = document.getElementById('followBtn');
            if (existingBtn) {
                existingBtn.remove();
            }

            // Create the button
            const t = (key) => (window.I18n && window.I18n.t(key)) || key;
            const followingLabel = t('follow.following');
            const followLabel = t('follow.followLabel');
            const button = document.createElement('button');
            button.id = 'followBtn';
            button.type = 'button';
            button.className = this.isFollowing ? 'follow-btn follow-btn-success follow-btn-sm' : 'follow-btn follow-btn-outline-primary follow-btn-sm';
            button.innerHTML = this.isFollowing 
                ? '<i class="fas fa-check"></i> ' + followingLabel 
                : '<i class="fas fa-plus"></i> ' + followLabel;

            // Add click event listener
            button.addEventListener('click', () => this.handleClick());

            // Add the button to the container
            container.appendChild(button);
        }

        /**
         * Handle follow button click
         */
        handleClick() {
            // If followed through parent, show message instead of opening modal
            if (this.followedThroughParent) {
                this.showParentFollowMessage();
                return;
            }

            // Otherwise, open the modal as usual
            if (window.FollowModal) {
                window.FollowModal.show(this.entityType, this.entityId, this.userId);
            } else {
                console.error('FollowModal not available');
            }
        }

        /**
         * Show message when item is followed through a parent
         */
        showParentFollowMessage() {
            // Remove existing message if any
            const existingMessage = document.getElementById('parentFollowMessage');
            if (existingMessage) {
                existingMessage.remove();
            }

            // Create message element
            const parentMsg = (window.I18n && window.I18n.t('follow.followingThroughParent')) || 'You are following this item or one of its parents.';
            const message = document.createElement('div');
            message.id = 'parentFollowMessage';
            message.className = 'parent-follow-message';
            message.innerHTML = `
                <div class="parent-follow-message-box">
                    <i class="fas fa-info-circle"></i>
                    <div class="parent-follow-message-content">
                        <strong>${parentMsg}</strong>
                    </div>
                </div>
            `;

            // Insert after the follow button
            const followBtn = document.getElementById('followBtn');
            if (followBtn && followBtn.parentNode) {
                followBtn.parentNode.insertBefore(message, followBtn.nextSibling);
            } else {
                // Fallback: add to form-actions container
                const container = document.querySelector('.form-actions');
                if (container) {
                    container.appendChild(message);
                }
            }

            // Auto-hide after 5 seconds
            setTimeout(() => {
                if (message.parentNode) {
                    message.style.opacity = '0';
                    message.style.transition = 'opacity 0.3s ease';
                    setTimeout(() => {
                        if (message.parentNode) {
                            message.remove();
                        }
                    }, 300);
                }
            }, 5000);
        }

        /**
         * Update the button state
         * @param {boolean} isFollowing - Whether the user is following
         */
        updateButton(isFollowing) {
            this.isFollowing = isFollowing;
            const button = document.getElementById('followBtn');
            if (button) {
                const t = (key) => (window.I18n && window.I18n.t(key)) || key;
                const followingLabel = t('follow.following');
                const followLabel = t('follow.followLabel');
                if (isFollowing) {
                    button.innerHTML = '<i class="fas fa-check"></i> ' + followingLabel;
                    button.className = 'follow-btn follow-btn-success follow-btn-sm';
                } else {
                    button.innerHTML = '<i class="fas fa-plus"></i> ' + followLabel;
                    button.className = 'follow-btn follow-btn-outline-primary follow-btn-sm';
                }
            }
        }

        /**
         * Remove the follow button
         */
        remove() {
            const button = document.getElementById('followBtn');
            if (button) {
                button.remove();
            }
        }
    }

    // Create global instance
    window.FollowButton = new FollowButton();

    // Helper function to easily add follow button to any page
    window.addFollowButton = async function(entityType, entityId, userId, containerSelector) {
        await window.FollowButton.init(entityType, entityId, userId, containerSelector);
    };

})();
