/**
 * The one way the app talks to the backend.
 *
 * - The access token lives only in memory (never localStorage), so an injected script
 *   cannot lift it from storage. On reload the session is restored from the HttpOnly
 *   refresh cookie, which page scripts cannot read at all.
 * - A 401 triggers one silent refresh and a retry; concurrent requests share that refresh.
 * - Errors arrive as RFC 7807 problems with a stable `code` and a message already
 *   localised by the server for the current language (sent as Accept-Language).
 */

export interface Problem {
  status: number;
  code: string;
  title: string;
  detail: string;
  requestId?: string;
  errors?: { field: string; constraint: string }[];
}

export class ApiError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail || problem.title || problem.code);
    this.problem = problem;
  }

  get code(): string {
    return this.problem.code;
  }

  get status(): number {
    return this.problem.status;
  }
}

/** Header the refresh/logout endpoints require; a cross-site page cannot send it (spec 16.2). */
const CSRF_HEADER = 'X-Ikimina-Csrf';
const BASE = '/api/v1';

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

let accessToken: string | null = null;
let language = 'rw';
let refreshing: Promise<boolean> | null = null;
const sessionListeners = new Set<(signedIn: boolean) => void>();

export function setLanguage(lang: string): void {
  language = lang;
}

export function isSignedIn(): boolean {
  return accessToken !== null;
}

export function onSessionChange(listener: (signedIn: boolean) => void): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

function setAccessToken(token: string | null): void {
  const changed = (token === null) !== (accessToken === null);
  accessToken = token;
  if (changed) {
    sessionListeners.forEach((listener) => listener(token !== null));
  }
}

/** Stores the token from a sign-in style response (login, verify-phone, reauth). */
export function acceptTokens(response: TokenResponse): void {
  setAccessToken(response.accessToken);
}

export function forgetSession(): void {
  setAccessToken(null);
}

async function toProblem(response: Response): Promise<Problem> {
  try {
    const body = (await response.json()) as Partial<Problem>;
    if (body && typeof body.code === 'string') {
      return { status: response.status, title: '', detail: '', ...body } as Problem;
    }
  } catch {
    // fall through: not a problem document (proxy error page, network glitch)
  }
  return { status: response.status, code: response.status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN', title: '', detail: '' };
}

/** Uses the refresh cookie to get a new access token. Never throws; false means signed out. */
export function refreshSession(): Promise<boolean> {
  if (!refreshing) {
    refreshing = fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { [CSRF_HEADER]: '1', 'Accept-Language': language },
    })
      .then(async (response) => {
        if (!response.ok) {
          setAccessToken(null);
          return false;
        }
        acceptTokens((await response.json()) as TokenResponse);
        return true;
      })
      .catch(() => {
        setAccessToken(null);
        return false;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export async function api<T>(
  method: Method,
  path: string,
  body?: unknown,
  extraHeaders: Record<string, string> = {},
  retried = false,
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', 'Accept-Language': language, ...extraHeaders };
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (path === '/auth/logout' || path === '/auth/refresh') {
    headers[CSRF_HEADER] = '1';
  }

  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && accessToken && !retried && !path.startsWith('/auth/')) {
    if (await refreshSession()) {
      return api<T>(method, path, body, extraHeaders, true);
    }
  }
  if (!response.ok) {
    throw new ApiError(await toProblem(response));
  }
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const get = <T>(path: string) => api<T>('GET', path);
export const post = <T>(path: string, body?: unknown) => api<T>('POST', path, body);
export const put = <T>(path: string, body?: unknown) => api<T>('PUT', path, body);
export const patch = <T>(path: string, body?: unknown) => api<T>('PATCH', path, body);

/**
 * POST for money-moving endpoints (Hard Rule H7). The caller keeps the same key for retries of
 * one logical action - see useIdempotencyKey - so a retry after a lost response replays the
 * original instead of recording twice.
 */
export const postIdempotent = <T>(path: string, body: unknown, idempotencyKey: string) =>
  api<T>('POST', path, body, { 'Idempotency-Key': idempotencyKey });
