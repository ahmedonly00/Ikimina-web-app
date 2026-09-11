package com.example.ikimina.privacy;

import com.example.ikimina.audit.AuditService;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.ledger.LedgerEntryRepository;
import com.example.ikimina.model.User;
import com.example.ikimina.payments.InboundPaymentEventRepository;
import com.example.ikimina.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Erasure has to satisfy two obligations that pull against each other: the
 * right to be forgotten, and the duty to keep financial books. These tests pin
 * the compromise - personal data goes, financial records stay.
 */
class DataSubjectRightsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ConsentRecordRepository consentRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private InboundPaymentEventRepository paymentEventRepository;
    @Mock private AuditService auditService;

    private DataSubjectRightsService service;
    private User member;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DataSubjectRightsService(userRepository, consentRepository,
                ledgerEntryRepository, paymentEventRepository, auditService);
        ReflectionTestUtils.setField(service, "allowErasureWithBalance", false);

        member = new User();
        member.setId(42L);
        member.setUsername("jean");
        member.setFirstName("Jean");
        member.setLastName("Uwimana");
        member.setEmail("jean@example.com");
        member.setPhoneNumber("+250788123456");
        member.setMemberNumber("M042");
        member.setActive(true);

        when(userRepository.findById(42L)).thenReturn(Optional.of(member));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(ledgerEntryRepository.balanceForMember(42L)).thenReturn(BigDecimal.ZERO);
        when(paymentEventRepository.scrubPayerMsisdnForMember(anyLong())).thenReturn(2);
    }

    @Test
    @DisplayName("erasure removes identifying data but never touches the ledger")
    void erasesPersonalDataOnly() {
        String pseudonym = service.erase(42L, "member request");

        assertEquals("[erased]", member.getFirstName());
        assertEquals("[erased]", member.getLastName());
        assertTrue(member.getEmail().endsWith("@erased.invalid"));
        assertNotEquals("+250788123456", member.getPhoneNumber());
        assertFalse(member.isActive());
        assertNotNull(member.getErasedAt());
        assertTrue(pseudonym.startsWith("erased-"));

        // The whole point: financial history is retained, not deleted.
        verify(ledgerEntryRepository, never()).deleteAll();
        verify(ledgerEntryRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("the pseudonym is unique per member so unique columns do not collide")
    void pseudonymIsUnique() {
        String first = service.erase(42L, null);

        User other = new User();
        other.setId(43L);
        other.setFirstName("Marie");
        other.setPhoneNumber("+250788999999");
        when(userRepository.findById(43L)).thenReturn(Optional.of(other));
        when(ledgerEntryRepository.balanceForMember(43L)).thenReturn(BigDecimal.ZERO);

        String second = service.erase(43L, null);

        assertNotEquals(first, second, "two erased members must not share an email or username");
    }

    @Test
    @DisplayName("the payer phone number on payment events is scrubbed too")
    void scrubsPaymentEvidence() {
        service.erase(42L, null);
        verify(paymentEventRepository).scrubPayerMsisdnForMember(42L);
    }

    @Test
    @DisplayName("consent history is removed with the person")
    void removesConsentHistory() {
        service.erase(42L, null);
        verify(consentRepository).deleteByUserId(42L);
    }

    @Test
    @DisplayName("a member holding a balance is not erased by default")
    void refusesErasureWithOutstandingBalance() {
        when(ledgerEntryRepository.balanceForMember(42L)).thenReturn(new BigDecimal("5000.00"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.erase(42L, null));
        assertTrue(ex.getMessage().contains("balance"));

        assertEquals("Jean", member.getFirstName());
        assertNull(member.getErasedAt());
    }

    @Test
    @DisplayName("the balance guard can be overridden deliberately")
    void allowsErasureWithBalanceWhenConfigured() {
        ReflectionTestUtils.setField(service, "allowErasureWithBalance", true);
        when(ledgerEntryRepository.balanceForMember(42L)).thenReturn(new BigDecimal("5000.00"));

        assertDoesNotThrow(() -> service.erase(42L, "court order"));
        assertEquals("[erased]", member.getFirstName());
    }

    @Test
    @DisplayName("erasing twice is refused rather than generating a second pseudonym")
    void refusesSecondErasure() {
        service.erase(42L, null);
        assertThrows(BusinessRuleException.class, () -> service.erase(42L, null));
    }

    @Test
    @DisplayName("erasure is audited")
    void erasureIsAudited() {
        service.erase(42L, "member request");
        verify(auditService).record(eq("PRIVACY"), eq("USER"), eq(42L),
                eq("ERASE_PERSONAL_DATA"), any(), any(), any());
    }

    @Test
    @DisplayName("consent to operate the account cannot be withdrawn while a member")
    void serviceOperationConsentCannotBeWithdrawn() {
        assertThrows(BusinessRuleException.class, () ->
                service.recordConsent(42L, ConsentPurpose.SERVICE_OPERATION, false, "1.0", null, null));
    }

    @Test
    @DisplayName("optional consent can be granted and withdrawn, append-only")
    void optionalConsentIsAppendOnly() {
        when(consentRepository.save(any(ConsentRecord.class))).thenAnswer(i -> {
            ConsentRecord r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        ConsentRecord granted = service.recordConsent(
                42L, ConsentPurpose.SMS_NOTIFICATIONS, true, "1.0", "127.0.0.1", "agent");
        assertTrue(granted.isGranted());

        ConsentRecord withdrawn = service.recordConsent(
                42L, ConsentPurpose.SMS_NOTIFICATIONS, false, "1.0", "127.0.0.1", "agent");
        assertFalse(withdrawn.isGranted());

        verify(consentRepository, times(2)).save(any(ConsentRecord.class));
        verify(consentRepository, never()).delete(any(ConsentRecord.class));
    }

    @Test
    @DisplayName("an over-long user agent is truncated rather than failing the write")
    void truncatesLongUserAgent() {
        when(consentRepository.save(any(ConsentRecord.class))).thenAnswer(i -> i.getArgument(0));
        String huge = new String(new char[2000]).replace("\0", "x");

        ConsentRecord record = service.recordConsent(
                42L, ConsentPurpose.ANALYTICS, true, "1.0", "127.0.0.1", huge);

        assertEquals(500, record.getUserAgent().length());
    }
}