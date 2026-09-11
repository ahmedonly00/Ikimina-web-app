package com.example.ikimina.payments;

/**
 * Inbound mobile-money payment handling.
 *
 * <h2>Non-custodial by design</h2>
 *
 * This platform never holds member funds. Money moves directly between a member
 * and the group's own mobile-money or bank account, operated by a licensed
 * provider; Ikimina only records that it happened and reconciles its ledger
 * against what the provider reports.
 *
 * That boundary is what keeps the platform outside BNR payment-service-provider
 * and e-money licensing (Regulation No. 54/2022, Law N° 061/2021). Concretely,
 * nothing in this package may:
 *
 * <ul>
 *   <li>create a balance that Ikimina owes to a member or a group;</li>
 *   <li>initiate a transfer of funds out of any account;</li>
 *   <li>net, pool, or hold value between members.</li>
 * </ul>
 *
 * A ledger entry written from a payment notification records value held by the
 * <em>group</em>, in the group's own account. If a future change needs any of
 * the three capabilities above, it is a licensing decision before it is an
 * engineering one.
 *
 * <h2>Trust model</h2>
 *
 * A webhook is untrusted input from the public internet. Every notification is
 * therefore signature-verified, timestamp-bounded against replay, recorded raw
 * before interpretation, and applied to the ledger exactly once via the
 * provider's own event id as the idempotency key.
 */
public final class PackageInfo {
    private PackageInfo() {
    }
}