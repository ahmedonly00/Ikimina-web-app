import { useCallback, useRef } from 'react';

/**
 * One Idempotency-Key per logical action (Hard Rule H7). The same key is reused for every retry
 * of that action - a double tap, or a retry after a dropped connection - so the server records
 * it once. Call {@link rotate} only after the action succeeded and the form starts a new one.
 */
export function useIdempotencyKey(): { current: () => string; rotate: () => void } {
  const key = useRef<string>(crypto.randomUUID());
  const current = useCallback(() => key.current, []);
  const rotate = useCallback(() => {
    key.current = crypto.randomUUID();
  }, []);
  return { current, rotate };
}
