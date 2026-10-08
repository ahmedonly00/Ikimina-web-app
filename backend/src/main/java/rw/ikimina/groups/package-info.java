/**
 * Groups, structured bylaws/settings, memberships, per-group roles, invitations, GroupAccessGuard (spec 5, 6.1, 17.2).
 *
 * <p>Public API lives in this package. Implementation (entities, repositories,
 * services that other modules must not call) lives in {@code .internal} and is
 * reachable only from inside this module - enforced by {@code ArchitectureTest}.
 */
package rw.ikimina.groups;
