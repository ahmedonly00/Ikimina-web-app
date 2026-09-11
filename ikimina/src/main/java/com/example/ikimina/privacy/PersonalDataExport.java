package com.example.ikimina.privacy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the platform holds about one member, in a portable shape.
 *
 * Law 058/2021 gives a data subject the right to access their data and to
 * receive it in a usable form. "Usable" is the operative word: a screenshot of
 * a dashboard is not portability, so this serialises to plain JSON a member can
 * keep, read, or hand to another provider.
 *
 * It deliberately includes the member's financial history. Their contributions
 * are their data as much as their phone number is, and a member asking what the
 * group holds about them is usually asking about the money.
 */
public record PersonalDataExport(
        LocalDateTime exportedAt,
        String policyVersion,
        Profile profile,
        List<GroupMembership> groups,
        List<Consent> consents,
        List<LedgerLine> ledger,
        BigDecimal currentBalance,
        String retentionNotice) {

    public record Profile(
            Long id,
            String username,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String memberNumber,
            String role,
            boolean active,
            LocalDateTime createdAt) {
    }

    public record GroupMembership(Long groupId, String groupName, boolean isAdmin) {
    }

    public record Consent(String purpose, boolean granted, String policyVersion, LocalDateTime recordedAt) {
    }

    /** One money movement, as it appears in the ledger. */
    public record LedgerLine(
            Long id,
            String entryType,
            String direction,
            BigDecimal amount,
            String currency,
            LocalDate occurredOn,
            String description) {
    }
}
