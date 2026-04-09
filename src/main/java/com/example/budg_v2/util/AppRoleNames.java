package com.example.budg_v2.util;

/**
 * Canonical role-name matching for app roles stored or sent in varied spellings
 * (e.g. "Super Admin", "SuperAdmin", "super_admin", JWT claims).
 */
public final class AppRoleNames {

    private AppRoleNames() {}

    /**
     * Collapses spaces, hyphens, and underscores for comparison.
     * "Super Admin", "SuperAdmin", "super_admin" → "superadmin".
     */
    public static String compactRoleKey(String role) {
        if (role == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : role.trim().toLowerCase().toCharArray()) {
            if (c == ' ' || c == '_' || c == '-') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static boolean isSuperAdminName(String roleName) {
        String k = compactRoleKey(roleName);
        return "superadmin".equals(k) || "suberadmin".equals(k);
    }

    public static boolean isAdminOnlyName(String roleName) {
        return "admin".equals(compactRoleKey(roleName));
    }

    public static boolean isAdminOrSuperAdminName(String roleName) {
        return isSuperAdminName(roleName) || isAdminOnlyName(roleName);
    }
}
