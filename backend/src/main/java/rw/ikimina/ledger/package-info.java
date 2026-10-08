/**
 * Double-entry, append-only ledger. LedgerService.post() is the only writer of ledger tables (spec 7).
 *
 * <p>Public API lives in this package. Implementation (entities, repositories,
 * services that other modules must not call) lives in {@code .internal} and is
 * reachable only from inside this module - enforced by {@code ArchitectureTest}.
 */
package rw.ikimina.ledger;
