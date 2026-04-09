/**
 * Custom Fields Runtime - Simplified Implementation
 * Handles rendering and saving custom fields in forms and views
 */

(function(global) {
    'use strict';

    const API_BASE = '/api/custom-fields';
    
    // Cache for facets from module table
    let facetsCache = null;
    let facetsCachePromise = null;

    function setSectionVisibility(section, shouldShow) {
        if (!section) return;
        if (shouldShow) {
            section.hidden = false;
            section.removeAttribute('hidden');
            section.style.setProperty('display', 'block', 'important');
        } else {
            section.hidden = true;
            section.setAttribute('hidden', '');
            section.style.setProperty('display', 'none', 'important');
        }
    }

    /** Section wrapper for show/hide when no metadata (form-section, or view-section e.g. System edit). */
    function getCustomFieldsSectionElement(container) {
        if (!container) return null;
        const byForm = container.closest('.form-section');
        if (byForm) return byForm;
        return container.closest('.view-section');
    }

    /**
     * Fetch all facets from the module table dynamically
     * @returns {Promise<Array>} Array of facet objects with id and name
     */
    async function fetchFacetsFromModule() {
        if (facetsCache) {
            return facetsCache;
        }

        if (facetsCachePromise) {
            return facetsCachePromise;
        }

        facetsCachePromise = (async () => {
            const endpoints = ['/api/custom-fields/facets', '/admin/api/facets'];
            for (const url of endpoints) {
                try {
                    const response = await fetch(url, { credentials: 'include' });
                    if (!response.ok) {
                        continue;
                    }
                    const data = await response.json();
                    const rows = Array.isArray(data) ? data : (data && Array.isArray(data.data) ? data.data : null);
                    if (rows && rows.length) {
                        facetsCache = rows;
                        return facetsCache;
                    }
                } catch (e) {
                    console.warn('Facets fetch failed for', url, e);
                }
            }
            console.warn('No facet list available; custom field facet mapping may be inaccurate');
            facetsCache = [];
            return facetsCache;
        })().catch(err => {
            console.error('Error fetching facets:', err);
            facetsCachePromise = null;
            return [];
        });

        return facetsCachePromise;
    }

    /**
     * Map frontend facet name to module primaryname
     * Uses the module table to find the correct primaryname
     * @param {string} facetName - The facet name from frontend
     * @returns {Promise<string>} The primaryname from module table
     */
    async function mapFacetName(facetName) {
        if (!facetName) return facetName;
        
        const facets = await fetchFacetsFromModule();
        const compact = String(facetName).trim().toLowerCase().replace(/[\s_-]+/g, '');

        // Change Request facet module name is usually "Change Requests" — frontend often passes ChangeRequest
        if (compact === 'changerequest' || compact === 'changerequests') {
            const hit = facets.find(f => /^change\s+requests?$/i.test(String(f.name || f.id || '').trim()));
            if (hit) return hit.name || hit.id;
        }
        if (compact === 'legalentity' || compact === 'legalentities') {
            const hit = facets.find(f => /^legal\s+entities?$/i.test(String(f.name || f.id || '').trim()));
            if (hit) return hit.name || hit.id;
        }

        // Attribute / Physical Fields — module primary name often differs from "Attribute"
        if (compact === 'attribute' || compact === 'attributes' || compact === 'physicalfields' || compact === 'physicalfield'
            || compact === 'dataattributes' || compact === 'dataattribute') {
            const hit = facets.find(f => {
                const raw = String(f.name || f.id || '').toLowerCase();
                return raw.includes('attribute') || raw.includes('physical field');
            });
            if (hit) return hit.name || hit.id;
        }

        if (compact === 'policy' || compact === 'policies') {
            let hit = facets.find(f => {
                const raw = String(f.name || f.id || '').toLowerCase().replace(/\s+/g, ' ').trim();
                return raw === 'policy' || raw === 'policies';
            });
            if (!hit) {
                hit = facets.find(f => /^policies?$/i.test(String(f.name || '').trim()));
            }
            if (hit) return hit.name || hit.id;
        }

        if (compact === 'product' || compact === 'products') {
            let hit = facets.find(f => {
                const raw = String(f.name || f.id || '').toLowerCase().replace(/\s+/g, ' ').trim();
                return raw === 'product' || raw === 'products';
            });
            if (!hit) {
                hit = facets.find(f => /^products?$/i.test(String(f.name || '').trim()));
            }
            if (hit) return hit.name || hit.id;
        }

        if (compact === 'process' || compact === 'processes') {
            let hit = facets.find(f => {
                const raw = String(f.name || f.id || '').toLowerCase().replace(/\s+/g, ' ').trim();
                return raw === 'process' || raw === 'processes';
            });
            if (!hit) {
                hit = facets.find(f => /^process(es)?$/i.test(String(f.name || '').trim()));
            }
            if (hit) return hit.name || hit.id;
        }

        if (compact === 'system' || compact === 'systems') {
            let hit = facets.find(f => {
                const raw = String(f.name || f.id || '').toLowerCase().replace(/\s+/g, ' ').trim();
                return raw === 'system' || raw === 'systems';
            });
            if (!hit) {
                hit = facets.find(f => /^systems?$/i.test(String(f.name || '').trim()));
            }
            if (hit) return hit.name || hit.id;
        }

        // Try exact match first
        const exactMatch = facets.find(f => 
            f.name === facetName || f.id === facetName
        );
        if (exactMatch) {
            return exactMatch.name || facetName;
        }

        // Try case-insensitive match
        const caseInsensitiveMatch = facets.find(f => 
            f.name && f.name.toLowerCase() === facetName.toLowerCase()
        );
        if (caseInsensitiveMatch) {
            return caseInsensitiveMatch.name || facetName;
        }

        // Try partial match (e.g., "Data Sets" matches "Data Set")
        const partialMatch = facets.find(f => {
            const fName = (f.name || '').toLowerCase();
            const inputName = facetName.toLowerCase();
            return fName.includes(inputName) || inputName.includes(fName);
        });
        if (partialMatch) {
            return partialMatch.name || facetName;
        }

        // Normalized match: strip spaces, hyphens, underscores and compare
        const normalize = (s) => s.toLowerCase().replace(/[\s\-_]+/g, '');
        const normalizedInput = normalize(facetName);
        const normalizedMatch = facets.find(f => {
            const fNorm = normalize(f.name || '');
            return fNorm === normalizedInput ||
                   fNorm.startsWith(normalizedInput) ||
                   normalizedInput.startsWith(fNorm);
        });
        if (normalizedMatch) {
            return normalizedMatch.name || facetName;
        }

        // If no match found, return as-is (backend will handle validation)
        console.warn(`Facet name "${facetName}" not found in module table, using as-is`);
        return facetName;
    }

    async function fetchMetadata(facetId, objectId) {
        const mappedFacetId = await mapFacetName(facetId);
        const url = `${API_BASE}/metadata?facetId=${encodeURIComponent(mappedFacetId)}${objectId != null ? '&objectId=' + objectId : ''}`;
        const response = await fetch(url, { credentials: 'include' });
        if (!response.ok) throw new Error('Failed to load custom field metadata');
        const data = await response.json();
        if (!data.success || !Array.isArray(data.data)) {
            throw new Error(data.error || 'Failed to load custom field metadata');
        }
        return data.data;
    }

    async function fetchValues(facetId, objectId) {
        if (objectId == null) return [];
        const mappedFacetId = await mapFacetName(facetId);
        const response = await fetch(`${API_BASE}/data?facetId=${encodeURIComponent(mappedFacetId)}&objectId=${objectId}`, { credentials: 'include' });
        if (!response.ok) throw new Error('Failed to load custom field values');
        const data = await response.json();
        if (!data.success || !Array.isArray(data.data)) {
            throw new Error(data.error || 'Failed to load custom field data');
        }
        return data.data;
    }

    async function saveValues(facetId, objectId, values) {
        const mappedFacetId = await mapFacetName(facetId);
        const response = await fetch(`${API_BASE}/data`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                facetId: mappedFacetId,
                objectId: objectId,
                values: values
            })
        });
        let data = {};
        try {
            data = await response.json();
        } catch (e) {
            data = {};
        }
        if (!response.ok || data.success === false) {
            throw new Error(data.error || `Failed to save custom field values (${response.status})`);
        }
        return data;
    }

    function coerceDefault(metadata, valueData, isExistingObject) {
        const dataType = (metadata.type || '').toLowerCase();
        // Only consider it saved data if there's an actual value saved in the database
        // Empty strings, null, or undefined should not count as saved data
        const hasSavedData = valueData && (
            (valueData.value != null && valueData.value !== '' && String(valueData.value).trim() !== '') ||
            valueData.enumId != null ||
            (valueData.enumIds && Array.isArray(valueData.enumIds) && valueData.enumIds.length > 0)
        );
        
        if (hasSavedData) {
            // For multiselect, preserve enumIds array
            if (dataType === 'multiselect' && valueData.enumIds && Array.isArray(valueData.enumIds)) {
                return { value: valueData.value, enumId: valueData.enumId, enumIds: valueData.enumIds };
            }
            return { value: valueData.value, enumId: valueData.enumId };
        }

        if (isExistingObject) {
            // For existing objects without saved data:
            // - Mandatory fields with a default value: USE the default so the form can be saved
            // - Non-mandatory fields: remain empty (placeholder shown instead)
            const isMandatory = Boolean(metadata.mandatory);
            const hasDefault = metadata.defaultValue != null && String(metadata.defaultValue).trim() !== '';

            if (isMandatory && hasDefault) {
                // Fall through to the "new object" default logic below
                // so mandatory fields get their default value pre-filled
            } else {
                // Optional fields with defaults: still show default for numeric/percentage (explicit UX)
                if (!isMandatory && hasDefault && (dataType === 'percentage' || dataType === 'number' || dataType === 'decimal')) {
                    const raw = String(metadata.defaultValue).replace(/%/g, '').trim();
                    return { value: raw, enumId: null };
                }
                if (dataType === 'dropdown' || dataType === 'multiselect') {
                    return { value: null, enumId: null };
                }
                if (dataType === 'checkbox') {
                    return { value: 'false', enumId: null };
                }
                if (dataType === 'date' || dataType === 'time') {
                    return { value: '', enumId: null };
                }
                return { value: '', enumId: null };
            }
        }

        // For new objects, always use default value if available
        if (dataType === 'dropdown' || dataType === 'multiselect') {
            if (metadata.defaultValue && metadata.enumValues) {
                if (dataType === 'multiselect') {
                    // For multiselect, default value can be comma-separated
                    const defaultValues = String(metadata.defaultValue).split(',').map(v => v.trim().toLowerCase());
                    const matchingEnumIds = [];
                    metadata.enumValues.forEach(ev => {
                        const enumVal = (ev.enumValue || ev.EnumValue || ev.name || '').toLowerCase();
                        if (defaultValues.includes(enumVal)) {
                            matchingEnumIds.push(ev.id || ev.ID);
                        }
                    });
                    if (matchingEnumIds.length > 0) {
                        return { value: null, enumId: matchingEnumIds[0], enumIds: matchingEnumIds };
                    }
                } else {
                    // For single dropdown, find exact match
                    const match = metadata.enumValues.find(ev => {
                        const enumVal = ev.enumValue || ev.EnumValue || ev.name || '';
                        return enumVal && String(enumVal).toLowerCase() === String(metadata.defaultValue).toLowerCase();
                    });
                    if (match) return { value: null, enumId: match.id || match.ID };
                }
            }
            return { value: null, enumId: null };
        }
        if (dataType === 'checkbox') {
            const val = metadata.defaultValue;
            // For new objects, default to checked if defaultValue is true/checked, otherwise unchecked
            return { value: (val === true || String(val).toLowerCase() === 'true' || String(val).toLowerCase() === 'checked') ? 'true' : 'false', enumId: null };
        }
        if (dataType === 'date' || dataType === 'time') {
            // For date/time, return default value if available
            return { value: metadata.defaultValue || '', enumId: null };
        }
        // For text and other types, use default value if available, otherwise empty
        return { value: metadata.defaultValue || '', enumId: null };
    }

    function createFieldElement(metadata, valueData, mode, isExistingObject) {
        const wrapper = document.createElement('div');
        wrapper.className = 'form-group custom-field-group';

        const label = document.createElement('label');
        label.className = 'form-label';
        label.textContent = metadata.displayName || 'Custom Field';
        if (metadata.mandatory) {
            const requiredSpan = document.createElement('span');
            requiredSpan.className = 'required';
            requiredSpan.textContent = ' *';
            label.appendChild(requiredSpan);
        }
        wrapper.appendChild(label);

        const dataType = (metadata.type || '').toLowerCase();
        const defaults = coerceDefault(metadata, valueData, isExistingObject);
        let input;

        if (dataType === 'textarea' || dataType === 'richtext') {
            input = document.createElement('textarea');
            input.className = 'form-input form-textarea';
            // Set value - empty string will allow placeholder to show
            input.value = (defaults.value != null && defaults.value !== '') ? defaults.value : '';
        } else if (dataType === 'number' || dataType === 'decimal') {
            input = document.createElement('input');
            input.type = 'number';
            input.className = 'form-input';
            if (defaults.value != null && defaults.value !== '') {
                input.value = defaults.value;
            }
            if (dataType === 'decimal') {
                input.step = '0.01';
            }
        } else if (dataType === 'percentage') {
            input = document.createElement('input');
            input.type = 'number';
            input.className = 'form-input';
            input.min = '0';
            input.max = '100';
            if (defaults.value != null && defaults.value !== '') {
                const normalizedDefault = String(defaults.value).replace('%', '').trim();
                input.value = normalizedDefault;
            }
            input.step = '1';
            input.style.paddingRight = '2.2rem';
            const inputWrapper = document.createElement('div');
            inputWrapper.className = 'cf-percentage-wrapper';
            inputWrapper.appendChild(input);
            const suffix = document.createElement('span');
            suffix.className = 'cf-percentage-icon';
            suffix.innerHTML = '<i class="fas fa-percent"></i>';
            inputWrapper.appendChild(suffix);
            wrapper.appendChild(inputWrapper);
        } else if (dataType === 'date') {
            input = document.createElement('input');
            input.type = 'date';
            input.className = 'form-input';
            if (defaults.value) input.value = defaults.value;

        } else if (dataType === 'time') {
            input = document.createElement('input');
            input.type = 'time';
            input.className = 'form-input';
            if (defaults.value) input.value = defaults.value;
        } else if (dataType === 'checkbox') {
            const checkboxWrapper = document.createElement('div');
            checkboxWrapper.className = 'checkbox-wrapper';
            input = document.createElement('input');
            input.type = 'checkbox';
            input.className = 'form-checkbox';
            input.checked = defaults.value === 'true' || defaults.value === true;
            checkboxWrapper.appendChild(input);
            wrapper.appendChild(checkboxWrapper);
        } else if (dataType === 'dropdown') {
            input = document.createElement('select');
            input.className = 'form-select';
            if (!metadata.mandatory) {
                const placeholderOption = document.createElement('option');
                placeholderOption.value = '';
                placeholderOption.textContent = 'Select...';
                input.appendChild(placeholderOption);
            }
            (metadata.enumValues || []).forEach(opt => {
                const option = document.createElement('option');
                const optionId = opt.id || opt.ID;
                option.value = optionId;
                option.textContent = opt.enumValue || opt.EnumValue || opt.name || '';
                input.appendChild(option);
            });
            // Set default values
            if (defaults.enumId != null) {
                input.value = String(defaults.enumId);
            }
        } else if (dataType === 'multiselect') {
            // Create custom tag-based multiselect component
            const multiselectContainer = document.createElement('div');
            multiselectContainer.className = 'custom-multiselect-container';
            
            // Tags container
            const tagsContainer = document.createElement('div');
            tagsContainer.className = 'custom-multiselect-tags';
            
            // Dropdown wrapper
            const dropdownWrapper = document.createElement('div');
            dropdownWrapper.className = 'custom-multiselect-dropdown-wrapper';
            
            // Hidden select for form submission
            input = document.createElement('select');
            input.className = 'custom-multiselect-hidden';
            input.multiple = true;
            input.style.display = 'none';
            
            // Visible dropdown input
            const dropdownInput = document.createElement('div');
            dropdownInput.className = 'custom-multiselect-input';
            dropdownInput.setAttribute('tabindex', '0');
            dropdownInput.textContent = 'Select default values';
            
            // Dropdown list
            const dropdownList = document.createElement('div');
            dropdownList.className = 'custom-multiselect-list';
            dropdownList.style.display = 'none';
            
            // Get selected enum IDs
            let selectedEnumIds = [];
            if (valueData && valueData.enumIds && Array.isArray(valueData.enumIds)) {
                selectedEnumIds = [...valueData.enumIds];
            } else if (defaults.enumIds && Array.isArray(defaults.enumIds)) {
                selectedEnumIds = [...defaults.enumIds];
            } else if (defaults.enumId != null) {
                selectedEnumIds = [defaults.enumId];
            }
            
            // Create options
            const options = [];
            (metadata.enumValues || []).forEach(opt => {
                const optionId = opt.id || opt.ID;
                const optionValue = opt.enumValue || opt.EnumValue || opt.name || '';
                
                // Add to hidden select
                const option = document.createElement('option');
                option.value = optionId;
                option.textContent = optionValue;
                if (selectedEnumIds.includes(optionId)) {
                    option.selected = true;
                }
                input.appendChild(option);
                
                // Create visible option
                const listItem = document.createElement('div');
                listItem.className = 'custom-multiselect-option';
                listItem.dataset.value = optionId;
                listItem.textContent = optionValue;
                if (selectedEnumIds.includes(optionId)) {
                    listItem.classList.add('selected');
                }
                
                listItem.addEventListener('click', () => {
                    const isSelected = selectedEnumIds.includes(optionId);
                    if (isSelected) {
                        // Remove selection
                        selectedEnumIds = selectedEnumIds.filter(id => id !== optionId);
                        listItem.classList.remove('selected');
                        option.selected = false;
                        removeTag(optionId, optionValue);
                    } else {
                        // Add selection
                        selectedEnumIds.push(optionId);
                        listItem.classList.add('selected');
                        option.selected = true;
                        addTag(optionId, optionValue);
                    }
                    updatePlaceholder();
                });
                
                dropdownList.appendChild(listItem);
                options.push({ id: optionId, value: optionValue, element: listItem, option: option });
            });
            
            // Add selected tags
            selectedEnumIds.forEach(enumId => {
                const opt = metadata.enumValues.find(ev => (ev.id || ev.ID) === enumId);
                if (opt) {
                    const optionValue = opt.enumValue || opt.EnumValue || opt.name || '';
                    addTag(enumId, optionValue);
                }
            });
            
            function addTag(enumId, value) {
                // Check if tag already exists
                if (tagsContainer.querySelector(`[data-value="${enumId}"]`)) {
                    return;
                }
                
                const tag = document.createElement('div');
                tag.className = 'custom-multiselect-tag';
                tag.dataset.value = enumId;
                tag.innerHTML = `
                    <span>${escapeHtml(value)}</span>
                    <button type="button" class="custom-multiselect-tag-remove" aria-label="Remove ${escapeHtml(value)}">
                        <i class="fas fa-times"></i>
                    </button>
                `;
                
                tag.querySelector('.custom-multiselect-tag-remove').addEventListener('click', (e) => {
                    e.stopPropagation();
                    removeTag(enumId, value);
                });
                
                tagsContainer.appendChild(tag);
            }
            
            function removeTag(enumId, value) {
                const tag = tagsContainer.querySelector(`[data-value="${enumId}"]`);
                if (tag) {
                    tag.remove();
                }
                
                // Update selected state
                selectedEnumIds = selectedEnumIds.filter(id => id !== enumId);
                const listItem = dropdownList.querySelector(`[data-value="${enumId}"]`);
                if (listItem) {
                    listItem.classList.remove('selected');
                }
                const option = input.querySelector(`option[value="${enumId}"]`);
                if (option) {
                    option.selected = false;
                }
                
                updatePlaceholder();
            }
            
            function updatePlaceholder() {
                if (selectedEnumIds.length === 0) {
                    dropdownInput.textContent = 'Select values';
                    dropdownInput.classList.add('placeholder');
                } else {
                    dropdownInput.textContent = '';
                    dropdownInput.classList.remove('placeholder');
                }
            }
            
            // Toggle dropdown
            dropdownInput.addEventListener('click', (e) => {
                e.stopPropagation();
                const isOpen = dropdownList.style.display === 'block';
                dropdownList.style.display = isOpen ? 'none' : 'block';
                if (!isOpen) {
                    dropdownInput.classList.add('active');
                } else {
                    dropdownInput.classList.remove('active');
                }
            });
            
            // Close dropdown when clicking outside
            document.addEventListener('click', (e) => {
                if (!multiselectContainer.contains(e.target)) {
                    dropdownList.style.display = 'none';
                    dropdownInput.classList.remove('active');
                }
            });
            
            // Keyboard navigation
            dropdownInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    dropdownInput.click();
                }
            });
            
            // Assemble components
            dropdownWrapper.appendChild(dropdownInput);
            dropdownWrapper.appendChild(dropdownList);
            multiselectContainer.appendChild(tagsContainer);
            multiselectContainer.appendChild(dropdownWrapper);
            multiselectContainer.appendChild(input);
            
            wrapper.appendChild(multiselectContainer);
            updatePlaceholder();
        } else {
            input = document.createElement('input');
            input.type = 'text';
            input.className = 'form-input';
            // Set value - empty string will allow placeholder to show
            input.value = (defaults.value != null && defaults.value !== '') ? defaults.value : '';
        }

        if (input && metadata && metadata.id != null) {
            input.setAttribute('data-cf-metadata-id', String(metadata.id));
        }

        if (input && !wrapper.contains(input)) {
            wrapper.appendChild(input);
        }

        // Set placeholder for input fields that support it
        // Placeholder should appear in the input box for both new and existing objects when editing
        // Backend sends it as "placeholder" (from Placeholder_Text column)
        const placeholderText = metadata.placeholder || metadata.placeholderText || '';
        if (placeholderText && String(placeholderText).trim() !== '' && dataType !== 'checkbox' && dataType !== 'date' && dataType !== 'time') {
            if (input) {
                // Check if it's an input or textarea element
                if (input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement) {
                    const trimmedPlaceholder = String(placeholderText).trim();
                    // Set placeholder attribute - this will show when input is empty
                    input.setAttribute('placeholder', trimmedPlaceholder);
                    input.placeholder = trimmedPlaceholder;
                }
            }
        }
        
        if (metadata.description) {
            const hint = document.createElement('div');
            hint.className = 'custom-field-hint';
            hint.textContent = metadata.description;
            wrapper.appendChild(hint);
        }

        const error = document.createElement('div');
        error.className = 'custom-field-error';
        error.style.display = 'none';
        wrapper.appendChild(error);

        if (mode === 'view') {
            if (input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement || input instanceof HTMLSelectElement) {
                input.disabled = true;
                input.classList.add('is-readonly');
            }
        }

        return { wrapper, input, error };
    }

    function createFieldState(metadata, wrapper, input, error, isExistingObject, hasSavedData) {
        const dataType = (metadata.type || '').toLowerCase();
        const isMandatory = Boolean(metadata.mandatory);

        function showError(message) {
            if (!error) return;
            error.textContent = message;
            error.style.display = message ? 'block' : 'none';
        }

        function clearError() {
            showError('');
        }

        function resolveInput() {
            if (!wrapper || !metadata || metadata.id == null) {
                return input;
            }
            const selector = `[data-cf-metadata-id="${String(metadata.id)}"]`;
            const liveInput = wrapper.querySelector(selector);
            return liveInput || input;
        }

        function getValue() {
            const currentInput = resolveInput();
            if (!currentInput) {
                return { metadataId: metadata.id, value: null, enumId: null };
            }
            if (dataType === 'dropdown') {
                const value = currentInput.value ? parseInt(currentInput.value, 10) : null;
                return { metadataId: metadata.id, value: null, enumId: Number.isInteger(value) ? value : null };
            }
            if (dataType === 'multiselect') {
                const selectedOptions = Array.from(currentInput.selectedOptions || []);
                const enumIds = selectedOptions
                    .filter(opt => opt.value && opt.value !== '')
                    .map(opt => parseInt(opt.value, 10))
                    .filter(id => Number.isInteger(id));
                return { metadataId: metadata.id, value: null, enumIds: enumIds };
            }
            if (dataType === 'checkbox') {
                return { metadataId: metadata.id, value: currentInput.checked ? 'true' : 'false', enumId: null };
            }
            const val = (currentInput.value || '').trim();
            return { metadataId: metadata.id, value: val.length ? val : null, enumId: null };
        }

        function validate() {
            clearError();
            const current = getValue();
            if (dataType === 'percentage' && current.value != null && current.value !== '') {
                const numericValue = Number(current.value);
                if (!Number.isFinite(numericValue) || numericValue < 0 || numericValue > 100) {
                    showError('Percentage must be between 0 and 100');
                    return false;
                }
            }
            if (!isMandatory) return true;
            if (dataType === 'dropdown') {
                if (current.enumId == null) {
                    showError('Please select a value');
                    return false;
                }
                return true;
            }
            if (dataType === 'multiselect') {
                if (!current.enumIds || current.enumIds.length === 0) {
                    showError('Please select at least one value');
                    return false;
                }
                return true;
            }
            if (dataType === 'checkbox') {
                return true;
            }
            if (current.value == null || current.value === '') {
                showError('This field is required');
                return false;
            }
            return true;
        }

        return { metadata, input, error, getValue, validate, showError, clearError };
    }

    async function initForm(options = {}) {
        const facetId = options.facetId;
        if (!facetId) throw new Error('facetId is required');
        
        const container = typeof options.containerId === 'string'
            ? document.getElementById(options.containerId)
            : options.container;
        if (!container) return null;

        const mode = options.mode || 'create';
        const objectId = options.objectId != null ? options.objectId : null;

        container.innerHTML = '';
        container.classList.add('custom-field-container');

        try {
            const metadataList = await fetchMetadata(facetId, objectId);
            if (!metadataList || metadataList.length === 0) {
                const customFieldsSection = getCustomFieldsSectionElement(container);
                setSectionVisibility(customFieldsSection, false);
                return {
                    facetId,
                    container,
                    getValues: () => [],
                    validate: () => true,
                    async saveValues() {}
                };
            }

            let valueMap = {};
            if (objectId != null) {
                try {
                    const values = await fetchValues(facetId, objectId);
                    valueMap = {};
                    // Group values by metadataId, handling multiselect (multiple enumIds)
                    values.forEach(entry => {
                        const metadataId = entry.metadataId;
                        if (!valueMap[metadataId]) {
                            valueMap[metadataId] = {
                                metadataId: metadataId,
                                enumId: entry.enumId != null ? entry.enumId : null,
                                value: entry.value != null ? entry.value : null,
                                enumIds: []
                            };
                        }
                        // For multiselect, accumulate enum IDs
                        if (entry.enumId != null) {
                            const existingEnumIds = valueMap[metadataId].enumIds || [];
                            if (!existingEnumIds.includes(entry.enumId)) {
                                existingEnumIds.push(entry.enumId);
                            }
                            valueMap[metadataId].enumIds = existingEnumIds;
                        }
                    });
                } catch (_) {
                    valueMap = {};
                }
            }

            const fieldStates = [];
            const isExistingObject = objectId != null;
            let renderedFieldCount = 0;
            metadataList.forEach(meta => {
                const metadataId = meta.id || meta.ID;
                if (!metadataId) return;
                const valueData = valueMap[metadataId] || {
                    enumId: meta.currentEnumId != null ? meta.currentEnumId : null,
                    value: meta.currentValue != null ? meta.currentValue : null
                };
                const hasSavedData = valueMap[metadataId] && (
                    (valueMap[metadataId].value != null && valueMap[metadataId].value !== '' && String(valueMap[metadataId].value).trim() !== '') ||
                    valueMap[metadataId].enumId != null ||
                    (valueMap[metadataId].enumIds && Array.isArray(valueMap[metadataId].enumIds) && valueMap[metadataId].enumIds.length > 0)
                );
                const { wrapper, input, error } = createFieldElement(meta, valueData, mode, isExistingObject);
                container.appendChild(wrapper);
                fieldStates.push(createFieldState(meta, wrapper, input, error, isExistingObject, hasSavedData));
                renderedFieldCount++;
            });

            const customFieldsSection = getCustomFieldsSectionElement(container);
            setSectionVisibility(customFieldsSection, renderedFieldCount > 0);

            if (renderedFieldCount === 0) {
                return {
                    facetId,
                    container,
                    getValues: () => [],
                    validate: () => true,
                    async saveValues() {}
                };
            }

            function collectValues() {
                const values = [];
                fieldStates.forEach(state => {
                    const valueData = state.getValue();
                    // Do not re-apply metadata defaults here. Defaults are already shown in the UI
                    // (coerceDefault / createFieldElement). Re-injecting on empty would override a
                    // user who cleared a mandatory field and would bypass validation.

                    // Handle multiselect - expand enumIds array into separate entries
                    if (valueData.enumIds && Array.isArray(valueData.enumIds)) {
                        if (valueData.enumIds.length === 0) {
                            // No selection - add empty entry if not mandatory
                            values.push({
                                metadataId: valueData.metadataId,
                                value: null,
                                enumId: null
                            });
                        } else {
                            // Add one entry per selected enum ID
                            valueData.enumIds.forEach(enumId => {
                                values.push({
                                    metadataId: valueData.metadataId,
                                    value: null,
                                    enumId: enumId
                                });
                            });
                        }
                    } else {
                        // Regular field (dropdown, text, checkbox, etc.)
                        values.push(valueData);
                    }
                });
                return values;
            }

            return {
                facetId,
                container,
                getValues: collectValues,
                validate() {
                    let valid = true;
                    fieldStates.forEach(state => {
                        if (!state.validate()) valid = false;
                    });
                    return valid;
                },
                async saveValues(objectIdOverride) {
                    const targetObjectId = objectIdOverride != null ? objectIdOverride : objectId;
                    if (targetObjectId == null) {
                        throw new Error('objectId is required to save custom field values');
                    }
                    let firstError = '';
                    let allValid = true;
                    fieldStates.forEach(state => {
                        if (!state.validate()) {
                            allValid = false;
                            if (!firstError && state.error && state.error.textContent) {
                                firstError = state.error.textContent.trim();
                            }
                        }
                    });
                    if (!allValid) {
                        const firstInvalid = fieldStates.find(s => s.error && s.error.style.display !== 'none');
                        const scrollTarget = firstInvalid && firstInvalid.error && firstInvalid.error.parentElement;
                        if (scrollTarget && typeof scrollTarget.scrollIntoView === 'function') {
                            try {
                                scrollTarget.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                            } catch (e) { /* ignore */ }
                        }
                        throw new Error(firstError || 'Please fix custom field errors before saving');
                    }
                    const values = collectValues();
                    await saveValues(facetId, targetObjectId, values);
                }
            };
        } catch (error) {
            console.error('Error initializing custom fields form:', error);
            return null;
        }
    }

    async function renderViewSection(options = {}) {
        const facetId = options.facetId;
        const objectId = options.objectId;
        if (!facetId || objectId == null) {
            throw new Error('facetId and objectId are required');
        }

        const container = typeof options.containerId === 'string'
            ? document.getElementById(options.containerId)
            : options.container || null;
        const title = options.title || 'CUSTOM FIELDS';

        try {
            const metadataList = await fetchMetadata(facetId, objectId);
            const values = await fetchValues(facetId, objectId);
            
            // Group values by metadataId, handling multiselect (multiple enumIds)
            // Only include entries that have actual saved values (not empty strings)
            const valueMap = {};
            values.forEach(entry => {
                const metadataId = entry.metadataId;
                // Only process entries that have actual saved data
                const hasValue = entry.value != null && entry.value !== '' && String(entry.value).trim() !== '';
                const hasEnumId = entry.enumId != null;
                
                if (hasValue || hasEnumId) {
                    if (!valueMap[metadataId]) {
                        valueMap[metadataId] = {
                            metadataId: metadataId,
                            enumId: entry.enumId != null ? entry.enumId : null,
                            value: hasValue ? entry.value : null,
                            enumIds: []
                        };
                    }
                    // For multiselect, accumulate enum IDs
                    if (entry.enumId != null) {
                        const existingEnumIds = valueMap[metadataId].enumIds || [];
                        if (!existingEnumIds.includes(entry.enumId)) {
                            existingEnumIds.push(entry.enumId);
                        }
                        valueMap[metadataId].enumIds = existingEnumIds;
                    }
                }
            });

            if (!metadataList.length) {
                return '';
            }

            let itemsHtml = '';
            metadataList.forEach(meta => {
                const metadataId = meta.id || meta.ID;
                const dataType = (meta.type || '').toLowerCase();
                const valueEntry = valueMap[metadataId];
                let valueHtml = '';
                let placeholderUsed = false;

                // Only consider it saved data if there's an actual value saved in the database
                // Empty strings, null, or undefined should not count as saved data
                const hasSavedData = valueEntry && (
                    (valueEntry.value != null && valueEntry.value !== '' && String(valueEntry.value).trim() !== '') ||
                    valueEntry.enumId != null ||
                    (valueEntry.enumIds && Array.isArray(valueEntry.enumIds) && valueEntry.enumIds.length > 0)
                );
                
                // For dropdown and multiselect, display selected values as text
                if (dataType === 'dropdown' || dataType === 'multiselect') {
                    const enumValues = meta.enumValues || [];
                    const isMultiselect = dataType === 'multiselect';
                    let displayValues = [];
                    
                    if (hasSavedData) {
                        if (isMultiselect && valueEntry.enumIds && Array.isArray(valueEntry.enumIds)) {
                            // Get all selected values for multiselect
                            valueEntry.enumIds.forEach(enumId => {
                                const match = enumValues.find(ev => {
                                    const optionId = ev.id || ev.ID;
                                    return optionId === enumId;
                                });
                                if (match) {
                                    const optionValue = match.enumValue || match.EnumValue || match.name || '';
                                    displayValues.push(optionValue);
                                }
                            });
                        } else if (!isMultiselect && valueEntry.enumId != null) {
                            // Get selected value for single dropdown
                            const match = enumValues.find(ev => {
                                const optionId = ev.id || ev.ID;
                                return optionId === valueEntry.enumId;
                            });
                            if (match) {
                                const optionValue = match.enumValue || match.EnumValue || match.name || '';
                                displayValues.push(optionValue);
                            }
                        }
                    }
                    // Note: For old objects without saved data, we show "not specified" below
                    
                    // Display as text (comma-separated for multiselect)
                    if (displayValues.length > 0) {
                        valueHtml = displayValues.map(v => escapeHtml(v)).join(isMultiselect ? ', ' : '');
                    } else {
                        // No saved data - show "not specified" for old objects
                        valueHtml = '<span class="empty">Not specified</span>';
                    }
                } else if (hasSavedData) {
                    if (dataType === 'checkbox') {
                        valueHtml = (valueEntry.value === true || String(valueEntry.value).toLowerCase() === 'true') ? 'Yes' : 'No';
                    } else if (dataType === 'percentage') {
                        valueHtml = escapeHtml(valueEntry.value) + ' <i class="fas fa-percent cf-percentage-view-icon"></i>';
                    } else {
                        valueHtml = escapeHtml(valueEntry.value);
                    }
                } else {
                    // For old objects without saved data, show "not specified"
                    valueHtml = '<span class="empty">Not specified</span>';
                }

                const label = escapeHtml(meta.displayName || 'Custom Field');
                itemsHtml += `
                    <div class="view-item">
                        <div class="view-label">${label}</div>
                        <div class="view-value">${valueHtml}</div>
                    </div>
                `;
            });

            const sectionHtml = `
                <div class="view-section custom-fields-view-section">
                    <div class="section-title">${escapeHtml(title)}</div>
                    ${itemsHtml}
                </div>
            `;

            if (container) {
                container.querySelectorAll('.custom-fields-view-section').forEach(el => el.remove());
                container.insertAdjacentHTML('beforeend', sectionHtml);
            }
            return sectionHtml;
        } catch (error) {
            console.error('Error rendering custom fields view:', error);
            return '';
        }
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /**
     * Get facet name from current page URL/path
     * Uses module table to dynamically match URL patterns to facet names
     * @returns {Promise<string|null>} Facet name or null if cannot determine
     */
    async function getFacetNameFromPage() {
        const path = window.location.pathname.toLowerCase();
        
        // Common URL patterns that map to module names
        // These are fallback patterns - the actual facet name comes from module table
        const urlPatterns = [
            'dataset', 'system', 'glossary', 'interface', 'system-interface',
            'capability', 'client', 'legal-entity', 'product', 'policy',
            'process', 'project', 'committee', 'business-area', 'org-unit',
            'people', 'regulation', 'regulator', 'regulatory-theme'
        ];

        // Find matching URL pattern
        let matchedPattern = null;
        for (const pattern of urlPatterns) {
            if (path.includes(pattern)) {
                matchedPattern = pattern;
                break;
            }
        }

        if (!matchedPattern) {
            return null;
        }

        // Normalize pattern to potential module name formats
        const normalizedPatterns = [
            matchedPattern.replace(/-/g, ' ').replace(/\b\w/g, l => l.toUpperCase()),
            matchedPattern.replace(/-/g, '').replace(/\b\w/g, l => l.toUpperCase()),
            matchedPattern.replace(/-/g, ' ').split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
        ];

        // Try to find matching facet in module table
        const facets = await fetchFacetsFromModule();
        for (const normalized of normalizedPatterns) {
            const match = facets.find(f => {
                const name = (f.name || f.id || '').toLowerCase();
                return name === normalized.toLowerCase() || 
                       name.includes(normalized.toLowerCase()) ||
                       normalized.toLowerCase().includes(name);
            });
            if (match) {
                return match.id || match.name;
            }
        }

        // If no match found, return null (caller should handle)
        return null;
    }

    /**
     * Clear facets cache (useful for testing or when modules are updated)
     */
    function clearFacetsCache() {
        facetsCache = null;
        facetsCachePromise = null;
    }

    // Export to global scope
    global.CustomFields = {
        initForm,
        renderViewSection,
        fetchFacetsFromModule,
        mapFacetName,
        getFacetNameFromPage,
        clearFacetsCache
    };

    // Backward compatibility functions
    global.initializeCustomFieldsView = function(facetId, containerId, objectId) {
        setTimeout(() => {
            if (window.CustomFields) {
                window.CustomFields.renderViewSection({
                    facetId: facetId,
                    containerId: containerId,
                    objectId: objectId
                }).catch(error => {
                    console.error(`Failed to render custom fields for ${facetId}:`, error);
                });
            }
        }, 100);
    };

    global.initializeCustomFieldsForm = function(facetId, containerId, mode, objectId) {
        setTimeout(() => {
            if (window.CustomFields) {
                window.CustomFields.initForm({
                    facetId: facetId,
                    containerId: containerId,
                    mode: mode,
                    objectId: objectId
                }).then(context => {
                    window.customFieldsContext = context;
                }).catch(error => {
                    console.error(`Failed to initialize custom fields form for ${facetId}:`, error);
                });
            }
        }, 100);
        return null;
    };

    global.saveCustomFields = function(objectId) {
        if (!window.customFieldsContext || !window.customFieldsContext.saveValues) {
            return Promise.resolve(true);
        }
        return window.customFieldsContext.saveValues(objectId)
            .then(() => true)
            .catch(error => {
                console.error('Error saving custom fields:', error);
                return false;
            });
    };

    global.validateCustomFields = function() {
        if (!window.customFieldsContext || !window.customFieldsContext.validate) {
            return true;
        }
        return window.customFieldsContext.validate();
    };

})(window);

