/**
 * Rwandan mobile numbers, mirroring the backend's PhoneNumber: accepts what people type
 * (0788 123 456, 250788123456, +250 788-123-456) and normalises to +2507XXXXXXXX.
 * The server re-validates; this only gives faster feedback.
 */
export function normalisePhone(raw: string): string | null {
  let digits = raw.trim().replace(/[\s\-().]/g, '');
  if (digits.startsWith('07') && digits.length === 10) {
    digits = `+250${digits.slice(1)}`;
  } else if (digits.startsWith('2507')) {
    digits = `+${digits}`;
  }
  return /^\+2507[0-9]{8}$/.test(digits) ? digits : null;
}

/** +250788123456 → "0788 123 456", the way numbers are written locally. */
export function displayPhone(e164: string | null | undefined): string {
  if (!e164 || !/^\+2507[0-9]{8}$/.test(e164)) {
    return e164 ?? '';
  }
  const local = `0${e164.slice(4)}`;
  return `${local.slice(0, 4)} ${local.slice(4, 7)} ${local.slice(7)}`;
}
