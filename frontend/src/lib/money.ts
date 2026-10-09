/**
 * RWF amounts in the UI. Money arrives as a decimal string ("150000.00") and is never turned
 * into a JavaScript number, which is binary floating point (Hard Rule H1). Formatting works
 * on the digits directly.
 */

const AMOUNT = /^-?(0|[1-9][0-9]{0,16})(\.[0-9]{1,2})?$/;

/** Whether the text is an amount the API accepts: plain digits, at most two decimals. */
export function isValidAmount(text: string): boolean {
  return AMOUNT.test(text.trim());
}

/** "150000.00" → "150,000 RWF". RWF has no minor unit in practice, so zero decimals are dropped. */
export function formatRwf(amount: string): string {
  const trimmed = amount.trim();
  if (!isValidAmount(trimmed)) {
    return amount;
  }
  const negative = trimmed.startsWith('-');
  const [whole = '0', fraction = ''] = trimmed.replace('-', '').split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const cents = fraction.replace(/0+$/, '');
  return `${negative ? '-' : ''}${grouped}${cents ? `.${cents}` : ''} RWF`;
}

/** Normalises user input like "5,000" or "5000" to the wire form "5000.00"; null if not an amount. */
export function toWireAmount(input: string): string | null {
  const compact = input.replace(/[\s,]/g, '');
  if (!isValidAmount(compact)) {
    return null;
  }
  const [whole = '0', fraction = ''] = compact.split('.');
  return `${whole}.${fraction.padEnd(2, '0')}`;
}
