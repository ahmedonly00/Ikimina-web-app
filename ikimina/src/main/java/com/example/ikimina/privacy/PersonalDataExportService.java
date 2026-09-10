package com.example.ikimina.privacy;

import com.example.ikimina.audit.AuditService;
import com.example.ikimina.exception.ResourceNotFoundException;
import com.example.ikimina.ledger.LedgerEntry;
import com.example.ikimina.ledger.LedgerEntryRepository;
import com.example.ikimina.model.User;
import com.example.ikimina.money.Money;
import com.example.ikimina.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds a member's subject-access export.
 *
 * The ledger portion is capped rather than unbounded: a right of access is not
 * a reason to let one request read an arbitrarily large table. A member with
 * more history than the cap is told so, and can ask for the rest by date range.
 */
@Service
@Transactional(readOnly = true)
public class PersonalDataExportService {

    /** Enough for years of weekly contributions without an unbounded query. */
    private static final int MAX_LEDGER_LINES = 5000;

    private final UserRepository userRepository;
    private final ConsentRecordRepository consentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;

    @Value("${ikimina.privacy.policy-version:1.0}")
    private String policyVersion;

    public PersonalDataExportService(UserRepository userRepository,
                                     ConsentRecordRepository consentRepository,
                                     LedgerEntryRepository ledgerEntryRepository,
                                     AuditService auditService) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PersonalDataExport export(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        List<PersonalDataExport.GroupMembership> groups = user.getMemberGroups().stream()
                .map(g -> new PersonalDataExport.GroupMembership(
                        g.getId(),
                        g.getName(),
                        g.getAdmin() != null && userId.equals(g.getAdmin().getId())))
                .toList();

        List<PersonalDataExport.Consent> consents =
                consentRepository.findByUserIdOrderByRecordedAtDesc(userId).stream()
                        .map(c -> new PersonalDataExport.Consent(
                                c.getPurpose().name(), c.isGranted(),
                                c.getPolicyVersion(), c.getRecordedAt()))
                        .toList();

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByMemberIdOrderByOccurredOnDescIdDesc(userId, PageRequest.of(0, MAX_LEDGER_LINES))
                .getContent();

        List<PersonalDataExport.LedgerLine> ledger = entries.stream()
                .map(e -> new PersonalDataExport.LedgerLine(
                        e.getId(),
                        e.getEntryType().name(),
                        e.getDirection().name(),
                        e.getAmount(),
                        e.getCurrency(),
                        e.getOccurredOn(),
                        e.getDescription()))
                .toList();

        // Access requests are themselves processing of personal data, and the
        // audit trail is how a controller demonstrates it handled them.
        auditService.record("PRIVACY", "USER", userId, "EXPORT_PERSONAL_DATA",
                null, null, "Subject access request fulfilled ("
                        + ledger.size() + " ledger lines)");

        return new PersonalDataExport(
                LocalDateTime.now(),
                policyVersion,
                new PersonalDataExport.Profile(
                        user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(),
                        user.getEmail(), user.getPhoneNumber(), user.getMemberNumber(),
                        user.getRole() == null ? null : user.getRole().name(),
                        user.isActive(), user.getCreatedAt()),
                groups,
                consents,
                ledger,
                Money.of(ledgerEntryRepository.balanceForMember(userId)),
                ledger.size() >= MAX_LEDGER_LINES
                        ? "This export was capped at " + MAX_LEDGER_LINES + " ledger lines. Ask your "
                          + "group administrator for the remainder by date range."
                        : "Financial records are retained after account closure to meet the group's "
                          + "record-keeping obligations, in anonymised form once erasure is requested.");
    }
}
