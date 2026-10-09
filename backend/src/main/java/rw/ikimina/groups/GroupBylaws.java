package rw.ikimina.groups;

import rw.ikimina.shared.money.Money;

/**
 * The groups module's public view of a group's bylaws (spec 6.1 group_settings), for the modules
 * whose rules depend on them. Acts on the group in the current tenant scope.
 */
public interface GroupBylaws {

    enum InterestRecognition { WHEN_PAID, WHEN_DUE }

    record Bylaws(InterestRecognition interestRecognition, boolean withdrawalsAllowed, int withdrawalNoticeDays, Money exitFee) {
    }

    Bylaws current();
}
