package rw.ikimina.groups.internal;

import java.util.Objects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import rw.ikimina.shared.money.Money;

/**
 * Version 1 of a group's bylaws in structured form (spec 6.1 group_settings). Only settings
 * the spec already defines are here; later phases add theirs as new versions.
 *
 * <p>Financial parameters (spec 5.5: "financial params need 2 officers") are marked below;
 * changing any of them needs a second officer's confirmation.
 *
 * @param defaultLocale        language for group messages to people without their own preference
 * @param interestRecognition  financial - when loan interest counts as income (spec 7.2; default when paid, cash basis)
 * @param withdrawalsAllowed   financial - whether members may withdraw savings (spec 8.3)
 * @param withdrawalNoticeDays financial - notice required before a withdrawal or exit (spec 8.3)
 * @param exitFee              financial - fee charged when a member leaves (spec 8.3)
 */
public record GroupSettingsV1(
        @NotNull @Pattern(regexp = "en|rw") String defaultLocale,
        @NotNull InterestRecognition interestRecognition,
        @NotNull Boolean withdrawalsAllowed,
        @NotNull @Min(0) @Max(365) Integer withdrawalNoticeDays,
        @NotNull Money exitFee) {

    public static final int SCHEMA_VERSION = 1;

    public enum InterestRecognition { WHEN_PAID, WHEN_DUE }

    static GroupSettingsV1 defaults() {
        return new GroupSettingsV1("rw", InterestRecognition.WHEN_PAID, false, 0, Money.ZERO);
    }

    /** Whether going from {@code this} to {@code proposed} changes any financial parameter. */
    boolean financialDiffersFrom(GroupSettingsV1 proposed) {
        return interestRecognition != proposed.interestRecognition
                || !Objects.equals(withdrawalsAllowed, proposed.withdrawalsAllowed)
                || !Objects.equals(withdrawalNoticeDays, proposed.withdrawalNoticeDays)
                || !exitFee.equals(proposed.exitFee);
    }
}
