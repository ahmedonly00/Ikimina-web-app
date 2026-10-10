import { forwardRef, useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';

/*
 * Small, accessible building blocks (spec 18.1): 44px touch targets, visible focus,
 * labels tied to inputs, errors announced to screen readers, never colour alone.
 */

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

const BUTTON: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-on-brand hover:bg-brand-strong disabled:opacity-60',
  secondary: 'bg-card text-brand border border-brand hover:bg-brand-soft disabled:opacity-60',
  danger: 'bg-card text-danger border border-danger hover:bg-danger-soft disabled:opacity-60',
  ghost: 'text-brand hover:bg-brand-soft disabled:opacity-60',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  busy?: boolean;
  block?: boolean;
}

export function Button({ variant = 'primary', busy = false, block = false, className = '', children, disabled, ...rest }: ButtonProps) {
  const { t } = useTranslation();
  return (
    <button
      {...rest}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={`inline-flex min-h-11 items-center justify-center rounded-lg px-4 text-base font-semibold transition-colors ${
        block ? 'w-full' : ''
      } ${BUTTON[variant]} ${className}`}
    >
      {busy ? t('common.loading') : children}
    </button>
  );
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
}

export const TextField = forwardRef<HTMLInputElement, FieldProps>(function TextField({ label, error, hint, id, ...rest }, ref) {
  const generated = useId();
  const inputId = id ?? generated;
  const describedBy = [hint ? `${inputId}-hint` : null, error ? `${inputId}-error` : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-sm font-semibold text-ink">
        {label}
      </label>
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`min-h-11 rounded-lg border bg-card px-3 text-base text-ink ${error ? 'border-danger' : 'border-line'}`}
        {...rest}
      />
      {hint && (
        <p id={`${inputId}-hint`} className="text-sm text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${inputId}-error`} role="alert" className="text-sm font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  );
});

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  children: ReactNode;
}

export const SelectField = forwardRef<HTMLSelectElement, SelectProps>(function SelectField({ label, error, id, children, ...rest }, ref) {
  const generated = useId();
  const selectId = id ?? generated;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={selectId} className="text-sm font-semibold text-ink">
        {label}
      </label>
      <select
        ref={ref}
        id={selectId}
        aria-invalid={error ? true : undefined}
        className="min-h-11 rounded-lg border border-line bg-card px-3 text-base text-ink"
        {...rest}
      >
        {children}
      </select>
      {error && (
        <p role="alert" className="text-sm font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  );
});

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-xl border border-line bg-card p-4 shadow-sm ${className}`}>{children}</section>;
}

export function PageTitle({ children, subtitle }: { children: ReactNode; subtitle?: ReactNode }) {
  return (
    <header className="mb-4">
      <h1 className="text-2xl font-bold text-ink">{children}</h1>
      {subtitle && <p className="mt-1 text-muted">{subtitle}</p>}
    </header>
  );
}

type Tone = 'info' | 'success' | 'warning' | 'danger';

const TONE: Record<Tone, string> = {
  info: 'bg-brand-soft text-brand-strong',
  success: 'bg-success-soft text-success',
  warning: 'bg-accent text-accent-ink',
  danger: 'bg-danger-soft text-danger',
};

export function Alert({ tone = 'info', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={`rounded-lg px-3 py-2 text-sm font-medium ${TONE[tone]}`}>
      {children}
    </div>
  );
}

/** Shows an error as the server described it (already in the user's language), with the reference for support. */
export function ErrorMessage({ error }: { error: unknown }) {
  const { t } = useTranslation();
  if (!error) {
    return null;
  }
  if (error instanceof ApiError) {
    const { detail, title, requestId, reasons } = error.problem;
    return (
      <Alert tone="danger">
        <span>{detail || title || t('errors.generic')}</span>
        {reasons && reasons.length > 0 && (
          <ul className="mt-1 list-inside list-disc">
            {reasons.map((reason) => (
              <li key={reason.code}>{reason.message}</li>
            ))}
          </ul>
        )}
        {requestId && error.status >= 500 && <span className="block text-xs">{t('errors.reference', { requestId })}</span>}
      </Alert>
    );
  }
  return <Alert tone="danger">{error instanceof TypeError ? t('errors.network') : t('errors.generic')}</Alert>;
}

export function Loading() {
  const { t } = useTranslation();
  return (
    <p role="status" className="py-8 text-center text-muted">
      {t('common.loading')}
    </p>
  );
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'brand' | 'accent' }) {
  const style = tone === 'brand' ? 'bg-brand-soft text-brand-strong' : tone === 'accent' ? 'bg-accent text-accent-ink' : 'bg-surface text-muted border border-line';
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${style}`}>{children}</span>;
}
