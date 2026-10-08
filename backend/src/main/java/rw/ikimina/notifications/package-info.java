/**
 * Notification templates (EN/RW), outbox worker, SmsProvider interface and fake (spec 14).
 *
 * <p>Public API lives in this package. Implementation (entities, repositories,
 * services that other modules must not call) lives in {@code .internal} and is
 * reachable only from inside this module - enforced by {@code ArchitectureTest}.
 */
package rw.ikimina.notifications;
