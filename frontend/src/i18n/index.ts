import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { setLanguage } from '../api/client';
import en from './locales/en.json';
import rw from './locales/rw.json';

/**
 * English and Kinyarwanda (spec 4.3, Hard Rule H10). Kinyarwanda strings are placeholders
 * marked [rw-todo] until the product owner supplies copy reviewed by a native speaker
 * (spec 14.2) - never machine-translated. The chosen language is remembered on the device;
 * a signed-in user's saved preference takes over once their profile loads.
 */
export const LANGUAGES = ['rw', 'en'] as const;
export type Language = (typeof LANGUAGES)[number];

const STORAGE_KEY = 'ikimina.language';

function initialLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'en' || stored === 'rw') {
      return stored;
    }
  } catch {
    // storage unavailable (private mode): fall through
  }
  return navigator.language?.toLowerCase().startsWith('en') ? 'en' : 'rw';
}

export function changeLanguage(language: Language): void {
  void i18n.changeLanguage(language);
  try {
    localStorage.setItem(STORAGE_KEY, language);
  } catch {
    // not fatal: the choice just will not survive a reload
  }
}

i18n.on('languageChanged', (language) => {
  setLanguage(language);
  document.documentElement.lang = language;
});

void i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, rw: { translation: rw } },
  lng: initialLanguage(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  returnNull: false,
});

setLanguage(i18n.language);

export default i18n;
