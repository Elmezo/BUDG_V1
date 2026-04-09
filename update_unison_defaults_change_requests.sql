-- SQL Update to add CHANGE_REQUESTS facet to UNISON_DEFAULTS in app_config table
-- This fixes the error: "Facet not found: CHANGE_REQUESTS for category: change-requests"

-- Method 1: Using JSON_ARRAY_APPEND (for MySQL 5.7+ / MariaDB 10.2+)
-- This adds the CHANGE_REQUESTS facet to the existing facets array
UPDATE `app_config`
SET `definition` = JSON_ARRAY_APPEND(
    `definition`,
    '$.facets',
    JSON_OBJECT(
        'id', 'CHANGE_REQUESTS',
        'visibility', true,
        'activeFields', 'ID, Subject, Summary, Type, Object Type, Object, Status, Created Date, Last Updated Date, Severity, Urgency, Segment'
    )
)
WHERE `config_key` = 'UNISON_DEFAULTS'
AND JSON_SEARCH(`definition`, 'one', 'CHANGE_REQUESTS', NULL, '$.facets[*].id') IS NULL;

-- Method 2: If Method 1 doesn't work, use this approach to manually update the JSON
-- Replace the entire UNISON_DEFAULTS value (backup first!)
/*
UPDATE `app_config`
SET `definition` = '{"facets": [{"id": "DATASET", "visibility": true, "activeFields": "Ref., Name, Definition, Lifecycle, System Short Name"}, {"id": "ATTRIBUTE", "visibility": true, "activeFields": "Ref., Name, Definition, Data Set Name, System Short Name"}, {"id": "SYSTEM", "visibility": true, "activeFields": "Short Name, Description, Type, Lifecycle, Classification, CIA Rating"}, {"id": "GLOSSARY", "visibility": true, "activeFields": "Ref., Name, Type, Definition, Parent Name, Parent Type, KDE, Lifecycle"}, {"id": "DATAQUALITY", "visibility": false, "activeFields": ""}, {"id": "PEOPLE", "visibility": true, "activeFields": "First Name, Last Name, Email, Function, Org Unit"}, {"id": "ROLE", "visibility": true, "activeFields": "Role, Full Name, Object Type, Object, Role Accepted"}, {"id": "BUSINESS_AREA", "visibility": true, "activeFields": "Name, Parent, Description"}, {"id": "LEGAL_ENTITY", "visibility": true, "activeFields": "Short Name, Parent Short Name, Description"}, {"id": "CLIENT", "visibility": true, "activeFields": "Name, Parent, Description, Lifecycle"}, {"id": "COMMITTEE", "visibility": true, "activeFields": "Ref., Name, Parent, Description"}, {"id": "POLICY", "visibility": false, "activeFields": "Ref., Name, Parent Name, Description, Lifecycle"}, {"id": "PROCESS", "visibility": false, "activeFields": "Ref., Name, Parent Name, Description"}, {"id": "INTERFACE", "visibility": false, "activeFields": "Ref., Name, Description, Source System Short Name, Target System Short Name, Automation, Frequency, Lifecycle"}, {"id": "CAPABILITY", "visibility": false, "activeFields": "Ref., Name, Parent, Description"}, {"id": "PRODUCT", "visibility": false, "activeFields": "Ref., Name, Parent, Description, Axon Status"}, {"id": "ORG_UNIT", "visibility": false, "activeFields": "Ref., Name, Parent, Description, Axon Status"}, {"id": "GEOGRAPHY", "visibility": false, "activeFields": "Name, Parent, Description"}, {"id": "REGULATION", "visibility": false, "activeFields": "Ref., Name, Parent, Description"}, {"id": "REGULATOR", "visibility": false, "activeFields": "Name, Description, Short Name"}, {"id": "REGULATORY_THEME", "visibility": true, "activeFields": "Ref., Name, Parent, Description"}, {"id": "ACTIVE_TASKS", "visibility": true, "activeFields": "Name, Title, Object Type, Object, Due Date, Due In (days), Owner"}, {"id": "CHANGE_REQUESTS", "visibility": true, "activeFields": "ID, Subject, Summary, Type, Object Type, Object, Status, Created Date, Last Updated Date, Severity, Urgency, Segment"}], "lastUpdated": 40986}'
WHERE `config_key` = 'UNISON_DEFAULTS';
*/

-- Verify the update worked:
-- SELECT JSON_EXTRACT(`definition`, '$.facets') FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS';
-- Or check if CHANGE_REQUESTS exists:
-- SELECT JSON_SEARCH(`definition`, 'one', 'CHANGE_REQUESTS', NULL, '$.facets[*].id') FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS';

