import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import en from './locales/en.json';
import rw from './locales/rw.json';

/** Hard Rule H10: both languages carry exactly the same keys, and the UI only uses keys that exist. */

type Tree = { [key: string]: string | Tree };

function keys(tree: Tree, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([key, value]) =>
    typeof value === 'string' ? [`${prefix}${key}`] : keys(value, `${prefix}${key}.`),
  );
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      return sourceFiles(path);
    }
    return /\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name) ? [path] : [];
  });
}

describe('translations', () => {
  const english = keys(en as Tree).sort();
  const kinyarwanda = keys(rw as Tree).sort();

  it('Kinyarwanda has exactly the English keys', () => {
    expect(kinyarwanda).toEqual(english);
  });

  it('every t("...") key used in the code exists', () => {
    const known = new Set(english);
    const missing: string[] = [];
    for (const file of sourceFiles(resolve(__dirname, '..'))) {
      const source = readFileSync(file, 'utf8');
      for (const match of source.matchAll(/\bt\(\s*['"]([a-zA-Z0-9_.]+)['"]/g)) {
        const key = match[1] ?? '';
        if (!known.has(key)) {
          missing.push(`${file}: ${key}`);
        }
      }
    }
    expect(missing).toEqual([]);
  });

  it('reports how much Kinyarwanda copy is still a placeholder', () => {
    const values = keys(rw as Tree).length;
    const placeholders = JSON.stringify(rw).match(/\[rw-todo\]/g)?.length ?? 0;
    console.info(`i18n: ${placeholders} of ${values} Kinyarwanda strings are [rw-todo] placeholders`);
    expect(placeholders).toBeLessThanOrEqual(values);
  });
});
