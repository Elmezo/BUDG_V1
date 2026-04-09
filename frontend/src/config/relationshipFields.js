/**
 * Relationship Field Configuration
 * Defines field metadata for each relationship type and operation
 */

// ===== ATTRIBUTE RELATIONSHIPS =====
export const ATTRIBUTE_X_ATTRIBUTE_FIELDS = {
  INSERT: [
    { name: 'Sourcing Logic', displayName: 'Sourcing Logic', required: false, dataType: 'STRING', description: 'Sourcing logic description (optional)' },
    { name: 'Sourcing Type', displayName: 'Sourcing Type', required: true, dataType: 'LIST', description: 'Sourcing Type (required, select from dropdown)' },
    { name: 'Target Attribute Ref.', displayName: 'Target Attribute Ref.', required: false, dataType: 'STRING', description: 'Target attribute reference number (optional)' },
    { name: 'Target Attribute Name', displayName: 'Target Attribute Name', required: false, dataType: 'STRING', description: 'Target attribute name (optional)' },
    { name: 'Target Data Set Name', displayName: 'Target Data Set Name', required: false, dataType: 'STRING', description: 'Target data set name for disambiguation (optional)' },
    { name: 'Target System Short Name', displayName: 'Target System Short Name', required: false, dataType: 'STRING', description: 'Target system short name for disambiguation (optional)' },
    { name: 'Source Attribute Ref.', displayName: 'Source Attribute Ref.', required: false, dataType: 'STRING', description: 'Source attribute reference number (optional)' },
    { name: 'Source Attribute Name', displayName: 'Source Attribute Name', required: false, dataType: 'STRING', description: 'Source attribute name (optional)' },
    { name: 'Source Data Set Name', displayName: 'Source Data Set Name', required: false, dataType: 'STRING', description: 'Source data set name for disambiguation (optional)' },
    { name: 'Source System Short Name', displayName: 'Source System Short Name', required: false, dataType: 'STRING', description: 'Source system short name for disambiguation (optional)' },
    { name: 'Scope of Data', displayName: 'Scope of Data', required: false, dataType: 'LIST', description: 'Scope of data (optional dropdown)' },
    { name: 'Interface Name', displayName: 'Interface Name', required: false, dataType: 'STRING', description: 'Interface name (optional)' },
    { name: 'Interface Source System Short Name', displayName: 'Interface Source System Short Name', required: false, dataType: 'STRING', description: 'Interface source system (optional)' },
    { name: 'Interface Target System Short Name', displayName: 'Interface Target System Short Name', required: false, dataType: 'STRING', description: 'Interface target system (optional)' }
  ],
  DELETE: [
    { name: 'Sourcing Logic', displayName: 'Sourcing Logic', required: false, dataType: 'STRING', description: 'Sourcing logic description (optional)' },
    { name: 'Sourcing Type', displayName: 'Sourcing Type', required: true, dataType: 'LIST', description: 'Sourcing Type (required, select from dropdown)' },
    { name: 'Target Attribute Ref.', displayName: 'Target Attribute Ref.', required: false, dataType: 'STRING', description: 'Target attribute reference number (optional)' },
    { name: 'Target Attribute Name', displayName: 'Target Attribute Name', required: false, dataType: 'STRING', description: 'Target attribute name (optional)' },
    { name: 'Target Data Set Name', displayName: 'Target Data Set Name', required: false, dataType: 'STRING', description: 'Target data set name for disambiguation (optional)' },
    { name: 'Target System Short Name', displayName: 'Target System Short Name', required: false, dataType: 'STRING', description: 'Target system short name for disambiguation (optional)' },
    { name: 'Source Attribute Ref.', displayName: 'Source Attribute Ref.', required: false, dataType: 'STRING', description: 'Source attribute reference number (optional)' },
    { name: 'Source Attribute Name', displayName: 'Source Attribute Name', required: false, dataType: 'STRING', description: 'Source attribute name (optional)' },
    { name: 'Source Data Set Name', displayName: 'Source Data Set Name', required: false, dataType: 'STRING', description: 'Source data set name for disambiguation (optional)' },
    { name: 'Source System Short Name', displayName: 'Source System Short Name', required: false, dataType: 'STRING', description: 'Source system short name for disambiguation (optional)' },
    { name: 'Scope of Data', displayName: 'Scope of Data', required: false, dataType: 'LIST', description: 'Scope of data (optional dropdown)' },
    { name: 'Interface Name', displayName: 'Interface Name', required: false, dataType: 'STRING', description: 'Interface name (optional)' },
    { name: 'Interface Source System Short Name', displayName: 'Interface Source System Short Name', required: false, dataType: 'STRING', description: 'Interface source system (optional)' },
    { name: 'Interface Target System Short Name', displayName: 'Interface Target System Short Name', required: false, dataType: 'STRING', description: 'Interface target system (optional)' }
  ]
};

export const ATTRIBUTE_X_PHYSICAL_FIELD_FIELDS = {
  INSERT: [
    { name: 'Attribute Ref.', displayName: 'Attribute Ref.', required: false, dataType: 'STRING', description: 'Attribute reference number (optional)' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: false, dataType: 'STRING', description: 'Attribute name (optional)' },
    { name: 'Attribute Data Set Name', displayName: 'Attribute Data Set Name', required: false, dataType: 'STRING', description: 'Attribute data set name for disambiguation (optional)' },
    { name: 'Attribute System Short Name', displayName: 'Attribute System Short Name', required: false, dataType: 'STRING', description: 'Attribute system short name for disambiguation (optional)' },
    { name: 'Name', displayName: 'Name', required: true, dataType: 'STRING', description: 'Physical field name (required)' },
    { name: 'Field ID', displayName: 'Field ID', required: true, dataType: 'STRING', description: 'Physical field ID (required)' },
    { name: 'Field Type', displayName: 'Field Type', required: true, dataType: 'STRING', description: 'Physical field type (required)' }
  ]
};

// ===== BUSINESS AREA RELATIONSHIPS =====
export const BUSINESS_AREA_X_GLOSSARY_FIELDS = {
  INSERT: [
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary for disambiguation (optional)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Parent Business Area Name', displayName: 'Parent Business Area Name', required: false, dataType: 'STRING', description: 'Parent business area for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ],
  DELETE: [
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary for disambiguation (optional)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Parent Business Area Name', displayName: 'Parent Business Area Name', required: false, dataType: 'STRING', description: 'Parent business area for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ]
};

export const BUSINESS_AREA_X_PROCESS_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process for disambiguation (optional)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Business Area Parent Name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Parent business area for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process for disambiguation (optional)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Business Area Parent Name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Parent business area for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ]
};

export const BUSINESS_AREA_X_SYSTEM_FIELDS = {
  INSERT: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent system for disambiguation (optional)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Business Area Parent Name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Parent business area for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ],
  DELETE: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent system for disambiguation (optional)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Business Area Parent Name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Parent business area for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ]
};

// ===== CAPABILITY RELATIONSHIPS =====
export const CAPABILITY_X_BUSINESS_AREA_FIELDS = {
  INSERT: [
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Business Area Parent Name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Business Area parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_businessarea_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Business Area Parent Name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Business Area parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_businessarea_relationtype.PrimaryName' }
  ]
};

export const CAPABILITY_X_CLIENT_FIELDS = {
  INSERT: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_client_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_client_relationtype.PrimaryName' }
  ]
};

export const CAPABILITY_X_GLOSSARY_FIELDS = {
  INSERT: [
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent Glossary name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Parent Capability Name', displayName: 'Parent Capability Name', required: false, dataType: 'STRING', description: 'Parent Capability name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_glossary_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent Glossary name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Parent Capability Name', displayName: 'Parent Capability Name', required: false, dataType: 'STRING', description: 'Parent Capability name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_glossary_relationtype.PrimaryName' }
  ]
};

export const CAPABILITY_X_LEGAL_ENTITY_FIELDS = {
  INSERT: [
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_legal_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_legal_relationtype.PrimaryName' }
  ]
};

export const CAPABILITY_X_PROCESS_FIELDS = {
  INSERT: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_process_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_process_relationtype.PrimaryName' }
  ]
};

export const CAPABILITY_X_PRODUCT_FIELDS = {
  INSERT: [
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent for disambiguation (optional)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_product_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent for disambiguation (optional)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_product_relationtype.PrimaryName' }
  ]
};

export const CAPABILITY_X_SYSTEM_FIELDS = {
  INSERT: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_system_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'capability_x_system_relationtype.PrimaryName' }
  ]
};

// ===== POLICY RELATIONSHIPS =====
export const POLICY_X_ATTRIBUTE_FIELDS = {
  INSERT: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Attribute Ref.', displayName: 'Attribute Ref.', required: false, dataType: 'STRING', description: 'Attribute reference number (optional)' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: false, dataType: 'STRING', description: 'Attribute name (optional)' },
    { name: 'Attribute Data Set Name', displayName: 'Attribute Data Set Name', required: false, dataType: 'STRING', description: 'Attribute Data Set name (optional, for disambiguation)' },
    { name: 'Attribute System Short Name', displayName: 'Attribute System Short Name', required: false, dataType: 'STRING', description: 'Attribute System short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_attribute_relationtype.PrimaryName' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' }
  ],
  DELETE: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Attribute Ref.', displayName: 'Attribute Ref.', required: false, dataType: 'STRING', description: 'Attribute reference number (optional)' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: false, dataType: 'STRING', description: 'Attribute name (optional)' },
    { name: 'Attribute Data Set Name', displayName: 'Attribute Data Set Name', required: false, dataType: 'STRING', description: 'Attribute Data Set name (optional, for disambiguation)' },
    { name: 'Attribute System Short Name', displayName: 'Attribute System Short Name', required: false, dataType: 'STRING', description: 'Attribute System short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_attribute_relationtype.PrimaryName' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' }
  ]
};

export const POLICY_X_BUSINESS_AREA_FIELDS = {
  INSERT: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Parent Business Area Name', displayName: 'Parent Business Area Name', required: false, dataType: 'STRING', description: 'Parent Business Area name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_businessarea_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Parent Business Area Name', displayName: 'Parent Business Area Name', required: false, dataType: 'STRING', description: 'Parent Business Area name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_businessarea_relationtype.PrimaryName' }
  ]
};

export const POLICY_X_CLIENT_FIELDS = {
  INSERT: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name (optional, for disambiguation)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'client_x_policy_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name (optional, for disambiguation)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'client_x_policy_relationtype.PrimaryName' }
  ]
};

export const POLICY_X_DATA_SET_FIELDS = {
  INSERT: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Data Set Ref.', displayName: 'Data Set Ref.', required: false, dataType: 'STRING', description: 'Data Set reference number (optional)' },
    { name: 'Data Set Name', displayName: 'Data Set Name', required: false, dataType: 'STRING', description: 'Data Set name (optional)' },
    { name: 'Data Set System Short Name', displayName: 'Data Set System Short Name', required: false, dataType: 'STRING', description: 'Data Set System short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_dataset_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Data Set Ref.', displayName: 'Data Set Ref.', required: false, dataType: 'STRING', description: 'Data Set reference number (optional)' },
    { name: 'Data Set Name', displayName: 'Data Set Name', required: false, dataType: 'STRING', description: 'Data Set name (optional)' },
    { name: 'Data Set System Short Name', displayName: 'Data Set System Short Name', required: false, dataType: 'STRING', description: 'Data Set System short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_dataset_relationtype.PrimaryName' }
  ]
};

export const POLICY_X_GLOSSARY_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent Glossary name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_glossary_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent Glossary name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_glossary_relationtype.PrimaryName' }
  ]
};

export const POLICY_X_LEGAL_ENTITY_FIELDS = {
  INSERT: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_legal_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_legal_relationtype.PrimaryName' }
  ]
};

export const POLICY_X_POLICY_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Source Policy Ref.', displayName: 'Source Policy Ref.', required: false, dataType: 'STRING', description: 'Source Policy reference number (optional)' },
    { name: 'Source Policy Name', displayName: 'Source Policy Name', required: false, dataType: 'STRING', description: 'Source Policy name (optional)' },
    { name: 'Source Parent Policy Name', displayName: 'Source Parent Policy Name', required: false, dataType: 'STRING', description: 'Source Parent Policy name (optional, for disambiguation)' },
    { name: 'Target Policy Ref.', displayName: 'Target Policy Ref.', required: false, dataType: 'STRING', description: 'Target Policy reference number (optional)' },
    { name: 'Target Policy Name', displayName: 'Target Policy Name', required: false, dataType: 'STRING', description: 'Target Policy name (optional)' },
    { name: 'Target Parent Policy Name', displayName: 'Target Parent Policy Name', required: false, dataType: 'STRING', description: 'Target Parent Policy name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_policy_relation_type.PrimaryName' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Source Policy Ref.', displayName: 'Source Policy Ref.', required: false, dataType: 'STRING', description: 'Source Policy reference number (optional)' },
    { name: 'Source Policy Name', displayName: 'Source Policy Name', required: false, dataType: 'STRING', description: 'Source Policy name (optional)' },
    { name: 'Source Parent Policy Name', displayName: 'Source Parent Policy Name', required: false, dataType: 'STRING', description: 'Source Parent Policy name (optional, for disambiguation)' },
    { name: 'Target Policy Ref.', displayName: 'Target Policy Ref.', required: false, dataType: 'STRING', description: 'Target Policy reference number (optional)' },
    { name: 'Target Policy Name', displayName: 'Target Policy Name', required: false, dataType: 'STRING', description: 'Target Policy name (optional)' },
    { name: 'Target Parent Policy Name', displayName: 'Target Parent Policy Name', required: false, dataType: 'STRING', description: 'Target Parent Policy name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_policy_relation_type.PrimaryName' }
  ]
};

export const POLICY_X_PROCESS_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_process_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_process_relationtype.PrimaryName' }
  ]
};

export const POLICY_X_PRODUCT_FIELDS = {
  INSERT: [
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent name (optional, for disambiguation)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'product_x_policy_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent name (optional, for disambiguation)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'product_x_policy_relationtype.PrimaryName' }
  ]
};

export const POLICY_X_PROJECT_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Parent Project Name', displayName: 'Parent Project Name', required: false, dataType: 'STRING', description: 'Parent Project name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_project_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference number (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Parent Project Name', displayName: 'Parent Project Name', required: false, dataType: 'STRING', description: 'Parent Project name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'policy_x_project_relationtype.PrimaryName' }
  ]
};

export const POLICY_X_SYSTEM_FIELDS = {
  INSERT: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent policy for disambiguation (optional)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent system for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ],
  DELETE: [
    { name: 'Policy Ref.', displayName: 'Policy Ref.', required: false, dataType: 'STRING', description: 'Policy reference (optional)' },
    { name: 'Policy Name', displayName: 'Policy Name', required: false, dataType: 'STRING', description: 'Policy name (optional)' },
    { name: 'Parent Policy Name', displayName: 'Parent Policy Name', required: false, dataType: 'STRING', description: 'Parent policy for disambiguation (optional)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent system for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ]
};

// ===== PRODUCT X LEGAL ENTITY =====
export const PRODUCT_X_LEGAL_ENTITY_FIELDS = {
  INSERT: [
    { name: 'Product Ref.', displayName: 'Product Ref.', required: false, dataType: 'STRING', description: 'Product reference number (optional)' },
    { name: 'Product Name', displayName: 'Product Name', required: false, dataType: 'STRING', description: 'Product name (optional)' },
    { name: 'Parent Product Name', displayName: 'Parent Product Name', required: false, dataType: 'STRING', description: 'Parent product name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'product_x_legalentity_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Product Ref.', displayName: 'Product Ref.', required: false, dataType: 'STRING', description: 'Product reference number (optional)' },
    { name: 'Product Name', displayName: 'Product Name', required: false, dataType: 'STRING', description: 'Product name (optional)' },
    { name: 'Parent Product Name', displayName: 'Parent Product Name', required: false, dataType: 'STRING', description: 'Parent product name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'product_x_legalentity_relationtype.PrimaryName' }
  ]
};

// ===== PRODUCT X CLIENT =====
export const PRODUCT_X_CLIENT_FIELDS = {
  INSERT: [
    { name: 'Product Ref.', displayName: 'Product Ref.', required: false, dataType: 'STRING', description: 'Product reference number (optional)' },
    { name: 'Product Name', displayName: 'Product Name', required: false, dataType: 'STRING', description: 'Product name (optional)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent for disambiguation (optional)', aliases: ['Parent Product Name'] },
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Parent Client Name', displayName: 'Parent Client Name', required: false, dataType: 'STRING', description: 'Parent client for disambiguation (optional)', aliases: ['Client Parent Name'] },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'product_x_client_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Product Ref.', displayName: 'Product Ref.', required: false, dataType: 'STRING', description: 'Product reference number (optional)' },
    { name: 'Product Name', displayName: 'Product Name', required: false, dataType: 'STRING', description: 'Product name (optional)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent for disambiguation (optional)', aliases: ['Parent Product Name'] },
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Parent Client Name', displayName: 'Parent Client Name', required: false, dataType: 'STRING', description: 'Parent client for disambiguation (optional)', aliases: ['Client Parent Name'] },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'product_x_client_relationtype.PrimaryName' }
  ]
};

// ===== SEGMENT RELATIONSHIPS =====
export const SEGMENT_X_OBJECT_FIELDS = {
  UPDATE: [
    { name: 'Object ID', displayName: 'Object ID', required: true, dataType: 'INTEGER', description: 'Object ID (required)' },
    { name: 'Object Type', displayName: 'Object Type', required: true, dataType: 'LIST', description: 'Object type (required, select from dropdown)', listSource: 'module.primaryname' },
    { name: 'Target Segment', displayName: 'Target Segment', required: true, dataType: 'STRING', description: 'Target segment (required)' }
  ]
};

// ===== PEOPLE RELATIONSHIPS =====
export const PEOPLE_X_PEOPLE_FIELDS = {
  INSERT: [
    { name: 'Manager Email', displayName: 'Manager Email', required: false, dataType: 'STRING', description: 'Manager email (optional)' },
    { name: 'Manager First Name', displayName: 'Manager First Name', required: false, dataType: 'STRING', description: 'Manager first name (optional)' },
    { name: 'Manager Last Name', displayName: 'Manager Last Name', required: false, dataType: 'STRING', description: 'Manager last name (optional)' },
    { name: 'Manager Lan ID', displayName: 'Manager Lan ID', required: false, dataType: 'STRING', description: 'Manager LAN ID (optional)' },
    { name: 'Employee Email', displayName: 'Employee Email', required: false, dataType: 'STRING', description: 'Employee email (optional)' },
    { name: 'Employee First Name', displayName: 'Employee First Name', required: false, dataType: 'STRING', description: 'Employee first name (optional)' },
    { name: 'Employee Last Name', displayName: 'Employee Last Name', required: false, dataType: 'STRING', description: 'Employee last name (optional)' },
    { name: 'Employee Lan ID', displayName: 'Employee Lan ID', required: false, dataType: 'STRING', description: 'Employee LAN ID (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'people_x_people_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Manager Email', displayName: 'Manager Email', required: false, dataType: 'STRING', description: 'Manager email (optional)' },
    { name: 'Manager First Name', displayName: 'Manager First Name', required: false, dataType: 'STRING', description: 'Manager first name (optional)' },
    { name: 'Manager Last Name', displayName: 'Manager Last Name', required: false, dataType: 'STRING', description: 'Manager last name (optional)' },
    { name: 'Manager Lan ID', displayName: 'Manager Lan ID', required: false, dataType: 'STRING', description: 'Manager LAN ID (optional)' },
    { name: 'Employee Email', displayName: 'Employee Email', required: false, dataType: 'STRING', description: 'Employee email (optional)' },
    { name: 'Employee First Name', displayName: 'Employee First Name', required: false, dataType: 'STRING', description: 'Employee first name (optional)' },
    { name: 'Employee Last Name', displayName: 'Employee Last Name', required: false, dataType: 'STRING', description: 'Employee last name (optional)' },
    { name: 'Employee Lan ID', displayName: 'Employee Lan ID', required: false, dataType: 'STRING', description: 'Employee LAN ID (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'people_x_people_relationtype.PrimaryName' }
  ]
};

// ===== PROCESS X CLIENT =====
export const PROCESS_X_CLIENT_FIELDS = {
  INSERT: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name (optional, for disambiguation)', aliases: ['Parent Client Name'] },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'client_x_process_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name (optional, for disambiguation)', aliases: ['Parent Client Name'] },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'client_x_process_relationtype.PrimaryName' }
  ]
};

export const PROCESS_X_DATA_SET_FIELDS = {
  INSERT: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Data Set Ref.', displayName: 'Data Set Ref.', required: false, dataType: 'STRING', description: 'Data Set reference number (optional)' },
    { name: 'Data Set Name', displayName: 'Data Set Name', required: false, dataType: 'STRING', description: 'Data Set name (optional)' },
    { name: 'Data Set System Short Name', displayName: 'Data Set System Short Name', required: false, dataType: 'STRING', description: 'Data Set system short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'process_x_dataset_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Data Set Ref.', displayName: 'Data Set Ref.', required: false, dataType: 'STRING', description: 'Data Set reference number (optional)' },
    { name: 'Data Set Name', displayName: 'Data Set Name', required: false, dataType: 'STRING', description: 'Data Set name (optional)' },
    { name: 'Data Set System Short Name', displayName: 'Data Set System Short Name', required: false, dataType: 'STRING', description: 'Data Set system short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'process_x_dataset_relationtype.PrimaryName' }
  ]
};

export const PROCESS_X_GLOSSARY_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent Glossary name (optional, for disambiguation)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'glossary_x_process_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent Glossary name (optional, for disambiguation)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'glossary_x_process_relationtype.PrimaryName' }
  ]
};

export const PROCESS_X_SYSTEM_INTERFACE_FIELDS = {
  INSERT: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Interface Ref.', displayName: 'Interface Ref.', required: false, dataType: 'STRING', description: 'Interface reference number (optional)' },
    { name: 'Interface Name', displayName: 'Interface Name', required: false, dataType: 'STRING', description: 'Interface name (optional)' },
    { name: 'Interface Source System Short Name', displayName: 'Interface Source System Short Name', required: false, dataType: 'STRING', description: 'Interface source system short name (optional)' },
    { name: 'Interface Target System Short Name', displayName: 'Interface Target System Short Name', required: false, dataType: 'STRING', description: 'Interface target system short name (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, dropdown from process_x_interface_relationtype)', listSource: 'process_x_interface_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Interface Ref.', displayName: 'Interface Ref.', required: false, dataType: 'STRING', description: 'Interface reference number (optional)' },
    { name: 'Interface Name', displayName: 'Interface Name', required: false, dataType: 'STRING', description: 'Interface name (optional)' },
    { name: 'Interface Source System Short Name', displayName: 'Interface Source System Short Name', required: false, dataType: 'STRING', description: 'Interface source system short name (optional)' },
    { name: 'Interface Target System Short Name', displayName: 'Interface Target System Short Name', required: false, dataType: 'STRING', description: 'Interface target system short name (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, dropdown from process_x_interface_relationtype)', listSource: 'process_x_interface_relationtype.PrimaryName' }
  ]
};

// ===== PROCESS X LEGAL ENTITY =====
export const PROCESS_X_LEGAL_ENTITY_FIELDS = {
  INSERT: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'process_x_legal_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'process_x_legal_relationtype.PrimaryName' }
  ]
};

// ===== LEGAL ENTITY X GEOGRAPHY =====
export const LEGAL_ENTITY_X_GEOGRAPHY_FIELDS = {
  INSERT: [
    { name: 'Geography', displayName: 'Geography', required: true, dataType: 'STRING', description: 'Geography name (required)' },
    { name: 'Parent Geography', displayName: 'Parent Geography', required: false, dataType: 'STRING', description: 'Parent Geography name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, dropdown from legal_x_geo_relationtype)', listSource: 'legal_x_geo_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Geography', displayName: 'Geography', required: true, dataType: 'STRING', description: 'Geography name (required)' },
    { name: 'Parent Geography', displayName: 'Parent Geography', required: false, dataType: 'STRING', description: 'Parent Geography name (optional, for disambiguation)' },
    { name: 'Legal Short Name', displayName: 'Legal Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Short Name', displayName: 'Parent Legal Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, dropdown from legal_x_geo_relationtype)', listSource: 'legal_x_geo_relationtype.PrimaryName' }
  ]
};

// ===== PROCESS RELATIONSHIPS =====
export const PROCESS_X_ATTRIBUTE_FIELDS = {
  INSERT: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process for disambiguation (optional)' },
    { name: 'Attribute Ref.', displayName: 'Attribute Ref.', required: false, dataType: 'STRING', description: 'Attribute reference number (optional)' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: false, dataType: 'STRING', description: 'Attribute name (optional)' },
    { name: 'Attribute Data Set Name', displayName: 'Attribute Data Set Name', required: false, dataType: 'STRING', description: 'Attribute data set name for disambiguation (optional)' },
    { name: 'Attribute System Short Name', displayName: 'Attribute System Short Name', required: false, dataType: 'STRING', description: 'Attribute system short name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'process_x_attribute_relationtype.primaryname' }
  ],
  DELETE: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process for disambiguation (optional)' },
    { name: 'Attribute Ref.', displayName: 'Attribute Ref.', required: false, dataType: 'STRING', description: 'Attribute reference number (optional)' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: false, dataType: 'STRING', description: 'Attribute name (optional)' },
    { name: 'Attribute Data Set Name', displayName: 'Attribute Data Set Name', required: false, dataType: 'STRING', description: 'Attribute data set name for disambiguation (optional)' },
    { name: 'Attribute System Short Name', displayName: 'Attribute System Short Name', required: false, dataType: 'STRING', description: 'Attribute system short name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'process_x_attribute_relationtype.primaryname' }
  ]
};

export const PROCESS_X_PROCESS_FIELDS = {
  INSERT: [
    { name: 'Condition', displayName: 'Condition', required: false, dataType: 'STRING', description: 'Condition (optional)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process for disambiguation (optional)' },
    { name: 'Predecessor Process Ref.', displayName: 'Predecessor Process Ref.', required: false, dataType: 'STRING', description: 'Predecessor process reference number (optional)' },
    { name: 'Predecessor Process Name', displayName: 'Predecessor Process Name', required: false, dataType: 'STRING', description: 'Predecessor process name (optional)' },
    { name: 'Predecessor Parent Process Name', displayName: 'Predecessor Parent Process Name', required: false, dataType: 'STRING', description: 'Predecessor parent process for disambiguation (optional)' },
    { name: 'annotations', displayName: 'Annotations', required: false, dataType: 'STRING', description: 'Annotations (optional)' }
  ],
  DELETE: [
    { name: 'Condition', displayName: 'Condition', required: false, dataType: 'STRING', description: 'Condition (optional)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process for disambiguation (optional)' },
    { name: 'Predecessor Process Ref.', displayName: 'Predecessor Process Ref.', required: false, dataType: 'STRING', description: 'Predecessor process reference number (optional)' },
    { name: 'Predecessor Process Name', displayName: 'Predecessor Process Name', required: false, dataType: 'STRING', description: 'Predecessor process name (optional)' },
    { name: 'Predecessor Parent Process Name', displayName: 'Predecessor Parent Process Name', required: false, dataType: 'STRING', description: 'Predecessor parent process for disambiguation (optional)' },
    { name: 'annotations', displayName: 'Annotations', required: false, dataType: 'STRING', description: 'Annotations (optional)' }
  ]
};

// ===== PROCESS X SYSTEM =====
export const PROCESS_X_SYSTEM_FIELDS = {
  INSERT: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'process_x_system_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'process_x_system_relationtype.PrimaryName' }
  ]
};

// ===== PROJECT RELATIONSHIPS =====
export const PROJECT_X_BUSINESS_AREA_FIELDS = {
  INSERT: [
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Business Area Parent Name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Business Area parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_businessarea_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)' },
    { name: 'Business Area Parent Name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Business Area parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_businessarea_relationtype.PrimaryName' }
  ]
};

export const PROJECT_X_CAPABILITY_FIELDS = {
  INSERT: [
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_capability_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_capability_relationtype.PrimaryName' }
  ]
};

export const PROJECT_X_CLIENT_FIELDS = {
  INSERT: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name for disambiguation (optional)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'client_x_project_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name for disambiguation (optional)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'client_x_project_relationtype.PrimaryName' }
  ]
};

export const PROJECT_X_DATA_SET_FIELDS = {
  INSERT: [
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Data Set Ref.', displayName: 'Data Set Ref.', required: false, dataType: 'STRING', description: 'Data set reference number (optional)' },
    { name: 'Data Set Name', displayName: 'Data Set Name', required: false, dataType: 'STRING', description: 'Data set name (optional)' },
    { name: 'Data Set System Short Name', displayName: 'Data Set System Short Name', required: false, dataType: 'STRING', description: 'Data set system short name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_dataset_relationtype.primaryname' }
  ],
  DELETE: [
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Data Set Ref.', displayName: 'Data Set Ref.', required: false, dataType: 'STRING', description: 'Data set reference number (optional)' },
    { name: 'Data Set Name', displayName: 'Data Set Name', required: false, dataType: 'STRING', description: 'Data set name (optional)' },
    { name: 'Data Set System Short Name', displayName: 'Data Set System Short Name', required: false, dataType: 'STRING', description: 'Data set system short name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_dataset_relationtype.primaryname' }
  ]
};

export const PROJECT_X_ATTRIBUTE_FIELDS = {
  INSERT: [
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Attribute Ref.', displayName: 'Attribute Ref.', required: false, dataType: 'STRING', description: 'Attribute reference number (optional)' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: false, dataType: 'STRING', description: 'Attribute name (optional)' },
    { name: 'Attribute Data Set Name', displayName: 'Attribute Data Set Name', required: false, dataType: 'STRING', description: 'Attribute data set name for disambiguation (optional)' },
    { name: 'Attribute System Short Name', displayName: 'Attribute System Short Name', required: false, dataType: 'STRING', description: 'Attribute system short name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_attribute_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Attribute Ref.', displayName: 'Attribute Ref.', required: false, dataType: 'STRING', description: 'Attribute reference number (optional)' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: false, dataType: 'STRING', description: 'Attribute name (optional)' },
    { name: 'Attribute Data Set Name', displayName: 'Attribute Data Set Name', required: false, dataType: 'STRING', description: 'Attribute data set name for disambiguation (optional)' },
    { name: 'Attribute System Short Name', displayName: 'Attribute System Short Name', required: false, dataType: 'STRING', description: 'Attribute system short name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_attribute_relationtype.PrimaryName' }
  ]
};

export const PROJECT_X_GLOSSARY_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary for disambiguation (optional)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Parent Project Name', displayName: 'Parent Project Name', required: false, dataType: 'STRING', description: 'Parent project for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'glossary_x_project_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary for disambiguation (optional)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Parent Project Name', displayName: 'Parent Project Name', required: false, dataType: 'STRING', description: 'Parent project for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'glossary_x_project_relationtype.PrimaryName' }
  ]
};

export const PROJECT_X_PROCESS_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process for disambiguation (optional)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_process_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Process Ref.', displayName: 'Process Ref.', required: false, dataType: 'STRING', description: 'Process reference number (optional)' },
    { name: 'Process Name', displayName: 'Process Name', required: false, dataType: 'STRING', description: 'Process name (optional)' },
    { name: 'Parent Process Name', displayName: 'Parent Process Name', required: false, dataType: 'STRING', description: 'Parent process for disambiguation (optional)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_process_relationtype.PrimaryName' }
  ]
};

export const PROJECT_X_SYSTEM_FIELDS = {
  INSERT: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_system_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description (optional)' },
    { name: 'Project Ref.', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (optional)' },
    { name: 'Project Parent Name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Project parent name for disambiguation (optional)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'project_x_system_relationtype.PrimaryName' }
  ]
};

// ===== REGULATION RELATIONSHIPS =====
export const REGULATION_X_PRODUCT_FIELDS = {
  INSERT: [
    { name: 'Regulation Ref.', displayName: 'Regulation Ref.', required: false, dataType: 'STRING', description: 'Regulation reference number (optional)' },
    { name: 'Regulation Name', displayName: 'Regulation Name', required: false, dataType: 'STRING', description: 'Regulation name (optional)' },
    { name: 'Parent Regulation Name', displayName: 'Parent Regulation Name', required: false, dataType: 'STRING', description: 'Parent regulation for disambiguation (optional)' },
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'regulation_x_product_relationtype.primaryName' }
  ],
  DELETE: [
    { name: 'Regulation Ref.', displayName: 'Regulation Ref.', required: false, dataType: 'STRING', description: 'Regulation reference number (optional)' },
    { name: 'Regulation Name', displayName: 'Regulation Name', required: false, dataType: 'STRING', description: 'Regulation name (optional)' },
    { name: 'Parent Regulation Name', displayName: 'Parent Regulation Name', required: false, dataType: 'STRING', description: 'Parent regulation for disambiguation (optional)' },
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent name for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'regulation_x_product_relationtype.primaryName' }
  ]
};

// ===== SYSTEM RELATIONSHIPS =====
export const SYSTEM_X_CLIENT_FIELDS = {
  INSERT: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name (optional, for disambiguation)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'client_x_system_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)' },
    { name: 'Client Parent Name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Client parent name (optional, for disambiguation)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'client_x_system_relationtype.PrimaryName' }
  ]
};

export const SYSTEM_X_DATA_ONBOARDING_RULE_FIELDS = {
  INSERT: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Data Onboarding Rule Name', displayName: 'Data Onboarding Rule Name', required: true, dataType: 'STRING', description: 'Data Onboarding Rule name (required)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ],
  DELETE: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Data Onboarding Rule Name', displayName: 'Data Onboarding Rule Name', required: true, dataType: 'STRING', description: 'Data Onboarding Rule name (required)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)' }
  ]
};

export const SYSTEM_X_GLOSSARY_FIELDS = {
  INSERT: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'glossary_x_system_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary for disambiguation (optional)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'glossary_x_system_relationtype.PrimaryName' }
  ]
};

export const INTERFACE_X_GLOSSARY_FIELDS = {
  INSERT: [
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, dropdown from interface_x_glossary_relationtype)', listSource: 'interface_x_glossary_relationtype.PrimaryName' },
    { name: 'Interface Ref.', displayName: 'Interface Ref.', required: false, dataType: 'STRING', description: 'Interface reference number (optional)' },
    { name: 'Interface Name', displayName: 'Interface Name', required: false, dataType: 'STRING', description: 'Interface name (optional)' },
    { name: 'Interface Source System Short Name', displayName: 'Interface Source System Short Name', required: false, dataType: 'STRING', description: 'Interface source system short name (optional)' },
    { name: 'Interface Target System Short Name', displayName: 'Interface Target System Short Name', required: false, dataType: 'STRING', description: 'Interface target system short name (optional)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent Glossary name (optional, for disambiguation)' }
  ],
  DELETE: [
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, dropdown from interface_x_glossary_relationtype)', listSource: 'interface_x_glossary_relationtype.PrimaryName' },
    { name: 'Interface Ref.', displayName: 'Interface Ref.', required: false, dataType: 'STRING', description: 'Interface reference number (optional)' },
    { name: 'Interface Name', displayName: 'Interface Name', required: false, dataType: 'STRING', description: 'Interface name (optional)' },
    { name: 'Interface Source System Short Name', displayName: 'Interface Source System Short Name', required: false, dataType: 'STRING', description: 'Interface source system short name (optional)' },
    { name: 'Interface Target System Short Name', displayName: 'Interface Target System Short Name', required: false, dataType: 'STRING', description: 'Interface target system short name (optional)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number (optional)' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (optional)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent Glossary name (optional, for disambiguation)' }
  ]
};

export const SYSTEM_X_LEGAL_ENTITY_FIELDS = {
  INSERT: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Legal Entity Short Name', displayName: 'Legal Entity Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Entity Short Name', displayName: 'Parent Legal Entity Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'system_x_legal_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent System short name (optional, for disambiguation)' },
    { name: 'Legal Entity Short Name', displayName: 'Legal Entity Short Name', required: true, dataType: 'STRING', description: 'Legal Entity short name (required)' },
    { name: 'Parent Legal Entity Short Name', displayName: 'Parent Legal Entity Short Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity short name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'system_x_legal_relationtype.PrimaryName' }
  ]
};

export const SYSTEM_X_PRODUCT_FIELDS = {
  INSERT: [
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent name (optional, for disambiguation)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'product_x_system_relationtype.PrimaryName' },
    { name: 'Product x Legal Entity Rel Type', displayName: 'Product x Legal Entity Rel Type', required: false, dataType: 'LIST', description: 'Product x Legal Entity Rel Type (optional, select from dropdown)', listSource: 'product_x_legal_relationtype.PrimaryName' },
    { name: 'Legal Entity Short Name', displayName: 'Legal Entity Short Name', required: false, dataType: 'STRING', description: 'Legal Entity short name (optional)' },
    { name: 'Legal Entity Parent Name', displayName: 'Legal Entity Parent Name', required: false, dataType: 'STRING', description: 'Legal Entity parent name (optional, for disambiguation)' }
  ],
  DELETE: [
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)' },
    { name: 'Product Parent Name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Product parent name (optional, for disambiguation)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'product_x_system_relationtype.PrimaryName' },
    { name: 'Product x Legal Entity Rel Type', displayName: 'Product x Legal Entity Rel Type', required: false, dataType: 'LIST', description: 'Product x Legal Entity Rel Type (optional, select from dropdown)', listSource: 'product_x_legal_relationtype.PrimaryName' },
    { name: 'Legal Entity Short Name', displayName: 'Legal Entity Short Name', required: false, dataType: 'STRING', description: 'Legal Entity short name (optional)' },
    { name: 'Legal Entity Parent Name', displayName: 'Legal Entity Parent Name', required: false, dataType: 'STRING', description: 'Legal Entity parent name (optional, for disambiguation)' }
  ]
};

export const SYSTEM_X_RESOURCE_FIELDS = {
  INSERT: [
    { name: 'System Short Name', displayName: 'System Short Name', required: true, dataType: 'STRING', description: 'System short name (required)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent system for disambiguation (optional)' },
    { name: 'Name', displayName: 'Name', required: true, dataType: 'STRING', description: 'Resource name (required)' },
    { name: 'Resource ID', displayName: 'Resource ID', required: true, dataType: 'STRING', description: 'Resource ID (required)' }
  ]
};

// ===== COMMITTEE RELATIONSHIPS =====
export const COMMITTEE_X_CAPABILITY_FIELDS = {
  INSERT: [
    { name: 'Committee Ref.', displayName: 'Committee Ref.', required: false, dataType: 'STRING', description: 'Committee reference number (optional)' },
    { name: 'Committee Name', displayName: 'Committee Name', required: false, dataType: 'STRING', description: 'Committee name (optional)' },
    { name: 'Committee Parent Name', displayName: 'Committee Parent Name', required: false, dataType: 'STRING', description: 'Committee parent name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'committee_x_capability_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Committee Ref.', displayName: 'Committee Ref.', required: false, dataType: 'STRING', description: 'Committee reference number (optional)' },
    { name: 'Committee Name', displayName: 'Committee Name', required: false, dataType: 'STRING', description: 'Committee name (optional)' },
    { name: 'Committee Parent Name', displayName: 'Committee Parent Name', required: false, dataType: 'STRING', description: 'Committee parent name (optional, for disambiguation)' },
    { name: 'Capability Ref.', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference number (optional)' },
    { name: 'Capability Name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (optional)' },
    { name: 'Capability Parent Name', displayName: 'Capability Parent Name', required: false, dataType: 'STRING', description: 'Capability parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'committee_x_capability_relationtype.PrimaryName' }
  ]
};

// Committee X Committee: source/target Committee (roles match template and backend findExcelColumnName)
export const COMMITTEE_X_COMMITTEE_FIELDS = {
  INSERT: [
    { name: 'Source Committee Ref.', displayName: 'Source Committee Ref.', required: false, dataType: 'STRING', description: 'Source Committee reference number (optional)' },
    { name: 'Source Committee Name', displayName: 'Source Committee Name', required: false, dataType: 'STRING', description: 'Source Committee name (optional)' },
    { name: 'Source Committee Parent Name', displayName: 'Source Committee Parent Name', required: false, dataType: 'STRING', description: 'Source Committee parent name (optional, for disambiguation)' },
    { name: 'Target Committee Ref.', displayName: 'Target Committee Ref.', required: false, dataType: 'STRING', description: 'Target Committee reference number (optional)' },
    { name: 'Target Committee Name', displayName: 'Target Committee Name', required: false, dataType: 'STRING', description: 'Target Committee name (optional)' },
    { name: 'Target Committee Parent Name', displayName: 'Target Committee Parent Name', required: false, dataType: 'STRING', description: 'Target Committee parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'committee_x_committee_relationtype.PrimaryName' }
  ],
  DELETE: [
    { name: 'Source Committee Ref.', displayName: 'Source Committee Ref.', required: false, dataType: 'STRING', description: 'Source Committee reference number (optional)' },
    { name: 'Source Committee Name', displayName: 'Source Committee Name', required: false, dataType: 'STRING', description: 'Source Committee name (optional)' },
    { name: 'Source Committee Parent Name', displayName: 'Source Committee Parent Name', required: false, dataType: 'STRING', description: 'Source Committee parent name (optional, for disambiguation)' },
    { name: 'Target Committee Ref.', displayName: 'Target Committee Ref.', required: false, dataType: 'STRING', description: 'Target Committee reference number (optional)' },
    { name: 'Target Committee Name', displayName: 'Target Committee Name', required: false, dataType: 'STRING', description: 'Target Committee name (optional)' },
    { name: 'Target Committee Parent Name', displayName: 'Target Committee Parent Name', required: false, dataType: 'STRING', description: 'Target Committee parent name (optional, for disambiguation)' },
    { name: 'Relationship Type', displayName: 'Relationship Type', required: true, dataType: 'LIST', description: 'Relationship type (required, select from dropdown)', listSource: 'committee_x_committee_relationtype.PrimaryName' }
  ]
};

// Helper mapping for all relationship entities
// This will be used in getFieldsForEntity to route to the correct field config
export const RELATIONSHIP_FIELD_MAP = {
  'Attribute X Attribute': ATTRIBUTE_X_ATTRIBUTE_FIELDS,
  'Attribute X Physical Field': ATTRIBUTE_X_PHYSICAL_FIELD_FIELDS,
  'Business Area X Glossary': BUSINESS_AREA_X_GLOSSARY_FIELDS,
  'Business Area X Process': BUSINESS_AREA_X_PROCESS_FIELDS,
  'Business Area X System': BUSINESS_AREA_X_SYSTEM_FIELDS,
  'Capability X Business Area': CAPABILITY_X_BUSINESS_AREA_FIELDS,
  'Capability X Client': CAPABILITY_X_CLIENT_FIELDS,
  'Capability X Glossary': CAPABILITY_X_GLOSSARY_FIELDS,
  'Capability X Legal Entity': CAPABILITY_X_LEGAL_ENTITY_FIELDS,
  'Capability X Process': CAPABILITY_X_PROCESS_FIELDS,
  'Capability X Product': CAPABILITY_X_PRODUCT_FIELDS,
  'Capability X System': CAPABILITY_X_SYSTEM_FIELDS,
  'Committee X Capability': COMMITTEE_X_CAPABILITY_FIELDS,
  'Committee X Committee': COMMITTEE_X_COMMITTEE_FIELDS,
  'People X People': PEOPLE_X_PEOPLE_FIELDS,
  'Policy X Attribute': POLICY_X_ATTRIBUTE_FIELDS,
  'Policy X Business Area': POLICY_X_BUSINESS_AREA_FIELDS,
  'Policy X Client': POLICY_X_CLIENT_FIELDS,
  'Policy X Data Set': POLICY_X_DATA_SET_FIELDS,
  'Policy X Glossary': POLICY_X_GLOSSARY_FIELDS,
  'Policy X Legal Entity': POLICY_X_LEGAL_ENTITY_FIELDS,
  'Policy X Policy': POLICY_X_POLICY_FIELDS,
  'Policy X Process': POLICY_X_PROCESS_FIELDS,
  'Policy X Product': POLICY_X_PRODUCT_FIELDS,
  'Policy X Project': POLICY_X_PROJECT_FIELDS,
  'Policy X System': POLICY_X_SYSTEM_FIELDS,
  'Process X Attribute': PROCESS_X_ATTRIBUTE_FIELDS,
  'Process X Client': PROCESS_X_CLIENT_FIELDS,
  'Process X Data Set': PROCESS_X_DATA_SET_FIELDS,
  'Process X Glossary': PROCESS_X_GLOSSARY_FIELDS,
  'Process X System Interface': PROCESS_X_SYSTEM_INTERFACE_FIELDS,
  'Process X Legal Entity': PROCESS_X_LEGAL_ENTITY_FIELDS,
  'Product X Client': PRODUCT_X_CLIENT_FIELDS,
  'Product X Legal Entity': PRODUCT_X_LEGAL_ENTITY_FIELDS,
  'Process X Process': PROCESS_X_PROCESS_FIELDS,
  'Process X System': PROCESS_X_SYSTEM_FIELDS,
  'Project X Attribute': PROJECT_X_ATTRIBUTE_FIELDS,
  'Project X Business Area': PROJECT_X_BUSINESS_AREA_FIELDS,
  'Project X Capability': PROJECT_X_CAPABILITY_FIELDS,
  'Project X Client': PROJECT_X_CLIENT_FIELDS,
  'Project X Data Set': PROJECT_X_DATA_SET_FIELDS,
  'Project X Glossary': PROJECT_X_GLOSSARY_FIELDS,
  'Project X Process': PROJECT_X_PROCESS_FIELDS,
  'Project X System': PROJECT_X_SYSTEM_FIELDS,
  'Regulation X Product': REGULATION_X_PRODUCT_FIELDS,
  'Segment X Object': SEGMENT_X_OBJECT_FIELDS,
  'System X Client': SYSTEM_X_CLIENT_FIELDS,
  'System X Data Onboarding Rule': SYSTEM_X_DATA_ONBOARDING_RULE_FIELDS,
  'System X Glossary': SYSTEM_X_GLOSSARY_FIELDS,
  'Interface X Glossary': INTERFACE_X_GLOSSARY_FIELDS,
  'Legal Entity X Geography': LEGAL_ENTITY_X_GEOGRAPHY_FIELDS,
  'System X Legal Entity': SYSTEM_X_LEGAL_ENTITY_FIELDS,
  'System X Product': SYSTEM_X_PRODUCT_FIELDS,
  'System X Resource': SYSTEM_X_RESOURCE_FIELDS
};

