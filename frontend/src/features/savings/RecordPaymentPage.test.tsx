import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from 'react';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router';
import { acceptTokens, forgetSession } from '../../api/client';
import type { GroupView } from '../../api/types';
import i18n from '../../i18n';
import { RecordPaymentPage } from './RecordPaymentPage';

const group = { groupId: 'g1', name: 'Twisungane', myRole: 'TREASURER', myMemberId: 'm-treasurer' } as GroupView;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/groups/g1/record']}>
        <Routes>
          <Route path="/groups/:groupId" element={<Outlet context={group} />}>
            <Route path="record" element={<RecordPaymentPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('RecordPaymentPage', () => {
  const fetchMock = vi.fn<typeof fetch>();
  const contributionCalls = () => fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/contributions'));
  const keyOf = (call: Parameters<typeof fetch>) => (call[1]!.headers as Record<string, string>)['Idempotency-Key'];

  beforeEach(async () => {
    forgetSession();
    acceptTokens({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 900 });
    fetchMock.mockReset();
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/members?')) {
        return json(200, {
          items: [{ memberId: 'm-1', memberNumber: '001', fullName: 'Aline', status: 'ACTIVE', role: 'MEMBER', phone: '+250788000001' }],
          page: 0,
          size: 200,
          totalItems: 1,
          totalPages: 1,
        });
      }
      if (url.endsWith('/buckets')) {
        return json(200, [{ bucketId: 'b-1', name: 'Monthly savings', status: 'ACTIVE' }]);
      }
      throw new Error(`unexpected ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    await act(() => i18n.changeLanguage('en'));
  });

  afterEach(() => vi.unstubAllGlobals());

  async function fill(amount: string) {
    await userEvent.selectOptions(await screen.findByLabelText('Member'), 'm-1');
    await userEvent.selectOptions(screen.getByLabelText('Fund'), 'b-1');
    await userEvent.clear(screen.getByLabelText('Amount (RWF)'));
    await userEvent.type(screen.getByLabelText('Amount (RWF)'), amount);
  }

  it('refuses part francs before calling the server', async () => {
    renderPage();
    await fill('1500.50');
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));

    expect(await screen.findByText(/whole/i)).toBeInTheDocument();
    expect(contributionCalls()).toHaveLength(0);
  });

  it('retries with the same Idempotency-Key and starts a new one after success', async () => {
    const recorded = {
      transactionId: 't-1',
      journalId: 'j-1',
      memberId: 'm-1',
      bucketId: 'b-1',
      amount: '5000.00',
      method: 'CASH',
      externalRef: null,
      businessDate: '2026-10-09',
      recordedAt: '2026-10-09T08:00:00Z',
      reversed: false,
      memberBalance: '5000.00',
    };
    const lookups = fetchMock.getMockImplementation()!;
    let attempt = 0;
    fetchMock.mockImplementation(async (input, init) => {
      if (!String(input).endsWith('/contributions')) {
        return lookups(input, init);
      }
      attempt += 1;
      // The first attempt is lost on the way back (a dropped connection).
      if (attempt === 1) {
        throw new TypeError('Failed to fetch');
      }
      return json(201, recorded);
    });

    renderPage();
    await fill('5,000');
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    expect(await screen.findByText(/Recorded .* for Aline/)).toBeInTheDocument();

    await fill('2000');
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    await screen.findByText(/Recorded .* for Aline/);

    const [first, retry, next] = contributionCalls();
    expect(JSON.parse(first![1]!.body as string)).toMatchObject({ memberId: 'm-1', bucketId: 'b-1', amount: '5000', method: 'CASH' });
    expect(keyOf(retry!)).toBe(keyOf(first!));
    expect(keyOf(next!)).not.toBe(keyOf(first!));
  });
});
