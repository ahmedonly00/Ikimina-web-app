/**
 * Rates and multiples (interest %, × savings) as the server sends them: a decimal string, or a JSON
 * number for a BigDecimal. Shown without trailing zeros after the decimal point - never touching the
 * zeros of the whole part ("10" stays "10", "5.5000" becomes "5.5"). Display only: no arithmetic.
 */
export function formatDecimal(value: string | number): string {
  const text = String(value).trim();
  if (!text.includes('.')) {
    return text;
  }
  return text.replace(/0+$/, '').replace(/\.$/, '');
}
