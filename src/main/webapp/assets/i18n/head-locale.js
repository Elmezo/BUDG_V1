/**
 * head-locale.js - Apply dir, lang, and RTL from localStorage before first paint.
 * Load this script in <head> before i18n.js to prevent language/direction flash.
 * Uses absolute path /assets/i18n/ for preload so it works from any page depth.
 */
(function () {
    'use strict';
    try {
        var stored = localStorage.getItem('budg-locale');
        var locale = (stored === 'ar' || stored === 'en') ? stored : 'en';
        var isRTL = locale === 'ar';
        var dir = isRTL ? 'rtl' : 'ltr';

        document.documentElement.setAttribute('dir', dir);
        document.documentElement.setAttribute('lang', locale);

        var rtlStyles = document.getElementById('rtl-styles');
        if (rtlStyles) {
            rtlStyles.disabled = !isRTL;
        }

        if (locale === 'ar') {
            var preload = document.createElement('link');
            preload.rel = 'preload';
            preload.href = '/assets/i18n/ar.json';
            preload.as = 'fetch';
            // Must match i18n.js fetch credentials (omit) or browser warns preload unused
            preload.crossOrigin = 'anonymous';
            document.head.appendChild(preload);

            document.documentElement.classList.add('i18n-pending');
        }
    } catch (e) {
        /* ignore */
    }
})();
