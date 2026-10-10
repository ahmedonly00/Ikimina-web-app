import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from 'react';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router';
import { acceptTokens, forgetSession } from '../../api/client';
import type { GroupView, LoanProductView } from '../../api/types';
import { SessionProvider } from '../../auth/session';
import i18n from '../../i18n';
import { LoanProductsPage } from './LoanProductsPage';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const group = { groupId: 'g1', name: 'Twisungane', myRole: 'TREASURER', myMemberId: 'm-treasurer' } as GroupView;

function product(rate: string, version: number): LoanProductView {
  return {
    productId: 'p1',
    name: 'Standard',
    status: 'ACTIVE',
    version,
    pendingChange: null,
    terms: {
      interestMethod: 'FLAT',
      interestRatePercent: rate,
      interestPeriod: 'MONTH',
      minAmount: null,
      maxAmount: null,
      maxMultipleOfSavings: null,
      minTermMonths: 1,
      maxTermMonths: 12,
      repaymentFrequency: 'MONTHLY',
      graceDays: 0,
      dualApprovalThreshold: null,
      allocationOrder: ['FINES', 'INTEREST', 'PRINCIPAL'],
      allowConcurrentLoans: false,
    },
  };
}

describe('LoanProductsPage', () => {
  const fetchMock = vi.fn<typeof fetch>();
  let client: QueryClient;

  beforeEach(async () => {
    forgetSession();
    acceptTokens({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 900 });
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    await act(() => i18n.changeLanguage('en'));
  });

  afterEach(() => vi.unstubAllGlobals());

  function renderPage() {
    return render(
      <QueryClientProvider client={client}>
        <SessionProvider>
          <MemoryRouter initialEntries={['/groups/g1/loan-products']}>
            <Routes>
              <Route path="/groups/:groupId" element={<Outlet context={group} />}>
                <Route path="loan-products" element={<LoanProductsPage />} />
              </Route>
            </Routes>
          </MemoryRouter>
        </SessionProvider>
      </QueryClientProvider>,
    );
  }

  it('shows a 10% rate as 10%, and edits start from the terms as they are now', async () => {
    fetchMock.mockImplementation(async () => json(200, [product('10.0000', 0)]));
    renderPage();
    expect(await screen.findByText('10% a month (Flat)')).toBeInTheDocument();

    // Another officer's change is confirmed and the list refreshes, without this card remounting.
    fetchMock.mockImplementation(async () => json(200, [product('4.5000', 1)]));
    await act(() => client.invalidateQueries({ queryKey: ['loan-products', 'g1'] }));
    expect(await screen.findByText('4.5% a month (Flat)')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Change money terms' }));
    expect(screen.getByLabelText('Interest rate (%)')).toHaveValue('4.5');
  });

  it('refuses terms the server has no schedule for, before sending anything', async () => {
    fetchMock.mockImplementation(async () => json(200, [product('5', 0)]));
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Change money terms' }));
    await userEvent.selectOptions(screen.getByLabelText('How interest is charged'), 'REDUCING_BALANCE');
    await userEvent.selectOptions(screen.getByLabelText('Repayments'), 'AT_MATURITY');
    await userEvent.click(screen.getByRole('button', { name: 'Propose change' }));

    expect(await screen.findByText(/needs a monthly rate and monthly repayments/)).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PATCH')).toBe(false);
  });
});
