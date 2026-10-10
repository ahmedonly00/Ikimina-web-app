import { expect, test, type Browser, type Page } from '@playwright/test';
import { freshPhone, newAccountPassword } from './accounts';
import { latestInvitationPath, latestOtp } from './otp';

/**
 * Phase 3 end to end, through the screens only, with four real accounts in separate browsers:
 * a President starts a group, invites two people and makes one of them Treasurer; the Treasurer
 * records a member's savings; the member asks for a loan; President and Treasurer approve it; the
 * Treasurer hands over the money and records repayments until it is fully repaid.
 */

interface Person {
  page: Page;
  phone: string;
  name: string;
}

async function register(browser: Browser, name: string): Promise<Person> {
  const page = await (await browser.newContext()).newPage();
  const phone = freshPhone();
  await page.goto('/register');
  await page.getByLabel(/language/i).first().selectOption('en');
  await page.getByLabel('Full name').fill(name);
  await page.getByLabel('Phone number').fill(`0${phone.slice(4)}`);
  await page.getByLabel('Password').fill(newAccountPassword());
  await page.getByLabel(/I accept the terms/).check();
  await page.getByRole('button', { name: 'Send me a code' }).click();
  await expect(page.getByRole('heading', { name: 'Enter your code' })).toBeVisible();
  await expect.poll(() => latestOtp(phone), { timeout: 15_000 }).toMatch(/^[0-9]{6}$/);
  await page.getByLabel('Code').fill(latestOtp(phone));
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page.getByRole('heading', { name: 'My groups' })).toBeVisible();
  return { page, phone, name };
}

async function joinByInvitation(person: Person) {
  await expect.poll(() => latestInvitationPath(person.phone), { timeout: 15_000 }).toContain('token=');
  await person.page.goto(latestInvitationPath(person.phone));
  await person.page.getByRole('button', { name: 'Join the group' }).click();
}

async function openTab(page: Page, groupUrl: string, tab: string) {
  await page.goto(groupUrl);
  await page.getByRole('link', { name: tab, exact: true }).click();
}

test('a loan from request to fully repaid, with every role doing its part', async ({ browser }) => {
  test.setTimeout(180_000);
  const stamp = Date.now();
  const president = await register(browser, `President ${stamp}`);
  const treasurer = await register(browser, `Treasurer ${stamp}`);
  const member = await register(browser, `Borrower ${stamp}`);

  // The President starts the group and invites the other two.
  const p = president.page;
  await p.getByRole('link', { name: 'Start a new group' }).click();
  await p.getByLabel('Group name').fill(`Loan day ${stamp}`);
  await p.getByRole('button', { name: 'Create group' }).click();
  await expect(p.getByRole('heading', { name: `Loan day ${stamp}` })).toBeVisible();
  const groupUrl = p.url();

  for (const invitee of [treasurer, member]) {
    await openTab(p, groupUrl, 'Invite');
    await p.getByLabel('Phone number').fill(`0${invitee.phone.slice(4)}`);
    await p.getByRole('button', { name: 'Send invitation' }).click();
    await expect(p.getByText(/Invitation sent/)).toBeVisible();
    await joinByInvitation(invitee);
    await expect(invitee.page.getByRole('heading', { name: `Loan day ${stamp}` })).toBeVisible();
  }

  // The President makes one of them Treasurer.
  await openTab(p, groupUrl, 'Members');
  const treasurerRow = p.getByRole('listitem').filter({ hasText: treasurer.name });
  await treasurerRow.getByRole('button', { name: 'Manage' }).click();
  await treasurerRow.getByLabel('Change role').selectOption('TREASURER');
  await treasurerRow.getByRole('button', { name: 'Save' }).click();
  await expect(treasurerRow.getByText('Member updated.')).toBeVisible();

  // A savings fund and a loan product: 5% a month, flat, 1-12 months, two approvals always.
  await openTab(p, groupUrl, 'Funds');
  await p.getByRole('button', { name: 'New fund' }).click();
  await p.getByLabel('Name', { exact: true }).fill('Ubwizigame');
  await p.getByLabel('Starts on').fill(new Date().toISOString().slice(0, 10));
  await p.getByLabel('Contribution amount (RWF)').fill('5000');
  await p.getByRole('button', { name: 'Create fund' }).click();
  await expect(p.getByRole('heading', { name: 'Ubwizigame' })).toBeVisible();
  await openTab(p, groupUrl, 'Loan products');
  await p.getByRole('button', { name: 'New loan product' }).click();
  await p.getByLabel('Name', { exact: true }).fill('Standard loan');
  await p.getByRole('button', { name: 'Create product' }).click();
  await expect(p.getByRole('heading', { name: 'Standard loan' })).toBeVisible();

  // The Treasurer records the member's savings: 100,000 RWF in cash.
  const t = treasurer.page;
  await openTab(t, groupUrl, 'Record payment');
  const memberOption = await t.getByLabel('Member').locator('option', { hasText: member.name }).getAttribute('value');
  expect(memberOption).toBeTruthy();
  await t.getByLabel('Member').selectOption(memberOption ?? '');
  await t.getByLabel('Fund').selectOption({ label: 'Ubwizigame' });
  await t.getByLabel('Amount (RWF)').fill('100000');
  await t.getByRole('button', { name: 'Record payment' }).click();
  await expect(t.getByText(/Recorded 100,000 RWF/)).toBeVisible();

  // The member asks for 30,000 RWF over 3 months.
  const m = member.page;
  await openTab(m, groupUrl, 'Loans');
  await m.getByRole('link', { name: 'Request a loan' }).click();
  await m.getByLabel('Loan product').selectOption({ label: 'Standard loan' });
  await m.getByLabel('Amount (RWF)').fill('30000');
  await m.getByLabel('Months to repay').fill('3');
  await m.getByRole('button', { name: 'Send request' }).click();
  await expect(m.getByRole('heading', { name: '30,000 RWF' })).toBeVisible();
  await expect(m.getByText('Waiting for approval')).toBeVisible();
  await expect(m.getByRole('button', { name: 'Approve loan' })).toHaveCount(0);
  const loanUrl = m.url();

  // The President approves first, then the Treasurer.
  await p.goto(loanUrl);
  await expect(p.getByText('Waiting for: President or Treasurer')).toBeVisible();
  await p.getByRole('button', { name: 'Approve loan' }).click();
  await expect(p.getByText('Partly approved')).toBeVisible();
  await t.goto(loanUrl);
  await t.getByRole('button', { name: 'Approve loan' }).click();
  await expect(t.getByText('Approved', { exact: true }).first()).toBeVisible();

  // The Treasurer hands over the money: the schedule appears, 3 x (10,000 + 1,500).
  await t.getByRole('button', { name: 'Record hand-over' }).click();
  await expect(t.getByText('Being repaid')).toBeVisible();
  await expect(t.getByText('Still owed: 34,500 RWF')).toBeVisible();
  const rows = t.getByRole('table').getByRole('row');
  await expect(rows).toHaveCount(4);
  await expect(rows.nth(1)).toContainText('10,000 RWF');
  await expect(rows.nth(1)).toContainText('1,500 RWF');

  // Repayments: too much is refused; then 12,000 and the rest, 22,500.
  await t.getByLabel('Amount (RWF)').fill('40000');
  await t.getByRole('button', { name: 'Record repayment' }).click();
  await expect(t.getByRole('alert')).toContainText('34500.00');
  await t.getByLabel('Amount (RWF)').fill('12000');
  await t.getByRole('button', { name: 'Record repayment' }).click();
  await expect(t.getByText('Recorded 12,000 RWF. Still owed: 22,500 RWF.')).toBeVisible();
  await t.getByLabel('Amount (RWF)').fill('22500');
  await t.getByRole('button', { name: 'Record repayment' }).click();
  await expect(t.getByText('Fully repaid')).toBeVisible();

  // The borrower sees the same, and the group's records hold up.
  await m.reload();
  await expect(m.getByText('Fully repaid')).toBeVisible();
  await expect(m.getByText('12,000 RWF', { exact: true })).toBeVisible();
  test.info().annotations.push({ type: 'group', description: groupUrl.split('/groups/')[1]?.split('/')[0] ?? '' });
});
