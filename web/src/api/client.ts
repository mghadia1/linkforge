import type { AuthToken, Link, PagedLinks, Stats } from './types';

/**
 * Thrown for any non-2xx response, carrying the status so callers can react to 401 specifically.
 */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/** Called when the API rejects a token, so the app can drop it and show the sign-in screen. */
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(path: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(path, { ...init, headers });

  if (response.status === 401 && token) {
    // The token was rejected — expired, or signed with a key this server no longer has.
    onUnauthorized?.();
  }

  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/**
 * The API returns a JSON body with a `message` for its own errors. Anything else — a proxy error
 * page, a dropped connection — must still produce something a human can read rather than an
 * exception about parsing JSON.
 */
async function errorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string') return body.message;
  } catch {
    /* not JSON */
  }
  if (response.status === 401) return 'Your session has expired. Sign in again.';
  if (response.status >= 500) return 'The server is not responding. Try again in a moment.';
  return `Request failed (${response.status})`;
}

export const api = {
  register: (email: string, password: string) =>
    request<AuthToken>('/api/auth/register', null, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  login: (email: string, password: string) =>
    request<AuthToken>('/api/auth/login', null, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  createLink: (token: string, url: string) =>
    request<Link>('/api/links', token, { method: 'POST', body: JSON.stringify({ url }) }),

  listLinks: (token: string, page = 0, size = 20) =>
    request<PagedLinks>(`/api/links?page=${page}&size=${size}`, token),

  stats: (token: string, code: string, days: number) =>
    request<Stats>(`/api/links/${encodeURIComponent(code)}/stats?days=${days}`, token),
};
