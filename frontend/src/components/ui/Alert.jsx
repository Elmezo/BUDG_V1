import React, { useEffect } from 'react';
import { useTranslation } from '../../hooks/useTranslation';

const Alert = ({ type = 'info', title, message, errors = [], onClose }) => {
  const { t } = useTranslation();
  const styles = {
    success: 'bg-success-50 border-success-500 text-success-600',
    error: 'bg-danger-50 border-danger-500 text-danger-600',
    warning: 'bg-yellow-50 border-yellow-500 text-yellow-600',
    info: 'bg-blue-50 border-blue-500 text-blue-600',
  };

  const icons = {
    success: '✓',
    error: '✕',
    warning: '⚠',
    info: 'ℹ',
  };

  // Debug: Log errors when they change
  useEffect(() => {
    if (errors && errors.length > 0) {
      console.log('🔔 Alert component received errors:', errors);
    }
  }, [errors]);

  return (
    <div className={`border-l-4 p-4 mb-4 ${styles[type]}`} role="alert">
      <div className="flex items-start">
        <div className="flex-shrink-0 text-xl mr-3">{icons[type]}</div>
        <div className="flex-1">
          {title && <p className="font-medium mb-1">{title}</p>}
          {message && <p className="text-sm">{message}</p>}
          {errors && errors.length > 0 && (
            <div className="mt-3 space-y-2">
              <p className="text-xs font-semibold mb-1">{t('message.errorDetails', 'Error Details:')}</p>
              {errors.map((error, index) => (
                <div key={index} className="text-sm pl-2 border-l-2 border-current opacity-80">
                  {typeof error === 'object' ? (
                    <>
                      {error.message && <p className="font-medium">{error.message}</p>}
                      {error.field != null && error.field !== '' && (
                        <p className="text-xs mt-0.5">
                          <span className="font-medium">{t('bulkUpload.error.field', 'Field')}</span>
                          {': '}
                          {error.field}
                        </p>
                      )}
                      {error.row != null && error.row !== '' && (
                        <p className="text-xs mt-0.5">
                          <span className="font-medium">{t('bulkUpload.error.row', 'Row')}</span>
                          {': '}
                          {error.row}
                        </p>
                      )}
                      {error.error_code != null && error.error_code !== '' && (
                        <p className="text-xs mt-0.5">
                          <span className="font-medium">{t('bulkUpload.error.code', 'Code')}</span>
                          {': '}
                          {error.error_code}
                        </p>
                      )}
                    </>
                  ) : (
                    <p>{error}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="flex-shrink-0 ml-3 text-lg hover:opacity-70"
            aria-label={t('button.close', 'Close')}
          >
            ×
          </button>
        )}
      </div>
    </div>
  );
};

export default Alert;

