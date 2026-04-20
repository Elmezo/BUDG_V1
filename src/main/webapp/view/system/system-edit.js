(function () {
    let segmentField = null; // Segment field component reference
    let editViewMode = 'original'; // 'original' | 'changes' (under active CR, edit should load nobject_id data)
    let originalSystemSegmentId = null; // segment loaded from server; used to detect segment change

    function normalizeSystemSegmentId(value) {
        if (value == null || value === '' || value === -1) return null;
        const n = parseInt(value, 10);
        return Number.isInteger(n) ? n : null;
    }

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatDateTime(dt) {
        if (!dt) return '';
        try {
            const d = new Date(dt);
            if (isNaN(d.getTime())) return '';
            return d.toLocaleString('en-US', {
                month: '2-digit',
                day: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: true
            });
        } catch (e) {
            return '';
        }
    }

    function msg(key, opts, fallback) {
        try {
            return (window.I18n && window.I18n.t(key, opts)) || fallback || '';
        } catch (e) {
            return fallback || '';
        }
    }

    /** Map API external (DB External column) to scopeSelect value Internal | External */
    function scopeSelectValueFromExternalApi(data) {
        const raw = data && Object.prototype.hasOwnProperty.call(data, 'external')
            ? data.external
            : data && data.External;
        if (raw === true || raw === 1) return 'External';
        if (raw === false || raw === 0) return 'Internal';
        if (raw == null || raw === '') return 'Internal';
        const s = String(raw).trim().toLowerCase();
        if (s === '1' || s === 'true' || s === 'yes') return 'External';
        if (s === '0' || s === 'false' || s === 'no') return 'Internal';
        return 'Internal';
    }

    function applyScopeSelectFromApiData(data) {
        const el = document.getElementById('scopeSelect');
        if (!el || !data) return;
        const v = scopeSelectValueFromExternalApi(data);
        el.value = v;
    }

    function parseId() {
        // First try URL params (for separate edit pages)
        const urlParams = new URLSearchParams(window.location.search);
        const id = parseInt(urlParams.get('id'), 10);
        if (!Number.isNaN(id)) return id;

        // Fallback to path-based ID (for main view pages)
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('system');
        if (idx === -1 || parts.length < idx + 2) return null;
        const pathId = parseInt(parts[idx + 1], 10);
        return Number.isNaN(pathId) ? null : pathId;
    }

    async function determineEditViewMode(systemId) {
        // For EDIT pages: If an active CR exists, load the pending changes (nobject_id data)
        // This ensures the edit form shows the latest pending changes, not the original data
        try {
            const res = await fetch(`/api/pending-changes/status/System/${systemId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (res.ok) {
                const status = await res.json();
                if (status?.underRevision) {
                    console.log('[System Edit] Object is under revision, loading changes view');
                    return 'changes';
                }
            }
        } catch (e) {
            console.warn('[System Edit] Failed to determine pending-changes status:', e);
        }
        return 'original';
    }

    async function loadSystem(id, viewMode = 'original') {
        try {
            console.log(`[loadSystem] Loading system ID: ${id}, viewMode: ${viewMode}`);
            const viewParam = viewMode === 'changes' ? 'changes' : null;
            console.log(`[loadSystem] Calling getSystemById with id=${id}, view=${viewParam}`);
            const data = await window.BUDG_API_SERVICE.getSystemById(id, viewParam);
            console.log(`[loadSystem] Received data for system ID: ${data?.id || 'unknown'}`);

            // Update page title with system name ONLY if we're on the edit page (not view page)
            // Check if we're on edit page by looking for systemEditContainer
            const isEditPage = document.getElementById('systemEditContainer') !== null;
            if (isEditPage) {
                const pageTitleMain = document.querySelector('.page-title-main');
                const pageTitleSub = document.querySelector('.page-title-sub');
                if (pageTitleMain) {
                    pageTitleMain.textContent = data.name || 'Edit System';
                    console.log('[System Edit] Title updated to:', data.name, '(viewMode:', viewMode, ')');
                }
                if (pageTitleSub) pageTitleSub.textContent = 'System';
            } else {
                console.log('[System Edit] Skipping title update - not on edit page');
            }

            // Helper function to set simple text field values
            const set = (id, v) => {
                const el = document.getElementById(id);
                if (el) {
                    el.value = v ?? '';
                    console.log(`Set ${id} = "${v}"`);
                }
            };

            // Enhanced helper function to set dropdown values
            const setSelectByValue = (id, v) => {
                const el = document.getElementById(id);
                if (!el) {
                    console.warn(`Element not found: ${id}`);
                    return false;
                }

                if (v == null || v === '' || v === 'null' || v === 'undefined') {
                    console.log(`Value for ${id} is null/undefined/empty, setting to empty`);
                    el.value = '';
                    return true;
                }

                const valueStr = String(v);
                console.log(`Setting ${id} dropdown to value: "${valueStr}"`);
                console.log(`Available options for ${id}:`, Array.from(el.options).map(o => `value:"${o.value}" text:"${o.textContent}"`));

                // First try: direct value match
                try {
                    el.value = valueStr;

                    // Check if the value was actually set (some browsers reset to default if invalid)
                    if (el.value === valueStr) {
                        console.log(`✓ Successfully set ${id}="${valueStr}" (direct match)`);
                        return true;
                    }

                    console.warn(`Direct value set failed for ${id}="${valueStr}"`);

                    // Second try: find option with matching value
                    const option = Array.from(el.options).find(opt => String(opt.value) === valueStr);
                    if (option) {
                        console.log(`✓ Found matching option for ${id}="${valueStr}" at index ${option.index}`);
                        el.selectedIndex = option.index;
                        return true;
                    }

                    // Third try: find by option text content (case insensitive)
                    const textOption = Array.from(el.options).find(
                        opt => opt.textContent && opt.textContent.trim().toLowerCase() === valueStr.toLowerCase()
                    );

                    if (textOption) {
                        console.log(`✓ Found option with matching text for ${id}="${valueStr}" at index ${textOption.index}`);
                        el.selectedIndex = textOption.index;
                        return true;
                    }

                    // Fourth try: numeric comparison for ID-based dropdowns
                    const numericValue = parseInt(valueStr, 10);
                    if (!isNaN(numericValue)) {
                        const numericOption = Array.from(el.options).find(opt => {
                            const optValue = parseInt(opt.value, 10);
                            return !isNaN(optValue) && optValue === numericValue;
                        });

                        if (numericOption) {
                            console.log(`✓ Found numeric matching option for ${id}="${valueStr}" at index ${numericOption.index}`);
                            el.selectedIndex = numericOption.index;
                            return true;
                        }
                    }

                    console.error(`✗ Could not find option with value or text "${valueStr}" in select "${id}"`);
                    return false;
                } catch (e) {
                    console.error(`Error setting value for ${id}:`, e);
                    return false;
                }
            };

            // Text fields
            set('shortName', data.name);
            set('description', data.description);
            set('longName', data.longName);
            set('url', data.url);

            // Checkbox for DQ Automation
            const dqAutomationCheckbox = document.getElementById('dqAutomation');
            if (dqAutomationCheckbox) {
                dqAutomationCheckbox.checked = !!(data.DQ_Automation || data.dqAutomation);
                console.log(`Set dqAutomation checkbox = ${dqAutomationCheckbox.checked}`);
            }

            // Read-only fields for display
            set('createdBy', data.createdByName || '');
            set('createdDate', data.createdDatetime ? formatDateTime(data.createdDatetime) : '');
            set('lastUpdatedBy', data.lastUpdatedByName || '');
            set('lastUpdatedDate', data.lastUpdatedDatetime ? formatDateTime(data.lastUpdatedDatetime) : '');

            // Debug: Log full API response to see what fields are available
            console.log('=== FULL API RESPONSE ===');
            console.log('All data keys:', Object.keys(data));
            console.log('Full data object:', JSON.stringify(data, null, 2));
            console.log('=== END API RESPONSE ===');

            // Type select - use Type ID (foreign key) to set the dropdown
            const typeSelect = document.getElementById('typeSelect');

            if (typeSelect) {
                // Wait for dropdown to be populated (in case it's still loading)
                let attempts = 0;
                const maxAttempts = 10;
                while (typeSelect.options.length <= 1 && attempts < maxAttempts) {
                    await new Promise(resolve => setTimeout(resolve, 50));
                    attempts++;
                }

                // Try multiple field names for Type - check all possible variations
                // Handle both null and undefined - null means the field exists but is null in DB
                let typeValue = null;
                if (data.Type !== undefined && data.Type !== null) {
                    typeValue = data.Type;
                } else if (data.type !== undefined && data.type !== null) {
                    typeValue = data.type;
                } else if (data.typeId !== undefined && data.typeId !== null) {
                    typeValue = data.typeId;
                } else if (data.type_id !== undefined && data.type_id !== null) {
                    typeValue = data.type_id;
                } else if (data.systemTypeId !== undefined && data.systemTypeId !== null) {
                    typeValue = data.systemTypeId;
                }

                const typeName = data.typeName || data.type_name || data.systemTypeName || data.TypeName;

                console.log('Type data from API:', {
                    typeValue,
                    typeName,
                    rawData: {
                        Type: data.Type,
                        type: data.type,
                        typeId: data.typeId,
                        type_id: data.type_id,
                        systemTypeId: data.systemTypeId,
                        typeName: data.typeName,
                        type_name: data.type_name,
                        systemTypeName: data.systemTypeName,
                        TypeName: data.TypeName
                    },
                    dropdownOptions: typeSelect.options.length,
                    dropdownValues: Array.from(typeSelect.options).map(o => ({ value: o.value, text: o.textContent }))
                });

                if (typeValue != null && typeValue !== '') {
                    // Convert to string for comparison
                    const typeValueStr = String(typeValue);
                    console.log('Attempting to set Type dropdown to value:', typeValueStr);
                    console.log('Available options:', Array.from(typeSelect.options).map(o => ({ value: o.value, text: o.textContent })));

                    const success = setSelectByValue('typeSelect', typeValueStr);
                    if (!success && typeName) {
                        // If setting by ID failed, try by name
                        console.log('Setting by ID failed, trying to set Type by name:', typeName);
                        const nameSuccess = setSelectByValue('typeSelect', typeName);
                        if (!nameSuccess) {
                            console.error('Failed to set Type dropdown by both ID and name');
                        }
                    } else if (success) {
                        console.log('✓ Successfully set Type dropdown to:', typeValueStr);
                    }
                } else if (typeName) {
                    // If no ID available, try by name
                    console.log('No Type ID available, setting Type by name only:', typeName);
                    const nameSuccess = setSelectByValue('typeSelect', typeName);
                    if (!nameSuccess) {
                        console.error('Failed to set Type dropdown by name');
                    }
                } else {
                    // Type field is missing from API response - leave dropdown unset
                    console.warn('Type field missing from API response - dropdown will remain unset');
                    typeSelect.value = '';
                }

                // Verify the value was set correctly
                if (typeSelect.value) {
                    const selectedOption = typeSelect.options[typeSelect.selectedIndex];
                    console.log('Type dropdown final state:', {
                        value: typeSelect.value,
                        text: selectedOption ? selectedOption.textContent : 'N/A',
                        expectedValue: typeValue,
                        expectedName: typeName
                    });
                } else {
                    console.warn('Type dropdown value is empty after setting attempt');
                }
            } else {
                console.warn('Type select element not found - may not be loaded yet');
            }

            // Scope (Internal/External) from API external / External
            const scope = scopeSelectValueFromExternalApi(data);
            set('scopeSelect', scope);
            queueMicrotask(() => applyScopeSelectFromApiData(data));

            // Numeric ID-based selects
            setSelectByValue('BUDGStatus', data.status);
            setSelectByValue('lifecycle', data.lifecycle);
            setSelectByValue('BUDGViewing', data.isPublic);

            // Classification - try multiple field names
            // Handle both null and undefined - null means the field exists but is null in DB
            let classificationValue = null;
            if (data.classification !== undefined && data.classification !== null) {
                classificationValue = data.classification;
            } else if (data.Classification !== undefined && data.Classification !== null) {
                classificationValue = data.Classification;
            } else if (data.classificationId !== undefined && data.classificationId !== null) {
                classificationValue = data.classificationId;
            }

            const classificationName = data.classificationName || data.ClassificationName;

            console.log('Classification data from API:', {
                classificationValue,
                classificationName,
                rawData: {
                    classification: data.classification,
                    Classification: data.Classification,
                    classificationId: data.classificationId,
                    classificationName: data.classificationName,
                    ClassificationName: data.ClassificationName
                }
            });

            if (classificationValue != null && classificationValue !== '') {
                const success = setSelectByValue('classification', String(classificationValue));
                if (!success && classificationName) {
                    console.log('Setting Classification by name:', classificationName);
                    setSelectByValue('classification', classificationName);
                } else if (success) {
                    console.log('✓ Successfully set Classification dropdown to:', classificationValue);
                }
            } else if (classificationName) {
                console.log('Setting Classification by name only:', classificationName);
                setSelectByValue('classification', classificationName);
            } else {
                console.warn('Classification field missing from API response');
            }
            // CIA ratings - try multiple field names and ensure proper mapping
            const ciaC = data.ciaC || data.confidentiality_rating || data.confidentialityRating;
            const ciaI = data.ciaI || data.integrity_rating || data.integrityRating;
            const ciaA = data.ciaA || data.availability_rating || data.availabilityRating;

            console.log('CIA ratings from API:', { ciaC, ciaI, ciaA });

            setSelectByValue('ciaC', ciaC);
            setSelectByValue('ciaI', ciaI);
            setSelectByValue('ciaA', ciaA);

            // Parent display only
            const parentEl = document.getElementById('parentShortName');
            if (parentEl) {
                // Exclude parent if it's the same as current system (circular reference)
                const currentSystemId = id;
                const parentId = data.hierarchy?.parentId;
                const parentName = data.hierarchy?.parentName;
                
                if (parentId != null && parentId === currentSystemId) {
                    console.warn('[System Edit] Excluding parent - circular reference detected: parentId=' + parentId + ', systemId=' + currentSystemId);
                    parentEl.value = '';
                    parentEl.dataset.parentId = '';
                } else {
                    parentEl.value = parentName || '';
                    if (parentId != null) {
                        parentEl.dataset.parentId = String(parentId);
                    } else {
                        parentEl.dataset.parentId = '';
                    }
                }
            }

            const loadedSegmentId = data.segmentId ?? data.segment_id ?? data.Segment_ID;

            // Set segment value if available (check for -1 which means not assigned)
            if (segmentField && loadedSegmentId != null && loadedSegmentId !== undefined && loadedSegmentId !== -1) {
                segmentField.setValue(loadedSegmentId);
            } else if (segmentField && (loadedSegmentId === -1 || loadedSegmentId == null)) {
                // System has no segment assignment - leave field empty
                console.log('⚠️ System has no segment assignment (segmentId: ' + loadedSegmentId + ')');
            }
            window.currentImpactSegmentId = loadedSegmentId != null && loadedSegmentId !== -1 ? loadedSegmentId : 1;
            if (segmentField && typeof segmentField.getValue === 'function') {
                originalSystemSegmentId = normalizeSystemSegmentId(segmentField.getValue());
            } else {
                originalSystemSegmentId = normalizeSystemSegmentId(loadedSegmentId);
            }

            // Verify that dropdowns were set correctly
            verifyDropdownSelections();

            console.log('System data loaded successfully');
            
            // ⚠️ CRITICAL: Re-apply DFCR locks after populating form
            // This ensures locks are applied based on current Auto CR state from backend
            if (window.reapplyDFCRLocks) {
                setTimeout(async () => {
                    await window.reapplyDFCRLocks('System', id);
                }, 50);
            }
        } catch (err) {
            console.error('Failed to load system:', err);
            if (typeof window.showNotification === 'function') {
                window.showNotification(msg('system.edit.messages.failedToLoadSystemData', null, 'Failed to load system data'), 'error');
            } else { alert(msg('system.edit.messages.failedToLoadSystemData', null, 'Failed to load system data')); }
        }
    }

    // Helper function to verify dropdown selections
    function verifyDropdownSelections() {
        const dropdowns = [
            'typeSelect', 'BUDGStatus', 'lifecycle', 'BUDGViewing',
            'classification', 'ciaC', 'ciaI', 'ciaA'
        ];

        console.log('=== Verifying Dropdown Selections ===');
        dropdowns.forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                const selectedOption = el.options[el.selectedIndex];
                console.log(`${id}: value="${el.value}", text="${selectedOption ? selectedOption.textContent : 'N/A'}", index=${el.selectedIndex}`);

                // Check if it's still on the default empty option
                if (el.selectedIndex === 0 && selectedOption && selectedOption.disabled) {
                    console.warn(`⚠️ ${id} is still on default empty option - value may not have been set correctly`);
                }
            } else {
                console.warn(`⚠️ Element ${id} not found`);
            }
        });
        console.log('=== End Verification ===');
    }

    async function loadDropdowns() {
        try {
            console.log('Starting to load dropdowns...');

            // Initialize dropdowns using system.js functionality if available
            if (typeof initSystemFormDropdowns === 'function') {
                console.log('Using initSystemFormDropdowns function');

                // The initSystemFormDropdowns function returns a Promise.all
                // We need to properly await it to ensure all dropdowns are loaded
                return new Promise((resolve) => {
                    try {
                        const result = initSystemFormDropdowns();
                        console.log('initSystemFormDropdowns result:', result);

                        if (result && typeof result.then === 'function') {
                            result.then(data => {
                                console.log('Dropdown data loaded successfully:', data);
                                resolve(data);
                            }).catch(err => {
                                console.error('Error in initSystemFormDropdowns:', err);
                                resolve(); // Resolve anyway to continue
                            });
                        } else {
                            console.log('initSystemFormDropdowns did not return a Promise');
                            resolve(); // Not a promise, just resolve
                        }
                    } catch (error) {
                        console.error('Error calling initSystemFormDropdowns:', error);
                        resolve(); // Resolve to continue
                    }
                });
            } else {
                console.warn('initSystemFormDropdowns function not available');

                // Fallback: Load dropdowns directly
                console.log('Using fallback dropdown loading');
                const svc = window.BUDG_API_SERVICE;
                if (!svc) {
                    console.error('BUDG_API_SERVICE not available');
                    return;
                }

                try {
                    const [systems, statuses, lifecycles, systemTypes, viewings, ciaRatings, classifications] = await Promise.all([
                        svc.getSystemsList().catch(e => { console.error('Failed to load systems:', e); return []; }),
                        svc.getStatusList().catch(e => { console.error('Failed to load statuses:', e); return []; }),
                        svc.getLifecycleList().catch(e => { console.error('Failed to load lifecycles:', e); return []; }),
                        svc.getSystemTypeList?.() ? svc.getSystemTypeList().catch(e => { console.error('Failed to load system types:', e); return []; }) : Promise.resolve([]),
                        svc.getViewingList().catch(e => { console.error('Failed to load viewings:', e); return []; }),
                        svc.getCiaRatings().catch(e => { console.error('Failed to load CIA ratings:', e); return []; }),
                        svc.getSystemClassifications().catch(e => { console.error('Failed to load classifications:', e); return []; })
                    ]);

                    console.log('Dropdown data loaded directly:', { systems, statuses, lifecycles, systemTypes, viewings, ciaRatings, classifications });

                    // Fill dropdowns manually and return the data for later use
                    await fillDropdowns(statuses, lifecycles, systemTypes, viewings, classifications, ciaRatings);

                    // Return the loaded data so it can be used for setting values
                    return { statuses, lifecycles, systemTypes, viewings, ciaRatings, classifications };
                } catch (error) {
                    console.error('Failed to load dropdowns directly:', error);
                    return null;
                }
            }
        } catch (err) {
            console.error('Failed to load dropdowns:', err);
            return null;
        }
    }

    async function refreshSystemParentOptionsForCurrentSegment(keepInvalidParent = true) {
        if (typeof initSystemFormDropdowns === 'function') {
            await initSystemFormDropdowns();
        }

        const parentEl = document.getElementById('parentShortName');
        const selectedParentId = parentEl?.dataset?.parentId ? parseInt(parentEl.dataset.parentId, 10) : null;
        const selectedSegmentId = segmentField && typeof segmentField.getValue === 'function'
            ? parseInt(segmentField.getValue(), 10)
            : NaN;

        if (!Number.isInteger(selectedParentId) || selectedParentId <= 0) {
            return true;
        }
        if (!Number.isInteger(selectedSegmentId) || selectedSegmentId <= 0 || !segmentField || typeof segmentField.validateParentForSegment !== 'function') {
            return true;
        }

        const isValid = await segmentField.validateParentForSegment(selectedParentId, selectedSegmentId);
        if (!isValid) {
            if (!keepInvalidParent && parentEl) {
                parentEl.value = '';
                parentEl.dataset.parentId = '';
            }
            return false;
        }
        return true;
    }

    // Helper function to fill dropdowns manually
    async function fillDropdowns(statuses, lifecycles, systemTypes, viewings, classifications, ciaRatings) {
        console.log('Filling dropdowns manually');

        // Helper function to fill a select element
        function fillSelect(selectId, list, labelKey = 'name', allowEmpty = true) {
            const el = document.getElementById(selectId);
            if (!el || !Array.isArray(list)) {
                console.warn(`Cannot fill ${selectId}: element not found or list is not an array`);
                return;
            }

            console.log(`Filling ${selectId} with ${list.length} items`);

            // Save current value if any
            const current = el.value;
            console.log(`Saving current value for ${selectId}: "${current}"`);

            // Clear existing options
            el.innerHTML = '';

            // Add empty option only if allowed (disabled so user can't select it)
            if (allowEmpty) {
                const empty = document.createElement('option');
                empty.value = '';
                empty.textContent = '-- Select --';
                empty.disabled = true;
                empty.selected = true;
                el.appendChild(empty);
            }

            // Sort the list by ID to maintain database order
            const sortedList = [...list].sort((a, b) => (a.id || 0) - (b.id || 0));

            // Add options from sorted list
            sortedList.forEach(item => {
                if (!item) return;

                const opt = document.createElement('option');
                const itemId = item.id != null ? String(item.id) : '';
                const itemName = item[labelKey] || item.name || '';
                opt.value = itemId;
                opt.textContent = itemName;

                el.appendChild(opt);
            });

            console.log(`Filled ${selectId} with ${el.options.length} options (including empty option if any)`);
            console.log(`Options for ${selectId}:`, Array.from(el.options).map(o => ({ value: o.value, text: o.textContent })));

            // Restore current value if it was set
            if (current) {
                el.value = current;
                // Verify the value was restored
                if (el.value !== current) {
                    console.warn(`Failed to restore value "${current}" for ${selectId}. Current value: "${el.value}"`);
                } else {
                    console.log(`✓ Successfully restored value "${current}" for ${selectId}`);
                }
            }
        }

        // Helper function to fill CIA dropdowns
        function fillCia(selectId, ratings, allowEmpty = true) {
            const el = document.getElementById(selectId);
            if (!el || !Array.isArray(ratings)) return;

            console.log(`Filling ${selectId} with ${ratings.length} ratings`);

            // Save current value if any
            const current = el.value;

            // Clear existing options
            el.innerHTML = '';

            // Add empty option only if allowed (disabled so user can't select it)
            if (allowEmpty) {
                const empty = document.createElement('option');
                empty.value = '';
                empty.textContent = '-- Select --';
                empty.disabled = true;
                empty.selected = true;
                el.appendChild(empty);
            }

            // Add options from ratings
            ratings.forEach(r => {
                if (!r) return;

                const opt = document.createElement('option');
                opt.value = r.id != null ? String(r.id) : '';
                opt.textContent = String(r.values || '');
                el.appendChild(opt);
            });

            // Restore current value if it was set
            if (current) el.value = current;
        }

        // Helper function to fill a select element using name as value
        function fillSelectByName(selectId, list, labelKey = 'name') {
            const el = document.getElementById(selectId);
            if (!el || !Array.isArray(list)) return;

            console.log(`Filling ${selectId} with ${list.length} items (using name as value)`);

            // Save current value if any
            const current = el.value;

            // Clear existing options
            el.innerHTML = '';

            // Add empty option
            const empty = document.createElement('option');
            empty.value = '';
            empty.textContent = 'Select Type...';
            el.appendChild(empty);

            // Add options using name as value
            list.forEach(item => {
                const opt = document.createElement('option');
                opt.value = item[labelKey] || item.name || ''; // Use name as value
                opt.textContent = item[labelKey] || item.name || '';
                el.appendChild(opt);
            });

            // Restore current value if it was set
            if (current) el.value = current;
        }

        // Fill all dropdowns
        console.log('Filling dropdowns with data:', {
            systemTypes: systemTypes?.length || 0,
            statuses: statuses?.length || 0,
            lifecycles: lifecycles?.length || 0,
            viewings: viewings?.length || 0,
            classifications: classifications?.length || 0,
            ciaRatings: ciaRatings?.length || 0
        });

        // Required fields: Type - use ID as value since Type column stores the foreign key ID
        fillSelect('typeSelect', systemTypes, 'name', false);

        // Optional dropdowns - allow empty option
        fillSelect('BUDGStatus', statuses, 'name', true);
        fillSelect('lifecycle', lifecycles, 'name', true);
        fillSelect('BUDGViewing', viewings, 'name', true);
        fillSelect('classification', classifications, 'name', true);
        fillCia('ciaC', ciaRatings, true);
        fillCia('ciaI', ciaRatings, true);
        fillCia('ciaA', ciaRatings, true);

        console.log('All dropdowns filled successfully');
    }

    function collectFormData() {
        const shortName = document.getElementById('shortName')?.value.trim();
        const parentShortName = document.getElementById('parentShortName')?.value.trim();
        const description = document.getElementById('description')?.value.trim();
        const typeId = document.getElementById('typeSelect')?.value.trim();
        const scope = document.getElementById('scopeSelect')?.value;
        const longName = document.getElementById('longName')?.value.trim();
        const url = document.getElementById('url')?.value.trim();
        const dqAutomation = document.getElementById('dqAutomation')?.checked;
        const BUDGStatus = document.getElementById('BUDGStatus')?.value;
        const lifecycle = document.getElementById('lifecycle')?.value;
        const BUDGViewing = document.getElementById('BUDGViewing')?.value;
        const classification = document.getElementById('classification')?.value;
        const ciaC = document.getElementById('ciaC')?.value;
        const ciaI = document.getElementById('ciaI')?.value;
        const ciaA = document.getElementById('ciaA')?.value;

        // Get parent ID from dataset if available
        let parentId = null;
        const parentEl = document.getElementById('parentShortName');
        if (parentEl && parentEl.dataset.parentId) {
            parentId = parseInt(parentEl.dataset.parentId, 10);
            if (isNaN(parentId)) parentId = null;
        }

        // Log the form data being collected with dropdown text values for verification
        const getDropdownText = (id) => {
            const el = document.getElementById(id);
            if (el && el.selectedIndex >= 0) {
                const option = el.options[el.selectedIndex];
                return option ? option.textContent : '';
            }
            return '';
        };

        console.log('Collecting form data:', {
            shortName, parentShortName, description, typeId, scope, longName, url,
            dqAutomation, BUDGStatus, lifecycle, BUDGViewing, classification,
            ciaC, ciaI, ciaA, parentId
        });

        console.log('Dropdown display values:', {
            typeText: getDropdownText('typeSelect'),
            statusText: getDropdownText('BUDGStatus'),
            lifecycleText: getDropdownText('lifecycle'),
            viewingText: getDropdownText('BUDGViewing'),
            classificationText: getDropdownText('classification'),
            ciaCText: getDropdownText('ciaC'),
            ciaIText: getDropdownText('ciaI'),
            ciaAText: getDropdownText('ciaA')
        });

        // Create a minimal payload with field names matching what the backend expects
        // Based on the SystemServlet.java code
        const payload = {
            // Essential fields - match the exact field names from SystemServlet.java
            name: shortName,
            description: description,
            Type: typeId ? parseInt(typeId, 10) : null,

            // Boolean fields - backend expects integer values
            external: scope === 'External' ? 1 : 0,
            dq_automation: dqAutomation ? 1 : 0,

            // Segment field
            segmentId: segmentField ? segmentField.getValue() : null,

            // Optional text fields with backend field names
            ...(longName && longName.trim() !== '' ? { long_name: longName } : {}),
            ...(url && url.trim() !== '' ? { url } : {})
        };

        if (BUDGStatus && BUDGStatus.trim() !== '') {
            const statusNum = parseInt(BUDGStatus, 10);
            if (!isNaN(statusNum)) {
                payload.status = statusNum;
            }
        }

        if (lifecycle && lifecycle.trim() !== '') {
            const lifecycleNum = parseInt(lifecycle, 10);
            if (!isNaN(lifecycleNum)) {
                payload.lifecycle = lifecycleNum;
            }
        }

        if (BUDGViewing && BUDGViewing.trim() !== '') {
            const viewingNum = parseInt(BUDGViewing, 10);
            if (!isNaN(viewingNum)) {
                payload.is_public = viewingNum; // Backend expects is_public
            }
        }

        if (classification && classification.trim() !== '') {
            const classNum = parseInt(classification, 10);
            if (!isNaN(classNum)) {
                payload.classification = classNum;
            }
        }

        // CIA ratings - only include if valid numbers
        if (ciaC && ciaC.trim() !== '') {
            const ciaCNum = parseInt(ciaC, 10);
            if (!isNaN(ciaCNum)) {
                payload.confidentiality_rating = ciaCNum; // Backend expects confidentiality_rating
            }
        }

        if (ciaI && ciaI.trim() !== '') {
            const ciaINum = parseInt(ciaI, 10);
            if (!isNaN(ciaINum)) {
                payload.integrity_rating = ciaINum; // Backend expects integrity_rating
            }
        }

        if (ciaA && ciaA.trim() !== '') {
            const ciaANum = parseInt(ciaA, 10);
            if (!isNaN(ciaANum)) {
                payload.availability_rating = ciaANum; // Backend expects availability_rating
            }
        }

        // Parent relationship - backend expects parent_id directly
        if (parentId) {
            payload.parent_id = parentId;
        }

        console.log('Prepared payload for API:', payload);
        return payload;
    }

    async function saveSystem(id, closeAfter = false) {
        // Prevent multiple simultaneous saves
        if (window._systemSaving) {
            console.log('Save operation already in progress, skipping...');
            return;
        }

        try {
            window._systemSaving = true;
            const activeTab = getCurrentActiveTab();
            console.log(`Starting save operation for tab: ${activeTab}`);

            // Relationships tab has no save - show message and return
            if (activeTab === 'relationships') {
                if (typeof window.showNotification === 'function') {
                    window.showNotification(msg('system.edit.messages.noChangesToSaveInTab', null, 'No changes to save in this tab.'), 'info');
                } else { alert(msg('system.edit.messages.noChangesToSaveInTab', null, 'No changes to save in this tab.')); }
                window._systemSaving = false;
                return;
            }

            // Validate summary fields only when saving the Summary tab
            if (activeTab === 'summary') {
                // Sync rich-text editor content back to textarea before reading
                if (typeof syncAdvancedRichTextToTextarea === 'function') {
                    syncAdvancedRichTextToTextarea('description');
                }
                const payload = collectFormData();
                if (!payload.name || payload.name.trim() === '') {
                    const m = msg('system.edit.messages.shortNameRequired', null, 'Error: Short Name is required. You must enter a Short Name for the System.');
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    document.getElementById('shortName')?.focus();
                    window._systemSaving = false;
                    const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
                    buttons.forEach(b => {
                        if (b) { b.disabled = false; if (b.dataset._txt) b.textContent = b.dataset._txt; }
                    });
                    return;
                }
                if (!payload.description || payload.description.trim() === '') {
                    const m = msg('system.edit.messages.descriptionRequired', null, 'Error: Description is required. You must enter a Description for the System.');
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    document.getElementById('description')?.focus();
                    window._systemSaving = false;
                    const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
                    buttons.forEach(b => {
                        if (b) { b.disabled = false; if (b.dataset._txt) b.textContent = b.dataset._txt; }
                    });
                    return;
                }
                if (!payload.Type) {
                    const m = msg('system.edit.messages.typeRequired', null, 'Error: Type is required. You must select a System Type.');
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    document.getElementById('typeSelect')?.focus();
                    window._systemSaving = false;
                    const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
                    buttons.forEach(b => {
                        if (b) { b.disabled = false; if (b.dataset._txt) b.textContent = b.dataset._txt; }
                    });
                    return;
                }
                // Client-side uniqueness check for Summary tab only
                // Skip duplicate check if there's an active CR (server will handle it correctly, excluding nobject)
                let skipDuplicateCheck = false;
                try {
                    const statusResponse = await fetch(`/api/pending-changes/status/System/${id}`, { credentials: 'include' });
                    if (statusResponse.ok) {
                        const statusData = await statusResponse.json();
                        if (statusData.underRevision === true && statusData.isAutoCR === true) {
                            skipDuplicateCheck = true;
                            console.log('[System Edit] Active auto CR detected, skipping client-side duplicate check (server will handle it)');
                        }
                    }
                } catch (err) {
                    console.warn('Could not check CR status for duplicate check skip:', err);
                }
                
                if (!skipDuplicateCheck) {
                    try {
                        const list = await window.BUDG_API_SERVICE.getSystemsList();
                        const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                        const nameVal = String(payload.name || '').trim().toLowerCase();
                        const longNameVal = String(payload.long_name || '').trim().toLowerCase();
                        const currentId = parseInt(id, 10);
                        const nameClash = rows.some(r => {
                            const recordId = r.id ?? r.ID;
                            if (recordId === currentId) return false;
                            return String(r.name || '').trim().toLowerCase() === nameVal;
                        });
                        if (nameClash) {
                            const m = msg('common.messages.duplicateSystemShortName', null, 'Error: Short Name already exists. The Short Name must be unique within Systems.');
                            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                            document.getElementById('shortName')?.focus();
                            window._systemSaving = false;
                            const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
                            buttons.forEach(b => { if (b) { b.disabled = false; if (b.dataset._txt) b.textContent = b.dataset._txt; } });
                            return;
                        }
                        if (longNameVal) {
                            const longNameClash = rows.some(r => {
                                const recordId = r.id ?? r.ID;
                                if (recordId === currentId) return false;
                                return String(r.longName || r.Long_Name || '').trim().toLowerCase() === longNameVal;
                            });
                            if (longNameClash) {
                                const m = msg('common.messages.duplicateSystemLongName', null, 'Error: Long Name already exists. The Long Name must be unique within Systems.');
                                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                                document.getElementById('longName')?.focus();
                                window._systemSaving = false;
                                const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
                                buttons.forEach(b => { if (b) { b.disabled = false; if (b.dataset._txt) b.textContent = b.dataset._txt; } });
                                return;
                            }
                        }
                    } catch (err) {
                        console.warn('Client-side duplicate check failed, falling back to server-side validation:', err);
                    }
                }
            }

            const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
            buttons.forEach(b => {
                if (b) { b.disabled = true; b.dataset._txt = b.textContent; b.textContent = 'Saving...'; }
            });

            // Check if the active tab has changes (only that tab)
            if (!hasAnyDataChanged()) {
                console.log('No data changes detected in active tab, skipping save');
                window._systemSaving = false;
                buttons.forEach(b => { if (b) { b.disabled = false; if (b.dataset._txt) b.textContent = b.dataset._txt; } });
                if (closeAfter) {
                    window.location.href = `/view/system/${id}`;
                } else {
                    const m = msg('system.edit.messages.noChangesToSave', null, 'No changes to save');
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'info'); } else { alert(m); }
                }
                return;
            }

            let saveSuccess = false;

            if (activeTab === 'summary') {
                try {
                    if (segmentField && !segmentField.validate()) {
                        restoreButtons();
                        return;
                    }
                    const payload = collectFormData();
                    const updatePayload = { ...payload, id: parseInt(id, 10) };
                    const res = await window.BUDG_API_SERVICE.updateSystem(id, updatePayload);
                    if (res && res.success === false) {
                        if (res.locked) {
                            const lockedBy = res.lockedBy || 'another user';
                            const isPermanent = res.isPermanent || false;
                            const key = isPermanent ? 'system.edit.messages.systemPermanentLockBy' : 'system.edit.messages.systemLockedBy';
                            const m = msg(key, { lockedBy }, `This system is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`);
                            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                            if (window.currentLockManager) await window.currentLockManager.releaseLock();
                            setTimeout(() => { window.location.href = `/view/system/${id}`; }, 3000);
                            window._systemSaving = false;
                            return false;
                        }
                        throw new Error(res.message || 'Update failed');
                    }
                    saveSuccess = true;
                    if (window.customFieldsContext && window.customFieldsContext.saveValues && id) {
                        await window.customFieldsContext.saveValues(id);
                    }
                    // Trigger impact save when segment changed (re-validate cross-segment links).
                    if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                        const systemImpactDirty = typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
                        const currentSystemSegment = normalizeSystemSegmentId(payload.segmentId ?? (segmentField && segmentField.getValue()));
                        const systemSegmentChanged = currentSystemSegment !== normalizeSystemSegmentId(originalSystemSegmentId);
                        if (systemImpactDirty || systemSegmentChanged) {
                            try {
                                if (systemSegmentChanged && !systemImpactDirty && window.initImpactEdit) {
                                    console.log('=== Segment changed; reloading system impact from server before save ===');
                                    window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                                    await window.initImpactEdit(id, editViewMode);
                                }
                                const sysImpactResult = await window.saveAllImpactData(id);
                                if (sysImpactResult && sysImpactResult.success !== false) {
                                    window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                                    if (window.initImpactEdit) await window.initImpactEdit(id, editViewMode);
                                    console.log('✅ Impact saved on segment change');
                                }
                            } catch (sysImpactError) {
                                console.error('Error saving system impact on segment change:', sysImpactError);
                            }
                        }
                    }
                    originalSystemSegmentId = normalizeSystemSegmentId(payload.segmentId ?? (segmentField && segmentField.getValue()));
                } catch (apiError) {
                    console.error('API error during save:', apiError);
                    // Always preserve server reason (body.error / body.message); never replace with generic 400 text
                    const errorMessage = (apiError && apiError.body && (apiError.body.error || apiError.body.message))
                        || (apiError && apiError.message)
                        || 'Failed to save';
                    throw new Error(errorMessage);
                }
            } else if (activeTab === 'stakeholders') {
                try {
                    saveSuccess = await saveStakeholdersData();
                    if (saveSuccess) console.log('✅ Stakeholders saved successfully');
                } catch (stakeholdersError) {
                    console.error('Error saving stakeholders:', stakeholdersError);
                    throw stakeholdersError;
                }
            } else if (activeTab === 'impact') {
                try {
                    if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                        const impactResult = await window.saveAllImpactData(id);
                        saveSuccess = impactResult && impactResult.success !== false;
                        if (saveSuccess && window.initImpactEdit && id) {
                            window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                            await window.initImpactEdit(id, editViewMode);
                        }
                        if (saveSuccess) console.log('✅ Impact saved successfully');
                    } else {
                        const m = msg('system.edit.messages.impactSaveNotAvailable', null, 'Impact save not available.');
                        if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    }
                } catch (impactError) {
                    console.error('Error saving impact:', impactError);
                    throw impactError;
                }
            }

            if (!saveSuccess) {
                throw new Error('Failed to save data');
            }

            // Release lock after successful save
            if (window.currentLockManager) {
                await window.currentLockManager.releaseLock();
            }

            if (typeof window.showNotification === 'function') {
                window.showNotification(msg('system.edit.messages.updatesSaved', null, 'UPDATES SAVED'), 'success');
            } else { alert(msg('system.edit.messages.updatesSaved', null, 'UPDATES SAVED')); }

            if (closeAfter) {
                setTimeout(() => {
                    window.location.href = `/view/system/${id}`;
                }, 1500);
            } else {
                // Reload the page to refresh data
                window.location.reload();
            }
        } catch (err) {
            console.error('Save error:', err);
            const errorMsg = typeof window.formatSaveError === 'function'
                ? window.formatSaveError(err)
                : (err?.body?.error || err?.body?.message || err?.message || 'Failed to save');
            if (typeof window.showNotification === 'function') { window.showNotification(errorMsg, 'error'); } else { alert(errorMsg); }
        } finally {
            window._systemSaving = false;
            // Re-enable buttons and restore text
            const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
            buttons.forEach(b => {
                if (b) {
                    b.disabled = false;
                    if (b.dataset._txt) b.textContent = b.dataset._txt;
                }
            });
        }
    }




    document.addEventListener('DOMContentLoaded', async function () {
        try {
            // Only run lock acquisition on edit pages, not view pages
            const isEditPage = document.getElementById('systemEditContainer') !== null || 
                              window.location.pathname.includes('/system-edit') ||
                              window.location.pathname.includes('/edit');
            
            if (!isEditPage) {
                console.log('[System Edit] Not on edit page, skipping lock acquisition');
                return;
            }

            const id = parseId();
            if (!id) {
                const m = msg('system.edit.messages.noSystemIdProvided', null, 'No system ID provided');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                window.location.href = '/';
                return;
            }

            console.log('Initializing system edit page for system ID:', id);

            // Initialize lock manager
            const lockManager = new window.LockManager('system', id);
            window.currentLockManager = lockManager;

            // Setup auto-release on page unload
            lockManager.setupBeforeUnload();

            // Check lock status
            const lockStatus = await lockManager.checkLockStatus();
            const status = lockStatus?.status || 'no_lock';

            // Handle lock conflicts
            if (status === 'locked_by_other') {
                const lockedBy = lockStatus.lockedByName || 'another user';
                const m = msg('system.edit.messages.systemLockedBy', { lockedBy }, `This system is currently locked by ${lockedBy}. Please try again later.`);
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                window.location.href = `/view/system/system.html?id=${id}`;
                return;
            } else if (status === 'permanently_locked') {
                const isSuperAdmin = await lockManager.checkIsSuperAdmin();
                if (!isSuperAdmin) {
                    const lockedBy = lockStatus.lockedByName || 'an administrator';
                    const m = msg('system.edit.messages.systemPermanentLockBy', { lockedBy }, `This system has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    window.location.href = `/view/system/system.html?id=${id}`;
                    return;
                }
            }

            // Acquire lock
            const lockResult = await lockManager.acquireLock(false);
            if (!lockResult || !lockResult.success) {
                // Check if we have details about who locked it
                if (lockResult && lockResult.lockedBy) {
                    const lockedBy = lockResult.lockedBy;
                    const m = msg('system.edit.messages.objectLockedBy', { lockedBy }, `The object is currently locked by ${lockedBy}. Try again later.`);
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                } else {
                    const m = msg('system.edit.messages.failedToAcquireLock', null, 'Failed to acquire lock. Please try again.');
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                }
                window.location.href = `/view/system/system.html?id=${id}`;
                return;
            }

            // Update global lock count if available
            if (window.globalLockUI) {
                await window.globalLockUI.updateLockCount();
            }

            // First load dropdowns - this must complete before setting values
            console.log('Loading dropdowns...');
            const dropdownData = await loadDropdowns();
            console.log('Dropdowns loaded successfully');

            // Small delay to ensure DOM is fully updated and dropdowns are populated
            await new Promise(resolve => setTimeout(resolve, 200));

            // Verify typeSelect dropdown is populated
            const typeSelectCheck = document.getElementById('typeSelect');
            if (typeSelectCheck) {
                console.log(`Type dropdown has ${typeSelectCheck.options.length} options after loadDropdowns`);
                if (typeSelectCheck.options.length <= 1) {
                    console.warn('Type dropdown appears empty, waiting additional time...');
                    await new Promise(resolve => setTimeout(resolve, 300));
                    console.log(`Type dropdown now has ${typeSelectCheck.options.length} options`);
                }
            }

            // Initialize segment field
            console.log('Initializing segment field...');
            if (window.SegmentField) {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    // Removed defaultValue - let API data set the correct value
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'System',
                    fieldId: 'sysSegment',
                    errorId: 'sysSegmentError',
                    onChange: async () => {
                        const isParentValid = await refreshSystemParentOptionsForCurrentSegment(true);
                        if (!isParentValid) {
                            alert('This parent is not valid for the selected segment. Please remove the parent first.');
                            return false;
                        }
                        return true;
                    }
                });
            }

            // Always start with 'original' view - user must manually switch to 'changes' via toggle
            editViewMode = await determineEditViewMode(id);

            // Set up toggle listener to update editViewMode when user switches views
            document.addEventListener('pendingChangesViewSwitch', function(event) {
                const newView = event.detail?.view;
                const facetType = event.detail?.facetType;
                const objectId = event.detail?.objectId;
                
                // Only handle events for System facet and current system
                if (facetType === 'System' && objectId === id) {
                    if (newView === 'original' || newView === 'changes') {
                        console.log('[System Edit] View switched to:', newView);
                        editViewMode = newView;
                        
                        // Reload system data with new view mode
                        const systemId = parseId();
                        if (systemId) {
                            loadSystem(systemId, editViewMode);
                            
                            // Reload data content summary (always in summary tab now)
                            loadDataContentEdit(systemId, editViewMode);
                            
                            const activeTab = getCurrentActiveTab();
                            
                            // Reload stakeholders if stakeholders tab is active
                            if (activeTab === 'stakeholders' && window.SystemStakeholderEdit) {
                                window.SystemStakeholderEdit.init(systemId, editViewMode);
                            }
                            
                            // Reload impact if impact tab is active
                            if (activeTab === 'impact' && window.initImpactEdit) {
                                window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                                window.initImpactEdit(systemId, editViewMode);
                            }
                        }
                    }
                }
            });

            // Then load existing system data and set form values
            console.log('Loading system data... viewMode=', editViewMode);
            await loadSystem(id, editViewMode);
            // Rebuild parent picker options using the loaded system segment value.
            await refreshSystemParentOptionsForCurrentSegment(true);

            // Load data content summary (now in summary tab)
            loadDataContentEdit(id, editViewMode);

            // Initialize custom fields
            if (window.CustomFields) {
                try {
                    window.customFieldsContext = await window.CustomFields.initForm({
                        facetId: 'System',
                        containerId: 'customFieldsContainer',
                        mode: 'edit',
                        objectId: id
                    });
                    console.log('Custom fields initialized:', window.customFieldsContext);
                } catch (error) {
                    console.error('Error initializing custom fields:', error);
                }
            }

            // Apply DFCR locked fields for System facet AFTER loading data and setting values
            // NOTE: This is an EDIT page. Locks will be applied only if workflow_create_id == workflow_edit_id
            // If only workflow_edit_id is set (different from workflow_create_id), locks will NOT be applied
            if (window.DFCRUtils) {
                try {
                    // Get system ID first to pass to DFCRUtils
                    const systemId = parseId();

                    await window.DFCRUtils.applyLockedFields('System', {
                        status: '#BUDGStatus',
                        lifecycle: '#lifecycle'
                    }, { isEditPage: true, objectId: systemId });
                    console.log('DFCR locked fields check done for System edit page (objectId:', systemId, ')');
                } catch (dfcrError) {
                    console.warn('Error applying DFCR locked fields:', dfcrError);
                }
            }

            // Check if edit workflow is enabled and show Save & Submit button
            // This runs after page load to ensure button is visible when workflow_edit_id is set
            if (window.DFCRUtils) {
                try {
                    // Try multiple facet name variations to handle different naming conventions
                    let dfcrInfo = null;
                    const facetNameVariations = ['System', 'Systems'];

                    for (const facetName of facetNameVariations) {
                        try {
                            console.log('[System Edit] Trying DFCR lookup with facet name:', facetName);
                            const systemIdForDFCR = parseId();
                            dfcrInfo = await window.DFCRUtils.getInfo(facetName, systemIdForDFCR);
                            if (dfcrInfo && (dfcrInfo.editWorkflowEnabled !== undefined || dfcrInfo.editWorkflowId !== undefined)) {
                                console.log('[System Edit] ✅ DFCR Info found for facet:', facetName);
                                break;
                            }
                        } catch (e) {
                            console.warn('[System Edit] Failed to get DFCR info for:', facetName, e);
                            continue;
                        }
                    }

                    console.log('[System Edit] DFCR Info:', dfcrInfo);
                    console.log('[System Edit] DFCR Info keys:', dfcrInfo ? Object.keys(dfcrInfo) : 'null');

                    // Check if object is under revision
                    let isUnderRevision = false;
                    try {
                        const statusRes = await fetch(`/api/pending-changes/status/System/${id}`, {
                            method: 'GET',
                            credentials: 'include'
                        });
                        if (statusRes.ok) {
                            const statusData = await statusRes.json();
                            isUnderRevision = statusData.underRevision === true;
                            console.log('[System Edit] System under revision:', isUnderRevision);
                        }
                    } catch (e) {
                        console.warn('[System Edit] Could not check revision status:', e);
                    }

                    const editWorkflowId = (dfcrInfo && dfcrInfo.editWorkflowId) ? dfcrInfo.editWorkflowId : 0;
                    const editWorkflowEnabled = dfcrInfo && dfcrInfo.editWorkflowEnabled === true;
                    const adminBypassEnabled = dfcrInfo && dfcrInfo.adminBypassEnabled === true;
                    const hasEditWorkflow = editWorkflowId > 0;

                    // Show button if:
                    // 1. editWorkflowId has a value (workflow_edit_id is set), OR
                    // 2. editWorkflowEnabled is true AND admin bypass is not enabled, OR
                    // 3. object is under revision
                    const shouldShowButton = hasEditWorkflow || (editWorkflowEnabled && !adminBypassEnabled) || isUnderRevision;

                    console.log('[System Edit] Button visibility logic:');
                    console.log('  - editWorkflowId:', editWorkflowId);
                    console.log('  - editWorkflowEnabled:', editWorkflowEnabled);
                    console.log('  - adminBypassEnabled:', adminBypassEnabled);
                    console.log('  - hasEditWorkflow:', hasEditWorkflow);
                    console.log('  - isUnderRevision:', isUnderRevision);
                    console.log('  - shouldShowButton:', shouldShowButton);

                    if (shouldShowButton) {
                        const saveSubmitBtn = document.getElementById('saveAndSubmitBtn');
                        console.log('  - saveSubmitBtn element found:', !!saveSubmitBtn);
                        if (saveSubmitBtn) {
                            saveSubmitBtn.style.display = 'inline-block';
                            console.log('[System Edit] ✅ Save & Submit button shown for edit workflow');
                        } else {
                            console.error('[System Edit] ❌ Save & Submit button element not found!');
                        }
                    } else {
                        console.log('[System Edit] ❌ Save & Submit button will NOT be shown');
                    }
                } catch (dfcrError) {
                    console.warn('[System Edit] Error checking DFCR settings:', dfcrError);
                }
            }

            // Wire up action buttons
            const saveBtn = document.getElementById('editSaveBtn');
            const saveCloseBtn = document.getElementById('editSaveCloseBtn');
            const cancelBtn = document.getElementById('editCancelBtn');

            if (saveBtn) saveBtn.addEventListener('click', () => saveSystem(id, false));
            if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => saveSystem(id, true));
            if (cancelBtn) cancelBtn.addEventListener('click', async () => {
                // Release lock before canceling
                if (window.currentLockManager) {
                    await window.currentLockManager.releaseLock();
                }
                window.location.href = `/view/system/${id}`;
            });

            // Show editor button – advanced rich text editor
            const editorButton = document.querySelector('.editor-button');
            if (editorButton) {
                editorButton.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    toggleAdvancedRichTextEditor('description', editorButton);
                });
            }

            // Save & Submit button handler - for edit workflow on existing objects
            const saveSubmitBtn = document.getElementById('saveAndSubmitBtn');
            if (saveSubmitBtn) {
                saveSubmitBtn.addEventListener('click', async () => {
                    console.log('Save & Submit clicked - will save and create change request');
                    const systemId = id;

                    const tabName = getCurrentActiveTab();
                    let saveSuccess = false;

                    if (tabName === 'relationships') {
                        const m = msg('system.edit.messages.noChangesToSaveInTab', null, 'No changes to save in this tab.');
                        if (typeof window.showNotification === 'function') { window.showNotification(m, 'info'); } else { alert(m); }
                        return;
                    }
                    if (tabName === 'stakeholders') {
                        if (window.SystemStakeholderEdit && window.SystemStakeholderEdit.saveStakeholders) {
                            saveSuccess = await window.SystemStakeholderEdit.saveStakeholders();
                        }
                    } else if (tabName === 'impact') {
                        if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                            const impactResult = await window.saveAllImpactData(systemId);
                            saveSuccess = impactResult && impactResult.success !== false;
                        } else {
                            saveSuccess = true; // No changes to save
                        }
                    } else {
                        // summary tab
                        saveSuccess = await saveSystem(systemId, false);
                    }

                    if (saveSuccess !== false) {
                        // Check if a CR was auto-created in the save response
                        let changeRequestId = null;

                        // Try to get CR ID from save response
                        if (typeof saveSuccess === 'object' && saveSuccess?.changeRequestId) {
                            changeRequestId = saveSuccess.changeRequestId;
                        } else if (typeof saveSuccess === 'object' && saveSuccess?.pendingChanges && saveSuccess?.id) {
                            changeRequestId = saveSuccess.id;
                        }

                        // Changes are saved - backend auto-creates CR when workflow is enabled
                        // Do NOT create CR manually from frontend to avoid duplicates
                        if (changeRequestId) {
                            const m = msg('system.edit.messages.changesSavedSubmitted', { changeRequestId }, 'Changes saved and submitted for approval. Change Request ID: ' + changeRequestId);
                            if (typeof window.showNotification === 'function') { window.showNotification(m, 'success'); } else { alert(m); }
                        } else {
                            const m = msg('system.edit.messages.changesSavedSuccess', null, 'Changes saved successfully.');
                            if (typeof window.showNotification === 'function') { window.showNotification(m, 'success'); } else { alert(m); }
                        }
                        window.location.href = `/view/system/${systemId}`;
                    }
                });
            }

            // Setup tab functionality
            setupTabFunctionality();

            // Check if there's a specific tab to open from URL parameter
            const urlParams = new URLSearchParams(window.location.search);
            const tabParam = urlParams.get('tab');
            const isStakeholderOnly = urlParams.get('stakeholderOnly') === 'true';
            
            if (tabParam) {
                // Find and click the specified tab
                const targetTab = document.querySelector(`[data-tab="${tabParam}"]`);
                if (targetTab) {
                    targetTab.click();
                }
            }
            
            // Stakeholder-only mode: disable all other tabs when opened from view page with auto CR active
            if (isStakeholderOnly && tabParam === 'stakeholders') {
                console.log('[System Edit] Stakeholder-only mode enabled - locking other tabs');
                document.querySelectorAll('.tab').forEach(function(tab) {
                    if (tab.getAttribute('data-tab') !== 'stakeholders') {
                        tab.disabled = true;
                        tab.classList.add('disabled');
                        tab.style.opacity = '0.4';
                        tab.style.cursor = 'not-allowed';
                        tab.style.pointerEvents = 'none';
                        tab.title = 'Only stakeholder editing is allowed during an active Auto CR';
                    }
                });
            }

            console.log('System edit page initialized successfully');
        } catch (err) {
            console.error('Error initializing system edit page:', err);
            const m = msg('system.edit.messages.failedToInitEditPage', null, 'Failed to initialize the edit page. Please try again.');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
        }
    });


    // Load data content summary for edit page
    async function loadDataContentEdit(systemId, viewMode = 'original') {
        const container = document.getElementById('dataContentContainer');
        if (!container) return;

        try {
            container.innerHTML = '<div class="empty">Loading data content...</div>';

            // Fetch data content from API with view mode support
            const viewParam = viewMode === 'changes' ? 'changes' : null;
            let list = await window.BUDG_API_SERVICE.getSystemDataContent(systemId, viewParam);
            if (list && list.data) list = list.data;
            const rows = Array.isArray(list) ? list : [];

            if (rows.length === 0) {
                container.innerHTML = `
                    <div class="empty-state" style="text-align: center; padding: 3rem;">
                        <i class="fas fa-database" style="font-size: 3rem; color: var(--text-muted, #9ca3af); margin-bottom: 1rem;"></i>
                        <p style="color: var(--text-muted, #6b7280); margin-bottom: 1rem;">No data content relationships found</p>
                        <button type="button" class="btn btn-primary" id="addFirstDataContentBtn">
                            <i class="fas fa-plus"></i> Add First Relationship
                        </button>
                    </div>
                `;

                // Wire up add button
                const addBtn = document.getElementById('addFirstDataContentBtn');
                if (addBtn) {
                    // Remove any existing listeners to prevent duplicates
                    const newBtn = addBtn.cloneNode(true);
                    addBtn.parentNode.replaceChild(newBtn, addBtn);
                    newBtn.addEventListener('click', (e) => {
                        e.preventDefault();
                        console.log('Add first button clicked, systemId:', systemId);
                        openDataContentAddModal(systemId);
                    });
                } else {
                    console.warn('Add first data content button not found');
                }
                return;
            }

            // Render editable table
            const rowsHtml = rows.map((r, index) => {
                const aliasHtml = (Array.isArray(r.aliasNames) && r.aliasNames.length)
                    ? r.aliasNames.map(a => `<span class="view-badge">${escapeHtml(a)}</span>`).join(' ')
                    : '<span class="empty">-</span>';
                const glossaryLink = r.glossaryId != null
                    ? `<a href="/view/glossary/${encodeURIComponent(r.glossaryId)}" target="_blank">${escapeHtml(r.glossary || '')}</a>`
                    : escapeHtml(r.glossary || '');
                const typeBadge = r.glossaryType ? `<span class="view-badge" style="background: var(--primary-50,#eef2ff); color: var(--primary-700,#4338ca); border-color: var(--primary-100,#e0e7ff);">${escapeHtml(r.glossaryType)}</span>` : '<span class="empty">-</span>';

                return `
                    <tr data-relationship-id="${escapeHtml(r.id || '')}" data-index="${index}">
                        <td>${escapeHtml(r.relationshipType || '-')}</td>
                        <td>${glossaryLink}</td>
                        <td>${typeBadge}</td>
                        <td>${escapeHtml(r.definition || '-')}</td>
                        <td>${escapeHtml(r.relationshipStatus || '-')}</td>
                        <td>${aliasHtml}</td>
                        <td style="text-align: center;">
                            <button type="button" class="btn btn-sm btn-secondary" onclick="editDataContentRelationship(${systemId}, ${index})" title="Edit">
                                <i class="fas fa-edit"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteDataContentRelationship(${systemId}, ${index})" title="Delete">
                                <i class="fas fa-trash"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');

            container.innerHTML = `
                <table class="data-table">
                    <thead>
                        <tr>
                            <th><i class="fa-solid fa-link"></i> Relationship Type</th>
                            <th><i class="fa-solid fa-book"></i> Glossary</th>
                            <th><i class="fa-solid fa-tag"></i> Glossary Type</th>
                            <th><i class="fa-solid fa-align-left"></i> Definition</th>
                            <th><i class="fa-regular fa-circle-dot"></i> Relationship Status</th>
                            <th><i class="fa-solid fa-tags"></i> Alias Names</th>
                            <th style="text-align: center;"><i class="fa-solid fa-cog"></i> Actions</th>
                        </tr>
                    </thead>
                    <tbody>${rowsHtml}</tbody>
                </table>
                <div class="table-footer">${rows.length} record${rows.length !== 1 ? 's' : ''}</div>
            `;

            // Store data for later use
            window._systemDataContentRows = rows;

            // Wire up add button
            const addBtn = document.getElementById('addDataContentBtn');
            if (addBtn) {
                // Remove any existing listeners to prevent duplicates
                const newBtn = addBtn.cloneNode(true);
                addBtn.parentNode.replaceChild(newBtn, addBtn);
                newBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    console.log('Add button clicked, systemId:', systemId);
                    openDataContentAddModal(systemId);
                });
            } else {
                console.warn('Add data content button not found');
            }

        } catch (e) {
            console.error('Failed to load data content:', e);
            container.innerHTML = '<div class="empty" style="color:var(--danger,#b91c1c);">Failed to load data content.</div>';
        }
    }

    // Open modal to add data content relationship
    async function openDataContentAddModal(systemId) {
        console.log('openDataContentAddModal called with systemId:', systemId);
        await openDataContentEditModal(systemId, null);
    }

    // Open modal to add or edit data content relationship
    async function openDataContentEditModal(systemId, row = null) {
        const isEditMode = row !== null;
        console.log(`openDataContentEditModal called with systemId: ${systemId}, isEditMode: ${isEditMode}`);
        
        try {
            // Create or get modal
            let modal = document.getElementById('dataContentModal');
            if (!modal) {
                console.log('Creating new modal...');
                modal = createDataContentModal();
                document.body.appendChild(modal);
            }

            // Update modal title
            const modalTitle = modal.querySelector('.modal-title');
            if (modalTitle) {
                modalTitle.textContent = isEditMode ? 'Edit Data Content Relationship' : 'Add Data Content Relationship';
            }

            // Update submit button text
            const submitBtn = modal.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.textContent = isEditMode ? 'Update Relationship' : 'Add Relationship';
            }

            // Reset form
            const form = modal.querySelector('#dataContentForm');
            if (form) {
                form.reset();
                form.dataset.systemId = systemId;
                // Store relationship ID if editing
                if (isEditMode && row.id) {
                    form.dataset.relationshipId = row.id;
                } else {
                    delete form.dataset.relationshipId;
                }
            } else {
                console.error('Form not found in modal');
                return;
            }

            // Load dropdowns
            console.log('Loading dropdowns...');
            await loadDataContentModalDropdowns(modal, systemId);

            // Pre-fill form if editing
            if (isEditMode && row) {
                const glossarySelect = modal.querySelector('#modalGlossarySelect');
                const relationTypeSelect = modal.querySelector('#modalRelationTypeSelect');
                const glossaryTypeInput = modal.querySelector('#modalGlossaryType');

                // Set glossary
                if (glossarySelect && row.glossaryId) {
                    glossarySelect.value = row.glossaryId;
                    // Trigger change to update glossary type
                    glossarySelect.dispatchEvent(new Event('change'));
                }

                // Set relationship type
                if (relationTypeSelect && row.relationTypeId) {
                    relationTypeSelect.value = row.relationTypeId;
                }

                // Set glossary type (read-only, auto-filled)
                if (glossaryTypeInput && row.glossaryType) {
                    glossaryTypeInput.value = row.glossaryType;
                    glossaryTypeInput.style.fontStyle = 'normal';
                    glossaryTypeInput.style.color = 'inherit';
                }
            }

            // Show modal
            console.log('Showing modal...');
            modal.style.display = 'flex';
        } catch (error) {
            console.error('Error opening data content modal:', error);
            const errMsg = error.message || 'Unknown error';
            const m = msg('system.edit.messages.failedToOpenModal', { message: errMsg }, 'Failed to open modal: ' + errMsg);
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
        }
    }

    // Expose to window for button onclick handlers
    window.openDataContentAddModal = openDataContentAddModal;

    // Create modal HTML structure
    function createDataContentModal() {
        const modal = document.createElement('div');
        modal.id = 'dataContentModal';
        modal.className = 'modal-overlay';
        modal.style.cssText = 'position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.5); display: none; align-items: center; justify-content: center; z-index: 10000;';
        modal.innerHTML = `
            <div class="modal-content" style="background: white; border-radius: 8px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); width: 90%; max-width: 600px; max-height: 90vh; overflow-y: auto;">
                <div class="modal-header" style="padding: 1.5rem; border-bottom: 1px solid #e5e7eb; display: flex; justify-content: space-between; align-items: center;">
                    <h3 class="modal-title" style="margin: 0; font-size: 1.25rem; font-weight: 600; color: #111827;">Add Data Content Relationship</h3>
                    <button type="button" class="modal-close" onclick="closeDataContentModal()" style="background: none; border: none; font-size: 1.5rem; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <form id="dataContentForm">
                    <div class="modal-body" style="padding: 1.5rem;">
                        <div class="form-group" style="margin-bottom: 1rem;">
                            <label class="form-label" style="display: block; margin-bottom: 0.5rem; font-weight: 500; color: #374151;">Glossary <span class="required" style="color: #dc2626;">*</span></label>
                            <select class="form-select" id="modalGlossarySelect" required style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.875rem;">
                                <option value="">Select Glossary</option>
                            </select>
                        </div>
                        <div class="form-group" style="margin-bottom: 1rem;">
                            <label class="form-label" style="display: block; margin-bottom: 0.5rem; font-weight: 500; color: #374151;">Relationship Type <span class="required" style="color: #dc2626;">*</span></label>
                            <select class="form-select" id="modalRelationTypeSelect" required style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px; font-size: 0.875rem;">
                                <option value="">Select Relationship Type</option>
                            </select>
                        </div>
                        <div class="form-group" style="margin-bottom: 1rem;">
                            <label class="form-label" style="display: block; margin-bottom: 0.5rem; font-weight: 500; color: #374151;">Glossary Type</label>
                            <input type="text" class="form-input" id="modalGlossaryType" readonly 
                                style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px; background-color: #f8f9fa; color: #6c757d; font-style: italic;" 
                                placeholder="Auto-filled when glossary is selected">
                        </div>
                    </div>
                    <div class="modal-footer" style="padding: 1.5rem; border-top: 1px solid #e5e7eb; display: flex; justify-content: flex-end; gap: 0.75rem;">
                        <button type="button" class="btn btn-secondary" onclick="closeDataContentModal()" style="padding: 0.5rem 1rem; border: 1px solid #d1d5db; background: white; color: #374151; border-radius: 4px; cursor: pointer;">Cancel</button>
                        <button type="submit" class="btn btn-primary" style="padding: 0.5rem 1rem; border: none; background: #248567; color: white; border-radius: 4px; cursor: pointer;">Add Relationship</button>
                    </div>
                </form>
            </div>
        `;

        // Close on outside click
        modal.addEventListener('click', function(e) {
            if (e.target === modal) {
                closeDataContentModal();
            }
        });

        // Handle form submission
        const form = modal.querySelector('#dataContentForm');
        form.addEventListener('submit', async function(e) {
            e.preventDefault();
            await saveDataContentRelationship(modal);
        });

        // Update glossary type when glossary is selected
        const glossarySelect = modal.querySelector('#modalGlossarySelect');
        glossarySelect.addEventListener('change', function() {
            const selectedOption = this.options[this.selectedIndex];
            const glossaryType = selectedOption.dataset.type || '';
            const typeInput = modal.querySelector('#modalGlossaryType');
            if (typeInput) {
                typeInput.value = glossaryType;
                typeInput.style.fontStyle = glossaryType ? 'normal' : 'italic';
                typeInput.style.color = glossaryType ? 'inherit' : '#6c757d';
            }
        });

        return modal;
    }

    // Load dropdowns for modal
    async function loadDataContentModalDropdowns(modal, systemId) {
        const glossarySelect = modal.querySelector('#modalGlossarySelect');
        const relationTypeSelect = modal.querySelector('#modalRelationTypeSelect');

        try {
            // Load glossaries, excluded glossary IDs (Strategic Source), and relationship types in parallel
            const [glossaries, excludedIdsResponse, relationTypes] = await Promise.all([
                window.BUDG_API_SERVICE.getGlossaryList(),
                systemId ? fetch(`/api/system/${systemId}/data-content/excluded-glossary-ids`).then(res => res.ok ? res.json() : []).catch(() => []) : Promise.resolve([]),
                fetch('/api/system/relation-types').then(res => res.json())
            ]);

            const excludedGlossaryIds = new Set((Array.isArray(excludedIdsResponse) ? excludedIdsResponse : []).map(Number));

            // Populate glossary dropdown (exclude glossaries already linked from Strategic Source)
            glossarySelect.innerHTML = '<option value="">Select Glossary</option>';
            const glossaryArray = Array.isArray(glossaries) ? glossaries : (glossaries?.data || []);
            const currentSystemSegmentId = getCurrentSystemSegmentIdForDataContent();
            glossaryArray.forEach(glossary => {
                const glossarySegmentId = resolveGlossarySegmentId(glossary);
                if (Number.isInteger(currentSystemSegmentId) && currentSystemSegmentId > 0 &&
                    Number.isInteger(glossarySegmentId) && glossarySegmentId > 0 &&
                    !isAllowedSystemGlossarySegmentPair(currentSystemSegmentId, glossarySegmentId)) {
                    return;
                }
                const option = document.createElement('option');
                option.value = glossary.id || glossary.ID;
                option.textContent = glossary.name || glossary.Name || glossary.primaryName || glossary.PrimaryName || 'Unnamed Glossary';
                option.dataset.type = glossary.typeName || glossary.TypeName || '';
                option.dataset.segmentId = Number.isInteger(glossarySegmentId) ? String(glossarySegmentId) : '';
                glossarySelect.appendChild(option);
            });

            // Populate relationship type dropdown
            relationTypeSelect.innerHTML = '<option value="">Select Relationship Type</option>';
            const relationTypesArray = Array.isArray(relationTypes) ? relationTypes : (relationTypes?.data || []);
            relationTypesArray.forEach(rt => {
                const option = document.createElement('option');
                option.value = rt.id || rt.ID;
                option.textContent = rt.name || rt.Name || rt.primaryName || rt.PrimaryName || 'Unnamed Type';
                relationTypeSelect.appendChild(option);
            });

        } catch (error) {
            console.error('Failed to load modal dropdowns:', error);
            const m = msg('system.edit.messages.failedToLoadData', null, 'Failed to load data. Please refresh the page and try again.');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
        }
    }

    // Close modal
    window.closeDataContentModal = function() {
        const modal = document.getElementById('dataContentModal');
        if (modal) {
            modal.style.display = 'none';
            const form = modal.querySelector('#dataContentForm');
            if (form) {
                form.reset();
            }
        }
    };

    // Save data content relationship
    async function saveDataContentRelationship(modal) {
        const form = modal.querySelector('#dataContentForm');
        const systemId = form.dataset.systemId;
        const relationshipId = form.dataset.relationshipId;
        const glossaryId = form.querySelector('#modalGlossarySelect').value;
        const relationTypeId = form.querySelector('#modalRelationTypeSelect').value;
        const glossarySelect = form.querySelector('#modalGlossarySelect');

        if (!glossaryId || !relationTypeId) {
            const m = msg('system.edit.messages.selectGlossaryAndRelationshipType', null, 'Please select both Glossary and Relationship Type');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'warning'); } else { alert(m); }
            return;
        }

        try {
            // Segment rule for Data Content:
            // Allowed only when same segment OR either side is Enterprise (ID=1).
            const systemSegmentId = getCurrentSystemSegmentIdForDataContent();
            let glossarySegmentId = parseInt(glossarySelect?.selectedOptions?.[0]?.dataset?.segmentId || '', 10);
            if (!Number.isInteger(glossarySegmentId) || glossarySegmentId <= 0) {
                try {
                    const glossaryResp = await fetch(`/api/glossary/${encodeURIComponent(glossaryId)}`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    if (glossaryResp.ok) {
                        const glossaryData = await glossaryResp.json();
                        glossarySegmentId = resolveGlossarySegmentId(glossaryData);
                    }
                } catch (segmentResolveError) {
                    console.warn('Unable to resolve glossary segment for relationship validation:', segmentResolveError);
                }
            }

            if (Number.isInteger(systemSegmentId) && systemSegmentId > 0 &&
                Number.isInteger(glossarySegmentId) && glossarySegmentId > 0 &&
                !isAllowedSystemGlossarySegmentPair(systemSegmentId, glossarySegmentId)) {
                alert('This glossary is not valid for the selected system segment. Private-to-private cross-segment relationships are not allowed.');
                return;
            }

            // Get current view mode
            const viewMode = editViewMode || 'original';
            
            // Prepare request body - check if this is an update or insert
            const isEditMode = relationshipId !== undefined && relationshipId !== null && relationshipId !== '';
            const requestBody = {
                inserts: [],
                updates: [],
                deletes: []
            };

            if (isEditMode) {
                // Update existing relationship
                requestBody.updates = [{
                    id: parseInt(relationshipId),
                    glossaryId: parseInt(glossaryId),
                    relationTypeId: parseInt(relationTypeId)
                }];
            } else {
                // Insert new relationship
                requestBody.inserts = [{
                    glossaryId: parseInt(glossaryId),
                    relationTypeId: parseInt(relationTypeId)
                }];
            }

            // Determine endpoint - if in changes mode, we need to handle it differently
            let endpoint = `/api/system/${systemId}/data-content`;
            if (viewMode === 'changes') {
                // When in changes mode, the API should handle the CR automatically
                // The endpoint should still work, but we might need to pass view parameter
                endpoint += '?view=changes';
            }

            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                credentials: 'include',
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            console.log('Save response:', result);
            if (result.success) {
                // Close modal
                closeDataContentModal();
                
                // Reload data content table
                await loadDataContentEdit(systemId, viewMode);
                
                // Show success message
                console.log(`Data content relationship ${isEditMode ? 'updated' : 'added'} successfully`);
            } else {
                throw new Error(result.error || 'Failed to save relationship');
            }

        } catch (error) {
            console.error('Error saving data content relationship:', error);
            console.error('Error details:', {
                systemId: systemId,
                glossaryId: glossaryId,
                relationTypeId: relationTypeId,
                viewMode: viewMode,
                error: error.message,
                stack: error.stack
            });
            const saveErrMsg = typeof window.formatSaveError === 'function' ? window.formatSaveError(error) : (error.message || 'Unknown error');
            const m = msg('system.edit.messages.failedToSaveRelationship', { message: saveErrMsg }, 'Failed to save relationship: ' + saveErrMsg);
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }     }
    }

    function getCurrentSystemSegmentIdForDataContent() {
        const fromField = segmentField && typeof segmentField.getValue === 'function'
            ? parseInt(segmentField.getValue(), 10)
            : NaN;
        if (Number.isInteger(fromField) && fromField > 0) {
            return fromField;
        }

        const fromWindow = parseInt(window.currentImpactSegmentId, 10);
        if (Number.isInteger(fromWindow) && fromWindow > 0) {
            return fromWindow;
        }

        return NaN;
    }

    function resolveGlossarySegmentId(glossary) {
        const parsed = parseInt(
            glossary?.segmentId ??
            glossary?.segment_id ??
            glossary?.Segment_ID ??
            glossary?.segmentID ??
            glossary?.segment?.id ??
            glossary?.segment?.ID,
            10
        );
        return Number.isInteger(parsed) && parsed > 0 ? parsed : NaN;
    }

    function isAllowedSystemGlossarySegmentPair(systemSegmentId, glossarySegmentId) {
        if (!Number.isInteger(systemSegmentId) || !Number.isInteger(glossarySegmentId)) {
            return true;
        }
        if (systemSegmentId === glossarySegmentId) {
            return true;
        }
        if (systemSegmentId === 1 || glossarySegmentId === 1) {
            return true;
        }
        return false;
    }

    // Edit data content relationship
    window.editDataContentRelationship = async function (systemId, index) {
        console.log('editDataContentRelationship called with systemId:', systemId, 'index:', index);
        const rows = window._systemDataContentRows || [];
        console.log('Available rows:', rows.length);
        const row = rows[index];
        if (!row) {
            console.error('Relationship not found at index:', index);
            const m = msg('system.edit.messages.relationshipNotFound', null, 'Relationship not found');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            return;
        }
        
        console.log('Row data:', row);
        
        if (!row.id) {
            console.error('Row ID missing:', row);
            const m = msg('system.edit.messages.cannotEditRelationshipIdNotFound', null, 'Cannot edit: Relationship ID not found');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            return;
        }
        
        // Open modal in edit mode
        console.log('Opening edit modal for relationship ID:', row.id);
        await openDataContentEditModal(systemId, row);
    };

    // Delete data content relationship
    window.deleteDataContentRelationship = async function (systemId, index) {
        const rows = window._systemDataContentRows || [];
        const row = rows[index];
        if (!row) {
            const m = msg('system.edit.messages.relationshipNotFound', null, 'Relationship not found');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            return;
        }

        if (!row.id) {
            const m = msg('system.edit.messages.cannotDeleteRelationshipIdNotFound', null, 'Cannot delete: Relationship ID not found');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            return;
        }

        const confirmMessage = `Are you sure you want to delete this data content relationship?\n\nGlossary: ${row.glossary || 'N/A'}\nRelationship Type: ${row.relationshipType || 'N/A'}`;
        const confirmed = await (typeof window.showConfirmDialog === 'function'
            ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
            : Promise.resolve(confirm(confirmMessage)));
        if (!confirmed) return;

        try {
            // Get current view mode
            const viewMode = editViewMode || 'original';
            
            // Prepare request body
            const requestBody = {
                inserts: [],
                updates: [],
                deletes: [parseInt(row.id)]
            };

            // Determine endpoint
            let endpoint = `/api/system/${systemId}/data-content`;
            if (viewMode === 'changes') {
                endpoint += '?view=changes';
            }

            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                credentials: 'include',
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            if (result.success) {
                // Reload data content table
                await loadDataContentEdit(systemId, viewMode);
                console.log('Data content relationship deleted successfully');
            } else {
                throw new Error(result.error || 'Failed to delete relationship');
            }

        } catch (error) {
            console.error('Error deleting data content relationship:', error);
            const errMsg = error.message || 'Unknown error';
            const m = msg('system.edit.messages.failedToDeleteRelationship', { message: errMsg }, 'Failed to delete relationship: ' + errMsg);
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
        }
    };

    // Load system hierarchy data for edit page
    async function loadSystemHierarchy(systemId) {
        const tbody = document.getElementById('relationshipsTableBody');
        if (!tbody) return;

        try {
            // First, check if this system has an active CR and get object_id and nobject_id to exclude
            let excludedIds = new Set();
            try {
                const statusRes = await fetch(`/api/pending-changes/status/System/${systemId}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                });
                if (statusRes.ok) {
                    const status = await statusRes.json();
                    if (status?.underRevision) {
                        // Get all mappings to find object_id and nobject_id
                        // Note: API endpoint is /api/pending-changes/mappings/{facetType}/{objectId}
                        // It automatically gets the active CR internally
                        const mappingsRes = await fetch(`/api/pending-changes/mappings/System/${systemId}`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (mappingsRes.ok) {
                            const mappings = await mappingsRes.json();
                            if (mappings?.success && mappings?.mappings) {
                                // Add object_id (original) to excluded IDs
                                excludedIds.add(systemId);
                                // Add all nobject_id values from mappings
                                Object.values(mappings.mappings).forEach(nobjectId => {
                                    if (nobjectId && typeof nobjectId === 'number') {
                                        excludedIds.add(nobjectId);
                                    }
                                });
                                console.log('[System Hierarchy] Excluding IDs from hierarchy (object_id and nobject_id):', Array.from(excludedIds));
                            }
                        }
                    }
                }
            } catch (e) {
                console.warn('[System Hierarchy] Failed to get pending changes status:', e);
            }

            const hierarchy = await window.BUDG_API_SERVICE.getSystemHierarchy(systemId);

            if (!Array.isArray(hierarchy) || hierarchy.length === 0) {
                tbody.innerHTML = `<tr><td colspan="5" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No hierarchy data available</td></tr>`;
                const footer = document.getElementById('hierarchyFooter');
                if (footer) footer.textContent = '0 records';
                return;
            }

            // Filter out excluded IDs (object_id and nobject_id) from hierarchy
            const filteredHierarchy = hierarchy.filter(item => {
                const itemId = item.id;
                if (excludedIds.has(itemId)) {
                    console.log('[System Hierarchy] Filtering out excluded ID:', itemId);
                    return false;
                }
                return true;
            });

            // Find the current system (level 0) and organize hierarchy
            const currentSystem = filteredHierarchy.find(item => item.level === 0);
            const parentSystems = filteredHierarchy.filter(item => item.level < 0); // Parents have negative levels
            const childSystems = filteredHierarchy.filter(item => item.level > 0); // Children have positive levels

            // Create hierarchy groups
            const hierarchyGroups = [];

            // Add parent systems first (if any)
            if (parentSystems.length > 0) {
                parentSystems.forEach(function (parent) {
                    hierarchyGroups.push({
                        parent: parent,
                        children: [],
                        expanded: true,
                        isParent: true
                    });
                });
            }

            // Add current system
            if (currentSystem) {
                hierarchyGroups.push({
                    parent: currentSystem,
                    children: childSystems,
                    expanded: true,
                    isCurrent: true
                });
            }

            // If no current system found, group by level
            if (!currentSystem && filteredHierarchy.length > 0) {
                filteredHierarchy.sort((a, b) => {
                    if (a.level !== b.level) return a.level - b.level;
                    return (a.name || '').localeCompare(b.name || '');
                });

                let currentGroup = null;
                filteredHierarchy.forEach(function (item) {
                    if (item.level === 0) {
                        if (currentGroup) {
                            hierarchyGroups.push(currentGroup);
                        }
                        currentGroup = {
                            parent: item,
                            children: [],
                            expanded: true
                        };
                    } else if (currentGroup) {
                        currentGroup.children.push(item);
                    }
                });

                if (currentGroup) {
                    hierarchyGroups.push(currentGroup);
                }
            }

            const Mask = window.HierarchyMask;
            const valOrMask = (node, raw) => (Mask && Mask.isMasked(node)) ? Mask.PLACEHOLDER : (raw || '');
            const isMaskedNode = (node) => Mask ? Mask.isMasked(node) : false;
            tbody.innerHTML = hierarchyGroups.map(function (group, groupIndex) {
                const hasChildren = group.children.length > 0;
                const isCurrentSystem = group.isCurrent;
                const isParentSystem = group.isParent;
                const parentMasked = isMaskedNode(group.parent);
                const parentName = valOrMask(group.parent, group.parent.displayName || group.parent.name);
                const parentNameHtml = parentMasked
                    ? `<i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(parentName)}`
                    : escapeHtml(parentName);

                let html = `
                    <tr class="hierarchy-parent ${isCurrentSystem ? 'current-system' : ''} ${isParentSystem ? 'parent-system' : ''}${parentMasked ? ' masked-row' : ''}"${parentMasked ? ' data-masked="true"' : ''}>
                        <td>
                            <div class="hierarchy-item">
                                <i class="fas fa-database database-icon"></i>
                                <span class="parent-name ${isCurrentSystem ? 'current-name' : ''} ${isParentSystem ? 'parent-name-style' : ''}${parentMasked ? ' masked-node' : ''}">${parentNameHtml}</span>
                                ${isParentSystem ? '<span class="relationship-label">(Parent System)</span>' : ''}
                            </div>
                        </td>
                        <td>${escapeHtml(valOrMask(group.parent, group.parent.description)) || '<span class="empty">-</span>'}</td>
                        <td>${escapeHtml(valOrMask(group.parent, group.parent.typeName)) || '<span class="empty">-</span>'}</td>
                        <td>${escapeHtml(valOrMask(group.parent, group.parent.longName)) || '<span class="empty">-</span>'}</td>
                        <td>${escapeHtml(valOrMask(group.parent, group.parent.classificationName)) || '<span class="empty">-</span>'}</td>
                    </tr>
                `;

                if (hasChildren) {
                    group.children.forEach(function (child, childIndex) {
                        const childMasked = isMaskedNode(child);
                        const childName = valOrMask(child, child.displayName || child.name);
                        const childNameHtml = childMasked
                            ? `<i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(childName)}`
                            : escapeHtml(childName);
                        html += `
                            <tr class="hierarchy-child${childMasked ? ' masked-row' : ''}"${childMasked ? ' data-masked="true"' : ''}>
                                <td>
                                    <div class="hierarchy-item">
                                        <div class="hierarchy-connector">
                                            <div class="connector-line ${childIndex === group.children.length - 1 ? 'last-child' : ''}"></div>
                                            <div class="connector-l-shape"></div>
                                        </div>
                                        <i class="fas fa-database database-icon"></i>
                                        <span class="child-name${childMasked ? ' masked-node' : ''}">${childNameHtml}</span>
                                        <span class="relationship-label">(Child of: ${escapeHtml(valOrMask(group.parent, group.parent.name))})</span>
                                    </div>
                                </td>
                                <td>${escapeHtml(valOrMask(child, child.description)) || '<span class="empty">-</span>'}</td>
                                <td>${escapeHtml(valOrMask(child, child.typeName)) || '<span class="empty">-</span>'}</td>
                                <td>${escapeHtml(valOrMask(child, child.longName)) || '<span class="empty">-</span>'}</td>
                                <td>${escapeHtml(valOrMask(child, child.classificationName)) || '<span class="empty">-</span>'}</td>
                            </tr>
                        `;
                    });
                }

                return html;
            }).join('');

            // Update footer with record count
            const footer = document.getElementById('hierarchyFooter');
            if (footer) {
                footer.textContent = `${hierarchy.length} record${hierarchy.length !== 1 ? 's' : ''}`;
            }

        } catch (e) {
            console.error('Failed to load hierarchy:', e);
            tbody.innerHTML = `<tr><td colspan="5" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data</td></tr>`;
            const footer = document.getElementById('hierarchyFooter');
            if (footer) footer.textContent = '0 records';
        }
    }

    // Setup tab functionality
    function setupTabFunctionality() {
        const tabs = document.querySelectorAll('.tab-container .tab');
        const tabContents = {
            'summary': document.getElementById('systemEditContainer'),
            'relationships': document.getElementById('relationshipsTab'),
            'stakeholders': document.getElementById('stakeholdersTab'),
            'impact': document.getElementById('impactTab')
        };

        // Set active tab based on URL parameter
        setActiveTab();

        tabs.forEach(function (btn) {
            btn.addEventListener('click', function () {
                // Remove active class from all tabs
                tabs.forEach(function (b) { b.classList.remove('active'); });
                this.classList.add('active');

                // Hide all tab contents
                Object.values(tabContents).forEach(function (content) {
                    if (content) content.style.display = 'none';
                });

                // Show selected tab content
                const tabName = this.getAttribute('data-tab');
                const selectedContent = tabContents[tabName];
                if (selectedContent) {
                    selectedContent.style.display = 'block';

                    // Load relationships data when relationships tab is selected
                    if (tabName === 'relationships') {
                        const id = parseId();
                        if (id) {
                            loadSystemHierarchy(id);
                        }
                    }
                    // Load stakeholders data when stakeholders tab is selected
                    else if (tabName === 'stakeholders') {
                        const id = parseId();
                        if (id) {
                            // Create stakeholders container if it doesn't exist
                            let stakeholdersContainer = document.getElementById('systemStakeholdersContainer');
                            if (!stakeholdersContainer) {
                                stakeholdersContainer = document.createElement('div');
                                stakeholdersContainer.id = 'systemStakeholdersContainer';
                                stakeholdersContainer.className = 'view-section';
                                stakeholdersContainer.style.gridColumn = '1/-1';
                                selectedContent.appendChild(stakeholdersContainer);
                            }

                            // Initialize stakeholders edit
                            if (window.SystemStakeholderEdit) {
                                window.SystemStakeholderEdit.init(id, editViewMode);
                            } else {
                                console.error('SystemStakeholderEdit not loaded');
                                stakeholdersContainer.innerHTML = '<div class="error">Stakeholders edit not available</div>';
                            }
                        }
                    }
                    // Initialize impact tab when impact tab is selected
                    else if (tabName === 'impact') {
                        const id = parseId();
                        if (id && window.initImpactEdit && typeof window.initImpactEdit === 'function') {
                            console.log('Initializing impact edit for system:', id);
                            setTimeout(() => {
                                window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                                window.initImpactEdit(id, editViewMode);
                            }, 100);
                        }
                    }
                }
            });
        });
    }

    // Set active tab based on URL parameter
    function setActiveTab() {
        const activeTabName = getActiveTab();
        const targetTab = document.querySelector(`[data-tab="${activeTabName}"]`);

        if (targetTab) {
            // Remove active class from all tabs
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));

            // Add active class to target tab
            targetTab.classList.add('active');

            // Show corresponding content
            const tabContents = {
                'summary': document.getElementById('systemEditContainer'),
                'relationships': document.getElementById('relationshipsTab'),
                'stakeholders': document.getElementById('stakeholdersTab'),
                'impact': document.getElementById('impactTab')
            };

            // Hide all tab contents
            Object.values(tabContents).forEach(content => {
                if (content) content.style.display = 'none';
            });

            // Show selected tab content
            const selectedContent = tabContents[activeTabName];
            if (selectedContent) {
                selectedContent.style.display = 'block';

                // Load data for specific tabs
                if (activeTabName === 'relationships') {
                    const id = parseId();
                    if (id) {
                        loadSystemHierarchy(id);
                    }
                } else if (activeTabName === 'stakeholders') {
                    const id = parseId();
                    if (id) {
                        // Initialize stakeholders edit
                        if (window.SystemStakeholderEdit) {
                            window.SystemStakeholderEdit.init(id, editViewMode);
                        } else {
                            console.error('SystemStakeholderEdit not loaded');
                            const container = document.getElementById('systemStakeholdersContainer');
                            if (container) {
                                container.innerHTML = '<div class="error">Stakeholders edit not available</div>';
                            }
                        }
                    }
                }
            }
        }
    }

    // Get active tab from URL parameter
    function getActiveTab() {
        const urlParams = new URLSearchParams(window.location.search);
        const tabParam = urlParams.get('tab');
        return tabParam || 'summary';
    }

    // Get currently active tab
    function getCurrentActiveTab() {
        const activeTab = document.querySelector('.tab.active');
        return activeTab ? activeTab.getAttribute('data-tab') : 'summary';
    }

    // Check if the active tab (or active subtab for Impact) has changes - used to decide whether to save
    function hasAnyDataChanged() {
        const activeTab = getCurrentActiveTab();
        switch (activeTab) {
            case 'relationships':
                return false; // No editable data to save in this tab
            case 'stakeholders':
                if (window.SystemStakeholderEdit && window.SystemStakeholderEdit.hasDataChanged) {
                    return window.SystemStakeholderEdit.hasDataChanged();
                }
                return false;
            case 'impact':
                // Impact save now persists all subtabs together, so change detection
                // must consider all subtabs (not only the currently active one).
                if (window.hasImpactChanges && typeof window.hasImpactChanges === 'function') {
                    return window.hasImpactChanges();
                }
                if (window.hasImpactChangesForActiveSubTab && typeof window.hasImpactChangesForActiveSubTab === 'function') {
                    return window.hasImpactChangesForActiveSubTab();
                }
                return false;
            case 'summary':
            default:
                return hasFormDataChanged();
        }
    }

    // Check if form data has changed
    function hasFormDataChanged() {
        // For now, always return true to allow saving
        // In a real implementation, you would compare current form values with original values
        return true;
    }

    // Save stakeholders data
    async function saveStakeholdersData() {
        if (window.SystemStakeholderEdit && window.SystemStakeholderEdit.saveStakeholders) {
            return await window.SystemStakeholderEdit.saveStakeholders();
        } else {
            const m = msg('system.edit.messages.stakeholderEditNotAvailable', null, 'Stakeholder edit functionality not available.');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            return false;
        }
    }

})();

