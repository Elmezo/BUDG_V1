/**
 * Canonical app role checks (align with Java AppRoleNames).
 */
(function (global) {
    'use strict';

    function compactRoleKey(role) {
        if (role == null || role === '') {
            return '';
        }
        return String(role).trim().toLowerCase().replace(/[\s\-_]+/g, '');
    }

    function isSuperAdminRole(role) {
        var k = compactRoleKey(role);
        return k === 'superadmin' || k === 'suberadmin';
    }

    function isAdminOnlyRole(role) {
        return compactRoleKey(role) === 'admin';
    }

    function isAdminOrSuperAdminRole(role) {
        return isSuperAdminRole(role) || isAdminOnlyRole(role);
    }

    global.RoleUtils = {
        compactRoleKey: compactRoleKey,
        isSuperAdminRole: isSuperAdminRole,
        isAdminOnlyRole: isAdminOnlyRole,
        isAdminOrSuperAdminRole: isAdminOrSuperAdminRole
    };
}(typeof window !== 'undefined' ? window : this));
