/**
 * Shared save-error formatting for edit facets.
 * Prefer server body.error / body.message; wrap with i18n (e.g. Arabic) via common.messages.errorSaving.
 */
(function (global) {
    function saveErrorDetail(err) {
        if (!err) return '';
        const body = err.body;
        if (body && typeof body === 'object') {
            const d = body.error || body.message;
            if (d) return String(d);
        }
        if (err.message) return String(err.message);
        return '';
    }

    /**
     * @param {Error|object} err - API error (may have .body) or any thrown value
     * @param {string} [fallbackDetail] - used when err yields no detail
     * @returns {string} User-facing message (translated wrapper when I18n is available)
     */
    function formatSaveError(err, fallbackDetail) {
        const detail = saveErrorDetail(err) || (fallbackDetail ? String(fallbackDetail) : '');
        if (global.I18n && typeof global.I18n.t === 'function') {
            const unknown = global.I18n.t('common.messages.unknownError') || 'Unknown error occurred';
            const msg = detail || unknown;
            const wrapped = global.I18n.t('common.messages.errorSaving', { error: msg });
            if (wrapped && wrapped !== 'common.messages.errorSaving') return wrapped;
            return 'Error saving: ' + msg;
        }
        return detail ? ('Error saving: ' + detail) : 'Failed to save';
    }

    global.saveErrorDetail = saveErrorDetail;
    global.formatSaveError = formatSaveError;
})(typeof window !== 'undefined' ? window : this);
