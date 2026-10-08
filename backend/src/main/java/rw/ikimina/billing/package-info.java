/**
 * Subscription plans, trials, invoices, PlanGate, subscription payments via PaymentProvider (spec 12, 13).
 *
 * <p>Public API lives in this package. Implementation (entities, repositories,
 * services that other modules must not call) lives in {@code .internal} and is
 * reachable only from inside this module - enforced by {@code ArchitectureTest}.
 */
package rw.ikimina.billing;
