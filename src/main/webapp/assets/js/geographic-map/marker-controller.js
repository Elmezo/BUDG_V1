/**
 * Marker Controller - Handles marker CRUD operations and interactions
 */

(function() {
    'use strict';

    const markerLayers = {}; // layerId -> L.LayerGroup
    const markers = {}; // markerId -> L.Marker

    /**
     * Load markers for a layer
     * @param {number} layerId - Layer ID
     */
    window.loadMarkersForLayer = async function(layerId) {
        try {
            const response = await fetch(`/api/layers/${layerId}/markers`, {
                credentials: 'include'
            });
            if (!response.ok) {
                throw new Error('Failed to load markers');
            }
            const markerData = await response.json();
            
            const map = window.getMapInstance();
            if (!map) {
                console.error('[MARKER] Map instance not found');
                return;
            }

            // Create layer group for this layer
            if (!markerLayers[layerId]) {
                markerLayers[layerId] = L.layerGroup().addTo(map);
            }

            // Clear existing markers for this layer
            markerLayers[layerId].clearLayers();

            // Add markers
            markerData.forEach(data => {
                const marker = createMarker(data);
                marker.addTo(markerLayers[layerId]);
                markers[data.id] = marker;
            });

            console.log(`[MARKER] Loaded ${markerData.length} markers for layer ${layerId}`);
        } catch (error) {
            console.error('[MARKER] Error loading markers:', error);
        }
    };

    /**
     * Create a Leaflet marker from data
     * @param {Object} data - Marker data
     */
    function createMarker(data) {
        const lat = parseFloat(data.lat);
        const lng = parseFloat(data.lng);
        
        // Create icon if URL provided
        let icon = null;
        if (data.icon_url) {
            icon = L.icon({
                iconUrl: data.icon_url,
                iconSize: [32, 32],
                iconAnchor: [16, 32],
                popupAnchor: [0, -32]
            });
        }

        const marker = L.marker([lat, lng], { icon: icon, draggable: true });
        
        // Add popup
        const popupContent = createPopupContent(data);
        marker.bindPopup(popupContent);

        // Handle drag end - save new position
        marker.on('dragend', function() {
            const newLat = marker.getLatLng().lat;
            const newLng = marker.getLatLng().lng;
            updateMarkerPosition(data.id, newLat, newLng);
        });

        // Store marker data
        marker._markerData = data;

        return marker;
    }

    /**
     * Create popup content HTML
     * @param {Object} data - Marker data
     */
    function createPopupContent(data) {
        let html = '<div class="marker-popup">';
        if (data.title) {
            html += `<h3>${escapeHtml(data.title)}</h3>`;
        }
        if (data.description) {
            html += `<p>${escapeHtml(data.description)}</p>`;
        }
        html += `<div class="marker-actions">`;
        html += `<button onclick="editMarker(${data.id})">Edit</button>`;
        html += `<button onclick="deleteMarker(${data.id})">Delete</button>`;
        html += `</div>`;
        html += '</div>';
        return html;
    }

    /**
     * Update marker position after drag
     * @param {number} markerId - Marker ID
     * @param {number} lat - New latitude
     * @param {number} lng - New longitude
     */
    async function updateMarkerPosition(markerId, lat, lng) {
        try {
            const marker = markers[markerId];
            if (!marker || !marker._markerData) return;

            const updateData = {
                ...marker._markerData,
                lat: lat,
                lng: lng
            };

            const response = await fetch(`/api/markers/${markerId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(updateData)
            });

            if (response.ok) {
                marker._markerData.lat = lat;
                marker._markerData.lng = lng;
                console.log(`[MARKER] Position updated for marker ${markerId}`);
            } else {
                console.error('[MARKER] Failed to update position');
            }
        } catch (error) {
            console.error('[MARKER] Error updating position:', error);
        }
    }

    /**
     * Create a new marker
     * @param {number} layerId - Layer ID
     * @param {number} lat - Latitude
     * @param {number} lng - Longitude
     * @param {Object} data - Marker data (title, description, etc.)
     */
    window.createMarker = async function(layerId, lat, lng, data = {}) {
        try {
            const markerData = {
                layer_id: layerId,
                lat: lat,
                lng: lng,
                title: data.title || '',
                description: data.description || '',
                icon_url: data.icon_url || null,
                metadata_json: data.metadata ? JSON.stringify(data.metadata) : null
            };

            const response = await fetch(`/api/layers/${layerId}/markers`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(markerData)
            });

            if (!response.ok) {
                throw new Error('Failed to create marker');
            }

            const created = await response.json();
            
            // Add marker to map
            const marker = createMarker(created);
            if (!markerLayers[layerId]) {
                const map = window.getMapInstance();
                markerLayers[layerId] = L.layerGroup().addTo(map);
            }
            marker.addTo(markerLayers[layerId]);
            markers[created.id] = marker;

            return created;
        } catch (error) {
            console.error('[MARKER] Error creating marker:', error);
            throw error;
        }
    };

    /**
     * Delete a marker
     * @param {number} markerId - Marker ID
     */
    window.deleteMarker = async function(markerId) {
        try {
            const response = await fetch(`/api/markers/${markerId}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (response.ok) {
                // Remove from map
                const marker = markers[markerId];
                if (marker) {
                    const layerId = marker._markerData?.layer_id;
                    if (layerId && markerLayers[layerId]) {
                        markerLayers[layerId].removeLayer(marker);
                    }
                    delete markers[markerId];
                }
                console.log(`[MARKER] Marker ${markerId} deleted`);
            } else {
                throw new Error('Failed to delete marker');
            }
        } catch (error) {
            console.error('[MARKER] Error deleting marker:', error);
            throw error;
        }
    };

    /**
     * Edit a marker (opens edit dialog)
     * @param {number} markerId - Marker ID
     */
    window.editMarker = function(markerId) {
        const marker = markers[markerId];
        if (!marker || !marker._markerData) return;

        // TODO: Open edit dialog/modal
        console.log('[MARKER] Edit marker:', markerId);
        // This would typically open a modal with a form
    };

    /**
     * Toggle marker layer visibility
     * @param {number} layerId - Layer ID
     * @param {boolean} visible - Visibility state
     */
    window.toggleMarkerLayer = function(layerId, visible) {
        if (markerLayers[layerId]) {
            const map = window.getMapInstance();
            if (visible) {
                markerLayers[layerId].addTo(map);
            } else {
                map.removeLayer(markerLayers[layerId]);
            }
        }
    };

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Export
    window.MarkerController = {
        load: window.loadMarkersForLayer,
        create: window.createMarker,
        delete: window.deleteMarker,
        edit: window.editMarker,
        toggleLayer: window.toggleMarkerLayer
    };

})();

