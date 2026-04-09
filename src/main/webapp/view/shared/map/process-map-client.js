/**
 * Process map client bundle: Cytoscape visuals (icons + styles) and overlay fetch/format.
 * Loaded only by process.html for Context / Components flow maps.
 */
(function() {
    'use strict';

    function createSvgDataUrl(svgString) {
        return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgString)}`;
    }

    function createProcessIcon() {
        const boxAndPlusColor = '#9333ea';
        const fill = '#ffffff';
        const svg = `<svg width="20" height="20" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="1" width="16" height="12" rx="2" fill="${fill}" stroke="${boxAndPlusColor}" stroke-width="1.8"/>
           <line x1="10" y1="3" x2="10" y2="11" stroke="${boxAndPlusColor}" stroke-width="2" stroke-linecap="round"/>
           <line x1="6" y1="7" x2="14" y2="7" stroke="${boxAndPlusColor}" stroke-width="2" stroke-linecap="round"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    function createStartStepIcon() {
        const green = '#16a34a';
        const svg = `<svg width="20" height="20" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
            <circle cx="10" cy="8" r="7" fill="none" stroke="${green}" stroke-width="2.5"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    function createEndStepIcon() {
        const blue = '#2563eb';
        const svg = `<svg width="20" height="20" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
            <circle cx="10" cy="8" r="7" fill="none" stroke="${blue}" stroke-width="2.5"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    function createControlStepIcon() {
        const orange = '#ea580c';
        const svg = `<svg width="20" height="20" viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
        <path d="M10 1 L18 7 L10 12 L2 7 Z" fill="none" stroke="${orange}" stroke-width="2"/>
        </svg>`;
        return createSvgDataUrl(svg);
    }

    function createCommonStepIcon() {
        const svg = '<svg width="1" height="1" xmlns="http://www.w3.org/2000/svg"><rect width="1" height="1" fill="none"/></svg>';
        return createSvgDataUrl(svg);
    }

    function getProcessStepIcon(stepType, primaryName) {
        const t = (stepType || '').toString().toLowerCase();

        if (t === 'step') {
            const p = (primaryName || '').toString().toLowerCase();
            if (p === 'starting') return createStartStepIcon();
            if (p === 'ending') return createEndStepIcon();
            return createCommonStepIcon();
        }

        if (t === 'control' || t === 'decision') return createControlStepIcon();

        if (t === 'start' || t === 'starting') return createStartStepIcon();
        if (t === 'end' || t === 'ending') return createEndStepIcon();

        return createProcessIcon();
    }

    window.ProcessMapIcons = {
        createSvgDataUrl,
        createProcessIcon,
        createStartStepIcon,
        createEndStepIcon,
        createControlStepIcon,
        createCommonStepIcon,
        getProcessStepIcon
    };
})();

(function() {
    'use strict';

    const PROCESS_MAP_COLORS = {
        processBorder: '#9333ea',
        commonStepBg: '#eeeeee',
        commonStepBorder: '#c0c0c0',
        startBorder: '#16a34a',
        endBorder: '#2563eb',
        controlBorder: '#ea580c',
        currentProcessBorder: '#7e22ce',
        edgeColor: '#64748b'
    };

    function getProcessMapCytoscapeStyles() {
        return [
            {
                selector: 'node',
                style: {
                    'label': 'data(label)',
                    'text-valign': 'center',
                    'text-halign': 'center',
                    'text-wrap': 'wrap',
                    'text-max-width': 90,
                    'font-size': 8,
                    'font-weight': '600',
                    'font-family': 'Inter, system-ui, sans-serif',
                    'color': '#1f2937',
                    'shape': 'round-rectangle',
                    'width': 80,
                    'height': 56,
                    'background-image': 'data(backgroundImage)',
                    'background-fit': 'none',
                    'background-width': 16,
                    'background-height': 20,
                    'background-position-x': '50%',
                    'background-position-y': 10,
                    'background-color': '#ffffff',
                    'border-width': 2,
                    'text-margin-y': 10
                }
            },
            {
                selector: 'node[stepType = "process"]',
                style: {
                    'border-color': PROCESS_MAP_COLORS.processBorder,
                    'border-width': 2,
                    'shape': 'round-rectangle'
                }
            },
            {
                selector: 'node[stepType = "step"][primaryName = "common"]',
                style: {
                    'background-color': PROCESS_MAP_COLORS.commonStepBg,
                    'border-color': PROCESS_MAP_COLORS.commonStepBorder,
                    'border-width': 2,
                    'shape': 'round-rectangle',
                    'background-image': 'none',
                    'background-opacity': 1
                }
            },
            {
                selector: 'node[stepType = "step"][primaryName = "starting"]',
                style: {
                    'background-color': '#ffffff',
                    'border-color': PROCESS_MAP_COLORS.startBorder,
                    'border-width': 2,
                    'shape': 'round-rectangle'
                }
            },
            {
                selector: 'node[stepType = "step"][primaryName = "ending"]',
                style: {
                    'background-color': '#ffffff',
                    'border-color': PROCESS_MAP_COLORS.endBorder,
                    'border-width': 2,
                    'shape': 'round-rectangle'
                }
            },
            {
                selector: 'node[stepType = "control"]',
                style: {
                    'background-color': '#ffffff',
                    'border-color': PROCESS_MAP_COLORS.controlBorder,
                    'border-width': 2,
                    'shape': 'round-rectangle'
                }
            },
            {
                selector: 'node[stepType = "start"]',
                style: {
                    'background-color': '#ffffff',
                    'border-color': PROCESS_MAP_COLORS.startBorder,
                    'border-width': 2,
                    'shape': 'round-rectangle'
                }
            },
            {
                selector: 'node[stepType = "end"]',
                style: {
                    'background-color': '#ffffff',
                    'border-color': PROCESS_MAP_COLORS.endBorder,
                    'border-width': 2,
                    'shape': 'round-rectangle'
                }
            },
            {
                selector: 'node[?isCurrent]',
                style: {
                    'border-width': 3
                }
            },
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': PROCESS_MAP_COLORS.edgeColor,
                    'target-arrow-color': PROCESS_MAP_COLORS.edgeColor,
                    'target-arrow-shape': 'triangle',
                    'arrow-scale': 1.2,
                    'curve-style': 'bezier',
                    'label': 'data(label)',
                    'font-size': 10,
                    'color': '#6b7280',
                    'text-background-color': '#ffffff',
                    'text-background-opacity': 0.8
                }
            },
            {
                selector: 'edge.reversed-edge',
                style: {
                    'target-arrow-shape': 'none',
                    'source-arrow-shape': 'triangle',
                    'source-arrow-color': PROCESS_MAP_COLORS.edgeColor
                }
            },
            {
                selector: 'edge.reversed-edge[lineColor]',
                style: {
                    'source-arrow-color': 'data(lineColor)'
                }
            }
        ];
    }

    function getProcessMapLayoutConfig(layoutName) {
        const layouts = {
            'left-to-right': {
                name: 'dagre',
                rankDir: 'LR',
                padding: 50,
                rankSep: 80,
                nodeSep: 40,
                animate: true,
                animationDuration: 400,
                nodeDimensionsIncludeLabels: true
            },
            'top-to-bottom': {
                name: 'dagre',
                rankDir: 'TB',
                padding: 50,
                rankSep: 60,
                nodeSep: 40,
                animate: true,
                animationDuration: 400,
                nodeDimensionsIncludeLabels: true
            },
            'organic': { name: 'cose', animate: true, animationDuration: 400, nodeRepulsion: 4000, idealEdgeLength: 120, edgeElasticity: 0.45, nodeDimensionsIncludeLabels: true }
        };
        const key = layoutName === 'force' ? 'organic' : layoutName;
        return layouts[key] || layouts['left-to-right'];
    }

    window.ProcessMapStyles = {
        colors: PROCESS_MAP_COLORS,
        getCytoscapeStyles: getProcessMapCytoscapeStyles,
        getLayoutConfig: getProcessMapLayoutConfig
    };
})();

(function() {
    'use strict';

    function escapeHtml(str) {
        if (str == null) return '';
        const s = String(str);
        const div = document.createElement('div');
        div.textContent = s;
        return div.innerHTML;
    }

    function getOverlayRequest(processId, overlayType) {
        if (!processId || !overlayType || overlayType === 'none') return null;
        const id = String(processId).replace(/[^0-9]/g, '') || processId;
        switch (overlayType) {
            case 'description':
                return { url: `/api/process/${id}`, type: 'process' };
            case 'glossary':
                return { url: `/api/process-impact/${id}/glossaries`, type: 'array' };
            case 'systems':
                return { url: `/api/process-impact/${id}/systems`, type: 'array' };
            case 'policies':
                return { url: `/api/process-impact/${id}/policies`, type: 'array' };
            case 'legal-entities':
                return { url: `/api/process-impact/${id}/legals`, type: 'array' };
            case 'stakeholders':
                return { url: `/api/process-stakeholder/${id}/stakeholders/view`, type: 'stakeholders' };
            case 'business-area':
                return { url: `/api/process/${id}`, type: 'process' };
            case 'geography':
                return { url: `/api/process/${id}`, type: 'process' };
            case 'data-quality':
                return null;
            default:
                return null;
        }
    }

    function fetchOverlayData(processId, overlayType) {
        const req = getOverlayRequest(processId, overlayType);
        if (!req) return Promise.resolve({ overlayType, data: null });

        return fetch(req.url, { credentials: 'include' })
            .then(r => {
                if (!r.ok) return null;
                return r.json();
            })
            .then(json => ({ overlayType, data: json }))
            .catch(() => ({ overlayType, data: null }));
    }

    function formatOverlayHtml(overlayType, data) {
        if (data == null) {
            if (overlayType === 'data-quality') return '<p class="map-overlay-empty">Data quality overlay is not available.</p>';
            return '<p class="map-overlay-empty">No data available.</p>';
        }

        switch (overlayType) {
            case 'description': {
                const processObj = data && (data.data || data.result || data.process) ? (data.data || data.result || data.process) : data;
                const desc = processObj && (
                    processObj.description ||
                    processObj.Description ||
                    processObj.processDescription ||
                    processObj.ProcessDescription ||
                    processObj.summary ||
                    processObj.Summary ||
                    ''
                );
                if (!processObj || !String(desc).trim()) return '<p class="map-overlay-empty">No description.</p>';
                return escapeHtml(String(desc).trim());
            }
            case 'glossary': {
                const list = Array.isArray(data) ? data : (data.data || data.items || []);
                if (!list.length) return '<p class="map-overlay-empty">No glossary terms linked.</p>';
                const rows = list.slice(0, 20).map(r => {
                    const name = r.glossaryName || r.glossaryname || r.name || r.Name || '-';
                    const ref = r.glossaryRefNumber || r.glossaryrefnumber || r.refNumber || r.Ref_Number || '';
                    const typeName = r.glossaryType || r.glossarytype || r.relationTypeName || '';
                    return '<div class="map-node-overlay-item">' + escapeHtml(name) + (ref ? ' <span class="map-overlay-ref">' + escapeHtml(ref) + '</span>' : '') + (typeName ? ' <span class="map-overlay-meta">' + escapeHtml(typeName) + '</span>' : '') + '</div>';
                });
                const more = list.length > 20 ? '<div class="map-overlay-more">+' + (list.length - 20) + ' more</div>' : '';
                return rows.join('') + more;
            }
            case 'systems': {
                const list = Array.isArray(data) ? data : (data.data || data.items || []);
                if (!list.length) return '<p class="map-overlay-empty">No systems linked.</p>';
                const rows = list.slice(0, 20).map(r => {
                    const name = r.systemName || r.systemname || r.name || r.Name || '-';
                    const ref = r.systemRef || r.systemref || r.refNumber || '';
                    return '<div class="map-node-overlay-item">' + escapeHtml(name) + (ref ? ' <span class="map-overlay-ref">' + escapeHtml(ref) + '</span>' : '') + '</div>';
                });
                const more = list.length > 20 ? '<div class="map-overlay-more">+' + (list.length - 20) + ' more</div>' : '';
                return  rows.join('');
            }
            case 'policies': {
                const list = Array.isArray(data) ? data : (data.data || data.items || []);
                if (!list.length) return '<p class="map-overlay-empty">No policies linked.</p>';
                const rows = list.slice(0, 20).map(r => {
                    const name = r.policyName || r.policyname || r.name || r.Name || '-';
                    return '<div class="map-node-overlay-item">' + escapeHtml(name) + '</div>';
                });
                const more = list.length > 20 ? '<div class="map-overlay-more">+' + (list.length - 20) + ' more</div>' : '';
                return rows.join('');
            }
            case 'legal-entities': {
                const list = Array.isArray(data) ? data : (data.data || data.items || []);
                if (!list.length) return '<p class="map-overlay-empty">No legal entities linked.</p>';
                const rows = list.slice(0, 20).map(r => {
                    const name = r.legalName || r.legalname || r.name || r.Name || '-';
                    return '<div class="map-node-overlay-item">' + escapeHtml(name) + '</div>';
                });
                const more = list.length > 20 ? '<div class="map-overlay-more">+' + (list.length - 20) + ' more</div>' : '';
                return rows.join('');
            }
            case 'stakeholders': {
                let list = Array.isArray(data) ? data : (data && data.data && Array.isArray(data.data) ? data.data : (data.stakeholders || data.result || []));
                if (!Array.isArray(list)) list = [];
                if (!list.length) return '<p class="map-overlay-empty">No stakeholders.</p>';
                const rows = list.slice(0, 20).map(r => {
                    const name = r.PersonName || r.personName || r.personname || r.Name || r.name || r.person_name || '';
                    const role = r.RoleName || r.roleName || r.rolename || r.role || r.Role || r.role_name || '';
                    const nameStr = String(name || '').trim() || '—';
                    const roleStr = String(role || '').trim() ? escapeHtml(String(role).trim()) : '—';
                    return '<div class="map-node-overlay-item"><span class="map-overlay-stakeholder-name">' + escapeHtml(nameStr) + '</span> <span class="map-overlay-meta">' + roleStr + '</span></div>';
                });
                const more = list.length > 20 ? '<div class="map-overlay-more">+' + (list.length - 20) + ' more</div>' : '';
                return  rows.join('');
            }
            case 'business-area': {
                const processObj = data && (data.data || data.result || data.process) ? (data.data || data.result || data.process) : data;
                const name = processObj && (
                    processObj.businessAreaName || processObj.businessareaname || processObj.businessArea || ''
                );
                if (!processObj || !String(name).trim()) return '<p class="map-overlay-empty">No business area.</p>';
                return escapeHtml(String(name).trim()) ;
            }
            case 'geography': {
                const processObj = data && (data.data || data.result || data.process) ? (data.data || data.result || data.process) : data;
                const geo = processObj && (
                    processObj.geography || processObj.Geography || processObj.region || processObj.Region || ''
                );
                if (!processObj || !String(geo).trim()) return '<p class="map-overlay-empty">No geography.</p>';
                return  escapeHtml(String(geo).trim()) ;
            }
            case 'data-quality':
                return '<p class="map-overlay-empty">Data quality overlay is not available.</p>';
            default:
                return '<p class="map-overlay-empty">No data.</p>';
        }
    }

    function fetchAndFormatOverlay(processId, overlayType) {
        if (!overlayType || overlayType === 'none') return Promise.resolve('');
        return fetchOverlayData(processId, overlayType).then(({ overlayType: t, data }) => formatOverlayHtml(t, data));
    }

    window.ProcessMapOverlay = {
        fetchOverlayData,
        formatOverlayHtml,
        fetchAndFormatOverlay,
        getOverlayRequest,
        escapeHtml
    };
})();
