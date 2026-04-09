/**
 * Universal Hierarchy Helper - Following Regulatory Theme Pattern
 * This module provides reusable hierarchy functions for all entity types
 */

(function() {
    'use strict';

    /**
     * Build family lineage tree (ancestors + current + descendants + siblings)
     * @param {Array} items - Array of all items
     * @param {Number|String} currentItemId - ID of current item
     * @param {Object} options - Configuration options
     * @param {Function} options.getIdmeta - Function to extract ID from item
     * @param {Function} options.getParentId - Function to extract parent ID from item
     * @returns {Array} Filtered array containing only family tree members
     */
    function buildFamilyLineage(items, currentItemId, options = {}) {
        const getId = options.getId || ((item) => item.ID ?? item.id);
        const getParentId = options.getParentId || ((item) => item.Parent_ID ?? item.parent_id ?? item.parentId);
        
        const byId = new Map();
        items.forEach(item => {
            const id = parseInt(getId(item));
            byId.set(id, item);
        });
        
        const currentId = parseInt(currentItemId);
        const currentItem = byId.get(currentId);
        
        if (!currentItem) {
            console.log('Current item not found in hierarchy');
            return items;
        }
        
        // Find all ancestors (parents, grandparents, etc.)
        const ancestors = new Set();
        let current = currentItem;
        
        while (current) {
            const parentId = getParentId(current);
            const parentIdNum = parseInt(parentId);
            
            if (parentId && parentId !== 0 && !isNaN(parentIdNum) && byId.has(parentIdNum)) {
                ancestors.add(parentIdNum);
                current = byId.get(parentIdNum);
            } else {
                break;
            }
        }
        
        // Find all descendants (children, grandchildren, etc.)
        const descendants = new Set();
        function findDescendants(id) {
            const children = items.filter(item => {
                const parentId = getParentId(item);
                return parseInt(parentId) === parseInt(id);
            });
            
            children.forEach(child => {
                const childId = parseInt(getId(child));
                descendants.add(childId);
                findDescendants(childId);
            });
        }
        findDescendants(currentId);
        
        // Find siblings and their descendants
        if (currentItem) {
            const currentParentId = getParentId(currentItem);
            if (currentParentId) {
                const parentId = parseInt(currentParentId);
                const siblings = items.filter(item => {
                    const itemParentId = parseInt(getParentId(item));
                    const itemId = parseInt(getId(item));
                    return itemParentId === parentId && itemId !== currentId;
                });
                
                siblings.forEach(sibling => {
                    const siblingId = parseInt(getId(sibling));
                    descendants.add(siblingId);
                    findDescendants(siblingId);
                });
            }
        }
        
        // Include current, ancestors, and descendants
        const includedIds = new Set([currentId, ...ancestors, ...descendants]);
        
        return items.filter(item => {
            const id = parseInt(getId(item));
            return includedIds.has(id);
        });
    }

    /**
     * Build hierarchy tree structure
     * @param {Array} items - Array of items to build tree from
     * @param {Number|String} rootId - ID of root item (or null for top-level)
     * @param {Object} options - Configuration options
     * @param {Function} options.getId - Function to extract ID from item
     * @param {Function} options.getParentId - Function to extract parent ID from item
     * @returns {Object} Object with {rows: Array, parentMap: Map}
     */
    function buildHierarchyTree(items, rootId, options = {}) {
        const getId = options.getId || ((item) => item.ID ?? item.id);
        const getParentId = options.getParentId || ((item) => item.Parent_ID ?? item.parent_id ?? item.parentId);
        
        const byParent = new Map();
        const byId = new Map();
        
        items.forEach(item => {
            const id = parseInt(getId(item));
            const parentId = getParentId(item);
            const parentIdNum = parentId ? parseInt(parentId) : null;
            byId.set(id, item);
            
            if (!byParent.has(parentIdNum)) {
                byParent.set(parentIdNum, []);
            }
            byParent.get(parentIdNum).push(item);
        });
        
        const rows = [];
        function buildRows(itemId, depth = 0) {
            const currentItem = byId.get(itemId);
            if (currentItem) {
                const childCount = (byParent.get(itemId) || []).length;
                
                rows.push({
                    node: currentItem,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: childCount > 0
                });
                
                const children = byParent.get(itemId) || [];
                children.forEach(item => {
                    buildRows(parseInt(getId(item)), depth + 1);
                });
            } else {
                const children = byParent.get(itemId) || [];
                children.forEach(item => {
                    buildRows(parseInt(getId(item)), depth);
                });
            }
        }
        
        const rootIdNum = rootId ? parseInt(rootId) : null;
        buildRows(rootIdNum);
        
        return { rows, parentMap: byParent };
    }

    /**
     * Render hierarchy table following Regulatory Theme pattern
     * @param {Object} hierarchyRows - Result from buildHierarchyTree
     * @param {Number|String} currentId - ID of current item
     * @param {Object} options - Configuration options
     * @returns {String} HTML string for table rows
     */
    function renderHierarchyTable(hierarchyRows, currentId, options = {}) {
        const {
            getId = (node) => node.ID ?? node.id,
            getName = (node) => node.PrimaryName ?? node.primaryName ?? node.Name ?? node.name,
            getDescription = (node) => node.Description ?? node.description,
            getLastUpdated = (node) => node.LastUpdateDatetime ?? node.lastUpdateDatetime ?? node.Last_Updated_Datetime,
            getParentId = (node) => node.Parent_ID ?? node.parent_id ?? node.parentId,
            getRefNumber = (node) => node.RefNumber ?? node.refNumber,
            iconClass = 'fas fa-circle',
            linkPattern = '/view/{entity}/{id}',
            entityName = 'Item'
        } = options;

        const formatDate = (dateString) => {
            if (!dateString) return '-';
            try {
                const parsedDate = new Date(dateString);
                if (Number.isNaN(parsedDate.getTime())) {
                    return '-';
                }
                return parsedDate.toLocaleDateString();
            } catch (error) {
                return '-';
            }
        };

        const escapeHtml = (text) => {
            if (text == null) return '';
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        };

        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const name = getName(node) || `Unnamed ${entityName}`;
            const desc = getDescription(node) || '';
            const lastUpdated = formatDate(getLastUpdated(node));
            const isCurrent = String(getId(node)) === String(currentId);
            const id = getId(node);
            const parentId = getParentId(node) || '';
            const refNumber = getRefNumber ? getRefNumber(node) : null;
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'item-link current-item-link' : 'item-link';
            const link = linkPattern.replace('{entity}', entityName.toLowerCase().replace(/ /g, '-')).replace('{id}', encodeURIComponent(id));
            const linkHtml = `<a class="${linkClass}" href="${link}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            const refHtml = refNumber ? `<span class="item-ref">(${escapeHtml(refNumber)})</span>` : '';
            
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="${iconClass} item-icon"></i><span class="item-name">${linkHtml}</span>${refHtml}${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
                <td><span title="${lastUpdated}">${lastUpdated}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    /**
     * Initialize hierarchy interactions (expand/collapse)
     * @param {HTMLElement} containerEl - Container element for the hierarchy table
     * @param {Object} hierarchyRows - Result from buildHierarchyTree
     */
    function initHierarchyInteractions(containerEl, hierarchyRows) {
        containerEl.addEventListener('click', function(e) {
            if (e.target.closest('.tree-expander')) {
                e.preventDefault();
                e.stopPropagation();
                
                const button = e.target.closest('.tree-expander');
                const row = button.closest('tr');
                const parentId = parseInt(row.dataset.id);
                const currentDepth = parseInt(row.dataset.depth);
                const icon = button.querySelector('i');
                
                const isExpanded = icon.classList.contains('fa-caret-down');
                icon.classList.toggle('fa-caret-down', !isExpanded);
                icon.classList.toggle('fa-caret-right', isExpanded);
                
                let nextRow = row.nextElementSibling;
                while (nextRow && parseInt(nextRow.dataset.depth) > currentDepth) {
                    const childDepth = parseInt(nextRow.dataset.depth);
                    
                    if (childDepth === currentDepth + 1) {
                        nextRow.style.display = isExpanded ? 'none' : '';
                        
                        if (isExpanded) {
                            const childExpander = nextRow.querySelector('.tree-expander i');
                            if (childExpander && childExpander.classList.contains('fa-caret-down')) {
                                childExpander.classList.remove('fa-caret-down');
                                childExpander.classList.add('fa-caret-right');
                            }
                        }
                    } else if (childDepth > currentDepth + 1) {
                        if (isExpanded) {
                            nextRow.style.display = 'none';
                        }
                    }
                    
                    nextRow = nextRow.nextElementSibling;
                }
            }
        });
    }

    /**
     * Find root ID (top-most ancestor) for an item
     * @param {Array} items - Array of items
     * @param {Number|String} currentItemId - ID of current item
     * @param {Object} options - Configuration options
     * @returns {Number} Root ID
     */
    function findRootId(items, currentItemId, options = {}) {
        const getId = options.getId || ((item) => item.ID ?? item.id);
        const getParentId = options.getParentId || ((item) => item.Parent_ID ?? item.parent_id ?? item.parentId);
        
        const byId = new Map();
        items.forEach(item => byId.set(parseInt(getId(item)), item));
        
        let rootId = parseInt(currentItemId);
        let current = byId.get(rootId);
        
        while (current) {
            const parentId = getParentId(current);
            const parentIdNum = parseInt(parentId);
            if (parentId && !isNaN(parentIdNum) && byId.has(parentIdNum)) {
                rootId = parentIdNum;
                current = byId.get(parentIdNum);
            } else {
                break;
            }
        }
        
        return rootId;
    }

    // Export to global scope
    window.HierarchyHelper = {
        buildFamilyLineage,
        buildHierarchyTree,
        renderHierarchyTable,
        initHierarchyInteractions,
        findRootId
    };

})();

