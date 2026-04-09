/**
 * Translation Helpers Utility
 * Provides mappings and helper functions for translating entity and field metadata
 */

/**
 * Map entity display names to translation key names
 * Example: 'Regulatory Theme' => 'regulatoryTheme'
 */
export const ENTITY_NAME_MAP = {
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

/**
 * Map upload options to operation types
 * Example: 'Add New Items' => 'create'
 */
export const OPERATION_MAP = {
  'Add New Items': 'create',
  'Update Existing Items': 'update',
  'Remove Existing Items': 'delete',
  'Upload New Items': 'create'  // For relationships
};

/**
 * Get sheet name translation key for an entity and operation
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @param {string} uploadOption - Upload option (e.g., 'Add New Items')
 * @returns {string} Translation key (e.g., 'bulkUpload.sheetName.regulator.create')
 */
export function getSheetNameTranslationKey(entity, uploadOption) {
  const entityKey = ENTITY_NAME_MAP[entity];
  const operationKey = OPERATION_MAP[uploadOption];
  
  if (!entityKey || !operationKey) {
    console.warn(`Cannot map entity="${entity}" or operation="${uploadOption}" to translation key`);
    return null;
  }
  
  return `bulkUpload.sheetName.${entityKey}.${operationKey}`;
}

/**
 * Get entity name translation key
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @returns {string} Translation key (e.g., 'bulkUpload.entity.regulator')
 */
export function getEntityTranslationKey(entity) {
  const entityKey = ENTITY_NAME_MAP[entity];
  
  if (!entityKey) {
    console.warn(`Cannot map entity="${entity}" to translation key`);
    return null;
  }
  
  return `bulkUpload.entity.${entityKey}`;
}

/**
 * Get upload description translation key
 * Example: For "Create new regulators" => 'bulkUpload.uploadOption.regulator.create'
 * Falls back to a parameterized format if needed
 * @param {string} entity - Entity name
 * @param {string} uploadOption - Upload option
 * @returns {string} Translation key
 */
export function getUploadDescriptionTranslationKey(entity, uploadOption) {
  const entityKey = ENTITY_NAME_MAP[entity];
  const operationKey = OPERATION_MAP[uploadOption];
  
  if (!entityKey || !operationKey) {
    // Return fallback key that uses parameterized description
    return 'bulkUpload.operation.{operation}Desc';
  }
  
  // Try specific key first
  return `bulkUpload.uploadOption.${entityKey}.${operationKey}`;
}

/**
 * Get display name for a field with translation support
 * For dynamic fields not in the translation file, provides intelligent fallback
 * @param {string} displayName - Field display name
 * @returns {string} Translation key for field display name
 */
export function getFieldDisplayNameTranslationKey(displayName) {
  // Common field names that are likely to appear in many entities
  const commonFieldMap = {
    'Primary Name': 'entity.field.primaryName',
    'Short Name': 'entity.field.shortName',
    'Name': 'entity.field.name',
    'Description': 'entity.field.description',
    'ID': 'entity.field.id',
    'Code': 'entity.field.code',
    'Status': 'entity.field.status',
    'Type': 'entity.field.type'
  };
  
  if (commonFieldMap[displayName]) {
    return `bulkUpload.${commonFieldMap[displayName]}`;
  }
  
  // For unknown fields, return the display name as-is (no translation key)
  return null;
}

/**
 * Get a fallback sheet name if translation is not available
 * Useful for debugging or as a last resort
 * @param {string} entity - Entity name
 * @param {string} uploadOption - Upload option
 * @returns {string} Fallback sheet name
 */
export function getSheetNameFallback(entity, uploadOption) {
  const operation = OPERATION_MAP[uploadOption] || uploadOption;
  const operationText = operation.charAt(0).toUpperCase() + operation.slice(1);
  return `${operationText} ${entity}`;
}

/**
 * Get fallback entity name (for when translation key doesn't exist)
 * @param {string} entity - Entity name
 * @returns {string} Entity name (unchanged)
 */
export function getEntityNameFallback(entity) {
  return entity;
}

export default {
  ENTITY_NAME_MAP,
  OPERATION_MAP,
  getSheetNameTranslationKey,
  getEntityTranslationKey,
  getUploadDescriptionTranslationKey,
  getFieldDisplayNameTranslationKey,
  getSheetNameFallback,
  getEntityNameFallback
};
