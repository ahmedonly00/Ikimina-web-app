package rw.ikimina.groups;

/** A person's role within one group (spec 2.2). The same person can hold different roles in different groups. */
public enum GroupRole {
    PRESIDENT,
    TREASURER,
    SECRETARY,
    AUDITOR,
    MEMBER;

    /** Offices have at most one active holder per group and change hands only by transfer (spec 2.2). */
    public boolean isOffice() {
        return this == PRESIDENT || this == TREASURER || this == SECRETARY;
    }
}
