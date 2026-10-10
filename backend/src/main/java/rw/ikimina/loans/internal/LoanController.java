package rw.ikimina.loans.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import rw.ikimina.loans.internal.LoanStateMachine.Status;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.money.Money;
import rw.ikimina.shared.security.RequiresRecentAuthentication;

/** Loan endpoints (spec 17.4). */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
class LoanController {

    record CreateProductRequest(@NotBlank @Size(max = 120) String name, @NotNull @Valid ProductTerms terms) {
    }

    /** {@code version} is the product version that was read; anything left out is unchanged. */
    record UpdateProductRequest(@NotNull Long version, @Size(min = 1, max = 120) String name, LoanProduct.Status status,
                                @Valid ProductTerms terms) {
    }

    record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    record LoanRequest(@NotNull UUID productId, @NotNull Money amount, @NotNull @Min(1) @Max(120) Integer termMonths,
                       @Size(max = 1000) String purpose) {
    }

    record ApproveRequest(@Size(max = 1000) String comment) {
    }

    record DisburseRequest(@NotNull LoanDisbursement.Method method, @Size(max = 100) String externalRef, LocalDate businessDate) {
    }

    record RepaymentRequest(@NotNull Money amount, @NotNull LoanRepayment.Method method, @Size(max = 100) String externalRef,
                            LocalDate businessDate) {
    }

    private final LoanProductService products;
    private final LoanService loans;
    private final DisbursementService disbursements;
    private final RepaymentService repayments;
    private final LoanQueries queries;

    LoanController(LoanProductService products, LoanService loans, DisbursementService disbursements, RepaymentService repayments,
                   LoanQueries queries) {
        this.products = products;
        this.loans = loans;
        this.disbursements = disbursements;
        this.repayments = repayments;
        this.queries = queries;
    }

    // --- products ---------------------------------------------------------------------------

    @GetMapping("/loan-products")
    @GroupAccess.AnyMember
    List<LoanProductService.ProductView> products(@PathVariable UUID groupId) {
        return products.list();
    }

    @GetMapping("/loan-products/{productId}")
    @GroupAccess.AnyMember
    LoanProductService.ProductView product(@PathVariable UUID groupId, @PathVariable UUID productId) {
        return products.get(productId);
    }

    @PostMapping("/loan-products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    LoanProductService.ProductView createProduct(@PathVariable UUID groupId, @Valid @RequestBody CreateProductRequest request) {
        return products.create(request.name(), request.terms());
    }

    /** 200 when applied; 202 when it changes money terms and now awaits a second officer. */
    @PatchMapping("/loan-products/{productId}")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    ResponseEntity<LoanProductService.ProductView> updateProduct(@PathVariable UUID groupId, @PathVariable UUID productId,
                                                                 @Valid @RequestBody UpdateProductRequest request) {
        LoanProductService.UpdateResult result = products.update(productId, request.version(), request.name(), request.status(),
                request.terms());
        return ResponseEntity.status(result.applied() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result.view());
    }

    @PostMapping("/loan-products/{productId}/changes/{changeId}/confirm")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    @RequiresRecentAuthentication
    LoanProductService.ProductView confirmProductChange(@PathVariable UUID groupId, @PathVariable UUID productId,
                                                        @PathVariable UUID changeId) {
        return products.confirm(productId, changeId).orElseThrow(() -> new ApiException(ErrorCode.LOAN_PRODUCT_CHANGE_STALE));
    }

    @PostMapping("/loan-products/{productId}/changes/{changeId}/reject")
    @PreAuthorize("@perm.has(#groupId, 'SETTINGS_EDIT')")
    LoanProductService.ProductView rejectProductChange(@PathVariable UUID groupId, @PathVariable UUID productId,
                                                       @PathVariable UUID changeId, @Valid @RequestBody ReasonRequest request) {
        return products.reject(productId, changeId, request.reason());
    }

    // --- loans ------------------------------------------------------------------------------

    /** A member asks for a loan for themselves. */
    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has(#groupId, 'LOAN_REQUEST')")
    LoanQueries.LoanView request(@PathVariable UUID groupId, @Valid @RequestBody LoanRequest request) {
        return loans.request(request.productId(), request.amount(), request.termMonths(), request.purpose());
    }

    @GetMapping("/loans")
    @GroupAccess.AnyMember
    LoanQueries.PageView<LoanQueries.LoanView> loans(@PathVariable UUID groupId,
                                                     @RequestParam(required = false) Status status,
                                                     @RequestParam(required = false) UUID memberId,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.list(memberId, status, page, size);
    }

    @GetMapping("/loans/{loanId}")
    @GroupAccess.AnyMember
    LoanQueries.LoanView loan(@PathVariable UUID groupId, @PathVariable UUID loanId) {
        return queries.get(loanId);
    }

    @GetMapping("/loans/{loanId}/schedule")
    @GroupAccess.AnyMember
    LoanQueries.ScheduleView schedule(@PathVariable UUID groupId, @PathVariable UUID loanId) {
        return queries.schedule(loanId);
    }

    /**
     * Who may approve is decided per loan (spec 9.3: the Secretary stands in for a borrowing
     * officer), so access is membership plus {@link ApprovalPolicy}, not a matrix permission.
     */
    @PostMapping("/loans/{loanId}/approve")
    @GroupAccess.AnyMember
    LoanQueries.LoanView approve(@PathVariable UUID groupId, @PathVariable UUID loanId,
                                 @Valid @RequestBody(required = false) ApproveRequest request) {
        return loans.approve(loanId, request == null ? null : request.comment());
    }

    @PostMapping("/loans/{loanId}/reject")
    @GroupAccess.AnyMember
    LoanQueries.LoanView reject(@PathVariable UUID groupId, @PathVariable UUID loanId, @Valid @RequestBody ReasonRequest request) {
        return loans.reject(loanId, request.reason());
    }

    /** Only the borrower, before the money is handed over. */
    @PostMapping("/loans/{loanId}/cancel")
    @GroupAccess.AnyMember
    LoanQueries.LoanView cancel(@PathVariable UUID groupId, @PathVariable UUID loanId) {
        return loans.cancel(loanId);
    }

    /** Requires an Idempotency-Key header (H7). */
    @PostMapping("/loans/{loanId}/disburse")
    @PreAuthorize("@perm.has(#groupId, 'LOAN_DISBURSE')")
    LoanQueries.LoanView disburse(@PathVariable UUID groupId, @PathVariable UUID loanId,
                                  @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                  @Valid @RequestBody DisburseRequest request) {
        return disbursements.disburse(loanId, idempotencyKey, request.method(), request.externalRef(), request.businessDate());
    }

    /** Requires an Idempotency-Key header (H7). */
    @PostMapping("/loans/{loanId}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has(#groupId, 'REPAYMENT_RECORD')")
    LoanQueries.LoanView repay(@PathVariable UUID groupId, @PathVariable UUID loanId,
                               @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                               @Valid @RequestBody RepaymentRequest request) {
        return repayments.record(loanId, idempotencyKey, request.amount(), request.method(), request.externalRef(),
                request.businessDate());
    }
}
