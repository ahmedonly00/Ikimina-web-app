import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from 'react';
import { MemoryRouter } from 'react-router';
import { forgetSession } from '../../api/client';
import i18n from '../../i18n';
import { LoginPage } from './LoginPage';

function renderLogin() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(async () => {
    forgetSession();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    await act(() => i18n.changeLanguage('en'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('validates locally before calling the server, in the chosen language', async () => {
    renderLogin();
    await userEvent.type(screen.getByLabelText('Phone number'), '12345');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Enter a Rwandan mobile number, for example 0788 123 456.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends the normalised phone number and shows the server\'s own message on failure', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ status: 401, code: 'INVALID_CREDENTIALS', title: 'Sign-in failed', detail: 'The phone number or password is incorrect.' }),
        { status: 401 },
      ),
    );
    const typed = crypto.randomUUID();
    renderLogin();
    await userEvent.type(screen.getByLabelText('Phone number'), '0788 123 456');
    await userEvent.type(screen.getByLabelText('Password'), typed);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The phone number or password is incorrect.');
    expect(JSON.parse(fetchMock.mock.calls[0]![1]!.body as string)).toEqual({ phone: '+250788123456', password: typed });
  });

  it('switches every label when the language changes', async () => {
    renderLogin();
    await act(() => i18n.changeLanguage('rw'));
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('[rw-todo]');
  });
});
