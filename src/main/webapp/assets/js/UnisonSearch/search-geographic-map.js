/**
 * Geographic Map View for Search Results
 * Displays search results as markers on a geographic map
 * Uses HTML5 Canvas (no external libraries)
 */

(function() {
    'use strict';

    let mapCanvas = null;
    let mapCtx = null;
    let markers = [];
    let mapBounds = { minLat: 30.0, maxLat: 31.5, minLng: 31.0, maxLng: 32.0 }; // Default: Cairo area
    let mapCenter = { lat: 30.044420, lng: 31.235712 };
    let zoomLevel = 10;
    let isDragging = false;
    let dragStart = { x: 0, y: 0 };
    let panOffset = { x: 0, y: 0 };

    /**
     * Render geographic map view with current search results
     */
    window.renderGeographicMapView = async function() {
        const mapContainer = document.getElementById('searchResultsMap');
        if (!mapContainer) {
            console.error('[GEO-MAP] Map container not found');
            return;
        }

        // Clear container
        mapContainer.innerHTML = '';

        // Get current category and data
        const category = typeof getActiveCategoryWithFallback === 'function'
            ? getActiveCategoryWithFallback()
            : null;

        if (!category) {
            mapContainer.innerHTML = `
                <div style="display: flex; align-items: center; justify-content: center; height: 100%; flex-direction: column;">
                    <i class="fas fa-map" style="font-size: 48px; color: #ccc; margin-bottom: 16px;"></i>
                    <p style="color: #666;">Select a category from the sidebar to view results on map</p>
                </div>
            `;
            return;
        }

        // Create canvas for map
        mapCanvas = document.createElement('canvas');
        mapCanvas.id = 'geographicMapCanvas';
        mapCanvas.style.width = '100%';
        mapCanvas.style.height = '100%';
        mapCanvas.style.cursor = 'grab';
        mapContainer.appendChild(mapCanvas);

        // Set canvas size
        resizeCanvas();

        // Get context
        mapCtx = mapCanvas.getContext('2d');

        // Get search results data
        let data = [];
        try {
            const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;
            const cached = typeof getCachedModuleData === 'function'
                ? getCachedModuleData(category, signature)
                : null;

            if (cached && cached.data && cached.data.length) {
                data = cached.data;
            } else {
                // Fetch fresh data
                if (typeof fetchCategoryData === 'function') {
                    const query = typeof getCurrentQuery === 'function' ? getCurrentQuery() : '';
                    data = await fetchCategoryData(category, query);
                }
            }
        } catch (error) {
            console.error('[GEO-MAP] Error loading data:', error);
        }

        // Extract markers from data
        markers = extractMarkersFromData(data, category);

        // Calculate bounds from markers
        if (markers.length > 0) {
            calculateBounds();
        }

        // Draw map
        drawMap();

        // Add event listeners
        setupMapEvents();
    };

    /**
     * Extract geographic markers from search results
     */
    function extractMarkersFromData(data, category) {
        const extractedMarkers = [];
        
        if (!data || !Array.isArray(data)) {
            return extractedMarkers;
        }

        data.forEach((row, index) => {
            // Try to find coordinates in the data
            // Check common field names for coordinates
            let lat = null;
            let lng = null;

            // Check for explicit lat/lng fields
            if (row['Latitude'] || row['latitude'] || row['Lat']) {
                lat = parseFloat(row['Latitude'] || row['latitude'] || row['Lat']);
            }
            if (row['Longitude'] || row['longitude'] || row['Lng'] || row['Lon']) {
                lng = parseFloat(row['Longitude'] || row['longitude'] || row['Lng'] || row['Lon']);
            }

            // If no coordinates found, use default distribution (for demo)
            if (!lat || !lng || isNaN(lat) || isNaN(lng)) {
                // Distribute markers in a grid pattern around center
                const gridSize = Math.ceil(Math.sqrt(data.length));
                const rowIndex = Math.floor(index / gridSize);
                const colIndex = index % gridSize;
                lat = mapCenter.lat + (rowIndex - gridSize/2) * 0.1;
                lng = mapCenter.lng + (colIndex - gridSize/2) * 0.1;
            }

            const marker = {
                id: row['ID'] || row['id'] || index,
                name: row['Name'] || row['name'] || row['Primary Name'] || `Item ${index + 1}`,
                ref: row['Ref.'] || row['Ref'] || row['ref'] || '',
                description: row['Description'] || row['description'] || '',
                lat: lat,
                lng: lng,
                category: category,
                data: row
            };

            extractedMarkers.push(marker);
        });

        return extractedMarkers;
    }

    /**
     * Calculate map bounds from markers
     */
    function calculateBounds() {
        if (markers.length === 0) return;

        let minLat = Infinity, maxLat = -Infinity;
        let minLng = Infinity, maxLng = -Infinity;

        markers.forEach(marker => {
            if (marker.lat < minLat) minLat = marker.lat;
            if (marker.lat > maxLat) maxLat = marker.lat;
            if (marker.lng < minLng) minLng = marker.lng;
            if (marker.lng > maxLng) maxLng = marker.lng;
        });

        // Add padding
        const latPadding = (maxLat - minLat) * 0.1 || 0.1;
        const lngPadding = (maxLng - minLng) * 0.1 || 0.1;

        mapBounds = {
            minLat: minLat - latPadding,
            maxLat: maxLat + latPadding,
            minLng: minLng - lngPadding,
            maxLng: maxLng + lngPadding
        };

        // Update center
        mapCenter = {
            lat: (minLat + maxLat) / 2,
            lng: (minLng + maxLng) / 2
        };
    }

    /**
     * Draw the map
     */
    function drawMap() {
        if (!mapCanvas || !mapCtx) return;

        const width = mapCanvas.width;
        const height = mapCanvas.height;

        // Clear canvas
        mapCtx.clearRect(0, 0, width, height);

        // Draw background (simple grid pattern)
        drawBackground();

        // Draw markers
        markers.forEach(marker => {
            drawMarker(marker);
        });

        // Draw scale
        drawScale();
    }

    /**
     * Draw background grid
     */
    function drawBackground() {
        const width = mapCanvas.width;
        const height = mapCanvas.height;

        // Fill background
        mapCtx.fillStyle = '#e8f4f8';
        mapCtx.fillRect(0, 0, width, height);

        // Draw grid lines
        mapCtx.strokeStyle = '#d0e0e8';
        mapCtx.lineWidth = 1;

        const gridSpacing = 50;
        for (let x = 0; x < width; x += gridSpacing) {
            mapCtx.beginPath();
            mapCtx.moveTo(x, 0);
            mapCtx.lineTo(x, height);
            mapCtx.stroke();
        }

        for (let y = 0; y < height; y += gridSpacing) {
            mapCtx.beginPath();
            mapCtx.moveTo(0, y);
            mapCtx.lineTo(width, y);
            mapCtx.stroke();
        }
    }

    /**
     * Draw a marker on the map
     */
    function drawMarker(marker) {
        const point = latLngToPixel(marker.lat, marker.lng);
        const x = point.x + panOffset.x;
        const y = point.y + panOffset.y;

        // Skip if outside viewport
        if (x < -20 || x > mapCanvas.width + 20 || y < -20 || y > mapCanvas.height + 20) {
            return;
        }

        // Draw marker circle
        mapCtx.beginPath();
        mapCtx.arc(x, y, 8, 0, Math.PI * 2);
        mapCtx.fillStyle = '#f97316';
        mapCtx.fill();
        mapCtx.strokeStyle = '#ea580c';
        mapCtx.lineWidth = 2;
        mapCtx.stroke();

        // Draw marker pin
        mapCtx.beginPath();
        mapCtx.moveTo(x, y + 8);
        mapCtx.lineTo(x - 6, y + 16);
        mapCtx.lineTo(x + 6, y + 16);
        mapCtx.closePath();
        mapCtx.fillStyle = '#ea580c';
        mapCtx.fill();
    }

    /**
     * Convert lat/lng to pixel coordinates
     */
    function latLngToPixel(lat, lng) {
        const width = mapCanvas.width;
        const height = mapCanvas.height;

        const x = ((lng - mapBounds.minLng) / (mapBounds.maxLng - mapBounds.minLng)) * width;
        const y = height - ((lat - mapBounds.minLat) / (mapBounds.maxLat - mapBounds.minLat)) * height;

        return { x, y };
    }

    /**
     * Convert pixel coordinates to lat/lng
     */
    function pixelToLatLng(x, y) {
        const width = mapCanvas.width;
        const height = mapCanvas.height;

        const lng = mapBounds.minLng + (x / width) * (mapBounds.maxLng - mapBounds.minLng);
        const lat = mapBounds.maxLat - (y / height) * (mapBounds.maxLat - mapBounds.minLat);

        return { lat, lng };
    }

    /**
     * Draw scale bar
     */
    function drawScale() {
        const width = mapCanvas.width;
        const height = mapCanvas.height;

        // Calculate approximate scale (1 pixel = X km)
        const latDiff = mapBounds.maxLat - mapBounds.minLat;
        const lngDiff = mapBounds.maxLng - mapBounds.minLng;
        const avgLat = (mapBounds.minLat + mapBounds.maxLat) / 2;
        
        // Approximate km per degree
        const kmPerDegreeLat = 111;
        const kmPerDegreeLng = 111 * Math.cos(avgLat * Math.PI / 180);
        
        const kmWidth = lngDiff * kmPerDegreeLng;
        const scaleKm = Math.pow(10, Math.floor(Math.log10(kmWidth / 5)));
        const scalePixels = (scaleKm / kmWidth) * width;

        // Draw scale bar
        const scaleX = 20;
        const scaleY = height - 40;

        mapCtx.strokeStyle = '#333';
        mapCtx.lineWidth = 2;
        mapCtx.beginPath();
        mapCtx.moveTo(scaleX, scaleY);
        mapCtx.lineTo(scaleX + scalePixels, scaleY);
        mapCtx.moveTo(scaleX, scaleY - 5);
        mapCtx.lineTo(scaleX, scaleY + 5);
        mapCtx.moveTo(scaleX + scalePixels, scaleY - 5);
        mapCtx.lineTo(scaleX + scalePixels, scaleY + 5);
        mapCtx.stroke();

        // Draw scale text
        mapCtx.fillStyle = '#333';
        mapCtx.font = '12px Inter, sans-serif';
        mapCtx.fillText(`${scaleKm} km`, scaleX, scaleY - 10);
    }

    /**
     * Setup map event listeners
     */
    function setupMapEvents() {
        if (!mapCanvas) return;

        // Mouse down - start drag
        mapCanvas.addEventListener('mousedown', (e) => {
            isDragging = true;
            dragStart.x = e.offsetX - panOffset.x;
            dragStart.y = e.offsetY - panOffset.y;
            mapCanvas.style.cursor = 'grabbing';
        });

        // Mouse move - drag map
        mapCanvas.addEventListener('mousemove', (e) => {
            if (isDragging) {
                panOffset.x = e.offsetX - dragStart.x;
                panOffset.y = e.offsetY - dragStart.y;
                drawMap();
            }
        });

        // Mouse up - end drag
        mapCanvas.addEventListener('mouseup', () => {
            isDragging = false;
            mapCanvas.style.cursor = 'grab';
        });

        // Mouse leave - end drag
        mapCanvas.addEventListener('mouseleave', () => {
            isDragging = false;
            mapCanvas.style.cursor = 'grab';
        });

        // Click - show marker info
        mapCanvas.addEventListener('click', (e) => {
            if (isDragging) return; // Ignore click if was dragging

            const clickPoint = pixelToLatLng(e.offsetX - panOffset.x, e.offsetY - panOffset.y);
            
            // Find nearest marker
            let nearestMarker = null;
            let minDistance = Infinity;

            markers.forEach(marker => {
                const distance = Math.sqrt(
                    Math.pow(marker.lat - clickPoint.lat, 2) +
                    Math.pow(marker.lng - clickPoint.lng, 2)
                );
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestMarker = marker;
                }
            });

            if (nearestMarker && minDistance < 0.01) { // Within ~1km
                showMarkerInfo(nearestMarker);
            }
        });

        // Wheel - zoom
        mapCanvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            const delta = e.deltaY > 0 ? 0.9 : 1.1;
            zoomLevel *= delta;
            zoomLevel = Math.max(1, Math.min(20, zoomLevel));
            
            // Adjust bounds based on zoom
            const centerLat = (mapBounds.minLat + mapBounds.maxLat) / 2;
            const centerLng = (mapBounds.minLng + mapBounds.maxLng) / 2;
            const latRange = (mapBounds.maxLat - mapBounds.minLat) / delta;
            const lngRange = (mapBounds.maxLng - mapBounds.minLng) / delta;

            mapBounds = {
                minLat: centerLat - latRange / 2,
                maxLat: centerLat + latRange / 2,
                minLng: centerLng - lngRange / 2,
                maxLng: centerLng + lngRange / 2
            };

            drawMap();
        });

        // Window resize
        window.addEventListener('resize', () => {
            resizeCanvas();
            drawMap();
        });
    }

    /**
     * Resize canvas to match container
     */
    function resizeCanvas() {
        if (!mapCanvas) return;
        
        const container = mapCanvas.parentElement;
        if (!container) return;

        const rect = container.getBoundingClientRect();
        mapCanvas.width = rect.width;
        mapCanvas.height = rect.height || 600;
    }

    /**
     * Show marker information popup
     */
    function showMarkerInfo(marker) {
        // Remove existing popup
        const existingPopup = document.querySelector('.marker-popup');
        if (existingPopup) {
            existingPopup.remove();
        }

        // Create popup
        const popup = document.createElement('div');
        popup.className = 'marker-popup';
        popup.style.cssText = `
            position: absolute;
            background: white;
            border: 1px solid #ddd;
            border-radius: 4px;
            padding: 12px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            max-width: 300px;
            z-index: 1000;
            top: 20px;
            right: 20px;
        `;

        popup.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 8px;">
                <h3 style="margin: 0; font-size: 16px; font-weight: 600;">${escapeHtml(marker.name)}</h3>
                <button onclick="this.closest('.marker-popup').remove()" style="background: none; border: none; cursor: pointer; font-size: 18px; color: #999;">&times;</button>
            </div>
            ${marker.ref ? `<div style="color: #666; font-size: 12px; margin-bottom: 4px;">Ref: ${escapeHtml(marker.ref)}</div>` : ''}
            ${marker.description ? `<div style="color: #666; font-size: 14px; margin-top: 8px;">${escapeHtml(marker.description)}</div>` : ''}
            <div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #eee; font-size: 12px; color: #999;">
                Lat: ${marker.lat.toFixed(6)}, Lng: ${marker.lng.toFixed(6)}
            </div>
        `;

        const container = document.getElementById('searchResultsMap');
        if (container) {
            container.appendChild(popup);
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

})();
