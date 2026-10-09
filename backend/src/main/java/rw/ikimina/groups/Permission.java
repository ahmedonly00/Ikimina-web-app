package rw.ikimina.groups;

/**
 * Group-level permissions, exactly the rows of the spec's permission matrix (5.5).
 * Endpoints check them with {@code @PreAuthorize("@perm.has(#groupId, 'NAME')")}.
 */
public enum Permission {
    MEMBER_MANAGE,
    /** Financial parameters additionally need a second officer's confirmation. */
    SETTINGS_EDIT,
    CONTRIBUTION_RECORD,
    REPAYMENT_RECORD,
    LOAN_REQUEST,
    /** The Secretary's substitute approval (spec 9.3) is a loan-engine rule, not a matrix grant. */
    LOAN_APPROVE,
    LOAN_DISBURSE,
    FINE_ISSUE,
    FINE_WAIVE,
    EXPENSE_RECORD,
    MEETING_MANAGE,
    /** Full group reports. Ordinary members get the summary only: {@link #REPORT_VIEW_SUMMARY}. */
    REPORT_VIEW_GROUP,
    REPORT_VIEW_SUMMARY,
    AUDIT_LOG_VIEW,
    OWN_DATA_VIEW
}
