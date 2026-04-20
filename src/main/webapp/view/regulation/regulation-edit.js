(function() {
    'use strict';

    // Global variables
    let currentRegulationId = null;
    let activeTab = 'summary';
    let hasFormChanges = false;
    let isDirty = false;
    let selectedParentId = null;
    let segmentField = null; // Segment selector reference
    let originalRegulationSegmentId = null; // segment loaded from server; used to detect segment change

    function normalizeRegulationSegmentId(value) {
        if (value == null || value === '') return null;
        const n = parseInt(value, 10);
        return Number.isInteger(n) ? n : null;
    }
    
    // Regulators table data
    let regulatorsData = [];
    let originalRegulatorsData = [];
    let regulatorsToDelete = [];
    let allRegulators = [];
    let allRegulatorRelationTypes = [];
    
    // Geography table data
    let geographyData = [];
    let originalGeographyData = [];
    let geographyToDelete = [];
    let allGeographies = [];
    let availableRegulatorGeographies = []; // Filtered based on selected regulators
    
    // Dropdown data
    let allRegulations = [];
    
    // Parse ID from URL
    function parseId() {
        const params = new URLSearchParams(window.location.search);
        const id = params.get('id');
        return id ? parseInt(id, 10) : null;
    }

    function parseTab() {
        const params = new URLSearchParams(window.location.search);
        return params.get('tab') || 'summary';
    }

    // Initialize page
    async function initializePage() {
        console.log('Initializing regulation edit page...');
        
        currentRegulationId = parseId();
        console.log('Current regulation ID:', currentRegulationId);
        
        if (!currentRegulationId) {
            console.error('No regulation ID found');
            return;
        }

        // Initialize lock
        const lockAcquired = await window.LockInitHelper.initializeLock('regulation', currentRegulationId, 'regulation');
        if (!lockAcquired) {
            return; // Lock initialization failed, user was redirected
        }
        
        setupEventListeners();
        setupTabSwitching();
        
        // Load dropdown data FIRST
        await loadDropdownData();
        
        // Initialize segment field before loading data
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    // Removed defaultValue - let API data set the correct value
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Regulation',
                    fieldId: 'regulationSegment',
                    errorId: 'regulationSegmentError',
                    getParentId: () => selectedParentId,
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        return await refreshRegulationParentForSelectedSegment(previousSegmentId);
                    }
                });
            } catch (err) {
                console.error('Error initializing segment field:', err);
            }
        }

        // THEN load regulation data (so dropdowns are populated before setting values)
        if (currentRegulationId) {
            await loadRegulation(currentRegulationId);
            
            // Initialize custom fields
            if (window.CustomFields) {
                try {
                    window.customFieldsContext = await window.CustomFields.initForm({
                        facetId: 'Regulation',
                        containerId: 'customFieldsContainer',
                        mode: 'edit',
                        objectId: currentRegulationId
                    });
                    console.log('Custom fields initialized:', window.customFieldsContext);
                } catch (error) {
                    console.error('Error initializing custom fields:', error);
                }
            }
        } else {
            // New regulation
            const titleEl = document.getElementById('regulationTitle');
            if (titleEl) titleEl.textContent = (window.I18n ? window.I18n.t('regulation.page.newRegulation') : 'New Regulation');
        }
        
        // Switch to the specified tab (if any)
        const targetTab = parseTab();
        console.log('Switching to tab:', targetTab);
        switchTab(targetTab);
    }

    // Load dropdown data
    async function loadDropdownData() {
        try {
            // Load regulation statuses for BUDG Status dropdown
            const statusResponse = await fetch('/api/regulation-status/list');
            if (statusResponse.ok) {
                const statuses = await statusResponse.json();
                const budgStatusSelect = document.getElementById('budgStatus');
                budgStatusSelect.innerHTML = '<option value="">' + (window.I18n ? window.I18n.t('regulation.placeholders.selectStatus') : 'Select Status') + '</option>';
                statuses.forEach(status => {
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.primaryname || status.primaryName || status.PrimaryName;
                    budgStatusSelect.appendChild(option);
                });
            }
            
            // Load maturity options
            const maturityResponse = await fetch('/api/regulation-maturity/list');
            if (maturityResponse.ok) {
                const maturities = await maturityResponse.json();
                const maturitySelect = document.getElementById('maturity');
                maturitySelect.innerHTML = '<option value="">' + (window.I18n ? window.I18n.t('regulation.placeholders.selectMaturity') : 'Select Maturity') + '</option>';
                maturities.forEach(maturity => {
                    const option = document.createElement('option');
                    option.value = maturity.id;
                    option.textContent = maturity.primaryname || maturity.primaryName || maturity.PrimaryName;
                    maturitySelect.appendChild(option);
                });
            }
            
            // Load stage options
            const stageResponse = await fetch('/api/regulation-stage/list');
            if (stageResponse.ok) {
                const stages = await stageResponse.json();
                const stageSelect = document.getElementById('stage');
                stageSelect.innerHTML = '<option value="">' + (window.I18n ? window.I18n.t('regulation.placeholders.selectStage') : 'Select Stage') + '</option>';
                stages.forEach(stage => {
                    const option = document.createElement('option');
                    option.value = stage.id;
                    option.textContent = stage.primaryname || stage.primaryName || stage.PrimaryName;
                    stageSelect.appendChild(option);
                });
            }
            
            // Load probability options
            const probabilityResponse = await fetch('/api/regulation-probability/list');
            if (probabilityResponse.ok) {
                const probabilities = await probabilityResponse.json();
                const probabilitySelect = document.getElementById('probability');
                probabilitySelect.innerHTML = '<option value="">' + (window.I18n ? window.I18n.t('regulation.placeholders.selectProbability') : 'Select Probability') + '</option>';
                probabilities.forEach(probability => {
                    const option = document.createElement('option');
                    option.value = probability.id;
                    option.textContent = probability.primaryname || probability.primaryName || probability.PrimaryName;
                    probabilitySelect.appendChild(option);
                });
            }
            
            // Load compliance level options
            const complianceLevelResponse = await fetch('/api/regulation-compliance-level/list');
            if (complianceLevelResponse.ok) {
                const levels = await complianceLevelResponse.json();
                const complianceLevelSelect = document.getElementById('complianceLevel');
                complianceLevelSelect.innerHTML = '<option value="">' + (window.I18n ? window.I18n.t('regulation.placeholders.selectComplianceLevel') : 'Select Compliance Level') + '</option>';
                levels.forEach(level => {
                    const option = document.createElement('option');
                    option.value = level.id;
                    option.textContent = level.primaryname || level.primaryName || level.PrimaryName;
                    complianceLevelSelect.appendChild(option);
                });
            }
            
            // Load legal advice type options
            const legalAdviceTypeResponse = await fetch('/api/legal-advice-type/list');
            if (legalAdviceTypeResponse.ok) {
                const types = await legalAdviceTypeResponse.json();
                const legalAdviceTypeSelect = document.getElementById('legalAdviceType');
                legalAdviceTypeSelect.innerHTML = '<option value="">' + (window.I18n ? window.I18n.t('regulation.placeholders.selectType') : 'Select Type') + '</option>';
                types.forEach(type => {
                    const option = document.createElement('option');
                    option.value = type.id;
                    option.textContent = type.primaryname || type.primaryName || type.PrimaryName;
                    legalAdviceTypeSelect.appendChild(option);
                });
            }
            
            // Load viewing/access control options
            const viewingResponse = await fetch('/api/viewing/list');
            if (viewingResponse.ok) {
                const viewingOptions = await viewingResponse.json();
                const accessControlSelect = document.getElementById('accessControl');
                accessControlSelect.innerHTML = '<option value="">' + (window.I18n ? window.I18n.t('regulation.placeholders.selectAccessControl') : 'Select Access Control') + '</option>';
                viewingOptions.forEach(viewing => {
                    const option = document.createElement('option');
                    option.value = viewing.id;
                    option.textContent = viewing.name || viewing.Name;
                    accessControlSelect.appendChild(option);
                });
            }
            
        } catch (error) {
            console.error('Error loading dropdown data:', error);
        }
    }

    // Load regulation data
    async function loadRegulation(id) {
        try {
            const response = await fetch(`/api/regulation/${id}`);
            if (!response.ok) {
                throw new Error('Failed to load regulation');
            }
            
            const regulation = await response.json();
            populateForm(regulation);
            
            // Load parent name if exists
            if (regulation.parentId) {
                selectedParentId = regulation.parentId;
                await loadParentRegulationName(regulation.parentId);
            }
            
        } catch (error) {
            console.error('Error loading regulation:', error);
            alert(window.I18n ? window.I18n.t('regulation.messages.failedToLoad') : 'Failed to load regulation data');
        }
    }

    // Populate form with regulation data
    function populateForm(regulation) {
        console.log('Populating form with regulation data:', regulation);
        
        document.getElementById('regulationTitle').textContent = regulation.primaryName || (window.I18n ? window.I18n.t('regulation.page.title') : 'Regulation');
        document.getElementById('primaryName').value = regulation.primaryName || '';
        document.getElementById('refNumber').value = regulation.refNumber || '';
        document.getElementById('description').value = regulation.description || '';
        document.getElementById('shortName').value = regulation.shortName || '';
        document.getElementById('legalAdvice').value = regulation.legalAdvice || '';
        document.getElementById('additionalInfo').value = regulation.additionalInfo || '';
        
        // Set access control (Is_Public is a foreign key to Viewing table)
        const isPublicValue = regulation.isPublic || regulation.is_Public || regulation.IsPublic || regulation.Is_Public;
        if (isPublicValue) {
            console.log('Setting accessControl to:', isPublicValue);
            document.getElementById('accessControl').value = isPublicValue;
        }
        
        // Set dropdown values (matching database column names)
        const statusId = regulation.regulationStatusId || regulation.regulationStatus_ID || regulation.RegulationStatus_ID;
        if (statusId) {
            console.log('Setting budgStatus to:', statusId);
            document.getElementById('budgStatus').value = statusId;
        }
        
        const maturityId = regulation.regulationMaturityId || regulation.regulationMaturity_ID || regulation.RegulationMaturity_ID;
        if (maturityId) {
            console.log('Setting maturity to:', maturityId);
            document.getElementById('maturity').value = maturityId;
        }
        
        const stageId = regulation.regulationStageId || regulation.regulationStage_ID || regulation.RegulationStage_ID;
        if (stageId) {
            console.log('Setting stage to:', stageId);
            document.getElementById('stage').value = stageId;
        }
        
        const probabilityId = regulation.regulationProbabilityId || regulation.regulationProbability_ID || regulation.RegulationProbability_ID;
        if (probabilityId) {
            console.log('Setting probability to:', probabilityId);
            document.getElementById('probability').value = probabilityId;
        }
        
        const complianceLevelId = regulation.complianceLevelId || regulation.complianceLevel_ID || regulation.ComplianceLevel_ID;
        if (complianceLevelId) {
            console.log('Setting complianceLevel to:', complianceLevelId);
            document.getElementById('complianceLevel').value = complianceLevelId;
        }
        
        const legalAdviceTypeId = regulation.legalAdviceTypeId || regulation.legalAdviceType_ID || regulation.LegalAdviceType_ID;
        if (legalAdviceTypeId) {
            console.log('Setting legalAdviceType to:', legalAdviceTypeId);
            document.getElementById('legalAdviceType').value = legalAdviceTypeId;
        }

        // Set segment value if available
        if (segmentField && (regulation.segmentId || regulation.segment_id || regulation.Segment_ID)) {
            const segVal = regulation.segmentId ?? regulation.segment_id ?? regulation.Segment_ID;
            segmentField.setValue(segVal);
        }
        if (segmentField && typeof segmentField.getValue === 'function') {
            originalRegulationSegmentId = normalizeRegulationSegmentId(segmentField.getValue());
        }
        
        // Set dates
        if (regulation.publicationDate) {
            document.getElementById('publicationDate').value = regulation.publicationDate.split(' ')[0];
        }
        if (regulation.complianceDate) {
            document.getElementById('complianceDate').value = regulation.complianceDate.split(' ')[0];
        }
        if (regulation.commentsDate) {
            document.getElementById('commentsDate').value = regulation.commentsDate.split(' ')[0];
        }
        if (regulation.finalisationDate) {
            document.getElementById('finalisationDate').value = regulation.finalisationDate.split(' ')[0];
        }
        
        console.log('Form populated successfully');
    }

    // Load parent regulation name
    async function loadParentRegulationName(parentId) {
        try {
            const response = await fetch(`/api/regulation/${parentId}`);
            if (response.ok) {
                const parent = await response.json();
                document.getElementById('parentName').value = parent.primaryName || '';
            }
        } catch (error) {
            console.error('Error loading parent regulation:', error);
        }
    }

    // Setup event listeners
    function setupEventListeners() {
        // Save buttons
        document.getElementById('saveBtn').addEventListener('click', () => saveRegulation(false));
        document.getElementById('saveAndCloseBtn').addEventListener('click', () => saveRegulation(true));
        document.getElementById('closeBtn').addEventListener('click', handleClose);
        
        // Show editor button – advanced rich text editor
        const showEditorBtn = document.getElementById('showEditorBtn');
        if (showEditorBtn) {
            showEditorBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('description', showEditorBtn);
            });
        }

        // Parent selection
        document.getElementById('selectParentBtn').addEventListener('click', showParentSelectionModal);
        document.getElementById('clearParentBtn').addEventListener('click', clearParent);
        document.getElementById('closeParentModal').addEventListener('click', closeParentModal);
        document.getElementById('cancelParentSelection').addEventListener('click', closeParentModal);
        
        // Form change tracking: delegate from a stable ancestor so load order does not matter.
        // Ignore non-trusted (script-dispatched) events so programmatic fills never mark dirty.
        (function setupRegulationFormDirtyDelegation() {
            const root = document.querySelector('.edit-form-container') || document.querySelector('.main-content');
            if (!root || root.dataset.budgDirtyDelegation === '1') return;
            root.dataset.budgDirtyDelegation = '1';
            const onDirty = (e) => {
                if (!e || e.isTrusted === false) return;
                const t = e.target;
                if (!t || typeof t.matches !== 'function') return;
                if (!t.matches('input, select, textarea')) return;
                hasFormChanges = true;
                isDirty = true;
            };
            root.addEventListener('change', onDirty);
            root.addEventListener('input', onDirty);
        })();
        // Intentionally no window.beforeunload: the Close button uses handleClose() + custom
        // confirm only, so users never see a second browser "Leave site?" dialog after that.
    }

    // Setup tab switching
    function setupTabSwitching() {
        const tabs = document.querySelectorAll('.tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const tabName = tab.dataset.tab;
                switchTab(tabName);
            });
        });
    }

    // Switch between tabs
    function switchTab(tabName) {
        // Update active tab
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        document.querySelector(`.tab[data-tab="${tabName}"]`).classList.add('active');
        
        // Update active content
        document.querySelectorAll('.tab-content').forEach(c => {
            c.style.display = 'none';
            c.classList.remove('active');
        });
        
        let activeContent;
        if (tabName === 'impact') {
            activeContent = document.getElementById('impact');
        } else {
            activeContent = document.getElementById(`${tabName}Container`);
        }
        if (activeContent) {
            activeContent.style.display = 'block';
            activeContent.classList.add('active');
        }
        
        // Load data for specific tabs
        if (tabName === 'summary' && currentRegulationId) {
            loadRegulatorsData();
        } else if (tabName === 'components' && currentRegulationId) {
            loadRegulationHierarchy();
        } else if (tabName === 'relationships' && currentRegulationId) {
            loadRelationshipsData();
        } else if (tabName === 'stakeholders' && currentRegulationId) {
            // Initialize stakeholder edit mode
            if (window.regulationStakeholderEdit && window.regulationStakeholderEdit.init) {
                window.regulationStakeholderEdit.init(currentRegulationId);
            }
        } else if (tabName === 'impact' && currentRegulationId) {
            // Initialize Impact edit mode
            if (window.initImpactEdit) {
                window.initImpactEdit(currentRegulationId);
            }
        }
        activeTab = tabName;
    }
    
    // Load regulation hierarchy for components tab - EXACT COPY from view page
    async function loadRegulationHierarchy() {
        if (!currentRegulationId) {
            console.warn('No regulation ID available for loading hierarchy');
            return;
        }
        
        try {
            console.log('Loading regulation hierarchy for regulation ID:', currentRegulationId);
            
            // Fetch all regulations
            const response = await fetch('/api/regulation/hierarchy');
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const regulations = await response.json();
            console.log('All regulations loaded for hierarchy:', regulations);
            
            const container = document.getElementById('hierarchyContainer');
            
            if (!Array.isArray(regulations) || regulations.length === 0) {
                container.innerHTML = `
                    <div class="view-section">
                        <div class="section-title">REGULATION HIERARCHY</div>
                        <div class="hierarchy-table-wrapper">
                            <table class="hierarchy-table">
                                <thead>
                                    <tr>
                                        <th>Ref.</th>
                                        <th>Component</th>
                                        <th>Short Name</th>
                                        <th>Description</th>
                                        <th>BUDG Status</th>
                                        <th>Stage</th>
                                        <th>Compliance Level</th>
                                        <th>Regulatory Theme</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td colspan="8" style="text-align: center; padding: 2rem;">
                                            <i class="fas fa-balance-scale" style="font-size: 2rem; color: var(--text-secondary, #64748b); margin-bottom: 1rem; display: block;"></i>
                                            <p>${(window.I18n && window.I18n.t('regulation.messages.noRegulationsFound')) || 'No regulations found.'}</p>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                        <div class="table-footer">${(window.I18n && window.I18n.t('message.zeroRecords')) || '0 records'}</div>
                    </div>
                `;
                return;
            }
            
            // Build hierarchy tree
            const hierarchyRows = buildRegulationHierarchyTree(regulations, parseInt(currentRegulationId));
            
            // Render the hierarchy table
            const html = renderRegulationTable(hierarchyRows, currentRegulationId);
            
            // Update the container with the new HTML
            container.innerHTML = html;
            
            // Initialize interactions (expand/collapse)
            const newContainer = container.querySelector('.view-section');
            if (newContainer) {
                initRegulationInteractions(newContainer, hierarchyRows);
            }
            
        } catch (error) {
            console.error('Error loading regulation hierarchy:', error);
            const container = document.getElementById('hierarchyContainer');
            container.innerHTML = `
                <div class="view-section">
                    <div class="section-title">REGULATION HIERARCHY</div>
                    <div class="hierarchy-table-wrapper">
                        <table class="hierarchy-table">
                            <thead>
                                <tr>
                                    <th>Ref.</th>
                                    <th>Component</th>
                                    <th>Short Name</th>
                                    <th>Description</th>
                                    <th>BUDG Status</th>
                                    <th>Stage</th>
                                    <th>Compliance Level</th>
                                    <th>Regulatory Theme</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td colspan="8" style="text-align: center; padding: 2rem; color: var(--danger, #dc3545);">
                                        <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem; display: block;"></i>
                                        <p>Error loading regulation hierarchy: ${error.message}</p>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    <div class="table-footer">Error loading data</div>
                </div>
            `;
        }
    }
    
    // Build regulation hierarchy tree - EXACT COPY from view page
    function buildRegulationHierarchyTree(regulations, currentRegulationId) {
        console.log('Building regulation hierarchy tree for regulation ID:', currentRegulationId);
        
        // Create maps for easy lookup
        const byId = {};
        const parentMap = {};
        
        regulations.forEach(regulation => {
            const id = parseInt(regulation.id);
            byId[id] = regulation;
            
            if (regulation.parentId) {
                const parentId = parseInt(regulation.parentId);
                if (!parentMap[parentId]) {
                    parentMap[parentId] = [];
                }
                parentMap[parentId].push(regulation);
            }
        });
        
        // Find the lineage for the current regulation
        const lineage = buildRegulationLineageTree(regulations, currentRegulationId);
        
        if (lineage.length === 0) {
            // Fallback: show all regulations
            console.log('No lineage found, showing all regulations');
            const hierarchyRows = {
                rows: regulations.map(regulation => ({
                    node: regulation,
                    depth: 0,
                    childCount: parentMap[regulation.id] ? parentMap[regulation.id].length : 0,
                    hasChildren: parentMap[regulation.id] && parentMap[regulation.id].length > 0
                })),
                parentMap: parentMap
            };
            return hierarchyRows;
        }
        
        // Build the tree structure
        const hierarchyRows = {
            rows: [],
            parentMap: parentMap
        };
        
        function buildRows(nodes, depth = 0) {
            nodes.forEach(node => {
                const nodeId = parseInt(node.id);
                const childCount = parentMap[nodeId] ? parentMap[nodeId].length : 0;
                const hasChildren = childCount > 0;
                
                hierarchyRows.rows.push({
                    node: node,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: hasChildren
                });
                
                // Add children recursively
                if (hasChildren) {
                    buildRows(parentMap[nodeId], depth + 1);
                }
            });
        }
        
        // Start with the root of the lineage
        const rootRegulation = lineage[0];
        buildRows([rootRegulation], 0);
        
        return hierarchyRows;
    }
    
    // Build regulation lineage tree - EXACT COPY from view page
    function buildRegulationLineageTree(regulations, currentRegulationId) {
        const byId = {};
        regulations.forEach(reg => {
            byId[parseInt(reg.id)] = reg;
        });
        
        const currentId = parseInt(currentRegulationId);
        const currentRegulation = byId[currentId];
        
        if (!currentRegulation) {
            console.log('Current regulation not found in data');
            return [];
        }
        
        // Find ancestors
        const ancestors = [];
        let current = currentRegulation;
        
        while (current && current.parentId) {
            const parentId = parseInt(current.parentId);
            const parent = byId[parentId];
            if (parent) {
                ancestors.unshift(parent);
                current = parent;
            } else {
                break;
            }
        }
        
        // Build parent map for finding descendants
        const parentMap = {};
        regulations.forEach(reg => {
            if (reg.parentId) {
                const parentId = parseInt(reg.parentId);
                if (!parentMap[parentId]) {
                    parentMap[parentId] = [];
                }
                parentMap[parentId].push(reg);
            }
        });
        
        // Find descendants of current regulation
        const descendants = [];
        function findDescendants(nodeId) {
            const children = parentMap[nodeId] || [];
            children.forEach(child => {
                descendants.push(child);
                findDescendants(parseInt(child.id));
            });
        }
        findDescendants(currentId);
        
        // Find siblings (other children of the same parent)
        const siblings = [];
        if (currentRegulation.parentId) {
            const parentId = parseInt(currentRegulation.parentId);
            const siblingsOfCurrent = parentMap[parentId] || [];
            siblingsOfCurrent.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                if (siblingId !== currentId) {
                    siblings.push(sibling);
                    // Find descendants of siblings (nephews/nieces and their descendants)
                    findDescendants(siblingId);
                }
            });
        }
        
        // Return the complete lineage: ancestors + current + descendants (including siblings and their descendants)
        return [...ancestors, currentRegulation, ...descendants];
    }
    
    // Render regulation table - EXACT COPY from view page
    function renderRegulationTable(hierarchyRows, currentId) {
        const formatDate = (dateString) => {
            if (!dateString) return '';
            try {
                return new Date(dateString).toLocaleDateString();
            } catch (error) {
                return dateString;
            }
        };

        const Mask = window.HierarchyMask;
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const isMaskedNode = Mask ? Mask.isMasked(node) : false;
            const ph = isMaskedNode ? Mask.PLACEHOLDER : null;
            const name = isMaskedNode ? ph : (node.primaryName || 'Unnamed Regulation');
            const shortName = isMaskedNode ? ph : (node.shortName || '');
            const desc = isMaskedNode ? ph : (node.description || '');
            const refNumber = isMaskedNode ? ph : (node.refNumber || '');
            const regulationStatus = isMaskedNode ? ph : (node.regulationStatus || '');
            const regulationStage = isMaskedNode ? ph : (node.regulationStage || '');
            const complianceLevel = isMaskedNode ? ph : (node.complianceLevel || '');
            const isCurrent = String(node.id) === String(currentId);
            const id = node.id;
            const parentId = node.parentId || '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'regulation-link current-regulation-link' : 'regulation-link';
            const componentLink = isMaskedNode
                ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
                : `<a class="${linkClass}" href="/view/regulation/regulation.html?id=${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            
            const regulatoryTheme = isMaskedNode ? ph : (node.regulatoryThemes || '');
            const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();
            
            return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
                <td><span class="ref-number">${escapeHtml(refNumber)}</span></td>
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-balance-scale item-icon"></i><span class="regulation-name">${componentLink}</span>${countBadge}</div></td>
                <td>${escapeHtml(shortName)}</td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
                <td>${escapeHtml(regulationStatus)}</td>
                <td>${escapeHtml(regulationStage)}</td>
                <td>${escapeHtml(complianceLevel)}</td>
                <td>${escapeHtml(regulatoryTheme)}</td>
            </tr>`;
        }).join('');

        return `
            <div class="view-section">
                <div class="section-title">REGULATION HIERARCHY</div>
                <div class="hierarchy-table-wrapper">
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th>Ref.</th>
                                <th>Component</th>
                                <th>Short Name</th>
                                <th>Description</th>
                                <th>BUDG Status</th>
                                <th>Stage</th>
                                <th>Compliance Level</th>
                                <th>Regulatory Theme</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rowsHtml}
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    ${hierarchyRows.rows.length} record${hierarchyRows.rows.length !== 1 ? 's' : ''}
                </div>
            </div>
        `;
    }
    
    // Initialize regulation interactions (expand/collapse) - EXACT COPY from view page
    function initRegulationInteractions(containerEl, hierarchyRows) {
        const parentMap = hierarchyRows.parentMap;
        
        // Handle expand/collapse functionality
        containerEl.addEventListener('click', function(e) {
            if (e.target.closest('.tree-expander')) {
                e.preventDefault();
                e.stopPropagation();
                
                const button = e.target.closest('.tree-expander');
                const row = button.closest('tr');
                const parentId = parseInt(row.dataset.id);
                const currentDepth = parseInt(row.dataset.depth);
                const icon = button.querySelector('i');
                
                // Toggle icon
                const isExpanded = icon.classList.contains('fa-caret-down');
                icon.classList.toggle('fa-caret-down', !isExpanded);
                icon.classList.toggle('fa-caret-right', isExpanded);
                
                // Find all child rows
                let nextRow = row.nextElementSibling;
                while (nextRow && parseInt(nextRow.dataset.depth) > currentDepth) {
                    const childDepth = parseInt(nextRow.dataset.depth);
                    
                    if (childDepth === currentDepth + 1) {
                        // Direct children - toggle visibility
                        nextRow.style.display = isExpanded ? 'none' : '';
                        
                        // If collapsing, also collapse any expanded grandchildren
                        if (isExpanded) {
                            const childExpander = nextRow.querySelector('.tree-expander i');
                            if (childExpander && childExpander.classList.contains('fa-caret-down')) {
                                childExpander.classList.remove('fa-caret-down');
                                childExpander.classList.add('fa-caret-right');
                            }
                        }
                    } else if (childDepth > currentDepth + 1) {
                        // Grandchildren and deeper - hide when collapsing
                        if (isExpanded) {
                            nextRow.style.display = 'none';
                        }
                    }
                    
                    nextRow = nextRow.nextElementSibling;
                }
            }
        });
    }

    // Save regulation (only the active tab's data)
    async function saveRegulation(closeAfter = false) {
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        function restoreButtons() {
            buttons.forEach(b => {
                if (b) {
                    b.disabled = false;
                    if (b.id === 'saveBtn') b.textContent = (window.I18n ? window.I18n.t('button.save') : 'Save');
                    if (b.id === 'saveAndCloseBtn') b.textContent = (window.I18n ? window.I18n.t('button.saveAndClose') : 'Save & Close');
                }
            });
        }
        try {
            buttons.forEach(b => {
                if (b) {
                    b.disabled = true;
                    const savingText = window.I18n ? window.I18n.t('message.saving') : 'Saving...';
                    if (b.id === 'saveBtn') b.textContent = savingText;
                    if (b.id === 'saveAndCloseBtn') b.textContent = savingText;
                }
            });

            // New regulation: only Summary tab can save (to create the record first)
            if (!currentRegulationId && activeTab !== 'summary') {
                showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.saveFirstFromSummary') : 'Save the regulation first from the Summary tab', true);
                restoreButtons();
                return;
            }

            // No saveable data on this tab
            if (activeTab === 'components' || activeTab === 'workflow') {
                showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noDataToSaveOnTab') : 'No data to save on this tab', true);
                restoreButtons();
                return;
            }

            try {
                if (activeTab === 'summary') {
                    // Validate required fields
                    const requiredFields = ['primaryName', 'description', 'publicationDate', 'complianceDate'];
                    for (const fieldId of requiredFields) {
                        const field = document.getElementById(fieldId);
                        if (!field || !field.value.trim()) {
                            showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.fillField', { field: fieldId }) : `Please fill in the ${fieldId} field`, true);
                            if (field) field.focus();
                            restoreButtons();
                            return;
                        }
                    }
                    if (segmentField && !segmentField.validate()) {
                        showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.selectSegment') : 'Please select a Segment', true);
                        restoreButtons();
                        return;
                    }
                    // Sync rich-text editor content back to textarea before reading
                    if (typeof syncAdvancedRichTextToTextarea === 'function') {
                        syncAdvancedRichTextToTextarea('description');
                    }
                    // Prepare and save regulation
                    const regulationData = {
                        primaryName: document.getElementById('primaryName').value.trim(),
                        description: document.getElementById('description').value.trim(),
                        refNumber: document.getElementById('refNumber').value.trim() || null,
                        shortName: document.getElementById('shortName').value.trim() || null,
                        legalAdvice: document.getElementById('legalAdvice').value.trim() || null,
                        additionalInfo: document.getElementById('additionalInfo').value.trim() || null,
                        parentId: selectedParentId || null,
                        isPublic: parseInt(document.getElementById('accessControl').value) || null,
                        publicationDate: document.getElementById('publicationDate').value || null,
                        complianceDate: document.getElementById('complianceDate').value || null,
                        commentsDate: document.getElementById('commentsDate').value || null,
                        finalisationDate: document.getElementById('finalisationDate').value || null,
                        segmentId: segmentField ? segmentField.getValue() : null
                    };
                    const regulationStatusId = document.getElementById('budgStatus').value;
                    const regulationMaturityId = document.getElementById('maturity').value;
                    const regulationStageId = document.getElementById('stage').value;
                    const regulationProbabilityId = document.getElementById('probability').value;
                    const complianceLevelId = document.getElementById('complianceLevel').value;
                    const legalAdviceTypeId = document.getElementById('legalAdviceType').value;
                    if (regulationStatusId) regulationData.regulationStatusId = parseInt(regulationStatusId);
                    if (regulationMaturityId) regulationData.regulationMaturityId = parseInt(regulationMaturityId);
                    if (regulationStageId) regulationData.regulationStageId = parseInt(regulationStageId);
                    if (regulationProbabilityId) regulationData.regulationProbabilityId = parseInt(regulationProbabilityId);
                    if (complianceLevelId) regulationData.complianceLevelId = parseInt(complianceLevelId);
                    if (legalAdviceTypeId) regulationData.legalAdviceTypeId = parseInt(legalAdviceTypeId);
                    let response;
                    if (currentRegulationId) {
                        regulationData.id = currentRegulationId;
                        response = await fetch(`/api/regulation/${currentRegulationId}`, {
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(regulationData)
                        });
                    } else {
                        response = await fetch('/api/regulation', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(regulationData)
                        });
                        if (response.ok) {
                            const result = await response.json();
                            currentRegulationId = result.id || result.ID;
                        }
                    }
                    if (!response.ok) throw new Error('Failed to save regulation');
                    if (window.customFieldsContext && window.customFieldsContext.saveValues && currentRegulationId) {
                        await window.customFieldsContext.saveValues(currentRegulationId);
                    }
                    await saveRegulatorsData();
                    saveCurrentGeographyFormData();
                    await loadRegulatorsData();
                    await new Promise(resolve => setTimeout(resolve, 100));
                    await saveGeographyData();
                    // Trigger impact save when segment changed (re-validate cross-segment links).
                    const regulationImpactDirty = typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
                    const currentRegSegment = normalizeRegulationSegmentId(regulationData.segmentId ?? (segmentField && segmentField.getValue()));
                    const regulationSegmentChanged = currentRegSegment !== normalizeRegulationSegmentId(originalRegulationSegmentId);
                    if ((regulationImpactDirty || regulationSegmentChanged) && window.saveAllImpactData && currentRegulationId) {
                        try {
                            if (regulationSegmentChanged && !regulationImpactDirty && window.initImpactEdit) {
                                console.log('=== Segment changed; reloading regulation impact from server before save ===');
                                await window.initImpactEdit(currentRegulationId);
                            }
                            const impactResult = await window.saveAllImpactData(currentRegulationId);
                            if (!impactResult || impactResult.success !== true) {
                                const details = Array.isArray(impactResult?.errorDetails) ? impactResult.errorDetails.join('. ') : '';
                                const msg = impactResult?.message || 'Impact save failed';
                                throw new Error(details ? `${msg}. ${details}` : msg);
                            }
                        } catch (impactError) {
                            console.error('Error saving impact on segment change:', impactError);
                            throw impactError;
                        }
                    }
                    originalRegulationSegmentId = normalizeRegulationSegmentId(regulationData.segmentId ?? (segmentField && segmentField.getValue()));
                } else if (activeTab === 'relationships') {
                    await saveRelationshipsData();
                } else if (activeTab === 'stakeholders') {
                    if (!window.regulationStakeholderEdit || !window.regulationStakeholderEdit.saveStakeholders) {
                        showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.stakeholdersNotLoaded') : 'Stakeholders not loaded', true);
                        restoreButtons();
                        return;
                    }
                    const ok = await window.regulationStakeholderEdit.saveStakeholders();
                    if (ok === false) {
                        restoreButtons();
                        return;
                    }
                } else if (activeTab === 'impact') {
                    const impactHasChanges = window.hasImpactChanges && typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
                    if (!impactHasChanges) {
                        showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noImpactChangesToSave') : 'No impact changes to save', true);
                        restoreButtons();
                        return;
                    }
                    if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                        const impactResult = await window.saveAllImpactData();
                        if (!impactResult || impactResult.success !== true) {
                            const details = Array.isArray(impactResult?.errorDetails) ? impactResult.errorDetails.join('. ') : '';
                            const msg = impactResult?.message || 'Impact save failed';
                            throw new Error(details ? `${msg}. ${details}` : msg);
                        }
                        if (window.initImpactEdit && currentRegulationId) {
                            await window.initImpactEdit(currentRegulationId);
                        }
                    }
                }

                await window.LockInitHelper.releaseLock();
                showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.updatesSaved') : 'UPDATES SAVED');
                isDirty = false;
                hasFormChanges = false;

                if (closeAfter) {
                    setTimeout(() => {
                        window.location.href = `/view/regulation/regulation.html?id=${currentRegulationId}`;
                    }, 1500);
                } else {
                    if (activeTab === 'summary' && currentRegulationId) {
                        await loadRegulation(currentRegulationId);
                        await loadRegulatorsData();
                    } else if (activeTab === 'relationships' && currentRegulationId) {
                        await loadRelationshipsData();
                    } else if (activeTab === 'stakeholders' && currentRegulationId && window.regulationStakeholderEdit && window.regulationStakeholderEdit.init) {
                        window.regulationStakeholderEdit.init(currentRegulationId);
                    } else if (activeTab === 'impact' && currentRegulationId && window.initImpactEdit) {
                        await window.initImpactEdit(currentRegulationId);
                    }
                }
            } catch (error) {
                console.error('Error saving regulation:', error);
                const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'Unknown error';
                showSuccessMessage(errorMsg, true);
            } finally {
                restoreButtons();
            }
        } catch (error) {
            console.error('Error in saveRegulation:', error);
            const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'Unknown error';
            showSuccessMessage(errorMsg, true);
            restoreButtons();
        }
    }

    // Handle close button
    async function handleClose() {
        if (isDirty || hasFormChanges) {
            const confirmMessage = window.I18n ? window.I18n.t('regulation.confirm.unsavedClose') : 'You have unsaved changes. Are you sure you want to close?';
            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (confirmed) {
                await window.LockInitHelper.releaseLock();
                isDirty = false;
                hasFormChanges = false;
                if (currentRegulationId) {
                    window.location.href = '/view/regulation/regulation.html?id=' + currentRegulationId;
                } else {
                    window.location.href = '/regulation.html';
                }
            }
        } else {
            await window.LockInitHelper.releaseLock();
            isDirty = false;
            hasFormChanges = false;
            if (currentRegulationId) {
                window.location.href = '/view/regulation/regulation.html?id=' + currentRegulationId;
            } else {
                window.location.href = '/regulation.html';
            }
        }
    }

    async function refreshRegulationParentForSelectedSegment(previousSegmentId) {
        const selectedSegmentId = segmentField && typeof segmentField.getValue === 'function'
            ? parseInt(segmentField.getValue(), 10)
            : NaN;

        const query = Number.isInteger(selectedSegmentId) && selectedSegmentId > 0
            ? `?segmentId=${encodeURIComponent(selectedSegmentId)}`
            : '';
        const response = await fetch(`/api/regulation${query}`, { credentials: 'include' });
        if (!response.ok) {
            throw new Error('Failed to load regulations');
        }

        allRegulations = await response.json();

        if (!selectedParentId) {
            return;
        }

        const allowedParentIds = new Set(
            (allRegulations || [])
                .map(reg => parseInt(reg.id || reg.ID, 10))
                .filter(id => Number.isInteger(id) && id > 0)
        );
        const selectedParentNumericId = parseInt(selectedParentId, 10);

        if (!allowedParentIds.has(selectedParentNumericId)) {
            alert('This parent is not valid for the selected segment. Please remove the parent first.');
            return false;
        }
        return true;
    }

    // Parent selection modal
    async function showParentSelectionModal() {
        console.log('Opening parent selection modal');
        
        const modal = document.getElementById('parentSelectionModal');
        const searchInput = document.getElementById('parentSearchInput');
        const listContainer = document.getElementById('regulationList');
        
        if (!modal || !searchInput || !listContainer) {
            console.error('Parent selection modal elements not found!');
            return;
        }
        
        // Clear previous content
        listContainer.innerHTML = '';
        if (searchInput) {
            searchInput.value = '';
        }
        
        try {
            const selectedSegmentId = segmentField && typeof segmentField.getValue === 'function'
                ? parseInt(segmentField.getValue(), 10)
                : NaN;
            const query = Number.isInteger(selectedSegmentId) && selectedSegmentId > 0
                ? `?segmentId=${encodeURIComponent(selectedSegmentId)}`
                : '';

            // Fetch regulations filtered by current segment (if selected)
            const response = await fetch(`/api/regulation${query}`);
            if (!response.ok) {
                throw new Error('Failed to load regulations');
            }
            
            allRegulations = await response.json();
            
            // Filter out current regulation and its descendants
            const filteredRegulations = allRegulations.filter(reg => {
                if (currentRegulationId && reg.id === currentRegulationId) {
                    return false; // Exclude current
                }
                if (currentRegulationId && isDescendantOf(reg.id, currentRegulationId, allRegulations)) {
                    return false; // Exclude descendants
                }
                return true;
            });
            
            // Store filtered regulations for search
            let availableRegulations = filteredRegulations;
            
            // Setup search functionality when modal is shown
            if (searchInput) {
                // Store reference to filteredRegulations in closure
                const regulationsToFilter = availableRegulations;
                console.log('Setting up search with', regulationsToFilter.length, 'regulations');
                
                // Use oninput directly for more reliable event handling
                searchInput.oninput = function() {
                    const searchTerm = this.value.toLowerCase().trim();
                    console.log('=== SEARCH INPUT EVENT ===');
                    console.log('Search term:', searchTerm);
                    console.log('Regulations to filter:', regulationsToFilter.length);
                    
                    let filtered;
                    if (searchTerm === '') {
                        // Show all filtered regulations if search is empty
                        filtered = regulationsToFilter;
                        console.log('Empty search, showing all regulations');
                    } else {
                        // Filter by name or refNumber
                        filtered = regulationsToFilter.filter(reg => {
                            const name = (reg.primaryName || reg.PrimaryName || reg.name || reg.Name || '').toLowerCase();
                            const refNumber = (reg.refNumber || reg.RefNumber || '').toLowerCase();
                            const matches = name.includes(searchTerm) || refNumber.includes(searchTerm);
                            return matches;
                        });
                        console.log('Filtered to', filtered.length, 'regulations matching:', searchTerm);
                    }
                    
                    // Re-render the list with filtered results
                    renderRegulationList(filtered);
                };
                
                // Also add keyup for better compatibility
                searchInput.onkeyup = searchInput.oninput;
                
                // Focus on search input when modal opens
                setTimeout(() => {
                    searchInput.focus();
                }, 100);
            }
            
            // Render regulations
            console.log('About to render regulations:', availableRegulations.length);
            renderRegulationList(availableRegulations);
            
            // Show modal
            console.log('Setting modal display to flex');
            modal.style.display = 'flex';
            console.log('Modal display style after setting:', modal.style.display);
        } catch (error) {
            console.error('Error loading regulations:', error);
            alert(window.I18n ? window.I18n.t('regulation.messages.failedToLoadList') : 'Failed to load regulations');
        }
    }

    // Check if a regulation is a descendant of another
    function isDescendantOf(regulationId, ancestorId, regulations) {
        const regulation = regulations.find(r => r.id === regulationId);
        if (!regulation || !regulation.parentId) {
            return false;
        }
        if (regulation.parentId === ancestorId) {
            return true;
        }
        return isDescendantOf(regulation.parentId, ancestorId, regulations);
    }

    // Render regulation list in modal
    function renderRegulationList(regulations) {
        const listContainer = document.getElementById('regulationList');
        
        if (!listContainer) {
            console.error('regulationList container not found!');
            return;
        }
        
        console.log('=== RENDERING REGULATION LIST ===');
        console.log('Rendering regulation list with', regulations.length, 'regulations');
        
        // Clear the list first
        listContainer.innerHTML = '';
        
        if (regulations.length === 0) {
            listContainer.innerHTML = '<p style="text-align: center; padding: 1rem; color: var(--text-secondary, #64748b);">' + (window.I18n ? window.I18n.t('regulation.messages.noRegulationsAvailable') : 'No regulations available') + '</p>';
            console.log('No regulations to render, showing "No regulations available"');
            return;
        }
        
        // Add "No Parent" option
        const noParentItem = document.createElement('div');
        noParentItem.className = 'regulation-item';
        noParentItem.style.borderBottom = '2px solid var(--border-color, #e2e8f0)';
        noParentItem.innerHTML = `
            <div class="regulation-item-name" style="font-style: italic; color: var(--text-secondary, #64748b);">No Parent</div>
            <div class="regulation-item-ref" style="font-style: italic; color: var(--text-secondary, #64748b);">Remove parent regulation</div>
        `;
        noParentItem.addEventListener('click', () => clearParent());
        listContainer.appendChild(noParentItem);
        
        // Render regulation items
        regulations.forEach((regulation, index) => {
            const regulationName = regulation.primaryName || regulation.PrimaryName || regulation.name || regulation.Name || 'Unnamed';
            const regulationRef = regulation.refNumber || regulation.RefNumber || 'No ref';
            console.log(`Rendering regulation ${index}: ${regulationName}`);
            
            const item = document.createElement('div');
            item.className = 'regulation-item';
            item.innerHTML = `
                <div class="regulation-item-name">${escapeHtml(regulationName)}</div>
                <div class="regulation-item-ref">${escapeHtml(regulationRef)}</div>
            `;
            item.addEventListener('click', () => selectRegulationAsParent(regulation));
            listContainer.appendChild(item);
        });
        
        console.log('Finished rendering regulation list');
    }

    // Select regulation as parent
    async function selectRegulationAsParent(regulation) {
        const candidateParentId = parseInt(regulation.id || regulation.ID, 10);
        if (!Number.isInteger(candidateParentId) || candidateParentId <= 0) {
            return;
        }

        const selectedSegmentId = segmentField && typeof segmentField.getValue === 'function'
            ? parseInt(segmentField.getValue(), 10)
            : NaN;
        if (segmentField && Number.isInteger(selectedSegmentId) && selectedSegmentId > 0) {
            const isValidForSegment = await segmentField.validateParentForSegment(candidateParentId, selectedSegmentId);
            if (!isValidForSegment) {
                alert('This parent is not valid for the selected segment. Please remove the parent first.');
                return;
            }
        }

        selectedParentId = candidateParentId;
        document.getElementById('parentName').value = regulation.primaryName || '';
        hasFormChanges = true;
        isDirty = true;
        closeParentModal();
    }

    // Clear parent
    function clearParent() {
        selectedParentId = null;
        document.getElementById('parentName').value = '';
        hasFormChanges = true;
        isDirty = true;
    }

    // Close parent modal
    function closeParentModal() {
        document.getElementById('parentSelectionModal').style.display = 'none';
    }

    // Escape HTML
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Show success message (centered, matching other impact tabs)
    function showSuccessMessage(message, isError = false) {
        // Remove any existing success message
        const existingMessage = document.getElementById('success-message');
        if (existingMessage) {
            existingMessage.remove();
        }
        
        // Create success message element
        const successDiv = document.createElement('div');
        successDiv.id = 'success-message';
        const backgroundColor = isError ? '#ef4444' : '#248567';
        successDiv.style.cssText = `
            position: fixed;
            top: 80px;
            left: 50%;
            transform: translateX(-50%);
            background-color: ${backgroundColor};
            color: white;
            padding: 12px 24px;
            border-radius: 6px;
            font-weight: 600;
            font-size: 14px;
            z-index: 10000;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            animation: slideDown 0.3s ease-out;
        `;
        successDiv.textContent = message;
        
        // Add CSS animation
        const style = document.createElement('style');
        style.textContent = `
            @keyframes slideDown {
                from {
                    opacity: 0;
                    transform: translateX(-50%) translateY(-20px);
                }
                to {
                    opacity: 1;
                    transform: translateX(-50%) translateY(0);
                }
            }
            @keyframes slideUp {
                from {
                    opacity: 1;
                    transform: translateX(-50%) translateY(0);
                }
                to {
                    opacity: 0;
                    transform: translateX(-50%) translateY(-20px);
                }
            }
        `;
        document.head.appendChild(style);
        document.body.appendChild(successDiv);
        
        // Auto remove after 3 seconds
        setTimeout(() => {
            if (successDiv.parentNode) {
                successDiv.style.animation = 'slideUp 0.3s ease-out';
                setTimeout(() => {
                    if (successDiv.parentNode) {
                        successDiv.remove();
                    }
                }, 300);
            }
        }, 3000);
    }

    // Show error message
    function showErrorMessage(message) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'error-message';
        messageDiv.textContent = message;
        messageDiv.style.cssText = `
            position: fixed;
            top: 80px;
            right: 20px;
            background-color: #ef4444;
            color: white;
            padding: 1rem 1.5rem;
            border-radius: 8px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            z-index: 10000;
            font-size: 0.875rem;
            font-weight: 500;
            max-width: 400px;
        `;
        document.body.appendChild(messageDiv);
        
        setTimeout(() => {
            messageDiv.remove();
        }, 5000);
    }

    // ========================
    // REGULATORS TABLE
    // ========================

    // Load regulators data
    async function loadRegulatorsData() {
        if (!currentRegulationId) {
            console.warn('No regulation ID available for loading regulators');
            renderRegulatorsTable();
            renderGeographyTable();
            return;
        }
        
        try {
            console.log('Loading regulators data for regulation ID:', currentRegulationId);
            
            // Load regulators for this regulation (WITHOUT inheritance - edit page only shows own data)
            const regulatorsResponse = await fetch(`/api/regulation-x-regulator/${currentRegulationId}`);
            if (regulatorsResponse.ok) {
                regulatorsData = await regulatorsResponse.json();
                originalRegulatorsData = JSON.parse(JSON.stringify(regulatorsData));
                console.log('Loaded regulators data:', regulatorsData);
            } else {
                regulatorsData = [];
                originalRegulatorsData = [];
            }
            
            // Load all regulators for dropdown
            const allRegulatorsResponse = await fetch('/api/regulator/list');
            if (allRegulatorsResponse.ok) {
                allRegulators = await allRegulatorsResponse.json();
                console.log('Loaded all regulators:', allRegulators);
            } else {
                allRegulators = [];
            }
            
            // Load all relation types for dropdown
            const relationTypesResponse = await fetch('/api/regulation-x-regulator-relationtype/list');
            if (relationTypesResponse.ok) {
                allRegulatorRelationTypes = await relationTypesResponse.json();
                console.log('Loaded relation types:', allRegulatorRelationTypes);
            } else {
                allRegulatorRelationTypes = [];
            }
            
            // Render the regulators table
            renderRegulatorsTable();
            
            // Initialize event listeners
            initializeRegulatorsTable();
            
            // Load geography data (depends on regulators)
            await loadGeographyData();
            
        } catch (error) {
            console.error('Error loading regulators data:', error);
            regulatorsData = [];
            originalRegulatorsData = [];
            allRegulators = [];
            allRegulatorRelationTypes = [];
            renderRegulatorsTable();
            initializeRegulatorsTable();
        }
    }

    // Render regulators table
    function renderRegulatorsTable() {
        const tableBody = document.getElementById('regulatorsTableBody');
        tableBody.innerHTML = '';
        
        // Render existing regulators
        regulatorsData.forEach((regulator, index) => {
            const row = createRegulatorRow(regulator, index);
            tableBody.appendChild(row);
        });
        
        // Only add empty row if table is completely empty
        if (regulatorsData.length === 0) {
            const emptyRow = createRegulatorRow(null, 0);
            tableBody.appendChild(emptyRow);
        }
    }

    // Create regulator row
    function createRegulatorRow(regulator, index) {
        const row = document.createElement('tr');
        row.className = regulator ? 'data-row' : 'empty-row';
        row.setAttribute('data-regulator-index', index);
        
        // Use relationId (the ID from regulation_x_regulator table) for deletion tracking
        if (regulator && (regulator.relationId || regulator.id)) {
            row.setAttribute('data-regulator-id', regulator.relationId || regulator.id);
        }
        
        // Relationship Type dropdown
        const relationTypeOptions = allRegulatorRelationTypes.map((rt) => {
            // Only select if there's existing data matching the type
            // For new rows, leave empty (mandatory selection required)
            const isSelected = regulator ? (regulator.relationTypeId == rt.id) : false;
            return `<option value="${rt.id}" ${isSelected ? 'selected' : ''}>${escapeHtml(rt.primaryname || rt.primaryName || rt.PrimaryName || '')}</option>`;
        }).join('');
        
        // Regulator dropdown
        const regulatorOptions = allRegulators.map(reg => {
            // Only select if there's existing data matching the regulator
            // For new rows, leave empty (mandatory selection required)
            const isSelected = regulator && regulator.regulatorId == reg.id;
            return `<option value="${reg.id}" ${isSelected ? 'selected' : ''}>${escapeHtml(reg.primaryname || reg.primaryName || reg.PrimaryName || '')}</option>`;
        }).join('');
        
        // Determine if we should select the placeholder (for new rows) or a specific value (for existing rows)
        const relationTypeSelectValue = regulator && regulator.relationTypeId ? regulator.relationTypeId : '';
        const regulatorSelectValue = regulator && regulator.regulatorId ? regulator.regulatorId : '';
        
        row.innerHTML = `
            <td>
                <select class="form-select" data-field="relationType">
                    <option value="" ${!relationTypeSelectValue ? 'selected' : ''}>${window.I18n ? window.I18n.t('regulation.placeholders.selectRelationshipType') : 'Select Relationship Type'}</option>
                    ${relationTypeOptions}
                </select>
            </td>
            <td>
                <select class="form-select" data-field="regulator">
                    <option value="" ${!regulatorSelectValue ? 'selected' : ''}>${window.I18n ? window.I18n.t('regulation.placeholders.selectRegulator') : 'Select Regulator'}</option>
                    ${regulatorOptions}
                </select>
            </td>
            <td class="action-column">
                <button type="button" class="btn btn-sm btn-success add-regulator-btn" title="Add">+</button>
                <button type="button" class="btn btn-sm btn-danger delete-regulator-btn" title="Delete">-</button>
            </td>
        `;
        
        // After creating the row, explicitly set the values to ensure placeholder is selected for new rows
        const relationTypeSelect = row.querySelector('[data-field="relationType"]');
        const regulatorSelect = row.querySelector('[data-field="regulator"]');
        
        if (relationTypeSelect) {
            relationTypeSelect.value = relationTypeSelectValue;
        }
        if (regulatorSelect) {
            regulatorSelect.value = regulatorSelectValue;
        }
        
        // Add change listeners
        const selects = row.querySelectorAll('select');
        selects.forEach(select => {
            select.addEventListener('change', () => {
                markRegulatorsAsChanged();
                // When regulator changes, update geography options
                if (select.dataset.field === 'regulator') {
                    updateAvailableGeographies();
                }
            });
        });
        
        return row;
    }

    // Initialize regulators table event listeners
    function initializeRegulatorsTable() {
        const tableBody = document.getElementById('regulatorsTableBody');
        if (!tableBody) return;
        
        // Remove old listeners by cloning
        const newTableBody = tableBody.cloneNode(true);
        tableBody.parentNode.replaceChild(newTableBody, tableBody);
        
        // Add new listeners with event delegation
        newTableBody.addEventListener('click', function(e) {
            if (e.target.closest('.add-regulator-btn')) {
                e.preventDefault();
                const row = e.target.closest('tr');
                addRegulatorRow(row);
            } else if (e.target.closest('.delete-regulator-btn')) {
                e.preventDefault();
                const row = e.target.closest('tr');
                deleteRegulatorRow(row);
            }
        });
    }

    // Add regulator row
    function addRegulatorRow(currentRow) {
        if (!currentRow) return;
        
        const relationTypeSelect = currentRow.querySelector('[data-field="relationType"]');
        const regulatorSelect = currentRow.querySelector('[data-field="regulator"]');
        
        if (!relationTypeSelect.value || !regulatorSelect.value) {
            alert(window.I18n ? window.I18n.t('regulation.messages.selectRelationshipTypeAndRegulator') : 'Please select both Relationship Type and Regulator before adding.');
            return;
        }
        
        // Add new empty row
        const tableBody = document.getElementById('regulatorsTableBody');
        const newEmptyRow = createRegulatorRow(null, regulatorsData.length);
        tableBody.appendChild(newEmptyRow);
        
        markRegulatorsAsChanged();
        
        // Update geography options when new regulator is added
        updateAvailableGeographies();
    }

    // Delete regulator row
    function deleteRegulatorRow(row) {
        if (!row) return;
        
        const tbody = row.closest('tbody');
        const allRows = tbody.querySelectorAll('tr');
        const isFirstRow = row === allRows[0];
        const regulatorId = row.getAttribute('data-regulator-id');
        
        console.log('=== DELETE REGULATOR ROW ===');
        console.log('Row:', row);
        console.log('Regulator ID:', regulatorId);
        console.log('Is first row:', isFirstRow);
        
        if (regulatorId) {
            // Check if this regulator has associated geographies that haven't been deleted
            const regulatorXRegulationId = parseInt(regulatorId);
            const associatedGeographies = geographyData.filter(geo => 
                geo.regulationXRegulatorID == regulatorXRegulationId
            );
            
            // Check if any of these geographies are NOT marked for deletion
            const undeletedGeographies = associatedGeographies.filter(geo => 
                !geographyToDelete.includes(geo.id)
            );
            
            // If there are undeleted geographies and this is the first deletion attempt
            if (undeletedGeographies.length > 0 && !regulatorsToDelete.includes(regulatorXRegulationId)) {
                // Show error and prevent deletion
                alert(window.I18n ? window.I18n.t('regulation.messages.deleteRegulatorGeographiesFirst', { count: undeletedGeographies.length }) : `Cannot delete regulator: There are ${undeletedGeographies.length} geography entries associated with this regulator. Please delete the geography entries first.`);
                return;
            }
            
            // Existing row with data
            if (isFirstRow) {
                // For first row, mark for deletion but preserve structure (clear data only)
                if (!regulatorsToDelete.includes(regulatorXRegulationId)) {
                    regulatorsToDelete.push(regulatorXRegulationId);
                    console.log('Added to regulatorsToDelete:', regulatorsToDelete);
                }
                
                // Also delete associated geography entries (if any remain)
                deleteGeographiesForRegulator(regulatorId);
                
                clearRegulatorRowData(row);
                markRegulatorsAsChanged();
            } else {
                // For other rows, remove entirely and mark for deletion
                if (!regulatorsToDelete.includes(regulatorXRegulationId)) {
                    regulatorsToDelete.push(regulatorXRegulationId);
                    console.log('Added to regulatorsToDelete:', regulatorsToDelete);
                }
                
                // Also delete associated geography entries (if any remain)
                deleteGeographiesForRegulator(regulatorId);
                
                row.remove();
                markRegulatorsAsChanged();
            }
        } else {
            // Empty row without saved data
            if (!isFirstRow) {
                row.remove();
            } else {
                clearRegulatorRowData(row);
            }
        }
        
        // Ensure there's always at least one row for adding
        const remainingRows = tbody.querySelectorAll('tr');
        if (remainingRows.length === 0) {
            addRegulatorRow(null);
        }
        
        // Update geography options after regulator deletion
        updateAvailableGeographies();
    }

    // Clear regulator row data
    function clearRegulatorRowData(row) {
        row.removeAttribute('data-regulator-id');
        row.classList.remove('data-row');
        row.classList.add('empty-row');
        
        const relationTypeSelect = row.querySelector('[data-field="relationType"]');
        const regulatorSelect = row.querySelector('[data-field="regulator"]');
        
        if (relationTypeSelect) relationTypeSelect.value = '';
        if (regulatorSelect) regulatorSelect.value = '';
    }

    // Mark regulators as changed
    function markRegulatorsAsChanged() {
        hasFormChanges = true;
        isDirty = true;
    }

    // Save regulators data
    async function saveRegulatorsData() {
        if (!currentRegulationId) {
            console.warn('No regulation ID available for saving regulators');
            return false;
        }
        
        try {
            console.log('=== SAVING REGULATORS DATA - DEBUG ===');
            console.log('Current regulation ID:', currentRegulationId);
            
            // Collect data from table rows
            const tableBody = document.getElementById('regulatorsTableBody');
            const rows = tableBody.querySelectorAll('tr');
            console.log('Found', rows.length, 'regulator rows');
            
            const inserts = [];
            const updates = [];
            
            rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const regulatorSelect = row.querySelector('[data-field="regulator"]');
                const regulatorId = row.getAttribute('data-regulator-id');
                
                console.log(`Row ${index}:`, {
                    relationTypeSelect: relationTypeSelect,
                    relationTypeValue: relationTypeSelect?.value,
                    regulatorSelect: regulatorSelect,
                    regulatorValue: regulatorSelect?.value,
                    regulatorId: regulatorId
                });
                
                // Skip empty rows
                if (!relationTypeSelect || !regulatorSelect || !relationTypeSelect.value || !regulatorSelect.value) {
                    console.log(`Skipping row ${index} - empty or missing fields`);
                    return;
                }
                
                const data = {
                    regulationId: currentRegulationId,
                    regulatorId: parseInt(regulatorSelect.value),
                    relationType: parseInt(relationTypeSelect.value)
                };
                
                console.log(`Row ${index} data:`, data);
                
                if (regulatorId) {
                    // Existing record - update
                    data.id = parseInt(regulatorId);
                    updates.push(data);
                    console.log(`Added to updates:`, data);
                } else {
                    // New record - insert
                    inserts.push(data);
                    console.log(`Added to inserts:`, data);
                }
            });
            
            // Prepare operations
            const operations = {
                inserts: inserts,
                updates: updates,
                deletes: regulatorsToDelete  // Changed from 'deletions' to 'deletes' to match servlet
            };
            
            console.log('Regulators operations:', operations);
            console.log('Inserts detail:', JSON.stringify(inserts, null, 2));
            console.log('Updates detail:', JSON.stringify(updates, null, 2));
            console.log('Deletes detail:', JSON.stringify(regulatorsToDelete, null, 2));
            
            // Send to backend
            const response = await fetch(`/api/regulation-x-regulator/regulation/${currentRegulationId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(operations)
            });
            
            if (response.ok) {
                console.log('Regulators data saved successfully');
                
                // Reload data to get updated IDs
                await loadRegulatorsData();
                
                // Reset deletion array
                regulatorsToDelete = [];
                
                return true;
            } else {
                const errorText = await response.text();
                throw new Error(`Failed to save regulators: ${response.status} - ${errorText}`);
            }
            
        } catch (error) {
            console.error('Error saving regulators data:', error);
            throw error; // Propagate error to main save function
        }
    }

    // ========================
    // GEOGRAPHY TABLE
    // ========================

    // Delete geographies for a specific regulator
    function deleteGeographiesForRegulator(regulatorXRegulationId) {
        // Find all geography entries associated with this regulator relationship
        const geographiesToRemove = geographyData.filter(geo => 
            geo.regulationXRegulatorID == regulatorXRegulationId
        );
        
        geographiesToRemove.forEach(geo => {
            if (geo.id && !geographyToDelete.includes(geo.id)) {
                geographyToDelete.push(geo.id);
            }
        });
        
        // Remove from display
        geographyData = geographyData.filter(geo => 
            geo.regulationXRegulatorID != regulatorXRegulationId
        );
        
        renderGeographyTable();
    }

    // Reload geography data after save - replace completely
    async function reloadGeographyDataAfterSave() {
        if (!currentRegulationId) return;
        
        try {
            // Load geography for this regulation (WITHOUT inheritance - edit page only shows own data)
            const geographyResponse = await fetch(`/api/regulation-x-regulator-x-geography/regulation/${currentRegulationId}?edit=true`);
            if (geographyResponse.ok) {
                const loadedData = await geographyResponse.json();
                
                // Normalize loaded data to ensure consistent field names
                const normalizedLoadedData = loadedData.map(item => ({
                    id: item.id || item.relationId || item.ID || null,
                    relationId: item.id || item.relationId || item.ID || null,
                    regulatorXGeographyId: item.regulatorXGeographyId || item.regulatorXGeographyID || null,
                    regulatorXGeographyID: item.regulatorXGeographyId || item.regulatorXGeographyID || null,
                    description: item.description || item.relationDescription || '',
                    regulationXRegulatorID: item.regulationXRegulatorID || item.regulationXRegulatorId || null
                }));
                
                // Replace completely - no merging after save
                geographyData = normalizedLoadedData;
                originalGeographyData = JSON.parse(JSON.stringify(geographyData));
                
                // Update available regulator-geography combinations
                await updateAvailableGeographies();
                
                // Render the geography table
                renderGeographyTable();
                
                // Initialize event listeners
                initializeGeographyTable();
            }
        } catch (error) {
            console.error('Error reloading geography data after save:', error);
        }
    }

    // Load geography data
    async function loadGeographyData() {
        if (!currentRegulationId) {
            console.warn('No regulation ID available for loading geography');
            renderGeographyTable();
            return;
        }
        
        try {
            console.log('Loading geography data for regulation ID:', currentRegulationId);
            
            // Save current form data before loading to preserve user selections
            saveCurrentGeographyFormData();
            
            // Load geography for this regulation (WITHOUT inheritance - edit page only shows own data)
            const geographyResponse = await fetch(`/api/regulation-x-regulator-x-geography/regulation/${currentRegulationId}?edit=true`);
            if (geographyResponse.ok) {
                const loadedData = await geographyResponse.json();
                
                // Save current unsaved entries from form before replacing
                const unsavedEntries = geographyData.filter(g => !g.id && !g.relationId);
                
                // Normalize loaded data to ensure consistent field names
                const normalizedLoadedData = loadedData.map(item => ({
                    id: item.id || item.relationId || item.ID || null,
                    relationId: item.id || item.relationId || item.ID || null,
                    regulatorXGeographyId: item.regulatorXGeographyId || item.regulatorXGeographyID || null,
                    regulatorXGeographyID: item.regulatorXGeographyId || item.regulatorXGeographyID || null,
                    description: item.description || item.relationDescription || '',
                    regulationXRegulatorID: item.regulationXRegulatorID || item.regulationXRegulatorId || null
                }));
                
                // Replace saved entries with loaded data, keep unsaved entries
                geographyData = [...normalizedLoadedData, ...unsavedEntries];
                originalGeographyData = JSON.parse(JSON.stringify(geographyData));
            } else {
                // If load fails, keep current data
                originalGeographyData = JSON.parse(JSON.stringify(geographyData));
            }
            
            // Load all geographies
            const allGeographiesResponse = await fetch('/api/geography/list');
            if (allGeographiesResponse.ok) {
                allGeographies = await allGeographiesResponse.json();
                console.log('Loaded all geographies:', allGeographies);
            } else {
                allGeographies = [];
            }
            
            // Update available regulator-geography combinations
            await updateAvailableGeographies();
            
            // Render the geography table
            renderGeographyTable();
            
            // Initialize event listeners
            initializeGeographyTable();
            
        } catch (error) {
            console.error('Error loading geography data:', error);
            geographyData = [];
            originalGeographyData = [];
            allGeographies = [];
            renderGeographyTable();
            initializeGeographyTable();
        }
    }

    // Update available regulator-geography combinations based on selected regulators
    async function updateAvailableGeographies() {
        try {
            // Get selected regulators from the regulators table
            const selectedRegulators = [];
            const regulatorsTableBody = document.getElementById('regulatorsTableBody');
            const rows = regulatorsTableBody.querySelectorAll('tr');
            
            rows.forEach(row => {
                const regulatorSelect = row.querySelector('[data-field="regulator"]');
                if (regulatorSelect && regulatorSelect.value) {
                    selectedRegulators.push(parseInt(regulatorSelect.value));
                }
            });
            
            console.log('Selected regulators:', selectedRegulators);
            
            if (selectedRegulators.length === 0) {
                availableRegulatorGeographies = [];
                renderGeographyTable();
                return;
            }
            
            // Load regulator-geography mappings for selected regulators
            const promises = selectedRegulators.map(regulatorId => 
                fetch(`/api/regulator-x-geography/${regulatorId}`)
                    .then(r => r.ok ? r.json() : [])
                    .catch(error => {
                        console.warn(`Failed to load geographies for regulator ${regulatorId}:`, error);
                        return [];
                    })
            );
            
            const results = await Promise.all(promises);
            
            // Flatten and combine results
            availableRegulatorGeographies = [];
            results.forEach((mappings, index) => {
                const regulatorId = selectedRegulators[index];
                
                // Ensure mappings is an array
                if (Array.isArray(mappings)) {
                    mappings.forEach(mapping => {
                        const regulator = allRegulators.find(r => r.id === regulatorId);
                        const geography = allGeographies.find(g => g.id === (mapping.geographyId || mapping.GeographyID));
                        
                        const regulatorName = regulator?.primaryname || regulator?.primaryName || regulator?.PrimaryName || '';
                        const geographyName = geography?.primaryname || geography?.primaryName || geography?.PrimaryName || '';
                        
                        availableRegulatorGeographies.push({
                            regulatorId: regulatorId,
                            geographyId: mapping.geographyId || mapping.GeographyID,
                            regulatorXGeographyId: mapping.id || mapping.ID,
                            regulatorName: regulatorName,
                            geographyName: geographyName
                        });
                        
                        console.log('Added geography option:', {
                            regulatorName,
                            geographyName,
                            display: `${geographyName} (${regulatorName})`
                        });
                    });
                } else {
                    console.warn(`Invalid response format for regulator ${regulatorId}:`, mappings);
                }
            });
            
            console.log('Available regulator-geography combinations:', availableRegulatorGeographies);
            
            // Re-render geography table with updated options
            renderGeographyTable();
            
        } catch (error) {
            console.error('Error updating available geographies:', error);
        }
    }

    // Render geography table - VERSION 2.0 (NO RESTORATION)
    function renderGeographyTable() {
        const tableBody = document.getElementById('geographyTableBody');
        tableBody.innerHTML = '';
        
        if (availableRegulatorGeographies.length === 0) {
            const emptyRow = document.createElement('tr');
            emptyRow.innerHTML = '<td colspan="4" style="text-align: center; color: #6b7280;">' + (window.I18n ? window.I18n.t('regulation.messages.noRegulatorOrGeography') : 'No regulators selected or no geographies available for selected regulators') + '</td>';
            tableBody.appendChild(emptyRow);
            return;
        }
        
        // Get selected regulators from the regulators table to filter geographies
        const selectedRegulators = [];
        const regulatorsTableBody = document.getElementById('regulatorsTableBody');
        if (regulatorsTableBody) {
            const rows = regulatorsTableBody.querySelectorAll('tr');
            rows.forEach(row => {
                const regulatorSelect = row.querySelector('[data-field="regulator"]');
                const regulatorId = row.getAttribute('data-regulator-id');
                // Only include regulators that are not marked for deletion
                if (regulatorSelect && regulatorSelect.value && regulatorId && !regulatorsToDelete.includes(parseInt(regulatorId))) {
                    selectedRegulators.push(parseInt(regulatorSelect.value));
                }
            });
        }
        
        // Filter geography data to only show geographies for selected regulators
        const filteredGeographyData = geographyData.filter(geography => {
            // Find the regulator-geography mapping
            // Check both regulatorXGeographyId and regulatorXGeographyID for compatibility
            const mapping = availableRegulatorGeographies.find(rg => 
                rg.regulatorXGeographyId == geography.regulatorXGeographyID ||
                rg.regulatorXGeographyId == geography.regulatorXGeographyId
            );
            // Only show if the regulator is in the selected regulators list
            return mapping && selectedRegulators.includes(mapping.regulatorId);
        });
        
        // Render filtered geography entries
        filteredGeographyData.forEach((geography, index) => {
            const row = createGeographyRow(geography, index);
            tableBody.appendChild(row);
        });
        
        // Only add empty row if table is completely empty
        if (filteredGeographyData.length === 0) {
            const emptyRow = createGeographyRow(null, 0);
            tableBody.appendChild(emptyRow);
        }
    }

    // Create geography row
    function createGeographyRow(geography, index) {
        const row = document.createElement('tr');
        row.className = geography ? 'data-row' : 'empty-row';
        row.setAttribute('data-geography-index', index);
        
        // Use relationId (the ID from regulation_x_regulator_x_geography table) for deletion tracking
        if (geography && (geography.relationId || geography.id)) {
            row.setAttribute('data-geography-id', geography.relationId || geography.id);
        }
        
        // Geography-Regulator combination dropdown
        const geographyRegulatorOptions = availableRegulatorGeographies.map((rg, idx) => {
            // Only select matching geography for existing data
            // For new rows, don't auto-select anything - let user choose
            const isSelected = geography 
                ? (geography.regulatorXGeographyId == rg.regulatorXGeographyId || geography.regulatorXGeographyID == rg.regulatorXGeographyId)
                : false; // No auto-select for new rows
            
            return `<option value="${rg.regulatorXGeographyId}" data-regulator-id="${rg.regulatorId}" data-geography-id="${rg.geographyId}" ${isSelected ? 'selected' : ''}>${escapeHtml(rg.geographyName)} (${escapeHtml(rg.regulatorName)})</option>`;
        }).join('');
        
        // Always show placeholder option
        const placeholderOption = '<option value="">Select Geography</option>';
        
        console.log('Creating geography row, available options:', availableRegulatorGeographies.length);
        console.log('Geography regulator options HTML:', geographyRegulatorOptions);
        
        row.innerHTML = `
            <td>
                <select class="form-select" data-field="geographyRegulator">
                    ${placeholderOption}
                    ${geographyRegulatorOptions}
                </select>
            </td>
            <td>
                <input type="text" class="form-input" data-field="description" value="${geography ? escapeHtml(geography.description || '') : ''}" placeholder="${window.I18n ? window.I18n.t('regulation.placeholders.enterDescription') : 'Enter description'}">
            </td>
            <td class="action-column">
                <button type="button" class="btn btn-sm btn-success add-geography-btn" title="Add">+</button>
                <button type="button" class="btn btn-sm btn-danger delete-geography-btn" title="Delete">-</button>
            </td>
        `;
        
        // Log the actual select element after rendering
        const selectElement = row.querySelector('[data-field="geographyRegulator"]');
        console.log('Rendered select element:', selectElement);
        console.log('Select options count:', selectElement?.options.length);
        console.log('Select current value:', selectElement?.value);
        
        // Add change listeners - no need to save on every change, just mark as changed
        const inputs = row.querySelectorAll('select, input');
        inputs.forEach(input => {
            input.addEventListener('change', () => {
                markGeographyAsChanged();
            });
        });
        
        return row;
    }

    // Get regulator name for geography entry
    function getRegulatorNameForGeography(geography) {
        // Find the regulator-geography mapping
        const mapping = availableRegulatorGeographies.find(rg => 
            rg.regulatorXGeographyId == geography.regulatorXGeographyID
        );
        return mapping ? mapping.regulatorName : 'Unknown';
    }

    // Initialize geography table event listeners
    function initializeGeographyTable() {
        const tableBody = document.getElementById('geographyTableBody');
        if (!tableBody) return;
        
        // Remove old listeners by cloning
        const newTableBody = tableBody.cloneNode(true);
        tableBody.parentNode.replaceChild(newTableBody, tableBody);
        
        // Add new listeners with event delegation
        newTableBody.addEventListener('click', function(e) {
            if (e.target.closest('.add-geography-btn')) {
                e.preventDefault();
                const row = e.target.closest('tr');
                addGeographyRow(row);
            } else if (e.target.closest('.delete-geography-btn')) {
                e.preventDefault();
                const row = e.target.closest('tr');
                deleteGeographyRow(row);
            }
        });
    }

    // Add geography row
    function addGeographyRow(currentRow) {
        if (!currentRow) return;
        
        const geographyRegulatorSelect = currentRow.querySelector('[data-field="geographyRegulator"]');
        
        if (!geographyRegulatorSelect || !geographyRegulatorSelect.value) {
            alert(window.I18n ? window.I18n.t('regulation.messages.selectGeographyBeforeAdding') : 'Please select Geography before adding.');
            return;
        }
        
        // Add new empty row directly - no need to save form data here
        const tableBody = document.getElementById('geographyTableBody');
        const newEmptyRow = createGeographyRow(null, 0);
        tableBody.appendChild(newEmptyRow);
        
        markGeographyAsChanged();
    }

    // Delete geography row
    function deleteGeographyRow(row) {
        if (!row) return;
        
        const tbody = row.closest('tbody');
        const allRows = tbody.querySelectorAll('tr');
        const isFirstRow = row === allRows[0];
        const geographyId = row.getAttribute('data-geography-id');
        
        if (geographyId) {
            // Existing saved row - mark for deletion
            if (!geographyToDelete.includes(parseInt(geographyId))) {
                geographyToDelete.push(parseInt(geographyId));
            }
            // Remove from geographyData
            geographyData = geographyData.filter(g => (g.id || g.relationId) != geographyId);
            
            if (isFirstRow) {
                clearGeographyRowData(row);
            } else {
                row.remove();
            }
            markGeographyAsChanged();
        } else {
            // Empty row - just remove it
            if (!isFirstRow) {
                row.remove();
            } else {
                clearGeographyRowData(row);
            }
        }
        
        // Ensure there's always at least one row
        const remainingRows = tbody.querySelectorAll('tr');
        if (remainingRows.length === 0) {
            const tableBody = document.getElementById('geographyTableBody');
            if (tableBody) {
                const emptyRow = createGeographyRow(null, 0);
                tableBody.appendChild(emptyRow);
            }
        }
    }

    // Clear geography row data
    function clearGeographyRowData(row) {
        row.removeAttribute('data-geography-id');
        row.classList.remove('data-row');
        row.classList.add('empty-row');
        
        const geographyRegulatorSelect = row.querySelector('[data-field="geographyRegulator"]');
        const descriptionInput = row.querySelector('[data-field="description"]');
        const regulatorDisplay = row.querySelector('.regulator-display');
        
        if (geographyRegulatorSelect) geographyRegulatorSelect.value = '';
        if (descriptionInput) descriptionInput.value = '';
        if (regulatorDisplay) regulatorDisplay.textContent = (window.I18n ? window.I18n.t('message.notSpecified') : '-');
    }

    // Mark geography as changed
    function markGeographyAsChanged() {
        hasFormChanges = true;
        isDirty = true;
    }
    
    // Save current geography form data from DOM to geographyData array
    // This is called before re-rendering to preserve user input
    function saveCurrentGeographyFormData() {
        const tableBody = document.getElementById('geographyTableBody');
        if (!tableBody) return;
        
        const rows = tableBody.querySelectorAll('tr');
        const savedIds = new Set(); // Track saved entries by ID
        const unsavedEntries = []; // Track unsaved entries
        
        rows.forEach((row) => {
            const geographyRegulatorSelect = row.querySelector('[data-field="geographyRegulator"]');
            const descriptionInput = row.querySelector('[data-field="description"]');
            const geographyId = row.getAttribute('data-geography-id');
            
            // Skip empty rows
            if (!geographyRegulatorSelect || !geographyRegulatorSelect.value) {
                return;
            }
            
            const regulatorXGeographyId = parseInt(geographyRegulatorSelect.value);
            const description = descriptionInput ? descriptionInput.value : '';
            
            if (geographyId) {
                // Existing saved entry - update it
                const entry = geographyData.find(g => (g.id || g.relationId) == geographyId);
                if (entry) {
                    entry.regulatorXGeographyId = regulatorXGeographyId;
                    entry.regulatorXGeographyID = regulatorXGeographyId;
                    entry.description = description;
                    savedIds.add(geographyId);
                }
            } else {
                // New unsaved entry - add to temporary array
                unsavedEntries.push({
                    id: null,
                    relationId: null,
                    regulatorXGeographyId: regulatorXGeographyId,
                    regulatorXGeographyID: regulatorXGeographyId,
                    description: description,
                    regulationXRegulatorID: null
                });
            }
        });
        
        // Update geographyData: keep saved entries + new unsaved entries (remove duplicates)
        const savedEntries = geographyData.filter(g => {
            const gId = g.id || g.relationId;
            return gId && savedIds.has(gId.toString());
        });
        
        // Remove duplicate unsaved entries
        const uniqueUnsaved = [];
        const seen = new Set();
        unsavedEntries.forEach(entry => {
            const key = `${entry.regulatorXGeographyId}_${entry.description}`;
            if (!seen.has(key)) {
                seen.add(key);
                uniqueUnsaved.push(entry);
            }
        });
        
        geographyData = [...savedEntries, ...uniqueUnsaved];
    }

    // Save geography data
    async function saveGeographyData() {
        if (!currentRegulationId) {
            console.warn('No regulation ID available for saving geography');
            return false;
        }
        
        try {
            console.log('=== GEOGRAPHY SAVE VERSION 2.0 - SIMPLE APPROACH ===');
            console.log('Current time:', new Date().toISOString());
            console.log('Current regulation ID:', currentRegulationId);
            
            // Collect data from table rows
            const tableBody = document.getElementById('geographyTableBody');
            const rows = tableBody.querySelectorAll('tr');
            console.log('Found', rows.length, 'geography rows');
            
            const inserts = [];
            const updates = [];
            const processed = new Set(); // Track processed entries to avoid duplicates
            
            rows.forEach((row) => {
                const geographyRegulatorSelect = row.querySelector('[data-field="geographyRegulator"]');
                const descriptionInput = row.querySelector('[data-field="description"]');
                const geographyId = row.getAttribute('data-geography-id');
                
                // Skip empty rows
                if (!geographyRegulatorSelect || !geographyRegulatorSelect.value) {
                    return;
                }
                
                const regulatorXGeographyId = parseInt(geographyRegulatorSelect.value);
                const description = descriptionInput ? descriptionInput.value : '';
                
                // Skip if already processed (avoid duplicates)
                const key = geographyId || `new_${regulatorXGeographyId}_${description}`;
                if (processed.has(key)) {
                    return;
                }
                processed.add(key);
                
                if (isNaN(regulatorXGeographyId)) {
                    console.error(`Invalid regulatorXGeographyId value: ${geographyRegulatorSelect.value}`);
                    return;
                }
                
                const selectedOption = geographyRegulatorSelect.options[geographyRegulatorSelect.selectedIndex];
                const regulatorId = selectedOption ? parseInt(selectedOption.getAttribute('data-regulator-id')) : null;
                
                // Find the regulator entry from regulatorsData
                let regulatorEntry = regulatorsData.find(r => r.regulatorId == regulatorId);
                
                // If not found, try to find from the regulators table in the DOM
                if (!regulatorEntry && regulatorId) {
                    const regulatorsTableBody = document.getElementById('regulatorsTableBody');
                    if (regulatorsTableBody) {
                        const regulatorRows = regulatorsTableBody.querySelectorAll('tr[data-regulator-id]');
                        for (const regRow of regulatorRows) {
                            const rowRegulatorSelect = regRow.querySelector('[data-field="regulator"]');
                            if (rowRegulatorSelect && parseInt(rowRegulatorSelect.value) == regulatorId) {
                                const rowRegulatorId = regRow.getAttribute('data-regulator-id');
                                regulatorEntry = regulatorsData.find(r => (r.relationId || r.id) == rowRegulatorId);
                                break;
                            }
                        }
                    }
                }
                
                const finalRegulationXRegulatorId = regulatorEntry ? (regulatorEntry.relationId || regulatorEntry.id) : null;
                
                if (!finalRegulationXRegulatorId) {
                    console.error(`Cannot save geography - no regulation_x_regulator entry found for regulator ID ${regulatorId}`);
                    alert(window.I18n ? window.I18n.t('regulation.messages.cannotSaveGeographyNoRegulator') : 'Error: Cannot save geography. No regulator relationship found for the selected regulator. Please ensure the regulator is saved first.');
                    return;
                }
                
                const data = {
                    regulationXRegulatorId: finalRegulationXRegulatorId,
                    regulatorXGeographyId: regulatorXGeographyId,
                    relationType: 1,
                    description: description
                };
                
                if (geographyId) {
                    // Existing record - update
                    data.id = parseInt(geographyId);
                    updates.push(data);
                    console.log(`Added to updates:`, data);
                } else {
                    // New record - insert
                    inserts.push(data);
                    console.log(`Added to inserts:`, data);
                }
            });
            
            // Prepare operations
            const operations = {
                inserts: inserts,
                updates: updates,
                deletes: geographyToDelete  // Changed from 'deletions' to 'deletes' to match servlet
            };
            
            console.log('Geography operations:', operations);
            
            // Send to backend
            const response = await fetch(`/api/regulation-x-regulator-x-geography/regulation/${currentRegulationId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(operations)
            });
            
            if (response.ok) {
                console.log('Geography data saved successfully');
                
                // Reload data to get updated IDs - replace completely, don't merge
                await reloadGeographyDataAfterSave();
                
                // Reset deletion array
                geographyToDelete = [];
                
                return true;
            } else {
                const errorText = await response.text();
                throw new Error(`Failed to save geography: ${response.status} - ${errorText}`);
            }
            
        } catch (error) {
            console.error('Error saving geography data:', error);
            throw error; // Propagate error to main save function
        }
    }

    // Relationships management
    let relationshipsData = [];
    let originalRelationshipsData = [];
    let relationshipsToDelete = [];
    let allRelationshipRegulations = [];
    let allRelationshipTypes = [];

    // Load relationships data
    async function loadRelationshipsData() {
        if (!currentRegulationId) {
            console.warn('No regulation ID available for loading relationships');
            return;
        }
        
        try {
            console.log('Loading relationships for regulation ID:', currentRegulationId);
            
            // Load all regulations and relationship types for dropdowns
            await loadRelationshipsDropdownData();
            
            // Load existing relationships
            const response = await fetch(`/api/regulation-x-regulation/source/${currentRegulationId}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            relationshipsData = await response.json();
            originalRelationshipsData = JSON.parse(JSON.stringify(relationshipsData));
            relationshipsToDelete = [];
            
            console.log('Loaded relationships:', relationshipsData);
            
            // Ensure there's always at least one empty row for adding
            if (relationshipsData.length === 0) {
                const emptyRelationship = {
                    id: null,
                    targetRegulationId: null,
                    relationType: null,
                    description: ''
                };
                relationshipsData.push(emptyRelationship);
            }
            
            renderRelationshipsTable();
            initializeRelationshipsTable();
            
        } catch (error) {
            console.error('Error loading relationships:', error);
            const tbody = document.getElementById('relationshipsTableBody');
            tbody.innerHTML = `
                <tr>
                    <td colspan="4" style="text-align: center; padding: 2rem; color: var(--danger, #dc3545);">
                        <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem; display: block;"></i>
                        <p>${window.I18n ? window.I18n.t('regulation.messages.errorLoadingRelationships', { error: error.message }) : 'Error loading relationships: ' + error.message}</p>
                    </td>
                </tr>
            `;
        }
    }

    // Load dropdown data for relationships
    async function loadRelationshipsDropdownData() {
        try {
            // Load all regulations
            const regulationsResponse = await fetch('/api/regulation/list');
            if (regulationsResponse.ok) {
                allRelationshipRegulations = await regulationsResponse.json();
                console.log('Loaded regulations for dropdown:', allRelationshipRegulations);
            }
            
            // Load relationship types
            const typesResponse = await fetch('/api/regulation-x-regulation-relationtype/');
            if (typesResponse.ok) {
                allRelationshipTypes = await typesResponse.json();
                console.log('Loaded relationship types for dropdown:', allRelationshipTypes);
            }
            
        } catch (error) {
            console.error('Error loading dropdown data:', error);
        }
    }

    // Render relationships table
    function renderRelationshipsTable() {
        const tbody = document.getElementById('relationshipsTableBody');
        
        // Always ensure there's at least one empty row for adding
        if (relationshipsData.length === 0) {
            const emptyRelationship = {
                id: null,
                targetRegulationId: null,
                relationType: null,
                description: ''
            };
            relationshipsData.push(emptyRelationship);
        }
        
        tbody.innerHTML = relationshipsData.map((relationship, index) => 
            createRelationshipRow(relationship, index)
        ).join('');
    }

    // Create relationship row
    function createRelationshipRow(relationship, index) {
        const isFirstRow = index === 0;
        
        return `
            <tr data-index="${index}" data-id="${relationship.id || ''}">
                <td>
                    <select class="form-input target-regulation-select" ${isFirstRow ? 'data-first-row="true"' : ''}>
                        <option value="">Select Target Regulation</option>
                        ${allRelationshipRegulations.map(reg => 
                            `<option value="${reg.id}" ${relationship.targetRegulationId == reg.id ? 'selected' : ''}>${escapeHtml(reg.primaryname || reg.primaryName || reg.PrimaryName || 'Unnamed')}</option>`
                        ).join('')}
                    </select>
                </td>
                <td>
                    <select class="form-input relationship-type-select" ${isFirstRow ? 'data-first-row="true"' : ''}>
                        <option value="">${window.I18n ? window.I18n.t('regulation.placeholders.selectRelationshipType') : 'Select Relationship Type'}</option>
                        ${allRelationshipTypes.map(type => 
                            `<option value="${type.id}" ${relationship.relationType == type.id ? 'selected' : ''}>${escapeHtml(type.primaryName || type.primaryname || type.PrimaryName || 'Unnamed')}</option>`
                        ).join('')}
                    </select>
                </td>
                <td>
                    <input type="text" class="form-input relationship-description" 
                           value="${escapeHtml(relationship.description || '')}" 
                           placeholder="${window.I18n ? window.I18n.t('regulation.placeholders.enterDescription') : 'Enter description'}"
                           ${isFirstRow ? 'data-first-row="true"' : ''}>
                </td>
                <td>
                    <div class="action-buttons">
                        <button type="button" class="btn-icon btn-add" title="Add Relationship" onclick="addRelationshipRow()">
                            <i class="fas fa-plus"></i>
                        </button>
                        <button type="button" class="btn-icon btn-delete" title="Delete Relationship" onclick="deleteRelationshipRow(${index})">
                            <i class="fas fa-minus"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }

    // Add relationship row
    function addRelationshipRow() {
        const newRelationship = {
            id: null,
            targetRegulationId: null,
            relationType: null,
            description: ''
        };
        
        relationshipsData.push(newRelationship);
        renderRelationshipsTable();
        initializeRelationshipsTable();
        markRelationshipsAsChanged();
    }
    
    // Make functions globally accessible for onclick handlers
    window.addRelationshipRow = addRelationshipRow;
    window.deleteRelationshipRow = deleteRelationshipRow;

    // Delete relationship row
    function deleteRelationshipRow(index) {
        const relationship = relationshipsData[index];
        
        if (index === 0) {
            // First row - clear data but keep structure
            clearRelationshipRowData(index);
        } else {
            // Other rows - remove completely
            if (relationship.id) {
                relationshipsToDelete.push(relationship.id);
            }
            relationshipsData.splice(index, 1);
            renderRelationshipsTable();
            initializeRelationshipsTable();
        }
        
        // Ensure there's always at least one row for adding
        if (relationshipsData.length === 0) {
            addRelationshipRow();
        }
        
        markRelationshipsAsChanged();
    }

    // Clear relationship row data
    function clearRelationshipRowData(index) {
        const relationship = relationshipsData[index];
        relationship.targetRegulationId = null;
        relationship.relationType = null;
        relationship.description = '';
        
        if (relationship.id) {
            relationshipsToDelete.push(relationship.id);
        }
        
        // Clear form fields
        const row = document.querySelector(`tr[data-index="${index}"]`);
        if (row) {
            const targetSelect = row.querySelector('.target-regulation-select');
            const typeSelect = row.querySelector('.relationship-type-select');
            const descriptionInput = row.querySelector('.relationship-description');
            
            if (targetSelect) targetSelect.value = '';
            if (typeSelect) typeSelect.value = '';
            if (descriptionInput) descriptionInput.value = '';
        }
        
        markRelationshipsAsChanged();
    }

    // Initialize relationships table event listeners
    function initializeRelationshipsTable() {
        // Remove existing listeners
        document.querySelectorAll('.target-regulation-select, .relationship-type-select, .relationship-description').forEach(element => {
            element.removeEventListener('change', handleRelationshipChange);
            element.removeEventListener('input', handleRelationshipChange);
        });
        
        // Add new listeners
        document.querySelectorAll('.target-regulation-select, .relationship-type-select, .relationship-description').forEach(element => {
            element.addEventListener('change', handleRelationshipChange);
            element.addEventListener('input', handleRelationshipChange);
        });
    }

    // Handle relationship field changes
    function handleRelationshipChange(event) {
        const row = event.target.closest('tr');
        const index = parseInt(row.dataset.index);
        const relationship = relationshipsData[index];
        
        if (event.target.classList.contains('target-regulation-select')) {
            relationship.targetRegulationId = event.target.value ? parseInt(event.target.value) : null;
        } else if (event.target.classList.contains('relationship-type-select')) {
            relationship.relationType = event.target.value ? parseInt(event.target.value) : null;
        } else if (event.target.classList.contains('relationship-description')) {
            relationship.description = event.target.value;
        }
        
        markRelationshipsAsChanged();
    }

    // Mark relationships as changed
    function markRelationshipsAsChanged() {
        // This will be called when relationships are modified
        console.log('Relationships marked as changed');
    }

    // Save relationships data
    async function saveRelationshipsData() {
        if (!currentRegulationId) {
            console.warn('No regulation ID available for saving relationships');
            return;
        }
        
        try {
            // Collect data from table rows
            const inserts = [];
            const updates = [];
            
            relationshipsData.forEach((relationship, index) => {
                const row = document.querySelector(`tr[data-index="${index}"]`);
                if (!row) return;
                
                const targetSelect = row.querySelector('.target-regulation-select');
                const typeSelect = row.querySelector('.relationship-type-select');
                const descriptionInput = row.querySelector('.relationship-description');
                
                const targetRegulationId = targetSelect ? parseInt(targetSelect.value) : null;
                const relationType = typeSelect ? parseInt(typeSelect.value) : null;
                const description = descriptionInput ? descriptionInput.value : '';
                
                // Only process if both target and type are selected
                if (targetRegulationId && relationType) {
                    const relationshipData = {
                        targetRegulationId: targetRegulationId,
                        relationType: relationType,
                        description: description
                    };
                    
                    if (relationship.id) {
                        // Update existing
                        relationshipData.id = relationship.id;
                        updates.push(relationshipData);
                    } else {
                        // Insert new
                        inserts.push(relationshipData);
                    }
                }
            });
            
            // Prepare batch data
            const batchData = {
                inserts: inserts,
                updates: updates,
                deletes: relationshipsToDelete
            };
            
            console.log('Saving relationships batch data:', batchData);
            
            // Send batch request
            const response = await fetch(`/api/regulation-x-regulation/batch/${currentRegulationId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(batchData)
            });
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            console.log('Relationships saved successfully');
            
            // Reset change tracking
            relationshipsToDelete = [];
            originalRelationshipsData = JSON.parse(JSON.stringify(relationshipsData));
            
        } catch (error) {
            console.error('Error saving relationships:', error);
            throw error;
        }
    }

    // Initialize on page load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializePage);
    } else {
        initializePage();
    }

})();

