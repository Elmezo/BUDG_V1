/**
 * overlay-highlight-helpers.js — shared cross-panel transitive highlighting helpers
 * for map overlays (datasets, attributes/linking-attributes, glossary).
 *
 * Exposes window.MapOverlayHighlight with:
 *   - findAllRelatedDatasets(clickedDatasetId, datasetGraph)
 *   - findAllRelatedAttributes(clickedAttributeId, relationships)
 *   - findAllRelatedGlossaries(clickedGlossaryId, glossaryToAnchors, anchorGraph, anchorToGlossaries)
 *   - buildAnchorGraph(datasetGraph, attributeRelationships, attributeOwnerMap)
 *   - dsKey(id) / attrKey(id) — consistent prefixes for the unified anchor graph.
 *   - addBidirectionalEdge(graph, a, b)
 *   - normId(value)
 *
 * Loaded after map-ui.js on every facet page that has overlays. Side effects: writes
 * to window.MapOverlayHighlight only.
 */
(function () {
    'use strict';

    function normId(value) {
        if (value === null || value === undefined) return '';
        return String(value).trim();
    }

    function dsKey(id) {
        var nid = normId(id);
        return nid ? 'ds:' + nid : '';
    }

    function attrKey(id) {
        var nid = normId(id);
        return nid ? 'attr:' + nid : '';
    }

    /** Add a -> b and b -> a to a Map<string, Set<string>> graph (skips self-edges). */
    function addBidirectionalEdge(graph, a, b) {
        if (!graph || typeof graph.set !== 'function') return;
        var aStr = normId(a);
        var bStr = normId(b);
        if (!aStr || !bStr || aStr === bStr) return;
        if (!graph.has(aStr)) graph.set(aStr, new Set());
        if (!graph.has(bStr)) graph.set(bStr, new Set());
        graph.get(aStr).add(bStr);
        graph.get(bStr).add(aStr);
    }

    /**
     * BFS over a bidirectional Map<string, Set<string>> graph starting from clickedId.
     * Returns the set of ids reachable from clickedId, EXCLUDING clickedId itself.
     */
    function bfsRelatedFromGraph(clickedId, graph) {
        var startId = normId(clickedId);
        if (!startId || !graph || typeof graph.get !== 'function') return new Set();

        var related = new Set();
        var visited = new Set();
        var queue = [startId];

        while (queue.length > 0) {
            var currentId = String(queue.shift());
            if (visited.has(currentId)) continue;
            visited.add(currentId);

            var neighbors = graph.get(currentId);
            if (!neighbors || typeof neighbors.forEach !== 'function') continue;
            neighbors.forEach(function (nid) {
                var nidStr = String(nid);
                if (!nidStr || visited.has(nidStr)) return;
                related.add(nidStr);
                queue.push(nidStr);
            });
        }

        related.delete(startId);
        return related;
    }

    /**
     * BFS on a bidirectional dataset graph (Map<datasetId, Set<datasetId>>).
     * Use to find all datasets directly OR transitively related to clickedDatasetId.
     */
    function findAllRelatedDatasets(clickedDatasetId, datasetGraph) {
        return bfsRelatedFromGraph(clickedDatasetId, datasetGraph);
    }

    /**
     * BFS on a list of attribute relationships [{sourceAttributeId, targetAttributeId}].
     * Returns the set of attribute ids reachable from clickedAttributeId, EXCLUDING the clicked one.
     * Bidirectional + transitive traversal: matches the existing per-map implementations.
     */
    function findAllRelatedAttributes(clickedAttributeId, relationships) {
        var startId = normId(clickedAttributeId);
        if (!startId) return new Set();

        var related = new Set();
        var visited = new Set();
        var queue = [startId];

        var normalizedRels = (relationships || [])
            .map(function (rel) {
                return {
                    source: normId(rel && rel.sourceAttributeId),
                    target: normId(rel && rel.targetAttributeId)
                };
            })
            .filter(function (rel) {
                return rel.source && rel.target && rel.source !== 'undefined' && rel.target !== 'undefined';
            });

        while (queue.length > 0) {
            var currentId = String(queue.shift());
            if (visited.has(currentId)) continue;
            visited.add(currentId);

            normalizedRels.forEach(function (rel) {
                if (rel.source === currentId && rel.target && !visited.has(rel.target)) {
                    related.add(rel.target);
                    queue.push(rel.target);
                }
            });
            normalizedRels.forEach(function (rel) {
                if (rel.target === currentId && rel.source && !visited.has(rel.source)) {
                    related.add(rel.source);
                    queue.push(rel.source);
                    normalizedRels.forEach(function (rel2) {
                        if (rel2.source === rel.source && rel2.target && rel2.target !== currentId && !visited.has(rel2.target)) {
                            related.add(rel2.target);
                            queue.push(rel2.target);
                        }
                    });
                }
            });
        }

        related.delete(startId);
        return related;
    }

    /**
     * Build a unified anchor graph from a dataset graph + attribute relationship list +
     * an attributeId -> datasetId ownership map. Anchors use prefixed keys: "ds:<id>" for
     * datasets and "attr:<id>" for attributes. Edges are bidirectional and include:
     *   - dataset <-> dataset (from datasetGraph)
     *   - attribute <-> attribute (from attributeRelationships)
     *   - attribute <-> owning dataset (from attributeOwnerMap)
     *
     * @param {Map<string, Set<string>>} datasetGraph
     * @param {Array<{sourceAttributeId, targetAttributeId}>} attributeRelationships
     * @param {Map<string, string>} attributeOwnerMap  attributeId -> datasetId
     * @returns {Map<string, Set<string>>} anchor graph keyed by ds:<id> / attr:<id>
     */
    function buildAnchorGraph(datasetGraph, attributeRelationships, attributeOwnerMap) {
        var graph = new Map();

        if (datasetGraph && typeof datasetGraph.forEach === 'function') {
            datasetGraph.forEach(function (neighbors, did) {
                if (!neighbors || typeof neighbors.forEach !== 'function') return;
                neighbors.forEach(function (nid) {
                    addBidirectionalEdge(graph, dsKey(did), dsKey(nid));
                });
            });
        }

        (attributeRelationships || []).forEach(function (rel) {
            if (!rel) return;
            var a = normId(rel.sourceAttributeId);
            var b = normId(rel.targetAttributeId);
            if (a && b) addBidirectionalEdge(graph, attrKey(a), attrKey(b));
        });

        if (attributeOwnerMap && typeof attributeOwnerMap.forEach === 'function') {
            attributeOwnerMap.forEach(function (datasetId, attributeId) {
                addBidirectionalEdge(graph, attrKey(attributeId), dsKey(datasetId));
            });
        }

        return graph;
    }

    /**
     * BFS over the anchor graph starting from every anchor (dataset or attribute) that
     * carries the clicked glossary, then collect every glossary attached to any reached
     * anchor. Returns a Set of glossary ids EXCLUDING the clicked glossary id.
     *
     * @param {string|number} clickedGlossaryId
     * @param {Map<string, Set<string>>} glossaryToAnchors  glossaryId -> Set of anchor keys (ds:<id>/attr:<id>)
     * @param {Map<string, Set<string>>} anchorGraph        anchor key -> Set of anchor keys
     * @param {Map<string, Set<string>>} anchorToGlossaries anchor key -> Set of glossary ids carried at that anchor
     */
    function findAllRelatedGlossaries(clickedGlossaryId, glossaryToAnchors, anchorGraph, anchorToGlossaries) {
        var clickedId = normId(clickedGlossaryId);
        if (!clickedId || !glossaryToAnchors || !anchorGraph || !anchorToGlossaries) return new Set();

        var seedAnchors = glossaryToAnchors.get(clickedId);
        if (!seedAnchors || typeof seedAnchors.forEach !== 'function' || seedAnchors.size === 0) return new Set();

        var visitedAnchors = new Set();
        var queue = [];
        seedAnchors.forEach(function (a) {
            var aStr = String(a);
            if (aStr) queue.push(aStr);
        });

        while (queue.length > 0) {
            var current = String(queue.shift());
            if (visitedAnchors.has(current)) continue;
            visitedAnchors.add(current);

            var neighbors = anchorGraph.get(current);
            if (!neighbors || typeof neighbors.forEach !== 'function') continue;
            neighbors.forEach(function (n) {
                var nStr = String(n);
                if (nStr && !visitedAnchors.has(nStr)) queue.push(nStr);
            });
        }

        var related = new Set();
        visitedAnchors.forEach(function (anchorKey) {
            var glossariesHere = anchorToGlossaries.get(anchorKey);
            if (!glossariesHere || typeof glossariesHere.forEach !== 'function') return;
            glossariesHere.forEach(function (gid) {
                var gStr = normId(gid);
                if (gStr && gStr !== clickedId) related.add(gStr);
            });
        });

        return related;
    }

    if (typeof window !== 'undefined') {
        window.MapOverlayHighlight = {
            __version: 1,
            normId: normId,
            dsKey: dsKey,
            attrKey: attrKey,
            addBidirectionalEdge: addBidirectionalEdge,
            findAllRelatedDatasets: findAllRelatedDatasets,
            findAllRelatedAttributes: findAllRelatedAttributes,
            findAllRelatedGlossaries: findAllRelatedGlossaries,
            buildAnchorGraph: buildAnchorGraph
        };
    }
})();
