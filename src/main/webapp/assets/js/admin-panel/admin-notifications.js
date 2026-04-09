/**
 * Unified admin panel toast notifications.
 * DOM: notification + notification-{type} + show, notification-content, FA icon + span.
 * Loaded first so window.showNotification is available to all admin-panel modules.
 */
(function () {
    var TOAST_CLASS = 'admin-panel-toast';
    var CONFIRM_OVERLAY_CLASS = 'admin-confirm-overlay';
    var CONFIRM_MODAL_CLASS = 'admin-confirm-modal';
    var nativeAlert = window.alert ? window.alert.bind(window) : null;

    function tOrDefault(key, fallbackText) {
        if (!(window.I18n && typeof window.I18n.t === 'function')) {
            return fallbackText;
        }
        var translated = window.I18n.t(key);
        if (!translated || translated === key) {
            return fallbackText;
        }
        return translated;
    }

    function showAdminNotification(message, type) {
        type = type || 'error';
        var msg = message == null ? '' : String(message);

        document.querySelectorAll('.' + TOAST_CLASS).forEach(function (el) {
            el.remove();
        });

        var wrap = document.createElement('div');
        wrap.className = 'notification notification-' + type + ' show ' + TOAST_CLASS;

        var content = document.createElement('div');
        content.className = 'notification-content';

        var icon = document.createElement('i');
        if (type === 'success') {
            icon.className = 'fas fa-check-circle';
        } else if (type === 'info') {
            icon.className = 'fas fa-info-circle';
        } else {
            // error, warning, or unknown → same icon as spec
            icon.className = 'fas fa-exclamation-triangle';
        }

        var span = document.createElement('span');
        span.textContent = msg;
        if (msg.indexOf('\n') >= 0) {
            span.style.whiteSpace = 'pre-line';
        }

        var closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'notification-close';
        closeBtn.setAttribute('aria-label', 'Close');
        closeBtn.textContent = '\u00d7';
        closeBtn.onclick = function () {
            if (wrap.parentNode) wrap.remove();
        };

        content.appendChild(icon);
        content.appendChild(span);
        content.appendChild(closeBtn);
        wrap.appendChild(content);
        document.body.appendChild(wrap);

        setTimeout(function () {
            if (wrap.parentNode) wrap.remove();
        }, 5000);
    }

    function closeConfirm(overlay, resolveValue, resolveFn) {
        if (!overlay) return;
        if (overlay.__escHandler) {
            document.removeEventListener('keydown', overlay.__escHandler);
        }
        overlay.classList.remove('show');
        setTimeout(function () {
            if (overlay.parentNode) {
                overlay.remove();
            }
            resolveFn(resolveValue);
        }, 120);
    }

    function showConfirmDialog(options) {
        options = options || {};
        var title = options.title || tOrDefault('common.confirm', 'Confirm');
        var message = options.message == null ? '' : String(options.message);
        var confirmText = options.confirmText || 'OK';
        var cancelText = options.cancelText || 'Cancel';
        var type = options.type || 'warning';

        document.querySelectorAll('.' + CONFIRM_OVERLAY_CLASS).forEach(function (el) {
            el.remove();
        });

        return new Promise(function (resolve) {
            var overlay = document.createElement('div');
            overlay.className = CONFIRM_OVERLAY_CLASS;

            var modal = document.createElement('div');
            modal.className = CONFIRM_MODAL_CLASS + ' notification-' + type;
            modal.setAttribute('role', 'dialog');
            modal.setAttribute('aria-modal', 'true');

            var titleEl = document.createElement('h3');
            titleEl.className = 'admin-confirm-title';
            titleEl.textContent = title;

            var messageEl = document.createElement('p');
            messageEl.className = 'admin-confirm-message';
            messageEl.textContent = message;

            var actions = document.createElement('div');
            actions.className = 'admin-confirm-actions';

            var cancelBtn = document.createElement('button');
            cancelBtn.type = 'button';
            cancelBtn.className = 'admin-confirm-btn admin-confirm-cancel';
            cancelBtn.textContent = cancelText;
            cancelBtn.addEventListener('click', function () {
                closeConfirm(overlay, false, resolve);
            });

            var confirmBtn = document.createElement('button');
            confirmBtn.type = 'button';
            confirmBtn.className = 'admin-confirm-btn admin-confirm-primary';
            confirmBtn.textContent = confirmText;
            confirmBtn.addEventListener('click', function () {
                closeConfirm(overlay, true, resolve);
            });

            actions.appendChild(cancelBtn);
            actions.appendChild(confirmBtn);
            modal.appendChild(titleEl);
            modal.appendChild(messageEl);
            modal.appendChild(actions);
            overlay.appendChild(modal);
            document.body.appendChild(overlay);

            overlay.addEventListener('click', function (e) {
                if (e.target === overlay) {
                    closeConfirm(overlay, false, resolve);
                }
            });

            var escHandler = function (e) {
                if (e.key === 'Escape') {
                    closeConfirm(overlay, false, resolve);
                }
            };
            overlay.__escHandler = escHandler;
            document.addEventListener('keydown', escHandler);

            requestAnimationFrame(function () {
                overlay.classList.add('show');
                confirmBtn.focus();
            });
        });
    }

    function showAlertDialog(message, type) {
        if (typeof window.showAdminNotification === 'function') {
            window.showAdminNotification(message, type || 'info');
            return;
        }
        if (nativeAlert) {
            nativeAlert(message);
        }
    }

    window.showAdminNotification = showAdminNotification;
    window.showNotification = showAdminNotification;
    window.showConfirmDialog = showConfirmDialog;
    window.showAlertDialog = showAlertDialog;

    // Replace browser alert with themed notification on pages where this script is loaded.
    window.alert = function (message) {
        showAlertDialog(message, 'info');
    };
})();
