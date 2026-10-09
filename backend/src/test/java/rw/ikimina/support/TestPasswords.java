package rw.ikimina.support;

import java.util.UUID;

/**
 * Passwords for test accounts, generated at run time. Tests never contain password literals,
 * so secret scanners have nothing to flag and nobody mistakes a fixture for a credential.
 */
public final class TestPasswords {

    private TestPasswords() {
    }

    /** Long, random and absent from the common-password list, so it passes the password policy. */
    public static String strong() {
        return "t-" + UUID.randomUUID();
    }
}
