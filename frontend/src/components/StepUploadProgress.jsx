import React, { useState, useEffect, useRef } from 'react';
import Button from './ui/Button';
import Progress from './ui/Progress';
import Alert from './ui/Alert';
import { uploadBulkFile, getJobStatus, downloadReport, downloadOriginalFile, getJobReport } from '../services/apiService';
import { connectToBulkUploadJob, isWebSocketSupported } from '../utils/websocket';
import { getCurrentUserId, ensureCurrentUserId } from '../utils/auth';
import { useTranslation } from '../hooks/useTranslation';

const StepUploadProgress = ({ stepData, onStartOver }) => {
  const { t } = useTranslation();
  const [uploadStatus, setUploadStatus] = useState('uploading'); // uploading, connecting, polling, completed, failed
  const [jobId, setJobId] = useState(null);
  const [referenceName, setReferenceName] = useState('');
  const [progress, setProgress] = useState(0);
  const [statusMessage, setStatusMessage] = useState(t('bulkUpload.step3.uploadingFile', 'Uploading file...'));
  const [stats, setStats] = useState({
    inserted: 0,
    updated: 0,
    deleted: 0,
    failed: 0
  });
  const [error, setError] = useState('');
  const [validationErrors, setValidationErrors] = useState([]);
  const [reportAvailable, setReportAvailable] = useState(false);
  const [useWebSocket, setUseWebSocket] = useState(false);
  const [connectionMethod, setConnectionMethod] = useState('');
  const pollingIntervalRef = useRef(null);
  const wsDisconnectRef = useRef(null);

  // Derive delete mode so deleted count shows in Deleted square, not Inserted.
  // Check step (user intent) first so we treat as delete when user chose Delete even if API omits upload_option.
  const isDeleteOperation = (responseOrData, step) => {
    if (step && (step.uploadOption === 'Remove Existing Items' || step.uploadOption === 'Delete Items' || step.uploadOption === 'DELETE')) return true;
    const u = responseOrData?.upload_option ?? responseOrData?.uploadOption;
    if (u === 'DELETE') return true;
    return false;
  };

  useEffect(() => {
    // Start upload immediately
    handleUpload();

    // Cleanup on unmount
    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
      if (wsDisconnectRef.current) {
        wsDisconnectRef.current();
      }
    };
  }, []);

  // Debug: Monitor validationErrors changes
  useEffect(() => {
    if (validationErrors.length > 0) {
      console.log('✅ validationErrors updated:', validationErrors);
    }
  }, [validationErrors]);

  // Handle file upload
  const handleUpload = async () => {
    try {
      setUploadStatus('uploading');
      setProgress(10);
      setStatusMessage(t('bulkUpload.step3.uploadingToServer', 'Uploading file to server...'));

      // Get current user ID from session or API
      const userId = await ensureCurrentUserId();
      
      if (!userId) {
        setUploadStatus('failed');
        setError(t('bulkUpload.step3.userSessionNotFound', 'User session not found. Please log in again.'));
        setProgress(100);
        return;
      }

      console.log('📤 Uploading file with user ID:', userId);

      // Map frontend operation names to backend codes ONLY for Relationship uploads
      let backendOperation = stepData.uploadOption;
      if (stepData.uploadType === 'Relationship') {
        if (stepData.uploadOption === 'Upload New Items' || stepData.uploadOption === 'Add New Items') {
          backendOperation = 'INSERT';
        } else if (stepData.uploadOption === 'Remove Existing Items' || stepData.uploadOption === 'Delete Items') {
          backendOperation = 'DELETE';
        } else if (stepData.uploadOption === 'Update Existing Items') {
          backendOperation = 'UPDATE';
        }
        console.log('📤 Mapped relationship operation:', stepData.uploadOption, '→', backendOperation);
      }

      console.log('📤 Uploading with Error Handling:', {
        errorHandling: stepData.errorHandling,
        cancelOnWarning: stepData.cancelOnWarning
      });
      
      const response = await uploadBulkFile({
        file: stepData.file,
        uploadType: stepData.uploadType,
        entity: stepData.uploadTypeGroup,
        uploadOption: backendOperation,
        errorHandling: stepData.errorHandling,
        cancelOnWarning: stepData.cancelOnWarning,
        userId: userId,
        columnMappings: stepData.columnMappings || null,
        segmentMode: stepData.uploadTypeGroup === 'Attribute' ? null : (stepData.segmentMode || null),
        segment: stepData.uploadTypeGroup === 'Attribute' ? null : (stepData.segment || null)
      });

      console.log('📥 Upload response:', response);
      console.log('📥 Response status:', response.status);
      console.log('📥 Response errors:', response.errors);
      console.log('📥 Response job_id:', response.job_id);
      console.log('📥 Response jobId:', response.jobId);
      console.log('📥 Response keys:', Object.keys(response));

      if (response.status === 'failed' || response.status === 'error' || response.status === 'invalid') {
        // Validation failed
        console.log('❌ Validation failed - handling error display');
        console.log('❌ Response object:', JSON.stringify(response, null, 2));
        
        setUploadStatus('failed');
        setError(response.message || 'Upload failed');
        setProgress(100);
        
        // Update statistics from error response. Use response.failed from API as source of truth;
        // never show 0 failed when there are errors or warnings (safety: max with errors length).
        const errLen = (response.errors && Array.isArray(response.errors)) ? response.errors.length : 0;
        const warnLen = (response.warnings && Array.isArray(response.warnings)) ? response.warnings.length : 0;
        const failedCount = Math.max(response.failed ?? 0, errLen, warnLen);
        const isDelete = isDeleteOperation(response, stepData);
        setStats({
          inserted: isDelete ? 0 : (response.inserted ?? 0),
          updated: response.updated ?? 0,
          deleted: isDelete ? (response.deleted ?? response.inserted ?? 0) : (response.deleted ?? 0),
          failed: failedCount
        });
        setReportAvailable(failedCount > 0);
        
        if (response.errors && Array.isArray(response.errors) && response.errors.length > 0) {
          console.log('✅ Setting validation errors:', response.errors);
          setValidationErrors(response.errors);
        } else {
          console.warn('⚠️ No errors array in response:', response);
          console.warn('⚠️ Response keys:', Object.keys(response));
          setValidationErrors([]);
        }
        
        // Support both job_id (backend) and jobId (frontend) for compatibility
        const errorJobId = response.job_id || response.jobId;
        if (errorJobId) {
          setJobId(errorJobId);
          setReferenceName(response.reference_name || response.referenceName || '');
        }
        
        console.log('✅ Error state set - uploadStatus: failed, error:', response.message);
        return;
      }

      // Upload successful, get job ID
      // Support both job_id (backend) and jobId (frontend) for compatibility
      const uploadedJobId = response.job_id || response.jobId;
      
      if (!uploadedJobId) {
        console.error('❌ No job ID in response:', response);
        setUploadStatus('failed');
        const errorMsg = response.message && String(response.message).trim()
          ? response.message
          : t('bulkUpload.step3.noJobId', 'Server did not return a job ID. Please try again.');
        setError(errorMsg);
        setProgress(100);
        return;
      }
      
      setJobId(uploadedJobId);
      setReferenceName(response.reference_name || response.referenceName || '');
      setProgress(20);

      // If response already contains final stats (synchronous processing)
      if (response.status === 'success' && response.inserted !== undefined) {
        // Check if response contains validation errors (when processing continued despite errors)
        if (response.errors && Array.isArray(response.errors) && response.errors.length > 0) {
          console.log('📋 Success response contains validation errors:', response.errors);
          setValidationErrors(response.errors);
          setReportAvailable(true);
        }
        handleCompleted(response);
        return;
      }

      // Try WebSocket first if supported
      if (isWebSocketSupported()) {
        setConnectionMethod('WebSocket');
        setUploadStatus('connecting');
        setStatusMessage(t('bulkUpload.step3.connecting', 'Connecting to real-time updates...'));
        tryWebSocketConnection(uploadedJobId);
      } else {
        // Fall back to polling immediately
        setConnectionMethod(t('bulkUpload.step3.polling', 'Polling'));
        startPolling(uploadedJobId);
      }

    } catch (err) {
      console.error('❌ Upload error:', err);
      console.error('Error response:', err.response);
      setUploadStatus('failed');
      
      // Extract error message from response
      let errorMessage = t('bulkUpload.step3.uploadFailed', 'Failed to upload file');
      
      if (err.response) {
        // Server responded with error status
        const errorData = err.response.data;
        console.log('📋 Error data from server:', errorData);
        
        if (errorData) {
          // Check if it's a validation error response (status: "failed", "error", or "invalid")
          if (errorData.status === 'failed' || errorData.status === 'error' || errorData.status === 'invalid') {
            errorMessage = errorData.message || errorMessage;
            console.log('✅ Validation error detected:', errorMessage);
            
            // Update statistics from error response; never show 0 failed when there are errors
            const errLenData = (errorData.errors && Array.isArray(errorData.errors)) ? errorData.errors.length : 0;
            const failedCountErr = Math.max(errorData.failed ?? 0, errLenData);
            const isDeleteErr = isDeleteOperation(errorData, stepData);
            setStats({
              inserted: isDeleteErr ? 0 : (errorData.inserted ?? 0),
              updated: errorData.updated ?? 0,
              deleted: isDeleteErr ? (errorData.deleted ?? errorData.inserted ?? 0) : (errorData.deleted ?? 0),
              failed: failedCountErr
            });
            
            // Store validation errors for report
            if (errorData.errors && Array.isArray(errorData.errors) && errorData.errors.length > 0) {
              console.log('📋 Validation errors array:', errorData.errors);
              setValidationErrors(errorData.errors);
              setReportAvailable(true);
            } else {
              console.warn('⚠️ No errors array found in errorData:', errorData);
              setValidationErrors([]);
            }
            
            // Store job info if available
            if (errorData.jobId || errorData.job_id) {
              setJobId(errorData.jobId || errorData.job_id);
              setReferenceName(errorData.referenceName || errorData.reference_name || '');
            }
          } else if (errorData.message) {
            errorMessage = errorData.message;
          } else if (typeof errorData === 'string') {
            errorMessage = errorData;
          }

          // Fallback for generic 500 errors that don't include structured status/failed counts.
          // Keep failed count/report availability meaningful instead of showing 0.
          const parsedJobId = errorData?.jobId || errorData?.job_id;
          if (parsedJobId) {
            setJobId(parsedJobId);
            setReferenceName(errorData.referenceName || errorData.reference_name || '');
            setReportAvailable(true);
          }

          const normalizedMessage = String(errorMessage || errorData?.message || '');
          const rowMatch = normalizedMessage.match(/row\s+(\d+)/i);
          const inferredFailed = Math.max(
            errorData?.failed ?? 0,
            (errorData?.errors && Array.isArray(errorData.errors)) ? errorData.errors.length : 0,
            rowMatch ? 1 : 0
          );
          if (inferredFailed > 0) {
            setStats(prev => ({
              inserted: prev?.inserted ?? 0,
              updated: prev?.updated ?? 0,
              deleted: prev?.deleted ?? 0,
              failed: inferredFailed
            }));
            setReportAvailable(true);
          }
        }
      } else if (err.message) {
        errorMessage = err.message;
      }
      
      console.log('🔴 Final error message:', errorMessage);
      console.log('🔴 Validation errors to set:', validationErrors);
      setError(errorMessage);
      setProgress(100);
      
      // Force re-render to show errors
      console.log('🔴 Setting upload status to failed, error:', errorMessage);
    }
  };

  // Try WebSocket connection with fallback to polling
  const tryWebSocketConnection = (jobIdToTrack) => {
    try {
      const disconnect = connectToBulkUploadJob(
        jobIdToTrack,
        handleWebSocketMessage,
        handleWebSocketError
      );

      wsDisconnectRef.current = disconnect;
      setUseWebSocket(true);
      setUploadStatus('polling'); // Change status to indicate tracking
      setStatusMessage(t('bulkUpload.step3.connectedWebSocket', 'Connected via WebSocket'));
      
    } catch (error) {
      console.error('WebSocket connection failed, falling back to polling:', error);
      fallbackToPolling(jobIdToTrack);
    }
  };

  // Handle WebSocket messages
  const handleWebSocketMessage = (data) => {
    console.log('WebSocket update:', data);

    if (data.progress !== undefined) {
      setProgress(data.progress);
    }

    if (data.message) {
      setStatusMessage(data.message);
    }

    if (data.status) {
      // Update stats if available
      if (data.inserted !== undefined) {
        const isDeleteWs = isDeleteOperation(data, stepData);
        setStats({
          inserted: isDeleteWs ? 0 : (data.inserted || 0),
          updated: data.updated || 0,
          deleted: isDeleteWs ? (data.deleted ?? data.inserted ?? 0) : (data.deleted || 0),
          failed: data.failed || 0
        });
      }

      // Check for completion
      if (data.status === 'Completed' || data.status === 'Partially Completed') {
        if (wsDisconnectRef.current) {
          wsDisconnectRef.current();
        }
        handleCompleted(data);
      } else if (data.status === 'Failed') {
        if (wsDisconnectRef.current) {
          wsDisconnectRef.current();
        }
        setUploadStatus('failed');
        setError(data.message || t('bulkUpload.step3.processingFailed', 'Processing failed'));
        setProgress(100);
        setReportAvailable(data.failed > 0);
        // Ensure stats reflect failed count; never show 0 failed when errors, warnings, or error report items exist
        const errLen = (data.errors && Array.isArray(data.errors)) ? data.errors.length : 0;
        const warnLen = (data.warnings && Array.isArray(data.warnings)) ? data.warnings.length : 0;
        const reportItemsData = data.report_items || data.items;
        let failedVal = Math.max(data.failed ?? 0, errLen, warnLen);
        if (Array.isArray(reportItemsData) && failedVal === 0) {
          const errItems = reportItemsData.filter((item) => (item.status === 'error' || item.status === 'failed')).length;
          if (errItems > 0) failedVal = errItems;
        }
        if (data.inserted !== undefined || data.failed !== undefined) {
          const isDeleteWsFail = isDeleteOperation(data, stepData);
          setStats({
            inserted: isDeleteWsFail ? 0 : (data.inserted || 0),
            updated: data.updated || 0,
            deleted: isDeleteWsFail ? (data.deleted ?? data.inserted ?? 0) : (data.deleted || 0),
            failed: failedVal
          });
        }
      }
    }
  };

  // Handle WebSocket errors - fallback to polling
  const handleWebSocketError = (error) => {
    console.error('WebSocket error, falling back to polling:', error);
    if (jobId) {
      fallbackToPolling(jobId);
    }
  };

  // Fallback to polling when WebSocket fails
  const fallbackToPolling = (jobIdToTrack) => {
    setUseWebSocket(false);
    setConnectionMethod(t('bulkUpload.step3.pollingFallback', 'Polling (fallback)'));
    setStatusMessage(t('bulkUpload.step3.usingPolling', 'Using polling for updates...'));
    startPolling(jobIdToTrack);
  };

  // Start polling for job status
  const startPolling = (jobIdToCheck) => {
        setUploadStatus('polling');
        setProgress(30);
        setStatusMessage(t('bulkUpload.step3.processing', 'Processing...'));

    // Poll every 3 seconds
    pollingIntervalRef.current = setInterval(async () => {
      try {
        const status = await getJobStatus(stepData.uploadTypeGroup, jobIdToCheck, stepData.uploadType);
        
        // Update progress and message
        if (status.progress !== undefined) {
          setProgress(status.progress);
        }
        
        if (status.message) {
          setStatusMessage(status.message);
        }

        // Update stats if available; never show 0 failed when errors, warnings, or error report items exist
        if (status.inserted !== undefined || status.failed !== undefined) {
          const errLenPoll = (status.errors && Array.isArray(status.errors)) ? status.errors.length : 0;
          const warnLenPoll = (status.warnings && Array.isArray(status.warnings)) ? status.warnings.length : 0;
          const reportItemsPoll = status.report_items || status.items;
          let failedPoll = Math.max(status.failed ?? 0, errLenPoll, warnLenPoll);
          if (Array.isArray(reportItemsPoll) && failedPoll === 0) {
            const errItemsPoll = reportItemsPoll.filter((item) => (item.status === 'error' || item.status === 'failed')).length;
            if (errItemsPoll > 0) failedPoll = errItemsPoll;
          }
          const isDeletePoll = isDeleteOperation(status, stepData);
          setStats({
            inserted: isDeletePoll ? 0 : (status.inserted || 0),
            updated: status.updated || 0,
            deleted: isDeletePoll ? (status.deleted ?? status.inserted ?? 0) : (status.deleted || 0),
            failed: failedPoll
          });
        }

        // Check if completed
        if (status.status === 'Completed' || status.status === 'Partially Completed') {
          clearInterval(pollingIntervalRef.current);
          handleCompleted(status);
        } else if (status.status === 'Failed') {
          clearInterval(pollingIntervalRef.current);
          setUploadStatus('failed');
          setError(status.message || t('bulkUpload.step3.processingFailed', 'Processing failed'));
          setProgress(100);
          setReportAvailable(status.report_available || false);
          // Ensure stats reflect failed count; never show 0 failed when errors, warnings, or error report items exist
          const errLenStatus = (status.errors && Array.isArray(status.errors)) ? status.errors.length : 0;
          const warnLenStatus = (status.warnings && Array.isArray(status.warnings)) ? status.warnings.length : 0;
          const reportItemsStatus = status.report_items || status.items;
          let failedValStatus = Math.max(status.failed ?? 0, errLenStatus, warnLenStatus);
          if (Array.isArray(reportItemsStatus) && failedValStatus === 0) {
            const errItemsStatus = reportItemsStatus.filter((item) => (item.status === 'error' || item.status === 'failed')).length;
            if (errItemsStatus > 0) failedValStatus = errItemsStatus;
          }
          if (status.inserted !== undefined || status.failed !== undefined) {
            const isDeletePollFail = isDeleteOperation(status, stepData);
            setStats({
              inserted: isDeletePollFail ? 0 : (status.inserted || 0),
              updated: status.updated || 0,
              deleted: isDeletePollFail ? (status.deleted ?? status.inserted ?? 0) : (status.deleted || 0),
              failed: failedValStatus
            });
          }
        }

      } catch (err) {
        console.error('Polling error:', err);
        // Don't stop polling on transient errors
      }
    }, 3000);
  };

  // Handle completion
  const handleCompleted = async (response) => {
    const isPartiallyCompleted = response.status === 'Partially Completed';
    // Use failed from API; backend sends failed >= count of error report items. Never show 0 failed when errors or report items indicate failures.
    const errorsLength = (response.errors && Array.isArray(response.errors)) ? response.errors.length : 0;
    const warningsLength = (response.warnings && Array.isArray(response.warnings)) ? response.warnings.length : 0;
    let failedCount = Math.max(response.failed ?? 0, errorsLength);
    // Safety net: when report_items are present (e.g. from status poll), ensure failed is at least the number of error/failed items
    const reportItems = response.report_items || response.items;
    if (Array.isArray(reportItems) && failedCount === 0) {
      const errorItemCount = reportItems.filter((item) => (item.status === 'error' || item.status === 'failed')).length;
      if (errorItemCount > 0) failedCount = errorItemCount;
    }
    // Never show 0 failed when there are warnings (e.g. duplicates skipped)
    if (failedCount === 0 && warningsLength > 0) {
      failedCount = Math.max(failedCount, warningsLength);
    }
    
    setUploadStatus('completed');
    setProgress(100);
    
    // Set status message based on failed count and response status
    if (isPartiallyCompleted || failedCount > 0) {
      setStatusMessage(t('bulkUpload.step3.uploadPartiallyCompleted', 'Upload completed with some errors'));
    } else {
      setStatusMessage(t('bulkUpload.step3.uploadCompleted', 'Upload completed successfully!'));
    }
    
    const isDeleteCompleted = isDeleteOperation(response, stepData);
    setStats({
      inserted: isDeleteCompleted ? 0 : (response.inserted || 0),
      updated: response.updated || 0,
      deleted: isDeleteCompleted ? (response.deleted ?? response.inserted ?? 0) : (response.deleted || 0),
      failed: failedCount
    });

    // Always make report available for relationship uploads, or if there are failures or status says so (so Download Report button works)
    setReportAvailable(
      stepData.uploadType === 'Relationship' ||
      failedCount > 0 ||
      (response.failed !== undefined && response.failed > 0) ||
      response.report_available === true ||
      false
    );

    // Check if response contains validation errors (when processing continued despite errors)
    if (response.errors && Array.isArray(response.errors) && response.errors.length > 0) {
      console.log('📋 Response contains validation errors:', response.errors);
      setValidationErrors(prevErrors => {
        // Merge with existing errors, removing duplicates
        const combined = [...prevErrors, ...response.errors];
        const unique = combined.filter((error, index, self) =>
          index === self.findIndex(e => 
            e.row === error.row && 
            e.field === error.field && 
            e.message === error.message
          )
        );
        return unique;
      });
    }

    // If there are failed operations, fetch the report to display the errors
    if (failedCount > 0 && jobId) {
      try {
        const report = await getJobReport(stepData.uploadTypeGroup, jobId, stepData.uploadType);
        // Handle both report_items and items array names
        const reportItems = report.report_items || report.items || [];
        
        console.log('📋 Report structure:', {
          hasReportItems: !!report.report_items,
          hasItems: !!report.items,
          reportItemsLength: reportItems.length,
          report: report
        });
        
        // Convert report items with status "error" to validation error format
        const processingErrors = [];
        reportItems.forEach((item) => {
          if (item.status === 'error' || item.status === 'failed') {
            const messages = item.messages || [];
            if (messages.length > 0) {
              // Check all messages and include those with severity 'error'
              messages.forEach((message) => {
                // Check severity field (used by LegalBulkReportServlet) or type field (for compatibility)
                const isError = message.severity === 'error' || 
                               message.severity === 'ERROR' ||
                               message.type === 'error' || 
                               message.type === 'ERROR';
                
                if (isError) {
                  processingErrors.push({
                    row: item.position || item.row_number || 0,
                    field: item.field_name || item.entity_name || '',
                    message: message.message || message.message_text || 'Processing error',
                    error_code: message.error_code || message.message_code || 'PROCESSING_ERROR'
                  });
                }
              });
            } else {
              // If no messages, create a generic error entry
              processingErrors.push({
                row: item.position || item.row_number || 0,
                field: item.field_name || item.entity_name || '',
                message: 'Processing failed',
                error_code: 'PROCESSING_ERROR'
              });
            }
          }
        });
        
        if (processingErrors.length > 0) {
          console.log('📋 Processing errors found:', processingErrors.length, 'errors');
          console.log('📋 Processing errors details:', processingErrors);
          // Merge with existing validation errors if any
          setValidationErrors(prevErrors => {
            const combined = [...prevErrors, ...processingErrors];
            // Remove duplicates based on row and field
            const unique = combined.filter((error, index, self) =>
              index === self.findIndex(e => e.row === error.row && e.field === error.field && e.message === error.message)
            );
            return unique;
          });
        } else {
          console.warn('⚠️ No processing errors found in report items, but failed count > 0');
        }
      } catch (err) {
        console.error('Failed to fetch processing errors:', err);
        // Don't show error to user, just log it
      }
    }
  };

  // Handle download report
  const handleDownloadReport = async () => {
    try {
      // Validate jobId before attempting download
      if (jobId === null || jobId === undefined || jobId === '' || jobId === 'null' || jobId === 'undefined') {
        alert(t('bulkUpload.step3.jobIdMissing', 'Error: Job ID is missing. The report cannot be downloaded.'));
        return;
      }
      
      const numericJobId = Number(jobId);
      if (isNaN(numericJobId) || numericJobId <= 0) {
        alert(t('bulkUpload.step3.invalidJobId', `Error: Invalid Job ID (${jobId}). The report cannot be downloaded.`));
        return;
      }
      
      await downloadReport(stepData.uploadTypeGroup, jobId, stepData.uploadType);
    } catch (err) {
      console.error('Download report error:', err);
      console.error('Error details:', {
        message: err.message,
        response: err.response,
        responseData: err.response?.data,
        stack: err.stack
      });
      
      // Extract error message from various possible locations
      let errorMessage = null;
      
      // Try to extract from response data first
      if (err.response?.data) {
        const data = err.response.data;
        if (typeof data === 'string') {
          errorMessage = data.trim();
        } else if (data.message && typeof data.message === 'string') {
          errorMessage = data.message.trim();
        } else if (data.error && typeof data.error === 'string') {
          errorMessage = data.error.trim();
        } else if (data.statusText && typeof data.statusText === 'string') {
          errorMessage = data.statusText.trim();
        } else if (data.status && data.statusText) {
          errorMessage = `${data.status}: ${data.statusText}`.trim();
        } else if (typeof data === 'object') {
          // Try to extract meaningful message from object
          const possibleKeys = ['error', 'message', 'detail', 'details', 'errorMessage', 'error_message'];
          for (const key of possibleKeys) {
            if (data[key] && typeof data[key] === 'string' && data[key].trim()) {
              errorMessage = data[key].trim();
              break;
            }
          }
          // If still no message, try to stringify (but limit length)
          if (!errorMessage) {
            try {
              const stringified = JSON.stringify(data);
              errorMessage = stringified.length > 200 ? stringified.substring(0, 200) + '...' : stringified;
            } catch (e) {
              // Ignore stringify errors
            }
          }
        }
      }
      
      // Try response status text if we don't have a message yet
      if (!errorMessage && err.response?.statusText) {
        errorMessage = `${err.response.status || 'HTTP'} ${err.response.statusText}`.trim();
      }
      
      // Try error message if we still don't have one
      if (!errorMessage && err.message && typeof err.message === 'string') {
        errorMessage = err.message.trim();
      }
      
      // Try error string representation
      if (!errorMessage && String(err) !== '[object Object]') {
        const errStr = String(err).trim();
        if (errStr && errStr.length > 0) {
          errorMessage = errStr;
        }
      }
      
      // Final fallback - ensure we always have a message
      if (!errorMessage || errorMessage.trim() === '') {
        if (err.response?.status) {
          errorMessage = `HTTP ${err.response.status}: Failed to download report`;
        } else {
          errorMessage = 'An unknown error occurred while downloading the report';
        }
      }
      
      // Get translation and replace placeholder
      const translatedMessage = t('bulkUpload.step3.downloadReportError', `Failed to download report: {error}`);
      // Replace {error} placeholder if it exists in the translation
      const finalMessage = translatedMessage.includes('{error}') 
        ? translatedMessage.replace('{error}', errorMessage)
        : `${translatedMessage} ${errorMessage}`;
      alert(finalMessage);
    }
  };

  // Handle download last uploaded file
  const handleDownloadLastFile = async () => {
    try {
      // Validate jobId before attempting download
      if (jobId === null || jobId === undefined || jobId === '' || jobId === 'null' || jobId === 'undefined') {
        alert(t('bulkUpload.step3.jobIdMissingFile', 'Error: Job ID is missing. The file cannot be downloaded.'));
        return;
      }
      
      const numericJobId = Number(jobId);
      if (isNaN(numericJobId) || numericJobId <= 0) {
        alert(t('bulkUpload.step3.invalidJobIdFile', `Error: Invalid Job ID (${jobId}). The file cannot be downloaded.`));
        return;
      }
      
      await downloadOriginalFile(stepData.uploadTypeGroup, jobId);
    } catch (err) {
      console.error('Download file error:', err);
      alert(t('bulkUpload.step3.downloadFileError', `Failed to download file: ${err.message || 'Unknown error'}`));
    }
  };

  // Render status icon
  const renderStatusIcon = () => {
    if (uploadStatus === 'completed') {
      return (
        <div className="flex items-center justify-center w-16 h-16 rounded-full bg-success text-white mx-auto mb-4">
          <svg className="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        </div>
      );
    }

    if (uploadStatus === 'failed') {
      return (
        <div className="flex items-center justify-center w-16 h-16 rounded-full bg-danger text-white mx-auto mb-4">
          <svg className="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
      );
    }

    return (
      <div className="flex items-center justify-center w-16 h-16 rounded-full bg-primary text-white mx-auto mb-4">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-white"></div>
      </div>
    );
  };

  return (
    <div className="max-w-2xl mx-auto">
      <div className="bg-white rounded-lg shadow-md p-6">
        <h2 className="text-2xl font-bold text-gray-800 mb-6">{t('bulkUpload.step3.title', 'Step 3: Upload Progress')}</h2>

        {/* Status Icon */}
        {renderStatusIcon()}

        {/* Job Reference */}
        {referenceName && (
          <div className="text-center mb-4">
            <span className="text-sm text-gray-600">{t('bulkUpload.step3.jobReference', 'Job Reference:')} </span>
            <span className="text-sm font-mono font-medium text-gray-800">{referenceName}</span>
          </div>
        )}

        {/* Connection Method Badge */}
        {connectionMethod && (
          <div className="text-center mb-4">
            <span className={`inline-block px-3 py-1 text-xs rounded-full ${
              useWebSocket ? 'bg-green-100 text-green-700' : 'bg-blue-100 text-blue-700'
            }`}>
              {connectionMethod}
            </span>
          </div>
        )}

        {/* Progress Bar */}
        <div className="mb-6">
          <div className="flex justify-between items-center mb-2">
            <span className="text-sm font-medium text-gray-700">{statusMessage}</span>
            <span className="text-sm font-medium text-primary">{progress}%</span>
          </div>
          <Progress value={progress} max={100} />
        </div>

        {/* Error Alert */}
        {((error && uploadStatus === 'failed') || (uploadStatus === 'completed' && validationErrors.length > 0)) && (
          <Alert 
            type="error" 
            title={uploadStatus === 'failed' ? t('bulkUpload.step3.uploadFailedTitle', 'Upload Failed') : t('bulkUpload.step3.uploadCompletedWithErrors', 'Upload Completed with Errors')} 
            message={error || t('bulkUpload.step3.someOperationsFailed', 'Some operations failed during upload')}
            errors={validationErrors}
          />
        )}

        {/* Success Alert - only show when completed with no errors and no failures */}
        {uploadStatus === 'completed' && validationErrors.length === 0 && stats.failed === 0 && (
          <Alert type="success" title={t('bulkUpload.step3.uploadCompletedTitle', 'Upload Completed')} message={t('bulkUpload.step3.allOperationsCompleted', 'All operations completed successfully!')} />
        )}

        {/* Statistics */}
        {(uploadStatus === 'completed' || uploadStatus === 'failed') && (
          <div className="mt-6 grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="bg-success-50 border border-success-500 rounded-lg p-4 text-center">
              <div className="text-2xl font-bold text-success-600">{stats.inserted}</div>
              <div className="text-xs text-success-600 mt-1">{t('bulkUpload.step3.inserted', 'Inserted')}</div>
            </div>
            <div className="bg-blue-50 border border-blue-500 rounded-lg p-4 text-center">
              <div className="text-2xl font-bold text-blue-600">{stats.updated}</div>
              <div className="text-xs text-blue-600 mt-1">{t('bulkUpload.step3.updated', 'Updated')}</div>
            </div>
            <div className="bg-orange-50 border border-orange-500 rounded-lg p-4 text-center">
              <div className="text-2xl font-bold text-orange-600">{stats.deleted}</div>
              <div className="text-xs text-orange-600 mt-1">{t('bulkUpload.step3.deleted', 'Deleted')}</div>
            </div>
            <div className="bg-danger-50 border border-danger-500 rounded-lg p-4 text-center">
              <div className="text-2xl font-bold text-danger-600">{stats.failed}</div>
              <div className="text-xs text-danger-600 mt-1">{t('bulkUpload.step3.failed', 'Failed')}</div>
            </div>
          </div>
        )}

        {/* Action Buttons - including Download Report for Relationship and all completed/failed jobs */}
        {(uploadStatus === 'completed' || uploadStatus === 'failed') && (
          <div className="flex flex-col gap-3 mt-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {/* Always show Download Report for completed/failed (Relationship uses /bulk/relationships/report/{jobId}) */}
              <Button onClick={handleDownloadReport} variant="outline">
                📥 {t('bulkUpload.step3.downloadReport', 'Download Report')}
              </Button>
              {jobId && (
                <Button onClick={handleDownloadLastFile} variant="outline">
                  📄 {t('bulkUpload.step3.downloadLastFile', 'Download Last File')}
                </Button>
              )}
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <Button onClick={onStartOver} variant="secondary">
                🔄 {t('bulkUpload.step3.startOver', 'Start Over')}
              </Button>
              <Button 
                onClick={async () => {
                  try {
                    console.log('🔍 Getting user ID for redirect...');
                    const userId = await ensureCurrentUserId();
                    console.log('👤 User ID:', userId);
                    
                    if (userId) {
                      const redirectUrl = `/view/people/${userId}?tab=activity&subtab=my-jobs`;
                      console.log('✅ Redirecting to:', redirectUrl);
                      window.location.href = redirectUrl;
                    } else {
                      console.warn('⚠️ User ID not found, falling back to bulk-jobs');
                      // Fallback to bulk-jobs if user ID not available
                      window.location.href = '/bulk-jobs.html';
                    }
                  } catch (err) {
                    console.error('❌ Failed to get user ID:', err);
                    window.location.href = '/bulk-jobs.html';
                  }
                }}
                variant="primary"
              >
                📊 {t('bulkUpload.step3.goToMyJobs', 'Go to My Jobs')}
              </Button>
            </div>
          </div>
        )}

        {/* Processing Info */}
        {(uploadStatus === 'uploading' || uploadStatus === 'polling' || uploadStatus === 'connecting') && (
          <div className="mt-6 p-4 bg-blue-50 rounded border border-blue-200">
            <h3 className="text-sm font-medium text-blue-800 mb-2">{t('bulkUpload.step3.processingInformation', 'Processing Information')}</h3>
            <ul className="text-xs text-blue-700 space-y-1">
              <li>• {t('bulkUpload.step3.processingInfo1', 'Your file is being processed in the background')}</li>
              <li>• {t('bulkUpload.step3.processingInfo2', 'Large files may take several minutes to complete')}</li>
              <li>• {t('bulkUpload.step3.processingInfo3', 'Please do not close this window')}</li>
              {useWebSocket ? (
                <li>• ✓ {t('bulkUpload.step3.processingInfo4', 'Connected via WebSocket for real-time updates')}</li>
              ) : (
                <li>• {t('bulkUpload.step3.processingInfo5', 'Status updates every 3 seconds')}</li>
              )}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
};

export default StepUploadProgress;
