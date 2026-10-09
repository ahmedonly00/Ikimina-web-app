package rw.ikimina.loans.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.security.StepUp;
import rw.ikimina.shared.tenancy.TenantContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loan products (spec 6.4, 17.4). Managed by SETTINGS_EDIT holders; once a product exists, a
 * change to its money terms needs a recent password and a second officer's confirmation (owner
 * decision, Phase 3) - the same pattern as savings buckets. Loans already requested keep the terms
 * they were requested on.
 */
@Service
class LoanProductService {

    record PendingChange(UUID changeId, ProductTerms proposed, UUID proposedBy, Instant proposedAt) {
    }

    record ProductView(UUID productId, String name, LoanProduct.Status status, ProductTerms terms, long version,
                       PendingChange pendingChange) {
    }

    record UpdateResult(boolean applied, ProductView view) {
    }

    private static final TypeReference<List<LoanTerms.Component>> ALLOCATION = new TypeReference<>() {
    };

    private final LoanProductRepository products;
    private final LoanProductChangeRequestRepository changes;
    private final GroupMembers members;
    private final AuditService audit;
    private final StepUp stepUp;
    private final JsonMapper json;
    private final Clock clock;

    LoanProductService(LoanProductRepository products, LoanProductChangeRequestRepository changes, GroupMembers members,
                       AuditService audit, StepUp stepUp, JsonMapper json, Clock clock) {
        this.products = products;
        this.changes = changes;
        this.members = members;
        this.audit = audit;
        this.stepUp = stepUp;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    ProductView create(String name, ProductTerms terms) {
        long groupId = TenantContext.requireGroup().groupId();
        String trimmed = name.trim();
        if (products.existsByGroupIdAndNameIgnoreCase(groupId, trimmed)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        terms.validate();
        LoanProduct product = products.saveAndFlush(new LoanProduct(groupId, trimmed, terms, json.writeValueAsString(terms.allocationOrder()),
                clock.instant()));
        audit.record(AuditEvent.of("LOAN_PRODUCT_CREATED").entity("loan_product", product.getPublicId())
                .after(Map.of("name", trimmed, "terms", terms)));
        return view(groupId, product);
    }

    @Transactional(readOnly = true)
    List<ProductView> list() {
        long groupId = TenantContext.requireGroup().groupId();
        return products.findByGroupIdOrderByName(groupId).stream().map(p -> view(groupId, p)).toList();
    }

    @Transactional(readOnly = true)
    ProductView get(UUID productId) {
        long groupId = TenantContext.requireGroup().groupId();
        return view(groupId, products.findByGroupIdAndPublicId(groupId, productId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)));
    }

    /** The name and open/closed apply at once; different money terms become a proposal for a second officer. */
    @Transactional
    UpdateResult update(UUID productId, long expectedVersion, String name, LoanProduct.Status status, ProductTerms terms) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        LoanProduct product = products.findByGroupIdAndPublicIdForUpdate(scope.groupId(), productId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (product.getVersion() != expectedVersion) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        String newName = name == null ? null : name.trim();
        if (newName != null && !newName.equalsIgnoreCase(product.getName())
                && products.existsByGroupIdAndNameIgnoreCase(scope.groupId(), newName)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Map<String, Object> before = Map.of("name", product.getName(), "status", product.getStatus());
        product.rename(newName);
        if (status != null) {
            product.changeStatus(status);
        }
        products.flush();
        audit.record(AuditEvent.of("LOAN_PRODUCT_UPDATED").entity("loan_product", product.getPublicId())
                .before(before).after(Map.of("name", product.getName(), "status", product.getStatus())));

        if (terms == null || terms(product).sameAs(terms)) {
            return new UpdateResult(true, view(scope.groupId(), product));
        }
        terms.validate();
        stepUp.require();
        Instant now = clock.instant();
        for (LoanProductChangeRequest open : changes.findByGroupIdAndProductIdAndStatus(scope.groupId(), product.getId(),
                LoanProductChangeRequest.Status.PENDING)) {
            open.decide(LoanProductChangeRequest.Status.SUPERSEDED, scope.membershipId(), "replaced by a newer proposal", now);
        }
        changes.flush();
        LoanProductChangeRequest change = changes.save(new LoanProductChangeRequest(scope.groupId(), product.getId(),
                product.getVersion(), json.writeValueAsString(terms), scope.membershipId(), now));
        audit.record(AuditEvent.of("LOAN_PRODUCT_CHANGE_PROPOSED").entity("loan_product_change", change.getPublicId())
                .before(terms(product)).after(terms));
        return new UpdateResult(false, view(scope.groupId(), product));
    }

    /** @return the updated product, or empty if the product changed after the proposal (the proposal is then closed). */
    @Transactional
    Optional<ProductView> confirm(UUID productId, UUID changeId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        LoanProduct product = products.findByGroupIdAndPublicIdForUpdate(scope.groupId(), productId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        LoanProductChangeRequest change = pending(scope.groupId(), product, changeId);
        if (change.getProposedByMembership().equals(scope.membershipId())) {
            throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN);
        }
        if (product.getVersion() != change.getBaseVersion()) {
            change.decide(LoanProductChangeRequest.Status.SUPERSEDED, scope.membershipId(), "product changed after the proposal",
                    clock.instant());
            return Optional.empty();
        }
        ProductTerms before = terms(product);
        ProductTerms proposed = json.readValue(change.getProposedTerms(), ProductTerms.class);
        product.applyTerms(proposed, json.writeValueAsString(proposed.allocationOrder()));
        change.decide(LoanProductChangeRequest.Status.APPLIED, scope.membershipId(), null, clock.instant());
        products.flush();
        audit.record(AuditEvent.of("LOAN_PRODUCT_TERMS_CHANGED").entity("loan_product", product.getPublicId())
                .before(before).after(proposed).reason("confirmed change " + change.getPublicId()));
        return Optional.of(view(scope.groupId(), product));
    }

    @Transactional
    ProductView reject(UUID productId, UUID changeId, String reason) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        LoanProduct product = products.findByGroupIdAndPublicId(scope.groupId(), productId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        LoanProductChangeRequest change = pending(scope.groupId(), product, changeId);
        change.decide(LoanProductChangeRequest.Status.REJECTED, scope.membershipId(), reason, clock.instant());
        audit.record(AuditEvent.of("LOAN_PRODUCT_CHANGE_REJECTED").entity("loan_product_change", change.getPublicId()).reason(reason));
        return view(scope.groupId(), product);
    }

    ProductTerms terms(LoanProduct product) {
        return new ProductTerms(product.getInterestMethod(), product.getInterestRatePercent(), product.getInterestPeriod(),
                product.getMinAmount(), product.getMaxAmount(), product.getMaxMultipleOfSavings(), product.getMinTermMonths(),
                product.getMaxTermMonths(), product.getRepaymentFrequency(), product.getGraceDays(), product.getDualApprovalThreshold(),
                allocation(product.getAllocationOrder()), product.isAllowConcurrentLoans());
    }

    List<LoanTerms.Component> allocation(String stored) {
        return json.readValue(stored, ALLOCATION);
    }

    private LoanProductChangeRequest pending(long groupId, LoanProduct product, UUID changeId) {
        LoanProductChangeRequest change = changes.findByGroupIdAndPublicIdForUpdate(groupId, changeId)
                .filter(c -> c.getProductId().equals(product.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!change.isPending()) {
            throw new ApiException(ErrorCode.LOAN_PRODUCT_CHANGE_STALE);
        }
        return change;
    }

    private ProductView view(long groupId, LoanProduct product) {
        PendingChange pending = changes.findByGroupIdAndProductIdAndStatus(groupId, product.getId(), LoanProductChangeRequest.Status.PENDING)
                .stream().findFirst()
                .map(c -> new PendingChange(c.getPublicId(), json.readValue(c.getProposedTerms(), ProductTerms.class),
                        members.findById(c.getProposedByMembership()).map(GroupMembers.Member::memberId).orElse(null),
                        c.getCreatedAt()))
                .orElse(null);
        return new ProductView(product.getPublicId(), product.getName(), product.getStatus(), terms(product), product.getVersion(),
                pending);
    }
}
