package com.example.ikimina.ledger;

import com.example.ikimina.money.Money;
import com.example.ikimina.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Real figures for a group's dashboard.
 *
 * Added because the dashboard was rendering hardcoded values - "$24,780",
 * "48 members", invented member names. On a financial product, a screen that
 * looks authoritative and shows invented numbers is worse than an empty one:
 * a group admin has no way to tell the difference.
 *
 * Every number here is derived from the ledger, so it reconciles with the
 * member balances by construction.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerSummaryController {

    private final LedgerService ledgerService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;

    public LedgerSummaryController(LedgerService ledgerService,
                                   LedgerEntryRepository ledgerEntryRepository,
                                   UserRepository userRepository) {
        this.ledgerService = ledgerService;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
    }

    /**
     * @param fundBalance      what the group currently holds, summed from the ledger
     * @param thisMonth        net movement in the current calendar month
     * @param activeMembers    members belonging to this group
     * @param contributingMembers members with at least one ledger entry
     * @param reconciled       whether the fund balance equals the sum of member
     *                         balances; false is a signal to investigate, not to hide
     */
    public record GroupSummary(Long groupId,
                               BigDecimal fundBalance,
                               BigDecimal thisMonth,
                               long activeMembers,
                               long contributingMembers,
                               boolean reconciled) {
    }

    @GetMapping("/groups/{groupId}/summary")
    @PreAuthorize("@savingsGroupSecurity.canAdministerGroup(authentication, #groupId) "
            + "or @savingsGroupSecurity.isGroupMember(authentication, #groupId)")
    public ResponseEntity<GroupSummary> summary(@PathVariable Long groupId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());

        var reconciliation = ledgerService.reconcile(groupId);
        var memberBalances = ledgerService.memberBalances(groupId);

        return ResponseEntity.ok(new GroupSummary(
                groupId,
                reconciliation.fundBalance(),
                Money.of(ledgerEntryRepository.balanceForGroupBetween(groupId, monthStart, today)),
                userRepository.findActiveBySavingsGroupId(groupId).size(),
                memberBalances.size(),
                reconciliation.balanced()));
    }
}
