package rw.ikimina.groups;

/**
 * Spec 5.5's permission matrix, copied by hand from the specification - deliberately
 * independent of {@link PermissionMatrix}, so tests can check the code against the spec
 * rather than against itself. Columns: President, Treasurer, Secretary, Auditor, Member.
 *
 * <p>Two spec rows are split, with identical grants: "FINE_ISSUE / WAIVE" into FINE_ISSUE and
 * FINE_WAIVE; REPORT_VIEW_GROUP's "summary only" for members into REPORT_VIEW_SUMMARY.
 */
final class SpecPermissionMatrix {

    static final String[][] TABLE = {
            //  permission              P    T    S    A    M
            {"MEMBER_MANAGE",        "x", " ", "x", " ", " "},
            {"SETTINGS_EDIT",        "x", "x", "x", " ", " "},
            {"CONTRIBUTION_RECORD",  " ", "x", " ", " ", " "},
            {"REPAYMENT_RECORD",     " ", "x", " ", " ", " "},
            {"LOAN_REQUEST",         "x", "x", "x", " ", "x"},
            {"LOAN_APPROVE",         "x", "x", " ", " ", " "},   // Secretary: substitute only, spec 9.3 (loan engine)
            {"LOAN_DISBURSE",        " ", "x", " ", " ", " "},
            {"FINE_ISSUE",           "x", "x", "x", " ", " "},
            {"FINE_WAIVE",           "x", "x", "x", " ", " "},
            {"EXPENSE_RECORD",       " ", "x", " ", " ", " "},
            {"MEETING_MANAGE",       "x", " ", "x", " ", " "},
            {"REPORT_VIEW_GROUP",    "x", "x", "x", "x", " "},
            {"REPORT_VIEW_SUMMARY",  "x", "x", "x", "x", "x"},   // members: "summary only"
            {"AUDIT_LOG_VIEW",       "x", "x", " ", "x", " "},
            {"OWN_DATA_VIEW",        "x", "x", "x", "x", "x"},
    };

    static final GroupRole[] COLUMNS = {
            GroupRole.PRESIDENT, GroupRole.TREASURER, GroupRole.SECRETARY, GroupRole.AUDITOR, GroupRole.MEMBER};

    private SpecPermissionMatrix() {
    }

    static boolean allows(GroupRole role, String permission) {
        for (String[] row : TABLE) {
            if (row[0].equals(permission)) {
                for (int column = 0; column < COLUMNS.length; column++) {
                    if (COLUMNS[column] == role) {
                        return "x".equals(row[column + 1]);
                    }
                }
            }
        }
        throw new IllegalArgumentException("Not in spec 5.5: " + permission);
    }
}
