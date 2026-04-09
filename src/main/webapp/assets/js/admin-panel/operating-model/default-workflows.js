// Default Workflows - Admin Panel
var DF_W_PREFIX = 'adminPanel.defaultWorkflowsPage';
function dfwT(key, fallback) {
    if (typeof adminT === 'function') return adminT(DF_W_PREFIX + '.' + key, fallback);
    return fallback;
}
function dfwEscape(s) {
    if (s == null) return '';
    var d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}
function dfwTpl(str, vars) {
    if (!str || !vars) return str;
    var out = str;
    Object.keys(vars).forEach(function (k) {
        out = out.split('{{' + k + '}}').join(String(vars[k]));
    });
    return out;
}

/**
 * Default Workflows toasts delegate to unified admin-notifications.js
 */
function dfwShowNotification(message, type) {
    if (typeof window.showAdminNotification === 'function') {
        window.showAdminNotification(message, type);
    } else {
        alert(message == null ? '' : String(message));
    }
}

let currentWorkflow = null, workflows = [], crTypes = [];
let bpmnModeler = null, currentModuleId = null, currentModuleName = null;
let propertiesPanelInitialized = false, currentElement = null;
let propertiesPanelElement = null;
let selectionChangeTimeout = null;

// Error handling helper
function handleError(context, error) {
    console.error(`Error in ${context}:`, error);
    return null;
}

function showDefaultWorkflowsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.defaultWorkflows');
    var h = dfwEscape;
    var t = dfwT;
    var selectType = h(t('selectType', 'Select Type'));

    contentArea.innerHTML = `
        <div class="default-workflows-content">
            <h2>${h(t('title', 'Default Workflows'))}</h2>
            <p>${h(t('intro', 'Configure and manage BPMN workflows for change requests.'))}</p>

            <div class="workflow-form-card">
                <h3>${h(t('selectWorkflow', 'Select Workflow'))}</h3>
                <form id="workflowForm">
                    <div class="form-group">
                        <label>${h(t('facet', 'Facet'))} <span class="required">*</span></label>
                        <select id="facetSelect" required>
                            <option value="">${h(t('selectFacet', 'Select Facet...'))}</option>
                        </select>
                    </div>

                    <div id="facetNotice" style="display: block; margin: 8px 0 12px; padding: 10px; background: #fff8e1; color: #8a6d3b; border: 1px solid #f0e0b3; border-radius: 4px; font-size: 13px;">
                        ${h(t('facetNotice', 'Please select a facet to load workflows.'))}
                    </div>

                    <div class="form-group">
                        <label>${h(t('workflow', 'Workflow'))} <span class="required">*</span></label>
                        <select id="workflowSelect">
                            <option value="">${h(t('addNewWorkflow', '+ Add New Workflow'))}</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label>${h(t('workflowName', 'Workflow Name'))} <span class="required">*</span></label>
                        <input type="text" id="workflowName" required minlength="6"
                               placeholder="${h(t('workflowNamePlaceholder', 'Enter workflow name...'))}">
                        <small id="workflowNameHint">${h(t('minChars', 'Minimum 6 characters'))}</small>
                    </div>

                    <div class="form-group">
                        <label>${h(t('description', 'Description'))} <span class="required">*</span></label>
                        <textarea id="workflowDescription" required minlength="6" rows="3"
                                  placeholder="${h(t('descriptionPlaceholder', 'Describe the workflow purpose...'))}"></textarea>
                        <small id="workflowDescriptionHint">${h(t('minChars', 'Minimum 6 characters'))}</small>
                    </div>

                    <div class="toggle-container" id="workflowActiveContainer">
                        <label>
                            <div class="toggle-switch">
                                <input type="checkbox" id="workflowActive" checked>
                                <span class="toggle-slider"></span>
                            </div>
                            <span>${h(t('active', 'Active'))}</span>
                        </label>
                    </div>

                    <div class="form-group">
                        <label id="label_workflow_form_crType">
                            ${h(t('type', 'Type'))} <i class="fas fa-question-circle" style="color: #6b7280; font-size: 0.85rem; margin-left: 4px; cursor: help;" title="${h(t('typeTitle', 'Select the workflow type'))}"></i>
                        </label>
                        <div class="custom-type-select-container" id="workflowTypeSelectContainer">
                            <button type="button"
                                    id="workflowTypeDropdownToggle"
                                    class="multiselect dropdown-toggle btn btn-default custom-select"
                                    role="combobox"
                                    aria-haspopup="true"
                                    aria-expanded="false"
                                    aria-labelledby="label_workflow_form_crType"
                                    aria-describedby="crTypeDescription"
                                    title="${selectType}">
                                <span class="multiselect-selected-text" id="workflowTypeButtonText">${selectType}</span>
                                <b class="caret"></b>
                            </button>
                            <div class="dropdown-menu custom-type-select-dropdown" id="workflowTypeSelectDropdown" aria-labelledby="workflowTypeDropdownToggle">
                                <div class="dropdown-description" id="crTypeDescription">${h(t('typeDescription', 'Select change request types (optional)'))}</div>
                                <div class="custom-type-options" id="workflowTypeOptions"></div>
                            </div>
                            <input type="hidden" id="workflowTypeSelect" name="workflowType">
                        </div>
                    </div>

                    <div class="form-actions">
                        <button type="button" id="saveWorkflowBtn" class="btn btn-primary">
                            <i class="fas fa-save"></i> ${h(t('saveWorkflow', 'Save Workflow'))}
                        </button>
                    </div>
                </form>
            </div>

            <div class="bpmn-editor-card" id="workflowDiagramCard" style="display: none;">
                <h3>${h(t('workflowDiagram', 'Workflow Diagram'))}</h3>
                <div style="width: 100%; height: 800px; min-height: 700px; position: relative; isolation: isolate;">
                    <div id="bpmnContainer" style="width: 100%; height: 100%; border: 1px solid #ddd; background: #f9f9f9; position: relative; overflow: hidden; z-index: 1; isolation: isolate;">
                        <p id="bpmnPlaceholder" style="padding: 20px; color: #666; text-align: center; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 1; pointer-events: none;">
                            <i class="fas fa-project-diagram" style="font-size: 48px; color: #ccc; display: block; margin-bottom: 10px;"></i>
                            ${h(t('diagramPlaceholder', 'Workflow diagram will appear here'))}
                        </p>
                    </div>
                    <div id="js-properties-panel" class="floating-properties-panel" style="position: absolute; width: 300px; max-height: 600px; right: 20px; top: 20px; border: 1px solid #ddd; background: #fff; overflow-y: auto; z-index: 1000; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); border-radius: 4px;">
                    </div>
                </div>
            </div>
        </div>
    `;

    setTimeout(() => {
        loadFacets();
        loadCRTypes();
        attachEventListeners();
    }, 0);
}

function loadFacets() {
    const facetSelect = document.getElementById('facetSelect');
    if (!facetSelect) {
        setTimeout(loadFacets, 100);
        return;
    }

    // Show loading state
    facetSelect.disabled = true;
    facetSelect.innerHTML = '<option value="">' + dfwEscape(dfwT('loadingFacets', 'Loading facets...')) + '</option>';

    fetch('/api/modules')
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log('API response data:', data);
            const modules = data.modules || [];
            const allowedModules = ['Business Area', 'Capability', 'Glossary', 'Data Sets', 'Client', 'Committee', 'Policy', 'Process', 'Product', 'Project', 'Regulation', 'System', 'Interface'];

            console.log('Total modules from API:', modules.length);
            console.log('Module names:', modules.map(m => m.primaryName));

            // Clear and reset dropdown
            facetSelect.innerHTML = '<option value="">' + dfwEscape(dfwT('selectFacet', 'Select Facet...')) + '</option>';

            if (modules.length === 0) {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = window.I18n ? window.I18n.t('adminPanel.common.noFacetsAvailable') : 'No facets available';
                option.disabled = true;
                facetSelect.appendChild(option);
                console.warn('No modules returned from API');
                return;
            }

            const filteredModules = modules
                .filter(module => {
                    const matches = allowedModules.includes(module.primaryName);
                    if (!matches) {
                        console.log(`Module "${module.primaryName}" (ID: ${module.id}) not in allowed list`);
                    }
                    return matches;
                })
                .sort((a, b) => a.primaryName.localeCompare(b.primaryName));

            console.log('Filtered modules count:', filteredModules.length);
            console.log('Filtered module names:', filteredModules.map(m => m.primaryName));

            if (filteredModules.length === 0) {
                const option = document.createElement('option');
                option.value = '';
                option.textContent = window.I18n ? window.I18n.t('adminPanel.common.noMatchingFacetsFound') : 'No matching facets found';
                option.disabled = true;
                facetSelect.appendChild(option);
                console.warn('No modules match the allowed list:', allowedModules);
                console.warn('Available module names:', modules.map(m => m.primaryName));
                return;
            }

            filteredModules.forEach(module => {
                const option = document.createElement('option');
                option.value = module.id;
                option.textContent = module.primaryName;
                facetSelect.appendChild(option);
                console.log('Added option:', module.id, module.primaryName);
            });

            console.log('Successfully loaded', filteredModules.length, 'facets into dropdown');
            console.log('Current facetSelect value:', facetSelect.value);
            console.log('facetSelect options count:', facetSelect.options.length);
        })
        .catch(error => {
            console.error('Error loading facets:', error);
            facetSelect.innerHTML = '<option value="">' + dfwEscape(dfwT('errorLoadingFacets', 'Error loading facets')) + '</option>';
            const errorOption = document.createElement('option');
            errorOption.value = '';
            errorOption.textContent = dfwT('failedLoadFacetsRefresh', 'Failed to load facets. Please refresh the page.');
            errorOption.disabled = true;
            facetSelect.appendChild(errorOption);
        })
        .finally(() => {
            facetSelect.disabled = false;
        });
}

function loadCRTypes() {
    const dropdownMenu = document.getElementById('workflowTypeSelectDropdown');
    const optionsContainer = document.getElementById('workflowTypeOptions');
    if (!dropdownMenu || !optionsContainer) {
        setTimeout(loadCRTypes, 100);
        return;
    }

    fetch('/api/changerequest_types')
        .then(response => response.json())
        .then(types => {
            crTypes = types;
            optionsContainer.innerHTML = '';

            types.forEach(type => {
                const optionWrapper = document.createElement('div');
                optionWrapper.className = 'dropdown-item custom-type-select-option';

                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.id = `workflowType_${type.name}`;
                checkbox.value = type.name;
                checkbox.className = 'custom-type-checkbox';

                const label = document.createElement('label');
                label.htmlFor = `workflowType_${type.name}`;
                label.textContent = type.name;
                label.className = 'custom-type-label';

                optionWrapper.appendChild(checkbox);
                optionWrapper.appendChild(label);
                optionsContainer.appendChild(optionWrapper);

                checkbox.addEventListener('change', function () {
                    optionWrapper.classList.toggle('option-selected', this.checked);
                    updateTypeSelectDisplay();
                });
            });

            updateTypeSelectDisplay();
        })
        .catch(error => console.error('Error loading CR types:', error));
}

function updateTypeSelectDisplay() {
    const optionsContainer = document.getElementById('workflowTypeOptions');
    const hiddenInput = document.getElementById('workflowTypeSelect');
    const buttonText = document.getElementById('workflowTypeButtonText');
    const button = document.getElementById('workflowTypeDropdownToggle');

    if (!optionsContainer || !hiddenInput || !buttonText || !button) return;

    const checkboxes = optionsContainer.querySelectorAll('.custom-type-checkbox');
    const selected = Array.from(checkboxes).filter(cb => cb.checked);
    const selectedValues = selected.map(cb => cb.value);

    hiddenInput.value = selectedValues.join(',');

    var st = dfwT('selectType', 'Select Type');
    if (selectedValues.length === 0) {
        buttonText.textContent = st;
        button.setAttribute('title', st);
        button.classList.remove('has-selection');
    } else if (selectedValues.length === 1) {
        buttonText.textContent = selectedValues[0];
        button.setAttribute('title', selectedValues[0]);
        button.classList.add('has-selection');
    } else {
        const count = selectedValues.length;
        buttonText.textContent = dfwTpl(dfwT('allSelectedCount', 'All selected ({{count}})'), { count: count });
        button.setAttribute('title', selectedValues.join(', '));
        button.classList.add('has-selection');
    }

    validateTypeSelection();
}

function toggleActiveButton() {
    const workflowSelect = document.getElementById('workflowSelect');
    const activeContainer = document.getElementById('workflowActiveContainer');

    if (!workflowSelect || !activeContainer) return;

    // Hide Active button when adding new workflow (empty value)
    // Show it when editing existing workflow
    if (workflowSelect.value === '' || !workflowSelect.value) {
        activeContainer.style.display = 'none';
    } else {
        activeContainer.style.display = '';
    }
}

function initializeTypeSelect() {
    const container = document.getElementById('workflowTypeSelectContainer');
    const dropdown = document.getElementById('workflowTypeSelectDropdown');
    const toggleButton = document.getElementById('workflowTypeDropdownToggle');

    if (!container || !dropdown || !toggleButton) return;

    const closeDropdown = () => {
        dropdown.classList.remove('show');
        container.classList.remove('open');
        toggleButton.setAttribute('aria-expanded', 'false');
    };

    toggleButton.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        const isOpen = dropdown.classList.contains('show');

        if (isOpen) {
            closeDropdown();
        } else {
            dropdown.classList.add('show');
            container.classList.add('open');
            toggleButton.setAttribute('aria-expanded', 'true');
        }
    });

    document.addEventListener('click', (event) => {
        if (!container.contains(event.target)) {
            closeDropdown();
        }
    });

    dropdown.addEventListener('click', (event) => {
        event.stopPropagation();
    });
}

async function loadWorkflows(entityId) {
    console.log('loadWorkflows called with entityId:', entityId, 'Type:', typeof entityId);

    // Ensure entityId is a valid number or empty string
    if (!entityId || entityId === '' || entityId === 'undefined' || entityId === 'null') {
        console.warn('loadWorkflows: Invalid entityId, using empty string');
        entityId = '';
    }

    try {
        const url = `/api/process_definitions?entityId=${entityId}`;
        console.log('Fetching workflows from URL:', url);
        const response = await fetch(url);

        if (!response.ok) {
            console.error('Error loading workflows: HTTP', response.status, response.statusText);
            workflows = [];
        } else {
            const data = await response.json();
            console.log('Workflows response received:', Array.isArray(data) ? `${data.length} workflows` : 'Not an array', data);
            // Validate that data is an array before using forEach
            if (Array.isArray(data)) {
                workflows = data;
            } else {
                console.error('Error loading workflows: Expected array but got', typeof data, data);
                workflows = [];
            }
        }

        const select = document.getElementById('workflowSelect');
        if (select) {
            select.innerHTML = '<option value="">' + dfwEscape(dfwT('addNewWorkflow', '+ Add New Workflow')) + '</option>';

            if (Array.isArray(workflows)) {
                workflows.forEach(wf => {
                    const option = document.createElement('option');
                    option.value = wf.id;
                    option.textContent = wf.primaryName;
                    select.appendChild(option);
                });
            }

            // Trigger toggle after loading workflows
            toggleActiveButton();
        }
    } catch (error) {
        console.error('Error loading workflows:', error);
        workflows = [];
    }
}

function checkAndShowDiagram(shouldGenerateDefault = true) {
    const facet = document.getElementById('facetSelect').value;
    const workflowSelect = document.getElementById('workflowSelect');
    const workflow = workflowSelect ? workflowSelect.value : '';
    const workflowName = document.getElementById('workflowName').value;
    const description = document.getElementById('workflowDescription').value;

    const diagramCard = document.getElementById('workflowDiagramCard');
    const allFieldsFilled = facet && workflowName && workflowName.length >= 6 && description && description.length >= 6;

    if (allFieldsFilled) {
        diagramCard.style.display = 'block';
        if (!bpmnModeler) {
            initializeBpmnModeler();
        }
        if (shouldGenerateDefault) {
            generateDefaultBpmn();
        }
    } else {
        diagramCard.style.display = 'none';
        destroyBpmnModeler();
    }
}

function attachEventListeners() {
    const ids = ['facetSelect', 'workflowSelect', 'workflowName', 'workflowDescription', 'saveWorkflowBtn'];
    const elements = ids.map(id => document.getElementById(id));

    if (elements.some(el => !el)) {
        setTimeout(attachEventListeners, 100);
        return;
    }

    const [facetSelect, workflowSelect, workflowName, workflowDescription, saveWorkflowBtn] = elements;

    const facetNotice = document.getElementById('facetNotice');

    const setFacetVisibility = (forceShow = false) => {
        const hasFacet = !!facetSelect.value;
        if (facetNotice) {
            facetNotice.style.display = hasFacet ? 'none' : 'block';
        }

        // Show/hide fields based on facet selection
        // If forceShow is true, always show fields (e.g., after save)
        const shouldShow = hasFacet || forceShow;

        const idsToToggle = ['workflowSelect', 'workflowName', 'workflowDescription', 'workflowActive', 'workflowTypeSelectContainer', 'saveWorkflowBtn'];
        idsToToggle.forEach(id => {
            const el = document.getElementById(id);
            if (!el) return;

            // Try to find parent container
            let group = el.closest('.form-group');
            if (!group) {
                group = el.closest('.toggle-container');
            }
            if (!group) {
                group = el.closest('.form-actions');
            }
            if (!group) {
                group = el.parentElement;
            }
            if (!group) {
                group = el;
            }

            // Show the element and its container when facet is selected or forceShow is true
            if (shouldShow) {
                group.style.display = '';
                el.style.display = '';
            } else {
                group.style.display = 'none';
            }
        });
    };

    // Make setFacetVisibility available globally for saveWorkflow function
    window.setFacetVisibility = setFacetVisibility;

    setFacetVisibility();

    facetSelect.addEventListener('change', async function () {
        const selectedValue = this.value;
        const selectedIndex = this.selectedIndex;
        const selectedOption = this.options[selectedIndex];

        console.log('Facet select changed. Value:', selectedValue, 'Selected index:', selectedIndex);
        console.log('Selected option:', selectedOption?.textContent);
        console.log('All options:', Array.from(this.options).map((opt, idx) => `${idx}: ${opt.value} - ${opt.textContent}`));

        // Double-check the value is still set (in case of async issues)
        const currentValue = document.getElementById('facetSelect')?.value;
        console.log('Current facetSelect value after change:', currentValue);

        if (selectedValue && selectedValue !== '' && selectedValue !== 'undefined' && selectedValue !== 'null') {
            currentModuleId = parseInt(selectedValue);
            currentModuleName = selectedOption ? selectedOption.textContent : null;

            console.log('Loading workflows for entityId:', selectedValue, 'Module name:', currentModuleName);

            // Completely clear all form data when facet changes
            // Use clearForm to reset all fields including Active toggle
            clearForm();

            // IMPORTANT: Restore the facet selection after clearForm() resets the form
            // This ensures the selected facet remains visible in the dropdown
            if (facetSelect) {
                facetSelect.value = selectedValue;
            }

            // Reset Active toggle to default (checked = true)
            const workflowActive = document.getElementById('workflowActive');
            if (workflowActive) {
                workflowActive.checked = true;
            }

            // Reset workflow select to default option
            const workflowSelect = document.getElementById('workflowSelect');
            if (workflowSelect) {
                workflowSelect.value = '';
            }

            // Reset current workflow and BPMN state
            currentWorkflow = null;
            window.currentBpmnXml = null;
            window.bpmnLoaded = false;
            currentElement = null;

            // Hide diagram card
            const diagramCard = document.getElementById('workflowDiagramCard');
            if (diagramCard) {
                diagramCard.style.display = 'none';
            }

            // Destroy existing modeler if any
            destroyBpmnModeler();

            // Show all fields when facet is selected
            setFacetVisibility();

            // Load workflows for the selected facet - use the captured value
            await loadWorkflows(selectedValue);

            // Hide Active button since we're starting with a new workflow
            toggleActiveButton();

            // Ensure properties panel stays visible
            const panel = document.getElementById('js-properties-panel');
            if (panel) {
                panel.style.display = 'block';
                panel.style.visibility = 'visible';
                panel.style.opacity = '1';
            }
        } else {
            currentModuleId = null;
            currentModuleName = null;
            // Clear form when no facet is selected
            clearForm();
            // Hide fields when no facet is selected
            setFacetVisibility();
        }
        checkAndShowDiagram();
    });

    workflowSelect.addEventListener('change', function () {
        // Toggle Active button visibility
        toggleActiveButton();

        if (this.value) {
            editWorkflow(parseInt(this.value));
        } else {
            // IMPORTANT: Preserve facetSelect value before clearForm() resets the form
            const facetSelect = document.getElementById('facetSelect');
            const preservedFacetValue = facetSelect ? facetSelect.value : '';

            clearForm();

            // Restore the facet selection after clearForm() resets the form
            // This ensures the selected facet remains visible and the code below works correctly
            if (facetSelect && preservedFacetValue) {
                facetSelect.value = preservedFacetValue;
            }

            const facet = preservedFacetValue || (facetSelect ? facetSelect.value : '');
            const workflowName = document.getElementById('workflowName').value;
            const workflowDescription = document.getElementById('workflowDescription').value;

            // Show diagram if facet, name, and description are filled
            if (facet && workflowName && workflowName.length >= 6 && workflowDescription && workflowDescription.length >= 6) {
                const diagramCard = document.getElementById('workflowDiagramCard');
                if (diagramCard) {
                    diagramCard.style.display = 'block';
                    if (!bpmnModeler) {
                        initializeBpmnModeler();
                    }
                    setTimeout(() => {
                        generateDefaultBpmn();
                    }, 500);
                }
            }
        }
    });

    // Initial toggle when page loads
    toggleActiveButton();

    // Add validation hints for workflow name and description
    function updateValidationHints() {
        const workflowNameHint = document.getElementById('workflowNameHint');
        const workflowDescriptionHint = document.getElementById('workflowDescriptionHint');

        if (workflowNameHint) {
            const nameLength = workflowName.value.trim().length;
            if (nameLength > 0 && nameLength < 6) {
                workflowNameHint.style.color = '#d32f2f'; // Red
            } else {
                workflowNameHint.style.color = ''; // Default color
            }
        }

        if (workflowDescriptionHint) {
            const descLength = workflowDescription.value.trim().length;
            if (descLength > 0 && descLength < 6) {
                workflowDescriptionHint.style.color = '#d32f2f'; // Red
            } else {
                workflowDescriptionHint.style.color = ''; // Default color
            }
        }
    }

    workflowName.addEventListener('input', function () {
        updateValidationHints();
        // Show diagram immediately when name and description are filled
        checkAndShowDiagram();
        const workflowSelect = document.getElementById('workflowSelect');
        const isNewWorkflow = !workflowSelect || !workflowSelect.value;
        if (isNewWorkflow && !window.bpmnLoadingInProgress) {
            const workflowDescription = document.getElementById('workflowDescription');
            const description = workflowDescription ? workflowDescription.value : '';
            // Only generate if both name and description are filled
            if (description && description.length >= 6) {
                generateDefaultBpmn();
            }
        }
    });
    workflowName.addEventListener('blur', function () {
        updateValidationHints();
        const workflowSelect = document.getElementById('workflowSelect');
        const isNewWorkflow = !workflowSelect || !workflowSelect.value;
        checkAndShowDiagram();
        if (isNewWorkflow && !window.bpmnLoadingInProgress) {
            generateDefaultBpmn();
        }
    });

    workflowDescription.addEventListener('input', function () {
        updateValidationHints();
        // Show diagram immediately when name and description are filled
        checkAndShowDiagram();
        const workflowSelect = document.getElementById('workflowSelect');
        const isNewWorkflow = !workflowSelect || !workflowSelect.value;
        if (isNewWorkflow && !window.bpmnLoadingInProgress) {
            const workflowName = document.getElementById('workflowName');
            const name = workflowName ? workflowName.value : '';
            // Only generate if both name and description are filled
            if (name && name.length >= 6) {
                generateDefaultBpmn();
            }
        }
    });
    workflowDescription.addEventListener('blur', function () {
        updateValidationHints();
        checkAndShowDiagram();
    });
    initializeTypeSelect();
    if (saveWorkflowBtn) saveWorkflowBtn.addEventListener('click', saveWorkflow);
}

function validateTypeSelection() {
    const optionsContainer = document.getElementById('workflowTypeOptions');
    const hiddenInput = document.getElementById('workflowTypeSelect');

    if (!optionsContainer || !hiddenInput) return;

    const checkboxes = optionsContainer.querySelectorAll('.custom-type-checkbox');
    const selected = Array.from(checkboxes).filter(cb => cb.checked);

    // Type field is now optional, so no validation error needed
    hiddenInput.setCustomValidity('');
}

// Validate that all required BPMN elements have names
function validateBpmnElementNames() {
    if (!bpmnModeler) return { valid: true, errors: [] };

    const elementRegistry = bpmnModeler.get('elementRegistry');
    const allElements = elementRegistry.getAll();
    const errors = [];

    // Validate Start Events
    const startEvents = allElements.filter(el => el.type === 'bpmn:StartEvent');
    startEvents.forEach(event => {
        const name = event.businessObject?.name || '';
        if (!name || name.trim() === '') {
            errors.push(dfwTpl(dfwT('bpmnErrStartEventMissingName', 'Start Event (ID: {{id}}) is missing a name'), { id: event.id }));
        }
    });

    // Validate End Events
    const endEvents = allElements.filter(el => el.type === 'bpmn:EndEvent');
    endEvents.forEach(event => {
        const name = event.businessObject?.name || '';
        if (!name || name.trim() === '') {
            errors.push(dfwTpl(dfwT('bpmnErrEndEventMissingName', 'End Event (ID: {{id}}) is missing a name'), { id: event.id }));
        }
    });

    // Validate User Tasks
    const userTasks = allElements.filter(el =>
        el.type === 'bpmn:UserTask' || el.type === 'bpmn:Task'
    );
    userTasks.forEach(task => {
        const name = task.businessObject?.name || '';
        if (!name || name.trim() === '') {
            errors.push(dfwTpl(dfwT('bpmnErrUserTaskMissingName', 'User Task (ID: {{id}}) is missing a name'), { id: task.id }));
        }
    });

    // Validate Sequence Flows (arrows from Gateway)
    const gateways = allElements.filter(el =>
        el.type === 'bpmn:ExclusiveGateway' ||
        el.type === 'bpmn:InclusiveGateway' ||
        el.type === 'bpmn:ParallelGateway'
    );

    gateways.forEach(gateway => {
        const outgoing = gateway.businessObject?.outgoing || [];
        // outgoing can be an array of references or direct IDs
        outgoing.forEach(flowRef => {
            let flowId = null;
            if (typeof flowRef === 'string') {
                flowId = flowRef;
            } else if (flowRef && flowRef.id) {
                flowId = flowRef.id;
            } else if (flowRef && typeof flowRef === 'object' && flowRef.$type) {
                // It's a reference object, get the ID
                flowId = flowRef.id || flowRef;
            }

            if (flowId) {
                const sequenceFlow = elementRegistry.get(flowId);
                if (sequenceFlow && sequenceFlow.type === 'bpmn:SequenceFlow') {
                    const flowName = sequenceFlow.businessObject?.name || '';
                    if (!flowName || flowName.trim() === '') {
                        errors.push(dfwTpl(dfwT('bpmnErrSequenceFlowGatewayMissingName', 'Sequence Flow from Gateway (ID: {{id}}) is missing a name'), { id: sequenceFlow.id }));
                    }
                }
            }
        });
    });

    // Also validate all sequence flows in the diagram (not just from gateways)
    // This ensures all arrows have names
    const allSequenceFlows = allElements.filter(el => el.type === 'bpmn:SequenceFlow');
    allSequenceFlows.forEach(flow => {
        const flowName = flow.businessObject?.name || '';
        if (!flowName || flowName.trim() === '') {
            // Check if this flow is connected to a gateway
            const sourceRef = flow.businessObject?.sourceRef;
            if (sourceRef) {
                const sourceElement = elementRegistry.get(sourceRef.id);
                if (sourceElement && (
                    sourceElement.type === 'bpmn:ExclusiveGateway' ||
                    sourceElement.type === 'bpmn:InclusiveGateway' ||
                    sourceElement.type === 'bpmn:ParallelGateway'
                )) {
                    errors.push(dfwTpl(dfwT('bpmnErrSequenceFlowGatewayMissingName', 'Sequence Flow from Gateway (ID: {{id}}) is missing a name'), { id: flow.id }));
                }
            }
        }
    });

    return {
        valid: errors.length === 0,
        errors: errors
    };
}

async function saveWorkflow() {
    if (!checkIsSuperAdmin(window._adminUserRole)) {
        dfwShowNotification(dfwT('noPermissionEditOrAddWorkflow', 'You do not have permission to edit a workflow or add a new one.'), 'error');
        return;
    }

    const facetId = document.getElementById('facetSelect').value;
    const name = document.getElementById('workflowName').value.trim();
    const description = document.getElementById('workflowDescription').value.trim();
    const optionsContainer = document.getElementById('workflowTypeOptions');
    const selectedTypes = optionsContainer ? Array.from(optionsContainer.querySelectorAll('.custom-type-checkbox:checked')).map(cb => cb.value) : [];
    const active = document.getElementById('workflowActive').checked;

    if (!facetId || !name || !description) {
        dfwShowNotification(dfwT('alertFillRequired', 'Please fill all required fields'), 'error');
        // Ensure fields stay visible after validation error
        if (window.setFacetVisibility) {
            window.setFacetVisibility(true);
        }
        return;
    }

    if (name.length < 6 || description.length < 6) {
        dfwShowNotification(dfwT('alertMinChars6', 'Name and description must be at least 6 characters'), 'error');
        // Ensure fields stay visible after validation error
        if (window.setFacetVisibility) {
            window.setFacetVisibility(true);
        }
        return;
    }

    // Validate BPMN element names before saving
    if (bpmnModeler) {
        const validation = validateBpmnElementNames();
        if (!validation.valid) {
            const errorMessage = dfwT('alertBpmnValidationPrefix', 'Please provide names for all required elements:') + '\n\n' + validation.errors.join('\n');
            dfwShowNotification(errorMessage, 'error');
            // Ensure fields stay visible after validation error
            if (window.setFacetVisibility) {
                window.setFacetVisibility(true);
            }
            return;
        }
    }

    try {
        let bpmnXml = null;
        if (bpmnModeler) {
            try {
                // Force save all properties from Properties Panel first (for currently selected element)
                // This ensures all current values (Due Date, Unlock Object, Status, Lifecycle, Commit Changes) are saved
                await saveAllPropertiesFromPanel();

                // Save all properties from all elements in the diagram
                // This ensures properties are saved even if no element is currently selected
                await saveAllPropertiesFromAllElements();

                // Delay to ensure all async saves complete
                await new Promise(resolve => setTimeout(resolve, 300));

                // Force save to get the latest XML with all properties
                const result = await bpmnModeler.saveXML({ format: true });
                bpmnXml = result?.xml || null;
                if (bpmnXml) {
                    // Fix any properties that were serialized incorrectly as [object Object]
                    bpmnXml = await fixPropertiesSerialization(bpmnXml);
                    window.currentBpmnXml = bpmnXml;
                } else {
                    bpmnXml = window.currentBpmnXml;
                }
            } catch (err) {
                console.error('Error saving BPMN XML before workflow save:', err);
                bpmnXml = window.currentBpmnXml;
            }
        } else {
            bpmnXml = window.currentBpmnXml;
        }

        // Determine if we're editing an existing workflow
        // Check both currentWorkflow and workflowSelect.value as fallback
        const workflowSelect = document.getElementById('workflowSelect');
        const selectedWorkflowId = workflowSelect ? workflowSelect.value : null;

        // Find the workflow being edited (use currentWorkflow if available, otherwise find from workflows array)
        let workflowToUpdate = currentWorkflow;
        if (!workflowToUpdate && selectedWorkflowId && selectedWorkflowId !== '') {
            workflowToUpdate = workflows.find(w => w.id === parseInt(selectedWorkflowId));
        }

        // Determine if this is an update or create operation
        const isUpdate = workflowToUpdate != null;
        const workflowId = isUpdate ? workflowToUpdate.id : null;

        // Preserve isDefault flag when updating existing workflow
        const isDefault = isUpdate && workflowToUpdate.isDefault !== undefined
            ? workflowToUpdate.isDefault
            : false;

        const workflowData = {
            primaryName: name,
            description: description,
            status: active ? 'Enabled' : 'Disabled',
            entityId: parseInt(facetId),
            isDefault: isDefault
        };

        const url = isUpdate ? `/api/process_definitions/${workflowId}` : '/api/process_definitions';
        const method = isUpdate ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(workflowData)
        });

        const data = await response.json();
        if (!response.ok || data.error) {
            throw new Error(data.error || `HTTP error! status: ${response.status}`);
        }

        if (!data.id) {
            throw new Error('Workflow saved but no ID returned from server');
        }

        await saveTypeMappings(data.id, selectedTypes, parseInt(facetId));

        if (bpmnXml && bpmnXml.trim().length > 0) {
            bpmnXml = updateBpmnProcessName(bpmnXml, name);
            await saveBpmn(data.id, bpmnXml);
        }

        dfwShowNotification(dfwT('alertSavedSuccess', 'Workflow saved successfully!'), 'success');

        // Reload workflows list and then display the saved workflow
        await loadWorkflows(facetId);

        // Select and display the saved workflow
        // Reuse workflowSelect variable declared earlier
        if (workflowSelect) {
            workflowSelect.value = data.id;
            // Trigger change event to load the workflow
            workflowSelect.dispatchEvent(new Event('change'));
        }

        // Keep fields visible after save (don't hide them)
        if (window.setFacetVisibility) {
            window.setFacetVisibility(true);
        }
    } catch (error) {
        console.error('Error saving workflow:', error);
        dfwShowNotification(dfwTpl(dfwT('alertErrorSaving', 'Error saving workflow: {{message}}'), { message: error.message || dfwT('unknownError', 'Unknown error') }), 'error');
    }
}

async function saveTypeMappings(processDefId, crTypes, entityId) {
    if (crTypes.length === 0) return;

    try {
        const response = await fetch('/api/workflow_types', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                processDefinitionId: processDefId,
                crTypes: crTypes,
                entityId: entityId
            })
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
        }
    } catch (error) {
        console.error('Error saving type mappings:', error);
        throw error;
    }
}

function updateBpmnProcessName(bpmnXml, workflowName) {
    if (!bpmnXml || !workflowName) return bpmnXml;

    try {
        const escapedName = workflowName.replace(/&/g, '&amp;').replace(/"/g, '&quot;');

        bpmnXml = bpmnXml.replace(
            /(<bpmn2:process[^>]*\s)name="[^"]*"/g,
            `$1name="${escapedName}"`
        );

        if (bpmnXml.includes('<bpmn2:process') && !bpmnXml.match(/<bpmn2:process[^>]*name=/)) {
            bpmnXml = bpmnXml.replace(
                /(<bpmn2:process[^>]*)(>)/g,
                `$1 name="${escapedName}"$2`
            );
        }

        return bpmnXml;
    } catch (error) {
        console.warn('Error updating BPMN process name:', error);
        return bpmnXml;
    }
}

function saveBpmn(processDefId, xml) {
    if (!processDefId || isNaN(processDefId)) {
        throw new Error('Invalid process definition ID');
    }

    if (!xml || typeof xml !== 'string' || xml.trim().length === 0) {
        throw new Error('BPMN XML content is required and cannot be empty');
    }

    console.log(`💾 Saving BPMN XML to server for process definition ${processDefId}...`);
    console.log(`📄 XML length: ${xml.length} characters`);

    // Log a sample of the XML for debugging (first 500 chars)
    const xmlSample = xml.substring(0, 500);
    console.log(`📋 XML sample (first 500 chars):`, xmlSample);

    // Check if XML contains expected properties (check for both camunda: and camunda_1: namespaces)
    const hasStatusProperty = 
        xml.includes('camunda:status=') || 
        xml.includes('camunda_1:status=') ||
        xml.includes('<camunda:property name="status"') ||
        xml.includes('<camunda_1:property name="status"') ||
        /<camunda[^:]*:property[^>]*name=["\']status["\'][^>]*>/.test(xml);
    const hasLifecycleProperty = 
        xml.includes('camunda:lifecycle=') || 
        xml.includes('camunda_1:lifecycle=') ||
        xml.includes('<camunda:property name="lifecycle"') ||
        xml.includes('<camunda_1:property name="lifecycle"') ||
        /<camunda[^:]*:property[^>]*name=["\']lifecycle["\'][^>]*>/.test(xml);
    
    console.log(`🔍 XML contains status property: ${hasStatusProperty}, lifecycle property: ${hasLifecycleProperty}`);
    
    // If properties are missing, log detailed information
    if (!hasStatusProperty || !hasLifecycleProperty) {
        // Try to find Properties container in XML
        const propertiesMatch = xml.match(/<camunda[^:]*:Properties[^>]*>([\s\S]*?)<\/camunda[^:]*:Properties>/i);
        if (propertiesMatch) {
            console.warn(`⚠️ Properties container found but properties may not be serialized correctly:`, propertiesMatch[0].substring(0, 200));
        } else {
            console.warn(`⚠️ No Properties container found in XML for element`);
        }
        
        // Check for extensionElements
        const extElementsMatch = xml.match(/<bpmn2:extensionElements[^>]*>([\s\S]*?)<\/bpmn2:extensionElements>/i);
        if (extElementsMatch) {
            console.log(`📋 ExtensionElements content:`, extElementsMatch[0].substring(0, 300));
        }
    }

    return fetch(`/api/process_definitions/${processDefId}/bpmn`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ xml: xml })
    })
        .then(async response => {
            if (!response.ok) {
                let errorMessage = `HTTP error! status: ${response.status}`;
                let errorDetails = null;
                try {
                    const errorData = await response.json();
                    errorMessage = errorData.error || errorMessage;
                    errorDetails = errorData;
                } catch (e) {
                    // If response is not JSON, try to get text
                    try {
                        const errorText = await response.text();
                        errorMessage = errorText || errorMessage;
                    } catch (e2) {
                        // Ignore if we can't read response
                    }
                }
                console.error(`❌ Error saving BPMN to server:`, {
                    status: response.status,
                    statusText: response.statusText,
                    error: errorMessage,
                    details: errorDetails
                });
                throw new Error(errorMessage);
            }

            const result = await response.json();
            console.log(`✅ BPMN XML saved successfully to server for process definition ${processDefId}`);
            if (result.path) {
                console.log(`📁 Saved to path: ${result.path}`);
            }
            return result;
        })
        .catch(error => {
            console.error('❌ Error saving BPMN to server:', {
                processDefId: processDefId,
                error: error.message || error,
                stack: error.stack
            });
            throw error;
        });
}

function editWorkflow(id) {
    const workflow = workflows.find(w => w.id === id);
    if (!workflow) return;

    currentWorkflow = workflow;

    document.getElementById('workflowName').value = workflow.primaryName;
    document.getElementById('workflowDescription').value = workflow.description;
    document.getElementById('workflowActive').checked = workflow.status === 'Enabled';

    // Show Active button when editing existing workflow
    toggleActiveButton();

    loadWorkflowTypes(id);
    checkAndShowDiagram(false);
    window.bpmnLoadingInProgress = true;

    fetch(`/api/process_definitions/${id}/bpmn`)
        .then(response => response.json())
        .then(data => {
            if (data.xml) {
                window.currentBpmnXml = data.xml;
                loadBpmnIntoModeler(data.xml).then(() => {
                    window.bpmnLoadingInProgress = false;
                }).catch(err => {
                    console.error('Error loading BPMN into modeler:', err);
                    window.bpmnLoadingInProgress = false;
                    generateDefaultBpmn();
                });
            } else {
                window.bpmnLoadingInProgress = false;
                generateDefaultBpmn();
            }
        })
        .catch(error => {
            console.error('Error loading BPMN:', error);
            window.bpmnLoadingInProgress = false;
            generateDefaultBpmn();
        });
}

function loadWorkflowTypes(processDefId) {
    fetch(`/api/workflow_types/${processDefId}`)
        .then(response => response.json())
        .then(types => {
            const optionsContainer = document.getElementById('workflowTypeOptions');
            if (!optionsContainer) {
                setTimeout(() => loadWorkflowTypes(processDefId), 300);
                return;
            }

            const checkboxes = optionsContainer.querySelectorAll('.custom-type-checkbox');
            checkboxes.forEach(checkbox => {
                checkbox.checked = false;
            });

            if (Array.isArray(types) && types.length > 0) {
                types.forEach(type => {
                    const crType = type.crType || type.cr_type || type.name;
                    if (crType) {
                        const checkbox = optionsContainer.querySelector(`.custom-type-checkbox[value="${crType}"]`);
                        if (checkbox) {
                            checkbox.checked = true;
                            const optionWrapper = checkbox.closest('.custom-type-select-option, .dropdown-item');
                            if (optionWrapper) {
                                optionWrapper.classList.add('option-selected');
                            }
                        }
                    }
                });
                updateTypeSelectDisplay();
            }
        })
        .catch(error => {
            console.error('Error loading workflow types:', error);
        });
}

function clearForm(keepFieldsVisible = false) {
    currentWorkflow = null;
    document.getElementById('workflowForm').reset();
    document.getElementById('workflowDiagramCard').style.display = 'none';
    window.currentBpmnXml = null;
    window.bpmnLoaded = false;

    // If keepFieldsVisible is true, ensure fields stay visible (e.g., after save)
    if (keepFieldsVisible && window.setFacetVisibility) {
        window.setFacetVisibility(true);
    }

    const optionsContainer = document.getElementById('workflowTypeOptions');
    if (optionsContainer) {
        const checkboxes = optionsContainer.querySelectorAll('.custom-type-checkbox');
        checkboxes.forEach(checkbox => {
            checkbox.checked = false;
            const optionWrapper = checkbox.closest('.custom-type-select-option, .dropdown-item');
            optionWrapper?.classList.remove('option-selected');
        });
        updateTypeSelectDisplay();
    }

    const dropdownMenu = document.getElementById('workflowTypeSelectDropdown');
    const toggleButton = document.getElementById('workflowTypeDropdownToggle');
    const container = document.getElementById('workflowTypeSelectContainer');

    dropdownMenu?.classList.remove('show');
    container?.classList.remove('open');

    if (toggleButton) {
        toggleButton.setAttribute('aria-expanded', 'false');
        var st2 = dfwT('selectType', 'Select Type');
        toggleButton.setAttribute('title', st2);
        toggleButton.classList.remove('has-selection');
        const buttonText = document.getElementById('workflowTypeButtonText');
        if (buttonText) buttonText.textContent = st2;
    }

    destroyBpmnModeler();
}

// Helper function to create BPMN XML
function createBpmnXml(params) {
    const { displayName, workflowNameClean, timestamp, facetSelect, facetName, processId, collaborationId, participantId, laneSetId, laneId, startEventId, endEventId, sequenceFlowId } = params;

    return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn2:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns:bpmn2="http://www.omg.org/spec/BPMN/20100524/MODEL"
                   xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                   xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                   xmlns:camunda="http://activiti.org/bpmn"
                   xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                   id="ChangeRequest-${workflowNameClean}-default-${timestamp}"
                   targetNamespace="ChangeRequest-${workflowNameClean}"
                   exporter="axon"
                   exporterVersion="1.0.0"
                   xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd">
  <bpmn2:collaboration id="${collaborationId}" name="${displayName}">
    <bpmn2:participant id="${participantId}" name="Roles" processRef="${processId}" />
  </bpmn2:collaboration>
  <bpmn2:process id="${processId}" name="${displayName}" isExecutable="true">
    <bpmn2:startEvent id="${startEventId}" name="Start" />
    <bpmn2:endEvent id="${endEventId}" name="End" />
    <bpmn2:sequenceFlow id="${sequenceFlowId}" sourceRef="${startEventId}" targetRef="${endEventId}" />
    <bpmn2:laneSet id="${laneSetId}">
      <bpmn2:lane id="${laneId}" name="${facetSelect}:${facetName}">
        <bpmn2:flowNodeRef>${startEventId}</bpmn2:flowNodeRef>
        <bpmn2:flowNodeRef>${endEventId}</bpmn2:flowNodeRef>
      </bpmn2:lane>
    </bpmn2:laneSet>
  </bpmn2:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_ChangeRequest-${workflowNameClean}-default_diagram">
    <bpmndi:BPMNPlane id="BPMNDiagram_ChangeRequest-${workflowNameClean}-default" bpmnElement="${collaborationId}">
      <bpmndi:BPMNShape id="${participantId}_di" bpmnElement="${participantId}" isHorizontal="true">
        <dc:Bounds x="100" y="100" width="600" height="250" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="${laneId}_di" bpmnElement="${laneId}" isHorizontal="true">
        <dc:Bounds x="130" y="100" width="570" height="250" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="${startEventId}_di" bpmnElement="${startEventId}">
        <dc:Bounds x="200" y="200" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="195" y="243" width="46" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="${endEventId}_di" bpmnElement="${endEventId}">
        <dc:Bounds x="500" y="200" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="495" y="243" width="46" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="${sequenceFlowId}_di" bpmnElement="${sequenceFlowId}">
        <di:waypoint x="236" y="218" />
        <di:waypoint x="500" y="218" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn2:definitions>`;
}

// Helper function to create empty BPMN XML (without start/end events)
function createEmptyBpmnXml(params) {
    const {
        displayName, workflowNameClean, timestamp,
        processId, collaborationId, participantId,
        laneSetId, lanes // lanes is array of {id, name}
    } = params;

    // Build lanes XML
    let lanesXml = '';
    let lanesDiXml = '';
    const laneHeight = 150; // Height per lane
    const startY = 100;
    const laneWidth = 570;
    const laneX = 130;

    lanes.forEach((lane, index) => {
        const laneY = startY + (index * laneHeight);
        lanesXml += `      <bpmn2:lane id="${lane.id}" name="${lane.name}">
      </bpmn2:lane>\n`;

        lanesDiXml += `      <bpmndi:BPMNShape id="${lane.id}_di" bpmnElement="${lane.id}" isHorizontal="true">
        <dc:Bounds x="${laneX}" y="${laneY}" width="${laneWidth}" height="${laneHeight}" />
      </bpmndi:BPMNShape>\n`;
    });

    const participantHeight = Math.max(lanes.length * laneHeight, 250);

    return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn2:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns:bpmn2="http://www.omg.org/spec/BPMN/20100524/MODEL"
                   xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                   xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                   xmlns:camunda="http://activiti.org/bpmn"
                   xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                   id="ChangeRequest-${workflowNameClean}-default-${timestamp}"
                   targetNamespace="ChangeRequest-${workflowNameClean}"
                   exporter="axon"
                   exporterVersion="1.0.0"
                   xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd">
  <bpmn2:collaboration id="${collaborationId}" name="${displayName}">
    <bpmn2:participant id="${participantId}" name="Roles" processRef="${processId}" />
  </bpmn2:collaboration>
  <bpmn2:process id="${processId}" name="${displayName}" isExecutable="true">
    <bpmn2:laneSet id="${laneSetId}">
${lanesXml}    </bpmn2:laneSet>
  </bpmn2:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_ChangeRequest-${workflowNameClean}-default_diagram">
    <bpmndi:BPMNPlane id="BPMNDiagram_ChangeRequest-${workflowNameClean}-default" bpmnElement="${collaborationId}">
      <bpmndi:BPMNShape id="${participantId}_di" bpmnElement="${participantId}" isHorizontal="true">
        <dc:Bounds x="100" y="100" width="600" height="${participantHeight}" />
      </bpmndi:BPMNShape>
${lanesDiXml}    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn2:definitions>`;
}

async function generateDefaultBpmn() {
    if (window.bpmnLoadingInProgress) return;
    if (currentWorkflow && window.currentBpmnXml && window.currentBpmnXml.trim().length > 0) return;

    const workflowNameInput = document.getElementById('workflowName');
    const workflowName = workflowNameInput ? workflowNameInput.value : '';
    const facetSelect = document.getElementById('facetSelect').value;
    const descriptionInput = document.getElementById('workflowDescription');
    const description = descriptionInput ? descriptionInput.value : '';

    if (!facetSelect) return;

    const displayName = workflowName && workflowName.length >= 6 ? workflowName : 'New Workflow';
    const facetSelectElement = document.getElementById('facetSelect');
    const facetName = facetSelectElement.options[facetSelectElement.selectedIndex]?.text || 'Role';
    const timestamp = Date.now();
    const workflowNameClean = displayName.replace(/\s+/g, '-').replace(/[^a-zA-Z0-9-]/g, '');
    const processId = `ChangeRequest-${workflowNameClean}-default-pool-${timestamp}`;
    const collaborationId = `ChangeRequest-${workflowNameClean}-default${timestamp}`;
    const participantId = `ChangeRequest-${workflowNameClean}-default-pool-participant-${timestamp}`;
    const laneSetId = `${processId}-laneset-${timestamp}`;

    // Check if this is a new workflow (not editing existing)
    const workflowSelect = document.getElementById('workflowSelect');
    const isNewWorkflow = !workflowSelect || !workflowSelect.value || workflowSelect.value === '';

    if (isNewWorkflow) {
        // For new workflows, create empty BPMN with lanes based on roles
        let rolesList = [];
        if (currentModuleId) {
            try {
                rolesList = await loadRolesForModule(currentModuleId);
            } catch (err) {
                console.error('Error loading roles:', err);
            }
        }

        // If no roles found, create a default lane
        if (rolesList.length === 0) {
            rolesList = [{ name: facetName }];
        }

        // Create lanes array
        const lanes = rolesList.map((role, index) => ({
            id: `${processId}-laneset-${timestamp}-lane-${index}`,
            name: role.name || role.primaryName || `Role ${index + 1}`
        }));

        // Create empty BPMN with lanes
        const emptyBpmn = createEmptyBpmnXml({
            displayName,
            workflowNameClean,
            timestamp,
            processId,
            collaborationId,
            participantId,
            laneSetId,
            lanes
        });

        window.currentBpmnXml = emptyBpmn;
        window.bpmnLoaded = false;

        if (bpmnModeler) {
            loadBpmnIntoModeler(emptyBpmn).catch(err => {
                console.error('Error loading empty BPMN into modeler:', err);
            });
            window.bpmnLoaded = true;

            // After loading empty diagram, set role names for lanes
            setTimeout(async () => {
                if (bpmnModeler && rolesList.length > 0) {
                    const elementRegistry = bpmnModeler.get('elementRegistry');
                    const modeling = bpmnModeler.get('modeling');
                    const allLanes = elementRegistry.filter(el => el.type === 'bpmn:Lane');

                    for (let index = 0; index < allLanes.length && index < rolesList.length; index++) {
                        const lane = allLanes[index];
                        const role = rolesList[index];
                        const roleName = role.name || role.primaryName || `Role ${index + 1}`;
                        try {
                            modeling.updateProperties(lane, { name: roleName });
                            modeling.updateLabel(lane, roleName);

                            // Save roleName property
                            if (lane.businessObject) {
                                await saveElementProperty(lane.businessObject, 'roleName', roleName);
                                if (roleName === 'Requestor') {
                                    await saveElementProperty(lane.businessObject, 'isRequester', 'true');
                                }
                            }
                        } catch (e) {
                            console.error('Error setting lane role:', e);
                        }
                    }
                }
            }, 500);
        }
    } else {
        // For existing workflows, use the old default BPMN with start/end events
        const laneId = `${processId}-laneset-${timestamp}-lane-${facetSelect}`;
        const startEventId = `${processId}-start-event`;
        const endEventId = `${processId}-end-event`;
        const sequenceFlowId = `${processId}-flow-1`;

        const defaultBpmn = createBpmnXml({
            displayName,
            workflowNameClean,
            timestamp,
            facetSelect,
            facetName,
            processId,
            collaborationId,
            participantId,
            laneSetId,
            laneId,
            startEventId,
            endEventId,
            sequenceFlowId
        });

        window.currentBpmnXml = defaultBpmn;
        window.bpmnLoaded = false;

        if (bpmnModeler) {
            loadBpmnIntoModeler(defaultBpmn).catch(err => {
                console.error('Error loading default BPMN into modeler:', err);
            });
            window.bpmnLoaded = true;

            // After loading default diagram, set default lane role if available
            setTimeout(() => {
                applyDefaultRoleToLanes();
            }, 500);
        }
    }
}

function initializeBpmnModeler() {
    const container = document.getElementById('bpmnContainer');
    if (!container) return;

    let ModelerClass = window.BpmnModeler || window.BpmnJS || BpmnModeler || BpmnJS;
    if (!ModelerClass) {
        container.innerHTML = `
            <div style="padding: 20px; text-align: center; color: #d32f2f;">
                <i class="fas fa-exclamation-triangle" style="font-size: 48px; margin-bottom: 10px;"></i>
                <p><strong>BPMN.js library not loaded</strong></p>
                <p>Please ensure the BPMN.js library is properly included in the HTML.</p>
            </div>
        `;
        return;
    }

    const placeholder = container.querySelector('#bpmnPlaceholder');
    if (placeholder) placeholder.remove();
    container.innerHTML = '';

    try {
        const additionalModules = [];
        if (typeof window.CustomBpmnRenderer !== 'undefined') {
            additionalModules.push(window.CustomBpmnRenderer);
        }

        // Note: Custom palette entries are added via addPaletteButtons() after initialization
        // We don't need to override the default palette provider

        bpmnModeler = new ModelerClass({
            container: container,
            additionalModules: additionalModules
        });

        try {
            const eventBus = bpmnModeler.get('eventBus');
            if (eventBus) {
                eventBus.on('commandStack.changed', async () => {
                    try {
                        const result = await bpmnModeler.saveXML({ format: true });
                        if (result && result.xml) {
                            window.currentBpmnXml = result.xml;
                        }
                    } catch (err) { }
                });

                // selection.changed handled in initializePropertiesPanel with debounce
                // Removed duplicate handler to prevent conflicts

                eventBus.on('element.click', async (event) => {
                    const element = event.element;
                    if (element && element.type) {
                        setTimeout(async () => {
                            if (element) {
                                await loadPropertiesForElement(element);
                            }
                        }, 50);
                    }
                });

                eventBus.on('element.click', (event) => {
                    const element = event.element;
                    if (element && element.type) {
                        setTimeout(() => {
                            if (element) {
                                loadPropertiesForElement(element);
                            }
                        }, 50);
                    }
                });

                // Prevent automatic naming of sequence flows from gateways
                // This handler intercepts when a connection is created and clears the name
                // if it was auto-named with the target element's name
                // Use a small delay to ensure the connection is fully created before checking
                eventBus.on('commandStack.connection.create.postExecute', (event) => {
                    setTimeout(() => {
                        try {
                            const context = event.context;
                            const connection = context.connection;

                            if (connection && connection.type === 'bpmn:SequenceFlow') {
                                const elementRegistry = bpmnModeler.get('elementRegistry');
                                const updatedConnection = elementRegistry.get(connection.id);

                                if (updatedConnection) {
                                    const source = updatedConnection.source;
                                    const target = updatedConnection.target;

                                    // Check if source is a gateway
                                    if (source && (
                                        source.type === 'bpmn:ExclusiveGateway' ||
                                        source.type === 'bpmn:InclusiveGateway' ||
                                        source.type === 'bpmn:ParallelGateway'
                                    )) {
                                        // Get target element name
                                        const targetName = target.businessObject?.name || '';
                                        const flowName = updatedConnection.businessObject?.name || '';

                                        // If flow was auto-named with target's name, clear it
                                        // This allows user to name it manually
                                        if (flowName === targetName && targetName) {
                                            const modeling = bpmnModeler.get('modeling');
                                            modeling.updateProperties(updatedConnection, { name: '' });
                                        }
                                    }
                                }
                            }
                        } catch (err) {
                            // Silently ignore errors to prevent breaking the connection creation
                            console.warn('Error preventing auto-naming of gateway sequence flow:', err);
                        }
                    }, 50);
                });
            }
        } catch (e) { }

        // Initialize properties panel after modeler is created
        initializePropertiesPanel();

        // Re-initialize properties panel event listeners after modeler is ready
        setTimeout(() => {
            if (bpmnModeler && propertiesPanelElement) {
                const eventBus = bpmnModeler.get('eventBus');
                if (eventBus) {
                    // Re-attach event listeners in case they weren't attached earlier
                    eventBus.on('selection.changed', (event) => {
                        clearTimeout(selectionChangeTimeout);
                        selectionChangeTimeout = setTimeout(() => {
                            const element = event.newSelection && event.newSelection.length > 0 ? event.newSelection[0] : null;
                            if (element) {
                                updatePropertiesPanelContent(element);
                            } else {
                                clearPropertiesPanel();
                            }
                        }, 50);
                    });

                    eventBus.on('element.changed', (event) => {
                        if (currentElement && event.element && event.element.id === currentElement.id) {
                            updatePropertiesPanelContent(event.element);
                        }
                    });

                    eventBus.on('root.set', () => {
                        // لا تفعل شيء - panel موجود بالفعل
                    });
                }
            }
        }, 100);

        setTimeout(() => {
            const bjsContainer = container.querySelector('.bjs-container');
            if (bjsContainer) {
                bjsContainer.style.width = '100%';
                bjsContainer.style.height = '100%';
                bjsContainer.style.position = 'relative';
            }

            const palette = container.querySelector('.djs-palette');
            if (palette) {
                palette.style.display = 'block';
                palette.style.visibility = 'visible';
                if (!palette.classList.contains('open')) {
                    const toggle = palette.querySelector('.djs-palette-toggle');
                    if (toggle) toggle.click();
                }
                addPaletteButtons();
            }

            if (window.currentBpmnXml && !window.bpmnLoaded) {
                loadBpmnIntoModeler(window.currentBpmnXml).catch(err => {
                    console.error('Error loading BPMN XML after initialization:', err);
                });
                window.bpmnLoaded = true;
            }

            try {
                const canvas = bpmnModeler.get('canvas');
                if (canvas) canvas.zoom('fit-viewport', 'auto');
            } catch (e) { }
        }, 300);
    } catch (error) {
        console.error('Error initializing BPMN Modeler:', error);
        container.innerHTML = `
            <div style="padding: 20px; text-align: center; color: #d32f2f;">
                <i class="fas fa-exclamation-triangle" style="font-size: 48px; margin-bottom: 10px;"></i>
                <p><strong>Error initializing BPMN Modeler</strong></p>
                <p>${error.message}</p>
            </div>
        `;
    }
}

// Save current BPMN XML snapshot into window.currentBpmnXml
async function saveCurrentBpmnXml() {
    if (!bpmnModeler) return;
    try {
        const result = await bpmnModeler.saveXML({ format: true });
        if (result && result.xml) {
            window.currentBpmnXml = result.xml;
        }
    } catch (e) {
        console.error('Error saving BPMN XML snapshot:', e);
    }
}

// Force save all properties from Properties Panel before workflow save
async function saveAllPropertiesFromPanel() {
    if (!bpmnModeler) return;

    try {
        const tabContent = document.getElementById('propertiesTabContent');
        if (!tabContent) return;

        // If currentElement is set, save its properties from DOM
        if (currentElement) {
            const elementType = currentElement.type;
            let businessObject = currentElement.businessObject;
            if (businessObject) {
                // Save properties based on element type
                if (elementType === 'bpmn:UserTask' || elementType === 'bpmn:Task') {
                    // Save Due Date
                    const dueDateSelect = document.getElementById('prop-task-due-date');
                    if (dueDateSelect && dueDateSelect.value) {
                        await saveElementProperty(businessObject, 'dueDate', dueDateSelect.value);
                    }

                    // Save Unlock Object
                    const unlockObjectCheckbox = document.getElementById('prop-task-unlock-object');
                    if (unlockObjectCheckbox) {
                        await saveElementProperty(businessObject, 'unlockObject', unlockObjectCheckbox.checked ? 'true' : 'false');
                    }
                } else if (elementType === 'bpmn:EndEvent') {
                    // Save Status
                    const statusSelect = document.getElementById('prop-end-status');
                    if (statusSelect && statusSelect.value) {
                        await saveElementProperty(businessObject, 'status', statusSelect.value);
                    }

                    // Save Lifecycle
                    const lifecycleSelect = document.getElementById('prop-end-lifecycle');
                    if (lifecycleSelect && lifecycleSelect.value) {
                        await saveElementProperty(businessObject, 'lifecycle', lifecycleSelect.value);
                    }

                    // Save Commit Changes
                    const commitChangesCb = document.getElementById('prop-end-commit-changes');
                    if (commitChangesCb) {
                        await saveElementProperty(businessObject, 'commitChanges', commitChangesCb.checked ? 'true' : 'false');
                    }
                }

                // Refresh business object after saves
                const elementRegistry = bpmnModeler.get('elementRegistry');
                const refreshedElement = elementRegistry.get(currentElement.id);
                if (refreshedElement) {
                    currentElement = refreshedElement;
                }
            }
        } else {
            // If no currentElement, try to save from DOM elements if they exist
            // This handles the case where properties panel has values but no element is selected
            const dueDateSelect = document.getElementById('prop-task-due-date');
            const unlockObjectCheckbox = document.getElementById('prop-task-unlock-object');
            const statusSelect = document.getElementById('prop-end-status');
            const lifecycleSelect = document.getElementById('prop-end-lifecycle');
            const commitChangesCb = document.getElementById('prop-end-commit-changes');

            // If any of these elements exist, find the corresponding BPMN element and save properties
            // We need to find which element is currently shown in the Properties Panel
            if (dueDateSelect || unlockObjectCheckbox || statusSelect || lifecycleSelect || commitChangesCb) {
                const elementRegistry = bpmnModeler.get('elementRegistry');
                const allElements = elementRegistry.getAll();

                // Try to find Task/UserTask elements if task properties are visible
                if (dueDateSelect || unlockObjectCheckbox) {
                    const tasks = allElements.filter(el =>
                        el.type === 'bpmn:UserTask' || el.type === 'bpmn:Task'
                    );

                    // If there's only one task, save to it. Otherwise, try to match by checking if properties panel values match
                    // For now, save to all tasks that might match (better to save to all than lose data)
                    for (const task of tasks) {
                        const businessObject = task.businessObject;
                        if (!businessObject) continue;

                        // Only save if the value in DOM is different from what's in businessObject
                        // This prevents overwriting with stale values
                        const existingProps = loadElementProperties(businessObject);

                        if (dueDateSelect && dueDateSelect.value && dueDateSelect.value !== existingProps.dueDate) {
                            await saveElementProperty(businessObject, 'dueDate', dueDateSelect.value);
                        }
                        if (unlockObjectCheckbox) {
                            const unlockValue = unlockObjectCheckbox.checked ? 'true' : 'false';
                            if (unlockValue !== existingProps.unlockObject) {
                                await saveElementProperty(businessObject, 'unlockObject', unlockValue);
                            }
                        }
                    }
                }

                // Try to find EndEvent elements if end event properties are visible
                if (statusSelect || lifecycleSelect || commitChangesCb) {
                    const endEvents = allElements.filter(el =>
                        el.type === 'bpmn:EndEvent'
                    );

                    // First, try to get element ID from hidden input in properties panel
                    const elementIdInput = document.getElementById('prop-end-element-id');
                    let targetEndEvent = null;

                    if (elementIdInput && elementIdInput.value) {
                        // Use stored element ID to find the specific end event
                        targetEndEvent = endEvents.find(el => el.id === elementIdInput.value || el.businessObject.id === elementIdInput.value);
                    }

                    // If element ID not found, try to match by comparing DOM values with existing properties
                    // This identifies which end event is currently being edited
                    if (!targetEndEvent && endEvents.length > 1) {
                        const domStatus = statusSelect ? statusSelect.value : '';
                        const domLifecycle = lifecycleSelect ? lifecycleSelect.value : '';
                        const domCommitChanges = commitChangesCb ? (commitChangesCb.checked ? 'true' : 'false') : '';

                        // Find the end event whose existing properties match what's in the DOM
                        // This indicates it's the one being edited
                        for (const endEvent of endEvents) {
                            const businessObject = endEvent.businessObject;
                            if (!businessObject) continue;

                            const existingProps = loadElementProperties(businessObject);
                            
                            // Match if DOM values match existing properties (or if DOM has values and properties match)
                            const statusMatches = !domStatus || domStatus === String(existingProps.status || '');
                            const lifecycleMatches = !domLifecycle || domLifecycle === String(existingProps.lifecycle || '');
                            const commitMatches = !domCommitChanges || domCommitChanges === String(existingProps.commitChanges || 'false');
                            
                            // If at least one property matches and we have DOM values, this is likely the target
                            if ((domStatus && statusMatches) || (domLifecycle && lifecycleMatches) || (domCommitChanges && commitMatches)) {
                                // Additional check: if we have multiple matches, prefer the one with the most matches
                                if (!targetEndEvent) {
                                    targetEndEvent = endEvent;
                                } else {
                                    // Count matches for both candidates
                                    const currentMatches = [
                                        statusMatches && domStatus,
                                        lifecycleMatches && domLifecycle,
                                        commitMatches && domCommitChanges
                                    ].filter(Boolean).length;
                                    
                                    const existingMatches = [
                                        !domStatus || domStatus === String(loadElementProperties(targetEndEvent.businessObject).status || ''),
                                        !domLifecycle || domLifecycle === String(loadElementProperties(targetEndEvent.businessObject).lifecycle || ''),
                                        !domCommitChanges || domCommitChanges === String(loadElementProperties(targetEndEvent.businessObject).commitChanges || 'false')
                                    ].filter(Boolean).length;
                                    
                                    if (currentMatches > existingMatches) {
                                        targetEndEvent = endEvent;
                                    }
                                }
                            }
                        }
                    }

                    // If still no target found and there's only one end event, use it
                    if (!targetEndEvent && endEvents.length === 1) {
                        targetEndEvent = endEvents[0];
                    }

                    // Only save to the identified target end event
                    if (targetEndEvent) {
                        const businessObject = targetEndEvent.businessObject;
                        if (businessObject) {
                            const existingProps = loadElementProperties(businessObject);

                            if (statusSelect && statusSelect.value && statusSelect.value !== existingProps.status) {
                                await saveElementProperty(businessObject, 'status', statusSelect.value);
                            }
                            if (lifecycleSelect && lifecycleSelect.value && lifecycleSelect.value !== existingProps.lifecycle) {
                                await saveElementProperty(businessObject, 'lifecycle', lifecycleSelect.value);
                            }
                            if (commitChangesCb) {
                                const commitValue = commitChangesCb.checked ? 'true' : 'false';
                                if (commitValue !== existingProps.commitChanges) {
                                    await saveElementProperty(businessObject, 'commitChanges', commitValue);
                                }
                            }
                        }
                    } else if (endEvents.length > 0) {
                        // Fallback: if we can't identify the target, don't save to avoid corrupting data
                        console.warn('Could not identify which end event to save to. Please select the end event again.');
                    }
                }
            }
        }
    } catch (err) {
        console.error('Error saving properties from panel:', err);
    }
}

// Save all properties from all elements in the BPMN diagram
async function saveAllPropertiesFromAllElements() {
    if (!bpmnModeler) return;

    try {
        const elementRegistry = bpmnModeler.get('elementRegistry');
        const allElements = elementRegistry.getAll();

        // Get DOM elements for Properties Panel (to capture any unsaved changes)
        // But we'll prioritize businessObject values which are the source of truth
        const dueDateSelect = document.getElementById('prop-task-due-date');
        const unlockObjectCheckbox = document.getElementById('prop-task-unlock-object');
        const statusSelect = document.getElementById('prop-end-status');
        const lifecycleSelect = document.getElementById('prop-end-lifecycle');
        const commitChangesCb = document.getElementById('prop-end-commit-changes');

        // Save properties for all UserTasks/Tasks
        const tasks = allElements.filter(el =>
            el.type === 'bpmn:UserTask' || el.type === 'bpmn:Task'
        );

        for (const task of tasks) {
            const businessObject = task.businessObject;
            if (!businessObject) continue;

            // Read current properties from businessObject (source of truth)
            const properties = loadElementProperties(businessObject);

            // Priority 1: Use DOM value if it exists AND is different from businessObject (unsaved change)
            // Priority 2: Otherwise use businessObject value (already saved)
            if (dueDateSelect && dueDateSelect.value) {
                const domValue = dueDateSelect.value;
                // Only save if different from what's in businessObject (unsaved change)
                if (domValue !== properties.dueDate) {
                    await saveElementProperty(businessObject, 'dueDate', domValue);
                } else if (properties.dueDate) {
                    // Re-save to ensure it's persisted in XML
                    await saveElementProperty(businessObject, 'dueDate', properties.dueDate);
                }
            } else if (properties.dueDate) {
                // No DOM element, but property exists in businessObject - ensure it's saved
                await saveElementProperty(businessObject, 'dueDate', properties.dueDate);
            }

            // Handle unlockObject
            if (unlockObjectCheckbox) {
                const domValue = unlockObjectCheckbox.checked ? 'true' : 'false';
                if (domValue !== properties.unlockObject) {
                    await saveElementProperty(businessObject, 'unlockObject', domValue);
                } else if (properties.unlockObject !== undefined) {
                    await saveElementProperty(businessObject, 'unlockObject', properties.unlockObject);
                }
            } else if (properties.unlockObject !== undefined) {
                await saveElementProperty(businessObject, 'unlockObject', properties.unlockObject);
            }
        }

        // Save properties for all EndEvents
        const endEvents = allElements.filter(el =>
            el.type === 'bpmn:EndEvent'
        );

        // If properties panel is showing end event properties, identify the specific end event
        // Otherwise, save properties for all end events from their business objects
        const elementIdInput = document.getElementById('prop-end-element-id');
        const hasEndEventPropertiesPanel = statusSelect || lifecycleSelect || commitChangesCb;
        
        if (hasEndEventPropertiesPanel && elementIdInput && elementIdInput.value) {
            // Properties panel is showing a specific end event - only save to that one
            const targetEndEvent = endEvents.find(el => el.id === elementIdInput.value || el.businessObject.id === elementIdInput.value);
            
            if (targetEndEvent) {
                const businessObject = targetEndEvent.businessObject;
                if (businessObject) {
                    const properties = loadElementProperties(businessObject);

                    // Use DOM values if they exist and are different (unsaved changes)
                    // Otherwise use businessObject values (already saved)
                    if (statusSelect && statusSelect.value) {
                        const domValue = statusSelect.value;
                        if (domValue !== properties.status) {
                            await saveElementProperty(businessObject, 'status', domValue);
                        } else if (properties.status) {
                            await saveElementProperty(businessObject, 'status', properties.status);
                        }
                    } else if (properties.status) {
                        await saveElementProperty(businessObject, 'status', properties.status);
                    }

                    if (lifecycleSelect && lifecycleSelect.value) {
                        const domValue = lifecycleSelect.value;
                        if (domValue !== properties.lifecycle) {
                            await saveElementProperty(businessObject, 'lifecycle', domValue);
                        } else if (properties.lifecycle) {
                            await saveElementProperty(businessObject, 'lifecycle', properties.lifecycle);
                        }
                    } else if (properties.lifecycle) {
                        await saveElementProperty(businessObject, 'lifecycle', properties.lifecycle);
                    }

                    if (commitChangesCb) {
                        const domValue = commitChangesCb.checked ? 'true' : 'false';
                        if (domValue !== properties.commitChanges) {
                            await saveElementProperty(businessObject, 'commitChanges', domValue);
                        } else if (properties.commitChanges !== undefined) {
                            await saveElementProperty(businessObject, 'commitChanges', properties.commitChanges);
                        }
                    } else if (properties.commitChanges !== undefined) {
                        await saveElementProperty(businessObject, 'commitChanges', properties.commitChanges);
                    }
                }
            }
        } else {
            // No specific end event identified - save properties for all end events from their business objects
            // This preserves existing properties without overwriting with DOM values meant for a different element
            for (const endEvent of endEvents) {
                const businessObject = endEvent.businessObject;
                if (!businessObject) continue;

                // Read current properties from businessObject (source of truth)
                const properties = loadElementProperties(businessObject);

                // Only save properties that exist in businessObject (don't use DOM values as they may be for a different element)
                if (properties.status) {
                    await saveElementProperty(businessObject, 'status', properties.status);
                }
                if (properties.lifecycle) {
                    await saveElementProperty(businessObject, 'lifecycle', properties.lifecycle);
                }
                if (properties.commitChanges !== undefined) {
                    await saveElementProperty(businessObject, 'commitChanges', properties.commitChanges);
                }
            }
        }

        // Force save XML to ensure all properties are persisted
        const result = await bpmnModeler.saveXML({ format: true });
        if (result && result.xml) {
            window.currentBpmnXml = result.xml;
        }
    } catch (err) {
        console.error('Error saving all properties from all elements:', err);
    }
}

async function loadBpmnIntoModeler(xml) {
    if (!xml) return;

    const doLoad = async () => {
        if (!bpmnModeler) return false;

        try {
            const result = await bpmnModeler.importXML(xml);
            const warnings = result?.warnings || [];
            if (warnings.length > 0) console.warn('BPMN import warnings:', warnings);

            try {
                const saveResult = await bpmnModeler.saveXML({ format: true });
                window.currentBpmnXml = saveResult?.xml || xml;
            } catch (saveErr) {
                window.currentBpmnXml = xml;
            }

            setTimeout(() => {
                try {
                    const canvas = bpmnModeler.get('canvas');
                    if (canvas) canvas.zoom('fit-viewport', 'auto');

                    const palette = document.querySelector('.djs-palette');
                    if (palette) {
                        if (!palette.classList.contains('open')) {
                            const toggle = palette.querySelector('.djs-palette-toggle');
                            if (toggle) toggle.click();
                        }
                        palette.style.display = 'block';
                        palette.style.visibility = 'visible';
                    }

                    // Ensure default role assignment after any import (especially new diagrams)
                    applyDefaultRoleToLanes();
                } catch (zoomErr) { }
            }, 300);

            return true;
        } catch (err) {
            console.error('Error importing BPMN XML:', err);
            const container = document.getElementById('bpmnContainer');
            if (container) {
                const warnings = err.warnings || [];
                container.innerHTML = `
                    <div style="padding: 20px; text-align: center; color: #d32f2f;">
                        <i class="fas fa-exclamation-triangle" style="font-size: 48px; margin-bottom: 10px;"></i>
                        <p><strong>Error loading BPMN diagram</strong></p>
                        <p>${err.message || 'Invalid BPMN XML'}</p>
                        ${warnings.length > 0 ? `<p style="font-size: 12px; margin-top: 10px; color: #666;">Warnings: ${JSON.stringify(warnings)}</p>` : ''}
                    </div>
                `;
            }
            return false;
        }
    };

    if (!bpmnModeler) {
        initializeBpmnModeler();
        let retries = 0;
        const maxRetries = 30;
        const checkInterval = setInterval(async () => {
            if (bpmnModeler) {
                clearInterval(checkInterval);
                await doLoad();
            } else if (retries >= maxRetries) {
                clearInterval(checkInterval);
                console.error('Failed to initialize BPMN Modeler after multiple attempts');
            }
            retries++;
        }, 100);
    } else {
        await doLoad();
    }
}

function destroyBpmnModeler() {
    if (bpmnModeler) {
        try {
            bpmnModeler.destroy();
        } catch (error) { }
        bpmnModeler = null;
    }
}

async function downloadBpmnFile() {
    if (!bpmnModeler) return;

    try {
        const result = await bpmnModeler.saveXML({ format: true });
        if (!result || !result.xml) return;

        const blob = new Blob([result.xml], { type: 'application/xml' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'workflow.bpmn';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    } catch (error) {
        console.error('Error downloading BPMN file:', error);
    }
}

function uploadBpmnFile() {
    if (!bpmnModeler) return;

    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.bpmn,.xml';
    input.style.display = 'none';

    input.onchange = async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        try {
            const text = await file.text();
            await loadBpmnIntoModeler(text);
        } catch (error) {
            console.error('Error uploading BPMN file:', error);
        }

        input.value = '';
    };

    document.body.appendChild(input);
    input.click();
    document.body.removeChild(input);
}

// Note: Custom palette provider removed - using default palette with all entries
// Custom buttons (upload/download) are added via addPaletteButtons() after initialization

function addPaletteButtons() {
    const container = document.getElementById('bpmnContainer');
    if (!container) return;

    const palette = container.querySelector('.djs-palette');
    if (!palette) return;

    const entriesContainer = palette.querySelector('.djs-palette-entries');
    if (!entriesContainer) return;

    if (entriesContainer.querySelector('.palette-download-button') ||
        entriesContainer.querySelector('.palette-upload-button')) {
        return;
    }

    const separator = document.createElement('div');
    separator.className = 'separator';
    entriesContainer.appendChild(separator);

    const downloadButton = document.createElement('div');
    downloadButton.className = 'entry palette-download-button';
    downloadButton.title = 'Download BPMN';
    downloadButton.innerHTML = '<i class="fas fa-download"></i>';
    downloadButton.addEventListener('click', (e) => {
        e.stopPropagation();
        downloadBpmnFile();
    });
    entriesContainer.appendChild(downloadButton);

    const uploadButton = document.createElement('div');
    uploadButton.className = 'entry palette-upload-button';
    uploadButton.title = 'Upload BPMN';
    uploadButton.innerHTML = '<i class="fas fa-upload"></i>';
    uploadButton.addEventListener('click', (e) => {
        e.stopPropagation();
        uploadBpmnFile();
    });
    entriesContainer.appendChild(uploadButton);
}

function initializePropertiesPanel() {
    // إنشاء panel مرة واحدة فقط - منع destroy/recreate
    if (propertiesPanelElement) return;

    const panel = document.getElementById('js-properties-panel');
    if (!panel || propertiesPanelInitialized) return;

    panel.innerHTML = `
        <div class="bpp-properties-panel">
            <div class="bpp-properties">
                <div class="bpp-properties-header properties-panel-drag-handle">
                    <div class="label" data-label-id="">Properties</div>
                    <i class="fas fa-grip-vertical" style="margin-left: auto; color: #999; cursor: move;"></i>
                </div>
                <div class="bpp-properties-tab-bar">
                    <ul class="bpp-properties-tabs-links">
                        <li class="bpp-properties-tab-link bpp-active">
                            <a href="#" data-tab-target="general">General</a>
                        </li>
                    </ul>
                </div>
                <div class="bpp-properties-tabs-container">
                    <div class="bpp-properties-tab bpp-active" data-tab="general" id="propertiesTabContent">
                        <div class="bpp-properties-group" data-group="general">
                            <div style="padding: 15px; color: #666; text-align: center;">
                                Select an element to view properties
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    propertiesPanelElement = panel;
    propertiesPanelInitialized = true;

    // إضافة drag functionality للـ properties panel
    initializePropertiesPanelDrag();

    // إضافة event listeners مع debounce لمنع destroy/recreate
    if (bpmnModeler) {
        const eventBus = bpmnModeler.get('eventBus');
        if (eventBus) {
            // Handle selection.changed - تحديث فقط، لا إعادة إنشاء
            eventBus.on('selection.changed', (event) => {
                clearTimeout(selectionChangeTimeout);
                selectionChangeTimeout = setTimeout(() => {
                    const element = event.newSelection && event.newSelection.length > 0 ? event.newSelection[0] : null;
                    if (element) {
                        updatePropertiesPanelContent(element); // تحديث فقط
                    } else {
                        clearPropertiesPanel(); // مسح المحتوى فقط
                    }
                }, 50);
            });

            // Handle element.changed - تحديث فقط
            eventBus.on('element.changed', (event) => {
                if (currentElement && event.element && event.element.id === currentElement.id) {
                    updatePropertiesPanelContent(event.element);
                }
            });

            // Handle root.set - لا تفعل شيء (منع reinit)
            eventBus.on('root.set', () => {
                // لا تفعل شيء - panel موجود بالفعل
            });
        }
    }
}

function initializePropertiesPanelDrag() {
    const panel = document.getElementById('js-properties-panel');
    if (!panel) return;

    const dragHandle = panel.querySelector('.properties-panel-drag-handle');
    if (!dragHandle) return;

    let isDragging = false;
    let startX = 0;
    let startY = 0;
    let initialLeft = 0;
    let initialTop = 0;

    // Store initial position (right: 20px, top: 20px)
    let panelLeft = null;
    let panelTop = null;
    let hasBeenDragged = false;

    // Get initial position
    function getInitialPosition() {
        const parent = panel.parentElement;
        if (parent && !hasBeenDragged) {
            const parentRect = parent.getBoundingClientRect();
            const panelRect = panel.getBoundingClientRect();
            // Calculate from right: 20px, top: 20px
            panelLeft = parentRect.width - panelRect.width - 20;
            panelTop = 20;
        }
    }

    getInitialPosition();

    dragHandle.addEventListener('mousedown', dragStart);
    document.addEventListener('mousemove', drag);
    document.addEventListener('mouseup', dragEnd);

    function dragStart(e) {
        if (e.button !== 0) return; // Only left mouse button

        // Check if clicking on drag handle
        if (e.target === dragHandle || dragHandle.contains(e.target)) {
            e.preventDefault();
            e.stopPropagation();

            isDragging = true;
            panel.classList.add('dragging');

            // Get current position
            const rect = panel.getBoundingClientRect();
            const parentRect = panel.parentElement.getBoundingClientRect();

            // If panel hasn't been dragged, use initial position
            if (!hasBeenDragged) {
                panelLeft = parentRect.width - rect.width - 20;
                panelTop = 20;
                // Set initial position using left/top instead of right/top
                panel.style.right = 'auto';
                panel.style.left = panelLeft + 'px';
                panel.style.top = panelTop + 'px';
                panel.style.transform = 'none';
            } else {
                // Get current left/top from computed style
                const computedStyle = window.getComputedStyle(panel);
                panelLeft = parseInt(computedStyle.left) || 0;
                panelTop = parseInt(computedStyle.top) || 0;
            }

            startX = e.clientX;
            startY = e.clientY;
            initialLeft = panelLeft;
            initialTop = panelTop;
        }
    }

    function drag(e) {
        if (isDragging) {
            e.preventDefault();
            e.stopPropagation();

            const deltaX = e.clientX - startX;
            const deltaY = e.clientY - startY;

            const newLeft = initialLeft + deltaX;
            const newTop = initialTop + deltaY;

            // Constrain to parent bounds
            const parent = panel.parentElement;
            if (parent) {
                const parentRect = parent.getBoundingClientRect();
                const panelRect = panel.getBoundingClientRect();
                const maxLeft = parentRect.width - panelRect.width;
                const maxTop = parentRect.height - panelRect.height;

                panelLeft = Math.max(0, Math.min(newLeft, maxLeft));
                panelTop = Math.max(0, Math.min(newTop, maxTop));
            } else {
                panelLeft = newLeft;
                panelTop = newTop;
            }

            // Update position using left/top
            panel.style.right = 'auto';
            panel.style.left = panelLeft + 'px';
            panel.style.top = panelTop + 'px';
            panel.style.transform = 'none';
            panel.style.bottom = 'auto';

            hasBeenDragged = true;
        }
    }

    function dragEnd(e) {
        if (isDragging) {
            isDragging = false;
            panel.classList.remove('dragging');
        }
    }
}

function updatePropertiesPanelContent(element) {
    // تحديث المحتوى فقط، لا إعادة إنشاء DOM
    loadPropertiesForElement(element);
}

async function loadPropertiesForElement(element) {
    if (!element || !bpmnModeler) return;

    currentElement = element;
    const elementRegistry = bpmnModeler.get('elementRegistry');

    // Always get the latest element from registry
    const latestElement = elementRegistry.get(element.id) || element;
    currentElement = latestElement;

    const elementType = latestElement.type;
    let businessObject = latestElement.businessObject;
    if (!businessObject) return;

    // Force refresh of business object to get latest properties
    try {
        const saveResult = await bpmnModeler.saveXML({ format: true });
        if (saveResult && saveResult.xml) {
            window.currentBpmnXml = saveResult.xml;
        }

        // Get the element again after save to ensure we have the latest data
        const refreshedElement = elementRegistry.get(element.id);
        if (refreshedElement) {
            currentElement = refreshedElement;
            businessObject = refreshedElement.businessObject;
        }
    } catch (err) {
        console.warn('Could not refresh element data:', err);
    }

    const properties = loadElementProperties(businessObject);

    try {
        if (elementType === 'bpmn:Lane') {
            await showRoleProperties(businessObject, properties);
        } else if (elementType === 'bpmn:Participant') {
            await showParticipantProperties(businessObject, properties);
        } else if (elementType === 'bpmn:UserTask' || elementType === 'bpmn:Task') {
            await showUserTaskProperties(businessObject, properties);
        } else if (elementType === 'bpmn:EndEvent') {
            await showEndEventProperties(businessObject, properties);
        } else if (elementType === 'bpmn:SequenceFlow') {
            showSequenceFlowProperties(businessObject, properties);
        } else {
            showDefaultProperties(businessObject, properties);
        }
    } catch (error) {
        console.error('Error showing properties:', error);
    }
}

async function showRoleProperties(businessObject, properties) {
    const tabContent = document.getElementById('propertiesTabContent');
    if (!tabContent) return;

    let rolesList = [];
    if (currentModuleId) {
        rolesList = await loadRolesForModule(currentModuleId);
    }

    // Load roleName from properties - this should come from saved BPMN
    // CRITICAL: Always prioritize saved roleName from BPMN over any default
    let roleName = properties.roleName || '';
    
    // Also check lane name as fallback, but only if it matches a valid role
    const laneName = businessObject.name || '';
    if (!roleName && laneName && rolesList.some(r => r.name === laneName)) {
        roleName = laneName;
    }
    
    const description = properties.description || businessObject.documentation?.[0]?.text || '';

    // IMPORTANT: Do NOT auto-select role if workflow is being edited (has existing BPMN)
    // Only auto-select for truly new workflows
    let selectedValue = roleName || '';
    
    // Check if we're editing an existing workflow (not creating a new one)
    const isEditingExistingWorkflow = currentWorkflow && currentWorkflow.id;
    
    // Only auto-select first role if:
    // 1. No role is saved in BPMN (selectedValue is empty)
    // 2. This is NOT an existing workflow being edited
    // 3. Roles list is available
    if (!selectedValue && !isEditingExistingWorkflow && rolesList.length > 0) {
        // Only auto-select for new workflows, never override existing saved roles
        selectedValue = rolesList[0].name;
        // Trigger save immediately for the default value
        if (businessObject) {
            saveElementProperty(businessObject, 'roleName', selectedValue);
            saveElementProperty(businessObject, 'isRequester', selectedValue === 'Requestor' ? 'true' : 'false');

            // Update visual label immediately
            if (currentElement && bpmnModeler) {
                try {
                    const modeling = bpmnModeler.get('modeling');
                    modeling.updateProperties(currentElement, { name: selectedValue });
                    modeling.updateLabel(currentElement, selectedValue);
                } catch (error) { }
            }
        }
    } else if (!selectedValue && laneName) {
        // If no roleName but lane has a name, use lane name as selected value (but don't save it automatically)
        selectedValue = laneName;
    }

    tabContent.innerHTML = `
        <div class="bpp-properties-group" data-group="general">
            <span class="group-label">Role Properties</span>
            <div class="bpp-properties-entry bpp-textfield" data-entry="roleName">
                <label for="prop-role-name">Name</label>
                <div class="bpp-field-wrapper">
                    <select id="prop-role-name" class="bpp-select" style="width: 100%; padding: 5px;">
                        <option value="">Select Role...</option>
                        ${rolesList.map(role => `<option value="${role.name}" ${selectedValue === role.name ? 'selected' : ''}>${role.name}</option>`).join('')}
                        <option value="Requestor" ${selectedValue === 'Requestor' ? 'selected' : ''}>Requestor</option>
                    </select>
                </div>
            </div>
            <div class="bpp-properties-entry bpp-textarea" data-entry="description">
                <label for="prop-role-description">Description</label>
                <div class="bpp-field-wrapper">
                    <textarea id="prop-role-description" placeholder="Enter Description" rows="3" style="width: 100%; padding: 5px;">${description}</textarea>
                </div>
            </div>
        </div>
    `;

    const roleNameSelect = document.getElementById('prop-role-name');
    if (roleNameSelect) {
        if (selectedValue) {
            roleNameSelect.value = selectedValue;
        }

        roleNameSelect.addEventListener('change', async (e) => {
            const selectedRole = e.target.value;
            if (currentElement && currentElement.businessObject) {
                businessObject = currentElement.businessObject;
            }
            await saveElementProperty(businessObject, 'roleName', selectedRole);

            // حفظ isRequester إذا كان Requestor
            if (selectedRole === 'Requestor') {
                await saveElementProperty(businessObject, 'isRequester', 'true');
            } else {
                await saveElementProperty(businessObject, 'isRequester', 'false');
            }

            if (currentElement && bpmnModeler) {
                try {
                    const modeling = bpmnModeler.get('modeling');
                    modeling.updateProperties(currentElement, { name: selectedRole || '' });
                    if (selectedRole) modeling.updateLabel(currentElement, selectedRole);
                    if (currentElement.businessObject) businessObject = currentElement.businessObject;
                } catch (error) { }
            }
        });
    }

    document.getElementById('prop-role-description').addEventListener('blur', async (e) => {
        if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
        await saveElementDescription(businessObject, e.target.value);
        if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
    });
}

async function showUserTaskProperties(businessObject, properties) {
    const tabContent = document.getElementById('propertiesTabContent');
    if (!tabContent) return;

    const name = businessObject.name || '';
    const description = businessObject.documentation?.[0]?.text || '';
    const dueDate = (properties.dueDate && properties.dueDate !== '') ? String(properties.dueDate) : '';
    const unlockObject = properties.unlockObject === 'true' || properties.unlockObject === true || properties.unlockObject === '1' || properties.unlockObject === 1;

    tabContent.innerHTML = `
        <div class="bpp-properties-group" data-group="general">
            <span class="group-label"></span>
            <div class="bpp-properties-entry bpp-textfield" data-entry="name">
                <label for="prop-task-name">Name</label>
                <div class="bpp-field-wrapper">
                    <input id="prop-task-name" type="text" placeholder="Enter Name" value="${name}" style="width: 100%; padding: 5px;">
                </div>
            </div>
            <div class="bpp-properties-entry bpp-textarea" data-entry="description">
                <label for="prop-task-description">Description</label>
                <div class="bpp-field-wrapper">
                    <textarea id="prop-task-description" placeholder="Enter Description" name="documentation" rows="3" style="width: 100%; padding: 5px;">${description}</textarea>
                </div>
            </div>
            <div class="bpp-properties-entry bpp-select" data-entry="dueDate">
                <label for="prop-task-due-date">Due Date</label>
                <div class="bpp-field-wrapper">
                    <select id="prop-task-due-date" class="bpp-select" style="width: 100%; padding: 5px;">
                        <option value="">Select a due date</option>
                        ${Array.from({ length: 30 }, (_, i) => {
        const days = i + 1;
        return `<option value="${days}" ${dueDate === String(days) ? 'selected' : ''}>${days} day${days > 1 ? 's' : ''}</option>`;
    }).join('')}
                    </select>
                </div>
            </div>
            <div class="bpp-properties-entry bpp-checkbox" data-entry="unlockObject">
                <label for="prop-task-unlock-object" style="display: flex; align-items: center; gap: 8px; cursor: pointer; font-weight: normal;">
                    <input id="prop-task-unlock-object" type="checkbox" ${unlockObject ? 'checked' : ''} style="width: 18px; height: 18px; cursor: pointer; margin: 0;">
                    <span>Unlock Object</span>
                </label>
            </div>
        </div>
    `;

    const nameInput = document.getElementById('prop-task-name');
    if (nameInput) {
        nameInput.addEventListener('blur', (e) => {
            if (!currentElement) return;
            const modeling = bpmnModeler.get('modeling');
            modeling.updateLabel(currentElement, e.target.value);
            modeling.updateProperties(currentElement, { name: e.target.value });
        });
    }

    const descriptionField = document.getElementById('prop-task-description');
    if (descriptionField) {
        descriptionField.addEventListener('blur', async (e) => {
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
            await saveElementDescription(businessObject, e.target.value);
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
        });
    }

    const dueDateSelect = document.getElementById('prop-task-due-date');
    if (dueDateSelect) {
        dueDateSelect.addEventListener('change', async (e) => {
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
            await saveElementProperty(businessObject, 'dueDate', e.target.value);
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
        });
    }

    const unlockObjectCheckbox = document.getElementById('prop-task-unlock-object');
    if (unlockObjectCheckbox) {
        unlockObjectCheckbox.addEventListener('change', async (e) => {
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
            await saveElementProperty(businessObject, 'unlockObject', e.target.checked ? 'true' : 'false');
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
        });
    }
}

async function showEndEventProperties(businessObject, properties) {
    const tabContent = document.getElementById('propertiesTabContent');
    if (!tabContent) return;

    const name = businessObject.name || '';
    const description = businessObject.documentation?.[0]?.text || '';
    const status = (properties.status && properties.status !== '') ? String(properties.status) : '';
    const lifecycle = (properties.lifecycle && properties.lifecycle !== '') ? String(properties.lifecycle) : '';
    const commitChanges = properties.commitChanges === 'true' || properties.commitChanges === true || properties.commitChanges === '1' || properties.commitChanges === 1;

    const statuses = await loadStatuses();
    let lifecycles = [];
    if (currentModuleName) {
        lifecycles = await loadLifecycleForModule(currentModuleName);
    }

    // Store element ID in the properties panel for tracking
    const elementId = businessObject.id || (currentElement ? currentElement.id : '');
    
    tabContent.innerHTML = `
        <div class="bpp-properties-group" data-group="general" data-element-id="${elementId}">
            <span class="group-label">End Event Properties</span>
            <input type="hidden" id="prop-end-element-id" value="${elementId}">
            <div class="bpp-properties-entry bpp-textfield" data-entry="name">
                <label for="prop-end-name">Name</label>
                <div class="bpp-field-wrapper">
                    <input id="prop-end-name" type="text" placeholder="Enter Name" value="${name}" style="width: 100%; padding: 5px;">
                </div>
            </div>
            <div class="bpp-properties-entry bpp-textarea" data-entry="description">
                <label for="prop-end-description">Description</label>
                <div class="bpp-field-wrapper">
                    <textarea id="prop-end-description" placeholder="Enter Description" rows="3" style="width: 100%; padding: 5px;">${description}</textarea>
                </div>
            </div>
            <div class="bpp-properties-entry bpp-select" data-entry="status">
                <label for="prop-end-status">Status</label>
                <div class="bpp-field-wrapper">
                    <select id="prop-end-status" class="bpp-select" style="width: 100%; padding: 5px;">
                        <option value="">Select Status...</option>
                        ${statuses.map(s => {
        const statusName = s.primaryname || s.primaryName || s.name || '';
        const statusId = s.id || '';
        return `<option value="${statusId}" ${status === String(statusId) ? 'selected' : ''}>${statusName}</option>`;
    }).join('')}
                    </select>
                </div>
            </div>
            <div class="bpp-properties-entry bpp-select" data-entry="lifecycle">
                <label for="prop-end-lifecycle">Lifecycle</label>
                <div class="bpp-field-wrapper">
                    <select id="prop-end-lifecycle" class="bpp-select" style="width: 100%; padding: 5px;">
                        <option value="">Select Lifecycle...</option>
                        ${lifecycles.map(l => {
        const lifecycleName = l.primaryname || l.primaryName || l.name || '';
        const lifecycleId = l.id || '';
        return `<option value="${lifecycleId}" ${lifecycle === String(lifecycleId) ? 'selected' : ''}>${lifecycleName}</option>`;
    }).join('')}
                    </select>
                </div>
            </div>
            <div class="bpp-properties-entry bpp-checkbox" data-entry="commitChanges">
                <label for="prop-end-commit-changes" style="display: flex; align-items: center; gap: 8px; cursor: pointer; font-weight: normal;">
                    <input id="prop-end-commit-changes" type="checkbox" ${commitChanges ? 'checked' : ''} style="width: 18px; height: 18px; cursor: pointer; margin: 0;">
                    <span>Commit Changes</span>
                </label>
                <div style="font-size: 0.85em; color: #666; margin-top: 4px; margin-left: 26px;">
                    When reached, apply all pending changes to the live data.
                </div>
            </div>
        </div>
    `;

    document.getElementById('prop-end-name').addEventListener('blur', (e) => {
        if (!currentElement) return;
        const modeling = bpmnModeler.get('modeling');
        modeling.updateLabel(currentElement, e.target.value);
        modeling.updateProperties(currentElement, { name: e.target.value });
    });

    document.getElementById('prop-end-description').addEventListener('blur', async (e) => {
        if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
        await saveElementDescription(businessObject, e.target.value);
        if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
    });

    document.getElementById('prop-end-status').addEventListener('change', async (e) => {
        if (!currentElement) return;
        const selectedValue = e.target.value;
        // Save the ID (not the name) - the value is already the ID from the select option
        await saveElementProperty(currentElement.businessObject, 'status', selectedValue);
        // Update the display to show name instead of ID
        if (selectedValue) {
            const selectedStatus = statuses.find(s => String(s.id || s.ID) === selectedValue);
            if (selectedStatus) {
                const statusName = selectedStatus.primaryname || selectedStatus.primaryName || selectedStatus.name || '';
                // Update the select display text (but keep value as ID)
                e.target.options[e.target.selectedIndex].text = statusName;
            }
        }
    });

    document.getElementById('prop-end-lifecycle').addEventListener('change', async (e) => {
        if (!currentElement) return;
        const selectedValue = e.target.value;
        // Save the ID (not the name) - the value is already the ID from the select option
        await saveElementProperty(currentElement.businessObject, 'lifecycle', selectedValue);
        // Update the display to show name instead of ID
        if (selectedValue) {
            const selectedLifecycle = lifecycles.find(l => String(l.id || l.ID) === selectedValue);
            if (selectedLifecycle) {
                const lifecycleName = selectedLifecycle.primaryname || selectedLifecycle.primaryName || selectedLifecycle.name || '';
                // Update the select display text (but keep value as ID)
                e.target.options[e.target.selectedIndex].text = lifecycleName;
            }
        }
    });

    const commitChangesCb = document.getElementById('prop-end-commit-changes');
    if (commitChangesCb) {
        commitChangesCb.addEventListener('change', async (e) => {
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
            await saveElementProperty(businessObject, 'commitChanges', e.target.checked ? 'true' : 'false');
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
        });
    }
}

function showDefaultProperties(businessObject, properties) {
    const tabContent = document.getElementById('propertiesTabContent');
    if (!tabContent) return;

    const name = businessObject.name || '';
    const description = businessObject.documentation?.[0]?.text || '';

    tabContent.innerHTML = `
        <div class="bpp-properties-group" data-group="general">
            <span class="group-label">General Properties</span>
            <div class="bpp-properties-entry bpp-textfield" data-entry="name">
                <label for="prop-default-name">Name</label>
                <div class="bpp-field-wrapper">
                    <input id="prop-default-name" type="text" placeholder="Enter Name" value="${name}" style="width: 100%; padding: 5px;">
                </div>
            </div>
            <div class="bpp-properties-entry bpp-textarea" data-entry="description">
                <label for="prop-default-description">Description</label>
                <div class="bpp-field-wrapper">
                    <textarea id="prop-default-description" placeholder="Enter Description" rows="3" style="width: 100%; padding: 5px;">${description}</textarea>
                </div>
            </div>
        </div>
    `;

    document.getElementById('prop-default-name').addEventListener('blur', (e) => {
        if (!currentElement) return;
        const modeling = bpmnModeler.get('modeling');
        modeling.updateLabel(currentElement, e.target.value);
        modeling.updateProperties(currentElement, { name: e.target.value });
    });

    document.getElementById('prop-default-description').addEventListener('blur', async (e) => {
        if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
        await saveElementDescription(businessObject, e.target.value);
        if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
    });
}

// Explicit sequence flow properties (text fields only)
function showSequenceFlowProperties(businessObject, properties) {
    const tabContent = document.getElementById('propertiesTabContent');
    if (!tabContent) return;

    const name = businessObject.name || '';
    const description = businessObject.documentation?.[0]?.text || '';

    tabContent.innerHTML = `
        <div class="bpp-properties-group" data-group="general">
            <span class="group-label">Connection Properties</span>
            <div class="bpp-properties-entry bpp-textfield" data-entry="name">
                <label for="prop-seq-name">Name</label>
                <div class="bpp-field-wrapper">
                    <input id="prop-seq-name" type="text" placeholder="Enter Name" value="${name}" style="width: 100%; padding: 5px;">
                </div>
            </div>
            <div class="bpp-properties-entry bpp-textarea" data-entry="description">
                <label for="prop-seq-description">Description</label>
                <div class="bpp-field-wrapper">
                    <textarea id="prop-seq-description" placeholder="Enter Description" rows="3" style="width: 100%; padding: 5px;">${description}</textarea>
                </div>
            </div>
        </div>
    `;

    const nameInput = document.getElementById('prop-seq-name');
    if (nameInput) {
        nameInput.addEventListener('blur', (e) => {
            if (!currentElement) return;
            const modeling = bpmnModeler.get('modeling');
            modeling.updateLabel(currentElement, e.target.value);
            modeling.updateProperties(currentElement, { name: e.target.value });
        });
    }

    const descriptionField = document.getElementById('prop-seq-description');
    if (descriptionField) {
        descriptionField.addEventListener('blur', async (e) => {
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
            await saveElementDescription(businessObject, e.target.value);
            if (currentElement && currentElement.businessObject) businessObject = currentElement.businessObject;
        });
    }
}

function clearPropertiesPanel() {
    currentElement = null;
    const tabContent = document.getElementById('propertiesTabContent');
    if (tabContent) {
        tabContent.innerHTML = `
            <div class="bpp-properties-group" data-group="general">
                <div style="padding: 15px; color: #666; text-align: center;">
                    Select an element to view properties
                </div>
            </div>
        `;
    }
}

async function loadRolesForModule(moduleId) {
    try {
        const response = await fetch(`/api/workflow/roles?module=${moduleId}`);
        if (!response.ok) throw new Error('Failed to load roles');
        const roles = await response.json();
        return Array.isArray(roles) ? roles : [];
    } catch (error) {
        console.error('Error loading roles:', error);
        return [];
    }
}

async function loadStatuses() {
    try {
        const response = await fetch('/api/status/list');
        if (!response.ok) throw new Error('Failed to load statuses');
        const data = await response.json();
        return Array.isArray(data) ? data : (data.data || []);
    } catch (error) {
        console.error('Error loading statuses:', error);
        return [];
    }
}

// Helper function to normalize lifecycle data
function normalizeLifecycles(data) {
    let lifecycles = [];

    if (data && typeof data === 'object' && data.data && Array.isArray(data.data)) {
        lifecycles = data.data;
    } else if (Array.isArray(data)) {
        lifecycles = data;
    } else {
        return [];
    }

    return lifecycles.map(item => {
        if (item.ID !== undefined && item.id === undefined) item.id = item.ID;
        if (item.PrimaryName !== undefined) {
            if (item.primaryname === undefined) item.primaryname = item.PrimaryName;
            if (item.primaryName === undefined) item.primaryName = item.PrimaryName;
            if (item.name === undefined) item.name = item.PrimaryName;
        }
        if (item.name !== undefined && item.primaryname === undefined) {
            item.primaryname = item.name;
            item.primaryName = item.name;
        }
        return item;
    });
}

async function loadLifecycleForModule(moduleName) {
    if (!moduleName) return [];

    let moduleNameLower = moduleName.toLowerCase().trim().replace(/\s+/g, '_');
    const endpoints = [
        `/api/${moduleNameLower}/lifecycle/list`,
        `/api/${moduleNameLower.replace(/_/g, '')}/lifecycle/list`,
        `/api/${moduleNameLower.replace('_', '-')}/lifecycle/list`
    ];

    if (moduleNameLower === 'data_set' || moduleNameLower === 'dataset' || moduleName === 'Data Sets') {
        endpoints.unshift('/api/lifecycle/list');
    }
    if (moduleNameLower === 'business_area' || moduleNameLower === 'businessarea' || moduleName === 'Business Area') {
        endpoints.unshift('/api/business-area-lifecycles');
    }
    if (moduleNameLower === 'committee' || moduleName === 'Committee') {
        endpoints.unshift('/api/committee/lookup?type=lifecycle');
    }
    if (moduleNameLower === 'capability' || moduleName === 'Capability') {
        endpoints.unshift('/api/capability-lifecycles/dropdown');
    }
    if (moduleNameLower === 'client' || moduleName === 'Client') {
        endpoints.unshift('/api/client/lifecycle-list');
    }
    if (moduleNameLower === 'process' || moduleName === 'Process') {
        endpoints.unshift('/api/process/lifecycle/list');
    }

    try {
        const response = await Promise.any(
            endpoints.map(endpoint =>
                fetch(endpoint).then(res => res.ok ? res.json() : Promise.reject())
            )
        );
        return normalizeLifecycles(response);
    } catch (error) {
        return [];
    }
}

// Set lane/participant name to "Roles" if empty, but do NOT auto-select role properties
async function applyDefaultRoleToLanes() {
    if (!bpmnModeler) return;

    try {
        const elementRegistry = bpmnModeler.get('elementRegistry');
        const modeling = bpmnModeler.get('modeling');

        // Target participants only (outer container)
        const laneElements = elementRegistry.filter(el =>
            el && (el.type === 'bpmn:Participant')
        );

        for (const lane of laneElements) {
            if (!lane.businessObject) continue;

            const hasName = lane.businessObject.name && lane.businessObject.name.trim() !== '';
            if (!hasName) {
                try {
                    modeling.updateProperties(lane, { name: 'Roles' });
                    modeling.updateLabel(lane, 'Roles');
                } catch (e) { }
            }
        }
    } catch (err) {
        console.error('Error applying default role to lanes:', err);
    }
}

// Participant (outer pool) properties - keep text inputs
async function showParticipantProperties(businessObject, properties) {
    const tabContent = document.getElementById('propertiesTabContent');
    if (!tabContent) return;

    // participant name
    const name = businessObject.name || 'Roles';

    // process reference (if any)
    const processRef = businessObject.processRef || null;
    const processElement = processRef ? bpmnModeler.get('elementRegistry').get(processRef.id) : null;
    const processName = processRef?.name || '';

    const description = businessObject.documentation?.[0]?.text || '';
    const processDocumentation = processRef?.documentation?.[0]?.text || '';

    tabContent.innerHTML = `
        <div class="bpp-properties-group" data-group="general">
            <span class="group-label">General</span>
            <div class="bpp-properties-entry bpp-textfield" data-entry="participantName">
                <label for="prop-participant-name">Name</label>
                <div class="bpp-field-wrapper">
                    <input id="prop-participant-name" type="text" value="${name}" style="width: 100%; padding: 5px;">
                </div>
            </div>
            <div class="bpp-properties-entry bpp-textfield" data-entry="processName">
                <label for="prop-participant-process-name">Process Name</label>
                <div class="bpp-field-wrapper">
                    <input id="prop-participant-process-name" type="text" placeholder="Enter Process Name" value="${processName}" style="width: 100%; padding: 5px;">
                </div>
            </div>
            <div class="bpp-properties-entry bpp-textarea" data-entry="description">
                <label for="prop-participant-description">Description</label>
                <div class="bpp-field-wrapper">
                    <textarea id="prop-participant-description" placeholder="Enter Description" rows="3" style="width: 100%; padding: 5px;">${description}</textarea>
                </div>
            </div>
            <div class="bpp-properties-entry bpp-textarea" data-entry="processDocumentation">
                <label for="prop-participant-process-doc">Process Documentation</label>
                <div class="bpp-field-wrapper">
                    <textarea id="prop-participant-process-doc" placeholder="Enter Process Documentation" rows="3" style="width: 100%; padding: 5px;">${processDocumentation}</textarea>
                </div>
            </div>
        </div>
    `;

    const nameInput = document.getElementById('prop-participant-name');
    if (nameInput) {
        nameInput.addEventListener('blur', (e) => {
            if (!currentElement) return;
            const modeling = bpmnModeler.get('modeling');
            modeling.updateLabel(currentElement, e.target.value || 'Roles');
            modeling.updateProperties(currentElement, { name: e.target.value || 'Roles' });
            saveCurrentBpmnXml();
        });
    }

    const procNameInput = document.getElementById('prop-participant-process-name');
    if (procNameInput) {
        procNameInput.addEventListener('blur', (e) => {
            const modeling = bpmnModeler.get('modeling');
            if (processElement) {
                modeling.updateProperties(processElement, { name: e.target.value || '' });
            } else if (processRef) {
                processRef.name = e.target.value || '';
            }
            saveCurrentBpmnXml();
        });
    }

    const descField = document.getElementById('prop-participant-description');
    if (descField) {
        descField.addEventListener('blur', async (e) => {
            await saveElementDescription(businessObject, e.target.value);
        });
    }

    const procDocField = document.getElementById('prop-participant-process-doc');
    if (procDocField) {
        procDocField.addEventListener('blur', async (e) => {
            if (processElement) {
                await saveElementDescription(processElement.businessObject || processElement, e.target.value);
            } else if (processRef) {
                // Manually set documentation on processRef when no element
                try {
                    const moddle = bpmnModeler.get('moddle');
                    const doc = moddle.create('bpmn:Documentation', { text: e.target.value || '' });
                    processRef.documentation = [doc];
                    await saveCurrentBpmnXml();
                } catch (err) {
                    console.error('Error saving process documentation:', err);
                }
            }
        });
    }
}

function loadElementProperties(businessObject) {
    const properties = {};
    if (!businessObject) return properties;

    const toArray = (val) => {
        if (!val) return [];
        if (Array.isArray(val)) return val;
        if (typeof val === 'object') {
            if (Array.isArray(val.$children)) return val.$children;
            if (Array.isArray(val.values)) return val.values;
            return Object.values(val);
        }
        return [];
    };

    // Try to get properties from extensionElements
    if (businessObject.extensionElements) {
        const extValues = businessObject.extensionElements.values;
        const extensionElements = toArray(extValues);

        extensionElements.forEach(ext => {
            if (!ext) return;

            // Check for camunda:Properties container
            if (ext.$type === 'camunda:Properties' || ext.$type === 'Properties' ||
                (ext.$type && ext.$type.includes('Properties') && ext.values)) {
                const props = toArray(ext.values);
                props.forEach(prop => {
                    if (prop && prop.name && prop.value !== undefined && prop.value !== null) {
                        // Convert to string for consistency
                        properties[prop.name] = String(prop.value);
                    }
                });
            }

            // Check for direct property elements (fallback)
            if (ext.$type && (ext.$type.includes('Property') || ext.$type === 'camunda:Property') &&
                ext.name && ext.value !== undefined && ext.value !== null) {
                properties[ext.name] = String(ext.value);
            }

            // Fallback if properties are inside $children
            if (ext.$children && Array.isArray(ext.$children)) {
                ext.$children.forEach(child => {
                    if (child && child.name && child.value !== undefined && child.value !== null) {
                        properties[child.name] = String(child.value);
                    }
                });
            }
        });
    }

    // Also check direct attributes (fallback)
    if (businessObject.$attrs) {
        Object.keys(businessObject.$attrs).forEach(key => {
            if (key.startsWith('camunda:')) {
                const propName = key.replace('camunda:', '');
                const value = businessObject.$attrs[key];
                if (value !== undefined && value !== null) {
                    properties[propName] = String(value);
                }
            }
        });
    }

    return properties;
}

// Helper function to fix properties serialization issues in XML
// This fixes cases where moddle serializes properties as [object Object] instead of proper XML elements
async function fixPropertiesSerialization(xml) {
    if (!xml || !bpmnModeler) return xml;
    
    try {
        const elementRegistry = bpmnModeler.get('elementRegistry');
        const allElements = elementRegistry.getAll();
        
        // Fix properties for all element types that can have properties
        const elementsToFix = allElements.filter(el => {
            const type = el.type;
            return type === 'bpmn:EndEvent' || 
                   type === 'bpmn:UserTask' || 
                   type === 'bpmn:Task' ||
                   type === 'bpmn:ServiceTask' ||
                   type === 'bpmn:ScriptTask' ||
                   type === 'bpmn:BusinessRuleTask' ||
                   type === 'bpmn:ManualTask' ||
                   type === 'bpmn:ReceiveTask' ||
                   type === 'bpmn:SendTask';
        });
        
        let fixedXml = xml;
        let hasFixes = false;
        let fixedCount = 0;
        
        for (const element of elementsToFix) {
            const elementId = element.id;
            const elementType = element.type;
            
            // Build regex pattern based on element type
            let elementTag;
            if (elementType === 'bpmn:EndEvent') {
                elementTag = 'bpmn2:endEvent';
            } else if (elementType === 'bpmn:UserTask') {
                elementTag = 'bpmn2:userTask';
            } else if (elementType === 'bpmn:Task') {
                elementTag = 'bpmn2:task';
            } else if (elementType === 'bpmn:ServiceTask') {
                elementTag = 'bpmn2:serviceTask';
            } else if (elementType === 'bpmn:ScriptTask') {
                elementTag = 'bpmn2:scriptTask';
            } else if (elementType === 'bpmn:BusinessRuleTask') {
                elementTag = 'bpmn2:businessRuleTask';
            } else if (elementType === 'bpmn:ManualTask') {
                elementTag = 'bpmn2:manualTask';
            } else if (elementType === 'bpmn:ReceiveTask') {
                elementTag = 'bpmn2:receiveTask';
            } else if (elementType === 'bpmn:SendTask') {
                elementTag = 'bpmn2:sendTask';
            } else {
                continue; // Skip unknown element types
            }
            
            const elementRegex = new RegExp(
                `(<${elementTag}[^>]*id=["']${elementId}["'][^>]*>)([\\s\\S]*?)(</${elementTag}>)`,
                'i'
            );
            const elementMatch = fixedXml.match(elementRegex);
            
            if (elementMatch) {
                const elementContent = elementMatch[2];
                // Check if Properties container has values="[object Object]"
                const badPropertiesRegex = /<camunda[^:]*:Properties[^>]*values=["']\[object Object\](?:,\[object Object\])*["'][^>]*\s*\/>/i;
                
                if (badPropertiesRegex.test(elementContent)) {
                    // Get all properties from business object
                    const businessObject = element.businessObject;
                    if (businessObject) {
                        const allProps = loadElementProperties(businessObject);
                        
                        // Build correct Properties XML
                        const propertiesXml = Object.keys(allProps)
                            .filter(key => allProps[key] && allProps[key] !== '' && allProps[key] !== 'null' && allProps[key] !== 'undefined')
                            .map(key => {
                                const propValue = String(allProps[key]).replace(/"/g, '&quot;').replace(/&/g, '&amp;');
                                return `        <camunda_1:property name="${key}" value="${propValue}" />`;
                            })
                            .join('\n');
                        
                        if (propertiesXml) {
                            // Replace the bad Properties element with correct one
                            const fixedProperties = `      <camunda_1:Properties>\n${propertiesXml}\n      </camunda_1:Properties>`;
                            const fixedElementContent = elementContent.replace(
                                /<camunda[^:]*:Properties[^>]*values=["'][^"]*["'][^>]*\s*\/>/i,
                                fixedProperties
                            );
                            
                            fixedXml = fixedXml.replace(
                                elementMatch[0],
                                elementMatch[1] + fixedElementContent + elementMatch[3]
                            );
                            hasFixes = true;
                            fixedCount++;
                        }
                    }
                }
            }
        }
        
        if (hasFixes) {
            console.log(`✅ Fixed properties serialization for ${fixedCount} element(s)`);
            // Reload the fixed XML into the modeler to ensure consistency
            try {
                await bpmnModeler.importXML(fixedXml);
                const saveResult = await bpmnModeler.saveXML({ format: true });
                if (saveResult && saveResult.xml) {
                    return saveResult.xml;
                }
            } catch (e) {
                console.warn(`⚠️ Error reloading fixed XML, using fixed XML as-is:`, e);
            }
        }
        
        return fixedXml;
    } catch (error) {
        console.error('Error fixing properties serialization:', error);
        return xml;
    }
}

async function saveElementDescription(businessObject, descriptionText) {
    if (!bpmnModeler || !businessObject) return;

    try {
        const moddle = bpmnModeler.get('moddle');
        const modeling = bpmnModeler.get('modeling');
        const elementRegistry = bpmnModeler.get('elementRegistry');

        let element = currentElement;
        if (!element || element.businessObject !== businessObject) {
            element = elementRegistry.get(businessObject.id);
        }

        if (!element) return;

        let documentation = businessObject.documentation || [];
        let docElement = documentation[0];

        if (!docElement) {
            docElement = moddle.create('bpmn:Documentation', { text: descriptionText || '' });
            documentation = [docElement];
        } else {
            docElement.text = descriptionText || '';
        }

        modeling.updateProperties(element, { documentation: documentation });

        const updatedBusinessObject = element.businessObject;
        if (updatedBusinessObject && updatedBusinessObject.documentation) {
            businessObject.documentation = updatedBusinessObject.documentation;
        }

        try {
            const result = await bpmnModeler.saveXML({ format: true });
            if (result && result.xml) window.currentBpmnXml = result.xml;
        } catch (err) { }
    } catch (error) {
        console.error('Error saving description:', error);
    }
}

async function saveElementProperty(businessObject, propertyName, propertyValue) {
    if (!bpmnModeler || !businessObject) return;

    try {
        const moddle = bpmnModeler.get('moddle');
        const modeling = bpmnModeler.get('modeling');
        const elementRegistry = bpmnModeler.get('elementRegistry');

        let element = currentElement;
        if (!element || element.businessObject !== businessObject) {
            element = elementRegistry.get(businessObject.id);
        }

        if (!element) return;

        // Get fresh business object from element
        let freshBusinessObject = element.businessObject;
        if (!freshBusinessObject) return;

        // Load existing properties first to preserve them
        const existingProperties = loadElementProperties(freshBusinessObject);

        // Also try to get properties directly from extensionElements if they exist
        let existingExtensionElements = freshBusinessObject.extensionElements;
        if (existingExtensionElements && existingExtensionElements.values) {
            // Convert to array if not already
            const extValues = Array.isArray(existingExtensionElements.values)
                ? existingExtensionElements.values
                : [existingExtensionElements.values];

            extValues.forEach(ext => {
                if (ext && (ext.$type === 'camunda:Properties' || ext.$type?.includes('Properties')) && ext.values) {
                    // Ensure ext.values is also an array
                    const propValues = Array.isArray(ext.values) ? ext.values : [ext.values];
                    propValues.forEach(prop => {
                        if (prop && prop.name && prop.value !== undefined && prop.value !== null) {
                            // Add to existingProperties if not already there
                            if (!existingProperties[prop.name]) {
                                existingProperties[prop.name] = String(prop.value);
                            }
                        }
                    });
                }
            });
        }

        // Create new extensionElements to ensure proper moddle serialization
        // CRITICAL: Must use moddle.create to ensure proper serialization
        let extensionElements;
        try {
            extensionElements = moddle.create('bpmn:ExtensionElements');
            // Ensure values array is initialized
            if (!extensionElements.values) {
                extensionElements.values = [];
            }
        } catch (e) {
            // Fallback - but this may cause serialization issues
            extensionElements = { $type: 'bpmn:ExtensionElements', values: [] };
            console.warn(`⚠️ Using fallback ExtensionElements. Serialization may fail.`);
        }

        // Ensure values is an array
        if (!extensionElements.values) {
            extensionElements.values = [];
        }
        
        // Ensure extensionElements is properly structured
        if (!extensionElements.$type) {
            extensionElements.$type = 'bpmn:ExtensionElements';
        }

        // Create new Properties container
        // CRITICAL: Must use moddle.create to ensure proper serialization
        let propertiesContainer;
        try {
            propertiesContainer = moddle.create('camunda:Properties');
            // Ensure values array is initialized
            if (!propertiesContainer.values) {
                propertiesContainer.values = [];
            }
            
            // CRITICAL: Ensure Properties container has $descriptor for proper serialization
            if (!propertiesContainer.$descriptor && moddle.registry) {
                try {
                    const descriptor = moddle.registry.get('camunda:Properties');
                    if (descriptor) {
                        propertiesContainer.$descriptor = descriptor;
                    }
                } catch (e) {
                    // Ignore if descriptor setting fails
                }
            }
        } catch (e) {
            try {
                propertiesContainer = moddle.createAny('camunda:Properties', 'http://camunda.org/schema/1.0/bpmn');
                if (!propertiesContainer.values) {
                    propertiesContainer.values = [];
                }
            } catch (e2) {
                // Last resort fallback - but this may cause serialization issues
                propertiesContainer = { $type: 'camunda:Properties', values: [] };
                console.warn(`⚠️ Using fallback Properties container. Serialization may fail.`);
                
                // Try to get descriptor even for fallback
                if (moddle && moddle.registry) {
                    try {
                        const descriptor = moddle.registry.get('camunda:Properties');
                        if (descriptor) {
                            propertiesContainer.$descriptor = descriptor;
                        }
                    } catch (e3) {
                        // Ignore
                    }
                }
            }
        }

        // Ensure values is an array and properly initialized
        if (!propertiesContainer.values) {
            propertiesContainer.values = [];
        }
        
        // Ensure the container is properly linked to moddle
        if (propertiesContainer && !propertiesContainer.$type) {
            propertiesContainer.$type = 'camunda:Properties';
        }
        
        // CRITICAL: Verify that Properties container is properly structured for serialization
        // The values array should contain camunda:Property elements, not plain objects
        if (propertyName === 'status' || propertyName === 'lifecycle') {
            console.log(`🔍 Properties container structure:`, {
                $type: propertiesContainer.$type,
                hasDescriptor: !!propertiesContainer.$descriptor,
                valuesType: Array.isArray(propertiesContainer.values) ? 'array' : typeof propertiesContainer.values,
                valuesLength: propertiesContainer.values ? propertiesContainer.values.length : 0
            });
        }

        // Copy all existing properties except the one we're updating
        // IMPORTANT: Create properties using moddle to ensure proper serialization
        Object.keys(existingProperties).forEach(key => {
            if (key !== propertyName && existingProperties[key] !== undefined && existingProperties[key] !== null && existingProperties[key] !== '') {
                const valueToCopy = String(existingProperties[key]).trim();
                if (valueToCopy) {
                    let existingProp;
                    try {
                        existingProp = moddle.create('camunda:Property', {
                            name: key,
                            value: valueToCopy
                        });
                    } catch (e) {
                        try {
                            existingProp = moddle.createAny('camunda:Property', 'http://camunda.org/schema/1.0/bpmn', {
                                name: key,
                                value: valueToCopy
                            });
                        } catch (e2) {
                            // Last resort: create using moddle then set properties
                            try {
                                existingProp = moddle.create('camunda:Property');
                                existingProp.name = key;
                                existingProp.value = valueToCopy;
                            } catch (e3) {
                                // Final fallback - but this may not serialize correctly
                                existingProp = { $type: 'camunda:Property', name: key, value: valueToCopy };
                                console.warn(`⚠️ Using fallback property creation for ${key}. Serialization may fail.`);
                            }
                        }
                    }
                    if (existingProp) {
                        // Ensure property is properly linked to moddle
                        if (!existingProp.$type) {
                            existingProp.$type = 'camunda:Property';
                        }
                        
                        // CRITICAL: Ensure property has $descriptor for proper serialization
                        if (!existingProp.$descriptor && moddle) {
                            try {
                                if (moddle.registry) {
                                    const descriptor = moddle.registry.get('camunda:Property');
                                    if (descriptor) {
                                        existingProp.$descriptor = descriptor;
                                    }
                                }
                            } catch (e) {
                                // Ignore if descriptor setting fails
                            }
                        }
                        
                        propertiesContainer.values.push(existingProp);
                    }
                }
            }
        });

        const isBooleanProperty = propertyName === 'unlockObject' || propertyName === 'isRequester';
        const isEmpty = !propertyValue || propertyValue === '' || propertyValue === 'null' || propertyValue === 'undefined';

        // Add the new/updated property if not empty (or if it's a boolean property)
        // CRITICAL: Add property to container BEFORE adding container to extensionElements
        if (!isEmpty || isBooleanProperty) {
            const valueToSave = String(propertyValue).trim();
            let property;

            try {
                property = moddle.create('camunda:Property', { name: propertyName, value: valueToSave });
            } catch (e) {
                try {
                    property = moddle.createAny('camunda:Property', 'http://camunda.org/schema/1.0/bpmn', {
                        name: propertyName,
                        value: valueToSave
                    });
                } catch (e2) {
                    // Last resort: create using moddle then set properties
                    try {
                        property = moddle.create('camunda:Property');
                        property.name = propertyName;
                        property.value = valueToSave;
                    } catch (e3) {
                        // Final fallback - but this may not serialize correctly
                        property = { $type: 'camunda:Property', name: propertyName, value: valueToSave };
                        console.warn(`⚠️ Using fallback property creation for ${propertyName}. Serialization may fail.`);
                    }
                }
            }

            if (property) {
                // Ensure property has correct structure for moddle serialization
                if (!property.$type) {
                    property.$type = 'camunda:Property';
                }
                
                // CRITICAL: Ensure property is a proper moddle element, not a plain object
                // If property was created as fallback, try to convert it to moddle element
                if (!property.$descriptor && moddle) {
                    try {
                        // Try to create a new property using moddle and copy values
                        const moddleProperty = moddle.create('camunda:Property', {
                            name: propertyName,
                            value: valueToSave
                        });
                        // Replace the fallback property with moddle property
                        property = moddleProperty;
                    } catch (e) {
                        // If that fails, try to register the property with moddle
                        try {
                            if (moddle.registry) {
                                // Try to get the descriptor for camunda:Property
                                const descriptor = moddle.registry.get('camunda:Property');
                                if (descriptor) {
                                    property.$descriptor = descriptor;
                                }
                            }
                        } catch (e2) {
                            console.warn(`⚠️ Could not register property ${propertyName} with moddle. Serialization may fail.`);
                        }
                    }
                }
                
                // Ensure property is linked to the moddle instance
                if (property.$parent !== propertiesContainer) {
                    // Try to set parent if moddle supports it
                    try {
                        if (moddle && typeof moddle.setProperty === 'function') {
                            moddle.setProperty(property, '$parent', propertiesContainer);
                        }
                    } catch (e) {
                        // Ignore if parent setting fails
                    }
                }
                
                propertiesContainer.values.push(property);
                console.log(`✅ Created/updated ${propertyName} property: ${valueToSave}`);
            }
        }

        // CRITICAL: Before adding Properties container, verify all properties are proper moddle elements
        // This ensures moddle can serialize them correctly
        if (propertiesContainer.values && propertiesContainer.values.length > 0) {
            propertiesContainer.values.forEach((prop, index) => {
                if (prop) {
                    // Ensure each property has $type
                    if (!prop.$type) {
                        prop.$type = 'camunda:Property';
                    }
                    
                    // Try to ensure property has $descriptor for proper serialization
                    if (!prop.$descriptor && moddle && moddle.registry) {
                        try {
                            const descriptor = moddle.registry.get('camunda:Property');
                            if (descriptor) {
                                prop.$descriptor = descriptor;
                            }
                        } catch (e) {
                            // Ignore if descriptor setting fails
                        }
                    }
                    
                    // Verify property structure
                    if (!prop.name || prop.value === undefined) {
                        console.warn(`⚠️ Property at index ${index} is missing name or value:`, prop);
                    }
                }
            });
        }
        
        // Add the Properties container to extensionElements AFTER all properties are added and verified
        extensionElements.values.push(propertiesContainer);

        // Verify properties container structure before updating
        if (propertyName === 'status' || propertyName === 'lifecycle') {
            const propsCount = propertiesContainer.values ? propertiesContainer.values.length : 0;
            console.log(`📦 Properties container ready with ${propsCount} properties`);
            
            // Verify the property we're adding is in the container
            const ourProperty = propertiesContainer.values.find(p => p && p.name === propertyName);
            if (ourProperty) {
                console.log(`✅ Property ${propertyName} found in container with value: ${ourProperty.value}`);
                
                // Verify property structure for moddle serialization
                if (!ourProperty.$type) {
                    console.warn(`⚠️ Property ${propertyName} missing $type, setting it now`);
                    ourProperty.$type = 'camunda:Property';
                }
                
                // Verify property is in the values array
                const isInArray = propertiesContainer.values.includes(ourProperty);
                if (!isInArray) {
                    console.warn(`⚠️ Property ${propertyName} not properly added to values array!`);
                }
            } else {
                console.warn(`⚠️ Property ${propertyName} NOT found in container before update!`);
            }
            
            // Verify Properties container structure
            if (!propertiesContainer.$type) {
                console.warn(`⚠️ Properties container missing $type, setting it now`);
                propertiesContainer.$type = 'camunda:Properties';
            }
            
            // Verify extensionElements structure
            if (!extensionElements.$type) {
                console.warn(`⚠️ ExtensionElements missing $type, setting it now`);
                extensionElements.$type = 'bpmn:ExtensionElements';
            }
        }

        // Ensure extensionElements is properly attached to the business object before updating
        // This ensures the moddle recognizes it as part of the element
        freshBusinessObject.extensionElements = extensionElements;

        // Update the element with the modified extension elements
        // For BPMN/Camunda, properties should be saved in extensionElements only, not as attributes
        const updates = { extensionElements: extensionElements };
        modeling.updateProperties(element, updates);

        // Force a refresh to ensure the business object is updated
        await new Promise(resolve => setTimeout(resolve, 10));
        const updatedElement = elementRegistry.get(element.id);
        if (updatedElement) {
            freshBusinessObject = updatedElement.businessObject;
            // Verify extensionElements are properly attached
            if (freshBusinessObject && !freshBusinessObject.extensionElements) {
                console.warn(`⚠️ ExtensionElements not found after updateProperties for element ${element.id}`);
                // Try to re-attach extensionElements directly
                freshBusinessObject.extensionElements = extensionElements;
                // Try updateProperties again
                modeling.updateProperties(updatedElement, { extensionElements: extensionElements });
            } else if (freshBusinessObject.extensionElements) {
                // Verify the property is actually in the extensionElements
                const verifyProps = loadElementProperties(freshBusinessObject);
                if (propertyName === 'status' || propertyName === 'lifecycle') {
                    if (verifyProps[propertyName] !== String(propertyValue).trim()) {
                        console.warn(`⚠️ Property ${propertyName} not found in extensionElements after update. Expected: ${propertyValue}, Found: ${verifyProps[propertyName]}`);
                    }
                }
            }
        }

        // Log for debugging
        if (propertyName === 'status' || propertyName === 'lifecycle') {
            console.log(`💾 Saving ${propertyName} property:`, {
                propertyName: propertyName,
                propertyValue: propertyValue,
                savedAs: String(propertyValue).trim(),
                elementId: element.id,
                elementType: element.type,
                hasExtensionElements: !!freshBusinessObject?.extensionElements,
                propertiesCount: freshBusinessObject?.extensionElements?.values?.length || 0
            });
        }

        // Refresh business object reference
        const updatedBusinessObject = element.businessObject;
        if (updatedBusinessObject) {
            businessObject.extensionElements = updatedBusinessObject.extensionElements;
        }
        // Force save XML to ensure properties are persisted
        try {
            // Small delay to ensure all updates are processed
            await new Promise(resolve => setTimeout(resolve, 50));

            const result = await bpmnModeler.saveXML({ format: true });
            if (result && result.xml) {
                window.currentBpmnXml = result.xml;
                
                // Verify that the property was actually serialized to XML
                if (propertyName === 'status' || propertyName === 'lifecycle') {
                    const valueToCheck = String(propertyValue).trim();
                    // Check for property in XML with various namespace formats
                    const propertyRegex = new RegExp(
                        `<camunda[^:]*:property[^>]*name=["']${propertyName}["'][^>]*value=["']${valueToCheck}["']`,
                        'i'
                    );
                    const hasPropertyInXML = propertyRegex.test(result.xml);
                    
                    if (hasPropertyInXML) {
                        console.log(`✅ ${propertyName} property (${valueToCheck}) successfully serialized to XML`);
                    } else {
                        console.warn(`⚠️ ${propertyName} property (${valueToCheck}) NOT found in serialized XML`);
                        
                        // Try to find what was actually serialized
                        const allPropertiesMatch = result.xml.match(
                            new RegExp(`<camunda[^:]*:property[^>]*name=["']${propertyName}["'][^>]*>`, 'i')
                        );
                        if (allPropertiesMatch) {
                            console.warn(`Found ${propertyName} property but with different value or format`);
                        }
                        
                        // Log the extensionElements section for this element
                        const elementId = element.id;
                        const elementRegex = new RegExp(
                            `<bpmn2:endEvent[^>]*id=["']${elementId}["'][^>]*>([\\s\\S]*?)</bpmn2:endEvent>`,
                            'i'
                        );
                        const elementMatch = result.xml.match(elementRegex);
                        if (elementMatch) {
                            console.warn(`Element XML for ${elementId}:`, elementMatch[0].substring(0, 500));
                        }
                        
                        // Try alternative approach: manually fix XML if moddle failed to serialize properties correctly
                        // This is a last resort fallback when moddle serializes values as "[object Object]"
                        console.warn(`🔄 Attempting alternative serialization approach for ${propertyName}...`);
                        try {
                            // Check if the problem is that properties are serialized as "[object Object]"
                            const elementId = element.id;
                            const elementType = element.type;
                            
                            // Determine the XML tag based on element type
                            let elementTag;
                            if (elementType === 'bpmn:EndEvent') {
                                elementTag = 'bpmn2:endEvent';
                            } else if (elementType === 'bpmn:UserTask') {
                                elementTag = 'bpmn2:userTask';
                            } else if (elementType === 'bpmn:Task') {
                                elementTag = 'bpmn2:task';
                            } else if (elementType === 'bpmn:ServiceTask') {
                                elementTag = 'bpmn2:serviceTask';
                            } else if (elementType === 'bpmn:ScriptTask') {
                                elementTag = 'bpmn2:scriptTask';
                            } else if (elementType === 'bpmn:BusinessRuleTask') {
                                elementTag = 'bpmn2:businessRuleTask';
                            } else if (elementType === 'bpmn:ManualTask') {
                                elementTag = 'bpmn2:manualTask';
                            } else if (elementType === 'bpmn:ReceiveTask') {
                                elementTag = 'bpmn2:receiveTask';
                            } else if (elementType === 'bpmn:SendTask') {
                                elementTag = 'bpmn2:sendTask';
                            } else {
                                elementTag = null; // Unknown type, skip
                            }
                            
                            if (!elementTag) {
                                // Skip if element type is not supported
                                return;
                            }
                            
                            const elementRegex = new RegExp(
                                `(<${elementTag}[^>]*id=["']${elementId}["'][^>]*>)([\\s\\S]*?)(</${elementTag}>)`,
                                'i'
                            );
                            const elementMatch = result.xml.match(elementRegex);
                            
                            if (elementMatch) {
                                const elementContent = elementMatch[2];
                                // Check if Properties container has values="[object Object]"
                                const badPropertiesRegex = /<camunda[^:]*:Properties[^>]*values=["']\[object Object\](?:,\[object Object\])*["'][^>]*\s*\/>/i;
                                
                                if (badPropertiesRegex.test(elementContent)) {
                                    console.warn(`⚠️ Properties serialized incorrectly as [object Object]. Attempting XML fix...`);
                                    
                                    // Get all properties from business object
                                    const retryElement = elementRegistry.get(element.id);
                                    if (retryElement) {
                                        const retryBusinessObject = retryElement.businessObject;
                                        const allProps = loadElementProperties(retryBusinessObject);
                                        
                                        // Build correct Properties XML
                                        const propertiesXml = Object.keys(allProps)
                                            .filter(key => allProps[key] && allProps[key] !== '')
                                            .map(key => {
                                                const propValue = String(allProps[key]).replace(/"/g, '&quot;');
                                                return `        <camunda_1:property name="${key}" value="${propValue}" />`;
                                            })
                                            .join('\n');
                                        
                                        if (propertiesXml) {
                                            // Replace the bad Properties element with correct one
                                            const fixedProperties = `      <camunda_1:Properties>\n${propertiesXml}\n      </camunda_1:Properties>`;
                                            const fixedElementContent = elementContent.replace(
                                                /<camunda[^:]*:Properties[^>]*values=["'][^"]*["'][^>]*\s*\/>/i,
                                                fixedProperties
                                            );
                                            
                                            const fixedXml = result.xml.replace(
                                                elementMatch[0],
                                                elementMatch[1] + fixedElementContent + elementMatch[3]
                                            );
                                            
                                            window.currentBpmnXml = fixedXml;
                                            console.log(`✅ Fixed XML serialization for ${propertyName} and other properties`);
                                            
                                            // Save the fixed XML
                                            await new Promise(resolve => setTimeout(resolve, 50));
                                            const fixedResult = await bpmnModeler.saveXML({ format: true });
                                            if (fixedResult && fixedResult.xml) {
                                                window.currentBpmnXml = fixedResult.xml;
                                                const fixedHasProperty = propertyRegex.test(fixedResult.xml);
                                                if (fixedHasProperty) {
                                                    console.log(`✅ Property ${propertyName} successfully serialized after XML fix`);
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Try standard retry approach
                                    const retryElement = elementRegistry.get(element.id);
                                    if (retryElement) {
                                        const retryBusinessObject = retryElement.businessObject;
                                        if (retryBusinessObject && retryBusinessObject.extensionElements) {
                                            const retryProps = loadElementProperties(retryBusinessObject);
                                            if (retryProps[propertyName] === valueToCheck) {
                                                console.log(`✅ Property ${propertyName} exists in business object, forcing re-serialization...`);
                                                await new Promise(resolve => setTimeout(resolve, 100));
                                                const retryResult = await bpmnModeler.saveXML({ format: true });
                                                if (retryResult && retryResult.xml) {
                                                    window.currentBpmnXml = retryResult.xml;
                                                    const retryHasProperty = propertyRegex.test(retryResult.xml);
                                                    if (retryHasProperty) {
                                                        console.log(`✅ Property ${propertyName} successfully serialized after retry`);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (retryErr) {
                            console.error(`Error in retry serialization:`, retryErr);
                        }
                    }
                }
            }
        } catch (err) {
            console.error('Error saving XML after property update:', err);
        }
    } catch (error) {
        console.error('Error saving property:', error);
    }
}

// Helper function to get parent lane or lane set
function getParentLaneSet(element) {
    if (!element || !element.businessObject) return null;

    const businessObject = element.businessObject;
    // Lane belongs to a LaneSet
    if (businessObject.$parent && businessObject.$parent.$type === 'bpmn:LaneSet') {
        return businessObject.$parent;
    }

    return null;
}

// Helper function to show role selection dialog
async function showRoleSelectionDialog() {
    return new Promise((resolve) => {
        let rolesList = [];
        if (currentModuleId) {
            loadRolesForModule(currentModuleId).then(roles => {
                rolesList = roles;
                showRoleDialog(rolesList, resolve);
            });
        } else {
            showRoleDialog(rolesList, resolve);
        }
    });
}

function showRoleDialog(rolesList, resolve) {
    const dialog = document.createElement('div');
    dialog.style.cssText = 'position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); background: white; padding: 20px; border: 1px solid #ddd; border-radius: 4px; z-index: 10000; box-shadow: 0 4px 12px rgba(0,0,0,0.15); min-width: 300px;';

    var req = dfwT('requestor', 'Requestor');
    var opts = rolesList.map(function (role) {
        var n = role.name == null ? '' : String(role.name);
        var v = n.replace(/"/g, '&quot;');
        return '<option value="' + v + '">' + dfwEscape(n) + '</option>';
    }).join('');
    dialog.innerHTML = `
        <h3 style="margin-top: 0;">${dfwEscape(dfwT('selectRoleTitle', 'Select Role'))}</h3>
        <select id="role-dialog-select" style="width: 100%; padding: 8px; margin-bottom: 15px;">
            <option value="">${dfwEscape(dfwT('selectRolePlaceholder', 'Select Role...'))}</option>
            ${opts}
            <option value="${req.replace(/"/g, '&quot;')}">${dfwEscape(req)}</option>
        </select>
        <div style="display: flex; gap: 10px; justify-content: flex-end;">
            <button id="role-dialog-cancel" style="padding: 8px 16px; background: #f0f0f0; border: 1px solid #ddd; border-radius: 4px; cursor: pointer;">${dfwEscape(dfwT('cancel', 'Cancel'))}</button>
            <button id="role-dialog-ok" style="padding: 8px 16px; background: #4a90e2; color: white; border: none; border-radius: 4px; cursor: pointer;">${dfwEscape(dfwT('ok', 'OK'))}</button>
        </div>
    `;

    document.body.appendChild(dialog);

    const select = dialog.querySelector('#role-dialog-select');
    const okBtn = dialog.querySelector('#role-dialog-ok');
    const cancelBtn = dialog.querySelector('#role-dialog-cancel');

    okBtn.addEventListener('click', () => {
        const selectedRole = select.value;
        document.body.removeChild(dialog);
        resolve(selectedRole || null);
    });

    cancelBtn.addEventListener('click', () => {
        document.body.removeChild(dialog);
        resolve(null);
    });

    // Close on Escape
    const escapeHandler = (e) => {
        if (e.key === 'Escape') {
            document.body.removeChild(dialog);
            document.removeEventListener('keydown', escapeHandler);
            resolve(null);
        }
    };
    document.addEventListener('keydown', escapeHandler);
}

// Add Lane function
async function addLane(position) {
    if (!currentElement || currentElement.type !== 'bpmn:Lane') {
        dfwShowNotification(dfwT('alertSelectLaneFirst', 'Please select a lane first'), 'error');
        return;
    }

    if (!bpmnModeler) return;

    try {
        const modeling = bpmnModeler.get('modeling');
        const elementRegistry = bpmnModeler.get('elementRegistry');

        // Get parent participant (pool)
        const parentParticipant = elementRegistry.filter(el => el.type === 'bpmn:Participant')[0];
        if (!parentParticipant) {
            dfwShowNotification(dfwT('alertNoParticipant', 'No participant found'), 'error');
            return;
        }

        // Get lane set
        const laneSet = getParentLaneSet(currentElement);
        if (!laneSet) {
            dfwShowNotification(dfwT('alertNoLaneSet', 'No lane set found'), 'error');
            return;
        }

        // Create new lane
        const newLaneShape = modeling.addLane(currentElement, position);

        if (!newLaneShape) {
            dfwShowNotification(dfwT('alertFailedCreateLane', 'Failed to create lane'), 'error');
            return;
        }

        // Open dialog to select role
        const role = await showRoleSelectionDialog();
        if (role) {
            const newLaneBusinessObject = newLaneShape.businessObject;
            await saveElementProperty(newLaneBusinessObject, 'roleName', role);
            if (role === 'Requestor') {
                await saveElementProperty(newLaneBusinessObject, 'isRequester', 'true');
            }

            // Update visual label
            modeling.updateProperties(newLaneShape, { name: role });
            modeling.updateLabel(newLaneShape, role);
        }

        // Refresh properties panel
        setTimeout(() => {
            loadPropertiesForElement(newLaneShape);
        }, 100);

    } catch (error) {
        console.error('Error adding lane:', error);
        dfwShowNotification(dfwTpl(dfwT('alertErrorAddingLane', 'Error adding lane: {{message}}'), { message: error.message }), 'error');
    }
}

// Divide Lane function
async function divideLane(count) {
    if (!currentElement || currentElement.type !== 'bpmn:Lane') {
        dfwShowNotification(dfwT('alertSelectLaneFirst', 'Please select a lane first'), 'error');
        return;
    }

    if (!bpmnModeler) return;

    try {
        const modeling = bpmnModeler.get('modeling');
        const elementRegistry = bpmnModeler.get('elementRegistry');
        const canvas = bpmnModeler.get('canvas');

        // 1. Get parent lane set
        const laneSet = getParentLaneSet(currentElement);
        if (!laneSet) {
            dfwShowNotification(dfwT('alertNoLaneSet', 'No lane set found'), 'error');
            return;
        }

        // 2. Get flow nodes in current lane
        const flowNodes = currentElement.businessObject.flowNodeRef || [];
        const flowNodeIds = flowNodes.map(node => node.id);

        // 3. Get position and height
        const currentShape = currentElement;
        const currentBounds = canvas.getAbsoluteBBox(currentShape);
        const laneHeight = currentBounds ? (currentBounds.height / count) : 200;
        const startY = currentBounds ? currentBounds.y : 0;

        // 4. Store current lane index for positioning
        const allLanes = elementRegistry.filter(el => el.type === 'bpmn:Lane');
        const currentLaneIndex = allLanes.indexOf(currentElement);

        // 5. Delete current lane
        modeling.removeElements([currentElement]);

        // 6. Create N new lanes
        const newLanes = [];
        for (let i = 0; i < count; i++) {
            // Find a reference lane to add after (use first remaining lane or participant)
            const referenceLane = allLanes.find(lane => lane !== currentElement && lane.businessObject.$parent === laneSet);
            const parentParticipant = elementRegistry.filter(el => el.type === 'bpmn:Participant')[0];

            let newLaneShape;
            if (referenceLane && i === 0) {
                // Add first lane after reference
                newLaneShape = modeling.addLane(referenceLane, 'bottom');
            } else if (newLanes.length > 0) {
                // Add subsequent lanes after previous new lane
                newLaneShape = modeling.addLane(newLanes[newLanes.length - 1], 'bottom');
            } else if (parentParticipant) {
                // Fallback: add to participant
                newLaneShape = modeling.addLane(parentParticipant, 'bottom');
            } else {
                dfwShowNotification(dfwT('alertCannotCreateNoReference', 'Cannot create lane: no reference found'), 'error');
                return;
            }

            if (!newLaneShape) {
                console.error('Failed to create lane', i);
                continue;
            }

            newLanes.push(newLaneShape);

            // 7. Move flow nodes to first lane
            if (i === 0 && flowNodeIds.length > 0) {
                for (const nodeId of flowNodeIds) {
                    const nodeElement = elementRegistry.get(nodeId);
                    if (nodeElement) {
                        try {
                            modeling.updateProperties(nodeElement, {
                                lane: newLaneShape.businessObject.id
                            });
                        } catch (err) {
                            console.warn('Could not move node to new lane:', err);
                        }
                    }
                }
            }

            // 8. Open dialog to select role for each lane
            const role = await showRoleSelectionDialog();
            if (role) {
                const newLaneBusinessObject = newLaneShape.businessObject;
                await saveElementProperty(newLaneBusinessObject, 'roleName', role);
                if (role === 'Requestor') {
                    await saveElementProperty(newLaneBusinessObject, 'isRequester', 'true');
                }

                // Update visual label
                modeling.updateProperties(newLaneShape, { name: role });
                modeling.updateLabel(newLaneShape, role);
            }
        }

        // Refresh properties panel with first new lane
        if (newLanes.length > 0) {
            setTimeout(() => {
                loadPropertiesForElement(newLanes[0]);
            }, 100);
        }

    } catch (error) {
        console.error('Error dividing lane:', error);
        dfwShowNotification(dfwTpl(dfwT('alertErrorDividingLane', 'Error dividing lane: {{message}}'), { message: error.message }), 'error');
    }
}