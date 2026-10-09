package rw.ikimina.shared.phone;

import java.util.Objects;
import java.util.regex.Pattern;

import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;

/**
 * A Rwandan mobile number in E.164 form, {@code +2507XXXXXXXX} - the primary
 * identifier of a person (spec 16.1).
 *
 * <p>{@link #parse(String)} accepts the forms people actually type ({@code 0788 123 456},
 * {@code 250788123456}, {@code +250 788-123-456}) and normalises them. Operator prefixes
 * are deliberately not checked: which {@code 07x} ranges are in use is [VERIFY] and changes
 * over time, so any {@code 07} followed by eight digits is accepted.
 */
public record PhoneNumber(String e164) {

    private static final Pattern E164_RWANDA_MOBILE = Pattern.compile("\\+2507[0-9]{8}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");

    public PhoneNumber {
        Objects.requireNonNull(e164, "e164");
        if (!E164_RWANDA_MOBILE.matcher(e164).matches()) {
            throw new ApiException(ErrorCode.INVALID_PHONE_NUMBER);
        }
    }

    /** @throws ApiException {@link ErrorCode#INVALID_PHONE_NUMBER} if the text is not a Rwandan mobile number */
    public static PhoneNumber parse(String raw) {
        if (raw == null || raw.length() > 32) {
            throw new ApiException(ErrorCode.INVALID_PHONE_NUMBER);
        }
        String digits = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (digits.startsWith("07") && digits.length() == 10) {
            digits = "+250" + digits.substring(1);
        } else if (digits.startsWith("2507")) {
            digits = "+" + digits;
        }
        return new PhoneNumber(digits);
    }

    /** For showing a number to people who should not see it in full, e.g. {@code +2507******56} (spec 16.7). */
    public String masked() {
        return e164.substring(0, 5) + "******" + e164.substring(11);
    }

    @Override
    public String toString() {
        return masked();
    }
}
