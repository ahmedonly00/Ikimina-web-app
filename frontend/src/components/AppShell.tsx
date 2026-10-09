import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useSession } from '../auth/session';
import { changeLanguage, LANGUAGES, type Language } from '../i18n';

/** Header with the language toggle on every screen (spec 18.1), and the page container. */
export function AppShell({ children }: { children: ReactNode }) {
  const { t, i18n } = useTranslation();
  const { status, signOut } = useSession();
  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:m-2 focus:rounded focus:bg-card focus:p-2">
        {t('nav.skipToContent')}
      </a>
      <header className="bg-brand text-on-brand">
        <div className="mx-auto flex max-w-2xl flex-wrap items-center gap-2 px-4 py-3">
          <Link to="/" className="mr-auto text-lg font-bold">
            {t('app.name')}
          </Link>
          <label className="flex items-center gap-1 text-sm">
            <span className="sr-only">{t('nav.language')}</span>
            <select
              value={i18n.language}
              onChange={(e) => changeLanguage(e.target.value as Language)}
              className="min-h-11 rounded-lg bg-brand-strong px-2 text-on-brand"
              aria-label={t('nav.language')}
            >
              {LANGUAGES.map((language) => (
                <option key={language} value={language}>
                  {t(`lang.${language}`)}
                </option>
              ))}
            </select>
          </label>
          {status === 'signedIn' && (
            <nav className="flex items-center gap-1 text-sm" aria-label={t('nav.myGroups')}>
              <Link to="/profile" className="inline-flex min-h-11 items-center rounded-lg px-2 hover:bg-brand-strong">
                {t('nav.profile')}
              </Link>
              <button type="button" onClick={() => void signOut()} className="min-h-11 rounded-lg px-2 hover:bg-brand-strong">
                {t('nav.signOut')}
              </button>
            </nav>
          )}
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-2xl flex-1 px-4 py-6">
        {children}
      </main>
    </div>
  );
}
