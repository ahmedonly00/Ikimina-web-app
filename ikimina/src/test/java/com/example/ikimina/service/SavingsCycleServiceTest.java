package com.example.ikimina.service;

import com.example.ikimina.enums.SavingsType;
import com.example.ikimina.model.MemberPayout;
import com.example.ikimina.model.Savings;
import com.example.ikimina.model.SavingsCycle;
import com.example.ikimina.model.SavingsCycle.CycleStatus;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.money.Money;
import com.example.ikimina.repository.MemberPayoutRepository;
import com.example.ikimina.repository.SavingsCycleRepository;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.SavingsRepository;
import com.example.ikimina.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * The reconciliation property for the payout engine: for any distribution of
 * contributions, the cycle totals must equal the sum of the per-member payout
 * rows exactly. This is the check that would have caught the double-based
 * drift, and it is the invariant to protect on every future change.
 */
class SavingsCycleServiceTest {

    @Mock private SavingsCycleRepository savingsCycleRepository;
    @Mock private MemberPayoutRepository memberPayoutRepository;
    @Mock private SavingsRepository savingsRepository;
    @Mock private UserRepository userRepository;
    @Mock private SavingsGroupRepository savingsGroupRepository;

    @InjectMocks private SavingsCycleService savingsCycleService;

    private SavingsGroup group;
    private SavingsCycle cycle;
    private final List<MemberPayout> savedPayouts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        savedPayouts.clear();

        group = new SavingsGroup();
        group.setId(1L);
        group.setName("Test Ikimina");

        cycle = new SavingsCycle();
        cycle.setId(10L);
        cycle.setSavingsGroup(group);
        cycle.setStartDate(LocalDate.now().minusMonths(6));
        cycle.setEndDate(LocalDate.now());
        cycle.setStatus(CycleStatus.COMPLETED);

        when(savingsCycleRepository.findById(10L)).thenReturn(Optional.of(cycle));
        when(savingsCycleRepository.save(any(SavingsCycle.class))).thenAnswer(i -> i.getArgument(0));
        when(memberPayoutRepository.findBySavingsCycleAndMember(any(), any())).thenReturn(Optional.empty());
        when(memberPayoutRepository.save(any(MemberPayout.class))).thenAnswer(i -> {
            MemberPayout p = i.getArgument(0);
            savedPayouts.add(p);
            return p;
        });
        when(memberPayoutRepository.findBySavingsCycle(any())).thenReturn(savedPayouts);
    }

    private User member(long id) {
        User u = new User();
        u.setId(id);
        u.setFirstName("Member");
        u.setLastName(String.valueOf(id));
        u.setMemberNumber("M" + id);
        return u;
    }

    private Savings saving(String amount, SavingsType type) {
        Savings s = new Savings();
        s.setAmount(new BigDecimal(amount));
        s.setType(type);
        s.setDate(LocalDate.now().minusDays(1));
        return s;
    }

    @Test
    @DisplayName("cycle totals equal the sum of member payouts, with cents")
    void cycleTotalsReconcileWithMemberPayouts() {
        List<User> members = List.of(member(1L), member(2L), member(3L));
        when(userRepository.findBySavingsGroupId(1L)).thenReturn(members);

        // Amounts chosen because they are exactly the shape double gets wrong.
        when(savingsRepository.findByUserIdAndDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(saving("0.10", SavingsType.UBWIZIGAME),
                                    saving("0.20", SavingsType.UBWIZIGAME),
                                    saving("50.05", SavingsType.INGOBOKA)));
        when(savingsRepository.findByUserIdAndDateBetween(eq(2L), any(), any()))
                .thenReturn(List.of(saving("1000.55", SavingsType.UBWIZIGAME),
                                    saving("0.01", SavingsType.INGOBOKA)));
        when(savingsRepository.findByUserIdAndDateBetween(eq(3L), any(), any()))
                .thenReturn(List.of(saving("33.33", SavingsType.UBWIZIGAME)));

        savingsCycleService.calculateCyclePayouts(10L);

        BigDecimal sumUbwizigame = Money.sum(savedPayouts.stream()
                .map(MemberPayout::getTotalUbwizigame).toList());
        BigDecimal sumIngoboka = Money.sum(savedPayouts.stream()
                .map(MemberPayout::getTotalIngoboka).toList());

        assertEquals(0, cycle.getTotalUbwizigameCollected().compareTo(sumUbwizigame),
                "cycle ubwizigame total must equal the sum of member rows");
        assertEquals(0, cycle.getTotalIngobokaCollected().compareTo(sumIngoboka),
                "cycle ingoboka total must equal the sum of member rows");

        // Exact expected values, not approximations.
        assertEquals("1034.18", cycle.getTotalUbwizigameCollected().toPlainString());
        assertEquals("50.06", cycle.getTotalIngobokaCollected().toPlainString());
    }

    @Test
    @DisplayName("reconciliation holds for randomised contribution patterns")
    void reconcilesForRandomisedPatterns() {
        Random random = new Random(20260910L);

        for (int trial = 0; trial < 50; trial++) {
            savedPayouts.clear();
            cycle.setStatus(CycleStatus.COMPLETED);
            cycle.setTotalUbwizigameCollected(BigDecimal.ZERO);
            cycle.setTotalIngobokaCollected(BigDecimal.ZERO);

            int memberCount = 1 + random.nextInt(12);
            List<User> members = new ArrayList<>();
            Map<Long, List<Savings>> byMember = new HashMap<>();

            for (int m = 1; m <= memberCount; m++) {
                User u = member(m);
                members.add(u);
                List<Savings> entries = new ArrayList<>();
                int n = random.nextInt(15);
                for (int e = 0; e < n; e++) {
                    BigDecimal amount = BigDecimal.valueOf(random.nextInt(500_00) + 1, 2);
                    entries.add(saving(amount.toPlainString(),
                            random.nextBoolean() ? SavingsType.UBWIZIGAME : SavingsType.INGOBOKA));
                }
                byMember.put((long) m, entries);
            }

            when(userRepository.findBySavingsGroupId(1L)).thenReturn(members);
            for (Map.Entry<Long, List<Savings>> e : byMember.entrySet()) {
                when(savingsRepository.findByUserIdAndDateBetween(eq(e.getKey()), any(), any()))
                        .thenReturn(e.getValue());
            }

            savingsCycleService.calculateCyclePayouts(10L);

            BigDecimal sumU = Money.sum(savedPayouts.stream().map(MemberPayout::getTotalUbwizigame).toList());
            BigDecimal sumI = Money.sum(savedPayouts.stream().map(MemberPayout::getTotalIngoboka).toList());

            assertEquals(0, cycle.getTotalUbwizigameCollected().compareTo(sumU),
                    "trial " + trial + ": ubwizigame must reconcile");
            assertEquals(0, cycle.getTotalIngobokaCollected().compareTo(sumI),
                    "trial " + trial + ": ingoboka must reconcile");
        }
    }

    @Test
    @DisplayName("payout equals the member ubwizigame total; ingoboka is not paid out")
    void payoutIsUbwizigameOnly() {
        when(userRepository.findBySavingsGroupId(1L)).thenReturn(List.of(member(1L)));
        when(savingsRepository.findByUserIdAndDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(saving("400.40", SavingsType.UBWIZIGAME),
                                    saving("99.99", SavingsType.INGOBOKA)));

        savingsCycleService.calculateCyclePayouts(10L);

        MemberPayout payout = savedPayouts.get(0);
        assertEquals(0, payout.getPayoutAmount().compareTo(payout.getTotalUbwizigame()));
        assertEquals("400.40", payout.getPayoutAmount().toPlainString());
        assertEquals("99.99", payout.getTotalIngoboka().toPlainString());
    }

    @Test
    @DisplayName("a member with no contributions gets a zero payout, not null")
    void zeroContributionsYieldZeroNotNull() {
        when(userRepository.findBySavingsGroupId(1L)).thenReturn(List.of(member(1L)));
        when(savingsRepository.findByUserIdAndDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        savingsCycleService.calculateCyclePayouts(10L);

        MemberPayout payout = savedPayouts.get(0);
        assertNotNull(payout.getPayoutAmount());
        assertEquals(0, payout.getPayoutAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, cycle.getTotalUbwizigameCollected().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("payouts cannot be calculated before the cycle is completed")
    void refusesIncompleteCycle() {
        cycle.setStatus(CycleStatus.ACTIVE);

        assertThrows(RuntimeException.class, () -> savingsCycleService.calculateCyclePayouts(10L));
        assertTrue(savedPayouts.isEmpty(), "no payout rows should be written");
    }
}