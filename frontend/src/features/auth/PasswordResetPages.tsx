import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { z } from 'zod';
import { post } from '../../api/client';
import { Button, Card, ErrorMessage, PageTitle, TextField } from '../../components/ui';
import { fieldError, otpField, passwordField, phoneField } from '../../lib/forms';
import { normalisePhone } from '../../lib/phone';

const forgotSchema = z.object({ phone: phoneField });
const resetSchema = z.object({ otp: otpField, newPassword: passwordField });

/** The response is the same whether or not the number has an account (spec 16.1: no enumeration). */
export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { register, handleSubmit, formState } = useForm<z.infer<typeof forgotSchema>>({ resolver: zodResolver(forgotSchema) });
  const send = useMutation({
    mutationFn: (phone: string) => post('/auth/password/forgot', { phone }),
    onSuccess: (_, phone) => navigate('/reset-password', { state: { phone } }),
  });

  return (
    <>
      <PageTitle subtitle={t('auth.forgot.intro')}>{t('auth.forgot.title')}</PageTitle>
      <Card>
        <form
          noValidate
          onSubmit={handleSubmit((values) => send.mutate(normalisePhone(values.phone) ?? values.phone))}
          className="flex flex-col gap-4"
        >
          <ErrorMessage error={send.error} />
          <TextField
            label={t('auth.login.phone')}
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            error={fieldError(t, formState.errors.phone)}
            {...register('phone')}
          />
          <Button type="submit" block busy={send.isPending}>
            {t('auth.forgot.submit')}
          </Button>
        </form>
      </Card>
    </>
  );
}

export function ResetPasswordPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const phone = (useLocation().state as { phone?: string } | null)?.phone;
  const { register, handleSubmit, formState } = useForm<z.infer<typeof resetSchema>>({ resolver: zodResolver(resetSchema) });
  const reset = useMutation({
    mutationFn: (values: z.infer<typeof resetSchema>) => post('/auth/password/reset', { phone, ...values }),
    onSuccess: () => navigate('/login', { replace: true, state: { notice: 'auth.reset.done' } }),
  });

  if (!phone) {
    return <Navigate to="/forgot-password" replace />;
  }

  return (
    <>
      <PageTitle>{t('auth.reset.title')}</PageTitle>
      <Card>
        <form noValidate onSubmit={handleSubmit((values) => reset.mutate(values))} className="flex flex-col gap-4">
          <ErrorMessage error={reset.error} />
          <TextField
            label={t('auth.reset.code')}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            error={fieldError(t, formState.errors.otp)}
            {...register('otp')}
          />
          <TextField
            label={t('auth.reset.newPassword')}
            type="password"
            autoComplete="new-password"
            hint={t('auth.register.passwordHint')}
            error={fieldError(t, formState.errors.newPassword)}
            {...register('newPassword')}
          />
          <Button type="submit" block busy={reset.isPending}>
            {t('auth.reset.submit')}
          </Button>
        </form>
      </Card>
    </>
  );
}
