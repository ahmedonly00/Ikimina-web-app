import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router';
import { z } from 'zod';
import { acceptTokens, post } from '../../api/client';
import { Alert, Button, Card, ErrorMessage, PageTitle, TextField } from '../../components/ui';
import { currentPasswordField, fieldError, phoneField } from '../../lib/forms';
import { normalisePhone } from '../../lib/phone';

const schema = z.object({ phone: phoneField, password: currentPasswordField });
type Values = z.infer<typeof schema>;

export function LoginPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const notice = (location.state as { notice?: string } | null)?.notice;
  const { register, handleSubmit, formState } = useForm<Values>({ resolver: zodResolver(schema) });

  const login = useMutation({
    mutationFn: (values: Values) =>
      post<{ accessToken: string; tokenType: string; expiresIn: number }>('/auth/login', {
        phone: normalisePhone(values.phone),
        password: values.password,
      }),
    // Storing the token flips the session to signed-in; GuestOnly then sends the user on.
    onSuccess: acceptTokens,
  });

  return (
    <>
      <PageTitle subtitle={t('app.tagline')}>{t('auth.login.title')}</PageTitle>
      <Card>
        <form noValidate onSubmit={handleSubmit((values) => login.mutate(values))} className="flex flex-col gap-4">
          {notice && <Alert tone="success">{t(notice)}</Alert>}
          <ErrorMessage error={login.error} />
          <TextField
            label={t('auth.login.phone')}
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            placeholder="0788 123 456"
            error={fieldError(t, formState.errors.phone)}
            {...register('phone')}
          />
          <TextField
            label={t('auth.login.password')}
            type="password"
            autoComplete="current-password"
            error={fieldError(t, formState.errors.password)}
            {...register('password')}
          />
          <Button type="submit" block busy={login.isPending}>
            {t('auth.login.submit')}
          </Button>
          <Link to="/forgot-password" className="text-center font-semibold text-brand underline">
            {t('auth.login.forgot')}
          </Link>
        </form>
      </Card>
      <p className="mt-4 text-center">
        {t('auth.login.noAccount')}{' '}
        <Link to="/register" state={location.state} className="font-semibold text-brand underline">
          {t('auth.login.register')}
        </Link>
      </p>
    </>
  );
}
