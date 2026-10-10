import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from 'react';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router';
import { acceptTokens, forgetSession } from '../../api/client';
import type { GroupRole, GroupView, LoanView } from '../../api/types';
import { SessionProvider } from '../../auth/session';
import i18n from '../../i18n';
import { LoanDetailPage } from './LoanDetailPage';
import { RequestLoanPage } from './LoansPage';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const groupAs = (role: GroupRole, memberId = 'm-officer') => ({ groupId: 'g1', name: 'Twisungane', myRole: role, myMemberId: memberId }) as GroupView;

function renderAt(path: string, group: GroupView) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SessionProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/groups/:groupId" element={<Outlet context={group} />}>
              <Route path="loans/new" element={<RequestLoanPage />} />
              <Route path="loans/:loanId" element={<LoanDetailPage />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  );
}

const product = {
  productId: 'p1',
  name: 'Standard',
  status: 'ACTIVE',
  version: 0,
  pendingChange: null,
  terms: {
    interestMethod: 'FLAT',
    interestRatePercent: '10.0000',
    interestPeriod: 'MONTH',
    minAmount: null,
    maxAmount: null,
    maxMultipleOfSavings: '2.00',
    minTermMonths: 1,
    maxTermMonths: 12,
    repaymentFrequency: 'MONTHLY',
    graceDays: 0,
    dualApprovalThreshold: null,
    allocationOrder: ['FINES', 'INTEREST', 'PRINCIPAL'],
    allowConcurrentLoans: false,
  },
};

function loan(overrides: Partial<LoanView>): LoanView {
  return {
    loanId: 'l1',
    productId: 'p1',
    productName: 'Standard',
    borrower: { memberId: 'm-borrower', memberNumber: '007', fullName: 'Aline' },
    purpose: null,
    principal: '30000.00',
    termMonths: 3,
    interestMethod: 'FLAT',
    interestRatePercent: '10.0000',
    interestPeriod: 'MONTH',
    repaymentFrequency: 'MONTHLY',
    graceDays: 0,
    status: 'SUBMITTED',
    requiredApprovals: 2,
    approvals: [],
    waitingFor: ['PRESIDENT', 'TREASURER'],
    requestedAt: '2026-10-01T08:00:00Z',
    approvedAt: null,
    disbursedAt: null,
    maturesOn: null,
    settledAt: null,
    rejectionReason: null,
    outstanding: { principal: '0.00', interest: '0.00', total: '0.00' },
    repayments: [],
    disbursedByBorrower: false,
    version: 0,
    ...overrides,
  };
}

const schedule = { loanId: 'l1', installments: [], outstanding: { principal: '0.00', interest: '0.00', total: '0.00' } };

describe('loans', () => {
  const fetchMock = vi.fn<typeof fetch>();
  const calls = (suffix: string) => fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith(suffix) && init?.method === 'POST');

  beforeEach(async () => {
    forgetSession();
    acceptTokens({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 900 });
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    await act(() => i18n.changeLanguage('en'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('shows every reason the group refuses a loan request', async () => {
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input).endsWith('/loan-products')) {
        return json(200, [product]);
      }
      if (init?.method === 'POST') {
        return json(422, {
          status: 422,
          code: 'LOAN_NOT_ELIGIBLE',
          title: 'Loan not possible',
          detail: 'This loan request does not meet the group rules.',
          reasons: [
            { code: 'ABOVE_SAVINGS_LIMIT', message: 'The amount is above the limit of 10000.00 RWF.' },
            { code: 'TERM_TOO_LONG', message: 'The term is longer than 12 months.' },
          ],
        });
      }
      throw new Error(`unexpected ${String(input)}`);
    });
    renderAt('/groups/g1/loans/new', groupAs('MEMBER'));

    await userEvent.selectOptions(await screen.findByLabelText('Loan product'), 'p1');
    expect(screen.getByText(/10% a month/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Amount (RWF)'), '60,000');
    await userEvent.type(screen.getByLabelText('Months to repay'), '24');
    await userEvent.click(screen.getByRole('button', { name: 'Send request' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('The amount is above the limit of 10000.00 RWF.');
    expect(alert).toHaveTextContent('The term is longer than 12 months.');
    expect(JSON.parse(calls('/loans')[0]![1]!.body as string)).toEqual({ productId: 'p1', amount: '60000', termMonths: 24 });
  });

  it('offers the decision only to an officer the loan is waiting for, who has not decided yet', async () => {
    fetchMock.mockImplementation(async () => json(200, loan({})));
    const officer = renderAt('/groups/g1/loans/l1', groupAs('PRESIDENT'));
    expect(await screen.findByRole('button', { name: 'Approve loan' })).toBeInTheDocument();
    expect(screen.getByText('Waiting for: President or Treasurer')).toBeInTheDocument();
    expect(screen.getByText(/10% a month/)).toBeInTheDocument();
    officer.unmount();

    // The Secretary only stands in for a borrowing officer, so not on an ordinary member's loan.
    const secretary = renderAt('/groups/g1/loans/l1', groupAs('SECRETARY'));
    expect(await screen.findByText('Waiting for: President or Treasurer')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve loan' })).not.toBeInTheDocument();
    secretary.unmount();

    // The President has approved; the loan now waits for the Treasurer only.
    fetchMock.mockImplementation(async () =>
      json(
        200,
        loan({
          status: 'PARTIALLY_COUNTERSIGNED',
          waitingFor: ['TREASURER'],
          approvals: [{ memberId: 'm-officer', role: 'PRESIDENT', decision: 'APPROVE', comment: null, decidedAt: '2026-10-02T08:00:00Z' }],
        }),
      ),
    );
    const approved = renderAt('/groups/g1/loans/l1', groupAs('PRESIDENT'));
    expect(await screen.findByText('Waiting for: Treasurer')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve loan' })).not.toBeInTheDocument();
    approved.unmount();

    renderAt('/groups/g1/loans/l1', groupAs('TREASURER', 'm-borrower'));
    expect(await screen.findByRole('button', { name: 'Withdraw my request' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve loan' })).not.toBeInTheDocument();
  });

  it('retries a repayment with the same Idempotency-Key and uses a new one for the next', async () => {
    const owed = loan({ status: 'DISBURSED', outstanding: { principal: '30000.00', interest: '4500.00', total: '34500.00' } });
    let attempt = 0;
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/schedule')) {
        return json(200, schedule);
      }
      if (url.endsWith('/repayments') && init?.method === 'POST') {
        attempt += 1;
        if (attempt === 1) {
          throw new TypeError('Failed to fetch');
        }
        return json(201, owed);
      }
      return json(200, owed);
    });
    renderAt('/groups/g1/loans/l1', groupAs('TREASURER'));

    await userEvent.type(await screen.findByLabelText('Amount (RWF)'), '5000');
    await userEvent.click(screen.getByRole('button', { name: 'Record repayment' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Record repayment' }));
    expect(await screen.findByText(/Recorded 5,000 RWF/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Amount (RWF)'), '1000');
    await userEvent.click(screen.getByRole('button', { name: 'Record repayment' }));
    await screen.findByText(/Recorded 1,000 RWF/);

    const [first, retry, next] = calls('/repayments');
    const key = (call: Parameters<typeof fetch>) => (call[1]!.headers as Record<string, string>)['Idempotency-Key'];
    expect(key(retry!)).toBe(key(first!));
    expect(key(next!)).not.toBe(key(first!));
    expect(screen.queryByRole('button', { name: 'Hand over the money' })).not.toBeInTheDocument();
  });
});
