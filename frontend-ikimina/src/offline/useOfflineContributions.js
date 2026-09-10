import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import {
  clearContributionsFor,
  enqueueContribution,
  pendingContributions,
  recordAttempt,
  removeContribution,
} from './contributionQueue';

/**
 * Records contributions whether or not there is a connection.
 *
 * Online, it posts straight through. Offline, it queues locally and replays when
 * the browser reports a connection again. The safety of that replay rests
 * entirely on the idempotency key stored with each item: the backend
 * deduplicates on it, so a replay after a crash, a refresh, or a flaky
 * connection cannot double-count a member.
 *
 * Deliberately conservative in two places:
 *  - A queued item that the server rejects for a business reason (a 4xx) is
 *    dropped from the queue and surfaced, not retried forever. Retrying a
 *    rejected contribution just hides the problem.
 *  - Only 5xx and network failures are treated as retryable.
 */
export const useOfflineContributions = ({ ownerId, submit }) => {
  const [pending, setPending] = useState([]);
  const [isOnline, setIsOnline] = useState(
    typeof navigator === 'undefined' ? true : navigator.onLine
  );
  const [isReplaying, setIsReplaying] = useState(false);

  const refresh = useCallback(async () => {
    if (!ownerId) {
      setPending([]);
      return;
    }
    try {
      setPending(await pendingContributions(ownerId));
    } catch {
      // No IndexedDB (private mode, old browser): offline queueing is simply
      // unavailable, which must not break online use.
      setPending([]);
    }
  }, [ownerId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const goOnline = () => setIsOnline(true);
    const goOffline = () => setIsOnline(false);
    window.addEventListener('online', goOnline);
    window.addEventListener('offline', goOffline);
    return () => {
      window.removeEventListener('online', goOnline);
      window.removeEventListener('offline', goOffline);
    };
  }, []);

  const replay = useCallback(async () => {
    if (!ownerId || isReplaying) return;

    let items = [];
    try {
      items = await pendingContributions(ownerId);
    } catch {
      return;
    }
    if (items.length === 0) return;

    setIsReplaying(true);
    let sent = 0;

    for (const item of items) {
      try {
        // The stored key is what makes this safe to repeat.
        await submit(item.payload, item.idempotencyKey);
        await removeContribution(item.idempotencyKey);
        sent += 1;
      } catch (err) {
        const status = err && err.status;
        const isClientError = typeof status === 'number' && status >= 400 && status < 500;

        if (isClientError) {
          // The server will keep refusing this; stop pretending it is pending.
          await removeContribution(item.idempotencyKey);
          toast.error(
            `A saved contribution was rejected and has been removed from the queue: ${
              (err.data && (err.data.detail || err.data.message)) || 'invalid entry'
            }`
          );
        } else {
          await recordAttempt(item, err);
          break; // still offline or the server is down; try again later
        }
      }
    }

    setIsReplaying(false);
    await refresh();
    if (sent > 0) {
      toast.success(`${sent} saved contribution${sent === 1 ? '' : 's'} synced`);
    }
  }, [ownerId, isReplaying, submit, refresh]);

  // Replay as soon as the connection returns.
  useEffect(() => {
    if (isOnline) {
      replay();
    }
    // replay is stable enough for this purpose; adding it re-runs on every
    // pending change and would replay in a loop.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOnline]);

  /**
   * Records a contribution. Returns { queued: true } when it was stored for
   * later, so the caller can tell the member the truth rather than implying it
   * reached the group.
   */
  const record = useCallback(
    async payload => {
      if (isOnline) {
        try {
          await submit(payload);
          return { queued: false };
        } catch (err) {
          const status = err && err.status;
          const retryable = !status || status >= 500 || status === 'FETCH_ERROR';
          if (!retryable) throw err;
          // Fall through to queueing: the request failed for a reason a retry
          // could fix.
        }
      }

      const stored = await enqueueContribution({
        ownerId,
        endpoint: 'savings',
        payload,
      });
      await refresh();
      return { queued: true, idempotencyKey: stored.idempotencyKey };
    },
    [isOnline, ownerId, submit, refresh]
  );

  /** Called on sign-out: a shared handset must not keep one member's entries. */
  const clearForSignOut = useCallback(async () => {
    if (!ownerId) return 0;
    const removed = await clearContributionsFor(ownerId);
    setPending([]);
    return removed;
  }, [ownerId]);

  return {
    isOnline,
    isReplaying,
    pending,
    pendingCount: pending.length,
    record,
    replay,
    clearForSignOut,
  };
};

export default useOfflineContributions;
