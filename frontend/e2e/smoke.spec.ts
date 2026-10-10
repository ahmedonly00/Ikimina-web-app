import { expect, test, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { freshPhone, newAccountPassword } from './accounts';
import { latestOtp } from './otp';

async function inEnglish(page: Page) {
  await page.getByLabel(/language/i).first().selectOption('en');
}

test('register, verify, start a group, sign out and back in', async ({ page }) => {
  const phone = freshPhone();
  const local = `0${phone.slice(4)}`;
  const password = newAccountPassword();
  const groupName = `Smoke ${Date.now()}`;

  await page.goto('/register');
  await inEnglish(page);

  await page.getByLabel('Full name').fill('Smoke Tester');
  await page.getByLabel('Phone number').fill(local);
  await page.getByLabel('Password').fill(password);
  await page.getByLabel(/I accept the terms/).check();
  await page.getByRole('button', { name: 'Send me a code' }).click();

  await expect(page.getByRole('heading', { name: 'Enter your code' })).toBeVisible();
  await expect.poll(() => latestOtp(phone), { timeout: 15_000 }).toMatch(/^[0-9]{6}$/);
  await page.getByLabel('Code').fill(latestOtp(phone));
  await page.getByRole('button', { name: 'Verify' }).click();

  await expect(page.getByRole('heading', { name: 'My groups' })).toBeVisible();
  await page.getByRole('link', { name: 'Start a new group' }).click();
  await page.getByLabel('Group name').fill(groupName);
  await page.getByRole('button', { name: 'Create group' }).click();

  await expect(page.getByRole('heading', { name: groupName })).toBeVisible();
  await expect(page.getByText('President').first()).toBeVisible();

  await page.getByRole('link', { name: 'Rules' }).click();
  await expect(page.getByLabel('Fee when a member leaves (RWF)')).toHaveValue('0');

  // Phase 2: the President sets up a savings fund; their own savings start at zero.
  await page.getByRole('link', { name: 'Funds' }).click();
  await page.getByRole('button', { name: 'New fund' }).click();
  await page.getByLabel('Name', { exact: true }).fill('Monthly savings');
  await page.getByLabel('Starts on').fill(new Date().toISOString().slice(0, 10));
  await page.getByLabel('Contribution amount (RWF)').fill('5000');
  await page.getByRole('button', { name: 'Create fund' }).click();
  await expect(page.getByRole('heading', { name: 'Monthly savings' })).toBeVisible();

  await page.getByRole('link', { name: 'Savings', exact: true }).click();
  const balances = page.getByRole('region', { name: 'Total savings' });
  await expect(balances).toBeVisible();
  // The fund also appears under "What is owed" once its first due amount exists, so look among the balances.
  await expect(balances.getByText('Monthly savings')).toBeVisible();

  // Phase 3: a loan product, then a loan request the group cannot fund yet - the reason is shown.
  await page.getByRole('link', { name: 'Loan products' }).click();
  await page.getByRole('button', { name: 'New loan product' }).click();
  await page.getByLabel('Name', { exact: true }).fill('Standard loan');
  await page.getByRole('button', { name: 'Create product' }).click();
  await expect(page.getByRole('heading', { name: 'Standard loan' })).toBeVisible();

  await page.getByRole('link', { name: 'Loans', exact: true }).click();
  await page.getByRole('link', { name: 'Request a loan' }).click();
  await page.getByLabel('Loan product').selectOption({ label: 'Standard loan' });
  await page.getByLabel('Amount (RWF)').fill('10000');
  await page.getByLabel('Months to repay').fill('3');
  await page.getByRole('button', { name: 'Send request' }).click();
  await expect(page.getByRole('alert')).toContainText('available to lend');

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();

  await page.getByLabel('Phone number').fill(local);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  // After a deliberate sign-out, sign-in starts from My groups - not the previous page.
  await expect(page.getByRole('heading', { name: 'My groups' })).toBeVisible();
  await expect(page.getByRole('link', { name: new RegExp(groupName) })).toBeVisible();
});

test('a wrong password is refused without revealing whether the number exists', async ({ page }) => {
  await page.goto('/login');
  await inEnglish(page);
  await page.getByLabel('Phone number').fill(`0${freshPhone().slice(4)}`);
  await page.getByLabel('Password').fill(randomUUID());
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toContainText('incorrect');
});

test('the language toggle relabels the page', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel(/language/i).first().selectOption('rw');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('[rw-todo]');
  await page.getByLabel(/language/i).first().selectOption('en');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Sign in');
});
