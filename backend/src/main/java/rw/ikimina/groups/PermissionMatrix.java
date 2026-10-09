package rw.ikimina.groups;

import static rw.ikimina.groups.Permission.AUDIT_LOG_VIEW;
import static rw.ikimina.groups.Permission.CONTRIBUTION_RECORD;
import static rw.ikimina.groups.Permission.EXPENSE_RECORD;
import static rw.ikimina.groups.Permission.FINE_ISSUE;
import static rw.ikimina.groups.Permission.FINE_WAIVE;
import static rw.ikimina.groups.Permission.LOAN_APPROVE;
import static rw.ikimina.groups.Permission.LOAN_DISBURSE;
import static rw.ikimina.groups.Permission.LOAN_REQUEST;
import static rw.ikimina.groups.Permission.MEETING_MANAGE;
import static rw.ikimina.groups.Permission.MEMBER_MANAGE;
import static rw.ikimina.groups.Permission.OWN_DATA_VIEW;
import static rw.ikimina.groups.Permission.REPAYMENT_RECORD;
import static rw.ikimina.groups.Permission.REPORT_VIEW_GROUP;
import static rw.ikimina.groups.Permission.REPORT_VIEW_SUMMARY;
import static rw.ikimina.groups.Permission.SETTINGS_EDIT;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Spec 5.5, transcribed. {@code PermissionMatrixTest} compares this cell by cell with an
 * independent copy of the spec's table, so a change here without a spec change fails.
 */
public final class PermissionMatrix {

    private static final Map<GroupRole, Set<Permission>> GRANTS = new EnumMap<>(GroupRole.class);

    static {
        GRANTS.put(GroupRole.PRESIDENT, EnumSet.of(MEMBER_MANAGE, SETTINGS_EDIT, LOAN_REQUEST, LOAN_APPROVE,
                FINE_ISSUE, FINE_WAIVE, MEETING_MANAGE, REPORT_VIEW_GROUP, REPORT_VIEW_SUMMARY, AUDIT_LOG_VIEW, OWN_DATA_VIEW));
        GRANTS.put(GroupRole.TREASURER, EnumSet.of(SETTINGS_EDIT, CONTRIBUTION_RECORD, REPAYMENT_RECORD, LOAN_REQUEST,
                LOAN_APPROVE, LOAN_DISBURSE, FINE_ISSUE, FINE_WAIVE, EXPENSE_RECORD, REPORT_VIEW_GROUP, REPORT_VIEW_SUMMARY,
                AUDIT_LOG_VIEW, OWN_DATA_VIEW));
        GRANTS.put(GroupRole.SECRETARY, EnumSet.of(MEMBER_MANAGE, SETTINGS_EDIT, LOAN_REQUEST, FINE_ISSUE, FINE_WAIVE,
                MEETING_MANAGE, REPORT_VIEW_GROUP, REPORT_VIEW_SUMMARY, OWN_DATA_VIEW));
        GRANTS.put(GroupRole.AUDITOR, EnumSet.of(REPORT_VIEW_GROUP, REPORT_VIEW_SUMMARY, AUDIT_LOG_VIEW, OWN_DATA_VIEW));
        GRANTS.put(GroupRole.MEMBER, EnumSet.of(LOAN_REQUEST, REPORT_VIEW_SUMMARY, OWN_DATA_VIEW));
    }

    private PermissionMatrix() {
    }

    public static boolean allows(GroupRole role, Permission permission) {
        return GRANTS.get(role).contains(permission);
    }

    public static Set<Permission> grantsOf(GroupRole role) {
        return Set.copyOf(GRANTS.get(role));
    }
}
