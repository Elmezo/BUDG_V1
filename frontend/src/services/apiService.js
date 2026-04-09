/**
 * API Service
 * Handles all HTTP requests to the backend
 */

import axios from 'axios';
import * as XLSX from 'xlsx';

// Determine API base URL based on environment
const getBaseURL = () => {
  // In production, use relative path (same origin)
  // In development, proxy will handle it (configured in vite.config.js)
  return window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
    ? `http://${window.location.hostname}:8080/api`
    : '/api';
};

const api = axios.create({
  baseURL: getBaseURL(),
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 120000, // 2 minutes for bulk operations
  withCredentials: true, // Include cookies (ACCESS_TOKEN) in requests
});

// Add request interceptor for auth or logging
api.interceptors.request.use(
  (config) => {
    // Ensure credentials are included for all requests
    config.withCredentials = true;

    // For multipart/form-data requests, let browser set Content-Type automatically
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Add response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      // Server responded with error status
      const status = error.response.status;
      const data = error.response.data;
      console.error(`API Error: ${status}`, data);

      // Log detailed error info for debugging
      if (data && typeof data === 'object') {
        if (data.message) {
          console.error('Error message:', data.message);
        }
        if (data.errors && Array.isArray(data.errors)) {
          console.error('Validation errors:', data.errors);
        }
      }
    } else if (error.request) {
      // Request made but no response
      console.error('Network Error:', error.message);
      console.error('Request details:', error.request);
    } else {
      // Something else happened
      console.error('Error:', error.message);
    }
    return Promise.reject(error);
  }
);

/**
 * Normalize entity display name to bulk object API path segment.
 * Must match upload/status/report URLs: spaces and dots removed; "Legal Entity" -> "legal".
 * @param {string} entity - Entity label (e.g. 'Org. Unit', 'Regulatory Theme')
 * @returns {string} URL segment (e.g. 'orgunit', 'regulatorytheme')
 */
function normalizeBulkObjectEntityPath(entity) {
  if (!entity) return '';
  const entityLower = String(entity).toLowerCase().trim();
  if (entityLower === 'legal entity' || entityLower === 'legalentity') {
    return 'legal';
  }
  let path = entityLower.replace(/\s+/g, '').replace(/\./g, '');
  // Align with backend UniversalBulkFieldsMetadataServlet ENTITY_NAME_MAP keys
  if (path === 'datasets') {
    return 'dataset';
  }
  return path;
}

/**
 * Get field metadata for an entity
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @returns {Promise} Field definitions array
 */
export async function getFieldMetadata(entity, operation = null) {
  try {
    // Match template/upload paths: "Org. Unit" -> orgunit, "Regulatory Theme" -> regulatorytheme
    const entityPath = normalizeBulkObjectEntityPath(entity);
    let url = `/bulk/${entityPath}/fields`;
    if (operation) {
      url += `?operation=${operation}`;
    }
    const response = await api.get(url);
    return response.data;
  } catch (error) {
    // Silently fail for 404 errors (endpoint not available) - field metadata is optional
    // Only log other errors
    if (error.response?.status !== 404) {
      console.error('Failed to fetch field metadata:', error);
    }
    throw error;
  }
}

/**
 * Upload bulk file
 * @param {Object} params - Upload parameters
 * @param {File} params.file - Excel file
 * @param {string} params.uploadType - Upload type (Object, Relationship, Role)
 * @param {string} params.entity - Entity name (e.g., 'Regulator')
 * @param {string} params.uploadOption - Upload option (Add/Update/Remove)
 * @param {string} params.errorHandling - Error handling mode
 * @param {number} params.userId - User ID
 * @param {Object} params.columnMappings - Column mappings {fieldName: excelColumnName}
 * @param {string} params.segmentMode - Segment mode (MULTIPLE | ENTERPRISE | SPECIFIC)
 * @param {string} params.segment - Segment ID (when SPECIFIC mode)
 * @returns {Promise} Upload response with job_id
 */
export async function uploadBulkFile(params) {
  const formData = new FormData();
  formData.append('file', params.file);
  formData.append('uploadType', params.uploadType);
  formData.append('entity', params.entity);
  formData.append('uploadOption', params.uploadOption);
  formData.append('errorHandling', params.errorHandling);
  formData.append('userId', params.userId);

  console.log('📤 Upload Parameters:', {
    uploadType: params.uploadType,
    entity: params.entity,
    uploadOption: params.uploadOption,
    errorHandling: params.errorHandling,
    userId: params.userId,
    segmentMode: params.segmentMode,
    segment: params.segment
  });

  // Add segment parameters if provided
  if (params.segmentMode) {
    formData.append('segmentMode', params.segmentMode);
    if (params.segment) {
      formData.append('segment', params.segment);
    }
  }

  // Add cancelOnWarning parameter for relationship uploads
  if (params.cancelOnWarning !== undefined) {
    formData.append('cancelOnWarning', params.cancelOnWarning ? 'true' : 'false');
  }

  // Add column mappings if provided
  if (params.columnMappings && Object.keys(params.columnMappings).length > 0) {
    formData.append('columnMappings', JSON.stringify(params.columnMappings));
  }

  try {
    // Determine upload URL based on upload type
    let uploadUrl;

    if (params.uploadType === 'Role') {
      // For roles, use /api/bulk/role/upload
      uploadUrl = `/bulk/role/upload`;
    } else if (params.uploadType === 'Relationship') {
      // For relationships, use /api/bulk/relationships/upload (plural)
      uploadUrl = `/bulk/relationships/upload`;
    } else {
      // For objects, use /api/bulk/{entity}/upload (same path as metadata / status / report)
      const entityPath = normalizeBulkObjectEntityPath(params.entity);
      uploadUrl = `/bulk/${entityPath}/upload`;
    }

    console.log('📤 Uploading to:', uploadUrl);
    const response = await api.post(uploadUrl, formData, { timeout: 300000 });
    console.log('✅ Upload successful - Full response:', response);
    console.log('✅ Upload successful - response.data:', response.data);
    console.log('✅ Response data type:', typeof response.data);
    console.log('✅ Response data job_id:', response.data?.job_id);
    console.log('✅ Response data jobId:', response.data?.jobId);
    console.log('✅ Response data keys:', Object.keys(response.data || {}));
    console.log('✅ Response data JSON:', JSON.stringify(response.data, null, 2));

    // Ensure job_id is accessible
    const data = response.data;
    if (data && !data.job_id && !data.jobId) {
      console.warn('⚠️ WARNING: No job_id or jobId found in response!');
      console.warn('⚠️ Response data:', data);
    }

    return response.data;
  } catch (error) {
    console.error('❌ Upload error caught:', error);
    console.error('Error response:', error.response);
    console.error('Error response data:', error.response?.data);

    // If server returned validation errors (400 status), return the error data
    // so the component can handle it properly
    if (error.response && error.response.status === 400 && error.response.data) {
      console.log('✅ Returning validation error data to component:', error.response.data);
      // Return the error data as if it were a successful response
      // This allows the component to handle validation errors gracefully
      return error.response.data;
    }
    console.error('❌ Throwing error (not a validation error)');
    throw error;
  }
}

/**
 * Helper function to determine the correct API path based on upload type and entity
 * @param {string} uploadType - Upload type ('Object', 'Role', 'Relationship')
 * @param {string} entity - Entity name
 * @param {string} endpoint - Endpoint type ('upload', 'status', 'report')
 * @returns {string} API path
 */
function getBulkApiPath(uploadType, entity, endpoint) {
  if (uploadType === 'Role') {
    return `/bulk/role/${endpoint}`;
  } else if (uploadType === 'Relationship') {
    return `/bulk/relationships/${endpoint}`;
  } else {
    // For objects, use entity-specific path (same normalization as getFieldMetadata)
    const entityPath = normalizeBulkObjectEntityPath(entity);
    return `/bulk/${entityPath}/${endpoint}`;
  }
}

/**
 * Get job status
 * @param {string} entity - Entity name (e.g., 'Regulator', 'Legal Entity Role')
 * @param {number} jobId - Job ID
 * @param {string} uploadType - Upload type ('Object', 'Role', 'Relationship')
 * @returns {Promise} Job status object
 */
export async function getJobStatus(entity, jobId, uploadType = 'Object') {
  try {
    const apiPath = getBulkApiPath(uploadType, entity, 'status');
    const response = await api.get(`${apiPath}/${jobId}`);
    return response.data;
  } catch (error) {
    console.error('Failed to fetch job status:', error);
    throw error;
  }
}

/**
 * Get user permissions for bulk upload
 * Uses current authenticated user from /api/me (via AuthFilter)
 * @returns {Promise} Permissions object with allowed entities and operations
 */
export async function getUserBulkUploadPermissions() {
  try {
    // No userId parameter needed - servlet gets it from request attributes (AuthFilter)
    const response = await api.get('/bulk/permissions');
    return response.data;
  } catch (error) {
    console.error('Failed to fetch user permissions:', error);
    // If it's a 403 error, throw it so the component can handle it properly
    if (error.response && error.response.status === 403) {
      throw error;
    }
    // For other errors, return empty permissions (fail-safe: user won't see any entities)
    return { allowedEntities: {} };
  }
}

/**
 * Get accessible segments for current user
 * Uses current authenticated user from /api/me (via AuthFilter)
 * @returns {Promise} Segments object with list of accessible segments
 */
export async function getAccessibleSegments() {
  try {
    const response = await api.get('/segments/accessible');
    return response.data;
  } catch (error) {
    console.error('Failed to fetch accessible segments:', error);
    throw error;
  }
}

/**
 * Get app config value
 * @param {string} configKey - Config key (e.g., 'INFORMATION_SEGMENTATION_ENABLED')
 * @returns {Promise<string|null>} Config value or null if not found
 */
export async function getAppConfig(configKey) {
  try {
    // AppConfigServlet is at /admin/api/app-config/*, but api baseURL is /api
    // So we need to use absolute path
    const baseURL = getBaseURL();
    // Replace /api with /admin/api/app-config
    const adminApiPath = baseURL.replace('/api', '') + '/admin/api/app-config';
    const response = await axios.get(`${adminApiPath}/${configKey}`, {
      withCredentials: true,
      headers: {
        'Content-Type': 'application/json',
      }
    });
    return response.data?.definition || null;
  } catch (error) {
    console.error(`Failed to fetch app config for ${configKey}:`, error);
    return null;
  }
}

/**
 * Get default segment settings from system settings
 * @returns {Promise<Object>} Object with enterprise_segment_default and assigned_segments_default
 */
export async function getDefaultSegmentSettings() {
  try {
    const response = await api.get('/system-settings/DefaultSegment');
    return {
      enterpriseSegmentDefault: response.data.enterprise_segment_default === true || response.data.enterprise_segment_default === 'true',
      assignedSegmentsDefault: response.data.assigned_segments_default === true || response.data.assigned_segments_default === 'true'
    };
  } catch (error) {
    console.error('Failed to fetch default segment settings:', error);
    return {
      enterpriseSegmentDefault: false,
      assignedSegmentsDefault: false
    };
  }
}

/**
 * Check if Information Segmentation is enabled
 * Uses dedicated endpoint /api/segmentation/enabled which checks both app_config and system_settings
 * @returns {Promise<boolean>} True if enabled, false otherwise
 */
export async function isInformationSegmentationEnabled() {
  try {
    const response = await api.get('/segmentation/enabled');
    if (response.data && typeof response.data.enabled === 'boolean') {
      return response.data.enabled;
    }
    return false;
  } catch (error) {
    console.error('Failed to check Information Segmentation enabled:', error);
    return false; // Default to disabled
  }
}

/**
 * Get job report
 * @param {string} entity - Entity name (e.g., 'Regulator', 'Legal Entity Role')
 * @param {number} jobId - Job ID
 * @param {string} uploadType - Upload type ('Object', 'Role', 'Relationship')
 * @returns {Promise} Job report object
 */
export async function getJobReport(entity, jobId, uploadType = 'Object') {
  try {
    // Validate jobId - check for null, undefined, empty string, or invalid number
    if (jobId === null || jobId === undefined || jobId === '' || jobId === 'null' || jobId === 'undefined') {
      throw new Error(`Invalid job ID: ${jobId}. Job ID must be a valid number.`);
    }
    
    // Ensure jobId is a valid number
    const numericJobId = Number(jobId);
    if (isNaN(numericJobId) || numericJobId <= 0) {
      throw new Error(`Invalid job ID: ${jobId}. Job ID must be a valid positive number.`);
    }

    const apiPath = getBulkApiPath(uploadType, entity, 'report');
    const url = `${apiPath}/${numericJobId}`;

    console.log('📥 Fetching report:');
    console.log('  - Entity:', entity);
    console.log('  - Upload Type:', uploadType);
    console.log('  - Job ID (original):', jobId, `(type: ${typeof jobId})`);
    console.log('  - Job ID (numeric):', numericJobId, `(type: ${typeof numericJobId})`);
    console.log('  - URL:', url);
    console.log('  - Full URL will be:', api.defaults.baseURL + url);

    const response = await api.get(url);
    console.log('✅ Report fetched successfully:', response.data);
    return response.data;
  } catch (error) {
    console.error('❌ Failed to fetch job report:');
    console.error('  - Entity:', entity);
    console.error('  - Job ID:', jobId);
    console.error('  - Error URL:', error.config?.url);
    console.error('  - Base URL:', error.config?.baseURL);
    console.error('  - Full URL:', error.config?.baseURL ? error.config.baseURL + error.config.url : error.config?.url);
    console.error('  - Error status:', error.response?.status);
    console.error('  - Error data:', error.response?.data);
    console.error('  - Error message:', error.message);

    // Provide more detailed error message
    if (error.response) {
      let errorMsg = 'Unknown error';
      if (error.response.data) {
        if (typeof error.response.data === 'string') {
          errorMsg = error.response.data;
        } else {
          errorMsg = error.response.data.message || 
                    error.response.data.error || 
                    error.response.data.statusText || 
                    errorMsg;
        }
      } else {
        errorMsg = error.response.statusText || errorMsg;
      }
      const status = error.response.status;

      if (status === 404) {
        throw new Error(`Report not found. The job may not exist or the report is not available. (Status: ${status})`);
      }

      throw new Error(`Failed to fetch report: ${errorMsg} (Status: ${status})`);
    }
    throw error;
  }
}

/**
 * Convert report data to Excel format
 * @param {Object} report - Report data from API
 * @returns {Array} Array of rows for Excel
 */
function convertReportToExcelRows(report) {
  const rows = [];
  
  // Get report items - handle different response structures
  const reportItems = report.report_items || report.items || [];
  
  // Add header row
  rows.push(['ID', 'Position', 'Field', 'Status', 'Type', 'Error Code', 'Message']);
  
  // Process each report item
  reportItems.forEach((item) => {
    const itemId = item.id;
    const position = item.position || item.row_number || '';
    const fieldName = item.field_name || item.entity_name || '';
    const status = item.status || '';
    
    // Get messages - handle different message structures
    const messages = item.messages || [];
    
    if (messages.length === 0) {
      // If no messages, add a single row with the item info
      rows.push([
        itemId,
        position,
        fieldName,
        status,
        '',
        '',
        ''
      ]);
    } else {
      // Add a row for each message
      messages.forEach((message) => {
        const messageType = message.type || message.severity || '';
        const errorCode = message.error_code || message.message_code || '';
        const messageText = message.message || message.message_text || '';
        
        rows.push([
          itemId,
          position,
          fieldName,
          status,
          messageType,
          errorCode,
          messageText
        ]);
      });
    }
  });
  
  return rows;
}

/**
 * Download job report as Excel
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @param {number} jobId - Job ID
 * @param {string} uploadType - Upload type ('Object', 'Role', 'Relationship')
 * @returns {Promise} Report data
 */
export async function downloadReportAsExcel(entity, jobId, uploadType = 'Object') {
  try {
    const report = await getJobReport(entity, jobId, uploadType);
    
    // Convert report to Excel rows
    const excelRows = convertReportToExcelRows(report);
    
    // Create workbook and worksheet
    const workbook = XLSX.utils.book_new();
    const worksheet = XLSX.utils.aoa_to_sheet(excelRows);
    
    // Set column widths
    worksheet['!cols'] = [
      { wch: 10 }, // ID
      { wch: 10 }, // Position
      { wch: 25 }, // Field
      { wch: 15 }, // Status
      { wch: 15 }, // Type
      { wch: 15 }, // Error Code
      { wch: 50 }  // Message
    ];
    
    // Add worksheet to workbook
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Report');
    
    // Generate Excel file
    const excelBuffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
    
    // Create blob and download
    const blob = new Blob([excelBuffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `bulk_upload_report_${jobId}.xlsx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    
    return report;
  } catch (error) {
    console.error('Failed to download report as Excel:', error);
    throw error;
  }
}

/**
 * Download job report as JSON
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @param {number} jobId - Job ID
 * @param {string} uploadType - Upload type ('Object', 'Role', 'Relationship')
 * @returns {Promise} Report data
 */
export async function downloadReportAsJSON(entity, jobId, uploadType = 'Object') {
  try {
    const report = await getJobReport(entity, jobId, uploadType);

    // Create blob and download
    const blob = new Blob([JSON.stringify(report, null, 2)], {
      type: 'application/json'
    });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `bulk_upload_report_${jobId}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);

    return report;
  } catch (error) {
    console.error('Failed to download report:', error);
    throw error;
  }
}

/**
 * Safe translation helper that falls back when the key is missing
 * or when the i18n library returns the key itself.
 * @param {string} key
 * @param {string} defaultText
 * @returns {string}
 */
function tOrDefault(key, defaultText) {
  const i18n = window.I18n;
  if (!i18n || typeof i18n.t !== 'function') return defaultText;
  try {
    const translated = i18n.t(key);
    if (!translated || translated === key) {
      return defaultText;
    }
    return translated;
  } catch (e) {
    console.error('Error translating key:', key, e);
    return defaultText;
  }
}

/**
 * Show format selection dialog
 * @returns {Promise<string>} 'excel' or 'json'
 */
function showFormatSelectionDialog() {
  return new Promise((resolve) => {
    // Create modal overlay
    const overlay = document.createElement('div');
    overlay.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10000;
    `;
    
    // Create modal dialog
    const modal = document.createElement('div');
    modal.style.cssText = `
      background: white;
      padding: 2rem;
      border-radius: 8px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
      max-width: 400px;
      width: 90%;
    `;
    
    // Create title using translation
    const title = document.createElement('h3');
    title.textContent = tOrDefault('bulkUpload.dialog.selectReportFormat', 'Select Report Format');
    title.style.cssText = 'margin: 0 0 1.5rem 0; font-size: 1.25rem; font-weight: bold; color: #333;';
    
    // Create buttons container
    const buttonsContainer = document.createElement('div');
    buttonsContainer.style.cssText = 'display: flex; gap: 1rem; justify-content: flex-end;';
    
    // Create Excel button using translation
    const excelBtn = document.createElement('button');
    excelBtn.textContent = tOrDefault('bulkUpload.dialog.excelFormat', 'Excel (.xlsx)');
    excelBtn.style.cssText = `
      padding: 0.75rem 1.5rem;
      background: #2563eb;
      color: white;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-size: 1rem;
      font-weight: 500;
    `;
    excelBtn.onmouseover = () => excelBtn.style.background = '#1d4ed8';
    excelBtn.onmouseout = () => excelBtn.style.background = '#2563eb';
    excelBtn.onclick = () => {
      document.body.removeChild(overlay);
      resolve('excel');
    };
    
    // Create JSON button using translation
    const jsonBtn = document.createElement('button');
    jsonBtn.textContent = tOrDefault('bulkUpload.dialog.jsonFormat', 'JSON (.json)');
    jsonBtn.style.cssText = `
      padding: 0.75rem 1.5rem;
      background: #059669;
      color: white;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-size: 1rem;
      font-weight: 500;
    `;
    jsonBtn.onmouseover = () => jsonBtn.style.background = '#047857';
    jsonBtn.onmouseout = () => jsonBtn.style.background = '#059669';
    jsonBtn.onclick = () => {
      document.body.removeChild(overlay);
      resolve('json');
    };
    
    // Close on overlay click
    overlay.onclick = (e) => {
      if (e.target === overlay) {
        document.body.removeChild(overlay);
        resolve(null);
      }
    };
    
    // Assemble modal
    buttonsContainer.appendChild(excelBtn);
    buttonsContainer.appendChild(jsonBtn);
    modal.appendChild(title);
    modal.appendChild(buttonsContainer);
    overlay.appendChild(modal);
    
    // Add to page
    document.body.appendChild(overlay);
  });
}

/**
 * Download job report with format selection
 * Shows a dialog to let user choose between JSON and Excel formats
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @param {number} jobId - Job ID
 * @param {string} uploadType - Upload type ('Object', 'Role', 'Relationship')
 * @returns {Promise} Report data
 */
export async function downloadReport(entity, jobId, uploadType = 'Object') {
  try {
    // Show dialog to choose format
    const formatChoice = await showFormatSelectionDialog();
    
    if (formatChoice === 'excel') {
      // User chose Excel
      return await downloadReportAsExcel(entity, jobId, uploadType);
    } else if (formatChoice === 'json') {
      // User chose JSON
      return await downloadReportAsJSON(entity, jobId, uploadType);
    }
    // User cancelled - do nothing
    return null;
  } catch (error) {
    console.error('Failed to download report:', error);
    throw error;
  }
}

/**
 * Download original uploaded file
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @param {number} jobId - Job ID
 */
export async function downloadOriginalFile(entity, jobId) {
  try {
    // Validate jobId - check for null, undefined, empty string, or invalid number
    if (jobId === null || jobId === undefined || jobId === '' || jobId === 'null' || jobId === 'undefined') {
      throw new Error(`Invalid job ID: ${jobId}. Job ID must be a valid number.`);
    }
    
    // Ensure jobId is a valid number
    const numericJobId = Number(jobId);
    if (isNaN(numericJobId) || numericJobId <= 0) {
      throw new Error(`Invalid job ID: ${jobId}. Job ID must be a valid positive number.`);
    }
    
    // Normalize entity name to lowercase for URL
    // Handle different entity name formats: "Regulator", "regulator", "Bulk Upload", null, undefined
    let entityLower = 'regulator'; // default fallback

    if (entity) {
      // Remove spaces and convert to lowercase
      entityLower = entity.toLowerCase().trim().replace(/\s+/g, '');

      // Map common entity names to their URL format
      const entityMap = {
        'bulkupload': 'regulator',
        'bulk': 'regulator',
        'regulator': 'regulator',
        'committee': 'committee',
        'process': 'process',
        'policy': 'policy',
        'geography': 'geography',
        'regulatorytheme': 'regulatorytheme',
        'regulation': 'regulation'
      };

      // Check if we have a mapping, otherwise use the cleaned entity name
      if (entityMap[entityLower]) {
        entityLower = entityMap[entityLower];
      } else if (!entityLower) {
        // If empty after cleaning, use default
        entityLower = 'regulator';
      }

      // If entity doesn't match known patterns, use the cleaned name (don't default to regulator)
      // This allows new entities to work without updating this list
      if (!['regulator', 'committee', 'policy', 'geography', 'regulatorytheme', 'regulation'].includes(entityLower)) {
        console.log(`ℹ️ Using entity path "${entityLower}" for entity "${entity}"`);
      }
    }

    const url = `/bulk/${entityLower}/download/${numericJobId}`;
    console.log('📥 Downloading original file:');
    console.log('  - Entity:', entity);
    console.log('  - Entity (normalized):', entityLower);
    console.log('  - Job ID:', jobId);
    console.log('  - URL:', url);

    const response = await api.get(url, {
      responseType: 'blob',
    });

    // Create blob and download
    const blobUrl = window.URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = `bulk_upload_${numericJobId}.xlsx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(blobUrl);

    console.log('✅ File downloaded successfully');
  } catch (error) {
    console.error('❌ Failed to download original file:');
    console.error('  - Entity:', entity);
    console.error('  - Job ID:', jobId);
    console.error('  - Error URL:', error.config?.url);
    console.error('  - Error status:', error.response?.status);
    console.error('  - Error data:', error.response?.data);

    if (error.response) {
      const errorMsg = error.response.data?.message || error.response.statusText || 'Unknown error';
      const status = error.response.status;

      if (status === 404) {
        throw new Error(`File not found. The job may not exist or the file is not available. (Status: ${status})`);
      }

      throw new Error(`Failed to download file: ${errorMsg} (Status: ${status})`);
    }
    throw error;
  }
}

/**
 * Connect to WebSocket for real-time job updates
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @param {number} jobId - Job ID
 * @param {Function} onMessage - Callback for messages
 * @param {Function} onError - Callback for errors
 * @returns {Function} Disconnect function
 */
export function connectJobWebSocket(entity, jobId, onMessage, onError) {
  // Import dynamically to avoid circular dependencies
  import('../utils/websocket.js').then(({ connectToBulkUploadJob }) => {
    return connectToBulkUploadJob(jobId, onMessage, onError);
  }).catch(err => {
    console.error('Failed to load WebSocket module:', err);
    if (onError) onError(err);
  });
}

/**
 * Get bulk jobs history (all jobs, no limit)
 * @param {number} userId - User ID (optional)
 * @returns {Promise} Jobs history
 */
export async function getBulkJobsHistory(userId = null) {
  try {
    const params = {};
    if (userId) {
      params.userId = userId;
    }

    const response = await api.get('/bulk/jobs/history', { params });
    return response.data;
  } catch (error) {
    console.error('Failed to fetch jobs history:', error);
    throw error;
  }
}

export default api;

