(function() {
    function parseId() {
        // First try URL params (for separate edit pages)
        const urlParams = new URLSearchParams(window.location.search);
        const id = parseInt(urlParams.get('id'), 10);
        if (!Number.isNaN(id)) return id;

        // Fallback to path-based ID (for main view pages)
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('people');
        if (idx === -1 || parts.length < idx + 2) return null;
        const pathId = parseInt(parts[idx + 1], 10);
        return Number.isNaN(pathId) ? null : pathId;
    }

    function populateSelectSimple(selectId, list, getLabel) {
        const el = document.getElementById(selectId);
        if (!el) return;
        el.innerHTML = `<option value="">${window.I18n ? window.I18n.t('placeholder.selectStatus') : 'Select...'}</option>`;
        (Array.isArray(list) ? list : []).forEach(item => {
            const op = document.createElement('option');
            op.value = String(item.id || item.ID);
            op.textContent = getLabel ? getLabel(item) : (item.name || item.Name || item.primaryname || '');
            el.appendChild(op);
        });
    }

	function formatDateForInput(value) {
		if (!value) return '';
		if (value instanceof Date) {
			const year = value.getFullYear();
			const month = String(value.getMonth() + 1).padStart(2, '0');
			const day = String(value.getDate()).padStart(2, '0');
			return `${year}-${month}-${day}`;
		}

		const str = String(value).trim();
		if (!str) return '';

		// Handle values like "2024-05-01 00:00:00"
		const normalized = str.includes('T') || str.includes('Z') ? str : str.replace(' ', 'T');
		const date = new Date(normalized);
		if (!Number.isNaN(date.getTime())) {
			const year = date.getFullYear();
			const month = String(date.getMonth() + 1).padStart(2, '0');
			const day = String(date.getDate()).padStart(2, '0');
			return `${year}-${month}-${day}`;
		}

		// Fallback: if the string already looks like YYYY-MM-DD, return as-is
		const match = str.match(/^(\d{4}-\d{2}-\d{2})/);
		return match ? match[1] : '';
	}

    async function loadPeopleEditLookups() {
      const svc = window.BUDG_API_SERVICE;
      try {
        const [
          statusesRaw, rolesRaw, orgUnitsRaw, employmentTypesRaw, lifecycleStatusesRaw
        ] = await Promise.all([
          svc.getStatusList(),
          svc.getRolesList(),
          svc.getOrgUnits(),
          svc.getEmploymentTypes ? svc.getEmploymentTypes() : Promise.resolve([]),
          svc.getLifecycleStatuses ? svc.getLifecycleStatuses() : Promise.resolve([])
        ]);

        // Normalize every response to a plain array
        const norm = (x) => (Array.isArray(x?.data) ? x.data : Array.isArray(x) ? x : []);
        const statuses = norm(statusesRaw);
        const roles = norm(rolesRaw);
        const orgUnits = norm(orgUnitsRaw);
        const employmentTypes = norm(employmentTypesRaw);
        const lifecycleStatuses = norm(lifecycleStatusesRaw);

        // Keep a global copy for lookups by id
        window._lookups = {
          statuses,
          roles,
          orgUnits,
          employmentTypes,
          lifecycleStatuses,
          // Tiny helpers:
          byId(list, id) {
            const key = (v) => v?.id ?? v?.ID;
            return list.find((v) => String(key(v)) === String(id));
          },
          labelOf(item) {
            return (
              item?.name ||
              item?.Name ||
              item?.primaryname ||
              item?.primary_Name ||
              item?.Primary_Name ||
              ''
            );
          }
        };

        // Debug logging (can be removed in production)
        console.log('Dropdowns loaded:', { statuses: statuses.length, roles: roles.length, employmentTypes: employmentTypes.length, lifecycleStatuses: lifecycleStatuses.length });
        console.log('=== LIFECYCLE STATUSES DEBUG ===');
        console.log('Lifecycle statuses raw response:', lifecycleStatusesRaw);
        console.log('Lifecycle statuses normalized:', lifecycleStatuses);
        console.log('=== END LIFECYCLE DEBUG ===');
        console.log('=== EMPLOYMENT TYPES DEBUG ===');
        console.log('Employment types raw response:', employmentTypesRaw);
        console.log('Employment types normalized:', employmentTypes);
        console.log('=== END EMPLOYMENT TYPES DEBUG ===');

        // Populate dropdowns
        populateSelectSimple('pSystemRole', roles, (x) => x.name || x.primaryname);
        populateSelectSimple('pStatus', statuses, (x) => x.name || x.primaryname);
        populateSelectSimple('pEmploymentType', employmentTypes, (x) => x.name || x.primaryname || x.primary_Name);
        populateSelectSimple('pLifecycle', lifecycleStatuses, (x) => x.name || x.primaryname || x.Primary_Name);

        // ✅ Profile dropdown uses the same roles list (fix for {data: [...]})
        populateSelectSimple('pProfile', roles, (x) => x.primaryname || x.name);

      } catch (err) {
        console.error('Failed to load lookups:', err);
      }
    }

    function initOrgUnitDropdown() {
        const dropdownContainer = document.getElementById('orgUnitSelectDropdown');
        const dropdownDisplay = document.getElementById('pOrgUnitDisplay');
        const dropdownArrow = document.getElementById('orgUnitSelectArrow');
        const dropdownMenu = document.getElementById('orgUnitSelectMenu');
        const dropdownSearch = document.getElementById('orgUnitSelectSearch');
        const dropdownItems = document.getElementById('orgUnitSelectItems');
        const hiddenInput = document.getElementById('pOrgUnitId');

        // Check if all required elements exist
        if (!dropdownContainer || !dropdownDisplay || !dropdownArrow || !dropdownMenu || !dropdownSearch || !dropdownItems) {
            console.error('Org Unit select dropdown elements not found');
            return;
        }

        let allOrgUnits = [];
        let selectedOrgUnit = null;
        let isDropdownOpen = false;
        let currentFocusIndex = -1;

        // Toggle dropdown open/closed
        function toggleDropdown() {
            if (isDropdownOpen) {
                closeDropdown();
            } else {
                openDropdown();
            }
        }

        // Open dropdown and load data if needed
        function openDropdown() {
            isDropdownOpen = true;
            dropdownContainer.classList.add('active');
            dropdownMenu.classList.add('show');

            // Focus on search input when dropdown opens
            setTimeout(() => {
                dropdownSearch.focus();
            }, 100);

            // Load org units if not already loaded
            if (allOrgUnits.length === 0) {
                loadOrgUnits();
            } else {
                populateDropdownItems(allOrgUnits);
            }
        }

        // Close dropdown and reset state
        function closeDropdown() {
            isDropdownOpen = false;
            dropdownContainer.classList.remove('active');
            dropdownMenu.classList.remove('show');
            dropdownSearch.value = '';
            currentFocusIndex = -1;
            clearDropdownItems();
        }

        // Load org units from API
        async function loadOrgUnits() {
            try {
                showDropdownLoading();
                const response = await window.BUDG_API_SERVICE.getOrgUnits();
                if (response && response.data) {
                    allOrgUnits = response.data;
                } else if (Array.isArray(response)) {
                    allOrgUnits = response;
                } else {
                    allOrgUnits = [];
                }
                populateDropdownItems(allOrgUnits);
            } catch (error) {
                console.error('Error loading org units:', error);
                showDropdownError(window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load organization units');
            }
        }

        // Populate dropdown items based on search
        function populateDropdownItems(orgUnits) {
            const searchTerm = dropdownSearch.value.toLowerCase();
            const filteredUnits = orgUnits.filter(unit =>
                (unit.name || unit.Name || '').toLowerCase().includes(searchTerm) ||
                (unit.description || unit.Description || '').toLowerCase().includes(searchTerm)
            );

            if (filteredUnits.length === 0) {
                showDropdownEmpty();
                return;
            }

            dropdownItems.innerHTML = '';
            filteredUnits.forEach((unit, index) => {
                const itemElement = createDropdownItem(unit, index);
                dropdownItems.appendChild(itemElement);
            });
        }

        // Create dropdown item element
        function createDropdownItem(item, index) {
            const itemElement = document.createElement('div');
            itemElement.className = 'select-dropdown-item';
            itemElement.setAttribute('data-index', index);

            const name = item.name || item.Name || item.id;
            const description = item.description || item.Description || '';

            itemElement.innerHTML = `
                <div class="select-dropdown-item-content">
                    <div class="select-dropdown-item-name">
                        <i class="fas fa-building"></i>
                        ${name}
                    </div>
                    ${description ? `<div class="select-dropdown-item-description">${description}</div>` : ''}
                </div>
            `;

            itemElement.addEventListener('click', () => {
                selectOrgUnit(item, itemElement);
            });

            itemElement.addEventListener('mouseenter', () => {
                // Remove previous focus
                const prevFocused = dropdownItems.querySelector('.focused');
                if (prevFocused) {
                    prevFocused.classList.remove('focused');
                }
                // Add focus to current item
                itemElement.classList.add('focused');
                currentFocusIndex = index;
            });

            return itemElement;
        }

        // Handle org unit selection
        function selectOrgUnit(item, itemElement) {
            // Remove previous selection
            const prevSelected = dropdownItems.querySelector('.selected');
            if (prevSelected) {
                prevSelected.classList.remove('selected');
            }

            // Add selection to current item
            itemElement.classList.add('selected');
            selectedOrgUnit = item;

            // Update display value
            dropdownDisplay.value = item.name || item.Name || item.id;

            // Store the selected org unit ID for form submission
            if (hiddenInput) {
                hiddenInput.value = item.id || item.ID;
            }

            // Close dropdown after selection
            closeDropdown();
        }

        // Show loading state in dropdown
        function showDropdownLoading() {
            dropdownItems.innerHTML = `
                <div class="select-dropdown-loading">
                    <i class="fas fa-spinner"></i>
                    ${window.I18n ? window.I18n.t('people.loadingOrgUnits') : 'Loading organization units...'}
                </div>
            `;
        }

        // Show error state in dropdown
        function showDropdownError(message) {
            dropdownItems.innerHTML = `
                <div class="select-dropdown-empty">
                    <i class="fas fa-exclamation-triangle"></i>
                    ${message}
                </div>
            `;
        }

        // Show empty state in dropdown
        function showDropdownEmpty() {
            dropdownItems.innerHTML = `
                <div class="select-dropdown-empty">
                    <i class="fas fa-search"></i>
                    ${window.I18n ? window.I18n.t('people.noOrgUnitsFound') : 'No organization units found'}
                </div>
            `;
        }

        // Clear all dropdown items
        function clearDropdownItems() {
            dropdownItems.innerHTML = '';
        }

        // Handle keyboard navigation in dropdown
        function handleKeyboardNavigation(e) {
            if (!isDropdownOpen) return;

            const items = dropdownItems.querySelectorAll('.select-dropdown-item');

            switch (e.key) {
                case 'ArrowDown':
                    e.preventDefault();
                    currentFocusIndex = Math.min(currentFocusIndex + 1, items.length - 1);
                    updateFocus();
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    currentFocusIndex = Math.max(currentFocusIndex - 1, 0);
                    updateFocus();
                    break;
                case 'Enter':
                    e.preventDefault();
                    if (currentFocusIndex >= 0 && items[currentFocusIndex]) {
                        items[currentFocusIndex].click();
                    }
                    break;
                case 'Escape':
                    e.preventDefault();
                    closeDropdown();
                    break;
            }
        }

        // Update focus in dropdown
        function updateFocus() {
            const items = dropdownItems.querySelectorAll('.select-dropdown-item');
            items.forEach((item, index) => {
                item.classList.toggle('focused', index === currentFocusIndex);
            });
        }

        // Event listeners
        dropdownContainer.addEventListener('click', (e) => {
            e.stopPropagation();
            if (e.target === dropdownContainer || e.target === dropdownDisplay || e.target === dropdownArrow) {
                toggleDropdown();
            }
        });

        dropdownSearch.addEventListener('input', () => {
            populateDropdownItems(allOrgUnits);
        });

        dropdownSearch.addEventListener('keydown', handleKeyboardNavigation);

        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!dropdownContainer.contains(e.target)) {
                closeDropdown();
            }
        });

        // Prevent dropdown from closing when clicking inside it
        dropdownMenu.addEventListener('click', (e) => {
            e.stopPropagation();
        });
    }

    // 👇 هيلبر لاختيار الأوبشن بالاسم لو الـ ID مش متوفر/مش مطابق
    function selectByTextFallback(selectId, wantedText) {
      const el = document.getElementById(selectId);
      if (!el || !wantedText) return false;

      const norm = (s) => String(s).trim().toLowerCase();
      const target = norm(wantedText);

      for (const opt of el.options) {
        if (norm(opt.textContent) === target) {
          el.value = opt.value;
          el.dispatchEvent(new Event('change', { bubbles: true }));
          return true;
        }
      }
      return false;
    }

    async function setDropdownDefaults(id) {
      try {
        let p = await window.BUDG_API_SERVICE.getPersonById(id);
        if (p && p.data) p = p.data;

        const setSel = (i, v) => {
          const el = document.getElementById(i);
          if (!el || v == null || v === '' || v === 'null' || v === 'undefined') return false;
          el.value = String(v);                    // زي الـ Status (ID → value)
          el.dispatchEvent(new Event('change', { bubbles: true }));
          return true;
        };

        // IDs الراجعة من الـ API
        const systemRoleId     = p.System_Role ?? p.system_role;
        const statusId         = p.status_id   ?? p.Status_ID;
        const lifecycleId      = p.lifecycle_id ?? null;
        const employmentTypeId = p.employment_type_id ?? null;

        // Debug logging (can be removed in production)
        console.log('Person data loaded:', { systemRoleId, statusId, lifecycleId, employmentTypeId });
        setSel('pStatus', statusId);
        const sysRoleOK = setSel('pSystemRole', systemRoleId)
                       || selectByTextFallback('pSystemRole', p.system_role_name);
        const profileOK = setSel('pProfile', systemRoleId)
                       || selectByTextFallback('pProfile', p.system_role_name);
        const lifecycleOK = (lifecycleId != null && lifecycleId !== '') 
                         ? setSel('pLifecycle', lifecycleId)
                         : selectByTextFallback('pLifecycle', p.lifecycle_name);
        const empTypeOK = (employmentTypeId != null && employmentTypeId !== '')
                       ? setSel('pEmploymentType', employmentTypeId)
                       : selectByTextFallback('pEmploymentType', p.employment_type_name);
        const orgUnitId = p.Org_Unit_ID ?? p.org_unit_id;
        const displayInput = document.getElementById('pOrgUnitDisplay');
        const hiddenInput  = document.getElementById('pOrgUnitId');

        if (orgUnitId != null) {
          let orgUnitName = p.Org_Unit_Name ?? p.org_unit_name;
          if (!orgUnitName && window._lookups?.orgUnits?.length) {
            const hit = window._lookups.orgUnits.find(u => String(u.id ?? u.ID) === String(orgUnitId));
            orgUnitName = (hit?.name || hit?.Name || '') || '';
          }
          if (displayInput) displayInput.value = orgUnitName || '';
          if (hiddenInput)  hiddenInput.value  = orgUnitId;
        }

      } catch (err) {
        console.error('Failed to set dropdown defaults:', err);
      }
    }


    /**
     * Check if current user can edit this profile
     * Returns: { canEdit: boolean, isOwnProfile: boolean, userRole: string, isSuperAdmin: boolean }
     */
    async function checkEditPermissions(profileId) {
        try {
            // Get current user info
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!meResp.ok) {
                return { canEdit: false, isOwnProfile: false, userRole: null, isSuperAdmin: false };
            }
            
            const me = await meResp.json();
            const currentUserId = me.id || me.ID || me.userId || me.UserID;
            const userRole = (me.role || me.Role || me.userRole || '').toString();
            const normalizedRole = userRole.toLowerCase().trim().replace(/[_\s-]/g, ' ');
            const isSuperAdmin = normalizedRole === 'super admin' || normalizedRole === 'superadmin';
            const isOwnProfile = currentUserId && parseInt(profileId) === parseInt(currentUserId);
            
            // Super Admins can edit any profile, others can only edit their own
            const canEdit = isSuperAdmin || isOwnProfile;
            
            return {
                canEdit,
                isOwnProfile,
                userRole: normalizedRole,
                isSuperAdmin,
                isAdmin: normalizedRole === 'admin' || isSuperAdmin
            };
        } catch (error) {
            console.error('Error checking edit permissions:', error);
            return { canEdit: false, isOwnProfile: false, userRole: null, isSuperAdmin: false };
        }
    }

    /**
     * Apply field restrictions based on user role when editing own profile
     */
    function applyFieldRestrictions(permissions) {
        if (!permissions.isOwnProfile) {
            return; // Only apply restrictions when editing own profile
        }
        
        // Super Admins have no restrictions
        if (permissions.isSuperAdmin) {
            return;
        }
        
        // Admins: disable system_role, org_unit_id, employed_since
        if (permissions.isAdmin) {
            const restrictedFields = ['pProfile', 'pOrgUnitId', 'pEmployedSince'];
            restrictedFields.forEach(fieldId => {
                const field = document.getElementById(fieldId);
                if (field) {
                    field.disabled = true;
                    field.setAttribute('title', 'This field cannot be edited by administrators');
                    // Add visual indicator
                    const label = field.closest('.form-group')?.querySelector('label');
                    if (label) {
                        label.style.opacity = '0.6';
                    }
                }
            });
            
            // Also disable the org unit dropdown display
            const orgUnitDisplay = document.getElementById('pOrgUnitDisplay');
            if (orgUnitDisplay) {
                orgUnitDisplay.style.opacity = '0.6';
                orgUnitDisplay.style.cursor = 'not-allowed';
            }
        } else {
            // Web Users: disable system_role, status_id, org_unit_id, employed_since
            const restrictedFields = ['pProfile', 'pStatus', 'pOrgUnitId', 'pEmployedSince'];
            restrictedFields.forEach(fieldId => {
                const field = document.getElementById(fieldId);
                if (field) {
                    field.disabled = true;
                    field.setAttribute('title', 'This field cannot be edited');
                    // Add visual indicator
                    const label = field.closest('.form-group')?.querySelector('label');
                    if (label) {
                        label.style.opacity = '0.6';
                    }
                }
            });
            
            // Also disable the org unit dropdown display
            const orgUnitDisplay = document.getElementById('pOrgUnitDisplay');
            if (orgUnitDisplay) {
                orgUnitDisplay.style.opacity = '0.6';
                orgUnitDisplay.style.cursor = 'not-allowed';
            }
        }
    }

    async function loadPerson(id) {
        try {
            let p = await window.BUDG_API_SERVICE.getPersonById(id);
            if (p && p.data) p = p.data;

            const setVal = (i, v) => { const el = document.getElementById(i); if (el) el.value = v ?? ''; };
            const setText = (i, v) => { const el = document.getElementById(i); if (el) el.textContent = v ?? ''; };

            // Set user display name in header
            const firstName = p.First_Name || p.first_name || '';
            const lastName = p.Last_Name || p.last_name || '';
            const fullName = `${firstName} ${lastName}`.trim() || 'Unknown User';
            setText('userDisplayName', fullName);

            // Load all person fields
            setVal('pFirstName', firstName);
            setVal('pLastName', lastName);
            setVal('pEmail', p.Email || p.email || '');
            setVal('pDescription', p.Description || p.description || '');
            setVal('pFunctionName', p.Function_Name || p.function_name || '');
            setVal('pFunctionDescription', p.Function_Description || p.function_description || '');
            // Profile will be set later in setDropdownDefaults after roles are loaded
            // Dropdown values will be set in setDropdownDefaults after lookups are loaded

            // Set org unit
            const orgUnitId = p.Org_Unit_ID || p.org_unit_id;
            const orgUnitName = p.Org_Unit_Name || p.org_unit_name || '';
            setVal('pOrgUnitDisplay', orgUnitName);
            setVal('pOrgUnitId', orgUnitId);

            // Load additional fields that might be in the response
            setVal('pCurrentPassword', ''); // Don't load current password
            setVal('pNewPassword', '');
            setVal('pConfirmPassword', '');
            // Dropdown values will be set in setDropdownDefaults after lookups are loaded
            setVal('pLinkedInUrl', p.LinkedIn_URL || p.linkedin_url || '');
            setVal('pTwitter', p.Twitter || p.twitter || '');
            setVal('pLanId', p.LAN_ID || p.lan_id || '');
            setVal('pMobilePhone', p.Mobile_Phone || p.mobile_phone || '');
            setVal('pCompanyName', p.Company_Name || p.company_name || '');
            setVal('pOfficeLocation', p.Office_Location || p.office_location || '');
            setVal('pInternalMailCode', p.Internal_Mail_Code || p.internal_mail_code || '');
			setVal('pOfficePhone', p.Office_Telephone || p.office_telephone || '');
			const employedSinceRaw = p.Employed_Since || p.employed_since;
			setVal('pEmployedSince', formatDateForInput(employedSinceRaw));

        } catch(err) {
            console.error('Failed to load person:', err);
            alert(window.I18n ? window.I18n.t('people.failedToLoadPerson') : 'Failed to load person data');
        }
    }

    function collectPersonFormData() {
        const getValue = (id) => {
            const el = document.getElementById(id);
            return el ? el.value.trim() : '';
        };

        const getSelectValue = (id) => {
            const el = document.getElementById(id);
            const value = el ? el.value : '';
            // Convert to integer if it's a valid number, otherwise return empty string
            return value && !isNaN(value) ? parseInt(value, 10) : value;
        };

        const data = {
            // Required fields
            first_name: getValue('pFirstName'),
            last_name: getValue('pLastName'),
            email: getValue('pEmail'),
            system_role: getSelectValue('pProfile'),
            status_id: getSelectValue('pStatus'),
            lifecycle_id: getSelectValue('pLifecycle'),
            employment_type_id: getSelectValue('pEmploymentType'),
            org_unit_id: getSelectValue('pOrgUnitId'),

            // Optional fields
            description: getValue('pDescription'),
            function_name: getValue('pFunctionName'),
            function_description: getValue('pFunctionDescription'),
            linkedin_url: getValue('pLinkedInUrl'),
            twitter: getValue('pTwitter'),
            lan_id: getValue('pLanId'),
            mobile_phone: getValue('pMobilePhone'),
            company_name: getValue('pCompanyName'),
            office_location: getValue('pOfficeLocation'),
            internal_mail_code: getValue('pInternalMailCode'),
            office_phone: getValue('pOfficePhone'),
            employed_since: getValue('pEmployedSince'),

            // Password fields
            password: getValue('pNewPassword'),
            confirm_password: getValue('pConfirmPassword'),
        };
        
        // Remove null/undefined values to avoid sending restricted fields
        Object.keys(data).forEach(key => {
            if (data[key] === null || data[key] === undefined || data[key] === '') {
                delete data[key];
            }
        });
        
        return data;
    }

    function validatePersonData(data) {
        const requiredFields = [
            { field: 'first_name', label: window.I18n ? window.I18n.t('label.firstName') : 'First Name' },
            { field: 'last_name', label: window.I18n ? window.I18n.t('label.lastName') : 'Last Name' },
            { field: 'email', label: window.I18n ? window.I18n.t('label.email') : 'Email' },
            { field: 'system_role', label: window.I18n ? window.I18n.t('people.profile') : 'Profile' },
            { field: 'status_id', label: window.I18n ? window.I18n.t('label.status') : 'Status' },
            { field: 'lifecycle_id', label: window.I18n ? window.I18n.t('people.lifecycle') : 'Lifecycle' },
            { field: 'employment_type_id', label: window.I18n ? window.I18n.t('people.employeeType') : 'Employment Type' },
            { field: 'org_unit_id', label: window.I18n ? window.I18n.t('people.orgUnit') : 'Org Unit' }
        ];

        const missingFields = requiredFields.filter(field => !data[field.field]);
        
        if (missingFields.length > 0) {
            return {
                isValid: false,
                message: window.I18n ? window.I18n.t('people.validation.requiredFields', {fields: missingFields.map(f => f.label).join(', ')}) : `Please fill in the following required fields: ${missingFields.map(f => f.label).join(', ')}`
            };
        }

        // Validate email format
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(data.email)) {
            return {
                isValid: false,
                message: window.I18n ? window.I18n.t('people.validation.invalidEmail') : 'Please enter a valid email address'
            };
        }
        
        // Validate password if provided
        if (data.password) {
            if (data.password.length < 6) {
                return {
                    isValid: false,
                    message: window.I18n ? window.I18n.t('people.validation.passwordLength') : 'New password must be at least 6 characters long'
                };
            }
            if (data.password !== data.confirm_password) {
                return {
                    isValid: false,
                    message: window.I18n ? window.I18n.t('people.validation.passwordMismatch') : 'Passwords do not match'
                };
            }
        }
        
        return { isValid: true };
    }

async function savePerson(id, closeAfter = false) {
    console.log('=== SAVE BUTTON CLICKED ===');
    console.log('Saving person with ID:', id);
    console.log('Close after save:', closeAfter);

    const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
    const activeTab = document.querySelector('.tab.active');
    const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'about';

    try {
        buttons.forEach(b=>{
            if (b){
                b.disabled = true;
                b.dataset._txt = b.textContent;
                b.textContent = window.I18n ? window.I18n.t('people.saving') : 'Saving...';
            }
        });

        let savedPersonId = id;

        // Save logic depending on the active tab
        if (tabName === 'about') {
            // Sync rich-text editor content back to textareas before reading
            if (typeof syncAdvancedRichTextToTextarea === 'function') {
                syncAdvancedRichTextToTextarea('pDescription');
                syncAdvancedRichTextToTextarea('pFunctionDescription');
            }
            // ✅ Save basic person info
            const personData = collectPersonFormData();
            const validation = validatePersonData(personData);
            if (!validation.isValid) throw new Error(validation.message);

            if (!id || id === 0) {
                const createResponse = await window.BUDG_API_SERVICE.createPerson(personData);
                savedPersonId = createResponse.id;
                
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues && savedPersonId != null) {
                    try {
                        await window.customFieldsContext.saveValues(savedPersonId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
            } else {
                await window.BUDG_API_SERVICE.updatePerson(id, personData);
                
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(id);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
            }
            alert('Person info saved successfully!');
            
            // Refresh header user data if user updated their own profile
            // This ensures the role display in the dropdown is updated
            if (id && window.headerAuthManager && window.headerAuthManager.refreshUserData) {
                try {
                    await window.headerAuthManager.refreshUserData();
                } catch (refreshError) {
                    console.warn('Failed to refresh header user data:', refreshError);
                }
            }
        }
        else if (tabName === 'team') {
            // ✅ Save only team relationships
            const result = await saveTeamRelationships(id, true);
            alert(result ? 'Team relationships saved!' : 'No new team data to save.');
        }
        else if (tabName === 'following') {
            // ✅ (Optional) Save following changes if editable
            alert('Following tab has no editable data to save.');
        }
        else if (tabName === 'activity') {
            // ✅ (Optional) Save notification settings if needed
            alert('Activity data saved (if any).');
        }

        // Release lock after successful save
        await window.LockInitHelper.releaseLock();

        if (closeAfter) {
            window.location.href = `/view/people/${savedPersonId}`;
        }

    } catch (err) {
        console.error('Save error:', err);
        let errorMessage = err?.body?.message || err?.message || 'Failed to save person';
        
        // Check if it's an authorization error
        if (errorMessage.includes('only edit your own profile') || 
            errorMessage.includes('403') || 
            err?.status === 403) {
            errorMessage = 'You can only edit your own profile. Access denied.';
            // Redirect to view page after showing error
            setTimeout(() => {
                window.location.href = `/view/people/${id}`;
            }, 2000);
        }
        
        alert(errorMessage);
    } finally {
        buttons.forEach(b=>{
            if (b){
                b.disabled = false;
                if (b.dataset._txt) b.textContent = b.dataset._txt;
            }
        });
    }
}


    async function saveTeamRelationships(personId, refreshUI = false) {
        console.log('=== SAVE TEAM RELATIONSHIPS CALLED ===');
        console.log('Saving team relationships for person:', personId);
        console.log('Current teamManagementData:', teamManagementData);
        console.log('Current teamReportsData:', teamReportsData);

        const incompleteManagement = teamManagementData.filter(item =>
            item && item.isNew && ((item.id && !item.type) || (!item.id && item.type))
        );
        const incompleteReports = teamReportsData.filter(item =>
            item && item.isNew && ((item.id && !item.type) || (!item.id && item.type))
        );

        if (incompleteManagement.length || incompleteReports.length) {
            throw new Error('Please select both a person and type for each new team row before saving.');
        }

        const managementToSave = teamManagementData.filter(item => item && item.isNew && item.id && item.type);
        const reportsToSave = teamReportsData.filter(item => item && item.isNew && item.id && item.type);

        let saved = false;

        for (const item of managementToSave) {
            try {
                await window.BUDG_API_SERVICE.addManagementRelationship({
                    managerId: item.id,
                    employeeId: personId,
                    relationType: item.type,
                    description: `${item.firstName} ${item.lastName} - ${item.type}`
                });
                item.isNew = false;
                saved = true;
            } catch (error) {
                console.error('Error saving management relationship:', error);
                throw error;
            }
        }

        for (const item of reportsToSave) {
            try {
                await window.BUDG_API_SERVICE.addReportsRelationship({
                    managerId: personId,
                    employeeId: item.id,
                    relationType: item.type,
                    description: `${item.firstName} ${item.lastName} - ${item.type}`
                });
                item.isNew = false;
                saved = true;
            } catch (error) {
                console.error('Error saving reports relationship:', error);
                throw error;
            }
        }

        if (saved && refreshUI) {
            await loadTeamData(personId);
        }

        return saved;
    }

    // Update type from dropdown selection
    window.updateTypeFromDropdown = function(selectElement) {
        const selectedType = selectElement.value;
        const row = selectElement.closest('tr');
        const rowIndex = parseInt(row.dataset.index);
        
        // Get the table headers to map columns correctly
        const table = row.closest('table');
        const section = row.closest('.team-section');
        const sectionTitle = section.querySelector('h3').textContent;
        
        console.log('Type selected:', selectedType, 'Row index:', rowIndex, 'Section:', sectionTitle);
        
        // Update the data array with the selected type
        if ((sectionTitle === 'MANAGEMENT' || sectionTitle === (window.I18n ? window.I18n.t('people.management') : 'MANAGEMENT')) && rowIndex >= 0 && rowIndex < teamManagementData.length) {
            teamManagementData[rowIndex].type = selectedType;
            console.log('Updated management type:', teamManagementData[rowIndex]);
        } else if ((sectionTitle === 'REPORTS' || sectionTitle === (window.I18n ? window.I18n.t('people.reports') : 'REPORTS')) && rowIndex >= 0 && rowIndex < teamReportsData.length) {
            teamReportsData[rowIndex].type = selectedType;
            console.log('Updated reports type:', teamReportsData[rowIndex]);
        }
    }

    // Team functionality
    async function loadTeamData(id) {
        const container = document.getElementById('peopleEditContainer');
        if (!container) return;
        
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n ? window.I18n.t('people.loadingTeamData') : 'Loading team data...'}</div>`;
        
        try {
            const teamData = await window.BUDG_API_SERVICE.getPersonTeam(id);
            
            // Debug logging
            console.log('Team data received:', teamData);
            
            const management = teamData && Array.isArray(teamData.management) ? teamData.management : [];
            const reports = teamData && Array.isArray(teamData.reports) ? teamData.reports : [];
            
            // Store data in arrays for tracking
            teamManagementData = management.map((item) => normalizeTeamMember(item, 'Direct report'));
            teamReportsData = reports.map((item) => normalizeTeamMember(item, 'Direct report'));
            
            // If no data exists, add empty rows for adding new members
            if (teamManagementData.length === 0) {
                teamManagementData.push({
                    relationshipId: null,
                    id: null,
                    firstName: '',
                    lastName: '',
                    email: '',
                    functionName: '',
                    orgUnitName: '',
                    telephone: '',
                    mobile: '',
                    type: '',
                    isNew: true
                });
            }
            if (teamReportsData.length === 0) {
                teamReportsData.push({
                    relationshipId: null,
                    id: null,
                    firstName: '',
                    lastName: '',
                    email: '',
                    functionName: '',
                    orgUnitName: '',
                    telephone: '',
                    mobile: '',
                    type: '',
                    isNew: true
                });
            }
            
            const teamHtml = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('card.team') : 'TEAM'}</div>
                    </div>
                    <div class="people-content">
                        ${createTeamSection(window.I18n ? window.I18n.t('people.management') : 'MANAGEMENT', teamManagementData, ['Manager', 'Function', 'Org Unit', 'Email', 'Telephone', 'Mobile', 'Type', 'Action'])}
                        ${createTeamSection(window.I18n ? window.I18n.t('people.reports') : 'REPORTS', teamReportsData, ['Name', 'Function', 'Org Unit', 'Email', 'Telephone', 'Mobile', 'Type', 'Action'])}
                    </div>
                </div>
            `;
            
            container.innerHTML = teamHtml;
            
            // Load all persons into dropdowns
            loadAllPersonsIntoDropdowns();
        } catch (e) {
            console.error('Failed to load team data:', e);
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('card.team') : 'TEAM'}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load team data'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    function createTeamSection(title, data, columns) {
        const count = data.length;
        const sectionHtml = `
            <div class="team-section">
                <div class="team-section-header">
                    <h3>${title}</h3>
                    <div class="team-section-actions">
                        <i class="fas fa-cog"></i>
                        <i class="fas fa-chevron-down"></i>
                </div>
                </div>
                    <div class="team-table" style="width: 100%; overflow-x: auto;">
                    <table id="${title.toLowerCase()}Table" class="team-table-content" style="width: 100%; min-width: 1200px; table-layout: auto;">
                            <thead>
                                <tr>
                                ${columns.map(col => `<th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 120px;">${col}</th>`).join('')}
                                </tr>
                            </thead>
                            <tbody>
                            ${data.map((item, index) => createTeamRow(item, columns, index)).join('')}
                            </tbody>
                        </table>
                </div>
            </div>
        `;
        return sectionHtml;
    }

    function createTeamRow(item, columns, index) {
        const isNewRow = item.isNew;
        const getCellValue = (item, column) => {
            if (isNewRow) {
                // For new rows, show input fields
            switch (column) {
                case 'Manager':
                case 'Name':
                    return `<div class="person-dropdown-container" style="position: relative;">
                        <select class="form-select person-dropdown-select" style="width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 4px;"
                                onchange="selectPersonFromDropdown(this)">
                            <option value="">${window.I18n ? window.I18n.t('people.selectPerson') : 'Select person...'}</option>
                        </select>
                    </div>`;
                case 'Function':
                        return `<input type="text" class="form-input" placeholder="${window.I18n ? window.I18n.t('people.function') : 'Function'}" readonly style="background: #f9fafb;">`;
                case 'Org Unit':
                        return `<input type="text" class="form-input" placeholder="${window.I18n ? window.I18n.t('people.orgUnit') : 'Org Unit'}" readonly style="background: #f9fafb;">`;
                case 'Email':
                        return `<input type="email" class="form-input" placeholder="${window.I18n ? window.I18n.t('label.email') : 'Email'}" readonly style="background: #f9fafb;">`;
                case 'Telephone':
                        return `<input type="tel" class="form-input" placeholder="${window.I18n ? window.I18n.t('people.telephone') : 'Telephone'}" readonly style="background: #f9fafb;">`;
                case 'Mobile':
                        return `<input type="tel" class="form-input" placeholder="${window.I18n ? window.I18n.t('people.mobile') : 'Mobile'}" readonly style="background: #f9fafb;">`;
                case 'Type':
                        const selectedType = item.type || '';
                        return `<select class="form-select type-select" name="type" style="width: 100%;" onchange="updateTypeFromDropdown(this)">
                            <option value="">Select Type...</option>
                            <option value="Direct report" ${selectedType === 'Direct report' ? 'selected' : ''}>Direct report</option>
                            <option value="Indirect report" ${selectedType === 'Indirect report' ? 'selected' : ''}>Indirect report</option>
                        </select>`;
                    case 'Action':
                        return `<div class="btn-group" role="group">
                            <button type="button" 
                                    class="btn btn-success btn-sm" 
                                    onclick="addNewTeamMemberAfter(${index})"
                                    title="Add team member after this row">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" 
                                    class="btn btn-danger btn-sm" 
                                    onclick="deleteTeamMember(${index})"
                                    title="Delete team member">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>`;
                default:
                    return '';
            }
            } else {
                // For existing rows, show data
            switch (column) {
                case 'Manager':
                case 'Name':
                        const fullName = `${item.firstName || ''} ${item.lastName || ''}`.trim();
                        const personId = item.id || item.ID;
                        const functionName = item.functionName || '';
                        return `<div class="team-member" style="display: flex; align-items: center; gap: 8px;">
                            <i class="fas fa-user" style="color: #6b7280;"></i>
                            <div>
                                <a href="/view/people/${personId}" class="team-member-link" style="color: #059669; text-decoration: none; font-weight: 500;">${escapeHtml(fullName)}</a>
                                ${functionName ? `<div style="font-size: 0.875rem; color: #6b7280; margin-top: 2px;">${escapeHtml(functionName)}</div>` : ''}
                            </div>
                        </div>`;
                case 'Function':
                        return escapeHtml(item.functionName || '');
                case 'Org Unit':
                        return escapeHtml(item.orgUnitName || '');
                case 'Email':
                        return escapeHtml(item.email || '');
                case 'Telephone':
                        return escapeHtml(item.telephone || '');
                case 'Mobile':
                        return escapeHtml(item.mobile || '');
                case 'Type':
                        return escapeHtml(item.type || '');
                    case 'Action':
                        return `<div class="btn-group" role="group">
                            <button type="button" 
                                    class="btn btn-success btn-sm" 
                                    onclick="addNewTeamMemberAfter(${index})"
                                    title="Add team member after this row">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" 
                                    class="btn btn-danger btn-sm" 
                                    onclick="deleteTeamMember(${index})"
                                    title="Delete team member">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>`;
                default:
                    return '';
                }
            }
        };

        return `<tr data-team-id="${item.relationshipId || item.relationship_id || ''}" data-index="${index}">
            ${columns.map(col => `<td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${getCellValue(item, col)}</td>`).join('')}
        </tr>`;
    }

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    // Team management data tracking (similar to stakeholder pattern)
    let teamManagementData = [];
    let teamReportsData = [];

    function normalizeTeamMember(raw, defaultType = '') {
        if (!raw) {
            return {
                relationshipId: null,
                id: null,
                firstName: '',
                lastName: '',
                email: '',
                functionName: '',
                orgUnitId: null,
                orgUnitName: '',
                telephone: '',
                mobile: '',
                type: defaultType || '',
                isNew: false
            };
        }

        const relationType = raw.relationType ?? raw.Relation_Type ?? '';
        const normalizedType = raw.type ? raw.type : (relationType || defaultType || '');
        return {
            ...raw,
            relationshipId: raw.relationshipId ?? raw.relationship_id ?? raw.Relationship_ID ?? null,
            id: raw.id ?? raw.ID ?? null,
            firstName: raw.firstName ?? raw.First_Name ?? '',
            lastName: raw.lastName ?? raw.Last_Name ?? '',
            email: raw.email ?? raw.Email ?? '',
            functionName: raw.functionName ?? raw.Function_Name ?? '',
            orgUnitId: raw.orgUnitId ?? raw.Org_Unit_ID ?? null,
            orgUnitName: raw.orgUnitName ?? raw.Org_Unit_Name ?? '',
            telephone: raw.telephone ?? raw.Telephone ?? '',
            mobile: raw.mobile ?? raw.Mobile ?? '',
            type: normalizedType,
            isNew: false
        };
    }

    // Add new team member row after specific index (stakeholder pattern)
    window.addNewTeamMemberAfter = function(index) {
        console.log('addNewTeamMemberAfter called with index:', index);
        
        // Find the button that was clicked to determine which section
        const clickedButton = event.target.closest('button');
        const section = clickedButton.closest('.team-section');
        const sectionTitle = section.querySelector('h3').textContent;
        console.log('Current section:', sectionTitle);
        
        const newTeamMember = {
            relationshipId: null,
            id: null,
            firstName: '',
            lastName: '',
            email: '',
            functionName: '',
            orgUnitName: '',
            telephone: '',
            mobile: '',
            type: '',
            isNew: true
        };
        
        if (sectionTitle === 'MANAGEMENT' || sectionTitle === (window.I18n ? window.I18n.t('people.management') : 'MANAGEMENT')) {
            teamManagementData.splice(index + 1, 0, newTeamMember);
        } else if (sectionTitle === 'REPORTS') {
            teamReportsData.splice(index + 1, 0, newTeamMember);
        }
        
        // Re-render the table
        renderTeamTable();
        console.log('Added new team member row after index:', index, 'in section:', sectionTitle);
    };

    // Delete team member by index (stakeholder pattern)
    window.deleteTeamMember = async function(index) {
        console.log('deleteTeamMember called with index:', index);
        
        // Find the button that was clicked to determine which section
        const clickedButton = event.target.closest('button');
        const section = clickedButton.closest('.team-section');
        const sectionTitle = section.querySelector('h3').textContent;
        console.log('Current section:', sectionTitle);
        
        let teamMember = null;
        if (sectionTitle === 'MANAGEMENT' && index >= 0 && index < teamManagementData.length) {
            teamMember = teamManagementData[index];
        } else if (sectionTitle === 'REPORTS' && index >= 0 && index < teamReportsData.length) {
            teamMember = teamReportsData[index];
        }
        
        if (teamMember) {
            const confirmMessage = `Are you sure you want to delete "${teamMember.firstName || teamMember.lastName || 'this team member'}"?`;
            
            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (confirmed) {
                // If it's an existing relationship (has relationshipId), delete from database
                if (teamMember.relationshipId) {
                    try {
                        await window.BUDG_API_SERVICE.removeTeamRelationship(teamMember.relationshipId);
                        console.log('Deleted relationship from database:', teamMember.relationshipId);
                    } catch (error) {
                        console.error('Error deleting relationship from database:', error);
                        alert('Error deleting relationship from database: ' + error.message);
                        return;
                    }
                }
                
                // Remove from local arrays
                if (sectionTitle === 'MANAGEMENT' || sectionTitle === (window.I18n ? window.I18n.t('people.management') : 'MANAGEMENT')) {
                    teamManagementData.splice(index, 1);
                } else if (sectionTitle === 'REPORTS' || sectionTitle === (window.I18n ? window.I18n.t('people.reports') : 'REPORTS')) {
                    teamReportsData.splice(index, 1);
                }
                
                // Re-render the table
                renderTeamTable();
                console.log('Deleted team member at index:', index, 'from section:', sectionTitle);
            }
        }
    };

    // Re-render team table with current data
    function renderTeamTable() {
        const container = document.getElementById('peopleEditContainer');
        if (!container) return;
        
        const teamHtml = `
            <div class="people-section">
                <div class="people-header">
                    <div class="people-title">TEAM</div>
                </div>
                <div class="people-content">
                    ${createTeamSection('MANAGEMENT', teamManagementData, ['Manager', 'Function', 'Org Unit', 'Email', 'Telephone', 'Mobile', 'Type', 'Action'])}
                    ${createTeamSection('REPORTS', teamReportsData, ['Name', 'Function', 'Org Unit', 'Email', 'Telephone', 'Mobile', 'Type', 'Action'])}
                </div>
            </div>
        `;
        
        container.innerHTML = teamHtml;
        
        // Load all persons into dropdowns
        loadAllPersonsIntoDropdowns();
    }

    // Load all persons into dropdown selects
    async function loadAllPersonsIntoDropdowns() {
        try {
            const allPersons = await window.BUDG_API_SERVICE.getPeople();
            console.log('All persons loaded:', allPersons);
            
            const dropdowns = document.querySelectorAll('.person-dropdown-select');
            dropdowns.forEach(dropdown => {
                // Clear existing options except the first one
                dropdown.innerHTML = '<option value="">Select person...</option>';
                
                if (allPersons && allPersons.data) {
                    allPersons.data.forEach(person => {
                        const fullName = `${person.first_name || ''} ${person.last_name || ''}`.trim();
                        const email = person.email || '';
                        const option = document.createElement('option');
                        option.value = person.id;
                        option.textContent = `${fullName} (${email})`;
                        option.dataset.personData = JSON.stringify(person);
                        dropdown.appendChild(option);
                    });
                }
            });
        } catch (error) {
            console.error('Error loading all persons:', error);
        }
    }

    // Handle person selection from dropdown
    window.selectPersonFromDropdown = function(selectElement) {
        const selectedOption = selectElement.options[selectElement.selectedIndex];
        if (!selectedOption || !selectedOption.value) return;
        
        const personData = selectedOption.dataset.personData ? JSON.parse(selectedOption.dataset.personData) : {};
        const fullName = `${personData.first_name || ''} ${personData.last_name || ''}`.trim();
        const email = personData.email || '';
        const personId = personData.id;
        
        console.log('Selected person from dropdown:', personData);
        console.log('Full name:', fullName, 'Email:', email, 'ID:', personId);
        
        // Get the row and fill in the data directly
        const row = selectElement.closest('tr');
        const inputs = row.querySelectorAll('input, select');
        const rowIndex = parseInt(row.dataset.index);
        
        // Get the table headers to map columns correctly
        const table = row.closest('table');
        const headers = table.querySelectorAll('thead th');
        
        // Determine which section we're in
        const section = row.closest('.team-section');
        const sectionTitle = section.querySelector('h3').textContent;
        
        console.log('Section title:', sectionTitle, 'Row index:', rowIndex);
        
        // Fill in all the fields
        inputs.forEach((input, index) => {
            const columnHeader = headers[index];
            if (!columnHeader) return;
            
            const columnName = columnHeader.textContent.trim();
            console.log('Processing column:', columnName);
            
            switch (columnName.toLowerCase().replace(/\s+/g, '')) {
                case 'manager':
                case 'name':
                    // Keep the dropdown but set the selected value
                    input.value = personId;
                    // Update the display text of the selected option
                    const selectedOption = input.querySelector(`option[value="${personId}"]`);
                    if (selectedOption) {
                        selectedOption.textContent = `${fullName} (${email})`;
                    }
                    break;
                case 'email':
                    input.value = email;
                    break;
                case 'function':
                    const functionValue = personData.function_name || personData.Function_Name || '';
                    input.value = functionValue;
                    console.log('Setting function:', functionValue);
                    break;
                case 'orgunit':
                    const orgUnitValue = personData.org_unit_name || personData.Org_Unit_Name || '';
                    input.value = orgUnitValue;
                    console.log('Setting org unit:', orgUnitValue);
                    break;
                case 'telephone':
                    const telephoneValue = personData.office_telephone || personData.Telephone || '';
                    input.value = telephoneValue;
                    console.log('Setting telephone:', telephoneValue);
                    break;
                case 'mobile':
                    const mobileValue = personData.mobile_telephone || personData.Mobile || '';
                    input.value = mobileValue;
                    console.log('Setting mobile:', mobileValue);
                    break;
            }
        });
        
        // Update the data array with complete information
        if ((sectionTitle === 'MANAGEMENT' || sectionTitle === (window.I18n ? window.I18n.t('people.management') : 'MANAGEMENT')) && rowIndex >= 0 && rowIndex < teamManagementData.length) {
            const existing = teamManagementData[rowIndex] || {};
            teamManagementData[rowIndex] = {
                ...existing,
                id: personId,
                firstName: personData.first_name || personData.First_Name || '',
                lastName: personData.last_name || personData.Last_Name || '',
                email: email,
                functionName: personData.function_name || personData.Function_Name || '',
                orgUnitName: personData.org_unit_name || personData.Org_Unit_Name || '',
                telephone: personData.office_telephone || personData.Telephone || '',
                mobile: personData.mobile_telephone || personData.Mobile || '',
                type: existing.type || '',
                isNew: existing.isNew !== false
            };
            console.log('Updated management data:', teamManagementData[rowIndex]);
        } else if ((sectionTitle === 'REPORTS' || sectionTitle === (window.I18n ? window.I18n.t('people.reports') : 'REPORTS')) && rowIndex >= 0 && rowIndex < teamReportsData.length) {
            const existing = teamReportsData[rowIndex] || {};
            teamReportsData[rowIndex] = {
                ...existing,
                id: personId,
                firstName: personData.first_name || personData.First_Name || '',
                lastName: personData.last_name || personData.Last_Name || '',
                email: email,
                functionName: personData.function_name || personData.Function_Name || '',
                orgUnitName: personData.org_unit_name || personData.Org_Unit_Name || '',
                telephone: personData.office_telephone || personData.Telephone || '',
                mobile: personData.mobile_telephone || personData.Mobile || '',
                type: existing.type || '',
                isNew: existing.isNew !== false
            };
            console.log('Updated reports data:', teamReportsData[rowIndex]);
        }
    };

    // Following functionality
    async function loadFollowingData(id) {
        const container = document.getElementById('peopleEditContainer');
        if (!container) return;

        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n ? window.I18n.t('people.loadingFollowing') : 'Loading following data...'}</div>`;

        try {
            const response = await window.BUDG_API_SERVICE.getPersonFollowing(id);
            const records = Array.isArray(response?.records) ? response.records : [];
            const facets = Array.isArray(response?.facets) ? response.facets : [];

            renderFollowingSection(container, records, facets, {
                title: 'FOLLOWING',
                emptyMessage: 'Not following anything',
                wrap: true
            });
        } catch (e) {
            console.error('Failed to load following data:', e);
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">FOLLOWING</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load following data'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    function renderFollowingSection(container, records, facets, options = {}) {
        const title = options.title || 'FOLLOWING';
        const emptyMessage = options.emptyMessage || 'No data available';
        const wrap = options.wrap !== false;

        const sectionClass = wrap ? 'people-section' : 'view-section';
        const headerClass = wrap ? 'people-header' : 'view-header';
        const contentClass = wrap ? 'people-content' : 'view-content';

        let html = `
            <div class="${sectionClass}">
                <div class="${headerClass}">
                    <div class="people-title">${title}</div>
                    <div class="people-actions">
                        <i class="fas fa-cog"></i>
                        <i class="fas fa-chevron-down"></i>
                    </div>
                </div>
                <div class="${contentClass}">
        `;

        if (records.length === 0) {
            html += `
                <div class="people-empty">
                    <i class="fas fa-info-circle"></i>
                    <span>${emptyMessage}</span>
                </div>
            `;
        } else {
            html += `
                <div class="following-table" style="width: 100%; overflow-x: auto;">
                    <table class="following-table-content" style="width: 100%; min-width: 1200px; table-layout: auto;">
                        <thead>
                            <tr>
                                <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 120px;">Type</th>
                                <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 120px;">Ref.</th>
                                <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 200px;">Name</th>
                                <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 200px;">Description</th>
                                <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 150px;">Interest Type</th>
                                <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 150px;">Include Children</th>
                                <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 100px;">Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${records.map(record => createFollowingRow(record)).join('')}
                        </tbody>
                    </table>
                    <div class="following-table-footer">
                        <span class="record-count">${records.length} record${records.length !== 1 ? 's' : ''}</span>
                    </div>
                </div>
            `;
        }

        html += `
                </div>
            </div>
        `;

        container.innerHTML = html;
    }

    function createFollowingRow(record) {
        const followId = record.followId || record.follow_id;
        const type = record.type || '';
        const reference = record.reference || '';
        const name = record.name || '';
        const description = record.description || '';
        const reason = record.reason || 'I use this item';
        const includeChildren = record.includeChildren ? 'Follow All Children' : 'No';
        
        return `
            <tr data-follow-id="${followId}">
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(type)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(reference)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(name)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(description)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(reason)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">
                    ${escapeHtml(includeChildren)}
                </td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">
                    <button type="button" class="btn btn-danger btn-sm" onclick="removeFollowing(${followId})" title="Remove from following">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }

    // Remove following function
    window.removeFollowing = async function(followId) {
        const id = parseId();
        if (!id) {
            alert('No person ID available');
            return;
        }

        const confirmed = await (typeof window.showConfirmDialog === 'function'
            ? window.showConfirmDialog({ message: 'Are you sure you want to remove this item from following?', type: 'warning' })
            : Promise.resolve(confirm('Are you sure you want to remove this item from following?')));
        if (!confirmed) {
            return;
        }

        try {
            const response = await window.BUDG_API_SERVICE.removeFollowing(id, followId);
            if (response.success) {
                alert('Item removed from following successfully!');
                // Reload the following data
                loadFollowingData(id);
            } else {
                alert('Failed to remove item from following');
            }
        } catch (error) {
            console.error('Error removing following:', error);
            alert('Error removing item from following: ' + (error.message || 'Unknown error'));
        }
    };

    // Activity stream functionality
    async function loadActivityData(id) {
        const container = document.getElementById('peopleEditContainer');
        if (!container) return;

        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n ? window.I18n.t('people.loading') : 'Loading activity stream...'}</div>`;

        try {
            const response = await window.BUDG_API_SERVICE.getPersonActivity(id) || {};
            const notification = response.notification || null;
            const stakeholderRecords = Array.isArray(response.stakeholder) ? response.stakeholder : [];
            const followingRecords = Array.isArray(response.following) ? response.following : [];
            const followingFacets = Array.isArray(response.followingFacets) ? response.followingFacets : [];

            container.innerHTML = buildActivityHtml(notification);

            const stakeholderContainer = container.querySelector('[data-activity-stakeholder]');
            if (stakeholderContainer) {
                renderStakeholderSection(stakeholderContainer, stakeholderRecords);
            }

            const followingContainer = container.querySelector('[data-activity-following]');
            if (followingContainer) {
                renderFollowingSection(followingContainer, followingRecords, followingFacets, {
                    emptyMessage: 'No following activity recorded',
                    wrap: false
                });
            }
        } catch (e) {
            console.error('Failed to load activity data:', e);
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">ACTIVITY STREAM</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load activity data'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    function buildActivityHtml(notification) {
        const notificationValue = notification?.value || 'Please select';
        const updatedAt = notification?.updatedAt || null;
        const updatedBy = notification?.updatedBy || null;

        return `
            <div class="people-section">
                <div class="people-header">
                    <div class="people-title">NOTIFICATION FREQUENCY</div>
                </div>
                <div class="people-content">
                    <div class="form-group" style="display: flex; align-items: center; gap: 16px; margin-bottom: 20px;">
                        <label for="emailFrequency" style="font-weight: 500; min-width: 120px;">Email Frequency:</label>
                        <select id="emailFrequency" class="form-select" style="width: 200px;" onchange="updateNotificationFrequency()">
                            <option value="Please select" ${notificationValue === 'Please select' ? 'selected' : ''}>Please select</option>
                            <option value="Daily" ${notificationValue === 'Daily' ? 'selected' : ''}>Daily</option>
                            <option value="Weekly" ${notificationValue === 'Weekly' ? 'selected' : ''}>Weekly</option>
                            <option value="Monthly" ${notificationValue === 'Monthly' ? 'selected' : ''}>Monthly</option>
                            <option value="Not receiving notification emails" ${notificationValue === 'Not receiving notification emails' ? 'selected' : ''}>Not receiving notification emails</option>
                        </select>
                    </div>
                    ${updatedAt ? `
                        <div class="notification-info" style="font-size: 0.875rem; color: #6b7280; margin-top: 8px;">
                            Last updated: ${new Date(updatedAt).toLocaleString()}
                            ${updatedBy ? ` by ${escapeHtml(updatedBy)}` : ''}
                        </div>
                    ` : ''}
                </div>
            </div>
        `;
    }

    function renderStakeholderSection(container, records) {
        if (records.length === 0) {
            container.innerHTML = `
                <div class="people-empty">
                    <i class="fas fa-info-circle"></i>
                    <span>${window.I18n ? window.I18n.t('people.noStakeholderActivity') : 'No stakeholder activity recorded'}</span>
                </div>
            `;
            return;
        }

        const typeLabel = window.I18n ? window.I18n.t('people.objectType') : 'Type';
        const refLabel = window.I18n ? window.I18n.t('people.ref') : 'Ref.';
        const nameLabel = window.I18n ? window.I18n.t('people.objectName') : 'Name';

        let html = `
            <div class="stakeholder-table" style="width: 100%; overflow-x: auto;">
                <table class="stakeholder-table-content" style="width: 100%; min-width: 1200px; table-layout: auto;">
                    <thead>
                        <tr>
                            <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 120px;">${typeLabel}</th>
                            <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 120px;">${refLabel}</th>
                            <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 200px;">${nameLabel}</th>
                            <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 200px;">${window.I18n ? window.I18n.t('label.description') : 'Description'}</th>
                            <th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 150px;">${window.I18n ? window.I18n.t('label.role') : 'Role'}</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${records.map(record => createStakeholderRow(record)).join('')}
                    </tbody>
                </table>
            </div>
        `;

        container.innerHTML = html;
    }

    function createStakeholderRow(record) {
        const type = record.type || '';
        const reference = record.reference || '';
        const name = record.name || '';
        const description = record.description || '';
        const role = record.role || '';
        
        return `
            <tr>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(type)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(reference)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(name)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(description)}</td>
                <td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap; vertical-align: top;">${escapeHtml(role)}</td>
            </tr>
        `;
    }

    // Update notification frequency function
    window.updateNotificationFrequency = async function() {
        const id = parseId();
        if (!id) {
            alert('No person ID available');
            return;
        }

        const selectElement = document.getElementById('emailFrequency');
        if (!selectElement) {
            console.error('Email frequency select element not found');
            return;
        }

        const frequency = selectElement.value;
        if (frequency === 'Please select') {
            alert('Please select a notification frequency');
            return;
        }

        try {
            const response = await window.BUDG_API_SERVICE.updatePersonNotification(id, frequency);
            if (response.success) {
                alert('Notification frequency updated successfully!');
                // Reload the activity data to show updated information
                loadActivityData(id);
            } else {
                alert('Failed to update notification frequency');
            }
        } catch (error) {
            console.error('Error updating notification frequency:', error);
            alert('Error updating notification frequency: ' + (error.message || 'Unknown error'));
        }
    };

    document.addEventListener('DOMContentLoaded', async function() {
        const editContainer = document.getElementById('peopleEditContainer');
        if (!editContainer) {
            // Not on the edit page; avoid binding edit-specific behaviour.
            return;
        }

        const id = parseId();
        if (!id) {
            console.error('No people ID found');
            return;
        }

        // Initialize lock
        const lockAcquired = await window.LockInitHelper.initializeLock('people', id, 'people');
        if (!lockAcquired) {
            return; // Lock initialization failed, user was redirected
        }


        const isNewPerson = !id || id === 0;

        // Load dropdown data
        await loadPeopleEditLookups();

        // Initialize org unit dropdown
        initOrgUnitDropdown();

        if (isNewPerson) {
            // For new person, set default values and update header
            document.getElementById('userDisplayName').textContent = 'New Person';
            console.log('Initializing new person form');
        } else {
            // Check edit permissions before loading
            const permissions = await checkEditPermissions(id);
            if (!permissions.canEdit) {
                alert('You can only edit your own profile. Access denied.');
                window.location.href = `/view/people/${id}`;
                return;
            }
            
            // Load existing person data
            await loadPerson(id);
            
            // Apply field restrictions based on user role
            setTimeout(() => {
                applyFieldRestrictions(permissions);
            }, 100);
            
            // Initialize custom fields
            if (window.CustomFields) {
                try {
                    window.customFieldsContext = await window.CustomFields.initForm({
                        facetId: 'People',
                        containerId: 'customFieldsContainer',
                        mode: 'edit',
                        objectId: id
                    });
                    console.log('Custom fields initialized:', window.customFieldsContext);
                } catch (error) {
                    console.error('Error initializing custom fields:', error);
                }
            }

            // Set dropdown values after lookups are loaded - with delay to ensure dropdowns are populated
            setTimeout(() => {
                setDropdownDefaults(id);
            }, 500);
        }

        // Wire up action buttons
        const saveBtn = document.getElementById('editSaveBtn');
        const saveCloseBtn = document.getElementById('editSaveCloseBtn');
        const cancelBtn = document.getElementById('editCancelBtn');

        if (saveBtn) saveBtn.addEventListener('click', () => savePerson(id, false));
        if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => savePerson(id, true));
        if (cancelBtn) cancelBtn.addEventListener('click', async () => {
            await window.LockInitHelper.releaseLock();
            if (isNewPerson) {
                window.location.href = '/';
            } else {
                window.location.href = `/view/people/${id}`;
            }
        });

        // Show editor buttons – advanced rich text editor
        const descriptionEditorBtn = document.getElementById('descriptionEditorBtn');
        if (descriptionEditorBtn) {
            descriptionEditorBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('pDescription', descriptionEditorBtn);
            });
        }
        const functionEditorBtn = document.getElementById('functionEditorBtn');
        if (functionEditorBtn) {
            functionEditorBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('pFunctionDescription', functionEditorBtn);
            });
        }

        // Wire up tab functionality
        const tabs = document.querySelectorAll('.tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                // Add active class to clicked tab
                this.classList.add('active');

                const tabName = this.getAttribute('data-tab');
                const container = document.getElementById('peopleEditContainer');
                if (!container) {
                    console.warn('peopleEditContainer not found when switching to tab:', tabName);
                    return;
                }
                
                if (tabName === 'about') {
                    // Reload the form
                    location.reload();
                } else if (tabName === 'team') {
                    loadTeamData(id);
                } else if (tabName === 'following') {
                    loadFollowingData(id);
                } else if (tabName === 'activity') {
                    loadActivityData(id);
                }
            });
        });
    });

})();

