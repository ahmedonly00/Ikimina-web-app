import { randomInt, randomUUID } from 'node:crypto';

/** Credentials for throwaway e2e accounts, generated per run; tests contain no password literals. */
export function newAccountPassword(): string {
  return `e2e-${randomUUID()}`;
}

/**
 * A fresh Rwandan mobile number. Random rather than clock-based, so tests running in parallel
 * workers never pick the same number - and read each other's one-time codes.
 */
export function freshPhone(): string {
  return `+2507${String(randomInt(0, 100_000_000)).padStart(8, '0')}`;
}
