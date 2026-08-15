import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, setUnauthorizedHandler } from './client';

function respond(status: number, body: unknown, ok = status < 400) {
  return {
    ok,
    status,
    json: async () => body,
  } as Response;
}

afterEach(() => {
  vi.unstubAllGlobals();
  setUnauthorizedHandler(() => {});
});

describe('api client', () => {
  it('sends the bearer token on authenticated calls', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(200, { links: [] }));
    vi.stubGlobal('fetch', fetchMock);

    await api.listLinks('token-123');

    const headers = fetchMock.mock.calls[0][1].headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer token-123');
  });

  it("raises the server's message rather than a status code", async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(respond(400, { message: 'url must include a host' })),
    );

    await expect(api.createLink('t', 'http:///nope')).rejects.toThrow('url must include a host');
  });

  /** A proxy error page is not JSON; the user still needs a sentence, not a parse failure. */
  it('falls back to a readable message when the body is not JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 502,
        json: async () => {
          throw new Error('not json');
        },
      } as unknown as Response),
    );

    await expect(api.listLinks('t')).rejects.toThrow(/server is not responding/i);
  });

  it('signs the user out when the server rejects the token', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(401, { message: 'nope' })));

    await expect(api.listLinks('expired')).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalled();
  });

  /** A failed login is not an expired session — it must not trigger the sign-out path. */
  it('does not sign out on a failed login, which carries no token', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(respond(401, { message: 'invalid email or password' })),
    );

    await expect(api.login('a@example.com', 'wrong')).rejects.toThrow('invalid email or password');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('escapes the code in the stats path', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(200, {}));
    vi.stubGlobal('fetch', fetchMock);

    await api.stats('t', 'a/b', 7);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/links/a%2Fb/stats?days=7');
  });
});
