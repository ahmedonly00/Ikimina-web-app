package rw.ikimina.savings.internal;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.groups.GroupAccess;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.RequiresRecentAuthentication;

/**
 * Withdrawal endpoints (spec 17.3 "POST /withdrawals (when allowed)"; owner decisions, Phase 3c).
 * Deciding uses LOAN_APPROVE - the President and the Treasurer, the officers spec 5.5 trusts to
 * approve money leaving the group; paying out uses CONTRIBUTION_RECORD - the Treasurer, who records
 * savings money in and out.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
class WithdrawalController {

    record WithdrawalRequest(@NotNull UUID bucketId, @NotNull Money amount, @Size(max = 500) String reason) {
    }

    record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    record PayRequest(@NotNull SavingsTransaction.Method method, @Size(max = 100) String externalRef, LocalDate businessDate) {
    }

    private final WithdrawalService withdrawals;
    private final WithdrawalQueries queries;

    WithdrawalController(WithdrawalService withdrawals, WithdrawalQueries queries) {
        this.withdrawals = withdrawals;
        this.queries = queries;
    }

    /** A member asks to withdraw their own savings. */
    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @GroupAccess.AnyMember
    WithdrawalQueries.WithdrawalView request(@PathVariable UUID groupId, @Valid @RequestBody WithdrawalRequest request) {
        return withdrawals.request(request.bucketId(), request.amount(), request.reason());
    }

    @GetMapping("/withdrawals")
    @GroupAccess.AnyMember
    WithdrawalQueries.PageView<WithdrawalQueries.WithdrawalView> list(@PathVariable UUID groupId,
                                                                      @RequestParam(required = false) SavingsWithdrawal.Status status,
                                                                      @RequestParam(required = false) UUID memberId,
                                                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.list(memberId, status, page, size);
    }

    @GetMapping("/withdrawals/{withdrawalId}")
    @GroupAccess.AnyMember
    WithdrawalQueries.WithdrawalView get(@PathVariable UUID groupId, @PathVariable UUID withdrawalId) {
        return queries.get(withdrawalId);
    }

    @PostMapping("/withdrawals/{withdrawalId}/approve")
    @PreAuthorize("@perm.has(#groupId, 'LOAN_APPROVE')")
    @RequiresRecentAuthentication
    WithdrawalQueries.WithdrawalView approve(@PathVariable UUID groupId, @PathVariable UUID withdrawalId) {
        return withdrawals.approve(withdrawalId);
    }

    @PostMapping("/withdrawals/{withdrawalId}/reject")
    @PreAuthorize("@perm.has(#groupId, 'LOAN_APPROVE')")
    WithdrawalQueries.WithdrawalView reject(@PathVariable UUID groupId, @PathVariable UUID withdrawalId,
                                            @Valid @RequestBody ReasonRequest request) {
        return withdrawals.reject(withdrawalId, request.reason());
    }

    /** Only the member who asked, before the payout. */
    @PostMapping("/withdrawals/{withdrawalId}/cancel")
    @GroupAccess.AnyMember
    WithdrawalQueries.WithdrawalView cancel(@PathVariable UUID groupId, @PathVariable UUID withdrawalId) {
        return withdrawals.cancel(withdrawalId);
    }

    /** Requires an Idempotency-Key header (H7). */
    @PostMapping("/withdrawals/{withdrawalId}/pay")
    @PreAuthorize("@perm.has(#groupId, 'CONTRIBUTION_RECORD')")
    WithdrawalQueries.WithdrawalView pay(@PathVariable UUID groupId, @PathVariable UUID withdrawalId,
                                         @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                         @Valid @RequestBody PayRequest request) {
        return withdrawals.pay(withdrawalId, idempotencyKey, request.method(), request.externalRef(), request.businessDate());
    }
}
