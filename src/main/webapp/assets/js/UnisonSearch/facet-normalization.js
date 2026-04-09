/**
 * Facet Normalization Utility for Frontend
 * Ensures consistency with backend FacetNormalizationUtil.java
 */

/**
 * Normalize facet ID to canonical uppercase form.
 * This matches the backend normalization logic.
 * 
 * @param {string} facetId - The facet ID to normalize
 * @returns {string|null} Canonical facet ID (e.g., "DATASET")
 */
function normalizeFacetIdToCanonical(facetId) {
    if (!facetId || typeof facetId !== 'string') {
        return null;
    }

    // Convert to uppercase and replace hyphens/spaces with underscores
    const normalized = facetId.trim().toUpperCase().replace(/-/g, '_').replace(/\s+/g, '_');

    // Map common variations to canonical forms (must match backend FacetNormalizationUtil.java)
    // This is the SINGLE SOURCE OF TRUTH for frontend facet normalization
    const canonicalMap = {
        // Dataset variations
        'DATA_SETS': 'DATASET',
        'DATA_SET': 'DATASET',
        'DATASETS': 'DATASET',
        'DATA-SETS': 'DATASET',
        'DATA-SET': 'DATASET',
        // Attribute variations
        'ATTRIBUTES': 'ATTRIBUTE',
        'ATTR': 'ATTRIBUTE',
        'FIELD': 'ATTRIBUTE',
        'FIELDS': 'ATTRIBUTE',
        'COLUMN': 'ATTRIBUTE',
        'COLUMNS': 'ATTRIBUTE',
        // System variations
        'SYSTEMS': 'SYSTEM',
        'SYS': 'SYSTEM',
        'APPLICATION': 'SYSTEM',
        'APPLICATIONS': 'SYSTEM',
        'APP': 'SYSTEM',
        'APPS': 'SYSTEM',
        // Glossary variations
        'GLOSSARIES': 'GLOSSARY',
        'DICTIONARY': 'GLOSSARY',
        'DICTIONARIES': 'GLOSSARY',
        'TERM': 'GLOSSARY',
        'TERMS': 'GLOSSARY',
        // People variations
        'PEOPLE': 'PEOPLE',
        'PERSON': 'PEOPLE',
        'PERSONS': 'PEOPLE',
        'USER': 'PEOPLE',
        'USERS': 'PEOPLE',
        'EMPLOYEE': 'PEOPLE',
        'EMPLOYEES': 'PEOPLE',
        'STAFF': 'PEOPLE',
        // Interface variations
        'INTERFACES': 'INTERFACE',
        'INTEGRATION': 'INTERFACE',
        'INTEGRATIONS': 'INTERFACE',
        'CONNECTION': 'INTERFACE',
        'CONNECTIONS': 'INTERFACE',
        // Org Unit variations
        'ORG_UNITS': 'ORG_UNIT',
        'ORG_UNIT': 'ORG_UNIT',
        'ORGUNIT': 'ORG_UNIT',
        'ORG-UNIT': 'ORG_UNIT',
        'ORG-UNITS': 'ORG_UNIT',
        'ORGANIZATION': 'ORG_UNIT',
        'ORGANIZATIONS': 'ORG_UNIT',
        'DEPARTMENT': 'ORG_UNIT',
        'DEPARTMENTS': 'ORG_UNIT',
        // Legal Entity variations
        'LEGAL_ENTITIES': 'LEGAL_ENTITY',
        'LEGAL_ENTITY': 'LEGAL_ENTITY',
        'LEGALENTITY': 'LEGAL_ENTITY',
        'LEGAL-ENTITY': 'LEGAL_ENTITY',
        'LEGAL-ENTITIES': 'LEGAL_ENTITY',
        'LEGAL': 'LEGAL_ENTITY',
        'COMPANY': 'LEGAL_ENTITY',
        'COMPANIES': 'LEGAL_ENTITY',
        // Business Area variations
        'BUSINESS_AREAS': 'BUSINESS_AREA',
        'BUSINESS_AREA': 'BUSINESS_AREA',
        'BUSINESSAREAS': 'BUSINESS_AREA',
        'BUSINESSAREA': 'BUSINESS_AREA',
        'BUSINESS-AREA': 'BUSINESS_AREA',
        'BUSINESS-AREAS': 'BUSINESS_AREA',
        'BUSINESS': 'BUSINESS_AREA',
        // Regulation variations
        'REGULATIONS': 'REGULATION',
        // Geography variations
        'GEOGRAPHIES': 'GEOGRAPHY',
        'GEOGRAPHY': 'GEOGRAPHY',
        'GEO': 'GEOGRAPHY',
        // Regulator variations
        'REGULATORS': 'REGULATOR',
        // Regulatory Theme variations
        'REGULATORY_THEMES': 'REGULATORY_THEME',
        'REGULATORY_THEME': 'REGULATORY_THEME',
        'REGULATORYTHEMES': 'REGULATORY_THEME',
        'REGULATORYTHEME': 'REGULATORY_THEME',
        'REGULATORY-THEME': 'REGULATORY_THEME',
        'REGULATORY-THEMES': 'REGULATORY_THEME',
        'THEME': 'REGULATORY_THEME',
        'THEMES': 'REGULATORY_THEME',
        // Process variations
        'PROCESSES': 'PROCESS',
        // Project variations
        'PROJECTS': 'PROJECT',
        // Product variations
        'PRODUCTS': 'PRODUCT',
        // Policy variations
        'POLICIES': 'POLICY',
        // Capability variations
        'CAPABILITIES': 'CAPABILITY',
        // Client variations
        'CLIENTS': 'CLIENT',
        // Committee variations
        'COMMITTEES': 'COMMITTEE',
        // Requirement variations
        'REQUIREMENTS': 'REQUIREMENT',
        // Data Store variations
        'DATA_STORES': 'DATA_STORE',
        'DATA_STORE': 'DATA_STORE',
        'DATASTORES': 'DATA_STORE',
        'DATASTORE': 'DATA_STORE',
        // Data Quality variations
        'DATA_QUALITY': 'DATAQUALITY',
        'DATAQUALITY': 'DATAQUALITY',
        // Association Origin variations
        'ASSOCIATION_ORIGINS': 'ASSOCIATION_ORIGIN',
        'ASSOCIATION_ORIGIN': 'ASSOCIATION_ORIGIN',
        'ASSOCIATIONORIGINS': 'ASSOCIATION_ORIGIN',
        'ASSOCIATIONORIGIN': 'ASSOCIATION_ORIGIN',
        // Role variations
        'ROLES': 'ROLE',
        // Active Tasks variations
        'ACTIVE_TASKS': 'ACTIVE_TASKS',
        'ACTIVE_TASK': 'ACTIVE_TASKS',
        'ACTIVETASKS': 'ACTIVE_TASKS',
        'ACTIVETASK': 'ACTIVE_TASKS',
        'ACTIVE-TASKS': 'ACTIVE_TASKS',
        'ACTIVE-TASK': 'ACTIVE_TASKS',
        'TASKS': 'ACTIVE_TASKS',
        'TASK': 'ACTIVE_TASKS',
        // Change Request variations
        'CHANGE_REQUESTS': 'CHANGE_REQUESTS',
        'CHANGE_REQUEST': 'CHANGE_REQUESTS',
        'CHANGEREQUESTS': 'CHANGE_REQUESTS',
        'CHANGEREQUEST': 'CHANGE_REQUESTS',
        'CHANGE-REQUESTS': 'CHANGE_REQUESTS',
        'CHANGE-REQUEST': 'CHANGE_REQUESTS'
    };

    return canonicalMap[normalized] || normalized;
}

/**
 * Check if two facet IDs represent the same facet.
 * 
 * @param {string} facetId1 - First facet ID
 * @param {string} facetId2 - Second facet ID
 * @returns {boolean} true if they represent the same facet
 */
function areSameFacet(facetId1, facetId2) {
    const norm1 = normalizeFacetIdToCanonical(facetId1);
    const norm2 = normalizeFacetIdToCanonical(facetId2);

    if (!norm1 || !norm2) {
        return false;
    }

    return norm1 === norm2;
}

// Export for use in other modules
if (typeof window !== 'undefined') {
    window.normalizeFacetIdToCanonical = normalizeFacetIdToCanonical;
    window.areSameFacet = areSameFacet;
}
