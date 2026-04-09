import React, { useState, useRef, useEffect, useMemo } from 'react';
import Select from './ui/Select';
import Checkbox from './ui/Checkbox';
import Button from './ui/Button';
import Alert from './ui/Alert';
import { UPLOAD_TYPES, getEntitiesForUploadType, getUploadOptionsForEntity, isEntitySupported } from '../config/uploadTypes';
import { getUserBulkUploadPermissions, getAccessibleSegments, isInformationSegmentationEnabled, getDefaultSegmentSettings } from '../services/apiService';
import { ensureCurrentUserId } from '../utils/auth';
import { useTranslation } from '../hooks/useTranslation';

// Segment mode enum
const SEGMENT_MODE = {
  MULTIPLE: 'MULTIPLE',
  ENTERPRISE: 'ENTERPRISE',
  SPECIFIC: 'SPECIFIC'
};

const StepChooseFile = ({ onNext, initialData = {} }) => {
  const { t, locale } = useTranslation();
  const [uploadType, setUploadType] = useState(initialData.uploadType || 'Object');
  const [uploadTypeGroup, setUploadTypeGroup] = useState(initialData.uploadTypeGroup || '');
  const [uploadOption, setUploadOption] = useState(initialData.uploadOption || '');
  const [segment, setSegment] = useState(initialData.segment || '');
  const [segmentMode, setSegmentMode] = useState(initialData.segmentMode || '');
  const [file, setFile] = useState(initialData.file || null);
  const [cancelOnWarning, setCancelOnWarning] = useState(initialData.cancelOnWarning !== undefined ? initialData.cancelOnWarning : true);
  const [error, setError] = useState('');
  const [dragActive, setDragActive] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [permissions, setPermissions] = useState({ allowedEntities: {} });
  const [loadingPermissions, setLoadingPermissions] = useState(true);
  const [segments, setSegments] = useState([]);
  const [loadingSegments, setLoadingSegments] = useState(false);
  const [segmentationEnabled, setSegmentationEnabled] = useState(false);
  const [defaultSegmentSettings, setDefaultSegmentSettings] = useState({ enterpriseSegmentDefault: false, assignedSegmentsDefault: false });
  const fileInputRef = useRef(null);

  // Attribute entity does not use segment for template download or upload
  const isAttributeEntity = uploadTypeGroup != null && String(uploadTypeGroup).toLowerCase() === 'attribute';
  // Org Unit: hide segment UI and do not send segment params for template download
  const isOrgUnitEntity = uploadTypeGroup === 'Org. Unit';
  // People: hide segment UI and do not send segment params for template download
  const isPeopleEntity = uploadTypeGroup === 'People';
  // Interface: hide segment UI and do not send segment params (inherits from Target System or Enterprise)
  const isInterfaceEntity = uploadTypeGroup === 'Interface';

  // Load user permissions and segmentation enabled status on component mount
  useEffect(() => {
    const loadPermissions = async () => {
      try {
        // Permissions API uses current authenticated user from /api/me (via AuthFilter)
        // No need to pass userId - servlet gets it from request attributes
        console.log('Loading permissions for current authenticated user...');
        const perms = await getUserBulkUploadPermissions();
        console.log('Loaded permissions:', perms);

        // Check if access was denied (403 error)
        if (perms.status === 'error' && perms.message && perms.message.includes('Access denied')) {
          setError(t('bulkUpload.errors.accessDenied', 'Access denied. Only Admin and Super Admin can access bulk upload. Please contact your administrator.'));
          setPermissions({ allowedEntities: {} });
        } else {
          setPermissions(perms);
        }
      } catch (err) {
        console.error('Error loading permissions:', err);

        // Check if it's an access denied error (403)
        if (err.response && err.response.status === 403) {
          setError(t('bulkUpload.errors.accessDenied', 'Access denied. Only Admin and Super Admin can access bulk upload. Please contact your administrator.'));
        } else if (err.response && err.response.data && err.response.data.message) {
          setError(err.response.data.message);
        } else {
          setError(t('bulkUpload.errors.loadPermissionsFailed', 'Failed to load user permissions. Please refresh the page or contact your administrator.'));
        }
        // Continue with empty permissions (fail-safe)
        setPermissions({ allowedEntities: {} });
      } finally {
        setLoadingPermissions(false);
      }
    };

    const loadDefaultSegmentSettings = async () => {
      try {
        const settings = await getDefaultSegmentSettings();
        setDefaultSegmentSettings(settings);
        console.log('Default segment settings:', settings);

        // Segmentation is enabled if either enterprise_segment_default OR assigned_segments_default is enabled
        const enabled = settings.enterpriseSegmentDefault || settings.assignedSegmentsDefault;
        setSegmentationEnabled(enabled);
        console.log('Information Segmentation enabled:', enabled);
      } catch (err) {
        console.error('Error loading default segment settings:', err);
        setDefaultSegmentSettings({ enterpriseSegmentDefault: false, assignedSegmentsDefault: false });
        setSegmentationEnabled(false); // Default to disabled on error
      }
    };

    loadPermissions();
    loadDefaultSegmentSettings();
  }, [initialData.userId]);

  // Reset entity and upload option when upload type changes
  useEffect(() => {
    setUploadTypeGroup('');
    setUploadOption('');
    setSegment('');
    setSegmentMode('');
    setError('');
  }, [uploadType]);

  // Clear segment when switching to Attribute, Org Unit, or People (segment not used for these templates)
  useEffect(() => {
    if (isAttributeEntity || isOrgUnitEntity || isPeopleEntity || isInterfaceEntity) {
      setSegment('');
      setSegmentMode('');
    }
  }, [uploadTypeGroup, isAttributeEntity, isOrgUnitEntity, isPeopleEntity, isInterfaceEntity]);

  // Helper function to check if upload option is a CREATE operation
  const isCreateOperation = (optionValue) => {
    if (!optionValue) return false;
    if (optionValue === 'Add New Items' || optionValue === 'Upload New Items') {
      return true;
    }
    // Check if the option label ends with '_bulkCreate' by looking it up in the entity's options
    if (uploadTypeGroup) {
      const allOptions = getUploadOptionsForEntity(uploadTypeGroup);
      const option = allOptions.find(opt => opt.value === optionValue);
      return option && option.label && option.label.endsWith('_bulkCreate');
    }
    return false;
  };

  // Load accessible segments when upload type is Object and upload option is a CREATE operation
  // AND Information Segmentation is enabled (not for Attribute, Org Unit, or People - segment not used for these)
  useEffect(() => {
    const loadSegments = async () => {
      if (uploadType === 'Object' && !isAttributeEntity && !isOrgUnitEntity && !isPeopleEntity && !isInterfaceEntity && isCreateOperation(uploadOption) && segmentationEnabled) {
        setLoadingSegments(true);
        try {
          const segmentsData = await getAccessibleSegments();
          if (segmentsData.success && segmentsData.segments) {
            setSegments(segmentsData.segments);

            // Auto-select Enterprise Segment as default (only if not already set)
            if (!segmentMode) {
              setSegmentMode(SEGMENT_MODE.ENTERPRISE);
              setSegment('1'); // Enterprise segment ID is 1
            }
          } else {
            setSegments([]);
          }
        } catch (err) {
          console.error('Error loading segments:', err);
          setSegments([]);
        } finally {
          setLoadingSegments(false);
        }
      } else {
        setSegments([]);
        setSegment('');
        setSegmentMode('');
      }
    };

    loadSegments();
  }, [uploadType, uploadOption, uploadTypeGroup, segmentationEnabled, segmentMode, isAttributeEntity, isOrgUnitEntity, isPeopleEntity, isInterfaceEntity]);

  // Filter entities based on upload type and permissions
  const getFilteredEntities = () => {
    const filtered = {};
    const entitiesForType = getEntitiesForUploadType(uploadType);

    Object.entries(entitiesForType).forEach(([groupName, entities]) => {
      // For relationships and roles, don't filter by permissions (show all)
      // For objects, filter by permissions
      if (uploadType === 'Relationship' || uploadType === 'Role') {
        // Show all relationship and role entities without permission filtering
        filtered[groupName] = entities;
      } else {
        // For objects, filter based on permissions
        const allowedEntities = entities.filter(entity => {
          const entityPerms = permissions.allowedEntities[entity];
          // Show entity if user has Create permission (can upload)
          return entityPerms && entityPerms.canUpload;
        });
        if (allowedEntities.length > 0) {
          filtered[groupName] = allowedEntities;
        }
      }
    });
    return filtered;
  };

  // Get available upload options based on selected entity and permissions
  const getAvailableOptions = () => {
    if (!uploadTypeGroup) return [];

    const allOptions = getUploadOptionsForEntity(uploadTypeGroup);

    // Helper function to translate option value
    const translateOptionValue = (value) => {
      const keyMap = {
        'Add New Items': 'uploadOptions.addNewItems',
        'Update Existing Items': 'uploadOptions.updateExistingItems',
        'Remove Existing Items': 'uploadOptions.removeExistingItems',
        'Upload New Items': 'uploadOptions.uploadNewItems'
      };
      const translationKey = keyMap[value];
      if (translationKey) {
        return t(translationKey, value);
      }
      return value;
    };

    // Filter options based on permissions (for objects only)
    let filteredOptions = allOptions;
    if (uploadType !== 'Relationship' && uploadType !== 'Role') {
      const entityPerms = permissions.allowedEntities[uploadTypeGroup];
      if (!entityPerms) return []; // No permissions = no options

      filteredOptions = allOptions.filter(option => {
        if (option.label.includes('Create') || option.value === 'Upload New Items' || option.value === 'Add New Items') {
          return entityPerms.canCreate;
        } else if (option.label.includes('Update') || option.value === 'Update Existing Items') {
          return entityPerms.canUpdate;
        } else if (option.label.includes('Delete') || option.value === 'Remove Existing Items') {
          return entityPerms.canDelete;
        }
        return true; // Default: allow if permission check not found
      });
    }

    // Translate options before returning
    return filteredOptions.map(option => ({
      ...option,
      value: option.value, // Keep original value for backend
      label: translateOptionValue(option.value), // Translate the displayed label
      description: option.description ? t(`uploadOptions.${option.value.replace(/\s+/g, '')}.description`, option.description) : option.description
    }));
  };

  // Memoize available options to recalculate when locale changes
  const availableOptions = useMemo(() => getAvailableOptions(), [uploadTypeGroup, uploadType, permissions, locale]);
  const filteredEntities = getFilteredEntities();

  // Handle entity selection change
  const handleEntityChange = (entity) => {
    setUploadTypeGroup(entity);
    setUploadOption(''); // Reset upload option when entity changes
    setSegment('');
    setSegmentMode('');

    // Show warning if entity is not yet supported
    if (!isEntitySupported(entity)) {
      setError(t('bulkUpload.errors.entityNotSupported', `Bulk upload for ${entity} is not yet implemented.`).replace('{entity}', entity));
    } else {
      setError('');
    }
  };

  // Handle segment selection change
  const handleSegmentChange = (selectedValue) => {
    setSegment(selectedValue);

    if (selectedValue === 'Multiple') {
      setSegmentMode(SEGMENT_MODE.MULTIPLE);
      setSegment('');
    } else if (selectedValue === 'Enterprise') {
      setSegmentMode(SEGMENT_MODE.ENTERPRISE);
      setSegment('1'); // Enterprise segment ID is 1
    } else if (selectedValue) {
      // Specific segment selected - find the segment ID
      const selectedSegment = segments.find(s => s.name === selectedValue || s.id === parseInt(selectedValue));
      if (selectedSegment) {
        setSegmentMode(SEGMENT_MODE.SPECIFIC);
        setSegment(selectedSegment.id.toString());
      }
    } else {
      setSegmentMode('');
      setSegment('');
    }
  };

  // Handle template download
  const handleDownloadTemplate = async () => {
    if (!uploadTypeGroup || !uploadOption) {
      setError(t('bulkUpload.errors.selectEntityAndOption', 'Please select both Entity and Upload Option first'));
      return;
    }

    console.log('Download template - Upload Type:', uploadType, 'Entity:', uploadTypeGroup, 'Option:', uploadOption);

    // Map upload option to template type
    const templateTypeMap = {
      'Upload New Items': 'INSERT',
      'Add New Items': 'INSERT',
      'Update Existing Items': 'UPDATE',
      'Remove Existing Items': 'DELETE'
    };

    const templateType = templateTypeMap[uploadOption];
    if (!templateType) {
      console.error('Template mapping not found for:', uploadOption);
      setError(t('bulkUpload.errors.templateNotAvailable', `Template not available for "${uploadOption}". Available options: ${Object.keys(templateTypeMap).join(', ')}`));
      return;
    }

    let templatePath;

    // Handle Role templates with new endpoint
    if (uploadType === 'Role') {
      // Convert role entity name to URL-friendly format
      // e.g., 'Business Area Role' -> 'businessarearole'
      const roleEntity = uploadTypeGroup.toLowerCase().replace(/\s+/g, '').replace(/\./g, '');
      templatePath = `/api/bulk/templates/generate-role/${roleEntity}/${templateType}`;
      console.log('Downloading Role template from:', templatePath);
    } else if (uploadType === 'Relationship') {
      // Handle Relationship templates with new endpoint
      // Convert relationship entity name to URL-friendly format
      // e.g., 'People X People' -> 'peoplexpeople'
      const relationshipKey = uploadTypeGroup.toLowerCase().replace(/\s+/g, '').replace(/\./g, '');
      templatePath = `/api/bulk/templates/generate-relationship/${relationshipKey}/${templateType}`;
      console.log('Downloading Relationship template from:', templatePath);
    } else {
      // Handle Object templates with existing endpoint
      // Convert entity name to URL-friendly format (e.g., 'Regulatory Theme' -> 'regulatorytheme', 'Org. Unit' -> 'orgunit')
      const entity = uploadTypeGroup.toLowerCase().replace(/\s+/g, '').replace(/\./g, '');

      // Map entity names to URL format
      const entityMap = {
        'regulator': 'regulator',
        'geography': 'geography',
        'regulatorytheme': 'regulatorytheme',
        'regulatory_theme': 'regulatorytheme',
        'regulation': 'regulation',
        'policy': 'policy',
        'process': 'process',
        'project': 'project',
        'committee': 'committee',
        'legalentity': 'legal',  // Map "Legal Entity" to "legal"
        'legal': 'legal'  // Also handle if already "legal"
      };

      const entityUrl = entityMap[entity] || entity;
      templatePath = `/api/bulk/templates/generate/${entityUrl}/${templateType}`;


      // Add segment parameters if segment mode is set (only for INSERT templates; not for Attribute, Org Unit, People, or Interface)
      if (templateType === 'INSERT' && segmentMode && !isAttributeEntity && !isOrgUnitEntity && !isPeopleEntity && !isInterfaceEntity) {
        const params = new URLSearchParams();
        params.append('segmentMode', segmentMode);
        if (segment) {
          params.append('segment', segment);
        }
        templatePath += '?' + params.toString();
      }

      console.log('Downloading Object template from:', templatePath);
      console.log('Entity mapping:', { original: uploadTypeGroup, normalized: entity, url: entityUrl, segmentMode, segment });
    }

    setDownloading(true);
    setError('');

    try {
      // Use fetch to download the file as a blob
      const response = await fetch(templatePath, {
        method: 'GET',
        headers: {
          'Accept': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        }
      });

      if (!response.ok) {
        // Try to parse error message from response
        let errorMessage = t('bulkUpload.errors.downloadTemplateFailed', `Failed to download template (${response.status})`);
        try {
          const errorData = await response.json();
          if (errorData.message) {
            errorMessage = errorData.message;
          }
        } catch (e) {
          // If response is not JSON, use status text
          errorMessage = response.statusText || errorMessage;
        }
        setError(errorMessage);
        console.error('Download failed:', response.status, errorMessage);
        return;
      }

      // Get the blob from response
      const blob = await response.blob();

      // Create a blob URL and trigger download
      const blobUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;


      // Determine filename based on segmentation settings (Attribute, People, Interface template do not use segment in name)
      let fileName;
      if (uploadType === 'Object' && !isAttributeEntity && !isPeopleEntity && !isInterfaceEntity && templateType === 'INSERT' && segmentationEnabled && segmentMode) {
        // Use "Segments X {Entity} TEMPLATE {Type}.xlsx" format when segmentation is enabled
        fileName = `Segments X ${uploadTypeGroup} TEMPLATE ${templateType}.xlsx`;
      } else {
        // Use original format for non-segmented templates, Attribute, or other upload types
        fileName = `${uploadTypeGroup}_TEMPLATE_${templateType}.xlsx`;
      }

      link.download = fileName;
      document.body.appendChild(link);
      link.click();

      // Clean up
      document.body.removeChild(link);
      window.URL.revokeObjectURL(blobUrl);

      setError('');
      console.log('Template downloaded successfully');
    } catch (error) {
      console.error('Error downloading template:', error);
      setError(t('bulkUpload.errors.downloadTemplateError', `Failed to download template: ${error.message}`));
    } finally {
      setDownloading(false);
    }
  };

  // Handle file selection
  const handleFileChange = (e) => {
    const selectedFile = e.target.files[0];
    validateAndSetFile(selectedFile);
  };

  // Validate and set file
  const validateAndSetFile = (selectedFile) => {
    if (!selectedFile) {
      setFile(null);
      return;
    }

    // Check file type
    const validExtensions = ['.xlsx', '.xls'];
    const fileName = selectedFile.name.toLowerCase();
    const isValidType = validExtensions.some(ext => fileName.endsWith(ext));

    if (!isValidType) {
      setError(t('bulkUpload.errors.invalidFileType', 'Invalid file type. Please upload an Excel file (.xlsx or .xls)'));
      setFile(null);
      return;
    }

    // Check file size (10MB max)
    const maxSize = 10 * 1024 * 1024; // 10MB
    if (selectedFile.size > maxSize) {
      setError(t('bulkUpload.errors.fileSizeExceeded', 'File size exceeds 10MB limit'));
      setFile(null);
      return;
    }

    setFile(selectedFile);
    setError('');
  };

  // Handle drag events
  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  // Handle drop
  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      validateAndSetFile(e.dataTransfer.files[0]);
    }
  };

  // Handle form submission
  const handleNext = () => {
    // Validation
    if (!uploadType) {
      setError(t('bulkUpload.errors.selectUploadType', 'Please select an upload type'));
      return;
    }

    if (!uploadTypeGroup) {
      setError(t('bulkUpload.errors.selectEntity', 'Please select an entity'));
      return;
    }

    if (!isEntitySupported(uploadTypeGroup)) {
      setError(t('bulkUpload.errors.entityNotImplemented', `Bulk upload for ${uploadTypeGroup} is not yet implemented. Currently only Regulator is supported.`).replace('{entity}', uploadTypeGroup));
      return;
    }

    if (!uploadOption) {
      setError(t('bulkUpload.errors.selectUploadOption', 'Please select an upload option'));
      return;
    }

    // Validate segment selection for Object + CREATE operations + Segmentation enabled (not for Attribute, Org Unit, People, or Interface)
    if (uploadType === 'Object' && !isAttributeEntity && !isOrgUnitEntity && !isPeopleEntity && !isInterfaceEntity && isCreateOperation(uploadOption) && segmentationEnabled) {
      if (!segmentMode) {
        setError(t('bulkUpload.errors.selectSegment', 'Please select a segment option'));
        return;
      }
    }

    if (!file) {
      setError(t('bulkUpload.errors.selectFile', 'Please select a file to upload'));
      return;
    }

    // Pass data to next step
    const errorHandlingValue = cancelOnWarning ? 'Cancel on Warning' : 'Continue on Warning';
    console.log('✅ Error Handling Configuration:', {
      cancelOnWarning,
      errorHandling: errorHandlingValue
    });

    const nextData = {
      uploadType,
      uploadTypeGroup,
      uploadOption,
      file,
      cancelOnWarning,
      errorHandling: errorHandlingValue
    };

    // Add segment data if available (not for Attribute, Org Unit, People, or Interface - segment not used for these uploads)
    if (!isAttributeEntity && !isOrgUnitEntity && !isPeopleEntity && !isInterfaceEntity && segmentMode) {
      nextData.segmentMode = segmentMode;
      if (segment) {
        nextData.segment = segment;
      }
    }

    onNext(nextData);
  };

  // Transform filtered entities for grouped select
  const groupedEntities = {};
  Object.entries(filteredEntities).forEach(([groupName, entities]) => {
    groupedEntities[groupName] = entities.map(entity => ({
      value: entity,
      label: entity
    }));
  });

  return (
    <div className="max-w-3xl mx-auto">
      <div className="bg-white rounded-lg shadow-lg border border-gray-200 p-8">
        <h2 className="text-3xl font-bold text-gray-900 mb-2">{t('bulkUpload.step1.title', 'Step 1: Choose File')}</h2>
        <p className="text-gray-600 mb-8">{t('bulkUpload.step1.description', 'Select your Excel file and configure upload settings')}</p>

        {error && (
          <Alert type="error" message={error} onClose={() => setError('')} />
        )}

        {/* Upload Type Dropdown */}
        <div className="mb-6">
          <Select
            label={t('bulkUpload.step1.uploadType', 'Upload Type')}
            value={uploadType}
            onChange={setUploadType}
            options={UPLOAD_TYPES}
            required
          />
        </div>

        {/* Entity Group Dropdown (Grouped) */}
        <div className="mb-6">
          <Select
            label={t('bulkUpload.step1.entity', 'Entity')}
            value={uploadTypeGroup}
            onChange={handleEntityChange}
            options={groupedEntities}
            grouped
            required
            placeholder={loadingPermissions ? t('bulkUpload.step1.loadingPermissions', 'Loading permissions...') : t('bulkUpload.step1.selectEntity', 'Select an entity...')}
            disabled={loadingPermissions || Object.keys(groupedEntities).length === 0}
          />
          {!loadingPermissions && Object.keys(groupedEntities).length === 0 && !error && (
            <div className="mt-2 p-3 bg-yellow-50 border border-yellow-200 rounded-md">
              <p className="text-sm text-yellow-800 font-medium">
                <i className="fas fa-exclamation-triangle mr-2"></i>
                {t('bulkUpload.step1.noPermission', "You don't have permission to upload any entities.")}
              </p>
              <p className="text-xs text-yellow-700 mt-1">
                {t('bulkUpload.step1.noPermissionDetails', 'You need "Create" permission for at least one module (System, Regulation, Regulator, etc.) to use bulk upload. Please contact your administrator to grant the necessary permissions.')}
              </p>
            </div>
          )}
        </div>

        {/* Upload Option Dropdown (Dynamic based on entity) */}
        {uploadTypeGroup && (
          <div className="mb-6">
            <Select
              label={t('bulkUpload.step1.uploadOption', 'Upload Option')}
              value={uploadOption}
              onChange={setUploadOption}
              options={availableOptions}
              required
              disabled={!isEntitySupported(uploadTypeGroup)}
              placeholder={
                isEntitySupported(uploadTypeGroup)
                  ? t('bulkUpload.step1.selectOperation', 'Select an operation...')
                  : t('bulkUpload.step1.entityNotSupported', 'Entity not yet supported')
              }
            />
          </div>
        )}

        {/* Segment Dropdown (Only for Object + CREATE operations + Segmentation enabled; hidden for Attribute, Org Unit, People, and Interface) */}
        {uploadType === 'Object' && !isAttributeEntity && !isOrgUnitEntity && !isPeopleEntity && !isInterfaceEntity && isCreateOperation(uploadOption) && segmentationEnabled && (
          <div className="mb-6">
            <Select
              label={t('bulkUpload.step1.segment', 'Segment')}
              value={segmentMode === SEGMENT_MODE.MULTIPLE ? 'Multiple' :
                segmentMode === SEGMENT_MODE.ENTERPRISE ? 'Enterprise' :
                  segment ? segments.find(s => s.id === parseInt(segment))?.name || segment : ''}
              onChange={handleSegmentChange}
              options={(() => {
                const options = [
                  { value: 'Multiple', label: t('bulkUpload.step1.segmentMultiple', 'Multiple') },
                  { value: 'Enterprise', label: t('bulkUpload.step1.segmentEnterprise', 'Enterprise') }
                ];
                // Add user-accessible segments
                segments.forEach(seg => {
                  if (seg.id !== 1) { // Don't duplicate Enterprise
                    options.push({
                      value: seg.name,
                      label: seg.name
                    });
                  }
                });
                return options;
              })()}
              required
              disabled={loadingSegments}
              placeholder={loadingSegments ? t('bulkUpload.step1.loadingSegments', 'Loading segments...') : t('bulkUpload.step1.selectSegment', 'Select a segment...')}
            />
            {segmentMode === SEGMENT_MODE.MULTIPLE && (
              <p className="mt-2 text-sm text-gray-600">
                {t('bulkUpload.step1.segmentMultipleHint', 'You will need to specify the segment for each row in the Excel file.')}
              </p>
            )}
          </div>
        )}

        {/* File Upload Area */}
        <div className="mb-6">
          <label className="block text-base font-semibold text-gray-900 mb-3">
            {t('bulkUpload.step1.excelFile', 'Excel File')}
            <span className="text-red-600 ml-1">*</span>
          </label>
          <div
            className={`relative border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-all ${dragActive
                ? 'border-green-500 bg-green-50'
                : file
                  ? 'border-green-500 bg-green-50'
                  : 'border-gray-300 hover:border-gray-400 hover:bg-gray-50'
              }`}
            onDragEnter={handleDrag}
            onDragLeave={handleDrag}
            onDragOver={handleDrag}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
          >
            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx,.xls"
              onChange={handleFileChange}
              className="hidden"
            />

            {file ? (
              <div className="text-green-700">
                <svg className="mx-auto h-16 w-16 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <p className="text-lg font-semibold text-gray-900">{file.name}</p>
                <p className="text-sm text-gray-600 mt-2">
                  {(file.size / 1024).toFixed(2)} KB
                </p>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    setFile(null);
                    if (fileInputRef.current) fileInputRef.current.value = '';
                  }}
                  className="mt-3 text-sm text-red-600 hover:text-red-700 hover:underline font-medium"
                >
                  {t('bulkUpload.step1.removeFile', 'Remove file')}
                </button>
              </div>
            ) : (
              <div className="text-gray-700">
                <svg className="mx-auto h-16 w-16 mb-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                </svg>
                <p className="text-lg font-semibold text-gray-900 mb-2">{t('bulkUpload.step1.clickOrDrag', 'Click to browse or drag and drop')}</p>
                <p className="text-sm text-gray-600 mt-1">{t('bulkUpload.step1.excelFilesOnly', 'Excel files only (.xlsx, .xls)')}</p>
                <p className="text-xs text-gray-500 mt-2">{t('bulkUpload.step1.maxFileSize', 'Maximum file size: 10MB')}</p>
              </div>
            )}
          </div>
        </div>

        {/* Cancel on Warning Checkbox */}
        <Checkbox
          label={t('bulkUpload.step1.cancelOnWarning', 'Cancel on Warning (rollback all changes if any error occurs)')}
          checked={cancelOnWarning}
          onChange={setCancelOnWarning}
        />

        {/* Action Buttons */}
        <div className="flex flex-col sm:flex-row justify-between items-stretch sm:items-center gap-3 mt-6">
          {/* Download Template Button */}
          {uploadOption && uploadTypeGroup && (
            <Button
              onClick={handleDownloadTemplate}
              variant="outline"
              className="flex items-center justify-center gap-2 w-full sm:w-auto"
              disabled={!isEntitySupported(uploadTypeGroup) || downloading}
            >
              {downloading ? (
                <>
                  <svg className="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {t('bulkUpload.step1.downloading', 'Downloading...')}
                </>
              ) : (
                <>
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                  {t('bulkUpload.step1.downloadTemplate', 'Download Template')}
                </>
              )}
            </Button>
          )}
          <div className={uploadOption && uploadTypeGroup ? 'w-full sm:w-auto' : 'w-full sm:w-auto ml-auto'}>
            <Button onClick={handleNext} variant="primary" className="w-full sm:w-auto">
              {t('bulkUpload.step1.next', 'Next → Map Columns')}
            </Button>
          </div>
        </div>

        {/* Help Text */}
        <div className="mt-8 p-6 bg-blue-50 rounded-lg border border-blue-200">
          <h3 className="text-base font-semibold text-gray-900 mb-3 flex items-center">
            <svg className="w-5 h-5 inline mr-2 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            {t('bulkUpload.step1.needHelp', 'Need help?')}
          </h3>
          <ul className="text-sm text-gray-700 space-y-2 leading-relaxed">
            <li className="flex items-start">
              <span className="text-blue-600 mr-2">•</span>
              <span>{t('bulkUpload.step1.help1', 'Select an Entity and Upload Option, then click "Download Template" to get the correct Excel format')}</span>
            </li>
            <li className="flex items-start">
              <span className="text-blue-600 mr-2">•</span>
              <span>{t('bulkUpload.step1.help2', 'Ensure your Excel file has the correct column names matching the template')}</span>
            </li>
            {uploadType === 'Role' ? (
              <>
                <li className="flex items-start">
                  <span className="text-blue-600 mr-2">•</span>
                  <span><strong>{t('bulkUpload.step1.helpRoleAssignments', 'Role assignments:')}</strong> {t('bulkUpload.step1.helpRoleAssignmentsDesc', 'Specify the object by Name/Ref (and Parent Name if needed to resolve duplicates)')}</span>
                </li>
                <li className="flex items-start">
                  <span className="text-blue-600 mr-2">•</span>
                  <span><strong>{t('bulkUpload.step1.helpUserIdentification', 'User identification:')}</strong> {t('bulkUpload.step1.helpUserIdentificationDesc', 'Provide User Email, User Lan ID, or User First Name + Last Name')}</span>
                </li>
                <li className="flex items-start">
                  <span className="text-blue-600 mr-2">•</span>
                  <span><strong>{t('bulkUpload.step1.helpGovernanceRole', 'Governance Role:')}</strong> {t('bulkUpload.step1.helpGovernanceRoleDesc', 'Select from the dropdown list in the template (loaded from system)')}</span>
                </li>
              </>
            ) : (
              <>
                <li className="flex items-start">
                  <span className="text-blue-600 mr-2">•</span>
                  <span><strong>{t('bulkUpload.step1.helpInsert', 'INSERT operations:')}</strong> {t('bulkUpload.step1.helpInsertDesc', 'PrimaryName is required')}</span>
                </li>
                <li className="flex items-start">
                  <span className="text-blue-600 mr-2">•</span>
                  <span><strong>{t('bulkUpload.step1.helpUpdate', 'UPDATE operations:')}</strong> {t('bulkUpload.step1.helpUpdateDesc', 'At least one of ID or PrimaryName is required')}</span>
                </li>
                {uploadOption === 'Update Existing Items' && (
                  <li className="flex items-start">
                    <span className="text-blue-600 mr-2">•</span>
                    <span>{t('bulkUpload.step1.helpUpdateShortNameNote', 'If Short Name is provided, the value will be the new value for the specified object.')}</span>
                  </li>
                )}
                <li className="flex items-start">
                  <span className="text-blue-600 mr-2">•</span>
                  <span><strong>{t('bulkUpload.step1.helpDelete', 'DELETE operations:')}</strong> {t('bulkUpload.step1.helpDeleteDesc', 'ID is required')}</span>
                </li>
              </>
            )}
          </ul>
        </div>
      </div>
    </div>
  );
};

export default StepChooseFile;

