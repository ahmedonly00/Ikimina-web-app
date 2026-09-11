/**
 * Offline queue for contributions recorded away from a connection.
 *
 * Groups meet in person, frequently somewhere with no usable signal, and an
 * admin entering a meeting sheet cannot be told to come back later. Entries are
 * therefore queued locally and replayed when the connection returns.
 *
 * This is only safe because every queued item carries a client-generated
 * idempotency key, which the backend uses to deduplicate (see LedgerService).
 * Replaying the queue - after a crash, a refresh, or two tabs racing - cannot
 * double-count a member's contribution. Without that key an offline queue would
 * be a way to corrupt the books, not a feature.
 *
 * Storage notes:
 *  - IndexedDB, not localStorage: the queue holds structured records and must
 *    survive a reload, and localStorage is synchronous and size-limited.
 *  - Scoped per user and cleared on sign-out. Handsets are commonly shared in
 *    these groups, so one member's unsent contributions must not be visible to
 *    or replayable by the next person to sign in.
 */

const DB_NAME = 'ikimina-offline';
const DB_VERSION = 1;
const STORE = 'pending-contributions';

let dbPromise = null;

const openDb = () => {
  if (dbPromise) return dbPromise;

  dbPromise = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: 'idempotencyKey' });
        // Queued work is replayed oldest-first, and filtered by owner so a
        // shared handset never replays someone else's entries.
        store.createIndex('byOwner', 'ownerId', { unique: false });
        store.createIndex('byQueuedAt', 'queuedAt', { unique: false });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });

  return dbPromise;
};

const tx = async (mode, fn) => {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE, mode);
    const store = transaction.objectStore(STORE);
    let result;
    try {
      result = fn(store);
    } catch (err) {
      reject(err);
      return;
    }
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
};

/**
 * Generates the idempotency key for a queued contribution.
 *
 * crypto.randomUUID where available; otherwise a random fallback, because a
 * missing key would make the replay unsafe rather than merely untidy.
 */
export const newIdempotencyKey = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `offline-${crypto.randomUUID()}`;
  }
  const random = Math.random().toString(36).slice(2);
  return `offline-${Date.now()}-${random}`;
};

/** Adds a contribution to the queue. Returns the stored record. */
export const enqueueContribution = async ({ ownerId, endpoint, payload }) => {
  const record = {
    idempotencyKey: newIdempotencyKey(),
    ownerId,
    endpoint,
    payload,
    queuedAt: new Date().toISOString(),
    attempts: 0,
    lastError: null,
  };
  await tx('readwrite', store => store.put(record));
  return record;
};

/** Everything still waiting, for this user only, oldest first. */
export const pendingContributions = async ownerId => {
  const all = await tx('readonly', store => {
    const request = store.getAll();
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result || []);
      request.onerror = () => reject(request.error);
    });
  });
  const resolved = await all;
  return resolved
    .filter(r => String(r.ownerId) === String(ownerId))
    .sort((a, b) => a.queuedAt.localeCompare(b.queuedAt));
};

export const removeContribution = async idempotencyKey =>
  tx('readwrite', store => store.delete(idempotencyKey));

export const recordAttempt = async (record, error) =>
  tx('readwrite', store =>
    store.put({
      ...record,
      attempts: (record.attempts || 0) + 1,
      lastError: error ? String(error).slice(0, 300) : null,
    })
  );

/**
 * Clears the queue for one user.
 *
 * Called on sign-out: unsent financial entries must not linger on a shared
 * device where the next person to sign in could replay them.
 */
export const clearContributionsFor = async ownerId => {
  const mine = await pendingContributions(ownerId);
  await Promise.all(mine.map(r => removeContribution(r.idempotencyKey)));
  return mine.length;
};

export const pendingCount = async ownerId => (await pendingContributions(ownerId)).length;
