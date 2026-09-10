# Payments architecture

## The constraint that shapes everything here

**This platform never holds member funds.** Money moves directly between a
member and the group's own mobile-money or bank account, operated by a licensed
provider. Ikimina records that it happened and reconciles against what the
provider reports.

That boundary is what keeps the platform outside BNR payment-service-provider
and e-money licensing (Regulation No. 54/2022; Law N° 061/2021 governing payment
systems). Concretely, nothing in the `payments` package may:

* create a balance that Ikimina owes to a member or a group;
* initiate a transfer of funds out of any account;
* net, pool, or hold value between members.

A ledger entry written from a payment notification records value held by the
**group**, in the group's own account. If a future feature needs any of the
three capabilities above, that is a licensing decision before it is an
engineering one - it would make the platform an e-money issuer, requiring a BNR
licence, minimum capital, a resident CEO, and separated board/CEO roles.

## Flow

```
member pays via MoMo
        │
        ▼
provider settles into the GROUP's account        (Ikimina is not in this path)
        │
        ▼
provider POSTs a signed notification  ──►  /api/webhooks/payments/{provider}
                                                    │
                            verify HMAC + timestamp │  reject if unproven
                                                    ▼
                                    inbound_payment_events  (raw, append-only)
                                                    │
                              resolve member/group  │
                                                    ▼
                                     ledger_entries  (idempotent, append-only)
```

## Adding a real provider

Implement `PaymentProvider` and register it as a bean. That is the only change:
ingestion, idempotency, ledger posting, audit and reconciliation are all
provider-agnostic.

```java
@Component
public class MtnMomoProvider implements PaymentProvider {
    public String name() { return "mtn-momo"; }

    public PaymentNotification verifyAndParse(String rawBody, Map<String, String> headers)
            throws PaymentVerificationException {
        // 1. verify the provider's signature over the exact bytes of rawBody
        // 2. bound the timestamp against replay
        // 3. map their field names into PaymentNotification
    }
}
```

The webhook is then live at `/api/webhooks/payments/mtn-momo`.

`GenericHmacPaymentProvider` is a working reference adapter, not a vendor
integration. It exists so the pipeline is complete and testable without
committing to a provider's contract.

### Three things adapters must get right

1. **Sign the exact bytes.** The controller passes the body as a `String`, never
   a parsed DTO. Re-serialising JSON changes whitespace and key order, and the
   signature will not match - or worse, a lenient implementation verifies
   different bytes than it acts on.
2. **Constant-time comparison.** `String.equals` on a signature leaks, through
   timing, how many leading bytes were correct. `WebhookSignatureVerifier` uses
   `MessageDigest.isEqual`.
3. **Bound the timestamp.** A valid signature is valid forever. Without a
   replay window a captured notification can be resent.

An unconfigured secret causes verification to **refuse everything**, so a
half-configured provider is a closed endpoint rather than an open one that
writes to the ledger.

## Idempotency

The provider's own event id is the idempotency key for the whole pipeline:

* `inbound_payment_events` has a unique constraint on `(provider, provider_event_id)`
* the ledger entry uses `payment:{provider}:{eventId}` as its idempotency key

Providers retry aggressively, and a retry must never credit a member twice.
Verified end to end: two identical signed requests produce one event row and one
ledger entry.

## Unmatched payments

A payment that cannot be attributed to exactly one member is recorded as
`FAILED` with a reason, and **not** credited. This is deliberate:

* An unknown phone number means nobody is credited until a human matches it.
* A phone number matching **more than one** member is also refused. Handsets are
  shared in some groups, and crediting the wrong person is a worse failure than
  crediting nobody yet.

`GET /api/payments/unmatched` is the queue that must not be allowed to grow -
every row is money the group has received that no member has been credited for.

## Reconciliation

`GET /api/payments/groups/{id}/reconciliation?from=&to=` compares what providers
reported against what the ledger records, and reports:

* `providerTotal` vs `ledgerTotal` and the difference
* how many notifications are stuck unapplied
* the event ids needing attention

It **reports and never auto-corrects.** Silently adjusting a member's balance to
match a provider is how real discrepancies get buried.

## Configuration

```
IKIMINA_PAYMENTS_GENERICHMAC_SECRET=...   # note: Spring drops the hyphen from
                                          # ikimina.payments.generic-hmac.secret
```

## Not built

* **Outbound disbursement.** Paying a member their payout still happens outside
  the platform; `MemberPayout` records that it was done. Automating it would put
  Ikimina in the payment path and change the licensing position.
* **Collection requests** (prompting a member's phone to pay). Feasible without
  custody, but needs a provider contract first.
* **SMS/USSD confirmation.** The consent purpose (`SMS_NOTIFICATIONS`) and the
  audit hooks exist; the sender does not.