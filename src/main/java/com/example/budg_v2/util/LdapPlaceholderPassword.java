package com.example.budg_v2.util;

/**
 * Marker password stored in {@code people.password} for LDAP-linked accounts.
 * Not a user-chosen secret: LDAP is the real authentication path.
 * Prefer {@link #matches(String)} over comparing raw strings in application code.
 */
public final class LdapPlaceholderPassword {

    private static final String VALUE = "LDAP_AUTH_REQUIRED_#@!$%^&*()";

    private LdapPlaceholderPassword() {
    }

    /** Value persisted for LDAP-only users (LDAP sync / login flow). */
    public static String storedValue() {
        return VALUE;
    }

    public static boolean matches(String candidate) {
        return candidate != null && VALUE.equals(candidate);
    }
}
