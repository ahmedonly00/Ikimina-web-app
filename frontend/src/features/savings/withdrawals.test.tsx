import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from 'react';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router';
import { acceptTokens, forgetSession } from '../../api/client';
import type { GroupRole, GroupView, WithdrawalView } from '../../api/types';
import { SessionProvider } from '../../auth/session';
import i18n from '../../i18n';
import { MyWithdrawals, WithdrawalsPage } from './WithdrawalsPage';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const groupAs = (role: GroupRole, memberId: string) => ({ groupId: 'g1', name: 'Twisungane', myRole: role, myMemberId: memberId }) as GroupView;

function renderWith(element: React.ReactNode, group: GroupView) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SessionProvider>
        <MemoryRouter initialEntries={['/groups/g1/x']}>
          <Routes>
            <Route path="/groups/:groupId" element={<Outlet context={group} />}>
              <Route path="x" element={element} />
            </Route>
          </Routes>
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  );
}

function withdrawal(overrides: Partial<WithdrawalView>): WithdrawalView {
  return {
    withdrawalId: 'w1',
    member: { memberId: 'm-member', memberNumber: '007', fullName: 'Aline' },
    bucketId: 'b1',
    bucketName: 'Ubwizigame',
    amount: '20000.00',
    reason: null,
    status: 'REQUESTED',
    requestedOn: '2026-10-01',
    earliestPayoutOn: '2026-10-01',
    requestedAt: '2026-10-01T08:00:00Z',
    decidedRole: null,
    decidedAt: null,
    decisionReason: null,
    paidAt: null,
    journalId: null,
    recordedByMember: false,
    reversed: false,
    ...overrides,
  };
}

const fund = {
  bucketId: 'b1',
  name: 'Ubwizigame',
  description: null,
  type: 'SAVINGS',
  cycleType: 'ROLLING',
  startDate: '2026-01-01',
  terms: { mandatory: false, minimumContribution: '0.00', contributionFrequency: 'ADHOC', withdrawable: true, endDate: null, latePenaltyRule: null },
  status: 'ACTIVE',
  version: 0,
  pendingChange: null,
};

describe('withdrawals', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(async () => {
    forgetSession();
    acceptTokens({ accessToken: 'token', tokenType: 'Bearer', expiresIn: 900 });
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    await act(() => i18n.changeLanguage('en'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('lets a member ask and shows every reason the group refuses', async () => {
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/buckets')) {
        return json(200, [fund]);
      }
      if (url.includes('/balances')) {
        return json(200, { memberId: 'm-member', buckets: [{ bucketId: 'b1', name: 'Ubwizigame', type: 'SAVINGS', balance: '15000.00' }], total: '15000.00' });
      }
      if (init?.method === 'POST') {
        return json(422, {
          status: 422,
          code: 'WITHDRAWAL_NOT_ALLOWED',
          title: 'Withdrawal not possible',
          detail: 'This withdrawal does not meet the group rules.',
          reasons: [
            { code: 'OPEN_LOAN_WITHDRAWAL', message: 'The member has a loan that is not finished.' },
            { code: 'ABOVE_BALANCE', message: 'The amount is more than the 15000.00 RWF available.' },
          ],
        });
      }
      return json(200, { items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 });
    });
    renderWith(<MyWithdrawals />, groupAs('MEMBER', 'm-member'));

    await userEvent.selectOptions(await screen.findByLabelText('From which fund'), 'b1');
    expect(screen.getByRole('option', { name: 'Ubwizigame (15,000 RWF)' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Amount (RWF)'), '20,000');
    await userEvent.click(screen.getByRole('button', { name: 'Ask to withdraw' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('The member has a loan that is not finished.');
    expect(alert).toHaveTextContent('15000.00 RWF available');
    const sent = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(sent![1]!.body as string)).toEqual({ bucketId: 'b1', amount: '20000' });
  });

  it('never offers an officer the approval of their own withdrawal', async () => {
    fetchMock.mockImplementation(async () =>
      json(200, { items: [withdrawal({ member: { memberId: 'm-treasurer', memberNumber: '002', fullName: 'Tess' } })], page: 0, size: 100, totalItems: 1, totalPages: 1 }),
    );
    const own = renderWith(<WithdrawalsPage />, groupAs('TREASURER', 'm-treasurer'));
    expect(await screen.findByText('Waiting for approval', { selector: 'span' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve withdrawal' })).not.toBeInTheDocument();
    own.unmount();

    renderWith(<WithdrawalsPage />, groupAs('PRESIDENT', 'm-president'));
    expect(await screen.findByRole('button', { name: 'Approve withdrawal' })).toBeInTheDocument();
  });

  it('holds the payout until the notice period ends', async () => {
    fetchMock.mockImplementation(async () =>
      json(200, { items: [withdrawal({ status: 'APPROVED', earliestPayoutOn: '2999-01-01' })], page: 0, size: 100, totalItems: 1, totalPages: 1 }),
    );
    renderWith(<WithdrawalsPage />, groupAs('TREASURER', 'm-treasurer'));

    expect(await screen.findByText(/cannot be paid out before then/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Record payout' })).toBeDisabled();
  });
});
