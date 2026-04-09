/**
 * i18n.js - Frontend-only internationalization utility
 * 
 * CRITICAL RULE: This module ONLY translates UI text.
 * It NEVER translates user data, API responses, or database values.
 * 
 * Usage:
 *   HTML: <button data-i18n="button.save">Save</button>
 *   JS:   I18n.t('button.save')
 * 
 * Supported Languages: English (en), Arabic (ar)
 */
(function (window) {
    'use strict';

    const I18n = {
        // Configuration
        currentLocale: 'en',
        defaultLocale: 'en',
        supportedLocales: ['en', 'ar'],
        translations: {},
        /** Fallback translations (e.g. en) when current locale misses a key - avoids showing key path to user */
        defaultTranslations: {},

        /**
         * Initialize i18n on page load
         * Automatically detects and applies saved language preference
         */
        async init(locale) {
            if (window.__BUDG_DEBUG__) console.log('[i18n] Initializing...');

            // After language switch we reload with ?_langcb= to bust cache; strip it so URL stays clean
            try {
                const url = new URL(window.location.href);
                if (url.searchParams.has('_langcb')) {
                    url.searchParams.delete('_langcb');
                    const qs = url.searchParams.toString();
                    const clean = url.pathname + (qs ? '?' + qs : '') + url.hash;
                    window.history.replaceState({}, '', clean);
                }
            } catch (e) { /* ignore */ }

            // Priority: 1) parameter, 2) localStorage, 3) default (forced English as per request)
            this.currentLocale = locale ||
                this.getStoredLocale() ||
                'en'; // Default to English, ignoring browser settings

            // Validate locale
            if (!this.supportedLocales.includes(this.currentLocale)) {
                console.warn(`[i18n] Unsupported locale: ${this.currentLocale}, using ${this.defaultLocale}`);
                this.currentLocale = this.defaultLocale;
            }

            // Load translations
            await this.loadTranslations(this.currentLocale);

            // Apply to DOM
            this.applyTranslations();

            // Apply directionality (LTR/RTL)
            this.applyDirectionality();

            if (window.__BUDG_DEBUG__) console.log(`[i18n] Initialized with locale: ${this.currentLocale}`);

            // Remove i18n-pending so body becomes visible (see head-locale.js + main.css)
            try {
                document.documentElement.classList.remove('i18n-pending');
            } catch (e) { /* ignore */ }

            // Notify pages (e.g. bulk-upload) that translations are ready so they can mount React
            try {
                window.dispatchEvent(new CustomEvent('i18nReady', { detail: { locale: this.currentLocale } }));
            } catch (e) {
                // ignore in environments without CustomEvent
            }
        },

        /**
         * Load translation JSON file
         */

        async loadTranslations(locale) {
            try {
                // improved path resolution: derive from script location if possible
                let basePath = 'assets/i18n/';
                const scriptTag = document.querySelector('script[src*="i18n.js"]');
                if (scriptTag) {
                    const src = scriptTag.getAttribute('src');
                    // Extract path up to the last slash (e.g. "assets/i18n/")
                    const lastSlash = src.lastIndexOf('/');
                    if (lastSlash !== -1) {
                        basePath = src.substring(0, lastSlash + 1);
                    }
                } else {
                    // Fallback: try different depths if simple fetch fails? 
                    // Or check for <base> tag?
                    // For now, let's try root-absolute if we are in a sub-path and relative failed?
                    // Actually, if scriptTag is missing (dynamic load?), we might default to root /assets/i18n/
                    // But for now, most pages should have the script tag if we add it.
                    // If we are in /view/x/y.html, 'assets/i18n/' becomes '/view/x/assets/i18n/' (WRONG).
                    // So we MUST have the helper script tag or guess absolute.
                    // Let's assume absolute '/assets/i18n/' as a fallback if locally hosted, 
                    // or relative to root if we can detect root.

                    // Allow override via config if available
                    if (window.BUDG_CONFIG && window.BUDG_CONFIG.I18N_BASE_PATH) {
                        basePath = window.BUDG_CONFIG.I18N_BASE_PATH;
                    }
                }

                // After language switch we set budg-i18n-reload so JSON isn't served from stale cache
                let fetchOpts = { credentials: 'omit' };
                try {
                    if (sessionStorage.getItem('budg-i18n-reload') === '1') {
                        fetchOpts = { cache: 'no-store', credentials: 'omit' };
                    }
                } catch (e) { /* ignore */ }
                const response = await fetch(`${basePath}${locale}.json`, fetchOpts);
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                this.translations = await response.json();
                this.currentLocale = locale;
                // Load default (en) as fallback when using another locale, so missing keys show English instead of key path
                if (locale !== this.defaultLocale && Object.keys(this.defaultTranslations).length === 0) {
                    try {
                        const defaultFetchOpts = { ...fetchOpts, credentials: 'omit' };
                        const defaultResponse = await fetch(`${basePath}${this.defaultLocale}.json`, defaultFetchOpts);
                        if (defaultResponse.ok) {
                            this.defaultTranslations = await defaultResponse.json();
                            console.log('[i18n] Loaded default locale fallback:', this.defaultLocale);
                        }
                    } catch (e) {
                        console.warn('[i18n] Could not load default locale fallback:', e);
                    }
                }
                if (locale === this.defaultLocale) {
                    this.defaultTranslations = this.translations;
                }
                try {
                    sessionStorage.removeItem('budg-i18n-reload');
                } catch (e) { /* ignore */ }
            } catch (error) {
                console.error(`[i18n] Failed to load translations for ${locale}:`, error);

                // Fallback to default if not already trying default
                if (locale !== this.defaultLocale) {
                    console.log(`[i18n] Falling back to ${this.defaultLocale}`);
                    await this.loadTranslations(this.defaultLocale);
                } else {
                    console.error('[i18n] Critical: Failed to load default translations');
                    this.translations = {}; // Empty fallback
                }
            }
        },

        /**
         * Get translated string by key
         * 
         * @param {string} key - Dot-notation key (e.g., 'button.save')
         * @param {object} params - Optional parameters for interpolation
         * @returns {string} Translated string
         * 
         * @example
         * I18n.t('button.save') // "Save" or "حفظ"
         * I18n.t('message.selectedCount', {count: 5}) // "5 item(s) selected"
         */
        t(key, params) {
            if (!key) {
                console.warn('[i18n] Empty translation key');
                return '';
            }

            const keys = key.split('.');
            let value = this.translations;

            for (const k of keys) {
                if (value && typeof value === 'object' && k in value) {
                    value = value[k];
                } else {
                    value = null;
                    break;
                }
            }

            // If not found in current locale, try default (e.g. en) so user never sees "message.something" as text
            if (value == null && this.defaultTranslations && Object.keys(this.defaultTranslations).length > 0) {
                value = this.defaultTranslations;
                for (const k of keys) {
                    if (value && typeof value === 'object' && k in value) {
                        value = value[k];
                    } else {
                        value = null;
                        break;
                    }
                }
            }

            if (value == null) {
                // Avoid noise when t() runs before loadTranslations() finishes (e.g. admin-panel main.js)
                const translationsLoaded = this.translations && typeof this.translations === 'object' &&
                    Object.keys(this.translations).length > 0;
                if (translationsLoaded &&
                    (typeof window === 'undefined' || window.location.hostname === 'localhost' || window.__I18N_DEBUG__)) {
                    if (!this._missingKeys) this._missingKeys = new Set();
                    if (!this._missingKeys.has(key)) {
                        this._missingKeys.add(key);
                        console.warn(`[i18n] Translation key not found: ${key}`);
                    }
                }
                return key;
            }

            if (typeof value !== 'string') {
                console.warn(`[i18n] Translation value is not a string: ${key}`);
                return key;
            }

            if (params && typeof params === 'object') {
                return value.replace(/\{(\w+)\}/g, (match, paramName) => {
                    return params[paramName] !== undefined ? params[paramName] : match;
                });
            }

            return value;
        },

        /**
         * Switch to a different language
         * Persists locale then reloads the page so every field re-inits with translations
         * and cache is bypassed (_langcb timestamp) so JSON/scripts re-fetch immediately.
         */
        async switchLanguage(locale) {
            if (!this.supportedLocales.includes(locale)) {
                console.error(`[i18n] Unsupported locale: ${locale}`);
                return;
            }

            if (locale === this.currentLocale) {
                return;
            }

            // 1) Persist first so after reload init() reads the new locale from localStorage
            this.storeLocale(locale);

            // 2) Sync backend before navigate (await so request isn't aborted by reload)
            try {
                await fetch('/api/me', {
                    method: 'PATCH',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ locale: locale })
                });
            } catch (e) { /* ignore — guest or offline */ }

            console.log(`[i18n] Language switched to: ${locale}, reloading...`);

            // Mark next load to fetch translation JSON without cache
            try {
                sessionStorage.setItem('budg-i18n-reload', '1');
            } catch (e) { /* ignore */ }

            // 3) Full reload with cache-bust query so HTML/assets revalidate and translations apply everywhere.
            // On Unison Search page, always drop search-specific params (like searchId) to avoid
            // re-running heavy saved searches after language switch.
            try {
                const url = new URL(window.location.href);
                const isSearchPage = url.pathname.toLowerCase().endsWith('/search.html');
                if (isSearchPage) {
                    const target = new URL('/search.html', window.location.origin);
                    target.searchParams.set('_langcb', String(Date.now()));
                    window.location.replace(target.toString());
                } else {
                    url.searchParams.set('_langcb', String(Date.now()));
                    window.location.replace(url.toString());
                }
            } catch (e) {
                window.location.reload();
            }
        },

        /**
         * Apply translations to DOM elements with [data-i18n] attributes
         * @param {Element} [root] - Optional root element; if provided, only elements inside root are updated. Otherwise document.
         * Supported attributes:
         * - data-i18n: Translates textContent
         * - data-i18n-placeholder: Translates placeholder
         * - data-i18n-title: Translates title (tooltip)
         * - data-i18n-aria: Translates aria-label
         */
        applyTranslations(root) {
            const scope = root && root.nodeType === 1 ? root : document;
            let count = 0;

            scope.querySelectorAll('[data-i18n]').forEach(element => {
                const key = element.getAttribute('data-i18n');
                if (key) {
                    const translation = this.t(key);
                    element.textContent = translation;
                    count++;
                }
            });

            scope.querySelectorAll('[data-i18n-placeholder]').forEach(element => {
                const key = element.getAttribute('data-i18n-placeholder');
                if (key) {
                    element.placeholder = this.t(key);
                    count++;
                }
            });

            scope.querySelectorAll('[data-i18n-title]').forEach(element => {
                const key = element.getAttribute('data-i18n-title');
                if (key) {
                    element.title = this.t(key);
                    count++;
                }
            });

            scope.querySelectorAll('[data-i18n-aria]').forEach(element => {
                const key = element.getAttribute('data-i18n-aria');
                if (key) {
                    element.setAttribute('aria-label', this.t(key));
                    count++;
                }
            });

            if (window.__BUDG_DEBUG__ && count > 0) console.log(`[i18n] Applied ${count} translations`);
        },

        /**
         * Apply text directionality (LTR/RTL) and language attribute
         */
        applyDirectionality() {
            const html = document.documentElement;
            const direction = this.isRTL() ? 'rtl' : 'ltr';
            const lang = this.currentLocale;

            html.setAttribute('dir', direction);
            html.setAttribute('lang', lang);

            // Toggle RTL stylesheet if it exists
            const rtlStylesheet = document.getElementById('rtl-styles');
            if (rtlStylesheet) {
                rtlStylesheet.disabled = !this.isRTL();
            }

            if (window.__BUDG_DEBUG__) console.log(`[i18n] Applied directionality: ${direction}, language: ${lang}`);
        },

        /**
         * Check if current locale uses RTL (Right-to-Left) direction
         */
        isRTL() {
            return this.currentLocale === 'ar';
        },

        /**
         * Convert Western digits (0-9) to Eastern Arabic digits (٠-٩) for Arabic locale.
         * @param {number|string} value
         * @returns {string}
         */
        toArabicIndicDigits(value) {
            if (this.currentLocale !== 'ar') {
                return String(value);
            }
            const s = String(value);
            const map = ['٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'];
            return s.replace(/\d/g, (d) => map[parseInt(d, 10)]);
        },

        /**
         * Facet sidebar "filtered of total" label (e.g. "12 of 184" / "١٢ من ١٨٤").
         * @param {number} count
         * @param {number} total
         * @returns {string}
         */
        formatFacetCountLabel(count, total) {
            const ofText = this.t('label.of') || 'of';
            const c = this.toArabicIndicDigits(count);
            const t = this.toArabicIndicDigits(total);
            return `${c} ${ofText} ${t}`;
        },

        /**
         * Get current locale
         */
        getLocale() {
            return this.currentLocale;
        },

        /**
         * Get display name for current locale
         */
        getDisplayName() {
            if (this.translations._meta && this.translations._meta.displayName) {
                return this.translations._meta.displayName;
            }
            return this.currentLocale.toUpperCase();
        },

        /**
         * Get stored locale from localStorage
         */
        getStoredLocale() {
            try {
                return localStorage.getItem('budg-locale');
            } catch (e) {
                console.warn('[i18n] Failed to read from localStorage:', e);
                return null;
            }
        },

        /**
         * Store locale preference in localStorage (key aligned with backend people.Locale / PATCH /api/me)
         */
        storeLocale(locale) {
            try {
                localStorage.setItem('budg-locale', locale);
            } catch (e) {
                console.warn('[i18n] Failed to write to localStorage:', e);
            }
        },

        /**
         * Apply locale from /api/me response (authenticated user's people.Locale)
         * Call when header/auth receives user data with locale.
         */
        async setLocaleFromMe(meData) {
            if (!meData || !meData.authenticated) return;
            const locale = meData.locale;
            if (!locale || !this.supportedLocales.includes(locale) || locale === this.currentLocale) return;
            await this.loadTranslations(locale);
            this.storeLocale(locale);
            try {
                window.dispatchEvent(new CustomEvent('languageChanged', { detail: { locale: this.currentLocale, direction: this.isRTL() ? 'rtl' : 'ltr' } }));
            } catch (e) { /* ignore */ }
            this.applyTranslations();
            this.applyDirectionality();
        },

        /**
         * Detect browser language preference
         */
        detectBrowserLanguage() {
            const browserLang = navigator.language || navigator.userLanguage;
            if (browserLang) {
                const lang = browserLang.split('-')[0]; // 'ar-SA' -> 'ar'
                if (this.supportedLocales.includes(lang)) {
                    console.log(`[i18n] Detected browser language: ${lang}`);
                    return lang;
                }
            }
            return null;
        },

        /**
         * Get all supported locales
         */
        getSupportedLocales() {
            return this.supportedLocales;
        }
    };

    // Expose globally
    window.I18n = I18n;

    // Auto-initialize on DOM ready; expose promise so main.js can await before injecting header
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            window.i18nReadyPromise = I18n.init();
        });
    } else {
        window.i18nReadyPromise = I18n.init();
    }

})(window);
