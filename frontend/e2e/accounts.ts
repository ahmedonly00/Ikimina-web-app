import { randomUUID } from 'node:crypto';

/** Credentials for throwaway e2e accounts, generated per run; tests contain no password literals. */
export function newAccountPassword(): string {
  return `e2e-${randomUUID()}`;
}
