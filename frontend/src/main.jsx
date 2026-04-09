import React from 'react';
import ReactDOM from 'react-dom/client';
import BulkUploadWizard from './components/BulkUploadWizard';
import './index.css';

// Wait for i18n to be initialized before rendering React
async function initializeApp() {
  // Wait for I18n to be available and initialized
  const waitForI18n = () => {
    return new Promise((resolve) => {
      // Check if I18n is already initialized (has translations loaded)
      if (window.I18n && window.I18n.currentLocale && Object.keys(window.I18n.translations || {}).length > 0) {
        console.log('[React] I18n already initialized with locale:', window.I18n.currentLocale);
        resolve();
        return;
      }

      // Poll for I18n to become available and initialized
      let attempts = 0;
      const maxAttempts = 100; // 10 seconds max wait
      const checkInterval = setInterval(() => {
        attempts++;
        
        // Check if I18n is initialized (has translations)
        if (window.I18n && 
            window.I18n.currentLocale && 
            window.I18n.translations && 
            Object.keys(window.I18n.translations).length > 0) {
          console.log('[React] I18n initialized with locale:', window.I18n.currentLocale);
          clearInterval(checkInterval);
          resolve();
        } else if (attempts >= maxAttempts) {
          clearInterval(checkInterval);
          console.warn('[React] I18n not fully initialized after waiting, proceeding anyway');
          resolve();
        }
      }, 100);
    });
  };

  await waitForI18n();

  // Now render React
  const rootElement = document.getElementById('bulk-upload-root');
  if (rootElement) {
    const root = ReactDOM.createRoot(rootElement);
    root.render(
      <React.StrictMode>
        <BulkUploadWizard />
      </React.StrictMode>
    );
  }
}

// Start initialization
initializeApp().catch((error) => {
  console.error('[React] Failed to initialize app:', error);
  // Fallback: render anyway
  const rootElement = document.getElementById('bulk-upload-root');
  if (rootElement) {
    const root = ReactDOM.createRoot(rootElement);
    root.render(
      <React.StrictMode>
        <BulkUploadWizard />
      </React.StrictMode>
    );
  }
});

