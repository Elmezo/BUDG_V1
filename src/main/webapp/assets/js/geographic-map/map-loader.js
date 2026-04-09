/**
 * Geographic Map Loader - Main Leaflet map initialization
 * Handles map creation, base layer management, and coordinate display
 */

(function() {
    'use strict';

    let map = null;
    let baseLayers = {};
    let currentBaseLayer = null;
    let coordinateDisplay = null;

    /**
     * Initialize the geographic map
     * @param {string} containerId - ID of the map container element
     * @param {Object} options - Map configuration options
     */
    window.initGeographicMap = function(containerId, options = {}) {
        const container = document.getElementById(containerId);
        if (!container) {
            console.error('[MAP] Container not found:', containerId);
            return null;
        }

        // Default options
        const defaultOptions = {
            center: [30.044420, 31.235712], // Cairo, Egypt default
            zoom: 10,
            minZoom: 2,
            maxZoom: 18
        };

        const config = { ...defaultOptions, ...options };

        // Initialize Leaflet map
        map = L.map(containerId, {
            center: config.center,
            zoom: config.zoom,
            minZoom: config.minZoom,
            maxZoom: config.maxZoom
        });

        // Add default OpenStreetMap tile layer
        const osmLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors',
            maxZoom: 19
        });
        osmLayer.addTo(map);
        baseLayers['OpenStreetMap'] = osmLayer;
        currentBaseLayer = 'OpenStreetMap';

        // Add scale bar
        L.control.scale({
            imperial: false,
            metric: true
        }).addTo(map);

        // Add coordinate display
        addCoordinateDisplay();

        // Add mouse position tracking
        map.on('mousemove', updateCoordinateDisplay);
        map.on('click', updateCoordinateDisplay);

        // Initialize base layer selector
        initBaseLayerSelector();

        console.log('[MAP] Geographic map initialized');
        return map;
    };

    /**
     * Load map configuration from API
     * @param {number} mapId - Map ID to load
     */
    window.loadMapConfig = async function(mapId) {
        try {
            const response = await fetch(`/api/maps/${mapId}`, {
                credentials: 'include'
            });
            if (!response.ok) {
                throw new Error('Failed to load map configuration');
            }
            const mapConfig = await response.json();
            
            // Update map center and zoom
            if (mapConfig.default_center_lat && mapConfig.default_center_lng) {
                map.setView(
                    [mapConfig.default_center_lat, mapConfig.default_center_lng],
                    mapConfig.default_zoom || 10
                );
            }

            // Load layers
            await loadMapLayers(mapId);
            
            return mapConfig;
        } catch (error) {
            console.error('[MAP] Error loading map config:', error);
            throw error;
        }
    };

    /**
     * Load all layers for a map
     * @param {number} mapId - Map ID
     */
    async function loadMapLayers(mapId) {
        try {
            const response = await fetch(`/api/maps/${mapId}/layers`, {
                credentials: 'include'
            });
            if (!response.ok) {
                throw new Error('Failed to load layers');
            }
            const layers = await response.json();
            
            // Load each layer
            for (const layerData of layers) {
                await loadLayer(layerData);
            }
        } catch (error) {
            console.error('[MAP] Error loading layers:', error);
        }
    }

    /**
     * Load a single layer
     * @param {Object} layerData - Layer configuration
     */
    async function loadLayer(layerData) {
        if (layerData.type === 'tile') {
            // Load tile layer
            const tileLayer = L.tileLayer(layerData.url_template, {
                attribution: layerData.style_json ? JSON.parse(layerData.style_json).attribution : '',
                maxZoom: 19
            });
            
            if (layerData.visible) {
                tileLayer.addTo(map);
            }
            
            baseLayers[layerData.name] = tileLayer;
        } else if (layerData.type === 'marker') {
            // Load markers for this layer
            if (typeof window.loadMarkersForLayer === 'function') {
                await window.loadMarkersForLayer(layerData.id);
            }
        } else if (['polygon', 'polyline', 'circle', 'rectangle'].includes(layerData.type)) {
            // Load shapes for this layer
            if (typeof window.loadShapesForLayer === 'function') {
                await window.loadShapesForLayer(layerData.id);
            }
        }
    }

    /**
     * Initialize base layer selector dropdown
     */
    function initBaseLayerSelector() {
        const selector = document.getElementById('baseLayerSelect');
        if (!selector) return;

        // Populate selector with available base layers
        Object.keys(baseLayers).forEach(layerName => {
            const option = document.createElement('option');
            option.value = layerName;
            option.textContent = layerName;
            if (layerName === currentBaseLayer) {
                option.selected = true;
            }
            selector.appendChild(option);
        });

        // Handle layer change
        selector.addEventListener('change', function(e) {
            switchBaseLayer(e.target.value);
        });
    }

    /**
     * Switch base map layer
     * @param {string} layerName - Name of the layer to switch to
     */
    function switchBaseLayer(layerName) {
        if (!baseLayers[layerName]) {
            console.warn('[MAP] Layer not found:', layerName);
            return;
        }

        // Remove current base layer
        if (currentBaseLayer && baseLayers[currentBaseLayer]) {
            map.removeLayer(baseLayers[currentBaseLayer]);
        }

        // Add new base layer
        baseLayers[layerName].addTo(map);
        currentBaseLayer = layerName;
    }

    /**
     * Add coordinate display to map
     */
    function addCoordinateDisplay() {
        const coordContainer = document.getElementById('mapCoordinates');
        if (!coordContainer) return;

        coordinateDisplay = document.createElement('div');
        coordinateDisplay.className = 'map-coordinate-display';
        coordinateDisplay.textContent = 'Lat: 0.000, Lng: 0.000';
        coordContainer.appendChild(coordinateDisplay);
    }

    /**
     * Update coordinate display
     * @param {Object} e - Leaflet event (mouse move or click)
     */
    function updateCoordinateDisplay(e) {
        if (!coordinateDisplay || !e.latlng) return;
        
        const lat = e.latlng.lat.toFixed(6);
        const lng = e.latlng.lng.toFixed(6);
        coordinateDisplay.textContent = `Lat: ${lat}, Lng: ${lng}`;
    }

    /**
     * Get current map instance
     */
    window.getMapInstance = function() {
        return map;
    };

    /**
     * Add base layer programmatically
     * @param {string} name - Layer name
     * @param {string} urlTemplate - Tile URL template
     * @param {Object} options - Layer options
     */
    window.addBaseLayer = function(name, urlTemplate, options = {}) {
        const tileLayer = L.tileLayer(urlTemplate, {
            attribution: options.attribution || '',
            maxZoom: options.maxZoom || 19,
            ...options
        });
        
        baseLayers[name] = tileLayer;
        
        // Update selector if it exists
        const selector = document.getElementById('baseLayerSelect');
        if (selector) {
            const option = document.createElement('option');
            option.value = name;
            option.textContent = name;
            selector.appendChild(option);
        }
        
        return tileLayer;
    };

    // Export for use in other modules
    window.GeographicMapLoader = {
        init: window.initGeographicMap,
        loadConfig: window.loadMapConfig,
        getInstance: window.getMapInstance,
        addBaseLayer: window.addBaseLayer,
        switchBaseLayer: switchBaseLayer
    };

})();

