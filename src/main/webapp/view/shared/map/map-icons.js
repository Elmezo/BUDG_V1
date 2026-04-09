/**
 * Shared Map Icons
 * SVG icon generators for map visualization across facets
 * 
 * Usage: Include this file before any map components
 */

(function() {
    'use strict';

    // ============================================
    // SVG DATA URL HELPER
    // ============================================
    function createSvgDataUrl(svgString) {
        return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgString)}`;
    }

    // ============================================
    // SYSTEM ICONS
    // ============================================
    
    // Database icon for system nodes (stacked bars like Axon)
    function createDatabaseIcon(isCurrent) {
        const iconColor = '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <rect x="6" y="4" width="28" height="6" rx="2" fill="${iconColor}" opacity="0.95"/>
            <rect x="6" y="14" width="28" height="6" rx="2" fill="${iconColor}" opacity="0.95"/>
            <rect x="6" y="24" width="28" height="6" rx="2" fill="${iconColor}" opacity="0.95"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // Alternative database icon with 4 bars
    function createDatabaseIconAlt(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <rect x="6" y="4" width="28" height="5" rx="1.5" fill="${iconColor}" opacity="0.9"/>
            <rect x="6" y="13" width="28" height="5" rx="1.5" fill="${iconColor}" opacity="0.9"/>
            <rect x="6" y="22" width="28" height="5" rx="1.5" fill="${iconColor}" opacity="0.9"/>
            <rect x="6" y="31" width="28" height="5" rx="1.5" fill="${iconColor}" opacity="0.9"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // System icon alias
    function createSystemIcon(isCurrent) {
        return createDatabaseIcon(isCurrent);
    }

    // ============================================
    // DATASET ICONS
    // ============================================
    
    // Dataset icon (layered sheets)
    function createDatasetIcon(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <rect x="8" y="8" width="24" height="4" rx="1" fill="${iconColor}" opacity="0.7"/>
            <rect x="6" y="14" width="28" height="4" rx="1" fill="${iconColor}" opacity="0.8"/>
            <rect x="4" y="20" width="32" height="4" rx="1" fill="${iconColor}" opacity="0.9"/>
            <rect x="6" y="26" width="28" height="4" rx="1" fill="${iconColor}" opacity="1"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // ============================================
    // ATTRIBUTE ICONS
    // ============================================
    
    // Attribute icon (tag)
    function createAttributeIcon(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <path d="M8 12 L20 8 L32 12 L32 28 L20 32 L8 28 Z" fill="${iconColor}" opacity="0.9"/>
            <circle cx="20" cy="18" r="3" fill="currentColor" opacity="0.5"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // ============================================
    // STATUS ICONS
    // ============================================
    
    // Lock icon for inaccessible items
    function createLockIcon(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <rect x="10" y="18" width="20" height="16" rx="2" fill="${iconColor}" opacity="0.9"/>
            <path d="M14 18 V14 A6 6 0 0 1 26 14 V18" stroke="${iconColor}" stroke-width="3" fill="none" opacity="0.9"/>
            <circle cx="20" cy="26" r="2" fill="currentColor" opacity="0.5"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // ============================================
    // RELATIONSHIP/CONNECTION ICONS
    // ============================================
    
    // Interface/Connection icon
    function createInterfaceIcon(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="20" r="6" fill="${iconColor}" opacity="0.9"/>
            <circle cx="28" cy="20" r="6" fill="${iconColor}" opacity="0.9"/>
            <line x1="18" y1="20" x2="22" y2="20" stroke="${iconColor}" stroke-width="2"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // ============================================
    // BUSINESS ICONS
    // ============================================
    
    // Process icon (gears)
    function createProcessIcon(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="16" cy="16" r="8" fill="${iconColor}" opacity="0.9"/>
            <circle cx="16" cy="16" r="4" fill="currentColor" opacity="0.3"/>
            <circle cx="26" cy="26" r="6" fill="${iconColor}" opacity="0.9"/>
            <circle cx="26" cy="26" r="3" fill="currentColor" opacity="0.3"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // Glossary icon (book)
    function createGlossaryIcon(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <path d="M8 8 L8 32 L32 32 L32 8 Z" fill="${iconColor}" opacity="0.9"/>
            <line x1="12" y1="14" x2="28" y2="14" stroke="currentColor" stroke-width="2" opacity="0.3"/>
            <line x1="12" y1="20" x2="24" y2="20" stroke="currentColor" stroke-width="2" opacity="0.3"/>
            <line x1="12" y1="26" x2="20" y2="26" stroke="currentColor" stroke-width="2" opacity="0.3"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // Person/Stakeholder icon
    function createPersonIcon(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="14" r="6" fill="${iconColor}" opacity="0.9"/>
            <ellipse cx="20" cy="32" rx="10" ry="6" fill="${iconColor}" opacity="0.9"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // Product icon (box)
    function createProductIcon(color) {
        const iconColor = color || '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <path d="M20 4 L36 12 L36 28 L20 36 L4 28 L4 12 Z" fill="${iconColor}" opacity="0.9"/>
            <path d="M20 4 L20 36" stroke="currentColor" stroke-width="1" opacity="0.3"/>
            <path d="M4 12 L36 12" stroke="currentColor" stroke-width="1" opacity="0.3"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    // ============================================
    // ICON GETTER BY TYPE
    // ============================================
    function getIconByType(type, isCurrent) {
        const iconFunctions = {
            'system': createDatabaseIcon,
            'database': createDatabaseIcon,
            'dataset': createDatasetIcon,
            'attribute': createAttributeIcon,
            'interface': createInterfaceIcon,
            'process': createProcessIcon,
            'glossary': createGlossaryIcon,
            'person': createPersonIcon,
            'stakeholder': createPersonIcon,
            'product': createProductIcon,
            'locked': createLockIcon
        };

        const iconFn = iconFunctions[type] || iconFunctions['system'];
        return iconFn(isCurrent);
    }

    // ============================================
    // ARROW MARKERS FOR EDGES
    // ============================================
    const ARROW_MARKERS = {
        upstream: {
            id: 'arrow-upstream',
            color: '#ef4444',
            path: 'M 0 0 L 10 5 L 0 10 z'
        },
        downstream: {
            id: 'arrow-downstream',
            color: '#22c55e',
            path: 'M 0 0 L 10 5 L 0 10 z'
        },
        default: {
            id: 'arrow-default',
            color: '#94a3b8',
            path: 'M 0 0 L 10 5 L 0 10 z'
        }
    };

    // ============================================
    // EXPORT TO GLOBAL SCOPE
    // ============================================
    window.SharedMapIcons = {
        createSvgDataUrl,
        createDatabaseIcon,
        createDatabaseIconAlt,
        createSystemIcon,
        createDatasetIcon,
        createAttributeIcon,
        createLockIcon,
        createInterfaceIcon,
        createProcessIcon,
        createGlossaryIcon,
        createPersonIcon,
        createProductIcon,
        getIconByType,
        arrowMarkers: ARROW_MARKERS
    };

    // Also export as InterfaceMapIcons for backward compatibility
    window.InterfaceMapIcons = window.SharedMapIcons;

})();

