import React, { useState, useEffect } from 'react';
import Button from './ui/Button';
import Alert from './ui/Alert';
import { useTranslation } from '../hooks/useTranslation';
import { getBulkJobsHistory, downloadReport, downloadOriginalFile } from '../services/apiService';
import { fetchCurrentUser, ensureCurrentUserId } from '../utils/auth';

const BulkJobsDashboard = () => {
  const { t, locale } = useTranslation();
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    // Initialize user session to prevent warnings
    const initializeUser = async () => {
      try {
        // Check if user already exists in session
        const userStr = sessionStorage.getItem('currentUser');
        if (!userStr) {
          // Silently fetch user if not in session (suppress warnings)
          await fetchCurrentUser();
        }
      } catch (err) {
        // Silently fail - user session might not be required for viewing jobs
        console.debug('User session initialization skipped:', err);
      }
    };

    // Initialize user session first, then load jobs
    initializeUser().then(() => {
      loadJobs();
    });
    // No auto-refresh interval - data updates only on manual refresh
  }, []);

  const loadJobs = async (silent = false) => {
    try {
      if (!silent) setLoading(true);

      // Filter by current user's jobs
      const userId = await ensureCurrentUserId();

      const response = await getBulkJobsHistory(userId);
      setJobs(response.jobs || []);
      setError('');
    } catch (err) {
      console.error('Failed to load jobs:', err);
      setError(t('bulkUpload.jobs.failedToLoadHistory', 'Failed to load jobs history'));
    } finally {
      if (!silent) setLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    const statusStyles = {
      'Pending': 'bg-yellow-100 text-yellow-700 border-yellow-300',
      'Processing': 'bg-blue-100 text-blue-700 border-blue-300',
      'Completed': 'bg-success-100 text-success-700 border-success-300',
      'Failed': 'bg-danger-100 text-danger-700 border-danger-300'
    };

    const statusIcons = {
      'Pending': '⏳',
      'Processing': '⚙️',
      'Completed': '✓',
      'Failed': '✕'
    };

    const statusLabel = status === 'Pending' ? t('bulkUpload.jobs.statusPending', 'Pending')
      : status === 'Processing' ? t('bulkUpload.jobs.statusProcessing', 'Processing')
      : status === 'Completed' ? t('bulkUpload.jobs.statusCompleted', 'Completed')
      : status === 'Failed' ? t('bulkUpload.jobs.statusFailed', 'Failed')
      : status;
    return (
      <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-medium border ${statusStyles[status] || 'bg-gray-100 text-gray-700 border-gray-300'}`}>
        <span className="mr-1.5 text-xs leading-none">{statusIcons[status] || '•'}</span>
        {statusLabel}
      </span>
    );
  };

  const formatDate = (dateString) => {
    if (!dateString) return t('bulkUpload.jobs.na', 'N/A');
    const date = new Date(dateString);
    const localeTag = (locale === 'ar') ? 'ar' : 'en-US';
    return date.toLocaleString(localeTag, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getUploadOptionText = (uploadOption) => {
    if (!uploadOption) return t('bulkUpload.jobs.upload', 'Upload');

    if (uploadOption === 'Add New Items' || uploadOption === 'Insert') {
      return t('bulkUpload.jobs.insertItems', 'Insert Items');
    } else if (uploadOption === 'Update Existing Items' || uploadOption === 'Update') {
      return t('bulkUpload.jobs.updateItems', 'Update Items');
    } else if (uploadOption === 'Remove Existing Items' || uploadOption === 'Delete') {
      return t('bulkUpload.jobs.deleteItems', 'Delete Items');
    }

    return t('bulkUpload.jobs.upload', 'Upload');
  };

  const handleDownloadReport = async (entity, jobId, uploadType = 'Object') => {
    try {
      console.log('📥 Downloading report:');
      console.log('  - Entity:', entity);
      console.log('  - Job ID:', jobId);
      console.log('  - Upload Type:', uploadType);

      // Validate inputs - check for null, undefined, empty string, or invalid number
      if (jobId === null || jobId === undefined || jobId === '' || jobId === 'null' || jobId === 'undefined') {
        alert(t('bulkUpload.jobs.jobIdMissingReport', 'Error: Job ID is missing from this record. The report cannot be downloaded.'));
        return;
      }
      
      const numericJobId = Number(jobId);
      if (isNaN(numericJobId) || numericJobId <= 0) {
        alert(t('bulkUpload.jobs.invalidJobIdReport', 'Error: Invalid Job ID ({jobId}). The report cannot be downloaded.').replace('{jobId}', jobId));
        return;
      }

      if (!entity) {
        console.warn('⚠️ Entity is missing, using default "regulator"');
      }

      await downloadReport(entity, jobId, uploadType);
      console.log('✅ Report downloaded successfully');
    } catch (err) {
      console.error('❌ Failed to download report:');
      console.error('  - Error:', err);

      const errorMsg = err.response?.data?.message || err.response?.data?.error || err.message || t('bulkUpload.jobs.unknownError', 'Unknown error');
      alert(t('bulkUpload.jobs.downloadReportFailed', 'Failed to download report: {error}').replace('{error}', errorMsg));
    }
  };

  const handleDownloadFile = async (entity, jobId) => {
    try {
      // Validate inputs - check for null, undefined, empty string, or invalid number
      if (jobId === null || jobId === undefined || jobId === '' || jobId === 'null' || jobId === 'undefined') {
        alert(t('bulkUpload.jobs.jobIdMissingFile', 'Error: Job ID is missing. The file cannot be downloaded.'));
        return;
      }
      
      const numericJobId = Number(jobId);
      if (isNaN(numericJobId) || numericJobId <= 0) {
        alert(t('bulkUpload.jobs.invalidJobIdFile', 'Error: Invalid Job ID ({jobId}). The file cannot be downloaded.').replace('{jobId}', jobId));
        return;
      }
      await downloadOriginalFile(entity, jobId);
    } catch (err) {
      alert(t('bulkUpload.jobs.downloadFileFailed', 'Failed to download file: {error}').replace('{error}', err.message || ''));
    }
  };

  if (loading) {
    return (
      <div className="min-h-full bg-gray-50 py-8 px-4">
        <div className="max-w-7xl mx-auto">
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
            <span className="ml-3 text-gray-600">{t('bulkUpload.jobs.loading', 'Loading jobs...')}</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-full bg-gray-50 py-8 px-4">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 mb-2">{t('bulkUpload.jobs.title', 'Bulk Upload Jobs')}</h1>
              <p className="text-gray-600">{t('bulkUpload.jobs.subtitle', 'View and manage your recent bulk upload operations')}</p>
            </div>
            <div className="flex items-center gap-3 flex-wrap">
              <Button
                onClick={() => loadJobs()}
                variant="outline"
                className="flex items-center gap-2"
              >
                <i className="fas fa-sync-alt text-sm"></i>
                <span>{t('bulkUpload.jobs.refresh', 'Refresh')}</span>
              </Button>
              <Button
                onClick={() => window.location.href = '/bulk-upload.html'}
                variant="primary"
                className="flex items-center gap-2"
              >
                <i className="fas fa-plus text-sm"></i>
                <span>{t('bulkUpload.jobs.newUpload', 'New Upload')}</span>
              </Button>
            </div>
          </div>
        </div>

        {/* Error Alert */}
        {error && (
          <Alert type="error" message={error} onClose={() => setError('')} />
        )}

        {/* Jobs Table */}
        {jobs.length === 0 ? (
          <div className="bg-white rounded-lg shadow-md border border-gray-200 p-12 text-center">
            <div className="text-gray-400 mb-4">
              <i className="fas fa-file-upload text-5xl"></i>
            </div>
            <h3 className="text-xl font-semibold text-gray-900 mb-2">{t('bulkUpload.jobs.noJobsFound', 'No jobs found')}</h3>
            <p className="text-gray-600 mb-6 max-w-md mx-auto">{t('bulkUpload.jobs.noJobsDescription', "You haven't uploaded any files yet. Start by uploading your first bulk file.")}</p>
            <Button
              onClick={() => window.location.href = '/bulk-upload.html'}
              variant="primary"
              className="inline-flex items-center gap-2"
            >
              <i className="fas fa-plus text-sm"></i>
              <span>{t('bulkUpload.jobs.startFirstUpload', 'Start Your First Upload')}</span>
            </Button>
          </div>
        ) : (
          <div className="bg-white rounded-lg shadow-md overflow-hidden border border-gray-200">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50 border-b-2 border-gray-200">
                  <tr>
                    <th className="text-left px-6 py-4 font-semibold text-gray-700 text-sm uppercase tracking-wider">{t('bulkUpload.jobs.reference', 'Reference')}</th>
                    <th className="text-left px-6 py-4 font-semibold text-gray-700 text-sm uppercase tracking-wider">{t('bulkUpload.jobs.entity', 'Entity')}</th>
                    <th className="text-left px-6 py-4 font-semibold text-gray-700 text-sm uppercase tracking-wider">{t('bulkUpload.jobs.status', 'Status')}</th>
                    <th className="text-left px-6 py-4 font-semibold text-gray-700 text-sm uppercase tracking-wider">{t('bulkUpload.jobs.items', 'Items')}</th>
                    <th className="text-left px-6 py-4 font-semibold text-gray-700 text-sm uppercase tracking-wider">{t('bulkUpload.jobs.results', 'Results')}</th>
                    <th className="text-left px-6 py-4 font-semibold text-gray-700 text-sm uppercase tracking-wider">{t('bulkUpload.jobs.date', 'Date')}</th>
                    <th className="text-right px-6 py-4 font-semibold text-gray-700 text-sm uppercase tracking-wider">{t('bulkUpload.jobs.actions', 'Actions')}</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {jobs.map((job) => (
                    <tr key={job.job_id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="font-medium text-sm text-gray-900">{job.reference_name || t('bulkUpload.jobs.na', 'N/A')}</div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex flex-col gap-1">
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                            {job.entity || t('bulkUpload.jobs.unknown', 'Unknown')}
                          </span>
                          {job.upload_option && (
                            <span className="text-xs text-gray-600">
                              {getUploadOptionText(job.upload_option)}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        {getStatusBadge(job.status)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm font-medium text-gray-900">{job.items_count || 0}</div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex flex-col gap-1 text-xs">
                          {job.inserted > 0 && (
                            <span className="inline-flex items-center text-green-700">
                              <i className="fas fa-check-circle mr-1.5 text-xs"></i>
                              {t('bulkUpload.jobs.insertedCount', '{count} inserted').replace('{count}', job.inserted)}
                            </span>
                          )}
                          {job.updated > 0 && (
                            <span className="inline-flex items-center text-blue-700">
                              <i className="fas fa-sync-alt mr-1.5 text-xs"></i>
                              {t('bulkUpload.jobs.updatedCount', '{count} updated').replace('{count}', job.updated)}
                            </span>
                          )}
                          {job.deleted > 0 && (
                            <span className="inline-flex items-center text-orange-700">
                              <i className="fas fa-trash-alt mr-1.5 text-xs"></i>
                              {t('bulkUpload.jobs.deletedCount', '{count} deleted').replace('{count}', job.deleted)}
                            </span>
                          )}
                          {job.failed > 0 && (
                            <span className="inline-flex items-center text-red-700 font-medium">
                              <i className="fas fa-times-circle mr-1.5 text-xs"></i>
                              {t('bulkUpload.jobs.failedCount', '{count} failed').replace('{count}', job.failed)}
                            </span>
                          )}
                          {job.inserted === 0 && job.updated === 0 && job.deleted === 0 && job.failed === 0 && (
                            <span className="text-gray-400">{t('bulkUpload.jobs.noResults', 'No results')}</span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm text-gray-900">{formatDate(job.created_date)}</div>
                        {job.completed_date && (
                          <div className="text-xs text-gray-500 mt-1 flex items-center">
                            <i className="fas fa-check-circle mr-1.5 text-green-600 text-xs"></i>
                            {formatDate(job.completed_date)}
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                        <div className="flex items-center justify-end gap-2">
                          {job.failed > 0 && job.job_id && (
                            <button
                              onClick={() => handleDownloadReport(job.technical_entity || job.entity, job.job_id, job.upload_type || 'Object')}
                              className="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
                              title={t('bulkUpload.jobs.downloadErrorReport', 'Download Error Report')}
                            >
                              <i className="fas fa-download mr-1.5 text-xs"></i>
                              {t('bulkUpload.jobs.report', 'Report')}
                            </button>
                          )}
                          {job.job_id && (
                            <button
                              onClick={() => handleDownloadFile(job.technical_entity || job.entity, job.job_id)}
                              className="inline-flex items-center px-3 py-2 border border-gray-300 text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-colors"
                              title={t('bulkUpload.jobs.downloadOriginalFile', 'Download Original File')}
                            >
                              <i className="fas fa-file-download mr-1.5 text-xs"></i>
                              {t('bulkUpload.jobs.file', 'File')}
                            </button>
                          )}
                          {!job.job_id && (
                            <span className="text-xs text-gray-400 italic">{t('bulkUpload.jobs.noJobId', 'No job ID')}</span>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Footer */}
        {jobs.length > 0 && (
          <div className="mt-6 flex items-center justify-between text-sm text-gray-500">
            <p>{t('bulkUpload.jobs.showingJobs', 'Showing {count} bulk upload job(s)').replace('{count}', jobs.length)}</p>
            <p className="text-xs text-gray-400 flex items-center">
              <i className="fas fa-info-circle mr-1.5 text-xs"></i>
              {t('bulkUpload.jobs.clickRefreshToUpdate', 'Click refresh to update the list')}
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default BulkJobsDashboard;

