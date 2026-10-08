/**
 * Append-only, per-group hash-chained audit log written in the caller's transaction (spec 16.6, H8).
 *
 * <p>Public API lives in this package. Implementation (entities, repositories,
 * services that other modules must not call) lives in {@code .internal} and is
 * reachable only from inside this module - enforced by {@code ArchitectureTest}.
 */
package rw.ikimina.audit;
