import React, { useState, useEffect } from 'react';
import * as XLSX from 'xlsx';
import Button from './ui/Button';
import Alert from './ui/Alert';
import { getFieldsForEntity, getOperationType } from '../config/entityConfig';
import { getFieldMetadata } from '../services/apiService';
import { useTranslation } from '../hooks/useTranslation';
import { getSheetNameTranslationKey, getSheetNameFallback } from '../utils/translationHelpers';

const StepMapColumns = ({ stepData, onNext, onBack }) => {
  const { t } = useTranslation();
  const [excelColumns, setExcelColumns] = useState([]);
  const [columnMappings, setColumnMappings] = useState({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [fieldDefinitions, setFieldDefinitions] = useState([]);

  useEffect(() => {
    parseExcelFile();
  }, []);

  // Get sheet name based on entity and upload option
  const getSheetName = (entity, uploadOption) => {
    // Use translation system for sheet names
    const translationKey = getSheetNameTranslationKey(entity, uploadOption);
    if (translationKey) {
      const translatedName = t(translationKey);
      // If translation key exists and returned a valid translation, use it
      if (translatedName && translatedName !== translationKey) {
        return translatedName;
      }
    }
    // Fallback to generating sheet name from entity and operation
    return getSheetNameFallback(entity, uploadOption);
  };

  // Parse Excel file to extract column names
  const parseExcelFile = async () => {
    try {
      setLoading(true);
      const file = stepData.file;

      const reader = new FileReader();
      reader.onload = async (e) => {
        try {
          const data = new Uint8Array(e.target.result);
          const workbook = XLSX.read(data, { type: 'array' });

          // Get sheet name based on entity and upload option
          const targetSheetName = getSheetName(stepData.uploadTypeGroup, stepData.uploadOption);
          let worksheet;
          let sheetName;

          // Filter out hidden sheets (sheets starting with "Hidden_")
          const visibleSheets = workbook.SheetNames.filter(name => !name.startsWith('Hidden_'));

          if (!visibleSheets.length) {
            setError(t('bulkUpload.step2.noVisibleSheets', 'No visible sheets found in Excel file. All sheets appear to be hidden.'));
            setLoading(false);
            return;
          }

          if (targetSheetName && workbook.SheetNames.includes(targetSheetName)) {
            // Use specific sheet if found
            sheetName = targetSheetName;
            worksheet = workbook.Sheets[targetSheetName];
            console.log(`Using expected sheet: ${sheetName}`);
          } else {
            // Fallback to first visible sheet
            sheetName = visibleSheets[0];
            worksheet = workbook.Sheets[sheetName];

            // Log which sheet is being used
            if (targetSheetName) {
              console.warn(`Expected sheet "${targetSheetName}" not found. Using first visible sheet "${sheetName}" instead.`);
            } else {
              console.log(`No expected sheet name. Using first visible sheet: ${sheetName}`);
            }
          }

          // Convert to JSON to get headers
          const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 });

          if (jsonData.length === 0) {
            setError(t('bulkUpload.step2.excelFileEmpty', 'Excel file is empty'));
            setLoading(false);
            return;
          }

          // First row contains headers
          const headers = jsonData[0].filter(h => h && h.toString().trim() !== '');
          setExcelColumns(headers);

          // Get field definitions based on entity and operation
          const operation = getOperationType(stepData.uploadOption);
          const standardFields = getFieldsForEntity(stepData.uploadTypeGroup, operation);

          let fieldsToMap = standardFields;

          // Fetch custom fields from API and merge with standard fields
          try {
            const apiFields = await getFieldMetadata(stepData.uploadTypeGroup, operation);
            const apiFieldList = Array.isArray(apiFields) ? apiFields : [];

            // Filter custom fields from API response (only for INSERT and UPDATE operations)
            const customFields = apiFieldList
              .filter(field => field.isCustomField && (operation === 'INSERT' || operation === 'UPDATE'))
              .map(field => ({
                name: field.name || field.fieldName || field.displayName,
                displayName: field.displayName,
                required: field.required || false,
                dataType: field.dataType || 'STRING',
                description: field.description || '',
                isCustomField: true,
                aliases: Array.isArray(field.aliases) ? field.aliases : undefined
              }));

            // Merge: standard first, then customs not already present (API may duplicate static defs)
            const fieldKey = (f) => {
              const n = (f.name || f.displayName || '').toString().trim().toLowerCase();
              const d = (f.displayName || f.name || '').toString().trim().toLowerCase();
              return `${n}|${d}`;
            };
            const seen = new Set(standardFields.map(fieldKey));
            const mergedCustoms = customFields.filter((cf) => {
              const k = fieldKey(cf);
              if (seen.has(k)) return false;
              seen.add(k);
              return true;
            });
            fieldsToMap = [...standardFields, ...mergedCustoms];
          } catch (error) {
            // Silently fail for 404 errors (endpoint not available) - custom fields are optional
            // Only log other errors
            if (error.response?.status !== 404) {
              console.warn('Failed to fetch custom fields from API, using standard fields only:', error);
            }
          }

          setFieldDefinitions(fieldsToMap);

          // Auto-map columns by exact name match and aliases (smart mapping)
          const autoMappings = {};

          // Helper function to normalize strings for comparison
          const normalize = (str) => {
            return str.toString().trim().toLowerCase()
              .replace(/\s+/g, ' ') // Normalize whitespace
              .replace(/[._-]/g, ' '); // Replace separators with space
          };

          // Helper function to check if two strings are similar (fuzzy match)
          const isSimilar = (str1, str2) => {
            const norm1 = normalize(str1);
            const norm2 = normalize(str2);

            // Exact match after normalization
            if (norm1 === norm2) return true;

            // Check if one contains the other (for partial matches)
            if (norm1.includes(norm2) || norm2.includes(norm1)) return true;

            // Check if all key words are present
            const words1 = norm1.split(/\s+/).filter(w => w.length > 2);
            const words2 = norm2.split(/\s+/).filter(w => w.length > 2);

            if (words1.length > 0 && words2.length > 0) {
              // Check if most words match
              const matchingWords = words1.filter(w => words2.includes(w));
              const matchRatio = matchingWords.length / Math.max(words1.length, words2.length);
              if (matchRatio >= 0.7) return true; // 70% word match
            }

            return false;
          };

          fieldsToMap.forEach(field => {
            let matchingColumn = null;
            const fieldNameLower = normalize(field.name);
            const displayNameLower = normalize(field.displayName);

            // Priority 1: Try exact match with field.name (case-insensitive, normalized)
            matchingColumn = headers.find(
              col => normalize(col) === fieldNameLower
            );

            // Priority 2: Try exact match with displayName
            if (!matchingColumn) {
              matchingColumn = headers.find(
                col => normalize(col) === displayNameLower
              );
            }

            // Priority 3: Try matching with aliases (exact match)
            if (!matchingColumn && field.aliases && Array.isArray(field.aliases)) {
              for (const alias of field.aliases) {
                matchingColumn = headers.find(
                  col => normalize(col) === normalize(alias)
                );
                if (matchingColumn) break;
              }
            }

            // Priority 3b: Fuzzy match on aliases (e.g. custom field technical names vs Excel headers)
            if (!matchingColumn && field.aliases && Array.isArray(field.aliases)) {
              for (const col of headers) {
                const alreadyMapped = Object.values(autoMappings).includes(col.toString());
                if (alreadyMapped) continue;
                for (const alias of field.aliases) {
                  if (isSimilar(col, alias)) {
                    matchingColumn = col;
                    break;
                  }
                }
                if (matchingColumn) break;
              }
            }

            // Priority 4: Fuzzy matching - check for similar column names
            if (!matchingColumn) {
              // For fields with "Short Name" or "Name", be flexible
              const isNameField = fieldNameLower.includes('name') || displayNameLower.includes('name');
              const isShortNameField = fieldNameLower.includes('short') || displayNameLower.includes('short');
              const isParentField = fieldNameLower.includes('parent') || displayNameLower.includes('parent');
              const isViewingField = fieldNameLower.includes('viewing') || displayNameLower.includes('viewing') ||
                fieldNameLower.includes('public') || displayNameLower.includes('public');
              const isStatusField = fieldNameLower.includes('status') || displayNameLower.includes('status');

              for (const col of headers) {
                const colNormalized = normalize(col);

                // Skip if column is already mapped to another field
                const alreadyMapped = Object.values(autoMappings).includes(col.toString());
                if (alreadyMapped) continue;

                // Skip parent columns if this is not a parent field
                if (!isParentField && colNormalized.includes('parent')) continue;

                // Special handling for BUDG Viewing / Axon Viewing fields
                if (isViewingField) {
                  // Match viewing-related columns: "Axon Viewing", "Viewing", "is_Public", "Public", etc.
                  if (colNormalized.includes('viewing') || colNormalized.includes('public') ||
                    colNormalized.includes('axon') && colNormalized.includes('viewing')) {
                    matchingColumn = col;
                    break;
                  }
                }
                // Special handling for BUDG Status / Axon Status fields
                else if (isStatusField) {
                  // Match status-related columns: "Axon Status", "Status", etc.
                  if (colNormalized.includes('status') ||
                    (colNormalized.includes('axon') && colNormalized.includes('status'))) {
                    matchingColumn = col;
                    break;
                  }
                }
                // Special handling for Legal Entity fields
                else if (fieldNameLower.includes('legal') || displayNameLower.includes('legal')) {
                  // Accept "Legal Entity Short Name", "Legal Short Name", etc.
                  if (colNormalized.includes('legal') && colNormalized.includes('name') && !colNormalized.includes('parent')) {
                    // Check if both have "short" or both don't have "short"
                    const colHasShort = colNormalized.includes('short');
                    if (isShortNameField && colHasShort) {
                      matchingColumn = col;
                      break;
                    } else if (!isShortNameField && !colHasShort) {
                      matchingColumn = col;
                      break;
                    }
                  }
                }
                // Special handling for System fields
                else if (fieldNameLower.includes('system') || displayNameLower.includes('system')) {
                  // Accept "System Short Name", "System Name", etc.
                  if (colNormalized.includes('system') && colNormalized.includes('name') && !colNormalized.includes('parent')) {
                    const colHasShort = colNormalized.includes('short');
                    if (isShortNameField && colHasShort) {
                      matchingColumn = col;
                      break;
                    } else if (!isShortNameField && !colHasShort) {
                      matchingColumn = col;
                      break;
                    }
                  }
                }
                // Special handling for Parent fields
                else if (isParentField) {
                  // Match parent fields - must contain "parent" and entity name
                  if (colNormalized.includes('parent') && isSimilar(col, field.displayName)) {
                    matchingColumn = col;
                    break;
                  }
                }
                // General fuzzy matching for other fields
                else if (isSimilar(col, field.displayName) || isSimilar(col, field.name)) {
                  matchingColumn = col;
                  break;
                }
              }
            }

            if (matchingColumn) {
              autoMappings[field.name] = matchingColumn.toString();
            } else {
              autoMappings[field.name] = ''; // Not mapped
            }
          });

          setColumnMappings(autoMappings);
          setLoading(false);
        } catch (err) {
          console.error('Error parsing Excel:', err);
          setError(t('bulkUpload.step2.parseError', `Failed to parse Excel file: ${err.message}`));
          setLoading(false);
        }
      };

      reader.onerror = () => {
        setError(t('bulkUpload.step2.readError', 'Failed to read file'));
        setLoading(false);
      };

      reader.readAsArrayBuffer(file);
    } catch (err) {
      console.error('Error reading file:', err);
      setError(t('bulkUpload.step2.readFileError', `Failed to read file: ${err.message}`));
      setLoading(false);
    }
  };

  // Handle mapping change
  const handleMappingChange = (fieldName, excelColumn) => {
    setColumnMappings(prev => ({
      ...prev,
      [fieldName]: excelColumn
    }));
  };

  // Validate mappings
  const validateMappings = () => {
    const requiredFields = fieldDefinitions.filter(f => f.required);
    const unmappedRequired = requiredFields.filter(f => !columnMappings[f.name] || columnMappings[f.name] === '');

    if (unmappedRequired.length > 0) {
      const fieldNames = unmappedRequired.map(f => f.displayName).join(', ');
      setError(t('bulkUpload.step2.requiredFieldsNotMapped', `Required fields not mapped: ${fieldNames}`));
      return false;
    }

    setError('');
    return true;
  };

  // Handle next step
  const handleNext = () => {
    if (!validateMappings()) {
      return;
    }

    onNext({
      ...stepData,
      columnMappings,
      excelColumns,
      fieldDefinitions
    });
  };

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto">
        <div className="bg-white rounded-lg shadow-md p-6">
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
            <span className="ml-3 text-gray-600">{t('bulkUpload.step2.parsingExcel', 'Parsing Excel file...')}</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="bg-white rounded-lg shadow-md p-6">
        <h2 className="text-2xl font-bold text-gray-800 mb-6">{t('bulkUpload.step2.title', 'Step 2: Map Columns')}</h2>

        {error && (
          <Alert type="error" message={error} onClose={() => setError('')} />
        )}

        {/* Summary Info */}
        <div className="mb-6 p-4 bg-gray-50 rounded border border-gray-200">
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="font-medium text-gray-700">{t('bulkUpload.step2.entity', 'Entity:')}</span>
              <span className="ml-2 text-gray-600">{stepData.uploadTypeGroup}</span>
            </div>
            <div>
              <span className="font-medium text-gray-700">{t('bulkUpload.step2.operation', 'Operation:')}</span>
              <span className="ml-2 text-gray-600">{stepData.uploadOption}</span>
            </div>
            <div>
              <span className="font-medium text-gray-700">{t('bulkUpload.step2.file', 'File:')}</span>
              <span className="ml-2 text-gray-600">{stepData.file.name}</span>
            </div>
            <div>
              <span className="font-medium text-gray-700">{t('bulkUpload.step2.columnsFound', 'Columns Found:')}</span>
              <span className="ml-2 text-gray-600">{excelColumns.length}</span>
            </div>
          </div>
        </div>

        {/* Mapping Table */}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="bg-gray-100 border-b-2 border-gray-300">
                <th className="text-left p-3 font-semibold text-gray-700">{t('bulkUpload.step2.columnInFile', 'Column in Uploaded File')}</th>
                <th className="text-left p-3 font-semibold text-gray-700">{t('bulkUpload.step2.budgField', 'BUDG Field')}</th>
                <th className="text-left p-3 font-semibold text-gray-700">{t('bulkUpload.step2.description', 'Description')}</th>
              </tr>
            </thead>
            <tbody>
              {fieldDefinitions.map((field, index) => (
                <tr key={field.name} className={`border-b ${index % 2 === 0 ? 'bg-white' : 'bg-gray-50'}`}>
                  {/* Excel Column Dropdown */}
                  <td className="p-3">
                    <select
                      value={columnMappings[field.name] || ''}
                      onChange={(e) => handleMappingChange(field.name, e.target.value)}
                      className={`w-full px-3 py-2 border rounded focus:outline-none focus:ring-2 focus:ring-primary ${field.required && !columnMappings[field.name]
                          ? 'border-danger'
                          : 'border-gray-300'
                        }`}
                    >
                      <option value="">{t('bulkUpload.step2.skip', '-- Skip --')}</option>
                      {excelColumns.map(col => (
                        <option key={col} value={col}>{col}</option>
                      ))}
                    </select>
                  </td>

                  {/* BUDG Field Name */}
                  <td className="p-3">
                    <div className="flex items-center">
                      <span className={`font-medium ${stepData.uploadTypeGroup === 'Project' && (field.name === 'BUDG Status' || field.displayName === 'BUDG Status') ? 'text-danger' : 'text-gray-800'}`}>
                        {field.displayName}
                      </span>
                      {(field.required || (stepData.uploadTypeGroup === 'Project' && (field.name === 'BUDG Status' || field.displayName === 'BUDG Status'))) && (
                        <span className="text-danger ml-1 text-lg" title={t('bulkUpload.step2.requiredField', 'Required field')}>*</span>
                      )}
                      {field.isCustomField && (
                        <span className="ml-2 px-2 py-0.5 text-xs bg-blue-100 text-blue-800 rounded" title={t('bulkUpload.step2.customField', 'Custom Field')}>
                          {t('bulkUpload.step2.custom', 'Custom')}
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-gray-500 mt-1">
                      {t('bulkUpload.step2.type', 'Type')}: {field.dataType}
                    </div>
                  </td>

                  {/* Description */}
                  <td className="p-3 text-sm text-gray-600">
                    {field.description}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Mapping Summary */}
        <div className="mt-6 p-4 bg-blue-50 rounded border border-blue-200">
          <h3 className="text-sm font-medium text-blue-800 mb-2">{t('bulkUpload.step2.mappingSummary', 'Mapping Summary')}</h3>
          <div className="text-xs text-blue-700 space-y-1">
            <p>
              • {t('bulkUpload.step2.totalFields', 'Total fields')}: {fieldDefinitions.length}
              ({fieldDefinitions.filter(f => f.required).length} {t('bulkUpload.step2.required', 'required')}, {fieldDefinitions.filter(f => !f.required).length} {t('bulkUpload.step2.optional', 'optional')})
            </p>
            <p>
              • {t('bulkUpload.step2.mappedFields', 'Mapped fields')}: {Object.values(columnMappings).filter(v => v !== '').length}
            </p>
            <p>
              • {t('bulkUpload.step2.unmappedFields', 'Unmapped fields')}: {Object.values(columnMappings).filter(v => v === '').length}
            </p>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex justify-between mt-6">
          <Button onClick={onBack} variant="secondary">
            ← {t('bulkUpload.step2.startOver', 'Start Over')}
          </Button>
          <Button onClick={handleNext} variant="primary">
            {t('bulkUpload.step2.next', 'Next → Start Upload')}
          </Button>
        </div>
      </div>
    </div>
  );
};

export default StepMapColumns;

