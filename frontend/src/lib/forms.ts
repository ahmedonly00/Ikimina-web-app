import type { TFunction } from 'i18next';
import { z } from 'zod';
import { isValidAmount } from './money';
import { normalisePhone } from './phone';

/*
 * Validation shared by the forms. Messages are i18n keys, translated where they are shown,
 * so switching language re-labels existing errors too. The server validates everything again.
 */

export const phoneField = z.string().refine((value) => normalisePhone(value) !== null, 'validation.phone');
export const passwordField = z.string().min(8, 'validation.passwordLength').max(128, 'validation.passwordLength');
export const currentPasswordField = z.string().min(1, 'validation.required');
export const requiredText = (max = 200) => z.string().trim().min(1, 'validation.required').max(max, 'validation.required');
export const otpField = z.string().regex(/^[0-9]{6}$/, 'validation.otp');
export const amountField = z.string().refine((value) => isValidAmount(value.replace(/[\s,]/g, '')), 'validation.amount');

/** Translates a field error whose message is an i18n key. */
export function fieldError(t: TFunction, error: { message?: string } | undefined): string | undefined {
  return error?.message ? t(error.message) : undefined;
}
