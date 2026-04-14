// API and preload

/**
 * Map Object Type variations to module names
 */
function normalizeObjectTypeToModule(objectType) {
    if (!objectType) return null;
    const normalized = objectType.toLowerCase().trim();
    const mapping = {
        'dataset': 'dataset',
        'data set': 'dataset',
        'data sets': 'dataset',
        'system': 'system',
        'systems': 'system',
        'glossary': 'glossary',
        'process': 'process',
        'processes': 'process',
        'project': 'project',
        'projects': 'project',
        'product': 'product',
        'products': 'product',
        'attribute': 'attribute',
        'attributes': 'attribute',
        'policy': 'policy',
        'policies': 'policy',
        'interface': 'interface',
        'interfaces': 'interface',
        'business area': 'business-area',
        'business-area': 'business-area',
        'businessarea': 'business-area',
        'legal entity': 'legal-entity',
        'legal-entity': 'legal-entity',
        'legalentity': 'legal-entity',
        'client': 'client',
        'clients': 'client',
        'committee': 'committee',
        'committees': 'committee',
        'capability': 'capability',
        'capabilities': 'capability',
        'geography': 'geography',
        'geographies': 'geography',
        'regulation': 'regulation',
        'regulations': 'regulation',
        'regulator': 'regulator',
        'regulators': 'regulator',
        'regulatory theme': 'regulatory-theme',
        'regulatory-theme': 'regulatory-theme',
        'regulatorytheme': 'regulatory-theme',
        'people': 'people',
        'role': 'role',
        'roles': 'role'
    };
    return mapping[normalized] || null;
}

/**
 * Transform change request data to match required column names
 */
function transformChangeRequestData(data) {
    if (!Array.isArray(data)) return data;
    
    // Helper function to format date
    function formatDate(dateValue) {
        if (dateValue === null || dateValue === undefined || dateValue === '') return '';
        const localeOpts = {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        };
        if (typeof dateValue === 'number' && !isNaN(dateValue)) {
            const date = new Date(dateValue);
            return isNaN(date.getTime()) ? String(dateValue) : date.toLocaleString('en-US', localeOpts);
        }
        if (typeof dateValue === 'string') {
            if (dateValue.includes('T')) {
                const date = new Date(dateValue);
                return isNaN(date.getTime()) ? dateValue : date.toLocaleString('en-US', localeOpts);
            }
            // Gson LocalDateTime from Unison API: "yyyy-MM-dd HH:mm:ss"
            if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(dateValue.trim())) {
                const date = new Date(dateValue.replace(' ', 'T'));
                return isNaN(date.getTime()) ? dateValue : date.toLocaleString('en-US', localeOpts);
            }
            return dateValue;
        }
        if (dateValue instanceof Date) {
            return dateValue.toLocaleString('en-US', localeOpts);
        }
        return String(dateValue);
    }
    
    return data.map(cr => {
        // Create a normalized object with transformed field names
        const normalized = { ...cr };
        
        // Ensure ID field exists (and Id for BUDG-style columns)
        const id = normalized.id || normalized.ID;
        normalized.ID = id;
        normalized.id = id;
        normalized.Id = id;
        
        // Transform field names to match required columns (Unison rows use Type/Status/… from Java; REST may use typeName)
        normalized.Subject = normalized.Subject || normalized.subject || normalized.primaryName || normalized.PrimaryName || '';
        normalized.Summary = normalized.summary || normalized.Summary || '';
        normalized.Type = normalized.typeName || normalized.TypeName || normalized.Type || normalized.type || '';
        normalized.Status = normalized.statusName || normalized.StatusName || normalized.Status || normalized.status || '';
        normalized.Severity = normalized.severityName || normalized.SeverityName || normalized.Severity || normalized.severity || '';
        normalized.Urgency = normalized.urgencyName || normalized.UrgencyName || normalized.Urgency || normalized.urgency || '';
        
        // Extract Object Type and Object from reference
        // Reference format: "Dataset 123" or "System 456" or "Data Set 123"
        // Preserve backend-resolved Object name (loadChangeRequestRows) — do not replace with ID only.
        const reference = normalized.reference || normalized.Reference || '';
        const preObject = normalized.Object ?? normalized.object;
        const preObjectType = normalized['Object Type'] ?? normalized.object_type ?? normalized['object_type'];
        if (reference) {
            // Try to parse reference - it might be "FacetType ID" or just contain the ID at the end
            const match = reference.match(/^(.+?)\s+(\d+)$/);
            if (match) {
                normalized['Object Type'] = (preObjectType && String(preObjectType).trim()) ? String(preObjectType).trim() : match[1].trim();
                const objectId = parseInt(match[2], 10);
                normalized['Object_ID'] = objectId;
                normalized['Object_Module'] = normalizeObjectTypeToModule(normalized['Object Type']);
                const preStr = preObject != null && preObject !== undefined ? String(preObject).trim() : '';
                const isIdOnly = preStr === String(objectId);
                if (preStr && !isIdOnly) {
                    normalized.Object = preStr;
                } else {
                    normalized.Object = String(objectId);
                }
            } else {
                // If reference doesn't match pattern, try to extract ID from end
                const idMatch = reference.match(/(\d+)$/);
                if (idMatch) {
                    const objectId = parseInt(idMatch[1], 10);
                    normalized['Object Type'] = (preObjectType && String(preObjectType).trim())
                        ? String(preObjectType).trim()
                        : reference.replace(/\s*\d+$/, '').trim();
                    normalized['Object_ID'] = objectId;
                    normalized['Object_Module'] = normalizeObjectTypeToModule(normalized['Object Type']);
                    const preStr = preObject != null && preObject !== undefined ? String(preObject).trim() : '';
                    const isIdOnly = preStr === String(objectId);
                    if (preStr && !isIdOnly) {
                        normalized.Object = preStr;
                    } else {
                        normalized.Object = idMatch[1];
                    }
                } else {
                    normalized['Object Type'] = preObjectType ? String(preObjectType).trim() : '';
                    normalized.Object = preObject != null && preObject !== undefined ? String(preObject).trim() : reference;
                    normalized['Object_ID'] = null;
                    normalized['Object_Module'] = null;
                }
            }
        } else {
            normalized['Object Type'] = preObjectType ? String(preObjectType).trim() : '';
            normalized.Object = preObject != null && preObject !== undefined ? String(preObject).trim() : '';
            normalized['Object_ID'] = null;
            normalized['Object_Module'] = null;
        }
        
        // Store CR ID for Subject link
        normalized['Subject_ID'] = normalized.ID || normalized.id;
        
        // Created By: display name and ID for people link
        const createdByName = normalized.createdByName || normalized.CreatedByName || '';
        const createdBy = normalized.createdBy ?? normalized.CreatedBy;
        normalized['Created By'] = createdByName || '';
        if (createdBy != null) {
            normalized['Created By_ID'] = createdBy;
        }

        // Format dates (BUDG-style: Create Date, Last Update Date; keep legacy keys for compatibility)
        const createdAt = normalized.createdAt || normalized.CreatedAt || normalized.Created_At
            || normalized['Created Date'] || normalized['Create Date'] || normalized.created_date;
        if (createdAt) {
            normalized['Created Date'] = formatDate(createdAt);
            normalized['Create Date'] = normalized['Created Date'];
        } else {
            const keepCreated = normalized['Created Date'] ?? normalized['Create Date'];
            if (keepCreated != null && keepCreated !== '') {
                normalized['Created Date'] = typeof keepCreated === 'string' ? keepCreated : formatDate(keepCreated);
                normalized['Create Date'] = normalized['Created Date'];
            } else {
                normalized['Created Date'] = '';
                normalized['Create Date'] = '';
            }
        }

        const updatedAt = normalized.updatedAt || normalized.UpdatedAt || normalized.Updated_At
            || normalized['Last Updated Date'] || normalized['Last Update Date'] || normalized.last_updated_date;
        if (updatedAt) {
            normalized['Last Updated Date'] = formatDate(updatedAt);
            normalized['Last Update Date'] = normalized['Last Updated Date'];
        } else {
            const keepUpd = normalized['Last Updated Date'] ?? normalized['Last Update Date'];
            if (keepUpd != null && keepUpd !== '') {
                normalized['Last Updated Date'] = typeof keepUpd === 'string' ? keepUpd : formatDate(keepUpd);
                normalized['Last Update Date'] = normalized['Last Updated Date'];
            } else {
                normalized['Last Updated Date'] = '';
                normalized['Last Update Date'] = '';
            }
        }

        // Segments = referenced object's segment (Unison rows may already set it; else filled in enrich)
        const incomingSeg = normalized.Segment ?? normalized.Segments ?? normalized.segment ?? normalized.segments;
        if (incomingSeg != null && String(incomingSeg).trim() !== '') {
            normalized.Segment = String(incomingSeg).trim();
            normalized.Segments = normalized.Segment;
        } else {
            normalized.Segment = 'Not Assigned';
            normalized.Segments = 'Not Assigned';
        }
        
        return normalized;
    });
}

/**
 * Enrich change request data with object names
 */
async function enrichChangeRequestDataWithObjectNames(data) {
    if (!Array.isArray(data) || data.length === 0) return data;
    
    // Group objects by module for batch fetching
    const objectsByModule = new Map();
    data.forEach(cr => {
        if (cr['Object_Module'] && cr['Object_ID']) {
            const module = cr['Object_Module'];
            if (!objectsByModule.has(module)) {
                objectsByModule.set(module, new Set());
            }
            objectsByModule.get(module).add(cr['Object_ID']);
        }
    });
    
    const nameCache = new Map();
    const segmentCache = new Map();
    const fetchPromises = Array.from(objectsByModule.entries()).map(async ([module, ids]) => {
        try {
            const idsArray = Array.from(ids);
            const moduleData = await fetchFacetDataByIds(module, idsArray);

            moduleData.forEach(obj => {
                const objId = obj.ID || obj.id;
                const objName = obj.Name || obj.PrimaryName || obj['Short Name'] ||
                    obj['Primary Name'] || obj['Long Name'] ||
                    obj['Ref.'] || obj.Ref || '';
                const segRaw = obj.Segment ?? obj.Segments ?? obj.segment ?? obj.segments ?? obj.segmentName;
                const segmentLabel = (segRaw != null && String(segRaw).trim() !== '') ? String(segRaw).trim() : '';
                if (objId != null && objName) {
                    nameCache.set(`${module}:${objId}`, objName);
                }
                if (objId != null && segmentLabel) {
                    segmentCache.set(`${module}:${objId}`, segmentLabel);
                }
            });
        } catch (e) {
            console.error(`[enrichChangeRequestDataWithObjectNames] Error fetching names for ${module}:`, e);
        }
    });

    await Promise.all(fetchPromises);

    data.forEach(cr => {
        if (cr['Object_Module'] && cr['Object_ID']) {
            const cacheKey = `${cr['Object_Module']}:${cr['Object_ID']}`;
            const objectName = nameCache.get(cacheKey);
            if (objectName) {
                cr.Object = objectName;
            }
            const objectSeg = segmentCache.get(cacheKey);
            if (objectSeg) {
                cr.Segments = objectSeg;
                cr.Segment = objectSeg;
            }
        }
    });
    
    return data;
}

async function fetchCategoryData(category, query) {
    try {
        const module = categoryToModule(category);
        if (!module) return [];

        // Special handling for Active Tasks
        if (module === 'activeTasks' || category.toLowerCase() === 'active-tasks' || category.toLowerCase() === 'activetasks') {
            if (typeof loadActiveTasksData === 'function') {
                return await loadActiveTasksData();
            }
            return [];
        }

        // Special handling for Change Requests
        if (module === 'change-requests' || category.toLowerCase() === 'change-requests' || 
            category.toLowerCase() === 'changerequests' || category.toLowerCase() === 'change-request' ||
            category.toLowerCase() === 'changerequest') {
            try {
                const url = '/api/changerequests';
                const resp = await fetch(url, {
                    headers: { 'Accept': 'application/json' },
                    credentials: 'include'
                });

                if (!resp.ok) {
                    console.error('[fetchCategoryData] Failed to fetch change requests:', resp.status);
                    return [];
                }

                const json = await resp.json();
                let data = Array.isArray(json) ? json : [];
                
                // Transform change request data to match required column names
                data = transformChangeRequestData(data);
                
                // Enrich with object names
                data = await enrichChangeRequestDataWithObjectNames(data);
                
                // Apply search query if provided
                if (query && query.trim()) {
                    const lowerQuery = query.toLowerCase();
                    return data.filter(cr => {
                        const subject = (cr.Subject || cr.primaryName || cr.PrimaryName || '').toLowerCase();
                        const summary = (cr.Summary || cr.summary || '').toLowerCase();
                        const object = (cr.Object || cr.reference || cr.Reference || '').toLowerCase();
                        const status = (cr.Status || cr.statusName || cr.StatusName || '').toLowerCase();
                        return subject.includes(lowerQuery) || 
                               summary.includes(lowerQuery) || 
                               object.includes(lowerQuery) ||
                               status.includes(lowerQuery);
                    });
                }
                
                return data;
            } catch (e) {
                console.error('[fetchCategoryData] Error fetching change requests:', e);
                return [];
            }
        }

        // Build URL with query parameters
        const params = new URLSearchParams();

        // Add query parameter if provided
        if (query) {
            params.append('q', query);
        }

        // Add related search parameters if available
        const selectedItem = getSelectedItem();
        if (selectedItem && selectedItem.category !== category) {
            // Normalize the related module name to ensure it matches what the server expects
            const relatedModule = categoryToModule(selectedItem.category);
            params.append('relatedModule', relatedModule);
            params.append('relatedId', selectedItem.id);
        }

        const url = `/UnisonSearch/${encodeURIComponent(module)}${params.toString() ? `?${params.toString()}` : ''}`;

        const resp = await fetch(url, {
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!resp.ok) {
            return [];
        }

        const json = await resp.json();
        return Array.isArray(json) ? json : [];
    } catch (e) {
        throw e;
    }
}

async function fetchCategoryDataWithConditions(category, conditions) {
    try {
        const module = categoryToModule(category);
        if (!module) return [];

        // Special handling for Active Tasks
        if (module === 'activeTasks' || category.toLowerCase() === 'active-tasks' || category.toLowerCase() === 'activetasks') {
            if (typeof loadActiveTasksData === 'function') {
                let data = await loadActiveTasksData();

                // Apply search conditions/filters if provided
                if (conditions && conditions.length > 0) {
                    const query = conditions.find(c => c.operator === 'FIND' || c.operator === 'AND' || c.operator === 'OR')?.query || '';
                    if (query) {
                        const lowerQuery = query.toLowerCase();
                        data = data.filter(task => {
                            // Search in name, title, objectType, object, owner
                            return (task.name && task.name.toLowerCase().includes(lowerQuery)) ||
                                (task.title && task.title.toLowerCase().includes(lowerQuery)) ||
                                (task.objectType && task.objectType.toLowerCase().includes(lowerQuery)) ||
                                (task.object && task.object.toLowerCase().includes(lowerQuery)) ||
                                (task.owner && task.owner.toLowerCase().includes(lowerQuery));
                        });
                    }
                }

                return data;
            }
            return [];
        }

        // Special handling for Change Requests
        if (module === 'change-requests' || category.toLowerCase() === 'change-requests' || 
            category.toLowerCase() === 'changerequests' || category.toLowerCase() === 'change-request' ||
            category.toLowerCase() === 'changerequest') {
            try {
                const url = '/api/changerequests';
                const resp = await fetch(url, {
                    headers: { 'Accept': 'application/json' },
                    credentials: 'include'
                });

                if (!resp.ok) {
                    console.error('[fetchCategoryDataWithConditions] Failed to fetch change requests:', resp.status);
                    return [];
                }

                const json = await resp.json();
                let data = Array.isArray(json) ? json : [];
                
                // Transform change request data to match required column names
                data = transformChangeRequestData(data);
                
                // Enrich with object names
                data = await enrichChangeRequestDataWithObjectNames(data);
                
                // Apply search conditions/filters if provided
                if (conditions && conditions.length > 0) {
                    const query = conditions.find(c => c.operator === 'FIND' || c.operator === 'AND' || c.operator === 'OR')?.query || '';
                    if (query) {
                        const lowerQuery = query.toLowerCase();
                        data = data.filter(cr => {
                            const subject = (cr.Subject || cr.primaryName || cr.PrimaryName || '').toLowerCase();
                            const summary = (cr.Summary || cr.summary || '').toLowerCase();
                            const object = (cr.Object || cr.reference || cr.Reference || '').toLowerCase();
                            const status = (cr.Status || cr.statusName || cr.StatusName || '').toLowerCase();
                            return subject.includes(lowerQuery) || 
                                   summary.includes(lowerQuery) || 
                                   object.includes(lowerQuery) ||
                                   status.includes(lowerQuery);
                        });
                    }
                }
                
                return data;
            } catch (e) {
                console.error('[fetchCategoryDataWithConditions] Error fetching change requests:', e);
                return [];
            }
        }

        // Build URL with multiple conditions
        const params = new URLSearchParams();

        // Add related search parameters if available
        const selectedItem = getSelectedItem();
        if (selectedItem && selectedItem.category !== category) {
            const relatedModule = categoryToModule(selectedItem.category);
            params.append('relatedModule', relatedModule);
            params.append('relatedId', selectedItem.id);
        } else if (conditions && conditions.length > 0) {
            // Add each condition
            conditions.forEach((condition, index) => {
                const conditionModule = categoryToModule(condition.category) || condition.category;
                params.append(`conditions[${index}][operator]`, condition.operator);
                params.append(`conditions[${index}][module]`, conditionModule);
                params.append(`conditions[${index}][query]`, condition.query);
            });
        }

        const url = `/UnisonSearch/${encodeURIComponent(module)}${params.toString() ? `?${params.toString()}` : ''}`;

        const resp = await fetch(url, {
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!resp.ok) {
            return [];
        }

        const json = await resp.json();
        return Array.isArray(json) ? json : [];
    } catch (e) {
        throw e;
    }
}

/**
 * Fetch category data with boolean query (AST parser mode).
 * Supports inline boolean expressions: AND, OR, NOT, parentheses, quoted strings, field:value, wildcards
 */
async function fetchCategoryDataWithBooleanQuery(category, query) {
    try {
        const module = categoryToModule(category);
        if (!module) return [];

        // Special handling for Active Tasks
        if (module === 'activeTasks' || category.toLowerCase() === 'active-tasks' || category.toLowerCase() === 'activetasks') {
            if (typeof loadActiveTasksData === 'function') {
                let data = await loadActiveTasksData();

                // Apply search query if provided
                if (query && query.trim()) {
                    const lowerQuery = query.toLowerCase();
                    data = data.filter(task => {
                        // Search in name, title, objectType, object, owner
                        return (task.name && task.name.toLowerCase().includes(lowerQuery)) ||
                            (task.title && task.title.toLowerCase().includes(lowerQuery)) ||
                            (task.objectType && task.objectType.toLowerCase().includes(lowerQuery)) ||
                            (task.object && task.object.toLowerCase().includes(lowerQuery)) ||
                            (task.owner && task.owner.toLowerCase().includes(lowerQuery));
                    });
                }

                return data;
            }
            return [];
        }

        // Special handling for Change Requests
        if (module === 'change-requests' || category.toLowerCase() === 'change-requests' || 
            category.toLowerCase() === 'changerequests' || category.toLowerCase() === 'change-request' ||
            category.toLowerCase() === 'changerequest') {
            try {
                const url = '/api/changerequests';
                const resp = await fetch(url, {
                    headers: { 'Accept': 'application/json' },
                    credentials: 'include'
                });

                if (!resp.ok) {
                    console.error('[fetchCategoryDataWithBooleanQuery] Failed to fetch change requests:', resp.status);
                    return [];
                }

                const json = await resp.json();
                let data = Array.isArray(json) ? json : [];
                
                // Transform change request data to match required column names
                data = transformChangeRequestData(data);
                
                // Enrich with object names
                data = await enrichChangeRequestDataWithObjectNames(data);
                
                // Apply search query if provided
                if (query && query.trim()) {
                    const lowerQuery = query.toLowerCase();
                    data = data.filter(cr => {
                        const subject = (cr.Subject || cr.primaryName || cr.PrimaryName || '').toLowerCase();
                        const summary = (cr.Summary || cr.summary || '').toLowerCase();
                        const object = (cr.Object || cr.reference || cr.Reference || '').toLowerCase();
                        const status = (cr.Status || cr.statusName || cr.StatusName || '').toLowerCase();
                        return subject.includes(lowerQuery) || 
                               summary.includes(lowerQuery) || 
                               object.includes(lowerQuery) ||
                               status.includes(lowerQuery);
                    });
                }
                
                return data;
            } catch (e) {
                console.error('[fetchCategoryDataWithBooleanQuery] Error fetching change requests:', e);
                return [];
            }
        }

        // Build URL with boolean query
        const params = new URLSearchParams();
        if (query) {
            params.append('q', query);
        }

        // Add related search parameters if available
        const selectedItem = getSelectedItem();
        if (selectedItem && selectedItem.category !== category) {
            const relatedModule = categoryToModule(selectedItem.category);
            params.append('relatedModule', relatedModule);
            params.append('relatedId', selectedItem.id);
        }

        const url = `/UnisonSearch/${encodeURIComponent(module)}${params.toString() ? `?${params.toString()}` : ''}`;

        const resp = await fetch(url, {
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!resp.ok) {
            return [];
        }

        const json = await resp.json();
        return Array.isArray(json) ? json : [];
    } catch (e) {
        throw e;
    }
}

// Preload functions removed - cache functionality disabled
async function preloadCommonEntities() {
    // Preload functionality disabled - no cache
}

async function searchEntityByName(module, name) {
    try {
        // Skip search if name is empty or invalid
        if (!name || name.trim() === '' || name === 'undefined' || name === 'null') {
            return [];
        }

        const url = `/UnisonSearch/${encodeURIComponent(module)}?q=${encodeURIComponent(name)}`;
        const response = await fetch(url, {
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            return [];
        }

        const data = await response.json();
        return Array.isArray(data) ? data : [];
    } catch (error) {
        return [];
    }
}

/**
 * Convert facet ID to category name
 */
function facetIdToCategory(facetId) {
    if (!facetId) return null;
    
    // Use normalizeFacetIdToCanonical from facet-normalization.js if available (single source of truth)
    // This ensures consistency with backend FacetNormalizationUtil
    let normalizedId;
    if (typeof window !== 'undefined' && typeof window.normalizeFacetIdToCanonical === 'function') {
        normalizedId = window.normalizeFacetIdToCanonical(facetId);
    } else {
        // Fallback to local normalization for backward compatibility
        const normalized = facetId.toString().trim().toUpperCase().replace(/-/g, '_');
        normalizedId = normalized;
        
        // Handle special cases
        switch (normalized) {
            case 'CHANGEREQUEST':
            case 'CHANGE_REQUEST':
                normalizedId = 'CHANGE_REQUESTS';
                break;
            case 'DATA_SETS':
            case 'DATA_SET':
            case 'DATASETS':
                normalizedId = 'DATASET';
                break;
            case 'ACTIVETASKS':
            case 'ACTIVE_TASKS':
                normalizedId = 'ACTIVE_TASKS';
                break;
        }
    }
    
    const mapping = {
        'DATASET': 'data-sets',
        'ATTRIBUTE': 'attributes',
        'SYSTEM': 'system',
        'GLOSSARY': 'glossary',
        'DATAQUALITY': 'dataquality',
        'PEOPLE': 'people',
        'ROLE': 'role',
        'BUSINESS_AREA': 'business-area',
        'LEGAL_ENTITY': 'legal-entity',
        'CLIENT': 'client',
        'COMMITTEE': 'committee',
        'POLICY': 'policy',
        'PROCESS': 'process',
        'INTERFACE': 'interface',
        'CAPABILITY': 'capability',
        'PRODUCT': 'product',
        'ORG_UNIT': 'org-unit',
        'GEOGRAPHY': 'geography',
        'REGULATION': 'regulation',
        'REGULATOR': 'regulator',
        'REGULATORY_THEME': 'regulatory-theme',
        'ACTIVE_TASKS': 'active-tasks',
        'CHANGE_REQUESTS': 'change-requests',
        'PROJECT': 'project',
        'PROJECTS': 'project'
    };
    
    return mapping[normalizedId] || null;
}

/**
 * Convert category name to facet ID
 */
function categoryToFacetId(category) {
    if (!category) return null;

    // ✅ Use categorySlugToFacetId from search-utils.js for consistency
    if (typeof categorySlugToFacetId === 'function') {
        const facetId = categorySlugToFacetId(category);
        if (facetId) {
            return facetId;
        }
    } else if (typeof window !== 'undefined' && typeof window.categorySlugToFacetId === 'function') {
        const facetId = window.categorySlugToFacetId(category);
        if (facetId) {
            return facetId;
        }
    }

    // Fallback to old mapping (for backward compatibility)
    const normalized = category.toString()
        .toLowerCase()
        .trim()
        .replace(/\s+/g, '-');

    const mapping = {
        'dataset': 'DATASET',
        'datasets': 'DATASET',
        'data-set': 'DATASET',
        'data-sets': 'DATASET',
        'attribute': 'ATTRIBUTE',
        'attributes': 'ATTRIBUTE', // ✅ Add plural form
        'system': 'SYSTEM',
        'glossary': 'GLOSSARY',
        'dataquality': 'DATAQUALITY',
        'people': 'PEOPLE',
        'role': 'ROLE',
        'business-area': 'BUSINESS_AREA',
        'legal-entity': 'LEGAL_ENTITY',
        'client': 'CLIENT',
        'committee': 'COMMITTEE',
        'policy': 'POLICY',
        'process': 'PROCESS',
        'interface': 'INTERFACE',
        'capability': 'CAPABILITY',
        'product': 'PRODUCT',
        'orgunit': 'ORG_UNIT',
        'geography': 'GEOGRAPHY',
        'regulation': 'REGULATION',
        'regulator': 'REGULATOR',
        'regulatory-theme': 'REGULATORY_THEME',
        'active-tasks': 'ACTIVE_TASKS',
        'activetasks': 'ACTIVE_TASKS',
        'tasks': 'ACTIVE_TASKS',
        'task': 'ACTIVE_TASKS',
        'change-requests': 'CHANGE_REQUESTS',
        'changerequests': 'CHANGE_REQUESTS',
        'change-request': 'CHANGE_REQUESTS',
        'changerequest': 'CHANGE_REQUESTS'
    };
    
    const result = mapping[normalized];
    if (result) {
        return result;
    }
    
    // ✅ No fallback - return null if no mapping exists
    console.warn(`[categoryToFacetId] No mapping found for category: ${category} (normalized: ${normalized})`);
    return null;
}

/**
 * Execute Unison Search - searches across all facets with graph traversal.
 * Returns results for all facets, not just the active one.
 * 
 * @param {Array} searches - Array of search items with {operator, facet, keyword, filters}
 * @param {Object} options - Search options {maxDepth, includeCounts}
 * @returns {Promise<Object>} Response with results per facet
 */
async function executeUnisonSearch(searches, options = {}) {
    const maxDepthValue = options.maxDepth !== undefined ? options.maxDepth : 2;
    try {
        if (typeof window !== 'undefined') {
            window.__lastUnisonSearchRequest = {
                at: new Date().toISOString(),
                maxDepth: maxDepthValue,
                searches: searches,
                options: options
            };
        }

        const traceHdr =
            typeof window !== 'undefined'
            && typeof window.isUnisonSearchDebugEnabled === 'function'
            && window.isUnisonSearchDebugEnabled();
        const fetchHeaders = {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        };
        if (traceHdr) {
            fetchHeaders['X-Unison-Trace'] = '1';
        }
        if (traceHdr && typeof window.unisonSearchDebugLog === 'function') {
            window.unisonSearchDebugLog('api.request', {
                maxDepth: maxDepthValue,
                includeCounts: options.includeCounts !== false,
                clauseCount: Array.isArray(searches) ? searches.length : 0,
                xUnisonTrace: '1'
            });
        }

        const response = await fetch('/api/unison/search', {
            method: 'POST',
            headers: fetchHeaders,
            credentials: 'include',
            body: JSON.stringify({
                searches: searches,
                options: {
                    maxDepth: maxDepthValue,
                    includeCounts: options.includeCounts !== false
                }
            })
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));

            // Provide more specific error messages based on status code
            let errorMessage = 'Search failed';
            if (response.status === 403) {
                errorMessage = 'Access denied. Please check your permissions.';
            } else if (response.status === 500) {
                errorMessage = 'Server error. Please try again or contact support.';
            } else if (response.status === 400) {
                errorMessage = errorData.error || 'Invalid search request. Please check your search criteria.';
            } else if (errorData.error) {
                errorMessage = errorData.error;
            } else {
                errorMessage = `Search failed with status ${response.status}`;
            }

            const httpErr = {
                at: new Date().toISOString(),
                status: response.status,
                message: errorMessage,
                body: errorData
            };
            if (typeof window !== 'undefined') {
                window.__lastUnisonSearchError = httpErr;
                if (typeof window.unisonSearchErrorLog === 'function') {
                    window.unisonSearchErrorLog('api.httpError', httpErr);
                }
            }
            throw new Error(errorMessage);
        }

        const result = await response.json();

        if (typeof window !== 'undefined') {
            const results = result.results || {};
            const perFacet = {};
            Object.keys(results).forEach((k) => {
                const v = results[k];
                perFacet[k] = {
                    count: v && v.count,
                    rowLen: v && Array.isArray(v.rows) ? v.rows.length : 0,
                    idLen: v && v.ids
                        ? (Array.isArray(v.ids) ? v.ids.length : (v.ids.size || 0))
                        : 0,
                    totalCount: v && v.totalCount
                };
            });
            window.__lastUnisonSearchResponseMeta = {
                at: new Date().toISOString(),
                success: result.success !== false,
                facetIds: Object.keys(results),
                perFacet: perFacet
            };
            window.__lastUnisonSearchError = null;
            if (typeof window.unisonSearchDebugLog === 'function') {
                window.unisonSearchDebugLog('api.response', window.__lastUnisonSearchResponseMeta);
            }
        }
        
        // Store related objects metadata globally for later use
        if (result.relatedObjects) {
            if (typeof window !== 'undefined') {
                window.unisonRelatedObjects = result.relatedObjects;
                console.log('[Unison Search API] Related objects loaded:', Object.keys(result.relatedObjects).length, 'facets');
            }
        }

        return result;
    } catch (error) {
        if (typeof window !== 'undefined') {
            const keepHttp = window.__lastUnisonSearchError && typeof window.__lastUnisonSearchError.status === 'number';
            if (!keepHttp) {
                window.__lastUnisonSearchError = {
                    at: new Date().toISOString(),
                    message: error && error.message,
                    stack: error && error.stack
                };
                if (typeof window.unisonSearchErrorLog === 'function') {
                    window.unisonSearchErrorLog('api.exception', window.__lastUnisonSearchError);
                }
            }
        }
        console.error('[Unison Search API] Error:', error);
        throw error;
    }
}

// Make executeUnisonSearch available on window for cross-file access
if (typeof window !== 'undefined') {
    window.executeUnisonSearch = executeUnisonSearch;
    
    // Initialize related objects storage
    if (!window.unisonRelatedObjects) {
        window.unisonRelatedObjects = {};
    }
    window.categoryToFacetId = categoryToFacetId;
    window.facetIdToCategory = facetIdToCategory;
}

/**
 * Fetch facet data by IDs (for displaying filtered results in a facet)
 * 
 * @param {string} category - Category name
 * @param {Array<number>} ids - Array of object IDs to fetch
 * @param {Object} options - Options {page, pageSize, sortBy, sortOrder}
 * @returns {Promise<Array>} Array of facet data objects
 */
async function fetchFacetDataByIds(category, ids, options = {}) {
    if (!ids || ids.length === 0) {
        return [];
    }

    // Convert Set to Array if needed
    const idArray = Array.isArray(ids) ? ids : Array.from(ids);

    const module = categoryToModule(category);
    if (!module) {
        console.warn(`[fetchFacetDataByIds] No module found for category: ${category}`);
        return [];
    }

    if (module === 'activeTasks' || category.toLowerCase() === 'active-tasks' || category.toLowerCase() === 'activetasks') {
        if (typeof loadActiveTasksData !== 'function') {
            return [];
        }
        try {
            const all = await loadActiveTasksData();
            if (!Array.isArray(all)) {
                return [];
            }
            const idSet = new Set(idArray.map(id => Number(id)));
            return all.filter(item => {
                const tid = item.taskId != null ? item.taskId : (item.id != null ? item.id : item.ID);
                return tid != null && !Number.isNaN(Number(tid)) && idSet.has(Number(tid));
            });
        } catch (e) {
            console.error('[fetchFacetDataByIds] Error loading active tasks:', e);
            return [];
        }
    }

    // Special handling for Change Requests
    if (module === 'change-requests' || category.toLowerCase() === 'change-requests' || 
        category.toLowerCase() === 'changerequests' || category.toLowerCase() === 'change-request' ||
        category.toLowerCase() === 'changerequest') {
        try {
            // Fetch all change requests and filter by IDs
            const url = '/api/changerequests';
            const resp = await fetch(url, {
                headers: { 'Accept': 'application/json' },
                credentials: 'include'
            });

            if (!resp.ok) {
                console.error(`[fetchFacetDataByIds] Failed to fetch change requests: ${resp.status}`);
                return [];
            }

            const allData = await resp.json();
            if (!Array.isArray(allData)) {
                console.warn(`[fetchFacetDataByIds] Response is not an array:`, typeof allData);
                return [];
            }

            // Transform and filter by IDs
            const idSet = new Set(idArray.map(id => Number(id)));
            const filtered = allData
                .filter(item => {
                    const itemId = item.id || item.ID;
                    return itemId && idSet.has(Number(itemId));
                });
            
            // Transform change request data to match required column names
            const transformed = transformChangeRequestData(filtered);
            
            // Enrich with object names
            return await enrichChangeRequestDataWithObjectNames(transformed);
        } catch (e) {
            console.error('[fetchFacetDataByIds] Error fetching change requests:', e);
            return [];
        }
    }

    try {
        // Build URL with ID filters
        const params = new URLSearchParams();

        // Add ID filters - we'll need to modify the backend to support this
        // For now, we'll fetch all data and filter client-side (not ideal but works)
        // TODO: Add backend endpoint to fetch by IDs

        const url = `/UnisonSearch/${encodeURIComponent(module)}${params.toString() ? `?${params.toString()}` : ''}`;

        const resp = await fetch(url, {
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!resp.ok) {
            console.error(`[fetchFacetDataByIds] Response not OK: ${resp.status}`);
            return [];
        }

        const allData = await resp.json();
        if (!Array.isArray(allData)) {
            console.warn(`[fetchFacetDataByIds] Response is not an array:`, typeof allData, allData);
            // Check if it's an error object
            if (allData && typeof allData === 'object' && allData.error) {
                console.error(`[fetchFacetDataByIds] Error response from server:`, allData.error);
                return [];
            }
            // Try to extract array from response if it's wrapped in an object
            if (allData && typeof allData === 'object' && allData.data && Array.isArray(allData.data)) {
                console.log(`[fetchFacetDataByIds] Found data array in response object, using it`);
                return allData.data;
            }
            // If it's a single object, wrap it in an array
            if (allData && typeof allData === 'object' && !Array.isArray(allData)) {
                console.warn(`[fetchFacetDataByIds] Response is a single object, wrapping in array`);
                return [allData];
            }
            return [];
        }

        // Filter by IDs client-side (temporary solution)
        const idSet = new Set(idArray.map(id => Number(id)));
        console.log(`[fetchFacetDataByIds] Filtering ${allData.length} ${module} records by ${idSet.size} IDs:`, Array.from(idSet));
        
        // For role, log first few items to see their ID structure
        if (module === 'role' && allData.length > 0) {
            console.log(`[fetchFacetDataByIds] Sample role items (first 3):`, allData.slice(0, 3).map(item => ({
                ID: item.ID,
                id: item.id,
                hasID: item.ID !== undefined,
                hasid: item.id !== undefined,
                allKeys: Object.keys(item)
            })));
        }
        
        const filtered = allData.filter(item => {
            const itemId = item.ID || item.id;
            const itemIdNum = itemId ? Number(itemId) : null;
            const matches = itemIdNum !== null && idSet.has(itemIdNum);
            
            // Log mismatches for role to debug
            if (module === 'role' && !matches && itemIdNum !== null) {
                console.log(`[fetchFacetDataByIds] Role item ID ${itemIdNum} (${typeof itemIdNum}) not in requested set:`, Array.from(idSet));
            }
            
            if (matches && module === 'dataset') {
                const name = item.PrimaryName || item.primaryName || item.Name || item.name || item['Name'] || 'N/A';
            }
            return matches;
        });

        console.log(`[fetchFacetDataByIds] Filtered result for ${module}:`, {
            totalRecords: allData.length,
            requestedIds: Array.from(idSet),
            filteredCount: filtered.length,
            sampleItem: filtered.length > 0 ? filtered[0] : null,
            sampleItemId: filtered.length > 0 ? (filtered[0].ID || filtered[0].id) : null
        });

        return filtered;
    } catch (e) {
        console.error('[fetchFacetDataByIds] Error:', e);
        return [];
    }
}

/**
 * Load Active Tasks data for current user
 * @returns {Promise<Array>} Array of active task objects
 */
async function loadActiveTasksData() {
    try {
        const response = await fetch('/api/active-tasks', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            console.error('[loadActiveTasksData] Response not OK:', response.status);
            const errorText = await response.text();
            console.error('[loadActiveTasksData] Error response:', errorText);
            return [];
        }

        const tasks = await response.json();

        if (!Array.isArray(tasks)) {
            console.warn('[loadActiveTasksData] Response is not an array:', typeof tasks);
            return [];
        }

        if (tasks.length === 0) {
            return [];
        }

        // Transform tasks to match Unison Search table format
        const transformedTasks = tasks.map(task => ({
            id: task.id || task.taskId,
            name: task.name || '',
            title: task.title || '',
            objectType: task.objectType || 'Change Request',
            object: task.object || 'N/A',
            assignDate: task.assignDate || '',
            dueDate: task.dueDate || '',
            dueInDays: task.dueInDays,
            isOverdue: task.isOverdue || false,
            owner: task.owner || 'Unassigned',
            segments: task.segments || 'Not Assigned',
            changeRequestId: task.changeRequestId,
            workflowInstanceId: task.workflowInstanceId,
            taskId: task.taskId,
            decisionOptions: task.decisionOptions || ['complete'],
            status: task.status || 'Pending'
        }));

        return transformedTasks;
    } catch (e) {
        console.error('[loadActiveTasksData] Error:', e);
        return [];
    }
}

// Make loadActiveTasksData available globally
if (typeof window !== 'undefined') {
    window.loadActiveTasksData = loadActiveTasksData;
}


