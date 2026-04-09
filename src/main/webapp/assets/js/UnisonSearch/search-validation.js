// Validation helpers

function validateData(data, category) {
    if (!data) {
        return false;
    }

    if (!Array.isArray(data)) {
        return false;
    }

    if (data.length === 0) {
        return false;
    }

    return true;
}

function validateColumns(columns, category) {
    if (!columns) {
        return false;
    }

    if (!Array.isArray(columns)) {
        return false;
    }

    if (columns.length === 0) {
        return false;
    }

    return true;
}

function validateElement(element, elementName) {
    if (!element) {
        return false;
    }
    return true;
}

function validateRequiredElements() {
    const elements = {
        'tableContainer': tableContainer,
        'searchInput': document.querySelector('.search-main-input'),
        'searchBtn': document.querySelector('.search-action-btn'),
        'settingsBtn': document.getElementById('settingsBtn'),
        'settingsDropdown': document.getElementById('settingsDropdown'),
        'columnsCheckboxes': document.getElementById('columnsCheckboxes')
    };

    const missingElements = [];
    Object.entries(elements).forEach(([name, element]) => {
        if (!element) {
            missingElements.push(name);
        }
    });

    if (missingElements.length > 0) {
        return false;
    }

    return true;
}

function validateCategory(category) {
    if (!category) {
        return false;
    }

    if (typeof category !== 'string') {
        return false;
    }

    if (category.trim() === '') {
        return false;
    }

    return true;
}


