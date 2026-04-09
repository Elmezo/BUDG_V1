var LU_PREFIX = 'adminPanel.operatingModel.licensedUsers';
function luT(key, fallback) {
    if (typeof adminT === 'function') return adminT(LU_PREFIX + '.' + key, fallback);
    return fallback;
}
function luEscape(s) {
    if (s == null) return '';
    var d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}
function luTpl(str, vars) {
    if (!str || !vars) return str;
    var out = str;
    Object.keys(vars).forEach(function (k) {
        out = out.split('{{' + k + '}}').join(String(vars[k]));
    });
    return out;
}

function showLicensedUsersContent(contentArea) {
    if (typeof highlightSubmenuItem === 'function') {
        highlightSubmenuItem('adminPanel.submenu.licensedUsers');
    }
    var h = luEscape;
    var t = luT;
    let allUsers = [];
    let filteredUsers = [];
    let selectedRow = null;
    let groupByColumn = 'none';

    // Column visibility
    const columnVisibility = {
        userType: true,
        firstName: true,
        lastName: true
    };

    // HTML Content
    contentArea.innerHTML = `
        <style>
            .licensed-users-container {
                background: white;
                border-radius: 8px;
                box-shadow: 0 2px 12px rgba(0,0,0,0.08);
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            }

            .licensed-users-header {
                background: linear-gradient(135deg, #4CAF50 0%, #2E7D32 100%);
                padding: 24px;
                display: flex;
                justify-content: space-between;
                align-items: center;
                border-radius: 8px 8px 0 0;
                color: white;
            }

            .licensed-users-header h1 {
                font-size: 18px;
                font-weight: 600;
                color: white;
                margin: 0;
                letter-spacing: 0.5px;
            }

            .licensed-settings-icon {
                position: relative;
                cursor: pointer;
                width: 40px;
                height: 40px;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                transition: all 0.3s ease;
                background: rgba(255,255,255,0.2);
                backdrop-filter: blur(10px);
                border: 2px solid rgba(255,255,255,0.3);
            }

            .licensed-settings-icon:hover {
                background: rgba(255,255,255,0.3);
                border-color: rgba(255,255,255,0.5);
            }

            .licensed-settings-icon svg {
                width: 20px;
                height: 20px;
                fill: white;
                filter: brightness(1.2);
            }

            .licensed-settings-dropdown {
                display: none;
                position: absolute;
                right: 0;
                top: 100%;
                margin-top: 8px;
                background: white;
                border: none;
                border-radius: 12px;
                box-shadow: 0 8px 32px rgba(0,0,0,0.15);
                min-width: 220px;
                z-index: 1000;
                overflow: visible;
                padding: 8px 0;
            }

            .licensed-settings-dropdown.active {
                display: block;
                animation: fadeInUp 0.2s ease-out;
            }

            @keyframes fadeInUp {
                from {
                    opacity: 0;
                    transform: translateY(-10px);
                }
                to {
                    opacity: 1;
                    transform: translateY(0);
                }
            }

            .licensed-dropdown-item {
                padding: 12px 20px;
                cursor: pointer;
                display: flex;
                justify-content: space-between;
                align-items: center;
                position: relative;
                color: #333;
                font-size: 14px;
                transition: all 0.2s ease;
                border: none;
            }

            .licensed-dropdown-item:hover {
                background: linear-gradient(135deg, #f8f9ff 0%, #f0f2ff 100%);
                color: #4CAF50;
            }

            .licensed-dropdown-item:last-child {
                border-bottom: none;
            }

            .licensed-arrow {
                font-size: 10px;
                margin-left: 8px;
                color: #999;
                transition: transform 0.2s ease;
            }

            .licensed-dropdown-item:hover .licensed-arrow {
                color: #4CAF50;
                transform: translateX(2px);
            }

            .licensed-submenu {
                display: none;
                position: absolute;
                right: 100%;
                top: 0;
                background: white;
                border: none;
                border-radius: 12px;
                box-shadow: 0 8px 32px rgba(0,0,0,0.15);
                min-width: 200px;
                padding: 8px 0;
                animation: fadeInLeft 0.2s ease-out;
                margin-left: 4px;
                z-index: 1001;
            }

            @keyframes fadeInLeft {
                from {
                    opacity: 0;
                    transform: translateX(-10px);
                }
                to {
                    opacity: 1;
                    transform: translateX(0);
                }
            }

            .licensed-dropdown-item:hover .licensed-submenu {
                display: block;
            }

            .licensed-submenu-item {
                padding: 12px 20px;
                cursor: pointer;
                display: flex;
                align-items: center;
                gap: 12px;
                color: #333;
                font-size: 14px;
                transition: all 0.2s ease;
                border: none;
            }

            .licensed-submenu-item:hover {
                background: linear-gradient(135deg, #f8f9ff 0%, #f0f2ff 100%);
                color: #4CAF50;
            }

            .licensed-submenu-item input[type="checkbox"] {
                cursor: pointer;
                width: 16px;
                height: 16px;
                accent-color: #4CAF50;
            }

            .licensed-filters {
                padding: 24px;
                background-color: #fafbff;
                display: grid;
                grid-template-columns: repeat(3, 1fr);
                gap: 20px;
                border-bottom: 1px solid #eef2ff;
            }

            .licensed-filter-group {
                display: flex;
                flex-direction: column;
                gap: 8px;
            }

            .licensed-filter-group label {
                font-size: 13px;
                font-weight: 600;
                color: #555;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }

            .licensed-filter-group select,
            .licensed-filter-group input {
                padding: 12px;
                border: 1px solid #e1e5e9;
                border-radius: 8px;
                font-size: 14px;
                transition: all 0.3s ease;
                background: white;
            }

            .licensed-filter-group select:focus,
            .licensed-filter-group input:focus {
                outline: none;
                border-color: #4CAF50;
                box-shadow: 0 0 0 3px rgba(76, 175, 80, 0.1);
            }

            .licensed-table-container {
                padding: 0;
                overflow-x: auto;
            }

            .licensed-users-table {
                width: 100%;
                border-collapse: collapse;
                background: white;
            }

            .licensed-users-table thead {
                background-color: #f8f9ff;
            }

            .licensed-users-table th {
                padding: 16px 20px;
                text-align: left;
                font-weight: 600;
                color: #555;
                border-bottom: 2px solid #eef2ff;
                font-size: 13px;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }

            .licensed-users-table td {
                padding: 16px 20px;
                border-bottom: 1px solid #f0f4f8;
                color: #333;
                font-size: 14px;
            }

            .licensed-users-table tbody tr {
                cursor: pointer;
                transition: all 0.2s ease;
            }

            .licensed-users-table tbody tr:hover {
                background-color: #f8f9ff;
                transform: translateY(-1px);
                box-shadow: 0 2px 8px rgba(0,0,0,0.05);
            }

            .licensed-users-table tbody tr.selected {
                background: linear-gradient(135deg, #f0f4ff 0%, #e8edff 100%);
                border-left: 4px solid #4CAF50;
            }

            .licensed-users-table tbody tr.group-header {
                background: linear-gradient(135deg, #4CAF50 0%, #2E7D32 100%);
                color: white;
                font-weight: 600;
            }

            .licensed-users-table tbody tr.group-header td {
                color: white;
                padding: 12px 20px;
                font-size: 13px;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }

            .licensed-record-count {
                padding: 20px 24px;
                text-align: right;
                color: #666;
                font-size: 13px;
                background: #fafbff;
                border-radius: 0 0 8px 8px;
                border-top: 1px solid #eef2ff;
            }

            /* Custom scrollbar */
            .licensed-table-container::-webkit-scrollbar {
                height: 6px;
            }

            .licensed-table-container::-webkit-scrollbar-track {
                background: #f1f1f1;
                border-radius: 3px;
            }

            .licensed-table-container::-webkit-scrollbar-thumb {
                background: #c1c1c1;
                border-radius: 3px;
            }

            .licensed-table-container::-webkit-scrollbar-thumb:hover {
                background: #a8a8a8;
            }
        </style>

        <div class="licensed-users-container">
            <div class="licensed-users-header">
                <h1>${h(t('pageHeading', 'LICENSED USERS'))}</h1>
                <div class="licensed-settings-icon" id="licensedSettingsBtn">
                    ⚙️
                    <div class="licensed-settings-dropdown" id="licensedSettingsDropdown">
                        <div class="licensed-dropdown-item">
                            ${h(t('columnsMenu', 'Columns'))} <span class="licensed-arrow">▶</span>
                            <div class="licensed-submenu" id="licensedColumnsSubmenu">
                                <div class="licensed-submenu-item">
                                    <input type="checkbox" id="licensed-col-userType" checked>
                                    <label for="licensed-col-userType">${h(t('userType', 'User Type'))}</label>
                                </div>
                                <div class="licensed-submenu-item">
                                    <input type="checkbox" id="licensed-col-firstName" checked>
                                    <label for="licensed-col-firstName">${h(t('firstName', 'First Name'))}</label>
                                </div>
                                <div class="licensed-submenu-item">
                                    <input type="checkbox" id="licensed-col-lastName" checked>
                                    <label for="licensed-col-lastName">${h(t('lastName', 'Last Name'))}</label>
                                </div>
                            </div>
                        </div>
                        <div class="licensed-dropdown-item">
                            ${h(t('groupByMenu', 'Group By'))} <span class="licensed-arrow">▶</span>
                            <div class="licensed-submenu" id="licensedGroupBySubmenu">
                                <div class="licensed-submenu-item" data-group="none">${h(t('groupByNone', 'None'))}</div>
                                <div class="licensed-submenu-item" data-group="userType">${h(t('groupByUserType', 'User Type'))}</div>
                                <div class="licensed-submenu-item" data-group="firstName">${h(t('groupByFirstName', 'First Name'))}</div>
                                <div class="licensed-submenu-item" data-group="lastName">${h(t('groupByLastName', 'Last Name'))}</div>
                            </div>
                        </div>
                        <div class="licensed-dropdown-item" id="licensedExportBtn">
                            ${h(t('export', 'Export'))}
                        </div>
                    </div>
                </div>
            </div>

            <div class="licensed-filters">
                <div class="licensed-filter-group">
                    <label for="licensedUserTypeFilter">${h(t('userType', 'User Type'))}</label>
                    <select id="licensedUserTypeFilter">
                        <option value="">${h(t('all', 'All'))}</option>
                    </select>
                </div>
                <div class="licensed-filter-group">
                    <label for="licensedFirstNameFilter">${h(t('firstName', 'First Name'))}</label>
                    <input type="text" id="licensedFirstNameFilter" placeholder="${h(t('searchFirstNamePlaceholder', 'Search by first name'))}">
                </div>
                <div class="licensed-filter-group">
                    <label for="licensedLastNameFilter">${h(t('lastName', 'Last Name'))}</label>
                    <input type="text" id="licensedLastNameFilter" placeholder="${h(t('searchLastNamePlaceholder', 'Search by last name'))}">
                </div>
            </div>

            <div class="licensed-table-container">
                <table class="licensed-users-table" id="licensedUsersTable">
                    <thead>
                        <tr>
                            <th class="licensed-col-userType">${h(t('userType', 'User Type'))}</th>
                            <th class="licensed-col-firstName">${h(t('firstName', 'First Name'))}</th>
                            <th class="licensed-col-lastName">${h(t('lastName', 'Last Name'))}</th>
                        </tr>
                    </thead>
                    <tbody id="licensedTableBody">
                    </tbody>
                </table>
            </div>

            <div class="licensed-record-count" id="licensedRecordCount">${h(luTpl(t('recordsCount', '{{count}} records'), { count: 0 }))}</div>
        </div>
    `;

    // Fetch data from API
    async function fetchUsers() {
        try {
            const response = await fetch('/api/licensed-users');
            const result = await response.json();

            if (result.success) {
                allUsers = result.data;
                filteredUsers = [...allUsers];
                populateUserTypeFilter();
                renderTable();
            }
        } catch (error) {
            console.error('Error fetching users:', error);
        }
    }

    // Populate User Type filter dropdown
    function populateUserTypeFilter() {
        const select = document.getElementById('licensedUserTypeFilter');
        const userTypes = [...new Set(allUsers.map(u => u.userType).filter(Boolean))];

        userTypes.forEach(type => {
            const option = document.createElement('option');
            option.value = type;
            option.textContent = type;
            select.appendChild(option);
        });
    }

    // Apply filters
    function applyFilters() {
        const userTypeValue = document.getElementById('licensedUserTypeFilter').value.toLowerCase();
        const firstNameValue = document.getElementById('licensedFirstNameFilter').value.toLowerCase();
        const lastNameValue = document.getElementById('licensedLastNameFilter').value.toLowerCase();

        filteredUsers = allUsers.filter(user => {
            const matchUserType = !userTypeValue || (user.userType || '').toLowerCase() === userTypeValue;
            const matchFirstName = !firstNameValue || (user.firstName || '').toLowerCase().includes(firstNameValue);
            const matchLastName = !lastNameValue || (user.lastName || '').toLowerCase().includes(lastNameValue);

            return matchUserType && matchFirstName && matchLastName;
        });

        renderTable();
    }

    // Group data
    function groupData(data, groupBy) {
        if (groupBy === 'none') return data;

        const grouped = {};
        data.forEach(user => {
            const key = user[groupBy] || luT('unknown', 'Unknown');
            if (!grouped[key]) {
                grouped[key] = [];
            }
            grouped[key].push(user);
        });

        return grouped;
    }

    // Render table
    function renderTable() {
        const tbody = document.getElementById('licensedTableBody');
        tbody.innerHTML = '';

        if (groupByColumn === 'none') {
            filteredUsers.forEach((user, index) => {
                const row = createUserRow(user, index);
                tbody.appendChild(row);
            });
        } else {
            const grouped = groupData(filteredUsers, groupByColumn);
            Object.keys(grouped).sort().forEach(groupName => {
                // Group header
                const headerRow = document.createElement('tr');
                headerRow.className = 'group-header';
                headerRow.innerHTML = '<td colspan="3">' + luEscape(groupName) + '</td>';
                tbody.appendChild(headerRow);

                // Group items
                grouped[groupName].forEach((user, index) => {
                    const row = createUserRow(user, index);
                    tbody.appendChild(row);
                });
            });
        }

        updateRecordCount();
        updateColumnVisibility();
    }

    // Create user row
    function createUserRow(user, index) {
        const row = document.createElement('tr');
        row.dataset.index = index;

        row.innerHTML = `
            <td class="licensed-col-userType">${luEscape(user.userType || '')}</td>
            <td class="licensed-col-firstName">${luEscape(user.firstName || '')}</td>
            <td class="licensed-col-lastName">${luEscape(user.lastName || '')}</td>
        `;

        row.addEventListener('click', function() {
            if (selectedRow) {
                selectedRow.classList.remove('selected');
            }
            row.classList.add('selected');
            selectedRow = row;
        });

        return row;
    }

    // Update record count
    function updateRecordCount() {
        document.getElementById('licensedRecordCount').textContent = luTpl(luT('recordsCount', '{{count}} records'), { count: filteredUsers.length });
    }

    // Update column visibility
    function updateColumnVisibility() {
        const table = document.getElementById('licensedUsersTable');
        
        Object.keys(columnVisibility).forEach(col => {
            const elements = table.querySelectorAll(`.licensed-col-${col}`);
            elements.forEach(el => {
                el.style.display = columnVisibility[col] ? '' : 'none';
            });
        });
    }

    // Export to PDF
// Export to PDF - تطبع الجدول بنفس شكل التجميع
function exportToPDF() {
    if (typeof window.jspdf === 'undefined') {
        if (window.showAdminNotification) window.showAdminNotification(luT('pdfLibraryMissing', 'PDF export library not loaded. Please include jsPDF library.'), 'error');
        return;
    }

    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();

    // Add title
    doc.setFontSize(16);
    doc.setTextColor(76, 175, 80);
    doc.text(luT('pdfReportTitle', 'Licensed Users Report'), 14, 15);

    // Add export info
    doc.setFontSize(10);
    doc.setTextColor(100, 100, 100);
    doc.text(luTpl(luT('pdfExportedLine', 'Exported: {{date}} | Records: {{count}}'), {
        date: new Date().toLocaleDateString(),
        count: filteredUsers.length
    }), 14, 22);

    let startY = 30;

    // Prepare headers
    const headers = [];
    if (columnVisibility.userType) headers.push(luT('userType', 'User Type'));
    if (columnVisibility.firstName) headers.push(luT('firstName', 'First Name'));
    if (columnVisibility.lastName) headers.push(luT('lastName', 'Last Name'));

    if (groupByColumn !== 'none') {
        // Grouped export - بنفس شكل التجميع في الواجهة
        const grouped = groupData(filteredUsers, groupByColumn);

        Object.keys(grouped).sort().forEach(groupName => {
            // Add group header
            doc.setFontSize(12);
            doc.setFont(undefined, 'bold');
            doc.setTextColor(76, 175, 80);
            doc.text(groupName.toUpperCase(), 14, startY);
            startY += 8;

            // Prepare group data
            const groupData = [];
            grouped[groupName].forEach(user => {
                const row = [];
                if (columnVisibility.userType) row.push(user.userType || '');
                if (columnVisibility.firstName) row.push(user.firstName || '');
                if (columnVisibility.lastName) row.push(user.lastName || '');
                groupData.push(row);
            });

            // Add table for this group
            doc.autoTable({
                head: [headers],
                body: groupData,
                startY: startY,
                theme: 'grid',
                headStyles: {
                    fillColor: [76, 175, 80],
                    textColor: [255, 255, 255],
                    fontStyle: 'bold'
                },
                styles: {
                    fontSize: 10,
                    cellPadding: 3
                },
                margin: { left: 14 }, // Indent the table
                tableWidth: 180,
                didDrawPage: function(data) {
                    // Update startY for next group
                    startY = data.cursor.y + 15;
                }
            });

            // Update startY for next group
            startY = doc.lastAutoTable.finalY + 15;
        });
    } else {
        // Non-grouped export
        const data = [];
        filteredUsers.forEach(user => {
            const row = [];
            if (columnVisibility.userType) row.push(user.userType || '');
            if (columnVisibility.firstName) row.push(user.firstName || '');
            if (columnVisibility.lastName) row.push(user.lastName || '');
            data.push(row);
        });

        doc.autoTable({
            head: [headers],
            body: data,
            startY: startY,
            theme: 'grid',
            headStyles: {
                fillColor: [76, 175, 80],
                textColor: [255, 255, 255],
                fontStyle: 'bold'
            },
            styles: {
                fontSize: 10,
                cellPadding: 3
            }
        });
    }

    // Add page numbers
    const pageCount = doc.internal.getNumberOfPages();
    for (let i = 1; i <= pageCount; i++) {
        doc.setPage(i);
        doc.setFontSize(8);
        doc.setTextColor(150, 150, 150);
        doc.text(luTpl(luT('pdfPageOf', 'Page {{page}} of {{total}}'), { page: i, total: pageCount }), doc.internal.pageSize.width / 2,
                doc.internal.pageSize.height - 10, { align: 'center' });
    }

    doc.save(`licensed-users-${new Date().getTime()}.pdf`);
}
    // Event Listeners
    document.getElementById('licensedSettingsBtn').addEventListener('click', function(e) {
        e.stopPropagation();
        document.getElementById('licensedSettingsDropdown').classList.toggle('active');
    });

    document.addEventListener('click', function() {
        const dropdown = document.getElementById('licensedSettingsDropdown');
        if (dropdown) {
            dropdown.classList.remove('active');
        }
    });

    document.getElementById('licensedSettingsDropdown').addEventListener('click', function(e) {
        e.stopPropagation();
    });

    // Column checkboxes
    document.querySelectorAll('#licensedColumnsSubmenu input[type="checkbox"]').forEach(checkbox => {
        checkbox.addEventListener('change', function() {
            const col = this.id.replace('licensed-col-', '');
            columnVisibility[col] = this.checked;
            updateColumnVisibility();
        });
    });

    // Group By
    document.querySelectorAll('#licensedGroupBySubmenu .licensed-submenu-item').forEach(item => {
        item.addEventListener('click', function() {
            groupByColumn = this.dataset.group;
            renderTable();
            document.getElementById('licensedSettingsDropdown').classList.remove('active');
        });
    });

    // Export
    document.getElementById('licensedExportBtn').addEventListener('click', function() {
        exportToPDF();
        document.getElementById('licensedSettingsDropdown').classList.remove('active');
    });

    // Filters
    document.getElementById('licensedUserTypeFilter').addEventListener('change', applyFilters);
    document.getElementById('licensedFirstNameFilter').addEventListener('input', applyFilters);
    document.getElementById('licensedLastNameFilter').addEventListener('input', applyFilters);

    // Initialize
    fetchUsers();
}