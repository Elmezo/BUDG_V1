/**
 * Unified Deletion Handler
 * Handles object deletion with validation checks and confirmation dialogs
 */

window.DeleteHandler = (function() {
    'use strict';

    // Facet configuration for deletion
    const FACET_CONFIG = {
        'system': {
            tableName: 'system',
            deleteColumn: 'Deleted_Datetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/system',
            specialChecks: ['datasets']
        },
        'dataset': {
            tableName: 'dataset',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'system_id',
            hasStatus: true,
            apiEndpoint: '/api/dataset'
        },
        'business-area': {
            tableName: 'business_area',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/business-area'
        },
        'capability': {
            tableName: 'capability',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/capability'
        },
        'client': {
            tableName: 'client',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/client'
        },
        'committee': {
            tableName: 'committee',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/committee'
        },
        'geography': {
            tableName: 'geography',
            deleteColumn: 'DeletedDatetime',
            statusColumn: null,
            parentColumn: 'parent_id',
            hasStatus: false,
            apiEndpoint: '/api/geography',
            specialChecks: ['regulator-links', 'legal-entity-links']
        },
        'glossary': {
            tableName: 'glossary',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/glossary'
        },
        'legal-entity': {
            tableName: 'legal',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/legal-entity'
        },
        'org-unit': {
            tableName: 'org_unit',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'Parent_ID',
            hasStatus: true,
            apiEndpoint: '/api/org-unit'
        },
        'people': {
            tableName: 'people',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'org_unit_id',
            hasStatus: true,
            apiEndpoint: '/api/people'
        },
        'policy': {
            tableName: 'policy',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/policy'
        },
        'process': {
            tableName: 'process',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/process'
        },
        'product': {
            tableName: 'product',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/product'
        },
        'project': {
            tableName: 'project',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/project'
        },
        'regulation': {
            tableName: 'regulation',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/regulation'
        },
        'regulator': {
            tableName: 'regulator',
            deleteColumn: 'DeletedDatetime',
            statusColumn: null,
            parentColumn: 'parent_id',
            hasStatus: false,
            apiEndpoint: '/api/regulator'
        },
        'regulatory-theme': {
            tableName: 'regulatory_theme',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'parent_id',
            hasStatus: true,
            apiEndpoint: '/api/regulatory-theme'
        },
        'system-interface': {
            tableName: 'system_interface',
            deleteColumn: 'DeletedDatetime',
            statusColumn: 'status',
            parentColumn: 'system_id',
            hasStatus: true,
            apiEndpoint: '/api/system-interface'
        }
    };

    /**
     * Main deletion handler function
     * @param {string} facetType - The type of facet to delete
     * @param {number} objectId - The ID of the object to delete
     */
    async function handleDelete(facetType, objectId) {
        try {
            console.log(`Starting deletion process for ${facetType} with ID: ${objectId}`);
            
            const config = FACET_CONFIG[facetType];
            if (!config) {
                throw new Error(`No configuration found for facet type: ${facetType}`);
            }

            // Step 1: Perform pre-deletion validation
            const validationResult = await validateDeletion(facetType, objectId, config);
            
            if (!validationResult.canDelete) {
                showValidationErrors(validationResult.errors);
                return;
            }

            // Step 2: Show warnings and get user confirmation
            const confirmationResult = await showDeletionWarnings(facetType, objectId, validationResult.warnings);
            
            if (!confirmationResult.confirmed) {
                console.log('Deletion cancelled by user');
                return;
            }

            // Step 3: Perform the deletion
            const deletionResult = await performDeletion(facetType, objectId, config, confirmationResult.options);
            
            // Step 4: Show success message and redirect
            showSuccessMessage(facetType, deletionResult);
            
            // Redirect to list page after short delay
            setTimeout(() => {
                window.location.href = getListPageUrl(facetType);
            }, 1500);

        } catch (error) {
            console.error('Deletion failed:', error);
            showErrorMessage(error.message || 'An unexpected error occurred during deletion.');
        }
    }

    /**
     * Validate deletion conditions using backend API
     */
    async function validateDeletion(facetType, objectId, config) {
        try {
            const response = await fetch(`/api/deletion-validation/${facetType}/${objectId}`, {
                method: 'GET',
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`Validation failed: ${response.statusText}`);
            }

            const validationResult = await response.json();
            return validationResult;

        } catch (error) {
            console.error('Validation error:', error);
            return {
                canDelete: false,
                errors: ['Failed to validate deletion conditions. Please try again.'],
                warnings: []
            };
        }
    }

    // Validation functions removed - now handled by backend API

    /**
     * Show validation errors with modern UI
     */
    function showValidationErrors(errors) {
        if (errors.length === 0) return;
        
        // Create modal-style error display
        const modal = document.createElement('div');
        modal.className = 'delete-error-modal';
        modal.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.5);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 10000;
            animation: fadeIn 0.2s ease;
        `;
        
        const content = document.createElement('div');
        content.style.cssText = `
            background: white;
            border-radius: 12px;
            padding: 24px;
            max-width: 500px;
            width: 90%;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
            animation: slideUp 0.3s ease;
        `;
        
        const icon = document.createElement('div');
        icon.innerHTML = '🚫';
        icon.style.cssText = 'font-size: 48px; text-align: center; margin-bottom: 16px;';
        
        const title = document.createElement('h3');
        title.textContent = 'Cannot Delete';
        title.style.cssText = 'margin: 0 0 16px 0; color: #dc2626; font-size: 20px; font-weight: 600; text-align: center;';
        
        const errorList = document.createElement('div');
        errorList.style.cssText = 'margin-bottom: 24px;';
        errors.forEach(error => {
            const errorItem = document.createElement('div');
            errorItem.textContent = error;
            errorItem.style.cssText = 'padding: 12px; margin-bottom: 8px; background: #fef2f2; border-left: 4px solid #dc2626; border-radius: 4px; color: #991b1b; font-size: 14px; line-height: 1.5;';
            errorList.appendChild(errorItem);
        });
        
        const button = document.createElement('button');
        button.textContent = 'Close';
        button.style.cssText = `
            width: 100%;
            padding: 12px;
            background: #dc2626;
            color: white;
            border: none;
            border-radius: 6px;
            font-size: 16px;
            font-weight: 500;
            cursor: pointer;
            transition: background 0.2s;
        `;
        button.onmouseover = () => button.style.background = '#b91c1c';
        button.onmouseout = () => button.style.background = '#dc2626';
        button.onclick = () => {
            modal.style.animation = 'fadeOut 0.2s ease';
            setTimeout(() => modal.remove(), 200);
        };
        
        content.appendChild(icon);
        content.appendChild(title);
        content.appendChild(errorList);
        content.appendChild(button);
        modal.appendChild(content);
        document.body.appendChild(modal);
        
        // Add animations
        const style = document.createElement('style');
        style.textContent = `
            @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
            @keyframes fadeOut { from { opacity: 1; } to { opacity: 0; } }
            @keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
        `;
        document.head.appendChild(style);
    }

    /**
     * Show deletion warnings and get user confirmation with modern UI
     */
    async function showDeletionWarnings(facetType, objectId, warnings) {
        const entityName = getEntityDisplayName(facetType);
        
        if (warnings.length === 0) {
            const confirmed = await showConfirmationDialog(
                `Delete ${entityName}?`,
                `Are you sure you want to permanently remove this ${entityName.toLowerCase()}? This action cannot be undone.`,
                'Delete',
                'Cancel'
            );
            return { confirmed: confirmed, options: {} };
        }

        const result = { confirmed: false, options: {} };
        
        // Show all warnings in a single modern dialog
        const warningMessages = warnings.map(w => w.message).join('\n\n');
        const confirmMessage = `${warningMessages}\n\n⚠️ Do you want to proceed with deletion?`;
        
        const confirmed = await showConfirmationDialog(
            `Delete ${entityName}?`,
            confirmMessage,
            'Yes, Delete',
            'Cancel'
        );
        
        if (!confirmed) {
            return result;
        }
        
        // Set options based on warnings
        const childrenWarning = warnings.find(w => w.type === 'children');
        if (childrenWarning) {
            result.options.unlinkChildren = true;
        }
        
        result.confirmed = true;
        return result;
    }
    
    /**
     * Show modern confirmation dialog
     */
    function showConfirmationDialog(title, message, confirmText, cancelText) {
        return new Promise((resolve) => {
            const modal = document.createElement('div');
            modal.className = 'delete-confirm-modal';
            modal.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: rgba(0, 0, 0, 0.5);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
                animation: fadeIn 0.2s ease;
            `;
            
            const content = document.createElement('div');
            content.style.cssText = `
                background: white;
                border-radius: 12px;
                padding: 28px;
                max-width: 480px;
                width: 90%;
                box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
                animation: slideUp 0.3s ease;
            `;
            
            const icon = document.createElement('div');
            icon.innerHTML = '⚠️';
            icon.style.cssText = 'font-size: 48px; text-align: center; margin-bottom: 16px;';
            
            const titleEl = document.createElement('h3');
            titleEl.textContent = title;
            titleEl.style.cssText = 'margin: 0 0 16px 0; color: #1f2937; font-size: 22px; font-weight: 600; text-align: center;';
            
            const messageEl = document.createElement('div');
            messageEl.textContent = message;
            messageEl.style.cssText = 'margin-bottom: 24px; color: #4b5563; font-size: 15px; line-height: 1.6; white-space: pre-line; text-align: center;';
            
            const buttonContainer = document.createElement('div');
            buttonContainer.style.cssText = 'display: flex; gap: 12px;';
            
            const cancelBtn = document.createElement('button');
            cancelBtn.textContent = cancelText || 'Cancel';
            cancelBtn.style.cssText = `
                flex: 1;
                padding: 12px;
                background: #f3f4f6;
                color: #374151;
                border: none;
                border-radius: 6px;
                font-size: 15px;
                font-weight: 500;
                cursor: pointer;
                transition: all 0.2s;
            `;
            cancelBtn.onmouseover = () => cancelBtn.style.background = '#e5e7eb';
            cancelBtn.onmouseout = () => cancelBtn.style.background = '#f3f4f6';
            cancelBtn.onclick = () => {
                modal.style.animation = 'fadeOut 0.2s ease';
                setTimeout(() => {
                    modal.remove();
                    resolve(false);
                }, 200);
            };
            
            const confirmBtn = document.createElement('button');
            confirmBtn.textContent = confirmText || 'Confirm';
            confirmBtn.style.cssText = `
                flex: 1;
                padding: 12px;
                background: #dc2626;
                color: white;
                border: none;
                border-radius: 6px;
                font-size: 15px;
                font-weight: 500;
                cursor: pointer;
                transition: all 0.2s;
            `;
            confirmBtn.onmouseover = () => confirmBtn.style.background = '#b91c1c';
            confirmBtn.onmouseout = () => confirmBtn.style.background = '#dc2626';
            confirmBtn.onclick = () => {
                modal.style.animation = 'fadeOut 0.2s ease';
                setTimeout(() => {
                    modal.remove();
                    resolve(true);
                }, 200);
            };
            
            buttonContainer.appendChild(cancelBtn);
            buttonContainer.appendChild(confirmBtn);
            content.appendChild(icon);
            content.appendChild(titleEl);
            content.appendChild(messageEl);
            content.appendChild(buttonContainer);
            modal.appendChild(content);
            document.body.appendChild(modal);
        });
    }
    
    /**
     * Get user-friendly display name for entity type
     */
    function getEntityDisplayName(facetType) {
        const nameMap = {
            'system': 'System',
            'dataset': 'Data Set',
            'business-area': 'Business Area',
            'capability': 'Capability',
            'client': 'Client',
            'committee': 'Committee',
            'geography': 'Geography',
            'glossary': 'Glossary',
            'legal-entity': 'Legal Entity',
            'org-unit': 'Organizational Unit',
            'people': 'Person',
            'policy': 'Policy',
            'process': 'Process',
            'product': 'Product',
            'project': 'Project',
            'regulation': 'Regulation',
            'regulator': 'Regulator',
            'regulatory-theme': 'Regulatory Theme',
            'system-interface': 'System Interface'
        };
        return nameMap[facetType] || facetType.charAt(0).toUpperCase() + facetType.slice(1);
    }

    /**
     * Perform the actual deletion
     */
    async function performDeletion(facetType, objectId, config, options = {}) {
        const response = await fetch(`/api/delete/${facetType}/${objectId}`, {
            method: 'DELETE',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(options)
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || errorData.message || `Failed to delete ${facetType}: ${response.statusText}`);
        }

        const result = await response.json();
        if (!result.success) {
            throw new Error(result.message || `Failed to delete ${facetType}`);
        }

        console.log(`Successfully deleted ${facetType} with ID: ${objectId}`);
        
        // Store datasets and attributes deleted counts for success message
        if (result.datasetsDeleted !== undefined) {
            result._datasetsDeleted = result.datasetsDeleted;
        }
        if (result.attributesDeleted !== undefined) {
            result._attributesDeleted = result.attributesDeleted;
        }
        
        return result;
    }

    /**
     * Show success message with modern toast
     */
    function showSuccessMessage(facetType, deletionResult = {}) {
        // Use the server's formatted message if available, otherwise build our own
        let message = deletionResult.message;
        
        if (!message) {
            const entityName = getEntityDisplayName(facetType);
            message = `${entityName} deleted successfully`;
            
            // Add datasets count if available
            if (deletionResult.datasetsDeleted || deletionResult._datasetsDeleted) {
                const count = deletionResult.datasetsDeleted || deletionResult._datasetsDeleted || 0;
                if (count > 0) {
                    message += ` (${count} data set${count > 1 ? 's' : ''} also removed)`;
                }
            }
            
            // Add attributes count if available
            if (deletionResult.attributesDeleted || deletionResult._attributesDeleted) {
                const count = deletionResult.attributesDeleted || deletionResult._attributesDeleted || 0;
                if (count > 0) {
                    message += ` (${count} attribute${count > 1 ? 's' : ''} also removed)`;
                }
            }
        }
        
        showToast(message, 'success');
    }

    /**
     * Show error message with modern toast
     */
    function showErrorMessage(message) {
        showToast(`Deletion failed: ${message}`, 'error');
    }
    
    /**
     * Show modern toast notification
     */
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `delete-toast delete-toast-${type}`;
        
        const colors = {
            success: { bg: '#10b981', icon: '✓' },
            error: { bg: '#ef4444', icon: '✕' },
            info: { bg: '#3b82f6', icon: 'ℹ' },
            warning: { bg: '#f59e0b', icon: '⚠' }
        };
        
        const config = colors[type] || colors.info;
        
        toast.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: ${config.bg};
            color: white;
            padding: 16px 20px;
            border-radius: 8px;
            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
            z-index: 10001;
            display: flex;
            align-items: center;
            gap: 12px;
            min-width: 280px;
            max-width: 400px;
            font-size: 15px;
            font-weight: 500;
            animation: slideInRight 0.3s ease;
        `;
        
        toast.innerHTML = `
            <span style="font-size: 20px;">${config.icon}</span>
            <span>${escapeHtml(message)}</span>
        `;
        
        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.style.animation = 'slideOutRight 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
        
        // Add animations if not already present
        if (!document.getElementById('delete-toast-styles')) {
            const style = document.createElement('style');
            style.id = 'delete-toast-styles';
            style.textContent = `
                @keyframes slideInRight {
                    from { transform: translateX(100%); opacity: 0; }
                    to { transform: translateX(0); opacity: 1; }
                }
                @keyframes slideOutRight {
                    from { transform: translateX(0); opacity: 1; }
                    to { transform: translateX(100%); opacity: 0; }
                }
            `;
            document.head.appendChild(style);
        }
    }
    
    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Get list page URL for facet
     */
    function getListPageUrl(facetType) {
        const urlMap = {
            'system': '/search.html?facet=System',
            'dataset': '/search.html?facet=Dataset',
            'business-area': '/search.html?facet=Business%20Area',
            'capability': '/search.html?facet=Capability',
            'client': '/search.html?facet=Client',
            'committee': '/search.html?facet=Committee',
            'geography': '/search.html?facet=Geography',
            'glossary': '/search.html?facet=Glossary',
            'legal-entity': '/search.html?facet=Legal%20Entity',
            'org-unit': '/search.html?facet=Org%20Unit',
            'people': '/search.html?facet=People',
            'policy': '/search.html?facet=Policy',
            'process': '/search.html?facet=Process',
            'product': '/search.html?facet=Product',
            'project': '/search.html?facet=Project',
            'regulation': '/search.html?facet=Regulation',
            'regulator': '/search.html?facet=Regulator',
            'regulatory-theme': '/search.html?facet=Regulatory%20Theme',
            'system-interface': '/search.html?facet=System%20Interface'
        };

        return urlMap[facetType] || '/search.html';
    }

    // Public API
    return {
        handleDelete: handleDelete,
        FACET_CONFIG: FACET_CONFIG
    };
})();
