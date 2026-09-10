package com.example.ikimina.privacy;

/**
 * Purposes consent is sought for, kept separate so each can be granted or
 * withdrawn on its own.
 *
 * {@link #SERVICE_OPERATION} is the exception: it is the lawful basis for
 * running the member's own savings account and cannot be withdrawn while the
 * membership exists - withdrawing it means leaving the group. It is recorded
 * anyway so there is evidence the member was told.
 */
public enum ConsentPurpose {

    /** Operating the member's savings, loans and payouts. */
    SERVICE_OPERATION,

    /** SMS or USSD confirmations of contributions and payouts. */
    SMS_NOTIFICATIONS,

    /** Sharing a member's balances with their group's administrators. */
    GROUP_VISIBILITY,

    /** Aggregate, non-identifying statistics for product improvement. */
    ANALYTICS
}