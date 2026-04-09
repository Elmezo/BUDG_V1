/**
 * Upload Type Configuration
 * Defines all upload types, groups, and options for the bulk upload wizard
 */

/**
 * Upload type groups with their associated entities
 * Organized by business domain
 */
export const UPLOAD_TYPE_GROUPS = {
  'Business & Change': [
    'Committee',
    'Policy',
    'Process',
    'Project'
  ],
  'Data & Technology': [
    'Attribute',
    'Dataset',
    'Glossary',
    'Interface',
    'System'
  ],
  'Organizational': [
    'Business Area',
    'Capability',
    'Client',
    'Legal',
    'Org. Unit',
    'People',
    'Product'
  ],
  'Regulatory': [
    'Geography',
    'Regulation',
    'Regulator',
    'Regulatory Theme'
  ]
};

/**
 * Relationship entities grouped by primary entity
 * Used when Upload Type is "Relationship"
 */
export const RELATIONSHIP_ENTITIES = {
  'Attribute Relationships': [
    'Attribute X Attribute',
    'Attribute X Physical Field'
  ],
  'Business Area Relationships': [
    'Business Area X Glossary',
    'Business Area X Process',
    'Business Area X System'
  ],
  'Capability Relationships': [
    'Capability X Business Area',
    'Capability X Client',
    'Capability X Glossary',
    'Capability X Legal Entity',
    'Capability X Process',
    'Capability X Product',
    'Capability X System'
  ],
  'Committee Relationships': [
    'Committee X Capability',
    'Committee X Committee'
  ],
  'Data Set Relationships': [
    'Data Set X Client',
    'Data Set X Legal Entity',
    'Data Set X Product'
  ],
  'Glossary Relationships': [
    'Glossary X Client',
    'Glossary X Glossary',
    'Glossary X Product',
    'Glossary X System'
  ],
  'Interface Relationships': [
    'Interface X Glossary',
    'Process X System Interface'
  ],
  'Legal/Geography Relationships': [
    'Legal Entity X Geography',
    'Regulator X Geography'
  ],
  'Data Quality Rule Relationships': [
    'Local Data Quality Rule X Technical Reference',
    'Standard Data Quality Rule X Technical Reference'
  ],
  'People Relationships': [
    'People X People'
  ],
  'Policy Relationships': [
    'Policy X Attribute',
    'Policy X Business Area',
    'Policy X Client',
    'Policy X Data Set',
    'Policy X Glossary',
    'Policy X Legal Entity',
    'Policy X Policy',
    'Policy X Process',
    'Policy X Product',
    'Policy X Project',
    'Policy X System'
  ],
  'Process Relationships': [
    'Process X Attribute',
    'Process X Client',
    'Process X Data Set',
    'Process X Glossary',
    'Process X Legal Entity',
    'Process X Process',
    'Process X Product',
    'Process X System'
  ],
  'Product Relationships': [
    'Product X Business Area',
    'Product X Client',
    'Product X Legal Entity'
  ],
  'Project Relationships': [
    'Project X Attribute',
    'Project X Business Area',
    'Project X Capability',
    'Project X Client',
    'Project X Data Set',
    'Project X Glossary',
    'Project X Process',
    'Project X Product',
    'Project X Project',
    'Project X System'
  ],
  'Regulation Relationships': [
    'Regulation X Policy',
    'Regulation X Product',
    'Regulation X Project',
    'Regulation X Regulator',
    'Regulation X Regulatory Theme'
  ],
  'Segment Relationships': [
    'Segment X Object'
  ],
  'System Relationships': [
    'System X Client',
    'System X Data Onboarding Rule',
    'System X Glossary',
    'System X Legal Entity',
    'System X Product',
    'System X Resource'
  ]
};

/**
 * Role entities
 * Used when Upload Type is "Role"
 */
export const ROLE_ENTITIES = {
  'Role Assignments': [
    'Business Area Role',
    'Capability Role',
    'Client Role',
    'Committee Role',
    'Data Quality Role',
    'Data Set Role',
    'Glossary Role',
    'Interface Role',
    'Legal Entity Role',
    'Policy Role',
    'Process Role',
    'Product Role',
    'Project Role',
    'Regulation Role',
    'System Role'
  ]
};

/**
 * Upload options for each entity
 * Maps entity name to available operations
 */
export const UPLOAD_OPTIONS = {
  'Regulator': [
    { value: 'Add New Items', label: 'Regulator_bulkCreate', description: 'Create new regulators' },
    { value: 'Update Existing Items', label: 'Regulator_bulkUpdate', description: 'Update existing regulators' },
    { value: 'Remove Existing Items', label: 'Regulator_bulkDelete', description: 'Delete existing regulators' }
  ],
  // Future entities can be added here
  'Committee': [
    { value: 'Add New Items', label: 'Committee_bulkCreate', description: 'Create new committees' },
    { value: 'Update Existing Items', label: 'Committee_bulkUpdate', description: 'Update existing committees' },
    { value: 'Remove Existing Items', label: 'Committee_bulkDelete', description: 'Delete existing committees' }
  ],
  'Policy': [
    { value: 'Add New Items', label: 'Policy_bulkCreate', description: 'Create new policies' },
    { value: 'Update Existing Items', label: 'Policy_bulkUpdate', description: 'Update existing policies' },
    { value: 'Remove Existing Items', label: 'Policy_bulkDelete', description: 'Delete existing policies' }
  ],
  'Geography': [
    { value: 'Add New Items', label: 'Geography_bulkCreate', description: 'Create new geographies' },
    { value: 'Update Existing Items', label: 'Geography_bulkUpdate', description: 'Update existing geographies' },
    { value: 'Remove Existing Items', label: 'Geography_bulkDelete', description: 'Delete existing geographies' }
  ],
  'Regulatory Theme': [
    { value: 'Add New Items', label: 'RegulatoryTheme_bulkCreate', description: 'Create new regulatory themes' },
    { value: 'Update Existing Items', label: 'RegulatoryTheme_bulkUpdate', description: 'Update existing regulatory themes' },
    { value: 'Remove Existing Items', label: 'RegulatoryTheme_bulkDelete', description: 'Delete existing regulatory themes' }
  ],
  'Process': [
    { value: 'Add New Items', label: 'Process_bulkCreate', description: 'Create new processes' },
    { value: 'Update Existing Items', label: 'Process_bulkUpdate', description: 'Update existing processes' },
    { value: 'Remove Existing Items', label: 'Process_bulkDelete', description: 'Delete existing processes' }
  ],
  'Regulation': [
    { value: 'Add New Items', label: 'Regulation_bulkCreate', description: 'Create new regulations' },
    { value: 'Update Existing Items', label: 'Regulation_bulkUpdate', description: 'Update existing regulations' },
    { value: 'Remove Existing Items', label: 'Regulation_bulkDelete', description: 'Delete existing regulations' }
  ],
  'Project': [
    { value: 'Add New Items', label: 'Project_bulkCreate', description: 'Create new projects' },
    { value: 'Update Existing Items', label: 'Project_bulkUpdate', description: 'Update existing projects' },
    { value: 'Remove Existing Items', label: 'Project_bulkDelete', description: 'Delete existing projects' }
  ],
  'Business Area': [
    { value: 'Add New Items', label: 'BusinessArea_bulkCreate', description: 'Create new business areas' },
    { value: 'Update Existing Items', label: 'BusinessArea_bulkUpdate', description: 'Update existing business areas' },
    { value: 'Remove Existing Items', label: 'BusinessArea_bulkDelete', description: 'Delete existing business areas' }
  ],
  'Capability': [
    { value: 'Add New Items', label: 'Capability_bulkCreate', description: 'Create new capabilities' },
    { value: 'Update Existing Items', label: 'Capability_bulkUpdate', description: 'Update existing capabilities' },
    { value: 'Remove Existing Items', label: 'Capability_bulkDelete', description: 'Delete existing capabilities' }
  ],
  'Client': [
    { value: 'Add New Items', label: 'Client_bulkCreate', description: 'Create new clients' },
    { value: 'Update Existing Items', label: 'Client_bulkUpdate', description: 'Update existing clients' },
    { value: 'Remove Existing Items', label: 'Client_bulkDelete', description: 'Delete existing clients' }
  ],
  'Legal': [
    { value: 'Add New Items', label: 'Legal_bulkCreate', description: 'Create new legal entities' },
    { value: 'Update Existing Items', label: 'Legal_bulkUpdate', description: 'Update existing legal entities' },
    { value: 'Remove Existing Items', label: 'Legal_bulkDelete', description: 'Delete existing legal entities' }
  ],
  'Org. Unit': [
    { value: 'Add New Items', label: 'OrgUnit_bulkCreate', description: 'Create new organizational units' },
    { value: 'Update Existing Items', label: 'OrgUnit_bulkUpdate', description: 'Update existing organizational units' },
    { value: 'Remove Existing Items', label: 'OrgUnit_bulkDelete', description: 'Delete existing organizational units' }
  ],
  'People': [
    { value: 'Add New Items', label: 'People_bulkCreate', description: 'Create new people records' },
    { value: 'Update Existing Items', label: 'People_bulkUpdate', description: 'Update existing people records' },
    { value: 'Remove Existing Items', label: 'People_bulkDelete', description: 'Delete existing people records' }
  ],
  'Product': [
    { value: 'Add New Items', label: 'Product_bulkCreate', description: 'Create new products' },
    { value: 'Update Existing Items', label: 'Product_bulkUpdate', description: 'Update existing products' },
    { value: 'Remove Existing Items', label: 'Product_bulkDelete', description: 'Delete existing products' }
  ],
  'System': [
    { value: 'Add New Items', label: 'System_bulkCreate', description: 'Create new systems' },
    { value: 'Update Existing Items', label: 'System_bulkUpdate', description: 'Update existing systems' },
    { value: 'Remove Existing Items', label: 'System_bulkDelete', description: 'Delete existing systems' }
  ],
  'Dataset': [
    { value: 'Add New Items', label: 'Dataset_bulkCreate', description: 'Create new datasets' },
    { value: 'Update Existing Items', label: 'Dataset_bulkUpdate', description: 'Update existing datasets' },
    { value: 'Remove Existing Items', label: 'Dataset_bulkDelete', description: 'Delete existing datasets' }
  ],
  'Attribute': [
    { value: 'Add New Items', label: 'Attribute_bulkCreate', description: 'Create new attributes' },
    { value: 'Update Existing Items', label: 'Attribute_bulkUpdate', description: 'Update existing attributes' },
    { value: 'Remove Existing Items', label: 'Attribute_bulkDelete', description: 'Delete existing attributes' }
  ],
  'Interface': [
    { value: 'Add New Items', label: 'Interface_bulkCreate', description: 'Create new interfaces' },
    { value: 'Update Existing Items', label: 'Interface_bulkUpdate', description: 'Update existing interfaces' },
    { value: 'Remove Existing Items', label: 'Interface_bulkDelete', description: 'Delete existing interfaces' }
  ],
  'Glossary': [
    { value: 'Add New Items', label: 'Glossary_bulkCreate', description: 'Create new glossary terms' },
    { value: 'Update Existing Items', label: 'Glossary_bulkUpdate', description: 'Update existing glossary terms' },
    { value: 'Remove Existing Items', label: 'Glossary_bulkDelete', description: 'Delete existing glossary terms' }
  ],
  
  // Relationship Upload Options
  'Attribute X Attribute': [
    { value: 'Upload New Items', label: 'AttributeXAttribute_bulkCreate', description: 'Create attribute to attribute relationships' },
    { value: 'Remove Existing Items', label: 'AttributeXAttribute_bulkDelete', description: 'Remove attribute to attribute relationships' }
  ],
  'Attribute X Physical Field': [
    { value: 'Upload New Items', label: 'AttributeXPhysicalField_bulkCreate', description: 'Create attribute to physical field relationships' }
  ],
  'Business Area X Glossary': [
    { value: 'Upload New Items', label: 'BusinessAreaXGlossary_bulkCreate', description: 'Create business area to glossary relationships' },
    { value: 'Remove Existing Items', label: 'BusinessAreaXGlossary_bulkDelete', description: 'Remove business area to glossary relationships' }
  ],
  'Business Area X Process': [
    { value: 'Upload New Items', label: 'BusinessAreaXProcess_bulkCreate', description: 'Create business area to process relationships' },
    { value: 'Remove Existing Items', label: 'BusinessAreaXProcess_bulkDelete', description: 'Remove business area to process relationships' }
  ],
  'Business Area X System': [
    { value: 'Upload New Items', label: 'BusinessAreaXSystem_bulkCreate', description: 'Create business area to system relationships' },
    { value: 'Remove Existing Items', label: 'BusinessAreaXSystem_bulkDelete', description: 'Remove business area to system relationships' }
  ],
  'Capability X Business Area': [
    { value: 'Upload New Items', label: 'CapabilityXBusinessArea_bulkCreate', description: 'Create capability to business area relationships' },
    { value: 'Remove Existing Items', label: 'CapabilityXBusinessArea_bulkDelete', description: 'Remove capability to business area relationships' }
  ],
  'Capability X Client': [
    { value: 'Upload New Items', label: 'CapabilityXClient_bulkCreate', description: 'Create capability to client relationships' },
    { value: 'Remove Existing Items', label: 'CapabilityXClient_bulkDelete', description: 'Remove capability to client relationships' }
  ],
  'Capability X Glossary': [
    { value: 'Upload New Items', label: 'CapabilityXGlossary_bulkCreate', description: 'Create capability to glossary relationships' },
    { value: 'Remove Existing Items', label: 'CapabilityXGlossary_bulkDelete', description: 'Remove capability to glossary relationships' }
  ],
  'Capability X Legal Entity': [
    { value: 'Upload New Items', label: 'CapabilityXLegalEntity_bulkCreate', description: 'Create capability to legal entity relationships' },
    { value: 'Remove Existing Items', label: 'CapabilityXLegalEntity_bulkDelete', description: 'Remove capability to legal entity relationships' }
  ],
  'Capability X Process': [
    { value: 'Upload New Items', label: 'CapabilityXProcess_bulkCreate', description: 'Create capability to process relationships' },
    { value: 'Remove Existing Items', label: 'CapabilityXProcess_bulkDelete', description: 'Remove capability to process relationships' }
  ],
  'Capability X Product': [
    { value: 'Upload New Items', label: 'CapabilityXProduct_bulkCreate', description: 'Create capability to product relationships' },
    { value: 'Remove Existing Items', label: 'CapabilityXProduct_bulkDelete', description: 'Remove capability to product relationships' }
  ],
  'Capability X System': [
    { value: 'Upload New Items', label: 'CapabilityXSystem_bulkCreate', description: 'Create capability to system relationships' },
    { value: 'Remove Existing Items', label: 'CapabilityXSystem_bulkDelete', description: 'Remove capability to system relationships' }
  ],
  'Committee X Capability': [
    { value: 'Upload New Items', label: 'CommitteeXCapability_bulkCreate', description: 'Create committee to capability relationships' },
    { value: 'Remove Existing Items', label: 'CommitteeXCapability_bulkDelete', description: 'Remove committee to capability relationships' }
  ],
  'Committee X Committee': [
    { value: 'Upload New Items', label: 'CommitteeXCommittee_bulkCreate', description: 'Create committee to committee relationships' },
    { value: 'Remove Existing Items', label: 'CommitteeXCommittee_bulkDelete', description: 'Remove committee to committee relationships' }
  ],
  'Data Set X Client': [
    { value: 'Upload New Items', label: 'DataSetXClient_bulkCreate', description: 'Create data set to client relationships' },
    { value: 'Remove Existing Items', label: 'DataSetXClient_bulkDelete', description: 'Remove data set to client relationships' }
  ],
  'Data Set X Legal Entity': [
    { value: 'Upload New Items', label: 'DataSetXLegalEntity_bulkCreate', description: 'Create data set to legal entity relationships' },
    { value: 'Remove Existing Items', label: 'DataSetXLegalEntity_bulkDelete', description: 'Remove data set to legal entity relationships' }
  ],
  'Data Set X Product': [
    { value: 'Upload New Items', label: 'DataSetXProduct_bulkCreate', description: 'Create data set to product relationships' },
    { value: 'Remove Existing Items', label: 'DataSetXProduct_bulkDelete', description: 'Remove data set to product relationships' }
  ],
  'Glossary X Client': [
    { value: 'Upload New Items', label: 'GlossaryXClient_bulkCreate', description: 'Create glossary to client relationships' },
    { value: 'Remove Existing Items', label: 'GlossaryXClient_bulkDelete', description: 'Remove glossary to client relationships' }
  ],
  'Glossary X Glossary': [
    { value: 'Upload New Items', label: 'GlossaryXGlossary_bulkCreate', description: 'Create glossary to glossary relationships' },
    { value: 'Remove Existing Items', label: 'GlossaryXGlossary_bulkDelete', description: 'Remove glossary to glossary relationships' }
  ],
  'Glossary X Product': [
    { value: 'Upload New Items', label: 'GlossaryXProduct_bulkCreate', description: 'Create glossary to product relationships' },
    { value: 'Remove Existing Items', label: 'GlossaryXProduct_bulkDelete', description: 'Remove glossary to product relationships' }
  ],
  'Glossary X System': [
    { value: 'Upload New Items', label: 'GlossaryXSystem_bulkCreate', description: 'Create glossary to system relationships' },
    { value: 'Remove Existing Items', label: 'GlossaryXSystem_bulkDelete', description: 'Remove glossary to system relationships' }
  ],
  'Interface X Glossary': [
    { value: 'Upload New Items', label: 'InterfaceXGlossary_bulkCreate', description: 'Create interface to glossary relationships' },
    { value: 'Remove Existing Items', label: 'InterfaceXGlossary_bulkDelete', description: 'Remove interface to glossary relationships' }
  ],
  'Process X System Interface': [
    { value: 'Upload New Items', label: 'ProcessXSystemInterface_bulkCreate', description: 'Create process to system interface relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXSystemInterface_bulkDelete', description: 'Remove process to system interface relationships' }
  ],
  'Legal Entity X Geography': [
    { value: 'Upload New Items', label: 'LegalEntityXGeography_bulkCreate', description: 'Create legal entity to geography relationships' },
    { value: 'Remove Existing Items', label: 'LegalEntityXGeography_bulkDelete', description: 'Remove legal entity to geography relationships' }
  ],
  'Regulator X Geography': [
    { value: 'Upload New Items', label: 'RegulatorXGeography_bulkCreate', description: 'Create regulator to geography relationships' },
    { value: 'Remove Existing Items', label: 'RegulatorXGeography_bulkDelete', description: 'Remove regulator to geography relationships' }
  ],
  'Local Data Quality Rule X Technical Reference': [
    { value: 'Upload New Items', label: 'LocalDQRXTechnicalReference_bulkCreate', description: 'Create local data quality rule to technical reference relationships' },
    { value: 'Remove Existing Items', label: 'LocalDQRXTechnicalReference_bulkDelete', description: 'Remove local data quality rule to technical reference relationships' }
  ],
  'Standard Data Quality Rule X Technical Reference': [
    { value: 'Upload New Items', label: 'StandardDQRXTechnicalReference_bulkCreate', description: 'Create standard data quality rule to technical reference relationships' },
    { value: 'Remove Existing Items', label: 'StandardDQRXTechnicalReference_bulkDelete', description: 'Remove standard data quality rule to technical reference relationships' }
  ],
  'People X People': [
    { value: 'Upload New Items', label: 'PeopleXPeople_bulkCreate', description: 'Create people to people relationships' },
    { value: 'Remove Existing Items', label: 'PeopleXPeople_bulkDelete', description: 'Remove people to people relationships' }
  ],
  'Policy X Attribute': [
    { value: 'Upload New Items', label: 'PolicyXAttribute_bulkCreate', description: 'Create policy to attribute relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXAttribute_bulkDelete', description: 'Remove policy to attribute relationships' }
  ],
  'Policy X Business Area': [
    { value: 'Upload New Items', label: 'PolicyXBusinessArea_bulkCreate', description: 'Create policy to business area relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXBusinessArea_bulkDelete', description: 'Remove policy to business area relationships' }
  ],
  'Policy X Client': [
    { value: 'Upload New Items', label: 'PolicyXClient_bulkCreate', description: 'Create policy to client relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXClient_bulkDelete', description: 'Remove policy to client relationships' }
  ],
  'Policy X Data Set': [
    { value: 'Upload New Items', label: 'PolicyXDataSet_bulkCreate', description: 'Create policy to data set relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXDataSet_bulkDelete', description: 'Remove policy to data set relationships' }
  ],
  'Policy X Glossary': [
    { value: 'Upload New Items', label: 'PolicyXGlossary_bulkCreate', description: 'Create policy to glossary relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXGlossary_bulkDelete', description: 'Remove policy to glossary relationships' }
  ],
  'Policy X Legal Entity': [
    { value: 'Upload New Items', label: 'PolicyXLegalEntity_bulkCreate', description: 'Create policy to legal entity relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXLegalEntity_bulkDelete', description: 'Remove policy to legal entity relationships' }
  ],
  'Policy X Policy': [
    { value: 'Upload New Items', label: 'PolicyXPolicy_bulkCreate', description: 'Create policy to policy relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXPolicy_bulkDelete', description: 'Remove policy to policy relationships' }
  ],
  'Policy X Process': [
    { value: 'Upload New Items', label: 'PolicyXProcess_bulkCreate', description: 'Create policy to process relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXProcess_bulkDelete', description: 'Remove policy to process relationships' }
  ],
  'Policy X Product': [
    { value: 'Upload New Items', label: 'PolicyXProduct_bulkCreate', description: 'Create policy to product relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXProduct_bulkDelete', description: 'Remove policy to product relationships' }
  ],
  'Policy X Project': [
    { value: 'Upload New Items', label: 'PolicyXProject_bulkCreate', description: 'Create policy to project relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXProject_bulkDelete', description: 'Remove policy to project relationships' }
  ],
  'Policy X System': [
    { value: 'Upload New Items', label: 'PolicyXSystem_bulkCreate', description: 'Create policy to system relationships' },
    { value: 'Remove Existing Items', label: 'PolicyXSystem_bulkDelete', description: 'Remove policy to system relationships' }
  ],
  'Process X Attribute': [
    { value: 'Upload New Items', label: 'ProcessXAttribute_bulkCreate', description: 'Create process to attribute relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXAttribute_bulkDelete', description: 'Remove process to attribute relationships' }
  ],
  'Process X Client': [
    { value: 'Upload New Items', label: 'ProcessXClient_bulkCreate', description: 'Create process to client relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXClient_bulkDelete', description: 'Remove process to client relationships' }
  ],
  'Process X Data Set': [
    { value: 'Upload New Items', label: 'ProcessXDataSet_bulkCreate', description: 'Create process to data set relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXDataSet_bulkDelete', description: 'Remove process to data set relationships' }
  ],
  'Process X Glossary': [
    { value: 'Upload New Items', label: 'ProcessXGlossary_bulkCreate', description: 'Create process to glossary relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXGlossary_bulkDelete', description: 'Remove process to glossary relationships' }
  ],
  'Process X Legal Entity': [
    { value: 'Upload New Items', label: 'ProcessXLegalEntity_bulkCreate', description: 'Create process to legal entity relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXLegalEntity_bulkDelete', description: 'Remove process to legal entity relationships' }
  ],
  'Process X Process': [
    { value: 'Upload New Items', label: 'ProcessXProcess_bulkCreate', description: 'Create process to process relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXProcess_bulkDelete', description: 'Remove process to process relationships' }
  ],
  'Process X Product': [
    { value: 'Upload New Items', label: 'ProcessXProduct_bulkCreate', description: 'Create process to product relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXProduct_bulkDelete', description: 'Remove process to product relationships' }
  ],
  'Process X System': [
    { value: 'Upload New Items', label: 'ProcessXSystem_bulkCreate', description: 'Create process to system relationships' },
    { value: 'Remove Existing Items', label: 'ProcessXSystem_bulkDelete', description: 'Remove process to system relationships' }
  ],
  'Product X Business Area': [
    { value: 'Upload New Items', label: 'ProductXBusinessArea_bulkCreate', description: 'Create product to business area relationships' },
    { value: 'Remove Existing Items', label: 'ProductXBusinessArea_bulkDelete', description: 'Remove product to business area relationships' }
  ],
  'Product X Client': [
    { value: 'Upload New Items', label: 'ProductXClient_bulkCreate', description: 'Create product to client relationships' },
    { value: 'Remove Existing Items', label: 'ProductXClient_bulkDelete', description: 'Remove product to client relationships' }
  ],
  'Product X Legal Entity': [
    { value: 'Upload New Items', label: 'ProductXLegalEntity_bulkCreate', description: 'Create product to legal entity relationships' },
    { value: 'Remove Existing Items', label: 'ProductXLegalEntity_bulkDelete', description: 'Remove product to legal entity relationships' }
  ],
  'Project X Attribute': [
    { value: 'Upload New Items', label: 'ProjectXAttribute_bulkCreate', description: 'Create project to attribute relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXAttribute_bulkDelete', description: 'Remove project to attribute relationships' }
  ],
  'Project X Business Area': [
    { value: 'Upload New Items', label: 'ProjectXBusinessArea_bulkCreate', description: 'Create project to business area relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXBusinessArea_bulkDelete', description: 'Remove project to business area relationships' }
  ],
  'Project X Capability': [
    { value: 'Upload New Items', label: 'ProjectXCapability_bulkCreate', description: 'Create project to capability relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXCapability_bulkDelete', description: 'Remove project to capability relationships' }
  ],
  'Project X Client': [
    { value: 'Upload New Items', label: 'ProjectXClient_bulkCreate', description: 'Create project to client relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXClient_bulkDelete', description: 'Remove project to client relationships' }
  ],
  'Project X Data Set': [
    { value: 'Upload New Items', label: 'ProjectXDataSet_bulkCreate', description: 'Create project to data set relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXDataSet_bulkDelete', description: 'Remove project to data set relationships' }
  ],
  'Project X Glossary': [
    { value: 'Upload New Items', label: 'ProjectXGlossary_bulkCreate', description: 'Create project to glossary relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXGlossary_bulkDelete', description: 'Remove project to glossary relationships' }
  ],
  'Project X Process': [
    { value: 'Upload New Items', label: 'ProjectXProcess_bulkCreate', description: 'Create project to process relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXProcess_bulkDelete', description: 'Remove project to process relationships' }
  ],
  'Project X Product': [
    { value: 'Upload New Items', label: 'ProjectXProduct_bulkCreate', description: 'Create project to product relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXProduct_bulkDelete', description: 'Remove project to product relationships' }
  ],
  'Project X Project': [
    { value: 'Upload New Items', label: 'ProjectXProject_bulkCreate', description: 'Create project to project relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXProject_bulkDelete', description: 'Remove project to project relationships' }
  ],
  'Project X System': [
    { value: 'Upload New Items', label: 'ProjectXSystem_bulkCreate', description: 'Create project to system relationships' },
    { value: 'Remove Existing Items', label: 'ProjectXSystem_bulkDelete', description: 'Remove project to system relationships' }
  ],
  'Regulation X Policy': [
    { value: 'Upload New Items', label: 'RegulationXPolicy_bulkCreate', description: 'Create regulation to policy relationships' },
    { value: 'Remove Existing Items', label: 'RegulationXPolicy_bulkDelete', description: 'Remove regulation to policy relationships' }
  ],
  'Regulation X Product': [
    { value: 'Upload New Items', label: 'RegulationXProduct_bulkCreate', description: 'Create regulation to product relationships' },
    { value: 'Remove Existing Items', label: 'RegulationXProduct_bulkDelete', description: 'Remove regulation to product relationships' }
  ],
  'Regulation X Project': [
    { value: 'Upload New Items', label: 'RegulationXProject_bulkCreate', description: 'Create regulation to project relationships' },
    { value: 'Remove Existing Items', label: 'RegulationXProject_bulkDelete', description: 'Remove regulation to project relationships' }
  ],
  'Regulation X Regulator': [
    { value: 'Upload New Items', label: 'RegulationXRegulator_bulkCreate', description: 'Create regulation to regulator relationships' },
    { value: 'Remove Existing Items', label: 'RegulationXRegulator_bulkDelete', description: 'Remove regulation to regulator relationships' }
  ],
  'Regulation X Regulatory Theme': [
    { value: 'Upload New Items', label: 'RegulationXRegulatoryTheme_bulkCreate', description: 'Create regulation to regulatory theme relationships' },
    { value: 'Remove Existing Items', label: 'RegulationXRegulatoryTheme_bulkDelete', description: 'Remove regulation to regulatory theme relationships' }
  ],
  'Segment X Object': [
    { value: 'Update Existing Items', label: 'SegmentXObject_bulkUpdate', description: 'Update segment to object relationships' }
  ],
  'System X Client': [
    { value: 'Upload New Items', label: 'SystemXClient_bulkCreate', description: 'Create system to client relationships' },
    { value: 'Remove Existing Items', label: 'SystemXClient_bulkDelete', description: 'Remove system to client relationships' }
  ],
  'System X Data Onboarding Rule': [
    { value: 'Upload New Items', label: 'SystemXDataOnboardingRule_bulkCreate', description: 'Create system to data onboarding rule relationships' },
    { value: 'Remove Existing Items', label: 'SystemXDataOnboardingRule_bulkDelete', description: 'Remove system to data onboarding rule relationships' }
  ],
  'System X Glossary': [
    { value: 'Upload New Items', label: 'SystemXGlossary_bulkCreate', description: 'Create system to glossary relationships' },
    { value: 'Remove Existing Items', label: 'SystemXGlossary_bulkDelete', description: 'Remove system to glossary relationships' }
  ],
  'System X Legal Entity': [
    { value: 'Upload New Items', label: 'SystemXLegalEntity_bulkCreate', description: 'Create system to legal entity relationships' },
    { value: 'Remove Existing Items', label: 'SystemXLegalEntity_bulkDelete', description: 'Remove system to legal entity relationships' }
  ],
  'System X Product': [
    { value: 'Upload New Items', label: 'SystemXProduct_bulkCreate', description: 'Create system to product relationships' },
    { value: 'Remove Existing Items', label: 'SystemXProduct_bulkDelete', description: 'Remove system to product relationships' }
  ],
  'System X Resource': [
    { value: 'Upload New Items', label: 'SystemXResource_bulkCreate', description: 'Create system to resource relationships' }
  ],
  
  // Role Upload Options
  'Business Area Role': [
    { value: 'Upload New Items', label: 'BusinessAreaRole_bulkCreate', description: 'Assign roles to business areas' },
    { value: 'Remove Existing Items', label: 'BusinessAreaRole_bulkDelete', description: 'Remove role assignments from business areas' }
  ],
  'Capability Role': [
    { value: 'Upload New Items', label: 'CapabilityRole_bulkCreate', description: 'Assign roles to capabilities' },
    { value: 'Remove Existing Items', label: 'CapabilityRole_bulkDelete', description: 'Remove role assignments from capabilities' }
  ],
  'Client Role': [
    { value: 'Upload New Items', label: 'ClientRole_bulkCreate', description: 'Assign roles to clients' },
    { value: 'Remove Existing Items', label: 'ClientRole_bulkDelete', description: 'Remove role assignments from clients' }
  ],
  'Committee Role': [
    { value: 'Upload New Items', label: 'CommitteeRole_bulkCreate', description: 'Assign roles to committees' },
    { value: 'Remove Existing Items', label: 'CommitteeRole_bulkDelete', description: 'Remove role assignments from committees' }
  ],
  'Data Quality Role': [
    { value: 'Upload New Items', label: 'DataQualityRole_bulkCreate', description: 'Assign data quality roles' },
    { value: 'Remove Existing Items', label: 'DataQualityRole_bulkDelete', description: 'Remove data quality role assignments' }
  ],
  'Data Set Role': [
    { value: 'Upload New Items', label: 'DataSetRole_bulkCreate', description: 'Assign roles to data sets' },
    { value: 'Remove Existing Items', label: 'DataSetRole_bulkDelete', description: 'Remove role assignments from data sets' }
  ],
  'Glossary Role': [
    { value: 'Upload New Items', label: 'GlossaryRole_bulkCreate', description: 'Assign roles to glossary terms' },
    { value: 'Remove Existing Items', label: 'GlossaryRole_bulkDelete', description: 'Remove role assignments from glossary terms' }
  ],
  'Interface Role': [
    { value: 'Upload New Items', label: 'InterfaceRole_bulkCreate', description: 'Assign roles to interfaces' },
    { value: 'Remove Existing Items', label: 'InterfaceRole_bulkDelete', description: 'Remove role assignments from interfaces' }
  ],
  'Legal Entity Role': [
    { value: 'Upload New Items', label: 'LegalEntityRole_bulkCreate', description: 'Assign roles to legal entities' },
    { value: 'Remove Existing Items', label: 'LegalEntityRole_bulkDelete', description: 'Remove role assignments from legal entities' }
  ],
  'Policy Role': [
    { value: 'Upload New Items', label: 'PolicyRole_bulkCreate', description: 'Assign roles to policies' },
    { value: 'Remove Existing Items', label: 'PolicyRole_bulkDelete', description: 'Remove role assignments from policies' }
  ],
  'Process Role': [
    { value: 'Upload New Items', label: 'ProcessRole_bulkCreate', description: 'Assign roles to processes' },
    { value: 'Remove Existing Items', label: 'ProcessRole_bulkDelete', description: 'Remove role assignments from processes' }
  ],
  'Product Role': [
    { value: 'Upload New Items', label: 'ProductRole_bulkCreate', description: 'Assign roles to products' },
    { value: 'Remove Existing Items', label: 'ProductRole_bulkDelete', description: 'Remove role assignments from products' }
  ],
  'Project Role': [
    { value: 'Upload New Items', label: 'ProjectRole_bulkCreate', description: 'Assign roles to projects' },
    { value: 'Remove Existing Items', label: 'ProjectRole_bulkDelete', description: 'Remove role assignments from projects' }
  ],
  'Regulation Role': [
    { value: 'Upload New Items', label: 'RegulationRole_bulkCreate', description: 'Assign roles to regulations' },
    { value: 'Remove Existing Items', label: 'RegulationRole_bulkDelete', description: 'Remove role assignments from regulations' }
  ],
  'System Role': [
    { value: 'Upload New Items', label: 'SystemRole_bulkCreate', description: 'Assign roles to systems' },
    { value: 'Remove Existing Items', label: 'SystemRole_bulkDelete', description: 'Remove role assignments from systems' }
  ]
};

/**
 * Upload types
 */
export const UPLOAD_TYPES = [
  { value: 'Object', label: 'Object' },
  { value: 'Relationship', label: 'Relationship' },
  { value: 'Role', label: 'Role' }
];

/**
 * Get entities for a specific upload type
 * @param {string} uploadType - Upload type ('Object', 'Relationship', 'Role')
 * @returns {Object} Grouped entities for the upload type
 */
export function getEntitiesForUploadType(uploadType) {
  switch (uploadType) {
    case 'Object':
      return UPLOAD_TYPE_GROUPS;
    case 'Relationship':
      return RELATIONSHIP_ENTITIES;
    case 'Role':
      return ROLE_ENTITIES;
    default:
      return UPLOAD_TYPE_GROUPS;
  }
}

/**
 * Get upload options for a specific entity
 * @param {string} entity - Entity name
 * @returns {Array} Upload options array
 */
export function getUploadOptionsForEntity(entity) {
  return UPLOAD_OPTIONS[entity] || [];
}

/**
 * Get all entities grouped by category
 * @returns {Object} Grouped entities
 */
export function getAllGroupedEntities() {
  return UPLOAD_TYPE_GROUPS;
}

/**
 * Get all entities as flat list
 * @returns {Array} All entity names
 */
export function getAllEntities() {
  return Object.values(UPLOAD_TYPE_GROUPS).flat();
}

/**
 * Check if entity has bulk upload support
 * @param {string} entity - Entity name
 * @returns {boolean} True if entity is supported
 */
export function isEntitySupported(entity) {
  return entity in UPLOAD_OPTIONS;
}

/**
 * Get translated description for an upload option
 * Uses i18n translation system with fallback to original description
 * @param {function} t - Translation function from useTranslation hook
 * @param {string} entity - Entity name
 * @param {string} description - Original English description
 * @returns {string} Translated or original description
 */
export function getTranslatedDescription(t, entity, description) {
  if (!t) return description;
  
  // Map entity names to lowercase keys for translation lookups
  const entityKeyMap = {
    'Regulator': 'regulator',
    'Committee': 'committee',
    'Policy': 'policy',
    'Geography': 'geography',
    'Regulatory Theme': 'regulatoryTheme',
    'Process': 'process',
    'Regulation': 'regulation',
    'Project': 'project',
    'Business Area': 'businessArea',
    'Capability': 'capability',
    'Client': 'client',
    'Legal': 'legal',
    'Org. Unit': 'orgUnit',
    'People': 'people',
    'Product': 'product',
    'System': 'system',
    'Dataset': 'dataset',
    'Attribute': 'attribute',
    'Interface': 'interface',
    'Glossary': 'glossary'
  };
  
  const entityKey = entityKeyMap[entity];
  if (!entityKey) return description;
  
  // Try to find a translation key based on the description pattern
  // Example: "Create new regulators" => look for bulkUpload.uploadOption.regulator.create
  if (description.includes('Create')) {
    const key = `bulkUpload.uploadOption.${entityKey}.create`;
    const translated = t(key, null); // Use null to detect if key exists
    if (translated && translated !== key) return translated;
  } else if (description.includes('Update')) {
    const key = `bulkUpload.uploadOption.${entityKey}.update`;
    const translated = t(key, null);
    if (translated && translated !== key) return translated;
  } else if (description.includes('Delete') || description.includes('Remove')) {
    const key = `bulkUpload.uploadOption.${entityKey}.delete`;
    const translated = t(key, null);
    if (translated && translated !== key) return translated;
  }
  
  // Fallback to original description
  return description;
}
