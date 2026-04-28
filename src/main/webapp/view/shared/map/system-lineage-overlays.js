/**
 * system-lineage-overlays.js - overlay fetch/render/highlight for system + dataset lineage maps.
 */
(function () {
    'use strict';

    window.createSystemLineageOverlayController = function (ctx) {
        var state = ctx.state;
        var showLoading = ctx.showLoading;
        var hideLoading = ctx.hideLoading;
        var checkSystemHasDataAttributes = ctx.checkSystemHasDataAttributes;
        var extractGlossaryNameFromAttr = ctx.extractGlossaryNameFromAttr;
        var logPrefix = ctx.logPrefix || '[SYSTEM-LINEAGE-OVERLAY]';

        /** Set window.BUDG_DEBUG_MAP_OVERLAYS = true for verbose overlay / highlight logs */
        function ovLog() {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_MAP_OVERLAYS === true) {
                console.log.apply(console, arguments);
            }
        }
        function ovWarn() {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_MAP_OVERLAYS === true) {
                console.warn.apply(console, arguments);
            }
        }

        function getGlossaryScopeHelper() {
            // Bumped contract: createSystemScope now expects collectScopeForDataset (not the old
            // getRelatedDatasetsForDataset/getAttributeRelationshipsBetweenSystems pair). If a
            // pre-existing helper from an older module load is on window, replace it.
            if (window.GlossaryOverlayScope &&
                typeof window.GlossaryOverlayScope.createSystemScope === 'function' &&
                window.GlossaryOverlayScope.__contractVersion === 2) {
                return window.GlossaryOverlayScope;
            }

            function normalizeId(value) {
                if (value === null || value === undefined) return '';
                var out = String(value).trim();
                return out;
            }

            window.GlossaryOverlayScope = {
                __contractVersion: 2,
                createSystemScope: async function (params) {
                    params = params || {};
                    var currentSystemId = normalizeId(params.currentSystemId);
                    var visibleSystemIds = Array.from(new Set((params.visibleSystemIds || []).map(normalizeId).filter(Boolean)));
                    var getDatasetsForSystem = params.getDatasetsForSystem;
                    // collectScopeForDataset(datasetId, relCache) ->
                    //   { datasetIds: Set<string>, attrLinks: Array<{datasetId, attributeId, systemId}> }
                    var collectScopeForDataset = params.collectScopeForDataset;
                    if (typeof getDatasetsForSystem !== 'function' || typeof collectScopeForDataset !== 'function') {
                        return {
                            currentSystemId: currentSystemId,
                            currentSystemIds: new Set(currentSystemId ? [currentSystemId] : []),
                            currentDatasetIds: new Set(),
                            relatedDatasetIds: new Set(),
                            datasetsBySystem: new Map(),
                            linkedAttributeIdsBySystem: new Map()
                        };
                    }

                    var datasetsBySystem = new Map();
                    for (const sid of visibleSystemIds) {
                        var dsIds = await getDatasetsForSystem(sid);
                        var normIds = Array.from(new Set((dsIds || []).map(normalizeId).filter(Boolean)));
                        datasetsBySystem.set(sid, normIds);
                    }

                    // Reverse map: dataset id -> system id, used as fallback when a relationship
                    // row doesn't carry the OTHER system id explicitly.
                    var datasetToSystem = new Map();
                    datasetsBySystem.forEach(function (dsList, sid) {
                        (dsList || []).forEach(function (did) {
                            var dn = normalizeId(did);
                            if (dn && !datasetToSystem.has(dn)) datasetToSystem.set(dn, sid);
                        });
                    });

                    var currentDatasetIds = new Set(datasetsBySystem.get(currentSystemId) || []);
                    var relCache = new Map();

                    // Pull both related-dataset IDs AND linked-attribute info from a single
                    // /api/dataset-relationships fetch per current dataset. This endpoint has
                    // no Relation_Method IS NULL filter, so it covers interface-mediated
                    // relationships (which is what System Lineage maps actually use).
                    var currentDatasetIdsArr = Array.from(currentDatasetIds);
                    var perDatasetResults = await Promise.all(currentDatasetIdsArr.map(async function (did) {
                        var dn = normalizeId(did);
                        if (!dn) return { srcId: '', res: null };
                        try {
                            return { srcId: dn, res: await collectScopeForDataset(dn, relCache) };
                        } catch (e) {
                            return { srcId: dn, res: null };
                        }
                    }));

                    // Bidirectional dataset-relationship graph: for any dataset id, the set of
                    // dataset ids it is directly related to. Used for cross-panel "related"
                    // highlighting when a user clicks a dataset row in any panel.
                    var datasetRelationshipsByDataset = new Map();
                    function addBidirectionalRel(a, b) {
                        if (!a || !b || a === b) return;
                        if (!datasetRelationshipsByDataset.has(a)) datasetRelationshipsByDataset.set(a, new Set());
                        if (!datasetRelationshipsByDataset.has(b)) datasetRelationshipsByDataset.set(b, new Set());
                        datasetRelationshipsByDataset.get(a).add(b);
                        datasetRelationshipsByDataset.get(b).add(a);
                    }

                    var relatedDatasetIds = new Set();
                    var allAttrLinks = [];
                    perDatasetResults.forEach(function (pair) {
                        if (!pair || !pair.res) return;
                        var srcId = pair.srcId;
                        var res = pair.res;
                        if (res.datasetIds && typeof res.datasetIds.forEach === 'function') {
                            res.datasetIds.forEach(function (id) {
                                var nid = normalizeId(id);
                                if (nid) {
                                    relatedDatasetIds.add(nid);
                                    addBidirectionalRel(srcId, nid);
                                }
                            });
                        }
                        if (Array.isArray(res.attrLinks)) {
                            res.attrLinks.forEach(function (link) { allAttrLinks.push(link); });
                        }
                    });

                    // Build attribute scope: for each linked attribute, register its ID under
                    // the OTHER side's system. Fall back to looking up the system via the
                    // dataset->system reverse map when the rel row didn't carry systemId.
                    var linkedAttributeIdsBySystem = new Map();
                    function addLinked(sid, aid) {
                        var sidStr = normalizeId(sid);
                        var aidStr = normalizeId(aid);
                        if (!sidStr || !aidStr || sidStr === currentSystemId) return;
                        if (!linkedAttributeIdsBySystem.has(sidStr)) {
                            linkedAttributeIdsBySystem.set(sidStr, new Set());
                        }
                        linkedAttributeIdsBySystem.get(sidStr).add(aidStr);
                    }
                    allAttrLinks.forEach(function (link) {
                        if (!link) return;
                        var sid = normalizeId(link.systemId);
                        var did = normalizeId(link.datasetId);
                        var aid = normalizeId(link.attributeId);
                        if (!sid && did) sid = datasetToSystem.get(did) || '';
                        if (sid && aid) addLinked(sid, aid);
                    });

                    return {
                        currentSystemId: currentSystemId,
                        currentSystemIds: new Set(currentSystemId ? [currentSystemId] : []),
                        currentDatasetIds: currentDatasetIds,
                        relatedDatasetIds: relatedDatasetIds,
                        datasetsBySystem: datasetsBySystem,
                        linkedAttributeIdsBySystem: linkedAttributeIdsBySystem,
                        datasetRelationshipsByDataset: datasetRelationshipsByDataset
                    };
                },
                isDatasetInScope: function (scope, datasetId, datasetSystemId) {
                    if (!scope) return false;
                    var did = normalizeId(datasetId);
                    var sid = normalizeId(datasetSystemId);
                    if (!did || !sid) return false;
                    if (scope.currentSystemIds && scope.currentSystemIds.has(sid)) {
                        return scope.currentDatasetIds && scope.currentDatasetIds.has(did);
                    }
                    return scope.relatedDatasetIds && scope.relatedDatasetIds.has(did);
                },
                isAttributeInScope: function (scope, attributeId, attributeSystemId) {
                    if (!scope) return false;
                    var aid = normalizeId(attributeId);
                    var sid = normalizeId(attributeSystemId);
                    if (!aid || !sid) return false;
                    // Current system: include all attributes
                    if (scope.currentSystemIds && scope.currentSystemIds.has(sid)) return true;
                    // Other systems: only attributes linked via attribute relationships to current
                    var linked = scope.linkedAttributeIdsBySystem && scope.linkedAttributeIdsBySystem.get(sid);
                    return !!(linked && linked.has(aid));
                }
            };

            return window.GlossaryOverlayScope;
        }

        function setOverlay(overlay) {
        ovLog(logPrefix + ' setOverlay called with:', overlay, 'mapType:', state.mapType);
        state.overlay = overlay;
        
        // Clear existing overlays
        clearOverlayPanels();
        
        if (overlay === 'none' || !overlay) {
            ovLog(logPrefix + ' Overlay cleared');
            return;
        }
        
        // Load and display overlay data
        ovLog(logPrefix + ' Loading overlay data for:', overlay);
        loadOverlayData(overlay);
    }

    /** Set overlay columns (field ids) for an overlay type. Redraws overlay if that type is active. */
    function setOverlayColumns(overlayType, columnIds) {
        if (!overlayType) return;
        state.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
        if (state.overlay === overlayType) {
            loadOverlayData(overlayType);
        }
    }

    /** Get selected overlay column ids for an overlay type. */
    function getOverlayColumns(overlayType) {
        var raw = state.overlayColumnsByType[overlayType];
        if (window.OverlayColumns && typeof window.OverlayColumns.resolveSelectedColumnIds === 'function') {
            return window.OverlayColumns.resolveSelectedColumnIds(raw, overlayType);
        }
        return (Array.isArray(raw) && raw.length > 0) ? raw.slice() : (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
    }

    // Load overlay data for all visible nodes
    // options.quiet / options.silent: skip showLoading/hideLoading (e.g. after graph re-render)
    async function loadOverlayData(overlayType, options) {
        options = options || {};
        var quiet = options.quiet === true || options.silent === true;
        ovLog(logPrefix + ' loadOverlayData called:', overlayType);
        
        if (!state.network) {
            console.error(logPrefix + ' Network not initialized!');
            return;
        }

        // Force a fresh glossary scope on every overlay load so any cache built
        // by an older module version (different scope contract) is discarded.
        // The datasets overlay shares the same scope cache because it also needs
        // current-vs-related dataset filtering for black systems.
        if (overlayType === 'glossary' || overlayType === 'datasets') {
            state._glossaryScopeCache = null;
        }

        // Reset the cross-panel highlight cache for every overlay load. It is
        // re-populated below as scope, attribute relationships and glossary
        // anchors come in. See ensureOverlayHighlightCache + populateGlossaryAnchor.
        state._overlayHighlightCache = {
            datasetGraph: null,
            attributeRels: [],
            attributeOwnerMap: new Map(),
            glossaryToAnchors: new Map(),
            anchorToGlossaries: new Map(),
            anchorGraph: null
        };

        if (!quiet) showLoading();

        try {
            const nodes = state.network.nodes();
            ovLog(logPrefix + ' Number of nodes:', nodes.length);

            if (!nodes || nodes.length === 0) {
                ovWarn(logPrefix + ' No map nodes — cannot show overlays.');
                if (!quiet) hideLoading();
                if (typeof window.showNotification === 'function') {
                    window.showNotification('No nodes on the map to attach overlays to. Try system lineage or add relationships.', 'info');
                }
                return;
            }
            
            const overlayData = new Map();
            const currentSystemId = String(state.systemId);
            const isDatasetLineage = state.mapType === 'dataset-lineage';
            
            ovLog(logPrefix + ' isDatasetLineage:', isDatasetLineage);

            // For dataset lineage, get dataset IDs instead of system IDs
            if (isDatasetLineage) {
                const datasetIds = nodes.map(n => {
                    const meta = n.data('meta') || {};
                    return meta.datasetId;
                }).filter(id => id && id !== 'undefined');
                
                ovLog(logPrefix + ' Dataset IDs for overlay:', datasetIds);

                // For attributes, linking-attributes in dataset lineage, special handling
                if (overlayType === 'attributes' || overlayType === 'linking-attributes') {
                    await loadAttributeOverlayDataForDatasets(overlayType, datasetIds, currentSystemId, overlayData);
                } else if (overlayType === 'glossary') {
                    await loadGlossaryOverlayDataForDatasets(datasetIds, currentSystemId, overlayData);
                } else {
                    // Load overlay data for each dataset (dataset-specific overlays)
                    ovLog(logPrefix + ' Loading', overlayType, 'overlay for', datasetIds.length, 'datasets in Dataset Lineage');
                    await Promise.all(datasetIds.map(async (datasetId) => {
                        try {
                            // Check if dataset is locked - skip API calls for locked datasets
                            const datasetInfo = state.linkedDatasets.get(String(datasetId));
                            if (datasetInfo?.isLocked) {
                                ovLog(logPrefix + ' Dataset', datasetId, 'is locked, skipping', overlayType, 'overlay');
                                return;
                            }
                            
                            const data = await fetchOverlayDataForDataset(datasetId, overlayType);
                            ovLog(logPrefix + ' Dataset', datasetId, overlayType, ':', data?.length || 0, 'items');
                            if (data && data.length > 0) {
                                overlayData.set(String(datasetId), data);
                            }
                        } catch (error) {
                            // Silently handle "not found" errors (locked/inaccessible datasets)
                            if (error.message && error.message.includes('not found')) {
                                ovLog(`${logPrefix} Dataset ${datasetId} not found (likely locked), skipping ${overlayType}`);
                            } else {
                                ovWarn(`${logPrefix} Failed to load ${overlayType} for dataset ${datasetId}:`, error);
                            }
                        }
                    }));
                }
            } else {
                // System lineage - get system IDs (string keys for overlayData Map lookup)
                const systemIds = nodes.map(n => String(n.data('id'))).filter(id => id && id !== 'undefined' && id !== 'null');

                // For attributes and linking-attributes, we need special handling
                if (overlayType === 'attributes' || overlayType === 'linking-attributes') {
                    await loadAttributeOverlayData(overlayType, systemIds, currentSystemId, overlayData);
                } else {
                    if (overlayType === 'glossary' || overlayType === 'datasets') {
                        // Build the system-lineage scope (current + related dataset IDs) once
                        // per overlay load and share it across per-system fetches so we do not
                        // re-fetch dataset relationships. The same scope is reused by the
                        // datasets overlay to filter black-system datasets to only those
                        // related to the current (orange) system.
                        try {
                            await ensureSystemLineageGlossaryScope(systemIds);
                        } catch (scopeErr) {
                            ovWarn(logPrefix + ' Failed to prepare glossary scope:', scopeErr);
                            state._glossaryScopeCache = null;
                        }
                    }

                    // Mirror the dataset graph (built by the scope) into the highlight
                    // cache so dataset/glossary BFS can read it without going through
                    // the scope again.
                    if (state._glossaryScopeCache && state._glossaryScopeCache.scope &&
                        state._glossaryScopeCache.scope.datasetRelationshipsByDataset) {
                        state._overlayHighlightCache.datasetGraph =
                            state._glossaryScopeCache.scope.datasetRelationshipsByDataset;
                    }

                    // Glossary overlay needs attribute relationships for its anchor graph
                    // BFS even though it is not the attributes overlay. Fetch them once
                    // here using the same per-pair logic loadAttributeOverlayData uses.
                    if (overlayType === 'glossary') {
                        try {
                            const visibleArr = Array.from(new Set(systemIds.map(id => String(id)).filter(Boolean)));
                            const relPromises = [];
                            for (let i = 0; i < visibleArr.length; i++) {
                                for (let j = i + 1; j < visibleArr.length; j++) {
                                    relPromises.push(fetchAttributeRelationships(visibleArr[i], visibleArr[j]));
                                    relPromises.push(fetchAttributeRelationships(visibleArr[j], visibleArr[i]));
                                }
                            }
                            const rels = (await Promise.all(relPromises)).flat();
                            state.attributeRelationships = rels;
                            state._overlayHighlightCache.attributeRels = rels;
                        } catch (relErr) {
                            ovWarn(logPrefix + ' Failed to load attribute relationships for glossary highlight cache:', relErr);
                        }
                    }

                    // Load overlay data for each system based on type
                    await Promise.all(systemIds.map(async (systemId) => {
                        try {
                            // Check if system has data attributes
                            // If overlayType is attributes, linking-attributes, or datasets,
                            // and system has no data attributes (dataAttributes = 0), skip overlay
                            const hasDataAttributes = checkSystemHasDataAttributes(systemId);
                            
                            if (!hasDataAttributes && 
                                (overlayType === 'attributes' || 
                                 overlayType === 'linking-attributes' || 
                                 overlayType === 'datasets')) {
                                // Skip overlay for systems without data attributes
                                return;
                            }
                            
                            const data = await fetchOverlayDataForSystem(systemId, overlayType);
                            if (data && data.length > 0) {
                                overlayData.set(String(systemId), data);
                            }
                        } catch (error) {
                            ovWarn(`${logPrefix} Failed to load ${overlayType} for system ${systemId}:`, error);
                        }
                    }));
                }
            }

            // Store overlay data in state
            state.overlayData = overlayData;

            // Finalise the cross-panel highlight cache. Dataset graph is already
            // populated from the scope; attributeRels is set by either the
            // attribute overlay loader or the glossary path above; glossary anchor
            // maps are populated by the glossary fetchers (per-system + per-dataset).
            // Only the unified anchor graph is built lazily here so it includes
            // every edge collected during this load.
            try {
                const _hcache = state._overlayHighlightCache;
                if (_hcache && window.MapOverlayHighlight && typeof window.MapOverlayHighlight.buildAnchorGraph === 'function') {
                    if (Array.isArray(state.attributeRelationships) && (!_hcache.attributeRels || _hcache.attributeRels.length === 0)) {
                        _hcache.attributeRels = state.attributeRelationships;
                    }
                    _hcache.anchorGraph = window.MapOverlayHighlight.buildAnchorGraph(
                        _hcache.datasetGraph || new Map(),
                        _hcache.attributeRels || [],
                        _hcache.attributeOwnerMap || new Map()
                    );
                }
            } catch (cacheErr) {
                ovWarn(logPrefix + ' Failed to build overlay highlight cache:', cacheErr);
            }

            try {
                var focusNodeIdsSL = new Set();
                if (state.mapType === 'dataset-lineage') {
                    if (state.datasetId != null) focusNodeIdsSL.add(String(state.datasetId));
                } else if (state.systemId != null) {
                    focusNodeIdsSL.add(String(state.systemId));
                }
                if (window.MapOverlayHighlight && typeof window.MapOverlayHighlight.sortOverlayDataByRelevance === 'function') {
                    window.MapOverlayHighlight.sortOverlayDataByRelevance({
                        overlayType: overlayType,
                        overlayData: overlayData,
                        cache: state._overlayHighlightCache,
                        getItemId: getOverlayItemId,
                        getItemText: getOverlayItemText,
                        focusNodeIds: focusNodeIdsSL
                    });
                }
            } catch (sortErr) {
                ovWarn(logPrefix + ' overlay relevance sort skipped:', sortErr);
            }

            // Render overlay panels for each node
            renderOverlayPanels(overlayType, overlayData);

        } catch (error) {
            console.error(logPrefix + ' Failed to load overlay data:', error);
        } finally {
            if (!quiet) hideLoading();
        }
    }

    // Record a glossary anchor (dataset or attribute that carries the glossary).
    // Called from the glossary fetch loops as terms are discovered, so the BFS
    // for related-glossary highlighting can later traverse the unified anchor
    // graph from the clicked glossary's anchors.
    function recordGlossaryAnchor(cache, anchorKey, glossaryId) {
        if (!cache || !anchorKey) return;
        const helper = window.MapOverlayHighlight;
        const gid = helper && typeof helper.normId === 'function'
            ? helper.normId(glossaryId)
            : String(glossaryId == null ? '' : glossaryId).trim();
        if (!gid) return;
        if (!cache.glossaryToAnchors.has(gid)) cache.glossaryToAnchors.set(gid, new Set());
        cache.glossaryToAnchors.get(gid).add(anchorKey);
        if (!cache.anchorToGlossaries.has(anchorKey)) cache.anchorToGlossaries.set(anchorKey, new Set());
        cache.anchorToGlossaries.get(anchorKey).add(gid);
    }

    function recordAttributeOwner(cache, attributeId, datasetId) {
        if (!cache) return;
        const helper = window.MapOverlayHighlight;
        const norm = helper && typeof helper.normId === 'function'
            ? helper.normId
            : function (v) { return String(v == null ? '' : v).trim(); };
        const aid = norm(attributeId);
        const did = norm(datasetId);
        if (aid && did) cache.attributeOwnerMap.set(aid, did);
    }

    // Special handling for attributes and linking-attributes overlays
    async function loadAttributeOverlayData(overlayType, systemIds, currentSystemId, overlayData) {
        ovLog(logPrefix + ' Loading attribute overlay:', overlayType, 'for system:', currentSystemId);

        // Normalize visible systems in current map
        const visibleSystemIds = Array.from(new Set((systemIds || []).map(id => String(id)).filter(Boolean)));
        const currentId = String(currentSystemId);
        const connectedSystemIds = visibleSystemIds.filter(id => id !== currentId);

        // Build relationships across ALL visible systems (not only current<->connected)
        const relationshipPromises = [];
        for (let i = 0; i < visibleSystemIds.length; i++) {
            for (let j = i + 1; j < visibleSystemIds.length; j++) {
                const a = visibleSystemIds[i];
                const b = visibleSystemIds[j];
                relationshipPromises.push(fetchAttributeRelationships(a, b));
                relationshipPromises.push(fetchAttributeRelationships(b, a));
            }
        }

        const allRelationships = (await Promise.all(relationshipPromises)).flat();
        state.attributeRelationships = allRelationships;
        if (state._overlayHighlightCache) {
            state._overlayHighlightCache.attributeRels = allRelationships;
        }
        ovLog(logPrefix + ' Loaded', allRelationships.length, 'attribute relationships across visible systems');

        // Build linked attribute IDs per system from all relationships
        const linkedAttributeIds = new Map(); // systemId -> Set(attributeId)
        allRelationships.forEach(rel => {
            if (rel.sourceSystemId && rel.sourceAttributeId != null) {
                const sid = String(rel.sourceSystemId);
                if (!linkedAttributeIds.has(sid)) linkedAttributeIds.set(sid, new Set());
                linkedAttributeIds.get(sid).add(String(rel.sourceAttributeId));
            }
            if (rel.targetSystemId && rel.targetAttributeId != null) {
                const sid = String(rel.targetSystemId);
                if (!linkedAttributeIds.has(sid)) linkedAttributeIds.set(sid, new Set());
                linkedAttributeIds.get(sid).add(String(rel.targetAttributeId));
            }
        });

        // Overlay behavior:
        // - attributes: keep existing intent (all attrs for current; linked attrs for others)
        // - linking-attributes: show linked attrs for every visible system
        const attrsBySystem = new Map();
        await Promise.all(visibleSystemIds.map(async sid => {
            const attrs = await fetchAllAttributesForSystem(sid);
            attrsBySystem.set(sid, Array.isArray(attrs) ? attrs : []);
        }));

        if (overlayType === 'attributes') {
            const currentAttrs = attrsBySystem.get(currentId) || [];
            if (currentAttrs.length > 0) overlayData.set(currentId, currentAttrs);

            connectedSystemIds.forEach(sid => {
                const linkedIds = linkedAttributeIds.get(sid) || new Set();
                if (!linkedIds.size) return;
                const attrs = attrsBySystem.get(sid) || [];
                const linkedAttrs = attrs.filter(attr => linkedIds.has(String(attr.id)));
                if (linkedAttrs.length > 0) overlayData.set(sid, linkedAttrs);
            });
        } else if (overlayType === 'linking-attributes') {
            function collectLinkedMetaForAttribute(systemId, attrId) {
                const sid = String(systemId);
                const aid = String(attrId);
                const directions = new Set();
                const relatedDatasets = new Set();
                const relatedAttributes = new Set();
                for (let i = 0; i < allRelationships.length; i++) {
                    const rel = allRelationships[i] || {};
                    const srcSid = String(rel.sourceSystemId != null ? rel.sourceSystemId : '');
                    const tgtSid = String(rel.targetSystemId != null ? rel.targetSystemId : '');
                    const srcAid = String(rel.sourceAttributeId != null ? rel.sourceAttributeId : '');
                    const tgtAid = String(rel.targetAttributeId != null ? rel.targetAttributeId : '');
                    if (sid === srcSid && aid === srcAid) {
                        directions.add('outbound');
                        const rd = rel.targetDataSet || rel.targetDatasetName || rel.target_dataset_name || rel.targetDataset || '';
                        const ra = rel.targetAttribute || rel.targetAttributeName || rel.target_attribute_name || rel.target_attribute || '';
                        if (rd) relatedDatasets.add(String(rd));
                        if (ra) relatedAttributes.add(String(ra));
                    } else if (sid === tgtSid && aid === tgtAid) {
                        directions.add('inbound');
                        const rd = rel.sourceDataSet || rel.sourceDatasetName || rel.source_dataset_name || rel.sourceDataset || '';
                        const ra = rel.sourceAttribute || rel.sourceAttributeName || rel.source_attribute_name || rel.source_attribute || '';
                        if (rd) relatedDatasets.add(String(rd));
                        if (ra) relatedAttributes.add(String(ra));
                    }
                }
                return {
                    direction: Array.from(directions).join(', '),
                    relatedDataset: Array.from(relatedDatasets).join(', '),
                    relatedAttribute: Array.from(relatedAttributes).join(', ')
                };
            }
            visibleSystemIds.forEach(sid => {
                const linkedIds = linkedAttributeIds.get(sid) || new Set();
                if (!linkedIds.size) return;
                const attrs = attrsBySystem.get(sid) || [];
                const linkedAttrs = attrs
                    .filter(attr => linkedIds.has(String(attr.id)))
                    .map(attr => {
                        const meta = collectLinkedMetaForAttribute(sid, attr.id);
                        return Object.assign({}, attr, meta);
                    });
                if (linkedAttrs.length > 0) overlayData.set(sid, linkedAttrs);
            });
        }

        ovLog(logPrefix + ' Final overlay data:', Array.from(overlayData.entries()).map(([k, v]) => [k, v.length]));
    }

    // Special handling for attributes and linking-attributes overlays in dataset lineage
    async function loadAttributeOverlayDataForDatasets(overlayType, datasetIds, currentSystemId, overlayData) {
        ovLog(logPrefix + ' Loading attribute overlay for datasets:', overlayType);
        
        // Store attribute relationships for highlighting
        state.attributeRelationships = [];
        
        // Get current system datasets
        const currentSystemDatasets = datasetIds.filter(datasetId => {
            const datasetInfo = state.linkedDatasets.get(String(datasetId));
            return datasetInfo && String(datasetInfo.systemId) === currentSystemId;
        });
        
        // Get all attributes for current system datasets
        const currentSystemAttrs = [];
        for (const datasetId of currentSystemDatasets) {
            try {
                const attrs = await fetchAllAttributesForDataset(datasetId);
                currentSystemAttrs.push(...attrs);
            } catch (e) {
                ovWarn(logPrefix + ' Failed to fetch attributes for dataset', datasetId, e);
            }
        }
        
        ovLog(logPrefix + ' Current system dataset attributes:', currentSystemAttrs.length);
        
        // Get attribute relationships between current system datasets and other datasets
        const otherDatasetIds = datasetIds.filter(id => !currentSystemDatasets.includes(id));
        
        // Fetch relationships
        const allRelationships = [];
        const linkedAttributeIds = new Map(); // datasetId -> Set of attribute IDs
        
        // Build relationships from dataset relationships
        state.datasetRelationships.forEach(rel => {
            const sourceDatasetId = String(rel.sourceDatasetId || rel.sourceDataSetId || '');
            const targetDatasetId = String(rel.targetDatasetId || rel.targetDataSetId || '');
            
            if (sourceDatasetId && targetDatasetId && sourceDatasetId !== 'undefined' && targetDatasetId !== 'undefined') {
                allRelationships.push(rel);
                
                // Track linked attributes - normalize IDs to strings for comparison
                if (rel.sourceAttributeId) {
                    const sourceAttrId = String(rel.sourceAttributeId);
                    if (!linkedAttributeIds.has(sourceDatasetId)) {
                        linkedAttributeIds.set(sourceDatasetId, new Set());
                    }
                    linkedAttributeIds.get(sourceDatasetId).add(sourceAttrId);
                }
                if (rel.targetAttributeId) {
                    const targetAttrId = String(rel.targetAttributeId);
                    if (!linkedAttributeIds.has(targetDatasetId)) {
                        linkedAttributeIds.set(targetDatasetId, new Set());
                    }
                    linkedAttributeIds.get(targetDatasetId).add(targetAttrId);
                }
            }
        });
        
        ovLog(logPrefix + ' Linked attribute IDs per dataset:', Array.from(linkedAttributeIds.entries()).map(([k, v]) => [k, v.size]));
        
        // Log relationships with attribute IDs for debugging
        const relationshipsWithAttrs = allRelationships.filter(r => r.sourceAttributeId || r.targetAttributeId);
        ovLog(logPrefix + ' Relationships with attribute IDs:', relationshipsWithAttrs.length, 'out of', allRelationships.length);
        if (relationshipsWithAttrs.length > 0) {
            ovLog(logPrefix + ' Sample relationship with attributes:', relationshipsWithAttrs[0]);
        }
        
        state.attributeRelationships = allRelationships;
        
        // For current system datasets
        if (overlayType === 'attributes') {
            // Show ALL attributes in datasets that belong to the opened system
            currentSystemDatasets.forEach(datasetId => {
                const datasetIdStr = String(datasetId);
                const attrs = currentSystemAttrs.filter(attr => String(attr.datasetId) === datasetIdStr);
                ovLog(logPrefix + ' Dataset', datasetId, 'attributes overlay:', attrs.length, 'attributes');
                if (attrs.length > 0) {
                    overlayData.set(datasetIdStr, attrs);
                }
            });
        } else if (overlayType === 'linking-attributes') {
            // Show only attributes of the opened system that are linked with other datasets
            currentSystemDatasets.forEach(datasetId => {
                const datasetIdStr = String(datasetId);
                const linkedIds = linkedAttributeIds.get(datasetIdStr) || new Set();
                ovLog(logPrefix + ' Dataset', datasetId, 'has', linkedIds.size, 'linked attribute IDs');
                const attrs = currentSystemAttrs.filter(attr => {
                    const attrDatasetId = String(attr.datasetId);
                    const attrId = String(attr.id);
                    return attrDatasetId === datasetIdStr && linkedIds.has(attrId);
                });
                ovLog(logPrefix + ' Dataset', datasetId, 'linking-attributes overlay:', attrs.length, 'linked attributes');
                if (attrs.length > 0) {
                    overlayData.set(datasetIdStr, attrs);
                }
            });
        }
        
        // For other datasets: only show attributes that have a link with the opened system
        for (const datasetId of otherDatasetIds) {
            const datasetIdStr = String(datasetId);
            const linkedIds = linkedAttributeIds.get(datasetIdStr) || new Set();
            if (linkedIds.size === 0) {
                ovLog(logPrefix + ' Dataset', datasetId, 'has no linked attributes, skipping');
                continue;
            }
            
            // Check if dataset is locked
            const datasetInfo = state.linkedDatasets.get(datasetIdStr);
            if (datasetInfo?.isLocked) {
                ovLog(logPrefix + ' Dataset', datasetId, 'is locked, skipping attribute fetch');
                continue;
            }
            
            try {
                const attrs = await fetchAllAttributesForDataset(datasetId);
                ovLog(logPrefix + ' Fetched', attrs.length, 'attributes for dataset', datasetId);
                const linkedAttrs = attrs.filter(attr => {
                    const attrId = String(attr.id);
                    return linkedIds.has(attrId);
                });
                ovLog(logPrefix + ' Filtered to', linkedAttrs.length, 'linked attributes for dataset', datasetId);
                
                if (linkedAttrs.length > 0) {
                    overlayData.set(datasetIdStr, linkedAttrs);
                }
            } catch (e) {
                ovWarn(logPrefix + ' Failed to fetch attributes for dataset', datasetId, e);
            }
        }
        
        ovLog(logPrefix + ' Final dataset attribute overlay data:', Array.from(overlayData.entries()).map(([k, v]) => [k, v.length]));
    }

    // Special handling for glossary overlay in dataset lineage.
    // Each dataset panel gets its own glossary plus glossary terms from linked
    // attributes and directly related datasets, so the overlay is not limited to
    // the currently opened object.
    async function loadGlossaryOverlayDataForDatasets(datasetIds, currentSystemId, overlayData) {
        ovLog(logPrefix + ' Loading glossary overlay for datasets:', datasetIds.length, 'datasets', 'current system:', currentSystemId);

        const datasetIdSet = new Set((datasetIds || []).map(id => String(id)).filter(Boolean));
        const relatedDatasetIdsByDataset = new Map();
        const linkedAttributeIdsByDataset = new Map();
        const datasetCache = new Map();
        const attributeCache = new Map();

        function addToSetMap(map, key, value) {
            const keyStr = String(key || '');
            const valueStr = String(value || '');
            if (!keyStr || !valueStr || valueStr === 'undefined' || valueStr === 'null') return;
            if (!map.has(keyStr)) map.set(keyStr, new Set());
            map.get(keyStr).add(valueStr);
        }

        function getRelDatasetId(rel, keys) {
            for (let i = 0; i < keys.length; i++) {
                const value = rel && rel[keys[i]];
                if (value !== null && value !== undefined && String(value).trim() !== '') return String(value);
            }
            return '';
        }

        function getRelAttributeId(rel, keys) {
            for (let i = 0; i < keys.length; i++) {
                const value = rel && rel[keys[i]];
                if (value !== null && value !== undefined && String(value).trim() !== '') return String(value);
            }
            return '';
        }

        const attrRelsForCache = [];

        (state.datasetRelationships || []).forEach(rel => {
            const sourceDatasetId = getRelDatasetId(rel, ['sourceDatasetId', 'sourceDataSetId', 'Source_DatasetID', 'source_dataset_id']);
            const targetDatasetId = getRelDatasetId(rel, ['targetDatasetId', 'targetDataSetId', 'Target_DatasetID', 'target_dataset_id']);
            const sourceAttributeId = getRelAttributeId(rel, ['sourceAttributeId', 'Source_AttributeID', 'source_attribute_id']);
            const targetAttributeId = getRelAttributeId(rel, ['targetAttributeId', 'Target_AttributeID', 'target_attribute_id']);

            if (sourceDatasetId && targetDatasetId) {
                addToSetMap(relatedDatasetIdsByDataset, sourceDatasetId, targetDatasetId);
                addToSetMap(relatedDatasetIdsByDataset, targetDatasetId, sourceDatasetId);
            }
            if (sourceDatasetId && sourceAttributeId) addToSetMap(linkedAttributeIdsByDataset, sourceDatasetId, sourceAttributeId);
            if (targetDatasetId && targetAttributeId) addToSetMap(linkedAttributeIdsByDataset, targetDatasetId, targetAttributeId);
            if (sourceAttributeId && targetAttributeId) {
                attrRelsForCache.push({ sourceAttributeId: sourceAttributeId, targetAttributeId: targetAttributeId });
            }
        });

        // Seed the highlight cache for dataset-lineage maps. The system-lineage
        // path does this via the scope cache; here we have to do it manually
        // because the dataset-lineage flow does not build a glossary scope.
        if (state._overlayHighlightCache) {
            if (!state._overlayHighlightCache.datasetGraph || state._overlayHighlightCache.datasetGraph.size === 0) {
                const dsGraph = new Map();
                relatedDatasetIdsByDataset.forEach((neighbors, did) => {
                    if (!dsGraph.has(did)) dsGraph.set(did, new Set());
                    if (neighbors && typeof neighbors.forEach === 'function') {
                        neighbors.forEach(n => dsGraph.get(did).add(String(n)));
                    }
                });
                state._overlayHighlightCache.datasetGraph = dsGraph;
            }
            if (!state._overlayHighlightCache.attributeRels || state._overlayHighlightCache.attributeRels.length === 0) {
                state._overlayHighlightCache.attributeRels = attrRelsForCache;
            }
        }

        async function getDataset(datasetId) {
            const datasetIdStr = String(datasetId);
            if (datasetCache.has(datasetIdStr)) return datasetCache.get(datasetIdStr);

            const cachedInfo = state.linkedDatasets?.get(datasetIdStr);
            if (cachedInfo?.dataset) {
                datasetCache.set(datasetIdStr, cachedInfo.dataset);
                return cachedInfo.dataset;
            }

            let dataset = null;
            try {
                const API = window.BUDG_API_SERVICE;
                if (API && typeof API.getDatasetById === 'function') {
                    const resp = await API.getDatasetById(datasetIdStr, null, { silent404: true });
                    dataset = resp?.data || resp || null;
                } else {
                    const resp = await fetch(`/api/dataset/${encodeURIComponent(datasetIdStr)}`, { credentials: 'include' });
                    if (resp.ok) {
                        const json = await resp.json();
                        dataset = json?.data || json || null;
                    }
                }
            } catch (e) {
                dataset = null;
            }

            datasetCache.set(datasetIdStr, dataset);
            return dataset;
        }

        async function getAttributes(datasetId) {
            const datasetIdStr = String(datasetId);
            if (attributeCache.has(datasetIdStr)) return attributeCache.get(datasetIdStr);
            const attrs = await fetchAllAttributesForDataset(datasetIdStr);
            attributeCache.set(datasetIdStr, attrs);
            return attrs;
        }

        function getDatasetGlossary(dataset) {
            if (!dataset) return null;
            const name = dataset.glossaryName || dataset.GlossaryName || dataset.glossary_name ||
                dataset.primaryGlossaryName || dataset.PrimaryGlossaryName ||
                (dataset.Glossary && isNaN(Number(dataset.Glossary)) ? dataset.Glossary : '');
            const id = dataset.glossaryId || dataset.Glossary_ID || dataset.glossary_id ||
                dataset.primaryGlossaryId || dataset.PrimaryGlossaryID || dataset.glossaryID || dataset.glossary;
            if (!name && (id === null || id === undefined || id === '')) return null;
            return { name: name, id: id };
        }

        const _highlightCache = state._overlayHighlightCache;
        const _hh = window.MapOverlayHighlight;

        function getAttributeGlossary(attr) {
            if (!attr) return null;
            const name = (typeof extractGlossaryNameFromAttr === 'function' ? extractGlossaryNameFromAttr(attr) : '') ||
                attr.glossaryName || attr.GlossaryName ||
                attr.glossary || attr['Glossary Name attribute'] || attr.glossary_name;
            const id = attr.glossaryId || attr.glossary_id || attr.Glossary_ID || attr.glossaryID;
            if (!name && (id === null || id === undefined || id === '')) return null;
            return { name: name, id: id };
        }

        function addGlossaryTerm(terms, seen, glossary, source) {
            if (!glossary || (!glossary.name && (glossary.id === null || glossary.id === undefined || glossary.id === ''))) return;
            const key = glossary.id != null && glossary.id !== ''
                ? `id:${String(glossary.id)}`
                : `name:${String(glossary.name).trim().toLowerCase()}`;
            if (seen.has(key)) return;
            seen.add(key);
            terms.push({
                name: glossary.name || '',
                glossary: glossary.name || '',
                id: glossary.id,
                glossaryId: glossary.id,
                source: source
            });
        }

        async function addDatasetGlossary(terms, seen, datasetId, source) {
            const dataset = await getDataset(datasetId);
            const glossary = getDatasetGlossary(dataset);
            if (glossary && _highlightCache && _hh && glossary.id != null) {
                recordGlossaryAnchor(_highlightCache, _hh.dsKey(datasetId), glossary.id);
            }
            addGlossaryTerm(terms, seen, glossary, source);
        }

        function isDatasetInCurrentSystem(datasetId) {
            const info = state.linkedDatasets?.get(String(datasetId));
            return !!(info && currentSystemId && String(info.systemId) === String(currentSystemId));
        }

        async function addLinkedAttributeGlossaries(terms, seen, datasetId, source, opts) {
            const datasetIdStr = String(datasetId);
            const linkedIds = linkedAttributeIdsByDataset.get(datasetIdStr) || new Set();
            const attrs = await getAttributes(datasetIdStr);
            const restrictToLinked = !!(opts && opts.restrictToLinked);
            // For non-current datasets we ALWAYS restrict to attributes that are
            // linked via attribute relationships; never fall back to all attrs.
            // For current-system datasets we include every attribute.
            const scopedAttrs = restrictToLinked
                ? attrs.filter(attr => linkedIds.has(String(attr.id || attr.ID || attr.attribute_id || '')))
                : (linkedIds.size > 0
                    ? attrs.filter(attr => linkedIds.has(String(attr.id || attr.ID || attr.attribute_id || '')))
                    : attrs);
            scopedAttrs.forEach(attr => {
                const aid = attr && (attr.id != null ? attr.id : (attr.ID != null ? attr.ID : attr.attribute_id));
                if (_highlightCache && aid != null) {
                    recordAttributeOwner(_highlightCache, aid, datasetId);
                }
                const glossary = getAttributeGlossary(attr);
                if (glossary && _highlightCache && _hh && aid != null && glossary.id != null) {
                    recordGlossaryAnchor(_highlightCache, _hh.attrKey(aid), glossary.id);
                }
                addGlossaryTerm(terms, seen, glossary, source);
            });
        }

        for (const datasetId of datasetIdSet) {
            const datasetIdStr = String(datasetId);
            const datasetInfo = state.linkedDatasets?.get(datasetIdStr);
            if (datasetInfo?.isLocked) continue;

            const terms = [];
            const seen = new Set();

            const isCurrent = isDatasetInCurrentSystem(datasetIdStr);

            await addDatasetGlossary(terms, seen, datasetIdStr, 'dataset');
            // Current-system dataset: include all attribute glossaries.
            // Other-system dataset: only attribute glossaries that are linked to current via relationships.
            await addLinkedAttributeGlossaries(terms, seen, datasetIdStr, 'attribute', { restrictToLinked: !isCurrent });

            const relatedIds = relatedDatasetIdsByDataset.get(datasetIdStr) || new Set();
            for (const relatedDatasetId of relatedIds) {
                if (!datasetIdSet.has(String(relatedDatasetId))) continue;
                const relatedInfo = state.linkedDatasets?.get(String(relatedDatasetId));
                if (relatedInfo?.isLocked) continue;
                const relatedIsCurrent = isDatasetInCurrentSystem(relatedDatasetId);
                await addDatasetGlossary(terms, seen, relatedDatasetId, 'related-dataset');
                await addLinkedAttributeGlossaries(terms, seen, relatedDatasetId, 'related-attribute', { restrictToLinked: !relatedIsCurrent });
            }

            const enrichedTerms = await enrichGlossaryRows(terms);
            if (enrichedTerms && enrichedTerms.length > 0) {
                overlayData.set(datasetIdStr, enrichedTerms);
            }
        }

        ovLog(logPrefix + ' Final glossary overlay data for datasets:', Array.from(overlayData.entries()).map(([k, v]) => [k, v.length]));
    }

    // Fetch all attributes for a dataset
    async function fetchAllAttributesForDataset(datasetId) {
        try {
            // Use /api/attribute/{datasetId} endpoint which returns attributes for a dataset
            const response = await fetch(`/api/attribute/${datasetId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            if (!response.ok) {
                ovWarn(logPrefix + ' Failed to fetch attributes for dataset', datasetId, '- status:', response.status);
                return [];
            }
            
            const attrData = await response.json();
            const attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
            
            ovLog(logPrefix + ' Fetched', attrs.length, 'attributes for dataset', datasetId);
            
            const norm = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.attributeFromApi === 'function'
                ? window.OverlayRowNormalize.attributeFromApi
                : null;
            return (norm
                ? attrs.map(function (attr) {
                    var row = norm(attr, { datasetId: String(datasetId) });
                    row.parentId = attr.parentId || attr.Parent_ID || attr.parent_id || null;
                    return row;
                })
                : attrs.map(attr => {
                    const id = attr.id || attr.ID || attr.attribute_id;
                    const name = attr['Name attribute'] || attr.name || attr.primaryName || attr.PrimaryName || attr.Name || attr.primary_name || '';
                    return Object.assign({}, attr, {
                        id,
                        name,
                        typeName: attr.typeName || attr.TypeName || attr.type || attr.Type || attr['Data Type attribute'] || attr.dataType,
                        glossaryName: attr.glossaryName || attr.GlossaryName || attr.glossary || attr['Glossary Name attribute'],
                        refNumber: attr.refNumber || attr.RefNumber || attr.ref || attr.Ref || attr['Ref. attribute'] || attr.attributeRef,
                        datasetId: String(datasetId),
                        parentId: attr.parentId || attr.Parent_ID || attr.parent_id || null
                    });
                })).filter(attr => attr.id);
        } catch (e) {
            ovWarn(logPrefix + ' Failed to fetch attributes for dataset', datasetId, e);
            return [];
        }
    }

    // Fetch overlay data for a dataset (for dataset lineage)
    async function fetchOverlayDataForDataset(datasetId, overlayType) {
        const API = window.BUDG_API_SERVICE;
        if (!API) {
            ovWarn(logPrefix + ' API service not available');
            return [];
        }

        try {
            switch (overlayType) {
                case 'description':
                    try {
                        // First try to get from linkedDatasets cache
                        const cachedInfo = state.linkedDatasets.get(String(datasetId));
                        ovLog(logPrefix + ' Fetching description for dataset', datasetId, 'cached:', !!cachedInfo?.dataset, 'locked:', cachedInfo?.isLocked);
                        
                        if (cachedInfo?.dataset) {
                            const ds = cachedInfo.dataset;
                            // If locked, return empty (no description available)
                            if (cachedInfo.isLocked) {
                                return [];
                            }
                            // Check various possible description fields
                            const desc = ds.definition || ds.Definition || ds.description || ds.Description;
                            if (desc) {
                                return [{ name: 'Definition', value: desc }];
                            }
                            // If no description but we have the dataset, return that as info
                            if (ds.name || ds.Name || ds.datasetName) {
                                return [{ name: 'Info', value: `Dataset: ${ds.name || ds.Name || ds.datasetName}` }];
                            }
                        }
                        // If not in cache, fetch from API
                        ovLog(logPrefix + ' Not in cache, fetching from API for dataset:', datasetId);
                        try {
                            const datasetData = await API.getDatasetById(datasetId, null, { silent404: true });
                            ovLog(logPrefix + ' API response for dataset', datasetId, ':', datasetData);
                            const dataset = datasetData?.data || datasetData;
                            if (dataset?.definition != null && String(dataset.definition).trim() !== '') {
                                return [{ name: 'Definition', value: dataset.definition }];
                            }
                            if (dataset?.Definition != null && String(dataset.Definition).trim() !== '') {
                                return [{ name: 'Definition', value: dataset.Definition }];
                            }
                            if (dataset?.description != null && String(dataset.description).trim() !== '') {
                                return [{ name: 'Definition', value: dataset.description }];
                            }
                            if (dataset?.Description != null && String(dataset.Description).trim() !== '') {
                                return [{ name: 'Definition', value: dataset.Description }];
                            }
                        } catch (apiError) {
                            // If API fails with "not found", return empty (dataset is likely locked)
                            if (apiError.message && apiError.message.includes('not found')) {
                                return [];
                            }
                            throw apiError;
                        }
                    } catch (e) { 
                        if (e.message && e.message.includes('not found')) {
                            return [];
                        }
                        ovWarn(logPrefix + ' Error fetching dataset description:', e);
                    }
                    return [];

                case 'stakeholders':
                    try {
                        const stakeholdersData = await API.getDatasetStakeholders(datasetId);
                        return Array.isArray(stakeholdersData?.data) ? stakeholdersData.data : (Array.isArray(stakeholdersData) ? stakeholdersData : []);
                    } catch (e) { 
                        ovWarn(logPrefix + ' Error fetching dataset stakeholders:', e);
                    }
                    return [];

                case 'datasets':
                    // For dataset lineage, show related datasets
                    const relatedDatasets = [];
                    state.datasetRelationships.forEach(rel => {
                        const sourceDatasetId = String(rel.sourceDatasetId || rel.sourceDataSetId || '');
                        const targetDatasetId = String(rel.targetDatasetId || rel.targetDataSetId || '');
                        
                        if (sourceDatasetId === String(datasetId) && targetDatasetId) {
                            const targetInfo = state.linkedDatasets.get(targetDatasetId);
                            if (targetInfo) relatedDatasets.push(targetInfo.dataset);
                        }
                        if (targetDatasetId === String(datasetId) && sourceDatasetId) {
                            const sourceInfo = state.linkedDatasets.get(sourceDatasetId);
                            if (sourceInfo) relatedDatasets.push(sourceInfo.dataset);
                        }
                    });
                    return relatedDatasets;

                case 'glossary': {
                    const cachedInfo = state.linkedDatasets.get(String(datasetId));
                    if (cachedInfo?.isLocked) return [];
                    const glossaryTerms = [];
                    const seen = new Set();
                    // 1) Dataset's own glossary term
                    try {
                        let dsGName = cachedInfo?.dataset?.glossaryName;
                        let dsGId = cachedInfo?.dataset?.glossaryId;
                        if (!dsGName) {
                            const dsResp = await fetch(`/api/dataset/${datasetId}`, { credentials: 'include' });
                            if (dsResp.ok) {
                                const dsJson = await dsResp.json();
                                const ds = dsJson?.data || dsJson;
                                dsGName = ds?.glossaryName;
                                dsGId = ds?.glossaryId;
                            }
                        }
                        if (dsGName && !seen.has(dsGName)) {
                            seen.add(dsGName);
                            glossaryTerms.push({ name: dsGName, glossary: dsGName, id: dsGId, glossaryId: dsGId, source: 'dataset' });
                        }
                    } catch (e) { /* ignore */ }
                    // 2) Glossary terms from attributes
                    try {
                        const attrResp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                        if (attrResp.ok) {
                            const attrData = await attrResp.json();
                            const attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
                            attrs.forEach(attr => {
                                const gName = extractGlossaryNameFromAttr(attr);
                                if (gName && !seen.has(gName)) {
                                    seen.add(gName);
                                    const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                    glossaryTerms.push({ name: gName, glossary: gName, id: gId, glossaryId: gId, source: 'attribute' });
                                }
                            });
                        }
                    } catch (e) {
                        if (!(e.message && e.message.includes('not found'))) {
                            ovWarn(logPrefix + ' Error fetching dataset glossary:', e);
                        }
                    }
                    return await enrichGlossaryRows(glossaryTerms);
                }

                case 'processes':
                    // Fetch processes that impact this dataset
                    try {
                        // First try from cache
                        const cachedProcInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedProcInfo?.isLocked) {
                            return []; // Locked datasets don't have accessible processes
                        }
                        if (cachedProcInfo?.dataset?.processes && Array.isArray(cachedProcInfo.dataset.processes)) {
                            return cachedProcInfo.dataset.processes;
                        }
                        // Get from dataset data
                        try {
                            const datasetForProc = await API.getDatasetById(datasetId, null, { silent404: true });
                            const dsProc = datasetForProc?.data || datasetForProc;
                            if (dsProc?.processes && Array.isArray(dsProc.processes)) {
                                return dsProc.processes;
                            }
                            // Check for process links in dataset data
                            if (dsProc?.processName || dsProc?.process) {
                                return [{ name: dsProc.processName || dsProc.process, id: dsProc.processId }];
                            }
                        } catch (apiError) {
                            if (apiError.message && apiError.message.includes('not found')) {
                                return [];
                            }
                            throw apiError;
                        }
                    } catch (e) { 
                        if (e.message && e.message.includes('not found')) {
                            return [];
                        }
                        ovWarn(logPrefix + ' Error fetching processes for dataset', datasetId, e);
                    }
                    return [];

                case 'projects':
                    // Fetch projects that impact this dataset
                    try {
                        // First try from cache
                        const cachedProjInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedProjInfo?.isLocked) {
                            return [];
                        }
                        if (cachedProjInfo?.dataset?.projects && Array.isArray(cachedProjInfo.dataset.projects)) {
                            return cachedProjInfo.dataset.projects;
                        }
                        // Get from dataset data
                        try {
                            const datasetForProj = await API.getDatasetById(datasetId, null, { silent404: true });
                            const dsProj = datasetForProj?.data || datasetForProj;
                            if (dsProj?.projects && Array.isArray(dsProj.projects)) {
                                return dsProj.projects;
                            }
                            if (dsProj?.projectName || dsProj?.project) {
                                return [{ name: dsProj.projectName || dsProj.project, id: dsProj.projectId }];
                            }
                        } catch (apiError) {
                            if (apiError.message && apiError.message.includes('not found')) {
                                return [];
                            }
                            throw apiError;
                        }
                    } catch (e) { 
                        if (e.message && e.message.includes('not found')) {
                            return [];
                        }
                        ovWarn(logPrefix + ' Error fetching projects for dataset', datasetId, e);
                    }
                    return [];

                case 'policies':
                    // Fetch policies that impact this dataset
                    try {
                        // First try from cache
                        const cachedPolInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedPolInfo?.isLocked) {
                            return [];
                        }
                        if (cachedPolInfo?.dataset?.policies && Array.isArray(cachedPolInfo.dataset.policies)) {
                            return cachedPolInfo.dataset.policies;
                        }
                        // Get from dataset data
                        try {
                            const datasetForPol = await API.getDatasetById(datasetId, null, { silent404: true });
                            const dsPol = datasetForPol?.data || datasetForPol;
                            if (dsPol?.policies && Array.isArray(dsPol.policies)) {
                                return dsPol.policies;
                            }
                            if (dsPol?.policyName || dsPol?.policy) {
                                return [{ name: dsPol.policyName || dsPol.policy, id: dsPol.policyId }];
                            }
                        } catch (apiError) {
                            if (apiError.message && apiError.message.includes('not found')) {
                                return [];
                            }
                            throw apiError;
                        }
                    } catch (e) { 
                        if (e.message && e.message.includes('not found')) {
                            return [];
                        }
                        ovWarn(logPrefix + ' Error fetching policies for dataset', datasetId, e);
                    }
                    return [];

                case 'business-area':
                    // Fetch business areas that impact this dataset
                    try {
                        // First try from cache
                        const cachedBAInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedBAInfo?.dataset?.businessAreas && Array.isArray(cachedBAInfo.dataset.businessAreas)) {
                            return cachedBAInfo.dataset.businessAreas;
                        }
                        // Get from dataset data
                        const datasetForBA = await API.getDatasetById(datasetId, null, { silent404: true });
                        const dsBA = datasetForBA?.data || datasetForBA;
                        if (dsBA?.businessAreas && Array.isArray(dsBA.businessAreas)) {
                            return dsBA.businessAreas;
                        }
                        if (dsBA?.businessAreaName || dsBA?.businessArea) {
                            return [{ name: dsBA.businessAreaName || dsBA.businessArea, id: dsBA.businessAreaId }];
                        }
                    } catch (e) { 
                        ovWarn(logPrefix + ' Error fetching business areas for dataset', datasetId, e);
                    }
                    return [];

                case 'products':
                    // Fetch products related to this dataset
                    try {
                        // First try from cache
                        const cachedProdInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedProdInfo?.dataset?.products && Array.isArray(cachedProdInfo.dataset.products)) {
                            return cachedProdInfo.dataset.products;
                        }
                        // Get from dataset data
                        const datasetForProd = await API.getDatasetById(datasetId, null, { silent404: true });
                        const dsProd = datasetForProd?.data || datasetForProd;
                        if (dsProd?.products && Array.isArray(dsProd.products)) {
                            return dsProd.products;
                        }
                        if (dsProd?.productName || dsProd?.product) {
                            return [{ name: dsProd.productName || dsProd.product, id: dsProd.productId }];
                        }
                    } catch (e) { 
                        ovWarn(logPrefix + ' Error fetching products for dataset', datasetId, e);
                    }
                    return [];

                case 'legal-entities':
                    // Fetch legal entities related to this dataset
                    try {
                        // First try from cache
                        const cachedLegalInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedLegalInfo?.dataset?.legalEntities && Array.isArray(cachedLegalInfo.dataset.legalEntities)) {
                            return cachedLegalInfo.dataset.legalEntities;
                        }
                        // Get from dataset data
                        const datasetForLegal = await API.getDatasetById(datasetId, null, { silent404: true });
                        const dsLegal = datasetForLegal?.data || datasetForLegal;
                        if (dsLegal?.legalEntities && Array.isArray(dsLegal.legalEntities)) {
                            return dsLegal.legalEntities;
                        }
                        if (dsLegal?.legalEntityName || dsLegal?.legalEntity) {
                            return [{ name: dsLegal.legalEntityName || dsLegal.legalEntity, id: dsLegal.legalEntityId }];
                        }
                    } catch (e) { 
                        ovWarn(logPrefix + ' Error fetching legal entities for dataset', datasetId, e);
                    }
                    return [];

                case 'data-quality':
                    // Data quality rules for this dataset
                    try {
                        // First try from cache
                        const cachedDQInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedDQInfo?.dataset?.dataQuality) {
                            const dq = cachedDQInfo.dataset.dataQuality;
                            if (Array.isArray(dq)) return dq;
                            if (typeof dq === 'object') return [dq];
                        }
                        // Get from dataset data
                        const datasetForDQ = await API.getDatasetById(datasetId, null, { silent404: true });
                        const dsDQ = datasetForDQ?.data || datasetForDQ;
                        if (dsDQ?.dataQuality) {
                            const dq = dsDQ.dataQuality;
                            if (Array.isArray(dq)) return dq;
                            if (typeof dq === 'object') return [dq];
                        }
                        // Check for quality rating/score
                        if (dsDQ?.qualityRating || dsDQ?.qualityScore) {
                            return [{ 
                                name: `Quality: ${dsDQ.qualityRating || dsDQ.qualityScore}`, 
                                rating: dsDQ.qualityRating, 
                                score: dsDQ.qualityScore 
                            }];
                        }
                    } catch (e) { 
                        ovWarn(logPrefix + ' Error fetching data quality for dataset', datasetId, e);
                    }
                    return [];

                case 'data-privacy':
                    // Data privacy rules/classifications for this dataset
                    try {
                        // First try to get from linkedDatasets cache
                        const cachedPrivacyInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedPrivacyInfo?.dataset) {
                            const ds = cachedPrivacyInfo.dataset;
                            if (ds.privacyClassification || ds.dataPrivacy || ds.sensitivity) {
                                const cls = ds.privacyClassification || ds.dataPrivacy || ds.sensitivity;
                                return [{ name: cls, classification: cls, type: 'privacy' }];
                            }
                        }
                        // If not in cache, fetch from API
                        const datasetDataPrivacy = await API.getDatasetById(datasetId, null, { silent404: true });
                        const datasetPriv = datasetDataPrivacy?.data || datasetDataPrivacy;
                        if (datasetPriv?.privacyClassification || datasetPriv?.dataPrivacy || datasetPriv?.sensitivity) {
                            const cls = datasetPriv.privacyClassification || datasetPriv.dataPrivacy || datasetPriv.sensitivity;
                            return [{ name: cls, classification: cls, type: 'privacy' }];
                        }
                    } catch (e) { 
                        ovWarn(logPrefix + ' Error fetching data privacy:', e);
                    }
                    return [];

                case 'geography':
                    // Geography for this dataset - try dataset data or classification
                    try {
                        // First try to get from linkedDatasets cache
                        const cachedGeoInfo = state.linkedDatasets.get(String(datasetId));
                        if (cachedGeoInfo?.dataset) {
                            const ds = cachedGeoInfo.dataset;
                            if (ds.geography || ds.region || ds.location || ds.country) {
                                const nm = ds.geography || ds.region || ds.location || ds.country;
                                return [{
                                    name: nm,
                                    region: ds.region || '',
                                    country: ds.country || '',
                                    type: 'geography'
                                }];
                            }
                        }
                        // If not in cache, fetch from API
                        const datasetDataGeo = await API.getDatasetById(datasetId, null, { silent404: true });
                        const datasetGeo = datasetDataGeo?.data || datasetDataGeo;
                        if (datasetGeo?.geography || datasetGeo?.region || datasetGeo?.location || datasetGeo?.country) {
                            const nm = datasetGeo.geography || datasetGeo.region || datasetGeo.location || datasetGeo.country;
                            return [{
                                name: nm,
                                region: datasetGeo.region || '',
                                country: datasetGeo.country || '',
                                type: 'geography'
                            }];
                        }
                    } catch (e) { 
                        ovWarn(logPrefix + ' Error fetching geography:', e);
                    }
                    return [];

                default:
                    return [];
            }
        } catch (e) {
            ovWarn(logPrefix + ' Failed to fetch overlay data for dataset', datasetId, overlayType, e);
            return [];
        }
    }

    // Fetch all attributes for a system using the API service
    async function fetchAllAttributesForSystem(systemId) {
        try {
            const API = window.BUDG_API_SERVICE;
            if (!API || !API.getSystemAttributes) {
                ovWarn(logPrefix + ' API service not available');
                return [];
            }
            
            const response = await API.getSystemAttributes(systemId);
            ovLog(logPrefix + ' API response for system', systemId, ':', response);
            
            const attrs = Array.isArray(response?.data) ? response.data : (Array.isArray(response) ? response : []);
            ovLog(logPrefix + ' Parsed', attrs.length, 'attributes from response');
            
            const norm = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.attributeFromApi === 'function'
                ? window.OverlayRowNormalize.attributeFromApi
                : null;
            const formatted = (norm
                ? attrs.map(function (attr) { return norm(attr, { systemId: systemId }); })
                : attrs.map(attr => {
                    const id = attr.id || attr.ID || attr.attributeId;
                    const name = attr.name || attr['Name attribute'] || attr.attributeName || attr.primaryName || attr.PrimaryName || attr.Name;
                    return Object.assign({}, attr, {
                        id,
                        name: name != null && name !== '' ? name : (attr.attributeName != null ? String(attr.attributeName) : ''),
                        typeName: attr.typeName || attr.TypeName || attr.type || attr.Type || attr.dataType || attr['Data Type attribute'],
                        glossaryName: attr.glossaryName || attr.GlossaryName || attr.glossary || attr['Glossary Name attribute']
                            || attr.attributeGlossaryName || attr.datasetGlossaryName,
                        refNumber: attr.refNumber || attr.RefNumber || attr.ref || attr.Ref || attr.attributeRef || attr['Ref. attribute'],
                        datasetId: attr.datasetId || attr.Dataset_ID || attr.dataset_id,
                        datasetName: attr.datasetName || attr.dataset_name || attr.Dataset_Name,
                        systemId: systemId
                    });
                })).filter(attr => attr.id);
            
            ovLog(logPrefix + ' Formatted', formatted.length, 'attributes with valid IDs');
            return formatted;
        } catch (e) {
            console.error(logPrefix + ' Failed to fetch attributes for system', systemId, e);
            return [];
        }
    }

    // Fetch attribute relationships between two systems
    async function fetchAttributeRelationships(sourceSystemId, targetSystemId) {
        try {
            const response = await fetch(`/api/attribute-relationships?sourceSystem=${sourceSystemId}&targetSystem=${targetSystemId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            if (!response.ok) return [];
            
            const data = await response.json();
            const relationships = Array.isArray(data) ? data : [];
            
            // Add system IDs to each relationship for easier lookup
            return relationships.map(rel => ({
                ...rel,
                sourceSystemId: sourceSystemId,
                targetSystemId: targetSystemId
            }));
        } catch (e) {
            ovWarn(logPrefix + ' Failed to fetch attribute relationships', sourceSystemId, '->', targetSystemId, e);
            return [];
        }
    }

    async function enrichGlossaryRows(terms) {
        if (window.OverlayColumns && typeof window.OverlayColumns.enrichGlossaryOverlayTerms === 'function') {
            return window.OverlayColumns.enrichGlossaryOverlayTerms(terms);
        }
        return terms;
    }

    function normalizeDatasetsList(raw) {
        if (raw == null) return [];
        if (Array.isArray(raw)) return raw;
        if (Array.isArray(raw.data)) return raw.data;
        if (raw.data && Array.isArray(raw.data.data)) return raw.data.data;
        if (Array.isArray(raw.datasets)) return raw.datasets;
        if (raw.data && Array.isArray(raw.data.datasets)) return raw.data.datasets;
        return [];
    }

    function getDatasetIdFromEntity(ds) {
        return ds && (ds.id || ds.ID || ds.datasetId || ds.dataSetId) ? String(ds.id || ds.ID || ds.datasetId || ds.dataSetId) : '';
    }

    function getRelationshipDatasetId(rel, keys) {
        if (!rel || !Array.isArray(keys)) return '';
        for (var i = 0; i < keys.length; i++) {
            var value = rel[keys[i]];
            if (value !== null && value !== undefined && String(value).trim() !== '') {
                return String(value);
            }
        }
        return '';
    }

    async function getSystemDatasetIdsForGlossary(systemId) {
        var systemIdStr = String(systemId || '');
        if (!systemIdStr) return [];

        var ids = state.linkedDatasets
            ? Array.from(state.linkedDatasets.entries())
                .filter(function (entry) { return String(entry[1] && entry[1].systemId) === systemIdStr; })
                .map(function (entry) { return String(entry[0]); })
            : [];

        if (ids.length > 0) return Array.from(new Set(ids));

        var API = window.BUDG_API_SERVICE;
        if (!API || typeof API.getSystemDatasets !== 'function') return [];

        try {
            var dsResp = await API.getSystemDatasets(systemIdStr);
            var dsList = normalizeDatasetsList(dsResp);
            return Array.from(new Set(dsList.map(getDatasetIdFromEntity).filter(Boolean)));
        } catch (e) {
            ovWarn(logPrefix + ' Failed to get system datasets for glossary scope', systemIdStr, e);
            return [];
        }
    }

    // Returns { datasetIds: Set<string>, attrLinks: Array<{datasetId, attributeId, systemId}> }.
    // Reads the field names that /api/dataset-relationships actually emits:
    // - inbound rows (backend filtered WHERE sa.Dataset_ID = ?, so OUR is source) carry the
    //   OTHER side as targetDatasetId / Target_AttributeID + systemId.
    // - outbound rows (backend filtered WHERE ta.Dataset_ID = ?, so OUR is target) carry the
    //   OTHER side as sourceDatasetId / Source_AttributeID + systemId.
    // This endpoint has NO Relation_Method IS NULL filter, so it returns interface-mediated
    // relationships too (which is what System Lineage maps actually use).
    async function collectLineageScopeFromDataset(datasetId, relCache) {
        var datasetIdStr = String(datasetId || '');
        if (!datasetIdStr) return { datasetIds: new Set(), attrLinks: [] };

        if (relCache && relCache.has(datasetIdStr)) {
            var cached = relCache.get(datasetIdStr);
            return {
                datasetIds: new Set(cached.datasetIds),
                attrLinks: cached.attrLinks.slice()
            };
        }

        var datasetIds = new Set();
        var attrLinks = [];
        try {
            var relResp = await fetch('/api/dataset-relationships/' + encodeURIComponent(datasetIdStr), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (relResp.ok) {
                var relData = await relResp.json();
                var inbound = Array.isArray(relData && relData.inbound) ? relData.inbound : [];
                var outbound = Array.isArray(relData && relData.outbound) ? relData.outbound : [];

                inbound.forEach(function (rel) {
                    var otherDs = getRelationshipDatasetId(rel, ['targetDatasetId', 'targetDataSetId']);
                    var otherAttr = getRelationshipDatasetId(rel, ['Target_AttributeID', 'targetAttributeId']);
                    var otherSys = getRelationshipDatasetId(rel, ['systemId']);
                    if (otherDs && otherDs !== datasetIdStr) {
                        datasetIds.add(otherDs);
                        if (otherAttr) attrLinks.push({ datasetId: otherDs, attributeId: otherAttr, systemId: otherSys });
                    }
                });
                outbound.forEach(function (rel) {
                    var otherDs = getRelationshipDatasetId(rel, ['sourceDatasetId', 'sourceDataSetId']);
                    var otherAttr = getRelationshipDatasetId(rel, ['Source_AttributeID', 'sourceAttributeId']);
                    var otherSys = getRelationshipDatasetId(rel, ['systemId']);
                    if (otherDs && otherDs !== datasetIdStr) {
                        datasetIds.add(otherDs);
                        if (otherAttr) attrLinks.push({ datasetId: otherDs, attributeId: otherAttr, systemId: otherSys });
                    }
                });
            }
        } catch (e) {
            ovWarn(logPrefix + ' Failed to fetch dataset relationships for glossary scope', datasetIdStr, e);
        }

        if (relCache) relCache.set(datasetIdStr, { datasetIds: new Set(datasetIds), attrLinks: attrLinks.slice() });
        return { datasetIds: datasetIds, attrLinks: attrLinks };
    }

    function buildGlossaryScopeKey(visibleSystemIds) {
        var ids = Array.from(new Set((visibleSystemIds || []).map(function (id) { return String(id || ''); }).filter(Boolean))).sort();
        return [
            String(state.systemId || ''),
            String(state.mapType || ''),
            ids.join(',')
        ].join('|');
    }

    async function ensureSystemLineageGlossaryScope(visibleSystemIds) {
        if (state.mapType === 'dataset-lineage') return null;

        var key = buildGlossaryScopeKey(visibleSystemIds);
        if (state._glossaryScopeCache && state._glossaryScopeCache.key === key) {
            return state._glossaryScopeCache.scope;
        }

        var scope = await getGlossaryScopeHelper().createSystemScope({
            currentSystemId: state.systemId,
            visibleSystemIds: visibleSystemIds,
            getDatasetsForSystem: getSystemDatasetIdsForGlossary,
            collectScopeForDataset: collectLineageScopeFromDataset
        });

        // Backward-compatible aliases used by existing code paths.
        scope.orangeDatasetIds = scope.currentDatasetIds;
        scope.allowedRelatedDatasetIds = scope.relatedDatasetIds;

        state._glossaryScopeCache = { key: key, scope: scope };
        return scope;
    }

    // Fetch overlay data for a specific system
    async function fetchOverlayDataForSystem(systemId, overlayType) {
        const API = window.BUDG_API_SERVICE;
        if (!API) return [];

        try {
            switch (overlayType) {
                case 'description':
                    const sysData = await API.getSystemById(systemId);
                    return sysData?.description ? [{ name: 'Description', value: sysData.description }] : [];

                case 'stakeholders':
                    const stakeholders = await API.getSystemStakeholders?.(systemId) || [];
                    return Array.isArray(stakeholders?.data) ? stakeholders.data : (Array.isArray(stakeholders) ? stakeholders : []);

                case 'datasets': {
                    // For system lineage:
                    //   - current (orange) system: show ALL its datasets
                    //   - other (black) systems: show only datasets that are related to the
                    //     current system's datasets via dataset relationships (matches the
                    //     same scope used for the glossary overlay).
                    // In all cases dedup by id so a dataset never appears twice in the panel.
                    const datasets = await API.getSystemDatasets?.(systemId) || [];
                    const rawList = Array.isArray(datasets?.data) ? datasets.data : (Array.isArray(datasets) ? datasets : []);
                    const systemIdStr = String(systemId);
                    const isCurrentSystem = systemIdStr === String(state.systemId);

                    const scope = state._glossaryScopeCache && state._glossaryScopeCache.scope
                        ? state._glossaryScopeCache.scope
                        : null;
                    const scopeHelper = (scope && state.mapType !== 'dataset-lineage') ? getGlossaryScopeHelper() : null;

                    const seenIds = new Set();
                    const result = [];
                    for (let i = 0; i < rawList.length; i++) {
                        const ds = rawList[i];
                        if (!ds) continue;
                        const did = getDatasetIdFromEntity(ds);
                        if (!did) continue;
                        if (seenIds.has(did)) continue;
                        if (!isCurrentSystem && scope && scopeHelper) {
                            if (!scopeHelper.isDatasetInScope(scope, did, systemIdStr)) continue;
                        }
                        seenIds.add(did);
                        result.push(ds);
                    }
                    return result;
                }

                case 'attributes':
                case 'linking-attributes':
                    const attrs = await API.getSystemAttributes?.(systemId) || [];
                    return Array.isArray(attrs?.data) ? attrs.data : (Array.isArray(attrs) ? attrs : []);

                case 'glossary': {
                    const glossaryTerms = [];
                    const seenGlossary = new Set();
                    try {
                        const systemIdStr = String(systemId);
                        const scope = state._glossaryScopeCache && state._glossaryScopeCache.scope
                            ? state._glossaryScopeCache.scope
                            : null;
                        const scopeHelper = getGlossaryScopeHelper();
                        const isCurrentSystem = scope && scope.currentSystemIds && scope.currentSystemIds.has(systemIdStr);

                        let sysDatasets = [];
                        if (scope && scope.datasetsBySystem && scope.datasetsBySystem.has(systemIdStr)) {
                            sysDatasets = (scope.datasetsBySystem.get(systemIdStr) || []).map(function (id) { return String(id); });
                        } else {
                            sysDatasets = await getSystemDatasetIdsForGlossary(systemIdStr);
                        }

                        // Restrict datasets contributing glossaries to those in scope:
                        // - current system: all its datasets
                        // - other systems: only datasets related to current's datasets
                        if (scope) {
                            sysDatasets = sysDatasets.filter(function (did) {
                                return scopeHelper.isDatasetInScope(scope, did, systemIdStr);
                            });
                        }

                        const _highlightCache = state._overlayHighlightCache;
                        const _hh = window.MapOverlayHighlight;
                        for (const did of sysDatasets) {
                            // 1) Dataset's own glossary
                            try {
                                const cachedDs = state.linkedDatasets?.get(String(did));
                                let dsGName = cachedDs?.dataset?.glossaryName;
                                let dsGId = cachedDs?.dataset?.glossaryId;
                                if (!dsGName) {
                                    const dsResp2 = await fetch(`/api/dataset/${did}`, { credentials: 'include' });
                                    if (dsResp2.ok) {
                                        const dsJson = await dsResp2.json();
                                        const ds = dsJson?.data || dsJson;
                                        dsGName = ds?.glossaryName;
                                        dsGId = ds?.glossaryId;
                                    }
                                }
                                if (dsGName && !seenGlossary.has(dsGName)) {
                                    seenGlossary.add(dsGName);
                                    glossaryTerms.push({ name: dsGName, glossary: dsGName, id: dsGId, glossaryId: dsGId, source: 'dataset' });
                                }
                                if (_highlightCache && _hh && dsGId != null) {
                                    recordGlossaryAnchor(_highlightCache, _hh.dsKey(did), dsGId);
                                }
                            } catch (e) { /* skip */ }
                            // 2) Glossary terms from attributes — for non-current systems,
                            // restrict to attributes linked via attribute relationships to current.
                            try {
                                const attrResp = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                                if (attrResp.ok) {
                                    const attrData = await attrResp.json();
                                    let attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
                                    if (scope && !isCurrentSystem) {
                                        attrs = attrs.filter(function (a) {
                                            const aid = a && (a.id != null ? a.id : (a.ID != null ? a.ID : a.attribute_id));
                                            return scopeHelper.isAttributeInScope(scope, aid, systemIdStr);
                                        });
                                    }
                                    attrs.forEach(attr => {
                                        const aid = attr && (attr.id != null ? attr.id : (attr.ID != null ? attr.ID : attr.attribute_id));
                                        if (_highlightCache && aid != null) {
                                            recordAttributeOwner(_highlightCache, aid, did);
                                        }
                                        const gName = extractGlossaryNameFromAttr(attr);
                                        const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                        if (_highlightCache && _hh && aid != null && gId != null) {
                                            recordGlossaryAnchor(_highlightCache, _hh.attrKey(aid), gId);
                                        }
                                        if (gName && !seenGlossary.has(gName)) {
                                            seenGlossary.add(gName);
                                            glossaryTerms.push({ name: gName, glossary: gName, id: gId, glossaryId: gId, source: 'attribute' });
                                        }
                                    });
                                }
                            } catch (e) { /* skip dataset */ }
                        }
                    } catch (e) { ovWarn(logPrefix + ' Error fetching glossary overlay for system', systemId, e); }
                    return await enrichGlossaryRows(glossaryTerms);
                }

                case 'processes':
                    // Fetch processes that impact this system
                    try {
                        const processResp = await fetch(`/api/process-impact/systems/${systemId}/processes`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (processResp.ok) {
                            const processData = await processResp.json();
                            return Array.isArray(processData?.data) ? processData.data : (Array.isArray(processData) ? processData : []);
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch processes for system', systemId, e); }
                    return [];

                case 'projects':
                    // Fetch projects that impact this system
                    try {
                        const projectResp = await fetch(`/api/project-impact/systems/${systemId}/projects`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (projectResp.ok) {
                            const projectData = await projectResp.json();
                            return Array.isArray(projectData?.data) ? projectData.data : (Array.isArray(projectData) ? projectData : []);
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch projects for system', systemId, e); }
                    return [];

                case 'policies':
                    // Fetch policies that impact this system
                    try {
                        const policyResp = await fetch(`/api/policy-impact/systems/${systemId}/policies`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (policyResp.ok) {
                            const policyData = await policyResp.json();
                            return Array.isArray(policyData?.data) ? policyData.data : (Array.isArray(policyData) ? policyData : []);
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch policies for system', systemId, e); }
                    return [];

                case 'business-area':
                    // Fetch business areas that impact this system
                    try {
                        const baResp = await fetch(`/api/businessarea-impact/systems/${systemId}/businessareas`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (baResp.ok) {
                            const baData = await baResp.json();
                            return Array.isArray(baData?.data) ? baData.data : (Array.isArray(baData) ? baData : []);
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch business areas for system', systemId, e); }
                    return [];

                case 'products':
                    // Fetch products related to this system
                    try {
                        const productResp = await fetch(`/api/system-impact/${systemId}/products`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (productResp.ok) {
                            const productData = await productResp.json();
                            return Array.isArray(productData?.data) ? productData.data : (Array.isArray(productData) ? productData : []);
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch products for system', systemId, e); }
                    return [];

                case 'legal-entities':
                    // Fetch legal entities related to this system
                    try {
                        const legalResp = await fetch(`/api/system-impact/${systemId}/legals`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (legalResp.ok) {
                            const legalData = await legalResp.json();
                            return Array.isArray(legalData?.data) ? legalData.data : (Array.isArray(legalData) ? legalData : []);
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch legal entities for system', systemId, e); }
                    return [];

                case 'clients':
                    // Fetch clients related to this system
                    try {
                        const clientResp = await fetch(`/api/system-impact/${systemId}/clients`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (clientResp.ok) {
                            const clientData = await clientResp.json();
                            return Array.isArray(clientData?.data) ? clientData.data : (Array.isArray(clientData) ? clientData : []);
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch clients for system', systemId, e); }
                    return [];

                case 'capabilities':
                    // Fetch capabilities that impact this system
                    try {
                        const capResp = await fetch(`/api/capability-impact/systems/${systemId}/capabilities`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (capResp.ok) {
                            const capData = await capResp.json();
                            return Array.isArray(capData?.data) ? capData.data : (Array.isArray(capData) ? capData : []);
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch capabilities for system', systemId, e); }
                    return [];

                case 'custom-fields':
                    // Fetch custom fields for this system
                    try {
                        const cfResp = await fetch(`/api/custom-fields/data?facetId=System&objectId=${systemId}`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (cfResp.ok) {
                            const cfData = await cfResp.json();
                            if (cfData.success && Array.isArray(cfData.data)) {
                                return cfData.data.filter(cf => cf.value || cf.enumId || (cf.enumIds && cf.enumIds.length > 0));
                            }
                        }
                    } catch (e) { ovWarn(logPrefix + ' Failed to fetch custom fields for system', systemId, e); }
                    return [];

                case 'data-quality':
                    // Data quality rules - try to fetch from quality API if available
                    try {
                        const dqResp = await fetch(`/api/data-quality/system/${systemId}`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (dqResp.ok) {
                            const dqData = await dqResp.json();
                            return Array.isArray(dqData?.data) ? dqData.data : (Array.isArray(dqData) ? dqData : []);
                        }
                    } catch (e) { /* Data quality API may not exist */ }
                    return [];

                case 'data-privacy':
                    // Data privacy rules/classifications - try to fetch from privacy API if available
                    try {
                        const dpResp = await fetch(`/api/data-privacy/system/${systemId}`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (dpResp.ok) {
                            const dpData = await dpResp.json();
                            return Array.isArray(dpData?.data) ? dpData.data : (Array.isArray(dpData) ? dpData : []);
                        }
                    } catch (e) { 
                        // If no dedicated API, try to get from system attributes or custom fields
                        try {
                            const sysData = await API.getSystemById(systemId);
                            const s = sysData?.data || sysData;
                            const cls = s?.privacyClassification || s?.PrivacyClassification || s?.dataPrivacy || s?.sensitivity || s?.classification;
                            if (cls) {
                                return [{
                                    name: cls,
                                    classification: cls,
                                    type: 'privacy'
                                }];
                            }
                        } catch (e2) { /* Privacy field may not exist */ }
                    }
                    return [];

                case 'geography':
                    // Geography - try system data or classification
                    try {
                        const sysDataForGeo = await API.getSystemById(systemId);
                        const g = sysDataForGeo?.data || sysDataForGeo;
                        if (g && (g.geography || g.Geography || g.region || g.Region || g.country || g.Country)) {
                            const name = g.geography || g.Geography || g.region || g.Region || g.country || g.Country || '';
                            return [{
                                name: name,
                                region: g.region || g.Region || '',
                                country: g.country || g.Country || '',
                                type: 'geography'
                            }];
                        }
                    } catch (e) { /* Geography field may not exist */ }
                    return [];

                default:
                    return [];
            }
        } catch (error) {
            ovWarn(`${logPrefix} Error fetching ${overlayType} for system ${systemId}:`, error);
            return [];
        }
    }

    // Render overlay panels near nodes
    function renderOverlayPanels(overlayType, overlayData) {
        if (!state.network || !state.canvas) return;

        // Avoid stacking duplicate viewport listeners when overlay is reopened or switched
        state.network.off('zoom pan', updateOverlayPositions);
        state.network.off('drag', 'node', updateOverlayPositions);

        const container = state.canvas;
        
        // Create overlay container if not exists
        let overlayContainer = container.querySelector('.map-overlay-container');
        if (!overlayContainer) {
            overlayContainer = document.createElement('div');
            overlayContainer.classList.add('map-overlay-container');
            container.appendChild(overlayContainer);
        }

        // Clear existing panels
        overlayContainer.innerHTML = '';

        // Render a panel for each node with data
        const isDatasetLineage = state.mapType === 'dataset-lineage';
        
        state.network.nodes().forEach(node => {
            const nodeIdRaw = node.data('id');
            const nodeId = nodeIdRaw != null ? String(nodeIdRaw) : '';
            let data;
            
            // For dataset lineage, use datasetId to lookup overlay data
            // In Dataset Lineage: overlay shows details of each dataset
            // In System Lineage: overlay shows details of each system
            if (isDatasetLineage) {
                const meta = node.data('meta') || {};
                const datasetId = meta.datasetId;
                if (datasetId) {
                    data = overlayData.get(String(datasetId));
                    ovLog(logPrefix + ' Dataset Lineage overlay lookup:', datasetId, overlayType, '->', data?.length || 0, 'items');
                } else {
                    data = null;
                }
            } else {
                // System lineage - normalize id for Map lookup (keys are String(systemId))
                data = overlayData.get(nodeId);
            }
            
            if (!data || data.length === 0) return;

            const panel = createOverlayPanel(node.data('label'), overlayType, data, nodeId);
            if (!panel) return;
            overlayContainer.appendChild(panel);
            positionOverlayPanelElement(panel, node);
        });

        // Update panel positions on zoom/pan/drag
        state.network.on('zoom pan', updateOverlayPositions);
        state.network.on('drag', 'node', updateOverlayPositions);
    }

    // Use global glossary-style overlay panel; overlay items/columns differ by map – pass this map's column defs and getItemField
    function createOverlayPanel(nodeName, overlayType, data, nodeId) {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return null;
        if (!window.MapOverlayPanel) {
            return createOverlayPanelFallback(nodeName, overlayType, arr, nodeId);
        }
        var overlayColumnDefs = [];
        if (window.OverlayColumns) {
            var allCols = window.OverlayColumns.getOverlayColumns(overlayType);
            var selectedIds = window.OverlayColumns.resolveSelectedColumnIds(state.overlayColumnsByType[overlayType], overlayType);
            overlayColumnDefs = allCols.filter(function(c) { return selectedIds && selectedIds.indexOf(c.id) !== -1; });
        }
        const panel = window.MapOverlayPanel.create(overlayType, arr, nodeId, {
            getTitle: getOverlayTitle,
            getItemText: getOverlayItemText,
            getItemId: getOverlayItemId,
            escapeHtml: window.MapRenderUtils.escapeHtml,
            getItemField: getOverlayItemField,
            overlayColumnDefs: overlayColumnDefs,
            onItemClick: function(panel, el) {
                const idx = parseInt(el.getAttribute('data-item-index'), 10);
                const list = panel._overlayData;
                const item = (list && list[idx] != null) ? list[idx] : null;
                if (item != null) highlightOverlayItem(panel._overlayType, item, panel.getAttribute('data-node-id'));
            }
        });
        return panel;
    }

    function createOverlayPanelFallback(nodeName, overlayType, data, nodeId) {
        const panel = document.createElement('div');
        panel.className = 'map-node-overlay-panel';
        panel.setAttribute('data-node-id', nodeId);
        panel._overlayData = data;
        panel._overlayType = overlayType;
        const body = document.createElement('div');
        body.className = 'map-node-overlay-body';
        body.textContent = 'Overlay not available.';
        panel.appendChild(body);
        return panel;
    }

    // Get display title for overlay type
    function getOverlayTitle(overlayType, nodeId) {
        if (overlayType === 'description' && nodeId != null && String(nodeId).indexOf('dataset-') === 0) {
            return 'Definition';
        }
        const titles = {
            'description': 'Description',
            'stakeholders': 'Stakeholders',
            'datasets': 'Data Sets',
            'attributes': 'Attributes',
            'linking-attributes': 'Linking Attributes',
            'glossary': 'Glossaries',
            'processes': 'Processes',
            'projects': 'Projects',
            'policies': 'Policies',
            'business-area': 'Business Area',
            'products': 'Products',
            'legal-entities': 'Legal Entities',
            'clients': 'Clients',
            'capabilities': 'Capabilities',
            'custom-fields': 'Custom Fields',
            'data-quality': 'Data Quality',
            'geography': 'Geography'
        };
        return titles[overlayType] || overlayType;
    }

    // Get display text for an overlay item
    function getOverlayItemText(overlayType, item) {
        if (!item) return '';
        
        switch (overlayType) {
            case 'description':
                return item.value || item.description || '';
            case 'stakeholders':
                const role = item.roleName || item.RoleName || item.role || item.Role || '';
                const name = item.personName || item.PersonName || item.name || item.Name || '';
                return name ? (role ? `${name} (${role})` : name) : (role || '');
            case 'datasets':
                return item.primaryName || item.name || item.shortName || '';
            case 'attributes':
            case 'linking-attributes':
                // API returns field as "Name attribute" (with space)
                return item.name || item['Name attribute'] || item.attributeName || item.primaryName || item.PrimaryName || item.Name || '';
            case 'glossary': {
                const gName = item.glossary || item.name || '';
                const srcLabel = item.source === 'dataset' ? 'Dataset Glossary'
                   : item.source === 'attribute' ? 'Attribute Glossary'
                   : item.source === 'related-dataset' ? 'Related Dataset Glossary'
                   : item.source === 'related-attribute' ? 'Related Attribute Glossary' : '';
                return srcLabel ? `${gName} (${srcLabel})` : gName;
            }
            case 'processes':
                return item.processName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'projects':
                return item.projectName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'policies':
                return item.policyName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'business-area':
                return item.businessAreaName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'products':
                return item.productName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'legal-entities':
                return item.legalEntityName || item.legalShortName || item.LegalShortName ||
                    item.legalLongName || item.LegalLongName || item.longName || item.longname ||
                    item.name || item.Name || '';
            case 'clients':
                return item.clientName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'capabilities':
                return item.capabilityName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'custom-fields':
                // Custom fields have fieldName and value
                const fieldName = item.fieldName || item.name || 'Field';
                const fieldValue = item.displayValue || item.value || item.enumName || '';
                return fieldValue ? `${fieldName}: ${fieldValue}` : fieldName;
            case 'data-quality':
                return item.ruleName || item.name || '';
            case 'data-privacy':
                return item.privacyClassification || item.classification || item.name || item.value || '';
            case 'geography':
                return item.name || item.region || item.country || '';
            default:
                return item.name || item.Name || item.primaryName || item.PrimaryName || item.toString();
        }
    }

    // Get value for a specific overlay column/field (for table view)
    function getOverlayItemField(overlayType, item, fieldId) {
        if (!item) return '';
        const v = (x) => (x != null && x !== '') ? String(x) : '';
        function fieldDefault(it, fid) {
            const d = it[fid];
            if (d !== undefined && d !== null && d !== '') return v(d);
            if (typeof window !== 'undefined' && window.MapOverlayFieldPick && typeof window.MapOverlayFieldPick.pick === 'function') {
                return window.MapOverlayFieldPick.pick(it, fid);
            }
            return '';
        }
        /** Match list-style overlay text: APIs use *Name, PrimaryName, PascalCase, etc. */
        function entityNameCell() {
            return v(
                item.processName || item.ProcessName ||
                item.projectName || item.ProjectName ||
                item.policyName || item.PolicyName ||
                item.businessAreaName || item.BusinessAreaName ||
                item.productName || item.ProductName ||
                item.legalEntityName || item.LegalEntityName ||
                item.legalShortName || item.LegalShortName ||
                item.legalLongName || item.LegalLongName ||
                item.clientName || item.ClientName ||
                item.capabilityName || item.CapabilityName ||
                item.systemName || item.SystemName ||
                item.name || item.Name || item.primaryName || item.PrimaryName ||
                item.glossaryName || item.GlossaryName || item.ruleName || item.RuleName ||
                item.longName || item.longname ||
                item.region || item.country
            );
        }
        switch (overlayType) {
            case 'glossary':
                switch (fieldId) {
                    case 'name': return v(item.glossary || item.name || item.primaryName);
                    case 'source': return item.source === 'dataset' ? 'Dataset Glossary'
                    : item.source === 'attribute' ? 'Attribute Glossary'
                    : item.source === 'related-dataset' ? 'Related Dataset Glossary'
                    : item.source === 'related-attribute' ? 'Related Attribute Glossary' : '';
                    case 'aliasNames':
                        if (Array.isArray(item.aliases) && item.aliases.length) return v(item.aliases.join(', '));
                        return v(item.aliasNames || item.aliases || item.alias);
                    case 'parentName': return v(item.parentName || item.ParentName || item.parent?.name);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    case 'securityClassification': return v(item.securityClassification || item.classification || item.securityName || item.SecurityName);
                    default: return fieldDefault(item, fieldId);
                }
            case 'description':
                return fieldId === 'value'
                    ? v(item.value || item.definition || item.Definition || item.description || item.Description)
                    : fieldDefault(item, fieldId);
            case 'datasets':
                switch (fieldId) {
                    case 'name': return v(item.primaryName || item.PrimaryName || item.name || item.shortName);
                    case 'refNumber': return v(item.refNumber || item.ref || item.RefNumber || item.Ref);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    default: return fieldDefault(item, fieldId);
                }
            case 'attributes':
            case 'linking-attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName || item.PrimaryName || item.Name);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type);
                    case 'glossary': return v(item.glossaryName || item.glossary || item['Glossary Name attribute']);
                    case 'refNumber': return v(item.refNumber || item.ref || item.RefNumber || item.Ref);
                    case 'direction': return v(item.direction || item.Direction);
                    case 'relatedDataset': return v(item.relatedDataset || item.related_dataset);
                    case 'relatedAttribute': return v(item.relatedAttribute || item.related_attribute);
                    default: return fieldDefault(item, fieldId);
                }
            case 'stakeholders':
                switch (fieldId) {
                    case 'name': return v(item.personName || item.PersonName || item.name || item.Name);
                    case 'role': return v(item.roleName || item.RoleName || item.role || item.Role);
                    case 'accepted':
                    case 'orgUnit': {
                        const norm = window.MapOverlayPanel && typeof window.MapOverlayPanel.normalizeStakeholderItem === 'function'
                            ? window.MapOverlayPanel.normalizeStakeholderItem(item)
                            : { accepted: '', orgUnit: '' };
                        return fieldId === 'accepted' ? v(norm.accepted) : v(norm.orgUnit);
                    }
                    default: return fieldDefault(item, fieldId);
                }
            case 'custom-fields':
                switch (fieldId) {
                    case 'metadataId': return v(item.metadataId != null ? item.metadataId : item.Metadata_ID);
                    case 'enumId':
                        if (item.enumId !== undefined && item.enumId !== null && item.enumId !== '') return v(item.enumId);
                        if (item.enumIds && item.enumIds.length) return v(item.enumIds.join(', '));
                        return '';
                    case 'value': return v(item.value != null && item.value !== '' ? item.value : (item.displayValue != null ? item.displayValue : ''));
                    default: return fieldDefault(item, fieldId);
                }
            case 'processes':
            case 'projects':
            case 'policies':
            case 'business-area':
            case 'products':
            case 'legal-entities':
            case 'clients':
            case 'capabilities':
            case 'systems':
            case 'data-quality':
            case 'data-privacy':
            case 'geography':
                switch (fieldId) {
                    case 'name': return entityNameCell();
                    case 'refNumber': return v(
                        item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref ||
                        item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef ||
                        item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef ||
                        item.productRefNumber || item.ProductRefNumber ||
                        item.policyRefNumber || item.PolicyRefNumber ||
                        item.capabilityRefNumber || item.CapabilityRefNumber ||
                        item.datasetRefNumber || item.DatasetRefNumber ||
                        item.attributeRefNumber || item.AttributeRefNumber ||
                        item.glossaryRefNumber || item.GlossaryRefNumber ||
                        item.interfaceRefNumber || item.InterfaceRefNumber ||
                        item.systemRef || item.SystemRef ||
                        item.regulationRefNumber || item.RegulationRefNumber ||
                        item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber ||
                        item.businessAreaReference || item.BusinessAreaReference ||
                        item.clientReference || item.ClientReference ||
                        item.legalReference || item.LegalReference ||
                        item.sourceProcessRef || item.targetProcessRef
                    );
                    case 'type': return v(
                        item.typeName || item.type || item.TypeName || item.Type ||
                        item.policyTypeName || item.PolicyTypeName ||
                        item.productTypeName || item.ProductTypeName ||
                        item.relationTypeName || item.RelationTypeName
                    );
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    case 'status': return v(
                        item.statusName || item.StatusName || item.status || item.Status ||
                        item.projectStatusName || item.ProjectStatusName ||
                        item.policyStatusName || item.PolicyStatusName
                    );
                    case 'ruleName': return v(item.ruleName || item.RuleName);
                    case 'rating': return v(item.rating || item.qualityRating);
                    case 'classification': return v(item.classification || item.privacyClassification);
                    case 'region': return v(item.region || item.Region);
                    case 'country': return v(item.country || item.Country);
                    default: return fieldDefault(item, fieldId);
                }
            default: {
                const fb = fieldDefault(item, fieldId);
                if (fb) return fb;
                return v(item.name || item.primaryName);
            }
        }
    }

    // Get item ID for an overlay item (for ID-based highlighting)
    function getOverlayItemId(overlayType, item) {
        if (!item) return null;
        
        // Try common ID field names first
        if (item.id !== undefined && item.id !== null) return item.id;
        if (item.ID !== undefined && item.ID !== null) return item.ID;
        
        // Type-specific ID fields
        switch (overlayType) {
            case 'glossary':
                return item.glossaryId || item.glossaryID || null;
            case 'processes':
                return item.processId || item.processID || null;
            case 'projects':
                return item.projectId || item.projectID || null;
            case 'policies':
                return item.policyId || item.policyID || null;
            case 'datasets':
                // getSystemDatasets returns rows with `id` (or `ID`); some other paths may
                // emit `datasetId` / `datasetID`. Accept all so dataset rows always carry
                // data-item-id, which cross-panel highlighting depends on.
                return item.datasetId || item.datasetID || item.id || item.ID || item.dataSetId || null;
            case 'products':
                return item.productId || item.productID || null;
            case 'legal-entities':
                return item.legalEntityId || item.legalEntityID || null;
            case 'clients':
                return item.clientId || item.clientID || null;
            case 'capabilities':
                return item.capabilityId || item.capabilityID || null;
            case 'business-area':
                return item.businessAreaId || item.businessAreaID || null;
            case 'stakeholders':
                return item.personId || item.personID || item.peopleId || item.peopleID || null;
            case 'data-quality':
                return item.ruleId || item.ruleID || null;
            case 'geography':
                return item.geographyId || item.geographyID || null;
            default:
                return null;
        }
    }

    function clearOverlayHighlights() {
        var overlayContainer = state.canvas?.querySelector('.map-overlay-container');
        if (overlayContainer) {
            overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(function(row) {
                row.classList.remove('highlighted-source', 'highlighted-related');
            });
        }
        if (state.network) {
            state.network.elements().removeClass('overlay-highlight-source-node overlay-highlight-related-node overlay-highlight-edge');
        }
        state._highlightedOverlayKey = null;
    }

    function highlightOverlayItem(overlayType, item, sourceNodeId) {
        var overlayContainer = state.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer) return;

        var clickedItemId = getOverlayItemId(overlayType, item);
        var itemValue = getOverlayItemText(overlayType, item);
        var key = overlayType + '|' + sourceNodeId + '|' + (clickedItemId != null ? clickedItemId : itemValue);

        // Toggle off if same item clicked again
        if (state._highlightedOverlayKey === key) {
            clearOverlayHighlights();
            return;
        }

        // Remove existing highlights
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(function(el) {
            el.classList.remove('highlighted-source', 'highlighted-related');
        });

        // For attributes/linking-attributes, use relationship-based highlighting
        if ((overlayType === 'attributes' || overlayType === 'linking-attributes') && item && (item.id != null || item.ID != null)) {
            var attrId = item.id != null ? item.id : item.ID;
            highlightAttributeRelationships(overlayContainer, attrId, sourceNodeId);
            state._highlightedOverlayKey = key;
            return;
        }

        // Cross-panel transitive highlighting via shared helpers.
        //   - datasets : BFS over the bidirectional dataset relationship graph.
        //     Example: Core.X relates to Black1.B and Black2.C. Clicking B should
        //     highlight X (direct) AND C (indirect via X).
        //   - glossary : BFS over the unified anchor graph. Clicking a glossary
        //     row also lights up every other glossary that sits on a dataset or
        //     attribute reachable through dataset+attribute relationships.
        var helper = window.MapOverlayHighlight;
        var hcache = state._overlayHighlightCache;
        var datasetRelatedIds = null;
        var glossaryRelatedIds = null;

        if (overlayType === 'datasets' && clickedItemId && helper && hcache && hcache.datasetGraph) {
            datasetRelatedIds = helper.findAllRelatedDatasets(String(clickedItemId), hcache.datasetGraph);
        }
        if (overlayType === 'glossary' && clickedItemId && helper && hcache && hcache.anchorGraph) {
            glossaryRelatedIds = helper.findAllRelatedGlossaries(
                String(clickedItemId),
                hcache.glossaryToAnchors,
                hcache.anchorGraph,
                hcache.anchorToGlossaries
            );
        }

        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(function(el) {
            var panel = el.closest('.map-node-overlay-panel');
            var isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            var elItemId = el.dataset.itemId ? String(el.dataset.itemId) : null;
            var shouldHighlight = false;

            if (clickedItemId) {
                if (elItemId && elItemId === String(clickedItemId)) shouldHighlight = true;
            }
            if (!shouldHighlight && el.dataset.overlayValue === itemValue) shouldHighlight = true;

            if (shouldHighlight) {
                if (isSource) el.classList.add('highlighted-source');
                else el.classList.add('highlighted-related');
                return;
            }

            if (datasetRelatedIds && elItemId && datasetRelatedIds.has(elItemId)) {
                el.classList.add('highlighted-related');
                return;
            }
            if (glossaryRelatedIds && elItemId && glossaryRelatedIds.has(elItemId)) {
                el.classList.add('highlighted-related');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
        state._highlightedOverlayKey = key;
    }

    function applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId) {
        var network = state.network;
        if (!network) return;
        network.elements().removeClass('overlay-highlight-source-node overlay-highlight-related-node overlay-highlight-edge');
        var relatedNodeIds = new Set();
        overlayContainer.querySelectorAll('.map-node-overlay-item.highlighted-source, .map-node-overlay-item.highlighted-related').forEach(function(el) {
            var panel = el.closest('.map-node-overlay-panel');
            var nodeId = panel?.getAttribute('data-node-id');
            if (nodeId) relatedNodeIds.add(nodeId);
        });
        relatedNodeIds.forEach(function(nodeId) {
            var node = network.getElementById(nodeId);
            if (node.length) node.addClass(nodeId === sourceNodeId ? 'overlay-highlight-source-node' : 'overlay-highlight-related-node');
        });
        network.edges().forEach(function(edge) {
            var src = edge.source().id();
            var tgt = edge.target().id();
            if (relatedNodeIds.has(src) && relatedNodeIds.has(tgt)) edge.addClass('overlay-highlight-edge');
        });
    }

    // Highlight attributes based on relationships (including transitive)
    // Example: Att3 > Q_Att2, Att3 > Q_Att3, Att2 > Q_Att2
    // When clicking Att3: highlights Q_Att2, Q_Att3, and also Q_Att2 (via Att2 transitively)
    function highlightAttributeRelationships(overlayContainer, clickedAttributeId, sourceNodeId) {
        const relationships = state.attributeRelationships || [];
        
        // Normalize clicked attribute ID to string for comparison
        const clickedAttrIdStr = String(clickedAttributeId);
        
        ovLog(logPrefix + ' Highlighting attribute relationships for:', clickedAttrIdStr);
        ovLog(logPrefix + ' Total relationships available:', relationships.length);
        ovLog(logPrefix + ' Relationships with attribute IDs:', relationships.filter(r => r.sourceAttributeId || r.targetAttributeId).length);
        
        // Find all directly and transitively related attribute IDs (shared helper).
        const relatedAttributeIds = (window.MapOverlayHighlight && typeof window.MapOverlayHighlight.findAllRelatedAttributes === 'function')
            ? window.MapOverlayHighlight.findAllRelatedAttributes(clickedAttrIdStr, relationships)
            : new Set();
        
        ovLog(logPrefix + ' Clicked attribute:', clickedAttrIdStr, 'Related attributes:', Array.from(relatedAttributeIds));

        // Count overlay items with attribute IDs
        let totalItems = 0;
        let highlightedSource = 0;
        let highlightedRelated = 0;
        
        // Highlight the clicked attribute (source - dark green)
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(el => {
            // Remove any existing highlights first
            el.classList.remove('highlighted-source', 'highlighted-related');
            
            const attrIdStr = (el.dataset.attributeId || el.dataset.itemId) ? String(el.dataset.attributeId || el.dataset.itemId) : null;
            const attrName = el.textContent?.trim() || '';
            totalItems++;
            
            if (!attrIdStr) {
                ovLog(logPrefix + ' Item without attribute ID:', attrName);
                return;
            }
            
            // Check if this is the clicked attribute
            if (attrIdStr === clickedAttrIdStr) {
                el.classList.add('highlighted-source');
                highlightedSource++;
                ovLog(logPrefix + ' ✓ Highlighted SOURCE attribute:', attrIdStr, 'Name:', attrName);
            } 
            // Check if this attribute is related to the clicked one
            else if (relatedAttributeIds.has(attrIdStr)) {
                el.classList.add('highlighted-related');
                highlightedRelated++;
                ovLog(logPrefix + ' ✓ Highlighted RELATED attribute:', attrIdStr, 'Name:', attrName);
            } else {
                ovLog(logPrefix + ' - Not highlighted:', attrIdStr, 'Name:', attrName, '(not in related set)');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
        ovLog(logPrefix + ' Highlighting complete. Total items:', totalItems, 'Source highlighted:', highlightedSource, 'Related highlighted:', highlightedRelated);
    }

    // BFS for related attributes / datasets / glossaries lives in
    // window.MapOverlayHighlight (see overlay-highlight-helpers.js). The local
    // copies that used to live here have been removed in favour of the shared
    // implementation; both the attribute and dataset highlight paths now go
    // through the helper.

    // Update overlay panel positions on zoom/pan; hide panels whose node is filtered out or off-screen
    function updateOverlayPositions() {
        const overlayContainer = state.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer || !state.network) return;
        const cw = overlayContainer.clientWidth || 800;
        const ch = overlayContainer.clientHeight || 600;
        const buf = 150;

        overlayContainer.querySelectorAll('.map-node-overlay-panel').forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = state.network.getElementById(nodeId);
            
            if (node && node.length) {
                const isVisible = node.style('display') !== 'none';
                if (!isVisible) { panel.style.display = 'none'; return; }
                const pos = node.renderedPosition();
                const offScreen = pos.x < -buf || pos.x > cw + buf || pos.y < -buf || pos.y > ch + buf;
                panel.style.display = offScreen ? 'none' : '';
                if (!offScreen) positionOverlayPanelElement(panel, node);
            } else {
                panel.style.display = 'none';
            }
        });
    }

    // Clear all overlay panels
    function clearOverlayPanels() {
        state._highlightedOverlayKey = null;
        if (state.network) {
            state.network.elements().removeClass('overlay-highlight-source-node overlay-highlight-related-node overlay-highlight-edge');
        }
        var overlayContainer = state.canvas?.querySelector('.map-overlay-container');
        if (overlayContainer) {
            overlayContainer.innerHTML = '';
        }
        state.overlayData = null;
        
        // Remove zoom/pan/drag listeners
        if (state.network) {
            state.network.off('zoom pan', updateOverlayPositions);
            state.network.off('drag', 'node', updateOverlayPositions);
        }
    }

    // Position an overlay panel above the node icon
    function positionOverlayPanelElement(panel, node) {
        const overlayContainer = state.canvas?.querySelector('.map-overlay-container');
        const network = state.network;
        if (!overlayContainer || !node || !network) return;

        const position = node.renderedPosition();
        const bb = node.boundingBox();
        const zoom = network.zoom();
        const panelHeight = panel.offsetHeight || 180;
        const panelWidth = panel.offsetWidth || 220;
        const margin = 12;
        const renderedNodeH = (bb.h || 60) * zoom;
        const panelScale = Math.max(0.75, Math.min(1.6, zoom));
        const scaledPanelWidth = panelWidth * panelScale;
        const scaledPanelHeight = panelHeight * panelScale;

        let left = position.x - scaledPanelWidth / 2;
        let top = position.y - renderedNodeH / 2 - scaledPanelHeight - margin;
        const belowTop = position.y + renderedNodeH / 2 + margin;
        const maxLeft = Math.max(0, (overlayContainer.clientWidth || 0) - scaledPanelWidth);
        const maxTop = Math.max(0, (overlayContainer.clientHeight || 0) - scaledPanelHeight);
        const minTop = 8;
        left = Math.max(0, Math.min(maxLeft, left));
        if (top < minTop) top = belowTop;
        top = maxTop < minTop ? Math.max(0, maxTop) : Math.max(minTop, Math.min(maxTop, top));

        panel.style.left = `${left}px`;
        panel.style.top = `${top}px`;
        panel.style.transform = `translate(0, 0) scale(${panelScale})`;
        panel.style.transformOrigin = 'top left';
    }
        return {
            setOverlay: setOverlay,
            setOverlayColumns: setOverlayColumns,
            getOverlayColumns: getOverlayColumns,
            loadOverlayData: loadOverlayData,
            clearOverlayPanels: clearOverlayPanels,
            updateOverlayPositions: updateOverlayPositions,
            highlightOverlayItem: highlightOverlayItem,
            clearOverlayHighlights: clearOverlayHighlights
        };
    };
})();