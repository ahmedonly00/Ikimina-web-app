# Privacy and data protection

Reference for compliance with **Law N° 058/2021 relating to the protection of
personal data and privacy** (in force 15 October 2021), enforced by Rwanda's
National Cyber Security Authority (NCSA).

This documents what the software does. It is not legal advice, and the items
under "Still outstanding" are organisational obligations that code cannot
satisfy.

## What the platform holds

| Data | Why | Where |
| --- | --- | --- |
| Name, email, phone number | Identify a member and contact them | `users` |
| Member number, group membership | Attribute contributions to the right person | `users`, `group_members` |
| Contributions, loans, fines, payouts | The purpose of the service | `ledger_entries`, `savings`, `loans`, `fines`, `member_payouts` |
| Payer phone number on a payment | The only identifier a mobile-money provider supplies | `inbound_payment_events.payer_msisdn` |
| Consent decisions | Demonstrating a lawful basis | `consent_records` |
| Audit trail (who did what, IP, user agent) | Financial accountability | `audit_logs` |

No special-category data (health, biometrics, political opinion) is collected.

## Lawful basis

| Purpose | Basis |
| --- | --- |
| Operating a member's savings account | Performance of the membership; recorded as `SERVICE_OPERATION` consent so there is evidence the member was told |
| SMS or USSD confirmations | Consent (`SMS_NOTIFICATIONS`), freely withdrawable |
| Showing balances to group admins | Consent (`GROUP_VISIBILITY`) |
| Aggregate product statistics | Consent (`ANALYTICS`) |
| Keeping financial records after closure | Legal obligation; survives withdrawal of consent |

`SERVICE_OPERATION` cannot be withdrawn while a membership exists - the software
refuses it and explains that leaving the group is the real action. Bundling
several purposes into one checkbox would not be specific consent, so each is
recorded separately with the version of the notice shown.

## Rights, and how they are served

| Right | Endpoint | Notes |
| --- | --- | --- |
| Access / portability | `GET /api/privacy/me/export` | Full JSON export: profile, groups, consents, ledger history, balance. A group admin can export on a member's behalf via `/api/privacy/users/{id}/export`, since many members ask in person |
| Consent status | `GET /api/privacy/me/consents` | Current state per purpose |
| Grant / withdraw consent | `POST /api/privacy/me/consents` | Append-only; a withdrawal is a new row |
| Erasure | `POST /api/privacy/users/{id}/erase` | Super-admin only. See below |
| Rectification | `PUT /api/users/{id}` | Existing endpoint, ownership-checked |

## Erasure is anonymisation, and why

A member's right to erasure and the group's duty to keep financial records both
apply, and they conflict.

The resolution: **erase the person, keep the money.** Name, email, phone number
and username are overwritten with a per-member pseudonym; the payer phone number
on payment events is cleared; consent history is deleted. Ledger entries are
left exactly as they were, still attached to the now-anonymous member id.

The result is that the group's books still balance and still reconcile, while
the individual is no longer identifiable from them. Deleting the ledger rows
instead would corrupt every group total that member ever contributed to, and
would itself breach the retention obligation.

Two guardrails:

* Erasure is refused while the member still holds a balance, because the group
  would otherwise be unable to explain a payout to someone who no longer exists
  in its records. Override deliberately with
  `ikimina.privacy.allow-erasure-with-balance=true`.
* It cannot be run twice, and it is irreversible. If it could be undone it
  would not be erasure.

## Retention

| Data | Retained |
| --- | --- |
| Personal identifiers | Until erasure is requested, or membership ends plus the group's stated period |
| Financial records (ledger, savings, loans, payouts) | Kept indefinitely in anonymised form after erasure, to meet record-keeping obligations |
| Audit trail | Kept; it is the evidence that the controller handled requests properly |
| Raw payment notifications | Kept as dispute evidence, with the payer number cleared on erasure |

## Security measures implemented

* Passwords hashed with bcrypt; no plaintext credential is ever logged, and
  `password`, `token`, `secret` and `authorization` are masked in production JSON logs.
* Bearer-token authentication with group-scoped claims; object-level
  authorisation on every member-scoped endpoint, so one member cannot read another's records.
* Webhooks authenticated by HMAC with constant-time comparison and a five-minute
  replay window.
* The ledger is append-only, enforced by database triggers, so financial history
  cannot be rewritten.
* Every money movement and every privacy action is audited with actor, IP and
  correlation id.
* Secrets supplied only through the environment; the application refuses to
  start without them.

## Still outstanding

These need a person or a decision, not a commit:

1. **Register with the NCSA** as data controller.
2. **Appoint a Data Protection Officer** - required for large-scale processing.
3. **Data localisation.** Personal data must be stored in Rwanda, or an NCSA
   certificate obtained for offshore storage. This constrains the hosting choice
   and should be settled before the first production deployment.
4. **Complete a formal DPIA** - see `docs/DPIA.md` for the started assessment.
5. **Publish a privacy notice** in Kinyarwanda, English and French. It must be
   in Kinyarwanda to be informed consent for most of this audience.
6. **Breach response plan.** A financial platform may be designated Critical
   Information Infrastructure, which carries a 24-hour incident reporting duty.
7. **Processor agreements** with the mobile-money provider and the hosting
   provider.
8. **Retention periods** stated as concrete durations, agreed with whoever
   advises on Rwandan tax and financial record-keeping.

## Known gap

The `X-Forwarded-For` address and user agent are stored on audit and consent
records. That is itself personal data, retained for accountability. It is a
legitimate-interest balance worth stating explicitly in the privacy notice
rather than leaving implicit.