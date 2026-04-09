/**
 * Logout Modal Utility
 * Displays a modal dialog informing users they have been logged out,
 * then redirects to the login page.
 */

(function() {
    'use strict';
    
    let isModalShowing = false;
    let redirectTimeout = null;
    
    /**
     * Show logout message modal and redirect to login
     * @param {string} message - Optional custom message (default: "You have been logged out")
     */
    function showLogoutMessage(message = 'You have been logged out') {
        // Prevent multiple modals from showing simultaneously
        if (isModalShowing) {
            return;
        }
        
        isModalShowing = true;
        
        // Remove any existing modal
        const existingModal = document.getElementById('logoutModal');
        if (existingModal) {
            existingModal.remove();
        }
        
        // Clear any existing timeout
        if (redirectTimeout) {
            clearTimeout(redirectTimeout);
        }
        
        // Create modal overlay
        const overlay = document.createElement('div');
        overlay.id = 'logoutModal';
        overlay.className = 'logout-modal-overlay';
        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.6);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 10000;
            animation: fadeIn 0.3s ease;
        `;
        
        // Create modal content
        const modal = document.createElement('div');
        modal.className = 'logout-modal';
        modal.style.cssText = `
            background: white;
            border-radius: 8px;
            padding: 0;
            max-width: 400px;
            width: 90%;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
            animation: slideUp 0.3s ease;
            overflow: hidden;
        `;
        
        // Add dark theme support
        const isDarkTheme = document.documentElement.getAttribute('data-theme') === 'dark';
        if (isDarkTheme) {
            modal.style.background = '#2d3748';
            modal.style.color = '#e2e8f0';
        }
        
        // Create modal header
        const header = document.createElement('div');
        header.className = 'logout-modal-header';
        header.style.cssText = `
            padding: 20px 24px;
            border-bottom: 1px solid ${isDarkTheme ? '#4a5568' : '#e2e8f0'};
            display: flex;
            align-items: center;
            gap: 12px;
        `;
        
        const icon = document.createElement('i');
        icon.className = 'fas fa-sign-out-alt';
        icon.style.cssText = `
            font-size: 24px;
            color: ${isDarkTheme ? '#f56565' : '#e53e3e'};
        `;
        
        const title = document.createElement('h3');
        title.textContent = 'Session Ended';
        title.style.cssText = `
            margin: 0;
            font-size: 20px;
            font-weight: 600;
            color: ${isDarkTheme ? '#e2e8f0' : '#1a202c'};
        `;
        
        header.appendChild(icon);
        header.appendChild(title);
        
        // Create modal body
        const body = document.createElement('div');
        body.className = 'logout-modal-body';
        body.style.cssText = `
            padding: 24px;
            color: ${isDarkTheme ? '#cbd5e0' : '#4a5568'};
            font-size: 15px;
            line-height: 1.6;
        `;
        body.textContent = message;
        
        // Create modal footer
        const footer = document.createElement('div');
        footer.className = 'logout-modal-footer';
        footer.style.cssText = `
            padding: 16px 24px;
            border-top: 1px solid ${isDarkTheme ? '#4a5568' : '#e2e8f0'};
            display: flex;
            justify-content: flex-end;
            gap: 12px;
        `;
        
        const okButton = document.createElement('button');
        okButton.className = 'logout-modal-ok-btn';
        okButton.textContent = 'OK';
        okButton.style.cssText = `
            padding: 10px 24px;
            background: ${isDarkTheme ? '#4299e1' : '#3182ce'};
            color: white;
            border: none;
            border-radius: 6px;
            font-size: 14px;
            font-weight: 500;
            cursor: pointer;
            transition: background 0.2s;
        `;
        
        okButton.addEventListener('mouseenter', function() {
            this.style.background = isDarkTheme ? '#3182ce' : '#2c5282';
        });
        
        okButton.addEventListener('mouseleave', function() {
            this.style.background = isDarkTheme ? '#4299e1' : '#3182ce';
        });
        
        okButton.addEventListener('click', function() {
            redirectToLogin();
        });
        
        footer.appendChild(okButton);
        
        // Assemble modal
        modal.appendChild(header);
        modal.appendChild(body);
        modal.appendChild(footer);
        overlay.appendChild(modal);
        
        // Add animations if not already present
        if (!document.getElementById('logout-modal-animations')) {
            const style = document.createElement('style');
            style.id = 'logout-modal-animations';
            style.textContent = `
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
                @keyframes slideUp {
                    from {
                        transform: translateY(20px);
                        opacity: 0;
                    }
                    to {
                        transform: translateY(0);
                        opacity: 1;
                    }
                }
            `;
            document.head.appendChild(style);
        }
        
        // Add to document
        document.body.appendChild(overlay);
        
        // Auto-redirect after 3 seconds
        redirectTimeout = setTimeout(function() {
            redirectToLogin();
        }, 3000);
    }
    
    /**
     * Redirect to login page
     */
    function redirectToLogin() {
        if (redirectTimeout) {
            clearTimeout(redirectTimeout);
            redirectTimeout = null;
        }
        
        isModalShowing = false;
        window.location.href = '/login.html';
    }
    
    // Make function available globally
    window.showLogoutMessage = showLogoutMessage;
    
})();

