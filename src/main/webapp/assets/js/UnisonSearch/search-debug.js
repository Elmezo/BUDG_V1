// Debug helpers (ported from original search.js)

window.debugGlossaryData = async function() {
    try {
        const data = await fetchCategoryData('glossary', '');
        if (data && data.length > 0) {
            const columns = getColumnsForCategory('glossary', data);
            const aliasColumn = columns.find(col => col.key === 'alias_names');
            generateTable(data, columns, 'glossary');
        }
    } catch (error) {
        // Silent fail
    }
};

window.debugSystemData = async function() {
    try {
        const data = await fetchCategoryData('system', '');
        if (data && data.length > 0) {
            const columns = getColumnsForCategory('system', data);
            const ciaColumn = columns.find(col => col.key === 'CIA_Rating');
            generateTable(data, columns, 'system');
        }
    } catch (error) {
        // Silent fail
    }
};

window.debugAllCategories = async function() {
    const categories = ['system', 'dataset', 'attribute', 'glossary', 'people', 'interface', 'orgunit', 'process', 'project', 'product', 'policy', 'business-area', 'capability', 'legal-entity', 'client', 'committee'];
    for (const category of categories) {
        try {
            const data = await fetchCategoryData(category, '');
            if (data && data.length > 0) {
                const firstRow = data[0];
                const allKeys = Object.keys(firstRow);
                const columns = getColumnsForCategory(category, data);
                const columnKeys = columns.map(col => col.key);

                const missingColumns = allKeys.filter(key => !columnKeys.includes(key));
                if (missingColumns.length > 0) {
                } else {
                }
            } else {
            }
        } catch (error) {
        }
    }
};

window.debugCategory = async function(category) {
    try {
        const data = await fetchCategoryData(category, '');
        if (data && data.length > 0) {
            const firstRow = data[0];
            const allKeys = Object.keys(firstRow);
            const columns = getColumnsForCategory(category, data);
            const columnKeys = columns.map(col => col.key);

            const missingColumns = allKeys.filter(key => !columnKeys.includes(key));
            if (missingColumns.length > 0) {
            } else {
            }
            generateTable(data, columns, category);
        } else {
        }
    } catch (error) {
    }
};

window.testSystemAPI = async function() {
    try {
        const response = await fetch('/UnisonSearch/system', { headers: { 'Accept': 'application/json' }, credentials: 'include' });
        if (!response.ok) { return; }
        const data = await response.json();
        if (data && data.length > 0) {
            const firstRow = data[0];
        }
    } catch (error) {
        // Silent fail
    }
};

window.testGlossaryAPI = async function() {
    try {
        const response = await fetch('/UnisonSearch/glossary', { headers: { 'Accept': 'application/json' }, credentials: 'include' });
        if (!response.ok) {return; }
        const data = await response.json();
        if (data && data.length > 0) {
            const firstRow = data[0];
            const columns = getColumnsForCategory('glossary', data);
            const formatTypeColumn = columns.find(col => col.key === 'format_type');
            generateTable(data, columns, 'glossary');
        } else {
        }
    } catch (error) {
    }
};

window.testFormatTypeField = async function() {
    try {
        const data = await fetchCategoryData('glossary', '');
        if (data && data.length > 0) {
            const formatTypeStats = { 'hasFormatType': 0, 'emptyFormatType': 0, 'nullFormatType': 0, 'undefinedFormatType': 0 };
            data.forEach((row, index) => {
                const formatType = row.format_type;
                if (formatType && formatType !== '' && formatType !== 'undefined') {
                    formatTypeStats.hasFormatType++;
                    if (index < 5) { /* sample row */ }
                } else if (formatType === null) formatTypeStats.nullFormatType++; 
                else if (formatType === undefined) formatTypeStats.undefinedFormatType++; 
                else formatTypeStats.emptyFormatType++;
            });
            const searchResults = performLocalSearch(data, 'Code');
            const columns = getColumnsForCategory('glossary', data);
            const formatTypeColumn = columns.find(col => col.key === 'format_type');
            if (formatTypeColumn) { /* format_type column found */ }
            generateTable(data, columns, 'glossary');
        } else {
        }
    } catch (error) {
    }
};

window.forceShowFormatType = async function() {
    showEmptyColumns = true;
    await loadCategoryData('glossary');
    setTimeout(() => {
        const table = document.querySelector('.search-table');
        if (table) {
            const headers = Array.from(table.querySelectorAll('th'));
            const formatTypeHeader = headers.find(th => th.getAttribute('data-column') === 'format_type');
            if (formatTypeHeader) {
                // format_type column is now visible
            } else {
                // format_type column still not visible
            }
        } else {
        }
    }, 1000);
};

window.forceReloadGlossary = async function() {
    showEmptyColumns = true;
    await loadCategoryData('glossary');
    setTimeout(() => { checkHiddenColumns('glossary'); }, 1000);
};

window.debugFormatType = async function() {
    const data = await fetchCategoryData('glossary', '');
    if (!data || data.length === 0) {return; }
    const formatTypeStats = { 'Code': 0, 'undefined': 0, 'null': 0, 'empty': 0, 'other': 0 };
    data.forEach((row, index) => {
        const value = row.format_type;
        if (value === 'Code') formatTypeStats['Code']++;
        else if (value === undefined) formatTypeStats['undefined']++;
        else if (value === null) formatTypeStats['null']++;
        else if (value === '' || value === 'undefined') formatTypeStats['empty']++;
        else { formatTypeStats['other']++;}
    });
    // Process first 10 records
    const columns = getColumnsForCategory('glossary', data);
    const formatTypeColumn = columns.find(col => col.key === 'format_type');
    generateTable(data, columns, 'glossary');
    return formatTypeStats;
};

window.testAliasNamesColumn = function() {
    const table = document.querySelector('.search-table');
    if (!table) {return; }
    const headers = Array.from(table.querySelectorAll('th'));
    const aliasHeader = headers.find(th => th.getAttribute('data-column') === 'alias_names');
    if (aliasHeader) {
        // alias_names header found
    }
    const aliasCells = Array.from(table.querySelectorAll('td[data-column="alias_names"]'));
    if (aliasCells.length > 0) {
        // alias_names cells found
    }
};

window.testCiaRatingColumn = function() {
    const table = document.querySelector('.search-table');
    if (!table) {return; }
    const headers = Array.from(table.querySelectorAll('th'));
    const ciaHeader = headers.find(th => th.getAttribute('data-column') === 'CIA_Rating');
    if (ciaHeader) {
        // CIA_Rating header found
    }
    const ciaCells = Array.from(table.querySelectorAll('td[data-column="CIA_Rating"]'));
    if (ciaCells.length > 0) {
        // CIA_Rating cells found
    }
};

window.testCurrentTable = function() {
    const table = document.querySelector('.search-table');
    if (!table) {return; }
    const headers = Array.from(table.querySelectorAll('th'));
    const headerColumns = headers.map(th => th.getAttribute('data-column'));
    const commonColumns = ['ID', 'Name', 'Ref', 'Description', 'Status', 'Created_By', 'Last_Updated'];
    const missingCommon = commonColumns.filter(col => !headerColumns.includes(col));
    if (missingCommon.length > 0) {
        // Missing common columns
    }
    if (headerColumns.includes('CIA_Rating')) {
        // CIA_Rating column found
    }
    const clickableLinks = Array.from(table.querySelectorAll('.clickable-link'));
    clickableLinks.forEach((link, index) => {
        // Process link
    });
    return { totalColumns: headers.length, columns: headerColumns, labels: headers.map(th => th.textContent.trim()), clickableLinks: clickableLinks.length };
};

window.checkHiddenColumns = async function(category) {
    const data = await fetchCategoryData(category, '');
    if (!data || data.length === 0) {
        return;
    }
    const firstRow = data[0];
    const allKeys = Object.keys(firstRow);
    const columns = getColumnsForCategory(category, data);
    const columnKeys = columns.map(col => col.key);
    const hiddenKeys = allKeys.filter(key => !columnKeys.includes(key));
    const columnsWithData = allKeys.filter(key => data.some(row => { const value = row[key]; return value != null && value !== undefined && value !== 'undefined' && String(value).trim() !== ''; }));
    const emptyColumns = allKeys.filter(key => !columnsWithData.includes(key));
    if (allKeys.includes('format_type')) {
        const formatTypeValues = data.map(row => row.format_type).slice(0, 5);
        if (columnKeys.includes('format_type')) {
            // format_type will be displayed
        }
    }
    allKeys.forEach(key => {
        const sampleValues = data.slice(0, 3).map(row => row[key]).filter(val => val != null);
    });
    return { allKeys, displayedColumns: columnKeys, hiddenColumns: hiddenKeys, columnsWithData, emptyColumns };
};

window.debugTableState = function() {
    const table = document.querySelector('.search-table') || document.querySelector('.data-table');
    if (table) {
        const columns = Array.from(table.querySelectorAll('th')).map(th => th.getAttribute('data-column'));
        const rowCount = table.querySelectorAll('tbody tr').length;
    }
    const checkboxes = document.querySelectorAll('#columnsCheckboxes input[type="checkbox"]');
    checkboxes.forEach(cb => {
        // Process checkbox
    });
};

window.testDynamicTable = async function(category = 'glossary') {
    try {
        const data = await fetchCategoryData(category, '');
        if (data && data.length > 0) {
            const table = createDynamicTable(data, category);
            if (table) {
                const container = document.querySelector('.data-table-wrapper');
                if (container) {
                    container.innerHTML = '';
                    container.appendChild(table);
                    addTableEventListeners();
                }
            } else {
            }
        } else {
        }
    } catch (error) {
    }
};

window.forceShowAllColumns = function() {
    showEmptyColumns = true;
    const activeCategory = getActiveCategoryWithFallback();
    if (activeCategory) {
        loadCategoryData(activeCategory);
    } else {
    }
};

window.checkEmptyColumns = function() {
    const table = document.querySelector('.search-table');
    if (!table) {return; }
    const headers = Array.from(table.querySelectorAll('th'));
    const emptyColumns = [];
    headers.forEach(header => {
        const columnName = header.getAttribute('data-column');
        const cells = Array.from(table.querySelectorAll(`td[data-column="${columnName}"]`));
        const allEmpty = cells.every(cell => { const text = cell.textContent.trim(); return text === '' || text === '&nbsp;'; });
        if (allEmpty) emptyColumns.push(columnName);
    });
    return emptyColumns;
};

window.testAttributeData = async function() {
    try {
        const data = await fetchCategoryData('attribute', '');
        if (data && data.length > 0) {
            const firstRow = data[0];
            const withDatasetId = data.filter(row => row.Dataset_ID || row.dataset_id);
            const withoutDatasetId = data.filter(row => !row.Dataset_ID && !row.dataset_id);
            if (withoutDatasetId.length > 0) {
                // Sample without Dataset_ID
            }
            generateTable(data, null, 'attribute');
        } else {
        }
    } catch (error) {
    }
};

window.testPeopleLinks = async function(category = 'glossary') {
    try {
        const data = await fetchCategoryData(category, '');
        if (data && data.length > 0) {
            const firstRow = data[0];
            const hasCreatedBy = 'Created_By' in firstRow;
            const hasLastUpdatedBy = 'Last_Updated_By' in firstRow;
            if (hasCreatedBy) {
                const createdByValues = data.slice(0, 5).map(row => row.Created_By);
            }
            if (hasLastUpdatedBy) {
                const lastUpdatedByValues = data.slice(0, 5).map(row => row.Last_Updated_By);
            }
            const peopleData = await fetchCategoryData('people', '');
            if (peopleData && peopleData.length > 0) {
                if (hasCreatedBy) {
                    const createdByValue = firstRow.Created_By;
                    if (createdByValue) {
                        const foundPerson = peopleData.find(person => {
                            const fullName = `${person.First_Name || ''} ${person.Last_Name || ''}`.trim();
                            return fullName.toLowerCase() === createdByValue.toLowerCase();
                        });
                        if (foundPerson) {
                            // Person found
                        }
                    }
                }
            }
            const table = createDynamicTable(data, category);
            if (table) {
                const container = document.querySelector('.data-table-wrapper');
                if (container) {
                    container.innerHTML = '';
                    container.appendChild(table);
                    addTableEventListeners();
                }
            }
        } else {
        }
    } catch (error) {
    }
};

window.test500ErrorFixes = async function() {
    const testRow = { 'Created_By': 'John Doe', 'Last_Updated_By': 'Jane Smith', 'System_Short_Name': 'Test System' };
    const glossaryModule = 'glossary';
    const createdByResult = formatCellValue(testRow['Created_By'], 'Created_By', testRow, glossaryModule);
    const lastUpdatedByResult = formatCellValue(testRow['Last_Updated_By'], 'Last_Updated_By', testRow, glossaryModule);
    const systemNameResult = formatCellValue(testRow['System_Short_Name'], 'System_Short_Name', testRow, glossaryModule);
    const searchResult = await searchEntityByName('people', 'NonExistentPerson');
    await preloadAllReferenceData();
};

window.testDateFormatting = function() {
    const testDates = ['2025-09-14', '2025-09-14T10:30:00', '2025-09-14 10:30:00', '09/14/2025', 'September 14, 2025', '2025-01-01', '2024-12-31', new Date('2025-09-14')];
    testDates.forEach(date => { const formatted = formatDateDisplay(date);});
    const testFields = ['Created_Date', 'created_date', 'Last_Updated', 'UpdatedDate', 'date', 'Name', 'Status', 'timestamp', 'modified_time'];
    testFields.forEach(field => { const isDate = isDateField(field);});
};

window.testFuzzySearch = async function(category = 'glossary', query = 'test') {
    try {
        const data = await fetchCategoryData(category, query);
        if (data.length > 0) {
            data.forEach((item, index) => {});
        } else {
        }
        const configResponse = await fetch('/UnisonSearch/config/fuzzy-search', { method: 'GET', headers: { 'Accept': 'application/json' }, credentials: 'include' });
        if (configResponse.ok) { const config = await configResponse.json();} else {}
    } catch (error) {
    }
};

window.testFuzzySearchVariations = async function(category = 'glossary') {
    const testQueries = ['test', 'tes', 'data', 'system', 'xyz', 'a', '123'];
    for (const query of testQueries) {
        try {
            const data = await fetchCategoryData(category, query);
            if (data.length > 0) {
                // Sample result
            }
        } catch (error) {
        }
    }
};

window.testRelationshipPath = function(fromCategory, toCategory) {
    const path = findRelationshipPath(fromCategory, toCategory);
    if (path) {
        const relationshipInfo = getRelationshipInfo(fromCategory, toCategory);
    } else {
    }
    return path;
};

window.testAllRelationshipPaths = function() {
    const categories = Object.keys(FRONTEND_RELATIONSHIP_MAP);
    const results = { direct: [], indirect: [], none: [] };
    for (const from of categories) {
        for (const to of categories) {
            if (from === to) continue;
            const path = findRelationshipPath(from, to);
            if (path) {
                const info = getRelationshipInfo(from, to);
                if (info.isDirect) results.direct.push(`${from} → ${to}`); else results.indirect.push(`${from} → ${to} (${info.hops} hops: ${info.path})`);
            } else {
                results.none.push(`${from} → ${to}`);
            }
        }
    }
    results.direct.forEach(r => {
        // Process direct relationship
    });
    results.indirect.slice(0, 20).forEach(r => {
        // Process indirect relationship
    });
    if (results.indirect.length > 20) {
        // More indirect relationships
    }
    if (results.none.length > 0) {
        results.none.slice(0, 10).forEach(r => {
            // Process no relationship
        });
        if (results.none.length > 10) {
            // More no relationships
        }
    }
    return results;
};

window.testRelatedSearch = async function(fromCategory = 'system', toCategory = 'dataset', itemId = null) {
    const path = findRelationshipPath(fromCategory, toCategory);
    if (!path) {return; }
    const relationshipInfo = getRelationshipInfo(fromCategory, toCategory);
    try {
        const sourceData = await fetchCategoryData(fromCategory, '');
        if (!sourceData || sourceData.length === 0) {return; }
        let selectedItem;
        if (itemId) {
            selectedItem = sourceData.find(item => (item.ID || item.id) == itemId);
            if (!selectedItem) {return; }
        } else {
            selectedItem = sourceData[0];
        }
        const itemName = (function() {
            const fields = getCategoryFields(fromCategory);
            if (fields.name) {
                if (Array.isArray(fields.name)) return fields.name.map(field => selectedItem[field] || '').join(' ').trim();
                return selectedItem[fields.name] || '';
            }
            return selectedItem.Name || selectedItem.name || selectedItem.PrimaryName || selectedItem.primaryname || selectedItem.ShortName || selectedItem.shortname || selectedItem.Title || selectedItem.title || `${fromCategory} #${selectedItem.ID || selectedItem.id}`;
        })();
        const itemId2 = selectedItem.ID || selectedItem.id;
        setSelectedItem({ id: itemId2, category: fromCategory, name: itemName });
        const relatedData = await fetchCategoryData(toCategory, '');
        if (relatedData.length > 0) {
            // Related data found
        }
        const relationship = getRelationshipText(toCategory, fromCategory);
        return { sourceItem: { id: itemId2, name: itemName, category: fromCategory }, relatedItems: relatedData, relationship: relationship };
    } catch (error) {
    }
};

window.testDatasetSearch = async function(query = 'd') {
    try {
        const data = await fetchCategoryData('dataset', query);
        if (data.length > 0) {
            data.forEach((item, index) => {});
        } else {
        }
    } catch (error) {
    }
};

window.testDataSetSystemRelationship = async function(datasetId = 1) {
    try {
        const datasetData = await fetchCategoryData('dataset', '');
        const dataset = datasetData.find(d => (d.ID || d.id) == datasetId);
        if (!dataset) {return; }
        const datasetName = dataset.Name || dataset.PrimaryName || `Dataset #${datasetId}`;
        setSelectedItem({ id: datasetId, category: 'data-sets', name: datasetName });
        const systemData = await fetchCategoryData('system', '');
        if (systemData.length > 0) {
            // Related systems found
        }
        if (dataset.MasterSource) {
            const matchingSystem = systemData.find(s => (s.ID || s.id) == dataset.MasterSource);
            if (matchingSystem) {
                // Matching system found
            }
        } else {
            // No MasterSource defined
        }
        return { dataset, relatedSystems: systemData };
    } catch (error) {
    }
};

window.testCategoryNormalization = function() {
    const testCases = ['dataset','datasets','data-set','data-sets','data','Dataset','DATASETS','system','systems','System','SYSTEMS','app','application','attribute','attributes','field','column','ATTRIBUTES','glossary','terms','dictionary','Glossary','people','person','user','employee','staff','interface','integration','connection','orgunit','org-unit','department','division','legal-entity','company','client','customer','committee','board','  dataset  ','DATA SET','data_set','data set','unknown-category','custom-module'];
    const results = {};
    testCases.forEach(category => { const normalized = categoryToModule(category); results[category] = normalized;});
    const groupedResults = {};
    Object.entries(results).forEach(([original, normalized]) => { if (!groupedResults[normalized]) groupedResults[normalized] = []; groupedResults[normalized].push(original); });
    Object.entries(groupedResults).forEach(([normalized, originals]) => {
        // Process grouped result
    });
    return { individualResults: results, groupedResults: groupedResults };
};



