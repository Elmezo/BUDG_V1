// Policy Impact Edit JavaScript - Implementation for Product and Client sub-tabs
console.log('=== POLICY IMPACT EDIT SCRIPT LOADING ===');
console.log('Script file loaded successfully!');

(function() {
    console.log('=== POLICY IMPACT EDIT SCRIPT LOADED ===');

    function normalizeSegmentId(value) {
        const parsed = parseInt(value, 10);
        return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
    }

    function resolveImpactSegmentId() {
        const explicit = normalizeSegmentId(window.currentImpactSegmentId);
        if (explicit) return explicit;

        const globalSegmentField = window.segmentField;
        if (globalSegmentField && typeof globalSegmentField.getValue === 'function') {
            const fromGlobalField = normalizeSegmentId(globalSegmentField.getValue());
            if (fromGlobalField) return fromGlobalField;
        }

        const segmentContainer = document.getElementById('segmentFieldContainer');
        const segmentFieldInstance = segmentContainer?._segmentFieldInstance;
        if (segmentFieldInstance && typeof segmentFieldInstance.getValue === 'function') {
            const fromContainerField = normalizeSegmentId(segmentFieldInstance.getValue());
            if (fromContainerField) return fromContainerField;
        }

        const domSelectors = ['#policySegment', '[name="segmentId"]', '[name="segment_id"]'];
        for (const selector of domSelectors) {
            const element = document.querySelector(selector);
            const fromDom = normalizeSegmentId(element?.value);
            if (fromDom) return fromDom;
        }

        return null;
    }

    function resolveObjectSegmentId(row) {
        return normalizeSegmentId(
            row?.segmentId ??
            row?.segment_id ??
            row?.Segment_ID ??
            row?.segmentID ??
            row?.SegmentId
        );
    }

    function isAllowedSegmentPair(sourceSegmentId, targetSegmentId) {
        if (!sourceSegmentId || !targetSegmentId) return true;
        const ENTERPRISE_SEGMENT_ID = 1;
        if (sourceSegmentId === ENTERPRISE_SEGMENT_ID || targetSegmentId === ENTERPRISE_SEGMENT_ID) return true;
        return sourceSegmentId === targetSegmentId;
    }

    function withImpactSegment(url) {
        const segmentId = resolveImpactSegmentId();
        if (!segmentId) return url;
        window.currentImpactSegmentId = segmentId;
        const separator = url.includes('?') ? '&' : '?';
        return `${url}${separator}segmentId=${encodeURIComponent(segmentId)}`;
    }

    /** When the segment dropdown is not ready, load segment from the policy record so list APIs always get segmentId. */
    async function ensureImpactSegmentFromPolicy() {
        if (resolveImpactSegmentId()) return;
        const pid = parseInt(currentPolicyId, 10);
        if (!Number.isInteger(pid) || pid <= 0) return;
        const api = window.BUDG_API_SERVICE;
        if (!api || typeof api.getPolicyById !== 'function') return;
        try {
            const resp = await api.getPolicyById(pid);
            const policy = resp?.data ?? resp;
            const sid = normalizeSegmentId(
                policy?.segmentId ?? policy?.segment_id ?? policy?.Segment_ID
            );
            if (sid) window.currentImpactSegmentId = sid;
        } catch (e) {
            console.warn('policy-impact-edit: could not resolve segment from policy', e);
        }
    }

    async function withImpactSegmentAsync(url) {
        if (!resolveImpactSegmentId()) {
            await ensureImpactSegmentFromPolicy();
        }
        let result = withImpactSegment(url);
        const pid = parseInt(currentPolicyId, 10);
        if (Number.isInteger(pid) && pid > 0) {
            const sep = result.includes('?') ? '&' : '?';
            result = `${result}${sep}sourceObjectId=${pid}&sourceObjectType=Policy`;
        }
        return result;
    }
    
    const impactSegmentValidationCache = new Map();

    async function isImpactRelationshipAllowed(targetObjectId, targetObjectType) {
        const targetId = parseInt(targetObjectId, 10);
        if (!Number.isInteger(targetId) || targetId <= 0) {
            return false;
        }
        const sourcePolicyId = parseInt(currentPolicyId, 10);
        if (!Number.isInteger(sourcePolicyId) || sourcePolicyId <= 0) {
            return false;
        }

        const cacheKey = `Policy:${sourcePolicyId}->${targetObjectType}:${targetId}`;
        if (impactSegmentValidationCache.has(cacheKey)) {
            return impactSegmentValidationCache.get(cacheKey);
        }

        try {
            const response = await fetch('/api/segments/validate-relationship', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sourceObjectId: sourcePolicyId,
                    sourceObjectType: 'Policy',
                    targetObjectId: targetId,
                    targetObjectType
                })
            });

            let result = null;
            try { result = await response.json(); } catch (_) { result = null; }
            const isValid = !!(response.ok && result && result.isValid !== false);
            impactSegmentValidationCache.set(cacheKey, isValid);
            return isValid;
        } catch (error) {
            console.warn('Segment relationship validation failed; blocking relationship by fallback:', error);
            impactSegmentValidationCache.set(cacheKey, false);
            return false;
        }
    }

    async function filterRelationshipsForSegment(relationships, targetIdSelector, targetObjectType) {
        const input = Array.isArray(relationships) ? relationships : [];
        const validRelationships = [];
        let skippedCount = 0;
        const sourceSegmentId = resolveImpactSegmentId();

        for (const relationship of input) {
            const targetSegmentId = resolveObjectSegmentId(relationship);
            if (sourceSegmentId && targetSegmentId && !isAllowedSegmentPair(sourceSegmentId, targetSegmentId)) {
                skippedCount++;
                continue;
            }

            const targetRaw = typeof targetIdSelector === 'function'
                ? targetIdSelector(relationship)
                : relationship?.[targetIdSelector];
            const targetId = parseInt(targetRaw, 10);
            if (!Number.isInteger(targetId) || targetId <= 0) {
                continue;
            }
            const isAllowed = await isImpactRelationshipAllowed(targetId, targetObjectType);
            if (isAllowed) {
                validRelationships.push(relationship);
            } else {
                skippedCount++;
            }
        }

        return { validRelationships, skippedCount };
    }
    
    // Global variables
    let currentPolicyId = null;
    let productRelationships = [];
    let clientRelationships = [];
    let processRelationships = [];
    let projectRelationships = [];
    let systemRelationships = [];
    let businessAreaRelationships = [];
    let legalRelationships = [];
    let datasetRelationships = [];
    let attributeRelationships = [];
    let glossaryRelationships = [];
    let originalProductData = [];
    let originalClientData = [];
    let originalProcessData = [];
    let originalProjectData = [];
    let originalSystemData = [];
    let originalBusinessAreaData = [];
    let originalLegalData = [];
    let originalDatasetData = [];
    let originalAttributeData = [];
    let originalGlossaryData = [];
    /** True only after loadImpactData() finished; avoids spurious Impact save when user never opened the tab. */
    let policyImpactDataLoaded = false;
    let productRelationTypes = [];
    let clientRelationTypes = [];
    let processRelationTypes = [];
    let glossaryRelationTypes = [];
    let projectRelationTypes = [];
    let systemRelationTypes = [];
    let businessAreaRelationTypes = [];
    let legalRelationTypes = [];
    let datasetRelationTypes = [];
    let attributeRelationTypes = [];
    let products = [];
    let clients = [];
    let processes = [];
    let projects = [];
    let systems = [];
    let glossaries = [];
    let businessAreas = [];
    let legals = [];
    let datasets = [];
    let attributes = [];

    // Initialize Impact tab edit functionality (await so save can run after data is ready)
    async function initImpactEdit(policyId) {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Policy ID:', policyId);
        currentPolicyId = policyId;
        console.log('Initializing Impact edit for policy:', policyId);
        await loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        policyImpactDataLoaded = false;
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            console.log('Starting to load Impact data...');
            await ensureImpactSegmentFromPolicy();
            
            // Load dropdown data first
            console.log('Loading dropdown data...');
            await Promise.all([
                loadProductRelationTypes(),
                loadClientRelationTypes(),
                loadProcessRelationTypes(),
                loadProjectRelationTypes(),
                loadSystemRelationTypes(),
                loadBusinessAreaRelationTypes(),
                loadLegalRelationTypes(),
                loadDatasetRelationTypes(),
                loadAttributeRelationTypes(),
                loadGlossaryRelationTypes(),
                loadProducts(),
                loadClients(),
                loadProcesses(),
                loadProjects(),
                loadSystems(),
                loadBusinessAreas(),
                loadLegals(),
                loadDatasets(),
                loadAttributes(),
                loadGlossaries()
            ]);
            console.log('Dropdown data loaded successfully');

            // Load relationship data
            console.log('Loading relationship data...');
            await Promise.all([
                loadProductRelationships(),
                loadClientRelationships(),
                loadProcessRelationships(),
                loadProjectRelationships(),
                loadSystemRelationships(),
                loadBusinessAreaRelationships(),
                loadLegalRelationships(),
                loadDatasetRelationships(),
                loadAttributeRelationships(),
                loadGlossaryRelationships()
            ]);
            console.log('Relationship data loaded successfully');

            // Setup sub-tabs
            setupImpactSubTabs();
            policyImpactDataLoaded = true;
            console.log('=== LOADING IMPACT DATA END ===');

        } catch (error) {
            console.error('=== IMPACT DATA LOADING ERROR ===');
            console.error('Error loading Impact data:', error);
            console.error('Error details:', error.message);
            policyImpactDataLoaded = false;
            console.log('=== IMPACT DATA LOADING ERROR END ===');
        }
    }

    // Setup Impact sub-tabs
    function setupImpactSubTabs() {
        console.log('Setting up Impact sub-tabs...');
        
        const subTabs = document.querySelectorAll('#impactTab .sub-tab');
        console.log('Found Impact sub-tabs:', subTabs.length);
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                console.log('Impact sub-tab clicked:', this.textContent.trim());
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                console.log('Switching to Impact sub-tab:', subTabName);
                
                // Hide all sub-tab contents
                const subTabContents = document.querySelectorAll('#impactTab .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductContent');
                    console.log('Found product content:', !!targetSubTab);
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientContent');
                    console.log('Found client content:', !!targetSubTab);
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessContent');
                    console.log('Found process content:', !!targetSubTab);
                } else if (subTabName === 'project') {
                    targetSubTab = document.getElementById('impactProjectContent');
                    console.log('Found project content:', !!targetSubTab);
                } else if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemContent');
                    console.log('Found system content:', !!targetSubTab);
                } else if (subTabName === 'businessarea') {
                    targetSubTab = document.getElementById('impactBusinessAreaContent');
                    console.log('Found business area content:', !!targetSubTab);
                } else if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalContent');
                    console.log('Found legal content:', !!targetSubTab);
                } else if (subTabName === 'dataset') {
                    targetSubTab = document.getElementById('impactDatasetContent');
                    console.log('Found dataset content:', !!targetSubTab);
                } else if (subTabName === 'attribute') {
                    targetSubTab = document.getElementById('impactAttributeContent');
                    console.log('Found attribute content:', !!targetSubTab);
                } else if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryContent');
                    console.log('Found glossary content:', !!targetSubTab);
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                    console.log('Switched to Impact sub-tab:', subTabName);
                } else {
                    console.error('Impact sub-tab content not found for:', subTabName);
                }
            });
        });
    }

    // Load product relationships
    async function loadProductRelationships() {
        try {
            console.log('Loading product relationships for policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/products`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Product relationships API response:', data);

            productRelationships = Array.isArray(data) ? data : [];
            originalProductData = JSON.parse(JSON.stringify(productRelationships));

            renderProductTable();

        } catch (error) {
            console.error('Error loading product relationships:', error);
            productRelationships = [];
            originalProductData = [];
        }
    }

    // Load client relationships
    async function loadClientRelationships() {
        try {
            console.log('Loading client relationships for policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/clients`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Client relationships API response:', data);

            clientRelationships = Array.isArray(data) ? data : [];
            originalClientData = JSON.parse(JSON.stringify(clientRelationships));

            renderClientTable();

        } catch (error) {
            console.error('Error loading client relationships:', error);
            clientRelationships = [];
            originalClientData = [];
        }
    }

    // Load product relation types
    async function loadProductRelationTypes() {
        try {
            const response = await fetch('/api/policy-impact/product-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            productRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded product relation types:', productRelationTypes.length);

        } catch (error) {
            console.error('Error loading product relation types:', error);
            productRelationTypes = [];
        }
    }

    // Load client relation types
    async function loadClientRelationTypes() {
        try {
            const response = await fetch('/api/policy-impact/client-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            clientRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded client relation types:', clientRelationTypes.length);

        } catch (error) {
            console.error('Error loading client relation types:', error);
            clientRelationTypes = [];
        }
    }

    // Load products
    async function loadProducts() {
        try {
            const response = await fetch(await withImpactSegmentAsync('/api/product/list'), {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            products = Array.isArray(data) ? data : [];
            products = (await filterRelationshipsForSegment(products, (p) => p?.id ?? p?.ID, 'Product')).validRelationships;
            console.log('Loaded products:', products.length);

        } catch (error) {
            console.error('Error loading products:', error);
            products = [];
        }
    }

    // Load clients
    async function loadClients() {
        try {
            console.log('=== LOADING CLIENTS START ===');
            console.log('Loading clients from /api/client/...');
            const response = await fetch(await withImpactSegmentAsync('/api/client/'), {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            console.log('Client response status:', response.status);
            console.log('Client response ok:', response.ok);

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Raw client data received:', data);
            
            // Handle nested data structure
            if (data && data.success && Array.isArray(data.data)) {
                clients = data.data;
                console.log('Loaded clients from nested data:', clients.length);
            } else if (Array.isArray(data)) {
                clients = data;
                console.log('Loaded clients from direct array:', clients.length);
            } else {
                clients = [];
                console.log('No valid client data found');
            }
            clients = (await filterRelationshipsForSegment(clients, (c) => c?.id ?? c?.ID, 'Client')).validRelationships;
            
            console.log('Final clients array length:', clients.length);
            
            if (clients.length > 0) {
                console.log('First client sample:', clients[0]);
                console.log('Client field names:', Object.keys(clients[0]));
                console.log('Client id:', clients[0].id);
                console.log('Client primary_name:', clients[0].primary_name);
            } else {
                console.log('No clients found in response');
            }
            console.log('=== LOADING CLIENTS END ===');

        } catch (error) {
            console.error('=== CLIENT LOADING ERROR ===');
            console.error('Error loading clients:', error);
            console.error('Error details:', error.message);
            clients = [];
            console.log('=== CLIENT LOADING ERROR END ===');
        }
    }

    // Load process relation types
    async function loadProcessRelationTypes() {
        try {
            console.log('Loading process relation types...');
            const response = await fetch('/api/policy-impact/process-relation-types');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            processRelationTypes = data.data || [];
            console.log('Loaded process relation types:', processRelationTypes.length);
        } catch (error) {
            console.error('Error loading process relation types:', error);
            processRelationTypes = [];
        }
    }

    // Load project relation types
    async function loadProjectRelationTypes() {
        try {
            console.log('Loading project relation types...');
            const response = await fetch('/api/policy-impact/project-relation-types');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            projectRelationTypes = data.data || [];
            console.log('Loaded project relation types:', projectRelationTypes.length);
        } catch (error) {
            console.error('Error loading project relation types:', error);
            projectRelationTypes = [];
        }
    }

    // Load processes
    async function loadProcesses() {
        try {
            console.log('=== LOADING PROCESSES START ===');
            console.log('Loading processes from /api/process/...');
            
            const response = await fetch(await withImpactSegmentAsync('/api/process/'));
            console.log('Process response status:', response.status);
            console.log('Process response ok:', response.ok);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const rawData = await response.json();
            console.log('Raw process data received:', rawData);
            
            if (rawData.success && rawData.data) {
                processes = rawData.data;
                console.log('Loaded processes from nested data:', processes.length);
            } else if (Array.isArray(rawData)) {
                processes = rawData;
                console.log('Loaded processes from direct array:', processes.length);
            } else {
                console.log('No valid process data found');
                processes = [];
            }
            processes = (await filterRelationshipsForSegment(processes, (p) => p?.id ?? p?.ID, 'Process')).validRelationships;
            
            console.log('Final processes array length:', processes.length);
            
            if (processes.length > 0) {
                console.log('First process sample:', processes[0]);
                console.log('Process field names:', Object.keys(processes[0]));
            } else {
                console.log('No processes found in response');
            }
            console.log('=== LOADING PROCESSES END ===');

        } catch (error) {
            console.error('=== PROCESS LOADING ERROR ===');
            console.error('Error loading processes:', error);
            console.error('Error details:', error.message);
            processes = [];
            console.log('=== PROCESS LOADING ERROR END ===');
        }
    }

    // Load projects
    async function loadProjects() {
        try {
            console.log('=== LOADING PROJECTS START ===');
            console.log('Loading projects from /api/project/...');
            
            const response = await fetch(await withImpactSegmentAsync('/api/project/'));
            console.log('Project response status:', response.status);
            console.log('Project response ok:', response.ok);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const rawData = await response.json();
            console.log('Raw project data received:', rawData);
            
            if (rawData.success && rawData.data) {
                projects = rawData.data;
                console.log('Loaded projects from nested data:', projects.length);
            } else if (Array.isArray(rawData)) {
                projects = rawData;
                console.log('Loaded projects from direct array:', projects.length);
            } else {
                console.log('No valid project data found');
                projects = [];
            }
            projects = (await filterRelationshipsForSegment(projects, (p) => p?.id ?? p?.ID, 'Project')).validRelationships;
            
            console.log('Final projects array length:', projects.length);
            
            if (projects.length > 0) {
                console.log('First project sample:', projects[0]);
                console.log('Project field names:', Object.keys(projects[0]));
            } else {
                console.log('No projects found in response');
            }
            console.log('=== LOADING PROJECTS END ===');

        } catch (error) {
            console.error('=== PROJECT LOADING ERROR ===');
            console.error('Error loading projects:', error);
            console.error('Error details:', error.message);
            projects = [];
            console.log('=== PROJECT LOADING ERROR END ===');
        }
    }

    // Load process relationships
    async function loadProcessRelationships() {
        try {
            console.log('Loading process relationships for policy:', currentPolicyId);
            const response = await fetch(`/api/policy-impact/${currentPolicyId}/processes`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('Process relationships API response:', data);
            processRelationships = Array.isArray(data) ? data : [];
            renderProcessTable();
        } catch (error) {
            console.error('Error loading process relationships:', error);
            processRelationships = [];
            renderProcessTable();
        }
    }

    // Load project relationships
    async function loadProjectRelationships() {
        try {
            console.log('Loading project relationships for policy:', currentPolicyId);
            const response = await fetch(`/api/policy-impact/${currentPolicyId}/projects`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('Project relationships API response:', data);
            projectRelationships = Array.isArray(data) ? data : [];
            renderProjectTable();
        } catch (error) {
            console.error('Error loading project relationships:', error);
            projectRelationships = [];
            renderProjectTable();
        }
    }

    // Load system relation types
    async function loadSystemRelationTypes() {
        try {
            const response = await fetch('/api/policy-impact/system-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            systemRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded system relation types:', systemRelationTypes.length);
        } catch (error) {
            console.error('Error loading system relation types:', error);
            systemRelationTypes = [];
        }
    }

    // Load business area relation types
    async function loadBusinessAreaRelationTypes() {
        try {
            const response = await fetch('/api/policy-impact/businessarea-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            businessAreaRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded business area relation types:', businessAreaRelationTypes.length);
        } catch (error) {
            console.error('Error loading business area relation types:', error);
            businessAreaRelationTypes = [];
        }
    }

    // Load legal relation types
    async function loadLegalRelationTypes() {
        try {
            const response = await fetch('/api/policy-impact/legal-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            legalRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded legal relation types:', legalRelationTypes.length);
        } catch (error) {
            console.error('Error loading legal relation types:', error);
            legalRelationTypes = [];
        }
    }

    // Load systems
    async function loadSystems() {
        try {
            const response = await fetch(await withImpactSegmentAsync('/api/system/list'), {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            systems = Array.isArray(data) ? data : [];
            systems = (await filterRelationshipsForSegment(systems, (s) => s?.id ?? s?.ID, 'System')).validRelationships;
            console.log('Loaded systems:', systems.length);
        } catch (error) {
            console.error('Error loading systems:', error);
            systems = [];
        }
    }

    // Load business areas
    async function loadBusinessAreas() {
        try {
            const response = await fetch(await withImpactSegmentAsync('/api/business-areas/'), {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            businessAreas = data.success && data.data ? data.data : [];
            businessAreas = (await filterRelationshipsForSegment(businessAreas, (b) => b?.id ?? b?.ID ?? b?.BusinessArea_ID, 'BusinessArea')).validRelationships;
            console.log('Loaded business areas:', businessAreas.length);
        } catch (error) {
            console.error('Error loading business areas:', error);
            businessAreas = [];
        }
    }

    // Load legals
    async function loadLegals() {
        try {
            const response = await fetch(await withImpactSegmentAsync('/api/LegalEntity/hierarchy'), {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            legals = Array.isArray(data) ? data : [];
            legals = (await filterRelationshipsForSegment(legals, (l) => l?.id ?? l?.ID ?? l?.Legal_ID, 'LegalEntity')).validRelationships;
            console.log('Loaded legals:', legals.length);
        } catch (error) {
            console.error('Error loading legals:', error);
            legals = [];
        }
    }

    // Load system relationships
    async function loadSystemRelationships() {
        try {
            console.log('Loading system relationships for policy:', currentPolicyId);
            const response = await fetch(`/api/policy-impact/${currentPolicyId}/systems`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('System relationships API response:', data);
            systemRelationships = Array.isArray(data) ? data : [];
            renderSystemTable();
        } catch (error) {
            console.error('Error loading system relationships:', error);
            systemRelationships = [];
            renderSystemTable();
        }
    }

    // Load business area relationships
    async function loadBusinessAreaRelationships() {
        try {
            console.log('Loading business area relationships for policy:', currentPolicyId);
            const response = await fetch(`/api/policy-impact/${currentPolicyId}/businessareas`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('Business area relationships API response:', data);
            businessAreaRelationships = Array.isArray(data) ? data : [];
            renderBusinessAreaTable();
        } catch (error) {
            console.error('Error loading business area relationships:', error);
            businessAreaRelationships = [];
            renderBusinessAreaTable();
        }
    }

    // Load legal relationships
    async function loadLegalRelationships() {
        try {
            console.log('Loading legal relationships for policy:', currentPolicyId);
            const response = await fetch(`/api/policy-impact/${currentPolicyId}/legals`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('Legal relationships API response:', data);
            legalRelationships = Array.isArray(data) ? data : [];
            renderLegalTable();
        } catch (error) {
            console.error('Error loading legal relationships:', error);
            legalRelationships = [];
            renderLegalTable();
        }
    }

    // Load dataset relation types
    async function loadDatasetRelationTypes() {
        try {
            const response = await fetch('/api/policy-impact/dataset-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            datasetRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded dataset relation types:', datasetRelationTypes.length);
        } catch (error) {
            console.error('Error loading dataset relation types:', error);
            datasetRelationTypes = [];
        }
    }

    // Load attribute relation types
    async function loadAttributeRelationTypes() {
        try {
            const response = await fetch('/api/policy-impact/attribute-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            attributeRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded attribute relation types:', attributeRelationTypes.length);
        } catch (error) {
            console.error('Error loading attribute relation types:', error);
            attributeRelationTypes = [];
        }
    }

    // Load datasets
    async function loadDatasets() {
        try {
            console.log('=== LOADING DATASETS START ===');
            const response = await fetch('/api/policy-impact/datasets-list', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            datasets = Array.isArray(data) ? data : [];
            console.log('Loaded datasets:', datasets.length);
            console.log('=== LOADING DATASETS END ===');
        } catch (error) {
            console.error('Error loading datasets:', error);
            datasets = [];
        }
    }

    // Load attributes
    async function loadAttributes() {
        try {
            console.log('=== LOADING ATTRIBUTES START ===');
            const response = await fetch('/api/policy-impact/attributes-list', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            attributes = Array.isArray(data) ? data : [];
            console.log('Loaded attributes:', attributes.length);
            console.log('=== LOADING ATTRIBUTES END ===');
        } catch (error) {
            console.error('Error loading attributes:', error);
            attributes = [];
        }
    }

    // Load dataset relationships
    async function loadDatasetRelationships() {
        try {
            console.log('Loading dataset relationships for policy:', currentPolicyId);
            const response = await fetch(`/api/policy-impact/${currentPolicyId}/datasets`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('Dataset relationships API response:', data);
            datasetRelationships = Array.isArray(data) ? data : [];
            console.log('Loaded dataset relationships:', datasetRelationships.length);
            if (datasetRelationships.length > 0) {
                console.log('First dataset relationship:', datasetRelationships[0]);
                console.log('Dataset owner name:', datasetRelationships[0].datasetOwnerName);
                console.log('Dataset owner email:', datasetRelationships[0].datasetOwnerEmail);
            }
            renderDatasetTable();
        } catch (error) {
            console.error('Error loading dataset relationships:', error);
            datasetRelationships = [];
            renderDatasetTable();
        }
    }

    // Load attribute relationships
    async function loadAttributeRelationships() {
        try {
            console.log('Loading attribute relationships for policy:', currentPolicyId);
            const response = await fetch(`/api/policy-impact/${currentPolicyId}/attributes`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('Attribute relationships API response:', data);
            attributeRelationships = Array.isArray(data) ? data : [];
            console.log('Loaded attribute relationships:', attributeRelationships.length);
            if (attributeRelationships.length > 0) {
                console.log('First attribute relationship:', attributeRelationships[0]);
                console.log('Attribute owner name:', attributeRelationships[0].attributeOwnerName);
                console.log('Attribute owner email:', attributeRelationships[0].attributeOwnerEmail);
            }
            renderAttributeTable();
        } catch (error) {
            console.error('Error loading attribute relationships:', error);
            attributeRelationships = [];
            renderAttributeTable();
        }
    }

    // Render product table
    function renderProductTable() {
        const tbody = document.getElementById('productImpactTableBody');
        const footer = document.getElementById('productImpactFooter');
        
        if (!tbody) return;

        // Always ensure at least one empty row exists for adding new relationships
        if (productRelationships.length === 0) {
            productRelationships.push({
                id: 'new-empty',
                productId: null,
                relationType: null,
                productName: '',
                productOwnerName: ''
            });
        }

        let html = '';
        productRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${productRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="productId" onchange="updateProductOwner(this)">
                            <option value="">Select product</option>
                            ${products.map(p => 
                                `<option value="${p.id}" ${relationship.productId == p.id ? 'selected' : ''}>${p.primaryname || p.name}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.productOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addProductRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteProductRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${productRelationships.length} record${productRelationships.length !== 1 ? 's' : ''}`;
    }

    // Render client table
    function renderClientTable() {
        const tbody = document.getElementById('clientImpactTableBody');
        const footer = document.getElementById('clientImpactFooter');
        
        if (!tbody) return;

        // Always ensure at least one empty row exists for adding new relationships
        if (clientRelationships.length === 0) {
            clientRelationships.push({
                id: 'new-empty',
                clientId: null,
                relationType: null,
                clientName: '',
                clientOwnerName: ''
            });
        }

        let html = '';
        clientRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${clientRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="clientId" onchange="updateClientOwner(this)">
                            <option value="">Select client</option>
                            ${clients.map(c => 
                                `<option value="${c.id}" ${relationship.clientId == c.id ? 'selected' : ''}>${c.primary_name || 'Unnamed Client'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.clientOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addClientRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteClientRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${clientRelationships.length} record${clientRelationships.length !== 1 ? 's' : ''}`;
    }

    // Render process table
    function renderProcessTable() {
        const tbody = document.getElementById('processImpactTableBody');
        const footer = document.getElementById('processImpactFooter');
        
        if (!tbody) return;

        // Always ensure at least one empty row exists for adding new relationships
        if (processRelationships.length === 0) {
            processRelationships.push({
                id: 'new-empty',
                processId: null,
                relationType: null,
                processName: '',
                processRefNumber: '',
                processOwnerName: ''
            });
        }

        let html = '';
        processRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${processRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="processId" onchange="updateProcessOwner(this)">
                            <option value="">Select process</option>
                            ${processes.map(p => 
                                `<option value="${p.id}" ${relationship.processId == p.id ? 'selected' : ''}>${p.primaryname || 'Unnamed Process'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.processRefNumber || ''}</span>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.processOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addProcessRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteProcessRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${processRelationships.length} record${processRelationships.length !== 1 ? 's' : ''}`;
    }

    // Render project table
    function renderProjectTable() {
        const tbody = document.getElementById('projectImpactTableBody');
        const footer = document.getElementById('projectImpactFooter');
        
        if (!tbody) return;

        // Always ensure at least one empty row exists for adding new relationships
        if (projectRelationships.length === 0) {
            projectRelationships.push({
                id: 'new-empty',
                projectId: null,
                relationType: null,
                projectName: '',
                projectRefNumber: '',
                projectOwnerName: '',
                description: ''
            });
        }

        let html = '';
        projectRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${projectRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="projectId" onchange="updateProjectOwner(this)">
                            <option value="">Select project</option>
                            ${projects.map(p => 
                                `<option value="${p.id}" ${relationship.projectId == p.id ? 'selected' : ''}>${p.primaryname || 'Unnamed Project'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.projectRefNumber || ''}</span>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.projectOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <input type="text" class="form-control" data-field="description" value="${relationship.description || ''}" placeholder="Enter description">
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addProjectRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteProjectRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${projectRelationships.length} record${projectRelationships.length !== 1 ? 's' : ''}`;
    }

    // Render system table
    function renderSystemTable() {
        const tbody = document.getElementById('systemImpactTableBody');
        const footer = document.getElementById('systemImpactFooter');
        
        if (!tbody) return;

        // Always ensure at least one empty row exists for adding new relationships
        if (systemRelationships.length === 0) {
            systemRelationships.push({
                id: 'new-empty',
                systemId: null,
                relationType: null,
                systemName: '',
                systemOwnerName: ''
            });
        }

        let html = '';
        systemRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${systemRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="systemId" onchange="updateSystemOwner(this)">
                            <option value="">Select system</option>
                            ${systems.map(s => 
                                `<option value="${s.id}" ${relationship.systemId == s.id ? 'selected' : ''}>${s.name || 'Unnamed System'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.systemOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addSystemRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteSystemRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${systemRelationships.length} record${systemRelationships.length !== 1 ? 's' : ''}`;
    }

    // Render business area table
    function renderBusinessAreaTable() {
        const tbody = document.getElementById('businessAreaImpactTableBody');
        const footer = document.getElementById('businessAreaImpactFooter');
        
        if (!tbody) return;

        // Always ensure at least one empty row exists for adding new relationships
        if (businessAreaRelationships.length === 0) {
            businessAreaRelationships.push({
                id: 'new-empty',
                businessAreaId: null,
                relationType: null,
                businessAreaName: ''
            });
        }

        let html = '';
        businessAreaRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${businessAreaRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="businessAreaId">
                            <option value="">Select business area</option>
                            ${businessAreas.map(ba => 
                                `<option value="${ba.id}" ${relationship.businessAreaId == ba.id ? 'selected' : ''}>${ba.primaryName || 'Unnamed Business Area'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addBusinessAreaRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteBusinessAreaRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${businessAreaRelationships.length} record${businessAreaRelationships.length !== 1 ? 's' : ''}`;
    }

    // Render legal table
    function renderLegalTable() {
        const tbody = document.getElementById('legalImpactTableBody');
        const footer = document.getElementById('legalImpactFooter');
        
        if (!tbody) return;

        // Always ensure at least one empty row exists for adding new relationships
        if (legalRelationships.length === 0) {
            legalRelationships.push({
                id: 'new-empty',
                legalId: null,
                relationType: null,
                legalShortName: '',
                legalOwnerName: ''
            });
        }

        let html = '';
        legalRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${legalRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="legalId" onchange="updateLegalOwner(this)">
                            <option value="">Select legal entity</option>
                            ${legals.map(l => 
                                `<option value="${l.id}" ${relationship.legalId == l.id ? 'selected' : ''}>${l.shortName || l.longName || 'Unnamed Legal Entity'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.legalOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addLegalRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteLegalRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${legalRelationships.length} record${legalRelationships.length !== 1 ? 's' : ''}`;
    }

    // Render dataset table
    function renderDatasetTable() {
        const tbody = document.getElementById('datasetImpactTableBody');
        const footer = document.getElementById('datasetImpactFooter');
        
        if (!tbody) return;

        // Only add empty row if there are no existing relationships
        if (datasetRelationships.length === 0) {
            datasetRelationships.push({
                id: 'new-empty',
                systemId: null,
                datasetId: null,
                relationType: null,
                datasetName: '',
                datasetOwnerName: '',
                systemName: ''
            });
        }

        let html = '';
        datasetRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            console.log('Rendering dataset relationship ' + index + ':', relationship);
            console.log('Dataset owner name:', relationship.datasetOwnerName);
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${datasetRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="systemId" onchange="filterDatasetsBySystem(this);">
                            <option value="">Select system</option>
                            ${systems.map(s => 
                                `<option value="${s.id}" data-system-name="${s.name}" ${relationship.systemId == s.id ? 'selected' : ''}>${s.name || 'Unnamed System'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="datasetId" data-system-id="${relationship.systemId || ''}" onchange="updateDatasetOwner(this);">
                            <option value="">Select dataset</option>
                            ${datasets.map(d => {
                                const isSelected = relationship.datasetId == d.ID;
                                const showOption = !relationship.systemId || d.MasterSource == relationship.systemId;
                                return `<option value="${d.ID}" ${isSelected ? 'selected' : ''} ${showOption ? 'style="display:block;"' : 'style="display:none;"'}>${d.PrimaryName || 'Unnamed Dataset'}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td title="${relationship.datasetOwnerName || 'No owner'}">
                        <span class="text-muted">${relationship.datasetOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addDatasetRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteDatasetRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${datasetRelationships.length} record${datasetRelationships.length !== 1 ? 's' : ''}`;
    }

    // Render attribute table
    function renderAttributeTable() {
        const tbody = document.getElementById('attributeImpactTableBody');
        const footer = document.getElementById('attributeImpactFooter');
        
        if (!tbody) return;

        // Always ensure at least one empty row exists for adding new relationships
        if (attributeRelationships.length === 0) {
            attributeRelationships.push({
                id: 'new-empty',
                attributeId: null,
                relationType: null,
                attributeName: '',
                attributeOwnerName: '',
                datasetName: '',
                systemName: ''
            });
        }

        let html = '';
        attributeRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            console.log('Rendering attribute relationship ' + index + ':', relationship);
            console.log('Attribute owner name:', relationship.attributeOwnerName);
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${attributeRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="systemId" onchange="filterDatasetsBySystemForAttribute(this);">
                            <option value="">Select system</option>
                            ${systems.map(s => 
                                `<option value="${s.id}" data-system-name="${s.name}" ${relationship.systemId == s.id ? 'selected' : ''}>${s.name || 'Unnamed System'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="datasetId" data-system-id="${relationship.systemId || ''}" onchange="filterAttributesByDataset(this);">
                            <option value="">Select dataset</option>
                            ${datasets.map(d => {
                                const isSelected = relationship.datasetId == d.ID;
                                const showOption = !relationship.systemId || d.MasterSource == relationship.systemId;
                                return `<option value="${d.ID}" ${isSelected ? 'selected' : ''} ${showOption ? 'style="display:block;"' : 'style="display:none;"'}>${d.PrimaryName || 'Unnamed Dataset'}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="attributeId" data-dataset-id="${relationship.datasetId || ''}" onchange="updateAttributeOwner(this);">
                            <option value="">Select attribute</option>
                            ${attributes.map(a => {
                                const isSelected = relationship.attributeId == a.ID;
                                const showOption = !relationship.datasetId || a.Dataset_ID == relationship.datasetId;
                                return `<option value="${a.ID}" ${isSelected ? 'selected' : ''} ${showOption ? 'style="display:block;"' : 'style="display:none;"'}>${a.PrimaryName || 'Unnamed Attribute'}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td title="${relationship.attributeOwnerName || 'No owner'}">
                        <span class="text-muted">${relationship.attributeOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addAttributeRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteAttributeRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${attributeRelationships.length} record${attributeRelationships.length !== 1 ? 's' : ''}`;
    }

    // Save current form data before re-rendering
    function saveCurrentProductFormData() {
        const rows = document.querySelectorAll('#productImpactTableBody tr');
        rows.forEach((row, index) => {
            if (productRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const productSelect = row.querySelector('select[data-field="productId"]');
                const ownerCell = row.querySelector('td:nth-child(3)'); // Product Owner column
                
                if (relationTypeSelect) {
                    productRelationships[index].relationType = relationTypeSelect.value;
                }
                if (productSelect) {
                    productRelationships[index].productId = productSelect.value;
                    // Update product name based on selected product
                    const selectedOption = productSelect.options[productSelect.selectedIndex];
                    if (selectedOption) {
                        productRelationships[index].productName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        productRelationships[index].productOwnerName = ownerSpan.textContent.trim();
                    }
                }
            }
        });
    }

    function saveCurrentClientFormData() {
        const rows = document.querySelectorAll('#clientImpactTableBody tr');
        rows.forEach((row, index) => {
            if (clientRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const clientSelect = row.querySelector('select[data-field="clientId"]');
                const ownerCell = row.querySelector('td:nth-child(3)'); // Client Owner column
                
                if (relationTypeSelect) {
                    clientRelationships[index].relationType = relationTypeSelect.value;
                }
                if (clientSelect) {
                    clientRelationships[index].clientId = clientSelect.value;
                    // Update client name based on selected client
                    const selectedOption = clientSelect.options[clientSelect.selectedIndex];
                    if (selectedOption) {
                        clientRelationships[index].clientName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        clientRelationships[index].clientOwnerName = ownerSpan.textContent.trim();
                    }
                }
            }
        });
    }

    function saveCurrentProcessFormData() {
        const rows = document.querySelectorAll('#processImpactTableBody tr');
        rows.forEach((row, index) => {
            if (processRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const processSelect = row.querySelector('select[data-field="processId"]');
                const ownerCell = row.querySelector('td:nth-child(4)'); // Process Owner column
                const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
                
                if (relationTypeSelect) {
                    processRelationships[index].relationType = relationTypeSelect.value;
                }
                if (processSelect) {
                    processRelationships[index].processId = processSelect.value;
                    // Update process name based on selected process
                    const selectedOption = processSelect.options[processSelect.selectedIndex];
                    if (selectedOption) {
                        processRelationships[index].processName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        processRelationships[index].processOwnerName = ownerSpan.textContent.trim();
                    }
                }
                // Preserve the current reference number from the DOM
                if (refCell) {
                    const refSpan = refCell.querySelector('span');
                    if (refSpan) {
                        processRelationships[index].processRefNumber = refSpan.textContent.trim();
                    }
                }
            }
        });
    }

    function saveCurrentProjectFormData() {
        const rows = document.querySelectorAll('#projectImpactTableBody tr');
        rows.forEach((row, index) => {
            if (projectRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const projectSelect = row.querySelector('select[data-field="projectId"]');
                const descriptionInput = row.querySelector('input[data-field="description"]');
                const ownerCell = row.querySelector('td:nth-child(4)'); // Project Owner column
                const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
                
                if (relationTypeSelect) {
                    projectRelationships[index].relationType = relationTypeSelect.value;
                }
                if (projectSelect) {
                    projectRelationships[index].projectId = projectSelect.value;
                    // Update project name based on selected project
                    const selectedOption = projectSelect.options[projectSelect.selectedIndex];
                    if (selectedOption) {
                        projectRelationships[index].projectName = selectedOption.text;
                    }
                }
                if (descriptionInput) {
                    projectRelationships[index].description = descriptionInput.value;
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        projectRelationships[index].projectOwnerName = ownerSpan.textContent.trim();
                    }
                }
                // Preserve the current reference number from the DOM
                if (refCell) {
                    const refSpan = refCell.querySelector('span');
                    if (refSpan) {
                        projectRelationships[index].projectRefNumber = refSpan.textContent.trim();
                    }
                }
            }
        });
    }

    // Add product row
    function addProductRow() {
        // Save current form data before re-rendering
        saveCurrentProductFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            productId: null,
            relationType: null,
            productName: '',
            productOwnerName: ''
        };
        
        productRelationships.push(newRelationship);
        renderProductTable();
    }

    // Add client row
    function addClientRow() {
        // Save current form data before re-rendering
        saveCurrentClientFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            clientId: null,
            relationType: null,
            clientName: '',
            clientOwnerName: ''
        };
        
        clientRelationships.push(newRelationship);
        renderClientTable();
    }

    // Delete product row
    function deleteProductRow(id) {
        // Save current form data before re-rendering
        saveCurrentProductFormData();
        
        if (productRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            productRelationships[0] = {
                id: 'new-' + Date.now(),
                productId: null,
                relationType: null,
                productName: '',
                productOwnerName: ''
            };
        } else {
            productRelationships = productRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProductTable();
    }

    // Delete client row
    function deleteClientRow(id) {
        // Save current form data before re-rendering
        saveCurrentClientFormData();
        
        if (clientRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            clientRelationships[0] = {
                id: 'new-' + Date.now(),
                clientId: null,
                relationType: null,
                clientName: '',
                clientOwnerName: ''
            };
        } else {
            clientRelationships = clientRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderClientTable();
    }

    // Add process row
    function addProcessRow() {
        // Save current form data before re-rendering
        saveCurrentProcessFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            processId: null,
            relationType: null,
            processName: '',
            processRefNumber: '',
            processOwnerName: ''
        };
        
        processRelationships.push(newRelationship);
        renderProcessTable();
    }

    // Add project row
    function addProjectRow() {
        // Save current form data before re-rendering
        saveCurrentProjectFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            projectId: null,
            relationType: null,
            projectName: '',
            projectRefNumber: '',
            projectOwnerName: '',
            description: ''
        };
        
        projectRelationships.push(newRelationship);
        renderProjectTable();
    }

    // Delete process row
    function deleteProcessRow(id) {
        // Save current form data before re-rendering
        saveCurrentProcessFormData();
        
        if (processRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            processRelationships[0] = {
                id: 'new-' + Date.now(),
                processId: null,
                relationType: null,
                processName: '',
                processRefNumber: '',
                processOwnerName: ''
            };
        } else {
            processRelationships = processRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProcessTable();
    }

    // Delete project row
    function deleteProjectRow(id) {
        // Save current form data before re-rendering
        saveCurrentProjectFormData();
        
        if (projectRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            projectRelationships[0] = {
                id: 'new-' + Date.now(),
                projectId: null,
                relationType: null,
                projectName: '',
                projectRefNumber: '',
                projectOwnerName: '',
                description: ''
            };
        } else {
            projectRelationships = projectRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProjectTable();
    }

    // Add system row
    function addSystemRow() {
        // Save current form data before re-rendering
        saveCurrentSystemFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            systemId: null,
            relationType: null,
            systemName: '',
            systemOwnerName: ''
        };
        
        systemRelationships.push(newRelationship);
        renderSystemTable();
    }

    // Delete system row
    function deleteSystemRow(id) {
        // Save current form data before re-rendering
        saveCurrentSystemFormData();
        
        if (systemRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            systemRelationships[0] = {
                id: 'new-' + Date.now(),
                systemId: null,
                relationType: null,
                systemName: '',
                systemOwnerName: ''
            };
        } else {
            systemRelationships = systemRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderSystemTable();
    }

    // Add business area row
    function addBusinessAreaRow() {
        // Save current form data before re-rendering
        saveCurrentBusinessAreaFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            businessAreaId: null,
            relationType: null,
            businessAreaName: ''
        };
        
        businessAreaRelationships.push(newRelationship);
        renderBusinessAreaTable();
    }

    // Delete business area row
    function deleteBusinessAreaRow(id) {
        // Save current form data before re-rendering
        saveCurrentBusinessAreaFormData();
        
        if (businessAreaRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            businessAreaRelationships[0] = {
                id: 'new-' + Date.now(),
                businessAreaId: null,
                relationType: null,
                businessAreaName: ''
            };
        } else {
            businessAreaRelationships = businessAreaRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderBusinessAreaTable();
    }

    // Add legal row
    function addLegalRow() {
        // Save current form data before re-rendering
        saveCurrentLegalFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            legalId: null,
            relationType: null,
            legalShortName: '',
            legalOwnerName: ''
        };
        
        legalRelationships.push(newRelationship);
        renderLegalTable();
    }

    // Delete legal row
    function deleteLegalRow(id) {
        // Save current form data before re-rendering
        saveCurrentLegalFormData();
        
        if (legalRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            legalRelationships[0] = {
                id: 'new-' + Date.now(),
                legalId: null,
                relationType: null,
                legalShortName: '',
                legalOwnerName: ''
            };
        } else {
            legalRelationships = legalRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderLegalTable();
    }

    // Add dataset row
    function addDatasetRow() {
        // Save current form data before re-rendering
        saveCurrentDatasetFormData();
        
        datasetRelationships.push({
            id: 'new-' + Date.now(),
            systemId: null,
            datasetId: null,
            relationType: null,
            datasetName: '',
            datasetOwnerName: '',
            systemName: ''
        });
        renderDatasetTable();
    }

    // Delete dataset row
    function deleteDatasetRow(id) {
        // Save current form data before re-rendering
        saveCurrentDatasetFormData();
        
        if (datasetRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            datasetRelationships[0] = {
                id: 'new-' + Date.now(),
                systemId: null,
                datasetId: null,
                relationType: null,
                datasetName: '',
                datasetOwnerName: '',
                systemName: ''
            };
        } else {
            datasetRelationships = datasetRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderDatasetTable();
    }

    // Add attribute row
    function addAttributeRow() {
        // Save current form data before re-rendering
        saveCurrentAttributeFormData();
        
        attributeRelationships.push({
            id: 'new-' + Date.now(),
            attributeId: null,
            relationType: null,
            attributeName: '',
            attributeOwnerName: '',
            datasetName: '',
            systemName: ''
        });
        renderAttributeTable();
    }

    // Delete attribute row
    function deleteAttributeRow(id) {
        // Save current form data before re-rendering
        saveCurrentAttributeFormData();
        
        if (attributeRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            attributeRelationships[0] = {
                id: 'new-' + Date.now(),
                attributeId: null,
                relationType: null,
                attributeName: '',
                attributeOwnerName: '',
                datasetName: '',
                systemName: ''
            };
        } else {
            attributeRelationships = attributeRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderAttributeTable();
    }

    // Save current dataset form data before re-rendering
    function saveCurrentDatasetFormData() {
        const rows = document.querySelectorAll('#datasetImpactTableBody tr');
        rows.forEach((row, index) => {
            if (datasetRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const systemSelect = row.querySelector('select[data-field="systemId"]');
                const datasetSelect = row.querySelector('select[data-field="datasetId"]');
                const ownerCell = row.querySelector('td:nth-child(4)'); // Dataset Owner column
                
                if (relationTypeSelect) {
                    datasetRelationships[index].relationType = relationTypeSelect.value;
                }
                if (systemSelect) {
                    datasetRelationships[index].systemId = systemSelect.value;
                    const systemName = systemSelect.options[systemSelect.selectedIndex]?.getAttribute('data-system-name') || '';
                    datasetRelationships[index].systemName = systemName;
                }
                if (datasetSelect) {
                    datasetRelationships[index].datasetId = datasetSelect.value;
                    // Update dataset name based on selected dataset
                    const selectedOption = datasetSelect.options[datasetSelect.selectedIndex];
                    if (selectedOption) {
                        datasetRelationships[index].datasetName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        datasetRelationships[index].datasetOwnerName = ownerSpan.textContent.trim();
                    }
                }
                
                // Preserve the current system name from the DOM
                const systemCell = row.querySelector('td:nth-child(2)'); // System column
                if (systemCell) {
                    const systemSpan = systemCell.querySelector('span');
                    if (systemSpan) {
                        datasetRelationships[index].systemName = systemSpan.textContent.trim();
                    }
                }
            }
        });
    }

    // Save current attribute form data before re-rendering
    function saveCurrentAttributeFormData() {
        const rows = document.querySelectorAll('#attributeImpactTableBody tr');
        rows.forEach((row, index) => {
            if (attributeRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const systemSelect = row.querySelector('select[data-field="systemId"]');
                const datasetSelect = row.querySelector('select[data-field="datasetId"]');
                const attributeSelect = row.querySelector('select[data-field="attributeId"]');
                const ownerCell = row.querySelector('td:nth-child(5)'); // Attribute Owner column
                
                if (relationTypeSelect) {
                    attributeRelationships[index].relationType = relationTypeSelect.value;
                }
                if (systemSelect) {
                    attributeRelationships[index].systemId = systemSelect.value;
                    const systemName = systemSelect.options[systemSelect.selectedIndex]?.getAttribute('data-system-name') || '';
                    attributeRelationships[index].systemName = systemName;
                }
                if (datasetSelect) {
                    attributeRelationships[index].datasetId = datasetSelect.value;
                    const selectedOption = datasetSelect.options[datasetSelect.selectedIndex];
                    if (selectedOption) {
                        attributeRelationships[index].datasetName = selectedOption.text;
                    }
                }
                if (attributeSelect) {
                    attributeRelationships[index].attributeId = attributeSelect.value;
                    // Update attribute name based on selected attribute
                    const selectedOption = attributeSelect.options[attributeSelect.selectedIndex];
                    if (selectedOption) {
                        attributeRelationships[index].attributeName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        attributeRelationships[index].attributeOwnerName = ownerSpan.textContent.trim();
                    }
                }
            }
        });
    }

    // Filter datasets based on selected system
    function filterDatasetsBySystem(selectElement) {
        const systemId = selectElement.value;
        const row = selectElement.closest('tr');
        const datasetSelect = row.querySelector('select[data-field="datasetId"]');
        const ownerCell = row.querySelector('td:nth-child(4)'); // Dataset Owner column
        
        if (!datasetSelect) return;
        
        // Update the system ID attribute
        datasetSelect.setAttribute('data-system-id', systemId || '');
        
        // Get all dataset options
        const options = datasetSelect.querySelectorAll('option');
        
        if (systemId) {
            // Show only datasets for this system
            options.forEach(option => {
                if (option.value) {
                    const dataset = datasets.find(d => d.ID == option.value);
                    if (dataset && dataset.MasterSource == systemId) {
                        option.style.display = 'block';
                    } else {
                        option.style.display = 'none';
                        // Clear selection if current dataset doesn't belong to this system
                        if (datasetSelect.value == option.value) {
                            datasetSelect.value = '';
                            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                        }
                    }
                }
            });
        } else {
            // Show all datasets if no system is selected
            options.forEach(option => {
                option.style.display = 'block';
            });
        }
    }

    function filterDatasetsBySystemForAttribute(selectElement) {
        const systemId = selectElement.value;
        const row = selectElement.closest('tr');
        const datasetSelect = row.querySelector('select[data-field="datasetId"]');
        const attributeSelect = row.querySelector('select[data-field="attributeId"]');
        
        if (!datasetSelect) return;
        
        // Update the system ID attribute
        datasetSelect.setAttribute('data-system-id', systemId || '');
        
        // Get all dataset options
        const options = datasetSelect.querySelectorAll('option');
        
        if (systemId) {
            // Show only datasets for this system
            options.forEach(option => {
                if (option.value) {
                    const dataset = datasets.find(d => d.ID == option.value);
                    if (dataset && dataset.MasterSource == systemId) {
                        option.style.display = 'block';
                    } else {
                        option.style.display = 'none';
                        // Clear selection if current dataset doesn't belong to this system
                        if (datasetSelect.value == option.value) {
                            datasetSelect.value = '';
                        }
                    }
                }
            });
        } else {
            // Show all datasets if no system is selected
            options.forEach(option => {
                option.style.display = 'block';
            });
        }
        
        // Clear attribute selection when system changes
        if (attributeSelect) {
            attributeSelect.value = '';
        }
    }

    function filterAttributesByDataset(selectElement) {
        const datasetId = selectElement.value;
        const row = selectElement.closest('tr');
        const attributeSelect = row.querySelector('select[data-field="attributeId"]');
        
        if (!attributeSelect) return;
        
        // Update the dataset ID attribute
        attributeSelect.setAttribute('data-dataset-id', datasetId || '');
        
        // Get all attribute options
        const options = attributeSelect.querySelectorAll('option');
        
        if (datasetId) {
            // Show only attributes for this dataset
            options.forEach(option => {
                if (option.value) {
                    const attribute = attributes.find(a => a.ID == option.value);
                    if (attribute && attribute.Dataset_ID == datasetId) {
                        option.style.display = 'block';
                    } else {
                        option.style.display = 'none';
                        // Clear selection if current attribute doesn't belong to this dataset
                        if (attributeSelect.value == option.value) {
                            attributeSelect.value = '';
                        }
                    }
                }
            });
        } else {
            // Show all attributes if no dataset is selected
            options.forEach(option => {
                option.style.display = 'block';
            });
        }
    }

    // Update dataset owner when dataset is selected
    async function updateDatasetOwner(selectElement) {
        const datasetId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)'); // Dataset Owner column
        
        if (datasetId && ownerCell) {
            try {
                const response = await fetch(`/api/policy-impact/dataset-owner/${datasetId}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
                
                if (response.ok) {
                    const ownerData = await response.json();
                    const ownerName = ownerData.ownerName || 'No owner';
                    ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
                } else {
                    ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                }
            } catch (error) {
                console.error('Error fetching dataset owner:', error);
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
        } else {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    // Update attribute owner when attribute is selected
    async function updateAttributeOwner(selectElement) {
        const attributeId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(5)'); // Attribute Owner column
        
        if (attributeId && ownerCell) {
            try {
                const response = await fetch(`/api/policy-impact/attribute-owner/${attributeId}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
                
                if (response.ok) {
                    const ownerData = await response.json();
                    const ownerName = ownerData.ownerName || 'No owner';
                    ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
                } else {
                    ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                }
            } catch (error) {
                console.error('Error fetching attribute owner:', error);
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
        } else {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    // Update dataset system and dataset info when dataset is selected
    async function updateDatasetInfo(selectElement) {
        const datasetId = selectElement.value;
        const row = selectElement.closest('tr');
        const systemCell = row.querySelector('td:nth-child(2)'); // System column
        
        if (datasetId && systemCell) {
            try {
                // Find the dataset in our loaded datasets array
                const dataset = datasets.find(d => d.ID == datasetId);
                if (dataset) {
                    // Find the system for this dataset
                    const system = systems.find(s => s.id == dataset.MasterSource);
                    if (system) {
                        systemCell.innerHTML = `<span class="text-muted">${system.name}</span>`;
                    } else {
                        systemCell.innerHTML = '<span class="text-muted">No system</span>';
                    }
                } else {
                    systemCell.innerHTML = '<span class="text-muted">No system</span>';
                }
            } catch (error) {
                console.error('Error updating dataset info:', error);
                systemCell.innerHTML = '<span class="text-muted">No system</span>';
            }
        } else {
            systemCell.innerHTML = '<span class="text-muted">No system</span>';
        }
    }

    // Update attribute dataset and system info when attribute is selected
    async function updateAttributeInfo(selectElement) {
        const attributeId = selectElement.value;
        const row = selectElement.closest('tr');
        const datasetCell = row.querySelector('td:nth-child(3)'); // Dataset column
        const systemCell = row.querySelector('td:nth-child(2)'); // System column
        
        if (attributeId && datasetCell && systemCell) {
            try {
                // Find the attribute in our loaded attributes array
                const attribute = attributes.find(a => a.ID == attributeId);
                if (attribute) {
                    // Find the dataset for this attribute
                    const dataset = datasets.find(d => d.ID == attribute.Dataset_ID);
                    if (dataset) {
                        datasetCell.innerHTML = `<span class="text-muted">${dataset.PrimaryName}</span>`;
                        
                        // Find the system for this dataset
                        const system = systems.find(s => s.id == dataset.MasterSource);
                        if (system) {
                            systemCell.innerHTML = `<span class="text-muted">${system.name}</span>`;
                        } else {
                            systemCell.innerHTML = '<span class="text-muted">No system</span>';
                        }
                    } else {
                        datasetCell.innerHTML = '<span class="text-muted">No dataset</span>';
                        systemCell.innerHTML = '<span class="text-muted">No system</span>';
                    }
                } else {
                    datasetCell.innerHTML = '<span class="text-muted">No dataset</span>';
                    systemCell.innerHTML = '<span class="text-muted">No system</span>';
                }
            } catch (error) {
                console.error('Error updating attribute info:', error);
                datasetCell.innerHTML = '<span class="text-muted">No dataset</span>';
                systemCell.innerHTML = '<span class="text-muted">No system</span>';
            }
        } else {
            datasetCell.innerHTML = '<span class="text-muted">No dataset</span>';
            systemCell.innerHTML = '<span class="text-muted">No system</span>';
        }
    }

    // Show success/error message (same design as other edit pages)
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

    // Save product relationships
    async function saveProductRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#productImpactTableBody tr');
            
            console.log('saveProductRelationships: Found ' + rows.length + ' rows');
            
            rows.forEach((row, index) => {
                const selects = row.querySelectorAll('select');
                const relationship = {};
                
                console.log('saveProductRelationships: Processing row ' + index + ' with ' + selects.length + ' selects');
                
                selects.forEach(select => {
                    const field = select.getAttribute('data-field');
                    const value = select.value;
                    console.log('saveProductRelationships: Field ' + field + ' = ' + value);
                    if (value) {
                        relationship[field] = parseInt(value);
                    }
                });
                
                console.log('saveProductRelationships: Row ' + index + ' relationship object:', relationship);
                
                if (relationship.productId && relationship.relationType) {
                    relationships.push(relationship);
                    console.log('saveProductRelationships: Added valid relationship to array');
                } else {
                    console.log('saveProductRelationships: Skipped row ' + index + ' - missing required fields');
                }
            });

            console.log('saveProductRelationships: Final relationships array:', relationships);
            console.log('saveProductRelationships: Sending request with policyId:', currentPolicyId);
            const filtered = await filterRelationshipsForSegment(relationships, 'productId', 'Product');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No product relationships were saved: selected objects belong to another private segment.' };
            }

            const response = await fetch('/api/policy-impact/products/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    policyId: currentPolicyId,
                    relationships: filtered.validRelationships
                })
            });

            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save product relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save product relationships (HTTP ${response.status})`
                };
            }

            const result = await response.json();
            console.log('Product relationships save result:', result);
            console.log('Product relationships - result.success type:', typeof result.success, 'value:', result.success);
            
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            console.log('Product relationships - returning success:', success);
            
            // Return object with success and error message if failed
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save product relationships'
                };
            }
            return true;

        } catch (error) {
            console.error('Error saving product relationships:', error);
            return {
                success: false,
                message: 'Error saving product relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // Save client relationships
    async function saveClientRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#clientImpactTableBody tr');
            
            rows.forEach(row => {
                const selects = row.querySelectorAll('select');
                const relationship = {};
                
                selects.forEach(select => {
                    const field = select.getAttribute('data-field');
                    const value = select.value;
                    if (value) {
                        relationship[field] = parseInt(value);
                    }
                });
                
                if (relationship.clientId && relationship.relationType) {
                    relationships.push(relationship);
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'clientId', 'Client');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No client relationships were saved: selected objects belong to another private segment.' };
            }

            const response = await fetch('/api/policy-impact/clients/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    policyId: currentPolicyId,
                    relationships: filtered.validRelationships
                })
            });

            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save client relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save client relationships (HTTP ${response.status})`
                };
            }

            const result = await response.json();
            console.log('Client relationships save result:', result);
            
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            
            // Return object with success and error message if failed
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save client relationships'
                };
            }
            return true;

        } catch (error) {
            console.error('Error saving client relationships:', error);
            return {
                success: false,
                message: 'Error saving client relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // Save dataset relationships
    async function saveDatasetRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#datasetImpactTableBody tr');
            
            rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const systemSelect = row.querySelector('select[data-field="systemId"]');
                const datasetSelect = row.querySelector('select[data-field="datasetId"]');
                
                if (relationTypeSelect && datasetSelect && 
                    relationTypeSelect.value && datasetSelect.value) {
                    relationships.push({
                        relationType: relationTypeSelect.value,
                        datasetId: datasetSelect.value
                    });
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'datasetId', 'Dataset');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No dataset relationships were saved: selected objects belong to another private segment.' };
            }
            
            const requestData = {
                policyId: currentPolicyId,
                relationships: filtered.validRelationships
            };
            
            const response = await fetch('/api/policy-impact/datasets/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save dataset relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save dataset relationships (HTTP ${response.status})`
                };
            }

            const result = await response.json();
            console.log('Dataset relationships saved successfully:', result);
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save dataset relationships'
                };
            }
            return true;
        } catch (error) {
            console.error('Error saving dataset relationships:', error);
            return {
                success: false,
                message: 'Error saving dataset relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // Save attribute relationships
    async function saveAttributeRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#attributeImpactTableBody tr');
            
            rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const attributeSelect = row.querySelector('select[data-field="attributeId"]');
                
                if (relationTypeSelect && attributeSelect && 
                    relationTypeSelect.value && attributeSelect.value) {
                    relationships.push({
                        relationType: relationTypeSelect.value,
                        attributeId: attributeSelect.value
                    });
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'attributeId', 'Attribute');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No attribute relationships were saved: selected objects belong to another private segment.' };
            }
            
            const requestData = {
                policyId: currentPolicyId,
                relationships: filtered.validRelationships
            };
            
            const response = await fetch('/api/policy-impact/attributes/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save attribute relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save attribute relationships (HTTP ${response.status})`
                };
            }

            const result = await response.json();
            console.log('Attribute relationships saved successfully:', result);
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save attribute relationships'
                };
            }
            return true;
        } catch (error) {
            console.error('Error saving attribute relationships:', error);
            return {
                success: false,
                message: 'Error saving attribute relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // ==================== GLOSSARY FUNCTIONS ====================
    
    // Load glossary relation types
    async function loadGlossaryRelationTypes() {
        try {
            const response = await fetch('/api/policy-impact/glossary-relation-types', {
                credentials: 'include'
            });
            if (response.ok) {
                const data = await response.json();
                glossaryRelationTypes = Array.isArray(data) ? data : [];
                console.log('Loaded glossary relation types:', glossaryRelationTypes.length);
            } else {
                console.error('Failed to load glossary relation types:', response.status);
                glossaryRelationTypes = [];
            }
        } catch (error) {
            console.error('Error loading glossary relation types:', error);
            glossaryRelationTypes = [];
        }
    }
    
    // Load glossaries list
    async function loadGlossaries() {
        try {
            const response = await fetch(await withImpactSegmentAsync('/api/glossary/list'), {
                credentials: 'include'
            });
            if (response.ok) {
                const data = await response.json();
                glossaries = Array.isArray(data) ? data : [];
                glossaries = (await filterRelationshipsForSegment(glossaries, (g) => g?.id ?? g?.ID, 'Glossary')).validRelationships;
                console.log('Loaded glossaries:', glossaries.length);
            } else {
                console.error('Failed to load glossaries:', response.status);
                glossaries = [];
            }
        } catch (error) {
            console.error('Error loading glossaries:', error);
            glossaries = [];
        }
    }
    
    // Load glossary relationships
    async function loadGlossaryRelationships() {
        try {
            const response = await fetch(`/api/policy-impact/${currentPolicyId}/glossaries`, {
                credentials: 'include'
            });
            if (response.ok) {
                const data = await response.json();
                glossaryRelationships = Array.isArray(data) ? data : [];
                originalGlossaryData = JSON.parse(JSON.stringify(glossaryRelationships));
                console.log('Glossary relationships API response:', glossaryRelationships);
                renderGlossaryTable();
            } else {
                console.error('Error loading glossary relationships:', response.status);
                glossaryRelationships = [];
                originalGlossaryData = [];
                renderGlossaryTable();
            }
        } catch (error) {
            console.error('Error loading glossary relationships:', error);
            glossaryRelationships = [];
            originalGlossaryData = [];
            renderGlossaryTable();
        }
    }
    
    // Render glossary table
    function renderGlossaryTable() {
        const tbody = document.querySelector('#glossaryImpactTableBody');
        const footer = document.querySelector('#glossaryImpactFooter');
        
        if (!tbody) return;
        
        // Always ensure at least one empty row exists for adding new relationships
        if (glossaryRelationships.length === 0) {
            glossaryRelationships.push({
                id: 'new-empty',
                glossaryId: null,
                relationType: null,
                glossaryName: '',
                glossaryTypeName: '',
                glossaryOwnerName: ''
            });
        }
        
        let html = '';
        glossaryRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            
            // Get glossary name with ref number
            let glossaryName = relationship.glossaryName || '';
            const glossaryRefNumber = relationship.glossaryRefNumber || '';
            const displayName = glossaryRefNumber ? `${glossaryName} (${glossaryRefNumber})` : glossaryName;
            
            // Get type name
            let typeName = relationship.glossaryTypeName || '';
            if (!typeName && relationship.glossaryId) {
                const glossary = glossaries.find(g => (g.ID === relationship.glossaryId || g.id === relationship.glossaryId));
                if (glossary) {
                    typeName = glossary.typeName || glossary.Type_Name || glossary.Type || '';
                }
            }
            
            html += `
                <tr>
                    <td>
                        <select class="form-select" data-field="relationType">
                            <option value="">Select...</option>
                            ${glossaryRelationTypes.map(rt => 
                                `<option value="${rt.ID || rt.id}" ${relationship.relationType && (rt.ID === relationship.relationType || rt.id === relationship.relationType) ? 'selected' : ''}>${rt.PrimaryName || rt.primaryName || rt.name}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-select" data-field="glossaryId" onchange="updateGlossaryType(this); updateGlossaryOwner(this);">
                            <option value="">Select...</option>
                            ${glossaries.map(g => {
                                const name = g.Name || g.name || '';
                                const refNumber = g.Ref_Number || g.refNumber || g.ref_number || '';
                                const displayText = refNumber ? `${name} (${refNumber})` : name;
                                return `<option value="${g.ID || g.id}" ${relationship.glossaryId && (g.ID === relationship.glossaryId || g.id === relationship.glossaryId) ? 'selected' : ''}>${displayText}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td>
                        <span class="text-muted">${typeName || ''}</span>
                    </td>
                    <td>
                        <span class="text-muted">${relationship.glossaryOwnerName || 'Loading...'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addGlossaryRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteGlossaryRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        
        // Update owner display for rows with glossaryId
        glossaryRelationships.forEach((relationship, index) => {
            if (relationship.glossaryId) {
                const row = tbody.querySelectorAll('tr')[index];
                if (row) {
                    const ownerCell = row.querySelector('td:nth-child(4)');
                    if (ownerCell) {
                        updateGlossaryOwnerDisplay(ownerCell, relationship.glossaryId);
                    }
                }
            }
        });
        
        if (footer) {
            const count = glossaryRelationships.filter(r => r.glossaryId && r.relationType).length;
            footer.textContent = `${count} record${count !== 1 ? 's' : ''}`;
        }
    }
    
    // Add glossary row
    function addGlossaryRow() {
        // Save current form data before re-rendering
        saveCurrentGlossaryFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            glossaryId: null,
            relationType: null,
            glossaryName: '',
            glossaryTypeName: '',
            glossaryOwnerName: ''
        };
        glossaryRelationships.push(newRelationship);
        renderGlossaryTable();
    }
    
    // Delete glossary row
    function deleteGlossaryRow(id) {
        // Save current form data before re-rendering
        saveCurrentGlossaryFormData();
        
        if (glossaryRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            glossaryRelationships[0] = {
                id: 'new-' + Date.now(),
                glossaryId: null,
                relationType: null,
                glossaryName: '',
                glossaryTypeName: '',
                glossaryOwnerName: ''
            };
        } else {
            glossaryRelationships = glossaryRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderGlossaryTable();
    }
    
    // Update glossary type display
    function updateGlossaryType(selectElement) {
        const glossaryId = selectElement.value;
        const row = selectElement.closest('tr');
        const typeCell = row.querySelector('td:nth-child(3)');
        if (!typeCell) return;
        
        if (glossaryId) {
            const glossary = glossaries.find(g => (g.ID == glossaryId || g.id == glossaryId));
            if (glossary) {
                // Use typeName from API response (GlossaryDAO.listGlossary returns typeName)
                const typeName = glossary.typeName || glossary.Type_Name || glossary.Type;
                if (typeName) {
                    typeCell.innerHTML = `<span class="text-muted">${typeName}</span>`;
                } else {
                    typeCell.innerHTML = '<span class="text-muted"></span>';
                }
            } else {
                typeCell.innerHTML = '<span class="text-muted"></span>';
            }
        } else {
            typeCell.innerHTML = '<span class="text-muted"></span>';
        }
    }
    
    // Update glossary owner display
    async function updateGlossaryOwner(selectElement) {
        const glossaryId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        if (!ownerCell) return;
        
        if (glossaryId) {
            await updateGlossaryOwnerDisplay(ownerCell, parseInt(glossaryId));
        } else {
            ownerCell.innerHTML = '<span class="text-muted"></span>';
        }
    }
    
    // Update glossary owner display helper
    async function updateGlossaryOwnerDisplay(ownerCell, glossaryId) {
        try {
            const response = await fetch(`/api/policy-impact/glossary-owner/${glossaryId}`, {
                credentials: 'include'
            });
            if (response.ok) {
                const data = await response.json();
                if (data && data.ownerName) {
                    ownerCell.innerHTML = `<span class="text-muted" title="${data.ownerName}">${data.ownerName}</span>`;
                } else {
                    ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                }
            } else {
                ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
            }
        } catch (error) {
            console.error('Error fetching glossary owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }
    
    // Save current glossary form data
    function saveCurrentGlossaryFormData() {
        const rows = document.querySelectorAll('#glossaryImpactTableBody tr');
        const savedData = [];
        
        rows.forEach((row, index) => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const glossarySelect = row.querySelector('select[data-field="glossaryId"]');
            const typeSpan = row.querySelector('td:nth-child(3) span');
            const ownerSpan = row.querySelector('td:nth-child(4) span');
            
            // Preserve existing id if available
            const existingRelationship = glossaryRelationships[index];
            const rowId = existingRelationship && existingRelationship.id ? existingRelationship.id : ('new-' + Date.now() + '-' + index);
            
            // Get glossary name and ref number from selected option
            let glossaryName = null;
            let glossaryRefNumber = null;
            if (glossarySelect && glossarySelect.value) {
                const glossary = glossaries.find(g => (g.ID == glossarySelect.value || g.id == glossarySelect.value));
                if (glossary) {
                    glossaryName = glossary.Name || glossary.name || null;
                    glossaryRefNumber = glossary.Ref_Number || glossary.refNumber || glossary.ref_number || null;
                }
            }
            
            const data = {
                id: rowId,
                relationType: relationTypeSelect ? parseInt(relationTypeSelect.value) : null,
                glossaryId: glossarySelect ? parseInt(glossarySelect.value) : null,
                glossaryName: glossaryName,
                glossaryRefNumber: glossaryRefNumber,
                glossaryTypeName: typeSpan ? typeSpan.textContent.trim() : null,
                glossaryOwnerName: ownerSpan ? ownerSpan.textContent.trim() : null
            };
            
            // Include even empty rows to maintain structure
            savedData.push(data);
        });
        
        glossaryRelationships = savedData.length > 0 ? savedData : [{
            id: 'new-empty',
            glossaryId: null,
            relationType: null,
            glossaryName: '',
            glossaryTypeName: '',
            glossaryOwnerName: ''
        }];
    }
    
    // Save glossary relationships
    async function saveGlossaryRelationships() {
        try {
            saveCurrentGlossaryFormData();
            
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#glossaryImpactTableBody tr');
            
            rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const glossarySelect = row.querySelector('select[data-field="glossaryId"]');
                
                if (relationTypeSelect && glossarySelect && 
                    relationTypeSelect.value && glossarySelect.value) {
                    relationships.push({
                        relationType: parseInt(relationTypeSelect.value),
                        glossaryId: parseInt(glossarySelect.value)
                    });
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'glossaryId', 'Glossary');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No glossary relationships were saved: selected objects belong to another private segment.' };
            }
            
            const requestData = {
                policyId: currentPolicyId,
                relationships: filtered.validRelationships
            };
            
            const response = await fetch('/api/policy-impact/glossaries/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                console.error('Failed to save glossary relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save glossary relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            console.log('Glossary relationships saved:', result);
            
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save glossary relationships'
                };
            }
            return true;
        } catch (error) {
            console.error('Error saving glossary relationships:', error);
            return {
                success: false,
                message: 'Error saving glossary relationships: ' + (error.message || 'Unknown error')
            };
        }
    }
    
    // Save all Impact data
    // Returns an object with {success: boolean, failedTabs: string[], message: string, errorDetails: string[]}
    async function saveAllImpactData() {
        try {
            console.log('=== saveAllImpactData START ===');
            const results = {
                product: await saveProductRelationships(),
                client: await saveClientRelationships(),
                process: await saveProcessRelationships(),
                project: await saveProjectRelationships(),
                system: await saveSystemRelationships(),
                businessArea: await saveBusinessAreaRelationships(),
                legal: await saveLegalRelationships(),
                dataset: await saveDatasetRelationships(),
                attribute: await saveAttributeRelationships(),
                glossary: await saveGlossaryRelationships()
            };
            
            console.log('Save results:', results);
            
            // Check if all succeeded - only consider true as success
            const allSuccess = Object.values(results).every(result => {
                if (typeof result === 'boolean') return result === true;
                if (result && typeof result === 'object') return result.success === true;
                return false;
            });
            
            const failedTabs = [];
            const errorDetails = [];
            
            // Check each result and collect failure information
            if (results.product !== true && (typeof results.product !== 'object' || !results.product.success)) {
                failedTabs.push('Product');
                if (results.product && typeof results.product === 'object' && results.product.message) {
                    errorDetails.push(`Product: ${results.product.message}`);
                }
            }
            if (results.client !== true && (typeof results.client !== 'object' || !results.client.success)) {
                failedTabs.push('Client');
                if (results.client && typeof results.client === 'object' && results.client.message) {
                    errorDetails.push(`Client: ${results.client.message}`);
                }
            }
            if (results.process !== true && (typeof results.process !== 'object' || !results.process.success)) {
                failedTabs.push('Process');
                if (results.process && typeof results.process === 'object' && results.process.message) {
                    errorDetails.push(`Process: ${results.process.message}`);
                }
            }
            if (results.project !== true && (typeof results.project !== 'object' || !results.project.success)) {
                failedTabs.push('Project');
                if (results.project && typeof results.project === 'object' && results.project.message) {
                    errorDetails.push(`Project: ${results.project.message}`);
                }
            }
            if (results.system !== true && (typeof results.system !== 'object' || !results.system.success)) {
                failedTabs.push('System');
                if (results.system && typeof results.system === 'object' && results.system.message) {
                    errorDetails.push(`System: ${results.system.message}`);
                }
            }
            if (results.businessArea !== true && (typeof results.businessArea !== 'object' || !results.businessArea.success)) {
                failedTabs.push('Business Area');
                if (results.businessArea && typeof results.businessArea === 'object' && results.businessArea.message) {
                    errorDetails.push(`Business Area: ${results.businessArea.message}`);
                }
            }
            if (results.legal !== true && (typeof results.legal !== 'object' || !results.legal.success)) {
                failedTabs.push('Legal Entity');
                if (results.legal && typeof results.legal === 'object' && results.legal.message) {
                    errorDetails.push(`Legal Entity: ${results.legal.message}`);
                }
            }
            if (results.dataset !== true && (typeof results.dataset !== 'object' || !results.dataset.success)) {
                failedTabs.push('Data Set');
                if (results.dataset && typeof results.dataset === 'object' && results.dataset.message) {
                    errorDetails.push(`Data Set: ${results.dataset.message}`);
                }
            }
            if (results.attribute !== true && (typeof results.attribute !== 'object' || !results.attribute.success)) {
                failedTabs.push('Data Attributes');
                if (results.attribute && typeof results.attribute === 'object' && results.attribute.message) {
                    errorDetails.push(`Data Attributes: ${results.attribute.message}`);
                }
            }
            if (results.glossary !== true && (typeof results.glossary !== 'object' || !results.glossary.success)) {
                failedTabs.push('Glossary');
                if (results.glossary && typeof results.glossary === 'object' && results.glossary.message) {
                    errorDetails.push(`Glossary: ${results.glossary.message}`);
                }
            }
            
            console.log('All success:', allSuccess, 'Failed tabs:', failedTabs, 'Error details:', errorDetails);
            
            if (allSuccess) {
                console.log('=== saveAllImpactData SUCCESS ===');
                return {
                    success: true,
                    failedTabs: [],
                    message: 'All Impact data saved successfully',
                    errorDetails: []
                };
            } else {
                // Build error message with details
                let errorMessage = failedTabs.length > 0 
                    ? `Failed to save: ${failedTabs.join(', ')}`
                    : 'Failed to save Impact data';
                
                // Add detailed error messages if available
                if (errorDetails.length > 0) {
                    errorMessage += '. ' + errorDetails.join('. ');
                }
                
                console.log('=== saveAllImpactData FAILED ===', errorMessage);
                return {
                    success: false,
                    failedTabs: failedTabs,
                    message: errorMessage,
                    errorDetails: errorDetails
                };
            }
        } catch (error) {
            console.error('=== saveAllImpactData ERROR ===', error);
            return {
                success: false,
                failedTabs: [],
                message: 'Failed to save Impact data: ' + (error.message || 'Unknown error'),
                errorDetails: []
            };
        }
    }

    // Check if Impact data has changes
    function hasImpactChanges() {
        if (!policyImpactDataLoaded) {
            return false;
        }
        // Capture current UI values from all sub-tabs before comparing.
        saveCurrentProductFormData();
        saveCurrentClientFormData();
        saveCurrentProcessFormData();
        saveCurrentProjectFormData();
        saveCurrentSystemFormData();
        saveCurrentBusinessAreaFormData();
        saveCurrentLegalFormData();
        saveCurrentDatasetFormData();
        saveCurrentAttributeFormData();
        saveCurrentGlossaryFormData();

        const productChanged = JSON.stringify(productRelationships) !== JSON.stringify(originalProductData);
        const clientChanged = JSON.stringify(clientRelationships) !== JSON.stringify(originalClientData);
        const processChanged = JSON.stringify(processRelationships) !== JSON.stringify(originalProcessData);
        const projectChanged = JSON.stringify(projectRelationships) !== JSON.stringify(originalProjectData);
        const systemChanged = JSON.stringify(systemRelationships) !== JSON.stringify(originalSystemData);
        const businessAreaChanged = JSON.stringify(businessAreaRelationships) !== JSON.stringify(originalBusinessAreaData);
        const legalChanged = JSON.stringify(legalRelationships) !== JSON.stringify(originalLegalData);
        const datasetChanged = JSON.stringify(datasetRelationships) !== JSON.stringify(originalDatasetData);
        const attributeChanged = JSON.stringify(attributeRelationships) !== JSON.stringify(originalAttributeData);
        const glossaryChanged = JSON.stringify(glossaryRelationships) !== JSON.stringify(originalGlossaryData);
        
        return productChanged || clientChanged || processChanged || projectChanged || systemChanged || businessAreaChanged || legalChanged || datasetChanged || attributeChanged || glossaryChanged;
    }

    // Update product owner when product is selected
    async function updateProductOwner(selectElement) {
        const productId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)'); // Product Owner column
        
        if (!productId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            return;
        }
        
        try {
            console.log('Updating product owner for product ID:', productId);
            
            const response = await fetch(`/api/policy-impact/product-owner/${productId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('Product owner data:', data);
            
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching product owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    // Update client owner when client is selected
    async function updateClientOwner(selectElement) {
        const clientId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)'); // Client Owner column
        
        if (!clientId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            return;
        }
        
        try {
            console.log('Updating client owner for client ID:', clientId);
            
            const response = await fetch(`/api/policy-impact/client-owner/${clientId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('Client owner data:', data);
            
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching client owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    // Update process owner when process is selected
    async function updateProcessOwner(selectElement) {
        const processId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)'); // Process Owner column
        const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
        
        if (!processId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            refCell.innerHTML = '<span class="text-muted"></span>';
            return;
        }
        
        try {
            console.log('Updating process owner for process ID:', processId);
            
            const response = await fetch(`/api/policy-impact/process-owner/${processId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('Process owner data:', data);
            
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
            // Update reference number
            const selectedProcess = processes.find(p => p.id == processId);
            if (selectedProcess && selectedProcess.refnumber) {
                refCell.innerHTML = `<span class="text-muted">${selectedProcess.refnumber}</span>`;
            } else {
                refCell.innerHTML = '<span class="text-muted"></span>';
            }
            
        } catch (error) {
            console.error('Error fetching process owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
            refCell.innerHTML = '<span class="text-muted"></span>';
        }
    }

    // Update project owner when project is selected
    async function updateProjectOwner(selectElement) {
        const projectId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)'); // Project Owner column
        const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
        
        if (!projectId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            refCell.innerHTML = '<span class="text-muted"></span>';
            return;
        }
        
        try {
            console.log('Updating project owner for project ID:', projectId);
            
            const response = await fetch(`/api/policy-impact/project-owner/${projectId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('Project owner data:', data);
            
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
            // Update reference number
            const selectedProject = projects.find(p => p.id == projectId);
            if (selectedProject && selectedProject.refnumber) {
                refCell.innerHTML = `<span class="text-muted">${selectedProject.refnumber}</span>`;
            } else {
                refCell.innerHTML = '<span class="text-muted"></span>';
            }
            
        } catch (error) {
            console.error('Error fetching project owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
            refCell.innerHTML = '<span class="text-muted"></span>';
        }
    }

    // Save process relationships
    async function saveProcessRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#processImpactTableBody tr');
            
            console.log('saveProcessRelationships: Found ' + rows.length + ' rows');
            
            rows.forEach((row, index) => {
                const selects = row.querySelectorAll('select');
                const relationship = {};
                
                console.log('saveProcessRelationships: Processing row ' + index + ' with ' + selects.length + ' selects');
                
                selects.forEach(select => {
                    const field = select.getAttribute('data-field');
                    const value = select.value;
                    console.log('saveProcessRelationships: Field ' + field + ' = ' + value);
                    if (value) {
                        relationship[field] = parseInt(value);
                    }
                });
                
                // Only add relationships that have both relationType and processId
                if (relationship.relationType && relationship.processId) {
                    relationships.push(relationship);
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'processId', 'Process');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No process relationships were saved: selected objects belong to another private segment.' };
            }
            
            console.log('saveProcessRelationships: Sending relationships:', relationships);
            
            const response = await fetch('/api/policy-impact/processes/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    policyId: currentPolicyId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save process relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save process relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            console.log('Process relationships saved:', result);
            
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save process relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving process relationships:', error);
            return {
                success: false,
                message: 'Error saving process relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // Save project relationships
    async function saveProjectRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#projectImpactTableBody tr');
            
            console.log('saveProjectRelationships: Found ' + rows.length + ' rows');
            
            rows.forEach((row, index) => {
                const selects = row.querySelectorAll('select');
                const inputs = row.querySelectorAll('input');
                const relationship = {};
                
                console.log('saveProjectRelationships: Processing row ' + index + ' with ' + selects.length + ' selects and ' + inputs.length + ' inputs');
                
                selects.forEach(select => {
                    const field = select.getAttribute('data-field');
                    const value = select.value;
                    console.log('saveProjectRelationships: Field ' + field + ' = ' + value);
                    if (value) {
                        relationship[field] = parseInt(value);
                    }
                });
                
                inputs.forEach(input => {
                    const field = input.getAttribute('data-field');
                    const value = input.value;
                    console.log('saveProjectRelationships: Field ' + field + ' = ' + value);
                    if (field && value) {
                        relationship[field] = value;
                    }
                });
                
                // Only add relationships that have both relationType and projectId
                if (relationship.relationType && relationship.projectId) {
                    relationships.push(relationship);
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'projectId', 'Project');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No project relationships were saved: selected objects belong to another private segment.' };
            }
            
            console.log('saveProjectRelationships: Sending relationships:', relationships);
            
            const response = await fetch('/api/policy-impact/projects/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    policyId: currentPolicyId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save project relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save project relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            console.log('Project relationships saved:', result);
            
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save project relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving project relationships:', error);
            return {
                success: false,
                message: 'Error saving project relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // Form data preservation functions for new sub-tabs
    function saveCurrentSystemFormData() {
        const rows = document.querySelectorAll('#systemImpactTableBody tr');
        rows.forEach((row, index) => {
            if (systemRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const systemSelect = row.querySelector('select[data-field="systemId"]');
                const ownerCell = row.querySelector('td:nth-child(3)'); // System Owner column
                
                if (relationTypeSelect) {
                    systemRelationships[index].relationType = relationTypeSelect.value;
                }
                if (systemSelect) {
                    systemRelationships[index].systemId = systemSelect.value;
                    // Update system name based on selected system
                    const selectedOption = systemSelect.options[systemSelect.selectedIndex];
                    if (selectedOption) {
                        systemRelationships[index].systemName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        systemRelationships[index].systemOwnerName = ownerSpan.textContent.trim();
                    }
                }
            }
        });
    }

    function saveCurrentBusinessAreaFormData() {
        const rows = document.querySelectorAll('#businessAreaImpactTableBody tr');
        rows.forEach((row, index) => {
            if (businessAreaRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const businessAreaSelect = row.querySelector('select[data-field="businessAreaId"]');
                
                if (relationTypeSelect) {
                    businessAreaRelationships[index].relationType = relationTypeSelect.value;
                }
                if (businessAreaSelect) {
                    businessAreaRelationships[index].businessAreaId = businessAreaSelect.value;
                    // Update business area name based on selected business area
                    const selectedOption = businessAreaSelect.options[businessAreaSelect.selectedIndex];
                    if (selectedOption) {
                        businessAreaRelationships[index].businessAreaName = selectedOption.text;
                    }
                }
            }
        });
    }

    function saveCurrentLegalFormData() {
        const rows = document.querySelectorAll('#legalImpactTableBody tr');
        rows.forEach((row, index) => {
            if (legalRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const legalSelect = row.querySelector('select[data-field="legalId"]');
                const ownerCell = row.querySelector('td:nth-child(3)'); // Legal Owner column
                
                if (relationTypeSelect) {
                    legalRelationships[index].relationType = relationTypeSelect.value;
                }
                if (legalSelect) {
                    legalRelationships[index].legalId = legalSelect.value;
                    // Update legal name based on selected legal
                    const selectedOption = legalSelect.options[legalSelect.selectedIndex];
                    if (selectedOption) {
                        legalRelationships[index].legalShortName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        legalRelationships[index].legalOwnerName = ownerSpan.textContent.trim();
                    }
                }
            }
        });
    }

    // Owner update functions for new sub-tabs
    async function updateSystemOwner(selectElement) {
        const systemId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)'); // System Owner column
        
        if (!systemId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            return;
        }
        
        try {
            console.log('Updating system owner for system ID:', systemId);
            
            const response = await fetch(`/api/policy-impact/system-owner/${systemId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('System owner data:', data);
            
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching system owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    async function updateLegalOwner(selectElement) {
        const legalId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)'); // Legal Owner column
        
        if (!legalId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            return;
        }
        
        try {
            console.log('Updating legal owner for legal ID:', legalId);
            
            const response = await fetch(`/api/policy-impact/legal-owner/${legalId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            console.log('Legal owner data:', data);
            
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching legal owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    // Save functions for new sub-tabs
    async function saveSystemRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#systemImpactTableBody tr');
            
            console.log('saveSystemRelationships: Found ' + rows.length + ' rows');
            
            rows.forEach((row, index) => {
                const selects = row.querySelectorAll('select');
                const relationship = {};
                
                console.log('saveSystemRelationships: Processing row ' + index + ' with ' + selects.length + ' selects');
                
                selects.forEach(select => {
                    const field = select.getAttribute('data-field');
                    const value = select.value;
                    console.log('saveSystemRelationships: Field ' + field + ' = ' + value);
                    if (value) {
                        relationship[field] = parseInt(value);
                    }
                });
                
                // Only add relationships that have both relationType and systemId
                if (relationship.relationType && relationship.systemId) {
                    relationships.push(relationship);
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'systemId', 'System');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No system relationships were saved: selected objects belong to another private segment.' };
            }
            
            console.log('saveSystemRelationships: Sending relationships:', relationships);
            
            const response = await fetch('/api/policy-impact/systems/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    policyId: currentPolicyId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save system relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save system relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            console.log('System relationships saved:', result);
            
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save system relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving system relationships:', error);
            return {
                success: false,
                message: 'Error saving system relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    async function saveBusinessAreaRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#businessAreaImpactTableBody tr');
            
            console.log('saveBusinessAreaRelationships: Found ' + rows.length + ' rows');
            
            rows.forEach((row, index) => {
                const selects = row.querySelectorAll('select');
                const relationship = {};
                
                console.log('saveBusinessAreaRelationships: Processing row ' + index + ' with ' + selects.length + ' selects');
                
                selects.forEach(select => {
                    const field = select.getAttribute('data-field');
                    const value = select.value;
                    console.log('saveBusinessAreaRelationships: Field ' + field + ' = ' + value);
                    if (value) {
                        relationship[field] = parseInt(value);
                    }
                });
                
                // Only add relationships that have both relationType and businessAreaId
                if (relationship.relationType && relationship.businessAreaId) {
                    relationships.push(relationship);
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'businessAreaId', 'BusinessArea');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No business area relationships were saved: selected objects belong to another private segment.' };
            }
            
            console.log('saveBusinessAreaRelationships: Sending relationships:', relationships);
            
            const response = await fetch('/api/policy-impact/businessareas/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    policyId: currentPolicyId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save business area relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save business area relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            console.log('Business area relationships saved:', result);
            
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save business area relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving business area relationships:', error);
            return {
                success: false,
                message: 'Error saving business area relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    async function saveLegalRelationships() {
        try {
            // Collect data from form
            const relationships = [];
            const rows = document.querySelectorAll('#legalImpactTableBody tr');
            
            console.log('saveLegalRelationships: Found ' + rows.length + ' rows');
            
            rows.forEach((row, index) => {
                const selects = row.querySelectorAll('select');
                const relationship = {};
                
                console.log('saveLegalRelationships: Processing row ' + index + ' with ' + selects.length + ' selects');
                
                selects.forEach(select => {
                    const field = select.getAttribute('data-field');
                    const value = select.value;
                    console.log('saveLegalRelationships: Field ' + field + ' = ' + value);
                    if (value) {
                        relationship[field] = parseInt(value);
                    }
                });
                
                // Only add relationships that have both relationType and legalId
                if (relationship.relationType && relationship.legalId) {
                    relationships.push(relationship);
                }
            });
            const filtered = await filterRelationshipsForSegment(relationships, 'legalId', 'LegalEntity');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No legal relationships were saved: selected objects belong to another private segment.' };
            }
            
            console.log('saveLegalRelationships: Sending relationships:', relationships);
            
            const response = await fetch('/api/policy-impact/legals/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    policyId: currentPolicyId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                // Don't read error response to avoid showing server messages
                console.error('Failed to save legal relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save legal relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            console.log('Legal relationships saved:', result);
            
            // Only return true if result.success is explicitly true
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save legal relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving legal relationships:', error);
            return {
                success: false,
                message: 'Error saving legal relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // Expose functions globally for use by policy-edit.js
    console.log('=== EXPORTING FUNCTIONS TO GLOBAL SCOPE ===');
    window.initImpactEdit = initImpactEdit;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    window.isPolicyImpactDataLoaded = function isPolicyImpactDataLoaded() {
        return policyImpactDataLoaded === true;
    };
    window.addProductRow = addProductRow;
    window.addClientRow = addClientRow;
    window.addProcessRow = addProcessRow;
    window.addProjectRow = addProjectRow;
    window.addSystemRow = addSystemRow;
    window.addBusinessAreaRow = addBusinessAreaRow;
    window.addLegalRow = addLegalRow;
    window.addDatasetRow = addDatasetRow;
    window.addAttributeRow = addAttributeRow;
    window.addGlossaryRow = addGlossaryRow;
    window.deleteProductRow = deleteProductRow;
    window.deleteClientRow = deleteClientRow;
    window.deleteProcessRow = deleteProcessRow;
    window.deleteProjectRow = deleteProjectRow;
    window.deleteSystemRow = deleteSystemRow;
    window.deleteBusinessAreaRow = deleteBusinessAreaRow;
    window.deleteLegalRow = deleteLegalRow;
    window.deleteDatasetRow = deleteDatasetRow;
    window.deleteAttributeRow = deleteAttributeRow;
    window.deleteGlossaryRow = deleteGlossaryRow;
    window.updateProductOwner = updateProductOwner;
    window.updateClientOwner = updateClientOwner;
    window.updateProcessOwner = updateProcessOwner;
    window.updateProjectOwner = updateProjectOwner;
    window.updateSystemOwner = updateSystemOwner;
    window.updateLegalOwner = updateLegalOwner;
    window.updateDatasetOwner = updateDatasetOwner;
    window.updateAttributeOwner = updateAttributeOwner;
    window.updateGlossaryOwner = updateGlossaryOwner;
    window.updateGlossaryType = updateGlossaryType;
    window.filterDatasetsBySystem = filterDatasetsBySystem;
    window.filterDatasetsBySystemForAttribute = filterDatasetsBySystemForAttribute;
    window.filterAttributesByDataset = filterAttributesByDataset;
    console.log('=== FUNCTIONS EXPORTED ===');

})();
console.log('=== POLICY IMPACT EDIT SCRIPT COMPLETE ===');
