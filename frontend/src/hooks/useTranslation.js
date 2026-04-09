import { useState, useEffect, useCallback } from 'react';

export function useTranslation() {
  const [locale, setLocale] = useState(
    window.I18n?.currentLocale || 'en'
  );

  // Force re-render when locale changes by updating state
  const updateLocale = useCallback(() => {
    if (window.I18n && window.I18n.currentLocale) {
      const newLocale = window.I18n.currentLocale;
      setLocale(prevLocale => {
        // Only update if locale actually changed
        if (prevLocale !== newLocale) {
          return newLocale;
        }
        return prevLocale;
      });
    }
  }, []);

  useEffect(() => {
    // Initial check
    updateLocale();

    // Listen for language changes
    const handleLanguageChange = () => {
      updateLocale();
    };

    window.addEventListener('languageChanged', handleLanguageChange);
    
    // Also poll for I18n initialization (with timeout)
    let attempts = 0;
    const maxAttempts = 50; // 5 seconds max
    const checkI18n = setInterval(() => {
      attempts++;
      updateLocale();
      
      if (window.I18n && window.I18n.currentLocale) {
        clearInterval(checkI18n);
      } else if (attempts >= maxAttempts) {
        clearInterval(checkI18n);
      }
    }, 100);

    return () => {
      window.removeEventListener('languageChanged', handleLanguageChange);
      clearInterval(checkI18n);
    };
  }, [updateLocale]);

  const t = useCallback((key, defaultValue = '') => {
    if (window.I18n && typeof window.I18n.t === 'function') {
      const translation = window.I18n.t(key);
      return translation !== key ? translation : defaultValue;
    }
    return defaultValue || key;
  }, [locale]); // Re-create function when locale changes

  return { t, locale };
}

