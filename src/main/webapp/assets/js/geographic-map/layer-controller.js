/**
 * Layer Controller - Handles layer visibility, ordering, and management
 */

(function() {
    'use strict';

    const layerVisibility = {}; // layerId -> boolean

    /**
     * Initialize layer list/legend panel
     * @param {number} mapId - Map ID
     */
    window.initLayerPanel = async function(mapId) {
        try {
            const response = await fetch(`/api/maps/${mapId}/layers`, {
                credentials: 'include'
            });
            if (!response.ok) {
                throw new Error('Failed to load layers');
            }
            const layers = await response.json();
            
            renderLayerList(layers);
            attachLayerHandlers();
        } catch (error) {
            console.error('[LAYER] Error initializing layer panel:', error);
        }
    };

    /**
     * Render layer list in sidebar
     * @param {Array} layers - Array of layer objects
     */
    function renderLayerList(layers) {
        const container = document.getElementById('layerList');
        if (!container) {
            console.warn('[LAYER] Layer list container not found');
            return;
        }

        container.innerHTML = '';

        // Sort by order_index
        layers.sort((a, b) => (a.order_index || 0) - (b.order_index || 0));

        layers.forEach(layer => {
            const layerItem = createLayerItem(layer);
            container.appendChild(layerItem);
            layerVisibility[layer.id] = layer.visible !== false;
        });
    }

    /**
     * Create a layer list item
     * @param {Object} layer - Layer data
     */
    function createLayerItem(layer) {
        const item = document.createElement('div');
        item.className = 'layer-item';
        item.dataset.layerId = layer.id;
        item.dataset.layerType = layer.type;

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.id = `layer-${layer.id}`;
        checkbox.checked = layer.visible !== false;
        checkbox.addEventListener('change', () => toggleLayerVisibility(layer.id, checkbox.checked));

        const label = document.createElement('label');
        label.htmlFor = `layer-${layer.id}`;
        label.textContent = layer.name || `Layer ${layer.id}`;

        const typeBadge = document.createElement('span');
        typeBadge.className = 'layer-type-badge';
        typeBadge.textContent = layer.type;
        typeBadge.title = `Type: ${layer.type}`;

        item.appendChild(checkbox);
        item.appendChild(label);
        item.appendChild(typeBadge);

        return item;
    }

    /**
     * Toggle layer visibility
     * @param {number} layerId - Layer ID
     * @param {boolean} visible - Visibility state
     */
    async function toggleLayerVisibility(layerId, visible) {
        try {
            // Update local state
            layerVisibility[layerId] = visible;

            // Update via API
            const response = await fetch(`/api/layers/${layerId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    visible: visible
                })
            });

            if (!response.ok) {
                throw new Error('Failed to update layer visibility');
            }

            // Update map display based on layer type
            const layerItem = document.querySelector(`[data-layer-id="${layerId}"]`);
            const layerType = layerItem?.dataset.layerType;

            if (layerType === 'marker') {
                if (typeof window.toggleMarkerLayer === 'function') {
                    window.toggleMarkerLayer(layerId, visible);
                }
            } else if (['polygon', 'polyline', 'circle', 'rectangle'].includes(layerType)) {
                if (typeof window.toggleShapeLayer === 'function') {
                    window.toggleShapeLayer(layerId, visible);
                }
            }

            console.log(`[LAYER] Layer ${layerId} visibility: ${visible}`);
        } catch (error) {
            console.error('[LAYER] Error toggling visibility:', error);
            // Revert checkbox
            const checkbox = document.getElementById(`layer-${layerId}`);
            if (checkbox) {
                checkbox.checked = !visible;
            }
        }
    }

    /**
     * Attach event handlers for layer management
     */
    function attachLayerHandlers() {
        // Add handlers for layer reordering, editing, etc.
        // This would be expanded based on UI requirements
    }

    /**
     * Create a new layer
     * @param {number} mapId - Map ID
     * @param {Object} layerData - Layer data
     */
    window.createLayer = async function(mapId, layerData) {
        try {
            const response = await fetch(`/api/maps/${mapId}/layers`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(layerData)
            });

            if (!response.ok) {
                throw new Error('Failed to create layer');
            }

            const created = await response.json();
            
            // Reload layer list
            await window.initLayerPanel(mapId);
            
            return created;
        } catch (error) {
            console.error('[LAYER] Error creating layer:', error);
            throw error;
        }
    };

    /**
     * Delete a layer
     * @param {number} layerId - Layer ID
     */
    window.deleteLayer = async function(layerId) {
        try {
            const response = await fetch(`/api/layers/${layerId}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error('Failed to delete layer');
            }

            // Remove from UI
            const layerItem = document.querySelector(`[data-layer-id="${layerId}"]`);
            if (layerItem) {
                layerItem.remove();
            }

            delete layerVisibility[layerId];
            console.log(`[LAYER] Layer ${layerId} deleted`);
        } catch (error) {
            console.error('[LAYER] Error deleting layer:', error);
            throw error;
        }
    };

    // Export
    window.LayerController = {
        init: window.initLayerPanel,
        create: window.createLayer,
        delete: window.deleteLayer,
        toggleVisibility: toggleLayerVisibility
    };

})();

