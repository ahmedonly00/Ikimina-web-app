-- Phase 3 review fixes.
--
--   * loans.borrower_role: the borrower's role when the loan was requested. Who may approve (spec 9.3)
--     depends on it, and must not shift if the borrower's role changes while approvals are collected.
--     Existing loans take the borrower's current role.
--   * Membership references on loan tables now carry the group too, so the database itself refuses a
--     membership of another group (the code already did).

ALTER TABLE loans ADD COLUMN borrower_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
    CHECK (borrower_role IN ('PRESIDENT', 'TREASURER', 'SECRETARY', 'AUDITOR', 'MEMBER'));
UPDATE loans l SET borrower_role = m.role FROM group_memberships m WHERE m.id = l.borrower_membership_id;

ALTER TABLE group_memberships ADD CONSTRAINT uq_membership_group UNIQUE (id, group_id);
ALTER TABLE loans ADD CONSTRAINT fk_loans_borrower_group
    FOREIGN KEY (borrower_membership_id, group_id) REFERENCES group_memberships (id, group_id);
ALTER TABLE loan_approvals ADD CONSTRAINT fk_loan_approvals_approver_group
    FOREIGN KEY (approver_membership_id, group_id) REFERENCES group_memberships (id, group_id);
ALTER TABLE loan_product_change_requests ADD CONSTRAINT fk_loan_product_changes_proposer_group
    FOREIGN KEY (proposed_by_membership, group_id) REFERENCES group_memberships (id, group_id);
