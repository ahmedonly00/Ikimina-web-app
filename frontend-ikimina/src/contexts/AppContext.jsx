import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import i18n, {
  SUPPORTED_LANGUAGES,
  changeLanguageSafely,
  formatCurrency,
  formatDate,
} from '../i18n';

/**
 * Application-wide language and theme.
 *
 * The three translation dictionaries that used to live here inline - 540 strings
 * across 758 lines - now live in src/i18n/locales as JSON, loaded by i18next.
 * This context stays as the public API so no component had to change, but the
 * lookup, pluralisation, interpolation and fallback behaviour are i18next's.
 *
 * Also exposed here: formatCurrency and formatDate, so amounts and dates are
 * rendered in the member's own locale rather than a hardcoded format. RWF is
 * shown in whole francs, which is how it is actually used.
 */
const AppContext = createContext();

export const useAppContext = () => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useAppContext must be used within an AppProvider');
  }
  return context;
};

const DOCUMENT_TITLES = {
  en: 'Ikimina Management System',
  fr: 'Système de Gestion Ikimina',
  rw: 'Sisitemu yo Gucunga Ikimina',
};

export const AppProvider = ({ children }) => {
  // i18next owns the language and its persistence; this mirrors it for consumers.
  const { t, i18n: instance } = useTranslation();
  const language = instance.language || 'en';

  const [theme, setTheme] = useState(() => {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme) {
      return savedTheme;
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });

  const resolvedTheme = useCallback(
    value =>
      value === 'system'
        ? window.matchMedia('(prefers-color-scheme: dark)').matches
          ? 'dark'
          : 'light'
        : value,
    []
  );

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('light', 'dark');
    root.classList.add(resolvedTheme(theme));

    localStorage.setItem('theme', theme);

    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme) {
      metaTheme.content = resolvedTheme(theme) === 'dark' ? '#1f2937' : '#ffffff';
    }
  }, [theme, resolvedTheme]);

  useEffect(() => {
    if (theme !== 'system') return undefined;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = e => {
      const root = document.documentElement;
      root.classList.remove('light', 'dark');
      root.classList.add(e.matches ? 'dark' : 'light');

      const metaTheme = document.querySelector('meta[name="theme-color"]');
      if (metaTheme) {
        metaTheme.content = e.matches ? '#1f2937' : '#ffffff';
      }
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [theme]);

  // Keep the document in step with the language, which matters for screen
  // readers and for the browser choosing hyphenation and fonts.
  useEffect(() => {
    document.documentElement.lang = language;
    document.title = DOCUMENT_TITLES[language] || DOCUMENT_TITLES.en;
  }, [language]);

  const changeLanguage = useCallback(async newLanguage => {
    // Fetches the dictionary before switching, so the UI never flashes raw
    // translation keys on a slow connection.
    const switched = await changeLanguageSafely(newLanguage);
    if (switched) {
      toast.success(i18n.t('changesSaved'));
    } else {
      toast.error('Could not load that language. Check your connection.');
    }
  }, []);
  const changeTheme = useCallback(newTheme => {
    setTheme(newTheme);
    toast.success(i18n.t('changesSaved'));
  }, []);

  const value = {
    language,
    languages: SUPPORTED_LANGUAGES,
    theme,
    t,
    changeLanguage,
    changeTheme,
    // Locale-aware formatting, bound to the current language so callers do not
    // have to pass it every time.
    formatCurrency: amount => formatCurrency(amount, language),
    formatDate: (date, options) => formatDate(date, language, options),
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
};

export default AppContext;
