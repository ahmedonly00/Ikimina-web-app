package rw.ikimina.identity.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.phone.PhoneNumber;

/**
 * Spec 16.1: minimum length 8, checked against a common-password list, and no
 * composition-rule theatre. Also refuses the person's own phone number, the first thing
 * anyone would guess. The upper bound keeps Argon2 from being fed megabytes per attempt.
 */
@Component
class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;

    private final Set<String> common;

    PasswordPolicy() {
        this.common = load("security/common-passwords.txt");
    }

    void check(String password, PhoneNumber phone) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK);
        }
        String lower = password.toLowerCase(Locale.ROOT);
        String nationalNumber = phone.e164().substring(4);       // 7XXXXXXXX
        if (common.contains(lower) || lower.contains(nationalNumber)) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK);
        }
    }

    int commonPasswordCount() {
        return common.size();
    }

    private static Set<String> load(String resource) {
        Set<String> passwords = new HashSet<>();
        try (InputStream in = new ClassPathResource(resource).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    passwords.add(line.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Common-password list missing: " + resource, e);
        }
        if (passwords.isEmpty()) {
            throw new IllegalStateException("Common-password list is empty: " + resource);
        }
        return Set.copyOf(passwords);
    }
}
