package rw.ikimina.savings.internal;

import java.time.LocalDate;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import rw.ikimina.shared.money.Money;

/**
 * A bucket's money terms. Changing any of them on an existing bucket needs a second officer
 * (owner decision, Phase 2), because they decide what every member owes.
 *
 * @param latePenaltyRule kept for the fines engine (Phase 4); not applied yet
 */
public record BucketTerms(
        @NotNull Boolean mandatory,
        @NotNull Money minimumContribution,
        @NotNull SavingsBucket.Frequency contributionFrequency,
        @NotNull Boolean withdrawable,
        LocalDate endDate,
        @Valid PenaltyRule latePenaltyRule) {

    /** Spec 6.3 late_penalty_rule: {type: FLAT|PERCENT, value, graceDays}. */
    public record PenaltyRule(@NotNull Type type, @NotNull String value, @NotNull Integer graceDays) {
        public enum Type { FLAT, PERCENT }
    }

    boolean differsFrom(BucketTerms other) {
        return !Objects.equals(mandatory, other.mandatory)
                || !minimumContribution.equals(other.minimumContribution)
                || contributionFrequency != other.contributionFrequency
                || !Objects.equals(withdrawable, other.withdrawable)
                || !Objects.equals(endDate, other.endDate)
                || !Objects.equals(latePenaltyRule, other.latePenaltyRule);
    }
}
