/**
 * language-switcher.js - Language Switcher Component
 *
 * Provides UI toggle between English and Arabic inside the Profile menu.
 * Persists choice via I18n (localStorage + PATCH /api/me when logged in).
 */

(function () {
    'use strict';

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setupLanguageSwitcher);
    } else {
        setupLanguageSwitcher();
    }

    window.addEventListener('headerReady', setupLanguageSwitcher);

    function setupLanguageSwitcher() {
        // Update display if I18n is ready (profile language submenu or any .language-option)
        if (window.I18n) {
            updateLanguageDisplay(window.I18n.getLocale());
        }

        if (window.languageSwitcherInitialized) return;
        window.languageSwitcherInitialized = true;

        document.addEventListener('click', function (e) {
            const profileLanguageItem = e.target.closest('#profileLanguageItem');
            const languageSubmenu = document.getElementById('languageDropdownInProfile');
            if (profileLanguageItem && languageSubmenu) {
                const clickedInsideSubmenu = languageSubmenu.contains(e.target);
                if (!clickedInsideSubmenu) {
                    e.preventDefault();
                    e.stopPropagation();
                    profileLanguageItem.classList.toggle('submenu-open');
                    languageSubmenu.classList.toggle('active');
                    return;
                }
            }

            const option = e.target.closest('.language-option');
            if (option) {
                e.preventDefault();
                e.stopPropagation();
                const selectedLang = option.getAttribute('data-lang');
                if (window.I18n && typeof window.I18n.switchLanguage === 'function') {
                    // switchLanguage persists locale then reloads the page (cache-busted) so all fields translate
                    window.I18n.switchLanguage(selectedLang);
                }
                return;
            }

            // Close profile language submenu when clicking outside
            const openSubmenu = document.querySelector('#profileLanguageItem.submenu-open');
            if (openSubmenu) {
                openSubmenu.classList.remove('submenu-open');
                const sub = document.getElementById('languageDropdownInProfile');
                if (sub) sub.classList.remove('active');
            }
        });

        window.addEventListener('languageChanged', function (e) {
            if (e.detail && e.detail.locale) {
                updateLanguageDisplay(e.detail.locale);
            }
        });
    }

    function updateLanguageDisplay(locale) {
        const options = document.querySelectorAll('.language-option');
        options.forEach(function (option) {
            if (option.getAttribute('data-lang') === locale) {
                option.classList.add('active');
            } else {
                option.classList.remove('active');
            }
        });

        const profileLabel = document.querySelector('#profileLanguageItem .profile-item-label');
        if (profileLabel) {
            profileLabel.textContent = (window.I18n && window.I18n.t('profile.language')) || 'Language';
        }
    }
})();
