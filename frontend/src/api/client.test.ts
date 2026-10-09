import { acceptTokens, api, ApiError, forgetSession, isSignedIn, refreshSession } from './client';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const problem = (status: number, code: string) => json(status, { status, code, title: code, detail: `detail of ${code}` });

describe('api client', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    forgetSession();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends the bearer token and language, never storing the token in localStorage', async () => {
    acceptTokens({ accessToken: 'token-1', tokenType: 'Bearer', expiresIn: 900 });
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }));

    await api('GET', '/me');

    const [, init] = fetchMock.mock.calls[0]!;
    const headers = init!.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer token-1');
    expect(headers['Accept-Language']).toBeDefined();
    expect(JSON.stringify(localStorage)).not.toContain('token-1');
  });

  it('turns an RFC 7807 problem into an ApiError with its stable code', async () => {
    fetchMock.mockResolvedValueOnce(problem(409, 'OFFICE_OCCUPIED'));
    const error = await api('PATCH', '/groups/g/members/m', {}).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('OFFICE_OCCUPIED');
    expect((error as ApiError).message).toBe('detail of OFFICE_OCCUPIED');
  });

  it('refreshes once on 401 and retries the request with the new token', async () => {
    acceptTokens({ accessToken: 'expired', tokenType: 'Bearer', expiresIn: 900 });
    fetchMock
      .mockResolvedValueOnce(problem(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(json(200, { accessToken: 'fresh', tokenType: 'Bearer', expiresIn: 900 }))
      .mockResolvedValueOnce(json(200, { name: 'Twisungane' }));

    await expect(api('GET', '/groups/g')).resolves.toEqual({ name: 'Twisungane' });

    const refreshCall = fetchMock.mock.calls[1]!;
    expect(refreshCall[0]).toBe('/api/v1/auth/refresh');
    expect((refreshCall[1]!.headers as Record<string, string>)['X-Ikimina-Csrf']).toBe('1');
    expect(((fetchMock.mock.calls[2]![1]!.headers) as Record<string, string>).Authorization).toBe('Bearer fresh');
  });

  it('signs out when the refresh is refused, without retrying forever', async () => {
    acceptTokens({ accessToken: 'expired', tokenType: 'Bearer', expiresIn: 900 });
    fetchMock.mockResolvedValueOnce(problem(401, 'UNAUTHENTICATED')).mockResolvedValueOnce(problem(401, 'INVALID_REFRESH_TOKEN'));

    await expect(api('GET', '/me')).rejects.toBeInstanceOf(ApiError);
    expect(isSignedIn()).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('shares one refresh between concurrent callers', async () => {
    let release: (r: Response) => void = () => {};
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => (release = resolve)));

    const first = refreshSession();
    const second = refreshSession();
    release(json(200, { accessToken: 'shared', tokenType: 'Bearer', expiresIn: 900 }));

    await expect(Promise.all([first, second])).resolves.toEqual([true, true]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('reports a network failure as a TypeError, not a server problem', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(api('GET', '/me')).rejects.toBeInstanceOf(TypeError);
  });
});
