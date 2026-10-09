import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/** Spec 18.1/18.2: WCAG 2.1 AA contrast for every text/background pair the UI uses. */

const css = readFileSync(resolve(__dirname, '../styles.css'), 'utf8');

function token(name: string): string {
  const match = new RegExp(`--color-${name}:\\s*(#[0-9a-fA-F]{6})`).exec(css);
  if (!match?.[1]) {
    throw new Error(`token --color-${name} not found`);
  }
  return match[1];
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r = 0, g = 0, b = 0] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(foreground: string, background: string): number {
  const [light, dark] = [luminance(foreground), luminance(background)].sort((a, b) => b - a) as [number, number];
  return (light + 0.05) / (dark + 0.05);
}

const PAIRS: [text: string, background: string][] = [
  ['ink', 'surface'],
  ['ink', 'card'],
  ['muted', 'surface'],
  ['muted', 'card'],
  ['brand', 'card'],
  ['brand', 'surface'],
  ['on-brand', 'brand'],
  ['on-brand', 'brand-strong'],
  ['brand-strong', 'brand-soft'],
  ['accent-ink', 'accent'],
  ['accent-strong', 'card'],
  ['danger', 'danger-soft'],
  ['danger', 'card'],
  ['success', 'success-soft'],
];

describe('design tokens', () => {
  it.each(PAIRS)('%s on %s meets AA (4.5:1)', (text, background) => {
    expect(contrast(token(text), token(background))).toBeGreaterThanOrEqual(4.5);
  });

  it('confirms why brand amber is never used as text on white', () => {
    expect(contrast(token('accent'), token('card'))).toBeLessThan(4.5);
  });
});
