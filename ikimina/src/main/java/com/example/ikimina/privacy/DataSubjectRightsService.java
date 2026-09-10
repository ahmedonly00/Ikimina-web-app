package com.example.ikimina.privacy;

import com.example.ikimina.audit.AuditService;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.exception.ResourceNotFoundException;
import com.example.ikimina.ledger.LedgerEntryRepository;
import com.example.ikimina.model.User;
import com.example.ikimina.payments.InboundPaymentEventRepository;
import com.example.ikimina.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data-subject rights under Law 058/2021.
 *
 * <h2>Why erasure is not deletion</h2>
 *
 * A member has a right to erasure, and the group has an obligation to keep
 * financial records. Both are real, and they conflict.
 *
 * The resolution is to erase the personal data and keep the financial record:
 * the member's name, email, phone number and identifiers are overwritten, while
 * ledger entries stay exactly as they were, still attached to the now-anonymous
 * member id. The books still balance and still reconcile, and the person is no
 * longer identifiable from them.
 *
 * Deleting the ledger rows instead would corrupt every group total that member
 * ever contributed to, and would itself breach the retention obligation. This is
 * the standard approach for regulated financial records; it is written up in
 * docs/PRIVACY.md.
 *
 * Anonymisation is irreversible by design. If it could be undone it would not
 * be erasure.
 */
@Service
public class DataSubjectRightsService {

    private static final Logger log = LoggerFactory.getLogger(DataSubjectRightsService.class);

    private static final String ANONYMISED = "[erased]";

    private final UserRepository userRepository;
    private final ConsentRecordRepository consentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final InboundPaymentEventRepository paymentEventRepository;
    private final AuditService auditService;

    /**
     * Whether a member may be erased while they still hold value in a group.
     * Default false: erasing someone mid-cycle leaves the group unable to
     * explain a payout to a person who no longer exists in its records.
     */
    @Value("${ikimina.privacy.allow-erasure-with-balance:false}")
    private boolean allowErasureWithBalance;

    public DataSubjectRightsService(UserRepository userRepository,
                                    ConsentRecordRepository consentRepository,
                                    LedgerEntryRepository ledgerEntryRepository,
                                    InboundPaymentEventRepository paymentEventRepository,
                                    AuditService auditService) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.auditService = auditService;
    }

    /**
     * Erases a member's personal data, preserving their financial history.
     *
     * @return the pseudonym the records now show, so the member can be told
     */
    @Transactional
    public String erase(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getErasedAt() != null) {
            throw new BusinessRuleException("This member's data has already been erased");
        }

        if (!allowErasureWithBalance) {
            var balance = ledgerEntryRepository.balanceForMember(userId);
            if (balance != null && balance.signum() != 0) {
                throw new BusinessRuleException(
                        "Member still holds a balance in a group. Settle or pay out before erasure, "
                        + "or set ikimina.privacy.allow-erasure-with-balance to override deliberately.");
            }
        }

        // A stable pseudonym keeps the ledger readable ("erased-a1b2c3d4") rather
        // than showing a blank where a person used to be.
        String pseudonym = "erased-" + UUID.randomUUID().toString().substring(0, 8);

        user.setFirstName(ANONYMISED);
        user.setLastName(ANONYMISED);
        user.setFullName(ANONYMISED);
        user.setUsername(pseudonym);
        // Unique columns need unique placeholders, not one shared constant.
        user.setEmail(pseudonym + "@erased.invalid");
        user.setPhoneNumber("+000000000000");
        user.setActive(false);
        user.setErasedAt(LocalDateTime.now());
        userRepository.save(user);

        // Consent history goes with the person: keeping it would keep proving
        // facts about someone who asked to be forgotten.
        consentRepository.deleteByUserId(userId);

        // The payer's phone number is the only personal data on a payment event;
        // amounts and references stay as financial evidence.
        int scrubbed = paymentEventRepository.scrubPayerMsisdnForMember(userId);

        log.info("Erased personal data for user {} ({} payment events scrubbed)", userId, scrubbed);

        auditService.record("PRIVACY", "USER", userId, "ERASE_PERSONAL_DATA",
                null, pseudonym,
                "Right to erasure exercised. Financial records retained under the group's "
                + "record-keeping obligation. Reason: " + (reason == null ? "not stated" : reason));

        return pseudonym;
    }

    /**
     * Records a consent decision. Append-only: a withdrawal is a new row, so
     * what was agreed and when stays demonstrable.
     */
    @Transactional
    public ConsentRecord recordConsent(Long userId,
                                       ConsentPurpose purpose,
                                       boolean granted,
                                       String policyVersion,
                                       String ipAddress,
                                       String userAgent) {
        if (!granted && purpose == ConsentPurpose.SERVICE_OPERATION) {
            throw new BusinessRuleException(
                    "Consent to operate your savings account cannot be withdrawn while you are a "
                    + "group member. Ask a group admin to remove you from the group instead.");
        }

        ConsentRecord record = new ConsentRecord();
        record.setUserId(userId);
        record.setPurpose(purpose);
        record.setGranted(granted);
        record.setPolicyVersion(policyVersion);
        record.setIpAddress(ipAddress);
        record.setUserAgent(userAgent == null || userAgent.length() <= 500
                ? userAgent : userAgent.substring(0, 500));
        record.setRecordedAt(LocalDateTime.now());

        ConsentRecord saved = consentRepository.save(record);

        auditService.record("PRIVACY", "CONSENT", saved.getId(),
                granted ? "GRANT" : "WITHDRAW", null, purpose.name(),
                "Consent " + (granted ? "granted" : "withdrawn") + " for " + purpose
                        + " (policy " + policyVersion + ")");

        return saved;
    }

    public boolean hasConsent(Long userId, ConsentPurpose purpose) {
        return consentRepository.findCurrent(userId, purpose)
                .map(ConsentRecord::isGranted)
                .orElse(false);
    }
}
