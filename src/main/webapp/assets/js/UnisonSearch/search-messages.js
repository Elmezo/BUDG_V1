// User notifications

function showNoDataMessage(category) {
    if (!tableContainer) {
        return;
    }

    // Special icon for Active Tasks
    let icon = 'database';
    if (category === 'people') {
        icon = 'users';
    } else if (category === 'active-tasks' || category === 'activetasks' || category === 'ACTIVE_TASKS') {
        icon = 'tasks';
    }

    const camelCategory = category.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+([a-z])/g, (g) => g[1].toUpperCase());
    // Only try to translate if camelCategory is not empty to avoid 'label.' error
    let displayCategory = category.replace('-', ' ');
    if (camelCategory && window.I18n && typeof window.I18n.t === 'function') {
        const translationKey = 'label.' + camelCategory;
        const translated = window.I18n.t(translationKey);
        if (translated && translated !== translationKey) {
            displayCategory = translated;
        }
    }

    let message = window.I18n?.t('message.noDataInCategory', { category: displayCategory }) || `No ${displayCategory} data available`;
    if (category === 'active-tasks' || category === 'activetasks' || category === 'ACTIVE_TASKS') {
        message = window.I18n?.t('message.noActiveTasks') || 'No active tasks found';
    }

    tableContainer.innerHTML = `<div class="no-data-content">
        <i class="fas fa-${icon}"></i>
        <p>${message}</p>
    </div>`;
}

function showErrorMessage(message) {
    if (tableContainer) {
        tableContainer.classList.add('empty');
        tableContainer.setAttribute('data-error', message);
    }
}

/**
 * Show a more specific message for related searches with no results
 * @param {string} category - The current category
 * @param {Object} selectedItem - The selected item from another category
 */
function showRelatedNoDataMessage(category, selectedItem) {
    if (!tableContainer) {
        return;
    }

    const relationshipText = getRelationshipText(category, selectedItem.category);

    const camelCategory = category.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+([a-z])/g, (g) => g[1].toUpperCase());
    // Only try to translate if camelCategory is not empty to avoid 'label.' error
    let displayCategory = category.replace('-', ' ');
    if (camelCategory && window.I18n && typeof window.I18n.t === 'function') {
        const translationKey = 'label.' + camelCategory;
        const translated = window.I18n.t(translationKey);
        if (translated && translated !== translationKey) {
            displayCategory = translated;
        }
    }

    tableContainer.innerHTML = `<div class="no-data-content">
        <i class="fas fa-link-slash"></i>
        <p>${window.I18n?.t('message.noRelatedData', { category: displayCategory, name: selectedItem.name, parentCategory: selectedItem.category }) || `No ${displayCategory} data related to "${selectedItem.name}" (${selectedItem.category})`}</p>
        <p class="relationship-hint">${relationshipText}</p>
        <button class="clear-related-btn" onclick="clearSelectedItem()">${window.I18n?.t('button.clearSelection') || 'Clear Selection'}</button>
    </div>`;

    // Add styles for the new elements
    const style = document.createElement('style');
    style.textContent = `
        .no-data-content .relationship-hint {
            font-size: 0.9em;
            color: var(--text-secondary);
            margin-top: 0.5rem;
        }
        .no-data-content .clear-related-btn {
            margin-top: 1rem;
            padding: 0.5rem 1rem;
            background-color: #248567;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 0.9em;
        }
        .no-data-content .clear-related-btn:hover {
            background-color: var(--primary-dark);
        }
    `;
    document.head.appendChild(style);
}

/**
 * Show toast notification message
 * @param {string} message - Message text
 * @param {string} type - Message type: 'info', 'success', 'error' (default: 'info')
 */
function showToast(message, type = 'info') {
    // Remove existing toast if any
    const existingToast = document.querySelector('.search-toast');
    if (existingToast) {
        existingToast.remove();
    }

    // Create toast notification
    const toast = document.createElement('div');
    toast.className = `search-toast search-toast-${type}`;
    toast.textContent = message;

    // Set styles
    const bgColor = type === 'error' ? '#dc3545' : type === 'success' ? '#198754' : '#0d6efd';
    toast.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: ${bgColor};
        color: white;
        padding: 12px 20px;
        border-radius: 4px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        z-index: 10000;
        animation: slideIn 0.3s ease-out;
        max-width: 300px;
        font-size: 14px;
    `;

    // Add animation styles if not already added
    if (!document.getElementById('toast-animations')) {
        const style = document.createElement('style');
        style.id = 'toast-animations';
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
        `;
        document.head.appendChild(style);
    }

    document.body.appendChild(toast);

    // Auto-remove after 3 seconds
    setTimeout(() => {
        toast.style.animation = 'slideOut 0.3s ease-out';
        setTimeout(() => {
            if (toast.parentElement) {
                toast.parentElement.removeChild(toast);
            }
        }, 300);
    }, 3000);
}


