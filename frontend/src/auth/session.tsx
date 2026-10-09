import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { acceptTokens, ApiError, forgetSession, isSignedIn, onSessionChange, post, refreshSession } from '../api/client';
import { Button, ErrorMessage, TextField } from '../components/ui';

type Status = 'restoring' | 'signedIn' | 'signedOut';

interface SessionValue {
  status: Status;
  /** True after the user signed out themselves (as opposed to a session that expired). */
  signedOutDeliberately: boolean;
  signOut: () => Promise<void>;
  /**
   * Runs a sensitive action; if the server answers REAUTHENTICATION_REQUIRED (spec 16.1),
   * asks for the password, re-authenticates, and runs the action once more.
   */
  withStepUp: <T>(action: () => Promise<T>) => Promise<T>;
}

const SessionContext = createContext<SessionValue | null>(null);

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error('useSession outside SessionProvider');
  }
  return value;
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>(isSignedIn() ? 'signedIn' : 'restoring');
  const [signedOutDeliberately, setSignedOutDeliberately] = useState(false);
  const [prompt, setPrompt] = useState<{ resolve: (ok: boolean) => void } | null>(null);
  const queryClient = useQueryClient();

  useEffect(() => {
    const unsubscribe = onSessionChange((signedIn) => {
      setStatus(signedIn ? 'signedIn' : 'signedOut');
      if (signedIn) {
        setSignedOutDeliberately(false);
      } else {
        queryClient.clear();
      }
    });
    if (!isSignedIn()) {
      // Restore a session from the refresh cookie after a reload.
      void refreshSession().then((ok) => setStatus(ok ? 'signedIn' : 'signedOut'));
    }
    return unsubscribe;
  }, [queryClient]);

  const signOut = useCallback(async () => {
    setSignedOutDeliberately(true);
    try {
      await post('/auth/logout');
    } finally {
      forgetSession();
      queryClient.clear();
    }
  }, [queryClient]);

  const withStepUp = useCallback(async <T,>(action: () => Promise<T>): Promise<T> => {
    try {
      return await action();
    } catch (error) {
      if (!(error instanceof ApiError) || error.code !== 'REAUTHENTICATION_REQUIRED') {
        throw error;
      }
      const confirmed = await new Promise<boolean>((resolve) => setPrompt({ resolve }));
      setPrompt(null);
      if (!confirmed) {
        throw error;
      }
      return action();
    }
  }, []);

  return (
    <SessionContext.Provider value={{ status, signedOutDeliberately, signOut, withStepUp }}>
      {children}
      {prompt && <ReauthDialog onDone={prompt.resolve} />}
    </SessionContext.Provider>
  );
}

function ReauthDialog({ onDone }: { onDone: (ok: boolean) => void }) {
  const { t } = useTranslation();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    dialog.current?.showModal?.();
  }, []);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      acceptTokens(await post('/auth/reauth', { password }));
      onDone(true);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby="reauth-title"
      onCancel={() => onDone(false)}
      className="m-auto w-[min(28rem,calc(100%-2rem))] rounded-xl border border-line bg-card p-0 backdrop:bg-ink/40"
    >
      <form onSubmit={submit} className="flex flex-col gap-3 p-4">
        <h2 id="reauth-title" className="text-lg font-bold">
          {t('auth.reauth.title')}
        </h2>
        <p className="text-muted">{t('auth.reauth.intro')}</p>
        <ErrorMessage error={error} />
        <TextField
          label={t('auth.reauth.password')}
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoFocus
          required
        />
        <div className="flex gap-2">
          <Button type="submit" busy={busy}>
            {t('auth.reauth.submit')}
          </Button>
          <Button type="button" variant="ghost" onClick={() => onDone(false)}>
            {t('common.cancel')}
          </Button>
        </div>
      </form>
    </dialog>
  );
}
