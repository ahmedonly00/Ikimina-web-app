package rw.ikimina.savings.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.groups.GroupAccess;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.RequiresRecentAuthentication;

/** Savings endpoints (spec 17.3). */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
class SavingsController {

    record CreateBucketRequest(@NotBlank @Size(max = 200) String name,
                               @Size(max = 1000) String description,
                               @NotNull SavingsBucket.Type type,
                               @NotNull SavingsBucket.CycleType cycleType,
                               @NotNull LocalDate startDate,
                               @NotNull @Valid BucketTerms terms) {
    }

    /** {@code version} is the bucket version that was read; anything left out is unchanged. */
    record UpdateBucketRequest(@NotNull Long version,
                               @Size(min = 1, max = 200) String name,
                               @Size(max = 1000) String description,
                               SavingsBucket.Status status,
                               @Valid BucketTerms terms) {
    }

    record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    record ContributionRequest(@NotNull UUID memberId,
                               @NotNull UUID bucketId,
                               @NotNull Money amount,
                               @NotNull SavingsTransaction.Method method,
                               @Size(max = 100) String externalRef,
                               UUID obligationId,
                               LocalDate businessDate) {
    }

    private final BucketService buckets;
    private final ContributionService contributions;
    private final SavingsQueries queries;

    SavingsController(BucketService buckets, ContributionService contributions, SavingsQueries queries) {
        this.buckets = buckets;
        this.contributions = contributions;
        this.queries = queries;
    }

    // --- buckets ------------------------------------------------------------------------

    @GetMapping("/buckets")
    @GroupAccess.AnyMember
    List<BucketService.BucketView> buckets(@PathVariable UUID groupId) {
        return buckets.list();
    }

    @GetMapping("/buckets/{bucketId}")
    @GroupAccess.AnyMember
    BucketService.BucketView bucket(@PathVariable UUID groupId, @PathVariable UUID bucketId) {
        return buckets.get(bucketId);
    }

    @PostMapping("/buckets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    BucketService.BucketView createBucket(@PathVariable UUID groupId, @Valid @RequestBody CreateBucketRequest request) {
        return buckets.create(new BucketService.NewBucket(request.name(), request.description(), request.type(), request.cycleType(),
                request.startDate(), request.terms()));
    }

    /** 200 when applied; 202 when it changes money terms and now awaits a second officer. */
    @PatchMapping("/buckets/{bucketId}")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    ResponseEntity<BucketService.BucketView> updateBucket(@PathVariable UUID groupId, @PathVariable UUID bucketId,
                                                         @Valid @RequestBody UpdateBucketRequest request) {
        BucketService.UpdateResult result = buckets.update(bucketId, request.version(), request.name(), request.description(),
                request.status(), request.terms());
        return ResponseEntity.status(result.applied() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result.view());
    }

    @PostMapping("/buckets/{bucketId}/changes/{changeId}/confirm")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    @RequiresRecentAuthentication
    BucketService.BucketView confirmBucketChange(@PathVariable UUID groupId, @PathVariable UUID bucketId, @PathVariable UUID changeId) {
        return buckets.confirm(bucketId, changeId).orElseThrow(() -> new ApiException(ErrorCode.BUCKET_CHANGE_STALE));
    }

    @PostMapping("/buckets/{bucketId}/changes/{changeId}/reject")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    BucketService.BucketView rejectBucketChange(@PathVariable UUID groupId, @PathVariable UUID bucketId, @PathVariable UUID changeId,
                                                @Valid @RequestBody ReasonRequest request) {
        return buckets.reject(bucketId, changeId, request.reason());
    }

    // --- contributions --------------------------------------------------------------------

    /** Requires an Idempotency-Key header: retrying with the same key never records twice (H7). */
    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has(#groupId, 'CONTRIBUTION_RECORD')")
    ContributionService.ContributionView contribute(@PathVariable UUID groupId,
                                                    @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                    @Valid @RequestBody ContributionRequest request) {
        return contributions.record(idempotencyKey, new ContributionService.ContributionCommand(request.memberId(), request.bucketId(),
                request.amount(), request.method(), request.externalRef(), request.obligationId(), request.businessDate()));
    }

    // --- reading --------------------------------------------------------------------------

    @GetMapping("/members/{memberId}/balances")
    @GroupAccess.AnyMember
    SavingsQueries.Balances balances(@PathVariable UUID groupId, @PathVariable UUID memberId) {
        return queries.balances(memberId);
    }

    @GetMapping("/members/{memberId}/transactions")
    @GroupAccess.AnyMember
    SavingsQueries.PageView<SavingsQueries.TransactionView> transactions(@PathVariable UUID groupId, @PathVariable UUID memberId,
                                                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                         @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.transactions(memberId, page, size);
    }

    @GetMapping("/members/{memberId}/statement")
    @GroupAccess.AnyMember
    SavingsQueries.Statement statement(@PathVariable UUID groupId, @PathVariable UUID memberId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) UUID bucketId) {
        return queries.statement(memberId, from, to, bucketId);
    }

    @GetMapping("/obligations")
    @GroupAccess.AnyMember
    SavingsQueries.PageView<SavingsQueries.ObligationView> obligations(@PathVariable UUID groupId,
                                                                       @RequestParam(required = false) UUID bucketId,
                                                                       @RequestParam(required = false) UUID memberId,
                                                                       @RequestParam(required = false) Obligation.Status status,
                                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                       @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.obligations(bucketId, memberId, status, page, size);
    }
}
