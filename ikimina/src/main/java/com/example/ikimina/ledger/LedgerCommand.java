package com.example.ikimina.ledger;

import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A request to append one ledger entry.
 *
 * Validated here rather than on the entity so an invalid movement can never
 * reach the table: amounts must be strictly positive (direction carries the
 * sign) and an idempotency key is mandatory.
 */
public record LedgerCommand(
        LedgerEntryType entryType,
        LedgerDirection direction,
        BigDecimal amount,
        String currency,
        Long groupId,
        Long memberId,
        LocalDate occurredOn,
        String idempotencyKey,
        String sourceType,
        Long sourceId,
        Long createdBy,
        String description) {

    public void validate() {
        if (entryType == null) {
            throw new BusinessRuleException("Ledger entry type is required");
        }
        if (direction == null) {
            throw new BusinessRuleException("Ledger direction is required");
        }
        if (!Money.isPositive(amount)) {
            throw new BusinessRuleException("Ledger amount must be greater than zero");
        }
        if (groupId == null) {
            throw new BusinessRuleException("Ledger entry must belong to a group");
        }
        if (occurredOn == null) {
            throw new BusinessRuleException("Ledger entry needs a business date");
        }
        if (occurredOn.isAfter(LocalDate.now())) {
            throw new BusinessRuleException("Ledger entry cannot be dated in the future");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessRuleException("Ledger writes require an idempotency key");
        }
        if (createdBy == null) {
            throw new BusinessRuleException("Ledger entry must record who created it");
        }
    }
}
