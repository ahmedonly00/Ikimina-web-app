/**
 * Fine rules, idempotent automatic assessment, manual fines, payment and waiver (spec 10).
 *
 * <p>Public API lives in this package. Implementation (entities, repositories,
 * services that other modules must not call) lives in {@code .internal} and is
 * reachable only from inside this module - enforced by {@code ArchitectureTest}.
 */
package rw.ikimina.fines;
