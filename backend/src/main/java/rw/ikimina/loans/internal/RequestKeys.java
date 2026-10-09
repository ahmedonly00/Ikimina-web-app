package rw.ikimina.loans.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;

/** Idempotency keys and request fingerprints for the loan endpoints that move money (H7). */
final class RequestKeys {

    /** What the API accepts from clients, as for contributions. */
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,80}");

    private RequestKeys() {
    }

    /** @throws ApiException VALIDATION_FAILED when the key is missing or malformed */
    static String require(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return idempotencyKey;
    }

    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
