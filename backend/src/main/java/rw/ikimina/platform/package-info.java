/**
 * Platform-admin console and consented support-access grants (spec 2.1, 12.5).
 *
 * <p>Public API lives in this package. Implementation (entities, repositories,
 * services that other modules must not call) lives in {@code .internal} and is
 * reachable only from inside this module - enforced by {@code ArchitectureTest}.
 */
package rw.ikimina.platform;
