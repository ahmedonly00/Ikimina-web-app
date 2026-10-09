import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useLocation } from 'react-router';
import { z } from 'zod';
import { acceptTokens, post } from '../../api/client';
import { Button, Card, ErrorMessage, PageTitle, TextField } from '../../components/ui';
import { fieldError, otpField } from '../../lib/forms';
import { displayPhone } from '../../lib/phone';

const schema = z.object({ otp: otpField });
type Values = z.infer<typeof schema>;

/** Step 2 of registration: the SMS code proves the phone; the account is created and signed in. */
export function VerifyPhonePage() {
  const { t } = useTranslation();
  const location = useLocation();
  const phone = (location.state as { phone?: string } | null)?.phone;
  const { register, handleSubmit, formState } = useForm<Values>({ resolver: zodResolver(schema) });

  const verify = useMutation({
    mutationFn: (values: Values) =>
      post<{ accessToken: string; tokenType: string; expiresIn: number }>('/auth/verify-phone', { phone, otp: values.otp }),
    onSuccess: acceptTokens,
  });

  if (!phone) {
    return <Navigate to="/register" replace />;
  }

  return (
    <>
      <PageTitle subtitle={t('auth.verify.sentTo', { phone: displayPhone(phone) })}>{t('auth.verify.title')}</PageTitle>
      <Card>
        <form noValidate onSubmit={handleSubmit((values) => verify.mutate(values))} className="flex flex-col gap-4">
          <ErrorMessage error={verify.error} />
          <TextField
            label={t('auth.verify.code')}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            error={fieldError(t, formState.errors.otp)}
            {...register('otp')}
          />
          <Button type="submit" block busy={verify.isPending}>
            {t('auth.verify.submit')}
          </Button>
          <Link to="/register" state={location.state} className="text-center font-semibold text-brand underline">
            {t('auth.verify.startOver')}
          </Link>
        </form>
      </Card>
    </>
  );
}
