import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

// Only the fallback dictionary is bundled. The others are fetched when a member
// selects them, so a first visit on 2G does not pay for three languages it will
// not use. Bundling all three added ~22 kB gzip to the critical path.
import en from './locales/en.json';

const lazyLoaders = {
  fr: () => import('./locales/fr.json'),
  rw: () => import('./locales/rw.json'),
};

/**
 * Localisation setup.
 *
 * Replaces a hand-rolled 758-line dictionary in a React context. What i18next
 * brings that the context could not:
 *
 *  - pluralisation rules per language, rather than string concatenation;
 *  - interpolation, so a translator can reorder a sentence;
 *  - a detected language persisted per device;
 *  - a real fallback chain, instead of rendering the key when a string is missing.
 *
 * Kinyarwanda is the primary audience language and is listed first. The existing
 * rw strings were carried over verbatim; several read as machine-translated and
 * need a native speaker to review them - the infrastructure is correct, the copy
 * is not yet.
 */

export const SUPPORTED_LANGUAGES = [
  { code: 'rw', name: 'Kinyarwanda', nativeName: 'Ikinyarwanda' },
  { code: 'en', name: 'English', nativeName: 'English' },
  { code: 'fr', name: 'French', nativeName: 'Français' },
];

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
    },
    // Render the fallback rather than a bare key while a dictionary is in flight.
    partialBundledLanguages: true,
    // English is the fallback because it is the most complete, not because it
    // is the primary audience language.
    fallbackLng: 'en',
    supportedLngs: SUPPORTED_LANGUAGES.map(l => l.code),
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'ikimina-language',
      caches: ['localStorage'],
    },
    interpolation: {
      // React escapes for us; double-escaping mangles apostrophes, which are
      // common in French and Kinyarwanda.
      escapeValue: false,
    },
    returnEmptyString: false,
  });

/**
 * Loads a language dictionary if it is not already present.
 *
 * Safe to call repeatedly: i18next keeps loaded bundles, and a failed fetch
 * leaves the fallback in place rather than blanking the UI.
 */
export const ensureLanguageLoaded = async code => {
  if (code === 'en' || i18n.hasResourceBundle(code, 'translation')) {
    return true;
  }
  const load = lazyLoaders[code];
  if (!load) return false;

  try {
    const module = await load();
    i18n.addResourceBundle(code, 'translation', module.default, true, true);
    return true;
  } catch {
    // Offline, or the chunk failed: stay on the current language rather than
    // showing raw translation keys to a member.
    return false;
  }
};

/** Switches language, fetching its dictionary first so nothing flashes as a key. */
export const changeLanguageSafely = async code => {
  const ok = await ensureLanguageLoaded(code);
  if (!ok && code !== 'en') {
    return false;
  }
  await i18n.changeLanguage(code);
  return true;
};

// The detector may have picked a lazy language before this module finished, so
// make sure its dictionary is fetched.
if (i18n.language && i18n.language !== 'en') {
  ensureLanguageLoaded(i18n.language);
}

export default i18n;

/**
 * Formats an amount as Rwandan francs.
 *
 * RWF is used in whole francs, so no decimal places are shown - rendering
 * "5,000.00 RWF" to a member reads as a foreign currency. The value itself is
 * still exact end to end; this is presentation only.
 */
export const formatCurrency = (amount, language = i18n.language) => {
  if (amount === null || amount === undefined || amount === '') return '';
  const numeric = typeof amount === 'number' ? amount : Number(amount);
  if (Number.isNaN(numeric)) return String(amount);

  try {
    return new Intl.NumberFormat(localeFor(language), {
      style: 'currency',
      currency: 'RWF',
      // ICU's short symbol for RWF is "RF", which is easy to misread and is
      // not what Rwandan statements use. The ISO code is unambiguous.
      currencyDisplay: 'code',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(numeric);
  } catch {
    return `${Math.round(numeric).toLocaleString()} RWF`;
  }
};

/** Date in the member's own locale rather than a fixed en-US format. */
export const formatDate = (value, language = i18n.language, options) => {
  if (!value) return '';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);

  try {
    return new Intl.DateTimeFormat(
      localeFor(language),
      options || { year: 'numeric', month: 'short', day: 'numeric' }
    ).format(date);
  } catch {
    return date.toISOString().slice(0, 10);
  }
};

/**
 * Maps a language to a BCP 47 locale.
 *
 * rw-RW has patchy ICU coverage in some runtimes, so it falls back to en-RW,
 * which still gives Rwandan date order and grouping even where Kinyarwanda
 * month names are unavailable.
 */
const localeFor = language => {
  switch (language) {
    case 'rw':
      return ['rw-RW', 'en-RW'];
    case 'fr':
      return 'fr-RW';
    default:
      return 'en-RW';
  }
};
