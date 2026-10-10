package rw.ikimina.loans.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.ikimina.shared.money.Money;

/** One installment of a loan's schedule (spec 6.4), generated once at disbursement. */
@Entity
@Table(name = "loan_installments")
public class LoanInstallment {

    enum Status { PENDING, PARTIAL, PAID, OVERDUE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private Long groupId;

    @Column(name = "loan_id", nullable = false, updatable = false)
    private Long loanId;

    @Column(name = "installment_no", nullable = false, updatable = false)
    private int installmentNo;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, updatable = false)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, updatable = false)
    private BigDecimal interestDue;

    @Column(name = "principal_paid", nullable = false)
    private BigDecimal principalPaid;

    @Column(name = "interest_paid", nullable = false)
    private BigDecimal interestPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Version
    private long version;

    protected LoanInstallment() {
    }

    LoanInstallment(long groupId, long loanId, LoanScheduleCalculator.Installment row) {
        this.groupId = groupId;
        this.loanId = loanId;
        this.installmentNo = row.number();
        this.dueDate = row.dueDate();
        this.principalDue = row.principal().toStorageAmount();
        this.interestDue = row.interest().toStorageAmount();
        this.principalPaid = BigDecimal.ZERO;
        this.interestPaid = BigDecimal.ZERO;
        // A tiny or interest-free loan can leave an installment with nothing due; it is paid from the start,
        // or the loan could never be settled.
        this.status = row.total().isZero() ? Status.PAID : Status.PENDING;
    }

    Money interestOutstanding() {
        return Money.of(interestDue).minus(Money.of(interestPaid));
    }

    Money principalOutstanding() {
        return Money.of(principalDue).minus(Money.of(principalPaid));
    }

    Money outstanding() {
        return interestOutstanding().plus(principalOutstanding());
    }

    /** Applies (positive) or undoes (negative) a payment, then recomputes the status as of {@code today}. */
    void applyPayment(Money interest, Money principal, LocalDate today, int graceDays) {
        interestPaid = Money.of(interestPaid).plus(interest).toStorageAmount();
        principalPaid = Money.of(principalPaid).plus(principal).toStorageAmount();
        refreshStatus(today, graceDays);
    }

    /** Overdue once the due date plus the grace days has passed unpaid (spec 9.7). */
    void refreshStatus(LocalDate today, int graceDays) {
        if (outstanding().isZero()) {
            status = Status.PAID;
        } else if (dueDate.plusDays(graceDays).isBefore(today)) {
            status = Status.OVERDUE;
        } else {
            status = Money.of(interestPaid).plus(Money.of(principalPaid)).isPositive() ? Status.PARTIAL : Status.PENDING;
        }
    }

    Long getId() {
        return id;
    }

    Long getLoanId() {
        return loanId;
    }

    int getInstallmentNo() {
        return installmentNo;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    Money getPrincipalDue() {
        return Money.of(principalDue);
    }

    Money getInterestDue() {
        return Money.of(interestDue);
    }

    Money getPrincipalPaid() {
        return Money.of(principalPaid);
    }

    Money getInterestPaid() {
        return Money.of(interestPaid);
    }

    Status getStatus() {
        return status;
    }
}
